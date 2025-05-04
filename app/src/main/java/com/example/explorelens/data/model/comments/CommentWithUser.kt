package com.example.explorelens.data.model.comments

import com.example.explorelens.data.db.User

data class CommentWithUser(
    val comment: Comment,
    val user: User? = null
) {
}