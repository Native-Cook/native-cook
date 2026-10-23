import SwiftUI
import UIKit

let UnfoldingSignaturePadSignatureWidth = 200.0
let UnfoldingSignaturePadSignatureHeight = 96.0

private enum UnfoldingSignaturePadStyle {
    static let cardWidth = 300.0, cardHeight = 372.0, cardRadius = 36.0, stageMargin = 28.0
    static let stageWidth = cardWidth + stageMargin * 2, stageHeight = cardHeight + stageMargin * 2
    static let dash: [CGFloat] = [9, 7]
    static let outline = 2.2
    static let button = 64.0, startWidth = 206.0, doneWidth = 214.0, inset = 10.0, openWidth = cardWidth - inset * 2
    static let drop = cardHeight / 2 - inset - button / 2, edit = 64.0, editGap = 12.0
    static let headerY = -cardHeight / 2 + 44, headerInset = 37.0, headerIcon = 24.0, hit = 48.0
    static let padTop = -cardHeight / 2 + 74, padBottom = drop - button / 2 - 8, padInset = 8.0
    static let label = 18.0, labelIcon = 28.0, labelDisc = 24.0, labelGap = 8.0, wordGap = 5.0
    static let letterBlur = 5.0, letterDrop = 3.0, letterStagger = 0.4, pressScale = 0.06
    static let inkFolded = 0.25, inkStep = 1.5, signatureStep = 3.0, signatureY = -12.0
    static let light = [244.0, 244, 248], dark = [35.0, 35, 38], text = [42.0, 42, 46], white = [255.0, 255, 255], title = [154.0, 154, 160]
    static let lightColor = Color(hex: 0xf4f4f8), darkColor = Color(hex: 0x232326), outlineColor = Color(hex: 0xdadadf)
    static let headerColor = Color(hex: 0xb5b5ba), pointer = Color(hex: 0x111113), shadow = Color.rgba(40, 40, 70, 0.07)
    static let shadowBlur = 10.0, shadowWidthInset = 16.0, shadowY = 10.0, shadowHeightInset = 8.0
    static let clearSize = 26.0, iconStroke = 2.6, headerPressScale = 0.18, clearTurn = 0.5, editPressMultiplier = 1.5
    static let editSlideInset = 4.0, labelPencilX = 11.5, labelPencilY = 1.5, labelPencilScale = 0.68, dotLength = 0.01
    static let pressRetentionSide = 20.0, pressRetentionTop = 20.0, pressRetentionBottom = 30.0, minimumPressDuration = 0.13
    static let clear = "M17.65 6.35A7.958 7.958 0 0 0 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08A5.99 5.99 0 0 1 12 18c-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"
    static let pencil = "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"
    static let cross = "M8.4 8.4L15.6 15.6M15.6 8.4L8.4 15.6"
    static let check = "M7.2 12.4L10.6 15.6L16.8 8.8"
    static let scribble = "M2.5 21.5C3.5 14.5 6.5 9 9 9.6C11.6 10.3 7.6 20.6 10.4 21.4C12.4 22 13.4 19.4 14.6 17.2"
    static let signature = [
        "M5 86C7 60 11 32 17 10C24 34 33 62 42 82",
        "M1 60C15 56 31 52 46 49",
        "M56 60C68 50 82 28 84 14C85 5 78 4 75 14C69 32 66 54 72 63C77 69 86 57 93 47C99 38 97 32 92 36C85 42 86 57 93 61C100 64 109 54 115 45C121 36 131 27 143 24C159 21 179 24 198 26",
        "M114 22C120 33 129 48 138 59",
        "M112 88C141 80 171 69 195 58C200 55 201 51 197 51"
    ]
    static func buttonX(_ open: Double, _ dark: Double) -> Double { -(editGap + edit) / 2 * dark * (1 - open) }
    static func buttonWidth(_ open: Double, _ dark: Double) -> Double { let closed = startWidth + (doneWidth - startWidth) * dark; return closed + (openWidth - closed) * open }
    static func mix(_ from: [Double], _ to: [Double], _ value: Double) -> Color {
        let t = UnfoldingSignaturePadMotion.clamp(value), rgb = (0..<3).map { floor(from[$0] + (to[$0] - from[$0]) * t + 0.5) }
        return .rgba(rgb[0], rgb[1], rgb[2], 1)
    }
}

private enum UnfoldingSignaturePadMotion {
    static let mass = 1.0, stiffness = 120.0, damping = 14.0
    static let wording = 0.32, darken = 0.28, inkOut = 0.26, pressIn = 0.07, pressOut = 0.18
    static let loop = 10.4, pressFor = 0.12, startAt = 0.8, signAt = 1.75, signFor = 2.6, signLift = 0.12
    static let finishAt = 4.75, editAt = 6.4, clearAt = 7.5, foldAt = 8.3, userHold = 3.0, resetFor = 0.9
    static let pencilStay = 0.4, pencilFadeAt = 0.1, pencilIn = 0.15, frameLimit = 0.064, energyThreshold = 6e-9
    static func clamp(_ t: Double) -> Double { min(1, max(0, t)) }
    static func smooth(_ t: Double, _ from: Double, _ to: Double) -> Double { let u = clamp((t - from) / (to - from)); return u * u * (3 - 2 * u) }
}

private struct UnfoldingSignaturePadScalar {
    private(set) var value: Double
    private var target: Double, from: Double
    private var velocity = 0.0, energy = 0.0, duration = 0.0
    private var start = Date.distantPast, last = Date.distantPast
    private var active = false, springing = false
    init(_ value: Double) { self.value = value; target = value; from = value }
    mutating func jump(_ next: Double) { value = next; target = next; velocity = 0; active = false; springing = false }
    mutating func time(_ next: Double, duration: Double, at date: Date = .now, still: Bool) {
        if still || value == next { jump(next); return }
        if !(active && !springing && target == next) { start = date; from = value }
        target = next; self.duration = duration; springing = false; velocity = 0; active = true
    }
    mutating func spring(_ next: Double, at date: Date = .now, still: Bool) {
        if still { jump(next); return }
        if springing && target == next { return }
        if value == next { jump(next); return }
        let continuing = springing && active
        if !continuing { velocity = 0; last = date }
        if velocity * (next - value) < 0 { velocity = 0 }
        target = next; springing = true; active = true
        energy = 0.5 * UnfoldingSignaturePadMotion.stiffness * pow(value - next, 2)
        advance(date)
    }
    mutating func advance(_ date: Date) {
        guard active else { return }
        guard springing else {
            let t = UnfoldingSignaturePadMotion.clamp(date.timeIntervalSince(start) / duration)
            let u = t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
            value = from + (target - from) * u; if t == 1 { active = false }; return
        }
        let dt = min(UnfoldingSignaturePadMotion.frameLimit, max(0, date.timeIntervalSince(last))); last = date
        let omega = sqrt(UnfoldingSignaturePadMotion.stiffness / UnfoldingSignaturePadMotion.mass)
        let decay = UnfoldingSignaturePadMotion.damping / (2 * UnfoldingSignaturePadMotion.mass), x = value - target
        if decay < omega {
            let damped = sqrt(omega * omega - decay * decay), b = (velocity + decay * x) / damped
            let wave = x * cos(damped * dt) + b * sin(damped * dt), attenuation = exp(-decay * dt)
            velocity = attenuation * (-decay * wave + damped * (-x * sin(damped * dt) + b * cos(damped * dt)))
            value = target + attenuation * wave
        } else {
            let b = velocity + omega * x, attenuation = exp(-omega * dt)
            value = target + attenuation * (x + b * dt); velocity = attenuation * (velocity - omega * b * dt)
        }
        let current = 0.5 * UnfoldingSignaturePadMotion.stiffness * pow(value - target, 2) + 0.5 * UnfoldingSignaturePadMotion.mass * velocity * velocity
        if energy == 0 || current / energy <= UnfoldingSignaturePadMotion.energyThreshold { value = target; velocity = 0; active = false }
    }
}

private struct UnfoldingSignaturePadSample { let time: Double; let point: CGPoint; let starts: Bool }

