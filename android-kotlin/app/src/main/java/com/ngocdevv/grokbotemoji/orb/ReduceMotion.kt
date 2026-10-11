package com.ngocdevv.grokbotemoji.orb

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android has no "Reduce Motion" switch; the closest system signal is the developer / accessibility
 * option that sets the animator duration scale to 0 ("Remove animations"). Mirrors Reanimated's
 * `useReducedMotion` on Android.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}
