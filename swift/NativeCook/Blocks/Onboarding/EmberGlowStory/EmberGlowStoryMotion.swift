import Foundation

/// Every pose is a function of the time since the page last changed, so a touch and the loop that
/// plays by itself make exactly the same pictures. Times are in seconds, lengths in points.
enum EmberGlowStoryMotion {
    /// How a page was reached: written word by word, faded in whole, or after the flood.
    enum How {
        case forward, whole, flooded
    }

    /// The page shown, the one before it, when it changed and how.
    struct Scene: Equatable {
        var page: Int
        var from: Int
        var at: Double
        var how: How
    }

    /// The button: whether a finger is on it, since when (or when it left), and where.
    struct Press: Equatable {
        var down = false
        var at = -10.0
        var x = 0.0
    }

    struct WordPose {
        var opacity = 1.0
        var y = 0.0
        var lit = 0.0
        var flash = 0.0
        var pop = 1.0
    }

    /// autoPlay: the button is pressed before each of these moments and let go at it.
    static let loop = 11.0
    static let autoTaps = [2.6, 5.2, 7.8, 10.2]
    static let pressFor = 0.15
    /// After a touch autoPlay waits this long before it goes back to the first page and starts over.
    static let userHold = 3.0

    // A page leaves upwards while the next one is written word by word.
    static let out = 0.3
    static let outShift = 20.0
    static let wordsAfter = 0.3
    static let wordStagger = 0.06
    static let wordIn = 0.25
    static let wordShift = 8.0
    // Once the words are there, the key words light up one group after another, each with a flash.
    static let lightAfter = 0.15
    static let lightStagger = 0.3
    static let lightIn = 0.25
    static let flashOut = 0.5
    static let pop = 0.35
    // A page that continues the one before: the old words dim and move up to make room.
    static let dim = 0.3
    static let slide = 0.5
    /// Going back, or back to the first page, a sentence fades in whole.
    static let wholeIn = 0.35
    // The last page's button floods the screen with the glow before the first page returns.
    static let returnTime = 0.8
    static let flood = 0.35
    static let floodScale = 3.4
    static let floodFade = [0.3, 0.55]

    // The glow changes colour over `glowCross` and surges `surge` points up the screen on the way.
    static let glowCross = 0.8
    static let surge = 80.0
    static let surgeIn = 0.18
    static let surgeOut = 0.9
    // At rest its hot spot breathes and drifts; both periods divide `loop`.
    static let breath = 2.2
    static let drift = 15.0
    static let driftPeriod = 5.5
    static let hotLow = 0.6
    static let hotBreath = 0.17
    // Under a finger on the button the hot spot comes over and brightens.
    static let hotPress = 0.23
    static let pressIn = 0.2
    static let pressOut = 0.3
    /// The amber streak of the third page.
    static let amber = [1.0, 1.8]

    static func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

    static func smooth(_ t: Double, _ from: Double, _ to: Double) -> Double {
        let u = clamp01((t - from) / (to - from))
        return u * u * (3 - 2 * u)
    }

    static func easeIn(_ t: Double) -> Double {
        let u = clamp01(t)
        return u * u
    }

    static func easeOut(_ t: Double) -> Double {
        let u = 1 - clamp01(t)
        return 1 - u * u * u
    }

    static func easeInOut(_ t: Double) -> Double {
        let u = clamp01(t)
        return u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2
    }

    /// A page's block of text: its opacity and its offset. `rise` is how far a page that continues
    /// the one before starts below its place; `handsOver` says the next page continues this one,
    /// so this one gives way at once.
    static func blockPose(_ index: Int, rise: Double, handsOver: Bool, _ scene: Scene, _ t: Double, still: Bool) -> (opacity: Double, y: Double) {
        let e = t - scene.at
        if index == scene.page {
            if scene.how != .forward {
                let p = easeOut((e - (scene.how == .flooded ? returnTime - wholeIn : out)) / wholeIn)
                return (p, still ? 0 : wordShift * (1 - p))
            }
            return (e < 0 && rise > 0 ? 0 : 1, still ? 0 : rise * (1 - easeInOut(e / slide)))
        }
        if index != scene.from { return (0, 0) }
        if handsOver, scene.how == .forward { return (e < 0 ? 1 : 0, 0) }
        let p = easeIn(e / out)
        // Forwards the old page leaves upwards, backwards downwards.
        return (1 - p, still ? 0 : (scene.page < scene.from && scene.how == .whole ? 1 : -1) * outShift * p)
    }

