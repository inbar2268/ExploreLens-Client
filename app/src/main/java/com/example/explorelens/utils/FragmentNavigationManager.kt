package com.example.explorelens.utils

import com.example.explorelens.R

object FragmentNavigationManager {
    private var lastFragmentId: Int? = null
    private val loginFragmentIds = setOf(
        R.id.loginFragment,
        R.id.registerFragment
    )

    private val DEFAULT_FRAGMENT_ID = R.id.profileFragment

    fun setCurrentFragmentId(fragmentId: Int) {
        lastFragmentId = fragmentId
    }

    fun getLastFragmentId(): Int {
        return if (lastFragmentId != null && !loginFragmentIds.contains(lastFragmentId)) {
            lastFragmentId!!
        } else {
            DEFAULT_FRAGMENT_ID
        }
    }
}