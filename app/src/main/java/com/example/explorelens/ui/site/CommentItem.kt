package com.example.explorelens.ui.site

/**
 * Data class representing a comment
 */

data class CommentItem(
    val username: String,
    val text: String,
    val profilePicUrl: String? = null
)