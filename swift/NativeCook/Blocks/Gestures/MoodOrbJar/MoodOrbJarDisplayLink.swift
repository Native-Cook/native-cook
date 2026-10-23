import QuartzCore

/// Keeps `CADisplayLink` ownership separate from the observable controller and uses a weak target,
/// so stopping the link is enough to release it.
@MainActor
final class MoodOrbJarDisplayLink {
    private let onFrame: (CFTimeInterval) -> Void
    private let target = Target()
    private var displayLink: CADisplayLink?

    init(onFrame: @escaping (CFTimeInterval) -> Void) {
        self.onFrame = onFrame
        target.owner = self
    }

    func start() {
        guard displayLink == nil else { return }
        let link = CADisplayLink(target: target, selector: #selector(Target.frame(_:)))
        link.preferredFrameRateRange = CAFrameRateRange(minimum: 60, maximum: 120, preferred: 120)
        link.add(to: .main, forMode: .common)
        displayLink = link
    }

    func stop() {
        displayLink?.invalidate()
        displayLink = nil
    }

    fileprivate func frame(_ link: CADisplayLink) {
        // targetTimestamp is the display's wall-clock presentation time, matching the RN frame clock.
        onFrame(link.targetTimestamp)
    }

    deinit {
        displayLink?.invalidate()
    }

    @MainActor
    private final class Target: NSObject {
        weak var owner: MoodOrbJarDisplayLink?

        @objc func frame(_ link: CADisplayLink) {
            owner?.frame(link)
        }
    }
}
