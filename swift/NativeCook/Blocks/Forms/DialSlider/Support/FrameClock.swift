import Foundation
import QuartzCore

#if canImport(UIKit)
import UIKit
#endif

/// Drives frame-based motion (decay, spring, timing) without SwiftUI's
/// implicit animations, so derived geometry can be recomputed every frame the
/// way Reanimated shared values are on the UI runtime.
final class FrameClock: NSObject {
    private let onFrame: (TimeInterval) -> Void
    private(set) var isRunning = false

    #if canImport(UIKit)
    private var displayLink: CADisplayLink?
    #else
    private var timer: Timer?
    #endif

    init(onFrame: @escaping (TimeInterval) -> Void) {
        self.onFrame = onFrame
        super.init()
    }

    deinit {
        stop()
    }

    static var now: TimeInterval {
        CACurrentMediaTime()
    }

    func start() {
        guard !isRunning else { return }
        isRunning = true
        #if canImport(UIKit)
        let link = CADisplayLink(target: self, selector: #selector(step(_:)))
        link.add(to: .main, forMode: .common)
        displayLink = link
        #else
        let timer = Timer(timeInterval: 1.0 / 60.0, repeats: true) { [weak self] _ in
            self?.onFrame(CACurrentMediaTime())
        }
        RunLoop.main.add(timer, forMode: .common)
        self.timer = timer
        #endif
    }

    func stop() {
        guard isRunning else { return }
        isRunning = false
        #if canImport(UIKit)
        displayLink?.invalidate()
        displayLink = nil
        #else
        timer?.invalidate()
        timer = nil
        #endif
    }

    #if canImport(UIKit)
    @objc private func step(_ link: CADisplayLink) {
        onFrame(link.timestamp)
    }
    #endif
}
