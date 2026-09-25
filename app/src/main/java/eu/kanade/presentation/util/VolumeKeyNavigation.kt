package eu.kanade.presentation.util

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
        pageLayout = { state.toPageScrollLayout() },
        scrollToIndex = { state.scrollToItem(it) },
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
        pageLayout = { state.toPageScrollLayout() },
        scrollToIndex = { state.scrollToItem(it) },
        enabled = enabled,
    )
}

private class PageScrollLayout(
    val viewportHeight: Int,
    val firstIndex: Int,
    val rowHeight: Int,
    val itemsPerRow: Int,
)

private fun LazyListState.toPageScrollLayout(): PageScrollLayout {
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    val rowHeight = if (visible.isNotEmpty()) visible.sumOf { it.size } / visible.size else 0
    return PageScrollLayout(
        viewportHeight = info.viewportEndOffset - info.viewportStartOffset,
        firstIndex = firstVisibleItemIndex,
        rowHeight = rowHeight,
        itemsPerRow = 1,
    )
}

private fun LazyGridState.toPageScrollLayout(): PageScrollLayout {
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    val firstRow = visible.firstOrNull()?.row
    var itemsPerRow = 1
    var rowHeight = 0
    if (firstRow != null) {
        val inFirstRow = visible.filter { it.row == firstRow }
        itemsPerRow = inFirstRow.size.coerceAtLeast(1)
        val top = inFirstRow.minOf { it.offset.y }
        val bottom = inFirstRow.maxOf { it.offset.y + it.size.height }
        val nextRowTop = visible.filter { it.row > firstRow }.minOfOrNull { it.offset.y }
        rowHeight = nextRowTop?.minus(top) ?: (bottom - top)
    }
    return PageScrollLayout(
        viewportHeight = info.viewportEndOffset - info.viewportStartOffset,
        firstIndex = firstVisibleItemIndex,
        rowHeight = rowHeight,
        itemsPerRow = itemsPerRow,
    )
}

/**
 * Index the new first row should land on: one page is however many whole rows fit in the
 * viewport (3.7 visible rows means pages of 3 rows), and pages always start at a row top.
 */
private fun PageScrollLayout.targetPageIndex(up: Boolean): Int? {
    if (viewportHeight <= 0 || rowHeight <= 0) return null
    val rowsPerPage = (viewportHeight / rowHeight).coerceAtLeast(1)
    val target = if (up) {
        firstIndex - rowsPerPage * itemsPerRow
    } else {
        firstIndex + rowsPerPage * itemsPerRow
    }
    return if (up) target.coerceAtLeast(0) else target
}

@Composable
private fun VolumeKeyPageScrollHandlerImpl(
    key: Any,
    canScroll: () -> Boolean,
    pageLayout: () -> PageScrollLayout,
    scrollToIndex: suspend (Int) -> Unit,
    enabled: () -> Boolean,
) {
    val scope = rememberCoroutineScope()
    val currentEnabled by rememberUpdatedState(enabled())

    val handler = remember(key) {
        object : VolumeKeyNavigation.Handler {
            override val enabled: Boolean
                get() = currentEnabled && canScroll()

            override fun pageScroll(up: Boolean) {
                val target = pageLayout().targetPageIndex(up) ?: return
                scope.launch { scrollToIndex(target) }
            }
        }
    }

    DisposableEffect(handler) {
        VolumeKeyNavigation.push(handler)
        onDispose { VolumeKeyNavigation.pop(handler) }
    }
}
