import SwiftUI
import UIKit

let OrbitUploadDropzoneLoop = 9.2

struct OrbitUploadDropzoneCopy: Equatable {
    var title: String
    var text: String
    static let idle = OrbitUploadDropzoneCopy(title: "Import file", text: "Tap to choose a file to upload it to your project. It only takes a few seconds")
    static let hover = OrbitUploadDropzoneCopy(title: "Release file", text: "Release your files here to upload")
    static let importing = OrbitUploadDropzoneCopy(title: "Importing data", text: "Please wait a few seconds while we’re importing your data to the project")
    static let done = OrbitUploadDropzoneCopy(title: "Import complete", text: "Your import is complete. You can now start sharing your file.")
}

private enum OrbitUploadDropzoneStyle {
    static let backdrop = "#F1F3F5", card = "#17161F", ring = "#2A2932", ink = "#FFFFFF", mute = "#A0A0A8"
    static let discBlack = Color.black, buttonDim = 0.22
    static let margin = 24.0, cardMax = 360.0, cardHeight = 340.0, cardRadius = 8.0, cardLift = 12.0, iconY = 102.0
    static let ringIdle = 36.0, discIdle = 22.0, glow = 32.0, glowBlur = 12.0, discImport = 31.0, ringImport = 47.5, band = 12.0, discDone = 33.0, arrowImport = 1.7
    static let copyTop = 176.0, copyWidth = 280.0, title = 18.0, titleLine = 24.0, text = 14.0, textLine = 20.0, copyGap = 6.0, hoverDrop = 18.0
    static let titleSpacing = -0.3, textSpacing = -0.1, labelSpacing = -0.2
    static let button = 44.0, buttonInset = 16.0, buttonRadius = 5.0, label = 15.0, labelLine = 20.0
    static let blur = 8.0, buttonBlur = 9.0, buttonSmear = 0.34
    static let sheetWidth = 60.0, sheetHeight = 77.0, sheetFold = 16.0, tag = 24.0, tagGap = 4.0, tagText = 11.0, tagMax = 112.0
    static let fileBox = 168.0, fileTop = 28.0, fileIn = 58.0, fileDown = 0.57, fileOut = 70.0, fileLow = 90.0, fileTilt = 16.0, fileBob = 2.5
    static let shadow = Color.rgba(23, 22, 31, 0.34), shadowY = 44.0, shadowBlur = 90.0
    static let shadowNear = Color.rgba(23, 22, 31, 0.2), shadowNearY = 14.0, shadowNearBlur = 30.0
    static let sheetShadow = Color.rgba(10, 10, 20, 0.28), sheetShadowY = 8.0, sheetShadowBlur = 10.0
    static let sheetLight = Color.white, sheetDark = Color(hex: 0xe9eaee), fold = Color(hex: 0xd3d5dc), mark = Color(hex: 0xcdcfd8)
    static let tagShadow = Color.rgba(10, 10, 20, 0.22), tagShadowY = 3.0, tagShadowBlur = 6.0, tagRadius = 5.0, tagPadding = 16.0
    static let arrowWidth = 4.0, pagesWidth = 3.0, markWidth = 2.2, markLift = 4.0
    static let iconBoundsWidth = 220.0, iconBoundsHalf = 110.0, iconBoundsBottom = 80.0, copyBoundsInset = 16.0, buttonBoundsInset = 28.0
    static let glowAlpha = 0.34, glowCenterAlpha = 0.62, zoneScale = 0.06, glowSwell = 0.1
    static let retentionSide = 20.0, retentionTop = 20.0, retentionBottom = 30.0, minimumPressDuration = 0.13
}

private enum OrbitUploadDropzoneMotion {
    static let loop = OrbitUploadDropzoneLoop, pressAt = 0.8, pressFor = 0.15, doneHold = 1.25, userHold = 3.0, defaultDuration = 3.0, resumePoll = 0.1
    static let hoverAt = 0.1, releaseAt = 1.25, dropFor = 0.3, importAt = 1.5, ringAt = importAt + 0.2
    static let swapOut = 0.28, swapLag = 0.04, swapIn = 0.22, inBlur = 0.5
    static let pullStep = 0.045, pullTravel = 0.28, doneAfter = 0.45, doneFor = 0.4, leave = 0.55, clockTail = 0.2
    static let burst = 5.5, streak = 0.03, streakMax = 9.0, breath = 2.0, swirl = 0.7
    static let buttonIn = 0.1, buttonOut = 0.15, zoneIn = 0.1, zoneOut = 0.2, autoPress = 0.1
    static let states = 5
    static let dots = [
        [226.0, 86, 6, 0.14, 0.0, 3], [304.0, 80, 10, 0.12, 0.05, 6], [273.0, 57, 5, 0.26, 0.02, 1],
        [224.0, 59, 3, 0.24, 0.08, 9], [196.0, 88, 9, 0.11, 0.03, 0], [189.0, 65, 3, 0.2, 0.1, 11],
        [162.0, 68, 6, 0.18, 0.01, 5], [140.0, 61, 3, 0.22, 0.06, 2], [42.0, 66, 3, 0.2, 0.09, 8],
        [19.0, 82, 9, 0.12, 0.04, 4], [351.0, 57, 5, 0.26, 0.0, 10], [9.0, 52, 3, 0.3, 0.07, 7],
        [322.0, 63, 3.5, 0.2, 0.05, 12], [343.0, 80, 2.5, 0.13, 0.11, 13]
    ]
    static let pullFor = Double(dots.count - 1) * pullStep + pullTravel
}

