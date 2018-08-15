package com.fiserv.mobiliti_ocr.frame

import android.app.Activity
import android.graphics.*
import android.support.v7.widget.SwitchCompat
import android.util.Log
import android.util.Size
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.TextView
import com.fiserv.kit.render.Renderable
import com.fiserv.mobiliti_ocr.R
import com.fiserv.mobiliti_ocr.proc.Rgb2Gray
import com.fiserv.mobiliti_ocr.ui.FrameCameraActivity
import com.fiserv.mobiliti_ocr.widget.overlay.OText
import com.fiserv.mobiliti_ocr.widget.overlay.OverlayView
import com.gmail.jiangyang5157.sudoku.widget.scan.imgproc.*
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import com.fiserv.mobiliti_ocr.proc.ByteBuffer2Rgb
import com.fiserv.mobiliti_ocr.proc.Gray2Rgb
import com.fiserv.mobiliti_ocr.proc.MatScale

class FrameHandler : FrameCameraActivity.FrameCropper {

    companion object {
        const val TAG = "FrameHandler"

        const val IMAGE_FORMAT = ImageFormat.YUV_420_888

        val BITMAP_CONFIG = Bitmap.Config.ARGB_8888

        const val MAX_CROPPED_SIZE = 480
    }

    /**
     * Camera view port size -- SurfaceTexture size
     */
    private var mViewWidth = 0
    private var mViewHeight = 0

    /**
     * Camera preview size from [com.fiserv.mobiliti_ocr.ui.FrameCamera2Fragment.chooseOptimalSize]
     */
    private var mPreviewWidth = 0
    private var mPreviewHeight = 0

    /**
     * Camera orientation relative to screen canvas: cameraRotation - screenRotation
     */
    private var mCameraOrientation = 0

    /**
     * Crop each frame from the preview size to a new size
     */
    private var mCroppedWidth = MAX_CROPPED_SIZE
    private var mCroppedHeight = MAX_CROPPED_SIZE
    private var mCroppedScale = 1.0

    private var mFrameBitmap: Bitmap? = null
    private var mCroppedBitmap: Bitmap? = null

    private var mFrame2Crop: Matrix? = null
    private var mCrop2Frame: Matrix? = null

    //
    private var mFps = 0

    private var mDebugText: OText? = null

    override fun onRelease() {
        mFrameBitmap?.recycle()
        mCroppedBitmap?.recycle()
    }

    override fun onViewSizeChanged(w: Int, h: Int) {
        mViewWidth = w
        mViewHeight = h

        // debug
        mDebugText = OText(size = 40f, color = Color.RED, x = 20f, y = mViewHeight.toFloat() - 20)
    }

    override fun onPreviewSizeChosen(size: Size, cameraRotation: Int, screenRotation: Int) {
        mPreviewWidth = size.width
        mPreviewHeight = size.height
        mCameraOrientation = cameraRotation - screenRotation
        Log.d(TAG, "onPreviewSizeChosen - preview height(${mPreviewWidth}_$mPreviewHeight) camera rotation($cameraRotation) screen rotation($screenRotation)")
        Log.d(TAG, "Camera orientation relative to screen canvas: $mCameraOrientation")

        // initialize cropped size
        if (mPreviewWidth < mPreviewHeight) {
            mCroppedScale = mCroppedHeight / mPreviewHeight.toDouble()
            mCroppedWidth = (mPreviewWidth * mCroppedScale).toInt()
        } else {
            mCroppedScale = mCroppedWidth / mPreviewWidth.toDouble()
            mCroppedHeight = (mPreviewHeight * mCroppedScale).toInt()
        }
        Log.d(TAG, "onViewSizeChanged - preview height(${mPreviewWidth}_$mPreviewHeight) cropped(${mCroppedWidth}_$mCroppedHeight) cropped scale($mCroppedScale)")

        // initialize bitmap
        mFrameBitmap = Bitmap.createBitmap(mPreviewWidth, mPreviewHeight, BITMAP_CONFIG)
        mCroppedBitmap = Bitmap.createBitmap(mCroppedWidth, mCroppedHeight, BITMAP_CONFIG)

        // initialize matrix
        mFrame2Crop = buildTransformationMatrix(
                mPreviewWidth, mPreviewHeight,
                mCroppedWidth, mCroppedHeight,
                mCameraOrientation, true)
        mCrop2Frame = Matrix()
        mFrame2Crop?.invert(mCrop2Frame)
    }

