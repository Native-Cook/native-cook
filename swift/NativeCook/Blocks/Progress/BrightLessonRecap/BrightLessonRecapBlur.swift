import SwiftUI
import UIKit

/// RN `ProgressiveBlurViewport` (tint light, `edgeSize` 28, top and bottom): while the content is
/// taller than the viewport, a 28 pt band at each edge blurs what scrolls under it, more strongly
/// towards the edge. It is three live backdrop blurs of increasing strength, each seen through its own
/// gradient mask, so the blur deepens without a visible step.
struct BrightLessonRecapBlurEdges: View {
    let width: CGFloat
    let height: CGFloat
    /// RN `reduceTransparency`: a thin solid edge replaces the blur.
    let solid: Bool

    /// RN `edgeSize`, clamped to half the viewport.
    private var band: CGFloat { min(28, height / 2) }

    var body: some View {
        ZStack(alignment: .topLeading) {
            edge(.top)
            edge(.bottom)
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    @ViewBuilder private func edge(_ edge: BrightLessonRecapBlurStack.Edge) -> some View {
        if solid {
            // RN: "a small solid edge, never a fake gradient blur".
            let thin = min(2, height / 2)
            BrightLessonRecapPalette.color(0xE5E9DF)
                .frame(width: width, height: thin)
                .offset(y: edge == .top ? 0 : height - thin)
        } else {
            BrightLessonRecapBlurEdge(edge: edge)
                .frame(width: width, height: band)
                .offset(y: edge == .top ? 0 : height - band)
        }
    }
}

private struct BrightLessonRecapBlurEdge: UIViewRepresentable {
    let edge: BrightLessonRecapBlurStack.Edge

    func makeUIView(context: Context) -> BrightLessonRecapBlurStack {
        BrightLessonRecapBlurStack(edge: edge)
    }

    func updateUIView(_ view: BrightLessonRecapBlurStack, context: Context) {}

    static func dismantleUIView(_ view: BrightLessonRecapBlurStack, coordinator: ()) {
        view.stopAnimators()
    }
}

/// Three `UIVisualEffectView`s (RN `INTENSITIES` 12, 32 and 64 of 100) stacked low to high, each masked
/// by a 65-stop gradient built from RN's `maskAlpha`.
final class BrightLessonRecapBlurStack: UIView {
    enum Edge {
        case top
        case bottom
    }

    private static let intensities: [CGFloat] = [12, 32, 64]
    private static let samples = 65

    private let edge: Edge
    private var effectViews: [UIVisualEffectView] = []
    private var animators: [UIViewPropertyAnimator] = []

    init(edge: Edge) {
        self.edge = edge
        super.init(frame: .zero)
        isUserInteractionEnabled = false
        isAccessibilityElement = false
        backgroundColor = .clear
        for (band, intensity) in Self.intensities.enumerated() {
            let view = UIVisualEffectView(effect: nil)
            // Blur strength by animator fraction, as expo-blur does on iOS.
            let animator = UIViewPropertyAnimator(duration: 1, curve: .linear) {
                view.effect = UIBlurEffect(style: .light)
            }
            animator.fractionComplete = intensity / 100
            animator.pausesOnCompletion = true
            view.layer.mask = Self.mask(edge: edge, band: band)
            addSubview(view)
            effectViews.append(view)
            animators.append(animator)
        }
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("BrightLessonRecapBlurStack is created in code")
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        for view in effectViews {
            view.frame = bounds
            view.layer.mask?.frame = bounds
        }
    }

    func stopAnimators() {
        for animator in animators {
            animator.stopAnimation(true)
        }
        animators.removeAll()
    }

    /// RN `makeMask(edge, band)`: depth 0 at the inner edge of the band, 1 at the screen edge.
    private static func mask(edge: Edge, band: Int) -> CAGradientLayer {
        let layer = CAGradientLayer()
        let positions = (0..<samples).map { Double($0) / Double(samples - 1) }
        layer.colors = positions.map { UIColor(white: 1, alpha: maskAlpha(depth: $0, band: band)).cgColor }
        layer.locations = positions.map { NSNumber(value: $0) }
        switch edge {
        case .top:
            layer.startPoint = CGPoint(x: 0.5, y: 1)
            layer.endPoint = CGPoint(x: 0.5, y: 0)
        case .bottom:
            layer.startPoint = CGPoint(x: 0.5, y: 0)
            layer.endPoint = CGPoint(x: 0.5, y: 1)
        }
        return layer
    }

    private static func smoothstep(_ value: Double, _ start: Double, _ end: Double) -> Double {
        let t = max(0, min(1, (value - start) / (end - start)))
        return t * t * (3 - 2 * t)
    }

    /// RN `maskAlpha(depth, band)`: the smooth partition of the band into the three blurs, turned into
    /// source-over alpha ordered low to high so no dark gap opens where neighbouring blurs cross-fade.
    private static func maskAlpha(depth: Double, band: Int) -> CGFloat {
        let coverage = smoothstep(depth, 0, 0.38)
        let medium = smoothstep(depth, 0.18, 0.65)
        let strong = smoothstep(depth, 0.52, 1)
        let weights = [coverage * (1 - medium), coverage * medium * (1 - strong), coverage * medium * strong]
        let above = band == 0 ? weights[1] + weights[2] : band == 1 ? weights[2] : 0
        return 1 - above > 0.00001 ? CGFloat(min(1, weights[band] / (1 - above))) : 0
    }
}

// MARK: - Scroll drag

/// Reports when a finger starts and stops dragging the enclosing `UIScrollView`, which is what RN's
/// `onScrollBeginDrag` / `onScrollEndDrag` report. It watches the scroll view's own pan recogniser, so
/// the scroll view keeps its delegate and every other gesture is left alone.
struct BrightLessonRecapScrollProbe: UIViewRepresentable {
    let onBegan: () -> Void
    let onEnded: () -> Void

    func makeUIView(context: Context) -> ProbeView {
        let view = ProbeView()
        view.onBegan = onBegan
        view.onEnded = onEnded
        return view
    }

    func updateUIView(_ view: ProbeView, context: Context) {
        view.onBegan = onBegan
        view.onEnded = onEnded
    }

    final class ProbeView: UIView {
        var onBegan: () -> Void = {}
        var onEnded: () -> Void = {}
        private weak var scroll: UIScrollView?
        private var dragging = false

        override init(frame: CGRect) {
            super.init(frame: frame)
            isUserInteractionEnabled = false
            isAccessibilityElement = false
        }

        @available(*, unavailable)
        required init?(coder: NSCoder) {
            fatalError("ProbeView is created in code")
        }

        override func didMoveToWindow() {
            super.didMoveToWindow()
            attach()
        }

        override func layoutSubviews() {
            super.layoutSubviews()
            attach()
        }

        private func attach() {
            guard scroll == nil, window != nil else { return }
            var ancestor = superview
            while let view = ancestor, !(view is UIScrollView) {
                ancestor = view.superview
            }
            guard let found = ancestor as? UIScrollView else { return }
            scroll = found
            found.contentInsetAdjustmentBehavior = .never
            found.panGestureRecognizer.addTarget(self, action: #selector(pan(_:)))
        }

        @objc private func pan(_ pan: UIPanGestureRecognizer) {
            switch pan.state {
            case .began:
                dragging = true
                onBegan()
            case .ended, .cancelled, .failed:
                if dragging {
                    dragging = false
                    onEnded()
                }
            default:
                break
            }
        }

        deinit {
            scroll?.panGestureRecognizer.removeTarget(self, action: #selector(pan(_:)))
        }
    }
}

/// The scroll content's measured height, to know whether it overflows the viewport.
struct BrightLessonRecapContentHeightKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = nextValue()
    }
}
