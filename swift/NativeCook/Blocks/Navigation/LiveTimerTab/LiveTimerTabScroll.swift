import SwiftUI
import UIKit

/// Scrolls the page for the loop and reports where it is. SwiftUI's own scroll view cannot be
/// taken to a point over a chosen time along a chosen curve, so the page sits in a `UIScrollView`.
final class LiveTimerTabPilot: NSObject, UIScrollViewDelegate {
    weak var scroll: UIScrollView?
    /// Called with the page's offset whenever it changes.
    var onScroll: ((Double) -> Void)?
    /// Called when a finger starts to drag the page.
    var onDrag: (() -> Void)?

    private var link: CADisplayLink?
    private var from = 0.0
    private var to = 0.0
    private var start = Date.distantPast
    private var seconds = 0.0

    /// Takes the page to `y` in `seconds`, counted from `date`.
    func glide(to y: Double, seconds: Double, from date: Date = .now) {
        guard let scroll else { return }
        halt()
        from = scroll.contentOffset.y
        to = min(y, max(0, scroll.contentSize.height - scroll.bounds.height))
        start = date
        self.seconds = seconds
        let link = CADisplayLink(target: self, selector: #selector(step))
        link.add(to: .main, forMode: .common)
        self.link = link
    }

    /// Scrolls back to the top the way the system does.
    func home() {
        halt()
        scroll?.setContentOffset(.zero, animated: true)
    }

    func halt() {
        link?.invalidate()
        link = nil
    }

    @objc private func step() {
        let t = seconds > 0 ? min(max(Date.now.timeIntervalSince(start) / seconds, 0), 1) : 1
        scroll?.contentOffset.y = from + (to - from) * LiveTimerTabMotion.glide.value(at: t)
        if t >= 1 { halt() }
    }

    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        onScroll?(scrollView.contentOffset.y)
    }

    func scrollViewWillBeginDragging(_ scrollView: UIScrollView) {
        halt()
        onDrag?()
    }
}

/// The page in a scroll view that `LiveTimerTabPilot` drives.
struct LiveTimerTabScroll<Content: View>: UIViewRepresentable {
    let pilot: LiveTimerTabPilot
    @ViewBuilder var content: Content

    func makeCoordinator() -> UIHostingController<Content> {
        UIHostingController(rootView: content)
    }

    func makeUIView(context: Context) -> UIScrollView {
        let scroll = UIScrollView()
        // The page draws under the status bar and leaves room for the tab bar itself.
        scroll.contentInsetAdjustmentBehavior = .never
        scroll.showsVerticalScrollIndicator = false
        scroll.alwaysBounceVertical = true
        scroll.backgroundColor = .clear
        scroll.delegate = pilot
        pilot.scroll = scroll

        let host = context.coordinator
        host.safeAreaRegions = []
        host.sizingOptions = .intrinsicContentSize
        host.view.backgroundColor = .clear
        host.view.translatesAutoresizingMaskIntoConstraints = false
        scroll.addSubview(host.view)
        NSLayoutConstraint.activate([
            host.view.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
            host.view.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor),
            host.view.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor),
            host.view.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor),
            host.view.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor),
        ])
        return scroll
    }

    func updateUIView(_ scroll: UIScrollView, context: Context) {
        context.coordinator.rootView = content
    }

    static func dismantleUIView(_ scroll: UIScrollView, coordinator: UIHostingController<Content>) {
        (scroll.delegate as? LiveTimerTabPilot)?.halt()
    }
}
