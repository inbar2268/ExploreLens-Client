package com.example.explorelens

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.explorelens.ml.R
import com.example.explorelens.ui.theme.ExploreLensTheme

class DetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        // Set label to TextView
        val label = intent.getStringExtra("AR_LABEL") ?: "Unknown"
        val textView = findViewById<TextView>(R.id.labelTextView)
        textView.text = label
    }
}