private struct OrbitUploadDropzoneScalar {
    private(set) var value: Double
    private var target: Double, from: Double
    private var start = Date.distantPast, duration = 0.0, active = false
    init(_ value: Double) { self.value = value; target = value; from = value }
    mutating func time(_ target: Double, duration: Double, at date: Date = .now, still: Bool) {
        if still || value == target { value = target; self.target = target; active = false; return }
        if !(active && self.target == target) { start = date; from = value }
        self.target = target; self.duration = duration; active = true
    }
    mutating func advance(_ date: Date) {
        guard active else { return }
        let t = OrbitUploadDropzoneMath.clamp(date.timeIntervalSince(start) / duration)
        let u = t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
        value = from + (target - from) * u; if t == 1 { active = false }
    }
}

private enum OrbitUploadDropzoneControl { case zone, button }
private enum OrbitUploadDropzoneEventKind { case press, pick, back, cycle(Date) }
private struct OrbitUploadDropzoneEvent { let at: Date; let order: Int; let kind: OrbitUploadDropzoneEventKind }

@MainActor
private final class OrbitUploadDropzoneState: ObservableObject {
    @Published var revision = 0
    @Published private(set) var touches = 0
    private(set) var stage = 0
    private(set) var leaving = false
    private(set) var autoPressed = false
    private(set) var elapsed = -1.0
    private(set) var leave = 0.0
    private(set) var buttonDown = OrbitUploadDropzoneScalar(0)
    private(set) var zoneDown = OrbitUploadDropzoneScalar(0)
    private var clockOrigin: Date?
    private var leaveOrigin: Date?
    private var clockEnd = 0.0
    private var silent = false
    private var oldStage = 0
    var fileName = "brand_guide.pdf"
    var duration = OrbitUploadDropzoneMotion.defaultDuration
    var still = false
    var onPick: ((String) -> Void)?
    var onImport: ((String) -> Void)?
    var onComplete: ((String) -> Void)?
    var onCancel: ((String) -> Void)?
    func configure(fileName: String, duration: Double, still: Bool, onPick: ((String) -> Void)?, onImport: ((String) -> Void)?, onComplete: ((String) -> Void)?, onCancel: ((String) -> Void)?) {
        self.fileName = fileName; self.duration = duration; self.still = still
        self.onPick = onPick; self.onImport = onImport; self.onComplete = onComplete; self.onCancel = onCancel
    }
    private func react() {
        let next = OrbitUploadDropzoneMath.stageAt(elapsed, duration)
        guard next != oldStage else { return }
        oldStage = next; stage = next
        if next == 2 {
            if !silent { UIImpactFeedbackGenerator(style: .medium).impactOccurred() }
            onImport?(fileName)
        }
        if next == 3 {
            if !silent { UINotificationFeedbackGenerator().notificationOccurred(.success) }
            onComplete?(fileName)
        }
    }
    func start(auto: Bool, at date: Date = .now) {
        guard stage == 0 && !leaving else { return }
        silent = auto
        if !auto { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        onPick?(fileName)
        // Only the running clock captures its end; geometry keeps reading current duration.
        clockEnd = OrbitUploadDropzoneMath.endAt(duration)
        elapsed = clockEnd.isFinite ? min(0, clockEnd) : .nan
        clockOrigin = clockEnd > 0 && clockEnd.isFinite ? date : nil
        react(); revision &+= 1
    }
    func back(auto: Bool, at date: Date = .now) {
        guard stage != 0 && !leaving else { return }
        leaving = true; clockOrigin = nil
        if stage < 3 { onCancel?(fileName) }
        if !auto { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        leave = 0; leaveOrigin = date; revision &+= 1
    }
    private func rest() {
        elapsed = -1; leave = 0; clockOrigin = nil; leaveOrigin = nil; stage = 0; leaving = false
        react()
    }
    private func autoPress(_ pressed: Bool, at date: Date = .now) {
        guard autoPressed != pressed else { return }
        autoPressed = pressed; buttonDown.time(pressed ? 1 : 0, duration: OrbitUploadDropzoneMotion.autoPress, at: date, still: still)
    }
    func hold(_ control: OrbitUploadDropzoneControl, _ down: Bool) {
        switch control {
        case .zone: zoneDown.time(down ? 1 : 0, duration: down ? OrbitUploadDropzoneMotion.zoneIn : OrbitUploadDropzoneMotion.zoneOut, still: still)
        case .button: buttonDown.time(down ? 1 : 0, duration: down ? OrbitUploadDropzoneMotion.buttonIn : OrbitUploadDropzoneMotion.buttonOut, still: still)
        }
        revision &+= 1
    }
    private func touched() { autoPress(false); touches += 1 }
    func activate(_ control: OrbitUploadDropzoneControl) {
        touched()
        if stage == 0 { start(auto: false) }
        else if control == .button || stage == 3 { back(auto: false) }
    }
    func stop() { clockOrigin = nil; leaveOrigin = nil }
    func advance(_ date: Date) {
        var returned = false
        if let clockOrigin { elapsed = min(clockEnd, max(0, date.timeIntervalSince(clockOrigin))); if elapsed == clockEnd { self.clockOrigin = nil } }
        if let leaveOrigin {
            leave = OrbitUploadDropzoneMath.clamp(date.timeIntervalSince(leaveOrigin) / OrbitUploadDropzoneMotion.leave)
            returned = leave == 1
        }
        buttonDown.advance(date); zoneDown.advance(date); react()
        if returned { rest() }
        revision &+= 1
    }
    func autoplay(enabled: Bool, duration: Double) async {
        guard enabled else { return }
        let period = OrbitUploadDropzoneMotion.loop + duration - OrbitUploadDropzoneMotion.defaultDuration
        // A nonpositive or nonfinite period cannot form a native timer cycle.
        guard period.isFinite && period > 0 else { return }
        do {
            if touches > 0 {
                try await Task.sleep(nanoseconds: UInt64(OrbitUploadDropzoneMotion.userHold * 1e9))
                while stage != 0 || leaving {
                    if stage == 3 { back(auto: true) }
                    try await Task.sleep(nanoseconds: UInt64(OrbitUploadDropzoneMotion.resumePoll * 1e9))
                }
            }
            var queue: [OrbitUploadDropzoneEvent] = [], order = 0
            func cycle(_ origin: Date) {
                let picked = origin.addingTimeInterval(OrbitUploadDropzoneMotion.pressAt + OrbitUploadDropzoneMotion.pressFor)
                let events: [(Date, OrbitUploadDropzoneEventKind)] = [
                    (origin.addingTimeInterval(OrbitUploadDropzoneMotion.pressAt), .press), (picked, .pick),
                    (picked.addingTimeInterval(OrbitUploadDropzoneMath.doneAt(duration) + OrbitUploadDropzoneMotion.doneFor + OrbitUploadDropzoneMotion.doneHold), .back),
                    (origin.addingTimeInterval(period), .cycle(origin.addingTimeInterval(period)))
                ]
                for (at, kind) in events { queue.append(OrbitUploadDropzoneEvent(at: at, order: order, kind: kind)); order += 1 }
                queue.sort { $0.at == $1.at ? $0.order < $1.order : $0.at < $1.at }
            }
            cycle(.now)
            while !queue.isEmpty {
                let event = queue.removeFirst(), wait = event.at.timeIntervalSinceNow
                if wait > 0 { try await Task.sleep(nanoseconds: UInt64(wait * 1e9)) }
                try Task.checkCancellation()
                switch event.kind {
                case .press: autoPress(true)
                case .pick: autoPress(false); start(auto: true)
                case .back: back(auto: true)
                case let .cycle(origin): cycle(origin)
                }
                revision &+= 1
            }
        } catch { return }
    }
}

private struct OrbitUploadDropzoneLoopKey: Equatable {
    let enabled: Bool
    let touches: Int
    let duration: UInt64
    init(enabled: Bool, touches: Int, duration: Double) { self.enabled = enabled; self.touches = touches; self.duration = duration.isNaN ? Double.nan.bitPattern : duration.bitPattern }
}

private enum OrbitUploadDropzoneMath {
    private typealias S = OrbitUploadDropzoneStyle
    private typealias M = OrbitUploadDropzoneMotion
    static func clamp(_ value: Double) -> Double { min(1, max(0, value)) }
    static func smooth(_ time: Double, _ from: Double, _ to: Double) -> Double {
        let value = clamp((time - from) / (to - from)); return value * value * (3 - 2 * value)
    }
    static func spring(_ time: Double, _ damping: Double, _ frequency: Double) -> Double { time <= 0 ? 0 : 1 - exp(-damping * time) * cos(frequency * time) }
    static func doneAt(_ duration: Double) -> Double { M.importAt + duration + M.doneAfter }
    static func endAt(_ duration: Double) -> Double { doneAt(duration) + M.doneFor + M.clockTail }
    static func stageAt(_ elapsed: Double, _ duration: Double) -> Int { elapsed < 0 ? 0 : elapsed < M.importAt ? 1 : elapsed < doneAt(duration) ? 2 : 3 }
    static func levelsAt(elapsed: Double, leave: Double, duration: Double) -> [Double] {
        let done = doneAt(duration), out = smooth(leave, 0, 0.6), back = smooth(leave, 0.3, 1)
        let hoverIn = smooth(elapsed, M.hoverAt + M.swapLag, M.hoverAt + M.swapLag + M.swapIn)
        let importIn = smooth(elapsed, M.importAt + M.swapLag, M.importAt + M.swapLag + M.swapIn)
        let doneIn = smooth(elapsed, done + M.swapLag, done + M.swapLag + M.swapIn)
        let idleOut = smooth(elapsed, M.hoverAt, M.hoverAt + M.swapOut), hoverOut = smooth(elapsed, M.importAt, M.importAt + M.swapOut), importOut = smooth(elapsed, done, done + M.swapOut)
        return [1 - idleOut * (1 - back), hoverIn * (1 - hoverOut) * (1 - out),
            importIn * (1 - importOut) * (1 - out), doneIn * (1 - out), importIn * (1 - out),
            leave > 0 ? M.inBlur * idleOut * (1 - back) : idleOut,
            max(M.inBlur * (1 - hoverIn), hoverOut, out), max(M.inBlur * (1 - importIn), importOut, out),
            max(M.inBlur * (1 - doneIn), out), max(M.inBlur * (1 - importIn), out)]
    }
    static func bandAt(elapsed: Double, duration: Double) -> Double {
        let pull = M.importAt + duration - M.pullFor
        var landed = 0.0
        for dot in M.dots { let lands = pull + dot[5] * M.pullStep + M.pullTravel; landed += smooth(elapsed, lands - 0.08, lands + 0.1) }
        return S.band * landed / Double(M.dots.count)
    }
    static func dotAt(index: Int, elapsed: Double, leave: Double, duration: Double, still: Bool) -> [Double] {
        let dot = M.dots[index], time = elapsed - M.ringAt - dot[4]
        guard time > 0 else { return Array(repeating: 0, count: 6) }
        let thrown = still ? 1 : 1 - exp(-M.burst * time)
        let floating = S.discImport + (dot[1] - S.discImport) * thrown + (still ? 0 : M.breath * sin(time * 1.3 + Double(index)))
        let fraction = clamp((elapsed - (M.importAt + duration - M.pullFor) - dot[5] * M.pullStep) / M.pullTravel)
        let fall = still ? 0 : fraction * fraction * fraction, cancel = still ? 0 : leave * leave
        let radius = (floating + (S.discImport + 2 - floating) * fall) * (1 - cancel) + (S.discImport + 2) * cancel
        let angle = dot[0] * .pi / 180 + (still ? 0 : dot[3] * time + M.swirl * fall)
        let velocity = still ? 0 : (dot[1] - S.discImport) * M.burst * (1 - thrown) + ((floating - S.discImport) * 3 * fraction * fraction * (fraction < 1 ? 1 : 0)) / M.pullTravel
        let long = dot[2] + min(M.streakMax, velocity * M.streak), wide = dot[2] * max(0.7, sqrt(dot[2] / long))
        let alpha = smooth(time, 0, still ? 0.3 : 0.06) * (1 - smooth(fraction, still ? 0 : 0.8, 1)) * (1 - smooth(leave, 0, 0.7))
        return [cos(angle) * radius, sin(angle) * radius, angle, long / 2, wide / 2, alpha]
    }
    static func fileAt(elapsed: Double, leave: Double, still: Bool, screenWidth: Double, left: Double, cardWidth: Double) -> [Double] {
        let fromX = screenWidth + S.fileOut - (left + cardWidth - S.fileIn), toIconX = cardWidth / 2 - (cardWidth - S.fileIn), toIconY = S.iconY - S.cardHeight * S.fileDown
        if still { return [0, 0, 0, 1, smooth(elapsed, 0, 0.25) * (1 - smooth(elapsed, M.releaseAt, M.releaseAt + M.dropFor)) * (1 - leave), 0] }
        let gone = leave * leave, away = 1 - spring(elapsed, 5.5, 6.5) * (1 - gone), held = smooth(elapsed, 0.5, 0.9)
        let fraction = clamp((elapsed - M.releaseAt) / M.dropFor), drop = fraction * fraction * (3 - 2 * fraction)
        return [fromX * away + toIconX * drop,
            S.fileLow * away + S.fileBob * held * sin((elapsed - 0.5) * 3.2) * (1 - drop) + toIconY * drop,
            S.fileTilt * away + 1.2 * held * sin(elapsed * 2.6) * (1 - drop), 1 - 0.8 * drop,
            elapsed < 0 ? 0 : 1 - smooth(fraction, 0.6, 1), S.blur * drop]
    }
}

private enum OrbitUploadDropzoneDrawing {
    private typealias S = OrbitUploadDropzoneStyle
    static let arrow: Path = { var path = Path(); path.move(to: CGPoint(x: 0, y: 8.8)); path.addLine(to: CGPoint(x: 0, y: -7.4)); path.move(to: CGPoint(x: -6.8, y: -1.2)); path.addLine(to: CGPoint(x: 0, y: -8)); path.addLine(to: CGPoint(x: 6.8, y: -1.2)); return path }()
    static let check: Path = { var path = Path(); path.move(to: CGPoint(x: -11.5, y: 1)); path.addLine(to: CGPoint(x: -4, y: 8.5)); path.addLine(to: CGPoint(x: 12, y: -8)); return path }()
    static let page: Path = {
        var path = Path(); path.move(to: CGPoint(x: -3.5, y: -16)); path.addLine(to: CGPoint(x: 8, y: -16)); path.addLine(to: CGPoint(x: 14, y: -10)); path.addLine(to: CGPoint(x: 14, y: 6.5))
        path.addQuadCurve(to: CGPoint(x: 10.5, y: 10), control: CGPoint(x: 14, y: 10)); path.addLine(to: CGPoint(x: -3.5, y: 10)); path.addQuadCurve(to: CGPoint(x: -7, y: 6.5), control: CGPoint(x: -7, y: 10))
        path.addLine(to: CGPoint(x: -7, y: -12.5)); path.addQuadCurve(to: CGPoint(x: -3.5, y: -16), control: CGPoint(x: -7, y: -16)); path.closeSubpath(); return path
    }()
    static let pageBehind: Path = { var path = Path(); path.move(to: CGPoint(x: -12.6, y: -9.5)); path.addLine(to: CGPoint(x: -12.6, y: 12)); path.addQuadCurve(to: CGPoint(x: -8.8, y: 15.8), control: CGPoint(x: -12.6, y: 15.8)); path.addLine(to: CGPoint(x: 7.5, y: 15.8)); return path }()
    static let sheet: Path = {
        let w = S.sheetWidth, h = S.sheetHeight, c = S.sheetFold
        var path = Path(); path.move(to: CGPoint(x: 5, y: 0)); path.addLine(to: CGPoint(x: w - 5, y: 0)); path.addQuadCurve(to: CGPoint(x: w, y: 5), control: CGPoint(x: w, y: 0))
        path.addLine(to: CGPoint(x: w, y: h - c)); path.addLine(to: CGPoint(x: w - c, y: h)); path.addLine(to: CGPoint(x: 5, y: h)); path.addQuadCurve(to: CGPoint(x: 0, y: h - 5), control: CGPoint(x: 0, y: h))
        path.addLine(to: CGPoint(x: 0, y: 5)); path.addQuadCurve(to: CGPoint(x: 5, y: 0), control: .zero); path.closeSubpath(); return path
    }()
    static let fold: Path = { let w = S.sheetWidth, h = S.sheetHeight, c = S.sheetFold; var path = Path(); path.move(to: CGPoint(x: w - c, y: h)); path.addLine(to: CGPoint(x: w - c, y: h - c + 4)); path.addQuadCurve(to: CGPoint(x: w - c + 4, y: h - c), control: CGPoint(x: w - c, y: h - c)); path.addLine(to: CGPoint(x: w, y: h - c)); path.closeSubpath(); return path }()
    static let mark: Path = { var path = Path(); path.move(to: CGPoint(x: -9, y: 5)); path.addLine(to: CGPoint(x: -3.5, y: -3.5)); path.addLine(to: CGPoint(x: 0, y: 1.5)); path.addLine(to: CGPoint(x: 3.5, y: -5)); path.addLine(to: CGPoint(x: 9, y: 5)); return path }()
    static func color(_ css: String) -> Color { OrbitUploadDropzoneCSS.color(css) }
    static func rounded(_ rect: CGRect, _ radius: Double) -> Path { RoundedRectangle(cornerRadius: radius, style: .circular).path(in: rect) }
    static func circle(_ x: Double, _ y: Double, _ radius: Double) -> Path { Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2)) }
    static func stroke(_ path: Path, color: Color, width: Double, context: GraphicsContext) { context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round)) }
}

