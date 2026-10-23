import SwiftUI
import UIKit

private struct PaddockWidgetBentoTopKey: PreferenceKey {
    static var defaultValue = 0.0
    static func reduce(value: inout Double, nextValue: () -> Double) { value = nextValue() }
}

private struct PaddockWidgetBentoFeedObserver: ViewModifier {
    var scale: Double
    var onChange: (PaddockWidgetBentoScrollSnapshot) -> Void

    @ViewBuilder func body(content: Content) -> some View {
        if #available(iOS 18.0, *) {
            content.onScrollGeometryChange(for: PaddockWidgetBentoScrollSnapshot.self) { geometry in
                let distance = geometry.contentOffset.y + geometry.contentInsets.top
                return PaddockWidgetBentoScrollSnapshot(offset: -distance, atTop: distance <= 4 * scale)
            } action: { _, snapshot in onChange(snapshot) }
        } else {
            content.onPreferenceChange(PaddockWidgetBentoTopKey.self) { minY in
                onChange(PaddockWidgetBentoScrollSnapshot(offset: minY, atTop: minY >= -4 * scale))
            }
        }
    }
}

private struct PaddockWidgetBentoScrollSnapshot: Equatable {
    var offset: Double
    var atTop: Bool
}


private typealias Theme = PaddockWidgetBentoTheme
private typealias Motion = PaddockWidgetBentoMotion

/// A pausable display clock that follows UIKit activation, independently of SwiftUI's scene timeline.
private final class PaddockWidgetBentoDisplayClock: ObservableObject {
    @Published private(set) var time = ProcessInfo.processInfo.systemUptime
    private(set) var controller = PaddockWidgetBentoController()
    private var displayLink: CADisplayLink?

    func update(controller: PaddockWidgetBentoController, running: Bool) {
        self.controller = controller
        if running, displayLink == nil {
            let target = Target()
            target.clock = self
            let link = CADisplayLink(target: target, selector: #selector(Target.tick(_:)))
            link.preferredFramesPerSecond = 60
            link.add(to: .main, forMode: .common)
            displayLink = link
        }
        displayLink?.isPaused = !running
        time = ProcessInfo.processInfo.systemUptime
    }

    func stop() { displayLink?.invalidate(); displayLink = nil }
    deinit { displayLink?.invalidate() }

    private final class Target: NSObject {
        weak var clock: PaddockWidgetBentoDisplayClock?
        @objc func tick(_ link: CADisplayLink) { clock?.time = ProcessInfo.processInfo.systemUptime }
    }
}

/// Only the motion layer observes frame times; the screen's state changes at event boundaries.
private struct PaddockWidgetBentoTimeline<Content: View>: View {
    @ObservedObject var clock: PaddockWidgetBentoDisplayClock
    let content: (TimeInterval, PaddockWidgetBentoController) -> Content

    var body: some View {
        content(clock.time, clock.controller)
            .onDisappear { clock.stop() }
    }
}

/// The device's safe area read from the key window: a GeometryReader under `.ignoresSafeArea()` reports zero.
enum PaddockWidgetBentoSafeArea {
    @MainActor static var insets: EdgeInsets {
        let window = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }
        let i = window?.safeAreaInsets ?? .zero
        return EdgeInsets(top: i.top, leading: i.left, bottom: i.bottom, trailing: i.right)
    }
}

struct PaddockWidgetBento: View {
    static let loop = PaddockWidgetBentoMotion.loopDuration
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver
    @Environment(\.scenePhase) private var scenePhase
    @State private var controller = PaddockWidgetBentoController()
    @State private var displayClock = PaddockWidgetBentoDisplayClock()
    @State private var appeared = false
    @State private var clockActive = false
    @State private var revision = 0
    @State private var wakeTask: Task<Void, Never>?
    @State private var activeOwner: Int?
    @State private var activeCard: PaddockWidgetBentoCardID?
    @State private var pointerReferenceY = 0.0
    @State private var selectedTab = PaddockWidgetBentoDestination.home
    @State private var destination: PaddockWidgetBentoDestination?
    @State private var guideVisible = true
    @State private var feedAtTop = true
    @State private var feedOffset = 0.0
    @State private var pinnedWidgets: [String] = []
    @State private var selectedWidget = "Lap count"
    @State private var lapCount = 3
    @State private var savedWeekends: Set<String> = []
    @State private var selectedCircuit = "Harbor"
    @State private var latestStats = true
    @State private var expandedLesson = "Find the apex"
    @State private var searchQuery = ""
    @State private var reactionText = "Ready"
    @State private var reactionStart: TimeInterval?
    @State private var reactionTask: Task<Void, Never>?
    // Touch-driven effects: the pull-to-refresh pit stop and the finger on the sticker.
    @State private var livePitStart: TimeInterval?
    @State private var pullFromTop = false
    @State private var peelHeld = false
    @State private var peelDistance = 0.0
    @State private var peelAngle = PaddockWidgetBentoMotion.Peel.angle
    @State private var peelReleaseStart: TimeInterval?
    @State private var peelReleaseTotal = 0.0

