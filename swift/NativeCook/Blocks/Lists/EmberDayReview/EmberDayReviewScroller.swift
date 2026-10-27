import SwiftUI
import UIKit

/// `ProgressiveBlurViewport` around a `ScrollView` whose touches pause the demo: the scroll view of
/// each day page. The blur bands show only while the content overflows the viewport.
struct EmberDayReviewScroller<Content: View>: View {
    let motion: EmberDayReviewMotion
    let size: CGSize
    /// `visibleDay === <this page> && !reviewVisible`: also gates whether touches pause the demo.
    let active: Bool
    @ViewBuilder var content: () -> Content

    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @State private var contentHeight: CGFloat = 0

    var body: some View {
        // `contentSize.height > size.height + 4`.
        let overflows = contentHeight > size.height + 4
        ScrollView(.vertical, showsIndicators: false) {
            content()
                .background(
                    GeometryReader { proxy in
                        Color.clear.preference(key: EmberDayReviewContentHeightKey.self, value: proxy.size.height)
                    }
                )
        }
        // React Native's vertical ScrollView bounces even when the content is shorter.
        .scrollBounceBehavior(.always, axes: .vertical)
        .onPreferenceChange(EmberDayReviewContentHeightKey.self) { contentHeight = $0 }
        .background(EmberDayReviewTouchObserver(motion: motion, active: active))
        .overlay {
            if active && overflows && size.width > 0 && size.height > 0 {
                EmberDayReviewEdgeBlur(edgeSize: 28, solid: reduceTransparency)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
            }
        }
        .modifier(EmberDayReviewScrollPhase(motion: motion))
        .frame(width: size.width, height: size.height)
    }
}

private struct EmberDayReviewContentHeightKey: PreferenceKey {
    static var defaultValue: CGFloat = 0

    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = max(value, nextValue())
    }
}

/// `onMomentumScrollBegin` / `onMomentumScrollEnd`: the demo stays paused while a fling decelerates.
private struct EmberDayReviewScrollPhase: ViewModifier {
    let motion: EmberDayReviewMotion

    func body(content: Content) -> some View {
        if #available(iOS 18.0, *) {
            content.onScrollPhaseChange { old, new in
                if new == .decelerating {
                    motion.touchStart()
                } else if old == .decelerating && new == .idle {
                    motion.touchEnd()
                }
            }
        } else {
            content
        }
    }
}

// MARK: - Touch

/// `onTouchStart` / `onTouchEnd` / `onTouchCancel` of the React Native ScrollView.
///
/// A zero-delay press recogniser on the window watches every finger that lands inside this view's
/// frame. It never takes a touch from the scroll view or a button (`cancelsTouchesInView` off, and
/// it recognises beside every other recogniser), and it survives the gallery's back swipe, which
/// would cancel a `DragGesture` after 24 points. The view itself is invisible to hit testing.
struct EmberDayReviewTouchObserver: UIViewRepresentable {
    let motion: EmberDayReviewMotion
    let active: Bool

    func makeUIView(context: Context) -> EmberDayReviewTouchObserverView {
        let view = EmberDayReviewTouchObserverView()
        view.backgroundColor = .clear
        return view
    }

    func updateUIView(_ view: EmberDayReviewTouchObserverView, context: Context) {
        view.active = active
        view.onBegan = { [weak motion] in motion?.touchStart() }
        view.onEnded = { [weak motion] in motion?.touchEnd() }
    }
}

final class EmberDayReviewTouchObserverView: UIView, UIGestureRecognizerDelegate {
    var active = true
    var onBegan: (() -> Void)?
    var onEnded: (() -> Void)?

    private var press: UILongPressGestureRecognizer?
    private weak var host: UIView?

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if let press, let host { host.removeGestureRecognizer(press) }
        press = nil
        host = nil
        guard let window else { return }
        let recognizer = UILongPressGestureRecognizer(target: self, action: #selector(track(_:)))
        recognizer.minimumPressDuration = 0
        recognizer.allowableMovement = .greatestFiniteMagnitude
        recognizer.cancelsTouchesInView = false
        recognizer.delaysTouchesBegan = false
        recognizer.delaysTouchesEnded = false
        recognizer.delegate = self
        window.addGestureRecognizer(recognizer)
        press = recognizer
        host = window
    }

    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        nil
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        active && bounds.contains(touch.location(in: self))
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer
    ) -> Bool {
        true
    }

    @objc private func track(_ recognizer: UILongPressGestureRecognizer) {
        switch recognizer.state {
        case .began:
            onBegan?()
        case .ended, .cancelled, .failed:
            onEnded?()
        default:
            break
        }
    }
}

// MARK: - Progressive blur

/// The three-profile progressive blur of `SkiaProgressiveBlur.tsx` on the top and bottom edges: for
/// each edge three light `UIVisualEffectView`s (intensity 12, 32 and 64) stacked low to high, each
/// masked by a 65-stop gradient whose alpha is `maskAlpha(depth, band)`. With Reduce Transparency
/// each edge is a 2 pt solid bar instead.
struct EmberDayReviewEdgeBlur: UIViewRepresentable {
    let edgeSize: CGFloat
    let solid: Bool

