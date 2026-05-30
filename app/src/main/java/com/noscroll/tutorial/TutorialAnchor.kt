package com.noscroll.tutorial

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

@Composable
fun TutorialAnchor(
    id: TutorialStepId,
    controller: TutorialController?,
    content: @Composable () -> Unit
) {
    if (controller == null) {
        content()
        return
    }
    Box(
        Modifier.onGloballyPositioned { coords ->
            controller.registerBounds(id, coords.boundsInWindow())
        }
    ) {
        content()
    }
}
