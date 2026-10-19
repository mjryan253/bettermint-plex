package dev.bettermint.plex.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/**
 * Requests focus on this element when its destination is shown, so a D-pad
 * always has a starting point. Without it, screens open with nothing focused
 * and the center button does nothing. On returning via Back, this lands focus
 * on the first item; a [focusRestorer] on the surrounding container restores
 * the column within a row. Safe on touch, where it is simply ignored.
 */
@Composable
fun Modifier.initialFocus(key: Any? = Unit): Modifier {
    val requester = remember { FocusRequester() }
    LaunchedEffect(key) { runCatching { requester.requestFocus() } }
    return this.focusRequester(requester)
}
