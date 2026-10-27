import SwiftUI
import UIKit

/// One knot of a keyframe track: the value `v` at loop time `t` (seconds).
struct PaperDeckTutorialKnot {
    let t: Double
    let v: Double
}

/// The live state every card and the cover are drawn from (`DeckPose` in the React Native file).
struct PaperDeckTutorialPose: Equatable {
    /// The card on top, 0 to 5, or 6 once the choice deck is shown.
    var step: Int
    /// Horizontal travel of the top card, in points.
    var x: Double
    /// 0 for the cover face, 1 once the cover has turned over to the first card.
    var flip: Double
    /// Loop time the tracks are read at.
    var time: Double
}

/// What one white card is drawn with: the animated style of `PaperCard`.
struct PaperDeckTutorialCardStyle {
    var opacity: Double
    var shadowOpacity: Double
    var shadowRadius: Double
    var shadowX: Double
    var shadowY: Double
    var offsetX: Double
    var offsetY: Double
    var scale: Double
    /// Degrees about the screen's normal.
    var roll: Double
    /// Degrees about the vertical axis.
    var yaw: Double
}

/// What the illustrated cover deck is drawn with: the animated styles of `IllustratedDeck`.
struct PaperDeckTutorialCoverStyle {
    var opacity: Double
    var translateY: Double
    /// Degrees about the vertical axis.
    var rotateY: Double
    var backsOpacity: Double
    var choiceOpacity: Double
    var outroBackOpacity: Double
}

/// The tracks, the curves and the per-frame maths of the React Native file, as pure functions.
enum PaperDeckTutorialScript {

    // MARK: Tracks

    private static func knots(_ pairs: [(Double, Double)]) -> [PaperDeckTutorialKnot] {
        pairs.map { PaperDeckTutorialKnot(t: $0.0, v: $0.1) }
    }

    /// `LAST_X`: source frames 860 to 870 at 60 fps, the last card's travel as a fraction of the screen width.
    static let lastX = knots([
        (860.0 / 60.0, 0), (861.0 / 60.0, 0.085104), (862.0 / 60.0, 0.112787), (863.0 / 60.0, 0.146173),
        (864.0 / 60.0, 0.209716), (865.0 / 60.0, 0.305564), (866.0 / 60.0, 0.434484), (867.0 / 60.0, 0.595059),
        (868.0 / 60.0, 0.783343), (869.0 / 60.0, 1), (870.0 / 60.0, 1.25),
    ])

    /// `LAST_Y`: the last card's rise, as a fraction of the screen height.
    static let lastY = knots([
        (860.0 / 60.0, 0), (861.0 / 60.0, -0.026521), (862.0 / 60.0, -0.03552), (863.0 / 60.0, -0.048075),
        (864.0 / 60.0, -0.072457), (865.0 / 60.0, -0.108951), (866.0 / 60.0, -0.158152), (867.0 / 60.0, -0.21929),
        (868.0 / 60.0, -0.291471), (869.0 / 60.0, -0.375), (870.0 / 60.0, -0.47),
    ])

    /// `LAST_ROLL`: the last card's roll in degrees.
    static let lastRoll = knots([
        (860.0 / 60.0, 0), (861.0 / 60.0, 1.73147), (862.0 / 60.0, 2.26703), (863.0 / 60.0, 3.00973),
        (864.0 / 60.0, 4.32635), (865.0 / 60.0, 5.97817), (866.0 / 60.0, 9.07868), (867.0 / 60.0, 12.31907),
        (868.0 / 60.0, 16.26098), (869.0 / 60.0, 20.3), (870.0 / 60.0, 24.5),
    ])

    /// `THROWS`: one track per card, the travel of the top card as a fraction of the screen width.
    static let throwTracks: [[PaperDeckTutorialKnot]] = [
        knots([(3.4, 0), (3.5, 0.02), (3.65, 0.04), (3.8, 0.07), (3.85, 0.18), (3.9, 0.42), (3.95, 0.85), (4, 1.4)]),
        knots([(5.15, 0), (5.35, 0.03), (5.45, 0.055), (5.5, 0.09), (5.6, 0.3), (5.6833, 0.8), (5.75, 1.4)]),
        knots([(9.1167, 0), (9.15, 0.1), (9.2, 0.3), (9.25, 0.75), (9.3333, 1.4)]),
        knots([(11.2667, 0), (11.3, 0.1), (11.35, 0.55), (11.4333, 1.4)]),
        knots([(12.55, 0), (12.5833, 0.1), (12.6333, 0.55), (12.75, 1.4)]),
        lastX,
    ]

