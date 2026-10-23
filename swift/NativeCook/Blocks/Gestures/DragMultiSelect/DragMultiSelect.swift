import CoreText
import SwiftUI
import UIKit

/// Offline for the flight: hold an episode and sweep a range onto the flight timeline, then
/// download the plan while the plane flies from SFO to JFK.
struct DragMultiSelect: View {
    var autoPlay: Bool

    /// React Native's LOOP_MS (10_000) expressed in seconds.
    static let loop: Double = 10

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @StateObject private var engine = DragMultiSelectEngine()

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        DragMultiSelectFonts.register()
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(
                width: proxy.size.width + insets.leading + insets.trailing,
                height: proxy.size.height + insets.top + insets.bottom
            )
            let layout = DragMultiSelectLayout(
                width: size.width,
                height: size.height,
                safeTop: insets.top,
                safeBottom: insets.bottom
            )

            TimelineView(.animation(minimumInterval: 1 / 60, paused: reduceMotion || !engine.needsFrames(autoPlay: autoPlay))) { timeline in
                let frame = engine.frame(at: timeline.date, autoPlay: autoPlay, reduceMotion: reduceMotion)
                DragMultiSelectScreen(
                    layout: layout,
                    frame: frame,
                    engine: engine,
                    autoPlay: autoPlay,
                    reduceMotion: reduceMotion
                )
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .offset(x: -insets.leading, y: -insets.top)
        }
        .background(DragMultiSelectPalette.page.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear { engine.configure(autoPlay: autoPlay, reduceMotion: reduceMotion) }
        .onChange(of: reduceMotion) { _, value in engine.configure(autoPlay: autoPlay, reduceMotion: value) }
        .onDisappear { engine.stop() }
    }
}

// MARK: - Reference tokens

enum DragMultiSelectPalette {
    static let page = color(0x0B0B0C)
    static let card = color(0x161618)
    static let ink = color(0xF4F2EE)
    static let mute = color(0x8F8C87)
    static let dim = color(0x5E5C58)
    static let orange = color(0xFF8A1F)
    static let violet = color(0xA57BFF)
    static let violetInk = color(0x170B30)
    static let violetTrack = color(0x2D2340)
    static let green = color(0x3DDC84)
    static let buttonOff = color(0x1E1E21)
    static let pass = color(0x050506)
    static let line = Color.white.opacity(0.08)
    static let dot = Color.white.opacity(0.20)
    static let glyph = Color.white.opacity(0.36)
    static let ring = violet.opacity(0.25)
    static let greenSoft = green.opacity(0.14)
    static let passEdge = Color.white.opacity(0.07)
    static let passMute = Color.white.opacity(0.56)
    static let passText = Color.white.opacity(0.78)

    private static func color(_ rgb: UInt32) -> Color {
        Color(
            .sRGB,
            red: Double((rgb >> 16) & 255) / 255,
            green: Double((rgb >> 8) & 255) / 255,
            blue: Double(rgb & 255) / 255
        )
    }
}

enum DragMultiSelectMetrics {
    static let edge: CGFloat = 16
    static let nav: CGFloat = 44
    static let cardGap: CGFloat = 4
    static let cardHeight: CGFloat = 148
    static let cardRadius: CGFloat = 24
    static let cardPad: CGFloat = 20
    static let sectionGap: CGFloat = 20
    static let sectionHeight: CGFloat = 16
    static let sectionAfter: CGFloat = 8
    static let row: CGFloat = 64
    static let numberX: CGFloat = 16
    static let numberWidth: CGFloat = 52
    static let railX: CGFloat = 76
    static let railWidth: CGFloat = 4
    static let textX: CGFloat = 96
    static let trailRight: CGFloat = 20
    static let glyph: CGFloat = 22
    static let cells: CGFloat = 72
    static let cellsGap: CGFloat = 12
    static let button: CGFloat = 52
    static let buttonRadius: CGFloat = 16
    static let panelHeight: CGFloat = 136
    static let panelSide: CGFloat = 16
    static let finger: CGFloat = 44
}

struct DragMultiSelectLayout {
    let width: CGFloat
    let height: CGFloat
    let safeTop: CGFloat
    let safeBottom: CGFloat
    let cardTop: CGFloat
    let sectionTop: CGFloat
    let listTop: CGFloat
    let panelTop: CGFloat
    let panelBottom: CGFloat
    let panelWidth: CGFloat
    let cardWidth: CGFloat

    init(width: CGFloat, height: CGFloat, safeTop: CGFloat, safeBottom: CGFloat) {
        self.width = width
        self.height = height
        self.safeTop = safeTop
        self.safeBottom = safeBottom
        cardTop = safeTop + DragMultiSelectMetrics.nav + DragMultiSelectMetrics.cardGap
        sectionTop = cardTop + DragMultiSelectMetrics.cardHeight + DragMultiSelectMetrics.sectionGap
        listTop = sectionTop + DragMultiSelectMetrics.sectionHeight + DragMultiSelectMetrics.sectionAfter
        panelBottom = max(safeBottom, DragMultiSelectMetrics.edge)
        panelTop = height - panelBottom - DragMultiSelectMetrics.panelHeight
        panelWidth = width - DragMultiSelectMetrics.panelSide * 2
        cardWidth = width - DragMultiSelectMetrics.edge * 2
    }
}

enum DragMultiSelectFont {
    static func light(_ size: CGFloat) -> Font { .custom("Inter24pt-Regular", fixedSize: size) }
    static func regular(_ size: CGFloat) -> Font { .custom("Inter24pt-Regular", fixedSize: size) }
    static func medium(_ size: CGFloat) -> Font { .custom("Inter-SemiBold", fixedSize: size).weight(.medium) }
    static func semibold(_ size: CGFloat) -> Font { .custom("Inter-SemiBold", fixedSize: size) }
    static func dots(_ size: CGFloat) -> Font { .custom("Doto-Black", fixedSize: size) }
}

enum DragMultiSelectFonts {
    private static var didRegister = false

    static func register() {
        guard !didRegister else { return }
        didRegister = true
        let files = [
            "DragMultiSelect-Inter-Light",
            "DragMultiSelect-Inter-Regular",
            "DragMultiSelect-Inter-Medium",
            "DragMultiSelect-Inter-SemiBold",
            "DragMultiSelect-Inter-Bold",
            "DragMultiSelect-Doto-Black",
        ]
        for name in files {
            guard let url = Bundle.main.url(forResource: name, withExtension: "ttf") else { continue }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }
}

struct DragMultiSelectEpisode: Identifiable {
    let id: Int
    let number: Int
    let title: String
    let date: String
    let minutes: Int
    let guest: String

