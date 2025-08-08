package com.example.explorelens.data.db.siteHistory

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface SiteHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSiteHistory(siteHistory: SiteHistory)

    @Query("SELECT * FROM site_history WHERE userId = :userId ORDER BY createdAt DESC")
    fun getSiteHistoryByUserId(userId: String): LiveData<List<SiteHistory>>

    @Query("SELECT * FROM site_history")
    fun getSiteHistory(): LiveData<List<SiteHistory>>

    @Delete
    suspend fun deleteSiteHistory(siteHistory: SiteHistory)

    @Update
    suspend fun updateSiteHistory(siteHistory: SiteHistory)

    @Query("SELECT * FROM site_history WHERE userId = :userId")
    suspend fun getSiteHistoryByUserIdSync(userId: String): List<SiteHistory>

    @Query("SELECT COUNT(*) FROM site_history WHERE userId = :userId")
    suspend fun getSiteHistoryCountForUser(userId: String): Int

    @Query("DELETE FROM site_history WHERE userId = :userId")
    suspend fun clearSiteHistoryForUser(userId: String)
}