private enum UnfoldingSignaturePadWriting {
    static func ink(_ strokes: [[Double]]) -> Path {
        var path = Path()
        for points in strokes where points.count >= 2 {
            path.move(to: CGPoint(x: points[0], y: points[1]))
            if points.count == 2 { path.addLine(to: CGPoint(x: points[0] + UnfoldingSignaturePadStyle.dotLength, y: points[1])) }
            if points.count > 4 {
                for index in stride(from: 2, to: points.count - 2, by: 2) {
                    path.addQuadCurve(to: CGPoint(x: (points[index] + points[index + 2]) / 2, y: (points[index + 1] + points[index + 3]) / 2), control: CGPoint(x: points[index], y: points[index + 1]))
                }
            }
            if points.count > 2 { path.addLine(to: CGPoint(x: points[points.count - 2], y: points[points.count - 1])) }
        }
        return path
    }
    static func script(_ source: [String]) -> [UnfoldingSignaturePadSample] {
        let contours = source.map { UnfoldingSignaturePadSVG.contour($0) }.filter { $0.count > 1 }
        let length = contours.reduce(0) { $0 + ($1.last?.distance ?? 0) }
        let speed = length / (UnfoldingSignaturePadMotion.signFor - UnfoldingSignaturePadMotion.signLift * Double(contours.count - 1))
        guard speed.isFinite && speed != 0 else { return [] }
        var time = 0.0, samples: [UnfoldingSignaturePadSample] = []
        for contour in contours {
            let total = contour.last!.distance, steps = max(1, Int(ceil(total / UnfoldingSignaturePadStyle.signatureStep)))
            var next = 1
            for index in 0...steps {
                let distance = total * Double(index) / Double(steps)
                while next < contour.count - 1 && contour[next].distance < distance { next += 1 }
                let a = contour[next - 1], b = contour[next], span = b.distance - a.distance
                let u = span > 0 ? (distance - a.distance) / span : 1
                let point = CGPoint(x: a.point.x + (b.point.x - a.point.x) * u - UnfoldingSignaturePadSignatureWidth / 2, y: a.point.y + (b.point.y - a.point.y) * u - UnfoldingSignaturePadSignatureHeight / 2 + UnfoldingSignaturePadStyle.signatureY)
                samples.append(UnfoldingSignaturePadSample(time: time + distance / speed, point: point, starts: index == 0))
            }
            time += total / speed + UnfoldingSignaturePadMotion.signLift
        }
        return samples
    }
}

private enum UnfoldingSignaturePadMode { case idle, open, done }
private enum UnfoldingSignaturePadControl { case button, edit, clear, close }

@MainActor
private final class UnfoldingSignaturePadState: ObservableObject {
    @Published var revision = 0
    @Published private(set) var touches = 0
    private(set) var mode = UnfoldingSignaturePadMode.idle
    private(set) var open = UnfoldingSignaturePadScalar(0)
    private(set) var wording = UnfoldingSignaturePadScalar(0)
    private(set) var dark = UnfoldingSignaturePadScalar(0)
    private(set) var inkShown = UnfoldingSignaturePadScalar(1)
    private(set) var buttonPress = UnfoldingSignaturePadScalar(0)
    private(set) var editPress = UnfoldingSignaturePadScalar(0)
    private(set) var clearPress = UnfoldingSignaturePadScalar(0)
    private(set) var closePress = UnfoldingSignaturePadScalar(0)
    private(set) var ink: [[Double]] = []
    private(set) var path = Path()
    private(set) var samples: [UnfoldingSignaturePadSample] = []
    private(set) var pen = -1.0
    private(set) var written = 0
    private var before: [[Double]] = []
    private var penOrigin: Date?
    private var erasing = false
    private var source: [String]? = nil
    var still = false
    var onStart: (() -> Void)?
    var onSign: (([[Double]]) -> Void)?
    var onClear: (() -> Void)?

    func configure(still: Bool, signature: [String], onStart: (() -> Void)?, onSign: (([[Double]]) -> Void)?, onClear: (() -> Void)?) {
        self.still = still; self.onStart = onStart; self.onSign = onSign; self.onClear = onClear
        if source != signature { source = signature; samples = UnfoldingSignaturePadWriting.script(signature) }
    }
    func hold(_ control: UnfoldingSignaturePadControl, _ down: Bool, at date: Date = .now) {
        let duration = down ? UnfoldingSignaturePadMotion.pressIn : UnfoldingSignaturePadMotion.pressOut
        switch control {
        case .button: buttonPress.time(down ? 1 : 0, duration: duration, at: date, still: still)
        case .edit: editPress.time(down ? 1 : 0, duration: duration, at: date, still: still)
        case .clear: clearPress.time(down ? 1 : 0, duration: duration, at: date, still: still)
        case .close: closePress.time(down ? 1 : 0, duration: duration, at: date, still: still)
        }
        revision &+= 1
    }
    func unfold(at date: Date = .now) {
        before = ink; mode = .open
        open.spring(1, at: date, still: still)
        wording.time(1, duration: UnfoldingSignaturePadMotion.wording, at: date, still: still)
        dark.time(0, duration: UnfoldingSignaturePadMotion.darken, at: date, still: still)
        onStart?(); revision &+= 1
    }
    @discardableResult private func fold(_ strokes: [[Double]], at date: Date) -> Bool {
        let signed = !strokes.isEmpty; mode = signed ? .done : .idle
        open.spring(0, at: date, still: still)
        if signed { dark.time(1, duration: UnfoldingSignaturePadMotion.darken, at: date, still: still) }
        else { wording.time(0, duration: UnfoldingSignaturePadMotion.wording, at: date, still: still) }
        return signed
    }
    func finish(byItself: Bool, at date: Date = .now) {
        let strokes = ink
        if fold(strokes, at: date) {
            if !byItself { UINotificationFeedbackGenerator().notificationOccurred(.success) }
            onSign?(strokes)
        } else { onClear?() }
        revision &+= 1
    }
    private func erase(at date: Date) {
        inkShown.time(0, duration: UnfoldingSignaturePadMotion.inkOut, at: date, still: still)
        erasing = true
        finishEraseIfNeeded()
    }
    private func finishEraseIfNeeded() {
        guard erasing && inkShown.value == 0 else { return }
        // A completed wipe clears current strokes, including ink written while it faded.
        ink = []; path = Path(); inkShown.jump(1); erasing = false
    }
    func wipe(at date: Date = .now) { erase(at: date); onClear?(); revision &+= 1 }
    func close(at date: Date = .now) {
        erasing = false; ink = before; path = UnfoldingSignaturePadWriting.ink(ink); inkShown.jump(1)
        fold(ink, at: date); revision &+= 1
    }
    func reset(at date: Date = .now) {
        penOrigin = nil; pen = -1; mode = .idle
        open.spring(0, at: date, still: still)
        wording.time(0, duration: UnfoldingSignaturePadMotion.wording, at: date, still: still)
        dark.time(0, duration: UnfoldingSignaturePadMotion.darken, at: date, still: still)
        erase(at: date); revision &+= 1
    }
    func sign(at date: Date = .now) { written = 0; pen = 0; penOrigin = date; writeSamples(); revision &+= 1 }
    func stopPen() { penOrigin = nil; pen = -1 }
    func penDown(_ point: CGPoint) { ink.append([point.x, point.y]); path = UnfoldingSignaturePadWriting.ink(ink); revision &+= 1 }
    func penMove(_ point: CGPoint) {
        guard let last = ink.last, last.count >= 2 else { return }
        guard hypot(point.x - last[last.count - 2], point.y - last[last.count - 1]) >= UnfoldingSignaturePadStyle.inkStep else { return }
        ink[ink.count - 1].append(contentsOf: [point.x, point.y]); path = UnfoldingSignaturePadWriting.ink(ink); revision &+= 1
    }
    private func writeSamples() {
        while written < samples.count && samples[written].time <= pen {
            let sample = samples[written]
            if sample.starts { penDown(sample.point) } else { penMove(sample.point) }
            written += 1
        }
    }
    var pencil: CGPoint? {
        guard !samples.isEmpty else { return nil }
        let next = min(written, samples.count - 1), last = max(0, next - 1)
        let a = samples[last], b = samples[next], span = b.time - a.time
        let u = span > 0 ? UnfoldingSignaturePadMotion.clamp((pen - a.time) / span) : 1
        return CGPoint(x: a.point.x + (b.point.x - a.point.x) * u, y: a.point.y + (b.point.y - a.point.y) * u)
    }
    func touched(at date: Date = .now) {
        penOrigin = nil; pen = -1
        for control in [UnfoldingSignaturePadControl.button, .edit, .clear, .close] { hold(control, false, at: date) }
        touches += 1
    }
    func pressIn(_ control: UnfoldingSignaturePadControl) { touched(); hold(control, true) }
    func activate(_ control: UnfoldingSignaturePadControl) {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        switch control {
        case .button: if mode == .open { finish(byItself: false) } else if mode == .idle { unfold() }
        case .edit: unfold()
        case .clear: wipe()
        case .close: close()
        }
    }
    func advance(_ date: Date) {
        open.advance(date); wording.advance(date); dark.advance(date); inkShown.advance(date)
        buttonPress.advance(date); editPress.advance(date); clearPress.advance(date); closePress.advance(date)
        finishEraseIfNeeded()
        if let penOrigin { pen = min(UnfoldingSignaturePadMotion.signFor + UnfoldingSignaturePadMotion.pencilStay, max(0, date.timeIntervalSince(penOrigin))); writeSamples() }
        revision &+= 1
    }
    func autoplay(enabled: Bool) async {
        guard enabled else { return }
        do {
            if touches > 0 {
                try await Task.sleep(nanoseconds: UInt64(UnfoldingSignaturePadMotion.userHold * 1e9))
                reset()
                try await Task.sleep(nanoseconds: UInt64(UnfoldingSignaturePadMotion.resetFor * 1e9))
            }
            var origin = Date.now
            while !Task.isCancelled {
                let events: [(Double, UnfoldingSignaturePadControl?, Bool)] = [
                    (UnfoldingSignaturePadMotion.startAt, .button, true), (UnfoldingSignaturePadMotion.startAt + UnfoldingSignaturePadMotion.pressFor, .button, false),
                    (UnfoldingSignaturePadMotion.signAt, nil, false),
                    (UnfoldingSignaturePadMotion.finishAt, .button, true), (UnfoldingSignaturePadMotion.finishAt + UnfoldingSignaturePadMotion.pressFor, .button, false),
                    (UnfoldingSignaturePadMotion.editAt, .edit, true), (UnfoldingSignaturePadMotion.editAt + UnfoldingSignaturePadMotion.pressFor, .edit, false),
                    (UnfoldingSignaturePadMotion.clearAt, .clear, true), (UnfoldingSignaturePadMotion.clearAt + UnfoldingSignaturePadMotion.pressFor, .clear, false),
                    (UnfoldingSignaturePadMotion.foldAt, .button, true), (UnfoldingSignaturePadMotion.foldAt + UnfoldingSignaturePadMotion.pressFor, .button, false)
                ]
                for (seconds, control, down) in events {
                    let wait = origin.addingTimeInterval(seconds).timeIntervalSinceNow
                    if wait > 0 { try await Task.sleep(nanoseconds: UInt64(wait * 1e9)) }
                    try Task.checkCancellation()
                    let date = Date.now
                    if let control {
                        hold(control, down, at: date)
                        if !down {
                            switch control {
                            case .button: if seconds < UnfoldingSignaturePadMotion.signAt { unfold(at: date) } else { finish(byItself: true, at: date) }
                            case .edit: unfold(at: date)
                            case .clear: wipe(at: date)
                            case .close: break
                            }
                        }
                    } else { sign(at: date) }
                }
                origin = origin.addingTimeInterval(UnfoldingSignaturePadMotion.loop)
                let wait = origin.timeIntervalSinceNow
                if wait > 0 { try await Task.sleep(nanoseconds: UInt64(wait * 1e9)) }
            }
        } catch { return }
    }
}