    static let all: [DragMultiSelectEpisode] = [
        .init(id: 48, number: 48, title: "The chapter that wouldn’t end", date: "Oct 17", minutes: 64, guest: "Mara Okafor"),
        .init(id: 47, number: 47, title: "Writing in a second language", date: "Oct 10", minutes: 52, guest: "Tomasz Wilk"),
        .init(id: 46, number: 46, title: "Notebooks, not outlines", date: "Oct 3", minutes: 71, guest: "Inés Calderón"),
        .init(id: 45, number: 45, title: "The red pen, live in Brooklyn", date: "Sep 26", minutes: 38, guest: "Live"),
        .init(id: 44, number: 44, title: "Starting over at sixty", date: "Sep 19", minutes: 58, guest: "Ruth Abernathy"),
        .init(id: 43, number: 43, title: "How to translate a joke", date: "Sep 12", minutes: 47, guest: "Kenji Sato"),
        .init(id: 42, number: 42, title: "Q&A: when to quit a draft", date: "Sep 5", minutes: 41, guest: "Listener questions"),
        .init(id: 41, number: 41, title: "Down the research hole", date: "Aug 29", minutes: 66, guest: "Priya Natarajan"),
        .init(id: 40, number: 40, title: "Short stories as sketches", date: "Aug 22", minutes: 44, guest: "Leo Brandt"),
        .init(id: 39, number: 39, title: "Writing the city you left", date: "Aug 15", minutes: 55, guest: "Amara Diallo"),
        .init(id: 38, number: 38, title: "Endings", date: "Aug 8", minutes: 49, guest: "Sofia Marchetti"),
        .init(id: 37, number: 37, title: "What the first reader said", date: "Aug 1", minutes: 53, guest: "Jonah Reyes"),
    ]
}

enum DragMultiSelectPhase: Equatable {
    case idle, selecting, downloading, ready
}

struct DragMultiSelectFrame {
    var phase: DragMultiSelectPhase = .idle
    var selected: Set<Int> = []
    var downloaded: Set<Int> = []
    var share = Array(repeating: 0.0, count: DragMultiSelectEpisode.all.count)
    var progress = Array(repeating: 0.0, count: DragMultiSelectEpisode.all.count)
    var mark = Array(repeating: 0.0, count: DragMultiSelectEpisode.all.count)
    var plane = 0.0
    var ghost = CGPoint.zero
    var ghostOpacity = 0.0
    var ghostPressed = 0.0
    var passProgress = 0.0
    var passOpacity = 0.0

    var timelineRows: Set<Int> { selected.union(downloaded) }
    var selecting: Bool { phase == .selecting || phase == .downloading }
}

// MARK: - One clock and one interaction model

@MainActor
final class DragMultiSelectEngine: ObservableObject {
    @Published private(set) var revision = 0

    private var scriptStart = Date()
    // Start the animation schedule eagerly for autoplay demos. `onAppear` immediately resets
    // the epoch (or pauses it for Reduce Motion); beginning paused can leave TimelineView's
    // animation schedule dormant even after the observable engine is configured.
    private var scriptRunning = true
    private var autoPlayAllowed = false
    private var resumeTask: Task<Void, Never>?
    private var finishTask: Task<Void, Never>?
    private var touchDepth = 0

    private(set) var phase: DragMultiSelectPhase = .idle
    private(set) var selected: Set<Int> = []
    private(set) var downloaded: Set<Int> = []
    private var downloadStart: Date?
    private var dragAnchor: Int?
    private var dragBase: Set<Int> = []
    private var dragTarget = true
    var passDismissed = false

    func configure(autoPlay: Bool, reduceMotion: Bool) {
        autoPlayAllowed = autoPlay && !reduceMotion
        resumeTask?.cancel()
        if autoPlayAllowed {
            resetManual()
            scriptStart = Date()
            scriptRunning = true
        } else {
            scriptRunning = false
        }
        revision &+= 1
    }

    func stop() {
        resumeTask?.cancel()
        finishTask?.cancel()
        scriptRunning = false
    }

    func needsFrames(autoPlay: Bool) -> Bool {
        (autoPlay && scriptRunning) || phase == .downloading
    }

    func frame(at date: Date, autoPlay: Bool, reduceMotion: Bool) -> DragMultiSelectFrame {
        if autoPlay && scriptRunning && !reduceMotion {
            let elapsed = max(0, date.timeIntervalSince(scriptStart))
            return scriptedFrame(t: elapsed.truncatingRemainder(dividingBy: DragMultiSelect.loop))
        }
        return manualFrame(at: date, reduceMotion: reduceMotion)
    }

    func userTouchBegan() {
        touchDepth += 1
        resumeTask?.cancel()
        if scriptRunning {
            scriptRunning = false
            resetManual()
            revision &+= 1
        }
    }

    func userTouchEnded() {
        touchDepth = max(0, touchDepth - 1)
        guard touchDepth == 0, autoPlayAllowed else { return }
        resumeTask?.cancel()
        resumeTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled, let self, self.phase != .downloading else { return }
            self.resetManual()
            self.passDismissed = false
            self.scriptStart = Date()
            self.scriptRunning = true
            self.revision &+= 1
        }
    }

    func selectButton() {
        pauseForAction()
        if phase == .selecting {
            phase = .idle
            selected.removeAll()
        } else if phase == .idle {
            phase = .selecting
        }
        revision &+= 1
    }

    func activate(row: Int) {
        pauseForAction()
        guard phase != .downloading, !downloaded.contains(row) else { return }
        if phase == .idle {
            phase = .selecting
            selected = [row]
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        } else {
            toggle(row: row)
        }
        revision &+= 1
    }

    func beginRange(row: Int) {
        pauseForAction()
        guard phase != .downloading, !downloaded.contains(row) else { return }
        if phase == .idle {
            phase = .selecting
            selected = [row]
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            dragTarget = true
        } else {
            dragTarget = !selected.contains(row)
            set(row: row, selected: dragTarget)
        }
        dragAnchor = row
        dragBase = selected
        revision &+= 1
    }

    func updateRange(row: Int) {
        guard phase == .selecting, let anchor = dragAnchor else { return }
        var next = dragBase
        for index in min(anchor, row)...max(anchor, row) where !downloaded.contains(index) {
            if dragTarget { next.insert(index) } else { next.remove(index) }
        }
        publish(next)
    }

    func endRange() {
        dragAnchor = nil
        revision &+= 1
    }

    func tap(row: Int) {
        guard phase == .selecting else { return }
        pauseForAction()
        toggle(row: row)
        revision &+= 1
    }