    /// One word of the page that is showing. `order` is its place among the words that are new on
    /// the page (negative for a word kept from the page before), `count` how many are new, `key`
    /// its turn to light up (negative for a plain word), `was` whether it was lit on the page before.
    static func wordPose(order: Int, count: Int, key: Int, was: Bool, _ scene: Scene, _ t: Double, still: Bool) -> WordPose {
        let e = t - scene.at
        if scene.how != .forward { return WordPose(lit: key >= 0 ? 1 : 0) }
        if order < 0 { return WordPose(lit: was ? 1 - smooth(e, 0, dim) : 0) }
        let shown = easeOut((e - wordsAfter - (still ? 0 : Double(order) * wordStagger)) / wordIn)
        if key < 0 { return WordPose(opacity: shown, y: still ? 0 : wordShift * (1 - shown)) }
        let due = wordsAfter + Double(count) * wordStagger + lightAfter + Double(key) * lightStagger
        let lit = smooth(e, due, due + lightIn)
        return WordPose(
            opacity: shown,
            y: still ? 0 : wordShift * (1 - shown),
            lit: lit,
            flash: still ? 0 : lit * (1 - smooth(e, due + lightIn, due + lightIn + flashOut)),
            // A glyph is a little small until its word lights up, and overshoots as it grows.
            pop: still ? 1 : 0.8 + 0.2 * lit + 0.15 * sin(.pi * clamp01((e - due) / pop))
        )
    }

    /// A word of a page that is leaving, or of any page at rest.
    static func wordAtRest(key: Int) -> WordPose {
        WordPose(lit: key >= 0 ? 1 : 0)
    }

    /// One colour of the glow: its opacity, how far it has climbed, and its stretch from the
    /// bottom edge. `glow` and `before` are the colours of the page showing and of the one before it.
    static func glowPose(color: Int, glow: Int, before: Int, _ scene: Scene, _ t: Double, still: Bool) -> (opacity: Double, lift: Double, stretch: Double) {
        let e = t - scene.at
        let flooded = scene.how == .flooded
        let arrived = flooded ? smooth(e, returnTime - 0.3, returnTime) : smooth(e, 0, glowCross)
        let left = flooded ? 1 - smooth(e, floodFade[0], floodFade[1]) : 1 - arrived
        let opacity: Double = glow == before ? (color == glow ? 1 : 0) : color == glow ? arrived : color == before ? left : 0
        let lift = still || scene.how != .forward ? 0 : surge * smooth(e, 0, surgeIn) * (1 - easeOut(e / surgeOut))
        let stretch = !still && flooded && color == before && color != glow ? 1 + (floodScale - 1) * easeOut(e / flood) : 1
        return (opacity, lift, stretch)
    }

    /// The amber streak: there on a page that has it, gone soon after it is left.
    static func amberAt(has: Bool, had: Bool, _ scene: Scene, _ t: Double) -> Double {
        let e = t - scene.at
        if has { return scene.how == .forward ? smooth(e, amber[0], amber[1]) : smooth(e, 0, glowCross) }
        return had ? 1 - smooth(e, 0.2, glowCross) : 0
    }

    /// How much of a press the button shows, 0 to 1.
    static func pressAt(_ press: Press, _ t: Double) -> Double {
        press.down ? easeOut((t - press.at) / pressIn) : 1 - easeOut((t - press.at) / pressOut)
    }

    /// The hot spot: how bright it is, and where its middle is for a resting place `home`.
    static func hotAt(home: Double, _ press: Press, _ t: Double, still: Bool) -> (light: Double, x: Double) {
        let pressed = pressAt(press, t)
        let breathing = still ? 0.5 : 0.5 - 0.5 * cos(t / breath * 2 * .pi)
        let drifted = still ? 0 : drift * sin(t / driftPeriod * 2 * .pi)
        return (hotLow + hotBreath * breathing + hotPress * pressed, home + drifted + (press.x - home - drifted) * pressed)
    }
}
