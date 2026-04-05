package de.keiss.selfhealing.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.keiss.selfhealing.app.ui.healableTestTag

/**
 * v2 Search Screen — Redesigned layout.
 *
 * Changed element IDs (v1 → v2):
 * - "input_from"   → "departure_station"   (renamed)
 * - "input_to"     → "arrival_station"      (renamed)
 * - "btn_search"   → "fab_search"           (type changed: Button → FAB)
 *
 * Structural changes:
 * - Card wrapper around inputs (not in v1)
 * - Swap button between from/to (new element)
 * - FAB instead of full-width button
 * - Different field labels ("Abfahrt" / "Ankunft" vs "Von" / "Nach")
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreenV2(
    from: String,
    to: String,
    onFromChange: (String) -> Unit,
    onToChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reiseauskunft") },  // Different title than v1
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            // v2: FAB instead of inline Button — different testTag!
            FloatingActionButton(
                onClick = onSearch,
                modifier = Modifier
                    .healableTestTag("fab_search")  // Was "btn_search" in v1
                    .semantics { contentDescription = "Suche starten" },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Search, contentDescription = "Suchen")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // v2: Inputs wrapped in a Card (not in v1)
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // v2: Different testTag and label!
                    OutlinedTextField(
                        value = from,
                        onValueChange = onFromChange,
                        label = { Text("Abfahrt") },  // Was "Von" in v1
                        modifier = Modifier
                            .fillMaxWidth()
                            .healableTestTag("departure_station")  // Was "input_from" in v1
                            .semantics { contentDescription = "Abfahrtsbahnhof" },
                        singleLine = true
                    )

                    // v2: New swap button (not in v1)
                    IconButton(
                        onClick = {
                            val temp = from
                            onFromChange(to)
                            onToChange(temp)
                        },
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .healableTestTag("btn_swap")
                    ) {
                        Icon(Icons.Default.SwapVert, contentDescription = "Tauschen")
                    }

                    OutlinedTextField(
                        value = to,
                        onValueChange = onToChange,
                        label = { Text("Ankunft") },  // Was "Nach" in v1
                        modifier = Modifier
                            .fillMaxWidth()
                            .healableTestTag("arrival_station")  // Was "input_to" in v1
                            .semantics { contentDescription = "Ankunftsbahnhof" },
                        singleLine = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Tippe auf den Suchbutton um Verbindungen zu finden",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
