package de.keiss.selfhealing.app.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val from: String = "",
    val to: String = "",
    val connections: List<Connection> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasSearched: Boolean = false
)

class SearchViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun updateFrom(value: String) {
        _uiState.value = _uiState.value.copy(from = value)
    }

    fun updateTo(value: String) {
        _uiState.value = _uiState.value.copy(to = value)
    }

    fun search() {
        val state = _uiState.value
        if (state.from.isBlank() || state.to.isBlank()) return

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)

            ConnectionRepository.search(state.from, state.to)
                .onSuccess { connections ->
                    _uiState.value = _uiState.value.copy(
                        connections = connections,
                        isLoading = false,
                        hasSearched = true
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        error = error.message ?: "Unbekannter Fehler",
                        isLoading = false,
                        hasSearched = true
                    )
                }
        }
    }
}
