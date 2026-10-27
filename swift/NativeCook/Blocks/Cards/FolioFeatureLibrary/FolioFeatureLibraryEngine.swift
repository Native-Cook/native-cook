import SwiftUI
import UIKit

/// What the view tells the engine about the screen, every frame.
struct FolioFeatureLibraryEnvironment {
    /// The window's size: `useWindowDimensions()`.
    var width: Double
    var height: Double
    /// `useSafeAreaInsets()`.
    var insetTop: Double
    var insetBottom: Double
    var pixelScale: Double
    var type: FolioFeatureLibraryType
    var reduced: Bool
    var reduceTransparency: Bool
    var autoPlay: Bool
    var previewTime: Double?
    var onBookCreated: (([String]) -> Void)?
}

/// The ids of the block's scroll views.
enum FolioFeatureLibraryScrollerID: String {
    case outer
    case carousel
    case categories
    case library
    case detail
    case topics
    case notice
}

/// A scripted `scrollTo`: it plays from where the view is to a target (or the end of its content) over a fixed time.
struct FolioFeatureLibraryScrollMotion {
    var from: Double
    /// `.infinity` stands for `scrollToEnd`.
    var to: Double
    var start: Double
    var duration: Double

    func value(at time: Double, limit: Double?) -> Double {
        let top = limit ?? Double.greatestFiniteMagnitude
        let target = min(to, top)
        let origin = min(from, top)
        if duration <= 0 { return max(0, target) }
        let p = FolioFeatureLibraryMath.clamp01((time - start) / duration)
        let eased = FolioFeatureLibraryCurve.systemEaseInOut.value(p)
        return max(0, origin + (target - origin) * eased)
    }

    func isDone(at time: Double) -> Bool {
        duration <= 0 || time >= start + duration
    }
}

/// A weak reference to a UIKit view the engine reads.
final class FolioFeatureLibraryWeakView {
    weak var view: UIView?
    init(_ view: UIView) { self.view = view }
}

/// A weak reference to one of the block's scroll views.
final class FolioFeatureLibraryWeakScrollView {
    weak var view: UIScrollView?
    init(_ view: UIScrollView) { self.view = view }
}

/// The behaviour of FolioFeatureLibrary.tsx's component: its state, refs, effects, timers and shared values, moved
/// onto a clock the engine steps itself.
///
/// React Native runs the loop from `setTimeout`s and Reanimated animations started at those moments. Here every
/// timer is an entry in a queue ordered by time, and `advance(to:)` runs the entries up to a time, each at its own
/// moment, so a value such as a tween's start is exactly the instant React Native would have started it. That makes
/// every frame a function of the loop time: `previewTime` is one `advance` from zero to that time, and the loop
/// plays the same way from the real clock. Touch changes go through the same state, stamped with the real clock.
///
/// The view calls `step(date:environment:)` once per frame and reads the state and `FolioFeatureLibraryFrame`.
final class FolioFeatureLibraryEngine {
    // MARK: Configuration

    var autoPlay = false
    var previewTime: Double?
    var onBookCreated: (([String]) -> Void)?
    var frozen: Bool { previewTime != nil }

    // MARK: Environment, refreshed by `step`

    private(set) var width = 393.0
    private(set) var height = 852.0
    private(set) var insetTop = 59.0
    private(set) var insetBottom = 34.0
    private(set) var pixelScale = 3.0
    private(set) var textType = FolioFeatureLibraryType()
    private(set) var reduced = false
    private(set) var reduceTransparency = false
    private(set) var foreground = true
    var keyboardHeight = 0.0

    var cardWidth: Double { min(248, width * 0.63) }

    // MARK: Clock and timers

    private(set) var clock = 0.0
    private var epoch: Double?
    private var pausedAt: Double?
    private var pausedTotal = 0.0

    private struct Scheduled {
        let id: Int
        let time: Double
        let action: () -> Void
    }

    private var queue: [Scheduled] = []
    private var nextID = 1

    // MARK: React state (the useState values of the component)

    var screen: FolioFeatureLibraryScreen = .explore
    var topicsOpen = false
    var selected: [String] = []
    var customTopic = ""
    var topicInputActive = false
    var generating = false
    var generated = false
    var coverReady = false
    var detailReady = false
    var saved = false
    var selectedBookID: FolioFeatureLibraryBookID = .noticing
    var bookmarks: [FolioFeatureLibraryBookID] = []
    var manual = false
    var activeCategory = "Health & Fitness"
    var addingTopic = false
    var authorReady = false
    var notice: String?
    var feature = 0
    var introHeight = 310.0
    var opening: FolioFeatureLibraryOpening?
    var generation: FolioFeatureLibraryGeneration?
    var coverOperation: FolioFeatureLibraryPending?
    var detailOperation: FolioFeatureLibraryPending?

