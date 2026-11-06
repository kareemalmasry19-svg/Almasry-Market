package com.almasry.market

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val market: Market? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repo(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.cached()?.let { m -> _state.update { it.copy(market = m) } }
            refresh()
        }
    }

    fun refresh() {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repo.fetch() }
                .onSuccess { m ->
                    _state.update { it.copy(market = m, loading = false) }
                    m.report?.created?.let { Prefs.setSeenReport(getApplication(), it) }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message ?: "تعذر التحديث") }
                }
        }
    }
}
