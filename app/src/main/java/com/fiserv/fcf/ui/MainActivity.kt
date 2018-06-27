package com.fiserv.fcf.ui

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import com.fiserv.fcf.R
import com.fiserv.kit.ext.instance
import com.fiserv.kit.ext.replaceFragmentInActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.container_activity)

        if (savedInstanceState == null) {
            replaceFragmentInActivity(R.id.activity_container, instance<MainFragment>())
        }
    }
}