    // MARK: Refs

    var returnOrigin: FolioFeatureLibraryReturnOrigin = .feature(0)
    var userOwned = false
    var touching = false
    var dragging = Set<String>()
    var momentum = Set<String>()
    var mounted = true

    // MARK: Shared values

    var appProgress = FolioFeatureLibraryTween()
    var detailProgress = FolioFeatureLibraryTween()
    var headerCollapse = FolioFeatureLibraryTween()
    /// The library list's scroll offset (`scrollY`).
    var scrollY = 0.0
    /// The frame the app surface opens from and closes to (`origin` and `originFrame`).
    var origin = FolioFeatureLibraryCardFrame(x: 16, y: 137, width: 248, height: 292)
    var originIsDefault = true
    /// The loading lens' progress once its operation has ended (the shared value stays where it was).
    var loaderLast = 0.0
    /// `shimmer`: a linear 900 ms ramp that repeats from where it started.
    private var shimmerRunning = false
    private var shimmerFrom = 0.0
    private var shimmerStart = 0.0
    private var shimmerFrozen = 0.0
    /// The topic sheet's and the notice's presentation (`Modal`).
    var sheet = FolioFeatureLibraryTween()
    var noticeFade = FolioFeatureLibraryTween()

    // MARK: Timers of the effects

    private var openingTimer: Int?
    private var generationTimer: Int?
    private var coverTimer: Int?
    private var detailTimer: Int?
    private var authorTimer: Int?
    var resumeTimer: Int?
    var headerTimer: Int?
    var scriptTimers: [Int] = []
    private var nextOperationID = 1

    // MARK: Scrolling

    var scrollViews: [FolioFeatureLibraryScrollerID: FolioFeatureLibraryWeakScrollView] = [:]
    var motions: [FolioFeatureLibraryScrollerID: FolioFeatureLibraryScrollMotion] = [:]
    /// Whether each scroller's content is taller than its viewport (the blur edges show only then).
    var overflows: [FolioFeatureLibraryScrollerID: Bool] = [:]
    /// The measured length of each scroller's content along its axis.
    var contentLengths: [FolioFeatureLibraryScrollerID: Double] = [:]
    var applyingScroll = false

    /// Bumped whenever a change can alter the height of a scroller's content, so the scrollers measure again.
    var layoutRevision = 0

    // MARK: Entering animations

    /// When each conditionally rendered element appeared (`entering` of Reanimated starts then).
    var since: [String: Double] = [:]
    var chipTarget = FolioFeatureLibraryChipLayout()
    var chipFrom = FolioFeatureLibraryChipLayout()
    var chipSince = 0.0
    var chipInputs: FolioFeatureLibraryChipInputs?
    var textWidths: [String: Double] = [:]
    /// The measured height of the generated book's card plus its margin: how far the other card slides down.
    var bookCardHeight = 141.0
    private var generatedBefore = false
    private var libraryMountedBefore = true

    /// Views whose frames are measured in UIKit (`measureInWindow`).
    var anchors: [String: FolioFeatureLibraryWeakView] = [:]
    weak var rootView: UIView?

    // MARK: Haptics

    private lazy var lightImpact = UIImpactFeedbackGenerator(style: .light)
    private lazy var selectionFeedback = UISelectionFeedbackGenerator()
    private lazy var notificationFeedback = UINotificationFeedbackGenerator()

    // MARK: Effects' dependencies

    private struct EffectDeps: Equatable {
        var opening: Int?
        var generation: Int?
        var cover: Int?
        var detail: Int?
        var detailReady: Bool
        var reduced: Bool
        var manual: Bool
        var autoPlay: Bool
        var foreground: Bool
        var notice: String?
        var screen: FolioFeatureLibraryScreen
        var topicsOpen: Bool
        var coverReady: Bool
        var generated: Bool
        var generating: Bool
    }

    private var deps: EffectDeps?

    /// Everything that can change the height of a scroller's content.
    private struct LayoutKey: Equatable {
        var screen: FolioFeatureLibraryScreen
        var generated: Bool
        var coverReady: Bool
        var detailReady: Bool
        var authorReady: Bool
        var selected: [String]
        var category: String
        var feature: Int
        var adding: Bool
        var custom: String
        var notice: String?
        var saved: Bool
        var bookmarked: Bool
        var book: FolioFeatureLibraryBookID
        var topicsOpen: Bool
        var introHeight: Double
        var scale: Double
        var width: Double
        var height: Double
        var reduced: Bool
    }

