package com.example.explorelens.data.model.siteHistory


data class FilterOption(
    val name: String,
    var isChecked: Boolean = false,
    val iconResId: Int? = null // Add a property to hold the icon resource ID
)