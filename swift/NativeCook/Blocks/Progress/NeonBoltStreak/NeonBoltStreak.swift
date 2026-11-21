import SwiftUI
import UIKit

/// Seven habit days drawn as physical neon tubes. A press strikes the selected eligible day; hold to charge, scrub
/// across eligible weekdays to re-strike them, or hold the number to replay the completed circuit.
struct NeonBoltStreak: View {
    static let loop = 9.5

    let autoPlay: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var loopOrigin = Date()
    @State private var touch: NeonBoltStreakTouch?
    @State private var returnTask: DispatchWorkItem?
    @State private var ignoringGesture = false
    @GestureState private var dragging = false

    init(autoPlay: Bool = true) {
        #if DEBUG
        self.autoPlay = autoPlay && !ProcessInfo.processInfo.arguments.contains("-nbs-manual")
        #else
        self.autoPlay = autoPlay
        #endif
    }

    var body: some View {
        GeometryReader { safe in
            scene(insets: safe.safeAreaInsets).ignoresSafeArea()
        }
        .onChange(of: dragging) { _, active in
            guard !active else { return }
            ignoringGesture = false
            if touch?.mode == .pressing {
                touch = nil
                loopOrigin = Date()
            } else if touch != nil, returnTask == nil {
                scheduleReturn()
            }
        }
        .onChange(of: reduceMotion) { _, reduced in
            if reduced { returnTask?.cancel(); touch = nil }
            else { loopOrigin = Date() }
        }
        .onDisappear { returnTask?.cancel() }
    }

    private func scene(insets: EdgeInsets) -> some View {
        GeometryReader { proxy in
            let scale = max(0.01, min(proxy.size.width / 402, (proxy.size.height / 2 - max(insets.top, insets.bottom)) / 291))
            TimelineView(.animation(minimumInterval: 1 / 60, paused: timelinePaused)) { timeline in
                let frame = displayedFrame(at: timeline.date)
                ZStack {
                    NeonBoltStreakDrawing.page
                    ZStack {
                        Canvas(opaque: true, colorMode: .nonLinear, rendersAsynchronously: false) { context, size in
                            NeonBoltStreakDrawing.scene(&context, size: size, frame: frame)
                        }
                        NeonBoltStreakIgnitionHaze(frame: frame, size: CGSize(width: 402, height: 874))
                    }
                    .frame(width: 402, height: 874)
                    .projectionEffect(cameraProjection(frame.camera))
                    .scaleEffect(scale)
                }
                .frame(width: proxy.size.width, height: proxy.size.height)
                .clipped()
                .contentShape(Rectangle())
                .gesture(dragGesture(size: proxy.size, scale: scale))
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Neon bolt streak")
                .accessibilityValue("\(Int(frame.count.rounded())) day streak")
                .accessibilityHint("Tap an eligible weekday to strike it. Hold to charge, or drag across weekdays to select one.")
                .accessibilityAddTraits(.isButton)
                .accessibilityAction { accessibilityStrike() }
            }
        }
        .background(NeonBoltStreakDrawing.page)
    }

    private var timelinePaused: Bool {
        pinnedTime != nil || reduceMotion || scenePhase != .active || (!autoPlay && touch == nil)
    }

