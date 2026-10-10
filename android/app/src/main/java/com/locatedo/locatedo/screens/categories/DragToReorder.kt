package com.locatedo.locatedo.screens.categories

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

// Drag-to-reorder for a keyed LazyColumn: a handle moves its row, and crossing a neighbor's center swaps the two.
class DragToReorderState(
    private val listState: LazyListState,
    private val onMove: (from: Int, to: Int) -> Unit,
    private val onDrop: () -> Unit,
) {
    var draggingKey: Any? by mutableStateOf(null)
        private set

    var draggedDistance: Float by mutableFloatStateOf(0f)
        private set

    fun start(key: Any) {
        draggingKey = key
        draggedDistance = 0f
    }

    fun drag(deltaY: Float) {
        val key = draggingKey ?: return
        draggedDistance += deltaY
        val items = listState.layoutInfo.visibleItemsInfo
        val dragged = items.firstOrNull { it.key == key } ?: return
        val center = dragged.offset + dragged.size / 2 + draggedDistance
        val target = items.firstOrNull { it.key != key && center >= it.offset && center < it.offset + it.size } ?: return
        onMove(dragged.index, target.index)
        // The row now sits where the target was, so the visual offset shrinks by the distance it jumped.
        draggedDistance += dragged.offset - target.offset
    }

    fun end() {
        if (draggingKey == null) {
            return
        }
        draggingKey = null
        draggedDistance = 0f
        onDrop()
    }
}

@Composable
fun rememberDragToReorderState(
    listState: LazyListState,
    onMove: (from: Int, to: Int) -> Unit,
    onDrop: () -> Unit,
): DragToReorderState {
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnDrop by rememberUpdatedState(onDrop)
    return remember(listState) {
        DragToReorderState(listState, onMove = { from, to -> currentOnMove(from, to) }, onDrop = { currentOnDrop() })
    }
}

fun Modifier.dragToReorderHandle(state: DragToReorderState, key: Any): Modifier = pointerInput(state, key) {
    detectDragGestures(
        onDragStart = { state.start(key) },
        onDragEnd = { state.end() },
        onDragCancel = { state.end() },
    ) { change, dragAmount ->
        change.consume()
        state.drag(dragAmount.y)
    }
}