    private var layoutKey: LayoutKey?

    // MARK: - Frame step

    /// Called once per frame: refreshes the environment, runs everything due up to this frame and applies the
    /// scripted scroll positions.
    func step(date: Date, environment env: FolioFeatureLibraryEnvironment) -> FolioFeatureLibraryFrame {
        configure(env)
        let wall = date.timeIntervalSinceReferenceDate
        if epoch == nil { epoch = wall }
        let target: Double
        if let preview = previewTime {
            target = max(0, preview)
        } else {
            target = wall - (epoch ?? wall) - pausedTotal
        }
        commit()
        advance(to: target)
        // The elements that came or went with this time (a sheet that has slid away, a chip that has settled).
        commit()
        applyAllScrolls()
        return FolioFeatureLibraryFrame(engine: self)
    }

    private func configure(_ env: FolioFeatureLibraryEnvironment) {
        width = env.width
        height = env.height
        insetTop = env.insetTop
        insetBottom = env.insetBottom
        pixelScale = env.pixelScale
        textType = env.type
        reduced = env.reduced
        reduceTransparency = env.reduceTransparency
        autoPlay = env.autoPlay
        previewTime = env.previewTime
        onBookCreated = env.onBookCreated
        if originIsDefault {
            origin = FolioFeatureLibraryCardFrame(x: 16, y: insetTop + 78, width: cardWidth, height: 292)
        }
    }

    /// The real clock in loop time, for a touch handler.
    func wallTime() -> Double {
        let wall = Date().timeIntervalSinceReferenceDate
        return wall - (epoch ?? wall) - pausedTotal
    }

    /// Runs a touch-driven change: first everything that is due, then the change, then the effects.
    func perform(_ body: () -> Void) {
        if frozen { return }
        advance(to: wallTime())
        body()
        commit()
    }

    /// `AppState`: the clock stops while the app is away (React Native's timers stop with it).
    func setForeground(_ active: Bool) {
        let wall = Date().timeIntervalSinceReferenceDate
        if active == foreground { return }
        if !active {
            pausedAt = wall
        } else if let started = pausedAt {
            pausedTotal += wall - started
            pausedAt = nil
        }
        foreground = active
        if !active {
            touching = false
            dragging.removeAll()
            momentum.removeAll()
            cancel(&headerTimer)
            appProgress.freeze(now: clock)
            detailProgress.freeze(now: clock)
            headerCollapse.freeze(now: clock)
        } else {
            appProgress.set(screen == .explore ? 0 : 1)
            detailProgress.set(screen == .detail ? 1 : 0)
        }
        commit()
    }

    // MARK: - Timers

    @discardableResult
    func schedule(after delay: Double, _ action: @escaping () -> Void) -> Int {
        let id = nextID
        nextID += 1
        let item = Scheduled(id: id, time: clock + max(0, delay), action: action)
        var index = queue.count
        while index > 0 && queue[index - 1].time > item.time {
            index -= 1
        }
        queue.insert(item, at: index)
        return id
    }

    func cancel(_ id: Int?) {
        guard let id = id else { return }
        queue.removeAll { $0.id == id }
    }

    func cancel(_ id: inout Int?) {
        if let value = id { queue.removeAll { $0.id == value } }
        id = nil
    }

    /// Runs every timer due by `target`, each at its own moment.
    func advance(to target: Double) {
        if target < clock { return }
        while let next = queue.first, next.time <= target {
            queue.removeFirst()
            clock = max(clock, next.time)
            next.action()
            commit()
        }
        clock = target
    }

    func newOperationID() -> Int {
        let id = nextOperationID
        nextOperationID += 1
        return id
    }

    // MARK: - Effects

    private func currentDeps() -> EffectDeps {
        EffectDeps(
            opening: opening?.id,
            generation: generation?.id,
            cover: coverOperation?.id,
            detail: detailOperation?.id,
            detailReady: detailReady,
            reduced: reduced,
            manual: manual,
            autoPlay: autoPlay,
            foreground: foreground,
            notice: notice,
            screen: screen,
            topicsOpen: topicsOpen,
            coverReady: coverReady,
            generated: generated,
            generating: generating
        )
    }

