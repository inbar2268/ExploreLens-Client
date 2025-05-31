package com.example.explorelens.data.db.siteDetails

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface SiteDetailsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSiteDetails(siteDetails: SiteDetailsEntity)

    @Query("SELECT * FROM site_details WHERE id = :siteId")
    fun getSiteDetailsById(siteId: String): LiveData<SiteDetailsEntity?>

    @Query("SELECT * FROM site_details WHERE id = :siteId")
    suspend fun getSiteDetailsByIdSync(siteId: String): SiteDetailsEntity?

    @Query("SELECT * FROM site_details")
    fun getAllSiteDetails(): LiveData<List<SiteDetailsEntity>>

    @Query("SELECT * FROM site_details WHERE id = :siteId")
    fun getSiteDetailsByIdNow(siteId: String): SiteDetailsEntity?

    @Update
    suspend fun updateSiteDetails(siteDetails: SiteDetailsEntity)

    @Delete
    suspend fun deleteSiteDetails(siteDetails: SiteDetailsEntity)

    @Query("DELETE FROM site_details WHERE id = :siteId")
    suspend fun deleteSiteDetailsById(siteId: String)

    @Query("DELETE FROM site_details")
    suspend fun deleteAllSiteDetails()
}