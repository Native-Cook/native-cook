import QuartzCore

/// Keeps `CADisplayLink` ownership separate from the observable controller and
/// uses a weak target so stopping animation is sufficient to release it.
@MainActor
final class PullCordThemeDisplayLink {
    private let onFrame: (CFTimeInterval) -> Void
    private let target = Target()
    private var displayLink: CADisplayLink?

    init(onFrame: @escaping (CFTimeInterval) -> Void) {
        self.onFrame = onFrame
        target.owner = self
    }

    var isRunning: Bool {
        displayLink != nil
    }

    func start() {
        guard displayLink == nil else { return }
        let link = CADisplayLink(
            target: target,
            selector: #selector(Target.frame(_:))
        )
        link.preferredFrameRateRange = CAFrameRateRange(
            minimum: 60,
            maximum: 120,
            preferred: 120
        )
        link.add(to: .main, forMode: .common)
        displayLink = link
    }

    func stop() {
        displayLink?.invalidate()
        displayLink = nil
    }

    fileprivate func frame(_ link: CADisplayLink) {
        onFrame(link.targetTimestamp)
    }

    deinit {
        displayLink?.invalidate()
    }
    @MainActor
    private final class Target: NSObject {
        weak var owner: PullCordThemeDisplayLink?

        @objc func frame(_ link: CADisplayLink) {
            owner?.frame(link)
        }
    }
}
