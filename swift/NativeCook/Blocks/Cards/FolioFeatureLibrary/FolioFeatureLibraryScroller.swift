import SwiftUI
import UIKit

/// A `UIScrollView` that tells its owner when it has been laid out.
final class FolioFeatureLibraryScrollView: UIScrollView {
    var onLayout: (() -> Void)?

    override func layoutSubviews() {
        super.layoutSubviews()
        onLayout?()
    }
}

/// React Native's `ScrollView` for this block: a `UIScrollView` (the same native view React Native uses, so the
/// bounce, deceleration and snapping are the system's) holding SwiftUI content. The guided loop needs to take it to
/// an exact offset every frame, which SwiftUI's own `ScrollView` cannot do on iOS 17, so the engine writes the offsets
/// (FolioFeatureLibraryEngineScroll.swift) and the scroll view reports drags back (`onScrollBeginDrag`,
/// `onScrollEndDrag`, `onMomentumScrollBegin`, `onMomentumScrollEnd`).
///
/// The content must not contain a `GeometryReader` or any view that grows without bound along the scroll axis: its
/// size is read from the hosting controller.
struct FolioFeatureLibraryScroller<Content: View>: UIViewRepresentable {
    let engine: FolioFeatureLibraryEngine
    let id: FolioFeatureLibraryScrollerID
    var horizontal = false
    /// `snapToInterval` of the carousel.
    var snapInterval: CGFloat?
    /// `decelerationRate="fast"`.
    var fastDeceleration = false
    /// Whether drags interrupt the loop (`onScrollBeginDrag` and the other callbacks are wired).
    var reportsDrags = false
    /// `pointerEvents`.
    var interactive = true
    /// `keyboardDismissMode="interactive"`.
    var dismissesKeyboard = false
    /// Changes whenever the content's size may have changed.
    var revision = 0
    let content: Content

    init(
        engine: FolioFeatureLibraryEngine,
        id: FolioFeatureLibraryScrollerID,
        horizontal: Bool = false,
        snapInterval: CGFloat? = nil,
        fastDeceleration: Bool = false,
        reportsDrags: Bool = false,
        interactive: Bool = true,
        dismissesKeyboard: Bool = false,
        revision: Int,
        @ViewBuilder content: () -> Content
    ) {
        self.engine = engine
        self.id = id
        self.horizontal = horizontal
        self.snapInterval = snapInterval
        self.fastDeceleration = fastDeceleration
        self.reportsDrags = reportsDrags
        self.interactive = interactive
        self.dismissesKeyboard = dismissesKeyboard
        self.revision = revision
        self.content = content()
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(engine: engine, id: id)
    }

    func makeUIView(context: Context) -> FolioFeatureLibraryScrollView {
        let scroll = FolioFeatureLibraryScrollView()
        scroll.delegate = context.coordinator
        scroll.showsVerticalScrollIndicator = false
        scroll.showsHorizontalScrollIndicator = false
        scroll.contentInsetAdjustmentBehavior = .never
        scroll.backgroundColor = .clear
        // React Native: a vertical scroll view always bounces vertically, a horizontal one horizontally.
        scroll.alwaysBounceVertical = !horizontal
        scroll.alwaysBounceHorizontal = horizontal
        if fastDeceleration { scroll.decelerationRate = .fast }
        if dismissesKeyboard { scroll.keyboardDismissMode = .interactive }

        let host = UIHostingController(rootView: content)
        host.view.backgroundColor = .clear
        host.safeAreaRegions = []
        scroll.addSubview(host.view)

        let coordinator = context.coordinator
        coordinator.host = host
        coordinator.configure(horizontal: horizontal, snapInterval: snapInterval, reportsDrags: reportsDrags, revision: revision)
        scroll.onLayout = { [weak coordinator, weak scroll] in
            guard let coordinator = coordinator, let scroll = scroll else { return }
            coordinator.relayout(scroll)
        }
        engine.register(id, scroll)
        return scroll
    }

    func updateUIView(_ scroll: FolioFeatureLibraryScrollView, context: Context) {
        let coordinator = context.coordinator
        let changed = coordinator.configure(horizontal: horizontal, snapInterval: snapInterval, reportsDrags: reportsDrags, revision: revision)
        coordinator.host?.rootView = content
        if scroll.isUserInteractionEnabled != interactive { scroll.isUserInteractionEnabled = interactive }
        engine.register(id, scroll)
        if changed { scroll.setNeedsLayout() }
        engine.applyScroll(id)
    }

