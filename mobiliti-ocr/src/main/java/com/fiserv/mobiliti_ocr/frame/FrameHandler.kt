package com.fiserv.mobiliti_ocr.frame

import android.graphics.*
import android.util.Log
import android.util.Size
import com.fiserv.kit.render.Renderable
import com.fiserv.mobiliti_ocr.proc.Rgb2Gray
import com.fiserv.mobiliti_ocr.ui.FrameCameraActivity
import com.fiserv.mobiliti_ocr.widget.overlay.OText
import com.fiserv.mobiliti_ocr.widget.overlay.OverlayView
import com.gmail.jiangyang5157.sudoku.widget.scan.imgproc.Gray2Rgb
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import java.nio.ByteBuffer

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

    private var mFrameBitmap: Bitmap? = null
    private var mCroppedBitmap: Bitmap? = null

    private var mFrame2Crop: Matrix? = null
    private var mCrop2Frame: Matrix? = null

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
        if (w > h) {
            mCroppedHeight = mCroppedWidth * mViewHeight / mViewWidth
        } else {
            mCroppedWidth = mCroppedHeight * mViewWidth / mViewHeight
        }

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

        val rgba = Mat().apply {
            val yuvFrameData = Mat(mPreviewHeight, mPreviewWidth, CvType.CV_8UC1, planes[0])
            val gray = yuvFrameData.submat(0, mPreviewHeight, 0, mPreviewWidth)
            Gray2Rgb.convert(gray, this)
            yuvFrameData.release()
            gray.release()
        }
        Utils.matToBitmap(rgba, mFrameBitmap)

        val croppedRgba = Mat().apply {
            Canvas(mCroppedBitmap).drawBitmap(mFrameBitmap, mFrame2Crop, null)
            Utils.bitmapToMat(mCroppedBitmap, this)
        }

        //
        val processingMat = Mat()
        Rgb2Gray.convert(croppedRgba, processingMat)

        //
        Utils.matToBitmap(processingMat, mCroppedBitmap)

        //
        rgba.release()
        croppedRgba.release()
        processingMat.release()
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