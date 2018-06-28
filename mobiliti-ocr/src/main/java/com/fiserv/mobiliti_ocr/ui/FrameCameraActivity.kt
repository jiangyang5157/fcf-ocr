package com.fiserv.mobiliti_ocr.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Trace
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.util.Size
import android.view.WindowManager
import android.widget.Toast
import com.fiserv.mobiliti_ocr.R
import com.fiserv.kit.ext.cameraManager
import com.fiserv.kit.ext.instance
import com.fiserv.kit.ext.replaceFragmentInActivity
import com.fiserv.kit.render.FpsMeter
import com.fiserv.kit.render.Renderable
import com.fiserv.mobiliti_ocr.proc.Imgproc
import com.fiserv.mobiliti_ocr.widget.overlay.OText
import com.fiserv.mobiliti_ocr.widget.overlay.OverlayView

class FrameCameraActivity : AppCompatActivity(), FrameCamera2Fragment.Callback, ImageReader.OnImageAvailableListener {

    companion object {
        const val TAG = "FrameCameraActivity"
        const val TAG_HANDLER_THREAD = "${TAG}_Handler_Thread"

        // Camera desired size: desired size for preview
        const val KEY_CAMERA_DESIRED_WIDTH = "KEY_CAMERA_DESIRED_WIDTH"
        const val KEY_CAMERA_DESIRED_HEIGHT = "KEY_CAMERA_DESIRED_HEIGHT"
        const val DEFAULT_CAMERA_DESIRED_WIDTH = 640
        const val DEFAULT_CAMERA_DESIRED_HEIGHT = 480

        // Cropped size: cropped size for each frame
        const val KEY_CROPPED_WIDTH = "KEY_CROPPED_WIDTH"
        const val KEY_CROPPED_HEIGHT = "KEY_CROPPED_HEIGHT"
        const val DEFAULT_CROPPED_WIDTH = 320
        const val DEFAULT_CROPPED_HEIGHT = 320
    }

    private var mBackgroundHandler: Handler? = null
    private var mBackgroundThread: HandlerThread? = null

    private var mViewWidth = 0
    private var mViewHeight = 0

    private lateinit var mDesiredSize: Size
    private var mPreviewWidth = 0
    private var mPreviewHeight = 0

    private var mFrameBytes: IntArray? = null
    private var mFrameBitmap: Bitmap? = null

    private var mCroppedWidth = 0
    private var mCroppedHeight = 0
    private var mCroppedBitmap: Bitmap? = null

    private var mFrame2Crop: Matrix? = null
    private var mCrop2Frame: Matrix? = null

    private val mYuvBytes = arrayOfNulls<ByteArray?>(3)
    private var mImageConverter: Runnable? = null
    private var mImageCloser: Runnable? = null
    private var mIsProcessingFrame = false
    private val mImageProcessingRate = FpsMeter()

    private var mOverlayView: OverlayView? = null

