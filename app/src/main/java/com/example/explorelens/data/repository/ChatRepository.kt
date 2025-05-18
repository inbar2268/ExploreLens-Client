package com.example.explorelens.data.repository

import com.example.explorelens.data.network.ExploreLensApiClient
import com.example.explorelens.data.model.chat.ChatCompletionRequest

/**
 * Repository for ChatGPT interactions.
 * @param apiKey your OpenAI API key (e.g., BuildConfig.OPENAI_API_KEY)
 */
class ChatRepository(private val apiKey: String) {

    /**
     * Sends a user message with the given history and returns the assistant's reply.
     * @param history the list of prior messages (role = "system"/"user"/"assistant").
     * @param newMessage the latest user prompt to send.
     * @return Result wrapping the assistant's reply as a String.
     */
    suspend fun sendMessage(
        history: List<ChatCompletionRequest.Message>,
        newMessage: String
    ): Result<String> {
        return try {
            val request = ChatCompletionRequest(
                model = "gpt-3.5-turbo",
                messages = history + ChatCompletionRequest.Message(role = "user", content = newMessage)
            )

            val response = ExploreLensApiClient.chatApi
                .createChatCompletion("Bearer $apiKey", request)

            if (response.isSuccessful) {
                val body = response.body()
                val content = body?.choices?.firstOrNull()?.message?.content
                if (!content.isNullOrEmpty()) {
                    Result.success(content)
                } else {
                    Result.failure(Exception("Empty assistant response"))
                }
            } else {
                val error = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("Error ${response.code()}: $error"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.localizedMessage}"))
        }
    }
}