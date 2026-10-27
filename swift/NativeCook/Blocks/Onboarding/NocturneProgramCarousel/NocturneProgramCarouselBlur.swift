import SwiftUI
import UIKit

/// `ProgressiveBlurViewport` of the React Native file with `tint="dark"` and `edgeSize={30}`: a
/// scroll view whose top and bottom 30 pt blur what slides under them, a little at the inner edge
/// and strongly at the outer. The edges only show when the content is taller than the viewport
/// (`onlyWhenOverflow`, 4 pt of slack).
///
/// `content` receives the viewport's size, which the scroll content needs to fill the viewport
/// (`flexGrow: 1`) and to centre itself in it.
struct NocturneProgramCarouselViewport<Content: View>: View {
    /// `enabled`.
    var enabled = true
    /// `showsVerticalScrollIndicator`.
    var showsIndicators = true
    @ViewBuilder var content: (CGSize) -> Content

    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @State private var contentHeight: CGFloat = 0

    /// `edgeSize={30}`, clamped to half the viewport.
    private static var edgeSize: CGFloat { 30 }

    var body: some View {
        GeometryReader { geo in
            let overflows = contentHeight > geo.size.height + 4
            let edge = min(Self.edgeSize, geo.size.height / 2)
            // React Native's vertical scroll view always bounces; SwiftUI's default only bounces
            // when the content overflows.
            ScrollView(.vertical, showsIndicators: showsIndicators) {
                content(geo.size)
                    .background {
                        GeometryReader { inner in
                            Color.clear.preference(
                                key: NocturneProgramCarouselContentHeightKey.self,
                                value: inner.size.height
                            )
                        }
                    }
            }
            .scrollBounceBehavior(.always, axes: .vertical)
            .onPreferenceChange(NocturneProgramCarouselContentHeightKey.self) { contentHeight = $0 }
            .overlay(alignment: .top) {
                if enabled && overflows && edge > 0 {
                    NocturneProgramCarouselEdgeBlur(edge: .top, height: edge, reduceTransparency: reduceTransparency)
                }
            }
            .overlay(alignment: .bottom) {
                if enabled && overflows && edge > 0 {
                    NocturneProgramCarouselEdgeBlur(edge: .bottom, height: edge, reduceTransparency: reduceTransparency)
                }
            }
        }
    }
}

/// The height of the scroll content, to know whether it overflows the viewport.
struct NocturneProgramCarouselContentHeightKey: PreferenceKey {
    static let defaultValue: CGFloat = 0

    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = nextValue()
    }
}

/// One blurred edge of the viewport.
///
/// Three live blurs (intensity 12, 32 and 64 of `expo-blur`'s 0 to 100) are each shown through a
/// gradient mask whose alpha follows `maskAlpha` of the React Native helper, so the strength grows
/// smoothly towards the edge. With Reduce Transparency it falls back to a plain 2 pt strip.
struct NocturneProgramCarouselEdgeBlur: View {
    enum Edge {
        case top
        case bottom
    }

    let edge: Edge
    let height: CGFloat
    let reduceTransparency: Bool

    /// `INTENSITIES`.
    private static let intensities: [Double] = [12, 32, 64]
    /// `MASK_SAMPLES`.
    private static let samples = 65

    var body: some View {
        Group {
            if reduceTransparency {
                // The explicit non-blur fallback: a small solid edge, never a fake gradient blur.
                Rectangle()
                    .fill(NocturneProgramCarouselPalette.blurFallback)
                    .frame(height: 2)
            } else {
                ZStack {
                    ForEach(0..<Self.intensities.count, id: \.self) { band in
                        NocturneProgramCarouselBackdropBlur(intensity: Self.intensities[band])
                            .mask(bandMask(band))
                    }
                }
                .frame(height: height)
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    /// The gradient of one band: depth 0 at the edge facing the content, 1 at the screen's edge.
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

/// A live backdrop blur of a given strength (0 to 100), the way `expo-blur` draws the `dark` tint on
/// iOS: a `UIVisualEffectView` whose dark blur effect is paused part-way through its animation.
struct NocturneProgramCarouselBackdropBlur: UIViewRepresentable {
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
                view.effect = UIBlurEffect(style: .dark)
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
