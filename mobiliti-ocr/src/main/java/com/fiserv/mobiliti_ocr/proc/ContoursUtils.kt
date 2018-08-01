package com.gmail.jiangyang5157.sudoku.widget.scan.imgproc

import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.imgproc.Imgproc

//class ContoursUtils {
//
//    fun findExternals(src: Mat, dst: MutableList<MatOfPoint>) {
//        val hierarchy = Mat()
//        Imgproc.findContours(src, dst, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
//        hierarchy.release()
//    }
//
//    fun sortByDescendingArea(contours: List<MatOfPoint>): MutableList<MatOfPoint> {
//        return contours.sortedByDescending { Imgproc.contourArea(it) }.toMutableList()
//    }
//
//    fun findIndexOfMaxArea(contours: List<MatOfPoint>): Int {
//        var maxArea = 0.0
//        var indexOfMaxArea = -1
//        contours.forEachIndexed { i, contour ->
//            Imgproc.contourArea(contour).apply {
//                if (this > maxArea) {
//                    maxArea = this
//                    indexOfMaxArea = i
//                }
//            }
//        }
//        return indexOfMaxArea
//    }
//
//}