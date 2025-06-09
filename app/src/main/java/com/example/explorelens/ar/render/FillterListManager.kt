package com.example.explorelens.ar.render
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.CopyOnWriteArrayList

object FilterListManager {

    private val filters = CopyOnWriteArrayList<String>()

    private val _filtersLiveData = MutableLiveData<List<String>>()
    val filtersLiveData: LiveData<List<String>> = _filtersLiveData

    init {
        _filtersLiveData.postValue(emptyList())
    }

    fun getAllFilters(): List<String> {
        return filters.toList()
    }

    fun setFilters(newFilters: List<String>) {
        filters.clear()
        filters.addAll(newFilters.distinct())
        _filtersLiveData.postValue(filters.toList())
    }

    fun addFilter(filter: String) {
        if (!filters.contains(filter)) {
            filters.add(filter)
            _filtersLiveData.postValue(filters.toList())
        }
    }

    fun removeFilter(filter: String) {
        val removed = filters.remove(filter)
        if (removed) {
            _filtersLiveData.postValue(filters.toList())
        }
    }

    fun clearAll() {
        filters.clear()
        _filtersLiveData.postValue(emptyList())
    }
}
