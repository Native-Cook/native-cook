import Foundation

/// The stage is drawn for a screen 393 by 852 points and scaled to the screen's width; every pose
/// is a function of the time since something happened, so a touch and the loop that plays by
/// itself make exactly the same pictures. Lengths are in the stage's points, times in seconds.
enum BookmarkPocketIntroMotion {
    /// Which picture is up, since when, and how long the rings had been turning by then.
    struct Mode: Equatable {
        var orbit: Bool
        var at: Double
        var spun: Double
    }

    /// A card let go: when, where its lower edge was, how far aside, and how strongly the pocket glows for it.
    struct Drop: Equatable {
        var at = -100.0
        var bottom = 0.0
        var dx = 0.0
        var glow = 0.0
    }

    /// The card waiting at the top edge: whether it is there, and since when.
    struct Wait: Equatable {
        var on: Bool
        var at: Double
    }

    /// How far a finger has pulled the waiting card; `at` is when it was let go without being fed.
    struct Hold: Equatable {
        var dx = 0.0
        var dy = 0.0
        var at = -100.0
    }

    /// The tagline goes from one text to another from `at` on.
    struct Tagline: Equatable {
        var from: String
        var to: String
        var at: Double
    }

    struct BookmarkPose {
        var scale: Double
        /// Degrees.
        var tilt: Double
        /// How far the bookmark is on its way to the hub, 0 to 1.
        var lift: Double
        var opacity: Double
    }

    struct IconPose {
        var x: Double
        var y: Double
        var scale: Double
        var opacity: Double
    }

    struct CardPose {
        var x: Double
        var bottom: Double
        var scale: Double
        var dark: Double
        var opacity: Double
    }

    /// autoPlay: three cards are fed, the bookmark is tapped, and the screen goes back to rest.
    static let loop = 9.0
    static let autoFeeds = [0, 0.3, 0.6]
    static let autoOrbit = 1.2
    static let autoRest = 7.5
    /// After a touch autoPlay waits this long before it goes back to rest and starts over.
    static let userHold = 3.0

    static let stageWidth = 393.0
    static let stageHeight = 852.0
    static let centerX = stageWidth / 2
    static let bookWidth = 177.0
    static let bookHeight = 213.0
    static let bookTop = 320.0
    // The hub of the orbits, and the bookmark there: 70 points wide and leaning to the right.
    static let orbitY = 355.0
    static let bookSmall = 70 / bookWidth
    static let tilt = 15.0
    static let rings = [107.5, 182.5]
    /// Degrees a second: the inner ring turns clockwise, the outer one the other way.
    static let spins = [6.0, -4.0]

    /// A card let go with its lower edge at the top of the stage reaches the pocket 0.45 s later.
    static let gravity = 3160.0
    // Near the pocket a card is smaller and darker; a card dragged aside is pulled back over it.
    static let cardShrink = 0.85
    static let cardDark = 0.25
    static let shrinkFrom = 100.0
    static let suck = 0.2
    // How much of the waiting card shows under the top edge, and how it comes and goes.
    static let peek = 106.0
    static let waitIn = 0.3
    static let waitOut = 0.2
    static let waitAfter = 0.25
    static let back = 0.25
    /// A waiting card pulled down by less than this goes back; by more, it falls in.
    static let feedAfter = 40.0

    // The gulp: the sides bow out by `bulge` and the top edge dips, then both swing back once.
    static let bulge = 9.5
    static let dip = 4.0
    static let gulpDecay = 9.0
    static let gulpSpeed = 13.0
    private static let gulpGain: Double = {
        let peak = atan(gulpSpeed / gulpDecay) / gulpSpeed
        return 1 / (exp(-gulpDecay * peak) * sin(gulpSpeed * peak))
    }()
    static let glowIn = 0.12
    static let glowHold = 0.2
    static let glowOut = 0.5

    // To the orbits: the bookmark shrinks, springs up towards the viewer as it leans, and drops to
    // the hub while the rings open and the icons fly out one after another.
    static let shrink = 0.4
    static let shrunk = 0.62
    static let lift = 0.4
    static let lifted = 1.5
    static let liftStiffness = 324.0
    static let liftDamping = 18.0
    static let drop = 0.5
    static let orbitIn = shrink + lift + drop
    static let ringsIn = 0.4
    static let iconStagger = 0.04
    static let iconOut = 0.35
    static let iconStiffness = 400.0
    static let iconDamping = 22.0
    // Back to the pocket: icons and rings fall into the bookmark, which grows and straightens.
    static let collapse = 0.4
    static let collapseStagger = 0.02
    static let growAfter = 0.3
    static let grow = 0.5
    static let pocketIn = growAfter + grow
    /// Without motion one picture fades out and the other in.
    static let swap = 0.3

