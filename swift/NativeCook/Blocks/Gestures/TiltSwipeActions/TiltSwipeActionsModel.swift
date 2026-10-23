import SwiftUI
import Observation

/// The scripted finger as state, not events: several writes can land in one frame, so the row compares
/// `down` with its own state to know whether to begin or end (as in the React Native block).
struct TiltSwipeActionsScriptTouch {
    var seq = 0
    var key = ""
    var down = false
    var tx = 0.0
    var vx = 0.0
}

/// Everything the screen shows and every rule of it: the rows, the Undo pill, the tabs, the menu, the
/// autoplay script and the real finger. The views only read it.
@MainActor
@Observable
final class TiltSwipeActionsModel {
    // MARK: Read by the views

    var rows: [TiltSwipeActionsRow] = TiltSwipeActionsData.spending.map { TiltSwipeActionsRow(item: $0) }
    var flagged: Set<String> = []
    /// Kept while the pill fades out after Undo.
    var pillTitle = ""
    /// The deleted row's height above the pill, for the pill's morph.
    var pillFrom = 0.0
    /// 0 Overview (the cards), 2 Spending (the list).
    var tab = 2
    var menuOpen = false
    var ghostX = 0.0
    var ghostY = 0.0

    let pill = TiltSwipeActionsAnimated(0)
    /// The balance as printed: it rolls to a new value.
    let balance = TiltSwipeActionsAnimated(TiltSwipeActionsData.balance)
    /// 1 = Spending, 0 = Overview.
    let tabShown = TiltSwipeActionsAnimated(1)
    let lens = TiltSwipeActionsAnimated(TiltSwipeActionsFlat.tabs[2])
    let menu = TiltSwipeActionsAnimated(0)
    let fabScale = TiltSwipeActionsAnimated(1)
    let tabScales = (0..<3).map { _ in TiltSwipeActionsAnimated(1) }
    let menuScales = (0..<3).map { _ in TiltSwipeActionsAnimated(1) }
    let ghostOpacity = TiltSwipeActionsAnimated(0)
    let ghostPress = TiltSwipeActionsAnimated(0)

    // MARK: Rules

    @ObservationIgnored var autoPlay = false
    @ObservationIgnored var reduceMotion = false
    /// The row that owns the touch; any other open row closes.
    @ObservationIgnored var activeKey = ""
    @ObservationIgnored var lastDeleted: (item: TiltSwipeActionsTransaction, index: Int)?
    @ObservationIgnored var balanceTarget = TiltSwipeActionsData.balance
    @ObservationIgnored var pillTimer: DispatchWorkItem?
    /// Work the React Native block hands to the JS thread: it runs after the frame.
    @ObservationIgnored var deferred: [() -> Void] = []

    // MARK: Autoplay

    @ObservationIgnored var time = 0.0
    @ObservationIgnored var paused = true
    @ObservationIgnored var touching = false
    @ObservationIgnored var quiet = 0.0
    @ObservationIgnored var tidy = -1.0
    @ObservationIgnored var scriptedDown = ""
    @ObservationIgnored var scriptedLastTx = 0.0
    @ObservationIgnored var script = TiltSwipeActionsScriptTouch()
    @ObservationIgnored var scriptDirty = false
    /// Seconds: the loop clock while the demo runs, a free clock otherwise (the FAB's glow).
    var clock = 0.0

    // MARK: Real finger

    @ObservationIgnored var touch: TiltSwipeActionsTouch?

    @ObservationIgnored private let link = TiltSwipeActionsFrameLink()

    func start(autoPlay: Bool, reduceMotion: Bool) {
        self.autoPlay = autoPlay
        self.reduceMotion = reduceMotion
        paused = !autoPlay
        link.onFrame = { [weak self] dt in self?.frame(dt) }
        link.start()
    }

    func stop() {
        link.stop()
        pillTimer?.cancel()
    }

    // MARK: Frame

    func frame(_ rawDt: Double) {
        let dt = min(rawDt, 0.1)
        demoFrame(dt)
        // Ambient clock: the loop clock while the demo runs, so the loop stays seamless.
        clock = autoPlay && !paused ? time : clock + min(dt, 0.05)
        for row in rows { row.advance(dt) }
        for value in [pill, balance, tabShown, lens, menu, fabScale, ghostOpacity, ghostPress] + tabScales + menuScales where value.isAnimating {
            value.advance(dt)
        }
        runDeferred()
    }

    func runDeferred() {
        while !deferred.isEmpty {
            let work = deferred
            deferred = []
            for item in work { item() }
        }
    }

    // MARK: Tabs, menu, balance, pill

    func selectTab(_ index: Int) {
        // The wallet tab's screen is not part of this block.
        if index == 1 { return }
        tab = index
        tabShown.timing(to: index == 2 ? 1 : 0, duration: reduceMotion ? TiltSwipeActionsMotion.Timing.reduced : TiltSwipeActionsMotion.Timing.tab)
        if reduceMotion {
            lens.timing(to: TiltSwipeActionsFlat.tabs[index], duration: TiltSwipeActionsMotion.Timing.reduced)
        } else {
            lens.spring(to: TiltSwipeActionsFlat.tabs[index], TiltSwipeActionsMotion.Spring.lens)
        }
    }

