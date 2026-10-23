import CoreGraphics
import Foundation

/// Every value on screen is a pure function of the loop time `t` (0 <= t < loop); t = loop equals t = 0.
/// Mirrors react-native/src/blocks/inputs/paste-places-import/motion.ts value for value.
enum PastePlacesImportMotion {
    typealias Spring = (omega: Double, zeta: Double)

    static let loop = 10.0
    static let filledAt = 2.05
    static let foundAtTime = 5.75
    static let resetAt = 7.32
    static let restAgain = 8.72
    static let pasteAt = 0.9
    static let startAt = 2.4
    static let resumeAfter = 2.0
    static let reducedFade = 0.1

    static let pastePress = (down: 0.8, up: 0.9)
    static let startPress = (down: 2.2, up: 2.33)
    static let closePress = (down: 7.2, up: 7.32)
    static let pressDown = 0.1
    static let pressUp = 0.14
    static let pressScale = 0.97
    static let closeScale = 0.86
    static let closeDim = 0.45
    static let pressIn = 0.110
    static let pressOut = 0.160

    static let fanOut = (0.9, 1.22)
    static let fanBack = (1.3, 1.8)
    static let notesFan = (rotation: -8.0, x: -9.0, y: -3.0, scale: 1.0)
    static let documentFan = (rotation: 8.0, x: 9.0, y: -2.0, scale: 1.0)
    static let chatFan = (rotation: 0.0, x: 0.0, y: -6.0, scale: 1.04)

    static let placeholderOut = (0.92, 1.04)
    static let noteInStart = 0.98
    static let noteInStagger = 0.07
    static let noteInDuration = 0.34
    static let noteInRise = 8.0
    static let countInStart = 1.05
    static let countInDuration = 0.8
    static let countInStagger = 0.08
    static let countOutStart = 7.72
    static let countOutDuration = 0.5
    static let countOutStagger = 0.05
    static let fillIn = (1.5, 1.95)
    static let fillOut = (7.5, 7.9)

    static let scanStart = 2.4
    static let scanDuration = 0.9
    static let scanFade = 0.1

    static let highlightDuration = 0.22
    static let liftDelay = 0.12
    static let wordDim = 0.5
    static let cityLineDuration = 0.25
    static let tipLine = (0.05, 0.42)
    static let resetTipLine = (7.4, 7.6)

    static let hoverT = (rise: 8.0, grow: 0.04, pill: 0.14, spring: Spring(omega: 13, zeta: 0.72))

    // The fold.
    static let foldPanels = 4
    static let foldMaxDeg = 84.0
    static let foldPerspective = 640.0
    static let foldClose = (3.5, 3.88)
    static let foldOpen = (3.88, 4.6)
    static let foldReclose = (7.72, 8.08)
    static let foldReopen = (8.08, 8.72)
    static let foldSettle = 0.16
    static let foldSpring = Spring(omega: 11, zeta: 0.55)
    static let foldShadeDown = 0.28
    static let foldShadeUp = 0.06
    static let foldDir = 1.0
    static let foldShadow = (y: 6.0, blur: 10.0, opacity: 0.16)
    static let foldMargin = 28.0

    // Chips fly to the map and drop as pins.
    static let fly = (start: 4.02, stagger: 0.09, spring: Spring(omega: 9.5, zeta: 0.78),
                      arc: 34.0, grow: 0.06, tilt: 4.0, dropAfter: 0.38)
    static let dropT = (height: 24.0, fall: 0.17, bounces: [0.12, 0.03],
                       squashX: 0.07, squashY: 0.12, squashDecay: 0.06)
    static let routeDraw = (5.0, 5.7)
    static let routeRetract = (7.32, 7.56)
    static let popT = (start: 7.34, stagger: 0.04, dur: 0.2, rise: 6.0, shrink: 0.2)
    static let dotT = (inTime: 0.2, shadowAir: 0.05, shadowGround: 0.22, shadowRest: 0.14)

    static let labelOut = 0.12
    static let labelGap = 0.06
    static let labelIn = 0.18
    static let labelShift = 6.0
    static let labelBackAt = 7.36
    static let countRoll = 0.24

    static let quiet = [(0.0, 0.79), (5.72, 7.19), (8.74, loop)]