    // Characters a second; each letter is up to `typeJitter` of a letter early or late.
    static let typeAfter = 0.2
    static let typeRate = 28.0
    static let typeJitter = 0.35
    static let deleteRate = 60.0
    static let blink = 0.5
    static let lineShift = 0.2

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

    /// A spring of mass 1 from 0 to 1, t seconds after it was let go.
    static func spring(_ t: Double, stiffness: Double, damping: Double) -> Double {
        guard t > 0 else { return 0 }
        let omega = stiffness.squareRoot()
        let zeta = damping / (2 * omega)
        let damped = omega * (1 - zeta * zeta).squareRoot()
        return 1 - exp(-zeta * omega * t) * (cos(damped * t) + (zeta * omega / damped) * sin(damped * t))
    }

    /// A number between 0 and 1 that is always the same for the same n.
    static func hash(_ n: Int) -> Double {
        let x = sin(Double(n) * 127.1 + 311.7) * 43758.5453
        return x - x.rounded(.down)
    }

    /// With reduced motion: which picture shows `e` seconds after a change, and how opaque it is.
    private static func swapped(_ orbit: Bool, _ e: Double) -> (orbit: Bool, opacity: Double) {
        let q = clamp01(e / swap)
        return (q >= 0.5 ? orbit : !orbit, abs(1 - 2 * q))
    }

    static func bookmarkPose(_ mode: Mode, _ t: Double, still: Bool) -> BookmarkPose {
        let e = t - mode.at
        if still {
            let now = swapped(mode.orbit, e)
            return BookmarkPose(scale: now.orbit ? bookSmall : 1, tilt: now.orbit ? tilt : 0, lift: now.orbit ? 1 : 0, opacity: now.opacity)
        }
        if mode.orbit {
            let up = spring(e - shrink, stiffness: liftStiffness, damping: liftDamping)
            let raised = 1 + (shrunk - 1) * easeInOut(e / shrink) + (lifted - shrunk) * up
            return BookmarkPose(
                scale: raised + (bookSmall - raised) * easeInOut((e - shrink - lift) / drop),
                tilt: tilt * up,
                lift: easeInOut((e - shrink) / (lift + drop)),
                opacity: 1
            )
        }
        let grown = easeInOut((e - growAfter) / grow)
        return BookmarkPose(scale: bookSmall + (1 - bookSmall) * grown, tilt: tilt * (1 - grown), lift: 1 - grown, opacity: 1)
    }

    /// How open the rings are, 0 to 1.
    static func ringsAt(_ mode: Mode, _ t: Double, still: Bool) -> Double {
        let e = t - mode.at
        if still {
            let now = swapped(mode.orbit, e)
            return now.orbit ? now.opacity : 0
        }
        return mode.orbit ? easeOut((e - shrink - lift) / ringsIn) : 1 - easeIn(e / collapse)
    }

    /// One icon: its offset from the hub, its scale and its opacity. `turn` is what a finger added, in degrees.
    static func iconPose(_ index: Int, ring: Int, angle: Double, _ mode: Mode, _ t: Double, turn: Double, still: Bool) -> IconPose {
        let e = t - mode.at
        if still {
            let now = swapped(mode.orbit, e)
            let at = (angle + turn) * .pi / 180
            return IconPose(x: rings[ring] * cos(at), y: rings[ring] * sin(at), scale: 1, opacity: now.orbit ? now.opacity : 0)
        }
        let out: Double
        let scale: Double
        let spun: Double
        if mode.orbit {
            let since = e - shrink - lift - Double(index) * iconStagger
            out = easeOut(since / iconOut)
            scale = spring(since, stiffness: iconStiffness, damping: iconDamping)
            spun = max(0, e - shrink - lift)
        } else {
            out = 1 - easeIn((e - Double(index) * collapseStagger) / (collapse - 8 * collapseStagger))
            scale = out
            spun = mode.spun + e
        }
        let at = (angle + turn + spins[ring] * spun) * .pi / 180
        return IconPose(x: rings[ring] * out * cos(at), y: rings[ring] * out * sin(at), scale: scale, opacity: smooth(scale, 0, 0.15))
    }

