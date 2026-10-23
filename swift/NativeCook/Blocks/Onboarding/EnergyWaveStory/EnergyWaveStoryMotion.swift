import Foundation

/// Where the story is: the step shown, the one before it, when it changed, and how far the colour had got then.
struct EnergyWaveStoryScene: Equatable {
    var step: Int
    var from: Int
    var at: Double
    var fill: Double
}

/// One tag: its offset from its place, its scale about the tip of its tail, and whether it shows
/// the emoji of the last step.
struct EnergyWaveStoryTagPose {
    var x: Double
    var y: Double
    var scaleX: Double
    var scaleY: Double
    var opacity: Double
    var late: Bool
    var spin: Double
}

/// Everything on the screen is a function of the time since the step last changed, so a touch and
/// the loop that plays by itself make exactly the same pictures. Times are in seconds.
enum EnergyWaveStoryMotion {
    /// autoPlay: one pass through the three steps every `loop` seconds, the footer pressed before each change.
    static let loop = 10.0
    static let autoTaps = [3.4, 6, 9]
    static let pressFor = 0.15
    /// After a touch autoPlay waits this long before it goes back to the first step and starts over.
    static let userHold = 3.0

    // Back to the first step: the tags leave, the colour drains, and `returnTime` later the wave is
    // grey and still. The colour then sets off again by itself.
    static let returnTime = 1.0
    static let shrink = 0.2
    static let drainAfter = 0.1
    static let drain = 0.6
    static let fillAfter = 0.4
    static let fill = 1.9
    /// A tap before the colour has arrived: it runs the rest of the way in this time.
    static let fillFinish = 0.3

    static let textOut = 0.2
    static let textInAfter = 0.12
    static let textIn = 0.3
    static let textShift = 12.0

    // The tags pop out of the tip of their tail one after another, on a spring that overshoots by an eighth.
    static let tagAfter = 0.15
    static let tagStagger = 0.1
    static let tagMass = 1.0
    static let tagStiffness = 260.0
    static let tagDamping = 17.7
    // As they arrive the crests rise by `flex` points and settle with one soft bounce.
    static let flex = 6.0
    static let flexDecay = 7.0
    static let flexSpeed = 11.0
    /// The gain that makes the first swing of exp(-flexDecay t) · sin(flexSpeed t) exactly `flex` high.
    private static let flexGain: Double = {
        let peak = atan(flexSpeed / flexDecay) / flexSpeed
        return flex / (exp(-flexDecay * peak) * sin(flexSpeed * peak))
    }()

    // Third step: each tag squashes flat and comes back with its new emoji, and the second one
    // moves along to the wave's last slope.
    static let flip = 0.25
    static let flipStagger = 0.08
    static let slide = 0.45
    static let slideX = 40.0
    static let slideY = 10.0

    static let bob = 2.0
    static let bobPeriod = 2.5
    // A tapped tag hops and its emoji turns once.
    static let jump = 0.45
    static let jumpHeight = 12.0
    static let spin = 0.55

    static let flashIn = 0.05
    static let flashOut = 0.25

    /// Half of a tag with its tail: a squash about the tag's middle is a scale about the tip plus this much travel.
    static let tagHalf = 23.0

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

    /// The tags' spring from 0 to 1, t seconds after it was let go.
    static func spring(_ t: Double) -> Double {
        guard t > 0 else { return 0 }
        let omega = (tagStiffness / tagMass).squareRoot()
        let zeta = tagDamping / (2 * (tagStiffness * tagMass).squareRoot())
        let damped = omega * (1 - zeta * zeta).squareRoot()
        return 1 - exp(-zeta * omega * t) * (cos(damped * t) + (zeta * omega / damped) * sin(damped * t))
    }

    /// How much of the wave is coloured, 0 to 1 along its length.
    static func fillAt(_ scene: EnergyWaveStoryScene, _ t: Double) -> Double {
        let e = t - scene.at
        if scene.step != 0 { return scene.fill + (1 - scene.fill) * easeInOut(e / fillFinish) }
        if e < returnTime { return scene.fill * (1 - easeInOut((e - drainAfter) / drain)) }
        return easeInOut((e - returnTime - fillAfter) / fill)
    }

    /// How many points the crests stand above their place.
    static func flexAt(_ scene: EnergyWaveStoryScene, _ t: Double, still: Bool) -> Double {
        let e = t - scene.at
        if still || scene.step != 1 || e <= 0 { return 0 }
        return flexGain * exp(-flexDecay * e) * sin(flexSpeed * e)
    }

    /// When nothing moves any more; never, while the tags bob.
    static func settledAt(_ scene: EnergyWaveStoryScene, still: Bool) -> Double {
        if scene.step == 0 { return scene.at + returnTime + fillAfter + fill }
        return still ? scene.at + 1 : .infinity
    }

    /// The text of one step: it leaves upwards and the next one comes in from below.
    static func textPose(_ index: Int, _ scene: EnergyWaveStoryScene, _ t: Double, still: Bool) -> (opacity: Double, y: Double) {
        let e = t - scene.at
        if scene.step == scene.from { return (index == scene.step ? 1 : 0, 0) }
        if index == scene.step {
            let p = easeOut((e - textInAfter) / textIn)
            return (p, still ? 0 : textShift * (1 - p))
        }
        if index == scene.from {
            let p = easeIn(e / textOut)
            return (1 - p, still ? 0 : -textShift * p)
        }
        return (0, 0)
    }

    /// `jumped` is when the tag was last tapped.
    static func tagPose(_ index: Int, _ scene: EnergyWaveStoryScene, _ t: Double, jumped: Double, still: Bool) -> EnergyWaveStoryTagPose {
        let e = t - scene.at
        var show = 0.0
        var squash = 1.0
        var slide = 0.0
        var late = false
        if scene.step == 1 {
            let since = e - tagAfter - Double(index) * tagStagger
            show = still ? clamp01(since / shrink) : spring(since)
        } else if scene.step == 2 {
            let p = clamp01((e - Double(index) * flipStagger) / flip)
            show = 1
            squash = abs(cos(.pi * p))
            late = p >= 0.5
            slide = still ? (late ? 1 : 0) : easeInOut(e / Self.slide)
        } else if scene.from != 0 {
            show = 1 - easeIn(e / shrink)
            late = scene.from == 2
            slide = late ? 1 : 0
        }
        let x = index == 1 ? slide * slideX : 0
        let y = index == 1 ? slide * slideY : 0
        // Without motion a tag fades where the others scale, and changes its emoji at the bottom of a dip.
        if still {
            return EnergyWaveStoryTagPose(x: x, y: y, scaleX: 1, scaleY: 1, opacity: min(1, show) * squash, late: late, spin: 0)
        }
        let bob = Self.bob * sin((t / bobPeriod + Double(index) / 3) * 2 * .pi) * min(1, show)
        let hop = clamp01((t - jumped) / jump)
        // The tag under the wave has its tail on top, so its middle is below the tip.
        let middle = (index == 2 ? 1 : -1) * tagHalf
        return EnergyWaveStoryTagPose(
            x: x,
            y: y + bob - jumpHeight * 4 * hop * (1 - hop) + middle * (1 - squash) * show,
            scaleX: show,
            scaleY: show * squash,
            opacity: smooth(show, 0, 0.1),
            late: late,
            spin: 360 * easeOut((t - jumped) / spin)
        )
    }

    /// The grey pill behind the footer while it is pressed.
    static func flashAt(pressed: Double, until: Double, _ t: Double) -> Double {
        smooth(t, pressed, pressed + flashIn) * (1 - smooth(t, until, until + flashOut))
    }
}
