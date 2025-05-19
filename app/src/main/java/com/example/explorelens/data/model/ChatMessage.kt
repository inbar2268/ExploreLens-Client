package com.example.explorelens.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Represents a message in the chat conversation
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val message: String,
    val sentByUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Returns the formatted time of the message
     */
    fun getFormattedTime(): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}