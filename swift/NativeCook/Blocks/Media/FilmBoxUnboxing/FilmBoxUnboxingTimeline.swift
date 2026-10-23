import Foundation

/// When each action happened, in seconds of the screen's clock; -1 when it has not.
struct FilmBoxUnboxingEvents: Equatable {
    var unbox = -1.0
    var load = -1.0
    var shot = -1.0
    var retake = -1.0
    var reset = -1.0

    static let none = FilmBoxUnboxingEvents()
}

/// What the controls on screen accept.
enum FilmBoxUnboxingStage {
    case busy, box, film, camera, photo
}

/// Everything on screen at one moment.
struct FilmBoxUnboxingScene {
    var stage = FilmBoxUnboxingStage.box
    var header = 1.0
    /// The pill's ring, knob and label.
    var pill = 1.0
    var spinner = 0.0
    /// Seconds the spinner has turned.
    var spinnerTime = 0.0
    var boxAlpha = 1.0
    /// The box's sway, in degrees and points.
    var yaw = 0.0
    var roll = 0.0
    var lift = 0.0
    var tilt = 0.0
    var flap = 0.0
    var glow = 0.0
    /// Where the streak of light is across the box, 0 to 1; outside that range it is not drawn.
    var sweep = -1.0
    var dive = 0.0
    var name = 0.0
    var canister = 0.0
    var canisterScale = 1.0
    /// 0 on the card, 1 at the top of the camera.
    var fly = 0.0
    var spin = 0.0
    var rows = [Double](repeating: 0, count: FilmBoxUnboxingTimeline.rows)
    var cardOut = 0.0
    var grey = 0.0
    var loadScale = 1.0
    /// Seconds since the card came in, which the expiry counts down from.
    var shown = 0.0
    var camera = 0.0
    var cameraRow = 1.0
    var photoRow = 0.0
    var shutter = 1.0
    var flash = 0.0
    var photo = 0.0
    var photoScale = 1.0
    var overlay = 0.0
}

/// The screen as a function of time. Every moving part reads `scene(at:)`, which turns the clock
/// and the moments of the five actions (unbox, load, shoot, retake, reset) into opacities, angles
/// and scales; touches and the autoPlay loop only write those moments.
enum FilmBoxUnboxingTimeline {
    /// Seconds of one autoPlay loop.
    static let loop = 10.0
    /// Seconds without a touch, once the screen has settled, before it goes back to rest and plays again.
    static let userHold = 3.0

    // The autoPlay loop, in seconds from its start: rest, drag the knob, open, read the card, load,
    // shoot, look at the photo, then fade through black to the closed box.
    static let dragAt = 1.4
    static let dragFor = 0.8
    static let unboxAt = dragAt + dragFor
    static let loadAt = 6.6
    static let shootAt = 7.7
    static let resetAt = 9.6
    /// Each half of the fade through black that puts the box back.
    static let resetHalf = 0.2

    // The idle box bobs and sways on a 2.5 s period, a quarter of `loop`, so every loop sees the same sway.
    static let bob = 2.5
    static let bobY = 4.0
    static let bobRoll = 1.5
    static let bobYaw = 3.0

    // Opening, in seconds from the release of the knob.
    static let pillOut = 0.2
    static let spinnerAt = 0.1
    static let spinnerIn = 0.2
    static let spinnerOut = 0.2
    /// Degrees the box tips towards the viewer as it opens, on a spring.
    static let tilt = 16.0
    static let tiltResponse = 0.4
    static let tiltDamping = 0.6
    static let flapsAt = 0.08
    static let flapsFor = 0.5
    /// Degrees the flaps swing open, past upright.
    static let flapOpen = 110.0
    static let glowAt = 0.35
    static let glowFor = 0.35
    /// The streak of light that crosses the box.
    static let sweepAt = 0.3
    static let sweepFor = 0.45
    /// The view dives into the open box.
    static let diveAt = 0.7
    static let diveFor = 0.5
    static let diveScale = 4.0
    static let boxOutAt = 0.95
    static let boxOut = 0.25
    static let headerOutAt = 0.7
    static let headerOut = 0.25
    static let nameAt = 1.15
    static let nameIn = 0.25
    static let revealAt = 1.7

    // The card, in seconds from `revealAt`.
    static let canisterIn = 0.25
    static let canisterFrom = 0.6
    static let canisterResponse = 0.5
    static let canisterDamping = 0.7
    /// The name's description, the four rows and the button come in one after the other.
    static let rows = 6
    static let rowsAt = 0.1
    static let rowStagger = 0.06
    static let rowIn = 0.3
    static let rowLift = 8.0
    /// The canister turns once about its long axis, then rests, every `spinPeriod`.
    static let spinAt = 0.4
    static let spinTurn = 2.2
    static let spinPeriod = 3.0
    static let openSettled = revealAt + 0.7

