package de.keiss.selfhealing.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.keiss.selfhealing.app.data.Connection
import de.keiss.selfhealing.app.ui.healableTestTag

/**
 * v2 Results Screen — Separate screen (navigation change from v1).
 *
 * Changed element IDs (v1 → v2):
 * - "connection_list" → "results_container"  (renamed)
 * - "connection_item" → "journey_card"       (renamed + restyled)
 * - "text_from"       → "label_departure"    (renamed)
 * - "text_to"         → "label_arrival"      (renamed)
 * - "text_duration"   → "label_travel_time"  (renamed)
 * - "text_transfers"  → "label_changes"      (renamed)
 * - "text_price"      → "label_fare"         (renamed)
 * - "text_no_results" → "empty_state_text"   (renamed)
 *
 * Structural changes:
 * - Separate screen with back navigation (was inline in v1)
 * - Bottom sheet for detail view (new)
 * - Different card layout (horizontal instead of stacked)
 * - Price badge instead of inline text
 * - Toolbar (Filter/Sort/Share) collapsed into icon-only IconButtons that all share
 *   the testTag "toolbar_action" and content-desc "Aktion" — only the rendered icon
 *   glyph distinguishes them. Vision-affine on purpose:
 *   - "btn_m3n" → first "toolbar_action"  (filter funnel icon)
 *   - "btn_x7q" → second "toolbar_action" (sort arrows icon)
 *   - "btn_p2k" → third "toolbar_action"  (share icon)
 *   The status text "toolbar_status" is identical in v1/v2.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreenV2(
    connections: List<Connection>,
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    toolbarStatus: String,
    onFilter: () -> Unit,
    onSort: () -> Unit,
    onShare: () -> Unit
) {
    var selectedConnection by remember { mutableStateOf<Connection?>(null) }
    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ergebnisse") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                error != null -> {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .healableTestTag("error_message")
                    )
                }

                connections.isEmpty() -> {
                    Text(
                        text = "Keine Verbindungen gefunden",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .healableTestTag("empty_state_text")  // Was "text_no_results" in v1
                            .semantics { contentDescription = "Keine Verbindungen gefunden" }
                    )
                }

                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // v2 toolbar: three identical IconButtons — same testTag, same content-desc.
                        // The only discriminator is the rendered icon glyph (FilterList / Sort / Share).
                        // Text-only XML inspection cannot tell them apart; vision can.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(
                                onClick = onFilter,
                                modifier = Modifier
                                    .healableTestTag("toolbar_action")
                                    .semantics { contentDescription = "Aktion" }
                            ) {
                                Icon(Icons.Filled.FilterList, contentDescription = null)
                            }
                            IconButton(
                                onClick = onSort,
                                modifier = Modifier
                                    .healableTestTag("toolbar_action")
                                    .semantics { contentDescription = "Aktion" }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null)
                            }
                            IconButton(
                                onClick = onShare,
                                modifier = Modifier
                                    .healableTestTag("toolbar_action")
                                    .semantics { contentDescription = "Aktion" }
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null)
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
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .healableTestTag("results_container")  // Was "connection_list" in v1
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(connections) { connection ->
                                JourneyCard(
                                    connection = connection,
                                    onClick = { selectedConnection = connection }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // v2: Bottom sheet for details (entirely new, not in v1)
    if (selectedConnection != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedConnection = null },
            sheetState = sheetState,
            modifier = Modifier.healableTestTag("detail_sheet")
        ) {
            ConnectionDetailSheet(selectedConnection!!)
        }
    }
}

@Composable
fun JourneyCard(connection: Connection, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .healableTestTag("journey_card"),  // Was "connection_item" in v1
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Route info (vertical stack — different layout than v1)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = connection.from,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .healableTestTag("label_departure")  // Was "text_from" in v1
                        .semantics { contentDescription = connection.from }
                )
                Text(
                    text = "↓ ${connection.trainTypes.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = connection.to,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .healableTestTag("label_arrival")  // Was "text_to" in v1
                        .semantics { contentDescription = connection.to }
                )
            }

            // Center: Duration & transfers
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Text(
                    text = "${connection.durationMinutes} min",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .healableTestTag("label_travel_time")  // Was "text_duration" in v1
                )
                Text(
                    text = "${connection.transfers}x umst.",  // Different format than v1
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .healableTestTag("label_changes")  // Was "text_transfers" in v1
                        .semantics { contentDescription = "${connection.transfers} Umstiege" }
                )
            }

            // Right: Price badge (different styling than v1)
            Badge(
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .healableTestTag("label_fare")  // Was "text_price" in v1
                    .semantics { contentDescription = "%.2f Euro".format(connection.priceEuro) }
            ) {
                Text(
                    text = "%.2f €".format(connection.priceEuro),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
fun ConnectionDetailSheet(connection: Connection) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .healableTestTag("detail_content")
    ) {
        Text(
            text = "${connection.from} → ${connection.to}",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(16.dp))

        connection.legs.forEachIndexed { index, leg ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .healableTestTag("leg_item_$index"),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    // v2: train-number tag moved into the bottom sheet — in v1 it was "leg_train_number"
                    // on the card. The self-healing benchmark targets this element for the
                    // "very-hard-navigation" track.
                    Text(
                        text = "${leg.trainType} ${leg.trainNumber}",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .healableTestTag("leg_item_${index}_train")
                            .semantics { contentDescription = "Zug ${leg.trainType} ${leg.trainNumber}" }
                    )
                    Text("${leg.from} → ${leg.to}")
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${leg.departure} - ${leg.arrival}")
                    Text(
                        text = "Gleis ${leg.platform}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .healableTestTag("leg_item_${index}_platform")
                            .semantics { contentDescription = "Gleis ${leg.platform}" }
                    )
                }
            }
            if (index < connection.legs.size - 1) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
