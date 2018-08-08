package com.fiserv.mobiliti_ocr.frame

import android.graphics.*
import android.util.Log
import android.util.Size
import com.fiserv.kit.render.Renderable
import com.fiserv.mobiliti_ocr.proc.Rgb2Gray
import com.fiserv.mobiliti_ocr.ui.FrameCameraActivity
import com.fiserv.mobiliti_ocr.widget.overlay.OText
import com.fiserv.mobiliti_ocr.widget.overlay.OverlayView
import com.gmail.jiangyang5157.sudoku.widget.scan.imgproc.*
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import android.R.attr.src
import android.R.attr.angle
import com.fiserv.mobiliti_ocr.proc.ByteBuffer2Rgb


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

    private var mByteBuffer2Rgb: ByteBuffer2Rgb? = null

    /**
     *
     */
    private var mFps = 0

    private var mDebugText: OText? = null

    override fun onRelease() {
        mFrameBitmap?.recycle()
        mCroppedBitmap?.recycle()
    }

    override fun onViewSizeChanged(w: Int, h: Int) {
        mViewWidth = w
        mViewHeight = h

        // initialize cropped size
        if (mPreviewWidth < mPreviewHeight) {
            mCroppedScale = mCroppedHeight / mPreviewHeight.toDouble()
            mCroppedWidth = (mPreviewWidth * mCroppedScale).toInt()
        } else {
            mCroppedScale = mCroppedWidth / mPreviewWidth.toDouble()
            mCroppedHeight = (mPreviewHeight * mCroppedScale).toInt()
        }
        Log.d(TAG, "onViewSizeChanged - preview height(${mPreviewWidth}_$mPreviewHeight) cropped(${mCroppedWidth}_$mCroppedHeight) cropped scale($mCroppedScale)")

        // initialize ByteBuffer2Rgb
        mByteBuffer2Rgb = ByteBuffer2Rgb(mPreviewWidth, mPreviewHeight)

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

        //
        mDebugText = OText(size = 40f, color = Color.RED, x = 20f, y = h.toFloat() - 20)
    }

    override fun onPreviewSizeChosen(size: Size, cameraRotation: Int, screenRotation: Int) {
        mPreviewWidth = size.width
        mPreviewHeight = size.height
        mCameraOrientation = cameraRotation - screenRotation
        Log.d(TAG, "onPreviewSizeChosen - preview height(${mPreviewWidth}_$mPreviewHeight) camera rotation($cameraRotation) screen rotation($screenRotation)")
        Log.d(TAG, "Camera orientation relative to screen canvas: $mCameraOrientation")
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

    private fun reshapeMat(
            srcMat: Mat,
            dstWidth: Int,
            dstHeight: Int,
            applyRotation: Int): Mat {

        val srcWidth = srcMat.width()
        val srcHeight = srcMat.height()

        val transpose = (Math.abs(applyRotation) + 90) % 180 == 0
        val inWidth = if (transpose) srcHeight else srcWidth
        val inHeight = if (transpose) srcWidth else srcHeight

        var scale = 1.0
        if (inWidth != dstWidth || inHeight != dstHeight) {
            val scaleFactorX = dstWidth / inWidth.toFloat()
            val scaleFactorY = dstHeight / inHeight.toFloat()
            scale = Math.max(scaleFactorX, scaleFactorY).toDouble()
        }

        val center = org.opencv.core.Point(srcWidth / 2.0, srcHeight / 2.0)
        val rotation = Imgproc.getRotationMatrix2D(center, applyRotation.toDouble(), scale)

//        val size = org.opencv.core.Size(inWidth.toDouble(), inHeight.toDouble())
//        Imgproc.warpAffine(this, this, rotation, size)
//
//        val m = Mat()
//        Imgproc.re

//        return Imgproc.getRotationMatrix2D(center, angle, scale)

        return Mat()
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

        var rgba = mByteBuffer2Rgb!!.map(planes).apply {
            val size = org.opencv.core.Size(width().toDouble(), height().toDouble())
            val center = org.opencv.core.Point(size.width / 2.0, size.height / 2.0)
//            val angle = (Math.abs(mCameraOrientation) + 90) % 180.0
            val angle = mCameraOrientation.toDouble()
            val scale = 1.0
            val rotation = Imgproc.getRotationMatrix2D(center, angle, scale)
            Imgproc.warpAffine(this, this, rotation, size)
        }


        Utils.matToBitmap(rgba, mFrameBitmap)

//        val croppedRgba = Mat().apply {
//            Canvas(mCroppedBitmap).drawBitmap(mFrameBitmap, mFrame2Crop, null)
//            Utils.bitmapToMat(mCroppedBitmap, this)
//        }
//
        val croppedRgba = Mat().apply {
            Imgproc.resize(rgba, this, org.opencv.core.Size(rgba.width() * mCroppedScale, rgba.height() * mCroppedScale))
        }

//        val croppedRgba = Mat().apply {
//            val size = org.opencv.core.Size(rgba.width() * mCroppedScale, rgba.height() * mCroppedScale)
//            val center = org.opencv.core.Point(size.width / 2.0, size.height / 2.0)
//            val angle = (Math.abs(mCameraOrientation) + 90) % 180.0
////            val angle = mCameraOrientation.toDouble()
//            val rotation = Imgproc.getRotationMatrix2D(center, angle, mCroppedScale)
//
////            val size =  org.opencv.core.Size(rgba.width() * mCroppedScale, rgba.height() * mCroppedScale)
//            Imgproc.warpAffine(rgba, this, rotation, size)
//        }


        //
        val processed = processMat(croppedRgba)

//        var contours = mutableListOf<MatOfPoint>()
//        ContoursUtils.findExternals(processed, contours)
//        if (contours.isNotEmpty()) {
//            contours = ContoursUtils.sortByDescendingArea(contours)
//            mDrawContour.draw(processed, contours[0])
//            contours.forEach { it.release() }
//        }

        //
        Utils.matToBitmap(processed, mCroppedBitmap)

        //
        rgba.release()
        croppedRgba.release()
        processed.release()
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

    private var debug_enable_GaussianBlur = false
    private var debug_enable_AdaptiveThreshold = false
    private var debug_enable_CrossDilate = false
    private var debug_enable_Canny = false

    private val mGaussianBlur = GaussianBlur(5.0, 5.0, 0.0, 0.0, Core.BORDER_DEFAULT)
    private val mAdaptiveThreshold = AdaptiveThreshold(255.0, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 5, 2.0)
    private val mCrossDilate = CrossDilate(0.5)
    private val mCanny = Canny(127.0, 255.0, 3, false)
    private val mDrawContour = DrawContour(Color.RED, 2)

}