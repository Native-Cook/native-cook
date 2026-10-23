package com.ngocdevv.nativecook.blocks.gestures.cassettedeck

/** RN `HAPTIC`: one per commit, never per frame. */
internal enum class CassetteDeckHaptic(val sourceCode: Int) {
    CLICK(1),
    LATCH(2),
    RELEASE(3),
    COMMIT(4),
    TICK(5),
}
