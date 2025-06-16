package com.example.explorelens.data.db.places

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface PlaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlace(place: Place)

    @Query("SELECT * FROM places WHERE placeId = :placeId")
    fun getPlace(placeId: String): LiveData<Place?>

    @Query("SELECT * FROM places WHERE placeId = :placeId")
    suspend fun getPlaceSync(placeId: String): Place?

    @Query("SELECT * FROM places")
    fun getAllPlaces(): LiveData<List<Place>>

    @Query("DELETE FROM places WHERE placeId = :placeId")
    suspend fun deletePlace(placeId: String)

    @Query("DELETE FROM places WHERE lastUpdated < :timestamp")
    suspend fun deleteOldPlaces(timestamp: Long)

    @Query("SELECT COUNT(*) FROM places WHERE placeId = :placeId")
    suspend fun placeExists(placeId: String): Int
}
