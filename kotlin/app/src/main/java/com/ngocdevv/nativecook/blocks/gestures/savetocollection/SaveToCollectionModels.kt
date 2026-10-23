package com.ngocdevv.nativecook.blocks.gestures.savetocollection

data class SaveCubicBezier(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
)

data class SaveSpring(
    val damping: Float,
    val stiffness: Float,
    val mass: Float,
)

data class SaveCard(
    val id: String,
    val label: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val blur: Float,
    val pile: Int,
    val art: Int,
    val soft: Int,
)

data class SaveRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

data class SaveFlight(
    val id: Int,
    val card: Int,
    val from: SaveRect,
    val start: Double,
    val duration: Double,
)

data class SaveStreamDrop(
    val art: Int,
    val x: Float,
    val width: Float,
    val start: Float,
    val duration: Float,
    val y0: Float,
)

enum class SaveHaptic {
    Lift,
    Magnet,
    Land,
    Select,
    Commit,
    Success,
}
