package com.fiserv.mobiliti_ocr.proc

import com.fiserv.kit.Converter
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

data class Canny(var threshold1: Double,
                 var threshold2: Double,
                 var apertureSize: Int,
                 var l2gradient: Boolean)
    : Converter<Mat, Mat> {

    override fun convert(src: Mat, dst: Mat) {
        Imgproc.Canny(src, dst, threshold1, threshold2, apertureSize, l2gradient)
    }

}