    static func dismantleUIView(_ scroll: FolioFeatureLibraryScrollView, coordinator: Coordinator) {
        coordinator.engine.unregister(coordinator.id, scroll)
    }

    final class Coordinator: NSObject, UIScrollViewDelegate {
        let engine: FolioFeatureLibraryEngine
        let id: FolioFeatureLibraryScrollerID
        var host: UIHostingController<Content>?
        private var horizontal = false
        private var snapInterval: CGFloat?
        private var reportsDrags = false
        private var revision = -1
        private var needsMeasure = true
        private var measuredLength: CGFloat = 0
        private var measuredAcross: CGFloat = 0

        init(engine: FolioFeatureLibraryEngine, id: FolioFeatureLibraryScrollerID) {
            self.engine = engine
            self.id = id
        }

        /// Takes the current settings; true when the content has to be measured again.
        @discardableResult
        func configure(horizontal: Bool, snapInterval: CGFloat?, reportsDrags: Bool, revision: Int) -> Bool {
            self.horizontal = horizontal
            self.snapInterval = snapInterval
            self.reportsDrags = reportsDrags
            if self.revision != revision {
                self.revision = revision
                needsMeasure = true
                return true
            }
            return false
        }

        /// Sizes the hosted content and the scrollable area.
        func relayout(_ scroll: UIScrollView) {
            guard let host = host else { return }
            let width = scroll.bounds.width
            let height = scroll.bounds.height
            if width <= 0 || height <= 0 { return }
            let across = horizontal ? height : width
            if needsMeasure || measuredAcross != across {
                let proposal = horizontal
                    ? CGSize(width: CGFloat.greatestFiniteMagnitude, height: height)
                    : CGSize(width: width, height: CGFloat.greatestFiniteMagnitude)
                let fit = host.sizeThatFits(in: proposal)
                measuredLength = ceil(horizontal ? fit.width : fit.height)
                measuredAcross = across
                needsMeasure = false
            }
            let size = horizontal
                ? CGSize(width: max(measuredLength, width), height: height)
                : CGSize(width: width, height: measuredLength)
            let frame = CGRect(origin: .zero, size: size)
            if host.view.frame != frame { host.view.frame = frame }
            if scroll.contentSize != size { scroll.contentSize = size }
            engine.scrollerLaidOut(
                id,
                content: Double(horizontal ? size.width : size.height),
                port: Double(horizontal ? width : height)
            )
        }

        // MARK: UIScrollViewDelegate

        func scrollViewDidScroll(_ scrollView: UIScrollView) {
            if engine.applyingScroll { return }
            if id == .library { engine.libraryScrolled(to: Double(scrollView.contentOffset.y)) }
        }

        func scrollViewWillBeginDragging(_ scrollView: UIScrollView) {
            if reportsDrags { engine.scrollBegan(id) }
        }

        func scrollViewDidEndDragging(_ scrollView: UIScrollView, willDecelerate decelerate: Bool) {
            if reportsDrags { engine.scrollEnded(id) }
        }

        func scrollViewWillBeginDecelerating(_ scrollView: UIScrollView) {
            if reportsDrags { engine.scrollBegan(id, momentum: true) }
        }

        func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) {
            if reportsDrags { engine.scrollEnded(id, momentum: true) }
        }

        /// `snapToInterval` (RCTScrollView): the target moves to the next stop in the direction of the fling, or the
        /// nearest one when the finger lifts at rest.
        func scrollViewWillEndDragging(_ scrollView: UIScrollView, withVelocity velocity: CGPoint, targetContentOffset: UnsafeMutablePointer<CGPoint>) {
            guard let interval = snapInterval, interval > 0 else { return }
            let along = horizontal ? velocity.x : velocity.y
            let target = horizontal ? targetContentOffset.pointee.x : targetContentOffset.pointee.y
            let fractional = target / interval
            let index: CGFloat
            if along > 0 {
                index = ceil(fractional)
            } else if along < 0 {
                index = floor(fractional)
            } else {
                index = fractional.rounded()
            }
            let content = horizontal ? scrollView.contentSize.width : scrollView.contentSize.height
            let port = horizontal ? scrollView.bounds.width : scrollView.bounds.height
            let snapped = min(max(0, index * interval), max(0, content - port))
            if horizontal {
                targetContentOffset.pointee.x = snapped
            } else {
                targetContentOffset.pointee.y = snapped
            }
        }
    }
}