    /// What React does after a render: every effect whose dependencies changed is cleaned up, then run again, in the
    /// order they are declared. An effect that sets state makes another pass.
    func commit() {
        var passes = 0
        while passes < 8 {
            passes += 1
            let next = currentDeps()
            let previous = deps
            if previous == next { break }
            deps = next
            let first = previous == nil
            let old = previous ?? next

            let openingChanged = first || old.opening != next.opening
            let generationChanged = first || old.generation != next.generation
            let coverChanged = first || old.cover != next.cover
            let authorChanged = first || old.detailReady != next.detailReady || old.reduced != next.reduced
            let detailChanged = first || old.detail != next.detail
            let resumeChanged = first || old.manual != next.manual || old.autoPlay != next.autoPlay
                || old.foreground != next.foreground || old.notice != next.notice || old.reduced != next.reduced
                || old.screen != next.screen || old.topicsOpen != next.topicsOpen
            let shimmerChanged = first || old.coverReady != next.coverReady || old.foreground != next.foreground
                || old.generated != next.generated || old.generating != next.generating
                || old.reduced != next.reduced || old.screen != next.screen
            let sheetChanged = first || old.topicsOpen != next.topicsOpen
            let noticeChanged = first || (old.notice == nil) != (next.notice == nil)
            let autoplayChanged = first || old.autoPlay != next.autoPlay || old.manual != next.manual
                || old.reduced != next.reduced || old.foreground != next.foreground || old.notice != next.notice

            // Cleanups.
            if openingChanged { cancel(&openingTimer) }
            if generationChanged { cancel(&generationTimer) }
            if coverChanged { cancel(&coverTimer) }
            if authorChanged { cancel(&authorTimer) }
            if detailChanged { cancel(&detailTimer) }
            if resumeChanged { cancel(&resumeTimer) }
            if shimmerChanged {
                let current = shimmerValue(at: clock)
                shimmerRunning = false
                shimmerFrozen = current
            }
            if autoplayChanged {
                cancelScript()
                headerCollapse.freeze(now: clock)
            }

            // Effects.
            if openingChanged, let operation = opening {
                openingTimer = schedule(after: max(0, operation.deadline - clock)) { [weak self] in
                    self?.openingTimer = nil
                    self?.finishOpening(operation)
                }
            }
            if generationChanged, let operation = generation {
                generationTimer = schedule(after: max(0, operation.deadline - clock)) { [weak self] in
                    self?.generationTimer = nil
                    self?.finishGeneration(operation)
                }
            }
            if coverChanged, let operation = coverOperation {
                coverTimer = schedule(after: max(0, operation.deadline - clock)) { [weak self] in
                    guard let self = self else { return }
                    self.coverTimer = nil
                    self.coverOperation = nil
                    self.coverReady = true
                }
            }
            if authorChanged {
                let ready = detailReady
                authorTimer = schedule(after: ready && !reduced ? 4.4 : 0) { [weak self] in
                    self?.authorTimer = nil
                    self?.authorReady = ready
                }
            }
            if detailChanged, let operation = detailOperation {
                detailTimer = schedule(after: max(0, operation.deadline - clock)) { [weak self] in
                    guard let self = self else { return }
                    self.detailTimer = nil
                    self.detailOperation = nil
                    self.detailReady = true
                }
            }
            if resumeChanged && manual {
                queueResume()
            }
            if shimmerChanged {
                let active = !reduced && foreground && (generating || screen == .loading || (generated && !coverReady))
                if active {
                    shimmerRunning = true
                    shimmerFrom = shimmerFrozen
                    shimmerStart = clock
                } else {
                    shimmerFrozen = 0
                }
            }
            if sheetChanged {
                let duration = reduced ? 0.3 : 0.4
                sheet.animate(to: topicsOpen ? 1 : 0, duration: duration, curve: reduced ? .linear : .modal, now: clock)
            }
            if noticeChanged {
                noticeFade.animate(to: notice == nil ? 0 : 1, duration: 0.3, curve: .linear, now: clock)
            }
            if autoplayChanged && autoPlay && !manual && !reduced && foreground && notice == nil && !userOwned {
                cycle(base: clock)
            }
        }
        syncMounts()
        updateChipLayout()
        let key = LayoutKey(
            screen: screen, generated: generated, coverReady: coverReady, detailReady: detailReady,
            authorReady: authorReady, selected: selected, category: activeCategory, feature: feature,
            adding: addingTopic, custom: customTopic, notice: notice, saved: saved, bookmarked: bookmarked,
            book: selectedBookID, topicsOpen: topicsOpen, introHeight: introHeight, scale: textType.scale,
            width: width, height: height, reduced: reduced
        )
        if key != layoutKey {
            layoutKey = key
            layoutRevision += 1
        }
    }

