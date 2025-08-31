package com.example.explorelens.data.model.comments

import com.example.explorelens.data.db.user.User

data class ReviewWithUser(
    val review: Review,
    val user: User? = null
) {
}