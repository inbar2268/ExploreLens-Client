package com.example.explorelens.data.db.user

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUser(user: UserEntity)

    @Query("SELECT * FROM user LIMIT 1")
    suspend fun getUser(): UserEntity?

    @Query("SELECT * FROM user WHERE id = :userId")
    suspend fun getUserById(userId: String): UserEntity?

    @Query("SELECT * FROM user WHERE id = :userId")
    fun observeUserById(userId: String): Flow<UserEntity?>

    @Query("SELECT * FROM user LIMIT 1")
    fun observeUser(): Flow<UserEntity?>

    @Query("DELETE FROM user WHERE id = :userId")
    suspend fun deleteUser(userId: String)


}