    private func displayedFrame(at date: Date) -> NeonBoltStreakFrame {
        if let pinnedTime { return NeonBoltStreakMotion.autoplayFrame(at: pinnedTime) }
        if reduceMotion {
            if let touch, touch.day == NeonBoltStreakMotion.today {
                var steady = NeonBoltStreakMotion.restFrame()
                steady.lit[touch.day] = 1
                steady.fill[touch.day] = 1.02
                steady.burn[touch.day] = 1.02
                steady.label[touch.day] = 1
                steady.count = 4
                return steady
            }
            return NeonBoltStreakMotion.restFrame()
        }
        if let touch {
            let elapsed = max(0, date.timeIntervalSince(touch.started))
            switch touch.mode {
            case .pressing, .igniting:
                let heldCharge = touch.mode == .pressing && !touch.countGesture
                    ? CGFloat(elapsed > 0.12 ? min(elapsed / 1.2, 1) : 0)
                    : touch.charge
                var frame = touch.mode == .pressing ? NeonBoltStreakMotion.restFrame() : NeonBoltStreakMotion.manualFrame(elapsed: elapsed, day: touch.day, charge: touch.charge, celebration: false, flicker: false)
                frame.charge = max(touch.charge, heldCharge)
                frame.strikeDay = touch.mode == .pressing && !touch.countGesture ? touch.day : frame.strikeDay
                if touch.mode == .pressing { frame.strikeDrop = 1 }
                for flicker in touch.flickers where date.timeIntervalSince(flicker.started) < 0.5 {
                    frame.lit[flicker.day] = NeonBoltStreakMotion.flicker(at: date.timeIntervalSince(flicker.started))
                    frame.fill[flicker.day] = 1
                }
                return frame
            case .flickering:
                var frame = NeonBoltStreakMotion.manualFrame(elapsed: elapsed, day: touch.day, charge: 0, celebration: false, flicker: true)
                for flicker in touch.flickers where date.timeIntervalSince(flicker.started) < 0.5 {
                    frame.lit[flicker.day] = NeonBoltStreakMotion.flicker(at: date.timeIntervalSince(flicker.started))
                    frame.fill[flicker.day] = 1
                }
                return frame
            case .celebrating:
                return NeonBoltStreakMotion.manualFrame(elapsed: elapsed, day: touch.day, charge: 1, celebration: true, flicker: false)
            }
        }
        guard autoPlay else { return NeonBoltStreakMotion.restFrame() }
        return NeonBoltStreakMotion.autoplayFrame(at: date.timeIntervalSince(loopOrigin))
    }

