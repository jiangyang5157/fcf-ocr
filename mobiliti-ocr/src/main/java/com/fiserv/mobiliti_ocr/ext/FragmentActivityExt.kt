package com.fiserv.mobiliti_ocr.ext

import android.support.annotation.IdRes
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentTransaction

fun FragmentActivity.addFragmentToActivity(@IdRes containerViewId: Int,
                                           fragment: Fragment, tag:
                                           String? = null) {
    supportFragmentManager.transact {
        add(containerViewId, fragment, tag)
    }
}

fun FragmentActivity.replaceFragmentInActivity(@IdRes containerViewId: Int,
                                               fragment: Fragment, tag:
                                               String? = null) {
    supportFragmentManager.transact {
        replace(containerViewId, fragment, tag)
    }
}

/**
 * Runs a FragmentTransaction, then calls commit().
 */
private inline fun FragmentManager.transact(action: FragmentTransaction.() -> Unit) {
    beginTransaction().apply {
        action()
    }.commit()
}