private struct OrbitUploadDropzoneParagraph: UIViewRepresentable, Equatable {
    let text: String
    let size: Double
    let line: Double
    let weight: Int
    let color: String
    let width: Double
    let spacing: Double
    var oneLine = false
    func makeUIView(context: Context) -> UILabel { let label = UILabel(); label.isUserInteractionEnabled = false; label.isAccessibilityElement = false; return label }
    func updateUIView(_ label: UILabel, context: Context) {
        label.numberOfLines = oneLine ? 1 : 0; label.lineBreakMode = oneLine ? .byTruncatingTail : .byWordWrapping
        let paragraph = NSMutableParagraphStyle(); paragraph.alignment = .center
        paragraph.minimumLineHeight = line; paragraph.maximumLineHeight = line; paragraph.lineBreakMode = label.lineBreakMode
        let attributed = NSAttributedString(string: text, attributes: [.font: UIFont.systemFont(ofSize: size, weight: weight == 500 ? .medium : .regular), .foregroundColor: UIColor(OrbitUploadDropzoneDrawing.color(color)), .paragraphStyle: paragraph, .kern: spacing])
        if label.attributedText != attributed { label.attributedText = attributed }
    }
    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UILabel, context: Context) -> CGSize? {
        let measured = uiView.sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude))
        return CGSize(width: width, height: oneLine ? line : max(line, ceil(measured.height)))
    }
}

