import SwiftUI
import UIKit

// The handlers, operations and the guided loop of FolioFeatureLibrary.tsx (lines 204-437), as engine methods.
// A method without `perform` runs inside a timer or inside another handler; a public handler wraps itself in it.

extension FolioFeatureLibraryEngine {
    // MARK: - Handing the screen over (queueResume, interrupt, touches)

    /// `queueResume` (lines 204-212): two quiet seconds after the last touch the loop starts again.
    func queueResume() {
        cancel(&resumeTimer)
        let blocked = !autoPlay || reduced || !foreground || notice != nil || topicsOpen || screen != .explore
            || userOwned || touching || !dragging.isEmpty || !momentum.isEmpty
        if blocked { return }
        resumeTimer = schedule(after: 2) { [weak self] in
            guard let self = self else { return }
            self.resumeTimer = nil
            let free = self.mounted && !self.userOwned && !self.touching && self.dragging.isEmpty && self.momentum.isEmpty
            if free { self.manual = false }
        }
    }

    /// `interrupt` (lines 213-224): a person took over.
    func interrupt() {
        manual = true
        cancel(&resumeTimer)
        cancel(&headerTimer)
        headerCollapse.freeze(now: clock)
        // Once a person uses a library, later demo cycles must never reset its data.
        if screen != .explore { userOwned = true }
        queueResume()
    }

    /// The root's `onTouchStart` (line 225).
    func touchBegan() {
        perform {
            touching = true
            interrupt()
        }
    }

    /// The root's `onTouchEnd` and `onTouchCancel` (lines 226, 490): no finger is left.
    func touchEnded() {
        perform {
            touching = false
            queueResume()
        }
    }

    /// `scrollBegin` (lines 227-231).
    func scrollBegan(_ id: FolioFeatureLibraryScrollerID, momentum isMomentum: Bool = false) {
        perform {
            // Native programmatic demo scrolling also emits momentum callbacks.
            if isMomentum && !manual && !dragging.contains(id.rawValue) && !touching { return }
            if isMomentum {
                momentum.insert(id.rawValue)
            } else {
                dragging.insert(id.rawValue)
            }
            // A finger took the view: a scripted scroll no longer applies.
            motions[id] = nil
            interrupt()
        }
    }

    /// `scrollEnd` (lines 232-234).
    func scrollEnded(_ id: FolioFeatureLibraryScrollerID, momentum isMomentum: Bool = false) {
        perform {
            if isMomentum {
                momentum.remove(id.rawValue)
            } else {
                dragging.remove(id.rawValue)
            }
            queueResume()
        }
    }

    /// `onLibraryScroll` (line 355).
    func libraryScrolled(to offset: Double) {
        scrollY = offset
    }

    // MARK: - Screens

    /// `moveTo` (lines 268-274).
    func moveTo(_ next: FolioFeatureLibraryScreen) {
        if next != .explore { cancel(&resumeTimer) }
        screen = next
        let instant = reduced || !foreground
        if instant {
            appProgress.set(next == .explore ? 0 : 1)
            detailProgress.set(next == .detail ? 1 : 0)
        } else {
            appProgress.animate(to: next == .explore ? 0 : 1, duration: next == .explore ? 0.22 : 0.28, curve: .out, now: clock)
            detailProgress.animate(to: next == .detail ? 1 : 0, duration: 0.26, curve: .out, now: clock)
        }
    }

    /// `collapseHeader` (lines 275-279).
    func collapseHeader() {
        if !foreground || !mounted || touching || !dragging.isEmpty || !momentum.isEmpty { return }
        if reduced {
            headerCollapse.set(1)
        } else {
            headerCollapse.animate(to: 1, duration: 1.0, curve: .out, now: clock)
        }
    }

    /// `beginOpening` (lines 280-284).
    func beginOpening(_ duration: Double) {
        loaderLast = 0
        opening = FolioFeatureLibraryOpening(id: newOperationID(), startedAt: clock, duration: duration)
        moveTo(.loading)
    }

