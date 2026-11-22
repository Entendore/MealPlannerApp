package com.example.mealplanner.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex

/**
 * Long-press to lift, drag to reorder, release to commit via [onMove].
 * Pure Compose — no LazyLayout dependency, so it nests inside any scroll.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (item: T, dragModifier: Modifier) -> Unit
) {
    val heights = remember { mutableStateMapOf<Int, Int>() }
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var targetIndex by remember { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current

    fun draggedCenter(from: Int): Float {
        var top = 0f
        for (i in 0 until from) top += heights[i] ?: 0
        return top + (heights[from] ?: 0) / 2f + dragOffset
    }

    fun updateTarget() {
        val from = dragIndex ?: return
        val center = draggedCenter(from)
        var passedCenters = 0
        var top = 0f
        for (i in items.indices) {
            val h = heights[i] ?: 0
            if (top + h / 2f < center) passedCenters++
            top += h
        }
        targetIndex = (passedCenters - 1).coerceIn(0, items.lastIndex)
    }

    fun endDrag() {
        val from = dragIndex
        if (from != null && targetIndex != from) onMove(from, targetIndex)
        dragIndex = null
        dragOffset = 0f
    }

    Column(modifier) {
        items.forEachIndexed { index, item ->
            val isDragging = dragIndex == index
            val shift = if (dragIndex != null) {
                val from = dragIndex!!
                val draggedHeight = (heights[from] ?: 0).toFloat()
                when {
                    from < targetIndex && index in (from + 1)..targetIndex -> -draggedHeight
                    targetIndex < from && index in targetIndex until from -> draggedHeight
                    else -> 0f
                }
            } else 0f

            Box(
                Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragging) dragOffset else shift
                        shadowElevation = if (isDragging) 16f else 0f
                    }
                    .onSizeChanged { heights[index] = it.height }
            ) {
                itemContent(item, Modifier.pointerInput(index, items.size) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            dragIndex = index; dragOffset = 0f; targetIndex = index
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount.y
                            updateTarget()
                        },
                        onDragEnd = { endDrag() },
                        onDragCancel = { endDrag() }
                    )
                })
            }
        }
    }
}
