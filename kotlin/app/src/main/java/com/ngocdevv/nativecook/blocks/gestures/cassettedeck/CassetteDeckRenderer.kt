package com.ngocdevv.nativecook.blocks.gestures.cassettedeck

import android.graphics.Canvas

/**
 * Draws the whole screen from the simulation, in dp units: the flat background, the backdrop, the three cassettes
 * and the keys, in the order the React Native views stack.
 */
internal class CassetteDeckRenderer(
    private val geometry: CassetteDeckGeometry,
    assets: CassetteDeckAssets,
    density: Float,
) {
    private val backdrop = CassetteDeckBackdrop(geometry, density)
    private val cassettes = CassetteDeckCassettePainter(geometry, assets)
    private val keys = CassetteDeckKeyPainter(geometry, assets)

    fun draw(canvas: Canvas, sim: CassetteDeckSim) {
        canvas.drawColor(CassetteDeckTheme.bg)
        backdrop.draw(canvas, sim)
        for (slot in 0 until CassetteDeckSim.SLOTS) cassettes.draw(canvas, sim, slot)
        keys.draw(canvas, sim)
    }
}
