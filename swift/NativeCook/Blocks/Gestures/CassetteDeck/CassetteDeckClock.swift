import QuartzCore

/// A display link that can pause itself while the deck is still. It holds its owner weakly, so a
/// view that goes away never keeps it alive.
@MainActor
final class CassetteDeckClock: NSObject {
    private var link: CADisplayLink?
    private var handler: ((CFTimeInterval) -> Void)?

    var isRunning: Bool {
        guard let link else { return false }
        return !link.isPaused
    }

    func start(_ handler: @escaping (CFTimeInterval) -> Void) {
        self.handler = handler
        if let link {
            link.isPaused = false
            return
        }
        let link = CADisplayLink(target: self, selector: #selector(fire(_:)))
        link.add(to: .main, forMode: .common)
        self.link = link
    }

    func pause() {
        link?.isPaused = true
    }

    func resume() {
        link?.isPaused = false
    }

    func stop() {
        link?.invalidate()
        link = nil
        handler = nil
    }

    @objc private func fire(_ link: CADisplayLink) {
        handler?(link.timestamp)
    }
}
