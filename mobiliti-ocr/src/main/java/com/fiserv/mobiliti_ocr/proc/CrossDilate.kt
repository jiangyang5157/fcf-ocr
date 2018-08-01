package com.gmail.jiangyang5157.sudoku.widget.scan.imgproc

import com.fiserv.kit.Converter
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

data class CrossDilate(var dilationSize: Double)
    : Converter<Mat, Mat> {

    private fun buildKernel(): Mat {
        return Imgproc.getStructuringElement(
                Imgproc.MORPH_CROSS,
                Size(2 * dilationSize + 1, 2 * dilationSize + 1),
                Point(dilationSize, dilationSize))
    }

    override fun convert(src: Mat, dst: Mat) {
        val kernel = buildKernel()
        Imgproc.dilate(src, dst, kernel)
        kernel.release()
    }

}