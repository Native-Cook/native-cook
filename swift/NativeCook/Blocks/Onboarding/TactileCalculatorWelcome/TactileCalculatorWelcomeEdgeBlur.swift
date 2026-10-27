import SwiftUI
import UIKit

/// `ProgressiveBlurViewport` of the React Native file: the top (and, at large text sizes, the
/// bottom) edge of the scroll view frosts what slides under it, a little at the inner edge and
/// strongly at the outer one.
///
/// Three live blurs (intensity 12, 32 and 64 of `expo-blur`'s 0 to 100) are each shown through
/// a gradient mask whose alpha follows `maskAlpha` of the React Native helper, so the strength
/// grows smoothly towards the screen's edge. With Reduce Transparency it falls back to a plain
/// 2 pt strip of the tint's solid colour, never a fake gradient blur.
struct TactileCalculatorWelcomeEdgeBlur: View {
    enum Edge {
        case top
        case bottom
    }

    let edge: Edge
    /// `edgeSize`, clamped to 0 to 56 points like the React Native helper.
    let size: CGFloat
    let reduceTransparency: Bool

    /// `INTENSITIES`.
    private static let intensities: [Double] = [12, 32, 64]
    /// `MASK_SAMPLES`.
    private static let samples = 65

    var body: some View {
        let height = min(56, max(0, size))
        Group {
            if reduceTransparency {
                Rectangle()
                    .fill(TactileCalculatorWelcomePalette.blurFallback)
                    .frame(height: 2)
            } else {
                ZStack {
                    ForEach(0..<Self.intensities.count, id: \.self) { band in
                        TactileCalculatorWelcomeBandBlur(
                            intensity: Self.intensities[band],
                            alphas: Self.alphas(band: band),
                            fromBottom: edge == .top
                        )
                    }
                }
                .frame(height: height)
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    /// The mask alpha of one band at each of the `MASK_SAMPLES` depths, from the edge facing the
    /// content (depth 0) to the screen's edge (depth 1).
    private static func alphas(band: Int) -> [Double] {
        (0..<samples).map { index in
            let depth = Double(index) / Double(samples - 1)
            return maskAlpha(depth: depth, band: band)
        }
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

/// One band of the blur: a live backdrop blur of a given strength (0 to 100), drawn the way
/// `expo-blur` does on iOS, seen through a gradient mask (the `MaskedView` of the React Native
/// helper).
struct TactileCalculatorWelcomeBandBlur: UIViewRepresentable {
    let intensity: Double
    /// Mask alphas from depth 0 to depth 1.
    let alphas: [Double]
    /// Depth 0 is at the bottom of the band (the band is at the top edge) when true, at the top
    /// otherwise.
    let fromBottom: Bool

    func makeUIView(context: Context) -> TactileCalculatorWelcomeBandView {
        let view = TactileCalculatorWelcomeBandView()
        view.configure(intensity: intensity, alphas: alphas, fromBottom: fromBottom)
        return view
    }

    func updateUIView(_ view: TactileCalculatorWelcomeBandView, context: Context) {
        view.configure(intensity: intensity, alphas: alphas, fromBottom: fromBottom)
    }
}

/// A `UIVisualEffectView` whose light blur effect is paused part-way through its animation (what
/// `expo-blur` calls `intensity`), inside a view whose layer mask is the band's gradient.
final class TactileCalculatorWelcomeBandView: UIView {
    private let effectView = UIVisualEffectView(effect: nil)
    private let gradient = CAGradientLayer()
    private var animator: UIViewPropertyAnimator?
    private var intensity = -1.0

    override init(frame: CGRect) {
        super.init(frame: frame)
        isUserInteractionEnabled = false
        backgroundColor = .clear
        addSubview(effectView)
        layer.mask = gradient
        let animator = UIViewPropertyAnimator(duration: 1, curve: .linear) { [effectView] in
            effectView.effect = UIBlurEffect(style: .light)
        }
        animator.pausesOnCompletion = true
        self.animator = animator
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("TactileCalculatorWelcomeBandView is created in code")
    }

    func configure(intensity value: Double, alphas: [Double], fromBottom: Bool) {
        if intensity != value {
            intensity = value
            animator?.fractionComplete = CGFloat(max(0.01, min(1, value / 100)))
        }
        let count = max(1, alphas.count - 1)
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        gradient.colors = alphas.map { UIColor.white.withAlphaComponent(CGFloat($0)).cgColor }
        gradient.locations = alphas.indices.map { NSNumber(value: Double($0) / Double(count)) }
        gradient.startPoint = CGPoint(x: 0.5, y: fromBottom ? 1 : 0)
        gradient.endPoint = CGPoint(x: 0.5, y: fromBottom ? 0 : 1)
        CATransaction.commit()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        effectView.frame = bounds
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        gradient.frame = bounds
        CATransaction.commit()
    }

    deinit {
        if let animator {
            animator.stopAnimation(false)
            animator.finishAnimation(at: .current)
        }
    }
}
