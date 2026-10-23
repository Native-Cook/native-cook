import SwiftUI
import UIKit

struct StampDiaryCalendar: View {
    static let loop = 8.6

    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver
    @Environment(\.scenePhase) private var scenePhase
    @State private var model = StampDiaryCalendarModel()

    var body: some View {
        GeometryReader { proxy in
            // Full screen: GeometryReader under ignoresSafeArea reports no insets, so read them from the key window.
            let insets = StampDiaryCalendarSafeArea.insets
            let size = proxy.size
            let s = size.width / StampDiaryCalendarTheme.refWidth
            let space = StampDiaryCalendarSpace(size: size, s: s, oy: insets.top - StampDiaryCalendarTheme.refSafeTop * s)
            scene(space)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
        }
        .ignoresSafeArea()
        .background(Color(hex: 0x0B0B0B).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear { configure() }
        .onChange(of: autoPlay) { configure() }
        .onChange(of: reduceMotion) { configure() }
        .onChange(of: voiceOver) { configure() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                model.controller.resumeClock(now: StampDiaryCalendarModel.clock)
                model.wake()
            }
        }
    }

    private func configure() {
        model.configure(autoPlay: autoPlay, reduceMotion: reduceMotion, screenReader: voiceOver)
    }

    private func scene(_ space: StampDiaryCalendarSpace) -> some View {
        ZStack(alignment: .topLeading) {
            StampDiaryCalendarBackdrop(space: space, month: model.month)
            StampDiaryCalendarDays(space: space, month: model.month) { model.select(day: $0) }
            StampDiaryCalendarTouch(
                onBegan: { model.touchBegan($0, space: space) },
                onMoved: { model.touchMoved($0, space: space) },
                onEnded: { model.touchEnded(velocityY: $0, space: space) }
            )
            .frame(width: space.size.width, height: space.size.height)
            StampDiaryCalendarTitle(space: space, month: model.month) { model.openMenu() }
            frame(space)
            StampDiaryCalendarHeader(space: space, onClose: { model.close() }, onMonth: { model.openMenu() })
            StampDiaryCalendarMenu(
                space: space, month: model.month, open: model.menuOpen, reduceMotion: reduceMotion,
                onClose: { model.closeMenu() }, onPick: { model.go(to: $0) }
            )
        }
        .frame(width: space.size.width, height: space.size.height, alignment: .topLeading)
    }

    /// Everything that moves: the numbers of emptied cells and the stamps, one pose per frame.
    private func frame(_ space: StampDiaryCalendarSpace) -> some View {
        TimelineView(.animation(paused: model.paused)) { timeline in
            let now = timeline.date.timeIntervalSinceReferenceDate
            let pose = model.controller.tick(now: now)
            let _ = model.pauseIfResting(now: now)
            ZStack(alignment: .topLeading) {
                StampDiaryCalendarReveal(
                    space: space, pose: pose, month: model.month,
                    emptyDay: model.emptyDay, emptyAlpha: model.controller.emptyAlpha(at: now)
                )
                if M.isMay2026(model.month) {
                    StampDiaryCalendarMovingLayers(space: space, pose: pose, actorMemory: model.actorMemory)
                }
            }
            .frame(width: space.size.width, height: space.size.height, alignment: .topLeading)
        }
        .allowsHitTesting(false)
    }

    private typealias M = StampDiaryCalendarMotion
}

#Preview {
    StampDiaryCalendar(autoPlay: true)
}

enum StampDiaryCalendarSafeArea {
    @MainActor static var insets: EdgeInsets {
        let window = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }
        let i = window?.safeAreaInsets ?? .zero
        return EdgeInsets(top: i.top, leading: i.left, bottom: i.bottom, trailing: i.right)
    }
}