    /**
     * Returns a transformation matrix from one reference frame into another.
     * Handles cropping (if maintaining aspect ratio is desired) and rotation.
     *
     * @param srcWidth            Width of source frame.
     * @param srcHeight           Height of source frame.
     * @param dstWidth            Width of destination frame.
     * @param dstHeight           Height of destination frame.
     * @param applyRotation       Amount of rotation to apply from one frame to another. Must be a multiple of 90.
     * @param maintainAspectRatio If true, will ensure that scaling in x and y remains constant, cropping the image if necessary.
     * @return The transformation fulfilling the desired requirements.
     */
    private fun buildTransformationMatrix(
            srcWidth: Int,
            srcHeight: Int,
            dstWidth: Int,
            dstHeight: Int,
            applyRotation: Int,
            maintainAspectRatio: Boolean): Matrix {

        val ret = Matrix()
        if (applyRotation != 0) {
            if (applyRotation % 90 != 0) {
                Log.w(TAG, "Rotation of $applyRotation % 90 != 0")
            }

            // Translate so center of image is at origin.
            ret.postTranslate(-srcWidth / 2.0f, -srcHeight / 2.0f)

            // Rotate around origin.
            ret.postRotate(applyRotation.toFloat())
        }

        // Account for the already applied rotation, if any, and then determine how
        // much scaling is needed for each axis.
        val transpose = (Math.abs(applyRotation) + 90) % 180 == 0

        val inWidth = if (transpose) srcHeight else srcWidth
        val inHeight = if (transpose) srcWidth else srcHeight

        // Apply scaling if necessary.
        if (inWidth != dstWidth || inHeight != dstHeight) {
            val scaleFactorX = dstWidth / inWidth.toFloat()
            val scaleFactorY = dstHeight / inHeight.toFloat()

            if (maintainAspectRatio) {
                // Scale by minimum factor so that dst is filled completely while
                // maintaining the aspect ratio. Some image may fall off the edge.
                val scaleFactor = Math.max(scaleFactorX, scaleFactorY)
                ret.postScale(scaleFactor, scaleFactor)
            } else {
                // Scale exactly to fill dst from src.
                ret.postScale(scaleFactorX, scaleFactorY)
            }
        }

        if (applyRotation != 0) {
            // Translate back from origin centered reference to destination frame.
            ret.postTranslate(dstWidth / 2.0f, dstHeight / 2.0f)
        }

        return ret
    }

    override fun onOverlayViewCreated(view: OverlayView) {

        // #### Debug usage: log text
        view.addRenderable(object : Renderable<Canvas> {
            override fun onRender(t: Canvas) {
                mDebugText?.apply {
                    lines.clear()
                    lines.add("Processing FPS: $mFps")
                    lines.add("View Size: ${mViewWidth}_$mViewHeight")
                    lines.add("Preview Size: ${mPreviewWidth}_$mPreviewHeight")
                    lines.add("Cropped Size: ${mCroppedWidth}_$mCroppedHeight")
                    onRender(t)
                }
            }
        })

        // #### Debug usage: draw cropped image
        view.addRenderable(object : Renderable<Canvas> {
            override fun onRender(t: Canvas) {
                if (mCroppedBitmap != null) {
                    Bitmap.createBitmap(mCroppedBitmap)?.apply {
                        val matrix = Matrix()
                        matrix.postTranslate(
                                (t.width - this.width).toFloat(),
                                (t.height - this.height).toFloat())
                        t.drawBitmap(this, matrix, Paint())
                    }
                }
            }
        })
    }