    static let passes: [Double] = PastePlacesImportTheme.noteLines.indices.map { index in
        let lineMid = PastePlacesImportTheme.Note.firstBaseline
            + Double(index) * PastePlacesImportTheme.Note.pitch
            - 0.3 * PastePlacesImportTheme.Note.size * 3
        return passAt((lineMid - PastePlacesImportTheme.Note.scanTop)
                      / (PastePlacesImportTheme.Note.scanBottom - PastePlacesImportTheme.Note.scanTop))
    }
    static let cityPass = passes[0]
    static let placePasses = Array(passes.dropFirst())
    static let places = placePasses.count
    static let firstLand = impactAt(0)
    static let lastLand = impactAt(places - 1)

    static func normalized(_ time: Double) -> Double {
        (time.truncatingRemainder(dividingBy: loop) + loop).truncatingRemainder(dividingBy: loop)
    }

    static func clamp(_ value: Double) -> Double { min(1, max(0, value)) }
    static func segment(_ time: Double, _ start: Double, _ end: Double) -> Double {
        clamp((time - start) / (end - start))
    }
    static func interpolate(_ from: Double, _ to: Double, _ progress: Double) -> Double {
        from + (to - from) * progress
    }

    static func bezier(_ progress: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        let x = clamp(progress)
        if x == 0 || x == 1 { return x }
        var low = 0.0
        var high = 1.0
        for _ in 0..<18 {
            let value = (low + high) / 2
            let inverse = 1 - value
            let at = 3 * inverse * inverse * value * x1
                + 3 * inverse * value * value * x2
                + value * value * value
            if at < x { low = value } else { high = value }
        }
        let value = (low + high) / 2
        let inverse = 1 - value
        return 3 * inverse * inverse * value * y1
            + 3 * inverse * value * value * y2
            + value * value * value
    }

    static func easeOut(_ progress: Double) -> Double { bezier(progress, 0.23, 1, 0.32, 1) }
    static func easeInOut(_ progress: Double) -> Double { bezier(progress, 0.77, 0, 0.175, 1) }

    /// A damped spring from 0 to 1, `tau` seconds after it starts (closed form).
    static func spring(_ tau: Double, _ s: Spring) -> Double {
        guard tau > 0 else { return 0 }
        let damped = s.omega * sqrt(1 - s.zeta * s.zeta)
        let envelope = exp(-s.zeta * s.omega * tau)
        let value = 1 - envelope * (cos(damped * tau) + (s.zeta * s.omega / damped) * sin(damped * tau))
        return envelope < 1e-4 ? 1 : value
    }

    static func press(at time: Double, interval: (down: Double, up: Double)) -> Double {
        if time < interval.down || time > interval.up + pressUp { return 0 }
        if time < interval.up {
            return easeOut(segment(time, interval.down, interval.down + pressDown))
        }
        return 1 - easeOut(segment(time, interval.up, interval.up + pressUp))
    }

    static func fan(at time: Double) -> Double {
        easeOut(segment(time, fanOut.0, fanOut.1)) - easeInOut(segment(time, fanBack.0, fanBack.1))
    }

    /// Note line i: 0 hidden ... 1 in place. The note leaves folded inside the paper, so after the reset it is 0.
    static func noteLine(at time: Double, index: Int) -> Double {
        if time >= resetAt { return 0 }
        let start = noteInStart + Double(index) * noteInStagger
        return easeOut(segment(time, start, start + noteInDuration))
    }

    static func placeholder(at time: Double) -> Double {
        if time >= resetAt { return 1 }
        return 1 - segment(time, placeholderOut.0, placeholderOut.1)
    }

    static func count(at time: Double) -> Double {
        if time < countOutStart { return segment(time, countInStart, countInStart + countInDuration) }
        return 1 - segment(time, countOutStart, countOutStart + countOutDuration)
    }

    static func slot(master: Double, index: Int, count: Int, span: Double, stagger: Double) -> Double {
        let fromRight = count - 1 - index
        let duration = 1 - Double(count - 1) * stagger / span
        return easeOut(clamp((master - Double(fromRight) * stagger / span) / duration))
    }

