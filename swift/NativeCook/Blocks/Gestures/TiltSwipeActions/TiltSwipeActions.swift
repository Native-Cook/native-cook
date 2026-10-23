import SwiftUI

private enum TiltSwipeActionsDragMode {
    case undecided, split, reveal, failed
}

private enum TiltSwipeActionsToast: Equatable {
    case split(key: String, count: Int, amount: Double)
    case delete(item: TiltSwipeActionsTransaction, index: Int)
}

/// A private-banking ledger whose rows split into equal paper shares when pulled right.
struct TiltSwipeActions: View {
    var autoPlay: Bool
    static let loop: Double = 10

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var loopStart = Date()
    @State private var interrupted = false
    @State private var resumeGeneration = 0
    @State private var draggingKey: String?
    @State private var dragMode = TiltSwipeActionsDragMode.undecided
    @State private var dragStartOffset: CGFloat = 0
    @State private var manualPull: CGFloat = 0
    @State private var manualTarget = 1
    @State private var manualOffsets: [String: CGFloat] = [:]
    @State private var manualSplits: [String: Int] = [:]
    @State private var flagged: Set<String> = []
    @State private var deleted: Set<String> = []
    @State private var toast: TiltSwipeActionsToast?
    @State private var toastDate = Date()
    @State private var menuOpen = false

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        TiltSwipeActionsFont.register()
    }

    var body: some View {
        GeometryReader { proxy in
            let windowInsets = TiltSwipeActionsScreenInsets.current
            let top = max(proxy.safeAreaInsets.top, windowInsets.top)
            let bottom = max(proxy.safeAreaInsets.bottom, windowInsets.bottom)
            let width = proxy.size.width
            let height = proxy.size.height
            let scene = TiltSwipeActionsScene(width: width, height: height, top: top, bottom: bottom)
            TimelineView(.animation(minimumInterval: 1 / 60, paused: reduceMotion || !autoPlay)) { timeline in
                let elapsed = max(0, timeline.date.timeIntervalSince(loopStart))
                let demo = autoPlay && !reduceMotion && !interrupted ? TiltSwipeActionsMotion.snapshot(at: elapsed) : manualSnapshot
                ledger(scene: scene, demo: demo, elapsed: elapsed, now: timeline.date)
                    .frame(width: width, height: height, alignment: .topLeading)
            }
        }
        .background(TiltSwipeActionsColor.paper.ignoresSafeArea())
        .ignoresSafeArea()
        .preferredColorScheme(.light)
        .onAppear { loopStart = Date().addingTimeInterval(TiltSwipeActionsTokens.tidy) }
        .onChange(of: reduceMotion) { _, reduced in
            if reduced { resetManual() }
            loopStart = Date().addingTimeInterval(TiltSwipeActionsTokens.tidy)
        }
    }

    private var manualSnapshot: TiltSwipeActionsSnapshot {
        var state = TiltSwipeActionsSnapshot()
        state.pull = manualPull
        state.splitCount = manualTarget
        state.rowOffsets = manualOffsets
        state.dim = manualPull > 0 ? TiltSwipeActionsCurve.interpolate(manualPull, from: 24, to: 52) * 0.72 : 0
        return state
    }

    @ViewBuilder
    private func ledger(scene: TiltSwipeActionsScene, demo: TiltSwipeActionsSnapshot, elapsed: TimeInterval, now: Date) -> some View {
        let scriptSplit = !interrupted && demo.splitCommitted ? 3 : nil
        let currentSplit = manualSplits["saigon"] ?? scriptSplit
        let total = TiltSwipeActionsData.weekTotal(splitCount: currentSplit)
        let week = TiltSwipeActionsData.money(total)
        let scriptToast = !interrupted && demo.toast > 0
        let toastOpacity = scriptToast ? demo.toast : (toast == nil ? 0 : 1)

        ZStack(alignment: .topLeading) {
            Rectangle()
                .fill(ImagePaint(image: TiltSwipeActionsAssets.paper, scale: 1))
                .background(TiltSwipeActionsColor.paper)
            ZStack(alignment: .topLeading) {
                TiltSwipeActionsBalanceCard(width: scene.listWidth)
                    .offset(x: TiltSwipeActionsLayout.gutter, y: scene.cardTop)
                Text("Latest spending")
                    .font(.custom(TiltSwipeActionsFont.italic, fixedSize: 26))
                    .tracking(-0.2)
                    .foregroundStyle(TiltSwipeActionsColor.ink)
                    .frame(height: 36)
                    .offset(x: TiltSwipeActionsLayout.gutter, y: scene.sectionTop - 3)
                    .accessibilityAddTraits(.isHeader)
                Text("\(week.whole)\(week.cents) this week")
                    .font(.custom(TiltSwipeActionsFont.sans, fixedSize: 13))
                    .foregroundStyle(TiltSwipeActionsColor.ink2)
                    .frame(width: 200, height: 16, alignment: .trailing)
                    .offset(x: scene.width - TiltSwipeActionsLayout.gutter - 200, y: scene.sectionTop + 12.33)
                ledgerSheet(scene: scene, demo: demo, split: currentSplit)
                Text("ALL TRANSACTIONS")
                    .font(.custom(TiltSwipeActionsFont.sansMedium, fixedSize: 11))
                    .tracking(1.54)
                    .foregroundStyle(TiltSwipeActionsColor.brassPaper)
                    .frame(width: scene.width, height: 24)
                    .offset(y: scene.linkTop)
                    .opacity(1 - min(toastOpacity * 10, 1))
            }
            .opacity(menuOpen ? 0.5 : 1)

            if scriptToast {
                TiltSwipeActionsToastView(toast: .split(key: "saigon", count: 3, amount: -96.60), opacity: toastOpacity, progress: max(0.001, 1 - max(0, elapsed.truncatingRemainder(dividingBy: 10) - 3.06) / 4), scene: scene, onUndo: {})
            } else if let toast {
                let progress = max(0.001, 1 - now.timeIntervalSince(toastDate) / TiltSwipeActionsTokens.Timing.toastVisible)
                TiltSwipeActionsToastView(toast: toast, opacity: toastOpacity, progress: progress, scene: scene, onUndo: undo)
            }

            TiltSwipeActionsTabPanel(scene: scene, reduceMotion: reduceMotion, menuOpen: $menuOpen)
            if autoPlay && !reduceMotion && !interrupted {
                demoFinger(scene: scene, demo: demo, elapsed: elapsed)
            }
        }
        .frame(width: scene.width, height: scene.height, alignment: .topLeading)
        .clipped()
    }

    private func ledgerSheet(scene: TiltSwipeActionsScene, demo: TiltSwipeActionsSnapshot, split: Int?) -> some View {
        let rows = TiltSwipeActionsData.spending.enumerated().filter { !deleted.contains($0.element.key) }
        let sheetHeight = TiltSwipeActionsLayout.listPad + CGFloat(max(rows.count, 1)) * TiltSwipeActionsLayout.row + 3
        return ZStack(alignment: .topLeading) {
            Rectangle()
                .fill(Color.clear)
                .overlay(alignment: .top) { Rectangle().fill(TiltSwipeActionsColor.ink).frame(height: 1) }
                .overlay(alignment: .bottom) { Rectangle().fill(TiltSwipeActionsColor.ruleBottom).frame(height: 1) }
                .frame(width: scene.listWidth, height: sheetHeight)
            ForEach(Array(rows.enumerated()), id: \.element.element.key) { shownIndex, pair in
                let item = pair.element
                let isSplitRow = item.key == "saigon"
                let pull = isSplitRow ? demo.pull : 0
                let rowSplit = isSplitRow ? (split ?? demo.splitCount) : 0
                let dim = isSplitRow ? 0 : demo.dim
                TiltSwipeActionsRowView(
                    item: item,
                    width: scene.listWidth,
                    splitCount: max(rowSplit, 1),
                    splitCommitted: isSplitRow && split != nil,
                    pull: pull,
                    offset: demo.rowOffsets[item.key] ?? 0,
                    dim: dim,
                    flagged: flagged.contains(item.key),
                    reduceMotion: reduceMotion,
                    gesture: rowGesture(item, width: scene.listWidth),
                    onFlag: { toggleFlag(item.key) },
                    onDelete: { delete(item, originalIndex: pair.offset, width: scene.listWidth) }
                )
                .offset(y: TiltSwipeActionsLayout.listPad + CGFloat(shownIndex) * TiltSwipeActionsLayout.row)
                .zIndex(isSplitRow && pull > 0 ? 10 : 0)
            }
        }
        .frame(width: scene.listWidth, height: sheetHeight, alignment: .topLeading)
        .offset(x: TiltSwipeActionsLayout.gutter, y: scene.listTop)
    }

    private func rowGesture(_ item: TiltSwipeActionsTransaction, width: CGFloat) -> AnyGesture<DragGesture.Value> {
        AnyGesture(DragGesture(minimumDistance: 0, coordinateSpace: .local)
            .onChanged { value in
                if draggingKey != item.key {
                    beginInteraction(item)
                }
                guard dragMode != .failed else { return }
                let dx = value.translation.width
                let dy = value.translation.height
                if dragMode == .undecided {
                    if abs(dy) > TiltSwipeActionsTokens.Swipe.failY {
                        dragMode = .failed
                        return
                    }
                    if abs(dx) >= TiltSwipeActionsTokens.Swipe.activeOffset {
                        dragMode = dx > 0 && abs(dragStartOffset) < 1 ? .split : .reveal
                    }
                }
                if dragMode == .split {
                    let last = TiltSwipeActionsTokens.Split.detentStart + CGFloat(TiltSwipeActionsData.people.count - 2) * TiltSwipeActionsTokens.Split.detentStep
                    let raw = max(dx, 0)
                    manualPull = raw > last ? last + (raw - last) * TiltSwipeActionsTokens.Split.overRubber : raw
                    let target = TiltSwipeActionsMotion.detent(for: manualPull)
                    if target != manualTarget {
                        manualTarget = target
                        TiltSwipeActionsHaptic.detent.play()
                    }
                } else if dragMode == .reveal {
                    var next = dragStartOffset + dx
                    if next > 0 { next *= TiltSwipeActionsTokens.Swipe.rightRubber }
                    let soft = -width * 0.85
                    if next < soft { next = soft + (next - soft) * TiltSwipeActionsTokens.Swipe.fullRubber }
                    let wasArmed = -(manualOffsets[item.key] ?? 0) > width * TiltSwipeActionsTokens.Swipe.fullAt
                    manualOffsets[item.key] = next
                    let armed = -next > width * TiltSwipeActionsTokens.Swipe.fullAt
                    if armed && !wasArmed { TiltSwipeActionsHaptic.arm.play() }
                }
            }
            .onEnded { value in
                defer { endInteraction() }
                if dragMode == .split {
                    if manualTarget >= 2 {
                        let count = manualTarget
                        withAnimation(reduceMotion ? .easeOut(duration: 0.18) : .spring(response: 0.48, dampingFraction: 0.86)) { manualPull = 0 }
                        manualSplits[item.key] = count
                        showToast(.split(key: item.key, count: count, amount: item.amount))
                        TiltSwipeActionsHaptic.split.play()
                    } else {
                        withAnimation(reduceMotion ? .easeOut(duration: 0.18) : .spring(response: 0.36, dampingFraction: 1)) { manualPull = 0 }
                    }
                } else if dragMode == .reveal {
                    let x = manualOffsets[item.key] ?? 0
                    let velocity = value.predictedEndTranslation.width - value.translation.width
                    if -x > width * TiltSwipeActionsTokens.Swipe.fullAt || (velocity < -140 && -x > width * 0.4) {
                        delete(item, originalIndex: TiltSwipeActionsData.spending.firstIndex(of: item) ?? 0, width: width)
                    } else {
                        let open = (-x > TiltSwipeActionsTokens.Swipe.openAt && velocity < 30) || velocity < -80
                        withAnimation(reduceMotion ? .easeOut(duration: 0.18) : .spring(response: open ? 0.42 : 0.36, dampingFraction: open ? 0.8 : 1)) {
                            manualOffsets[item.key] = open ? TiltSwipeActionsLayout.openX : 0
                        }
                    }
                }
                draggingKey = nil
                dragMode = .undecided
            })
    }

    private func beginInteraction(_ item: TiltSwipeActionsTransaction) {
        interrupted = true
        resumeGeneration += 1
        draggingKey = item.key
        dragMode = .undecided
        dragStartOffset = manualOffsets[item.key] ?? 0
        manualTarget = 1
        for key in manualOffsets.keys where key != item.key {
            withAnimation(.spring(response: 0.36, dampingFraction: 1)) { manualOffsets[key] = 0 }
        }
    }

    private func endInteraction() {
        let generation = resumeGeneration
        DispatchQueue.main.asyncAfter(deadline: .now() + TiltSwipeActionsTokens.resume) {
            guard generation == resumeGeneration else { return }
            resetManual()
            loopStart = Date().addingTimeInterval(TiltSwipeActionsTokens.tidy)
            interrupted = false
        }
    }

    private func resetManual() {
        manualPull = 0
        manualTarget = 1
        manualOffsets = [:]
        manualSplits = [:]
        flagged = []
        deleted = []
        toast = nil
        menuOpen = false
    }

    private func toggleFlag(_ key: String) {
        beginStandaloneInteraction()
        if flagged.contains(key) { flagged.remove(key) } else { flagged.insert(key) }
        manualOffsets[key] = 0
        TiltSwipeActionsHaptic.flag.play()
        endInteraction()
    }

    private func delete(_ item: TiltSwipeActionsTransaction, originalIndex: Int, width: CGFloat) {
        beginStandaloneInteraction()
        withAnimation(TiltSwipeActionsCurve.out.speed(1 / TiltSwipeActionsTokens.Timing.slideOut)) {
            manualOffsets[item.key] = -(width + 16)
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + TiltSwipeActionsTokens.Timing.slideOut) {
            deleted.insert(item.key)
            manualOffsets[item.key] = 0
            showToast(.delete(item: item, index: originalIndex))
        }
        endInteraction()
    }

    private func beginStandaloneInteraction() {
        interrupted = true
        resumeGeneration += 1
    }

    private func showToast(_ value: TiltSwipeActionsToast) {
        toast = value
        toastDate = Date()
        let generation = resumeGeneration
        DispatchQueue.main.asyncAfter(deadline: .now() + TiltSwipeActionsTokens.Timing.toastVisible) {
            if generation == resumeGeneration { withAnimation(.easeOut(duration: 0.14)) { toast = nil } }
        }
    }

    private func undo() {
        guard let toast else { return }
        TiltSwipeActionsHaptic.undo.play()
        switch toast {
        case let .split(key, _, _): manualSplits.removeValue(forKey: key)
        case let .delete(item, _): deleted.remove(item.key)
        }
        withAnimation(.easeOut(duration: reduceMotion ? 0.18 : 0.14)) { self.toast = nil }
        beginStandaloneInteraction()
        endInteraction()
    }

    private func demoFinger(scene: TiltSwipeActionsScene, demo: TiltSwipeActionsSnapshot, elapsed: TimeInterval) -> some View {
        let t = elapsed.truncatingRemainder(dividingBy: TiltSwipeActionsTokens.loop)
        let point: CGPoint
        if (0.7...2.9).contains(t) {
            point = CGPoint(x: demo.ghostPoint.x, y: scene.rowsTop + 2 * TiltSwipeActionsLayout.row + TiltSwipeActionsLayout.row / 2 + 24)
        } else if (3.7...4.25).contains(t) {
            point = CGPoint(x: demo.ghostPoint.x, y: scene.rowsTop + 3 * TiltSwipeActionsLayout.row + TiltSwipeActionsLayout.row / 2)
        } else if (5.6...5.75).contains(t) {
            point = CGPoint(x: TiltSwipeActionsLayout.gutter + scene.listWidth - 40, y: scene.toastTop + 28)
        } else {
            point = CGPoint(x: TiltSwipeActionsLayout.gutter + 120, y: scene.rowsTop + 3 * TiltSwipeActionsLayout.row + TiltSwipeActionsLayout.row / 2)
        }
        return Circle()
            .fill(TiltSwipeActionsColor.ink.opacity(0.10))
            .overlay(Circle().stroke(TiltSwipeActionsColor.ink.opacity(0.18), lineWidth: 1))
            .frame(width: TiltSwipeActionsLayout.finger, height: TiltSwipeActionsLayout.finger)
            .scaleEffect(1 - 0.15 * demo.ghostPress)
            .position(point)
            .opacity(demo.ghostOpacity)
            .accessibilityHidden(true)
    }
}