    func setMenu(open: Bool) {
        menuOpen = open
        if reduceMotion {
            menu.timing(to: open ? 1 : 0, duration: TiltSwipeActionsMotion.Timing.reduced)
        } else {
            menu.spring(to: open ? 1 : 0, TiltSwipeActionsMotion.Spring.menu)
        }
    }

    func setPressed(_ target: TiltSwipeActionsTouch.Target, pressed: Bool) {
        let value: TiltSwipeActionsAnimated?
        let downScale: Double
        switch target {
        case .fab:
            value = fabScale
            downScale = TiltSwipeActionsMotion.Timing.pressScale
        case let .tab(index):
            value = tabScales[index]
            downScale = TiltSwipeActionsTokens.Panel.tabPressScale
        case let .menuItem(index):
            value = menuScales[index]
            downScale = TiltSwipeActionsTokens.Panel.menuPressScale
        default:
            value = nil
            downScale = 1
        }
        guard let value else { return }
        let targetScale = pressed && !reduceMotion ? downScale : 1
        value.timing(
            to: targetScale,
            duration: pressed ? TiltSwipeActionsMotion.Timing.pressIn : TiltSwipeActionsMotion.Timing.pressOut,
            easing: .out
        )
    }

    func setBalance(_ value: Double) {
        balanceTarget = value
        if reduceMotion {
            balance.set(value)
        } else {
            balance.timing(to: value, duration: TiltSwipeActionsMotion.Timing.roll, easing: .out)
        }
    }

    func showPill(_ on: Bool) {
        if reduceMotion {
            pill.timing(to: on ? 1 : 0, duration: TiltSwipeActionsMotion.Timing.reduced)
        } else {
            pill.spring(to: on ? 1 : 0, TiltSwipeActionsMotion.Spring.pill)
        }
    }

    // MARK: Delete, undo, flag, reset

    func deleteRow(_ key: String) {
        guard let index = rows.firstIndex(where: { $0.key == key }) else { return }
        let item = rows[index].item
        rows.remove(at: index)
        lastDeleted = (item, index)
        pillTitle = item.title
        let rowCentre = TiltSwipeActionsFlat.rowsTop + TiltSwipeActionsLayout.row * (Double(index) + 0.5)
        let pillCentre = TiltSwipeActionsFlat.planeHeight - TiltSwipeActionsLayout.pillBottom - TiltSwipeActionsLayout.pillHeight / 2
        pillFrom = rowCentre - pillCentre
        pill.set(0)
        showPill(true)
        setBalance(balanceTarget + item.value)
        // The demo hides the pill with its own Undo tap, on the loop clock; by hand it times out.
        let scripted = autoPlay && !paused
        if !scripted { TiltSwipeActionsHaptic.commit.play() }
        pillTimer?.cancel()
        if !scripted {
            let timer = DispatchWorkItem { [weak self] in self?.showPill(false) }
            pillTimer = timer
            DispatchQueue.main.asyncAfter(deadline: .now() + TiltSwipeActionsMotion.Timing.undoVisible, execute: timer)
        }
    }

    func undo() {
        if let current = lastDeleted {
            setBalance(balanceTarget - current.item.value)
            let row = TiltSwipeActionsRow(item: current.item, restoring: true, reduceMotion: reduceMotion)
            rows.insert(row, at: min(current.index, rows.count))
            mount(restoring: row)
            lastDeleted = nil
        }
        showPill(false)
        pillTimer?.cancel()
    }

    func undoPressed() {
        TiltSwipeActionsHaptic.undo.play()
        undo()
    }

    func toggleFlag(_ key: String) {
        if flagged.contains(key) {
            flagged.remove(key)
        } else {
            flagged.insert(key)
        }
    }

    /// Demo restart: every row back, nothing flagged, no pill.
    func reset() {
        rows = TiltSwipeActionsData.spending.map { item in
            rows.first(where: { $0.key == item.key }) ?? TiltSwipeActionsRow(item: item)
        }
        flagged = []
        lastDeleted = nil
        setBalance(TiltSwipeActionsData.balance)
        showPill(false)
    }

    func resetForLoopBoundary() {
        rows = TiltSwipeActionsData.spending.map { TiltSwipeActionsRow(item: $0) }
        flagged = []
        lastDeleted = nil
        activeKey = ""
        balanceTarget = TiltSwipeActionsData.balance
        balance.set(TiltSwipeActionsData.balance)
        pill.set(0)
        pillTitle = ""
        pillFrom = 0
    }

    /// The row's mount effect: an Undo grows the gap, then slides the row back in from where it left.
    private func mount(restoring row: TiltSwipeActionsRow) {
        if reduceMotion {
            row.height.set(1)
            row.x.set(0)
            row.strip.set(1)
            row.fade.timing(to: 1, duration: TiltSwipeActionsMotion.Timing.reduced)
            return
        }
        row.height.spring(to: 1, TiltSwipeActionsMotion.Spring.restore)
        row.x.spring(to: 0, TiltSwipeActionsMotion.Spring.restore, delay: TiltSwipeActionsMotion.Timing.restoreDelay) { done in
            if done { row.strip.set(1) }
        }
    }
}
