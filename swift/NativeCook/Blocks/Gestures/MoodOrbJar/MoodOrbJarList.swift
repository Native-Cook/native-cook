import SwiftUI

/// The source's separate cards for the newest three entries. Rows fade in from above, fade out and
/// slide to their places; the newest row grows out of the flying orb and has no entrance of its own.
struct MoodOrbJarList: View {
    typealias Layout = MoodOrbJarTokens.Layout
    typealias Motion = MoodOrbJarTokens.Motion

    let controller: MoodOrbJarController
    let geometry: MoodOrbJarGeometry
    /// False until the screen has appeared, so the first rows fade in as they do in Reanimated.
    let isShown: Bool

    var body: some View {
        let entries = controller.entries
        let visible = Array(entries.prefix(3))
        let width = geometry.width - Layout.side * geometry.scale * 2
        VStack(alignment: .leading, spacing: Layout.entryGap) {
            ForEach(Array(visible.enumerated()), id: \.element.id) { _, entry in
                if isShown {
                    MoodOrbJarEntryRow(controller: controller, entry: entry, width: width)
                        .transition(transition(for: entry))
                }
            }
        }
        .frame(width: width, alignment: .topLeading)
        .offset(x: Layout.side * geometry.scale, y: geometry.rowY)
    }

    private func transition(for entry: MoodOrbJarEntry) -> AnyTransition {
        guard !controller.reducedMotion else { return .identity }
        let curve = { (duration: Double) in Animation.timingCurve(0.455, 0.03, 0.515, 0.955, duration: duration) }
        let exit = AnyTransition.opacity.animation(curve(Motion.rowExit))
        if entry.isFresh {
            return .asymmetric(insertion: .identity, removal: exit)
        }
        let enter = AnyTransition.opacity.combined(with: .offset(y: -Motion.rowFadeOffset))
            .animation(curve(Motion.rowEnter))
        return .asymmetric(insertion: enter, removal: exit)
    }
}
