package com.fiserv.mobiliti_ocr.proc

import android.graphics.*
import android.util.Log
import android.util.Size
import com.fiserv.kit.render.Renderable
import com.fiserv.mobiliti_ocr.ui.FrameCameraActivity
import com.fiserv.mobiliti_ocr.widget.overlay.OText
import com.fiserv.mobiliti_ocr.widget.overlay.OverlayView
import com.gmail.jiangyang5157.sudoku.widget.scan.imgproc.GaussianBlur
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.MatOfInt

class Frameproc : FrameCameraActivity.FrameCropper {

    companion object {
        const val TAG = "Frameproc"

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

    override fun onNewFrame(frameInts: IntArray, fps: Int) {
        mFps = fps

        // gen frame bitmap
        mFrameBitmap?.setPixels(frameInts, 0, mPreviewWidth, 0, 0, mPreviewWidth, mPreviewHeight)

        // gen cropped bitmap
        Canvas(mCroppedBitmap).drawBitmap(mFrameBitmap, mFrame2Crop, null)

        // gen cropped Ints
        mCroppedBitmap?.getPixels(mCroppedInts, 0, mCroppedWidth, 0, 0, mCroppedWidth, mCroppedHeight)

        //
        var curr = MatOfInt(*mCroppedInts!!)

        val mGaussianBlur = GaussianBlur(5.0, 5.0, 0.0, 0.0, Core.BORDER_DEFAULT)
        val dst = MatOfInt()
        mGaussianBlur.convert(curr, dst)
        curr.release()
        curr = dst

        Utils.matToBitmap(curr, mCroppedBitmap)
    }

}