import CoreGraphics
import Foundation

/// Shared motion equations and deterministic 9 s autoplay script.
/// Distances use screen points (Swift) or dp (Compose); all timeline and spring durations use seconds.
enum TapRidePassMotion {
    static let loop = 9.0
    /// Clamps a value to a phase interval; reversing the interval reverses the ramp.
    static func segment(_ t: Double, _ a: Double, _ b: Double) -> Double { min(max((t - a) / (b - a), 0), 1) }
    /// Cubic smoothstep across the supplied interval, with zero slope at both ends.
    static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = segment(x, a, b)
        return t * t * (3 - 2 * t)
    }
    /// Symmetric cubic easing for a normalized phase in 0…1.
    static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    /// Cubic deceleration for a normalized phase in 0…1.
    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    /// Closed-form spring response from rest at 0 toward 1. Duration is seconds; ratio is damping.
    static func springStep(_ t: Double, _ duration: Double, _ ratio: Double) -> Double {
        if t <= 0 { return 0 }
        let w = 2 * Double.pi / duration
        if ratio >= 1 { return 1 - exp(-w * t) * (1 + w * t) }
        let wd = w * sqrt(1 - ratio * ratio)
        return 1 - exp(-ratio * w * t) * (cos(wd * t) + ratio * w / wd * sin(wd * t))
    }
    /// One semi-implicit spring step. Position is points/dp, velocity is points/dp per second.
    /// The final argument is the integration step in seconds; duration and damping set the response.
    static func spring(
        _ x: inout Double, _ v: inout Double, _ target: Double, _ duration: Double, _ ratio: Double, _ h: Double
    ) {
        let w = 2 * Double.pi / duration
        v += (w * w * (target - x) - 2 * ratio * w * v) * h
        x += v * h
    }
    /// Cubic path coordinate at normalized progress u; control coordinates share the same units.
    static func bezier(_ a: Double, _ b: Double, _ c: Double, _ d: Double, _ u: Double) -> Double {
        let k = 1 - u
        return k * k * k * a + 3 * k * k * u * b + 3 * k * u * u * c + u * u * u * d
    }
    /// Seconds since the read: print 0.58–1.33 to 0.36, then rise 1.33–2.03 to 1.
    static func ticketAfter(_ tt: Double) -> Double {
        easeOut(segment(tt, 0.58, 1.33)) * 0.36 + easeInOut(segment(tt, 1.33, 2.03)) * 0.64
    }
    /// Balance roll progress over 2.23–2.83 s after the read.
    static func rollAfter(_ tt: Double) -> Double { easeInOut(segment(tt, 2.23, 2.83)) }
    /// Scripted card centre, normalized lift/ticket/roll progress, and seconds since the read.
    struct Pose {
        var cardX: Double
        var cardY: Double
        var lift: Double
        var ticketProgress: Double
        var roll: Double
        var tapAge: Double
    }
    /// Autoplay beats in seconds: rest to 1.2, lift to 1.45, approach to 2.8, read at 3.12.
    /// The card returns as paper prints at 3.7–4.45, rises at 4.45–5.15 and rolls at 5.35–5.95.
    /// Close at 7.05–7.8; from 8.3 onward the pose exactly equals the opening rest.
    static func script(_ loopTime: Double, _ layout: TapRidePassGeometry) -> Pose {
        if loopTime >= 8.3 || loopTime < 1.2 {
            return .init(
                cardX: layout.homeX, cardY: layout.homeY, lift: 0, ticketProgress: 0, roll: 0, tapAge: -10)
        }
        let approachX = layout.readerX + 26
        let approachY = layout.readerY + 88
        var cardX = layout.homeX
        var cardY = layout.homeY
        if loopTime >= 1.45 && loopTime < 2.8 {
            let u = easeInOut(segment(loopTime, 1.45, 2.8))
            cardX = bezier(layout.homeX, layout.homeX - 40, approachX + 60, approachX, u)
            cardY = bezier(layout.homeY, layout.homeY - 110, approachY + 90, approachY, u)
        } else if loopTime >= 2.8 {
            let k = springStep(loopTime - 2.8, 0.3, 0.66)
            let dockedX = approachX + (layout.readerX - approachX) * k
            let dockedY = approachY + (layout.readerY - approachY) * k
            let returnProgress = springStep(loopTime - 3.12 - 0.43, 0.5, 0.8)
            cardX = dockedX + (layout.homeX - dockedX) * returnProgress
            cardY = dockedY + (layout.homeY - dockedY) * returnProgress
        }
        let lift = easeOut(segment(loopTime, 1.2, 1.45)) * (1 - easeInOut(segment(loopTime, 2.8, 3.1)))
        let timeAfterTap = loopTime - 3.12
        return .init(
            cardX: cardX, cardY: cardY, lift: lift,
            ticketProgress: ticketAfter(timeAfterTap) * (1 - easeInOut(segment(loopTime, 7.05, 7.8))),
            roll: rollAfter(timeAfterTap) * (1 - segment(loopTime, 7.75, 7.8)), tapAge: timeAfterTap)
    }
}