    static func fill(at time: Double) -> Double {
        if time < fillOut.0 { return easeInOut(segment(time, fillIn.0, fillIn.1)) }
        return 1 - easeInOut(segment(time, fillOut.0, fillOut.1))
    }

    static func scan(at time: Double) -> (progress: Double, opacity: Double) {
        let progress = segment(time, scanStart, scanStart + scanDuration)
        let opacity = min(
            segment(time, scanStart, scanStart + scanFade),
            1 - segment(time, scanStart + scanDuration - scanFade, scanStart + scanDuration)
        )
        return (progress, opacity)
    }

    static func passAt(_ fraction: Double) -> Double { scanStart + fraction * scanDuration }
    static func highlight(at time: Double, pass: Double) -> Double {
        easeOut(segment(time, pass, pass + highlightDuration))
    }

    static func cityLine(at time: Double) -> Double {
        easeOut(segment(time, cityPass, cityPass + cityLineDuration))
    }

    static func tipLine(at time: Double) -> Double {
        if time >= resetTipLine.0 {
            return 1 - easeOut(segment(time, resetTipLine.0, resetTipLine.1))
        }
        return easeOut(segment(time, cityPass + tipLine.0, cityPass + tipLine.1))
    }

    /// The place's word in the note: dims once its chip has lifted off.
    static func word(at time: Double, pass: Double) -> Double {
        let lift = pass + liftDelay
        return interpolate(1, wordDim, segment(time, lift, lift + 0.12))
    }

    // MARK: fold

    private static func foldPhase(
        _ time: Double,
        close: (Double, Double),
        open: (Double, Double)
    ) -> Double {
        if time < close.0 || time >= open.1 { return 0 }
        if time < close.1 { return foldMaxDeg * easeInOut(segment(time, close.0, close.1)) }
        let s = spring(time - open.0, foldSpring)
        return foldMaxDeg * (1 - s) * (1 - segment(time, open.1 - foldSettle, open.1))
    }

    /// Fold angle in degrees (0 flat ... foldMaxDeg shut; a little negative while the paper flexes after opening).
    static func fold(at time: Double) -> Double {
        time < 6
            ? foldPhase(time, close: foldClose, open: foldOpen)
            : foldPhase(time, close: foldReclose, open: foldReopen)
    }

    /// The paper panels replace the text area from the first fold to the end of the reset.
    static func panels(at time: Double) -> Bool { time >= foldClose.0 && time < foldReopen.1 }

    /// The map is printed on the paper from the moment the first fold is shut until the reset fold is shut.
    static func mapFace(at time: Double) -> Bool { time >= foldClose.1 && time < foldReclose.1 }

    /// Panel i of n under fold angle `deg`: translateY (pt), rotation (deg) and shade (0..1).
    static func panel(_ i: Int, deg: Double, panelHeight hp: Double, perspective p: Double)
        -> (ty: Double, rot: Double, shade: Double) {
        let th = deg * .pi / 180
        let sn = sin(th) * foldDir
        let cs = cos(th)
        let near = p / (p - (hp / 2) * sn)
        let far = p / (p + (hp / 2) * sn)
        var c = (hp / 2) * cs * far
        for j in 0..<i { c += hp * cs * (j % 2 == 0 ? near : far) }
        let sign = i % 2 == 0 ? 1.0 : -1.0
        let facing = sign * sn
        let f2 = facing * facing
        return (c - (Double(i) + 0.5) * hp, sign * deg, facing > 0 ? f2 * foldShadeDown : f2 * foldShadeUp)
    }

    /// The homography of panel i (see motion.ts `panelMatrix`) about the panel's own centre reduces to
    /// y' = cos(th) * y / (1 - k y), x' = x / (1 - k y); this returns (cos(th), k).
    static func panelProjection(_ i: Int, deg: Double, perspective p: Double) -> (cos: Double, k: Double) {
        let th = deg * .pi / 180
        let k = ((i % 2 == 0 ? 1.0 : -1.0) * sin(th) * foldDir) / p
        return (cos(th), k)
    }

    // MARK: chips to pins

    static func hover(at time: Double, pass: Double) -> Double {
        spring(time - pass - liftDelay, hoverT.spring)
    }

    static func chipPill(at time: Double, pass: Double) -> Double {
        let lift = pass + liftDelay
        return segment(time, lift, lift + hoverT.pill)
    }

