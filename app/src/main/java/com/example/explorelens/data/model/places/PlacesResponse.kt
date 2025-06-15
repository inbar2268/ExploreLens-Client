package com.example.explorelens.data.model.places

import com.google.gson.annotations.SerializedName

data class PlaceResponse(
    @SerializedName("place_id")
    val placeId: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("location")
    val location: LocationResponse,

    @SerializedName("rating")
    val rating: Float,

    @SerializedName("type")
    val type: String,

    @SerializedName("editorial_summary")
    val editorialSummary: String? = null,

    @SerializedName("website")
    val website: String? = null,

    @SerializedName("price_level")
    val priceLevel: Int? = null,

    @SerializedName("elevation")
    val elevation: Double? = null,

    @SerializedName("address")
    val address: String? = null,

    @SerializedName("phone_number")
    val phoneNumber: String? = null,

    @SerializedName("business_status")
    val businessStatus: String? = null,

    @SerializedName("opening_hours")
    val openingHours: OpeningHoursResponse? = null,

    @SerializedName("reviews")
    val reviews: List<ReviewResponse>? = null
)

data class LocationResponse(
    @SerializedName("lat")
    val lat: Double,

    @SerializedName("lng")
    val lng: Double
)

data class OpeningHoursResponse(
    @SerializedName("open_now")
    val openNow: Boolean? = null,

    @SerializedName("weekday_text")
    val weekdayText: List<String>? = null
)

data class ReviewResponse(
    @SerializedName("author_name")
    val authorName: String,

    @SerializedName("rating")
    val rating: Float,

    @SerializedName("relative_time_description")
    val relativeTimeDescription: String,

    @SerializedName("text")
    val text: String,

    @SerializedName("time")
    val time: Long
)