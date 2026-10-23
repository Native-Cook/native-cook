import Observation
import SwiftUI
import UIKit

/// Strolls' first screen and the glass magnifier that opens it into the map underneath.
struct GlassDiscOnboarding: View {
    static let loop: Double = 10

    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var model: GlassDiscOnboardingModel?
    @State private var pages: GlassDiscOnboardingPages?
    @State private var previousFrame: Date?

    var body: some View {
        GeometryReader { geometry in
            let insets = geometry.safeAreaInsets
            let layout = GlassDiscOnboardingLayout(
                width: geometry.size.width + insets.leading + insets.trailing,
                height: geometry.size.height + insets.top + insets.bottom,
                top: insets.top > 0 ? insets.top : 59,
                bottom: insets.bottom > 0 ? insets.bottom : 34
            )

            Group {
                if let model, let pages {
                    TimelineView(.animation(minimumInterval: 1 / 120, paused: !model.isAnimating)) { timeline in
                        Color.clear
                            .onChange(of: timeline.date) { _, date in
                                let delta = date.timeIntervalSince(previousFrame ?? date)
                                model.update(delta: min(max(delta, 0), 0.05))
                                previousFrame = date
                            }
                            .overlay(alignment: .topLeading) {
                                GlassDiscOnboardingScene(model: model, pages: pages)
                            }
                    }
                    .frame(width: layout.width, height: layout.height, alignment: .topLeading)
                } else {
                    Color(red: 244 / 255, green: 239 / 255, blue: 230 / 255)
                }
            }
            .frame(width: layout.width, height: layout.height, alignment: .topLeading)
            .offset(x: -insets.leading, y: -insets.top)
            .task(id: layout) { rebuild(layout: layout) }
            .onChange(of: reduceMotion) { _, _ in rebuild(layout: layout) }
        }
        .background(Color(red: 244 / 255, green: 239 / 255, blue: 230 / 255).ignoresSafeArea())
        .preferredColorScheme(.light)
    }

    @MainActor
    private func rebuild(layout: GlassDiscOnboardingLayout) {
        guard layout.width > 0, layout.height > 0 else { return }
        pages = GlassDiscOnboardingPages.make(layout: layout)
        model = GlassDiscOnboardingModel(
            layout: layout,
            autoPlay: autoPlay && !reduceMotion,
            reduceMotion: reduceMotion
        )
        previousFrame = nil
    }
}

private struct GlassDiscOnboardingScene: View {
    @Bindable var model: GlassDiscOnboardingModel
    let pages: GlassDiscOnboardingPages

    var body: some View {
        let layout = model.layout
        ZStack(alignment: .topLeading) {
            Image(uiImage: pages.intro)
                .resizable()
                .frame(width: layout.width, height: layout.height)

            Image(uiImage: pages.map)
                .resizable()
                .frame(width: layout.width, height: layout.height)
                .layerEffect(
                    ShaderLibrary.glassDiscOnboardingLens(
                        .float2(Float(layout.width), Float(layout.height)),
                        .float2(Float(model.center.x), Float(model.center.y)),
                        .float(Float(model.radius)),
                        .float(Float(model.magnification)),
                        .float(Float(model.rim)),
                        .float(1),
                        .float2(0, Float(min(0, (layout.lensY - model.center.y) * 0.9) * model.rim))
                    ),
                    maxSampleOffset: CGSize(width: layout.width, height: layout.height)
                )

            if model.route > 0.001 {
                let route = GlassDiscOnboardingPages.routePath(layout: layout).trimmedPath(from: 0, to: model.route)
                route.stroke(
                    Color.white,
                    style: StrokeStyle(lineWidth: 9, lineCap: .round, lineJoin: .round)
                )
                route.stroke(
                    Color(red: 232 / 255, green: 100 / 255, blue: 46 / 255),
                    style: StrokeStyle(lineWidth: 5, lineCap: .round, lineJoin: .round)
                )
            }

            GlassDiscOnboardingHint()
                .opacity(model.hint)
                .offset(y: 8 * (1 - model.hint))
                .position(x: layout.width / 2, y: layout.domeY - 172 - 34)

            if model.autoPlay {
                Circle()
                    .fill(Color(red: 29 / 255, green: 43 / 255, blue: 34 / 255).opacity(0.12))
                    .overlay {
                        Circle().stroke(
                            Color(red: 29 / 255, green: 43 / 255, blue: 34 / 255).opacity(0.3),
                            lineWidth: 1
                        )
                    }
                    .frame(width: 44, height: 44)
                    .scaleEffect(1 - 0.12 * model.ghostPress)
                    .opacity(model.ghostOpacity)
                    .position(model.ghost)
                    .allowsHitTesting(false)
            }

            GlassDiscOnboardingTouchView(model: model)
                .frame(width: layout.width, height: layout.height)
        }
        .frame(width: layout.width, height: layout.height, alignment: .topLeading)
        .clipped()
    }
}

