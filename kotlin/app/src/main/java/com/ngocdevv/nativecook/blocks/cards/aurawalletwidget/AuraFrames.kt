package com.ngocdevv.nativecook.blocks.cards.aurawalletwidget

// Measured layout, computed only when width changes. The field order also
// mirrors auraFrames in the reference score's geometry fixtures.
internal data class AuraFrames(
    val u: Double,
    val size: Double,
    val radius: Double,
    val hero: Hero,
    val lip: Lip,
    val pane: Pane,
    val actions: List<Rect>,
) {
    data class Rect(val x: Double, val y: Double, val w: Double, val h: Double)
    data class Hero(val x: Double, val y: Double, val w: Double, val h: Double,
        val radiusTop: Double, val radiusBottom: Double)
    data class Lip(val cy: Double, val avatar: Double, val ring: Double, val xs: List<Double>)
    data class Pane(val x: Double, val y: Double, val w: Double, val h: Double, val radius: Double)
}

internal fun auraFrames(width: Double): AuraFrames {
    val unit = width / AuraPalette.design
    val widths = listOf(AuraPalette.Actions.settingsWidth, AuraPalette.Actions.swapWidth,
        AuraPalette.Actions.receiveWidth, AuraPalette.Actions.sendWidth)
    var x = AuraPalette.inset
    val actions = widths.map { actionWidth ->
        val frame = AuraFrames.Rect(x * unit, AuraPalette.Actions.y * unit,
            actionWidth * unit, AuraPalette.Actions.h * unit)
        x += actionWidth + AuraPalette.Actions.gap
        frame
    }
    return AuraFrames(unit, width, AuraPalette.radius * unit,
        AuraFrames.Hero(AuraPalette.Hero.x * unit, AuraPalette.Hero.y * unit,
            AuraPalette.Hero.w * unit, AuraPalette.Hero.h * unit,
            AuraPalette.Hero.radiusTop * unit, AuraPalette.Hero.radiusBottom * unit),
        AuraFrames.Lip(AuraPalette.Lip.cy * unit, AuraPalette.Lip.avatar * unit,
            AuraPalette.Lip.ring * unit, List(AuraPalette.Lip.count) { auraSlotX(it) * unit }),
        AuraFrames.Pane(AuraPalette.Pane.x * unit, AuraPalette.Pane.y * unit,
            AuraPalette.Pane.w * unit, AuraPalette.Pane.h * unit, AuraPalette.Pane.radius * unit),
        actions)
}
