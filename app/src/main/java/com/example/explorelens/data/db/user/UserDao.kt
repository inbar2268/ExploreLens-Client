package com.example.explorelens.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUser(user: User)

    @Query("SELECT * FROM user LIMIT 1")
    suspend fun getUser(): User?

    @Query("SELECT * FROM user WHERE id = :userId")
    suspend fun getUserById(userId: String): User?

    @Query("SELECT * FROM user WHERE id = :userId")
    fun observeUserById(userId: String): Flow<User?>

    @Query("SELECT * FROM user LIMIT 1")
    fun observeUser(): Flow<User?>

    @Query("DELETE FROM user WHERE id = :userId")
    suspend fun deleteUser(userId: String)


}
