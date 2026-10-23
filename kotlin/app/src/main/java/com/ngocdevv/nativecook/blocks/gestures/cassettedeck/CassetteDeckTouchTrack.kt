package com.ngocdevv.nativecook.blocks.gestures.cassettedeck

import androidx.compose.ui.input.pointer.util.VelocityTracker

/** What one finger on the screen is doing: pressing a key, or panning the cassettes. */
internal sealed interface CassetteDeckTouchTrack {
    /** A finger holding key `index` down (RN `Gesture.Pan().minDistance(0)` on the key). */
    class Key(val index: Int) : CassetteDeckTouchTrack

    /** A finger on the cassette (RN `Gesture.Pan().minDistance(2)`); it counts as a pan after moving 2 dp. */
    class Cassette(val downX: Double, val downY: Double) : CassetteDeckTouchTrack {
        val velocity = VelocityTracker()
        var active = false
        var startX = 0.0
    }
}
