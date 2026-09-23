package eu.kanade.presentation.util

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch

/**
 * Registry for screens that page their list with hardware volume keys, driven by
 * [eu.kanade.tachiyomi.ui.main.MainActivity]. The last registered *enabled* handler wins,
 * so gated-off pager pages don't shadow the visible one.
 */
object VolumeKeyNavigation {

    interface Handler {
        val enabled: Boolean

        fun pageScroll(up: Boolean)
    }

    private val handlers = mutableListOf<Handler>()

    val activeHandler: Handler?
        get() = handlers.lastOrNull { it.enabled }

    internal fun push(handler: Handler) {
        handlers += handler
    }

    internal fun pop(handler: Handler) {
        handlers -= handler
    }
}

@Composable
fun VolumeKeyPageScrollHandler(
    state: LazyListState,
    enabled: () -> Boolean = { true },
) {
    VolumeKeyPageScrollHandlerImpl(
        key = state,
        canScroll = { state.canScrollForward || state.canScrollBackward },
        viewportHeight = { state.layoutInfo.viewportEndOffset - state.layoutInfo.viewportStartOffset },
        scrollBy = { state.scrollBy(it) },
        enabled = enabled,
    )
}

@Composable
fun VolumeKeyPageScrollHandler(
    state: LazyGridState,
    enabled: () -> Boolean = { true },
) {
    VolumeKeyPageScrollHandlerImpl(
        key = state,
        canScroll = { state.canScrollForward || state.canScrollBackward },
        viewportHeight = { state.layoutInfo.viewportEndOffset - state.layoutInfo.viewportStartOffset },
        scrollBy = { state.scrollBy(it) },
        enabled = enabled,
    )
}

@Composable
private fun VolumeKeyPageScrollHandlerImpl(
    key: Any,
    canScroll: () -> Boolean,
    viewportHeight: () -> Int,
    scrollBy: suspend (Float) -> Unit,
    enabled: () -> Boolean,
) {
    val scope = rememberCoroutineScope()
    val currentEnabled by rememberUpdatedState(enabled())

    val handler = remember(key) {
        object : VolumeKeyNavigation.Handler {
            override val enabled: Boolean
                get() = currentEnabled && canScroll()

            override fun pageScroll(up: Boolean) {
                val height = viewportHeight()
                if (height <= 0) return
                scope.launch { scrollBy(if (up) -height.toFloat() else height.toFloat()) }
            }
        }
    }

    DisposableEffect(handler) {
        VolumeKeyNavigation.push(handler)
        onDispose { VolumeKeyNavigation.pop(handler) }
    }
}
