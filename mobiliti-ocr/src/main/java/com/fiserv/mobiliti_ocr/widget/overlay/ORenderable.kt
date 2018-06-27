package com.fiserv.mobiliti_ocr.widget.overlay

import android.graphics.Canvas
import android.graphics.Paint
import com.fiserv.kit.render.Renderable

interface ORenderable : Renderable<Canvas> {
    var paint: Paint
}