private enum OrbitUploadDropzoneCSS {
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


private final class OrbitUploadDropzoneTagCache {
    private var name: String?
    private var measured = 0.0
    func width(_ text: String) -> Double {
        if name != text {
            name = text
            let storage = NSTextStorage(string: text, attributes: [.font: UIFont.systemFont(ofSize: OrbitUploadDropzoneStyle.tagText, weight: .medium)])
            let layout = NSLayoutManager(), container = NSTextContainer(size: CGSize(width: OrbitUploadDropzoneStyle.tagMax, height: .greatestFiniteMagnitude))
            container.lineFragmentPadding = 0; container.maximumNumberOfLines = 1; container.lineBreakMode = .byTruncatingTail
            storage.addLayoutManager(layout); layout.addTextContainer(container); layout.ensureLayout(for: container)
            measured = ceil(layout.usedRect(for: container).width) + OrbitUploadDropzoneStyle.tagPadding
        }
        return measured
    }
}

private struct OrbitUploadDropzoneLayer<OrbitUploadDropzoneContent: View>: View {
    let levels: [Double]
    let index: Int
    let bounds: CGRect
    let width: Double
    @ViewBuilder let content: () -> OrbitUploadDropzoneContent
    var body: some View {
        content().frame(width: width, height: OrbitUploadDropzoneStyle.cardHeight)
            .offset(x: -bounds.minX, y: -bounds.minY)
            .frame(width: bounds.width, height: bounds.height, alignment: .topLeading)
            .clipped().blur(radius: OrbitUploadDropzoneStyle.blur * levels[index + OrbitUploadDropzoneMotion.states])
            .opacity(levels[index]).clipped().offset(x: bounds.minX, y: bounds.minY)
            .allowsHitTesting(false).accessibilityHidden(true)
    }
}

private struct OrbitUploadDropzoneCardShadows: View {
    private typealias S = OrbitUploadDropzoneStyle
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: S.cardRadius, style: .continuous).fill(S.shadow).blur(radius: S.shadowBlur / 2).offset(y: S.shadowY)
            RoundedRectangle(cornerRadius: S.cardRadius, style: .continuous).fill(S.shadowNear).blur(radius: S.shadowNearBlur / 2).offset(y: S.shadowNearY)
        }.allowsHitTesting(false).accessibilityHidden(true)
    }
}