    private func dragGesture(size: CGSize, scale: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .local)
            .updating($dragging) { _, active, _ in active = true }
            .onChanged { value in
                if touch == nil, !ignoringGesture {
                    beginTouch(at: scenePoint(value.startLocation, size: size, scale: scale))
                }
                guard var active = touch else { return }
                let point = scenePoint(value.location, size: size, scale: scale)
                let start = scenePoint(value.startLocation, size: size, scale: scale)
                let moved = hypot(point.x - start.x, point.y - start.y)
                if moved > 30, active.beganCharging, active.mode == .pressing {
                    active.cancelled = true
                    active.charge = 0
                }
                if active.mode != .celebrating {
                    let held = Date().timeIntervalSince(active.started)
                    if active.countGesture, moved <= 30, held > 0.5 {
                        active.mode = .celebrating
                        active.started = Date()
                        UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
                    } else if isWeek(point) {
                        let entered = day(at: point.x)
                        if moved > 30, entered != active.lastDay {
                            let step = entered > active.lastDay ? 1 : -1
                            for day in stride(from: active.lastDay + step, through: entered, by: step) where day <= NeonBoltStreakMotion.today {
                                if day < NeonBoltStreakMotion.today { active.flickers.append((day, Date())) }
                                else { active.day = day; active.mode = .igniting; active.started = Date(); active.cancelled = false }
                            }
                            active.lastDay = entered
                        }
                        if entered == NeonBoltStreakMotion.today, moved <= 30, !active.cancelled {
                            active.day = entered
                            active.beganCharging = true
                        }
                        if moved <= 30 { active.charge = CGFloat(held > 0.12 ? min(held / 1.2, 1) : 0) }
                        if active.charge > 0.55, !active.chargeHapticPlayed {
                            active.chargeHapticPlayed = true
                            UISelectionFeedbackGenerator().selectionChanged()
                        }
                    }
                }
                touch = active
            }
            .onEnded { _ in ignoringGesture = false; endTouch() }
    }

    private func beginTouch(at point: CGPoint) {
        returnTask?.cancel()
        returnTask = nil
        let now = Date()
        if isCount(point) {
            touch = NeonBoltStreakTouch(mode: .pressing, day: 6, started: now, countGesture: true, lastDay: 6)
            return
        }
        guard isWeek(point) else { ignoringGesture = true; return }
        let selected = day(at: point.x)
        guard selected <= NeonBoltStreakMotion.today else { ignoringGesture = true; return }
        let mode: NeonBoltStreakTouch.Mode = selected < NeonBoltStreakMotion.today ? .flickering : .pressing
        touch = NeonBoltStreakTouch(mode: mode, day: selected, started: now, lastDay: selected, beganCharging: selected == NeonBoltStreakMotion.today)
        UIImpactFeedbackGenerator(style: .soft).impactOccurred()
    }

    private func endTouch() {
        guard var active = touch else { return }
        if active.cancelled { scheduleReturn(); return }
        switch active.mode {
        case .pressing:
            let held = Date().timeIntervalSince(active.started)
            if active.countGesture, held > 0.5 {
                active.mode = .celebrating
                active.started = Date()
                touch = active
                UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
                scheduleReturn()
                return
            }
            guard !active.countGesture else { scheduleReturn(); return }
            active.charge = max(active.charge, CGFloat(held > 0.12 ? min(held / 1.2, 1) : 0))
            active.mode = .igniting
            active.started = Date()
            touch = active
            UIImpactFeedbackGenerator(style: active.charge > 0.55 ? .heavy : .rigid).impactOccurred()
            scheduleReturn()
        case .igniting, .flickering, .celebrating:
            scheduleReturn()
        }
    }

    private func scheduleReturn() {
        returnTask?.cancel()
        let task = DispatchWorkItem {
            touch = nil
            loopOrigin = Date()
            returnTask = nil
        }
        returnTask = task
        DispatchQueue.main.asyncAfter(deadline: .now() + 2, execute: task)
    }

    private func accessibilityStrike() {
        returnTask?.cancel()
        touch = NeonBoltStreakTouch(mode: .igniting, day: NeonBoltStreakMotion.today, started: Date(), lastDay: NeonBoltStreakMotion.today, beganCharging: true)
        UIImpactFeedbackGenerator(style: .rigid).impactOccurred()
        scheduleReturn()
    }

    private func scenePoint(_ point: CGPoint, size: CGSize, scale: CGFloat) -> CGPoint {
        CGPoint(x: (point.x - size.width / 2) / scale + 201, y: (point.y - size.height / 2) / scale + 437)
    }

    // The same perspective camera as RN: the focus sits 20pt left of Thursday, rather than at the screen centre.
    private func cameraProjection(_ camera: CGFloat) -> ProjectionTransform {
        let zoom = pow(3.5, camera)
        let rz = -3 * camera * .pi / 180
        let rx = 14 * camera * .pi / 180
        let a = zoom * cos(rz), b = zoom * sin(rz)
        let c = -zoom * sin(rz), d = zoom * cos(rz)
        let targetX = 181 + 20 * camera, targetY = 629 - 159 * camera
        // Homography of rotateZ → rotateX → perspective, composed with the focus translations.
        let px = -sin(rx) * b / 900, py = -sin(rx) * d / 900
        var transform = CATransform3DIdentity
        transform.m11 = a + targetX * px
        transform.m12 = cos(rx) * b + targetY * px
        transform.m14 = px
        transform.m21 = c + targetX * py
        transform.m22 = cos(rx) * d + targetY * py
        transform.m24 = py
        transform.m41 = targetX - transform.m11 * 181 - transform.m21 * 629
        transform.m42 = targetY - transform.m12 * 181 - transform.m22 * 629
        transform.m44 = 1 - px * 181 - py * 629
        return ProjectionTransform(transform)
    }

    private var pinnedTime: Double? {
        #if DEBUG
        let arguments = ProcessInfo.processInfo.arguments
        if let index = arguments.firstIndex(of: "-nbs-time"), arguments.indices.contains(index + 1) {
            return Double(arguments[index + 1])
        }
        #endif
        return nil
    }

    private func isCount(_ point: CGPoint) -> Bool { (127...287).contains(point.x) && (320...460).contains(point.y) }
    private func isWeek(_ point: CGPoint) -> Bool { (16...386).contains(point.x) && (560...728).contains(point.y) }
    private func day(at x: CGFloat) -> Int { min(6, max(0, Int((x - 16) / (370 / 7)))) }
}