    // Loading, in seconds from the tap on Load film.
    static let loadGrey = 0.1
    static let cardOutAt = 0.05
    static let cardOut = 0.25
    static let fly = 0.55
    /// The canister's size at the top of the camera.
    static let small = 0.3
    static let cameraInAt = 0.25
    static let cameraIn = 0.3
    static let cameraLift = 24.0
    static let loadSettled = 0.6

    // Shooting, in seconds from the tap on the shutter, and retaking.
    static let press = 0.15
    static let pressScale = 0.9
    static let buttonPressScale = 0.94
    static let flash = 0.12
    static let flashAlpha = 0.3
    static let photoAt = 0.05
    static let photoIn = 0.4
    static let photoFrom = 1.1
    static let cameraRowOutAt = 0.1
    static let cameraRowOut = 0.2
    static let photoRowInAt = 0.2
    static let photoRowIn = 0.25
    static let shotSettled = 0.5
    static let photoOut = 0.25
    static let retakeSettled = 0.4

    static func clamp01(_ x: Double) -> Double { min(max(x, 0), 1) }

    /// 0 before `from`, 1 after `to`, a smoothstep between.
    static func smooth(_ t: Double, _ from: Double, _ to: Double) -> Double {
        let x = clamp01((t - from) / (to - from))
        return x * x * (3 - 2 * x)
    }

    static func easeOut(_ x: Double) -> Double {
        let k = 1 - clamp01(x)
        return 1 - k * k * k
    }

    static func easeIn(_ x: Double) -> Double {
        let k = clamp01(x)
        return k * k * k
    }

    static func easeInOut(_ x: Double) -> Double {
        let k = clamp01(x)
        return k < 0.5 ? 4 * k * k * k : 1 - pow(-2 * k + 2, 3) / 2
    }

    /// A spring from 0 to 1 after `t` seconds: `response` is the period of the undamped spring,
    /// `damping` the fraction of critical damping (below 1), as in `Animation.spring`.
    static func spring(_ t: Double, response: Double, damping: Double) -> Double {
        guard t > 0 else { return 0 }
        let w0 = tau / response
        let wd = w0 * (1 - damping * damping).squareRoot()
        let decay = exp(-damping * w0 * t)
        return 1 - decay * (cos(wd * t) + (damping * w0 / wd) * sin(wd * t))
    }

    /// Up and back down once over `length` seconds.
    private static func pulse(_ t: Double, _ length: Double) -> Double {
        t < 0 || t > length ? 0 : sin(.pi * t / length)
    }

    /// The canister's turn, `t` seconds after the card came in.
    private static func spin(_ t: Double, still: Bool) -> Double {
        if still || t < spinAt { return 0 }
        return easeInOut((t - spinAt).truncatingRemainder(dividingBy: spinPeriod) / spinTurn) * tau
    }

