import Foundation

/// The carousel's spring: where it is going (a whole card, counted without end), from where, how
/// fast it was let go, and when.
struct SkyGlassStepsSlide: Equatable {
    var page: Int
    var from: Double
    var velocity: Double
    var at: Double
}

/// The card whose preview plays: its page, the step that had the focus before and for how long,
/// and when the focus changed. `warm` means the card kept the focus and only starts over, so
/// nothing comes in again; `row` is where its white fill was then.
struct SkyGlassStepsFocus: Equatable {
    var page: Int
    var was: Int
    var held: Double
    var at: Double
    var warm: Bool
    var row: Double
}

/// A row picked by hand: the fill goes there from where it was. No row is -1.
struct SkyGlassStepsPick: Equatable {
    var row: Int
    var from: Double
    var at: Double
}

/// The headline's drag: how far the finger has pulled, and when it let go (negative while it holds).
struct SkyGlassStepsPull: Equatable {
    var x: Double
    var released: Double
}

/// Everything on the screen is a function of the clock and of the last few events (a swipe, a
/// change of the step in focus, a tap), so a finger and the loop that plays by itself make exactly
/// the same pictures. Times are in seconds, lengths in points.
enum SkyGlassStepsMotion {
    /// autoPlay: one pass through the three steps every `loop` seconds. The sky repeats in the same time.
    static let loop = 9.0
    /// autoPlay swipes on at these moments; the last swipe brings the first step round again.
    static let autoSwipes = [2.2, 5.2, 8.2]
    /// How long the first step has been in focus when a loop starts: its rows are in and it is still.
    static let lead = loop - autoSwipes[2]
    /// After a touch autoPlay waits this long before it goes back to the first step and starts over.
    static let userHold = 3.0

    static let steps = 3
    /// From one card to the next: a card's width and the gap.
    static let pitch = 314.0

    // The carousel: a spring with almost no bounce (response 0.5 s, damping ratio 0.85).
    static let slideMass = 1.0
    static let slideStiffness = 158.0
    static let slideDamping = 21.4
    /// After this long the spring is where it was going.
    static let slideRest = 1.2
    /// How far ahead a flick looks for the card to stop on, and the fastest flick, in cards a second.
    static let flick = 0.2
    static let flickMax = 8.0

    // A preview comes in when its card takes the focus: each part rises and fades in, one after another.
    static let enterAfter = 0.1
    static let enterRise = 0.45
    static let enterFade = 0.25
    /// The preview of the card that lost the focus fades out in this time.
    static let leaveFade = 0.25
    /// With reduced motion, and for anything that only fades.
    static let fade = 0.2

    // Step 1: four rows; the white fill steps down them once.
    static let rowStagger = 0.06
    static let rowShift = 16.0
    static let rowFirst = 1.2
    static let rowEvery = 0.55
    static let rowSlide = 0.3

    // Step 2: three lesson cards, back to front; the front one lifts and settles once.
    static let lessonStagger = 0.08
    static let lessonShift = 30.0
    static let liftAfter = 1.3
    static let liftFor = 0.6
    static let lift = 4.0

    // Step 3: the panel grows in, the checks tick in reading order, the badge pulses once.
    static let panelIn = 0.35
    static let panelScale = 0.96
    static let tickFirst = 0.7
    static let tickEvery = 0.15
    static let tickFor = 0.3
    static let tickPop = 0.2
    static let untickFor = 0.15
    static let pulseAfter = 0.2
    static let pulseFor = 0.45
    static let pulse = 0.14

    // The headline moves against a dragging finger by up to this much, and goes back when it lifts.
    static let parallax = 6.0
    static let parallaxBack = 0.3

    /// The longest a preview plays after its card took the focus; the clock may stop after this.
    static let quiet = 3.5

    static func clamp01(_ t: Double) -> Double {
        min(1, max(0, t))
    }

    static func smooth(_ t: Double, _ from: Double, _ to: Double) -> Double {
        let u = clamp01((t - from) / (to - from))
        return u * u * (3 - 2 * u)
    }

    static func easeOut(_ t: Double) -> Double {
        let u = 1 - clamp01(t)
        return 1 - u * u * u
    }

    static func easeInOut(_ t: Double) -> Double {
        let u = clamp01(t)
        return u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2
    }

    /// One smooth rise and fall over 0…1, flat at both ends.
    static func bump(_ t: Double) -> Double {
        (1 - cos(2 * .pi * clamp01(t))) / 2
    }

    /// The step a page shows: pages go on without end in both directions.
    static func stepOf(_ page: Int) -> Int {
        ((page % steps) + steps) % steps
    }

    /// How many cards a step sits from the middle of the screen, the short way round.
    static func offsetOf(_ step: Int, _ position: Double) -> Double {
        let d = Double(step) - position
        return d - Double(steps) * (d / Double(steps)).rounded()
    }

