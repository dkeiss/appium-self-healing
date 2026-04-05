package de.keiss.selfhealing.app

import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import de.keiss.selfhealing.app.data.SearchViewModel
import de.keiss.selfhealing.app.ui.HealingHighlightReceiver
import de.keiss.selfhealing.app.ui.screens.AppContent
import de.keiss.selfhealing.app.ui.theme.ZugverbindungTheme

/**
 * Main activity — delegates to flavor-specific AppContent composable.
 * v1 and v2 flavors provide different implementations of AppContent
 * with different element IDs, layouts, and navigation patterns.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: SearchViewModel by viewModels()
    private val healingHighlightReceiver = HealingHighlightReceiver()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        registerReceiver(
            healingHighlightReceiver,
            IntentFilter("de.keiss.selfhealing.HIGHLIGHT"),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RECEIVER_EXPORTED else 0
        )
        setContent {
            ZugverbindungTheme {
                Box(Modifier.semantics { testTagsAsResourceId = true }) {
                    AppContent(viewModel = viewModel)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(healingHighlightReceiver)
    }
}
