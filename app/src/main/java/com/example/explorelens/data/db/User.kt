package com.example.explorelens.data.db
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user")
data class User(
    @PrimaryKey val id: String,
    val username: String,
    val email: String,
    val profilePictureUrl: String?
)