import SwiftUI
import UIKit

/// `ProgressiveBlurViewport` (shared/SkiaProgressiveBlur.tsx) for SwiftUI: when the content of a scroll view is
/// taller than its viewport, the top and bottom edges blur the content that slides under them. Three
/// constant-intensity blurs (12, 32 and 64) are stacked and each is revealed by its own alpha mask, so the blur
/// is clear at the inner edge and strongest at the outer one. Under Reduce Transparency a 2 pt solid edge stays.
enum ConfettiGiftOfferBlur {
    static let intensities: [CGFloat] = [12, 32, 64]
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

/// A `UIVisualEffectView` blurred to a fraction of the full light blur (what expo-blur's `intensity` does).
struct ConfettiGiftOfferBlurView: UIViewRepresentable {
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
            fatalError("ConfettiGiftOfferBlurView.BlurUIView is created in code")
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
                self?.effect = UIBlurEffect(style: .light)
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
struct ConfettiGiftOfferBlurEdge: View {
    var top: Bool
    var size: CGFloat
    var reduceTransparency: Bool

    var body: some View {
        if reduceTransparency {
            // The explicit non-blur fallback: a small solid edge, never a fake gradient blur.
            ConfettiGiftOfferPalette.hex(0xE5E9DF)
                .frame(height: 2)
        } else {
            ZStack {
                ForEach(0..<3, id: \.self) { band in
                    ConfettiGiftOfferBlurView(intensity: ConfettiGiftOfferBlur.intensities[band])
                        .mask(ConfettiGiftOfferBlur.mask(band: band, top: top))
                }
            }
            .frame(height: size)
        }
    }
}

/// A scroll view with the block's behaviour: it bounces even when its content fits (React Native's
/// `alwaysBounceVertical`), tells the controller about drags, and blurs its top and bottom edges when its content
/// overflows. `height` fixes the viewport's height (the sheet hugs its content up to a maximum); nil fills the
/// space it is given. `onContentHeight` reports the content's measured height.
struct ConfettiGiftOfferScroller<Content: View>: View {
    var enabled: Bool
    var edgeSize: CGFloat = 28
    var showsIndicator = false
    var height: CGFloat?
    var onContentHeight: (CGFloat) -> Void = { _ in }
    var onDragBegan: () -> Void
    var onDragEnded: () -> Void = {}
    var onMomentumBegan: () -> Void = {}
    var onMomentumEnded: () -> Void = {}
    @ViewBuilder var content: () -> Content

    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @State private var viewport: CGFloat = 0
    @State private var contentHeight: CGFloat = 0

    private var overflows: Bool {
        contentHeight > viewport + 4
    }

    var body: some View {
        ScrollView(.vertical, showsIndicators: showsIndicator) {
            content()
                .background(
                    ConfettiGiftOfferScrollWatcher(
                        onDragBegan: onDragBegan,
                        onDragEnded: onDragEnded,
                        onMomentumBegan: onMomentumBegan,
                        onMomentumEnded: onMomentumEnded
                    )
                )
                .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { measured in
                    contentHeight = measured
                    onContentHeight(measured)
                }
        }
        .scrollBounceBehavior(.always)
        // A fixed frame, not a flexible one: a `maxHeight` frame would grow to the proposal and centre the viewport.
        .frame(height: height)
        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { viewport = $0 }
        .overlay(alignment: .top) {
            if enabled && overflows && edgeSize > 0 {
                ConfettiGiftOfferBlurEdge(top: true, size: min(edgeSize, viewport / 2), reduceTransparency: reduceTransparency)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
            }
        }
        .overlay(alignment: .bottom) {
            if enabled && overflows && edgeSize > 0 {
                ConfettiGiftOfferBlurEdge(top: false, size: min(edgeSize, viewport / 2), reduceTransparency: reduceTransparency)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
            }
        }
    }
}
