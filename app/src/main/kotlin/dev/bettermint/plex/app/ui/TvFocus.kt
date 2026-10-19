package dev.bettermint.plex.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect

/**
 * Requests focus on this element when it first composes, so a D-pad has a
 * starting point. Without it, screens open with nothing focused and the
 * center button does nothing until the user presses a direction. Safe on
 * touch devices, where it is simply ignored.
 */
@Composable
fun Modifier.initialFocus(key: Any? = Unit): Modifier {
    val requester = remember { FocusRequester() }
    LaunchedEffect(key) { runCatching { requester.requestFocus() } }
    return this.focusRequester(requester)
}
