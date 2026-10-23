import QuartzCore
import UIKit

/// The block's brain, on the main thread: the selection engine, the scripted finger of the demo, the
/// rows' layout and the frame loop. A real finger and the demo call the same functions:
/// `enter(row)`, `dragBegin(row)`, `dragMove()` as `fingerY` changes, `dragEnd()`, `toggle(row)`, `exit()`.
/// During a drag a row takes the anchor's target state while it is inside [anchor, current] and gets
/// its old state back when it leaves the range, so dragging back undoes.
@MainActor
final class DragMultiSelectEngine {
    typealias Layout = DragMultiSelectLayout
    typealias Script = DragMultiSelectDemoScript

    let state = DragMultiSelectState()
    let clock: DragMultiSelectClock
    let haptics = DragMultiSelectHaptics()

    /// 0 add button … 1 toolbar.
    let shown: DragMultiSelectNumber
    let navFade: DragMultiSelectNumber
    /// Anticipation: how far the band has grown under a finger that is about to long-press.
    let pressP: DragMultiSelectNumber
    let ghostO: DragMultiSelectNumber
    let ghostPress: DragMultiSelectNumber
    /// The trash button's bounce on delete, 0 to 1.
    let trashKick: DragMultiSelectNumber

    // MARK: What the view tells the engine

    var autoPlay = false
    var reduceMotion = false
    var width = 0.0
    var windowHeight = 0.0
    var topInset = 0.0
    var bottomInset = 0.0
    /// Where the first row starts in the list: it sits on the nav bar's line, hidden under it while browsing.
    var listTop = 0.0
    /// The list's height.
    var viewport = 0.0
    weak var scrollView: UIScrollView?
    /// Tells the list its content got shorter or taller.
    var contentChanged: (() -> Void)?

    // MARK: Rows

    var prepared = false
    /// The rows that are still in the list, in order.
    var live: [DragMultiSelectRow] = []

    // MARK: Selection

    /// 1 for a selected row, by index in `live`.
    var sel: [Int] = []
    /// 0 browsing, 1 selecting.
    var mode = 0
    var scrollY = 0.0
    var lockScroll = false {
        didSet { if lockScroll != oldValue { scrollView?.isScrollEnabled = !lockScroll } }
    }
    /// In list coordinates.
    var fingerY = 0.0
    var dragging = false
    var anchor = 0
    var target = 1
    var current = -1
    var base: [Int] = []
    /// Edge auto-scroll speed, -1 to 1.
    var lastSpeed = 0.0
    /// pt/s the list keeps after a drag that ends while auto-scrolling.
    var coast = 0.0
    var ambientClock = 0.0

    // MARK: Autoplay

    var t = 0.0
    var paused = true
    var touching = false
    var tidy = -1.0
    var tidyFrom = 0.0
    var scriptDown = false
    /// The absolute display-link timestamp at which the current 13 s loop began.
    var scriptEpoch: Double?
    var scriptCycle = 0
    /// Absolute times keep the 2 s user pause and 0.6 s tidy independent of frame rate.
    var lastTouchEndedAt: Double?
    var tidyStartedAt: Double?

    // MARK: Touch

    var session: Session?

    // MARK: Frame loop

    private var link: CADisplayLink?
    private var lastTimestamp = 0.0

    init() {
        let clock = DragMultiSelectClock()
        self.clock = clock
        shown = DragMultiSelectNumber(0, clock: clock)
        navFade = DragMultiSelectNumber(0, clock: clock)
        pressP = DragMultiSelectNumber(0, clock: clock)
        ghostO = DragMultiSelectNumber(0, clock: clock)
        ghostPress = DragMultiSelectNumber(0, clock: clock)
        trashKick = DragMultiSelectNumber(0, clock: clock)
        clock.onEnqueue = { [weak self] in self?.wakeDisplayLink() }
    }

    // MARK: Setup

    func configure(width: Double, height: Double, topInset: Double, bottomInset: Double, autoPlay: Bool, reduceMotion: Bool) {
        let changed = self.width != width || windowHeight != height || self.topInset != topInset || self.bottomInset != bottomInset
        let playbackChanged = self.autoPlay != autoPlay || self.reduceMotion != reduceMotion
        self.width = width
        windowHeight = height
        self.topInset = topInset
        self.bottomInset = bottomInset
        self.autoPlay = autoPlay
        self.reduceMotion = reduceMotion
        if !prepared {
            prepare()
        } else if changed {
            relayoutInPlace()
        }
        if playbackChanged {
            scriptEpoch = nil
            lastTouchEndedAt = nil
            tidyStartedAt = nil
            paused = !autoPlay || reduceMotion
            if !autoPlay || reduceMotion { resetScriptToRest() }
        }
        if state.width != width { state.width = width }
        refreshDisplayLinkState()
    }

    private func prepare() {
        prepared = true
        paused = !autoPlay
        listTop = topInset + Layout.navTop + Layout.nav / 2 - Layout.row / 2
        live = DragMultiSelectTasks.all.enumerated().map { index, task in
            makeRow(task, order: index, liveIndex: index)
        }
        sel = Array(repeating: 0, count: live.count)
        state.mounted = live
    }