    func startDownload() {
        pauseForAction()
        guard phase == .selecting, !selected.isEmpty else { return }
        phase = .downloading
        downloadStart = Date()
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        revision &+= 1

        let duration = 0.9 + 0.11 * Double(max(0, selected.count - 1)) + 0.4
        finishTask?.cancel()
        finishTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(duration))
            guard !Task.isCancelled, let self else { return }
            self.downloaded.formUnion(self.selected)
            self.selected.removeAll()
            self.phase = .ready
            self.downloadStart = nil
            self.passDismissed = false
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            self.revision &+= 1
        }
    }

    func dismissPass() {
        passDismissed = true
        revision &+= 1
    }

    private func pauseForAction() {
        resumeTask?.cancel()
        if scriptRunning {
            scriptRunning = false
            resetManual()
        }
    }

    private func toggle(row: Int) {
        guard !downloaded.contains(row) else { return }
        let before = totalMinutes(selected)
        var next = selected
        if next.contains(row) { next.remove(row) } else { next.insert(row) }
        let after = totalMinutes(next)
        selected = next
        if before < 325, after >= 325 {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        } else {
            UISelectionFeedbackGenerator().selectionChanged()
        }
    }

    private func set(row: Int, selected value: Bool) {
        var next = selected
        if value { next.insert(row) } else { next.remove(row) }
        publish(next)
    }

    private func publish(_ next: Set<Int>) {
        guard next != selected else { return }
        let before = totalMinutes(selected)
        let after = totalMinutes(next)
        selected = next
        if before < 325, after >= 325 {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        } else {
            UISelectionFeedbackGenerator().selectionChanged()
        }
        revision &+= 1
    }

    private func resetManual() {
        finishTask?.cancel()
        phase = .idle
        selected.removeAll()
        downloaded.removeAll()
        downloadStart = nil
        dragAnchor = nil
        passDismissed = false
    }

    private func manualFrame(at date: Date, reduceMotion: Bool) -> DragMultiSelectFrame {
        var frame = DragMultiSelectFrame()
        frame.phase = phase
        frame.selected = selected
        frame.downloaded = downloaded
        for index in frame.timelineRows {
            frame.share[index] = 1
            frame.mark[index] = downloaded.contains(index) ? 1 : 0
        }
        if phase == .downloading, let downloadStart {
            let elapsed = date.timeIntervalSince(downloadStart)
            let rows = selected.sorted()
            var weighted = 0.0
            let total = max(1, totalMinutes(selected))
            for (order, row) in rows.enumerated() {
                let raw = (elapsed - Double(order) * 0.11) / 0.9
                let progress = DragMultiSelectMath.easeInOut(raw)
                frame.progress[row] = progress
                frame.mark[row] = DragMultiSelectMath.easeOut((progress - 0.94) / 0.06)
                weighted += Double(DragMultiSelectEpisode.all[row].minutes) * progress
            }
            frame.plane = weighted / Double(total)
        } else if phase == .ready {
            frame.plane = 1
            if !passDismissed {
                frame.passProgress = 1
                frame.passOpacity = 1
            }
        }
        return frame
    }

    private func scriptedFrame(t: Double) -> DragMultiSelectFrame {
        var frame = DragMultiSelectFrame()
        let enterAt = 0.9
        let downloadAt = 4.8
        let downloadEnd = 6.25
        let readyAt = 6.65
        let resetAt = 9.1

        if t >= enterAt, t < readyAt {
            frame.phase = t < downloadAt ? .selecting : .downloading
            let count: Int
            if t < 1.05 { count = 1 }
            else if t < 2.3 {
                let travel = DragMultiSelectMath.easeInOut((t - 1.05) / 1.25)
                count = min(5, max(1, Int(floor(travel * 4.0 + 1.0))))
            } else if t < 3.6 { count = 5 }
            else { count = 6 }
            frame.selected = Set(0..<count)
        }

        if t >= downloadAt, t < resetAt {
            frame.phase = t < readyAt ? .downloading : .ready
            let chosen = Set(0..<6)
            if t < readyAt { frame.selected = chosen } else { frame.downloaded = chosen }
            var weighted = 0.0
            for row in 0..<6 {
                let p = DragMultiSelectMath.easeInOut((t - downloadAt - Double(row) * 0.11) / 0.9)
                frame.progress[row] = p
                frame.mark[row] = DragMultiSelectMath.easeOut((p - 0.94) / 0.06)
                if p >= 0.97 { frame.downloaded.insert(row) }
                weighted += Double(DragMultiSelectEpisode.all[row].minutes) * p
            }
            frame.plane = min(1, weighted / 330)
            if t >= downloadEnd { frame.plane = 1 }
        }

        if t >= resetAt {
            frame.phase = .idle
            frame.selected.removeAll()
            let fade = DragMultiSelectMath.easeOut((t - resetAt) / 0.24)
            for row in 0..<6 {
                frame.share[row] = 1 - fade
                frame.mark[row] = 1 - fade
            }
            frame.passProgress = max(0, 1 - (t - resetAt) / 0.22)
            frame.passOpacity = frame.passProgress
        } else {
            for row in frame.timelineRows {
                let intro = DragMultiSelectMath.rowIntro(row: row, t: t)
                frame.share[row] = max(frame.mark[row], intro)
            }
        }

        if t >= readyAt, t < resetAt {
            let p = DragMultiSelectMath.spring(t: (t - readyAt) / 0.52, damping: 0.86)
            frame.passProgress = p
            frame.passOpacity = DragMultiSelectMath.easeOut((t - readyAt) / 0.26)
        }

        if t >= 0.6, t < 2.67 {
            frame.ghostOpacity = min(1, (t - 0.6) / 0.15) * min(1, (2.67 - t) / 0.15)
            let y0 = 32.0
            let y1 = 4.5 * 64.0
            let u = DragMultiSelectMath.easeInOut((t - 1.05) / 1.25)
            frame.ghost = CGPoint(x: 196.5, y: y0 + (y1 - y0) * u)
            frame.ghostPressed = t >= 0.6 && t < 2.52 ? 1 : 0
        } else if t >= 3.3, t < 3.75 {
            frame.ghostOpacity = min(1, (t - 3.3) / 0.15) * min(1, (3.75 - t) / 0.15)
            frame.ghost = CGPoint(x: 48, y: 5.5 * 64)
            frame.ghostPressed = t >= 3.52 && t < 3.68 ? 1 : 0
        } else if t >= 4.5, t < 4.95 {
            frame.ghostOpacity = min(1, (t - 4.5) / 0.15) * min(1, (4.95 - t) / 0.15)
            frame.ghost = CGPoint(x: 196.5, y: -1)
            frame.ghostPressed = t >= 4.72 && t < 4.88 ? 1 : 0
        }
        return frame
    }

    private func totalMinutes(_ rows: Set<Int>) -> Int {
        rows.reduce(0) { $0 + DragMultiSelectEpisode.all[$1].minutes }
    }
}

enum DragMultiSelectMath {
    static func clamp(_ value: Double) -> Double { min(max(value, 0), 1) }

    static func easeOut(_ value: Double) -> Double {
        cubic(x1: 0.23, y1: 1, x2: 0.32, y2: 1, value: value)
    }

    static func easeInOut(_ value: Double) -> Double {
        cubic(x1: 0.77, y1: 0, x2: 0.175, y2: 1, value: value)
    }

    static func cubic(x1: Double, y1: Double, x2: Double, y2: Double, value: Double) -> Double {
        let target = clamp(value)
        var u = target
        for _ in 0..<7 {
            let x = bezier(u, x1, x2) - target
            let dx = derivative(u, x1, x2)
            if abs(x) < 0.00001 || abs(dx) < 0.000001 { break }
            u -= x / dx
            u = clamp(u)
        }
        return bezier(u, y1, y2)
    }

