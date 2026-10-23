import Foundation

/// The curl shader's uniforms. Only scalar floats, in the order of the Metal struct, so both sides agree on layout.
struct PageCurlJournalCurlUniforms {
    var sizeX: Float = 0
    var sizeY: Float = 0
    var grabX: Float = 0
    var grabY: Float = 0
    var tipX: Float = 0
    var tipY: Float = 0
    var r0: Float = 0
    var r1: Float = 0
    var rmin: Float = 0
    var frontIdx: Float = 0
    var underIdx: Float = 0
    var shA: Float = 0
    var shW: Float = 0
    var eA: Float = 0
    var eW: Float = 0
    var amb1: Float = 0
    var amb2: Float = 0
    var g2: Float = 0
    var bg: Float = 0
    var ba: Float = 0
    var grain: Float = 0
    var edgeInk: Float = 0
    var px: Float = 0
    var fade: Float = 0
    var clearUnder: Float = 0
    var spec: Float = 0
}

/// The foil shader's uniforms. The rect is the canvas the foil is drawn in, in points on the screen.
struct PageCurlJournalFoilUniforms {
    var dirX: Float = 0
    var dirY: Float = 0
    var center: Float = 0
    var width: Float = 0
    var amount: Float = 0
    var rectX: Float = 0
    var rectY: Float = 0
    var rectWidth: Float = 0
    var rectHeight: Float = 0
    var sizeX: Float = 0
    var sizeY: Float = 0
    var px: Float = 0
}

/// What one frame draws: the sheets in flight (bottom first) and, when the cover rests, the foil.
struct PageCurlJournalFrame {
    var curls: [PageCurlJournalCurlUniforms] = []
    var foil: PageCurlJournalFoilUniforms?
}