    func makeUIView(context: Context) -> EmberDayReviewBlurView {
        let view = EmberDayReviewBlurView()
        view.isUserInteractionEnabled = false
        return view
    }

    func updateUIView(_ view: EmberDayReviewBlurView, context: Context) {
        view.edgeSize = min(56, max(0, edgeSize))
        view.solid = solid
        view.setNeedsLayout()
    }
}

final class EmberDayReviewBlurView: UIView {
    private enum Edge: CaseIterable {
        case top
        case bottom
    }

    private final class Band {
        let edge: Edge
        let fraction: CGFloat
        let view = UIVisualEffectView(effect: nil)
        let mask = CAGradientLayer()
        var animator: UIViewPropertyAnimator?

        init(edge: Edge, band: Int, fraction: CGFloat) {
            self.edge = edge
            self.fraction = fraction
            let samples = 65
            var colors: [CGColor] = []
            var locations: [NSNumber] = []
            for index in 0..<samples {
                let depth = Double(index) / Double(samples - 1)
                let alpha = EmberDayReviewBlurView.maskAlpha(depth: depth, band: band)
                colors.append(UIColor(white: 1, alpha: alpha).cgColor)
                locations.append(NSNumber(value: depth))
            }
            mask.colors = colors
            mask.locations = locations
            // Depth 0 is the inner edge of the band, depth 1 the edge of the viewport.
            mask.startPoint = edge == .top ? CGPoint(x: 0.5, y: 1) : CGPoint(x: 0.5, y: 0)
            mask.endPoint = edge == .top ? CGPoint(x: 0.5, y: 0) : CGPoint(x: 0.5, y: 1)
            view.layer.mask = mask
        }

        func startBlur() {
            guard animator == nil else { return }
            let effectView = view
            let blur = UIViewPropertyAnimator(duration: 1, curve: .linear) {
                effectView.effect = UIBlurEffect(style: .light)
            }
            blur.pausesOnCompletion = true
            blur.fractionComplete = fraction
            animator = blur
        }

        func stopBlur() {
            animator?.stopAnimation(true)
            animator = nil
            view.effect = nil
        }

        func refresh() {
            animator?.fractionComplete = fraction
        }
    }

    /// Points, clamped to 0 to 56 and half the viewport.
    var edgeSize: CGFloat = 28
    var solid = false {
        didSet {
            if solid != oldValue { applyMode() }
        }
    }

    private static let intensities: [CGFloat] = [12, 32, 64]
    private var bands: [Band] = []
    private var bars: [Edge: UIView] = [:]
    private var observer: NSObjectProtocol?

    override init(frame: CGRect) {
        super.init(frame: frame)
        for edge in Edge.allCases {
            for (index, intensity) in Self.intensities.enumerated() {
                let band = Band(edge: edge, band: index, fraction: intensity / 100)
                bands.append(band)
                addSubview(band.view)
            }
            let bar = UIView()
            bar.backgroundColor = UIColor(red: 0xE5 / 255, green: 0xE9 / 255, blue: 0xDF / 255, alpha: 1)
            bars[edge] = bar
            addSubview(bar)
        }
        observer = NotificationCenter.default.addObserver(
            forName: UIApplication.didBecomeActiveNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.bands.forEach { $0.refresh() }
        }
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("EmberDayReviewBlurView is created in code")
    }

    deinit {
        if let observer { NotificationCenter.default.removeObserver(observer) }
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        applyMode()
    }

    private func applyMode() {
        let blurring = window != nil && !solid
        for band in bands {
            band.view.isHidden = !blurring
            if blurring { band.startBlur() } else { band.stopBlur() }
        }
        for bar in bars.values {
            bar.isHidden = !solid
        }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let width = bounds.width
        let height = bounds.height
        let band = min(edgeSize, height / 2)
        func frame(for edge: Edge, thickness: CGFloat) -> CGRect {
            switch edge {
            case .top: return CGRect(x: 0, y: 0, width: width, height: thickness)
            case .bottom: return CGRect(x: 0, y: height - thickness, width: width, height: thickness)
            }
        }
        for item in bands {
            item.view.frame = frame(for: item.edge, thickness: band)
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            item.mask.frame = item.view.bounds
            CATransaction.commit()
        }
        for (edge, bar) in bars {
            bar.frame = frame(for: edge, thickness: min(2, height / 2))
        }
    }

    // MARK: Mask

    private static func smoothstep(_ value: Double, _ start: Double, _ end: Double) -> Double {
        let t = min(1, max(0, (value - start) / (end - start)))
        return t * t * (3 - 2 * t)
    }

    /// `maskAlpha(depth, band)` of SkiaProgressiveBlur.tsx: a smooth partition into three profiles,
    /// turned into source-over alpha ordered low to high so neighbours leave no dark gap.
    static func maskAlpha(depth: Double, band: Int) -> Double {
        let coverage = smoothstep(depth, 0, 0.38)
        let medium = smoothstep(depth, 0.18, 0.65)
        let strong = smoothstep(depth, 0.52, 1)
        let weights = [coverage * (1 - medium), coverage * medium * (1 - strong), coverage * medium * strong]
        let above = band == 0 ? weights[1] + weights[2] : (band == 1 ? weights[2] : 0)
        return 1 - above > 0.00001 ? min(1, weights[band] / (1 - above)) : 0
    }
}