    static func spring(t: Double, damping: Double) -> Double {
        let x = max(0, t)
        if x >= 1.4 { return 1 }
        let omega = 2 * Double.pi
        if damping >= 0.999 { return 1 - exp(-8 * x) * (1 + 8 * x) }
        let wd = omega * sqrt(max(0.001, 1 - damping * damping))
        let e = exp(-damping * omega * x)
        return 1 - e * (cos(wd * x) + damping / sqrt(1 - damping * damping) * sin(wd * x))
    }

    static func rowIntro(row: Int, t: Double) -> Double {
        let starts = [0.9, 1.25, 1.55, 1.85, 2.15, 3.6]
        guard row < starts.count else { return 1 }
        return clamp(spring(t: (t - starts[row]) / 0.46, damping: 0.72))
    }

    private static func bezier(_ t: Double, _ p1: Double, _ p2: Double) -> Double {
        let v = 1 - t
        return 3 * v * v * t * p1 + 3 * v * t * t * p2 + t * t * t
    }

    private static func derivative(_ t: Double, _ p1: Double, _ p2: Double) -> Double {
        3 * (1 - t) * (1 - t) * p1 + 6 * (1 - t) * t * (p2 - p1) + 3 * t * t * (1 - p2)
    }
}

// MARK: - Screen

struct DragMultiSelectScreen: View {
    let layout: DragMultiSelectLayout
    let frame: DragMultiSelectFrame
    @ObservedObject var engine: DragMultiSelectEngine
    let autoPlay: Bool
    let reduceMotion: Bool

    @State private var rootTouch = false

    var body: some View {
        ZStack(alignment: .topLeading) {
            DragMultiSelectPalette.page
            DragMultiSelectEpisodeList(layout: layout, frame: frame, engine: engine, reduceMotion: reduceMotion)
            DragMultiSelectHeader(layout: layout, frame: frame, engine: engine, reduceMotion: reduceMotion)
            DragMultiSelectFooterFade(layout: layout)
            DragMultiSelectPanel(layout: layout, frame: frame, engine: engine, reduceMotion: reduceMotion)
            if autoPlay, frame.ghostOpacity > 0 {
                DragMultiSelectGhost(layout: layout, frame: frame)
            }
            if frame.passOpacity > 0 {
                DragMultiSelectBoardingPass(layout: layout, frame: frame, engine: engine, reduceMotion: reduceMotion)
            }
        }
        .frame(width: layout.width, height: layout.height, alignment: .topLeading)
        .clipped()
        .contentShape(Rectangle())
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    guard !rootTouch else { return }
                    rootTouch = true
                    engine.userTouchBegan()
                }
                .onEnded { _ in
                    rootTouch = false
                    engine.userTouchEnded()
                }
        )
    }
}

struct DragMultiSelectEpisodeList: View {
    let layout: DragMultiSelectLayout
    let frame: DragMultiSelectFrame
    @ObservedObject var engine: DragMultiSelectEngine
    let reduceMotion: Bool
    private let plan: DragMultiSelectPlan

    init(layout: DragMultiSelectLayout, frame: DragMultiSelectFrame, engine: DragMultiSelectEngine, reduceMotion: Bool) {
        self.layout = layout
        self.frame = frame
        self.engine = engine
        self.reduceMotion = reduceMotion
        plan = DragMultiSelectPlan(rows: frame.timelineRows)
    }

    var body: some View {
        ScrollView(.vertical) {
            LazyVStack(spacing: 0) {
                ForEach(Array(DragMultiSelectEpisode.all.enumerated()), id: \.element.id) { index, episode in
                    DragMultiSelectEpisodeRow(
                        episode: episode,
                        index: index,
                        frame: frame,
                        time: plan.times[index],
                        color: plan.colors[index],
                        engine: engine,
                        reduceMotion: reduceMotion,
                        showsLanding: plan.landsAfter == index
                    )
                }
                Color.clear.frame(height: DragMultiSelectMetrics.panelHeight + layout.panelBottom + 32)
            }
            .coordinateSpace(name: "DragMultiSelectList")
        }
        .scrollIndicators(.hidden)
        .frame(width: layout.width, height: layout.height - layout.listTop)
        .offset(y: layout.listTop)
    }
}

struct DragMultiSelectPlan {
    let times: [(String, String)?]
    let colors: [Color]
    let landsAfter: Int?
    let minutes: Int

    init(rows: Set<Int>) {
        var builtTimes = Array<(String, String)?>(repeating: nil, count: DragMultiSelectEpisode.all.count)
        var builtColors = Array(repeating: DragMultiSelectPalette.orange, count: DragMultiSelectEpisode.all.count)
        var elapsed = 0
        var colorIndex = 0
        var landing: Int?
        for index in DragMultiSelectEpisode.all.indices {
            builtColors[index] = colorIndex.isMultiple(of: 2) ? DragMultiSelectPalette.orange : DragMultiSelectPalette.violet
            if rows.contains(index) {
                builtTimes[index] = Self.clock(minutes: 9 * 60 + 5 + elapsed)
                elapsed += DragMultiSelectEpisode.all[index].minutes
                colorIndex += 1
                if landing == nil, elapsed >= 325 { landing = index }
            }
        }
        times = builtTimes
        colors = builtColors
        landsAfter = landing
        minutes = elapsed
    }

    private static func clock(minutes: Int) -> (String, String) {
        let h24 = (minutes / 60) % 24
        let minute = minutes % 60
        let hour = h24 % 12 == 0 ? 12 : h24 % 12
        return ("\(hour):\(String(format: "%02d", minute))", h24 >= 12 ? "PM" : "AM")
    }
}

