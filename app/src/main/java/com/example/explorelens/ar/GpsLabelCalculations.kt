package com.example.explorelens.ar

  public fun calculateHeadingQuaternion(heading: Double): FloatArray {
    val headingRadians = Math.toRadians(heading)
    return floatArrayOf(
        0f,
        kotlin.math.sin(headingRadians.toFloat() / 2),
        0f,
        kotlin.math.cos(headingRadians.toFloat() / 2)
    )
}

public fun computeBearing(
    fromLat: Double, fromLng: Double,
    toLat: Double, toLng: Double
): Double {
    val fromLatRad = Math.toRadians(fromLat)
    val fromLngRad = Math.toRadians(fromLng)
    val toLatRad = Math.toRadians(toLat)
    val toLngRad = Math.toRadians(toLng)

    val dLng = toLngRad - fromLngRad
    val y = Math.sin(dLng) * Math.cos(toLatRad)
    val x = Math.cos(fromLatRad) * Math.sin(toLatRad) -
            Math.sin(fromLatRad) * Math.cos(toLatRad) * Math.cos(dLng)

    return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0
}