    private var mDebugText: OText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (!hasPermission()) {
            Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_SHORT).show()
            finish()
        }

        val cameraId = chooseCamera()
        if (cameraId == null) {
            Toast.makeText(this, "No Camera Detected", Toast.LENGTH_SHORT).show()
            finish()
        }

        setContentView(R.layout.activity_rgb_camera)

        if (intent.extras == null) {
            mDesiredSize = Size(DEFAULT_CAMERA_DESIRED_WIDTH, DEFAULT_CAMERA_DESIRED_HEIGHT)
            mCroppedWidth = DEFAULT_CROPPED_WIDTH
            mCroppedHeight = DEFAULT_CROPPED_HEIGHT
        } else {
            val desiredWidth = intent.extras.getInt(KEY_CAMERA_DESIRED_WIDTH, DEFAULT_CAMERA_DESIRED_WIDTH)
            val desiredHeight = intent.extras.getInt(KEY_CAMERA_DESIRED_HEIGHT, DEFAULT_CAMERA_DESIRED_HEIGHT)
            mDesiredSize = Size(desiredWidth, desiredHeight)
            mCroppedWidth = intent.extras.getInt(KEY_CROPPED_WIDTH, DEFAULT_CROPPED_WIDTH)
            mCroppedHeight = intent.extras.getInt(KEY_CROPPED_HEIGHT, DEFAULT_CROPPED_HEIGHT)
        }

        val camera2Fragment = instance<FrameCamera2Fragment>(Bundle().apply {
            putString(FrameCamera2Fragment.KEY_CAMERA_ID, cameraId)
            putSize(FrameCamera2Fragment.KEY_DESIRED_SIZE, mDesiredSize)
        }) as FrameCamera2Fragment
        camera2Fragment.setCallback(this)
        camera2Fragment.setOnImageAvailableListener(this)
        replaceFragmentInActivity(R.id.camera_container, camera2Fragment)

        mOverlayView = findViewById(R.id.view_overlay) as OverlayView?
    }

    override fun onDestroy() {
        super.onDestroy()
        mFrameBitmap?.recycle()
        mCroppedBitmap?.recycle()
    }

    private fun hasPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun isHardwareLevelSupported(characteristics: CameraCharacteristics, requiredLevel: Int): Boolean {
        val deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                ?: return false
        return if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
            requiredLevel == deviceLevel
        } else {
            requiredLevel <= deviceLevel
        }
    }

    private fun isCamera2Supported(characteristics: CameraCharacteristics): Boolean {
        val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
        return facing == CameraCharacteristics.LENS_FACING_EXTERNAL
                || isHardwareLevelSupported(characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL)
    }

    private fun isFrontCamera(characteristics: CameraCharacteristics): Boolean {
        val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
        return facing == CameraCharacteristics.LENS_FACING_FRONT
    }

    private fun chooseCamera(): String? {
        try {
            for (cameraId in cameraManager.cameraIdList) {
                val ccs = cameraManager.getCameraCharacteristics(cameraId)

                ccs.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        ?: continue

                if (isFrontCamera(ccs)) {
                    continue
                }

                if (!isCamera2Supported(ccs)) {
                    continue
                }

                return cameraId
            }
        } catch (e: CameraAccessException) {
            Log.d(TAG, "Choose camera error: Not allowed to access camera $e")
        }
        return null
    }

    private fun startBackgroundThread() {
        mBackgroundThread = HandlerThread(TAG_HANDLER_THREAD).apply {
            start()
        }
        mBackgroundHandler = Handler(mBackgroundThread?.looper)
    }

    private fun stopBackgroundThread() {
        try {
            mBackgroundThread?.apply {
                quitSafely()
                join()
            }
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            Log.w(TAG, "Stop background thread: InterruptedException $e")
        }
    }

    private fun runInBackground(r: Runnable) {
        mBackgroundHandler?.apply {
            post(r)
        }
    }

    public override fun onResume() {
        super.onResume()
        startBackgroundThread()
    }

    public override fun onPause() {
        if (!isFinishing) {
            Log.d(TAG, "onPause: Requesting finish")
            finish()
        }
        stopBackgroundThread()
        super.onPause()
    }

    override fun onViewSizeChanged(viewWidth: Int, viewHeight: Int) {
        Log.d(TAG, "onViewSizeChanged: $viewWidth")
        mViewWidth = viewWidth
        mViewHeight = viewHeight
        mDebugText = OText(size = 40f, color = Color.RED, x = 20f, y = viewHeight.toFloat() - 20)
    }

    override fun onPreviewSizeChosen(previewSize: Size, cameraRotation: Int, screenRotation: Int) {
        Log.d(TAG, "onPreviewSizeChosen: $previewSize")
        mPreviewWidth = previewSize.width
        mPreviewHeight = previewSize.height

        val rotation = cameraRotation - screenRotation
        Log.d(TAG, "Camera orientation relative to screen canvas: $rotation")

        mFrameBytes = IntArray(mPreviewWidth * mPreviewHeight)
        mFrameBitmap = Bitmap.createBitmap(
                mPreviewWidth, mPreviewHeight, Bitmap.Config.ARGB_8888)

        mCroppedBitmap = Bitmap.createBitmap(
                mCroppedWidth, mCroppedHeight, Bitmap.Config.ARGB_8888)
        mFrame2Crop = Imgproc.getTransformationMatrix(
                mPreviewWidth, mPreviewHeight, mCroppedWidth, mCroppedHeight, rotation, true)

        mCrop2Frame = Matrix()
        mFrame2Crop?.invert(mCrop2Frame)

        // #### Debug usage: log text
        mOverlayView?.addRenderable(object : Renderable<Canvas> {
            override fun onRender(t: Canvas) {
                mDebugText?.apply {
                    lines.clear()
                    lines.add("Image Processing Rate: ${mImageProcessingRate.fpsRealTime}")
                    lines.add("View Size: ${mViewWidth}x$mViewHeight")
                    lines.add("Preview Size: ${mPreviewWidth}x$mPreviewHeight")
                    lines.add("Cropped Image Size: ${mCroppedWidth}x$mCroppedHeight")
                    onRender(t)
                }
            }
        })

        // #### Debug usage: draw cropped image
        mOverlayView?.addRenderable(object : Renderable<Canvas> {
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

    override fun onImageAvailable(reader: ImageReader?) {
        if (mPreviewWidth == 0 || mPreviewHeight == 0) {
            return
        }

        try {
            val image = reader?.acquireLatestImage() ?: return
            if (mIsProcessingFrame) {
                image.close()
                return
            }

            if (!mImageProcessingRate.accept()) {
                return
            }

            Trace.beginSection("imageAvailable")
            mIsProcessingFrame = true

            val planes = image.planes
            planes.forEachIndexed { i, plane ->
                plane.buffer.apply {
                    if (mYuvBytes[i] == null) {
                        Log.d(TAG, "Initializing buffer " + i + " at size " + capacity())
                        mYuvBytes[i] = ByteArray(capacity())
                    }
                    get(mYuvBytes[i])
                }
            }

            val yRowStride = planes[0].rowStride
            val uvRowStride = planes[1].rowStride
            val uvPixelStride = planes[1].pixelStride
            mImageConverter = Runnable {
                Imgproc.convertYuv420ToArgb8888(
                        mYuvBytes[0]!!,
                        mYuvBytes[1]!!,
                        mYuvBytes[2]!!,
                        mPreviewWidth,
                        mPreviewHeight,
                        yRowStride,
                        uvRowStride,
                        uvPixelStride,
                        mFrameBytes!!)
            }

            mImageCloser = Runnable {
                image.close()
                mIsProcessingFrame = false
            }

            runInBackground(Runnable {
                processImage()
            })
        } catch (e: Exception) {
            Log.d(TAG, "onImageAvailable: Exception $e")
        }
        Trace.endSection()
    }

    private fun processImage() {
        // gen frame bytes
        mImageConverter?.run()

        // gen frame bitmap
        mFrameBitmap?.setPixels(mFrameBytes, 0, mPreviewWidth, 0, 0, mPreviewWidth, mPreviewHeight)

        // gen cropped frame bitmap
        Canvas(mCroppedBitmap).drawBitmap(mFrameBitmap, mFrame2Crop, null)

        // draw overlays
        mOverlayView?.postInvalidate()

        // close image
        mImageCloser?.run()
    }

}