struct DragMultiSelectEpisodeRow: View {
    let episode: DragMultiSelectEpisode
    let index: Int
    let frame: DragMultiSelectFrame
    let time: (String, String)?
    let color: Color
    @ObservedObject var engine: DragMultiSelectEngine
    let reduceMotion: Bool
    let showsLanding: Bool
    @State private var rangeStarted = false

    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .topLeading) {
                lead
                rail
                VStack(alignment: .leading, spacing: 4) {
                    Text(episode.title)
                        .font(DragMultiSelectFont.light(19))
                        .tracking(-0.2)
                        .foregroundStyle(DragMultiSelectPalette.ink)
                        .lineLimit(1)
                    Text("#\(episode.number) · \(DragMultiSelectFormat.minutes(episode.minutes)) · \(episode.guest)")
                        .font(DragMultiSelectFont.regular(12))
                        .foregroundStyle(DragMultiSelectPalette.mute)
                        .lineLimit(1)
                }
                .frame(
                    width: geometry.size.width - DragMultiSelectMetrics.textX - DragMultiSelectMetrics.trailRight - DragMultiSelectMetrics.glyph - 12,
                    height: 44,
                    alignment: .topLeading
                )
                .offset(x: DragMultiSelectMetrics.textX, y: 10)

                DragMultiSelectRowMark(
                    progress: frame.progress[index],
                    mark: frame.mark[index],
                    selected: frame.selected.contains(index),
                    downloaded: frame.downloaded.contains(index)
                )
                .frame(width: 22, height: 22)
                .offset(x: geometry.size.width - DragMultiSelectMetrics.trailRight - 22, y: 21)

                if showsLanding {
                    DragMultiSelectLandingMarker(width: geometry.size.width - DragMultiSelectMetrics.railX - 13)
                        .offset(x: DragMultiSelectMetrics.railX - 3, y: DragMultiSelectMetrics.row - 8)
                        .zIndex(4)
                }
            }
        }
        .frame(height: DragMultiSelectMetrics.row)
        .contentShape(Rectangle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityLabel)
        .accessibilityAddTraits(.isButton)
        .accessibilityValue(frame.selected.contains(index) ? "Selected" : frame.downloaded.contains(index) ? "Downloaded" : "")
        .onTapGesture { engine.tap(row: index) }
        .highPriorityGesture(rangeGesture)
    }

    private var lead: some View {
        Group {
            if let time {
                VStack(alignment: .leading, spacing: 0) {
                    Text(time.0)
                        .font(DragMultiSelectFont.medium(13))
                        .foregroundStyle(DragMultiSelectPalette.ink)
                        .monospacedDigit()
                    Text(time.1)
                        .font(DragMultiSelectFont.regular(11))
                        .foregroundStyle(DragMultiSelectPalette.mute)
                }
            } else {
                Text("—")
                    .font(DragMultiSelectFont.medium(13))
                    .foregroundStyle(DragMultiSelectPalette.dim)
            }
        }
        .frame(width: DragMultiSelectMetrics.numberWidth, height: 32, alignment: .topLeading)
        .offset(x: DragMultiSelectMetrics.numberX, y: 12)
        .opacity(frame.share[index] > 0.4 || time == nil ? 1 : 0)
    }

    private var rail: some View {
        ZStack(alignment: .top) {
            DragMultiSelectDottedRail()
                .opacity(1 - min(frame.share[index] * 3, 1))
            Capsule()
                .fill(color)
                .frame(width: DragMultiSelectMetrics.railWidth, height: DragMultiSelectMetrics.row - 8)
                .scaleEffect(x: 1, y: reduceMotion ? 1 : max(0.001, frame.share[index]), anchor: .top)
                .opacity(reduceMotion ? frame.share[index] : frame.share[index] > 0.002 ? 1 : 0)
        }
        .frame(width: 4, height: DragMultiSelectMetrics.row - 8)
        .offset(x: DragMultiSelectMetrics.railX, y: 4)
    }

    private var rangeGesture: some Gesture {
        LongPressGesture(minimumDuration: 0.3, maximumDistance: 12)
            .sequenced(before: DragGesture(minimumDistance: 0, coordinateSpace: .named("DragMultiSelectList")))
            .onChanged { value in
                switch value {
                case .first(true):
                    if !rangeStarted {
                        rangeStarted = true
                        engine.beginRange(row: index)
                    }
                case .second(true, let drag?):
                    let row = min(max(Int(drag.location.y / DragMultiSelectMetrics.row), 0), DragMultiSelectEpisode.all.count - 1)
                    engine.updateRange(row: row)
                default:
                    break
                }
            }
            .onEnded { _ in
                rangeStarted = false
                engine.endRange()
            }
    }

    private var accessibilityLabel: String {
        var text = "Episode \(episode.number), \(episode.title), \(DragMultiSelectFormat.minutes(episode.minutes)), \(episode.guest)"
        if let time { text += ", starts at \(time.0) \(time.1)" }
        if frame.downloaded.contains(index) { text += ", downloaded" }
        return text
    }
}

struct DragMultiSelectDottedRail: View {
    var body: some View {
        VStack(spacing: 4) {
            ForEach(0..<10, id: \.self) { _ in
                Circle().fill(DragMultiSelectPalette.dot).frame(width: 2, height: 2)
            }
        }
        .frame(width: 4, height: DragMultiSelectMetrics.row - 8)
    }
}

struct DragMultiSelectRowMark: View {
    let progress: Double
    let mark: Double
    let selected: Bool
    let downloaded: Bool

