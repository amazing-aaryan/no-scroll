package com.noscroll.tutorial

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

class TutorialController {
    private val boundsMap = mutableStateMapOf<TutorialStepId, Rect>()
    private var steps: List<TutorialStep> = emptyList()
    private var index = 0
    var onDone: (() -> Unit)? = null

    var current by mutableStateOf<TutorialStep?>(null)
        private set

    fun stepCount() = steps.size
    fun stepIndex() = index

    fun boundsFor(id: TutorialStepId): Rect? = boundsMap[id]

    fun registerBounds(id: TutorialStepId, rect: Rect) {
        boundsMap[id] = rect
    }

    fun start(sequence: List<TutorialStep>) {
        steps = sequence
        index = 0
        current = steps.firstOrNull()
    }

    fun advance() {
        index++
        current = steps.getOrNull(index)
        if (current == null) onDone?.invoke()
    }

    fun skip() {
        current = null
        onDone?.invoke()
    }
}