/// Screen geometry in points/dp, including safe-area insets.
/// Validator feature coordinates originate in its 697 × 1440 px artwork; card art is 1600 × 1009 px.
/// The printed ticket starts at slot width, then expands toward its full-size 430-point/dp height.
struct TapRidePassGeometry: Equatable {
    let width: Double
    let height: Double
    let safeTop: Double
    let safeBottom: Double
    var validatorScale: Double { min(width * 0.78, 330) / 694 }
    var validatorWidth: Double { 697 * validatorScale }
    var validatorHeight: Double { 1440 * validatorScale }
    var validatorX: Double { (width - validatorWidth) / 2 }
    var validatorY: Double { height * 0.3 - 2 * validatorScale }
    var readerX: Double { validatorX + 346 * validatorScale }
    var readerEllipseY: Double { validatorY + 206 * validatorScale }
    var readerY: Double { readerEllipseY - 2 }
    var readerRadiusX: Double { 166 * validatorScale }
    var readerRadiusY: Double { 105 * validatorScale }
    var slotY: Double { validatorY + 380 * validatorScale }
    var slotWidth: Double { 399 * validatorScale }
    var cardWidth: Double { min(width - 32, 372) }
    var cardHeight: Double { cardWidth * 1009 / 1600 }
    var homeX: Double { width / 2 }
    var homeY: Double { height - safeBottom - 18 - cardHeight / 2 }
    var dockScale: Double { readerRadiusX * 2 * 1.22 / cardWidth }
    var homeDistance: Double { hypot(homeX - readerX, homeY - readerY) }
    var ticketWidth: Double { min(width - 48, 340) }
    var ticketHeight: Double { 430 }
    var ticketY: Double { safeTop + 355 }
    var printedWidth: Double { slotWidth * 0.86 }
    var printedHeight: Double { printedWidth * ticketHeight / ticketWidth }
    var printedExposure: Double { printedHeight * 0.42 }
}

/// Per-frame drawing values derived from the driver: positions/sizes in points, rotations in radians,
/// and opacity/progress in 0…1. Separates the card pose from the ticket emerging through the slot.
struct TapRidePassDrawing {
    typealias Motion = TapRidePassMotion
    var cardX: Double
    var cardY: Double
    var dockProgress: Double
    var cardScale: Double
    var rotationX: Double
    var rotationZ: Double
    var coil: Double
    var ringAlpha: Double
    var ringSuccess: Double
    var rippleScale: Double
    var rippleAlpha: Double
    var flash: Double
    var shockScale: Double
    var shockAlpha: Double
    var paperX: Double
    var paperY: Double
    var paperWidth: Double
    var paperHeight: Double
    var paperExpansion: Double
    var inSlot: Bool
    /// Resolves reader lighting, card perspective and the print-then-rise ticket pose for one frame.
    init(_ driver: TapRidePassDriver, _ layout: TapRidePassGeometry, _ reduceMotion: Bool) {
        let distanceToReader = hypot(driver.cardX - layout.readerX, driver.cardY - layout.readerY)
        dockProgress = Motion.smooth(0, 1, 1 - min(1, distanceToReader / layout.homeDistance))
        cardScale = (1 + (layout.dockScale - 1) * dockProgress) * (1 + 0.035 * driver.lift)
        rotationX = acos(105.0 / 166) * dockProgress
        rotationZ = driver.tiltDegrees * Double.pi / 180 * (1 - dockProgress)
        cardX = driver.cardX
        cardY = driver.cardY + Motion.smooth(0.45, 0.95, driver.ticketProgress) * layout.cardHeight * 0.78
        let tickPulse = max(0, 1 - (driver.now - driver.lastTickTime) / 0.22) * driver.proximity
        coil = min(
            1,
            0.85 * pow(driver.proximity, 1.2) + 0.35 * tickPulse
                + (driver.tapAge > 0 ? max(0, 1 - driver.tapAge / 0.9) : 0))
        ringAlpha = min(
            1,
            0.32 + 0.1 * sin(2 * Double.pi * driver.breathingTime / 3) + 0.45 * driver.proximity + 0.3
                * max(0, 1 - (driver.now - driver.lastTickTime) / 0.25) * driver.proximity + 0.5
                * Motion.smooth(0, 0.3, driver.ticketProgress))
        let successProgress =
            driver.tapAge > 0
            ? Motion.smooth(0.15, 0.45, driver.tapAge) * (driver.mode == 0 && !driver.scripted ? 0 : 1) : 0
        ringSuccess = min(successProgress, driver.scripted ? 1 - Motion.smooth(7.05, 7.8, driver.loopTime) : 1)
        rippleScale = 1 + 0.55 * Motion.easeOut(Motion.segment(driver.now - driver.lastTickTime, 0, 0.6))
        rippleAlpha =
            reduceMotion ? 0 : max(0, 1 - (driver.now - driver.lastTickTime) / 0.6) * 0.7 * driver.proximity
        flash =
            driver.tapAge < 0
            ? 0 : Motion.smooth(0, 0.05, driver.tapAge) * (1 - Motion.smooth(0.05, 0.6, driver.tapAge))
        shockScale = 1 + 3.2 * Motion.easeOut(Motion.segment(driver.tapAge, 0, 0.8))
        shockAlpha = driver.tapAge < 0 || reduceMotion ? 0 : max(0, 1 - driver.tapAge / 0.8) * 0.85
        let riseProgress = driver.ticketProgress <= 0.36 ? 0 : (driver.ticketProgress - 0.36) / 0.64
        let exposedHeight =
            driver.ticketProgress <= 0.36
            ? layout.printedExposure * driver.ticketProgress / 0.36
            : layout.printedExposure + (layout.printedHeight + 6 - layout.printedExposure)
                * Motion.smooth(0, 0.4, riseProgress)
        let printedY = layout.slotY - exposedHeight + layout.printedHeight / 2
        paperExpansion = Motion.smooth(0.4, 1, riseProgress)
        paperWidth = layout.printedWidth + (layout.ticketWidth - layout.printedWidth) * paperExpansion
        paperHeight = paperWidth * layout.ticketHeight / layout.ticketWidth
        paperX = layout.readerX + (layout.homeX - layout.readerX) * paperExpansion
        paperY = printedY + (layout.ticketY - printedY) * paperExpansion
        inSlot = Motion.smooth(0, 0.4, riseProgress) < 1
    }
}
