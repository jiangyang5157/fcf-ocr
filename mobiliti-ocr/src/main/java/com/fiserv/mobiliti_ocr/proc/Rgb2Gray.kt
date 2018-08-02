package com.fiserv.mobiliti_ocr.proc

import com.fiserv.kit.Converter
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

object Rgb2Gray : Converter<Mat, Mat> {

    override fun convert(src: Mat, dst: Mat) {
        Imgproc.cvtColor(src, dst, Imgproc.COLOR_RGB2GRAY)
    }

}