    private var quiet: Bool { reduceMotion || voiceOver }
    private var now: TimeInterval { ProcessInfo.processInfo.systemUptime }
    /// A touch's y as a card-top coordinate: the controller compares it with the slots' tops (slot top - half a card).
    private let touchShift = PaddockWidgetBentoMotion.slotY[0] - Theme.Layout.quick.height / 2

    var body: some View {
        // Register wake invalidations on this view, before GeometryReader defers its content.
        let _ = revision
        return GeometryReader { proxy in
            let scale = proxy.size.width / Theme.referenceWidth
            let safe = PaddockWidgetBentoSafeArea.insets
            ZStack(alignment: .top) {
                Color.black
                ScrollView(showsIndicators: false) {
                    VStack(spacing: 0) {
                        Group {
                            if selectedTab == .home {
                                home(scale: scale)
                                    .background {
                                        GeometryReader { reader in
                                            Color.clear.preference(key: PaddockWidgetBentoTopKey.self,
                                                                   value: reader.frame(in: .named("PaddockFeed")).minY)
                                        }
                                    }
                            } else {
                                secondaryPage(scale: scale).padding(.top, safe.top)
                            }
                        }
                        .frame(minHeight: Theme.Layout.contentMinHeight * scale, alignment: .top)
                        .frame(maxWidth: .infinity)
                        Color.clear.frame(height: Theme.Layout.bottomClearance * scale + safe.bottom)
                    }
                }
                .id(selectedTab)
                .scrollDisabled(peelHeld)
                .modifier(PaddockWidgetBentoFeedObserver(scale: scale) { snapshot in
                    updateFeed(snapshot.offset, scale: scale, explicitTop: snapshot.atTop)
                })
                .coordinateSpace(name: "PaddockFeed")
                PaddockWidgetBentoDock(scale: scale, tab: selectedTab, quiet: reduceMotion, onSelect: selectTab)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                    .padding(.bottom, Theme.Layout.dock.bottom * scale)
            }
            .ignoresSafeArea()
            .coordinateSpace(name: "PaddockRoot")
            .preferredColorScheme(.dark)
            .environment(\.colorScheme, .dark)
            .sheet(item: $destination) { sheet($0) }
            .onChange(of: destination) { _, value in destinationChanged(value) }
            .onChange(of: scenePhase) { _, phase in lifecycleChanged(phase) }
            // XCTest can leave SwiftUI's scene phase inactive while UIKit has activated the app.
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in
                lifecycleChanged(.active)
            }
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.willResignActiveNotification)) { _ in
                lifecycleChanged(.inactive)
            }
            .onChange(of: quiet) { _, value in quietChanged(value) }
            .onChange(of: autoPlay) { _, value in let time = now; controller.setAutoPlay(value, now: time); wake(at: time) }
            .onAppear { appearIfNeeded() }
            .onDisappear { cancelTasks(); clockActive = false; controller.setActive(false, now: now) }
        }
    }

    // MARK: Home

    private func home(scale: CGFloat) -> some View {
        ZStack(alignment: .topLeading) {
            motionLayer(scale: scale)
            PaddockWidgetBentoTouchSurface(
                onDown: { owner, localY, timestamp in rawTouchDown(owner: owner, referenceY: Double(localY / scale) + touchShift, timestamp: timestamp) },
                onHold: { owner, localY in rawTouchHold(owner: owner, referenceY: Double(localY / scale) + touchShift) },
                onMove: { owner, localY in rawTouchMove(owner: owner, referenceY: Double(localY / scale) + touchShift) },
                onUp: { owner in rawTouchUp(owner: owner) },
                onCancel: { owner in rawTouchCancel(owner: owner) }
            )
            .frame(width: Theme.Layout.touchSurface.width * scale, height: Theme.Layout.touchSurface.height * scale)
            .offset(x: Theme.Layout.touchSurface.minX * scale, y: Theme.Layout.touchSurface.minY * scale)
            .accessibilityHidden(true)
            .zIndex(7)
            if controller.isEditing {
                let done = Theme.Layout.done
                Button { doneEditing() } label: {
                    Text("Done").font(.system(size: Theme.TypeSize.done * scale, weight: .bold)).foregroundStyle(.black)
                        .frame(width: done.width * scale, height: Theme.Layout.doneHeight)
                        .background(.white, in: Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityHint("Finish arranging quick cards")
                .offset(x: done.centerX * scale - done.width * scale / 2, y: done.centerY * scale - Theme.Layout.doneHeight / 2)
                .zIndex(8)
            }
            pinnedCards(scale: scale)
            PaddockWidgetBentoPullObserver(
                onBegan: { distance in pullFromTop = selectedTab == .home && distance <= 4 * scale && !peelHeld },
                onEnded: { distance, velocity in pullEnded(distance: distance, velocity: velocity, scale: scale) }
            )
            .frame(width: 1, height: 1)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
        }
        .frame(width: Theme.referenceWidth * scale, height: (Theme.Layout.contentHeight + Double(pinnedWidgets.count) * Theme.Layout.pinnedPitch) * scale, alignment: .topLeading)
    }

    private func motionLayer(scale: CGFloat) -> some View {
        PaddockWidgetBentoTimeline(clock: displayClock) { time, renderController in
            var sampler = renderController
            let frame = sampler.frame(now: time)
            let pit = pitTime(frame, at: time)
            let peel = peelState(frame, at: time)
            return ZStack(alignment: .topLeading) {
                PaddockWidgetBentoPitTrails(scale: scale, pit: pit, frame: frame)
                PaddockWidgetBentoStaticGrid(scale: scale, guideVisible: guideVisible, pit: pit, peel: peel, onAction: handleAction) {
                    PaddockWidgetBentoPanSurface(
                        onBegan: { peelBegan() },
                        onChanged: { size in peelMoved(size, scale: scale) },
                        onEnded: { peelEnded() },
                        onTap: { handleAction(.widgetStudio) }
                    )
                    .accessibilityHidden(true)
                }
                if frame.handlesOpacity > 0.01 {
                    PaddockWidgetBentoGooLayer(scale: scale, frame: frame, time: frame.loopTime >= 0 ? frame.loopTime : time)
                }
                ForEach(PaddockWidgetBentoCardID.allCases, id: \.self) { id in
                    quickCard(id, frame: frame, pit: pit, time: time, scale: scale)
                }
            }
            .frame(width: Theme.referenceWidth * scale, height: Theme.Layout.gridHeight * scale, alignment: .topLeading)
        }
    }

    private func quickCard(_ id: PaddockWidgetBentoCardID, frame: PaddockWidgetBentoMotion.Frame, pit: Double, time: Double, scale: CGFloat) -> some View {
        let selected = frame.selectedID == id
        let pose = Motion.pitTile(id.index, pit)
        let jiggleTime = frame.loopTime >= 0 ? frame.loopTime : time
        let lean = selected ? frame.rotationDegrees : (reduceMotion ? 0 : Motion.jiggle(id: id.index, time: jiggleTime, handles: frame.handlesOpacity))
        let top = frame.cardY[id] ?? Motion.slotY[0]
        return PaddockWidgetBentoCardFace(id: id, scale: scale, handles: frame.handlesOpacity)
            .shadow(color: .black.opacity(selected ? 0.28 : 0), radius: (selected ? frame.shadowRadius : Motion.restShadow) * scale, y: 4 * scale)
            .scaleEffect(x: pose.sx, y: pose.sy)
            .scaleEffect(selected ? frame.selectedScale : 1)
            .rotationEffect(.degrees(pose.rotate + lean))
            .offset(x: (Theme.Layout.quick.minX + pose.x) * scale, y: (top + pose.y) * scale)
            .opacity(frame.cardOpacity[id] ?? 1)
            .zIndex(selected ? 5 : 1)
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { present(cardDestination(id)) }
            .accessibilityAction(named: "Move up") { accessibleMove(id, offset: -1) }
            .accessibilityAction(named: "Move down") { accessibleMove(id, offset: 1) }
            .accessibilityAction(named: "Done") { doneEditing() }
    }

    private func pinnedCards(scale: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: Theme.Space.pinnedGap * scale) {
            ForEach(pinnedWidgets, id: \.self) { widget in
                HStack(spacing: 8) {
                    Image(systemName: widgetSymbol(widget)).font(.system(size: Theme.TypeSize.body)).foregroundStyle(.white)
                    Text(widget).font(.system(size: Theme.TypeSize.body, weight: .bold)).foregroundStyle(.white)
                    Spacer(minLength: 0)
                    Text("Pinned").font(.system(size: Theme.TypeSize.body)).foregroundStyle(Theme.Colors.pinnedLabel)
                }
                .padding(.horizontal, Theme.Space.pinnedInset * scale)
                .frame(width: Theme.Layout.hero.width * scale, height: Theme.Layout.pinnedHeight * scale)
                .background(Theme.Colors.panel10, in: RoundedRectangle(cornerRadius: Theme.Radius.pinned * scale, style: .continuous))
            }
        }
        .offset(x: Theme.Space.screenInset * scale, y: Theme.Layout.pinnedTop * scale)
    }

    // MARK: Pit stop and sticker

    /// Seconds into the pit stop: a pull-to-refresh, or the demo's loop. -1 when none runs.
    private func pitTime(_ frame: PaddockWidgetBentoMotion.Frame, at time: TimeInterval) -> Double {
        if let start = livePitStart, time - start < Motion.Pit.duration { return time - start }
        return frame.loopTime >= 0 ? frame.loopTime - Motion.Pit.loopStart : -1
    }

    /// The sticker: the finger, a released finger falling back, or the demo (always with the resting curl).
    private func peelState(_ frame: PaddockWidgetBentoMotion.Frame, at time: TimeInterval) -> PaddockWidgetBentoMotion.PeelState {
        if peelHeld { return Motion.withHint(.init(distance: peelDistance, angle: peelAngle, slap: -1)) }
        if let start = peelReleaseStart {
            let since = time - start
            if since < peelReleaseTotal {
                if reduceMotion {
                    return Motion.withHint(.init(distance: peelDistance * (1 - Motion.clamp(since / 0.2)), angle: peelAngle, slap: -1))
                }
                return Motion.withHint(Motion.peelRelease(distance: peelDistance, angle: peelAngle, since: since))
            }
        } else if peelDistance > 0.5 {
            return Motion.withHint(.init(distance: peelDistance, angle: peelAngle, slap: -1))
        }
        return Motion.withHint(frame.loopTime >= 0 ? Motion.peelDemo(frame.loopTime) : .init(distance: 0, angle: Motion.Peel.angle, slap: -1))
    }

    private func peelBegan() {
        let time = now
        peelReleaseStart = nil
        peelHeld = true
        controller.holdDemo(until: time + 30)
        wake(at: time)
    }

    private func peelMoved(_ size: CGSize, scale: CGFloat) {
        let dx = Double(size.width / scale), dy = Double(size.height / scale)
        peelDistance = min(Motion.Peel.touchMaxDistance, hypot(dx, dy))
        peelAngle = atan2(dy, dx) * 180 / .pi
    }

    /// Letting go drops the corner back (accelerating), then it slaps: a press, a shake and a medium impact.
    private func peelEnded() {
        guard peelHeld else { return }
        let time = now
        peelHeld = false
        peelReleaseStart = time
        if reduceMotion {
            peelReleaseTotal = 0.2
            controller.holdDemo(until: time + 0.2)
        } else {
            peelReleaseTotal = Motion.Peel.release + Motion.Peel.slapEnd
            controller.holdDemo(until: time + peelReleaseTotal)
            Task { @MainActor in
                try? await Task.sleep(for: .seconds(Motion.Peel.release))
                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            }
        }
        wake(at: time)
    }

    /// Pull the feed past its top and let go: the pit crew rebuilds the bento.
    private func pullEnded(distance: Double, velocity: Double, scale: CGFloat) {
        guard pullFromTop else { return }
        pullFromTop = false
        let pulled = max(0, -distance)
        let far = pulled >= Motion.Pull.trigger * scale
        let flick = velocity >= Motion.Pull.flick * scale && pulled >= Motion.Pull.trigger * Motion.Pull.flickShare * scale
        guard !reduceMotion, far || flick, livePitStart == nil else { return }
        let time = now
        livePitStart = time
        controller.holdDemo(until: time + Motion.Pit.duration)
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        wake(at: time)
    }

    @ViewBuilder private func secondaryPage(scale: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 18 * scale) {
            Text(selectedTab.rawValue.capitalized).font(.system(size: 30 * scale, weight: .bold))
            switch selectedTab {
            case .calendar: calendarContent(scale: scale)
            case .results: resultsContent(scale: scale)
            case .circuits: circuitsContent(scale: scale)
            default: customizeContent(scale: scale)
            }
        }
        .padding(.horizontal, 24 * scale).padding(.top, 28 * scale)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func calendarContent(scale: CGFloat) -> some View {
        VStack(spacing: 12 * scale) {
            ForEach(["Harbor Circuit", "Juniper Park"], id: \.self) { venue in
                Toggle("\(venue) · Race weekend", isOn: Binding(get: { savedWeekends.contains(venue) }, set: { saved in
                    if saved { savedWeekends.insert(venue) } else { savedWeekends.remove(venue) }
                })).padding(16 * scale).background(.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 18 * scale))
            }
        }
    }

    private func resultsContent(scale: CGFloat) -> some View {
        VStack(spacing: 0) {
            ForEach(Array([("Mira Chen", 42), ("Alex Vale", 38), ("Sam Ives", 34), ("Noah Reed", 29)].enumerated()), id: \.offset) { index, racer in
                HStack { Text("\(index + 1)").foregroundStyle(.secondary); Text(racer.0); Spacer(); Text("\(racer.1) pts").foregroundStyle(.secondary) }
                    .frame(minHeight: 58 * scale).overlay(alignment: .bottom) { Divider().overlay(.white.opacity(0.12)) }
            }
        }
    }

    private func circuitsContent(scale: CGFloat) -> some View {
        VStack(spacing: 10 * scale) {
            ForEach(["Harbor", "Juniper", "Mesa"], id: \.self) { circuit in
                Button { selectedCircuit = circuit } label: {
                    HStack { circuitOutline(circuit, selected: selectedCircuit == circuit).frame(width: 80, height: 44); Text(circuit); Spacer(); if selectedCircuit == circuit { Image(systemName: "checkmark.circle.fill") } }
                        .frame(minHeight: 52 * scale)
                }.buttonStyle(.plain)
            }
            Text("\(selectedCircuit) Circuit · Local venue guide").foregroundStyle(.secondary)
        }
    }

    private func customizeContent(scale: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 12 * scale) {
            Text("Quick card order").font(.headline)
            ForEach(controller.order, id: \.self) { id in
                HStack { Text(cardName(id)); Spacer(); Button("Up") { accessibleMove(id, offset: -1) }.frame(minHeight: 44); Button("Down") { accessibleMove(id, offset: 1) }.frame(minHeight: 44) }
                    .padding(.horizontal, 14 * scale).background(.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 16 * scale))
            }
            Button(controller.isEditing ? "Done" : "Arrange cards") { controller.setEditing(!controller.isEditing, now: now); wake(at: now) }.buttonStyle(.borderedProminent).frame(minHeight: 44)
            Button("Restore order") { restoreOrder() }.buttonStyle(.bordered).frame(minHeight: 44)
            Button("Restore guide") { guideVisible = true }.buttonStyle(.bordered).frame(minHeight: 44)
        }
    }

    private func sheet(_ destination: PaddockWidgetBentoDestination) -> some View {
        NavigationStack {
            destinationContent(destination)
                .navigationTitle(sheetTitle(destination)).navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Done") { self.destination = nil } } }
        }.preferredColorScheme(.dark)
    }

    @ViewBuilder private func destinationContent(_ destination: PaddockWidgetBentoDestination) -> some View {
        switch destination {
        case .laps:
            VStack(spacing: 20) { Text("Every lap counts").font(.largeTitle.bold()); Text("This week: 18.4 km"); Text("Today's laps: \(lapCount)"); Button("Log a lap") { lapCount += 1 }.buttonStyle(.borderedProminent) }.frame(maxWidth: .infinity, maxHeight: .infinity)
        case .reflex:
            VStack(spacing: 22) { Text(reactionText).font(.largeTitle.bold()); Button(reactionButtonTitle) { reactionAction() }.buttonStyle(.borderedProminent) }.frame(maxWidth: .infinity, maxHeight: .infinity)
        case .pitcast:
            List { Section("The weekend brief · Alex Vale / Sam Ives") { Text("A cleaner first lap — Leave room before the first turn."); Text("Watch the weather — Coastal cloud can change quickly."); Text("One thing to notice — How the field settles after the restart.") } }
        case .stats:
            VStack(spacing: 18) { Picker("Stats", selection: $latestStats) { Text("Latest").tag(true); Text("Season").tag(false) }.pickerStyle(.segmented); Text(latestStats ? "Harbor · 8 laps · 42 points" : "Season · 64 laps · 286 points").font(.title2.bold()) }.padding()
        case .glossary: glossary
        case .education: lessons
        case .widgetStudio, .banner: widgetStudio
        case .guide:
            List { Text("1. Hold a quick card for a moment."); Text("2. Move it to a new slot."); Text("3. Release, then tap Done.") }
        case .homeCards, .customize: customizeContent(scale: 1)
        default: Text("Local Turn Nine content").frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private var glossary: some View {
        let terms = [
            ("Apex", "The innermost point of a corner's racing line."),
            ("Braking point", "The place where a driver begins slowing for a turn."),
            ("Kerb", "The raised painted edge beside the racing surface."),
            ("Slipstream", "Reduced air resistance behind another car."),
            ("Sector", "One timed section of a complete lap."),
            ("Stint", "A continuous run between pit stops."),
            ("Undercut", "Pitting earlier to gain time on fresher tyres."),
            ("Warm-up lap", "The preparation lap before the timed start."),
        ]
        let matches = searchQuery.isEmpty ? terms : terms.filter { $0.0.localizedCaseInsensitiveContains(searchQuery) }
        return List(matches, id: \.0) { term in
            VStack(alignment: .leading) { Text(term.0).font(.headline); Text(term.1).foregroundStyle(.secondary) }
        }.searchable(text: $searchQuery, prompt: "Search glossary")
    }

    private var lessons: some View {
        let copy = [
            "Find the apex": "Look through the corner and choose the inside point that leaves a clean exit.",
            "Leave room": "Keep a predictable lane when another car is already alongside.",
            "Read the weather": "Watch surface colour and wind before choosing a braking reference.",
        ]
        return List(["Find the apex", "Leave room", "Read the weather"], id: \.self) { lesson in
            DisclosureGroup(isExpanded: Binding(get: { expandedLesson == lesson }, set: { expandedLesson = $0 ? lesson : "" })) {
                Text(copy[lesson, default: "A short local lesson."])
            } label: { Text(lesson).font(.headline) }
        }
    }

    private var widgetStudio: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Pin to your dashboard").font(.title2.bold())
            Text("Choose a card for your Turn Nine dashboard.").foregroundStyle(.secondary)
            ZStack {
                RoundedRectangle(cornerRadius: 24).fill(Color.white.opacity(0.10))
                VStack(spacing: 10) { Image(systemName: widgetSymbol(selectedWidget)).font(.system(size: 42)); Text(selectedWidget).font(.title3.bold()) }
            }.frame(maxWidth: .infinity).frame(height: 160)
            ForEach(["Lap count", "Race weekend", "Circuit"], id: \.self) { option in
                Button { selectedWidget = option } label: { HStack { Image(systemName: widgetSymbol(option)); Text(option); Spacer(); if selectedWidget == option { Image(systemName: "checkmark.circle.fill") } }.frame(minHeight: 44) }.buttonStyle(.plain)
            }
            Button {
                if !pinnedWidgets.contains(selectedWidget) { pinnedWidgets.append(selectedWidget) }
            } label: {
                Text(pinnedWidgets.contains(selectedWidget) ? "Pinned" : "Pin to dashboard")
                    .font(.body.weight(.semibold))
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .foregroundStyle(pinnedWidgets.contains(selectedWidget) ? Color.white.opacity(0.5) : .black)
                    .background(pinnedWidgets.contains(selectedWidget) ? Color.white.opacity(0.12) : .white, in: Capsule())
            }
            .buttonStyle(.plain).disabled(pinnedWidgets.contains(selectedWidget))
            Spacer()
        }.padding(24)
    }

    private func circuitOutline(_ circuit: String, selected: Bool) -> some View {
        Canvas { context, size in
            var path = Path(); path.move(to: CGPoint(x: 4, y: size.height * 0.6))
            if circuit == "Mesa" { path.addLines([CGPoint(x: 18, y: 5), CGPoint(x: 58, y: 9), CGPoint(x: 72, y: 30), CGPoint(x: 35, y: 40)]); path.closeSubpath() }
            else { path.addCurve(to: CGPoint(x: 40, y: 6), control1: CGPoint(x: 6, y: 8), control2: CGPoint(x: 25, y: 2)); path.addCurve(to: CGPoint(x: 74, y: 28), control1: CGPoint(x: 60, y: 8), control2: CGPoint(x: 77, y: 12)); path.addCurve(to: CGPoint(x: 4, y: size.height * 0.6), control1: CGPoint(x: 55, y: 45), control2: CGPoint(x: 15, y: 42)) }
            context.stroke(path, with: .color(selected ? .yellow : .white.opacity(0.55)), style: StrokeStyle(lineWidth: selected ? 3 : 2, lineCap: .round, lineJoin: .round))
        }.accessibilityHidden(true)
    }

    private func rawTouchDown(owner: Int, referenceY: Double, timestamp: TimeInterval) {
        var sampler = controller
        let frame = sampler.frame(now: now)
        guard let id = PaddockWidgetBentoCardID.allCases.min(by: {
            abs((frame.cardY[$0] ?? Motion.slotY[0]) - referenceY) < abs((frame.cardY[$1] ?? Motion.slotY[0]) - referenceY)
        }), abs((frame.cardY[id] ?? Motion.slotY[0]) - referenceY) <= Theme.Layout.pickRadius else { return }
        if let activeOwner, activeOwner != owner {
            controller.beginPress(id: id, y: referenceY, owner: owner, now: now)
            clearTouch(); wake(at: now); return
        }
        pointerReferenceY = referenceY
        activeOwner = owner; activeCard = id
        // UITouch timestamps share the system-uptime clock; delivery may be delayed by scrolling.
        controller.beginPress(id: id, y: referenceY, owner: owner, now: timestamp)
        wake(at: now)
    }

    private func rawTouchHold(owner: Int, referenceY: Double) {
        guard activeOwner == owner, let id = activeCard else { return }
        pointerReferenceY = referenceY
        controller.beginDrag(id: id, y: referenceY, owner: owner, now: now)
        if controller.isDragging { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        wake(at: now)
    }

    private func rawTouchMove(owner: Int, referenceY: Double) {
        guard activeOwner == owner else { return }
        pointerReferenceY = referenceY
        if controller.isDragging {
            let before = controller.candidateIndex
            controller.updateDrag(y: referenceY, owner: owner, now: now)
            if controller.candidateIndex != before { UISelectionFeedbackGenerator().selectionChanged() }
            wake(at: now)
        }
    }

    private func rawTouchUp(owner: Int) {
        guard activeOwner == owner, let id = activeCard else { return }
        let wasDragging = controller.isDragging
        let changed = controller.endDrag(owner: owner, now: now)
        if changed { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        clearTouch(); wake(at: now)
        if !wasDragging && !controller.isEditing { present(cardDestination(id)) }
    }

    private func rawTouchCancel(owner: Int) {
        guard activeOwner == owner else { return }
        controller.cancelDrag(now: now); clearTouch(); wake(at: now)
    }

    private func accessibleMove(_ id: PaddockWidgetBentoCardID, offset: Int) {
        let time = now
        if !controller.isEditing { controller.setEditing(true, now: time) }
        if controller.move(id: id, offset: offset, now: time) { UIAccessibility.post(notification: .announcement, argument: "\(cardName(id)) moved") }
        wake(at: time)
    }

    private func doneEditing() { let time = now; controller.done(now: time); wake(at: time) }
    private func restoreOrder() {
        let time = now
        controller.done(now: time)
        controller = PaddockWidgetBentoController(); controller.appear(autoPlay: autoPlay, quiet: quiet, now: time)
        controller.setVisible(selectedTab == .home && feedAtTop && destination == nil, now: time)
        wake(at: time)
    }

    private func handleAction(_ action: PaddockWidgetBentoDestination) {
        if action == .closeGuide { guideVisible = false; return }
        present(action == .banner ? .widgetStudio : action)
    }

    private func present(_ value: PaddockWidgetBentoDestination) {
        let time = now
        controller.cancelDrag(now: time); controller.setVisible(false, now: time)
        destination = value; wake(at: time)
    }

    private func destinationChanged(_ value: PaddockWidgetBentoDestination?) {
        if value != .reflex { cancelReaction() }
        let time = now
        controller.setVisible(value == nil && selectedTab == .home && feedAtTop, now: time); wake(at: time)
    }

    private func selectTab(_ tab: PaddockWidgetBentoDestination) {
        guard selectedTab != tab else { return }
        clearTouch(); let time = now
        if controller.isEditing { controller.done(now: time) }
        selectedTab = tab; feedAtTop = true; feedOffset = 0
        controller.setVisible(tab == .home, now: time); wake(at: time)
    }

    private func updateFeed(_ minY: Double, scale: CGFloat, explicitTop: Bool? = nil) {
        feedOffset = minY
        let top = explicitTop ?? (minY >= -4 * scale)
        guard selectedTab == .home, feedAtTop != top else { return }
        feedAtTop = top; let time = now; controller.setVisible(top && destination == nil, now: time); wake(at: time)
    }

    private func appearIfNeeded() {
        let time = now
        if !appeared {
            appeared = true
            controller.appear(autoPlay: autoPlay, quiet: quiet, now: time)
        }
        controller.setAutoPlay(autoPlay, now: time)
        controller.setQuiet(quiet, now: time)
        controller.setVisible(selectedTab == .home && feedAtTop && destination == nil, now: time)
        controller.setActive(UIApplication.shared.applicationState == .active, now: time)
        wake(at: time)
    }

    private func lifecycleChanged(_ phase: ScenePhase) {
        let time = now; controller.setActive(phase == .active, now: time)
        if phase != .active { clearTouch(); cancelReaction() }; wake(at: time)
    }

    private func quietChanged(_ value: Bool) { let time = now; controller.setQuiet(value, now: time); wake(at: time) }

    private func wake(at time: TimeInterval) {
        cleanupExtras(time)
        controller.advance(now: time); revision += 1; syncCadence(at: time)
    }

    /// Ends the touch-driven effects whose time has passed.
    private func cleanupExtras(_ time: TimeInterval) {
        if let start = livePitStart, time >= start + Motion.Pit.duration { livePitStart = nil }
        if let start = peelReleaseStart, time >= start + peelReleaseTotal { peelReleaseStart = nil; peelDistance = 0 }
    }

    /// True while the jiggle or a held sticker needs frames until an event ends them.
    private var continuousMotion: Bool { peelHeld || (controller.isEditing && !reduceMotion) }

    private func syncCadence(at time: TimeInterval) {
        wakeTask?.cancel(); wakeTask = nil
        let interval = controller.cadence(now: time)
        let fast = interval.map { $0 <= 1.0 / 55 } ?? false
        var extraEnd: TimeInterval?
        if let start = livePitStart { extraEnd = start + Motion.Pit.duration }
        if let start = peelReleaseStart { extraEnd = max(extraEnd ?? 0, start + peelReleaseTotal) }
        clockActive = continuousMotion || fast || extraEnd != nil
        displayClock.update(controller: controller, running: clockActive)
        var wait: TimeInterval?
        if !continuousMotion {
            if let extraEnd { wait = max(0.001, extraEnd - time) }
            // Schedule both ends of a motion window, independently of the display clock.
            // Like the React Native runtime, the controller owns every wake; the timeline only draws.
            let boundary = fast ? (controller.frameWindowEnd(now: time) ?? interval) : interval
            if let boundary { wait = min(wait ?? boundary, max(0.001, boundary)) }
        }
        guard let wait else { return }
        wakeTask = Task { @MainActor in
            do { try await Task.sleep(for: .seconds(wait)) } catch { return }
            guard !Task.isCancelled else { return }; wake(at: now)
        }
    }

    private func clearTouch() { activeOwner = nil; activeCard = nil }
    private func cancelTasks() { wakeTask?.cancel(); clearTouch(); cancelReaction() }
    private func cancelReaction() { reactionTask?.cancel(); reactionTask = nil; reactionStart = nil; reactionText = "Ready" }

    private func reactionAction() {
        if reactionText.hasSuffix(" ms") { cancelReaction(); return }
        if reactionText == "Ready" || reactionText.hasPrefix("Too soon") {
            reactionText = "Get ready…"; reactionTask?.cancel()
            reactionTask = Task { @MainActor in
                do { try await Task.sleep(for: .seconds(3)) } catch { return }
                guard destination == .reflex, scenePhase == .active else { return }
                reactionStart = now; reactionText = "GO"
            }
        } else if reactionText == "GO", let reactionStart {
            reactionText = "\(Int((now - reactionStart) * 1000)) ms"; self.reactionStart = nil
        } else { cancelReaction(); reactionText = "Too soon. Try again" }
    }

    private var reactionButtonTitle: String { reactionText == "Ready" || reactionText.hasPrefix("Too soon") ? "Start test" : reactionText.hasSuffix(" ms") ? "Try again" : "Tap when ready" }
    private func cardDestination(_ id: PaddockWidgetBentoCardID) -> PaddockWidgetBentoDestination { id == .laps ? .laps : id == .reflex ? .reflex : .homeCards }
    private func cardName(_ id: PaddockWidgetBentoCardID) -> String { id == .laps ? "Every lap counts" : id == .reflex ? "Race day reflex test" : "Custom home cards" }
    private func dockSymbol(_ tab: PaddockWidgetBentoDestination) -> String { tab == .calendar ? "calendar" : tab == .results ? "list.number" : tab == .circuits ? "point.topleft.down.to.point.bottomright.curvepath" : tab == .customize ? "square.grid.2x2" : "house.fill" }
    private func widgetSymbol(_ widget: String) -> String { widget == "Lap count" ? "figure.walk" : widget == "Race weekend" ? "calendar" : "point.topleft.down.to.point.bottomright.curvepath" }
    private func sheetTitle(_ value: PaddockWidgetBentoDestination) -> String { value == .widgetStudio || value == .banner ? "Widget Studio" : value.rawValue.capitalized }
}
