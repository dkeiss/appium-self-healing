package de.keiss.selfhealing.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.keiss.selfhealing.app.data.SearchViewModel

/**
 * v2 AppContent — Two-screen navigation (Search → Results).
 * This is the "redesigned" version that breaks v1 tests.
 *
 * Key differences from v1:
 * - Navigation: separate screens instead of single page
 * - Element IDs renamed (input_from → departure_station, etc.)
 * - Search button is now a FAB instead of full-width button
 * - Results use a different card layout
 * - Bottom sheet for connection details (not in v1)
 */
@Composable
fun AppContent(viewModel: SearchViewModel) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()

    NavHost(navController = navController, startDestination = "search") {
        composable("search") {
            SearchScreenV2(
                from = uiState.from,
                to = uiState.to,
                onFromChange = viewModel::updateFrom,
                onToChange = viewModel::updateTo,
                onSearch = {
                    viewModel.search()
                    navController.navigate("results")
                }
            )
        }
        composable("results") {
            ResultScreenV2(
                connections = uiState.connections,
                isLoading = uiState.isLoading,
                error = uiState.error,
                onBack = { navController.popBackStack() },
                toolbarStatus = uiState.toolbarStatus,
                onFilter = viewModel::applyFilter,
                onSort = viewModel::applySort,
                onShare = viewModel::shareResults
            )
        }
    }
}
