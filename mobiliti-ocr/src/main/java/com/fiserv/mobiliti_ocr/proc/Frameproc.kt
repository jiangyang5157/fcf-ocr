package com.fiserv.mobiliti_ocr.proc

import android.util.Size
import com.fiserv.mobiliti_ocr.ui.FrameCameraActivity

class Frameproc : FrameCameraActivity.FrameCropper {

    companion object {
        const val TAG = "Frameproc"

        const val DEFAULT_CROPPED_WIDTH = 320
        const val DEFAULT_CROPPED_HEIGHT = 320
    }

    override fun getCroppedSize(previewWidth: Int, previewHeight: Int): Size {
        return Size(DEFAULT_CROPPED_WIDTH, DEFAULT_CROPPED_HEIGHT)
    }

}