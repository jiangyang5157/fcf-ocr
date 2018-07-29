package com.fiserv.mobiliti_ocr.ui

import android.Manifest
import android.content.pm.PackageManager
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
import com.fiserv.mobiliti_ocr.proc.Frameproc
import com.fiserv.mobiliti_ocr.proc.Imgproc
import com.fiserv.mobiliti_ocr.widget.overlay.OverlayView
import org.opencv.android.OpenCVLoader

class FrameCameraActivity : AppCompatActivity(),
        FrameCamera2Fragment.ViewSizeListener,
        FrameCamera2Fragment.PreviewSizeListener,
        ImageReader.OnImageAvailableListener {

    companion object {
        const val TAG = "FrameCameraActivity"
        const val TAG_HANDLER_THREAD = "${TAG}_Handler_Thread"

        /**
         * Desired camera preview size for [com.fiserv.mobiliti_ocr.ui.FrameCamera2Fragment.chooseOptimalSize]
         */
        const val KEY_CAMERA_DESIRED_WIDTH = "KEY_CAMERA_DESIRED_WIDTH"
        const val KEY_CAMERA_DESIRED_HEIGHT = "KEY_CAMERA_DESIRED_HEIGHT"
        const val DEFAULT_CAMERA_DESIRED_WIDTH = 640
        const val DEFAULT_CAMERA_DESIRED_HEIGHT = 480

        init {
            if (OpenCVLoader.initDebug()) {
                Log.d(TAG, "Initialization of OpenCV was successful")
            } else {
                Log.d(TAG, "Initialization of OpenCV was failed")
            }
        }
    }

    internal interface FrameCropper : FrameCamera2Fragment.ViewSizeListener, FrameCamera2Fragment.PreviewSizeListener {

        fun onRelease()

        fun onOverlayViewCreated(view: OverlayView)

        fun onNewFrame(mFrameBytes: IntArray, fps: Int)

    }

    private var mBackgroundHandler: Handler? = null
    private var mBackgroundThread: HandlerThread? = null

    private var mPreviewWidth = 0
    private var mPreviewHeight = 0

    private var mFrameInts: IntArray? = null

    private val mImageProcessingRate = FpsMeter()
    private var mIsProcessingFrame = false
    private val mYuvBytes = arrayOfNulls<ByteArray?>(3)
    private var mImageConverter: Runnable? = null
    private var mImageCloser: Runnable? = null

    private var mOverlayView: OverlayView? = null

    private val mFrameCropper = Frameproc()

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

        val desiredWidth: Int
        val desiredHeight: Int
        if (intent.extras == null) {
            desiredWidth = DEFAULT_CAMERA_DESIRED_WIDTH
            desiredHeight = DEFAULT_CAMERA_DESIRED_HEIGHT
        } else {
            desiredWidth = intent.extras.getInt(KEY_CAMERA_DESIRED_WIDTH, DEFAULT_CAMERA_DESIRED_WIDTH)
            desiredHeight = intent.extras.getInt(KEY_CAMERA_DESIRED_HEIGHT, DEFAULT_CAMERA_DESIRED_HEIGHT)
        }

        val camera2Fragment = instance<FrameCamera2Fragment>(Bundle().apply {
            putString(FrameCamera2Fragment.KEY_CAMERA_ID, cameraId)
            putSize(FrameCamera2Fragment.KEY_DESIRED_SIZE, Size(desiredWidth, desiredHeight))
        }) as FrameCamera2Fragment
        camera2Fragment.setViewSizeListener(this)
        camera2Fragment.setPreviewSizeListener(this)
        camera2Fragment.setOnImageAvailableListener(this)
        replaceFragmentInActivity(R.id.camera_container, camera2Fragment)

        mOverlayView = findViewById(R.id.view_overlay) as OverlayView
        mFrameCropper.onOverlayViewCreated(mOverlayView!!)
    }

    override fun onDestroy() {
        super.onDestroy()
        mFrameCropper.onRelease()
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

    override fun onViewSizeChanged(w: Int, h: Int) {
        Log.d(TAG, "onViewSizeChanged: $w")
        mFrameCropper.onViewSizeChanged(w, h)
    }

    override fun onPreviewSizeChosen(size: Size, cameraRotation: Int, screenRotation: Int) {
        Log.d(TAG, "onPreviewSizeChosen: $size")
        mPreviewWidth = size.width
        mPreviewHeight = size.height
        mFrameInts = IntArray(mPreviewWidth * mPreviewHeight)
        mFrameCropper.onPreviewSizeChosen(size, cameraRotation, screenRotation)
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

            Trace.beginSection("Accepted Available Image")
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
                        mFrameInts!!)
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

        mFrameCropper.onNewFrame(mFrameInts!!, mImageProcessingRate.fpsRealTime)

        // draw overlays
        mOverlayView?.postInvalidate()

        // close image
        mImageCloser?.run()
    }
}