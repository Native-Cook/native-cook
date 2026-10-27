import SwiftUI
import UIKit

/// `ProgressiveBlurViewport` of the React Native block (`tint="light"`, `edgeSize={30}`): when the
/// content of a scroll view is taller than its viewport, the top and bottom 30 pt of the viewport
/// blur what slides under them, a little at the inner edge and strongly at the outer.
///
/// Three live blurs (intensity 12, 32 and 64 of `expo-blur`'s 0 to 100) are each shown through a
/// gradient mask whose alpha follows `maskAlpha` of the React Native helper, so the strength grows
/// smoothly towards the edge. With Reduce Transparency it falls back to a plain 2 pt strip.
struct SpectrumNewsCarouselEdgeBlur: View {
    enum Edge {
        case top
        case bottom
    }

    let edge: Edge
    /// The strip's height: `edgeSize`, at most half the viewport.
    let size: CGFloat
    let reduceTransparency: Bool

    /// `edgeSize={30}`.
    static let edgeSize: CGFloat = 30
    /// `INTENSITIES`.
    private static let intensities: [Double] = [12, 32, 64]
    /// `MASK_SAMPLES`.
    private static let samples = 65

    var body: some View {
        Group {
            if reduceTransparency {
                // The explicit non-blur fallback: a small solid edge, never a fake gradient blur.
                Rectangle()
                    .fill(SpectrumNewsCarouselPalette.rgb(0xE5E9DF))
                    .frame(height: 2)
            } else {
                ZStack {
                    ForEach(0..<Self.intensities.count, id: \.self) { band in
                        SpectrumNewsCarouselBackdropBlur(intensity: Self.intensities[band])
                            .mask(bandMask(band))
                    }
                }
                .frame(height: size)
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    /// The gradient of one band: depth 0 at the edge facing the content, 1 at the viewport's edge.
    private func bandMask(_ band: Int) -> some View {
        let stops = (0..<Self.samples).map { index -> Gradient.Stop in
            let depth = Double(index) / Double(Self.samples - 1)
            return Gradient.Stop(color: .white.opacity(Self.maskAlpha(depth: depth, band: band)), location: depth)
        }
        return LinearGradient(
            stops: stops,
            startPoint: edge == .top ? .bottom : .top,
            endPoint: edge == .top ? .top : .bottom
        )
    }

    private static func smoothstep(_ value: Double, _ start: Double, _ end: Double) -> Double {
        let t = max(0, min(1, (value - start) / (end - start)))
        return t * t * (3 - 2 * t)
    }

    /// `maskAlpha`: a smooth partition between the three blur strengths, turned into source-over
    /// alpha ordered low to high so there are no dark gaps where neighbours cross-fade.
    private static func maskAlpha(depth: Double, band: Int) -> Double {
        let coverage = smoothstep(depth, 0, 0.38)
        let medium = smoothstep(depth, 0.18, 0.65)
        let strong = smoothstep(depth, 0.52, 1)
        let weights = [coverage * (1 - medium), coverage * medium * (1 - strong), coverage * medium * strong]
        let above = band == 0 ? weights[1] + weights[2] : band == 1 ? weights[2] : 0
        return 1 - above > 0.00001 ? min(1, weights[band] / (1 - above)) : 0
    }
}

/// A live backdrop blur of a given strength (0 to 100), the way `expo-blur` draws it on iOS: a
/// `UIVisualEffectView` whose light blur effect is paused part-way through its animation.
struct SpectrumNewsCarouselBackdropBlur: UIViewRepresentable {
    let intensity: Double

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> UIVisualEffectView {
        let view = UIVisualEffectView(effect: nil)
        context.coordinator.attach(to: view)
        context.coordinator.apply(intensity: intensity)
        return view
    }

    func updateUIView(_ view: UIVisualEffectView, context: Context) {
        context.coordinator.apply(intensity: intensity)
    }

    static func dismantleUIView(_ view: UIVisualEffectView, coordinator: Coordinator) {
        coordinator.stop()
    }

    final class Coordinator {
        private var animator: UIViewPropertyAnimator?

        func attach(to view: UIVisualEffectView) {
            let animator = UIViewPropertyAnimator(duration: 1, curve: .linear) {
                view.effect = UIBlurEffect(style: .light)
            }
            animator.pausesOnCompletion = true
            self.animator = animator
        }

        func apply(intensity: Double) {
            animator?.fractionComplete = CGFloat(max(0.01, min(1, intensity / 100)))
        }

        func stop() {
            guard let animator else { return }
            animator.stopAnimation(false)
            animator.finishAnimation(at: .current)
            self.animator = nil
        }

        deinit {
            if let animator {
                animator.stopAnimation(false)
                animator.finishAnimation(at: .current)
            }
        }
    }
}