    func makeRow(_ task: DragMultiSelectTask, order: Int, liveIndex: Int) -> DragMultiSelectRow {
        DragMultiSelectRow(
            task: task,
            order: order,
            liveIndex: liveIndex,
            y: listTop + Layout.row * Double(liveIndex),
            circleIn: Double(mode),
            clock: clock
        )
    }

    /// The screen changed size: rows go to their places at once.
    private func relayoutInPlace() {
        listTop = topInset + Layout.navTop + Layout.nav / 2 - Layout.row / 2
        for row in live {
            row.targetY = listTop + Layout.row * Double(row.liveIndex)
            row.y.set(row.targetY)
        }
    }

    var contentHeight: Double {
        listTop + Layout.row * Double(live.count) + bottomInset + Layout.toolbarHeight + DragMultiSelectTokens.Component.listBottomPadding
    }

    /// Room for every task, so the hosted rows never need resizing.
    var maxContentHeight: Double {
        listTop + Layout.row * Double(DragMultiSelectTasks.all.count) + bottomInset + Layout.toolbarHeight + DragMultiSelectTokens.Component.listBottomPadding
    }

    // MARK: Frame loop

    func startLoop() {
        if link != nil {
            refreshDisplayLinkState()
            return
        }
        lastTimestamp = 0
        let displayLink = CADisplayLink(target: DragMultiSelectLinkTarget(self), selector: #selector(DragMultiSelectLinkTarget.tick(_:)))
        displayLink.add(to: .main, forMode: .common)
        link = displayLink
        refreshDisplayLinkState()
    }

    func stopLoop() {
        link?.invalidate()
        link = nil
        lastTimestamp = 0
    }

    func frame(_ timestamp: Double) {
        let elapsed = lastTimestamp == 0 ? 0 : max(timestamp - lastTimestamp, 0)
        lastTimestamp = timestamp
        clock.now = timestamp
        clock.step()
        sweepExitedRows()
        // Physics keeps to short steps; the demo's own clock follows the wall clock. This is below
        // the 0.1 s hard ceiling even when the process missed several display refreshes.
        let dt = min(elapsed, DragMultiSelectTokens.Component.maximumFrameDelta)
        checkLongPress(at: timestamp)
        autoScroll(dt: dt, silent: !paused)
        // Momentum after an auto-scroll drag: exponential decay.
        if !dragging && coast != 0 {
            let maxScroll = max(contentHeight - viewport, 0)
            let next = min(max(scrollY + coast * dt, 0), maxScroll)
            scrollY = next
            scrollTo(next)
            let v = coast * exp(-dt * DragMultiSelectSelect.coastDecay)
            coast = abs(v) < DragMultiSelectSelect.coastStop || next == 0 || next == maxScroll ? 0 : v
        }
        if autoPlay && viewport != 0 && !reduceMotion {
            playScript(at: timestamp)
        }
        // Ambient: the add button breathes; in the demo on the wall-clock loop phase so the seam is exact.
        ambientClock = autoPlay && !paused ? t : ambientClock + dt
        let breath = reduceMotion ? 0 : sin(2 * Double.pi * ambientClock / DragMultiSelectTiming.breathPeriod)
        if state.breath != breath { state.breath = breath }
        refreshDisplayLinkState()
    }

    /// A touch can arrive while an idle link is paused. Resume it synchronously so long-press timing
    /// and press feedback begin on the same event.
    func wakeDisplayLink() {
        guard let link, link.isPaused else { return }
        link.isPaused = false
        lastTimestamp = 0
    }

    func refreshDisplayLinkState() {
        guard let link else { return }
        let autoplayNeedsFrames = autoPlay && !reduceMotion
        // The source's FAB breath is intentional ambient motion, so a normal-motion rest state is
        // not idle. Reduce Motion removes it and lets the display link sleep between interactions.
        let ambientNeedsFrames = !reduceMotion
        let interactionNeedsFrames = touching || session != nil || dragging || coast != 0
        link.isPaused = !(autoplayNeedsFrames || ambientNeedsFrames || interactionNeedsFrames || clock.hasRunningAnimations)
        if link.isPaused { lastTimestamp = 0 }
    }

    // MARK: Scroll

    func scrollTo(_ y: Double) {
        scrollView?.setContentOffset(CGPoint(x: 0, y: y), animated: false)
    }

    /// The list scrolled by itself (a finger's flick): remember where it is unless a drag drives it.
    func scrolled(to y: Double) {
        if !dragging { scrollY = y }
    }

    func haptic(_ kind: DragMultiSelectHaptics.Kind, silent: Bool) {
        if !silent { haptics.play(kind) }
    }
}

/// CADisplayLink keeps its target alive, so it gets this thin one that holds the engine weakly.
final class DragMultiSelectLinkTarget: NSObject {
    private weak var engine: DragMultiSelectEngine?

    @MainActor
    init(_ engine: DragMultiSelectEngine) {
        self.engine = engine
    }

    @MainActor @objc func tick(_ link: CADisplayLink) {
        // `targetTimestamp` is the wall-clock instant represented by the frame being drawn. Script
        // phase comes from it directly; dropped frames therefore never lengthen the 13 s period.
        engine?.frame(link.targetTimestamp)
    }
}