    /// Ends the opening operation; the loading lens keeps the progress it had reached.
    func clearOpening() {
        loaderLast = loaderProgress(at: clock)
        opening = nil
    }

    /// The opening operation's deadline (lines 323-330): the library appears, and a second later its header contracts.
    func finishOpening(_ operation: FolioFeatureLibraryOpening) {
        clearOpening()
        moveTo(.library)
        headerTimer = schedule(after: max(0, operation.deadline + 1 - clock)) { [weak self] in
            self?.headerTimer = nil
            self?.collapseHeader()
        }
    }

    /// `beginDetail` (lines 285-289).
    func beginDetail(_ book: FolioFeatureLibraryBookID, duration: Double) {
        selectedBookID = book
        detailReady = false
        detailOperation = FolioFeatureLibraryPending(id: newOperationID(), deadline: clock + duration)
        scrollTo(.detail, 0, animated: false)
        moveTo(.detail)
    }

    /// `beginGeneration` (lines 290-293).
    func beginGeneration(_ topics: [String], duration: Double, coverDelay: Double, notifyHost: Bool) {
        generating = true
        generation = FolioFeatureLibraryGeneration(id: newOperationID(), deadline: clock + duration, topics: topics, notifyHost: notifyHost, coverDelay: coverDelay)
    }

    /// The generation operation's deadline (lines 331-339): the book is created, its cover arrives later.
    func finishGeneration(_ operation: FolioFeatureLibraryGeneration) {
        generation = nil
        generated = true
        coverReady = false
        generating = false
        topicsOpen = false
        coverOperation = FolioFeatureLibraryPending(id: newOperationID(), deadline: operation.deadline + operation.coverDelay)
        if operation.notifyHost {
            onBookCreated?(operation.topics)
            notifySuccess()
        }
    }

    // MARK: - Where a library opens from and returns to

    /// The frame of a control in root coordinates: `captureOrigin`'s `measureInWindow` (lines 239-267). The feature
    /// cards and the plus button sit where the layout puts them, so their frames follow from the scroll offsets; the
    /// recent list is measured in UIKit.
    func frame(for trigger: FolioFeatureLibraryReturnOrigin) -> FolioFeatureLibraryCardFrame? {
        switch trigger {
        case .feature(let index):
            let factor = textType.factor(maxScale: 1.7, scales: true)
            let title = FolioFeatureLibraryMetrics.lineHeight(size: 22 * factor, weight: .semibold, pixelScale: pixelScale)
            let top = (insetTop - 14) + 22 + title + 14 - currentOffset(.outer)
            let left = 19 + Double(index) * (cardWidth + 14) - currentOffset(.carousel)
            return FolioFeatureLibraryCardFrame(x: left, y: top, width: cardWidth, height: 330)
        case .plus:
            let rowHeight = 62.0
            let left = width - 20 - 56
            let top = height - (insetBottom - 14) - rowHeight + (rowHeight - 56) / 2
            return FolioFeatureLibraryCardFrame(x: left, y: top, width: 56, height: 56)
        case .recent:
            return measured("recent")
        }
    }

    private func measured(_ key: String) -> FolioFeatureLibraryCardFrame? {
        guard let view = anchors[key]?.view, let root = rootView, view.window != nil else { return nil }
        let rect = view.convert(view.bounds, to: root)
        return FolioFeatureLibraryCardFrame(x: Double(rect.minX), y: Double(rect.minY), width: Double(rect.width), height: Double(rect.height))
    }

    /// `captureOrigin` (lines 239-267): remembers the frame the app surface grows from, then goes on.
    func captureOrigin(_ trigger: FolioFeatureLibraryReturnOrigin, then done: () -> Void) {
        if let found = frame(for: trigger), found.width > 0, found.height > 0 {
            origin = found
            originIsDefault = false
        }
        done()
    }

    // MARK: - Handlers of the controls

