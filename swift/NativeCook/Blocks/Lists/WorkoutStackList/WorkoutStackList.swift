import SwiftUI
import UIKit

struct WorkoutStackList: View {
    static let loop: Double = 11.75

    let autoPlay: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @State private var scene = WorkoutStackListModel.Scene.rest
    @State private var dragStart = WorkoutStackListModel.Scene.rest
    @State private var autoplaying: Bool
    @State private var loopStart = Date()
    @State private var manualTweens = ManualTweens()
    @State private var manualAnimating = false
    @State private var animationGeneration = 0
    @State private var touchTarget = TouchTarget.none
    @State private var pressedCategory: Int?
    @State private var cardPress = 1.0
    @State private var backPress = 1.0
    @State private var resumeTask: Task<Void, Never>?
    @State private var animationTask: Task<Void, Never>?
    @State private var completionTask: Task<Void, Never>?

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        _autoplaying = State(initialValue: autoPlay)
    }

    var body: some View {
        GeometryReader { proxy in
            TimelineView(.animation(paused: !(autoplaying || manualAnimating))) { timeline in
                let shown = displayedScene(at: timeline.date)
                ZStack(alignment: .topLeading) {
                    WorkoutStackListModel.Palette.background
                    WorkoutStackListRenderer(
                        layer: .list,
                        scene: shown,
                        width: proxy.size.width,
                        height: proxy.size.height,
                        reduceMotion: reduceMotion,
                        pressedCategory: pressedCategory,
                        cardPress: cardPress,
                        backPress: backPress
                    )
                    WorkoutStackListRenderer.TabBar(
                        scene: shown,
                        width: proxy.size.width,
                        height: proxy.size.height,
                        reduceTransparency: reduceTransparency
                    )
                    WorkoutStackListRenderer(
                        layer: .detail,
                        scene: shown,
                        width: proxy.size.width,
                        height: proxy.size.height,
                        reduceMotion: reduceMotion,
                        pressedCategory: pressedCategory,
                        cardPress: cardPress,
                        backPress: backPress
                    )
                    WorkoutStackListTouchView(
                        onBegan: { beginTouch(at: $0, width: proxy.size.width) },
                        onChanged: { translation, point in changeTouch(translationY: translation, point: point) },
                        onEnded: { translation, velocity, point, tapped in
                            endTouch(translationY: translation, velocityY: velocity, point: point, tapped: tapped, width: proxy.size.width)
                        }
                    )
                    .frame(width: proxy.size.width, height: proxy.size.height)
                }
                .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
                .clipped()
            }
        }
        .ignoresSafeArea()
        .background(WorkoutStackListModel.Palette.background.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            _ = WorkoutStackListAssets.registered
            if reduceMotion {
                scene = .rest
                autoplaying = false
            }
        }
        .onDisappear {
            resumeTask?.cancel()
            animationTask?.cancel()
            completionTask?.cancel()
            resumeTask = nil
            animationTask = nil
            completionTask = nil
        }
        .onChange(of: autoPlay) { _, enabled in
            resumeTask?.cancel()
            animationTask?.cancel()
            completionTask?.cancel()
            scene = .rest
            manualTweens = ManualTweens()
            manualAnimating = false
            loopStart = Date()
            autoplaying = enabled && !reduceMotion
        }
        .onChange(of: reduceMotion) { _, reduced in
            resumeTask?.cancel()
            animationTask?.cancel()
            completionTask?.cancel()
            scene = .rest
            manualTweens = ManualTweens()
            manualAnimating = false
            loopStart = Date()
            autoplaying = autoPlay && !reduced
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Workout class browser")
        .accessibilityHint("Swipe vertically through workout categories, then select a class day.")
        .accessibilityActions {
            ForEach(WorkoutStackListModel.categories) { category in
                Button(category.name) {
                    grabForAccessibility()
                    openCategory(category.id)
                    scheduleResume()
                }
            }
            Button("Back") {
                grabForAccessibility()
                back()
                scheduleResume()
            }
            ForEach(6...31, id: \.self) { day in
                Button("April \(day)") {
                    grabForAccessibility()
                    pickDay(day)
                    scheduleResume()
                }
            }
        }
    }

    private func displayedScene(at date: Date) -> WorkoutStackListModel.Scene {
        if autoplaying {
            return WorkoutStackListModel.sample(milliseconds: date.timeIntervalSince(loopStart) * 1_000)
        }
        return manualTweens.sample(base: scene, at: date)
    }

    private func beginTouch(at point: CGPoint, width: CGFloat) {
        resumeTask?.cancel()
        animationTask?.cancel()
        completionTask?.cancel()
        resumeTask = nil
        animationTask = nil
        completionTask = nil
        let current = displayedScene(at: Date())
        scene = current
        manualTweens = ManualTweens()
        manualAnimating = false
        dragStart = current
        autoplaying = false

        if isDetail(current) {
            if backHitArea.contains(point) {
                touchTarget = .back
                withAnimation(outAnimation(milliseconds: WorkoutStackListModel.Motion.pressInMilliseconds)) {
                    backPress = WorkoutStackListModel.Motion.pressScale
                }
            } else if let day = day(at: point, scene: current) {
                touchTarget = .day(day)
            } else {
                touchTarget = .detail
            }
        } else {
            touchTarget = .deck
            pressedCategory = category(at: point, scene: current, width: width)
            if pressedCategory != nil {
                withAnimation(outAnimation(milliseconds: WorkoutStackListModel.Motion.pressInMilliseconds)) {
                    cardPress = WorkoutStackListModel.Motion.pressScale
                }
            }
        }
    }

    private func grabForAccessibility() {
        resumeTask?.cancel()
        animationTask?.cancel()
        completionTask?.cancel()
        scene = displayedScene(at: Date())
        manualTweens = ManualTweens()
        manualAnimating = false
        autoplaying = false
    }

    private func changeTouch(translationY: CGFloat, point: CGPoint) {
        guard abs(translationY) >= 10 else { return }
        releasePresses()
        switch touchTarget {
        case .deck:
            let maximum = Double(WorkoutStackListModel.categories.count) / 2
            var value = dragStart.deck - Double(translationY / WorkoutStackListModel.Motion.deckDrag)
            if value < 0 { value *= 0.3 }
            if value > maximum { value = maximum + (value - maximum) * 0.3 }
            scene.deck = value
        case .detail:
            guard dragStart.month <= 0.5 else { return }
            var value = dragStart.scroll - Double(translationY / WorkoutStackListModel.Detail.collapsedShift)
            if value < 0 { value *= 0.3 }
            if value > 1 { value = 1 + (value - 1) * 0.3 }
            scene.scroll = value
        case .back, .day, .none:
            break
        }
    }

    private func endTouch(
        translationY: CGFloat,
        velocityY: CGFloat,
        point: CGPoint,
        tapped: Bool,
        width: CGFloat
    ) {
        let target = touchTarget
        touchTarget = .none
        releasePresses()

        if tapped {
            switch target {
            case .deck:
                if let category = pressedCategory ?? category(at: point, scene: scene, width: width) {
                    openCategory(category)
                }
            case .back:
                back()
            case let .day(value):
                pickDay(value)
            case .detail, .none:
                break
            }
        } else {
            switch target {
            case .deck:
                finishDeck(velocityY: velocityY)
            case .detail:
                finishDetail(translationY: translationY, velocityY: velocityY)
            case .back, .day, .none:
                break
            }
        }
        pressedCategory = nil
        scheduleResume()
    }

    private func finishDeck(velocityY: CGFloat) {
        let maximum = Double(WorkoutStackListModel.categories.count) / 2
        let projected = scene.deck - Double((velocityY * 0.2) / WorkoutStackListModel.Motion.deckDrag)
        let target = min(max(projected.rounded(), 0), maximum)
        let changed = target != dragStart.deck.rounded()
        startDeckTween(target: target, velocity: -Double(velocityY / WorkoutStackListModel.Motion.deckDrag))
        if changed { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
    }

    private func finishDetail(translationY: CGFloat, velocityY: CGFloat) {
        if dragStart.month > 0.5 {
            if translationY < -30 || velocityY < -500 {
                startMonthTween(target: 0, velocity: -Double(velocityY / 400))
            }
            return
        }
        if scene.scroll >= 0.98, translationY > 30 || velocityY > 500, dragStart.scroll >= 0.98 {
            startScrollAndMonthTween(monthVelocity: Double(velocityY / 400))
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            return
        }
        let projected = scene.scroll - Double((velocityY * 0.2) / WorkoutStackListModel.Detail.collapsedShift)
        startScrollTween(
            target: projected > 0.5 ? 1 : 0,
            velocity: -Double(velocityY / WorkoutStackListModel.Detail.collapsedShift)
        )
    }

    private func openCategory(_ index: Int) {
        scene.category = Double(index)
        scene.scroll = 0
        scene.month = 0
        scene.day = 0
        scene.leave = 0
        let milliseconds = reduceMotion
            ? WorkoutStackListModel.Motion.reducedFadeMilliseconds
            : WorkoutStackListModel.Motion.openMilliseconds
        startPinTween(property: .open, target: 1, milliseconds: milliseconds)
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    private func back() {
        let milliseconds = reduceMotion
            ? WorkoutStackListModel.Motion.reducedFadeMilliseconds
            : WorkoutStackListModel.Motion.backMilliseconds
        startPinTween(property: .leave, target: 1, milliseconds: milliseconds)
        let generationScene = scene
        completionTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: UInt64(milliseconds * 1_000_000))
            guard !autoplaying, scene.leave >= generationScene.leave else { return }
            animationTask?.cancel()
            var reset = scene
            reset.open = 0
            reset.leave = 0
            reset.scroll = 0
            reset.month = 0
            reset.day = 0
            scene = reset
            manualTweens = ManualTweens()
            manualAnimating = false
        }
    }

    private func pickDay(_ value: Int) {
        let milliseconds = reduceMotion
            ? WorkoutStackListModel.Motion.reducedFadeMilliseconds
            : WorkoutStackListModel.Motion.dayMilliseconds
        startPinTween(property: .day, target: value == 9 ? 1 : 0, milliseconds: milliseconds)
        UISelectionFeedbackGenerator().selectionChanged()
    }

    private func releasePresses() {
        withAnimation(outAnimation(milliseconds: WorkoutStackListModel.Motion.pressOutMilliseconds)) {
            cardPress = 1
            backPress = 1
        }
    }

    private func scheduleResume() {
        guard autoPlay, !reduceMotion else { return }
        resumeTask?.cancel()
        let delay = WorkoutStackListModel.Motion.resumeAfterMilliseconds
        resumeTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000))
            guard !Task.isCancelled, !reduceMotion else { return }
            let transition = scene.open > 0 ? WorkoutStackListModel.Motion.backMilliseconds : 1
            startReturnTween(leaveMilliseconds: transition)
            try? await Task.sleep(nanoseconds: UInt64(transition * 1_000_000))
            guard !Task.isCancelled, !reduceMotion else { return }
            animationTask?.cancel()
            animationGeneration += 1
            scene = .rest
            manualTweens = ManualTweens()
            manualAnimating = false
            loopStart = Date()
            autoplaying = true
        }
    }

    private func category(at point: CGPoint, scene: WorkoutStackListModel.Scene, width: CGFloat) -> Int? {
        for index in WorkoutStackListModel.categories.indices {
            let pose = WorkoutStackListModel.pose(Double(index) - scene.deck)
            guard pose.opacity > 0.01 else { continue }
            let cardWidth = WorkoutStackListModel.Deck.width * pose.scale
            let rect = CGRect(
                x: (width - WorkoutStackListModel.Deck.width) / 2 + WorkoutStackListModel.deckShift + (WorkoutStackListModel.Deck.width - cardWidth) / 2,
                y: pose.top,
                width: cardWidth,
                height: WorkoutStackListModel.Deck.height * pose.scale
            )
            if rect.contains(point) { return index }
        }
        return nil
    }

    private func day(at point: CGPoint, scene: WorkoutStackListModel.Scene) -> Int? {
        let cell = (WorkoutStackListModel.screenWidth - WorkoutStackListModel.Detail.weekSide * 2) / 7
        let calendarShift = WorkoutStackListModel.Detail.collapsedShift * CGFloat(1 - scene.scroll)
        let localY = point.y - calendarShift
        for (rowIndex, week) in WorkoutStackListModel.weeks.enumerated() {
            guard rowIndex == 0 || scene.month > 0.01 else { continue }
            let rowTop = WorkoutStackListModel.Detail.numbersTop - 12 + WorkoutStackListModel.Detail.monthRow * CGFloat(rowIndex)
            guard localY >= rowTop, localY <= rowTop + 34 else { continue }
            let column = Int((point.x - WorkoutStackListModel.Detail.weekSide) / cell)
            guard week.indices.contains(column), week[column] != 0 else { continue }
            return week[column]
        }
        return nil
    }

    private func isDetail(_ value: WorkoutStackListModel.Scene) -> Bool {
        value.open > 0.5 && value.leave < 0.5
    }

    private var backHitArea: CGRect {
        CGRect(
            x: WorkoutStackListModel.Header.side - 12,
            y: WorkoutStackListModel.Header.top - 12,
            width: WorkoutStackListModel.Header.icon + 24,
            height: WorkoutStackListModel.Header.icon + 24
        )
    }

    private func startPinTween(property: MotionProperty, target: Double, milliseconds: Double) {
        let now = Date()
        let current = displayedScene(at: now)
        scene = current
        manualTweens = ManualTweens()
        let tween = ManualTween(from: property.value(in: current), to: target, start: now, milliseconds: milliseconds, curve: .pin)
        property.set(target, in: &scene)
        manualTweens.set(tween, for: property)
        activateManualAnimation(milliseconds: milliseconds)
    }

    private func startDeckTween(target: Double, velocity: Double) {
        let now = Date()
        let current = displayedScene(at: now)
        scene = current
        scene.deck = target
        manualTweens = ManualTweens(
            deck: ManualTween(
                from: current.deck,
                to: target,
                start: now,
                milliseconds: WorkoutStackListModel.Motion.scrollSnapMilliseconds,
                curve: .spring(dampingRatio: WorkoutStackListModel.Motion.scrollDampingRatio, velocity: velocity)
            )
        )
        activateManualAnimation(milliseconds: WorkoutStackListModel.Motion.scrollSnapMilliseconds)
    }

    private func startScrollTween(target: Double, velocity: Double) {
        let now = Date()
        let current = displayedScene(at: now)
        scene = current
        scene.scroll = target
        manualTweens = ManualTweens(
            scroll: ManualTween(
                from: current.scroll,
                to: target,
                start: now,
                milliseconds: WorkoutStackListModel.Motion.scrollSnapMilliseconds,
                curve: .spring(dampingRatio: WorkoutStackListModel.Motion.scrollDampingRatio, velocity: velocity)
            )
        )
        activateManualAnimation(milliseconds: WorkoutStackListModel.Motion.scrollSnapMilliseconds)
    }

    private func startMonthTween(target: Double, velocity: Double) {
        let now = Date()
        let current = displayedScene(at: now)
        scene = current
        scene.month = target
        manualTweens = ManualTweens(
            month: ManualTween(
                from: current.month,
                to: target,
                start: now,
                milliseconds: WorkoutStackListModel.Motion.scrollSnapMilliseconds,
                curve: .spring(dampingRatio: WorkoutStackListModel.Motion.scrollDampingRatio, velocity: velocity)
            )
        )
        activateManualAnimation(milliseconds: WorkoutStackListModel.Motion.scrollSnapMilliseconds)
    }

    private func startScrollAndMonthTween(monthVelocity: Double) {
        let now = Date()
        let current = displayedScene(at: now)
        scene = current
        scene.scroll = 1
        scene.month = 1
        manualTweens = ManualTweens(
            scroll: ManualTween(from: current.scroll, to: 1, start: now, milliseconds: WorkoutStackListModel.Motion.scrollSnapMilliseconds, curve: .spring(dampingRatio: WorkoutStackListModel.Motion.scrollDampingRatio, velocity: 0)),
            month: ManualTween(from: current.month, to: 1, start: now, milliseconds: WorkoutStackListModel.Motion.scrollSnapMilliseconds, curve: .spring(dampingRatio: WorkoutStackListModel.Motion.scrollDampingRatio, velocity: monthVelocity))
        )
        activateManualAnimation(milliseconds: WorkoutStackListModel.Motion.scrollSnapMilliseconds)
    }

    private func startReturnTween(leaveMilliseconds: Double) {
        let now = Date()
        let current = displayedScene(at: now)
        scene = current
        scene.leave = 1
        scene.deck = 0
        manualTweens = ManualTweens(
            deck: ManualTween(from: current.deck, to: 0, start: now, milliseconds: WorkoutStackListModel.Motion.backMilliseconds, curve: .pin),
            leave: ManualTween(from: current.leave, to: 1, start: now, milliseconds: leaveMilliseconds, curve: .pin)
        )
        activateManualAnimation(milliseconds: max(leaveMilliseconds, WorkoutStackListModel.Motion.backMilliseconds))
    }

    private func activateManualAnimation(milliseconds: Double) {
        animationTask?.cancel()
        animationGeneration += 1
        let generation = animationGeneration
        manualAnimating = true
        animationTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: UInt64(milliseconds * 1_000_000))
            guard !Task.isCancelled, generation == animationGeneration else { return }
            manualTweens = ManualTweens()
            manualAnimating = false
        }
    }

    private func outAnimation(milliseconds: Double) -> Animation {
        .timingCurve(0.23, 1, 0.32, 1, duration: milliseconds / 1_000)
    }
}

