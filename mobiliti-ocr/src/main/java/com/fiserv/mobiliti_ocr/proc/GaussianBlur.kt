package com.gmail.jiangyang5157.sudoku.widget.scan.imgproc

import com.fiserv.kit.Converter
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

data class GaussianBlur(var kWidth: Double,
                        var kHeight: Double,
                        var sigmaX: Double,
                        var sigmaY: Double,
                        var boardType: Int)
    : Converter<Mat, Mat> {

    override fun convert(src: Mat, dst: Mat) {
        Imgproc.GaussianBlur(src, dst, Size(kWidth, kHeight), sigmaX, sigmaY, boardType)
    }

}