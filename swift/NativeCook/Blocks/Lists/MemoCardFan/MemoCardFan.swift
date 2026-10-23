import CoreText
import SwiftUI
import UIKit

struct MemoCardFan: View {
    static let loop: TimeInterval = 8

    let autoPlay: Bool
    let freezeAt: Double?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var loopOrigin: Date?
    @State private var resumeTask: Task<Void, Never>?
    @State private var topOpen = 0.0
    @State private var bottomOpen = 1.0
    @State private var topPress = 0.0
    @State private var bottomPress = 0.0
    @State private var bottomPull = 0.0
    @State private var bottomSpread = 0.0
    @State private var dragStart = 0.0
    @State private var pullingPrint = false
    @State private var activeContacts = 0
    @State private var returning: MemoCardFanReturn?

    init(autoPlay: Bool = false, freezeAt: Double? = nil) {
        self.autoPlay = autoPlay
        self.freezeAt = freezeAt
        MemoCardFanFonts.register()
    }

    var body: some View {
        GeometryReader { geometry in
            TimelineView(.animation(
                minimumInterval: 1.0 / 120.0,
                paused: freezeAt != nil || reduceMotion || (loopOrigin == nil && returning == nil)
            )) { timeline in
                let time = freezeAt ?? autoplayTime(at: timeline.date)
                let returnFrame = time == nil ? returning?.sample(at: timeline.date) : nil
                // GeometryReader already measures the safe-area content; its origin supplies the top inset.
                let midY = geometry.size.height / 2

                ZStack(alignment: .topLeading) {
                    MemoCardFanColor.page
                        .ignoresSafeArea()

                    folder(
                        index: 0,
                        left: geometry.size.width / 2 + (327 - MemoCardFanLayout.pinCenterX) * MemoCardFanLayout.scale,
                        top: midY + (173 - MemoCardFanLayout.pinCenterY) * MemoCardFanLayout.scale,
                        open: time.map(MemoCardFanMotion.topOpen) ?? returnFrame?.topOpen ?? topOpen,
                        press: topPress,
                        pull: 0,
                        spread: 0
                    )

                    folder(
                        index: 1,
                        left: geometry.size.width / 2 + (344 - MemoCardFanLayout.pinCenterX) * MemoCardFanLayout.scale,
                        top: midY + (691 - MemoCardFanLayout.pinCenterY) * MemoCardFanLayout.scale,
                        open: time.map(MemoCardFanMotion.bottomOpen) ?? returnFrame?.bottomOpen ?? bottomOpen,
                        press: bottomPress,
                        pull: time.map(MemoCardFanMotion.pull) ?? returnFrame?.bottomPull ?? bottomPull,
                        spread: time.map(MemoCardFanMotion.spread) ?? returnFrame?.bottomSpread ?? bottomSpread
                    )
                }
                .frame(width: geometry.size.width, height: geometry.size.height, alignment: .topLeading)
            }
        }
        .background(MemoCardFanColor.page.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear { resetAutoplay() }
        .onChange(of: autoPlay) { _, _ in resetAutoplay() }
        .onChange(of: freezeAt) { _, _ in resetAutoplay() }
        .onChange(of: reduceMotion) { _, _ in resetAutoplay() }
        .onDisappear { resumeTask?.cancel() }
    }

    @ViewBuilder
    private func folder(
        index: Int,
        left: CGFloat,
        top: CGFloat,
        open: Double,
        press: Double,
        pull: Double,
        spread: Double
    ) -> some View {
        MemoCardFanFolder(
            memo: MemoCardFanMemo.sample,
            open: open,
            press: press,
            pull: pull,
            spread: spread,
            reduceMotion: reduceMotion
        )
        .accessibilityHidden(true)
        .overlay {
            MemoCardFanTouchView(
                dragEnabled: !reduceMotion,
                accessibilityValue: open > 0.5 ? "Expanded" : "Collapsed",
                accessibilityHint: open > 0.5
                    ? "Puts the photos back in the folder"
                    : "Fans out the photos",
                onPressChanged: { pressed in setPressed(pressed, index: index) },
                onDragBegan: { beginDrag(index: index) },
                onDragChanged: { translation in updateDrag(index: index, translation: translation) },
                onDragEnded: { translation, velocity in
                    endDrag(index: index, translation: translation, velocity: velocity)
                },
                onTap: { toggle(index: index) },
                onContactBegan: contactBegan(count:),
                onContactEnded: { count, _ in contactEnded(count: count) },
                onAccessibilityEnded: queueAutoplayResume
            )
            .allowsHitTesting(freezeAt == nil)
            .accessibilityHidden(freezeAt != nil)
        }
        .offset(x: left, y: top)
    }

    private func autoplayTime(at date: Date) -> Double? {
        guard autoPlay, !reduceMotion else { return nil }

        guard let loopOrigin else { return nil }
        return date.timeIntervalSince(loopOrigin)
            .truncatingRemainder(dividingBy: Self.loop) * 1_000
    }

    private func resetAutoplay() {
        resumeTask?.cancel()
        resumeTask = nil
        returning = nil
        loopOrigin = nil
        guard activeContacts == 0 else { return }
        topOpen = 0
        bottomOpen = 1
        bottomPull = 0
        bottomSpread = 0
        loopOrigin = autoPlay && freezeAt == nil && !reduceMotion ? Date() : nil
    }

    private func stopAutoplay() {
        resumeTask?.cancel()
        resumeTask = nil
        let now = Date()
        if let returning {
            let frame = returning.sample(at: now)
            topOpen = frame.topOpen
            bottomOpen = frame.bottomOpen
            bottomPull = frame.bottomPull
            bottomSpread = frame.bottomSpread
            self.returning = nil
        } else if let time = autoplayTime(at: now) {
            topOpen = MemoCardFanMotion.topOpen(time)
            bottomOpen = MemoCardFanMotion.bottomOpen(time)
            bottomPull = MemoCardFanMotion.pull(time)
            bottomSpread = MemoCardFanMotion.spread(time)
        }
        loopOrigin = nil
    }

    private func queueAutoplayResume() {
        guard autoPlay, freezeAt == nil, !reduceMotion, activeContacts == 0 else { return }
        resumeTask?.cancel()
        resumeTask = Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(MemoCardFanMotion.resumeAfter))
            guard !Task.isCancelled, autoPlay, freezeAt == nil, !reduceMotion, activeContacts == 0 else { return }
            returning = MemoCardFanReturn(
                startedAt: .now,
                topOpen: topOpen,
                bottomOpen: bottomOpen,
                bottomPull: bottomPull,
                bottomSpread: bottomSpread
            )
            try? await Task.sleep(for: .milliseconds(MemoCardFanReturn.durationMilliseconds))
            guard !Task.isCancelled, autoPlay, freezeAt == nil, !reduceMotion, activeContacts == 0 else { return }
            topOpen = 0
            bottomOpen = 1
            bottomPull = 0
            bottomSpread = 0
            returning = nil
            loopOrigin = Date()
            resumeTask = nil
        }
    }

    private func contactBegan(count: Int) {
        let previous = activeContacts
        activeContacts += max(1, count)
        if previous == 0 { stopAutoplay() }
    }

    private func contactEnded(count: Int) {
        activeContacts = max(0, activeContacts - max(1, count))
        if activeContacts == 0 { queueAutoplayResume() }
    }

    private func setPressed(_ pressed: Bool, index: Int) {
        if pressed {
            stopAutoplay()
        }
        let duration = pressed ? MemoCardFanMotion.pressIn : MemoCardFanMotion.pressOut
        withAnimation(.linear(duration: duration / 1_000)) {
            if index == 0 {
                topPress = pressed ? 1 : 0
            } else {
                bottomPress = pressed ? 1 : 0
            }
        }
    }

    private func beginDrag(index: Int) {
        dragStart = index == 0 ? topOpen : bottomOpen
        pullingPrint = false
    }

    private func updateDrag(index: Int, translation: CGPoint) {
        if index == 1, dragStart > 0.5, pullingPrint || translation.y < 0 {
            pullingPrint = true
            var value = -translation.y / (MemoCardFanLayout.pullRise * MemoCardFanLayout.scale)
            value = MemoCardFanMotion.rubberBanded(value)
            bottomPull = value
            bottomSpread = min(1.08, max(0, value))
            return
        }

        var value = dragStart - translation.y / MemoCardFanMotion.dragRange
        value = MemoCardFanMotion.rubberBanded(value)
        if index == 0 {
            topOpen = value
        } else {
            bottomOpen = value
        }
    }

    private func endDrag(index: Int, translation: CGPoint, velocity: CGPoint) {
        if index == 1, pullingPrint {
            let normalizedVelocity = -velocity.y / (MemoCardFanLayout.pullRise * MemoCardFanLayout.scale)
            withAnimation(.interpolatingSpring(
                duration: MemoCardFanMotion.closeDuration / 1_000,
                bounce: 1 - MemoCardFanMotion.closeDamping,
                initialVelocity: normalizedVelocity
            )) {
                bottomPull = 0
                bottomSpread = 0
            }
            haptic()
            return
        }

        let current = index == 0 ? topOpen : bottomOpen
        let target: Double
        if velocity.y < -MemoCardFanMotion.dragVelocity {
            target = 1
        } else if velocity.y > MemoCardFanMotion.dragVelocity {
            target = 0
        } else {
            target = current > MemoCardFanMotion.dragCommit ? 1 : 0
        }
        let normalizedVelocity = -velocity.y / MemoCardFanMotion.dragRange
        withAnimation(.interpolatingSpring(
            duration: MemoCardFanMotion.flingDuration / 1_000,
            bounce: 1 - MemoCardFanMotion.flingDamping,
            initialVelocity: normalizedVelocity
        )) {
            if index == 0 {
                topOpen = target
            } else {
                bottomOpen = target
            }
        }
        haptic()
    }

    private func toggle(index: Int) {
        let current = index == 0 ? topOpen : bottomOpen
        let target = current > 0.5 ? 0.0 : 1.0
        let animation: Animation
        if reduceMotion {
            animation = .linear(duration: MemoCardFanMotion.reduceMotionFade / 1_000)
        } else if target > current {
            animation = .spring(
                duration: MemoCardFanMotion.openDuration / 1_000,
                bounce: 1 - MemoCardFanMotion.openDamping
            )
        } else {
            animation = .spring(
                duration: MemoCardFanMotion.closeDuration / 1_000,
                bounce: 1 - MemoCardFanMotion.closeDamping
            )
        }

        withAnimation(animation) {
            if index == 0 {
                topOpen = target
            } else {
                bottomOpen = target
            }
        }
        haptic()
    }

    private func haptic() {
        let generator = UIImpactFeedbackGenerator(style: .light)
        generator.prepare()
        generator.impactOccurred()
    }
}