    /// `FLIP`: the cover's turn in degrees, 0 to 180.
    static let flipTrack = knots([
        (0.6, 0), (0.65, 7), (0.7, 24), (0.75, 45), (0.8, 69), (0.8333, 87.4), (0.85, 90),
        (0.8833, 99), (0.9167, 117), (0.95, 133), (1, 154), (1.0667, 171), (1.1167, 180),
    ])

    /// `LIFT`: the cover rising to the card position before it turns.
    static let liftTrack = knots([(0, 0), (0.2, 0), (0.3, 0.05), (0.4, 0.27), (0.5, 0.55), (0.6, 0.81), (0.7, 1)])

    /// The rear sheets settling into the deck (`introRear`).
    static let introRearTrack = knots([(1.1, 0), (1.22, 0.6), (1.34, 1.04), (1.6, 1)])

    /// The covers behind the front cover fading out (`backsStyle`).
    static let backsTrack = knots([(0.2, 0), (0.6, 1)])

    /// `ANCHORS`: where the loop picks up again after a touch, by the card that was on top.
    static let anchors: [Double] = [1.8, 4.3, 6, 9.65, 11.65, 12.95, 15.5]

    /// `DIRECTIONS`: the side each card is thrown to.
    static let directions: [Double] = [1, -1, 1, 1, 1, 1]

    /// `EASE`: `Easing.bezier(0.23, 1, 0.32, 1)`.
    private static let settleCurve = UnitCurve.bezier(
        startControlPoint: UnitPoint(x: 0.23, y: 1),
        endControlPoint: UnitPoint(x: 0.32, y: 1)
    )

    static func ease(_ progress: Double) -> Double {
        settleCurve.value(at: progress)
    }

    // MARK: Helpers

    /// `clamp`.
    static func clamp(_ value: Double, _ lower: Double = 0, _ upper: Double = 1) -> Double {
        max(lower, min(upper, value))
    }

    /// `previewPhase`: a preview time folded into one loop.
    static func previewPhase(_ t: Double) -> Double {
        guard t.isFinite else { return 0 }
        let phase = t.truncatingRemainder(dividingBy: PaperDeckTutorial.loop)
        return phase < 0 ? phase + PaperDeckTutorial.loop : phase
    }

    /// `track`: a monotone cubic Hermite curve through the knots, held flat before the first and after the last.
    static func track(_ t: Double, _ keys: [PaperDeckTutorialKnot]) -> Double {
        if t <= keys[0].t { return keys[0].v }
        let last = keys.count - 1
        if t >= keys[last].t { return keys[last].v }
        var i = 0
        while keys[i + 1].t < t { i += 1 }
        let a = keys[i].t
        let y0 = keys[i].v
        let b = keys[i + 1].t
        let y1 = keys[i + 1].v
        let secant = (y1 - y0) / (b - a)
        var before = 0.0
        if i > 0 {
            before = (y0 - keys[i - 1].v) / (a - keys[i - 1].t)
        }
        var after = 0.0
        if i + 1 < last {
            after = (keys[i + 2].v - y1) / (keys[i + 2].t - b)
        }
        let m0 = before * secant > 0 ? 2 * before * secant / (before + secant) : 0
        let m1 = after * secant > 0 ? 2 * after * secant / (after + secant) : 0
        let p = (t - a) / (b - a)
        let p2 = p * p
        let p3 = p2 * p
        let span = b - a
        let h00 = (2 * p3 - 3 * p2 + 1) * y0
        let h10 = (p3 - 2 * p2 + p) * span * m0
        let h01 = (-2 * p3 + 3 * p2) * y1
        let h11 = (p3 - p2) * span * m1
        return h00 + h10 + h01 + h11
    }

    // MARK: Pose

