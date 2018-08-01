package com.gmail.jiangyang5157.sudoku.widget.scan.imgproc

import com.fiserv.kit.Converter
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

object Gray2Rgb : Converter<Mat, Mat> {

    override fun convert(src: Mat, dst: Mat) {
        Imgproc.cvtColor(src, dst, Imgproc.COLOR_GRAY2RGB)
    }

}