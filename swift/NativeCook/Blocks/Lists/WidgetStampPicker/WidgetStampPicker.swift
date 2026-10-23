import CoreText
import Observation
import QuartzCore
import SwiftUI
import UIKit

struct WidgetStampSelection {
    let index: Int
    let id: String
    let label: String
}

/// A playful onboarding picker whose widget packs are browsed as postage stamps.
struct WidgetStampPicker: View {
    static let loop: Double = 11.0

    let autoPlay: Bool
    let onBack: (() -> Void)?
    let onSkip: ((WidgetStampSelection) -> Void)?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(
        autoPlay: Bool = false,
        onBack: (() -> Void)? = nil,
        onSkip: ((WidgetStampSelection) -> Void)? = nil
    ) {
        self.autoPlay = autoPlay
        self.onBack = onBack
        self.onSkip = onSkip
        WidgetStampPickerAssets.registerFonts()
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(
                width: proxy.size.width + insets.leading + insets.trailing,
                height: proxy.size.height + insets.top + insets.bottom
            )
            WidgetStampPickerScene(
                size: size,
                insets: insets,
                autoPlay: autoPlay,
                reduced: reduceMotion,
                onBack: onBack,
                onSkip: onSkip
            )
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .offset(x: -insets.leading, y: -insets.top)
        }
        .background(WidgetStampPickerPalette.background.ignoresSafeArea())
        .preferredColorScheme(.light)
    }

    private enum WidgetStampPickerPalette {
        static let background = color(0xFFFCFF)
        static let ink = color(0x161C33)
        static let backBackground = color(0xF1F5FB)
        static let skipBackground = color(0xFFE8FF)
        static let skipInk = color(0xE0129E)
        static let caption = color(0x4E4B50)
        static let hint = color(0xDCDFE7)
        static let door = color(0xE3E8F0)
        static let doorEdge = color(0xCBD3DF)
        static let blobColors = [color(0xFFA51A), color(0x26B36B), color(0xFF3FC4)]

        static func color(_ hex: UInt32, opacity: Double = 1) -> Color {
            Color(
                .sRGB,
                red: Double((hex >> 16) & 0xFF) / 255,
                green: Double((hex >> 8) & 0xFF) / 255,
                blue: Double(hex & 0xFF) / 255,
                opacity: opacity
            )
        }
    }

    struct WidgetStampPickerTone {
        let tint: UInt32
        let glow: UInt32
        let border: UInt32
        let label: UInt32
        let shade: UInt32
    }

    struct WidgetStampPickerStampItem: Identifiable {
        let id: String
        let label: String
        let imageName: String
        let tone: WidgetStampPickerTone
        let wheelIndex: Int
    }

    private enum WidgetStampPickerAssets {
        static let displayFont = "TitanOne"
        static let textFont = "Inter24pt-Regular"

        static let stamps: [WidgetStampPickerStampItem] = [
            WidgetStampPickerStampItem(
                id: "organisation",
                label: "Organisation",
                imageName: "WidgetStampPicker-art-organisation",
                tone: WidgetStampPickerTone(tint: 0xEEF9F4, glow: 0xA9DCC6, border: 0x2FB47C,
                                            label: 0x006539, shade: 0x1E8A5E),
                wheelIndex: 0
            ),
            WidgetStampPickerStampItem(
                id: "mixed",
                label: "Mixed",
                imageName: "WidgetStampPicker-art-mixed",
                tone: WidgetStampPickerTone(tint: 0xFEECE9, glow: 0xEDB9B4, border: 0xF8544D,
                                            label: 0x490000, shade: 0x4A1820),
                wheelIndex: 1
            ),
            WidgetStampPickerStampItem(
                id: "games",
                label: "Games",
                imageName: "WidgetStampPicker-art-games",
                tone: WidgetStampPickerTone(tint: 0xE8F0FE, glow: 0xB3C9F5, border: 0x304FD9,
                                            label: 0x0F35B5, shade: 0x3A5596),
                wheelIndex: 2
            ),
            WidgetStampPickerStampItem(
                id: "daily",
                label: "Daily",
                imageName: "WidgetStampPicker-art-daily",
                tone: WidgetStampPickerTone(tint: 0xFEF9E8, glow: 0xF5D9A2, border: 0xE58A00,
                                            label: 0x8F4E00, shade: 0x9A6A20),
                wheelIndex: 3
            ),
        ]

        private static let registered: Void = {
            let files = [
                "WidgetStampPicker-TitanOne-Regular",
                "WidgetStampPicker-Inter-Regular",
            ]
            for file in files {
                guard let url = Bundle.main.url(forResource: file, withExtension: "ttf") else { continue }
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }()

        static func registerFonts() {
            _ = registered
        }

        static func image(named name: String) -> Image {
            let uiImage: UIImage?
            if let image = UIImage(named: name) {
                uiImage = image
            } else if let url = Bundle.main.url(forResource: name, withExtension: "png") {
                uiImage = UIImage(contentsOfFile: url.path)
            } else {
                uiImage = nil
            }

            guard let uiImage else {
                #if DEBUG
                assertionFailure("Missing WidgetStampPicker image: \(name).png")
                #endif
                return Image(systemName: "photo")
            }
            return Image(uiImage: uiImage)
        }
    }

    struct WidgetStampPickerPose {
        let x: Double
        let rise: Double
        let tilt: Double
        let scale: Double
        let opacity: Double
        let zIndex: Double
    }

    private enum WidgetStampPickerMath {
        static let startIndex = 2
        static let count = 4
        static let stepX = 251.0
        static let wheelTable = [0.0, 0.25, 0.5, 0.75, 1.0, 1.5, 2.2]
        static let riseTable = [0.0, 9.5, 38.0, 84.0, 159.0, 300.0, 480.0]
        static let tiltTable = [0.0, 7.0, 14.0, 21.0, 31.0, 44.0, 58.0]
        static let scaleTable = [1.0, 0.875, 0.755, 0.69, 0.62, 0.55, 0.5]
        static let hideBeyond = 2.2
        static let rubber = 0.35
        static let projectSeconds = 0.2
        static let snapDurationSeconds = 0.4
        static let snapDampingRatio = 0.8
        static let loopMilliseconds = 11_000.0
        static let nudgeAt = 1_567.0
        static let nudgeItems = 0.047
        static let nudgeIn = 33.0
        static let nudgeHold = 67.0
        static let nudgeOut = 33.0
        static let tauMilliseconds = 100.0
        static let settleMilliseconds = 700.0
        static let fadeSeconds = 0.180
        static let resumeSeconds = 3.5

        struct Swipe {
            let at: Double
            let direction: Double
            let dragMilliseconds: Double
            let dragItems: Double
        }

        static let swipes = [
            Swipe(at: 2_333, direction: -1, dragMilliseconds: 100, dragItems: 0.46),
            Swipe(at: 3_167, direction: 1, dragMilliseconds: 100, dragItems: 0.43),
            Swipe(at: 4_100, direction: -1, dragMilliseconds: 100, dragItems: 0.42),
            Swipe(at: 4_867, direction: -1, dragMilliseconds: 133, dragItems: 0.35),
            Swipe(at: 7_300, direction: 1, dragMilliseconds: 100, dragItems: 0.43),
            Swipe(at: 8_200, direction: 1, dragMilliseconds: 100, dragItems: 0.43),
        ]

        static func clamp(_ value: Double, _ low: Double = 0, _ high: Double = 1) -> Double {
            max(low, min(high, value))
        }

        static func interpolate(_ value: Double, _ inputs: [Double], _ outputs: [Double]) -> Double {
            guard let first = inputs.first, let last = inputs.last, inputs.count == outputs.count else { return 0 }
            let value = clamp(value, first, last)
            for i in 1..<inputs.count where value <= inputs[i] {
                let span = inputs[i] - inputs[i - 1]
                let fraction = span > 0 ? (value - inputs[i - 1]) / span : 0
                return outputs[i - 1] + (outputs[i] - outputs[i - 1]) * fraction
            }
            return outputs.last ?? 0
        }

        static func settle(_ u: Double) -> Double {
            let k = settleMilliseconds / tauMilliseconds
            return (1 - exp(-k * u)) / (1 - exp(-k))
        }

        static func grabEase(_ u: Double) -> Double {
            0.6 * u + 0.4 * u * u
        }

        static func wheelPosition(at milliseconds: Double) -> Double {
            let nudge = milliseconds - nudgeAt
            let nudgeLength = nudgeIn + nudgeHold + nudgeOut
            if nudge > 0, nudge < nudgeLength {
                let progress: Double
                if nudge < nudgeIn {
                    progress = nudge / nudgeIn
                } else if nudge < nudgeIn + nudgeHold {
                    progress = 1
                } else {
                    progress = 1 - (nudge - nudgeIn - nudgeHold) / nudgeOut
                }
                return Double(startIndex) - nudgeItems * progress
            }

            var index = startIndex
            var position = Double(startIndex)
            for swipe in swipes {
                if swipe.at > milliseconds { break }
                let local = milliseconds - swipe.at
                let target = max(0, min(count - 1, index + Int(swipe.direction)))
                let grab = Double(index) + swipe.direction * swipe.dragItems
                if local < swipe.dragMilliseconds {
                    position = Double(index) + (grab - Double(index)) * grabEase(local / swipe.dragMilliseconds)
                } else if local < swipe.dragMilliseconds + settleMilliseconds {
                    position = grab + (Double(target) - grab) * settle((local - swipe.dragMilliseconds) / settleMilliseconds)
                } else {
                    position = Double(target)
                }
                index = target
            }
            return position
        }

        static func eyes(at milliseconds: Double) -> [Bool] {
            let blinks: [(Int, Double)] = [
                (0, 120), (2, 2_050), (0, 2_400), (2, 2_400), (1, 3_000),
                (1, 4_930), (0, 5_500), (2, 6_000), (1, 8_300), (0, 9_800), (2, 9_800),
            ]
            return (0..<3).map { blob in
                blinks.contains { index, start in
                    index == blob && milliseconds >= start && milliseconds < start + 130
                }
            }
        }
    }

    @MainActor
    @Observable
    final class WidgetStampPickerEngine {
        let autoPlay: Bool
        var reduced: Bool

        private(set) var position = Double(WidgetStampPickerMath.startIndex)
        private(set) var fade = 1.0
        private(set) var closed = [false, false, false]
        private(set) var needsFrame: Bool
        private(set) var touching = false

        private var elapsed = 0.0
        private var lastDate: Date?
        private var loopStart = 0.0
        private var autoPlaying: Bool
        private var userPaused = false
        private var userInteraction = false
        private var lastUserIndex = WidgetStampPickerMath.startIndex
        private var resumeAt: Double?
        private var recovering = false
        private var restartAt: Double?

        private var dragStart = Double(WidgetStampPickerMath.startIndex)
        private var springActive = false
        private var springTarget = 0.0
        private var springVelocity = 0.0
        private var springStiffness = 0.0
        private var springInitialEnergy = 0.0
        private var reducedFadeAge = 1.0
        private var reducedFadeTarget: Double?

        init(autoPlay: Bool, reduced: Bool) {
            self.autoPlay = autoPlay
            self.reduced = reduced
            self.autoPlaying = autoPlay && !reduced
            self.needsFrame = autoPlay && !reduced
        }

        var currentIndex: Int {
            max(0, min(WidgetStampPickerMath.count - 1, Int(position.rounded())))
        }

        func start() {
            elapsed = 0
            lastDate = nil
            loopStart = 0
            position = Double(WidgetStampPickerMath.startIndex)
            fade = 1
            reducedFadeAge = 1
            reducedFadeTarget = nil
            closed = [false, false, false]
            autoPlaying = autoPlay && !reduced
            userPaused = false
            userInteraction = false
            lastUserIndex = WidgetStampPickerMath.startIndex
            resumeAt = nil
            recovering = false
            restartAt = nil
            springActive = false
            touching = false
            needsFrame = autoPlay && !reduced
        }

        func stop() {
            lastDate = nil
            springActive = false
            touching = false
            needsFrame = false
        }

        func setReduced(_ value: Bool) {
            reduced = value
            reducedFadeAge = 1
            reducedFadeTarget = nil
            fade = 1
            if value {
                autoPlaying = false
                springActive = false
                position = Double(currentIndex)
                closed = [false, false, false]
                userPaused = false
                recovering = false
                resumeAt = nil
                restartAt = nil
            } else if autoPlay, !touching {
                autoPlaying = true
                loopStart = elapsed
            }
            needsFrame = autoPlaying || touching || springActive || reducedFadeAge < 1
        }

        func advance(to date: Date) {
            guard let previous = lastDate else {
                lastDate = date
                if autoPlaying {
                    closed = WidgetStampPickerMath.eyes(at: 0)
                }
                return
            }
            lastDate = date
            let delta = max(0, date.timeIntervalSince(previous))
            elapsed += delta

            if touching {
                needsFrame = true
                return
            }

            if userPaused {
                if springActive { advanceSpring(delta) }
                if autoPlay, !reduced, !recovering, !springActive,
                   let resumeAt, elapsed >= resumeAt {
                    recovering = true
                    self.resumeAt = nil
                    beginSpring(to: WidgetStampPickerMath.startIndex, velocity: 0)
                    restartAt = elapsed + 0.9
                }
                if recovering, let restartAt, elapsed >= restartAt {
                    userPaused = false
                    recovering = false
                    self.restartAt = nil
                    autoPlaying = autoPlay && !reduced
                    userInteraction = false
                    lastUserIndex = WidgetStampPickerMath.startIndex
                    loopStart = elapsed
                    position = Double(WidgetStampPickerMath.startIndex)
                    fade = 1
                    springActive = false
                    closed = [false, false, false]
                }
                updateReducedFade(delta)
                needsFrame = (autoPlay && !reduced) || recovering || springActive || reducedFadeAge < 1
                return
            }

            if springActive { advanceSpring(delta) }
            if autoPlaying {
                let loopMilliseconds = ((elapsed - loopStart) * 1_000)
                    .truncatingRemainder(dividingBy: WidgetStampPickerMath.loopMilliseconds)
                let scriptedPosition = WidgetStampPickerMath.wheelPosition(at: loopMilliseconds)
                if reduced {
                    let nextIndex = max(0, min(WidgetStampPickerMath.count - 1, Int(scriptedPosition.rounded())))
                    if nextIndex != currentIndex, reducedFadeTarget != Double(nextIndex) {
                        beginReducedFade(to: Double(nextIndex))
                    }
                    updateReducedFade(delta)
                } else {
                    position = scriptedPosition
                    fade = 1
                }
                closed = WidgetStampPickerMath.eyes(at: loopMilliseconds)
            } else if reduced {
                updateReducedFade(delta)
            }
            needsFrame = autoPlaying || springActive || reducedFadeAge < 1
        }

        func beginTouch() {
            lastDate = .now
            touching = true
            dragStart = position
            springActive = false
            resumeAt = nil
            recovering = false
            restartAt = nil
            userInteraction = true
            lastUserIndex = currentIndex
            if autoPlay && !reduced {
                autoPlaying = false
                userPaused = true
            }
            needsFrame = true
        }

        func endTouch() {
            touching = false
            scheduleResume()
            needsFrame = autoPlaying || userPaused || recovering || springActive || reducedFadeAge < 1
        }

        func updateDrag(translationX: Double) {
            var next = dragStart - translationX / WidgetStampPickerMath.stepX
            if next < 0 { next *= WidgetStampPickerMath.rubber }
            if next > Double(WidgetStampPickerMath.count - 1) {
                next = Double(WidgetStampPickerMath.count - 1)
                    + (next - Double(WidgetStampPickerMath.count - 1)) * WidgetStampPickerMath.rubber
            }
            position = next
            emitSelectionIfNeeded()
            needsFrame = true
        }

        func endDrag(velocityX: Double) {
            let velocity = -velocityX / WidgetStampPickerMath.stepX
            let projected = position + velocity * WidgetStampPickerMath.projectSeconds
            let target = max(0, min(WidgetStampPickerMath.count - 1, Int(projected.rounded())))
            beginSpring(to: target, velocity: velocity)
            touching = false
            scheduleResume()
            needsFrame = true
        }

        func tap(atX x: Double, width: Double) {
            let dx = x - width / 2
            if abs(dx) < 114 {
                endTouch()
                return
            }
            let direction = dx > 0 ? 1 : -1
            let target = max(0, min(WidgetStampPickerMath.count - 1, currentIndex + direction))
            beginSpring(to: target, velocity: 0)
            emitSelectionIfNeeded()
            touching = false
            scheduleResume()
            needsFrame = true
        }

        func adjust(_ direction: Int) {
            beginTouch()
            let target = max(0, min(WidgetStampPickerMath.count - 1, currentIndex + direction))
            beginSpring(to: target, velocity: 0)
            emitSelectionIfNeeded()
            endTouch()
        }

        func pose(for item: WidgetStampPickerStampItem) -> WidgetStampPickerPose {
            let distance = Double(item.wheelIndex) - position
            let absolute = abs(distance)
            let rise = WidgetStampPickerMath.interpolate(absolute, WidgetStampPickerMath.wheelTable,
                                                         WidgetStampPickerMath.riseTable)
            let tilt = WidgetStampPickerMath.interpolate(absolute, WidgetStampPickerMath.wheelTable,
                                                         WidgetStampPickerMath.tiltTable)
            let scale = WidgetStampPickerMath.interpolate(absolute, WidgetStampPickerMath.wheelTable,
                                                          WidgetStampPickerMath.scaleTable)
            let side = distance < 0 ? -1.0 : 1.0
            return WidgetStampPickerPose(
                x: distance * WidgetStampPickerMath.stepX,
                rise: rise,
                tilt: -side * tilt,
                scale: scale,
                opacity: absolute > WidgetStampPickerMath.hideBeyond ? 0 : 1,
                zIndex: (100 - absolute * 10).rounded()
            )
        }

        private func updateReducedFade(_ delta: Double) {
            guard reducedFadeAge < 1 else { return }
            let previousAge = reducedFadeAge
            reducedFadeAge = min(1, reducedFadeAge + delta / WidgetStampPickerMath.fadeSeconds)
            if previousAge < 0.5, reducedFadeAge >= 0.5, let target = reducedFadeTarget {
                position = target
                emitSelectionIfNeeded()
            }
            fade = reducedFadeAge < 0.5
                ? 1 - reducedFadeAge * 2
                : (reducedFadeAge - 0.5) * 2
            if reducedFadeAge == 1 {
                reducedFadeTarget = nil
                fade = 1
            }
        }

        private func beginReducedFade(to target: Double) {
            reducedFadeTarget = target
            reducedFadeAge = 0
            fade = 1
        }

        private func scheduleResume() {
            guard autoPlay, !reduced, userPaused else { return }
            resumeAt = elapsed + WidgetStampPickerMath.resumeSeconds
        }

        private func emitSelectionIfNeeded() {
            guard userInteraction else { return }
            let index = currentIndex
            guard index != lastUserIndex else { return }
            lastUserIndex = index
            UISelectionFeedbackGenerator().selectionChanged()
        }

        private func beginSpring(to target: Int, velocity: Double) {
            if reduced {
                beginReducedFade(to: Double(target))
                springActive = false
                return
            }
            springTarget = Double(target)
            let initialOffset = position - springTarget
            springVelocity = velocity
            if (springTarget > position && springVelocity < 0)
                || (springTarget < position && springVelocity > 0) {
                springVelocity = 0
            }
            springStiffness = durationMatchedStiffness(offset: initialOffset, velocity: springVelocity)
            springInitialEnergy = springEnergy(
                displacement: initialOffset,
                velocity: velocity,
                stiffness: springStiffness
            )
            springActive = springInitialEnergy > 0
            if !springActive { position = springTarget }
        }

        private func advanceSpring(_ delta: Double) {
            guard springActive else { return }
            let dampingRatio = WidgetStampPickerMath.snapDampingRatio
            let mass = 4.0
            let naturalFrequency = sqrt(springStiffness / mass)
            let dampedFrequency = naturalFrequency * sqrt(1 - dampingRatio * dampingRatio)
            let step = min(delta, 0.064)
            let offset = position - springTarget
            let coefficient = (springVelocity + dampingRatio * naturalFrequency * offset) / dampedFrequency
            let exponential = exp(-dampingRatio * naturalFrequency * step)
            let angle = dampedFrequency * step
            let common = offset * cos(angle) + coefficient * sin(angle)
            position = springTarget + exponential * common
            springVelocity = exponential * (
                -dampingRatio * naturalFrequency * common
                - offset * dampedFrequency * sin(angle)
                + coefficient * dampedFrequency * cos(angle)
            )
            let currentEnergy = springEnergy(
                displacement: springTarget - position,
                velocity: springVelocity,
                stiffness: springStiffness
            )
            if springInitialEnergy == 0
                || currentEnergy / springInitialEnergy <= 6e-9 {
                position = springTarget
                springVelocity = 0
                springActive = false
            }
            emitSelectionIfNeeded()
        }

        private func durationMatchedStiffness(offset: Double, velocity: Double) -> Double {
            let dampingRatio = WidgetStampPickerMath.snapDampingRatio
            let mass = 4.0
            let threshold = 6e-9
            let settlingDuration = WidgetStampPickerMath.snapDurationSeconds * 1.5

            func energyDifference(_ stiffness: Double) -> Double {
                let omega = sqrt(stiffness / mass) * dampingRatio
                let exponential = exp(-omega * settlingDuration)
                let terminalOffset = (offset + (velocity + offset * omega) * settlingDuration) * exponential
                let terminalVelocity = terminalOffset * -omega + (velocity + offset * omega) * exponential
                let initialEnergy = springEnergy(displacement: offset, velocity: velocity, stiffness: stiffness)
                let terminalEnergy = springEnergy(
                    displacement: terminalOffset,
                    velocity: terminalVelocity,
                    stiffness: stiffness
                )
                return terminalEnergy / initialEnergy - threshold
            }

            var minimum = Double.ulpOfOne
            var maximum = 8_000.0
            let direction = energyDifference(maximum) >= energyDifference(minimum) ? 1.0 : -1.0
            var current = (maximum + minimum) / 2
            let precision = threshold * 1e-3
            var iterations = 100
            while abs(energyDifference(current)) > precision, iterations > 0 {
                iterations -= 1
                if energyDifference(current) * direction < 0 {
                    minimum = current
                } else {
                    maximum = current
                }
                current = (maximum + minimum) / 2
            }
            return current
        }

        private func springEnergy(displacement: Double, velocity: Double, stiffness: Double) -> Double {
            0.5 * stiffness * displacement * displacement + 2 * velocity * velocity
        }
    }

    private struct WidgetStampPickerScene: View {
        let size: CGSize
        let insets: EdgeInsets
        let autoPlay: Bool
        let reduced: Bool
        let onBack: (() -> Void)?
        let onSkip: ((WidgetStampSelection) -> Void)?

        @Environment(\.scenePhase) private var scenePhase
        @State private var engine: WidgetStampPickerEngine
        @State private var interactionRevision = 0

        init(
            size: CGSize,
            insets: EdgeInsets,
            autoPlay: Bool,
            reduced: Bool,
            onBack: (() -> Void)?,
            onSkip: ((WidgetStampSelection) -> Void)?
        ) {
            self.size = size
            self.insets = insets
            self.autoPlay = autoPlay
            self.reduced = reduced
            self.onBack = onBack
            self.onSkip = onSkip
            _engine = State(initialValue: WidgetStampPickerEngine(autoPlay: autoPlay, reduced: reduced))
        }

        var body: some View {
            let _ = interactionRevision
            let top = insets.top + 18
            TimelineView(.animation(paused: scenePhase != .active || !engine.needsFrame)) { timeline in
                ZStack(alignment: .topLeading) {
                    WidgetStampPickerPalette.background
                    WidgetStampPickerTouchView(
                        engine: engine,
                        screenWidth: size.width,
                        onInput: { interactionRevision &+= 1 }
                    )
                    .frame(width: size.width, height: size.height)

                    wheel(top: top)
                    navigation(top: top)
                    WidgetStampPickerMascot(closed: engine.closed)
                        .frame(width: 64, height: 78)
                        .position(x: size.width / 2 - 201 + 167 + 32, y: top + 89 + 39)
                        .allowsHitTesting(false)
                    title(top: top)
                    WidgetStampPickerHint()
                        .frame(width: 50, height: 28)
                        .position(x: size.width / 2, y: top + 667.8)
                        .allowsHitTesting(false)
                    caption(top: top)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .clipped()
                .onChange(of: timeline.date, initial: true) { _, date in
                    engine.advance(to: date)
                }
            }
            .onAppear { engine.start() }
            .onDisappear { engine.stop() }
            .onChange(of: reduced) { _, value in engine.setReduced(value) }
        }

        private func wheel(top: CGFloat) -> some View {
            // Capture value poses so ForEach observes frame changes, not just a stable engine reference.
            let poses = WidgetStampPickerAssets.stamps.map { engine.pose(for: $0) }
            return ZStack {
                ForEach(Array(WidgetStampPickerAssets.stamps.enumerated()), id: \.element.id) { entry in
                    WidgetStampPickerStampView(item: entry.element, pose: poses[entry.offset])
                }
            }
            .frame(width: 320, height: 360, alignment: .center)
            .position(x: size.width / 2, y: top + 478.5)
            .opacity(engine.fade)
            .allowsHitTesting(false)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Widget pack")
            .accessibilityValue("\(WidgetStampPickerAssets.stamps[engine.currentIndex].label), \(engine.currentIndex + 1) of 4")
            .accessibilityAdjustableAction { direction in
                engine.adjust(direction == .increment ? 1 : -1)
                interactionRevision &+= 1
            }
        }

        private func navigation(top: CGFloat) -> some View {
            Group {
                Button(action: {
                    engine.beginTouch()
                    onBack?()
                    engine.endTouch()
                    interactionRevision &+= 1
                }) {
                    WidgetStampPickerBackChevron()
                        .frame(width: 14, height: 14)
                        .frame(width: 38, height: 38)
                        .background(Circle().fill(WidgetStampPickerPalette.backBackground))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Back")
                .disabled(onBack == nil)
                .frame(width: 46, height: 46)
                .position(x: 17 + 19, y: top + 4.5)

                Button(action: {
                    engine.beginTouch()
                    let item = WidgetStampPickerAssets.stamps[engine.currentIndex]
                    onSkip?(WidgetStampSelection(index: engine.currentIndex, id: item.id, label: item.label))
                    engine.endTouch()
                    interactionRevision &+= 1
                }) {
                    Text("Skip")
                        .font(.custom(WidgetStampPickerAssets.displayFont, fixedSize: 17))
                        .foregroundStyle(WidgetStampPickerPalette.skipInk)
                        .frame(width: 74, height: 39)
                        .background(Capsule().fill(WidgetStampPickerPalette.skipBackground))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Skip")
                .disabled(onSkip == nil)
                .frame(width: 82, height: 47)
                .position(x: size.width - 17 - 37, y: top + 4.5)
            }
        }

        private func title(top: CGFloat) -> some View {
            VStack(spacing: 0) {
                Text("Choose your")
                    .frame(height: 37.5)
                Text("starting widgets")
                    .frame(height: 37.5)
            }
            .font(.custom(WidgetStampPickerAssets.displayFont, fixedSize: 31.9))
            .foregroundStyle(WidgetStampPickerPalette.ink)
            .lineLimit(1)
            .frame(width: size.width, height: 75, alignment: .top)
            .scaleEffect(x: 1.125, y: 1)
            .position(x: size.width / 2, y: top + 189.5 + 37.5)
            .accessibilityAddTraits(.isHeader)
            .allowsHitTesting(false)
        }

        private func caption(top: CGFloat) -> some View {
            Text("Swipe through and pick the pack that best\nsuits you.")
                .font(.custom(WidgetStampPickerAssets.textFont, fixedSize: 18))
                .foregroundStyle(WidgetStampPickerPalette.caption)
                .multilineTextAlignment(.center)
                // Inter's native 18 pt line box is about 21.6 pt; add the measured 2.4 pt
                // leading so the two baselines land on the RN 24 pt grid.
                .lineSpacing(2.4)
                .frame(width: 360, height: 48)
                .position(x: size.width / 2, y: top + 704 + 24)
                .allowsHitTesting(false)
        }
    }

    private struct WidgetStampPickerStampView: View {
        let item: WidgetStampPickerStampItem
        let pose: WidgetStampPickerPose

        private let width: CGFloat = 228
        private let height: CGFloat = 287

        var body: some View {
            let panel: (left: CGFloat, top: CGFloat, width: CGFloat, height: CGFloat) = (33, 30, 162, 183)
            ZStack(alignment: .topLeading) {
                WidgetStampPickerStampShape()
                    .fill(WidgetStampPickerPalette.color(item.tone.tint), style: FillStyle(eoFill: true))
                    .clipShape(WidgetStampPickerStampBodyClip())
                    .shadow(color: .white, radius: 7)
                    .shadow(color: WidgetStampPickerPalette.color(item.tone.glow), radius: 7, y: 5)
                    .shadow(color: WidgetStampPickerPalette.color(item.tone.shade, opacity: 0.3), radius: 4, y: 3)
                    .blur(radius: 0.5)
                    .frame(width: width + 32, height: height + 32)
                    .offset(x: -16, y: -16)

                WidgetStampPickerAssets.image(named: item.imageName)
                    .resizable()
                    .scaledToFill()
                    .frame(width: panel.width, height: panel.height)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .stroke(WidgetStampPickerPalette.color(item.tone.border), lineWidth: 2)
                    )
                    .offset(x: panel.left, y: panel.top)

                Text(item.label)
                    .font(.custom(WidgetStampPickerAssets.displayFont, fixedSize: 27.5))
                    .foregroundStyle(WidgetStampPickerPalette.color(item.tone.label))
                    .lineLimit(1)
                    .frame(width: width, height: 38.5, alignment: .center)
                    .scaleEffect(x: 1.09, y: 1)
                    .offset(y: 243 - 27.5 * 0.7)
            }
            .frame(width: width, height: height, alignment: .topLeading)
            .scaleEffect(CGFloat(pose.scale))
            .rotationEffect(.degrees(pose.tilt))
            .offset(x: CGFloat(pose.x), y: -CGFloat(pose.rise))
            .opacity(pose.opacity)
            .zIndex(pose.zIndex)
        }
    }

    private struct WidgetStampPickerStampShape: Shape {
        private let width: CGFloat = 228
        private let height: CGFloat = 287
        private let pad: CGFloat = 16
        private let radius: CGFloat = 7.8
        private let pitch: CGFloat = 19.2

        func path(in rect: CGRect) -> Path {
            var path = Path()
            path.addRect(CGRect(x: pad, y: pad, width: width, height: height))

            let horizontalStart = (width - 10 * pitch) / 2
            for index in 0..<11 {
                let x = pad + horizontalStart + CGFloat(index) * pitch
                path.addEllipse(in: CGRect(x: x - radius, y: pad - radius,
                                           width: radius * 2, height: radius * 2))
                path.addEllipse(in: CGRect(x: x - radius, y: pad + height - radius,
                                           width: radius * 2, height: radius * 2))
            }

            let verticalStart = (height - 14 * pitch) / 2
            for index in 0..<15 {
                let y = pad + verticalStart + CGFloat(index) * pitch
                path.addEllipse(in: CGRect(x: pad - radius, y: y - radius,
                                           width: radius * 2, height: radius * 2))
                path.addEllipse(in: CGRect(x: pad + width - radius, y: y - radius,
                                           width: radius * 2, height: radius * 2))
            }
            return path
        }
    }

    private struct WidgetStampPickerStampBodyClip: Shape {
        func path(in rect: CGRect) -> Path {
            Path(CGRect(x: 16, y: 16, width: 228, height: 287))
        }
    }

    private struct WidgetStampPickerMascot: View {
        let closed: [Bool]

        var body: some View {
            Canvas { context, _ in
                var floor = Path()
                floor.move(to: CGPoint(x: 0, y: 74.5))
                floor.addLine(to: CGPoint(x: 18, y: 74.5))
                context.stroke(floor, with: .color(WidgetStampPickerPalette.color(0xEEF0F4)),
                               style: StrokeStyle(lineWidth: 1.2, lineCap: .round))

                let centers: [(CGFloat, CGFloat)] = [(52, 14), (50, 38), (51, 62)]
                for (index, center) in centers.enumerated() {
                    drawBlob(context: &context, center: center,
                             color: WidgetStampPickerPalette.blobColors[index],
                             closed: closed.indices.contains(index) && closed[index], tail: index == 1)
                }

                var door = Path()
                door.move(to: CGPoint(x: 14, y: 9))
                door.addQuadCurve(to: CGPoint(x: 19, y: 3.6), control: CGPoint(x: 14, y: 4))
                door.addLine(to: CGPoint(x: 39.5, y: 1.6))
                door.addLine(to: CGPoint(x: 41, y: 1.6))
                door.addLine(to: CGPoint(x: 41, y: 76))
                door.addLine(to: CGPoint(x: 39.5, y: 76))
                door.addLine(to: CGPoint(x: 17, y: 73.6))
                door.addQuadCurve(to: CGPoint(x: 14, y: 70), control: CGPoint(x: 14, y: 73.3))
                door.closeSubpath()
                context.fill(door, with: .color(WidgetStampPickerPalette.door))
                context.fill(Path(CGRect(x: 39.5, y: 1, width: 2, height: 75)),
                             with: .color(WidgetStampPickerPalette.doorEdge))
                context.fill(Path(ellipseIn: CGRect(x: 35.1, y: 38.9, width: 2.2, height: 2.2)),
                             with: .color(.white))
            }
        }

        private func drawBlob(context: inout GraphicsContext, center: (CGFloat, CGFloat), color: Color,
                              closed: Bool, tail: Bool) {
            let puffs: [(CGFloat, CGFloat, CGFloat)] = [(0, -6.5, 7), (6.5, -2, 6.5), (5, 6, 6.5),
                                                         (-4, 6.5, 6.5), (-6, -2, 6.5)]
            for puff in puffs {
                context.fill(Path(ellipseIn: CGRect(x: center.0 + puff.0 - puff.2,
                                                     y: center.1 + puff.1 - puff.2,
                                                     width: puff.2 * 2, height: puff.2 * 2)),
                             with: .color(color))
            }
            context.fill(Path(ellipseIn: CGRect(x: center.0 - 7.5, y: center.1 - 7.5,
                                                 width: 15, height: 15)), with: .color(color))
            if tail {
                var tailPath = Path()
                tailPath.move(to: CGPoint(x: center.0 - 6, y: center.1 + 4))
                tailPath.addLine(to: CGPoint(x: center.0 - 9, y: center.1 + 11))
                tailPath.addLine(to: CGPoint(x: center.0 - 1, y: center.1 + 7))
                tailPath.closeSubpath()
                context.fill(tailPath, with: .color(color))
            }
            if closed {
                context.fill(Path(roundedRect: CGRect(x: center.0 - 3.4, y: center.1 + 1.5,
                                                       width: 3.4, height: 1.4), cornerRadius: 0.7),
                             with: .color(.white))
                context.fill(Path(roundedRect: CGRect(x: center.0 + 2.3, y: center.1 + 1.5,
                                                       width: 3.4, height: 1.4), cornerRadius: 0.7),
                             with: .color(.white))
            } else {
                context.fill(Path(ellipseIn: CGRect(x: center.0 - 3.35, y: center.1 - 0.15,
                                                     width: 3.3, height: 4.7)), with: .color(.white))
                context.fill(Path(ellipseIn: CGRect(x: center.0 + 2.35, y: center.1 - 0.15,
                                                     width: 3.3, height: 4.7)), with: .color(.white))
            }
        }
    }

    private struct WidgetStampPickerHint: View {
        var body: some View {
            Canvas { context, _ in
                var arc = Path()
                arc.move(to: CGPoint(x: 2.5, y: 4.6))
                arc.addQuadCurve(to: CGPoint(x: 47.5, y: 4.6), control: CGPoint(x: 25, y: 22))
                context.stroke(arc, with: .color(WidgetStampPickerPalette.hint),
                               style: StrokeStyle(lineWidth: 2.4, lineCap: .round))

                var arrows = Path()
                arrows.move(to: CGPoint(x: 1.2, y: 1.4))
                arrows.addLine(to: CGPoint(x: 2.5, y: 4.6))
                arrows.addLine(to: CGPoint(x: 6, y: 4.1))
                arrows.move(to: CGPoint(x: 48.8, y: 1.4))
                arrows.addLine(to: CGPoint(x: 47.5, y: 4.6))
                arrows.addLine(to: CGPoint(x: 44, y: 4.1))
                context.stroke(arrows, with: .color(WidgetStampPickerPalette.hint),
                               style: StrokeStyle(lineWidth: 2.2, lineCap: .round, lineJoin: .round))

                context.translateBy(x: 28, y: 21)
                context.rotate(by: .degrees(-22))
                context.translateBy(x: -28, y: -21)
                context.fill(Path(roundedRect: CGRect(x: 23.4, y: 12.2, width: 3.6, height: 9), cornerRadius: 1.8),
                             with: .color(WidgetStampPickerPalette.hint))
                context.fill(Path(roundedRect: CGRect(x: 22.6, y: 18, width: 11.6, height: 8.6), cornerRadius: 3.4),
                             with: .color(WidgetStampPickerPalette.hint))
                context.fill(Path(roundedRect: CGRect(x: 20.4, y: 19.6, width: 4, height: 5), cornerRadius: 2),
                             with: .color(WidgetStampPickerPalette.hint))
            }
        }
    }

    private struct WidgetStampPickerBackChevron: View {
        var body: some View {
            Canvas { context, _ in
                var path = Path()
                path.move(to: CGPoint(x: 9, y: 1.9))
                path.addLine(to: CGPoint(x: 2.5, y: 7))
                path.addLine(to: CGPoint(x: 9, y: 12.1))
                context.stroke(path, with: .color(WidgetStampPickerPalette.ink),
                               style: StrokeStyle(lineWidth: 2.2, lineCap: .round, lineJoin: .round))
            }
        }
    }

    private struct WidgetStampPickerTouchView: UIViewRepresentable {
        let engine: WidgetStampPickerEngine
        let screenWidth: CGFloat
        let onInput: () -> Void

        func makeUIView(context: Context) -> WidgetStampPickerTouchSurface {
            WidgetStampPickerTouchSurface(engine: engine, screenWidth: screenWidth, onInput: onInput)
        }

        func updateUIView(_ view: WidgetStampPickerTouchSurface, context: Context) {
            view.engine = engine
            view.screenWidth = screenWidth
            view.onInput = onInput
        }
    }

    private final class WidgetStampPickerTouchSurface: UIView, UIGestureRecognizerDelegate {
        var engine: WidgetStampPickerEngine
        var screenWidth: CGFloat
        var onInput: () -> Void

        private var startPoint = CGPoint.zero
        private var lastPoint = CGPoint.zero
        private var lastTime = 0.0
        private var velocityX = 0.0
        private var horizontalDrag = false
        private var multiTouchCancelled = false

        init(engine: WidgetStampPickerEngine, screenWidth: CGFloat, onInput: @escaping () -> Void) {
            self.engine = engine
            self.screenWidth = screenWidth
            self.onInput = onInput
            super.init(frame: .zero)
            backgroundColor = .clear
            isOpaque = false

            let recognizer = UILongPressGestureRecognizer(target: self, action: #selector(handleGesture(_:)))
            recognizer.minimumPressDuration = 0
            recognizer.allowableMovement = .greatestFiniteMagnitude
            recognizer.cancelsTouchesInView = false
            recognizer.delegate = self
            addGestureRecognizer(recognizer)
        }

        required init?(coder: NSCoder) {
            fatalError("init(coder:) has not been implemented")
        }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                               shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
            true
        }

        @objc private func handleGesture(_ recognizer: UILongPressGestureRecognizer) {
            let point = recognizer.location(in: self)
            let now = CACurrentMediaTime()
            switch recognizer.state {
            case .began:
                startPoint = point
                lastPoint = point
                lastTime = now
                velocityX = 0
                horizontalDrag = false
                multiTouchCancelled = false
                engine.beginTouch()
                onInput()
            case .changed:
                if recognizer.numberOfTouches > 1 {
                    multiTouchCancelled = true
                    horizontalDrag = false
                    engine.endTouch()
                    onInput()
                    return
                }
                let deltaTime = max(0.001, now - lastTime)
                velocityX = Double(point.x - lastPoint.x) / deltaTime
                lastPoint = point
                lastTime = now
                let dx = point.x - startPoint.x
                let dy = point.y - startPoint.y
                if !horizontalDrag, abs(dx) > 8, abs(dy) <= 24 {
                    horizontalDrag = true
                }
                if horizontalDrag, abs(dy) <= 24 {
                    engine.updateDrag(translationX: Double(dx))
                    onInput()
                }
            case .ended, .cancelled, .failed:
                if multiTouchCancelled {
                    engine.endTouch()
                    onInput()
                    return
                }
                let dx = point.x - startPoint.x
                let dy = point.y - startPoint.y
                if horizontalDrag {
                    engine.endDrag(velocityX: velocityX)
                } else if abs(dx) < 8, abs(dy) < 8 {
                    engine.tap(atX: Double(point.x), width: Double(screenWidth))
                } else {
                    engine.endTouch()
                }
                onInput()
            default:
                break
            }
        }
    }
}
