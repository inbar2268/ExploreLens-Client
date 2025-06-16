package com.example.explorelens.data.db

import androidx.room.TypeConverter
import com.example.explorelens.data.db.places.Review
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return if (value == null) null else gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return if (value == null) null else {
            val listType = object : TypeToken<List<String>>() {}.type
            gson.fromJson(value, listType)
        }
    }

    @TypeConverter
    fun fromReviewList(value: List<Review>?): String? {
        return if (value == null) null else gson.toJson(value)
    }

    @TypeConverter
    fun toReviewList(value: String?): List<Review>? {
        return if (value == null) null else {
            val listType = object : TypeToken<List<Review>>() {}.type
            gson.fromJson(value, listType)
        }
    }
}