    var body: some View {
        ZStack {
            DragMultiSelectGlyph(kind: .download)
                .stroke(selected ? DragMultiSelectPalette.violet : DragMultiSelectPalette.glyph, style: .init(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
                .opacity(progress < 0.01 && !downloaded ? 1 : 0)
            if progress > 0, !downloaded {
                Circle().stroke(DragMultiSelectPalette.ring, lineWidth: 2)
                Circle()
                    .trim(from: 0, to: progress)
                    .stroke(DragMultiSelectPalette.violet, style: .init(lineWidth: 2, lineCap: .round))
                    .rotationEffect(.degrees(-90))
            }
            if downloaded || mark > 0.01 {
                Circle().fill(DragMultiSelectPalette.greenSoft)
                DragMultiSelectGlyph(kind: .check)
                    .trim(from: 0, to: max(mark, downloaded ? 1 : 0))
                    .stroke(DragMultiSelectPalette.green, style: .init(lineWidth: 2.2, lineCap: .round, lineJoin: .round))
                    .padding(3)
            }
        }
    }
}

struct DragMultiSelectLandingMarker: View {
    let width: CGFloat

    var body: some View {
        HStack(spacing: 0) {
            Circle()
                .fill(DragMultiSelectPalette.page)
                .overlay(Circle().stroke(DragMultiSelectPalette.green, lineWidth: 2))
                .frame(width: 10, height: 10)
            Rectangle()
                .fill(DragMultiSelectPalette.green.opacity(0.45))
                .frame(height: 0.5)
            Text("Lands JFK 5:30 PM")
                .font(DragMultiSelectFont.medium(12))
                .foregroundStyle(DragMultiSelectPalette.green)
                .padding(.leading, 8)
                .background(DragMultiSelectPalette.page)
        }
        .frame(width: width, height: 16)
        .accessibilityLabel("Lands at New York 5:30 PM")
    }
}

// MARK: - Header and route

struct DragMultiSelectHeader: View {
    let layout: DragMultiSelectLayout
    let frame: DragMultiSelectFrame
    @ObservedObject var engine: DragMultiSelectEngine
    let reduceMotion: Bool

    var body: some View {
        ZStack(alignment: .topLeading) {
            DragMultiSelectPalette.page
            HStack {
                Button(action: {}) {
                    DragMultiSelectGlyph(kind: .back)
                        .stroke(DragMultiSelectPalette.ink, style: .init(lineWidth: 2, lineCap: .round, lineJoin: .round))
                        .frame(width: 26, height: 26)
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel("Back")
                Spacer()
                Button(action: engine.selectButton) {
                    Text(frame.selecting ? "Cancel" : "Select")
                        .font(DragMultiSelectFont.medium(17))
                        .tracking(-0.2)
                        .foregroundStyle(DragMultiSelectPalette.violet)
                        .padding(.horizontal, 8)
                        .frame(height: 44)
                }
                .opacity(frame.phase == .downloading ? 0 : 1)
                .disabled(frame.phase == .downloading || frame.phase == .ready)
                .accessibilityLabel(frame.selecting ? "Cancel selection" : "Select episodes")
            }
            .frame(height: DragMultiSelectMetrics.nav)
            .padding(.horizontal, DragMultiSelectMetrics.edge - 8)
            .offset(y: layout.safeTop)

            DragMultiSelectFlightCard(layout: layout, frame: frame, reduceMotion: reduceMotion)
                .offset(x: DragMultiSelectMetrics.edge, y: layout.cardTop)

            HStack {
                Text("IN-FLIGHT LISTENING")
                    .font(DragMultiSelectFont.medium(12))
                    .tracking(1.4)
                Spacer()
                Text("Second Draft · 7 new")
                    .font(DragMultiSelectFont.regular(13))
            }
            .foregroundStyle(DragMultiSelectPalette.mute)
            .frame(height: DragMultiSelectMetrics.sectionHeight)
            .padding(.horizontal, DragMultiSelectMetrics.edge)
            .offset(y: layout.sectionTop)
        }
        .frame(width: layout.width, height: layout.listTop, alignment: .topLeading)
    }
}

struct DragMultiSelectFlightCard: View {
    let layout: DragMultiSelectLayout
    let frame: DragMultiSelectFrame
    let reduceMotion: Bool

    private var coverage: Double {
        let drawn = DragMultiSelectEpisode.all.indices.reduce(0.0) {
            $0 + Double(DragMultiSelectEpisode.all[$1].minutes) * frame.share[$1]
        }
        return min(1, drawn / 325)
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: DragMultiSelectMetrics.cardRadius, style: .continuous)
                .fill(DragMultiSelectPalette.card)
                .overlay(
                    RoundedRectangle(cornerRadius: DragMultiSelectMetrics.cardRadius, style: .continuous)
                        .stroke(DragMultiSelectPalette.line, lineWidth: 0.5)
                )
            DragMultiSelectRoute(coverage: coverage, plane: frame.plane, reduceMotion: reduceMotion)
            HStack(spacing: 6) {
                Text("UA 1514").font(DragMultiSelectFont.medium(12)).foregroundStyle(DragMultiSelectPalette.ink)
                Text("·  Thu 23 Oct  ·  5h 25m").font(DragMultiSelectFont.regular(12)).foregroundStyle(DragMultiSelectPalette.mute)
            }
            .offset(x: 20, y: 14)
            codeColumn(code: "SFO", city: "San Francisco", time: "9:05", ap: "AM")
                .offset(x: 20)
            codeColumn(code: "JFK", city: "New York", time: "5:30", ap: "PM", right: true)
                .frame(maxWidth: .infinity, alignment: .trailing)
                .offset(x: -20)
        }
        .frame(width: layout.cardWidth, height: DragMultiSelectMetrics.cardHeight, alignment: .topLeading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("United 1514, Thursday 23 October, San Francisco 9:05 AM to New York 5:30 PM, 5 hours 25 minutes")
    }

    private func codeColumn(code: String, city: String, time: String, ap: String, right: Bool = false) -> some View {
        VStack(alignment: right ? .trailing : .leading, spacing: 0) {
            Text(code)
                .font(DragMultiSelectFont.light(44))
                .tracking(-0.9)
                .foregroundStyle(DragMultiSelectPalette.ink)
                .frame(height: 48)
            Text(city)
                .font(DragMultiSelectFont.regular(12))
                .foregroundStyle(DragMultiSelectPalette.mute)
                .frame(height: 16)
            Spacer().frame(height: 14)
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Text(time).font(DragMultiSelectFont.medium(17)).monospacedDigit()
                Text(ap).font(DragMultiSelectFont.regular(12)).foregroundStyle(DragMultiSelectPalette.mute)
            }
            .foregroundStyle(DragMultiSelectPalette.ink)
            .frame(height: 22)
        }
        .frame(height: 102, alignment: .top)
        .offset(y: 32)
    }
}

struct DragMultiSelectRoute: View {
    let coverage: Double
    let plane: Double
    let reduceMotion: Bool

    private let x0: CGFloat = 120
    private let x1: CGFloat = 250
    private let y: CGFloat = 56
    private let lift: CGFloat = 18

