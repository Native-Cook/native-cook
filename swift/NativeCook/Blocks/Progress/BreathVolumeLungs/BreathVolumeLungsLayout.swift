import Foundation

struct BreathVolumeLungsLayout {
    let width: Double, height: Double, safeTop: Double, safeBottom: Double
    var scale: Double { width / 402 }
    var pitchX: Double { width / 32 }
    var k: Double { max(0.9, min(1, (height - 140 * scale) / 734)) }
    var cardHeight: Double { 271 * scale * k }
    var cardTop: Double { height - 22 * scale - cardHeight }
    var titleBase: Double { safeTop + 52.5 * scale }
    var heroTop0: Double { safeTop + (49.5 + 39.2) * scale }
    var available: Double { cardTop - 24 * scale - heroTop0 }
    var pitchY: Double { pitchX * max(0.45, min(1, available / (30 * pitchX))) }
    var heroTop: Double { heroTop0 + max(0, available - 30 * pitchY - 29.3 * scale) * 0.5 }
    var digitY: Double { heroTop + 16.86 * pitchY }
    var labelBase: Double { heroTop + 20.8 * pitchY }
    var knobY: Double { cardTop + 135 * scale * k }
    var knobX0: Double { 88 * scale }
    var knobX1: Double { 338 * scale }
    var travel: Double { 250 * scale }
    var buttonY: Double { min(cardTop + 204 * scale * k, height - safeBottom - 58 * scale * k) }
}