private struct GlassDiscOnboardingHint: View {
    var body: some View {
        VStack(spacing: 6) {
            Path { path in
                path.move(to: CGPoint(x: 1, y: 9))
                path.addLine(to: CGPoint(x: 5, y: 5))
                path.addLine(to: CGPoint(x: 9, y: 9))
            }
            .stroke(
                Color(red: 29 / 255, green: 43 / 255, blue: 34 / 255).opacity(0.7),
                style: StrokeStyle(lineWidth: 2, lineCap: .square, lineJoin: .miter)
            )
            .frame(width: 10, height: 10)

            Text("Swipe up to explore")
                .font(.custom("Inter-SemiBold", fixedSize: 13))
                .tracking(0.1)
                .foregroundStyle(Color(red: 29 / 255, green: 43 / 255, blue: 34 / 255))
        }
        .fixedSize()
        .allowsHitTesting(false)
    }
}

@Observable @MainActor
final class GlassDiscOnboardingModel {
    enum Phase { case rest, pulling, opening, open, gathering, returning }

    let layout: GlassDiscOnboardingLayout
    let autoPlay: Bool
    let reduceMotion: Bool

    var center: CGPoint
    var radius: CGFloat = 172
    var magnification: CGFloat = 1.45
    var rim: CGFloat = 1
    var hint: CGFloat = 1
    var route: CGFloat = 0
    var ghost = CGPoint.zero
    var ghostOpacity: CGFloat = 0
    var ghostPress: CGFloat = 0
    var phase: Phase = .rest

    var isAnimating: Bool { autoPlay || phase != .rest || routeAnimation != nil }

    private struct LensState {
        let center: CGPoint
        let radius: CGFloat
        let magnification: CGFloat
        let rim: CGFloat
        let hint: CGFloat
    }

    private enum TransitionKind { case open, gather, home }
    private struct Transition {
        let kind: TransitionKind
        let start: LensState
        let duration: Double
        var elapsed = 0.0
    }
    private struct RouteAnimation {
        let from: CGFloat
        let to: CGFloat
        let duration: Double
        let delay: Double
        var elapsed = 0.0
    }

    private var transition: Transition?
    private var routeAnimation: RouteAnimation?
    private var scriptClock = 0.0
    private var scriptActive: Bool
    private var scriptPaused = false
    private var touching = false
    private var quiet = 0.0
    private var grabY: CGFloat = 0
    private var pull: CGFloat = 0
    private var backTap = false
    private var silent = true

    init(layout: GlassDiscOnboardingLayout, autoPlay: Bool, reduceMotion: Bool) {
        self.layout = layout
        self.autoPlay = autoPlay
        self.reduceMotion = reduceMotion
        center = CGPoint(x: layout.domeX, y: layout.domeY)
        scriptActive = autoPlay
        silent = autoPlay
    }

    func update(delta: Double) {
        if scriptActive {
            scriptClock += delta
            if scriptClock >= 10 { scriptClock.formTruncatingRemainder(dividingBy: 10) }
            applyScript(at: scriptClock)
            return
        }
        updateTransition(delta: delta)
        updateRoute(delta: delta)
        guard autoPlay, scriptPaused, !touching, phase != .pulling else { return }
        quiet += delta
        guard quiet >= 2 else { return }
        if phase == .open {
            back(silent: true)
        } else if phase == .rest, transition == nil {
            scriptPaused = false
            scriptActive = true
            scriptClock = 0
            silent = true
            applyScript(at: 0)
        }
    }

