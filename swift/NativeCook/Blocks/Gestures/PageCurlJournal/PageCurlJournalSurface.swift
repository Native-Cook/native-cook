import MetalKit
import SwiftUI
import UIKit

/// The block's interactive layer: it owns the engine, the Metal view that draws the curl and the foil, the display
/// link and the finger. The pages themselves are SwiftUI views beneath it; this view is transparent whenever the
/// book is at rest.
@MainActor
final class PageCurlJournalSurface: UIView, UIGestureRecognizerDelegate {
    private typealias Motion = PageCurlJournalMotion

    private let model: PageCurlJournalModel
    private let engine: PageCurlJournalEngine
    private let renderer: PageCurlJournalRenderer?
    private let metalView = MTKView()
    private let tilt = PageCurlJournalTilt()
    private var link: CADisplayLink?
    private var active = false
    private var snapshotSize = CGSize.zero
    private var snapshotScale: CGFloat = 0
    private var samples: [(time: Double, point: CGPoint)] = []
    private let selection = UISelectionFeedbackGenerator()
    private let lightImpact = UIImpactFeedbackGenerator(style: .light)
    private let softImpact = UIImpactFeedbackGenerator(style: .soft)

    init(model: PageCurlJournalModel, autoPlay: Bool, reduced: Bool) {
        self.model = model
        engine = PageCurlJournalEngine(
            width: Double(PageCurlJournalTheme.referenceWidth),
            height: Double(PageCurlJournalTheme.referenceHeight),
            initialPage: model.page
        )
        renderer = PageCurlJournalRenderer.make()
        super.init(frame: .zero)
        backgroundColor = .clear
        isAccessibilityElement = false
        engine.autoPlay = autoPlay
        engine.reduced = reduced

        if let renderer {
            renderer.configure(metalView)
            metalView.isUserInteractionEnabled = false
            metalView.isHidden = true
            addSubview(metalView)
        }

        engine.onHaptic = { [weak self] haptic in self?.play(haptic) }
        engine.onLanded = { [weak model] page in model?.page = page }
        model.turn = { [weak engine] step in engine?.turnBy(step) }

        let touch = UILongPressGestureRecognizer(target: self, action: #selector(handleTouch(_:)))
        touch.minimumPressDuration = 0
        touch.allowableMovement = .greatestFiniteMagnitude
        touch.numberOfTouchesRequired = 1
        touch.cancelsTouchesInView = false
        touch.delegate = self
        addGestureRecognizer(touch)
    }

    required init?(coder: NSCoder) { nil }

    func configure(autoPlay: Bool, reduced: Bool) {
        engine.autoPlay = autoPlay
        engine.reduced = reduced
    }

    /// Runs the display link only while the scene is active and the view is on screen.
    func setActive(_ value: Bool) {
        active = value
        updateLink()
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        updateLink()
        buildPagesIfNeeded()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        metalView.frame = bounds
        buildPagesIfNeeded()
    }

    // MARK: - Pages

    /// Device pixels per point: the screen's once the view is on one.
    private var pixelScale: CGFloat {
        if let scale = window?.screen.scale { return scale }
        return traitCollection.displayScale > 0 ? traitCollection.displayScale : 3
    }

    private func buildPagesIfNeeded() {
        guard bounds.width > 0, bounds.height > 0, bounds.size != snapshotSize || pixelScale != snapshotScale else { return }
        snapshotSize = bounds.size
        snapshotScale = pixelScale
        buildPages()
    }

    /// Snapshots the four pages once they can be laid out: the curl samples them as textures.
    private func buildPages() {
        guard let renderer else { return }
        var images: [CGImage] = []
        for page in 0..<4 {
            guard let image = snapshot(page: page) else { return }
            images.append(image)
        }
        renderer.setPages(images)
        engine.resize(width: Double(bounds.width), height: Double(bounds.height))
        if renderer.hasPages, !engine.ready { engine.markReady() }
    }

    /// `ImageRenderer` substitutes warning glyphs for `UIViewRepresentable` content. Host the page in a real view
    /// hierarchy so its UILabel typography, paper image and vector seal are all baked into the curl texture.
    private func snapshot(page: Int) -> CGImage? {
        let content = PageCurlJournalPageView(page: page, size: bounds.size)
        let host = UIHostingController(rootView: content)
        let hostedView = host.view!
        hostedView.frame = CGRect(origin: .zero, size: bounds.size)
        hostedView.bounds = CGRect(origin: .zero, size: bounds.size)
        hostedView.backgroundColor = UIColor(PageCurlJournalTheme.Paper.base)
        hostedView.overrideUserInterfaceStyle = .light
        hostedView.isUserInteractionEnabled = false
        insertSubview(hostedView, at: 0)
        hostedView.setNeedsLayout()
        hostedView.layoutIfNeeded()

        let format = UIGraphicsImageRendererFormat()
        format.scale = pixelScale
        format.opaque = true
        let image = UIGraphicsImageRenderer(size: bounds.size, format: format).image { context in
            if !hostedView.drawHierarchy(in: hostedView.bounds, afterScreenUpdates: true) {
                hostedView.layer.render(in: context.cgContext)
            }
        }
        hostedView.removeFromSuperview()
        return image.cgImage
    }

    // MARK: - Frame

    private func updateLink() {
        let wanted = active && window != nil
        if wanted, link == nil {
            let displayLink = CADisplayLink(target: PageCurlJournalLinkTarget(self), selector: #selector(PageCurlJournalLinkTarget.tick(_:)))
            displayLink.add(to: .main, forMode: .common)
            link = displayLink
            tilt.start()
        } else if !wanted, let displayLink = link {
            displayLink.invalidate()
            link = nil
            tilt.stop()
        }
    }

    fileprivate func tick(_ link: CADisplayLink) {
        engine.step(now: link.timestamp)
        render()
    }

    /// Draws only what moves: the sheets in flight, or the foil on the resting cover. Otherwise the view is hidden.
    private func render() {
        guard let renderer, renderer.hasPages, bounds.width > 0, metalView.drawableSize.width > 0 else { return }
        let ratio = Double(metalView.drawableSize.width / bounds.width)
        var frame = PageCurlJournalFrame()
        if engine.curlVisible {
            frame.curls.append(engine.curlUniforms(slot: 0, pixelRatio: ratio))
            for index in 1..<engine.slots.count where engine.slots[index].on > 0 {
                frame.curls.append(engine.curlUniforms(slot: index, pixelRatio: ratio))
            }
        } else if engine.cur == PageCurlJournalEngine.lastPage, model.page == PageCurlJournalEngine.lastPage {
            frame.foil = engine.foilUniforms(roll: tilt.roll, pixelRatio: ratio)
        }
        let draws = !frame.curls.isEmpty || frame.foil != nil
        metalView.isHidden = !draws
        if draws { renderer.render(frame, in: metalView) }
    }

    private func play(_ haptic: PageCurlJournalEngine.Haptic) {
        switch haptic {
        case .tick: selection.selectionChanged()
        case .half: lightImpact.impactOccurred()
        case .land: softImpact.impactOccurred()
        }
    }

    // MARK: - Finger

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool { true }

    @objc private func handleTouch(_ recognizer: UILongPressGestureRecognizer) {
        let point = recognizer.location(in: self)
        let now = CACurrentMediaTime()
        switch recognizer.state {
        case .began:
            samples = [(now, point)]
            selection.prepare()
            lightImpact.prepare()
            softImpact.prepare()
            engine.touchDown(x: Double(point.x), y: Double(point.y))
        case .changed:
            record(point, at: now)
            engine.touchMove(x: Double(point.x), y: Double(point.y))
        case .ended, .cancelled, .failed:
            record(point, at: now)
            let velocity = releaseVelocity(at: now)
            engine.touchEnd(x: Double(point.x), y: Double(point.y), vx: velocity.x, vy: velocity.y)
        default:
            break
        }
    }

    private func record(_ point: CGPoint, at time: Double) {
        samples.append((time, point))
        samples.removeAll { time - $0.time > 0.1 }
    }

    /// Points per second over the last 80 ms of the drag (the pan gesture's velocity); a finger that rested before lifting has none.
    private func releaseVelocity(at now: Double) -> (x: Double, y: Double) {
        let recent = samples.filter { now - $0.time <= 0.08 }
        guard let first = recent.first, let last = recent.last, last.time - first.time >= 0.01 else { return (0, 0) }
        let span = last.time - first.time
        return (Double(last.point.x - first.point.x) / span, Double(last.point.y - first.point.y) / span)
    }
}

/// CADisplayLink retains its target: this forwarder holds the surface weakly so the surface can go away.
@MainActor
private final class PageCurlJournalLinkTarget: NSObject {
    private weak var surface: PageCurlJournalSurface?

    init(_ surface: PageCurlJournalSurface) {
        self.surface = surface
    }

    @objc func tick(_ link: CADisplayLink) {
        surface?.tick(link)
    }
}
