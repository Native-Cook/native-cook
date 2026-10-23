import SwiftUI
import UIKit

/// The list: a native scroll view (so the flick, the bounce and the deceleration are the system's)
/// holding the rows, which are drawn by SwiftUI. The engine drives its offset for auto-scroll,
/// momentum and the trip home, and turns scrolling off while a range drag is under the finger.
final class DragMultiSelectListController: UIViewController, UIScrollViewDelegate, UIGestureRecognizerDelegate {
    private let engine: DragMultiSelectEngine
    private let scrollView = UIScrollView()
    private let rows: UIHostingController<DragMultiSelectRows>

    init(engine: DragMultiSelectEngine) {
        self.engine = engine
        rows = UIHostingController(rootView: DragMultiSelectRows(engine: engine))
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear
        scrollView.backgroundColor = .clear
        scrollView.delegate = self
        scrollView.showsVerticalScrollIndicator = false
        scrollView.alwaysBounceVertical = true
        scrollView.contentInsetAdjustmentBehavior = .never
        view.addSubview(scrollView)

        rows.sizingOptions = []
        rows.safeAreaRegions = []
        rows.view.backgroundColor = .clear
        addChild(rows)
        scrollView.addSubview(rows.view)
        rows.didMove(toParent: self)

        let listener = DragMultiSelectTouchListener(target: nil, action: nil)
        listener.cancelsTouchesInView = false
        listener.delaysTouchesBegan = false
        listener.delaysTouchesEnded = false
        listener.delegate = self
        listener.engine = engine
        view.addGestureRecognizer(listener)
        engine.scrollView = scrollView
        engine.contentChanged = { [weak self] in self?.refreshContentSize() }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        scrollView.frame = view.bounds
        engine.viewport = view.bounds.height
        rows.view.frame = CGRect(x: 0, y: 0, width: view.bounds.width, height: engine.maxContentHeight)
        refreshContentSize()
        if view.window != nil { engine.startLoop() }
    }

    /// The content is as tall as the rows that are still in the list.
    func refreshContentSize() {
        let size = CGSize(width: view.bounds.width, height: engine.contentHeight)
        if scrollView.contentSize != size { scrollView.contentSize = size }
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        engine.startLoop()
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        engine.stopLoop()
    }

    func stop() {
        engine.stopLoop()
    }

    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        engine.scrolled(to: scrollView.contentOffset.y)
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
        true
    }
}
