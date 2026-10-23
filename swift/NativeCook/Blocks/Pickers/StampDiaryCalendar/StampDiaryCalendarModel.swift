import SwiftUI
import UIKit

/// View state of the block. The per-frame story lives in the controller (not observed); only what
/// changes the view tree is observed here.
@MainActor
@Observable
final class StampDiaryCalendarModel {
    typealias M = StampDiaryCalendarMotion

    @ObservationIgnored let controller = StampDiaryCalendarController()
    @ObservationIgnored private var selection: UISelectionFeedbackGenerator?
    @ObservationIgnored private var light: UIImpactFeedbackGenerator?
    @ObservationIgnored private var medium: UIImpactFeedbackGenerator?

    var month = M.may2026
    /// The memory the moving layer has rendered (a cell stamp hides only once its actor can show).
    var actorMemory = StampDiaryCalendarController.autoMemory
    var emptyDay = 0
    var menuOpen = false
    var paused = false

    static var clock: Double { Date().timeIntervalSinceReferenceDate }

    init() {
        controller.onActor = { [weak self] memory in
            DispatchQueue.main.async { self?.actorMemory = memory }
        }
        controller.onEmptyDay = { [weak self] day in
            DispatchQueue.main.async { self?.emptyDay = day }
        }
        controller.onHaptic = { [weak self] kind in
            DispatchQueue.main.async { self?.haptic(kind) }
        }
    }

    func haptic(_ kind: StampDiaryCalendarController.Haptic) {
        switch kind {
        case .selection:
            if selection == nil { selection = UISelectionFeedbackGenerator() }
            selection?.selectionChanged()
        case .light:
            if light == nil { light = UIImpactFeedbackGenerator(style: .light) }
            light?.impactOccurred()
        case .medium:
            if medium == nil { medium = UIImpactFeedbackGenerator(style: .medium) }
            medium?.impactOccurred()
        }
    }

    func wake() { if paused { paused = false } }

    /// Called from the timeline after a tick: stop asking for frames once the pin is at rest.
    func pauseIfResting(now: Double) {
        if !paused && !controller.isRunning(now: now) {
            DispatchQueue.main.async { [weak self] in
                guard let self, !self.controller.isRunning(now: Self.clock) else { return }
                self.paused = true
            }
        }
    }

    func configure(autoPlay: Bool, reduceMotion: Bool, screenReader: Bool) {
        controller.autoOn = autoPlay && !reduceMotion && !screenReader
        controller.quiet = reduceMotion
        controller.may = M.isMay2026(month)
        wake()
    }

    // MARK: touch (screen points in, reference points to the controller)

    func touchBegan(_ p: CGPoint, space: StampDiaryCalendarSpace) {
        controller.touchBegan(x: p.x / space.s, y: (p.y - space.oy) / space.s, now: Self.clock)
        wake()
    }

    func touchMoved(_ t: CGSize, space: StampDiaryCalendarSpace) {
        controller.touchMoved(translationX: t.width / space.s, translationY: t.height / space.s, now: Self.clock)
        wake()
    }

    func touchEnded(velocityY: CGFloat, space: StampDiaryCalendarSpace) {
        controller.touchEnded(velocityY: velocityY / space.s, now: Self.clock)
        wake()
    }

    func close() {
        controller.sendHome(now: Self.clock)
        wake()
    }

    func select(day: Int) {
        controller.select(day: day, month: month, now: Self.clock)
        wake()
    }

    // MARK: month menu

    func openMenu() {
        menuOpen = true
        haptic(.selection)
    }

    func closeMenu() { menuOpen = false }

    func go(to next: M.Month) {
        menuOpen = false
        month = next
        emptyDay = 0
        controller.may = M.isMay2026(next)
        haptic(.selection)
        wake()
    }
}
