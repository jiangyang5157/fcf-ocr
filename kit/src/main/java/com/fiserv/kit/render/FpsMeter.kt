package com.fiserv.kit.render

import com.fiserv.kit.utils.TimeUnit

class FpsMeter(fps: Int = 0) {

    // nano per frame
    private val npf: Long = if (fps <= 0) 0 else TimeUnit.NANO_IN_SECOND / fps

    private var lastTime: Long = 0

    private var npfRealTime: Long = 0

    val fpsRealTime: Int
        get() = Math.round(TimeUnit.NANO_IN_SECOND / npfRealTime.toDouble()).toInt()

    fun accept(): Boolean {
        var ret = false
        val thisTime = System.nanoTime()
        val elapsedTime = thisTime - lastTime
        if (npf <= 0 || elapsedTime >= npf) {
            lastTime = thisTime
            npfRealTime = elapsedTime
            ret = true
        }

        return ret
    }

}
