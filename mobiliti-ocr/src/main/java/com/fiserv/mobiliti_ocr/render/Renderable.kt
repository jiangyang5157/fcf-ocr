package com.fiserv.mobiliti_ocr.render

interface Renderable<in T> {

    fun onRender(t: T)

}