    // MARK: - Shimmer

    func shimmerValue(at time: Double) -> Double {
        if !shimmerRunning { return shimmerFrozen }
        let cycles = max(0, time - shimmerStart) / 0.9
        let fraction = cycles - cycles.rounded(.down)
        return shimmerFrom + (1 - shimmerFrom) * fraction
    }

    // MARK: - Derived state

    var statusText: String? {
        if generating || (generated && !coverReady) { return "Creating content" }
        if screen == .detail && !authorReady { return detailReady ? "Searching images" : "Creating content" }
        return nil
    }

    var topicList: [String] {
        selected.isEmpty ? FolioFeatureLibraryData.topics : FolioFeatureLibraryData.topics + FolioFeatureLibraryData.moreTopics
    }

    /// `[...topicList, ...selected.filter(item => !topicList.includes(item))]`.
    var topicItems: [String] {
        let list = topicList
        return list + selected.filter { !list.contains($0) }
    }

    var libraryVisible: Bool { screen == .library && !topicsOpen && notice == nil }
    var detailVisible: Bool { screen == .detail && !topicsOpen && notice == nil }
    var exploreVisible: Bool { screen == .explore && notice == nil }
    var bookmarked: Bool { bookmarks.contains(selectedBookID) }

    /// The topic sheet's `Modal` renders its content while it is open and while it slides away.
    func topicsMounted(at time: Double) -> Bool {
        topicsOpen || sheet.value(at: time) > 0.0001
    }

    var noticeMounted: Bool {
        notice != nil || noticeFade.value(at: clock) > 0.0001
    }

    /// The loading lens' progress (`OpeningCollection`'s `progress` shared value).
    func loaderProgress(at time: Double) -> Double {
        guard let operation = opening else { return reduced ? 1 : loaderLast }
        if reduced { return 1 }
        return FolioFeatureLibraryMath.clamp01((time - operation.startedAt) / operation.duration)
    }

    // MARK: - Mount times

    private func markMounted(_ key: String, _ on: Bool) {
        if on {
            if since[key] == nil { since[key] = clock }
        } else {
            since[key] = nil
        }
    }

    /// Notes when each conditionally rendered element appears, so its entering animation can be a function of time.
    private func syncMounts() {
        let libraryMounted = screen != .loading
        // The other book's card slides down only when the new card is inserted into a library that was already there.
        if generated && !generatedBefore && libraryMounted && libraryMountedBefore { since["bookShift"] = clock }
        if !generated { since["bookShift"] = nil }
        generatedBefore = generated
        libraryMountedBefore = libraryMounted
        markMounted("status", libraryMounted && statusText != nil)
        markMounted("book", libraryMounted && generated)
        markMounted("cover", libraryMounted && generated && coverReady)
        markMounted("detailText", detailReady)
        markMounted("author", (detailReady || reduced) && authorReady)
        markMounted("heart", bookmarked)
        let sheetMounted = topicsMounted(at: clock)
        markMounted("more", sheetMounted && !selected.isEmpty)
        for key in Array(since.keys) where key.hasPrefix("check:") {
            let topic = String(key.dropFirst(6))
            if !sheetMounted || !selected.contains(topic) { since[key] = nil }
        }
        if sheetMounted {
            for topic in selected {
                markMounted("check:\(topic)", true)
            }
        }
    }

    /// 0 to 1: how far an `entering` animation of `duration` seconds, started `delay` seconds after the element
    /// appeared, has come. Reduced motion shows the element at once.
    func entering(_ key: String, duration: Double, delay: Double = 0, curve: FolioFeatureLibraryCurve = .inOutQuad, at time: Double) -> Double {
        if reduced { return 1 }
        guard let start = since[key] else { return 1 }
        return curve.value((time - start - delay) / duration)
    }

    /// The same for `ZoomIn.springify()`.
    func zoom(_ key: String, spring: FolioFeatureLibrarySpring, at time: Double) -> Double {
        if reduced { return 1 }
        guard let start = since[key] else { return 1 }
        return spring.value(time - start)
    }

    /// `onLayout` of the header content (line 523).
    func setIntroHeight(_ measured: Double) {
        if abs(measured - introHeight) > 1 { introHeight = measured }
    }

    // MARK: - Haptics

    func impactLight() {
        lightImpact.impactOccurred()
    }

    func selectionTick() {
        selectionFeedback.selectionChanged()
    }

    func notifySuccess() {
        notificationFeedback.notificationOccurred(.success)
    }

    func dismissKeyboard() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }
}
