package com.fiserv.mobiliti_ocr.ui

import android.content.res.Configuration
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.support.v4.app.Fragment
import android.text.TextUtils
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.*
import com.fiserv.kit.ext.cameraManager
import com.fiserv.kit.utils.CompareSizesByArea
import com.fiserv.kit.widget.AutoFitTextureView
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class FrameCamera2Fragment : Fragment() {

    companion object {
        const val TAG = "FrameCamera2Fragment"
        const val TAG_HANDLER_THREAD = "${TAG}_Handler_Thread"

        const val KEY_CAMERA_ID = "KEY_CAMERA_ID"
        const val KEY_DESIRED_SIZE = "KEY_DESIRED_SIZE"
    }

    internal interface ViewSizeListener {
        fun onViewSizeChanged(w: Int, h: Int)
    }

    internal interface PreviewSizeListener {
        fun onPreviewSizeChosen(size: Size, cameraRotation: Int, screenRotation: Int)
    }

    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private val mOrientations = SparseIntArray()

    init {
        mOrientations.append(Surface.ROTATION_0, 90)
        mOrientations.append(Surface.ROTATION_90, 0)
        mOrientations.append(Surface.ROTATION_180, 270)
        mOrientations.append(Surface.ROTATION_270, 180)
    }

    private var mBackgroundHandler: Handler? = null
    private var mBackgroundThread: HandlerThread? = null

    private var mViewSizeListener: ViewSizeListener? = null
    private var mPreviewSizeListener: PreviewSizeListener? = null
    private var mOnImageAvailableListener: ImageReader.OnImageAvailableListener? = null

    private val mCameraOpenCloseLock = Semaphore(1)
    private lateinit var mCameraId: String
    private var mCameraDevice: CameraDevice? = null
    private var mCaptureSession: CameraCaptureSession? = null
    private var mPreviewRequestBuilder: CaptureRequest.Builder? = null
    private var mPreviewRequest: CaptureRequest? = null
    private var mPreviewReader: ImageReader? = null

    private lateinit var mDesiredSize: Size
    private lateinit var mPreviewSize: Size
    private lateinit var mAutoFitTextureView: AutoFitTextureView

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        arguments?.apply {
            mCameraId = getString(KEY_CAMERA_ID)
            mDesiredSize = getSize(KEY_DESIRED_SIZE)
        }

        return AutoFitTextureView(context).apply {
            mAutoFitTextureView = this
        }
    }

    private val mSurfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
            configureTransform(width, height)
            mViewSizeListener?.onViewSizeChanged(width, height)
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
    }

    private val mCaptureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureProgressed(
                session: CameraCaptureSession,
                request: CaptureRequest,
                partialResult: CaptureResult) {
        }

        override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult) {
        }
    }

    /**
     * [android.hardware.camera2.CameraDevice.StateCallback] is called when [CameraDevice] changes its state.
     */
    private val mStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cd: CameraDevice) {
            mCameraOpenCloseLock.release()
            mCameraDevice = cd
            createCameraPreviewSession()
        }

        override fun onDisconnected(cd: CameraDevice) {
            mCameraOpenCloseLock.release()
            cd.close()
            mCameraDevice = null
        }

        override fun onError(cd: CameraDevice, error: Int) {
            Log.d(TAG, "Camera device state error: $error")
            val activity = activity
            mCameraOpenCloseLock.release()
            cd.close()
            mCameraDevice = null
            activity.finish()
        }
    }

    /**
     * Creates a new [CameraCaptureSession] for camera preview.
     */
    private fun createCameraPreviewSession() {
        try {
            val texture = mAutoFitTextureView.surfaceTexture

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.width, mPreviewSize.height)

            // This is the output Surface we need to start preview.
            val surface = Surface(texture)

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder = mCameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            mPreviewRequestBuilder?.addTarget(surface)

            // Create the reader for the preview frames.
            mPreviewReader = ImageReader.newInstance(
                    mPreviewSize.width, mPreviewSize.height, ImageFormat.YUV_420_888, 2)

            mPreviewReader?.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler)
            mPreviewRequestBuilder?.addTarget(mPreviewReader?.surface)

            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice?.createCaptureSession(Arrays.asList(surface, mPreviewReader?.surface),
                    object : CameraCaptureSession.StateCallback() {

                        override fun onConfigured(ccs: CameraCaptureSession) {
                            if (null == mCameraDevice) {
                                Log.d(TAG, "Camera capture session: the camera is already closed.")
                                return
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = ccs
                            try {
                                // Auto focus should be continuous for camera preview.
                                mPreviewRequestBuilder?.set(
                                        CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                // Flash is automatically enabled when necessary.
                                mPreviewRequestBuilder?.set(
                                        CaptureRequest.CONTROL_AE_MODE,
                                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)

                                // Finally, we start displaying the camera preview.
                                mPreviewRequest = mPreviewRequestBuilder?.build()
                                mCaptureSession?.setRepeatingRequest(
                                        mPreviewRequest, mCaptureCallback, mBackgroundHandler)
                            } catch (e: CameraAccessException) {
                                Log.e(TAG, "Camera capture session state error: CameraAccessException $e")
                            }

                        }

                        override fun onConfigureFailed(ccs: CameraCaptureSession) {
                            Log.e(TAG, "Camera capture session state failed: $ccs")
                        }
                    }, null)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Create camera preview session error: CameraAccessException $e")
        }
    }

    /**
     * Opens the camera specified by [CameraFragment.mCameraId].
     */
    private fun openCamera(viewWidth: Int, viewHeight: Int) {
        setUpCameraOutputs()
        configureTransform(viewWidth, viewHeight)
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            activity.cameraManager.openCamera(mCameraId, mStateCallback, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Open camera error: CameraAccessException $e")
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
        }
    }

    /**
     * Closes the current [CameraDevice].
     */
    private fun closeCamera() {
        try {
            mCameraOpenCloseLock.acquire()
            if (null != mCaptureSession) {
                mCaptureSession?.close()
                mCaptureSession = null
            }
            if (null != mCameraDevice) {
                mCameraDevice?.close()
                mCameraDevice = null
            }
            if (null != mPreviewReader) {
                mPreviewReader?.close()
                mPreviewReader = null
            }
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            mCameraOpenCloseLock.release()
        }
    }

    /**
     * Given [choices] of [Size]s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the minimum of [desiredSize], or an exact match the [desiredSize] if possible.
     *
     * @param choices The list of sizes that the camera supports for the intended output class
     * @param desiredSize The desired size to choose
     * @return The optimal [Size], or an arbitrary one if none were big enough
     */
    private fun chooseOptimalSize(choices: Array<Size>, desiredSize: Size): Size {
        val minSize = Math.min(desiredSize.width, desiredSize.height)

        // Collect the supported resolutions that are at least as big as the preview Surface
        var exactSizeFound = false
        val bigEnough = ArrayList<Size>()
        val tooSmall = ArrayList<Size>()
        for (option in choices) {
            if (option == desiredSize) {
                exactSizeFound = true
                // continue to collect logs
            }

            if (option.height >= minSize && option.width >= minSize) {
                bigEnough.add(option)
            } else {
                tooSmall.add(option)
            }
        }
        Log.d(TAG, "Min size: " + minSize + "x" + minSize)
        Log.d(TAG, "Desired size: $desiredSize")
        Log.d(TAG, "Valid preview sizes: [" + TextUtils.join(", ", bigEnough) + "]")
        Log.d(TAG, "Rejected preview sizes: [" + TextUtils.join(", ", tooSmall) + "]")

        return when {
            exactSizeFound -> {
                Log.d(TAG, "Exact size match found: $desiredSize")
                desiredSize
            }
            bigEnough.size > 0 -> {
                val chosenSize = Collections.min(bigEnough, CompareSizesByArea())
                Log.d(TAG, "Chosen the smallest valid size: $chosenSize")
                chosenSize
            }
            else -> {
                val chosenSize = choices[0]
                Log.d(TAG, "Couldn't find any suitable preview size matches the desired size, chosen: $chosenSize")
                chosenSize
            }
        }
    }

    /**
     * Sets up member variables related to camera.
     */
    private fun setUpCameraOutputs() {
        try {
            val characteristics = activity.cameraManager.getCameraCharacteristics(mCameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture::class.java), mDesiredSize)
            mPreviewSize.apply {
                if (getConfigOrientation() == Configuration.ORIENTATION_LANDSCAPE) {
                    mAutoFitTextureView.setAspectRatio(width, height)
                } else {
                    mAutoFitTextureView.setAspectRatio(height, width)
                }
                mPreviewSizeListener?.onPreviewSizeChosen(
                        this, getCameraRotation(characteristics), getScreenRotation())
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Set up camera outputs error: CameraAccessException $e")
            activity.finish()
        } catch (e: NullPointerException) {
            Log.e(TAG, "Set up camera outputs error: This device doesn't support Camera2 API.")
            activity.finish()
        }
    }

    /**
     * Configures the necessary [android.graphics.Matrix] transformation to [TextureView].
     * This method should be called after the camera preview size is determined in setUpCameraOutputs,
     * and also the size of [TextureView] is fixed.
     *
     * @param viewWidth  The width of [TextureView]
     * @param viewHeight The height of [TextureView]
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        val bufferRect = RectF(0f, 0f, mPreviewSize.height.toFloat(), mPreviewSize.width.toFloat())
        val screenOrientation = getScreenOrientation()

        if (Surface.ROTATION_90 == screenOrientation || Surface.ROTATION_270 == screenOrientation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = Math.max(
                    viewHeight.toFloat() / mPreviewSize.height,
                    viewWidth.toFloat() / mPreviewSize.width)
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((90 * (screenOrientation - 2)).toFloat(), centerX, centerY)
        } else if (Surface.ROTATION_180 == screenOrientation) {
            matrix.postRotate(180f, centerX, centerY)
        }

        mAutoFitTextureView.setTransform(matrix)
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

    override fun onResume() {
        super.onResume()
        startBackgroundThread()

        // When the screen is turned off and turned back on,
        // the SurfaceTexture is already available, and "onSurfaceTextureAvailable" will not be called.
        // In that case, we can open a camera and start preview from here.
        // (otherwise, we wait until the surface is ready in the SurfaceTextureListener)
        mAutoFitTextureView.apply {
            if (isAvailable) {
                openCamera(width, height)
            } else {
                surfaceTextureListener = mSurfaceTextureListener
            }
        }
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    fun getConfigOrientation(): Int {
        return resources.configuration.orientation
    }

    fun getCameraRotation(cameraCharacteristics: CameraCharacteristics): Int {
        return cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
    }

    fun getScreenOrientation(): Int {
        return activity.windowManager.defaultDisplay.rotation
    }

    fun getScreenRotation(): Int {
        return when (getScreenOrientation()) {
            Surface.ROTATION_270 -> 270
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_0 -> 0
            else -> 0
        }
    }

    fun getCameraId(): String {
        return mCameraId
    }

    internal fun setViewSizeListener(viewSizeListener: ViewSizeListener) {
        mViewSizeListener = viewSizeListener
    }

    internal fun setPreviewSizeListener(previewSizeListener: PreviewSizeListener) {
        mPreviewSizeListener = previewSizeListener
    }

    fun setOnImageAvailableListener(onImageAvailableListener: ImageReader.OnImageAvailableListener) {
        mOnImageAvailableListener = onImageAvailableListener
    }

}