    override fun onNewFrame(planes: Array<ByteBuffer?>, fps: Int) {
        mFps = fps

        if (mFrameBitmap == null || mCroppedBitmap == null) {
            return
        }

        // raw rgba mat
        val rgba = ByteBuffer2Rgb(mPreviewWidth, mPreviewHeight).map(planes)
        // raw rgba bitmap
        Utils.matToBitmap(rgba, mFrameBitmap)

        // cropping raw rgba
        val croppedRgba = MatScale(mCroppedScale).map(rgba)
        // processing cropped
        val processedGray = processMat(croppedRgba)
        val processedRgba = Mat()
        Gray2Rgb.convert(processedGray, processedRgba)


        //
        var contours = mutableListOf<MatOfPoint>()
        ContoursUtils.findExternals(processedGray, contours)
        if (contours.isNotEmpty()) {
            contours = ContoursUtils.sortByDescendingArea(contours)
            mDrawContour.draw(processedRgba, contours[0])
            contours.forEach { it.release() }
        }

        // convert processed cropped to bitmap
        Utils.matToBitmap(processedRgba, mCroppedBitmap)

        rgba.release()
        croppedRgba.release()
        processedGray.release()
        processedRgba.release()
    }

    private fun processMat(rgba: Mat): Mat {
        var curr = Mat()
        Rgb2Gray.convert(rgba, curr)

        if (debug_enable_GaussianBlur) {
            val dst = Mat()
            mGaussianBlur.convert(curr, dst)
            curr.release()
            curr = dst
        }

        if (debug_enable_AdaptiveThreshold) {
            val dst = Mat()
            mAdaptiveThreshold.convert(curr, dst)
            curr.release()
            curr = dst
        }

        if (debug_enable_CrossDilate) {
            val dst = Mat()
            mCrossDilate.convert(curr, dst)
            curr.release()
            curr = dst
        }

        if (debug_enable_Canny) {
            val dst = Mat()
            mCanny.convert(curr, dst)
            curr.release()
            curr = dst
        }

        return curr
    }

    /* ################ DEBUG ################ */
    private var debug_enable_GaussianBlur = false
    private var debug_enable_AdaptiveThreshold = false
    private var debug_enable_CrossDilate = false
    private var debug_enable_Canny = false

    private val mGaussianBlur = GaussianBlur(5.0, 5.0, 0.0, 0.0, Core.BORDER_DEFAULT)
    private val mAdaptiveThreshold = AdaptiveThreshold(255.0, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 5, 2.0)
    private val mCrossDilate = CrossDilate(0.5)
    private val mCanny = Canny(127.0, 255.0, 3, false)
    private val mDrawContour = DrawContour(Color.RED, 2)

