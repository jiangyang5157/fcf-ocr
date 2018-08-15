package com.fiserv.mobiliti_ocr.proc

import com.fiserv.kit.Mapper
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

class MatScale(val scale: Double) : Mapper<Mat, Mat> {

    override fun map(from: Mat): Mat {
        return Mat().apply {
            Imgproc.resize(from, this, org.opencv.core.Size(from.width() * scale, from.height() * scale))
        }
    }

}

