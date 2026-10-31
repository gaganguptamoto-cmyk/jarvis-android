package com.jarvis.assistant

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        tv.text = "JARVIS online"
        tv.textSize = 28f
        tv.setTextColor(Color.CYAN)
        tv.setBackgroundColor(Color.BLACK)
        tv.gravity = Gravity.CENTER
        setContentView(tv)
    }
}
