package com.example.explorelens.ar.render
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.CopyOnWriteArrayList

object FilterListManager {

    private val filters = CopyOnWriteArrayList<String>()

    private val _filtersLiveData = MutableLiveData<List<String>>()
    private val listeners = mutableListOf<() -> Unit>()

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
        notifyListeners()
    }

    fun addFilter(filter: String) {
        if (!filters.contains(filter)) {
            filters.add(filter)
            _filtersLiveData.postValue(filters.toList())
            notifyListeners()
        }
    }

    fun removeFilter(filter: String) {
        val removed = filters.remove(filter)
        if (removed) {
            _filtersLiveData.postValue(filters.toList())
            notifyListeners()
        }
    }

    fun clearAll() {
        filters.clear()
        _filtersLiveData.postValue(emptyList())
        notifyListeners()
    }
    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { it.invoke() }
    }
}
