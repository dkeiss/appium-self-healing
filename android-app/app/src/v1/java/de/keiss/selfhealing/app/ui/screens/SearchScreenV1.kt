package de.keiss.selfhealing.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.keiss.selfhealing.app.data.Connection
import de.keiss.selfhealing.app.ui.healableTestTag

/**
 * v1 Search Screen — Standard layout.
 *
 * Test-relevant element IDs (testTag / contentDescription):
 * - "input_from"         : Departure station text field
 * - "input_to"           : Destination station text field
 * - "btn_search"         : Search button
 * - "connection_list"    : Results list container
 * - "connection_item"    : Individual result items
 * - "text_from"          : Departure text in result
 * - "text_to"            : Destination text in result
 * - "text_duration"      : Duration text in result
 * - "text_transfers"     : Transfer count in result
 * - "text_price"         : Price text in result
 * - "text_no_results"    : "No results" message
 * - "leg_train_number"   : Train number of first leg (inline in card)
 * - "leg_platform"       : Platform of first leg (inline in card)
 * - "btn_m3n"            : First toolbar action — bound to filter behavior in v1 (labeled TextButton)
 * - "btn_x7q"            : Second toolbar action — bound to sort behavior in v1 (labeled TextButton)
 * - "btn_p2k"            : Third toolbar action — bound to share behavior in v1 (labeled TextButton)
 * - "toolbar_status"     : Status text after a toolbar action was triggered
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreenV1(
    from: String,
    to: String,
    onFromChange: (String) -> Unit,
    onToChange: (String) -> Unit,
    onSearch: () -> Unit,
    connections: List<Connection>,
    isLoading: Boolean,
    error: String?,
    hasSearched: Boolean,
    toolbarStatus: String,
    onFilter: () -> Unit,
    onSort: () -> Unit,
    onShare: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zugverbindung") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Search inputs
            OutlinedTextField(
                value = from,
                onValueChange = onFromChange,
                label = { Text("Von") },
                leadingIcon = { Icon(Icons.Default.Train, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .healableTestTag("input_from")
                    .semantics { contentDescription = "Startbahnhof" },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = to,
                onValueChange = onToChange,
                label = { Text("Nach") },
                leadingIcon = { Icon(Icons.Default.Train, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .healableTestTag("input_to")
                    .semantics { contentDescription = "Zielbahnhof" },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Search button
            Button(
                onClick = onSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .healableTestTag("btn_search")
                    .semantics { contentDescription = "Suchen" },
                enabled = from.isNotBlank() && to.isNotBlank() && !isLoading
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Verbindung suchen")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Loading indicator
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            // Error message
            if (error != null) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.healableTestTag("text_error")
                )
            }

            // No results message
            if (hasSearched && connections.isEmpty() && !isLoading && error == null) {
                Text(
                    text = "Keine Verbindungen gefunden",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .healableTestTag("text_no_results")
                        .semantics { contentDescription = "Keine Verbindungen gefunden" }
                        .padding(vertical = 16.dp)
                )
            }

            // Results list
            if (connections.isNotEmpty()) {
                // v1 toolbar: labeled buttons. testTags are intentionally noise-suffixed
                // (`btn_m3n/_x7q/_p2k`) — neither alphabetic nor positional ordering, so
                // the broken-locator name carries no usable hint to the LLM. Visible button
                // labels stay descriptive ("Filtern" etc.) for the demo UX.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = onFilter,
                        modifier = Modifier
                            .healableTestTag("btn_m3n")
                            .semantics { contentDescription = "Filtern" }
                    ) {
                        Text("Filtern")
                    }
                    TextButton(
                        onClick = onSort,
                        modifier = Modifier
                            .healableTestTag("btn_x7q")
                            .semantics { contentDescription = "Sortieren" }
                    ) {
                        Text("Sortieren")
                    }
                    TextButton(
                        onClick = onShare,
                        modifier = Modifier
                            .healableTestTag("btn_p2k")
                            .semantics { contentDescription = "Teilen" }
                    ) {
                        Text("Teilen")
                    }
                }

                if (toolbarStatus.isNotEmpty()) {
                    Text(
                        text = toolbarStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .healableTestTag("toolbar_status")
                            .semantics { contentDescription = toolbarStatus }
                            .padding(vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.healableTestTag("connection_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(connections) { connection ->
                        ConnectionItemV1(connection)
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionItemV1(connection: Connection) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .healableTestTag("connection_item"),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Route: From -> To
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = connection.from,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .healableTestTag("text_from")
                        .semantics { contentDescription = connection.from }
                )
                Text(
                    text = "→",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = connection.to,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .healableTestTag("text_to")
                        .semantics { contentDescription = connection.to }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Details row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${connection.departure} - ${connection.arrival}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.healableTestTag("text_duration")
                )
                Text(
                    text = "${connection.transfers} Umstiege",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .healableTestTag("text_transfers")
                        .semantics { contentDescription = "${connection.transfers} Umstiege" }
                )
                Text(
                    text = "%.2f €".format(connection.priceEuro),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .healableTestTag("text_price")
                        .semantics { contentDescription = "%.2f Euro".format(connection.priceEuro) }
                )
            }

            // Train types
            Row(modifier = Modifier.padding(top = 4.dp)) {
                connection.trainTypes.forEach { type ->
                    AssistChip(
                        onClick = {},
                        label = { Text(type) },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }

            // First-leg details — inline in v1 (v2 hides these behind a BottomSheet)
            connection.legs.firstOrNull()?.let { firstLeg ->
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${firstLeg.trainType} ${firstLeg.trainNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .healableTestTag("leg_train_number")
                            .semantics { contentDescription = "Zug ${firstLeg.trainType} ${firstLeg.trainNumber}" }
                    )
                    Text(
                        text = "Gleis ${firstLeg.platform}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .healableTestTag("leg_platform")
                            .semantics { contentDescription = "Gleis ${firstLeg.platform}" }
                    )
                }
            }
        }
    }
}
