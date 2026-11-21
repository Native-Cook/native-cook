import SwiftUI
import UIKit

/// The same eight-well composition and deterministic ten-second press sequence as the React Native reference.
struct PressedStampCard: View {
    static let loop = 10.0
    let autoPlay: Bool
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @State private var origin = Date()
    @State private var touch: PressedStampCardTouch?
    @State private var settleTask: DispatchWorkItem?
    @State private var resumeTask: DispatchWorkItem?
    @State private var impactTask: DispatchWorkItem?
    @State private var debugPin: Double?
    @State private var baseCount = 5
    @State private var live = false
    @State private var redeemed = false
    @State private var queuedScan = false
    @GestureState private var dragging = false

    init(autoPlay: Bool = true) {
        #if DEBUG
        let arguments = ProcessInfo.processInfo.arguments
        self.autoPlay = autoPlay && !arguments.contains("-psc-manual")
        if let i = arguments.firstIndex(of: "-psc-time"), arguments.indices.contains(i + 1) { _debugPin = State(initialValue: Double(arguments[i + 1])) }
        if let i = arguments.firstIndex(of: "-psc-stamps"), arguments.indices.contains(i + 1), let count = Int(arguments[i + 1]) {
            _baseCount = State(initialValue: min(max(count, 0), 8)); _live = State(initialValue: true)
        }
        #else
        self.autoPlay = autoPlay
        #endif
    }

    var body: some View {
        GeometryReader { safe in
            scene(insets: safe.safeAreaInsets).ignoresSafeArea()
        }
        .onChange(of: dragging) { _, active in
            guard !active, let touch,
                  touch.mode == .holding || (touch.mode == .redeeming && !touch.redeemed) else { return }
            self.touch = nil
            scheduleResume()
        }
        .onDisappear { settleTask?.cancel(); resumeTask?.cancel(); impactTask?.cancel() }
        .onChange(of: reduced) { _, value in
            if value {
                if let touch, touch.mode == .stamping { baseCount = touch.target + 1 }
                else if let touch, touch.mode == .redeeming, touch.redeemed { baseCount = 0 }
                settleTask?.cancel(); resumeTask?.cancel(); impactTask?.cancel(); touch = nil; live = true
            }
        }
    }