    private static func flyStart(_ index: Int) -> Double { fly.start + Double(index) * fly.stagger }

    static func flight(at time: Double, index: Int) -> Double {
        spring(time - flyStart(index), fly.spring)
    }

    /// Time chip i first touches the map.
    static func impactAt(_ index: Int) -> Double { flyStart(index) + fly.dropAfter + dropT.fall }

    /// Drop of chip i: height h (1 = dropT.height above the map, 0 on it) and the squash impulse of the impacts.
    static func dropAt(_ time: Double, index: Int) -> (h: Double, imp: Double) {
        let tau = time - flyStart(index) - fly.dropAfter
        if tau <= 0 { return (1, 0) }
        let fall = dropT.fall
        var h = 0.0
        var at = fall
        var imp = 0.0
        if tau < fall { h = 1 - (tau / fall) * (tau / fall) }
        else { imp += exp(-(tau - fall) / dropT.squashDecay) }
        for peak in dropT.bounces {
            let duration = 2 * fall * sqrt(peak)
            if tau >= at && tau < at + duration {
                let u = (tau - at) / duration
                h = 4 * peak * u * (1 - u)
            }
            at += duration
            if tau >= at { imp += sqrt(peak) * exp(-(tau - at) / dropT.squashDecay) }
        }
        return (h, imp)
    }

    static func pop(at time: Double, index: Int) -> Double {
        let start = popT.start + Double(index) * popT.stagger
        return easeOut(segment(time, start, start + popT.dur))
    }

    /// The dot on the map under chip i: 0..1.
    static func dot(at time: Double, index: Int) -> Double {
        if time >= popT.start { return 1 - pop(at: time, index: index) }
        let start = impactAt(index)
        return easeOut(segment(time, start, start + dotT.inTime))
    }

    /// Contact shadow under chip i: opacity (0 while it is high in the air).
    static func contact(at time: Double, index: Int) -> Double {
        let start = flyStart(index) + fly.dropAfter
        if time < start { return 0 }
        let h = dropAt(time, index: index).h
        let landed = segment(time, impactAt(index) + 0.3, impactAt(index) + 0.6)
        let value = interpolate(dotT.shadowAir, dotT.shadowGround, 1 - h)
        return interpolate(value, dotT.shadowRest, landed) * (1 - pop(at: time, index: index))
    }

    /// N of "found N places" (continuous); each landing rolls it on.
    static func foundCount(at time: Double) -> Double {
        if time >= labelBackAt { return Double(places) }
        return (0..<places).reduce(0) { result, index in
            result + easeOut(segment(time, impactAt(index), impactAt(index) + countRoll))
        }
    }

    /// Label morph, sequential: (old label gone 0..1, new label in 0..1); rest (0, 0), found (1, 1).
    static func labels(at time: Double) -> (oldGone: Double, newIn: Double) {
        func transition(_ start: Double) -> (Double, Double) {
            (
                easeOut(segment(time, start, start + labelOut)),
                easeOut(segment(time, start + labelOut + labelGap, start + labelOut + labelGap + labelIn))
            )
        }
        if time >= labelBackAt {
            let value = transition(labelBackAt)
            return (1 - value.1, 1 - value.0)
        }
        return transition(impactAt(0))
    }

    static func route(at time: Double) -> Double {
        if time < routeRetract.0 { return easeInOut(segment(time, routeDraw.0, routeDraw.1)) }
        return 1 - easeInOut(segment(time, routeRetract.0, routeRetract.1))
    }

    static func onFlatMap(at time: Double) -> Bool {
        panels(at: time) && mapFace(at: time) && abs(fold(at: time)) < 3
    }

    // MARK: quiet stretches

    static func quietIndex(at time: Double) -> Int? {
        quiet.firstIndex { time >= $0.0 && time <= $0.1 }
    }

    /// Seconds until motion starts again, when `time` is inside a quiet stretch (wrapping the loop seam).
    static func nextMotionDelay(at time: Double) -> Double? {
        guard let range = quiet.first(where: { time >= $0.0 && time < $0.1 }) else { return nil }
        var delay = range.1 - time
        if range.1 >= loop, let first = quiet.first, first.0 <= 0 { delay += first.1 }
        return delay
    }
}
