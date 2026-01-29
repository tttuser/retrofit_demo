package com.example.retrofitdemo.day8

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.retrofit.RetrofitMethodCacheInstrumentationTest
import com.example.retrofitdemo.R

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val r = RetrofitMethodCacheInstrumentationTest()
        this.findViewById<Button>(R.id.button1).apply {
            setOnClickListener {
                r.cold()
            }
        }
        this.findViewById<Button>(R.id.button2).apply {
            setOnClickListener {
                r.warm()
            }
        }
    }
}