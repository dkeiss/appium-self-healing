package de.keiss.selfhealing.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import de.keiss.selfhealing.app.data.SearchViewModel

/**
 * v1 AppContent — single-screen layout with search and results on the same page.
 * This is the "stable" version that tests are written against.
 */
@Composable
fun AppContent(viewModel: SearchViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    SearchScreenV1(
        from = uiState.from,
        to = uiState.to,
        onFromChange = viewModel::updateFrom,
        onToChange = viewModel::updateTo,
        onSearch = viewModel::search,
        connections = uiState.connections,
        isLoading = uiState.isLoading,
        error = uiState.error,
        hasSearched = uiState.hasSearched
    )
}
