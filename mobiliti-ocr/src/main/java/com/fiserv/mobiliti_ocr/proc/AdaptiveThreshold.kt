package com.gmail.jiangyang5157.sudoku.widget.scan.imgproc

import com.fiserv.kit.Converter
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

data class AdaptiveThreshold(var maxValue: Double,
                             var adaptiveMethod: Int,
                             var thresholdType: Int,
                             var blockSize: Int,
                             var c: Double)
    : Converter<Mat, Mat> {

    override fun convert(src: Mat, dst: Mat) {
        Imgproc.adaptiveThreshold(src, dst, maxValue, adaptiveMethod, thresholdType, blockSize, c)
    }

}