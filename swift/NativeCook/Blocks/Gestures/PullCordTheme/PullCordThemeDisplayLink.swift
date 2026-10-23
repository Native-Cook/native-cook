import QuartzCore

@MainActor
final class PullCordThemeDisplayLink {
    private let target = Target()
    private let onFrame: (CFTimeInterval) -> Void
    private var link: CADisplayLink?

    init(onFrame: @escaping (CFTimeInterval) -> Void) {
        self.onFrame = onFrame
        target.owner = self
    }

    func start() {
        guard link == nil else { return }
        let link = CADisplayLink(target: target, selector: #selector(Target.frame(_:)))
        link.preferredFrameRateRange = CAFrameRateRange(minimum: 60, maximum: 120, preferred: 120)
        link.add(to: .main, forMode: .common)
        self.link = link
    }

    func stop() {
        link?.invalidate()
        link = nil
    }

    fileprivate func frame(_ link: CADisplayLink) {
        onFrame(link.targetTimestamp)
    }

    deinit {
        link?.invalidate()
    }

    @MainActor
    private final class Target: NSObject {
        weak var owner: PullCordThemeDisplayLink?

        @objc func frame(_ link: CADisplayLink) {
            owner?.frame(link)
        }
    }
}
