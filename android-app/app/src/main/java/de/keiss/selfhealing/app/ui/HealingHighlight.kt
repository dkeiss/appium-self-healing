package de.keiss.selfhealing.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Singleton that holds the currently highlighted test tags.
 * Tags are added via [HealingHighlightReceiver] and auto-removed after [HIGHLIGHT_DURATION_MS].
 */
object HealingHighlightState {
    val highlightedTags = mutableStateListOf<String>()
}

private const val HIGHLIGHT_DURATION_MS = 3000L

/**
 * Drop-in replacement for [Modifier.testTag] that additionally draws a red border
 * when the tag is in [HealingHighlightState.highlightedTags].
 *
 * Visible in noVNC during self-healing demos.
 */
fun Modifier.healableTestTag(tag: String): Modifier = composed {
    val isHighlighted = tag in HealingHighlightState.highlightedTags
    val borderColor by animateColorAsState(
        targetValue = if (isHighlighted) Color.Red else Color.Transparent,
        animationSpec = tween(durationMillis = 300),
        label = "healingBorder"
    )
    testTag(tag).then(
        if (isHighlighted) Modifier.border(3.dp, borderColor, RoundedCornerShape(4.dp))
        else Modifier
    )
}

/**
 * BroadcastReceiver that listens for healing highlight commands from the test runner.
 *
 * Triggered via ADB:
 * ```
 * am broadcast -a de.keiss.selfhealing.HIGHLIGHT --es tag fab_search
 * ```
 */
class HealingHighlightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val tag = intent.getStringExtra("tag") ?: return
        HealingHighlightState.highlightedTags.add(tag)
        Handler(Looper.getMainLooper()).postDelayed({
            HealingHighlightState.highlightedTags.remove(tag)
        }, HIGHLIGHT_DURATION_MS)
    }
}
