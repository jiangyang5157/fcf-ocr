package com.fiserv.mobiliti_ocr.ext

import android.os.Bundle
import android.support.v4.app.Fragment

inline fun <reified T : Fragment> instance(args: Bundle? = null): Fragment = T::class.java.newInstance().apply { arguments = args }
