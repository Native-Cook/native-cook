import SwiftUI

/// The shared element flies above the list, so it lands on the new card's avatar (MoodOrbJar.tsx's
/// second canvas). Touches pass through.
struct MoodOrbJarGhostLayer: View {
    let controller: MoodOrbJarController
    let geometry: MoodOrbJarGeometry

    var body: some View {
        let ghost = controller.frame.ghost
        let scale = geometry.scale
        Canvas { context, _ in
            guard let ghost else { return }
            var orb = context
            orb.translateBy(x: ghost.x, y: ghost.y)
            orb.scaleBy(x: ghost.scale, y: ghost.scale)
            MoodOrbJarBeadPainter.draw(
                in: orb,
                radius: MoodOrbJarTokens.Layout.orb * scale,
                mood: ghost.mood
            )
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