    var body: some View {
        ZStack {
            route.stroke(DragMultiSelectPalette.dot, style: StrokeStyle(lineWidth: 1.5, lineCap: .round, dash: [1, 5]))
            route
                .trim(from: 0, to: coverage)
                .stroke(
                    LinearGradient(colors: [DragMultiSelectPalette.orange, DragMultiSelectPalette.violet], startPoint: .leading, endPoint: .trailing),
                    style: StrokeStyle(lineWidth: 2.5, lineCap: .round)
                )
            Circle().fill(DragMultiSelectPalette.ink).frame(width: 7, height: 7).position(x: x0, y: y)
            Circle()
                .fill(DragMultiSelectPalette.card)
                .overlay(Circle().stroke(DragMultiSelectPalette.ink, lineWidth: 1.5))
                .frame(width: 7, height: 7)
                .position(x: x1, y: y)
            let value = routePoint(plane)
            DragMultiSelectGlyph(kind: .plane)
                .fill(DragMultiSelectPalette.ink)
                .frame(width: 18, height: 18)
                .rotationEffect(.radians(reduceMotion ? 0 : value.angle))
                .background(Circle().fill(DragMultiSelectPalette.card).frame(width: 20, height: 20))
                .position(x: value.point.x, y: value.point.y)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var route: Path {
        var path = Path()
        path.move(to: CGPoint(x: x0, y: y))
        path.addQuadCurve(to: CGPoint(x: x1, y: y), control: CGPoint(x: (x0 + x1) / 2, y: y - 2 * lift))
        return path
    }

    private func routePoint(_ value: Double) -> (point: CGPoint, angle: Double) {
        let t = CGFloat(min(max(value, 0), 1))
        let v = 1 - t
        let cx = (x0 + x1) / 2
        let cy = y - 2 * lift
        let px = v * v * x0 + 2 * v * t * cx + t * t * x1
        let py = v * v * y + 2 * v * t * cy + t * t * y
        let dx = 2 * v * (cx - x0) + 2 * t * (x1 - cx)
        let dy = 2 * v * (cy - y) + 2 * t * (y - cy)
        return (CGPoint(x: px, y: py), atan2(dy, dx))
    }
}

// MARK: - Panel

struct DragMultiSelectFooterFade: View {
    let layout: DragMultiSelectLayout

    var body: some View {
        LinearGradient(
            colors: [DragMultiSelectPalette.page.opacity(0), DragMultiSelectPalette.page, DragMultiSelectPalette.page],
            startPoint: .top,
            endPoint: .bottom
        )
        .frame(width: layout.width, height: layout.height - layout.panelTop + 24)
        .offset(y: layout.panelTop - 24)
        .allowsHitTesting(false)
    }
}

struct DragMultiSelectPanel: View {
    let layout: DragMultiSelectLayout
    let frame: DragMultiSelectFrame
    @ObservedObject var engine: DragMultiSelectEngine
    let reduceMotion: Bool
    @State private var pressed = false

    private var rows: Set<Int> { frame.timelineRows }
    private var minutes: Int { rows.reduce(0) { $0 + DragMultiSelectEpisode.all[$1].minutes } }
    private var finished: Bool { frame.phase == .ready }
    private var enabled: Bool { frame.phase == .selecting && !frame.selected.isEmpty }
    private var busy: Bool { frame.phase == .downloading }

    var body: some View {
        VStack(spacing: DragMultiSelectMetrics.cellsGap) {
            HStack(spacing: 0) {
                DragMultiSelectFigure(value: DragMultiSelectFormat.short(minutes), label: finished ? "Offline" : "Planned")
                Rectangle().fill(DragMultiSelectPalette.line).frame(width: 0.5)
                DragMultiSelectFigure(value: rightValue, label: rightLabel, tint: rightTint)
            }
            .frame(height: DragMultiSelectMetrics.cells)
            .background(DragMultiSelectPalette.card)
            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(DragMultiSelectPalette.line, lineWidth: 0.5))
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("\(DragMultiSelectFormat.spoken(minutes)) planned of a 5 hour 25 minute flight")

            Button(action: engine.startDownload) {
                ZStack {
                    RoundedRectangle(cornerRadius: DragMultiSelectMetrics.buttonRadius, style: .continuous).fill(buttonColor)
                    if busy {
                        GeometryReader { proxy in
                            Rectangle()
                                .fill(DragMultiSelectPalette.violet)
                                .frame(width: proxy.size.width * averageProgress, height: proxy.size.height)
                        }
                        .clipShape(RoundedRectangle(cornerRadius: DragMultiSelectMetrics.buttonRadius, style: .continuous))
                    }
                    HStack(spacing: 6) {
                        if finished {
                            DragMultiSelectGlyph(kind: .check)
                                .stroke(DragMultiSelectPalette.green, style: .init(lineWidth: 2.4, lineCap: .round, lineJoin: .round))
                                .frame(width: 18, height: 18)
                        }
                        Text(buttonLabel)
                            .font(DragMultiSelectFont.semibold(16))
                            .tracking(-0.2)
                            .foregroundStyle(buttonInk)
                            .lineLimit(1)
                    }
                }
                .frame(height: DragMultiSelectMetrics.button)
                .scaleEffect(pressed && enabled && !reduceMotion ? 0.97 : 1)
                .opacity(pressed && enabled && reduceMotion ? 0.85 : 1)
            }
            .buttonStyle(.plain)
            .disabled(!enabled)
            .simultaneousGesture(DragGesture(minimumDistance: 0).onChanged { _ in pressed = true }.onEnded { _ in pressed = false })
            .accessibilityLabel(buttonLabel)
        }
        .frame(width: layout.panelWidth, height: DragMultiSelectMetrics.panelHeight, alignment: .top)
        .offset(x: DragMultiSelectMetrics.panelSide, y: layout.panelTop)
    }

    private var rightValue: String {
        if finished { return "\(frame.downloaded.count)" }
        if frame.phase == .idle || rows.isEmpty { return DragMultiSelectFormat.short(325) }
        let gap = 325 - minutes
        return gap > 0 ? DragMultiSelectFormat.short(gap) : "+\(DragMultiSelectFormat.short(-gap))"
    }

    private var rightLabel: String {
        if finished { return "Episodes" }
        if frame.phase == .idle || rows.isEmpty { return "Flight time" }
        return 325 - minutes > 0 ? "Still to fill" : "Spare on landing"
    }

    private var rightTint: Color? {
        325 - minutes <= 0 && !finished ? DragMultiSelectPalette.green : nil
    }

    private var buttonLabel: String {
        if finished { return "Ready for takeoff" }
        if busy { return "Downloading \(frame.selected.count) episodes" }
        if enabled { return "Download \(frame.selected.count) · \(DragMultiSelectFormat.size(minutes))" }
        return "Hold an episode, then drag"
    }

    private var buttonColor: Color {
        if enabled { return DragMultiSelectPalette.violet }
        if busy { return DragMultiSelectPalette.violetTrack }
        return DragMultiSelectPalette.buttonOff
    }

    private var buttonInk: Color {
        if enabled { return DragMultiSelectPalette.violetInk }
        if busy || finished { return DragMultiSelectPalette.ink }
        return DragMultiSelectPalette.mute
    }

    private var averageProgress: CGFloat {
        guard !frame.selected.isEmpty else { return 0 }
        let sum = frame.selected.reduce(0.0) { $0 + frame.progress[$1] }
        return CGFloat(sum / Double(frame.selected.count))
    }
}

struct DragMultiSelectFigure: View {
    let value: String
    let label: String
    var tint: Color?

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value)
                .font(DragMultiSelectFont.light(26))
                .tracking(-0.3)
                .foregroundStyle(tint ?? DragMultiSelectPalette.ink)
                .monospacedDigit()
                .frame(height: 30)
            Text(label)
                .font(DragMultiSelectFont.regular(12))
                .foregroundStyle(DragMultiSelectPalette.mute)
                .frame(height: 16)
        }
        .padding(.horizontal, 16)
        .padding(.top, 12)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .contentTransition(.numericText())
    }
}

// MARK: - Boarding pass and finger

struct DragMultiSelectBoardingPass: View {
    let layout: DragMultiSelectLayout
    let frame: DragMultiSelectFrame
    @ObservedObject var engine: DragMultiSelectEngine
    let reduceMotion: Bool

    private let side: CGFloat = 28
    private let height: CGFloat = 560
    private let perforation: CGFloat = 392

    var body: some View {
        ZStack(alignment: .topLeading) {
            Color.black.opacity(0.6 * frame.passOpacity)
                .contentShape(Rectangle())
                .onTapGesture { engine.dismissPass() }
            card
                .frame(width: layout.width - side * 2, height: height)
                .offset(x: side, y: layout.safeTop + 52 + (1 - frame.passProgress) * (reduceMotion ? 0 : 120))
                .scaleEffect(0.96 + 0.04 * frame.passProgress)
                .opacity(min(1, frame.passOpacity * 1.4))
                .onTapGesture { engine.dismissPass() }
        }
        .frame(width: layout.width, height: layout.height, alignment: .topLeading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Ready for takeoff. 6 episodes, 5 hours 30 minutes offline, 310 megabytes on this iPhone. United 1514, San Francisco to New York.")
    }

    private var card: some View {
        let width = layout.width - side * 2
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .fill(DragMultiSelectPalette.pass)
                .overlay(RoundedRectangle(cornerRadius: 28, style: .continuous).stroke(DragMultiSelectPalette.passEdge, lineWidth: 0.5))
                .shadow(color: .black.opacity(0.6), radius: 40, y: 24)
            RadialGradient(
                colors: [DragMultiSelectPalette.orange, DragMultiSelectPalette.violet, DragMultiSelectPalette.violet.opacity(0)],
                center: .bottom,
                startRadius: 0,
                endRadius: 230
            )
            .frame(width: width, height: 220)
            .offset(y: height - 220)
            .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))

            HStack {
                DragMultiSelectPassPair(key: "FLIGHT", value: "UA 1514")
                Spacer()
                DragMultiSelectPassPair(key: "SEAT", value: "23A")
                Spacer()
                DragMultiSelectPassPair(key: "BOARDING", value: "8:25 AM")
                Spacer()
                DragMultiSelectPassPair(key: "DATE", value: "23 Oct")
            }
            .padding(.horizontal, 22)
            .padding(.top, 22)

