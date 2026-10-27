import SwiftUI
import UIKit

// The block's scroll views: where the guided loop scrolls them, and what the engine knows of them.

extension FolioFeatureLibraryEngine {
    func register(_ id: FolioFeatureLibraryScrollerID, _ view: UIScrollView) {
        if scrollViews[id]?.view === view { return }
        scrollViews[id] = FolioFeatureLibraryWeakScrollView(view)
    }

    func unregister(_ id: FolioFeatureLibraryScrollerID, _ view: UIScrollView) {
        if scrollViews[id]?.view === view { scrollViews[id] = nil }
    }

    private func scrollView(_ id: FolioFeatureLibraryScrollerID) -> UIScrollView? {
        scrollViews[id]?.view
    }

    func isHorizontal(_ id: FolioFeatureLibraryScrollerID) -> Bool {
        id == .carousel || id == .categories
    }

    /// The furthest offset the view can reach, once it has been laid out.
    func scrollLimit(_ id: FolioFeatureLibraryScrollerID) -> Double? {
        guard let view = scrollView(id) else { return nil }
        let horizontal = isHorizontal(id)
        let content = horizontal ? view.contentSize.width : view.contentSize.height
        let port = horizontal ? view.bounds.width : view.bounds.height
        if content <= 0 || port <= 0 { return nil }
        return max(0, Double(content - port))
    }

    /// Where the view is, or where a scripted scroll has it at this time.
    func currentOffset(_ id: FolioFeatureLibraryScrollerID) -> Double {
        if let motion = motions[id] { return motion.value(at: clock, limit: scrollLimit(id)) }
        if let view = scrollView(id) {
            return isHorizontal(id) ? Double(view.contentOffset.x) : Double(view.contentOffset.y)
        }
        return 0
    }

    /// `ScrollView.scrollTo` of the guided loop. React Native clamps the target to the content; the system animates
    /// an animated one, which is estimated here as 0.4 s on UIKit's ease in-out curve (see PORT-NOTES).
    func scrollTo(_ id: FolioFeatureLibraryScrollerID, _ value: Double, animated: Bool) {
        let from = currentOffset(id)
        motions[id] = FolioFeatureLibraryScrollMotion(from: from, to: value, start: clock, duration: animated ? 0.4 : 0)
        applyScroll(id)
    }

    /// `scrollTo` / `scrollToEnd` from a handler: the system scrolls the view itself.
    func scrollDirect(_ id: FolioFeatureLibraryScrollerID, y: Double, toEnd: Bool = false, animated: Bool) {
        motions[id] = nil
        guard let view = scrollView(id) else { return }
        let limit = max(0, Double(view.contentSize.height - view.bounds.height))
        let target = toEnd ? limit : min(max(0, y), limit)
        view.setContentOffset(CGPoint(x: view.contentOffset.x, y: CGFloat(target)), animated: animated)
    }

    /// Writes the scripted position of one scroll view.
    func applyScroll(_ id: FolioFeatureLibraryScrollerID) {
        guard let motion = motions[id], let view = scrollView(id) else { return }
        let horizontal = isHorizontal(id)
        let content = horizontal ? view.contentSize.width : view.contentSize.height
        let port = horizontal ? view.bounds.width : view.bounds.height
        // Not laid out yet: keep the motion for when it is.
        if port <= 0 || content <= 0 { return }
        if view.isTracking || view.isDragging || view.isDecelerating {
            motions[id] = nil
            return
        }
        let limit = max(0, Double(content - port))
        let value = motion.value(at: clock, limit: limit)
        var offset = view.contentOffset
        if horizontal {
            offset.x = CGFloat(value)
        } else {
            offset.y = CGFloat(value)
        }
        if offset != view.contentOffset {
            applyingScroll = true
            view.contentOffset = offset
            applyingScroll = false
        }
        if motion.isDone(at: clock) { motions[id] = nil }
    }

    func applyAllScrolls() {
        for id in Array(motions.keys) {
            applyScroll(id)
        }
    }

    /// Called by a scroller after it has measured its content.
    func scrollerLaidOut(_ id: FolioFeatureLibraryScrollerID, content: Double, port: Double) {
        overflows[id] = content > port + 4
        contentLengths[id] = content
        applyScroll(id)
    }

    func hasOverflow(_ id: FolioFeatureLibraryScrollerID) -> Bool {
        overflows[id] ?? false
    }
}