    /// Where the carousel is, in cards. With reduced motion it is where it is going.
    static func slideAt(_ slide: SkyGlassStepsSlide, _ t: Double, still: Bool) -> Double {
        let e = t - slide.at
        let page = Double(slide.page)
        if still || e >= slideRest { return page }
        if e <= 0 { return slide.from }
        let omega = (slideStiffness / slideMass).squareRoot()
        let decay = slideDamping / (2 * slideMass)
        let damped = (omega * omega - decay * decay).squareRoot()
        let a = slide.from - page
        let b = (slide.velocity + decay * a) / damped
        return page + exp(-decay * e) * (a * cos(damped * e) + b * sin(damped * e))
    }

    /// The clock of a step's preview: seconds since its card took the focus, how visible it is, and
    /// whether its parts come in. The card that lost the focus stays as it was and fades; the third
    /// one is hidden.
    static func previewAt(_ step: Int, _ focus: SkyGlassStepsFocus, _ t: Double) -> (e: Double, shown: Double, warm: Bool) {
        let since = t - focus.at
        if step == stepOf(focus.page) { return (since, 1, focus.warm) }
        if step == focus.was { return (focus.held, 1 - clamp01(since / leaveFade), false) }
        return (0, 0, false)
    }

    /// One part coming in, `delay` after the others started: its opacity and how far below its place it is.
    static func enter(_ e: Double, delay: Double, shift: Double, warm: Bool, still: Bool) -> (opacity: Double, y: Double) {
        if warm { return (1, 0) }
        let since = e - enterAfter - delay
        if still { return (clamp01(since / fade), 0) }
        return (clamp01(since / enterFade), shift * (1 - easeOut(since / enterRise)))
    }

    /// Step 1: the row the white fill is on, 0 to 3; between rows while it slides.
    static func rowAt(_ e: Double, from: Double, _ pick: SkyGlassStepsPick, _ t: Double, still: Bool) -> Double {
        if pick.row >= 0 {
            let row = Double(pick.row)
            return still ? row : pick.from + (row - pick.from) * easeInOut((t - pick.at) / rowSlide)
        }
        let steps = Double(min(3, max(0, Int(((e - rowFirst) / rowEvery).rounded(.down)) + 1)))
        // A card that starts over brings its fill back to the first row.
        if steps == 0 { return still ? 0 : from * (1 - easeInOut(e / rowSlide)) }
        if still { return steps }
        return steps - 1 + easeInOut((e - rowFirst - (steps - 1) * rowEvery) / rowSlide)
    }

    /// Step 2: how far the front lesson card is lifted.
    static func liftAt(_ e: Double, still: Bool) -> Double {
        still ? 0 : lift * bump((e - liftAfter) / liftFor)
    }

    /// Step 3: the panel's opacity and scale.
    static func panelAt(_ e: Double, warm: Bool, still: Bool) -> (opacity: Double, scale: Double) {
        if warm { return (1, 1) }
        let since = e - enterAfter
        if still { return (clamp01(since / fade), 1) }
        return (smooth(since, 0, panelIn), panelScale + (1 - panelScale) * easeOut(since / panelIn))
    }

    /// Step 3: how checked a square is, 0 to 1, and how much it swells while it is being checked.
    /// `order` is its place among the squares that tick by themselves (-1: it stays empty), `toggled`
    /// the time a finger last changed it: positive for checked, negative for cleared, 0 for never.
    static func tickAt(_ e: Double, order: Int, toggled: Double, _ t: Double, still: Bool) -> (q: Double, pop: Double) {
        var q = 0.0
        var rising = true
        if toggled > 0 {
            q = clamp01((t - toggled) / tickFor)
        } else if toggled < 0 {
            q = 1 - clamp01((t + toggled) / untickFor)
            rising = false
        } else if order >= 0 {
            q = clamp01((e - tickFirst - Double(order) * tickEvery) / tickFor)
        }
        return (q, still || !rising ? 1 : 1 + tickPop * sin(.pi * q))
    }

    /// Step 3: the badge's scale; it pulses once after the last square that ticks by itself.
    static func pulseAt(_ e: Double, ticks: Int, still: Bool) -> Double {
        if still { return 1 }
        let from = tickFirst + Double(max(0, ticks - 1)) * tickEvery + tickFor + pulseAfter
        return 1 + pulse * bump((e - from) / pulseFor)
    }

    /// How far the headline stands off its place.
    static func parallaxAt(_ pull: SkyGlassStepsPull, _ t: Double, still: Bool) -> Double {
        if still { return 0 }
        let back = pull.released < 0 ? 1 : 1 - easeOut((t - pull.released) / parallaxBack)
        return -parallax * sin(.pi / 2 * max(-1, min(1, pull.x / pitch))) * back
    }
}