private struct TiltSwipeActionsToastView: View {
    let toast: TiltSwipeActionsToast
    let opacity: CGFloat
    let progress: Double
    let scene: TiltSwipeActionsScene
    let onUndo: () -> Void

    private var lines: (String, String) {
        switch toast {
        case let .split(_, count, amount):
            let split = TiltSwipeActionsData.shares(amount, count: count)
            let requested = split.dropFirst().reduce(0, +)
            let who = count == 3 ? "Mai and Leo" : count == 2 ? "Mai" : "\(count - 1) people"
            let requestedMoney = TiltSwipeActionsData.money(requested, signed: false)
            let each = TiltSwipeActionsData.money(split[1], signed: false)
            return ("Requested \(requestedMoney.whole)\(requestedMoney.cents)", "\(who) · \(each.whole)\(each.cents) each")
        case let .delete(item, _):
            let money = TiltSwipeActionsData.money(item.amount)
            return ("Transaction deleted", "\(item.title) · \(money.whole)\(money.cents)")
        }
    }

    var body: some View {
        HStack(spacing: 12) {
            if case let .split(_, count, _) = toast {
                HStack(spacing: -8) {
                    ForEach(1..<count, id: \.self) { index in
                        TiltSwipeActionsMonogram(person: TiltSwipeActionsData.people[index], size: 28)
                    }
                }
            } else {
                Circle().fill(TiltSwipeActionsColor.monoToast)
                    .overlay(TiltSwipeActionsGlyph(name: .trash, color: TiltSwipeActionsColor.brass, size: 16))
                    .frame(width: 28, height: 28)
            }
            VStack(alignment: .leading, spacing: 0) {
                Text(lines.0).font(.custom(TiltSwipeActionsFont.sansMedium, fixedSize: 15)).foregroundStyle(TiltSwipeActionsColor.ivory)
                Text(lines.1).font(.custom(TiltSwipeActionsFont.sans, fixedSize: 12)).foregroundStyle(TiltSwipeActionsColor.ivory.opacity(0.55))
            }
            .lineLimit(1)
            Spacer(minLength: 0)
            Button(action: onUndo) {
                HStack(spacing: 6) {
                    TiltSwipeActionsGlyph(name: .undo, color: TiltSwipeActionsColor.brass, size: 16)
                    Text("Undo").font(.custom(TiltSwipeActionsFont.sansSemi, fixedSize: 14)).foregroundStyle(TiltSwipeActionsColor.brass)
                }
                .padding(.horizontal, 12).frame(height: 40)
            }
            .buttonStyle(.plain)
        }
        .padding(.leading, 12).padding(.trailing, 8)
        .frame(width: scene.listWidth, height: TiltSwipeActionsLayout.toast)
        .background(TiltSwipeActionsColor.ink, in: RoundedRectangle(cornerRadius: TiltSwipeActionsLayout.toastRadius, style: .continuous))
        .overlay(alignment: .bottomLeading) {
            Rectangle().fill(TiltSwipeActionsColor.brass.opacity(0.45))
                .frame(width: max(0.001, scene.listWidth - 2 * TiltSwipeActionsLayout.toastRadius) * progress, height: 1)
                .padding(.leading, TiltSwipeActionsLayout.toastRadius)
        }
        .shadow(color: TiltSwipeActionsColor.ink.opacity(0.28), radius: 16, y: 8)
        .position(
            x: TiltSwipeActionsLayout.gutter + scene.listWidth / 2,
            y: scene.toastTop + TiltSwipeActionsLayout.toast / 2 + (1 - opacity) * 16
        )
        .opacity(opacity)
        .zIndex(20)
    }
}
