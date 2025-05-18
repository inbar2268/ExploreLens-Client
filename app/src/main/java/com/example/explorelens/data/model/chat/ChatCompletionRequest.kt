package com.example.explorelens.data.model.chat

data class ChatCompletionRequest(
    val model: String,
    val messages: List<Message>
) {
    data class Message(
        val role: String,    // "system", "user" or "assistant"
        val content: String
    )
}