    /// The deck pose at loop time `t` when the clock drives it (`pose` without `manual`).
    static func autoPose(t: Double, screenWidth: Double) -> PaperDeckTutorialPose {
        if t >= 17.9 {
            return PaperDeckTutorialPose(step: 0, x: 0, flip: 0, time: 0)
        }
        var active = 0
        while active < 6, let end = throwTracks[active].last, t >= end.t {
            active += 1
        }
        var x = 0.0
        if active < 6 {
            x = directions[active] * screenWidth * track(t, throwTracks[active])
        }
        let flip = track(t, flipTrack) / 180
        return PaperDeckTutorialPose(step: active, x: x, flip: flip, time: t)
    }

    // MARK: Cards

    /// `PaperCard`'s animated style for the card `index` in `pose`.
    static func cardStyle(
        index: Int,
        pose: PaperDeckTutorialPose,
        grab: Double,
        width: Double,
        height: Double,
        screenHeight: Double,
        reduced: Bool
    ) -> PaperDeckTutorialCardStyle {
        let depth = index - pose.step
        let top = depth == 0
        let advance = clamp(abs(pose.x) / width)
        let remainingDepth = max(0.0, Double(depth) - advance)
        let rearFraction = depth > 0 ? remainingDepth / Double(depth) : 0.0
        let introRear = pose.step == 0 ? track(pose.time, introRearTrack) : 1.0
        let rearAlpha = pose.step == 0 ? clamp((pose.time - 1.1) / 0.2) : 1.0
        let edgeOn = pose.time >= 0.8417 && pose.time < 0.875
        let lastThrow = top && index == 5 && pose.time >= lastX[0].t
        let even = depth % 2 == 0
        let side: Double = even ? -1.0 : 1.0

        let rearX = side * width * 0.024 * rearFraction * introRear
        let offsetX = top ? pose.x : rearX

        var offsetY = height * 0.011 * remainingDepth * introRear
        if lastThrow {
            offsetY = screenHeight * track(pose.time, lastY)
        } else if top {
            offsetY = -min(36.0, abs(pose.x) * 0.065)
        }

        let scale = top ? 1.0 : 1.0 - 0.015 * remainingDepth * introRear

        // A sheet held near its bottom swings its top the other way: it pivots under the finger.
        var roll = (even ? -0.85 : 1.4) * remainingDepth * introRear
        if lastThrow {
            roll = track(pose.time, lastRoll)
        } else if top {
            roll = clamp(pose.x / 22, -24, 24) * grab
        }

        // Three visible source edges constrain yaw to about 0 degrees before the last card clips.
        var yaw = 0.0
        if lastThrow {
            yaw = 0
        } else if index == 0 && pose.flip < 1 {
            yaw = 180 + pose.flip * 180
        } else if top && !reduced {
            yaw = clamp(-pose.x / 23, -16, 16)
        }

        // A sheet held off the deck throws a wider, softer shadow; one lying on it barely any.
        let lift = top && !reduced ? clamp(abs(pose.x) / (width * 0.6)) : 0.0

        let hidden = pose.flip < 0.5 || edgeOn || depth < 0 || depth > 2
        var opacity = 0.0
        if !hidden {
            opacity = top ? 1.0 : rearAlpha
        }

        return PaperDeckTutorialCardStyle(
            opacity: opacity,
            shadowOpacity: 0.16 + 0.22 * lift,
            shadowRadius: 9 + 18 * lift,
            shadowX: -pose.x * 0.03,
            shadowY: 4 + 14 * lift,
            offsetX: offsetX,
            offsetY: offsetY,
            scale: scale,
            roll: roll,
            yaw: yaw
        )
    }

    /// The vertical bow of the sheet's top and bottom edges (`paper` animated props), signed with the travel.
    static func curve(index: Int, pose: PaperDeckTutorialPose, reduced: Bool) -> Double {
        let settling = index == 5 && pose.step == 5 && pose.time >= lastX[0].t
        var bend = 3.5
        if reduced || settling {
            bend = 0
        } else if pose.step == index {
            bend = min(14.0, abs(pose.x) / 28)
        }
        return pose.x < 0 ? -bend : bend
    }

