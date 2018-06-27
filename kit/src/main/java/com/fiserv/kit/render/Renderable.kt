package com.fiserv.kit.render

interface Renderable<in T> {

    fun onRender(t: T)

}