package com.ngocdevv.nativecook.blocks.cards.paddockwidgetbento

data class PaddockWidgetBentoFrame(
    val phase: PaddockWidgetBentoMotion.Phase,
    val order: List<PaddockWidgetBentoCardID>,
    val cardY: Map<PaddockWidgetBentoCardID, Double>,
    val cardOpacity: Map<PaddockWidgetBentoCardID, Double>,
    val handlesOpacity: Double,
    val selectedID: PaddockWidgetBentoCardID?,
    val selectedScale: Double,
    val shadowRadius: Double,
    val rotationDegrees: Double,
    val isDemo: Boolean,
    /** Seconds into the loop while the demo plays, -1 otherwise: the pit stop, peel and jiggle read it. */
    val loopTime: Double,
)