            VStack(spacing: 0) {
                Text("SFO").font(DragMultiSelectFont.dots(84)).tracking(1).foregroundStyle(DragMultiSelectPalette.ink).frame(height: 88)
                Text("San Francisco International").font(DragMultiSelectFont.regular(11)).foregroundStyle(DragMultiSelectPalette.passMute).frame(height: 14)
                Spacer().frame(height: 24)
                Text("JFK").font(DragMultiSelectFont.dots(84)).tracking(1).foregroundStyle(DragMultiSelectPalette.ink).frame(height: 88)
                Text("John F. Kennedy International").font(DragMultiSelectFont.regular(11)).foregroundStyle(DragMultiSelectPalette.passMute).frame(height: 14)
            }
            .frame(width: width)
            .offset(y: 80)

            DragMultiSelectPerforation()
                .frame(width: width - 32, height: 2)
                .offset(x: 16, y: perforation)
            Circle().fill(DragMultiSelectPalette.page).frame(width: 22, height: 22).offset(x: -11, y: perforation - 11)
            Circle().fill(DragMultiSelectPalette.page).frame(width: 22, height: 22).offset(x: width - 11, y: perforation - 11)

            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("SECOND DRAFT").font(DragMultiSelectFont.semibold(12)).tracking(0.7).foregroundStyle(DragMultiSelectPalette.ink)
                    Text("6 episodes · 5h 30m offline\n310 MB on this iPhone")
                        .font(DragMultiSelectFont.regular(12))
                        .foregroundStyle(DragMultiSelectPalette.passText)
                }
                Spacer()
                Text("Ready for takeoff")
                    .font(DragMultiSelectFont.semibold(12))
                    .foregroundStyle(DragMultiSelectPalette.pass)
                    .padding(.horizontal, 12)
                    .frame(height: 28)
                    .background(Color.white.opacity(0.94))
                    .clipShape(Capsule())
            }
            .padding(.horizontal, 22)
            .offset(y: perforation + 28)
        }
    }
}

struct DragMultiSelectPassPair: View {
    let key: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(key).font(DragMultiSelectFont.medium(10)).tracking(0.8).foregroundStyle(DragMultiSelectPalette.passMute)
            Text(value).font(DragMultiSelectFont.medium(13)).foregroundStyle(DragMultiSelectPalette.ink)
        }
    }
}

struct DragMultiSelectPerforation: View {
    var body: some View {
        HStack(spacing: 8) {
            ForEach(0..<34, id: \.self) { _ in
                Circle().fill(Color.white.opacity(0.32)).frame(width: 2, height: 2)
            }
        }
    }
}

struct DragMultiSelectGhost: View {
    let layout: DragMultiSelectLayout
    let frame: DragMultiSelectFrame

    var body: some View {
        Circle()
            .fill(Color.white.opacity(0.16))
            .overlay(Circle().stroke(Color.white.opacity(0.32), lineWidth: 1))
            .frame(width: DragMultiSelectMetrics.finger, height: DragMultiSelectMetrics.finger)
            .scaleEffect(1 - 0.12 * frame.ghostPressed)
            .opacity(frame.ghostOpacity)
            .position(
                x: frame.ghost.x,
                y: frame.ghost.y < 0 ? layout.panelTop + DragMultiSelectMetrics.panelHeight - DragMultiSelectMetrics.button / 2 : layout.listTop + frame.ghost.y
            )
            .allowsHitTesting(false)
    }
}

// MARK: - Drawing and formatting

struct DragMultiSelectGlyph: Shape {
    enum Kind { case back, check, download, plane }
    let kind: Kind

    func path(in rect: CGRect) -> Path {
        let sx = rect.width / 24
        let sy = rect.height / 24
        func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: rect.minX + x * sx, y: rect.minY + y * sy) }
        var path = Path()
        switch kind {
        case .back:
            path.move(to: p(15, 5)); path.addLine(to: p(8, 12)); path.addLine(to: p(15, 19))
        case .check:
            path.move(to: p(5.5, 12.5)); path.addLine(to: p(10, 17)); path.addLine(to: p(18.5, 7.5))
        case .download:
            path.move(to: p(12, 4.5)); path.addLine(to: p(12, 14))
            path.move(to: p(8, 10.25)); path.addLine(to: p(12, 14.25)); path.addLine(to: p(16, 10.25))
            path.move(to: p(5.5, 15.5)); path.addLine(to: p(5.5, 18))
            path.addQuadCurve(to: p(7, 19.5), control: p(5.5, 19.5))
            path.addLine(to: p(17, 19.5)); path.addQuadCurve(to: p(18.5, 18), control: p(18.5, 19.5))
            path.addLine(to: p(18.5, 15.5))
        case .plane:
            path.move(to: p(21, 12))
            path.addCurve(to: p(19.5, 10.6), control1: p(21, 11.2), control2: p(20.3, 10.6))
            path.addLine(to: p(14.6, 10.6)); path.addLine(to: p(10, 3.5)); path.addLine(to: p(8.2, 3.5))
            path.addLine(to: p(10.5, 10.6)); path.addLine(to: p(6, 10.6)); path.addLine(to: p(4.4, 8.6))
            path.addLine(to: p(3, 8.6)); path.addLine(to: p(4, 12)); path.addLine(to: p(3, 15.4))
            path.addLine(to: p(4.4, 15.4)); path.addLine(to: p(6, 13.4)); path.addLine(to: p(10.5, 13.4))
            path.addLine(to: p(8.2, 20.5)); path.addLine(to: p(10, 20.5)); path.addLine(to: p(14.6, 13.4))
            path.addLine(to: p(19.5, 13.4))
            path.addCurve(to: p(21, 12), control1: p(20.3, 13.4), control2: p(21, 12.8))
            path.closeSubpath()
        }
        return path
    }
}

enum DragMultiSelectFormat {
    static func minutes(_ value: Int) -> String {
        if value < 60 { return "\(value) min" }
        let h = value / 60
        let m = value % 60
        return m == 0 ? "\(h) hr" : "\(h) hr \(m) min"
    }

    static func short(_ value: Int) -> String {
        if value < 60 { return "\(value)m" }
        let h = value / 60
        let m = value % 60
        return m == 0 ? "\(h)h" : "\(h)h \(m)m"
    }

    static func size(_ value: Int) -> String {
        let mb = Int((Double(value) * 0.94).rounded())
        return mb >= 1000 ? String(format: "%.1f GB", Double(mb) / 1000) : "\(mb) MB"
    }

    static func spoken(_ value: Int) -> String {
        let h = value / 60
        let m = value % 60
        var parts: [String] = []
        if h > 0 { parts.append("\(h) hour\(h == 1 ? "" : "s")") }
        if m > 0 { parts.append("\(m) minute\(m == 1 ? "" : "s")") }
        return parts.isEmpty ? "0 minutes" : parts.joined(separator: " ")
    }
}
