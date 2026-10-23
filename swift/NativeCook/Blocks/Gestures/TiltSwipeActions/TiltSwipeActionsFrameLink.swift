import QuartzCore

/// One display link: calls back with the seconds since the previous frame.
@MainActor
final class TiltSwipeActionsFrameLink: NSObject {
    var onFrame: ((Double) -> Void)?
    private var link: CADisplayLink?
    private var last: CFTimeInterval = 0

    func start() {
        guard link == nil else { return }
        let link = CADisplayLink(target: self, selector: #selector(fire(_:)))
        link.add(to: .main, forMode: .common)
        self.link = link
        last = 0
    }

    func stop() {
        link?.invalidate()
        link = nil
    }

    @objc private func fire(_ link: CADisplayLink) {
        // targetTimestamp is the display link's wall-clock presentation time. It keeps the declared
        // loop independent of refresh rate; the model clamps one slow frame to the contract's 0.1 s.
        let now = link.targetTimestamp
        let dt = last == 0 ? 1.0 / 60 : now - last
        last = now
        onFrame?(max(dt, 0))
    }
}