    private func scene(insets: EdgeInsets) -> some View {
        GeometryReader { proxy in
            let layout = PressedStampCardLayout(size: proxy.size, insets: insets)
            TimelineView(.animation(minimumInterval: 1 / 60, paused: timelinePaused)) { timeline in
                let frame = current(at: timeline.date)
                ZStack {
                    Canvas(opaque: true, colorMode: .nonLinear, rendersAsynchronously: false) { context, size in
                        PressedStampCardDrawing.scene(&context, size: size, layout: layout, frame: frame, hand: handPoint(at: timeline.date))
                    }
                    .contentShape(Rectangle())
                    .gesture(drag(layout: layout))
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("Coffee Club stamp card")
                    .accessibilityValue("\(frame.count) of 8 stamps\(redeemed ? ", redeemed" : "")")
                    .accessibilityHint("Hold a card well, then drag the brass die into the next empty well and release. Pull down on a completed card to redeem.")
                    .accessibilityAdjustableAction { direction in if direction == .increment { scan() } }
                    .accessibilityAction(named: "Add a stamp") { scan() }
                    .accessibilityAction(named: "Redeem completed card") { redeem() }
                    Button(action: { frame.count == 8 ? redeem() : scan() }) {
                        Rectangle().fill(.clear).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .frame(width: 362 * layout.scale, height: 56 * layout.scale)
                    .position(x: proxy.size.width / 2, y: layout.origin.y + 506 * layout.scale)
                    .accessibilityLabel(frame.count == 8 ? "Redeem free coffee" : "Scan at the counter")
                }
            }
        }
        .background(PressedStampCardDrawing.page)
    }

    private var timelinePaused: Bool {
        scenePhase != .active || reduced || (touch == nil && (debugPin != nil || !autoPlay || live))
    }
    private func current(at date: Date) -> PressedStampCardFrame {
        guard let touch else {
            if reduced || live || !autoPlay { return PressedStampCardMotion.rest(count: baseCount) }
            return PressedStampCardMotion.autoplay(debugPin ?? date.timeIntervalSince(origin))
        }
        let elapsed = max(0, date.timeIntervalSince(touch.started))
        switch touch.mode {
        case .holding:
            var frame = PressedStampCardMotion.rest(count: touch.target)
            frame.toolRotation = touch.velocityTilt
            return frame
        case .stamping:
            if reduced { return PressedStampCardMotion.rest(count: touch.target + 1) }
            var frame = PressedStampCardMotion.manual(elapsed: elapsed + touch.eventOffset, target: touch.target, charge: touch.charge)
            if touch.eventOffset > 0, elapsed < 0.14 {
                let p = pow(CGFloat(min(elapsed / 0.14, 1)), 3), target = PressedStampCardMotion.seal(touch.target)
                frame.toolPoint = CGPoint(x: PressedStampCardMotion.mix(touch.point.x - 20, target.x, p), y: PressedStampCardMotion.mix(touch.point.y - 118, target.y, p))
            }
            return frame
        case .redeeming:
            if touch.redeemed { return reduced ? PressedStampCardMotion.rest(count: 0) : PressedStampCardMotion.flip(elapsed) }
            var frame = PressedStampCardMotion.rest(count: 8); frame.dip = reduced ? 0 : touch.pull; return frame
        case .cancelled:
            var frame = PressedStampCardMotion.rest(count: baseCount)
            if !reduced, elapsed < 0.32 {
                let p = PressedStampCardMotion.out(PressedStampCardMotion.segment(elapsed, 0, 0.32))
                frame.tool = 1; frame.toolPoint = CGPoint(x: touch.point.x - 20 + p * 440, y: touch.point.y - 118 - p * 80); frame.toolRadius = 52; frame.toolLift = 1
            }
            return frame
        }
    }
    private func drag(layout: PressedStampCardLayout) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .local)
            .updating($dragging) { _, active, _ in active = true }
            .onChanged { value in
                let point = layout.scenePoint(value.location)
                if touch == nil { begin(layout.scenePoint(value.startLocation)) }
                guard var active = touch else { return }
                if active.mode == .holding {
                    let held = Date().timeIntervalSince(active.started)
                    guard held >= 0.18 else { return }
                    let target = PressedStampCardMotion.seal(active.target), targetPoint = CGPoint(x: target.x + 20, y: target.y + 118)
                    let engaged = hypot(point.x - targetPoint.x, point.y - targetPoint.y) <= 48
                    if engaged, !active.magnet { UISelectionFeedbackGenerator().selectionChanged() }
                    active.magnet = engaged
                    active.point = CGPoint(x: PressedStampCardMotion.mix(point.x, targetPoint.x, engaged ? 0.35 : 0), y: PressedStampCardMotion.mix(point.y, targetPoint.y, engaged ? 0.35 : 0))
                    active.charge = min(CGFloat(held / 1.2), 1)
                    active.velocityTilt = min(max(value.velocity.width / layout.scale / 90, -10), 10)
                    touch = active
                } else if active.mode == .redeeming, !active.redeemed {
                    let distance = max(0, value.translation.height / layout.scale)
                    active.pull = distance / (1 + distance / 120)
                    if distance >= 72, !active.redeemHapticPlayed { active.redeemHapticPlayed = true; UIImpactFeedbackGenerator(style: .rigid).impactOccurred() }
                    touch = active
                }
            }
            .onEnded { value in
                end(layout.scenePoint(value.location), distance: value.translation.height / layout.scale)
            }
    }
    private func takeOver() {
        let visible = current(at: Date())
        settleTask?.cancel(); resumeTask?.cancel(); impactTask?.cancel()
        baseCount = visible.count; live = true; debugPin = nil
    }
    private func begin(_ point: CGPoint) {
        guard (20...382).contains(point.x), (118...462).contains(point.y) else { return }
        takeOver()
        let mode: PressedStampCardTouch.Mode = baseCount == 8 ? .redeeming : .holding
        touch = PressedStampCardTouch(mode: mode, started: Date(), point: point, target: min(baseCount, 7))
    }
    private func end(_ point: CGPoint, distance: CGFloat) {
        guard var active = touch else { return }
        if active.mode == .redeeming {
            if distance >= 72 { redeem() } else { touch = nil; scheduleResume() }
            return
        }
        guard active.mode == .holding else { return }
        let held = Date().timeIntervalSince(active.started), target = PressedStampCardMotion.seal(active.target)
        guard held >= 0.18 else { touch = nil; scheduleResume(); return }
        guard hypot(point.x - 20 - target.x, point.y - 118 - target.y) <= 48 else {
            active.mode = .cancelled; active.started = Date(); touch = active
            settle(after: 0.32, count: baseCount); return
        }
        active.charge = min(CGFloat(held / 1.2), 1); active.mode = .stamping; active.started = Date()
        active.eventOffset = active.target == 7 ? 1.0 : 0.82; touch = active
        scheduleImpact(after: reduced ? 0 : 0.14, final: active.target == 7)
        settle(after: reduced ? 0.2 : (active.target == 7 ? 1.65 : 1.38), count: active.target + 1)
    }
    private func handPoint(at date: Date) -> CGPoint? {
        guard let touch, touch.mode == .holding, !reduced, date.timeIntervalSince(touch.started) >= 0.18 else { return nil }
        return touch.point
    }
    private func scan() {
        if let touch, touch.mode == .stamping { queuedScan = true; return }
        guard touch == nil else { return }
        takeOver()
        guard baseCount < 8 else { return }
        redeemed = false
        touch = PressedStampCardTouch(mode: .stamping, started: Date(), point: .zero, target: baseCount)
        scheduleImpact(after: reduced ? 0 : baseCount == 7 ? 1.14 : 0.96, final: baseCount == 7)
        settle(after: reduced ? 0.4 : baseCount == 7 ? 2.65 : 2.2, count: baseCount + 1)
    }
    private func redeem() {
        guard current(at: Date()).count == 8 else { return }
        takeOver(); redeemed = true
        var action = PressedStampCardTouch(mode: .redeeming, started: Date(), point: .zero, target: 7); action.redeemed = true; touch = action
        UIImpactFeedbackGenerator(style: .rigid).impactOccurred()
        settle(after: reduced ? 0.2 : 0.7, count: 0)
    }
    private func settle(after duration: Double, count: Int) {
        settleTask?.cancel()
        let task = DispatchWorkItem {
            baseCount = count; touch = nil
            if queuedScan, count < 8 { queuedScan = false; scan() }
            else { queuedScan = false; scheduleResume() }
        }
        settleTask = task; DispatchQueue.main.asyncAfter(deadline: .now() + duration, execute: task)
    }
    private func scheduleResume() {
        resumeTask?.cancel()
        guard autoPlay, !reduced, !redeemed else { return }
        let task = DispatchWorkItem { baseCount = 5; live = false; origin = Date() }
        resumeTask = task; DispatchQueue.main.asyncAfter(deadline: .now() + 2, execute: task)
    }
    private func scheduleImpact(after delay: Double, final: Bool) {
        impactTask?.cancel()
        let task = DispatchWorkItem { UIImpactFeedbackGenerator(style: final ? .heavy : .rigid).impactOccurred() }
        impactTask = task; DispatchQueue.main.asyncAfter(deadline: .now() + delay, execute: task)
    }
}

struct PressedStampCardLayout {
    let scale: CGFloat
    let origin: CGPoint
    let tabsY: CGFloat

    init(size: CGSize, insets: EdgeInsets) {
        let tabsBottom = max(10, insets.bottom - 10)
        // Above the tabs, keep the Scan button and at least one whole order visible.
        scale = max(0.01, min(size.width / 402, (size.height - insets.top - tabsBottom) / 680))
        origin = CGPoint(x: (size.width - 402 * scale) / 2, y: insets.top - 62 * scale)
        tabsY = (size.height - tabsBottom - origin.y) / scale - 64
    }

    func scenePoint(_ point: CGPoint) -> CGPoint {
        CGPoint(x: (point.x - origin.x) / scale, y: (point.y - origin.y) / scale)
    }
}