    /// `openFeature` (lines 294-303).
    func openFeature(_ index: Int, from trigger: FolioFeatureLibraryReturnOrigin? = nil) {
        perform {
            interrupt()
            clearOpening()
            detailOperation = nil
            if index != 0 {
                feature = index
                notice = index == 1 ? FolioFeatureLibraryData.placesNotice : FolioFeatureLibraryData.portraitNotice
                return
            }
            returnOrigin = trigger ?? .feature(index)
            userOwned = true
            cancel(&resumeTimer)
            feature = index
            scrollY = 0
            headerCollapse.set(0)
            let duration = reduced ? 0.15 : 0.9
            captureOrigin(returnOrigin) { beginOpening(duration) }
            impactLight()
        }
    }

    /// `openDetail` (lines 304-306).
    func openDetail(_ book: FolioFeatureLibraryBookID) {
        perform {
            interrupt()
            beginDetail(book, duration: reduced ? 0.12 : 0.65)
        }
    }

    /// `closeLibrary` (lines 307-310).
    func closeLibrary() {
        perform {
            interrupt()
            clearOpening()
            detailOperation = nil
            captureOrigin(returnOrigin) { moveTo(.explore) }
        }
    }

    /// The detail's back button (line 549).
    func backToLibrary() {
        perform {
            interrupt()
            detailOperation = nil
            moveTo(.library)
        }
    }

    /// `toggleBookmark` (lines 311-314).
    func toggleBookmark() {
        perform {
            interrupt()
            if bookmarks.contains(selectedBookID) {
                bookmarks.removeAll { $0 == selectedBookID }
            } else {
                bookmarks.append(selectedBookID)
            }
            selectionTick()
        }
    }

    /// The detail's Read button (line 543).
    func read() {
        perform {
            interrupt()
            detailOperation = nil
            detailReady = true
            scrollDirect(.detail, y: 560, animated: !reduced)
        }
    }

    /// `generate` (lines 315-319).
    func generate() {
        perform {
            if selected.isEmpty || generating { return }
            dismissKeyboard()
            topicInputActive = false
            interrupt()
            beginGeneration(selected, duration: reduced ? 0.25 : 1.6, coverDelay: reduced ? 0.12 : 0.7, notifyHost: true)
        }
    }

    /// `closeTopics` (line 320).
    func closeTopics() {
        perform {
            dismissKeyboard()
            topicInputActive = false
            interrupt()
            generation = nil
            topicsOpen = false
            generating = false
        }
    }

    /// Remix and Generate New (lines 527, 536).
    func openTopics() {
        perform {
            interrupt()
            topicsOpen = true
        }
    }

    /// Get (line 527).
    func toggleSaved() {
        perform {
            interrupt()
            saved.toggle()
        }
    }

    /// A control that shows the notice (lines 498, 501, 534).
    func openNotice(_ text: String) {
        perform {
            interrupt()
            notice = text
        }
    }

    func closeNotice() {
        perform {
            notice = nil
        }
    }

    /// A category chip (line 500).
    func selectCategory(_ category: String) {
        perform {
            interrupt()
            activeCategory = category
            selectionTick()
        }
    }

    /// The Home half of the pill (line 506).
    func scrollHome() {
        perform {
            interrupt()
            scrollDirect(.outer, y: 0, animated: !reduced)
        }
    }

    /// A topic chip (line 567).
    func toggleTopic(_ topic: String) {
        perform {
            if generating { return }
            interrupt()
            if selected.contains(topic) {
                selected.removeAll { $0 == topic }
            } else {
                selected.append(topic)
            }
            selectionTick()
        }
    }

    /// "Add yours" (line 568).
    func beginAddingTopic() {
        perform {
            if generating { return }
            interrupt()
            addingTopic = true
        }
    }

    /// `addTopic` (line 488).
    func addTopic() {
        perform {
            let value = customTopic.trimmingCharacters(in: .whitespacesAndNewlines)
            if !value.isEmpty && !selected.contains(value) { selected.append(value) }
            customTopic = ""
            addingTopic = false
        }
    }

    func setCustomTopic(_ text: String) {
        perform {
            customTopic = String(text.prefix(32))
        }
    }

