package com.example.explorelens.data.model.chat

data class ChatCompletionResponse(
    val id: String,
    val choices: List<Choice>
) {
    data class Choice(
        val message: ChatCompletionRequest.Message
    )
}