private struct OrbitUploadDropzoneHit: View {
    let width: Double
    let height: Double
    let label: String
    let hold: (Bool) -> Void
    let activate: () -> Void
    @State private var began = false
    @State private var inside = false
    @State private var activatedAt: Date?
    @State private var pendingOut: Task<Void, Never>?
    @GestureState private var active = false
    private func contains(_ point: CGPoint) -> Bool {
        point.x > -OrbitUploadDropzoneStyle.retentionSide && point.x < width + OrbitUploadDropzoneStyle.retentionSide && point.y > -OrbitUploadDropzoneStyle.retentionTop && point.y < height + OrbitUploadDropzoneStyle.retentionBottom
    }
    private func pressIn() { inside = true; activatedAt = .now; hold(true) }
    private func pressOut() {
        inside = false
        let delay = max(0, OrbitUploadDropzoneStyle.minimumPressDuration - Date.now.timeIntervalSince(activatedAt ?? .distantPast)); activatedAt = nil
        if delay == 0 { hold(false); return }
        pendingOut = Task { @MainActor in
            do { try await Task.sleep(nanoseconds: UInt64(delay * 1e9)); try Task.checkCancellation(); hold(false) }
            catch { return }
        }
    }
    var body: some View {
        Color.clear.frame(width: width, height: height).contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0)
                .updating($active) { _, active, _ in active = true }
                .onChanged { event in
                    if !began { pendingOut?.cancel(); began = true; pressIn() }
                    let next = contains(event.location)
                    if next != inside { if next { pressIn() } else { pressOut() } }
                }
                .onEnded { _ in
                    guard began else { return }; began = false
                    let perform = inside; if inside { pressOut() }; if perform { activate() }
                })
            .onChange(of: active) { _, next in if !next { DispatchQueue.main.async { if !active && began { began = false; if inside { pressOut() } } } } }
            .onDisappear { pendingOut?.cancel(); began = false; inside = false; activatedAt = nil }
            .accessibilityElement(children: .ignore).accessibilityAddTraits(.isButton).accessibilityLabel(label)
            .accessibilityAction { activate() }
    }
}

