package com.ngocdevv.nativecook.blocks.gestures.cassettedeck

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/** Routes every finger on the screen to the deck's keys and cassette (see [CassetteDeckTouchRouter]). */
internal fun Modifier.cassetteDeckTouch(controller: CassetteDeckController): Modifier {
    return pointerInput(controller) {
        val router = CassetteDeckTouchRouter(controller)
        var geometry: CassetteDeckGeometry? = null
        try {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val density = this.density
                    val current = CassetteDeckGeometry(size.width / density.toDouble(), size.height / density.toDouble())
                    geometry = current
                    router.onEvent(event, current, density)
                }
            }
        } finally {
            router.cancelAll(geometry)
        }
    }
}
