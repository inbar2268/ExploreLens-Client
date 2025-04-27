//package com.example.explorelens.utils
//
//import android.Manifest
//import android.content.Context
//import android.content.pm.PackageManager
//import android.location.Location
//import android.os.Looper
//import androidx.core.app.ActivityCompat
//import com.google.android.gms.location.FusedLocationProviderClient
//import com.google.android.gms.location.LocationCallback
//import com.google.android.gms.location.LocationRequest
//import com.google.android.gms.location.LocationResult
//import com.google.android.gms.location.LocationServices
//import kotlinx.coroutines.channels.awaitClose
//import kotlinx.coroutines.flow.Flow
//import kotlinx.coroutines.flow.callbackFlow
//import kotlinx.coroutines.tasks.await
//
//class GeoHashUtils(private val context: Context) {
//
//    private val fusedLocationClient: FusedLocationProviderClient =
//        LocationServices.getFusedLocationProviderClient(context)
//
//    fun getCurrentLocation(): Flow<Location?> = callbackFlow {
//        if (ActivityCompat.checkSelfPermission(
//                context,
//                Manifest.permission.ACCESS_FINE_LOCATION
//            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
//                context,
//                Manifest.permission.ACCESS_COARSE_LOCATION
//            ) != PackageManager.PERMISSION_GRANTED
//        ) {
//            trySend(null) // Or handle permission denial as needed
//            close()
//            return@callbackFlow
//        }
//
//        val locationRequest = LocationRequest.create().apply {
//            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
//            interval = 10000 // Update interval in milliseconds
//            fastestInterval = 5000 // Fastest update interval
//        }
//
//        val locationCallback = object : LocationCallback() {
//            override fun onLocationResult(locationResult: LocationResult) {
//                locationResult.lastLocation?.let { location ->
//                    trySend(location).isSuccess
//                }
//            }
//        }
//
//        fusedLocationClient.requestLocationUpdates(
//            locationRequest,
//            locationCallback,
//            Looper.getMainLooper()
//        )
//
//        awaitClose {
//            fusedLocationClient.removeLocationUpdates(locationCallback)
//        }
//    }
//
//    suspend fun getSingleCurrentLocation(): Location? {
//        return try {
//            if (ActivityCompat.checkSelfPermission(
//                    context,
//                    Manifest.permission.ACCESS_FINE_LOCATION
//                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
//                    context,
//                    Manifest.permission.ACCESS_COARSE_LOCATION
//                ) != PackageManager.PERMISSION_GRANTED
//            ) {
//                return null // Or handle permission denial
//            }
//            val locationResult = fusedLocationClient.lastLocation.await()
//            locationResult
//        } catch (e: Exception) {
//            e.printStackTrace()
//            null
//        }
//    }
//}