    public fun setup_debug_panel(aty : Activity){
        val debug_panel_container = aty.findViewById(R.id.debug_panel_container) as ViewGroup
        val debug_panel_gaussian_blur = aty.layoutInflater.inflate(R.layout.debug_panel_gaussian_blur, debug_panel_container, false)
        val debug_panel_adaptive_threshold = aty.layoutInflater.inflate(R.layout.debug_panel_adaptive_threshold, debug_panel_container, false)
        val debug_panel_cross_dilate = aty.layoutInflater.inflate(R.layout.debug_panel_cross_dilate, debug_panel_container, false)
        val debug_panel_canny = aty.layoutInflater.inflate(R.layout.debug_panel_canny, debug_panel_container, false)

        aty.findViewById(R.id.debug_btn_gaussian_blur).setOnClickListener {
            if (debug_panel_gaussian_blur.parent == null) {
                debug_panel_container.removeAllViews()
                debug_panel_container.addView(debug_panel_gaussian_blur)
            } else {
                debug_panel_container.removeAllViews()
            }
        }
        aty.findViewById(R.id.debug_btn_adaptive_threshold).setOnClickListener {
            if (debug_panel_adaptive_threshold.parent == null) {
                debug_panel_container.removeAllViews()
                debug_panel_container.addView(debug_panel_adaptive_threshold)
            } else {
                debug_panel_container.removeAllViews()
            }
        }
        aty.findViewById(R.id.debug_btn_cross_dilate).setOnClickListener {
            if (debug_panel_cross_dilate.parent == null) {
                debug_panel_container.removeAllViews()
                debug_panel_container.addView(debug_panel_cross_dilate)
            } else {
                debug_panel_container.removeAllViews()
            }
        }
        aty.findViewById(R.id.debug_btn_canny).setOnClickListener {
            if (debug_panel_canny.parent == null) {
                debug_panel_container.removeAllViews()
                debug_panel_container.addView(debug_panel_canny)
            } else {
                debug_panel_container.removeAllViews()
            }
        }

        /*
        Gaussian Blur
        */
        val debug_sb_guassion_blur_ksize = debug_panel_gaussian_blur.findViewById(R.id.debug_sb_guassion_blur_ksize) as SeekBar
        val debug_sb_guassion_blur_sigma = debug_panel_gaussian_blur.findViewById(R.id.debug_sb_guassion_blur_sigma) as SeekBar
        val debug_cb_guassian_blur = debug_panel_gaussian_blur.findViewById(R.id.debug_cb_guassian_blur) as CheckBox
        debug_cb_guassian_blur.setOnCheckedChangeListener { _, isChecked ->
            debug_enable_GaussianBlur = isChecked
        }
        val guassion_blur_ksize_step = 2.0
        val guassion_blur_ksize_min = 1.0
        val guassion_blur_ksize_max = 21.0
        debug_sb_guassion_blur_ksize.max = ((guassion_blur_ksize_max - guassion_blur_ksize_min) / guassion_blur_ksize_step).toInt()
        debug_sb_guassion_blur_ksize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = guassion_blur_ksize_min + progress.toDouble() * guassion_blur_ksize_step
                (debug_panel_gaussian_blur.findViewById(R.id.debug_tv_guassion_blur_ksize) as TextView).text = value.toString()
                mGaussianBlur.kWidth = value
                mGaussianBlur.kHeight = value
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        val guassion_blur_sigma_step = 0.1
        val guassion_blur_sigma_min = 0.0
        val guassion_blur_sigma_max = 20.0
        debug_sb_guassion_blur_sigma.max = ((guassion_blur_sigma_max - guassion_blur_sigma_min) / guassion_blur_sigma_step).toInt()
        debug_sb_guassion_blur_sigma.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = guassion_blur_sigma_min + progress.toDouble() * guassion_blur_sigma_step
                (debug_panel_gaussian_blur.findViewById(R.id.debug_tv_guassion_blur_sigma) as TextView).text = value.toString()
                mGaussianBlur.sigmaX = value
                mGaussianBlur.sigmaY = value
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        debug_cb_guassian_blur.isChecked = false
        debug_sb_guassion_blur_ksize.progress = 2 // 5.0
        debug_sb_guassion_blur_sigma.progress = 0 // 0.0

        /*
        Adaptive Threshold
        */
        val debug_sb_adaptive_threshold_block_size = debug_panel_adaptive_threshold.findViewById(R.id.debug_sb_adaptive_threshold_block_size) as SeekBar
        val debug_sb_adaptive_threshold_c = debug_panel_adaptive_threshold.findViewById(R.id.debug_sb_adaptive_threshold_c) as SeekBar
        val debug_cb_adaptive_threshold = debug_panel_adaptive_threshold.findViewById(R.id.debug_cb_adaptive_threshold) as CheckBox
        debug_cb_adaptive_threshold.setOnCheckedChangeListener { _, isChecked ->
            debug_enable_AdaptiveThreshold = isChecked
        }
        val adaptive_threshold_block_size_step = 2
        val adaptive_threshold_block_size_min = 3
        val adaptive_threshold_block_size_max = 41
        debug_sb_adaptive_threshold_block_size.max = ((adaptive_threshold_block_size_max - adaptive_threshold_block_size_min) / adaptive_threshold_block_size_step)
        debug_sb_adaptive_threshold_block_size.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = adaptive_threshold_block_size_min + progress * adaptive_threshold_block_size_step
                (debug_panel_adaptive_threshold.findViewById(R.id.debug_tv_adaptive_threshold_block_size) as TextView).text = value.toString()
                mAdaptiveThreshold.blockSize = value
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        val adaptive_threshold_c_step = 0.1
        val adaptive_threshold_c_min = 0.0
        val adaptive_threshold_c_max = 10.0
        debug_sb_adaptive_threshold_c.max = ((adaptive_threshold_c_max - adaptive_threshold_c_min) / adaptive_threshold_c_step).toInt()
        debug_sb_adaptive_threshold_c.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = adaptive_threshold_c_min + progress.toDouble() * adaptive_threshold_c_step
                (debug_panel_adaptive_threshold.findViewById(R.id.debug_tv_adaptive_threshold_c) as TextView).text = value.toString()
                mAdaptiveThreshold.c = value
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        debug_cb_adaptive_threshold.isChecked = false
        debug_sb_adaptive_threshold_block_size.progress = 1 // 5.0
        debug_sb_adaptive_threshold_c.progress = 20 // 2.0

        /*
        Cross Dilate
        */
        val debug_sb_cross_dilate_dilation_size = debug_panel_cross_dilate.findViewById(R.id.debug_sb_cross_dilate_dilation_size) as SeekBar
        val debug_cb_cross_dilate = debug_panel_cross_dilate.findViewById(R.id.debug_cb_cross_dilate) as CheckBox
        debug_cb_cross_dilate.setOnCheckedChangeListener { _, isChecked ->
            debug_enable_CrossDilate = isChecked
        }
        val cross_dilate_dilation_size_step = 0.1
        val cross_dilate_dilation_size_min = 0.0
        val cross_dilate_dilation_size_max = 10.0
        debug_sb_cross_dilate_dilation_size.max = (((cross_dilate_dilation_size_max - cross_dilate_dilation_size_min) / cross_dilate_dilation_size_step).toInt())
        debug_sb_cross_dilate_dilation_size.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = cross_dilate_dilation_size_min + progress.toDouble() * cross_dilate_dilation_size_step
                (debug_panel_cross_dilate.findViewById(R.id.debug_tv_cross_dilate_dilation_size) as TextView).text = value.toString()
                mCrossDilate.dilationSize = value
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        debug_cb_cross_dilate.isChecked = false
        debug_sb_cross_dilate_dilation_size.progress = 1

        /*
        Canny
        */
        val debug_sb_canny_threshold1 = debug_panel_canny.findViewById(R.id.debug_sb_canny_threshold1) as SeekBar
        val debug_sb_canny_threshold2 = debug_panel_canny.findViewById(R.id.debug_sb_canny_threshold2) as SeekBar
        val debug_sb_canny_aperture_size = debug_panel_canny.findViewById(R.id.debug_sb_canny_aperture_size) as SeekBar
        val debug_sc_canny_l2gradient = debug_panel_canny.findViewById(R.id.debug_sc_canny_l2gradient) as SwitchCompat
        val debug_cb_canny = debug_panel_canny.findViewById(R.id.debug_cb_canny) as CheckBox
        debug_cb_canny.setOnCheckedChangeListener { _, isChecked ->
            debug_enable_Canny = isChecked
        }
        val canny_threshold1_step = 1.0
        val canny_threshold1_min = 0.0
        val canny_threshold1_max = 255.0
        debug_sb_canny_threshold1.max = (((canny_threshold1_max - canny_threshold1_min) / canny_threshold1_step).toInt())
        debug_sb_canny_threshold1.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = canny_threshold1_min + progress.toDouble() * canny_threshold1_step
                (debug_panel_canny.findViewById(R.id.debug_tv_canny_threshold1) as TextView).text = value.toString()
                mCanny.threshold1 = value
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        val canny_threshold2_step = 1.0
        val canny_threshold2_min = 0.0
        val canny_threshold2_max = 255.0
        debug_sb_canny_threshold2.max = (((canny_threshold2_max - canny_threshold2_min) / canny_threshold2_step).toInt())
        debug_sb_canny_threshold2.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = canny_threshold2_min + progress.toDouble() * canny_threshold2_step
                (debug_panel_canny.findViewById(R.id.debug_tv_canny_threshold2) as TextView).text = value.toString()
                mCanny.threshold2 = value
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        val canny_apertureSize_step = 2
        val canny_apertureSize_min = 3
        val canny_apertureSize_max = 7
        debug_sb_canny_aperture_size.max = (((canny_apertureSize_max - canny_apertureSize_min) / canny_apertureSize_step))
        debug_sb_canny_aperture_size.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = canny_apertureSize_min + progress * canny_apertureSize_step
                (debug_panel_canny.findViewById(R.id.debug_tv_canny_aperture_size) as TextView).text = value.toString()
                mCanny.apertureSize = value
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        debug_sc_canny_l2gradient.setOnCheckedChangeListener { _, isChecked ->
            mCanny.l2gradient = isChecked
        }
        debug_cb_canny.isChecked = false
        debug_sb_canny_threshold1.progress = 1
        debug_sb_canny_threshold2.progress = 1
        debug_sb_canny_aperture_size.progress = 1
    }
}