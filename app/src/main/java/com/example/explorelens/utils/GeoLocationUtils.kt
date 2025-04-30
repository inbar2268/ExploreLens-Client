package com.example.explorelens.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.app.ActivityCompat
import ch.hsr.geohash.GeoHash
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.Executors // Import for Executor

class GeoLocationUtils(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var userLatitude: Double? = null
    private var userLongitude: Double? = null
    private var geoHash: String? = null

    fun getCurrentLocationFlow(): Flow<Location?> = callbackFlow {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 10000
            fastestInterval = 5000
        }

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    trySend(location).isSuccess
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        awaitClose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    suspend fun getSingleCurrentLocation(): Location? {
        return try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return null
            }
            fusedLocationClient.lastLocation.await()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getCurrentLocation(): Location? {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        return try {
            fusedLocationClient.lastLocation.await()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun requestNewLocationData(): Location? {
        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 5000
            fastestInterval = 2000
            numUpdates = 1
        }

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        return try {
            // Use a LocationCallback and an Executor.
            val executor = Executors.newSingleThreadExecutor() // Create an Executor
            val locationResult =  fusedLocationClient.requestLocationUpdates(locationRequest, executor, object : LocationCallback() {
                override fun onLocationResult(p0: LocationResult) {
                    //  I moved the return logic here.
                    executor.shutdown() // shutdown the executor.
                }
            }).await()

            fusedLocationClient.lastLocation.await()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            // It's important to remove updates if you requested a specific number of updates
            // However, 'requestLocationUpdates' with await might behave differently.
            // Consider using a callback-based approach if fine-grained control is needed.
        }
    }

    fun updateLocation(location: Location) {
        userLatitude = location.latitude
        userLongitude = location.longitude
        val geoHashObj = GeoHash.geoHashStringWithCharacterPrecision(userLatitude!!, userLongitude!!, 9) // Adjust precision
        geoHash = geoHashObj    }

    fun getUserLatitude(): Double? = userLatitude
    fun getUserLongitude(): Double? = userLongitude
    fun getGeoHash(): String? = geoHash
}
