package com.chronos.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.chronos.app.settings.ChronosPreferences

/** Preference-aware feedback for ordinary controls; never used to gate alarm dismissal. */
@Composable
fun rememberChronosHaptic(preferences: ChronosPreferences): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic, preferences.hapticFeedback) {
        {
            if (preferences.hapticFeedback) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            Unit
        }
    }
}
