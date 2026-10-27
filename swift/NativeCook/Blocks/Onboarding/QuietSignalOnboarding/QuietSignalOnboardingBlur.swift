import SwiftUI
import UIKit

/// `ProgressiveBlurViewport` (shared/SkiaProgressiveBlur.tsx) for SwiftUI: when the content of a scroll view is
/// taller than its viewport, the top and bottom edges blur the content that slides under them. Three
/// constant-intensity dark blurs (12, 32 and 64) are stacked and each is revealed by its own alpha mask, so the
/// blur is clear at the inner edge and strongest at the outer one. Under Reduce Transparency a 2 pt solid edge
/// stays.
enum QuietSignalOnboardingBlur {
    /// `INTENSITIES`.
    static let intensities: [CGFloat] = [12, 32, 64]
    /// `MASK_SAMPLES`.
    private static let samples = 65

    private static func smoothstep(_ value: Double, _ start: Double, _ end: Double) -> Double {
        let t = min(1, max(0, (value - start) / (end - start)))
        return t * t * (3 - 2 * t)
    }

    /// `maskAlpha(depth, band)`: the smooth partition of the edge into the three profiles, turned into
    /// source-over alpha so neighbouring profiles leave no dark gap.
    static func maskAlpha(depth: Double, band: Int) -> Double {
        let coverage = smoothstep(depth, 0, 0.38)
        let medium = smoothstep(depth, 0.18, 0.65)
        let strong = smoothstep(depth, 0.52, 1)
        let weights = [coverage * (1 - medium), coverage * medium * (1 - strong), coverage * medium * strong]
        let above = band == 0 ? weights[1] + weights[2] : band == 1 ? weights[2] : 0
        return 1 - above > 0.00001 ? min(1, weights[band] / (1 - above)) : 0
    }

    /// The mask of one profile: opaque where that profile shows. `depth` 0 is the inner edge of the strip.
    static func mask(band: Int, top: Bool) -> LinearGradient {
        let stops = (0..<samples).map { index -> Gradient.Stop in
            let depth = Double(index) / Double(samples - 1)
            return Gradient.Stop(color: Color.white.opacity(maskAlpha(depth: depth, band: band)), location: depth)
        }
        return LinearGradient(
            gradient: Gradient(stops: stops),
            startPoint: top ? .bottom : .top,
            endPoint: top ? .top : .bottom
        )
    }
}

/// A `UIVisualEffectView` blurred to a fraction of the full dark blur (what expo-blur's `intensity` does with
/// `tint="dark"`).
struct QuietSignalOnboardingBlurView: UIViewRepresentable {
    var intensity: CGFloat

    func makeUIView(context: Context) -> BlurUIView {
        BlurUIView(intensity: intensity)
    }

    func updateUIView(_ view: BlurUIView, context: Context) {
        view.intensity = intensity
    }

    final class BlurUIView: UIVisualEffectView {
        private var animator: UIViewPropertyAnimator?

        var intensity: CGFloat {
            didSet { if oldValue != intensity { rebuild() } }
        }

        init(intensity: CGFloat) {
            self.intensity = intensity
            super.init(effect: nil)
            isUserInteractionEnabled = false
            rebuild()
        }

        @available(*, unavailable)
        required init?(coder: NSCoder) {
            fatalError("QuietSignalOnboardingBlurView.BlurUIView is created in code")
        }

        override func didMoveToWindow() {
            super.didMoveToWindow()
            // The paused animator stops when the view leaves its window: build it again.
            if window != nil { rebuild() }
        }

        private func rebuild() {
            animator?.stopAnimation(true)
            effect = nil
            let next = UIViewPropertyAnimator(duration: 1, curve: .linear) { [weak self] in
                self?.effect = UIBlurEffect(style: .dark)
            }
            next.pausesOnCompletion = true
            next.fractionComplete = min(1, max(0, intensity / 100))
            animator = next
        }

        deinit {
            animator?.stopAnimation(true)
        }
    }
}

/// The blurred strip of one edge: three bands of different intensity, each under its own mask.
private struct QuietSignalOnboardingBlurEdge: View {
    var top: Bool
    var size: CGFloat
    var reduceTransparency: Bool

    var body: some View {
        if reduceTransparency {
            // The explicit non-blur fallback: a small solid edge, never a fake gradient blur.
            QuietSignalOnboardingPalette.hex(0x20231F)
                .frame(height: 2)
        } else {
            ZStack {
                ForEach(0..<3, id: \.self) { band in
                    QuietSignalOnboardingBlurView(intensity: QuietSignalOnboardingBlur.intensities[band])
                        .mask(QuietSignalOnboardingBlur.mask(band: band, top: top))
                }
            }
            .frame(height: size)
        }
    }
}

/// `ProgressiveBlurViewport` around a vertical `ScrollView`, with the scroll view's content filling the viewport
/// at least (`flexGrow: 1`) and placed by `alignment` (`justifyContent`). The scroll view bounces even when its
/// content fits, as React Native's does. The top and bottom `edgeSize` points (0 to 56, at most half the
/// viewport) blur only when the content is more than 4 pt taller than the viewport.
struct QuietSignalOnboardingScroller<Content: View>: View {
    var edgeSize: CGFloat = 30
    var alignment: Alignment = .center
    var events: QuietSignalOnboardingScrollEvents?
    @ViewBuilder var content: () -> Content

    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @State private var contentHeight: CGFloat = 0

    var body: some View {
        GeometryReader { viewport in
            let height = viewport.size.height
            let overflows = contentHeight > height + 4
            let edge = min(min(56, max(0, edgeSize)), height / 2)
            ScrollView(.vertical, showsIndicators: false) {
                content()
                    .frame(maxWidth: .infinity)
                    .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { contentHeight = $0 }
                    .frame(minHeight: height, alignment: alignment)
                    .background {
                        if let events {
                            QuietSignalOnboardingScrollWatcher(events: events)
                        }
                    }
            }
            .scrollBounceBehavior(.always)
            .overlay(alignment: .top) {
                if overflows && edge > 0 {
                    QuietSignalOnboardingBlurEdge(top: true, size: edge, reduceTransparency: reduceTransparency)
                        .allowsHitTesting(false)
                        .accessibilityHidden(true)
                }
            }
            .overlay(alignment: .bottom) {
                if overflows && edge > 0 {
                    QuietSignalOnboardingBlurEdge(top: false, size: edge, reduceTransparency: reduceTransparency)
                        .allowsHitTesting(false)
                        .accessibilityHidden(true)
                }
            }
        }
    }
}
