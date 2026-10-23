import QuartzCore

@MainActor
final class HabitBubbleClusterDisplayLink: NSObject {
    var onFrame: ((CFTimeInterval) -> Void)?

    private var displayLink: CADisplayLink?

    func start() {
        guard displayLink == nil else { return }
        let link = CADisplayLink(target: self, selector: #selector(frame(_:)))
        link.add(to: .main, forMode: .common)
        displayLink = link
    }

    func stop() {
        displayLink?.invalidate()
        displayLink = nil
    }

    @objc private func frame(_ link: CADisplayLink) {
        onFrame?(link.targetTimestamp)
    }
}
