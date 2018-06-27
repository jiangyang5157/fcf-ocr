package com.fiserv.kit.utils

import android.os.Build
import android.support.annotation.RequiresApi
import android.util.Size
import java.lang.Long.signum

class CompareSizesByArea : Comparator<Size> {

    // We cast here to ensure the multiplications won't overflow
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun compare(lhs: Size, rhs: Size) =
            signum(lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height)

}