    /// How strongly the highlight shows on the card (`sheen` opacity before the 0.9 factor).
    static func sheenAmount(index: Int, pose: PaperDeckTutorialPose, width: Double, reduced: Bool) -> Double {
        if reduced || pose.step != index { return 0 }
        return clamp(abs(pose.x) / (width * 0.5))
    }

    /// Where the highlight sits across the card (`sheen` translateX).
    static func sheenOffset(pose: PaperDeckTutorialPose, width: Double) -> Double {
        (pose.x < 0 ? 0.62 : 0.08) * width - pose.x * 0.12
    }

    // MARK: Cover

    /// `IllustratedDeck`'s animated styles in `pose`.
    static func coverStyle(pose: PaperDeckTutorialPose, reduced: Bool, introTravel: Double) -> PaperDeckTutorialCoverStyle {
        let outro = pose.step == 6
        let rise = outro ? ease(clamp((pose.time - 14.9) / 0.3)) : 1.0

        var opacity = 0.0
        if reduced {
            opacity = outro ? 1 : 0
        } else if outro {
            opacity = rise
        } else {
            opacity = pose.time < 0.8417 && pose.flip < 0.5 ? 1 : 0
        }

        let translateY = outro
            ? introTravel + 160 * (1 - rise)
            : introTravel * (1 - track(pose.time, liftTrack))
        let rotateY = outro ? 0.0 : pose.flip * 180
        let choice = outro ? 1 - ease(clamp((pose.time - 17.5) / 0.4)) : 0.0
        let backs = outro ? 1.0 : 1 - track(pose.time, backsTrack)

        return PaperDeckTutorialCoverStyle(
            opacity: opacity,
            translateY: translateY,
            rotateY: rotateY,
            backsOpacity: backs,
            choiceOpacity: choice,
            outroBackOpacity: outro ? 1 : 0
        )
    }

    // MARK: Projection

    /// `projectedFace`: React Native's column-major `P(1600) * T * S * Rz * Ry` restricted to the card's
    /// plane, so the perspective foreshortening is kept. Its 16 numbers are
    ///
    ///     [s cz cy, s sz cy, 0, s sy / 1600,  -s sz, s cz, 0, 0,  0, 0, 1, 0,  x, y, 0, 1]
    ///
    /// which is also the order of a `CATransform3D`'s `m11 ... m44`. React Native applies it about the
    /// view's centre; `ProjectionTransform` has its origin at the top-left corner, hence the two shifts.
    static func face(size: CGSize, x: Double, y: Double, scale: Double, roll: Double, yaw: Double) -> ProjectionTransform {
        let rz = roll * Double.pi / 180
        let ry = yaw * Double.pi / 180
        let cz = cos(rz)
        let sz = sin(rz)
        let cy = cos(ry)
        let sy = sin(ry)

        var matrix = CATransform3DIdentity
        matrix.m11 = CGFloat(scale * cz * cy)
        matrix.m12 = CGFloat(scale * sz * cy)
        matrix.m14 = CGFloat(scale * sy / 1600)
        matrix.m21 = CGFloat(-scale * sz)
        matrix.m22 = CGFloat(scale * cz)
        matrix.m41 = CGFloat(x)
        matrix.m42 = CGFloat(y)

        let centerX = size.width / 2
        let centerY = size.height / 2
        var transform = CATransform3DMakeTranslation(-centerX, -centerY, 0)
        transform = CATransform3DConcat(transform, matrix)
        transform = CATransform3DConcat(transform, CATransform3DMakeTranslation(centerX, centerY, 0))
        return ProjectionTransform(transform)
    }

    // MARK: Type size

    /// `fontScale` of React Native on iOS for each Dynamic Type size.
    static func fontScale(_ size: DynamicTypeSize) -> Double {
        switch size {
        case .xSmall: return 0.823
        case .small: return 0.882
        case .medium: return 0.941
        case .large: return 1
        case .xLarge: return 1.118
        case .xxLarge: return 1.235
        case .xxxLarge: return 1.353
        case .accessibility1: return 1.786
        case .accessibility2: return 2.143
        case .accessibility3: return 2.643
        case .accessibility4: return 3.143
        case .accessibility5: return 3.571
        @unknown default: return 1
        }
    }
}
