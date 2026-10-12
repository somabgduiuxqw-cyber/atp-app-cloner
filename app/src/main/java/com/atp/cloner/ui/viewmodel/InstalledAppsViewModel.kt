package com.atp.cloner.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atp.cloner.data.model.InstalledAppInfo
import com.atp.cloner.engine.InstalledAppsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class InstalledAppsUiState {
    object Loading : InstalledAppsUiState()
    data class Success(val apps: List<InstalledAppInfo>) : InstalledAppsUiState()
    object Empty : InstalledAppsUiState()
    data class Error(val message: String) : InstalledAppsUiState()
}

class InstalledAppsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = InstalledAppsRepository(application)

    private val _uiState = MutableStateFlow<InstalledAppsUiState>(InstalledAppsUiState.Loading)
    val uiState: StateFlow<InstalledAppsUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _includeSystemApps = MutableStateFlow(false)
    val includeSystemApps: StateFlow<Boolean> = _includeSystemApps.asStateFlow()

    private val _sortAscending = MutableStateFlow(true)
    val sortAscending: StateFlow<Boolean> = _sortAscending.asStateFlow()

    private var allLoadedApps = listOf<InstalledAppInfo>()

    init {
        loadInstalledApps()
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            _uiState.value = InstalledAppsUiState.Loading
            try {
                allLoadedApps = repository.getInstalledApps(includeSystemApps = true)
                applyFilters()
            } catch (e: Exception) {
                _uiState.value = InstalledAppsUiState.Error(
                    e.localizedMessage ?: "Failed to query installed applications."
                )
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        applyFilters()
    }

    fun toggleSystemApps(include: Boolean) {
        _includeSystemApps.value = include
        applyFilters()
    }

    fun toggleSortOrder() {
        _sortAscending.value = !_sortAscending.value
        applyFilters()
    }

    private fun applyFilters() {
        val query = _searchQuery.value.trim().lowercase()
        val includeSystem = _includeSystemApps.value
        val asc = _sortAscending.value

        var filtered = allLoadedApps.filter { app ->
            val matchesSystem = if (includeSystem) true else !app.isSystemApp
            val matchesQuery = if (query.isEmpty()) true else {
                app.appName.lowercase().contains(query) || app.packageName.lowercase().contains(query)
            }
            matchesSystem && matchesQuery
        }

        filtered = if (asc) {
            filtered.sortedBy { it.appName.lowercase() }
        } else {
            filtered.sortedByDescending { it.appName.lowercase() }
        }

        if (filtered.isEmpty()) {
            _uiState.value = InstalledAppsUiState.Empty
        } else {
            _uiState.value = InstalledAppsUiState.Success(filtered)
        }
    }
}
