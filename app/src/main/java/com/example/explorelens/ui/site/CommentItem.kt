package com.example.explorelens.ui.site

/**
 * Data class representing a comment
 */

data class CommentItem(
    val user: String,
    val content: String,
    val date: String?,
    val profilePicUrl: String? = null
)