private struct UnfoldingSignaturePadLoopKey: Equatable { let enabled: Bool; let touches: Int }

private struct UnfoldingSignaturePadLetterMetric { let text: String; let x: Double }
private struct UnfoldingSignaturePadWordMetric {
    let text: String
    let width: Double
    let height: Double
    let letters: [UnfoldingSignaturePadLetterMetric]
    init(_ text: String) {
        self.text = text
        let storage = NSTextStorage(string: text, attributes: [.font: UIFont.systemFont(ofSize: UnfoldingSignaturePadStyle.label, weight: .medium)])
        let layout = NSLayoutManager(), container = NSTextContainer(size: CGSize(width: UnfoldingSignaturePadStyle.stageWidth, height: .greatestFiniteMagnitude))
        container.lineFragmentPadding = 0; container.lineBreakMode = .byWordWrapping
        storage.addLayoutManager(layout); layout.addTextContainer(container); layout.ensureLayout(for: container)
        var longest = 0.0
        let glyphs = layout.glyphRange(for: container)
        layout.enumerateLineFragments(forGlyphRange: glyphs) { _, used, _, _, _ in longest = max(longest, used.width) }
        width = longest; height = ceil(layout.usedRect(for: container).height)
        letters = text.utf16.enumerated().map { index, unit in
            let glyph = layout.glyphIndexForCharacter(at: index)
            let x = glyph < layout.numberOfGlyphs ? layout.location(forGlyphAt: glyph).x + layout.lineFragmentRect(forGlyphAt: glyph, effectiveRange: nil).minX : 0
            return UnfoldingSignaturePadLetterMetric(text: String(decoding: [unit], as: UTF16.self), x: x)
        }
    }
}

private struct UnfoldingSignaturePadLabelMetric {
    let shared: UnfoldingSignaturePadWordMetric
    let start: UnfoldingSignaturePadWordMetric
    let finish: UnfoldingSignaturePadWordMetric
    let done: UnfoldingSignaturePadWordMetric
    let heading: UnfoldingSignaturePadWordMetric
    let startLeft: Double
    let finishLeft: Double
    let doneLeft: Double
    init(_ sharedWord: String, _ startWord: String, _ finishWord: String, _ doneWord: String, _ title: String) {
        shared = UnfoldingSignaturePadWordMetric(sharedWord); start = UnfoldingSignaturePadWordMetric(startWord)
        finish = UnfoldingSignaturePadWordMetric(finishWord); done = UnfoldingSignaturePadWordMetric(doneWord); heading = UnfoldingSignaturePadWordMetric(title)
        let gap = UnfoldingSignaturePadStyle.labelGap + UnfoldingSignaturePadStyle.wordGap
        startLeft = -(UnfoldingSignaturePadStyle.labelIcon + gap + start.width + shared.width) / 2
        finishLeft = -(UnfoldingSignaturePadStyle.labelIcon + gap + finish.width + shared.width) / 2
        doneLeft = -(UnfoldingSignaturePadStyle.labelDisc + gap + shared.width + done.width) / 2
    }
}

private final class UnfoldingSignaturePadLabelCache {
    private var key: [String] = []
    private var metric: UnfoldingSignaturePadLabelMetric?
    func get(_ shared: String, _ start: String, _ finish: String, _ done: String, _ title: String) -> UnfoldingSignaturePadLabelMetric {
        let next = [shared, start, finish, done, title]
        if next != key { key = next; metric = UnfoldingSignaturePadLabelMetric(shared, start, finish, done, title) }
        return metric!
    }
}

private enum UnfoldingSignaturePadDrawing {
    static let clear = Path(UnfoldingSignaturePadSVG.path(UnfoldingSignaturePadStyle.clear)!)
    static let pencil = Path(UnfoldingSignaturePadSVG.path(UnfoldingSignaturePadStyle.pencil)!)
    static let cross = Path(UnfoldingSignaturePadSVG.path(UnfoldingSignaturePadStyle.cross)!)
    static let check = Path(UnfoldingSignaturePadSVG.path(UnfoldingSignaturePadStyle.check)!)
    static let scribble = Path(UnfoldingSignaturePadSVG.path(UnfoldingSignaturePadStyle.scribble)!)
    static func color(_ css: String) -> Color { UnfoldingSignaturePadCSS.color(css) }
    static func rounded(_ rect: CGRect, _ radius: Double) -> Path { RoundedRectangle(cornerRadius: radius, style: .circular).path(in: rect) }
    static func centered(_ context: GraphicsContext) -> GraphicsContext {
        var copy = context; copy.translateBy(x: UnfoldingSignaturePadStyle.stageWidth / 2, y: UnfoldingSignaturePadStyle.stageHeight / 2); return copy
    }
    static func stroke(_ path: Path, _ color: Color, in context: GraphicsContext) {
        context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: UnfoldingSignaturePadStyle.iconStroke, lineCap: .round, lineJoin: .round))
    }
}

