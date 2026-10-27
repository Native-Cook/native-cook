import SwiftUI
import UIKit

/// `ProgressiveBlurViewport` (shared/SkiaProgressiveBlur.tsx, `tint="dark"`, `edgeSize={28}`) for
/// SwiftUI: when the content of a scroll view is taller than its viewport, the top and bottom
/// edges blur what slides under them, a little at the inner edge and strongly at the outer one.
///
/// Three live blurs (intensity 12, 32 and 64 of `expo-blur`'s 0 to 100) are each shown through a
/// gradient mask whose alpha follows `maskAlpha` of the React Native helper. With Reduce
/// Transparency it falls back to a plain 2 pt strip.
enum CipherSeamBlur {
    /// `INTENSITIES`.
    static let intensities: [CGFloat] = [12, 32, 64]
    /// `MASK_SAMPLES`.
    private static let samples = 65

    /// `maskAlpha(depth, band)`: a smooth partition between the three blur strengths, turned into
    /// source-over alpha ordered low to high so neighbouring bands leave no dark gap.
    static func maskAlpha(depth: Double, band: Int) -> Double {
        let coverage = CipherSeamMath.smoothstep(depth, 0, 0.38)
        let medium = CipherSeamMath.smoothstep(depth, 0.18, 0.65)
        let strong = CipherSeamMath.smoothstep(depth, 0.52, 1)
        let weights = [coverage * (1 - medium), coverage * medium * (1 - strong), coverage * medium * strong]
        let above = band == 0 ? weights[1] + weights[2] : band == 1 ? weights[2] : 0
        return 1 - above > 0.00001 ? min(1, weights[band] / (1 - above)) : 0
    }

    /// The mask of one band: opaque where that band shows. Depth 0 is the edge facing the
    /// content, 1 the edge of the viewport.
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

/// A `UIVisualEffectView` blurred to a fraction of the full dark blur: what `expo-blur`'s
/// `intensity` does on iOS (a blur effect animation paused part-way).
struct CipherSeamBackdropBlur: UIViewRepresentable {
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
            didSet {
                if oldValue != intensity { rebuild() }
            }
        }

        init(intensity: CGFloat) {
            self.intensity = intensity
            super.init(effect: nil)
            isUserInteractionEnabled = false
            rebuild()
        }

        @available(*, unavailable)
        required init?(coder: NSCoder) {
            fatalError("CipherSeamBackdropBlur.BlurUIView is created in code")
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

/// The blurred strips of one edge: three bands of different intensity, each under its own mask.
struct CipherSeamBlurEdge: View {
    let top: Bool
    let size: CGFloat
    let reduceTransparency: Bool

    var body: some View {
        if reduceTransparency {
            // The explicit non-blur fallback: a small solid edge, never a fake gradient blur.
            CipherSeamPalette.rgb(0x20231F)
                .frame(height: 2)
        } else {
            ZStack {
                ForEach(0..<3, id: \.self) { band in
                    CipherSeamBackdropBlur(intensity: CipherSeamBlur.intensities[band])
                        .mask(CipherSeamBlur.mask(band: band, top: top))
                }
            }
            .frame(height: size)
        }
    }
}

/// Puts the blurred edges over a scroll view whose content is taller than its viewport
/// (`contentHeight > viewportHeight + 4`; both in points).
struct CipherSeamBlurViewport: ViewModifier {
    let enabled: Bool
    let contentHeight: CGFloat
    let viewportHeight: CGFloat
    /// `edgeSize`, clamped to 0 to 56 and to half the viewport.
    var edgeSize: CGFloat = 28

    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    func body(content: Content) -> some View {
        let overflows = contentHeight > viewportHeight + 4
        let edge = min(min(56, max(0, edgeSize)), viewportHeight / 2)
        let visible = enabled && overflows && edge > 0
        content
            .overlay(alignment: .top) {
                if visible {
                    CipherSeamBlurEdge(top: true, size: edge, reduceTransparency: reduceTransparency)
                        .allowsHitTesting(false)
                        .accessibilityHidden(true)
                }
            }
            .overlay(alignment: .bottom) {
                if visible {
                    CipherSeamBlurEdge(top: false, size: edge, reduceTransparency: reduceTransparency)
                        .allowsHitTesting(false)
                        .accessibilityHidden(true)
                }
            }
    }
}

extension View {
    func cipherSeamBlurViewport(enabled: Bool, contentHeight: CGFloat, viewportHeight: CGFloat) -> some View {
        modifier(CipherSeamBlurViewport(enabled: enabled, contentHeight: contentHeight, viewportHeight: viewportHeight))
    }
}
