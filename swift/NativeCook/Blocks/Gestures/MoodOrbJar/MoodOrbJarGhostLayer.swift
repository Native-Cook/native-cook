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
            let d = MoodOrbJarTokens.Layout.orb * 2 * scale * MoodOrbJarTokens.orbImageScale
            let image = context.resolve(Image(uiImage: MoodOrbJarAssets.orbs[ghost.mood]))
            var orb = context
            orb.translateBy(x: ghost.x, y: ghost.y)
            orb.scaleBy(x: ghost.scale, y: ghost.scale)
            orb.draw(image, in: CGRect(x: -d / 2, y: -d / 2, width: d, height: d))
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