private extension WorkoutStackList {
    enum TouchTarget: Equatable {
        case none
        case deck
        case detail
        case back
        case day(Int)
    }

    enum MotionProperty {
        case open
        case leave
        case day

        func value(in scene: WorkoutStackListModel.Scene) -> Double {
            switch self {
            case .open: scene.open
            case .leave: scene.leave
            case .day: scene.day
            }
        }

        func set(_ value: Double, in scene: inout WorkoutStackListModel.Scene) {
            switch self {
            case .open: scene.open = value
            case .leave: scene.leave = value
            case .day: scene.day = value
            }
        }
    }

    enum ManualCurve {
        case pin
        case spring(dampingRatio: Double, velocity: Double)
    }

    struct ManualTween {
        let from: Double
        let to: Double
        let start: Date
        let milliseconds: Double
        let curve: ManualCurve

        func value(at date: Date) -> Double {
            let progress = min(max(date.timeIntervalSince(start) * 1_000 / milliseconds, 0), 1)
            let eased: Double
            switch curve {
            case .pin:
                eased = WorkoutStackListModel.pin(progress)
            case let .spring(dampingRatio, velocity):
                if progress >= 1 {
                    eased = 1
                } else {
                    let frequency = 10.0
                    let damped = frequency * sqrt(max(1 - dampingRatio * dampingRatio, 0.001))
                    let envelope = exp(-dampingRatio * frequency * progress)
                    let value = 1 - envelope * (cos(damped * progress) + (dampingRatio * frequency - velocity / max(damped, 0.001)) / damped * sin(damped * progress))
                    eased = min(max(value, -0.3), 1.3)
                }
            }
            return from + (to - from) * eased
        }
    }

    struct ManualTweens {
        var deck: ManualTween?
        var open: ManualTween?
        var leave: ManualTween?
        var scroll: ManualTween?
        var month: ManualTween?
        var day: ManualTween?

        init(
            deck: ManualTween? = nil,
            open: ManualTween? = nil,
            leave: ManualTween? = nil,
            scroll: ManualTween? = nil,
            month: ManualTween? = nil,
            day: ManualTween? = nil
        ) {
            self.deck = deck
            self.open = open
            self.leave = leave
            self.scroll = scroll
            self.month = month
            self.day = day
        }

        func sample(base: WorkoutStackListModel.Scene, at date: Date) -> WorkoutStackListModel.Scene {
            var result = base
            if let deck { result.deck = deck.value(at: date) }
            if let open { result.open = open.value(at: date) }
            if let leave { result.leave = leave.value(at: date) }
            if let scroll { result.scroll = scroll.value(at: date) }
            if let month { result.month = month.value(at: date) }
            if let day { result.day = day.value(at: date) }
            return result
        }

        mutating func set(_ tween: ManualTween, for property: MotionProperty) {
            switch property {
            case .open: open = tween
            case .leave: leave = tween
            case .day: day = tween
            }
        }
    }
}
