package com.example.explorelens.data.db.statistics

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface UserStatisticsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserStatistics(userStatistics: UserStatisticsEntity)

    @Query("SELECT * FROM user_statistics WHERE userId = :userId")
    fun getUserStatistics(userId: String): LiveData<UserStatisticsEntity?>

    @Query("SELECT * FROM user_statistics WHERE userId = :userId")
    suspend fun getUserStatisticsSync(userId: String): UserStatisticsEntity?

    @Delete
    suspend fun deleteUserStatistics(userStatistics: UserStatisticsEntity)

    @Query("DELETE FROM user_statistics WHERE userId = :userId")
    suspend fun deleteUserStatisticsByUserId(userId: String)
}