private enum UnfoldingSignaturePadCSS {
    private static let names: [String: UInt32] = [
        "transparent": 0x00000000,
        "aliceblue": 0xf0f8ffff,
        "antiquewhite": 0xfaebd7ff,
        "aqua": 0x00ffffff,
        "aquamarine": 0x7fffd4ff,
        "azure": 0xf0ffffff,
        "beige": 0xf5f5dcff,
        "bisque": 0xffe4c4ff,
        "black": 0x000000ff,
        "blanchedalmond": 0xffebcdff,
        "blue": 0x0000ffff,
        "blueviolet": 0x8a2be2ff,
        "brown": 0xa52a2aff,
        "burlywood": 0xdeb887ff,
        "burntsienna": 0xea7e5dff,
        "cadetblue": 0x5f9ea0ff,
        "chartreuse": 0x7fff00ff,
        "chocolate": 0xd2691eff,
        "coral": 0xff7f50ff,
        "cornflowerblue": 0x6495edff,
        "cornsilk": 0xfff8dcff,
        "crimson": 0xdc143cff,
        "cyan": 0x00ffffff,
        "darkblue": 0x00008bff,
        "darkcyan": 0x008b8bff,
        "darkgoldenrod": 0xb8860bff,
        "darkgray": 0xa9a9a9ff,
        "darkgreen": 0x006400ff,
        "darkgrey": 0xa9a9a9ff,
        "darkkhaki": 0xbdb76bff,
        "darkmagenta": 0x8b008bff,
        "darkolivegreen": 0x556b2fff,
        "darkorange": 0xff8c00ff,
        "darkorchid": 0x9932ccff,
        "darkred": 0x8b0000ff,
        "darksalmon": 0xe9967aff,
        "darkseagreen": 0x8fbc8fff,
        "darkslateblue": 0x483d8bff,
        "darkslategray": 0x2f4f4fff,
        "darkslategrey": 0x2f4f4fff,
        "darkturquoise": 0x00ced1ff,
        "darkviolet": 0x9400d3ff,
        "deeppink": 0xff1493ff,
        "deepskyblue": 0x00bfffff,
        "dimgray": 0x696969ff,
        "dimgrey": 0x696969ff,
        "dodgerblue": 0x1e90ffff,
        "firebrick": 0xb22222ff,
        "floralwhite": 0xfffaf0ff,
        "forestgreen": 0x228b22ff,
        "fuchsia": 0xff00ffff,
        "gainsboro": 0xdcdcdcff,
        "ghostwhite": 0xf8f8ffff,
        "gold": 0xffd700ff,
        "goldenrod": 0xdaa520ff,
        "gray": 0x808080ff,
        "green": 0x008000ff,
        "greenyellow": 0xadff2fff,
        "grey": 0x808080ff,
        "honeydew": 0xf0fff0ff,
        "hotpink": 0xff69b4ff,
        "indianred": 0xcd5c5cff,
        "indigo": 0x4b0082ff,
        "ivory": 0xfffff0ff,
        "khaki": 0xf0e68cff,
        "lavender": 0xe6e6faff,
        "lavenderblush": 0xfff0f5ff,
        "lawngreen": 0x7cfc00ff,
        "lemonchiffon": 0xfffacdff,
        "lightblue": 0xadd8e6ff,
        "lightcoral": 0xf08080ff,
        "lightcyan": 0xe0ffffff,
        "lightgoldenrodyellow": 0xfafad2ff,
        "lightgray": 0xd3d3d3ff,
        "lightgreen": 0x90ee90ff,
        "lightgrey": 0xd3d3d3ff,
        "lightpink": 0xffb6c1ff,
        "lightsalmon": 0xffa07aff,
        "lightseagreen": 0x20b2aaff,
        "lightskyblue": 0x87cefaff,
        "lightslategray": 0x778899ff,
        "lightslategrey": 0x778899ff,
        "lightsteelblue": 0xb0c4deff,
        "lightyellow": 0xffffe0ff,
        "lime": 0x00ff00ff,
        "limegreen": 0x32cd32ff,
        "linen": 0xfaf0e6ff,
        "magenta": 0xff00ffff,
        "maroon": 0x800000ff,
        "mediumaquamarine": 0x66cdaaff,
        "mediumblue": 0x0000cdff,
        "mediumorchid": 0xba55d3ff,
        "mediumpurple": 0x9370dbff,
        "mediumseagreen": 0x3cb371ff,
        "mediumslateblue": 0x7b68eeff,
        "mediumspringgreen": 0x00fa9aff,
        "mediumturquoise": 0x48d1ccff,
        "mediumvioletred": 0xc71585ff,
        "midnightblue": 0x191970ff,
        "mintcream": 0xf5fffaff,
        "mistyrose": 0xffe4e1ff,
        "moccasin": 0xffe4b5ff,
        "navajowhite": 0xffdeadff,
        "navy": 0x000080ff,
        "oldlace": 0xfdf5e6ff,
        "olive": 0x808000ff,
        "olivedrab": 0x6b8e23ff,
        "orange": 0xffa500ff,
        "orangered": 0xff4500ff,
        "orchid": 0xda70d6ff,
        "palegoldenrod": 0xeee8aaff,
        "palegreen": 0x98fb98ff,
        "paleturquoise": 0xafeeeeff,
        "palevioletred": 0xdb7093ff,
        "papayawhip": 0xffefd5ff,
        "peachpuff": 0xffdab9ff,
        "peru": 0xcd853fff,
        "pink": 0xffc0cbff,
        "plum": 0xdda0ddff,
        "powderblue": 0xb0e0e6ff,
        "purple": 0x800080ff,
        "rebeccapurple": 0x663399ff,
        "red": 0xff0000ff,
        "rosybrown": 0xbc8f8fff,
        "royalblue": 0x4169e1ff,
        "saddlebrown": 0x8b4513ff,
        "salmon": 0xfa8072ff,
        "sandybrown": 0xf4a460ff,
        "seagreen": 0x2e8b57ff,
        "seashell": 0xfff5eeff,
        "sienna": 0xa0522dff,
        "silver": 0xc0c0c0ff,
        "skyblue": 0x87ceebff,
        "slateblue": 0x6a5acdff,
        "slategray": 0x708090ff,
        "slategrey": 0x708090ff,
        "snow": 0xfffafaff,
        "springgreen": 0x00ff7fff,
        "steelblue": 0x4682b4ff,
        "tan": 0xd2b48cff,
        "teal": 0x008080ff,
        "thistle": 0xd8bfd8ff,
        "tomato": 0xff6347ff,
        "turquoise": 0x40e0d0ff,
        "violet": 0xee82eeff,
        "wheat": 0xf5deb3ff,
        "white": 0xffffffff,
        "whitesmoke": 0xf5f5f5ff,
        "yellow": 0xffff00ff,
        "yellowgreen": 0x9acd32ff
    ]
    private static let hex = try! NSRegularExpression(pattern: #"^#([0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$"#)
    private static let formats: [(String, Bool, NSRegularExpression)] = [
        ("rgb", false, try! NSRegularExpression(pattern: #"rgba?\(\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+)\s*\)"#)),
        ("rgb", true, try! NSRegularExpression(pattern: #"rgba?\(\s*([-+]?\d*\.?\d+)\s*,\s*([-+]?\d*\.?\d+)\s*,\s*([-+]?\d*\.?\d+)\s*,\s*([-+]?\d*\.?\d+)\s*\)"#)),
        ("rgb", true, try! NSRegularExpression(pattern: #"rgba?\(\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+)\s*/\s*([-+]?\d*\.?\d+)\s*\)"#)),
        ("hsl", false, try! NSRegularExpression(pattern: #"hsl\(\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+%)\s*,?\s*([-+]?\d*\.?\d+%)\s*\)"#)),
        ("hsl", true, try! NSRegularExpression(pattern: #"hsla\(\s*([-+]?\d*\.?\d+)\s*,\s*([-+]?\d*\.?\d+%)\s*,\s*([-+]?\d*\.?\d+%)\s*,\s*([-+]?\d*\.?\d+)\s*\)"#)),
        ("hsl", true, try! NSRegularExpression(pattern: #"hsla\(\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+%)\s*,?\s*([-+]?\d*\.?\d+%)\s*/\s*([-+]?\d*\.?\d+)\s*\)"#)),
        ("hwb", false, try! NSRegularExpression(pattern: #"hwb\(\s*([-+]?\d*\.?\d+)\s*([-+]?\d*\.?\d+%)\s*([-+]?\d*\.?\d+%)\s*\)"#)),
        ("hwb", true, try! NSRegularExpression(pattern: #"hwb\(\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+%)\s*,?\s*([-+]?\d*\.?\d+%)\s*/\s*([-+]?\d*\.?\d+)\s*\)"#))
    ]
    private static func clamp(_ value: Double, _ high: Double = 1) -> Double { min(high, max(0, value)) }
    private static func byte(_ value: Double) -> Double {
        guard value.isFinite else { return 0 }
        let low = floor(value)
        return value - low < 0.5 ? low : low + 1
    }
    private static func hue(_ p: Double, _ q: Double, _ input: Double) -> Double {
        var t = input
        if t < 0 { t += 1 }; if t > 1 { t -= 1 }
        if t < 1 / 6 { return p + (q - p) * 6 * t }
        if t < 1 / 2 { return q }
        if t < 2 / 3 { return p + (q - p) * (2 / 3 - t) * 6 }
        return p
    }
    static func color(_ source: String) -> Color {
        if let value = names[source] { return Color(hex: value >> 8, opacity: Double(value & 255) / 255) }
        let ns = source as NSString, whole = NSRange(location: 0, length: (source as NSString).length)
        if hex.firstMatch(in: source, range: whole) != nil {
            var digits = String(source.dropFirst())
            if digits.count == 3 || digits.count == 4 { digits = digits.map { "\($0)\($0)" }.joined() }
            if digits.count == 6 { digits += "ff" }
            if let value = UInt32(digits, radix: 16) { return Color(hex: value >> 8, opacity: Double(value & 255) / 255) }
        }
        for (kind, alpha, pattern) in formats {
            guard let match = pattern.firstMatch(in: source, range: whole) else { continue }
            let values = (1..<match.numberOfRanges).map { Double(ns.substring(with: match.range(at: $0)).replacingOccurrences(of: "%", with: "")) ?? 0 }
            let opacity = alpha ? byte(clamp(values[3]) * 255) / 255 : 1
            if kind == "rgb" {
                return .rgba(clamp(values[0], 255).rounded(.towardZero), clamp(values[1], 255).rounded(.towardZero), clamp(values[2], 255).rounded(.towardZero), opacity)
            }
            let h = ((values[0].truncatingRemainder(dividingBy: 360) + 360).truncatingRemainder(dividingBy: 360)) / 360
            let second = clamp(values[1] / 100), third = clamp(values[2] / 100)
            let channels: [Double]
            if kind == "hwb" {
                if second + third >= 1 { channels = Array(repeating: second / (second + third), count: 3) }
                else { channels = [h + 1 / 3, h, h - 1 / 3].map { hue(0, 1, $0) * (1 - second - third) + second } }
            } else {
                let q = third < 0.5 ? third * (1 + second) : third + second - third * second
                let p = 2 * third - q
                channels = [h + 1 / 3, h, h - 1 / 3].map { hue(p, q, $0) }
            }
            return .rgba(byte(channels[0] * 255), byte(channels[1] * 255), byte(channels[2] * 255), opacity)
        }
        return .clear
    }
}


private struct UnfoldingSignaturePadHit: View {
    let width: Double
    let height: Double
    let label: String
    let pressIn: () -> Void
    let hold: (Bool) -> Void
    let activate: () -> Void
    @State private var began = false
    @State private var inside = false
    @State private var activatedAt: Date?
    @State private var pendingPressOut: Task<Void, Never>?
    @GestureState private var active = false
    private func contains(_ point: CGPoint) -> Bool {
        point.x > -UnfoldingSignaturePadStyle.pressRetentionSide && point.x < width + UnfoldingSignaturePadStyle.pressRetentionSide && point.y > -UnfoldingSignaturePadStyle.pressRetentionTop && point.y < height + UnfoldingSignaturePadStyle.pressRetentionBottom
    }
    private func activateInside() { inside = true; activatedAt = .now; pressIn() }
    private func deactivateInside() {
        inside = false
        let delay = max(0, UnfoldingSignaturePadStyle.minimumPressDuration - Date.now.timeIntervalSince(activatedAt ?? .distantPast))
        activatedAt = nil
        if delay == 0 { hold(false); return }
        pendingPressOut = Task { @MainActor in
            do { try await Task.sleep(nanoseconds: UInt64(delay * 1e9)); try Task.checkCancellation(); hold(false) }
            catch { return }
        }
    }
    var body: some View {
        Color.clear.frame(width: width, height: height).contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0)
                .updating($active) { _, active, _ in active = true }
                .onChanged { event in
                    if !began { pendingPressOut?.cancel(); began = true; activateInside() }
                    let next = contains(event.location)
                    if next != inside { if next { activateInside() } else { deactivateInside() } }
                }
                .onEnded { _ in
                    guard began else { return }
                    let perform = inside; began = false
                    if inside { deactivateInside() }
                    if perform { activate() }
                })
            .onChange(of: active) { _, next in
                if !next { DispatchQueue.main.async { if !active && began { began = false; if inside { deactivateInside() } } } }
            }
            // Pressability cancels the pending press-out when its hit view unmounts.
            .onDisappear { pendingPressOut?.cancel(); began = false; inside = false; activatedAt = nil }
            .accessibilityElement(children: .ignore).accessibilityAddTraits(.isButton).accessibilityLabel(label)
            .accessibilityAction { activate() }
    }
}

/// The card grows from the pill's frame; fixed hit regions switch as soon as its mode changes.
struct UnfoldingSignaturePad: View {
    var autoPlay = false
    var sharedWord = "Signing"
    var startWord = "Start"
    var finishWord = "Finish"
    var doneWord = "Done"
    var title = "Sign"
    var inkColor = "#111113"
    var inkWidth = 3.0
    var background = "#ffffff"
    var signature = UnfoldingSignaturePadStyle.signature
    var onStart: (() -> Void)? = nil
    var onSign: (([[Double]]) -> Void)? = nil
    var onClear: (() -> Void)? = nil
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @StateObject private var state = UnfoldingSignaturePadState()
    @State private var cache = UnfoldingSignaturePadLabelCache()
    @State private var drawing = false
    @GestureState private var drawActive = false
    private typealias S = UnfoldingSignaturePadStyle
    private typealias M = UnfoldingSignaturePadMotion
    private typealias D = UnfoldingSignaturePadDrawing

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let width = proxy.size.width + insets.leading + insets.trailing
            TimelineView(.animation) { timeline in
                let _ = state.configure(still: reduceMotion, signature: signature, onStart: onStart, onSign: onSign, onClear: onClear)
                let metric = cache.get(sharedWord, startWord, finishWord, doneWord, title)
                stage(metric)
                    .frame(width: width, height: proxy.size.height).offset(x: -insets.leading)
                    .onChange(of: timeline.date) { _, date in state.advance(date) }
            }
        }
        .background(D.color(background).ignoresSafeArea()).preferredColorScheme(.light)
        .task(id: UnfoldingSignaturePadLoopKey(enabled: autoPlay, touches: state.touches)) { await state.autoplay(enabled: autoPlay) }
        .onChange(of: drawActive) { _, active in if !active { drawing = false } }
        .onDisappear { state.stopPen() }
    }
    private func stage(_ metric: UnfoldingSignaturePadLabelMetric) -> some View {
        let o = state.open.value, k = state.dark.value
        let headerOpacity = M.smooth(o, 0.45, 0.95)
        return ZStack(alignment: .topLeading) {
            outlineAndHeader(open: o, dark: k, opacity: headerOpacity)
            Text(metric.heading.text).font(.system(size: S.label, weight: .medium)).foregroundStyle(S.mix(S.title, S.title, 0))
                .fixedSize(horizontal: false, vertical: true).frame(width: metric.heading.width + 2, height: metric.heading.height, alignment: .topLeading)
                .opacity(headerOpacity).offset(x: S.stageWidth / 2 - metric.heading.width / 2, y: S.stageHeight / 2 + S.headerY - metric.heading.height / 2)
                .allowsHitTesting(false).accessibilityHidden(true)
            ink(open: o, dark: k)
            chrome(open: o, dark: k, metric: metric)
            words(metric, open: o, dark: k)
            disc(open: o, dark: k, metric: metric)
            hits
        }
        .frame(width: S.stageWidth, height: S.stageHeight).clipped()
    }
    private func outlineAndHeader(open o: Double, dark k: Double, opacity: Double) -> some View {
        Canvas { context, _ in
            var canvas = D.centered(context)
            let width = S.buttonWidth(o, k) + S.inset * 2 * o
            let top = -S.button / 2 + (-S.cardHeight / 2 + S.button / 2) * o
            let bottom = S.drop * o + S.button / 2 + S.inset * o
            let radius = S.button / 2 + (S.cardRadius - S.button / 2) * M.clamp(o)
            canvas.opacity = M.smooth(o, 0.12, 0.6)
            canvas.stroke(D.rounded(CGRect(x: S.buttonX(o, k) - width / 2, y: top, width: width, height: max(S.button, bottom - top)), radius), with: .color(S.outlineColor), style: StrokeStyle(lineWidth: S.outline, dash: S.dash))
            var clear = D.centered(context); clear.opacity = opacity
            clear.translateBy(x: -S.cardWidth / 2 + S.headerInset, y: S.headerY)
            clear.scaleBy(x: 1 - S.headerPressScale * state.clearPress.value, y: 1 - S.headerPressScale * state.clearPress.value)
            clear.rotate(by: .radians(state.clearPress.value * S.clearTurn))
            clear.translateBy(x: -S.clearSize / 2, y: -S.clearSize / 2); clear.scaleBy(x: S.clearSize / 24, y: S.clearSize / 24)
            clear.fill(D.clear, with: .color(S.headerColor))
            var close = D.centered(context); close.opacity = opacity
            close.translateBy(x: S.cardWidth / 2 - S.headerInset, y: S.headerY)
            close.scaleBy(x: 1 - S.headerPressScale * state.closePress.value, y: 1 - S.headerPressScale * state.closePress.value)
            close.fill(Path(ellipseIn: CGRect(x: -S.headerIcon / 2, y: -S.headerIcon / 2, width: S.headerIcon, height: S.headerIcon)), with: .color(S.headerColor))
            close.translateBy(x: -12, y: -12); D.stroke(D.cross, .white, in: close)
        }.allowsHitTesting(false).accessibilityHidden(true)
    }
    private func ink(open o: Double, dark k: Double) -> some View {
        Canvas { context, _ in
            var ink = D.centered(context); ink.opacity = state.inkShown.value * M.smooth(o, 0.1, 0.7)
            ink.translateBy(x: S.buttonX(o, k), y: S.drop * o)
            let scale = S.inkFolded + (1 - S.inkFolded) * o; ink.scaleBy(x: scale, y: scale); ink.translateBy(x: 0, y: -S.drop)
            ink.stroke(state.path, with: .color(D.color(inkColor)), style: StrokeStyle(lineWidth: inkWidth, lineCap: .round, lineJoin: .round))
            if let pencil = state.pencil {
                ink.opacity *= M.smooth(state.pen, 0, M.pencilIn) * (1 - M.smooth(state.pen, M.signFor + M.pencilFadeAt, M.signFor + M.pencilStay))
                ink.translateBy(x: pencil.x - 3, y: pencil.y - 21); ink.fill(D.pencil, with: .color(S.pointer))
            }
        }.allowsHitTesting(false).accessibilityHidden(true)
    }
    private func chrome(open o: Double, dark k: Double, metric: UnfoldingSignaturePadLabelMetric) -> some View {
        Canvas { context, _ in
            let bx = S.buttonX(o, k), bw = S.buttonWidth(o, k), press = 1 - S.pressScale * state.buttonPress.value
            var edit = D.centered(context); edit.opacity = M.smooth(k * (1 - o), 0.15, 0.7)
            edit.translateBy(x: bx + bw / 2 - S.edit / 2 - S.editSlideInset + (S.edit + S.editGap + S.editSlideInset) * M.clamp(k * (1 - o)), y: S.drop * o)
            let editScale = 1 - S.pressScale * S.editPressMultiplier * state.editPress.value; edit.scaleBy(x: editScale, y: editScale)
            edit.fill(Path(ellipseIn: CGRect(x: -S.edit / 2, y: -S.edit / 2, width: S.edit, height: S.edit)), with: .color(S.lightColor))
            edit.translateBy(x: -12, y: -12); edit.fill(D.pencil, with: .color(S.darkColor))
            var shadow = D.centered(context); shadow.addFilter(.blur(radius: S.shadowBlur))
            shadow.fill(D.rounded(CGRect(x: bx - (bw - S.shadowWidthInset) / 2, y: S.drop * o - S.button / 2 + S.shadowY, width: bw - S.shadowWidthInset, height: S.button - S.shadowHeightInset), S.button / 2), with: .color(S.shadow))
            var pill = D.centered(context)
            pill.fill(D.rounded(CGRect(x: bx - bw * press / 2, y: S.drop * o - S.button * press / 2, width: bw * press, height: S.button * press), S.button * press / 2), with: .color(S.mix(S.light, S.dark, k)))
            pill.translateBy(x: bx, y: S.drop * o); pill.scaleBy(x: press, y: press)
            pill.opacity = 1 - M.smooth(k, 0, 0.5)
            pill.translateBy(x: iconX(metric), y: -S.labelIcon / 2)
            let tint = S.mix(S.text, S.white, M.smooth(k, 0.1, 0.6))
            D.stroke(D.scribble, tint, in: pill)
            pill.translateBy(x: S.labelPencilX, y: S.labelPencilY); pill.scaleBy(x: S.labelPencilScale, y: S.labelPencilScale)
            pill.fill(D.pencil, with: .color(tint))
        }.allowsHitTesting(false).accessibilityHidden(true)
    }
    private func iconX(_ metric: UnfoldingSignaturePadLabelMetric) -> Double {
        metric.startLeft + (metric.finishLeft - metric.startLeft) * state.wording.value + (metric.doneLeft - metric.finishLeft) * state.dark.value
    }
    private func sharedX(_ metric: UnfoldingSignaturePadLabelMetric) -> Double {
        let x = iconX(metric), first = metric.start.width + (metric.finish.width - metric.start.width) * state.wording.value
        let afterFirst = x + S.labelIcon + S.labelGap + first + S.wordGap, afterDisc = x + S.labelDisc + S.labelGap
        return afterFirst + (afterDisc - afterFirst) * M.smooth(state.dark.value, 0.15, 1)
    }
    private func words(_ metric: UnfoldingSignaturePadLabelMetric, open o: Double, dark k: Double) -> some View {
        let x = iconX(metric), shared = sharedX(metric), tint = S.mix(S.text, S.white, M.smooth(k, 0.1, 0.6))
        return ZStack(alignment: .topLeading) {
            letters(metric.start, word: .idle, x: x + S.labelIcon + S.labelGap, height: metric.shared.height, tint: tint)
            letters(metric.finish, word: .open, x: x + S.labelIcon + S.labelGap, height: metric.shared.height, tint: tint)
            Text(metric.shared.text).font(.system(size: S.label, weight: .medium)).foregroundStyle(tint)
                .fixedSize(horizontal: false, vertical: true).frame(width: metric.shared.width + 2, height: metric.shared.height, alignment: .topLeading)
                .offset(x: S.stageWidth / 2 + shared, y: S.stageHeight / 2 - metric.shared.height / 2)
            letters(metric.done, word: .done, x: shared + metric.shared.width + S.wordGap, height: metric.shared.height, tint: tint)
        }
        // The stack is only as large as one word; the letters are placed from the stage's corner.
        .frame(width: S.stageWidth, height: S.stageHeight, alignment: .topLeading).scaleEffect(1 - S.pressScale * state.buttonPress.value)
        .offset(x: S.buttonX(o, k), y: S.drop * o).allowsHitTesting(false).accessibilityHidden(true)
    }
    private func letters(_ metric: UnfoldingSignaturePadWordMetric, word: UnfoldingSignaturePadMode, x: Double, height: Double, tint: Color) -> some View {
        ForEach(metric.letters.indices, id: \.self) { index in
            let span = 1 + S.letterStagger * Double(metric.letters.count - 1)
            let finishing = M.clamp(state.wording.value * span - S.letterStagger * Double(index))
            let done = M.clamp(state.dark.value * span - S.letterStagger * Double(index))
            let shown = word == .idle ? 1 - finishing : word == .open ? finishing * (1 - done) : done
            Text(metric.letters[index].text).font(.system(size: S.label, weight: .medium)).foregroundStyle(tint)
                .frame(width: S.label * 2, height: height, alignment: .topLeading)
                .blur(radius: S.letterBlur * (1 - shown)).opacity(shown)
                .offset(x: S.stageWidth / 2 + x + metric.letters[index].x, y: S.stageHeight / 2 - height / 2 + S.letterDrop * (1 - shown))
        }
    }
    private func disc(open o: Double, dark k: Double, metric: UnfoldingSignaturePadLabelMetric) -> some View {
        Canvas { context, _ in
            var disc = D.centered(context)
            disc.translateBy(x: S.buttonX(o, k), y: S.drop * o)
            let press = 1 - S.pressScale * state.buttonPress.value; disc.scaleBy(x: press, y: press)
            disc.translateBy(x: iconX(metric) + S.labelDisc / 2, y: 0)
            let scale = 0.5 + 0.5 * M.smooth(k, 0.3, 1); disc.scaleBy(x: scale, y: scale); disc.opacity = M.smooth(k, 0.3, 0.9)
            disc.fill(Path(ellipseIn: CGRect(x: -S.labelDisc / 2, y: -S.labelDisc / 2, width: S.labelDisc, height: S.labelDisc)), with: .color(.white))
            disc.translateBy(x: -12, y: -12); D.stroke(D.check, S.darkColor, in: disc)
        }.allowsHitTesting(false).accessibilityHidden(true)
    }
    private func hit(_ control: UnfoldingSignaturePadControl, width: Double, height: Double, label: String) -> some View {
        UnfoldingSignaturePadHit(width: width, height: height, label: label, pressIn: { state.pressIn(control) }, hold: { state.hold(control, $0) }, activate: { state.activate(control) })
    }
    @ViewBuilder private var hits: some View {
        let mx = S.stageWidth / 2, my = S.stageHeight / 2
        switch state.mode {
        case .open:
            Color.clear.frame(width: S.cardWidth - S.padInset * 2, height: S.padBottom - S.padTop).contentShape(Rectangle())
                .gesture(DragGesture(minimumDistance: 0).updating($drawActive) { _, active, _ in active = true }
                    .onChanged { event in
                        if !drawing {
                            drawing = true; state.touched()
                            state.penDown(CGPoint(x: event.startLocation.x - S.cardWidth / 2 + S.padInset, y: event.startLocation.y + S.padTop))
                        }
                        state.penMove(CGPoint(x: event.location.x - S.cardWidth / 2 + S.padInset, y: event.location.y + S.padTop))
                    }.onEnded { _ in drawing = false })
                .accessibilityLabel("\(title): draw your signature here")
                .offset(x: S.stageMargin + S.padInset, y: my + S.padTop)
            hit(.clear, width: S.hit, height: S.hit, label: "Clear").offset(x: S.stageMargin + S.headerInset - S.hit / 2, y: my + S.headerY - S.hit / 2)
            hit(.close, width: S.hit, height: S.hit, label: "Close").offset(x: S.stageMargin + S.cardWidth - S.headerInset - S.hit / 2, y: my + S.headerY - S.hit / 2)
            hit(.button, width: S.openWidth, height: S.button, label: "\(finishWord) \(sharedWord)").offset(x: mx - S.openWidth / 2, y: my + S.drop - S.button / 2)
        case .idle:
            hit(.button, width: S.startWidth, height: S.button, label: "\(startWord) \(sharedWord)").offset(x: mx - S.startWidth / 2, y: my - S.button / 2)
        case .done:
            let left = mx - (S.doneWidth + S.editGap + S.edit) / 2
            Color.clear.frame(width: S.doneWidth, height: S.button).accessibilityElement(children: .ignore).accessibilityLabel("\(sharedWord) \(doneWord)")
                .offset(x: left, y: my - S.button / 2)
            hit(.edit, width: S.edit, height: S.edit, label: "Edit signature").offset(x: left + S.doneWidth + S.editGap, y: my - S.edit / 2)
        }
    }
}

private enum UnfoldingSignaturePadSVG {
    private static let toleranceSquared = 0.025 * 0.025
    private static let maximumDepth = 18
    private struct UnfoldingSignaturePadSVGReader {
        let bytes: [UInt8]
        var index = 0
        mutating func skip() {
            while index < bytes.count, [UInt8(9), 10, 13, 32, 44].contains(bytes[index]) { index += 1 }
        }
        mutating func number() -> Double? {
            skip()
            let start = index
            if index < bytes.count, bytes[index] == 43 || bytes[index] == 45 { index += 1 }
            var digits = 0
            while index < bytes.count, (UInt8(48)...57).contains(bytes[index]) { index += 1; digits += 1 }
            if index < bytes.count, bytes[index] == 46 {
                index += 1
                while index < bytes.count, (UInt8(48)...57).contains(bytes[index]) { index += 1; digits += 1 }
            }
            guard digits > 0 else { index = start; return nil }
            if index < bytes.count, bytes[index] == 69 || bytes[index] == 101 {
                index += 1
                if index < bytes.count, bytes[index] == 43 || bytes[index] == 45 { index += 1 }
                let exponent = index
                while index < bytes.count, (UInt8(48)...57).contains(bytes[index]) { index += 1 }
                guard index > exponent else { index = start; return nil }
            }
            guard let value = Double(String(decoding: bytes[start..<index], as: UTF8.self)), value.isFinite else { return nil }
            return value
        }
        mutating func flag() -> Bool? {
            skip()
            guard index < bytes.count, bytes[index] == 48 || bytes[index] == 49 else { return nil }
            let value = bytes[index] == 49
            index += 1
            return value
        }
    }

    static func path(_ source: String) -> CGPath? {
        var reader = UnfoldingSignaturePadSVGReader(bytes: Array(source.utf8))
        let result = CGMutablePath()
        var command: UInt8?
        var previous: UInt8 = 0
        var current = CGPoint.zero
        var start = CGPoint.zero
        var cubic: CGPoint?
        var quadratic: CGPoint?
        var began = false
        func numbers(_ count: Int) -> [Double]? {
            var out: [Double] = []
            for _ in 0..<count {
                guard let value = reader.number() else { return nil }
                out.append(value)
            }
            return out
        }
        while true {
            reader.skip()
            if reader.index >= reader.bytes.count { break }
            let byte = reader.bytes[reader.index]
            if (UInt8(65)...90).contains(byte) || (UInt8(97)...122).contains(byte) {
                guard Array("MmLlHhVvCcSsQqTtAaZz".utf8).contains(byte) else { return nil }
                command = byte
                reader.index += 1
                if byte == 90 || byte == 122 {
                    guard began else { return nil }
                    result.closeSubpath()
                    current = start; cubic = nil; quadratic = nil; previous = 122; command = nil
                    continue
                }
            }
            guard let letter = command else { return nil }
            let relative = letter >= 97
            let kind = relative ? letter : letter + 32
            guard began || kind == 109 else { return nil }
            let base = current
            func point(_ x: Double, _ y: Double) -> CGPoint {
                CGPoint(x: CGFloat(x) + (relative ? base.x : 0), y: CGFloat(y) + (relative ? base.y : 0))
            }
            switch kind {
            case 109:
                guard let p = numbers(2) else { return nil }
                current = point(p[0], p[1]); start = current; began = true
                result.move(to: current)
                command = relative ? 108 : 76
                cubic = nil; quadratic = nil
            case 108:
                guard let p = numbers(2) else { return nil }
                current = point(p[0], p[1]); result.addLine(to: current)
                cubic = nil; quadratic = nil
            case 104:
                guard let p = numbers(1) else { return nil }
                current = CGPoint(x: CGFloat(p[0]) + (relative ? base.x : 0), y: base.y)
                result.addLine(to: current); cubic = nil; quadratic = nil
            case 118:
                guard let p = numbers(1) else { return nil }
                current = CGPoint(x: base.x, y: CGFloat(p[0]) + (relative ? base.y : 0))
                result.addLine(to: current); cubic = nil; quadratic = nil
            case 99:
                guard let p = numbers(6) else { return nil }
                let c1 = point(p[0], p[1]), c2 = point(p[2], p[3])
                current = point(p[4], p[5])
                result.addCurve(to: current, control1: c1, control2: c2)
                cubic = c2; quadratic = nil
            case 115:
                guard let p = numbers(4) else { return nil }
                let c1 = (previous == 99 || previous == 115) && cubic != nil ? reflect(cubic!, around: base) : base
                let c2 = point(p[0], p[1]); current = point(p[2], p[3])
                result.addCurve(to: current, control1: c1, control2: c2)
                cubic = c2; quadratic = nil
            case 113:
                guard let p = numbers(4) else { return nil }
                let control = point(p[0], p[1]); current = point(p[2], p[3])
                result.addQuadCurve(to: current, control: control)
                quadratic = control; cubic = nil
            case 116:
                guard let p = numbers(2) else { return nil }
                let control = (previous == 113 || previous == 116) && quadratic != nil ? reflect(quadratic!, around: base) : base
                current = point(p[0], p[1]); result.addQuadCurve(to: current, control: control)
                quadratic = control; cubic = nil
            case 97:
                guard let p = numbers(3), let large = reader.flag(), let sweep = reader.flag(), let end = numbers(2) else { return nil }
                current = point(end[0], end[1])
                guard arc(from: base, to: current, rx: abs(p[0]), ry: abs(p[1]), degrees: p[2], large: large, sweep: sweep, into: result) else { return nil }
                cubic = nil; quadratic = nil
            default: return nil
            }
            guard current.x.isFinite, current.y.isFinite else { return nil }
            previous = kind
        }
        return began ? result.copy() : nil
    }

    private static func reflect(_ point: CGPoint, around centre: CGPoint) -> CGPoint {
        CGPoint(x: 2 * centre.x - point.x, y: 2 * centre.y - point.y)
    }
    private static func midpoint(_ a: CGPoint, _ b: CGPoint) -> CGPoint {
        CGPoint(x: (a.x + b.x) / 2, y: (a.y + b.y) / 2)
    }
    private static func arc(from a: CGPoint, to b: CGPoint, rx inputX: Double, ry inputY: Double, degrees: Double, large: Bool, sweep: Bool, into path: CGMutablePath) -> Bool {
        if a == b { return true }
        if inputX == 0 || inputY == 0 { path.addLine(to: b); return true }
        var rx = inputX, ry = inputY
        let phi = degrees.truncatingRemainder(dividingBy: 360) * .pi / 180
        let c = cos(phi), s = sin(phi)
        let dx = Double(a.x - b.x) / 2, dy = Double(a.y - b.y) / 2
        let x = c * dx + s * dy, y = -s * dx + c * dy
        let size = x * x / (rx * rx) + y * y / (ry * ry)
        if size > 1 { let correction = sqrt(size); rx *= correction; ry *= correction }
        let denominator = rx * rx * y * y + ry * ry * x * x
        guard denominator.isFinite, denominator > 0 else { return false }
        let ratio = max(0, (rx * rx * ry * ry - denominator) / denominator)
        let factor = (large == sweep ? -1.0 : 1.0) * sqrt(ratio)
        let cx = factor * rx * y / ry, cy = -factor * ry * x / rx
        let centreX = c * cx - s * cy + Double(a.x + b.x) / 2
        let centreY = s * cx + c * cy + Double(a.y + b.y) / 2
        let ux = (x - cx) / rx, uy = (y - cy) / ry
        let vx = (-x - cx) / rx, vy = (-y - cy) / ry
        var angle = atan2(uy, ux)
        var delta = atan2(ux * vy - uy * vx, ux * vx + uy * vy)
        if !sweep && delta > 0 { delta -= 2 * .pi }
        if sweep && delta < 0 { delta += 2 * .pi }
        guard [centreX, centreY, angle, delta].allSatisfy({ $0.isFinite }) else { return false }
        let count = max(1, Int(ceil(abs(delta) / (.pi / 2))))
        let step = delta / Double(count)
        func position(_ t: Double) -> CGPoint {
            CGPoint(x: centreX + c * rx * cos(t) - s * ry * sin(t), y: centreY + s * rx * cos(t) + c * ry * sin(t))
        }
        func tangent(_ t: Double) -> CGPoint {
            CGPoint(x: -c * rx * sin(t) - s * ry * cos(t), y: -s * rx * sin(t) + c * ry * cos(t))
        }
        for index in 0..<count {
            let next = angle + step, k = 4.0 / 3 * tan(step / 4)
            let p0 = position(angle), p1 = position(next), d0 = tangent(angle), d1 = tangent(next)
            let control1 = CGPoint(x: p0.x + CGFloat(k) * d0.x, y: p0.y + CGFloat(k) * d0.y)
            let control2 = CGPoint(x: p1.x - CGFloat(k) * d1.x, y: p1.y - CGFloat(k) * d1.y)
            path.addCurve(to: index == count - 1 ? b : p1, control1: control1, control2: control2)
            angle = next
        }
        return true
    }

    static func contour(_ source: String) -> [(point: CGPoint, distance: Double)] {
        guard let path = path(source) else { return [] }
        var points: [CGPoint] = []
        var current = CGPoint.zero, start = CGPoint.zero
        var finished = false
        func append(_ point: CGPoint) {
            if points.last != point { points.append(point) }
        }
        // Curves are flattened once, before playback; frame drawing uses cached samples.
        path.applyWithBlock { pointer in
            if finished { return }
            let element = pointer.pointee
            switch element.type {
            case .moveToPoint:
                if points.count > 1 { finished = true; return }
                current = element.points[0]; start = current; points = [current]
            case .addLineToPoint:
                current = element.points[0]; append(current)
            case .addQuadCurveToPoint:
                let end = element.points[1]
                flattenQuad(current, element.points[0], end, depth: 0, append: append)
                current = end
            case .addCurveToPoint:
                let end = element.points[2]
                flattenCubic(current, element.points[0], element.points[1], end, depth: 0, append: append)
                current = end
            case .closeSubpath:
                append(start); current = start
                if points.count > 1 { finished = true }
            @unknown default: break
            }
        }
        guard points.count > 1 else { return [] }
        var total = 0.0
        return points.enumerated().map { index, point in
            if index > 0 { total += Double(hypot(point.x - points[index - 1].x, point.y - points[index - 1].y)) }
            return (point, total)
        }
    }
    private static func distanceSquared(_ point: CGPoint, to a: CGPoint, _ b: CGPoint) -> CGFloat {
        let dx = b.x - a.x, dy = b.y - a.y
        let length = dx * dx + dy * dy
        let t = length > 0 ? min(1, max(0, ((point.x - a.x) * dx + (point.y - a.y) * dy) / length)) : 0
        let x = point.x - a.x - t * dx, y = point.y - a.y - t * dy
        return x * x + y * y
    }
    private static func flattenQuad(_ a: CGPoint, _ control: CGPoint, _ b: CGPoint, depth: Int, append: (CGPoint) -> Void) {
        if depth >= maximumDepth || distanceSquared(control, to: a, b) <= toleranceSquared { append(b); return }
        let left = midpoint(a, control), right = midpoint(control, b), middle = midpoint(left, right)
        flattenQuad(a, left, middle, depth: depth + 1, append: append)
        flattenQuad(middle, right, b, depth: depth + 1, append: append)
    }
    private static func flattenCubic(_ a: CGPoint, _ c1: CGPoint, _ c2: CGPoint, _ b: CGPoint, depth: Int, append: (CGPoint) -> Void) {
        if depth >= maximumDepth || max(distanceSquared(c1, to: a, b), distanceSquared(c2, to: a, b)) <= toleranceSquared { append(b); return }
        let ab = midpoint(a, c1), bc = midpoint(c1, c2), cd = midpoint(c2, b)
        let left = midpoint(ab, bc), right = midpoint(bc, cd), middle = midpoint(left, right)
        flattenCubic(a, ab, left, middle, depth: depth + 1, append: append)
        flattenCubic(middle, right, cd, b, depth: depth + 1, append: append)
    }
}