private struct MemoCardFanReturn {
    static let durationMilliseconds = 320

    let startedAt: Date
    let topOpen: Double
    let bottomOpen: Double
    let bottomPull: Double
    let bottomSpread: Double

    func sample(at date: Date) -> (topOpen: Double, bottomOpen: Double, bottomPull: Double, bottomSpread: Double) {
        let linear = min(1, max(0, date.timeIntervalSince(startedAt) * 1_000 / Double(Self.durationMilliseconds)))
        let eased = 1 - pow(1 - linear, 3)
        return (
            topOpen * (1 - eased),
            bottomOpen + (1 - bottomOpen) * eased,
            bottomPull * (1 - eased),
            bottomSpread * (1 - eased)
        )
    }
}

enum MemoCardFanFonts {
    static let regular = "Inter24pt-Regular"
    static let medium = "Inter-Medium"
    static let semibold = "Inter-SemiBold"

    static let registration: Void = {
        for weight in ["Regular", "Medium", "SemiBold"] {
            guard let url = Bundle.main.url(
                forResource: "MemoCardFan-Inter-\(weight)",
                withExtension: "ttf"
            ) else {
                assertionFailure("Missing MemoCardFan Inter \(weight) font")
                continue
            }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    static func register() {
        _ = registration
    }
}
