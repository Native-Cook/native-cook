import SwiftUI
import UIKit

struct GlowToggleChecklistDemo: View {
    var body: some View {
        GlowToggleChecklist(autoPlay: true)
    }
}

/// A self-contained checklist. The gallery enables the optional demonstration finger.
struct GlowToggleChecklist: View {
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var clock = GlowToggleChecklistClock()

    var body: some View {
        GeometryReader { geometry in
            TimelineView(.animation) { timeline in
                let frame = clock.frame(at: timeline.date, autoPlay: autoPlay, still: reduceMotion)
                GlowToggleChecklistSurface(frame: frame, size: geometry.size)
                    .overlay {
                        GlowToggleChecklistInput(frame: frame, size: geometry.size) { touch in
                            clock.touch(touch)
                        }
                    }
                    .opacity(frame.opacity)
            }
        }
        .background(.black)
        .ignoresSafeArea()
        .statusBarHidden()
    }
}

private final class GlowToggleChecklistClock {
    private var last: Date?
    private var state = GlowToggleChecklistSimulation()

    func frame(at date: Date, autoPlay: Bool, still: Bool) -> GlowToggleChecklistSimulation {
        if last == nil { state = .init(autoPlay: autoPlay, still: still) }
        state.still = still
        state.clearEvents()
        state.step(dt: last.map { date.timeIntervalSince($0) } ?? 0)
        last = date
        return state
    }

    func touch(_ touch: GlowToggleChecklistTouch) {
        state.clearEvents()
        state.step(touch: touch, dt: 0)
        let events = state.events
        if events.cap { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        for i in 0..<4 {
            if events.onMask & (1 << i) != 0 { UIImpactFeedbackGenerator(style: .rigid).impactOccurred() }
            if events.offMask & (1 << i) != 0 { UIImpactFeedbackGenerator(style: .soft).impactOccurred() }
        }
        if events.success { UINotificationFeedbackGenerator().notificationOccurred(.success) }
    }
}

#Preview {
    GlowToggleChecklistDemo()
}
