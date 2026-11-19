package com.audiobook.player

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val textView = TextView(this).apply {
            text = "Плеер аудиокниг готов к работе!"
            textSize = 22f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#121212"))
            gravity = Gravity.CENTER
        }
        setContentView(textView)
    }
    
}