    /// When the lower edge of a dropped card reaches the pocket.
    private static func gulpStart(_ drop: Drop) -> Double {
        drop.at + max(0, 2 * (bookTop - drop.bottom) / gravity).squareRoot()
    }

    /// How far the bookmark's sides bow out, in points (the top edge dips in proportion), and how
    /// strong the glow under it is.
    static func gulpAt(_ drops: [Drop], _ t: Double, still: Bool) -> (bulge: Double, glow: Double) {
        var bulge = 0.0
        var glow = 0.0
        for drop in drops {
            let u = t - gulpStart(drop)
            if u <= 0 { continue }
            bulge += Self.bulge * gulpGain * exp(-gulpDecay * u) * sin(gulpSpeed * u)
            glow += drop.glow * smooth(u, 0, glowIn) * (1 - smooth(u, glowHold, glowHold + glowOut))
        }
        return (still ? 0 : bulge, min(1, glow))
    }

    /// A card's scale for the place of its lower edge.
    static func cardScale(_ bottom: Double) -> Double {
        1 - (1 - cardShrink) * smooth(bottom, shrinkFrom, bookTop)
    }

    /// One card: where its lower edge is, how far aside it is, its scale about that edge, how dark
    /// it is and its opacity. `top` is the stage's y at the top of the screen; `waiting` says the
    /// card is the one at the top edge.
    static func cardPose(height: Double, waiting: Bool, _ wait: Wait, _ hold: Hold, _ drop: Drop, top: Double, _ t: Double, still: Bool) -> CardPose {
        let shown = wait.on ? easeOut((t - wait.at) / waitIn) : 1 - easeIn((t - wait.at) / waitOut)
        if waiting, shown > 0 {
            // Pulled by a finger, or on its way back after being let go too early.
            let pull = 1 - easeOut((t - hold.at) / back)
            let bottom = top + peek * (still ? 1 : shown) + hold.dy * pull
            let scale = cardScale(bottom)
            return CardPose(x: hold.dx * pull, bottom: bottom, scale: scale, dark: cardDark * (1 - scale) / (1 - cardShrink), opacity: still ? shown : 1)
        }
        let e = t - drop.at
        if e < 0 { return CardPose(x: 0, bottom: top, scale: 1, dark: 0, opacity: 0) }
        // Without motion a card that was fed fades where it is.
        if still { return CardPose(x: drop.dx, bottom: drop.bottom, scale: cardScale(drop.bottom), dark: 0, opacity: 1 - clamp01(e / suck)) }
        let bottom = drop.bottom + gravity * e * e / 2
        let scale = cardScale(bottom)
        // Gone once its upper edge is behind the bookmark.
        let opacity: Double = bottom - height * scale < bookTop + 10 ? 1 : 0
        return CardPose(x: drop.dx * (1 - easeOut(e / suck)), bottom: bottom, scale: scale, dark: cardDark * (1 - scale) / (1 - cardShrink), opacity: opacity)
    }

    private static func commonPrefix(_ a: [Character], _ b: [Character]) -> Int {
        var n = 0
        while n < a.count, n < b.count, a[n] == b[n] { n += 1 }
        return n
    }

    /// When the tagline has finished changing.
    static func typedBy(_ tagline: Tagline) -> Double {
        let (from, to) = (Array(tagline.from), Array(tagline.to))
        let kept = commonPrefix(from, to)
        return tagline.at + Double(from.count - kept) / deleteRate + (Double(to.count - kept) + typeJitter) / typeRate
    }

    /// The tagline as it reads at t: the old text is deleted from its end down to what the two
    /// share, then the new one is typed.
    static func typedAt(_ tagline: Tagline, _ t: Double, still: Bool) -> String {
        let e = t - tagline.at
        if e < 0 { return tagline.from }
        if still { return tagline.to }
        let (from, to) = (Array(tagline.from), Array(tagline.to))
        let kept = commonPrefix(from, to)
        let deleting = Double(from.count - kept) / deleteRate
        if e < deleting { return String(from.prefix(from.count - Int(e * deleteRate))) }
        // Letter n is due at (n + its own lateness) / typeRate.
        let due = (e - deleting) * typeRate
        var count = max(0, Int(due.rounded(.down)) - 1)
        while count < to.count - kept, Double(count) + (hash(count) * 2 - 1) * typeJitter <= due { count += 1 }
        return String(to.prefix(kept + count))
    }
}