    func canBegin(at point: CGPoint) -> Bool {
        if autoPlay { return true }
        if phase == .open { return backRect.contains(point) }
        guard phase == .rest else { return false }
        return hypot(point.x - center.x, point.y - center.y) <= radius
    }

    func touchBegin(at point: CGPoint) {
        interruptScript()
        touching = true
        quiet = 0
        if phase == .open, backRect.contains(point) {
            backTap = true
            return
        }
        guard phase == .rest,
              hypot(point.x - center.x, point.y - center.y) <= radius else { return }
        phase = .pulling
        grabY = point.y
        pull = 0
        if !silent { UISelectionFeedbackGenerator().selectionChanged() }
    }

    func touchMove(to point: CGPoint) {
        guard phase == .pulling else { return }
        let span = max(1, grabY - layout.lensY)
        let progress = (grabY - point.y) / span
        pull = progress
        if progress <= 0 {
            center = CGPoint(x: layout.domeX, y: layout.domeY + (point.y - grabY) * 0.3)
            radius = 172
            hint = 1
            return
        }
        let amount = Self.smooth(progress)
        center = CGPoint(
            x: layout.domeX + (point.x - layout.domeX) * amount,
            y: layout.domeY + (point.y - layout.domeY) * amount
        )
        radius = 172 + (116 - 172) * amount
        hint = max(0, 1 - progress * 3)
    }

    func touchEnd(at point: CGPoint, velocityY: CGFloat, cancelled: Bool) {
        defer {
            touching = false
            quiet = 0
            backTap = false
        }
        if backTap {
            if !cancelled, backRect.contains(point) { back(silent: false) }
            return
        }
        guard phase == .pulling else { return }
        if !cancelled, pull >= 0.6 || velocityY < -650 {
            openMap(silent: false)
        } else {
            startHome()
        }
    }

    func accessibilityActivate() {
        interruptScript()
        if phase == .open { back(silent: false) }
        else if phase == .rest { openMap(silent: false) }
    }

    var accessibilityLabel: String {
        phase == .open
            ? "Back. Canal loop. 3.2 kilometres, 45 minutes, easy."
            : "Strolls. Walks worth taking, five minutes from your door. Swipe up to explore."
    }

    private var backRect: CGRect {
        CGRect(x: 16, y: layout.top + 12, width: 48, height: 48)
    }

    private func interruptScript() {
        guard autoPlay else { silent = false; return }
        let wasActive = scriptActive
        scriptActive = false
        scriptPaused = true
        silent = false
        ghostOpacity = 0
        ghostPress = 0
        guard wasActive else { return }

        let interruptedPhase = phase
        let interruptedState = currentState
        transition = nil
        routeAnimation = nil
        switch interruptedPhase {
        case .rest:
            break
        case .pulling, .returning:
            startHome()
        case .opening:
            phase = .opening
            transition = Transition(
                kind: .open,
                start: interruptedState,
                duration: reduceMotion ? 0.22 : 0.68
            )
        case .open:
            phase = .open
            if route < 1 {
                routeAnimation = RouteAnimation(
                    from: route,
                    to: 1,
                    duration: reduceMotion ? 0.2 : 0.9,
                    delay: 0
                )
            }
        case .gathering:
            phase = .gathering
            transition = Transition(
                kind: .gather,
                start: interruptedState,
                duration: reduceMotion ? 0.2 : 0.42
            )
            routeAnimation = RouteAnimation(from: route, to: 0, duration: 0.16, delay: 0)
        }
    }

