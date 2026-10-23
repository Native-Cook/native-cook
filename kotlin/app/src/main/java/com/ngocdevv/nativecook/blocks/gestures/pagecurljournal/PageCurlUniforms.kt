package com.ngocdevv.nativecook.blocks.gestures.pagecurljournal

/** Everything the curl shader reads for one sheet, in points (dp). Filled by the engine every frame. */
internal class CurlUniforms {
    var sizeW = 0f
    var sizeH = 0f
    var grabX = 0f
    var grabY = 0f
    var tipX = 0f
    var tipY = 0f
    var r0 = 0f
    var r1 = 0f
    var rmin = 0f
    var frontIdx = 0f
    var underIdx = 0f
    var shA = 0f
    var shW = 0f
    var eA = 0f
    var eW = 0f
    var amb1 = 0f
    var amb2 = 0f
    var g2 = 0f
    var bg = 0f
    var ba = 0f
    var grain = 0f
    var edgeInk = 0f
    var px = 0f
    var fade = 0f
    var clearUnder = 0f
    var spec = 0f
}

/** The foil band's uniforms, in points of the foil rectangle. */
internal class FoilUniforms {
    var dirX = 0f
    var dirY = 0f
    var center = 0f
    var width = 0f
    var amount = 0f
}

/** The foil's rectangle on the screen, in points. */
internal class FoilRect(val left: Float, val top: Float, val width: Float, val height: Float)
