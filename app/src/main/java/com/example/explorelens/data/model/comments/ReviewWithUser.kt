package com.example.explorelens.data.model.comments

import com.example.explorelens.data.db.user.UserEntity

data class ReviewWithUser(
    val review: Review,
    val user: UserEntity? = null
) {
}