    private func openMap(silent shouldBeSilent: Bool) {
        guard phase == .pulling || phase == .rest else { return }
        silent = shouldBeSilent
        phase = .opening
        if !shouldBeSilent { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        transition = Transition(
            kind: .open,
            start: currentState,
            duration: reduceMotion ? 0.22 : 0.68
        )
    }

    private func back(silent shouldBeSilent: Bool) {
        guard phase == .open else { return }
        silent = shouldBeSilent
        phase = .gathering
        if !shouldBeSilent { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        transition = Transition(
            kind: .gather,
            start: currentState,
            duration: reduceMotion ? 0.2 : 0.42
        )
        routeAnimation = RouteAnimation(from: route, to: 0, duration: 0.16, delay: 0)
    }

    private func startHome() {
        phase = .returning
        transition = Transition(
            kind: .home,
            start: currentState,
            duration: reduceMotion ? 0.2 : 0.52
        )
    }

    private var currentState: LensState {
        LensState(center: center, radius: radius, magnification: magnification, rim: rim, hint: hint)
    }

    private func updateTransition(delta: Double) {
        guard var active = transition else { return }
        active.elapsed += delta
        let unit = min(active.elapsed / active.duration, 1)
        switch active.kind {
        case .open:
            let value = Self.cubicBezier(unit, 0.77, 0, 0.175, 1)
            radius = Self.mix(active.start.radius, layout.coverRadius + 40, value)
            magnification = Self.mix(active.start.magnification, 1, value)
            rim = Self.mix(active.start.rim, 0, value)
            hint = Self.mix(active.start.hint, 0, min(1, active.elapsed / 0.2))
        case .gather:
            let value = Self.cubicBezier(unit, 0.77, 0, 0.175, 1)
            center = CGPoint(
                x: Self.mix(active.start.center.x, layout.domeX, value),
                y: Self.mix(active.start.center.y, layout.lensY, value)
            )
            radius = Self.mix(active.start.radius, 116, value)
            magnification = Self.mix(active.start.magnification, 1.45, value)
            rim = Self.mix(active.start.rim, 1, value)
        case .home:
            let value = reduceMotion ? Self.easeOut(unit) : Self.spring(unit, damping: 0.78)
            center = CGPoint(
                x: Self.mix(active.start.center.x, layout.domeX, value),
                y: Self.mix(active.start.center.y, layout.domeY, value)
            )
            radius = Self.mix(active.start.radius, 172, value)
            magnification = Self.mix(active.start.magnification, 1.45, value)
            rim = Self.mix(active.start.rim, 1, value)
            let hintUnit = max(0, (active.elapsed - 0.2) / 0.2)
            hint = Self.mix(active.start.hint, 1, Self.easeOut(min(1, hintUnit)))
        }
        if unit < 1 {
            transition = active
            return
        }
        transition = nil
        switch active.kind {
        case .open:
            phase = .open
            routeAnimation = RouteAnimation(from: route, to: 1, duration: reduceMotion ? 0.2 : 0.9, delay: 0.12)
        case .gather:
            startHome()
        case .home:
            phase = .rest
            center = CGPoint(x: layout.domeX, y: layout.domeY)
            radius = 172
            magnification = 1.45
            rim = 1
            hint = 1
        }
    }

    private func updateRoute(delta: Double) {
        guard var active = routeAnimation else { return }
        active.elapsed += delta
        if active.elapsed >= active.delay {
            let unit = min((active.elapsed - active.delay) / active.duration, 1)
            let value = Self.cubicBezier(unit, 0.77, 0, 0.175, 1)
            route = Self.mix(active.from, active.to, value)
            if unit >= 1 { routeAnimation = nil; return }
        }
        routeAnimation = active
    }

    private func applyScript(at time: Double) {
        let width = layout.width
        let height = layout.height
        transition = nil
        routeAnimation = nil
        silent = true

        ghost = CGPoint(x: width * 0.5, y: height * 0.9)
        ghostOpacity = time < 0.8 ? 0 : time < 0.95 ? CGFloat((time - 0.8) / 0.15) : 1
        ghostPress = time < 0.95 ? 0 : time < 1.1 ? CGFloat((time - 0.95) / 0.15) : 1

        if time < 0.95 {
            setRest()
            return
        }

        let path: [(Double, CGFloat, CGFloat)] = [
            (1.05, 0.5, 0.9),
            (2.3, 0.5, 0.5),
            (2.75, 0.62, 0.42),
            (3.05, 0.56, 0.46),
        ]
        if time < 3.15 {
            phase = .pulling
            let point = Self.pathPoint(time: time, path: path, width: width, height: height)
            ghost = point
            let grab = height * 0.9
            let progress = (grab - point.y) / max(1, grab - layout.lensY)
            let amount = Self.smooth(progress)
            center = CGPoint(
                x: layout.domeX + (point.x - layout.domeX) * amount,
                y: layout.domeY + (point.y - layout.domeY) * amount
            )
            radius = 172 + (116 - 172) * amount
            magnification = 1.45
            rim = 1
            hint = max(0, 1 - progress * 3)
            route = 0
            return
        }

        let releasePoint = CGPoint(x: width * 0.56, y: height * 0.46)
        let grab = height * 0.9
        let progress = (grab - releasePoint.y) / max(1, grab - layout.lensY)
        let amount = Self.smooth(progress)
        let releaseCenter = CGPoint(
            x: layout.domeX + (releasePoint.x - layout.domeX) * amount,
            y: layout.domeY + (releasePoint.y - layout.domeY) * amount
        )
        let releaseRadius = 172 + (116 - 172) * amount

        ghostPress = time < 3.3 ? CGFloat(max(0, 1 - (time - 3.15) / 0.15)) : 0
        ghostOpacity = time < 3.3 ? CGFloat(max(0, 1 - (time - 3.15) / 0.15)) : 0

        if time < 3.83 {
            phase = .opening
            let value = Self.cubicBezier((time - 3.15) / 0.68, 0.77, 0, 0.175, 1)
            center = releaseCenter
            radius = Self.mix(releaseRadius, layout.coverRadius + 40, value)
            magnification = Self.mix(1.45, 1, value)
            rim = 1 - value
            hint = 0
            route = 0
            return
        }

        if time < 6.7 {
            phase = .open
            center = releaseCenter
            radius = layout.coverRadius + 40
            magnification = 1
            rim = 0
            hint = 0
            route = CGFloat(Self.cubicBezier(min(max((time - 3.95) / 0.9, 0), 1), 0.77, 0, 0.175, 1))
            return
        }

        ghost = CGPoint(x: 40, y: layout.top + 36)
        ghostOpacity = time < 6.9 ? CGFloat(min(1, (time - 6.7) / 0.15)) : CGFloat(max(0, 1 - (time - 6.96) / 0.15))
        ghostPress = time < 6.78 ? 0 : time < 6.88 ? CGFloat((time - 6.78) / 0.1) : CGFloat(max(0, 1 - (time - 6.9) / 0.15))

        if time < 6.9 {
            phase = .open
            center = releaseCenter
            radius = layout.coverRadius + 40
            magnification = 1
            rim = 0
            hint = 0
            route = 1
            return
        }

        if time < 7.32 {
            phase = .gathering
            let value = Self.cubicBezier((time - 6.9) / 0.42, 0.77, 0, 0.175, 1)
            center = CGPoint(
                x: Self.mix(releaseCenter.x, layout.domeX, value),
                y: Self.mix(releaseCenter.y, layout.lensY, value)
            )
            radius = Self.mix(layout.coverRadius + 40, 116, value)
            magnification = Self.mix(1, 1.45, value)
            rim = value
            hint = 0
            route = CGFloat(1 - Self.easeOut(min(1, (time - 6.9) / 0.16)))
            return
        }

        if time < 7.84 {
            phase = .returning
            let value = Self.spring((time - 7.32) / 0.52, damping: 0.78)
            center = CGPoint(x: layout.domeX, y: Self.mix(layout.lensY, layout.domeY, value))
            radius = Self.mix(116, 172, value)
            magnification = 1.45
            rim = 1
            hint = CGFloat(Self.easeOut(min(max((time - 7.52) / 0.2, 0), 1)))
            route = 0
            return
        }

        setRest()
    }

    private func setRest() {
        phase = .rest
        center = CGPoint(x: layout.domeX, y: layout.domeY)
        radius = 172
        magnification = 1.45
        rim = 1
        hint = 1
        route = 0
    }

    private static func pathPoint(
        time: Double,
        path: [(Double, CGFloat, CGFloat)],
        width: CGFloat,
        height: CGFloat
    ) -> CGPoint {
        if time <= path[0].0 { return CGPoint(x: path[0].1 * width, y: path[0].2 * height) }
        for index in 0..<(path.count - 1) where time <= path[index + 1].0 {
            let first = path[index]
            let next = path[index + 1]
            let unit = (time - first.0) / (next.0 - first.0)
            let value = (1 - cos(.pi * unit)) / 2
            return CGPoint(
                x: mix(first.1, next.1, value) * width,
                y: mix(first.2, next.2, value) * height
            )
        }
        let last = path[path.count - 1]
        return CGPoint(x: last.1 * width, y: last.2 * height)
    }

    private static func smooth(_ value: CGFloat) -> CGFloat {
        let unit = min(max(value, 0), 1)
        return unit * unit * (3 - 2 * unit)
    }

    private static func easeOut(_ value: Double) -> Double {
        cubicBezier(value, 0.23, 1, 0.32, 1)
    }

    private static func spring(_ value: Double, damping: Double) -> Double {
        let unit = min(max(value, 0), 1)
        if unit >= 1 { return 1 }
        let decay = exp(-damping * 8 * unit)
        return 1 - decay * cos(10 * sqrt(max(0.05, 1 - damping * damping)) * unit)
    }

    private static func cubicBezier(_ value: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        let target = min(max(value, 0), 1)
        var low = 0.0
        var high = 1.0
        for _ in 0..<14 {
            let mid = (low + high) / 2
            if bezier(mid, x1, x2) < target { low = mid } else { high = mid }
        }
        return bezier((low + high) / 2, y1, y2)
    }

    private static func bezier(_ time: Double, _ first: Double, _ second: Double) -> Double {
        let inverse = 1 - time
        return 3 * inverse * inverse * time * first + 3 * inverse * time * time * second + time * time * time
    }

    private static func mix(_ from: CGFloat, _ to: CGFloat, _ value: Double) -> CGFloat {
        from + (to - from) * CGFloat(value)
    }
}

struct GlassDiscOnboardingTouchView: UIViewRepresentable {
    let model: GlassDiscOnboardingModel

    func makeUIView(context: Context) -> GlassDiscOnboardingTouchSurface {
        GlassDiscOnboardingTouchSurface(model: model)
    }

    func updateUIView(_ view: GlassDiscOnboardingTouchSurface, context: Context) {
        view.model = model
        view.accessibilityLabel = model.accessibilityLabel
    }
}

@MainActor
final class GlassDiscOnboardingTouchSurface: UIView, UIGestureRecognizerDelegate {
    var model: GlassDiscOnboardingModel
    private var previous = CGPoint.zero
    private var previousTime = 0.0
    private var velocityY: CGFloat = 0

    init(model: GlassDiscOnboardingModel) {
        self.model = model
        super.init(frame: .zero)
        backgroundColor = .clear
        isAccessibilityElement = true
        accessibilityTraits = .button
        accessibilityLabel = model.accessibilityLabel

        let gesture = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
        gesture.minimumPressDuration = 0
        gesture.allowableMovement = .greatestFiniteMagnitude
        gesture.cancelsTouchesInView = false
        gesture.delaysTouchesBegan = false
        gesture.delegate = self
        addGestureRecognizer(gesture)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { nil }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        model.canBegin(at: touch.location(in: self))
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool { true }

    override func accessibilityActivate() -> Bool {
        model.accessibilityActivate()
        return true
    }

    @objc private func handle(_ gesture: UILongPressGestureRecognizer) {
        let point = gesture.location(in: self)
        let now = ProcessInfo.processInfo.systemUptime
        switch gesture.state {
        case .began:
            previous = point
            previousTime = now
            velocityY = 0
            model.touchBegin(at: point)
        case .changed:
            let elapsed = now - previousTime
            if elapsed > 0.008 { velocityY = (point.y - previous.y) / elapsed }
            previous = point
            previousTime = now
            model.touchMove(to: point)
        case .ended, .cancelled, .failed:
            let elapsed = now - previousTime
            if elapsed > 0.008, abs(point.y - previous.y) > 0.01 {
                velocityY = (point.y - previous.y) / elapsed
            }
            if elapsed > 0.1 { velocityY = 0 }
            model.touchEnd(at: point, velocityY: velocityY, cancelled: gesture.state != .ended)
        default:
            break
        }
    }
}
