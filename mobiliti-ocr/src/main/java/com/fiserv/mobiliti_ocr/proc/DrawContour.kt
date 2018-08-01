package com.gmail.jiangyang5157.sudoku.widget.scan.imgproc

import android.graphics.Color
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

data class DrawContour(var color: Int, val thickness: Int) {

    private val scalar: Scalar
        get() = Scalar(Color.red(color).toDouble(), Color.green(color).toDouble(), Color.blue(color).toDouble())

    fun draw(src: Mat, contour: MatOfPoint) {
        Imgproc.drawContours(src, listOf(contour), 0, scalar, thickness)
    }

}