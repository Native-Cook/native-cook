package com.ngocdevv.nativecook.blocks.sheets.iconmorphsheet

internal data class IconMorphFrame(
    var position: Double = 0.0, var open: Double = 1.0, var contents: Double = 1.0,
    var stage: Double = 1.0, var drag: Double = 0.0, var edge: Double = 0.0,
    var labelOpen: Double = 1.0, var labelClosed: Double = 0.0,
    var labelClosedY: Double = -12.0, var labelOpenY: Double = 0.0,
    var buttonPress: Double = 0.0, var puckPress: Double = 0.0, var puck: Int = 0,
    var running: Double = 1.0, var bounce: Double = 0.0, var lean: Double = 0.0, var press: Double = 0.0,
    var markerX: Double = 201.0, var markerY: Double = 804.0,
    var markerOpacity: Double = 0.0, var markerPress: Double = 0.0, var markerKind: Int = 0,
)
