package com.example.explorelens.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChatMessage(
    val id: String,
    val message: String,
    val sentByUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getFormattedTime(): String {
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }
}