    /// The topic field's `onFocus` (line 568).
    func topicFieldFocused() {
        perform {
            topicInputActive = true
            interrupt()
            scrollDirect(.topics, y: 0, toEnd: true, animated: !reduced)
        }
    }

    /// The topic field's `onBlur` (line 568).
    func topicFieldBlurred() {
        perform {
            topicInputActive = false
            if customTopic.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { addingTopic = false }
        }
    }

    // MARK: - The guided loop

    /// Runs `action` `seconds` after `base`, unless the loop was cancelled (`at` of line 401).
    private func at(_ base: Double, _ seconds: Double, _ action: @escaping () -> Void) {
        let id = schedule(after: base + seconds - clock) { [weak self] in
            guard let self = self, self.mounted else { return }
            action()
        }
        scriptTimers.append(id)
    }

    func cancelScript() {
        for id in scriptTimers {
            cancel(id)
        }
        scriptTimers.removeAll()
    }

    /// `cycle` (lines 405-433): resets the screen and lays out the 39 s of timers.
    func cycle(base: Double) {
        returnOrigin = .feature(0)
        opening = nil
        generation = nil
        coverOperation = nil
        detailOperation = nil
        screen = .explore
        feature = 0
        generated = false
        coverReady = false
        selected = []
        topicsOpen = false
        generating = false
        saved = false
        bookmarks = []
        selectedBookID = .noticing
        detailReady = false
        appProgress.set(0)
        detailProgress.set(0)
        scrollY = 0
        headerCollapse.set(0)
        scrollTo(.outer, 0, animated: false)
        scrollTo(.carousel, 0, animated: false)

        let step = cardWidth + 12
        at(base, 0.5) { [weak self] in self?.scrollTo(.carousel, step, animated: true) }
        at(base, 1.5) { [weak self] in self?.scrollTo(.carousel, step * 2, animated: true) }
        at(base, 2.5) { [weak self] in self?.scrollTo(.carousel, 0, animated: true) }
        at(base, 3.5) { [weak self] in self?.scrollTo(.outer, 240, animated: true) }
        at(base, 5.5) { [weak self] in self?.scrollTo(.outer, 480, animated: true) }
        at(base, 6.6) { [weak self] in self?.scrollTo(.outer, 900, animated: true) }
        at(base, 8) { [weak self] in self?.scrollTo(.outer, 0, animated: true) }
        at(base, 9.75) { [weak self] in
            guard let self = self else { return }
            self.captureOrigin(.feature(0)) { self.beginOpening(2.75) }
        }
        at(base, 16) { [weak self] in self?.topicsOpen = true }
        at(base, 17.25) { [weak self] in self?.selected = ["Productivity"] }
        at(base, 18.25) { [weak self] in
            self?.beginGeneration(["Productivity"], duration: 4.25, coverDelay: 1.5, notifyHost: false)
        }
        at(base, 26.5) { [weak self] in self?.beginDetail(.rituals, duration: 2.0) }
        // The reading pass of the reference: a short lift, a long read down to the author, back to the top.
        at(base, 29.8) { [weak self] in self?.scrollTo(.detail, 170, animated: true) }
        at(base, 30.6) { [weak self] in self?.scrollTo(.detail, 720, animated: true) }
        at(base, 31.8) { [weak self] in self?.scrollTo(.detail, 960, animated: true) }
        at(base, 32.8) { [weak self] in self?.scrollTo(.detail, Double.infinity, animated: true) }
        at(base, 33.8) { [weak self] in self?.scrollTo(.detail, 170, animated: true) }
        at(base, 34.6) { [weak self] in self?.scrollTo(.detail, 0, animated: true) }
        at(base, 35.5) { [weak self] in self?.moveTo(.library) }
        at(base, 36.75) { [weak self] in
            guard let self = self else { return }
            self.captureOrigin(self.returnOrigin) { self.moveTo(.explore) }
        }
        at(base, FolioFeatureLibrary.loop) { [weak self] in self?.cycle(base: base + FolioFeatureLibrary.loop) }
    }
}
