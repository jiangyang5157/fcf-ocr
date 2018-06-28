package com.fiserv.fcf.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.design.widget.TextInputEditText
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.fiserv.fcf.R
import com.fiserv.mobiliti_ocr.ui.FrameCameraActivity

class MainFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view?.apply {
            findViewById(R.id.btn_scan)?.setOnClickListener {
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.CAMERA), 1)
                } else {
                    val desiredPreviewWidth = (findViewById(R.id.et_desired_preview_width) as TextInputEditText).text.trim().toString()
                            .toInt()
                    val desiredPreviewHeight = (findViewById(R.id.et_desired_preview_height) as TextInputEditText).text.trim().toString()
                            .toInt()
                    val croppedWidth = (findViewById(R.id.et_cropped_width) as TextInputEditText).text.trim().toString()
                            .toInt()
                    val croppedHeight = (findViewById(R.id.et_cropped_height) as TextInputEditText).text.trim().toString()
                            .toInt()
                    activity.startActivity(Intent(context, FrameCameraActivity::class.java).apply {
                        putExtra(FrameCameraActivity.KEY_CAMERA_DESIRED_WIDTH, desiredPreviewWidth)
                        putExtra(FrameCameraActivity.KEY_CAMERA_DESIRED_HEIGHT, desiredPreviewHeight)
                        putExtra(FrameCameraActivity.KEY_CROPPED_WIDTH, croppedWidth)
                        putExtra(FrameCameraActivity.KEY_CROPPED_HEIGHT, croppedHeight)
                    })
                }
            }
        }
    }

}