/// A single elapsed clock carries the file, blurred state layers and fixed particle orbits.
struct OrbitUploadDropzone: View {
    var fileName = "brand_guide.pdf"
    var duration = OrbitUploadDropzoneMotion.defaultDuration
    var autoPlay = false
    var idle = OrbitUploadDropzoneCopy.idle
    var hover = OrbitUploadDropzoneCopy.hover
    var importing = OrbitUploadDropzoneCopy.importing
    var done = OrbitUploadDropzoneCopy.done
    var chooseLabel = "Choose file"
    var cancelLabel = "Cancel"
    var backdrop = OrbitUploadDropzoneStyle.backdrop
    var cardColor = OrbitUploadDropzoneStyle.card
    var ringColor = OrbitUploadDropzoneStyle.ring
    var ink = OrbitUploadDropzoneStyle.ink
    var mute = OrbitUploadDropzoneStyle.mute
    var onPick: ((String) -> Void)? = nil
    var onImport: ((String) -> Void)? = nil
    var onComplete: ((String) -> Void)? = nil
    var onCancel: ((String) -> Void)? = nil
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @StateObject private var state = OrbitUploadDropzoneState()
    @State private var tagCache = OrbitUploadDropzoneTagCache()
    private typealias S = OrbitUploadDropzoneStyle
    private typealias M = OrbitUploadDropzoneMotion
    private typealias F = OrbitUploadDropzoneMath
    private typealias D = OrbitUploadDropzoneDrawing

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let screenWidth = proxy.size.width + insets.leading + insets.trailing
            let height = proxy.size.height + insets.top + insets.bottom
            let width = min(S.cardMax, screenWidth - S.margin * 2), left = (screenWidth - width) / 2
            let top = max(insets.top + S.margin, (height - S.cardHeight) / 2 - S.cardLift)
            TimelineView(.animation) { timeline in
                let _ = state.configure(fileName: fileName, duration: duration, still: reduceMotion, onPick: onPick, onImport: onImport, onComplete: onComplete, onCancel: onCancel)
                ZStack(alignment: .topLeading) {
                    card(width: width).offset(x: left, y: top)
                    file(screenWidth: screenWidth, left: left, width: width)
                        .offset(x: left + width - S.fileIn - S.fileBox / 2, y: top + S.cardHeight * S.fileDown - S.fileTop - S.sheetHeight / 2)
                }
                .frame(width: screenWidth, height: height, alignment: .topLeading).offset(x: -insets.leading, y: -insets.top)
                .onChange(of: timeline.date) { _, date in state.advance(date) }
            }
        }
        .background(D.color(backdrop).ignoresSafeArea()).preferredColorScheme(.light)
        .task(id: OrbitUploadDropzoneLoopKey(enabled: autoPlay, touches: state.touches, duration: duration)) { await state.autoplay(enabled: autoPlay, duration: duration) }
        .onDisappear { state.stop() }
    }
    private func card(width: Double) -> some View {
        let levels = F.levelsAt(elapsed: state.elapsed, leave: state.leave, duration: duration)
        let buttonTop = S.cardHeight - S.buttonInset - S.button
        let iconBounds = CGRect(x: width / 2 - S.iconBoundsHalf, y: 0, width: S.iconBoundsWidth, height: S.iconY + S.iconBoundsBottom)
        let copyBounds = CGRect(x: 0, y: S.copyTop - S.copyBoundsInset, width: width, height: buttonTop - S.copyTop + S.copyBoundsInset)
        let copies = [idle, hover, importing, done], shown = copies[state.stage]
        return ZStack(alignment: .topLeading) {
            ZStack(alignment: .topLeading) {
                OrbitUploadDropzoneLayer(levels: levels, index: 0, bounds: iconBounds, width: width) { idleIcon(width: width) }
                OrbitUploadDropzoneLayer(levels: levels, index: 1, bounds: iconBounds, width: width) { hoverIcon(width: width) }
                OrbitUploadDropzoneLayer(levels: levels, index: 4, bounds: iconBounds, width: width) { importingIcon(width: width) }
                particles(width: width)
                ForEach(copies.indices, id: \.self) { index in
                    OrbitUploadDropzoneLayer(levels: levels, index: index, bounds: copyBounds, width: width) {
                        ZStack(alignment: .topLeading) {
                            paragraph(copies[index].title, size: S.title, line: S.titleLine, weight: 500, color: ink, width: S.copyWidth, spacing: S.titleSpacing)
                                .offset(x: (width - S.copyWidth) / 2, y: S.copyTop + (index == 1 ? S.hoverDrop : 0))
                            paragraph(copies[index].text, size: S.text, line: S.textLine, weight: 400, color: mute, width: S.copyWidth, spacing: S.textSpacing)
                                .offset(x: (width - S.copyWidth) / 2, y: S.copyTop + (index == 1 ? S.hoverDrop : 0) + S.titleLine + S.copyGap)
                        }.frame(width: width, height: S.cardHeight, alignment: .topLeading)
                    }
                }
                button(width: width)
            }.frame(width: width, height: S.cardHeight).clipShape(RoundedRectangle(cornerRadius: S.cardRadius, style: .continuous))
            OrbitUploadDropzoneHit(width: width, height: S.cardHeight, label: "\(shown.title). \(shown.text)", hold: { state.hold(.zone, $0) }, activate: { state.activate(.zone) })
            if state.stage < 3 {
                OrbitUploadDropzoneHit(width: width - S.buttonInset * 2, height: S.button, label: state.stage == 0 ? chooseLabel : cancelLabel, hold: { state.hold(.button, $0) }, activate: { state.activate(.button) })
                    .offset(x: S.buttonInset, y: buttonTop)
            }
        }
        .frame(width: width, height: S.cardHeight)
        .background {
            ZStack {
                OrbitUploadDropzoneCardShadows()
                RoundedRectangle(cornerRadius: S.cardRadius, style: .continuous).fill(D.color(cardColor))
            }
        }
    }
    private func paragraph(_ text: String, size: Double, line: Double, weight: Int, color: String, width: Double, spacing: Double, oneLine: Bool = false) -> some View {
        OrbitUploadDropzoneParagraph(text: text, size: size, line: line, weight: weight, color: color, width: width, spacing: spacing, oneLine: oneLine).equatable().frame(width: width).allowsHitTesting(false).accessibilityHidden(true)
    }
    private func idleIcon(width: Double) -> some View {
        Canvas { context, _ in
            let center = CGPoint(x: width / 2, y: S.iconY)
            var icon = context.scaled(1 - S.zoneScale * state.zoneDown.value, about: center)
            icon.fill(D.circle(center.x, center.y, S.ringIdle), with: .color(D.color(ringColor)))
            icon.fill(D.circle(center.x, center.y, S.discIdle), with: .color(D.color(ink)))
            icon.translateBy(x: center.x, y: center.y); D.stroke(D.arrow, color: D.color(cardColor), width: S.arrowWidth, context: icon)
        }
    }
    private func hoverIcon(width: Double) -> some View {
        Canvas { context, _ in
            let landing = M.releaseAt + M.dropFor * 0.7, center = CGPoint(x: width / 2, y: S.iconY)
            let scale = reduceMotion ? 1 : 1 + S.glowSwell * F.smooth(state.elapsed, landing - 0.12, landing) * (1 - F.smooth(state.elapsed, landing, landing + 0.2))
            var icon = context.scaled(scale, about: center)
            var glow = icon; glow.opacity = S.glowAlpha; glow.addFilter(.blur(radius: S.glowBlur))
            glow.fill(D.circle(center.x, center.y, S.glow), with: .color(D.color(ink)))
            icon.fill(D.circle(center.x, center.y, S.glow), with: .radialGradient(Gradient(colors: [Color.white.opacity(S.glowCenterAlpha), Color.white.opacity(S.glowAlpha)]), center: center, startRadius: 0, endRadius: S.glow))
            icon.translateBy(x: center.x, y: center.y); D.stroke(D.pageBehind, color: D.color(ink), width: S.pagesWidth, context: icon); icon.fill(D.page, with: .color(D.color(ink)))
        }
    }
    private func importingIcon(width: Double) -> some View {
        Canvas { context, _ in
            let e = state.elapsed, band = F.bandAt(elapsed: e, duration: duration) * (1 - state.leave), turned = F.smooth(e, F.doneAt(duration), F.doneAt(duration) + M.doneFor)
            let time = e - M.ringAt, ring = S.discImport + (S.ringImport - S.discImport) * (reduceMotion ? F.smooth(time, 0, 0.2) : F.spring(time, 9, 11))
            let whiteTime = e - F.doneAt(duration) - M.doneFor * 0.25
            let settle = reduceMotion ? F.smooth(whiteTime, 0, M.doneFor * 0.75) : F.spring(whiteTime, 8, 9)
            let whiteRadius = S.discImport + band - (S.discImport + S.band - S.discDone) * settle
            var ringContext = context; ringContext.opacity = 1 - F.smooth(band, S.band * 0.7, S.band)
            ringContext.fill(D.circle(width / 2, S.iconY, ring), with: .color(D.color(ringColor)))
            ringContext = context; ringContext.opacity = 0.1 * F.smooth(e, M.ringAt, M.ringAt + 0.08) * (1 - F.smooth(e, M.ringAt + 0.1, M.ringAt + 0.6))
            ringContext.fill(D.circle(width / 2, S.iconY, ring), with: .color(D.color(ink)))
            ringContext = context; ringContext.opacity = F.smooth(band, 0, 0.6)
            ringContext.fill(D.circle(width / 2, S.iconY, whiteRadius), with: .color(D.color(ink)))
            let gray = floor(255 * F.smooth(turned, 0, 0.75) + 0.5)
            context.fill(D.circle(width / 2, S.iconY, S.discImport), with: .color(.rgba(gray, gray, gray, 1)))
            var center = context; center.translateBy(x: width / 2, y: S.iconY)
            var arrow = center; arrow.scaleBy(x: S.arrowImport, y: S.arrowImport); arrow.opacity = 1 - F.smooth(turned, 0.15, 0.6)
            D.stroke(D.arrow, color: D.color(ink), width: S.arrowWidth, context: arrow)
            D.stroke(D.check.trimmedPath(from: 0, to: F.smooth(turned, 0.45, 1)), color: S.discBlack, width: S.arrowWidth, context: center)
        }
    }
    private func particles(width: Double) -> some View {
        Canvas { context, _ in
            for index in M.dots.indices {
                let dot = F.dotAt(index: index, elapsed: state.elapsed, leave: state.leave, duration: duration, still: reduceMotion)
                var particle = context; particle.translateBy(x: width / 2 + dot[0], y: S.iconY + dot[1]); particle.rotate(by: .radians(dot[2]))
                particle.scaleBy(x: dot[3], y: dot[4]); particle.opacity = dot[5]; particle.fill(unitCircle, with: .color(D.color(ink)))
            }
        }.allowsHitTesting(false).accessibilityHidden(true)
    }
    private func button(width: Double) -> some View {
        let e = state.elapsed, leave = state.leave, top = S.cardHeight - S.buttonInset - S.button
        let smear = F.smooth(e, M.hoverAt, M.hoverAt + M.swapOut) * (1 - F.smooth(e, M.importAt + M.swapLag, M.importAt + M.swapLag + M.swapIn)) * (1 - F.smooth(leave, 0, 0.6))
        let gone = F.smooth(e, F.doneAt(duration), F.doneAt(duration) + M.swapOut) * (1 - F.smooth(leave, 0.3, 1))
        let weight = (1 - (1 - S.buttonSmear) * smear) * (1 - gone), gray = floor(255 * (1 - S.buttonDim * state.buttonDown.value) + 0.5)
        let chosen = 1 - F.smooth(e, M.hoverAt, M.hoverAt + 0.15)
        let chooseWeight = chosen + (1 - chosen) * F.smooth(leave, 0.4, 1), cancelWeight = F.smooth(e, M.hoverAt + 0.1, M.hoverAt + 0.3) * (1 - F.smooth(leave, 0, 0.5))
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: S.buttonRadius, style: .circular).fill(Color.rgba(gray, gray, gray, 1))
                .frame(width: width - S.buttonInset * 2 * (1 - smear), height: S.button).offset(x: S.buttonInset * (1 - smear), y: S.buttonBoundsInset)
            paragraph(chooseLabel, size: S.label, line: S.labelLine, weight: 500, color: cardColor, width: width - S.buttonInset * 2, spacing: S.labelSpacing, oneLine: true)
                .opacity(chooseWeight).offset(x: S.buttonInset, y: S.buttonBoundsInset + (S.button - S.labelLine) / 2)
            paragraph(cancelLabel, size: S.label, line: S.labelLine, weight: 500, color: cardColor, width: width - S.buttonInset * 2, spacing: S.labelSpacing, oneLine: true)
                .opacity(cancelWeight).offset(x: S.buttonInset, y: S.buttonBoundsInset + (S.button - S.labelLine) / 2)
        }
        .frame(width: width, height: S.button + S.buttonInset + S.buttonBoundsInset, alignment: .topLeading)
        .clipped().blur(radius: S.buttonBlur * smear).opacity(weight).clipped().offset(y: top - S.buttonBoundsInset)
        .allowsHitTesting(false).accessibilityHidden(true)
    }
    private func file(screenWidth: Double, left: Double, width: Double) -> some View {
        let file = F.fileAt(elapsed: state.elapsed, leave: state.leave, still: reduceMotion, screenWidth: screenWidth, left: left, cardWidth: width)
        let tagWidth = tagCache.width(fileName), tagY = S.fileTop + S.sheetHeight + S.tagGap
        return ZStack(alignment: .topLeading) {
            Canvas { context, _ in
                var sheet = context; sheet.translateBy(x: (S.fileBox - S.sheetWidth) / 2, y: S.fileTop)
                var shadow = sheet; shadow.translateBy(x: 0, y: S.sheetShadowY); shadow.addFilter(.blur(radius: S.sheetShadowBlur)); shadow.fill(D.sheet, with: .color(S.sheetShadow))
                sheet.fill(D.sheet, with: .linearGradient(Gradient(colors: [S.sheetLight, S.sheetDark]), startPoint: .zero, endPoint: CGPoint(x: S.sheetWidth, y: S.sheetHeight)))
                sheet.fill(D.fold, with: .color(S.fold)); sheet.translateBy(x: S.sheetWidth / 2, y: S.sheetHeight / 2 - S.markLift); D.stroke(D.mark, color: S.mark, width: S.markWidth, context: sheet)
                let tag = CGRect(x: (S.fileBox - tagWidth) / 2, y: tagY, width: tagWidth, height: S.tag)
                shadow = context; shadow.addFilter(.blur(radius: S.tagShadowBlur)); shadow.translateBy(x: 0, y: S.tagShadowY); shadow.fill(D.rounded(tag, S.tagRadius), with: .color(S.tagShadow))
                context.fill(D.rounded(tag, S.tagRadius), with: .color(.white))
            }
            paragraph(fileName, size: S.tagText, line: S.tag, weight: 500, color: S.card, width: S.tagMax, spacing: 0, oneLine: true)
                .offset(x: (S.fileBox - S.tagMax) / 2, y: tagY)
        }
        .frame(width: S.fileBox, height: S.fileBox).blur(radius: file[5]).clipped()
        .scaleEffect(file[3]).rotationEffect(.degrees(file[2])).offset(x: file[0], y: file[1]).opacity(file[4])
        .allowsHitTesting(false).accessibilityHidden(true)
    }
}