    /// Everything on screen at `now`, from the moments of the actions.
    static func scene(at now: Double, events ev: FilmBoxUnboxingEvents, still: Bool) -> FilmBoxUnboxingScene {
        let resetting = ev.reset >= 0 ? now - ev.reset : -1
        // Past the darkest moment of the fade the box is back, closed.
        let cleared = resetting >= resetHalf
        var overlay = 0.0
        if resetting >= 0 && resetting < resetHalf { overlay = smooth(resetting, 0, resetHalf) }
        else if resetting >= resetHalf { overlay = 1 - smooth(resetting, resetHalf, resetHalf * 2) }

        let o = !cleared && ev.unbox >= 0 ? now - ev.unbox : -1
        let l = o >= 0 && ev.load > ev.unbox ? now - ev.load : -1
        let shotOn = l >= 0 && ev.shot > ev.load && ev.shot > ev.retake
        let s = shotOn ? now - ev.shot : -1
        let r = l >= 0 && ev.retake > ev.load && ev.retake > ev.shot ? now - ev.retake : -1
        let v = o - revealAt

        let phase = now / bob * tau
        let sway = still ? 0.0 : 1.0

        var out = FilmBoxUnboxingScene()
        for i in 0..<rows {
            let at = rowsAt + Double(i) * rowStagger
            out.rows[i] = o < 0 ? 0 : still ? smooth(v, rowsAt, rowsAt + rowIn) : smooth(v, at, at + rowIn)
        }

        var turn = spin(v, still: still)
        var fly = 0.0
        var canister = o < 0 ? 0 : smooth(v, 0, canisterIn)
        if l >= 0 {
            // The canister finishes its turn on the way up, the short way round.
            let from = spin(ev.load - ev.unbox - revealAt, still: still)
            let to = from > .pi ? tau : 0
            if still {
                fly = l >= Self.fly / 2 ? 1 : 0
                canister = 1 - smooth(l, 0, Self.fly / 2) + smooth(l, Self.fly / 2, Self.fly)
            } else {
                fly = easeInOut(l / Self.fly)
            }
            turn = from + (to - from) * fly
        }

        var stage = FilmBoxUnboxingStage.camera
        if resetting >= 0 && resetting < resetHalf * 2 { stage = .busy }
        else if o < 0 { stage = .box }
        else if l < 0 { stage = v >= rowsAt + rowIn ? .film : .busy }
        else if l < loadSettled { stage = .busy }
        else if shotOn { stage = s >= 0.3 ? .photo : .busy }
        else if r >= 0 && r < 0.3 { stage = .busy }

        out.stage = stage
        out.header = o < 0 ? 1 : 1 - smooth(o, headerOutAt, headerOutAt + headerOut)
        out.pill = o < 0 ? 1 : 1 - smooth(o, 0, pillOut)
        out.spinner = o < 0 ? 0 : smooth(o, spinnerAt, spinnerAt + spinnerIn) * (1 - smooth(o, revealAt, revealAt + spinnerOut))
        out.spinnerTime = max(0, o)
        out.boxAlpha = o < 0 ? 1 : still ? 1 - smooth(o, 0.3, 0.6) : 1 - smooth(o, boxOutAt, boxOutAt + boxOut)
        out.yaw = bobYaw * cos(phase) * sway
        out.roll = bobRoll * sin(phase) * sway
        out.lift = -bobY * sin(phase) * sway
        out.tilt = o < 0 || still ? 0 : tilt * spring(o, response: tiltResponse, damping: tiltDamping)
        out.flap = o < 0 || still ? 0 : flapOpen * easeOut((o - flapsAt) / flapsFor)
        out.glow = o < 0 || still ? 0 : smooth(o, glowAt, glowAt + glowFor)
        out.sweep = o < 0 || still ? -1 : (o - sweepAt) / sweepFor
        out.dive = o < 0 || still ? 0 : easeIn((o - diveAt) / diveFor)
        out.name = o < 0 ? 0 : smooth(o, nameAt, nameAt + nameIn)
        out.canister = canister
        out.canisterScale = still || o < 0 ? 1 : canisterFrom + (1 - canisterFrom) * spring(v, response: canisterResponse, damping: canisterDamping)
        out.fly = fly
        out.spin = turn
        out.cardOut = l < 0 ? 0 : smooth(l, cardOutAt, cardOutAt + cardOut)
        out.grey = l < 0 ? 0 : smooth(l, 0, loadGrey)
        out.loadScale = l < 0 || still ? 1 : 1 - (1 - buttonPressScale) * pulse(l, press)
        out.shown = max(0, v)
        out.camera = l < 0 ? 0 : smooth(l, cameraInAt, cameraInAt + cameraIn)
        out.cameraRow = shotOn ? 1 - smooth(s, cameraRowOutAt, cameraRowOutAt + cameraRowOut) : r >= 0 ? smooth(r, 0.1, 0.35) : 1
        out.photoRow = shotOn ? smooth(s, photoRowInAt, photoRowInAt + photoRowIn) : r >= 0 ? 1 - smooth(r, 0, 0.2) : 0
        out.shutter = shotOn && !still ? 1 - (1 - pressScale) * pulse(s, press) : 1
        out.flash = shotOn && s < flash ? flashAlpha * (1 - s / flash) : 0
        out.photo = shotOn ? smooth(s, photoAt, photoAt + photoIn) : r >= 0 ? 1 - smooth(r, 0, photoOut) : 0
        out.photoScale = shotOn && !still ? photoFrom - (photoFrom - 1) * easeOut((s - photoAt) / photoIn) : 1
        out.overlay = overlay
        return out
    }

    /// When the last action's motion ends; the screen counts its idle time from there.
    static func settledAt(_ ev: FilmBoxUnboxingEvents) -> Double {
        max(
            ev.unbox < 0 ? -1 : ev.unbox + openSettled,
            ev.load < 0 ? -1 : ev.load + loadSettled,
            ev.shot < 0 ? -1 : ev.shot + shotSettled,
            ev.retake < 0 ? -1 : ev.retake + retakeSettled,
            ev.reset < 0 ? -1 : ev.reset + resetHalf * 2
        )
    }

    /// The actions of the autoPlay loop `local` seconds into a loop that started at `base`.
    static func loopEvents(_ local: Double, base: Double) -> FilmBoxUnboxingEvents {
        FilmBoxUnboxingEvents(
            unbox: local >= unboxAt ? base + unboxAt : -1,
            load: local >= loadAt ? base + loadAt : -1,
            shot: local >= shootAt ? base + shootAt : -1,
            retake: -1,
            reset: local >= resetAt ? base + resetAt : -1
        )
    }

    /// Where the loop's finger holds the knob, 0 to 1; under reduced motion it jumps.
    static func loopDrag(_ local: Double, still: Bool) -> Double {
        if local < dragAt || local >= resetAt + resetHalf { return 0 }
        if still { return 1 }
        return easeInOut((local - dragAt) / dragFor)
    }

    /// "17h 21m 58s".
    static func countdown(_ seconds: Int) -> String {
        let left = max(0, seconds)
        return String(format: "%dh %02dm %02ds", left / 3600, left % 3600 / 60, left % 60)
    }
}
