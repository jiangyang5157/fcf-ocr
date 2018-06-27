package com.fiserv.kit.utils

object TimeUnit {

    const val MILLI_IN_SECOND: Long = 1000L

    const val MICRO_IN_SECOND: Long = MILLI_IN_SECOND * 1000L

    const val NANO_IN_SECOND: Long = MICRO_IN_SECOND * 1000L

    const val MULTIPLICATOR_NANO_TO_MICRO: Double = 0.001

    const val MULTIPLICATOR_NANO_TO_MILLI: Double = 0.000001

    const val MULTIPLICATOR_NANO_TO_SECOND: Double = 0.000000001

    const val MILLI_IN_MINUTE: Long = 60 * MILLI_IN_SECOND

    const val MILLI_IN_HOUR: Long = 60 * MILLI_IN_MINUTE

    const val MILLI_IN_DAY: Long = 24 * MILLI_IN_HOUR

    fun nanoToMilli(time: Long): Double = time * MULTIPLICATOR_NANO_TO_MILLI

}
