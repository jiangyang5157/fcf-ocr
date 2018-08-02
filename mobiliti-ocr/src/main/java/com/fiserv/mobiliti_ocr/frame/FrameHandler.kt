package com.fiserv.mobiliti_ocr.frame

import android.graphics.*
import android.util.Log
import android.util.Size
import com.fiserv.kit.render.Renderable
import com.fiserv.mobiliti_ocr.ui.FrameCameraActivity
import com.fiserv.mobiliti_ocr.widget.overlay.OText
import com.fiserv.mobiliti_ocr.widget.overlay.OverlayView
import com.gmail.jiangyang5157.sudoku.widget.scan.imgproc.Gray2Rgb
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer

class FrameHandler : FrameCameraActivity.FrameCropper {

    companion object {
        const val TAG = "FrameHandler"

        const val IMAGE_FORMAT = ImageFormat.YUV_420_888

        const val DEFAULT_CROPPED_WIDTH = 320
        const val DEFAULT_CROPPED_HEIGHT = 320
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
     * Crop each frame from the preview size to an other size
     */
    private var mCroppedWidth = DEFAULT_CROPPED_WIDTH
    private var mCroppedHeight = DEFAULT_CROPPED_HEIGHT

    private var mFrameBitmap: Bitmap? = null
    private var mCroppedBitmap: Bitmap? = null

    private var mFrame2Crop: Matrix? = null
    private var mCrop2Frame: Matrix? = null

    private var mCroppedInts: IntArray? = null

    private var mFps = 0

    private var mDebugText: OText? = null

    override fun onRelease() {
        mFrameBitmap?.recycle()
        mCroppedBitmap?.recycle()
    }

    override fun onViewSizeChanged(w: Int, h: Int) {
        mViewWidth = w
        mViewHeight = h
        mDebugText = OText(size = 40f, color = Color.RED, x = 20f, y = h.toFloat() - 20)
    }

    override fun onPreviewSizeChosen(size: Size, cameraRotation: Int, screenRotation: Int) {
        mPreviewWidth = size.width
        mPreviewHeight = size.height

        val rotation = cameraRotation - screenRotation
        Log.d(TAG, "Camera orientation relative to screen canvas: $rotation")

        mFrameBitmap = Bitmap.createBitmap(
                mPreviewWidth, mPreviewHeight, Bitmap.Config.ARGB_8888)


        mCroppedBitmap = Bitmap.createBitmap(
                mCroppedWidth, mCroppedHeight, Bitmap.Config.ARGB_8888)
        mFrame2Crop = getTransformationMatrix(
                mPreviewWidth, mPreviewHeight, mCroppedWidth, mCroppedHeight, rotation, true)

        mCrop2Frame = Matrix()
        mFrame2Crop?.invert(mCrop2Frame)

        mCroppedInts = IntArray(mCroppedWidth * mCroppedHeight)
    }

    /**
     * Returns a transformation matrix from one reference frame into another.
     * Handles cropping (if maintaining aspect ratio is desired) and rotation.
     *
     * @param srcWidth            Width of source frame.
     * @param srcHeight           Height of source frame.
     * @param dstWidth            Width of destination frame.
     * @param dstHeight           Height of destination frame.
     * @param applyRotation       Amount of rotation to apply from one frame to another.
     * Must be a multiple of 90.
     * @param maintainAspectRatio If true, will ensure that scaling in x and y remains constant,
     * cropping the image if necessary.
     * @return The transformation fulfilling the desired requirements.
     */
    fun getTransformationMatrix(
            srcWidth: Int,
            srcHeight: Int,
            dstWidth: Int,
            dstHeight: Int,
            applyRotation: Int,
            maintainAspectRatio: Boolean): Matrix {

        val matrix = Matrix()
        if (applyRotation != 0) {
            if (applyRotation % 90 != 0) {
                Log.w(TAG, "Rotation of $applyRotation % 90 != 0")
            }

            // Translate so center of image is at origin.
            matrix.postTranslate(-srcWidth / 2.0f, -srcHeight / 2.0f)

            // Rotate around origin.
            matrix.postRotate(applyRotation.toFloat())
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
                matrix.postScale(scaleFactor, scaleFactor)
            } else {
                // Scale exactly to fill dst from src.
                matrix.postScale(scaleFactorX, scaleFactorY)
            }
        }

        if (applyRotation != 0) {
            // Translate back from origin centered reference to destination frame.
            matrix.postTranslate(dstWidth / 2.0f, dstHeight / 2.0f)
        }

        return matrix
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

        val yuvFrameData = Mat(mPreviewHeight, mPreviewWidth, CvType.CV_8UC1, planes[0])
//        val uvFrameData = Mat(mPreviewHeight / 2, mPreviewWidth / 2, CvType.CV_8UC2, planes[1])

        val gray = yuvFrameData.submat(0, mPreviewHeight, 0, mPreviewWidth)
        val rgba = Mat().apply {
            Gray2Rgb.convert(gray, this)
        }

        yuvFrameData.release()
//        uvFrameData.release()


        Utils.matToBitmap(rgba, mFrameBitmap)

        Canvas(mCroppedBitmap).drawBitmap(mFrameBitmap, mFrame2Crop, null)

        rgba.release()
        gray.release()
    }

//    override fun onCameraFrame(inputFrame: Camera2CvViewBase.CvCameraViewFrame): Mat {
//        mFrameRgb?.release()
//        mFrameProcess?.release()
//
//        mFrameRgb = Mat()
//        mFrameProcess = frameProcessing(inputFrame.gray())
//        Gray2Rgb.convert(mFrameProcess!!, mFrameRgb!!)
//
//        var contours = mutableListOf<MatOfPoint>()
//        ContoursUtils.findExternals(mFrameProcess!!, contours)
//        if (contours.isNotEmpty()) {
//            contours = ContoursUtils.sortByDescendingArea(contours)
//            mDrawContour.draw(mFrameRgb!!, contours[0])
//            contours.forEach { it.release() }
//        }
//
//        return mFrameRgb!!
//    }
//
//    private fun frameProcessing(frame: Mat): Mat {
//        var curr = frame
//
//        if (debug_enable_GaussianBlur) {
//            val dst = Mat()
//            mGaussianBlur.convert(curr, dst)
//            curr.release()
//            curr = dst
//        }
//
//        if (debug_enable_AdaptiveThreshold) {
//            val dst = Mat()
//            mAdaptiveThreshold.convert(curr, dst)
//            curr.release()
//            curr = dst
//        }
//
//        if (debug_enable_CrossDilate) {
//            val dst = Mat()
//            mCrossDilate.convert(curr, dst)
//            curr.release()
//            curr = dst
//        }
//
//        if (debug_enable_Canny) {
//            val dst = Mat()
//            mCanny.convert(curr, dst)
//            curr.release()
//            curr = dst
//        }
//
//        return curr
//    }

}