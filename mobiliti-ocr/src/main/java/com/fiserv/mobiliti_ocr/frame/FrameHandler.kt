package com.fiserv.mobiliti_ocr.frame

import android.graphics.*
import android.util.Log
import android.util.Size
import com.fiserv.kit.render.Renderable
import com.fiserv.mobiliti_ocr.proc.Imgproc
import com.fiserv.mobiliti_ocr.ui.FrameCameraActivity
import com.fiserv.mobiliti_ocr.widget.overlay.OText
import com.fiserv.mobiliti_ocr.widget.overlay.OverlayView
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
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
        mFrame2Crop = Imgproc.getTransformationMatrix(
                mPreviewWidth, mPreviewHeight, mCroppedWidth, mCroppedHeight, rotation, true)

        mCrop2Frame = Matrix()
        mFrame2Crop?.invert(mCrop2Frame)

        mCroppedInts = IntArray(mCroppedWidth * mCroppedHeight)
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

        var yuvFrameData = Mat(mPreviewHeight, mPreviewWidth, CvType.CV_8UC1, planes[0])
        var uvFrameData = Mat(mPreviewHeight / 2, mPreviewWidth / 2, CvType.CV_8UC2, planes[1])

        val gray = yuvFrameData.submat(0, mPreviewHeight, 0, mPreviewWidth)
        Utils.matToBitmap(gray, mFrameBitmap)

        Canvas(mCroppedBitmap).drawBitmap(mFrameBitmap, mFrame2Crop, null)

        yuvFrameData.release()
        uvFrameData.release()
    }

//    fun gray(): Mat {
//        return mYuvFrameData.submat(0, mHeight, 0, mWidth)
//    }
//
//    fun rgba(): Mat {
//        org.opencv.imgproc.Imgproc.cvtColorTwoPlane(mYuvFrameData, mUvFrameData, mRgba, org.opencv.imgproc.Imgproc.COLOR_YUV2RGBA_NV21)
//        return mRgba
//    }
}