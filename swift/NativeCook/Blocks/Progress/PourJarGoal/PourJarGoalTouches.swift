import SwiftUI
import UIKit

/// Native touch sampling preserves the reference's second-finger surface force.
struct PourJarGoalTouchSurface: UIViewRepresentable {
    var began: ([CGPoint]) -> Void
    var moved: ([CGPoint]) -> Void
    var ended: (Double) -> Void
    var cancelled: () -> Void
    func makeUIView(context: Context) -> PourJarGoalTouchView {
        let view = PourJarGoalTouchView()
        view.backgroundColor = .clear; view.isMultipleTouchEnabled = true
        updateUIView(view, context: context)
        return view
    }
    func updateUIView(_ uiView: PourJarGoalTouchView, context: Context) {
        uiView.began = began; uiView.moved = moved; uiView.ended = ended; uiView.cancelled = cancelled
    }
}

final class PourJarGoalTouchView: UIView {
    var began: (([CGPoint]) -> Void)?
    var moved: (([CGPoint]) -> Void)?
    var ended: ((Double) -> Void)?
    var cancelled: (() -> Void)?
    private var fingers: [UITouch] = []
    private let velocityTracker = UIPanGestureRecognizer()
    override init(frame: CGRect) {
        super.init(frame: frame)
        velocityTracker.cancelsTouchesInView = false
        velocityTracker.delaysTouchesBegan = false
        velocityTracker.delaysTouchesEnded = false
        addGestureRecognizer(velocityTracker)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        let first = fingers.isEmpty
        fingers += touches.sorted { $0.timestamp < $1.timestamp }
        let points = fingers.map { $0.location(in: self) }
        if first {
            began?(points)
        } else { moved?(points) }
    }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        moved?(fingers.map { $0.location(in: self) })
    }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        // RNGH's iOS Pan also reports UIPanGestureRecognizer.velocity(in:), rather
        // than a last-event finite difference (important for the 1400 pt/s skip).
        let velocityY = velocityTracker.velocity(in: self).y
        fingers.removeAll { touches.contains($0) }
        if fingers.isEmpty { ended?(velocityY) } else { moved?(fingers.map { $0.location(in: self) }) }
    }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        fingers.removeAll(); cancelled?()
    }
}

/// RN's largest-type ScrollView uses bounces=false and owns its safe-area layout.
struct PourJarGoalScroll<Content: View>: UIViewControllerRepresentable {
    let size: CGSize
    let content: Content
    func makeUIViewController(context: Context) -> PourJarGoalScrollController<Content> {
        PourJarGoalScrollController(size: size, content: content)
    }
    func updateUIViewController(_ controller: PourJarGoalScrollController<Content>, context: Context) {
        controller.update(size: size, content: content)
    }
}

final class PourJarGoalScrollView: UIScrollView {
    override func touchesShouldCancel(in view: UIView) -> Bool {
        if view is PourJarGoalTouchView { return false }
        return super.touchesShouldCancel(in: view)
    }
}

final class PourJarGoalScrollController<Content: View>: UIViewController {
    private let scroll = PourJarGoalScrollView()
    private let host: UIHostingController<Content>
    private var contentSize: CGSize
    init(size: CGSize, content: Content) {
        contentSize = size; host = UIHostingController(rootView: content)
        super.init(nibName: nil, bundle: nil)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear
        scroll.backgroundColor = .clear; scroll.bounces = false
        scroll.showsVerticalScrollIndicator = false; scroll.showsHorizontalScrollIndicator = false
        scroll.contentInsetAdjustmentBehavior = .never; scroll.delaysContentTouches = false
        view.addSubview(scroll)
        addChild(host); host.safeAreaRegions = []
        host.view.backgroundColor = .clear; scroll.addSubview(host.view); host.didMove(toParent: self)
    }
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        scroll.frame = view.bounds; scroll.contentSize = contentSize
        host.view.frame = CGRect(origin: .zero, size: contentSize)
    }
    func update(size: CGSize, content: Content) {
        contentSize = size; host.rootView = content
        if isViewLoaded { view.setNeedsLayout() }
    }
}
