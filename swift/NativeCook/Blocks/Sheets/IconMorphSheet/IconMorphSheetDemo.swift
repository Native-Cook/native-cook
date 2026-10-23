import SwiftUI

struct IconMorphSheetDemo: View {
    var autoPlay = true
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.displayScale) private var displayScale
    @Environment(\.scenePhase) private var scenePhase
    @State private var motion = IconMorphSheetMotion()

    init(autoPlay: Bool = true) {
        self.autoPlay = autoPlay
        _ = IconMorphSheetAssets.registered
    }

    var body: some View {
        GeometryReader { geometry in
            let insets = geometry.safeAreaInsets
            GeometryReader { full in
                let layout = IconMorphSheetLayout(width: full.size.width, height: full.size.height, top: insets.top, bottom: insets.bottom)
                TimelineView(.animation(minimumInterval: 1 / 60, paused: scenePhase != .active)) { timeline in
                    let _ = update(layout: layout, date: timeline.date)
                    let f = motion.frame
                    let icon = IconMorphSheetIcon(position: f.position, times: motion.times, bounce: f.bounce, lean: f.lean, press: f.press, reduced: reduced)
                    ZStack {
                        IconMorphSheetCanvas(layout: layout, frame: f, icon: icon, reduced: reduced, autoPlay: autoPlay, pixelRatio: displayScale)
                        IconMorphSheetTouch(motion: motion)
                    }
                    .opacity(motion.visible ? 1 : 0)
                }
            }
            .ignoresSafeArea()
        }
        .background(IconMorphSheetAssets.color(0x0E0E12).ignoresSafeArea())
        .statusBarHidden()
        .persistentSystemOverlays(.hidden)
        .onChange(of: scenePhase) { _, _ in motion.resetDate() }
    }

    private func update(layout: IconMorphSheetLayout, date: Date) {
        motion.autoPlay = autoPlay
        motion.reduced = reduced
        motion.layout = layout
        motion.advance(to: date)
    }
}

#Preview {
    IconMorphSheetDemo()
}
