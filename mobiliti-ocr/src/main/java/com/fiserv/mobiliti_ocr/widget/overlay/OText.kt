package com.fiserv.mobiliti_ocr.widget.overlay

import android.graphics.Canvas
import android.graphics.Paint

class OText(size: Float, color: Int, override var x: Float = 0.0f, override var y: Float = 0.0f)
    : OParticle {

    override var paint = Paint().apply {
        this.textSize = size
        this.color = color
        this.style = Paint.Style.FILL
        this.isAntiAlias = false
    }

    var lines = arrayListOf<String>()

    override fun onRender(t: Canvas) {
        lines.forEachIndexed { i, line ->
            t.drawText(line, x, y - paint.textSize * (lines.size - i - 1), paint)
        }
    }

}