package com.fiserv.mobiliti_ocr.proc

import com.fiserv.kit.Mapper
import com.gmail.jiangyang5157.sudoku.widget.scan.imgproc.Gray2Rgb
import org.opencv.core.CvType
import org.opencv.core.Mat
import java.nio.ByteBuffer

class ByteBuffer2Rgb(val w: Int, private val h: Int) : Mapper<Array<ByteBuffer?>, Mat> {

    override fun map(from: Array<ByteBuffer?>): Mat {
        return Mat().apply {
            val yuvFrameData = Mat(h, w, CvType.CV_8UC1, from[0])
            val gray = yuvFrameData.submat(0, h, 0, w)
            Gray2Rgb.convert(gray, this)
            yuvFrameData.release()
            gray.release()
        }
    }

}


