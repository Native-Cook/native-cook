import SwiftUI
import UIKit

private struct PitStopReviewFeedTopKey: PreferenceKey {
    static var defaultValue = 0.0
    static func reduce(value: inout Double, nextValue: () -> Double) { value = nextValue() }
}

private struct PitStopReviewRowYKey: PreferenceKey {
    static var defaultValue = 0.0
    static func reduce(value: inout Double, nextValue: () -> Double) { value = nextValue() }
}

private struct PitStopReviewScrollSnapshot: Equatable {
    var offset: Double
    var atTop: Bool
}

struct PitStopReview: View {
    typealias Motion = PitStopReviewMotion
    typealias Theme = PitStopReviewTheme
    typealias Colors = PitStopReviewTheme.Colors

    static let loop = PitStopReviewMotion.loop
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver
    @Environment(\.scenePhase) private var scenePhase
    @State private var controller: PitStopReviewController
    @State private var cadence = PitStopReviewController.Cadence.stopped
    @State private var timelineRevision = 0
    @State private var wakeTask: Task<Void, Never>?
    @State private var panelVisible = false
    @State private var feedAtTop = true
    @State private var destination: PitStopReviewDestination?
    @State private var selectedTab = PitStopReviewTab.home
    @State private var selectedCircuit = "Harbor"
    @State private var savedWeekend: Set<String> = []
    @State private var passSaved = false
    @State private var cards = [true, true, true]
    @State private var lapCount = 3
    @State private var displayName = "Alex Vale"
    @State private var gameText = "Ready"
    @State private var gameStart: TimeInterval?
    @State private var gameTask: Task<Void, Never>?
    @State private var sharePresented = false
    @State private var shareLockUntil = 0.0
    @State private var keyboardHeight = 0.0
    @State private var rowY = 0.0
    @FocusState private var noteFocused: Bool

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        _controller = State(initialValue: PitStopReviewController(autoPlay: autoPlay, anchor: ProcessInfo.processInfo.systemUptime))
        _cadence = State(initialValue: autoPlay ? .animation : .stopped)
    }

    private var quiet: Bool { reduceMotion || voiceOver }
    private var now: TimeInterval { ProcessInfo.processInfo.systemUptime }
    /// Convert TimelineView's scheduled wall-clock tick to the monotonic clock used by the controller.
    private func frameTime(_ date: Date) -> TimeInterval { now + date.timeIntervalSinceNow }
    private var presentedRating: Int { controller.displayRating(now) }
    private var presentedNote: String { controller.displayNote(now) }
    private var isPanel: Bool { panelVisible }

    var body: some View {
        GeometryReader { proxy in
            let width = proxy.size.width, height = proxy.size.height
            let s = width / Theme.referenceWidth
            let insets = PitStopReviewSafeArea.insets
            ZStack(alignment: .topLeading) {
                Colors.bg
                feed(width: width, s: s, insets: insets)
                tabBar(width: width, height: height, s: s)
                composer(width: width, height: height, s: s, insets: insets)
                PitStopReviewWarmup().allowsHitTesting(false)
            }
            .frame(width: width, height: height, alignment: .topLeading)
            .sheet(item: $destination) { destinationSheet($0) }
            .sheet(isPresented: $sharePresented) { PitStopReviewShareTextActivity(items: [shareText]).presentationDetents([.medium, .large]) }
            .onChange(of: scenePhase) { _, phase in
                let time = now
                controller.setForeground(phase == .active, at: time)
                if phase != .active { cancelReaction() }
                wake(at: time)
            }
            .onChange(of: destination) { oldValue, newValue in
                let time = now
                controller.setDestinationOwned(newValue != nil, at: time)
                if oldValue == .reflex && newValue != .reflex { cancelReaction() }
                wake(at: time)
            }
            .onChange(of: quiet) { _, enabled in let time = now; controller.setQuiet(enabled, at: time); wake(at: time) }
            .onChange(of: autoPlay) { _, enabled in let time = now; controller.setAutoPlay(enabled, at: time); wake(at: time) }
            .onChange(of: sharePresented) { old, active in
                let time = now
                controller.setSharing(active, at: time)
                if old && !active { shareLockUntil = time + 0.45 }
                wake(at: time)
            }
            .onChange(of: selectedTab) { _, tab in
                let time = now
                let home = tab == .home
                if home { feedAtTop = true; controller.setFeedAtTop(true, at: time) }
                controller.setHomeSelected(home, at: time)
                wake(at: time)
            }
            .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillChangeFrameNotification)) { note in
                keyboardChanged(note, screenHeight: height)
            }
            .preferredColorScheme(.dark)
            .environment(\.colorScheme, .dark)
        }
        .ignoresSafeArea()
        .ignoresSafeArea(.keyboard)
        .onAppear {
            let time = now
            controller.setAutoPlay(autoPlay, at: time)
            controller.setQuiet(quiet, at: time)
            controller.setForeground(scenePhase == .active, at: time)
            controller.setHomeSelected(selectedTab == .home, at: time)
            wake(at: time)
        }
        .onDisappear { wakeTask?.cancel(); wakeTask = nil; cancelReaction() }
    }

    private var shareText: String {
        let trimmed = controller.note.trimmingCharacters(in: .whitespacesAndNewlines)
        return "Turn Nine — \(controller.rating)/5" + (trimmed.isEmpty ? "" : "\n\(trimmed)")
    }

    // MARK: Clock

    private func wake(at time: TimeInterval) {
        wakeTask?.cancel()
        cadence = controller.cadence(time)
        panelVisible = controller.panelVisible(time)
        timelineRevision &+= 1
        scheduleWake(at: time)
    }

    private func cadenceChanged(_ next: PitStopReviewController.Cadence, at time: TimeInterval) {
        guard cadence != next else { return }
        wakeTask?.cancel()
        cadence = next
        timelineRevision &+= 1
        scheduleWake(at: time)
    }

    private func scheduleWake(at time: TimeInterval) {
        guard cadence == .stopped,
              let delay = controller.nextWakeDelay(time), delay > 0 else { wakeTask = nil; return }
        wakeTask = Task { @MainActor in
            do { try await Task.sleep(for: .seconds(delay + 0.012)) } catch { return }
            guard !Task.isCancelled else { return }
            cadence = .animation
            timelineRevision &+= 1
            wakeTask = nil
        }
    }

    // MARK: Feed

    private func feed(width: CGFloat, s: CGFloat, insets: EdgeInsets) -> some View {
        ScrollViewReader { reader in
            ScrollView(showsIndicators: false) {
                ZStack(alignment: .topLeading) {
                    Color.clear.frame(width: 1, height: 1).id("top")
                    homeContent(width: width, s: s)
                        .frame(width: width, height: 1030 * s, alignment: .topLeading)
                        .offset(y: insets.top)
                        .accessibilityHidden(selectedTab != .home)
                        .allowsHitTesting(selectedTab == .home)
                    ForEach([PitStopReviewTab.calendar, .results, .circuits, .customize], id: \.self) { tab in
                        tabScreen(tab, s: s)
                            .frame(width: width, height: 1030 * s + 120 * s, alignment: .topLeading)
                            .background(Colors.bg)
                            .offset(y: insets.top)
                            .opacity(selectedTab == tab ? 1 : 0)
                            .allowsHitTesting(selectedTab == tab)
                            .accessibilityHidden(selectedTab != tab)
                    }
                }
                .frame(width: width, height: insets.top + 1030 * s + 120 * s, alignment: .topLeading)
                .background {
                    GeometryReader { reader in
                        Color.clear.preference(key: PitStopReviewFeedTopKey.self, value: reader.frame(in: .named("PitStopReviewFeed")).minY)
                    }
                }
            }
            .scrollDisabled(isPanel)
            .modifier(PitStopReviewFeedPositionObserver(scale: s, onChange: updateFeedPosition))
            .coordinateSpace(name: "PitStopReviewFeed")
            .accessibilityHidden(isPanel)
            .onChange(of: selectedTab) { _, _ in reader.scrollTo("top", anchor: .top) }
        }
    }

    private func updateFeedPosition(_ snapshot: PitStopReviewScrollSnapshot) {
        guard selectedTab == .home, feedAtTop != snapshot.atTop else { return }
        feedAtTop = snapshot.atTop
        let time = now
        controller.setFeedAtTop(snapshot.atTop, at: time)
        wake(at: time)
    }

    private func homeContent(width: CGFloat, s: CGFloat) -> some View {
        let t = Theme.HeroText.self
        return ZStack(alignment: .topLeading) {
            LinearGradient(stops: Theme.pageTone, startPoint: .top, endPoint: .bottom)
                .frame(width: width, height: Theme.pageToneHeight * s)
            TimelineView(.animation(paused: cadence == .stopped)) { timeline in
                let time = frameTime(timeline.date)
                let next = controller.cadence(time)
                PitStopReviewHero(width: width, scale: s, scene: controller.scene(time))
                    .onChange(of: next, initial: true) { _, value in cadenceChanged(value, at: time) }
            }
            .id(timelineRevision)
            .offset(y: Theme.Hero.top * s)
            clubPass(s: s)
            avatar(width: width, s: s)
            Text("Enjoying Turn Nine?")
                .font(PitStopReviewFont.font(.bold, size: t.title.size * s)).tracking(t.title.tracking * s).foregroundStyle(Colors.ink)
                .multilineTextAlignment(.center).lineLimit(1).fixedSize()
                .frame(width: width)
                .alignmentGuide(.top) { $0[.firstTextBaseline] }
                .offset(x: (t.centerX - 201) * s, y: t.title.baseline * s)
            Text("Share a quick review — help more fans find their next race-day companion.")
                .font(PitStopReviewFont.font(.medium, size: t.subtitle.size * s))
                .lineSpacing((t.subtitle.lineHeight - 1.219 * t.subtitle.size) * s)
                .foregroundStyle(Colors.ink).multilineTextAlignment(.center)
                .frame(width: t.subtitle.width * s)
                .alignmentGuide(.top) { $0[.firstTextBaseline] }
                .offset(x: (t.centerX - t.subtitle.width / 2) * s, y: t.subtitle.baseline * s)
            writeButton(s: s)
            pager(width: width, s: s)
            PitStopReviewRail(scale: s, width: width, onOpen: openDestination)
            PitStopReviewBento(scale: s, cards: cards, onOpen: openDestination)
            PitStopReviewStatsCard(scale: s, onOpen: openDestination)
        }
    }

    private func clubPass(s: CGFloat) -> some View {
        typealias CP = Theme.ClubPass
        return Button { openDestination(.pass) } label: {
            HStack(spacing: CP.gap * s) {
                Circle().fill(.white).frame(width: CP.badge * s, height: CP.badge * s)
                    .overlay { PitStopReviewSparkle().fill(Colors.violet).frame(width: CP.sparkleW * s, height: CP.sparkleH * s) }
                Text("Club Pass").font(.system(size: CP.fontSize * s, weight: CP.fontWeight)).tracking(CP.letterSpacing * s).foregroundStyle(.white)
                    .lineLimit(1).fixedSize()
                    .frame(height: CP.lineHeight * s)
            }
            .padding(.leading, CP.padLeft * s).padding(.trailing, CP.padRight * s)
            .frame(height: CP.height * s)
            .background(Capsule().fill(Colors.violet))
            .padding(.vertical, 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Club Pass")
        .offset(x: CP.left * s, y: (Theme.barCenter - CP.height / 2) * s - 8)
    }

    private func avatar(width: CGFloat, s: CGFloat) -> some View {
        typealias A = Theme.Avatar
        let label = displayName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Profile" : "\(displayName) profile"
        return Button { openDestination(.profile) } label: {
            Circle().fill(Color.white.opacity(0.16))
                .overlay { Circle().strokeBorder(Color.white.opacity(0.22), lineWidth: 0.67) }
                .frame(width: A.ring * s, height: A.ring * s)
                .overlay {
                    Image(uiImage: PitStopReviewAssets.profile).resizable().scaledToFill()
                        .frame(width: A.photo * s, height: A.photo * s).clipShape(Circle())
                }
                .frame(width: A.hit * s, height: A.hit * s)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .offset(x: width - A.right * s - A.hit * s, y: (Theme.barCenter - A.hit / 2) * s)
    }

    private func writeButton(s: CGFloat) -> some View {
        let c = Theme.HeroText.cta
        return TimelineView(.animation(paused: cadence == .stopped)) { timeline in
            Button { openEditor() } label: {
                Text("Write a review").font(PitStopReviewFont.font(.semibold, size: c.size * s)).tracking(c.tracking * s)
                    .foregroundStyle(Colors.ctaInk)
                    .frame(width: c.width * s, height: c.height * s)
                    .background(Capsule().fill(Colors.ink))
            }
            .buttonStyle(PitStopReviewCtaStyle(sceneScale: Motion.ctaScale(controller.scene(frameTime(timeline.date)))))
        }
        .id(timelineRevision)
        .frame(width: c.width * s, height: c.height * s)
        .offset(x: (Theme.HeroText.centerX - c.width / 2) * s, y: c.top * s)
    }

    private func pager(width: CGFloat, s: CGFloat) -> some View {
        typealias P = Theme.Pager
        let total = (P.active + P.dot * 2 + P.gap * 2) * s
        return HStack(spacing: P.gap * s) {
            Capsule().fill(.white).frame(width: P.active * s, height: 4 * s)
            Circle().fill(Color.white.opacity(0.38)).frame(width: P.dot * s, height: P.dot * s)
            Circle().fill(Color.white.opacity(0.38)).frame(width: P.dot * s, height: P.dot * s)
        }
        .frame(width: total, height: P.height * s)
        .offset(x: (width - total) / 2, y: P.top * s)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    // MARK: Tab screens

    private func tabScreen(_ tab: PitStopReviewTab, s: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 18 * s) {
            Text(tab.rawValue).font(.system(size: 30 * s, weight: .bold)).foregroundStyle(Colors.ink)
            Text(subtitle(tab)).font(.system(size: 15 * s)).foregroundStyle(Colors.muted)
            switch tab {
            case .calendar: calendarPage(s: s)
            case .results: resultsPage(s: s)
            case .circuits: circuitsPage(s: s)
            default: customizePage(s: s)
            }
        }
        .padding(.horizontal, 24 * s).padding(.top, 28 * s)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private func subtitle(_ tab: PitStopReviewTab) -> String {
        switch tab {
        case .calendar: "Keep a weekend on your list"
        case .results: "Turn Nine standings"
        case .circuits: "Explore the circuit guides"
        case .customize: "Choose which cards appear on Home"
        case .home: ""
        }
    }

    private func calendarPage(s: CGFloat) -> some View {
        VStack(spacing: 12 * s) {
            ForEach(["Harbor Circuit", "Juniper Park"], id: \.self) { venue in
                Toggle("\(venue) · Race weekend", isOn: Binding(
                    get: { savedWeekend.contains(venue) },
                    set: { saved in if saved { savedWeekend.insert(venue) } else { savedWeekend.remove(venue) } }
                ))
                .tint(Colors.yellow).foregroundStyle(Colors.ink)
                .frame(minHeight: 44).padding(16 * s).background(.white.opacity(0.07), in: RoundedRectangle(cornerRadius: 18 * s))
            }
        }
    }

    private static let racers: [(String, Int)] = [("Mira Chen", 42), ("Alex Vale", 38), ("Sam Ives", 34), ("Noah Reed", 29)]

    private func resultsPage(s: CGFloat) -> some View {
        VStack(spacing: 0) {
            ForEach(Array(Self.racers.enumerated()), id: \.offset) { index, racer in
                HStack {
                    Text("\(index + 1)").foregroundStyle(Colors.muted)
                    Text(racer.0).padding(.leading, 14)
                    Spacer()
                    Text("\(racer.1) pts").foregroundStyle(Colors.muted)
                }
                .frame(minHeight: 56).overlay(alignment: .bottom) { Divider().overlay(.white.opacity(0.12)) }
            }
        }.foregroundStyle(Colors.ink)
    }

    private func circuitsPage(s: CGFloat) -> some View {
        VStack(spacing: 10) {
            ForEach(["Harbor", "Juniper", "Mesa"], id: \.self) { circuit in circuitRow(circuit) }
            Text("\(selectedCircuit) Circuit · A short track guide").font(.system(size: 15 * s)).foregroundStyle(Colors.muted).padding(.top, 8)
        }
    }

    private func customizePage(s: CGFloat) -> some View {
        VStack(spacing: 10) {
            ForEach(0..<3, id: \.self) { index in
                Toggle(["Every lap counts", "Race day reflex test", "Custom home cards"][index], isOn: $cards[index])
                    .tint(Colors.yellow).foregroundStyle(Colors.ink)
                    .frame(minHeight: 44).padding(16 * s).background(.white.opacity(0.07), in: RoundedRectangle(cornerRadius: 18 * s))
            }
            Button("Restore cards") { cards = [true, true, true] }.buttonStyle(.bordered).tint(Colors.ink).frame(minHeight: 44)
        }
    }

    // MARK: Dock

    private func tabBar(width: CGFloat, height: CGFloat, s: CGFloat) -> some View {
        PitStopReviewTabBar(active: selectedTab, scale: s, disabled: isPanel, quiet: quiet) { tab in selectTab(tab) }
            .accessibilityHidden(isPanel)
            .allowsHitTesting(!isPanel)
            .frame(width: width, height: height, alignment: .bottom)
            .padding(.bottom, 0)
            .offset(y: -Theme.TabBar.bottom * s)
            .frame(width: width, height: height, alignment: .topLeading)
    }

    private func selectTab(_ tab: PitStopReviewTab) {
        noteFocused = false
        destination = nil
        selectedTab = tab
    }

    // MARK: Composer

    private func keyboardChanged(_ note: Notification, screenHeight: CGFloat) {
        guard let frame = (note.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? NSValue)?.cgRectValue else { return }
        let h = frame.minY >= screenHeight ? 0 : Double(screenHeight - frame.minY)
        let duration = (note.userInfo?[UIResponder.keyboardAnimationDurationUserInfoKey] as? Double) ?? 0.25
        let was = keyboardHeight > 0
        withAnimation(.easeOut(duration: duration)) { keyboardHeight = h }
        if (h > 0) != was {
            let time = now
            controller.setKeyboardPresented(h > 0, at: time)
            wake(at: time)
        }
    }

    private func composer(width: CGFloat, height: CGFloat, s: CGFloat, insets: EdgeInsets) -> some View {
        let panelBottom = 16 * s + (keyboardHeight > 0 ? keyboardHeight : insets.bottom)
        let panelHeight = min(422 * s, height - keyboardHeight - insets.top - 32)
        let slots = slotList(width: width, height: height, s: s, panelBottom: panelBottom, panelHeight: panelHeight)
        return ZStack(alignment: .topLeading) {
            TimelineView(.animation(paused: cadence == .stopped)) { timeline in
                let time = frameTime(timeline.date)
                let scene = controller.scene(time)
                let p = controller.panel(time)
                ZStack(alignment: .topLeading) {
                    LinearGradient(stops: [.init(color: .black.opacity(0), location: 0), .init(color: .black.opacity(0.12), location: 0.34),
                                           .init(color: .black.opacity(0.62), location: 0.6)], startPoint: .top, endPoint: .bottom)
                        .frame(width: width, height: height)
                        .contentShape(Rectangle())
                        .onTapGesture { closeFromBackdrop() }
                        .opacity(p)
                        .allowsHitTesting(isPanel)
                        .accessibilityHidden(true)
                    panel(s: s, scene: scene, width: panelWidth(s), height: panelHeight)
                        .frame(width: 370 * s, height: panelHeight)
                        .opacity(p)
                        .scaleEffect(quiet ? 1 : Motion.M.panelScale + (1 - Motion.M.panelScale) * p)
                        .offset(y: quiet ? 0 : (1 - p) * Motion.M.panelRise * s)
                        .offset(x: (width - 370 * s) / 2, y: height - panelBottom - panelHeight)
                        .allowsHitTesting(isPanel && !sharePresented)
                        .accessibilityHidden(!isPanel)
                        .accessibilityAddTraits(.isModal)
                }
                .frame(width: width, height: height, alignment: .topLeading)
                .onChange(of: controller.panelVisible(time), initial: true) { _, value in panelVisible = value }
            }
            .id(timelineRevision)
            if !quiet {
                TimelineView(.animation(paused: cadence == .stopped)) { timeline in
                    PitStopReviewLights(width: width, height: height, scale: s, top: insets.top + Theme.Hero.top * s, slots: slots, scene: controller.scene(frameTime(timeline.date)))
                }
                .id(timelineRevision)
            }
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .onPreferenceChange(PitStopReviewRowYKey.self) { rowY = $0 }
    }

    private func panelWidth(_ s: CGFloat) -> CGFloat { 370 * s }

    /// Window positions of the five star slots when the composer is open (for the light → star flight).
    private func slotList(width: CGFloat, height: CGFloat, s: CGFloat, panelBottom: CGFloat, panelHeight: CGFloat) -> [CGPoint] {
        let ST = Theme.Stars.self
        let top = height - panelBottom - panelHeight + rowY + ST.height * s / 2
        return (0..<5).map { CGPoint(x: width / 2 - ST.row * s / 2 + (Double($0) + 0.5) * ST.cell * s, y: top) }
    }

    private func panel(s: CGFloat, scene: Double, width: CGFloat, height: CGFloat) -> some View {
        let ST = Theme.Stars.self
        let rating = presentedRating
        let noteBinding = Binding<String>(get: { presentedNote }, set: { setNote($0) })
        return ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .top, spacing: 8) {
                    VStack(alignment: .leading, spacing: 0) {
                        Text("Your race-day take").font(.system(size: 24 * s, weight: .bold)).foregroundStyle(Colors.ink)
                        Text("What has your experience been like?").font(.system(size: 15 * s)).foregroundStyle(Colors.muted).padding(.top, 6 * s)
                    }
                    Spacer(minLength: 0)
                    Button(action: closeEditor) {
                        Image(systemName: "xmark").font(.system(size: 14, weight: .bold)).foregroundStyle(Colors.ink)
                            .frame(width: 44, height: 44).background(Color.white.opacity(0.08), in: Circle())
                    }
                    .buttonStyle(.plain).padding(.top, -4).accessibilityLabel("Close review")
                }
                HStack(spacing: 0) {
                    ForEach(1...5, id: \.self) { n in
                        PitStopReviewStarSlot(index: n - 1, shown: rating > 0 || quiet, selected: rating > 0 && n <= rating, scene: scene, s: s)
                            .frame(width: ST.cell * s, height: ST.height * s)
                            .accessibilityElement(children: .ignore)
                            .accessibilityLabel("\(n) \(n == 1 ? "star" : "stars")")
                            .accessibilityValue(n == rating ? "Selected" : "")
                            .accessibilityAddTraits(.isButton)
                            .accessibilityAction { updateRating(n) }
                    }
                }
                .frame(width: ST.row * s, height: ST.height * s)
                .contentShape(Rectangle())
                .overlay {
                    PitStopReviewRatingTouch { x in
                        updateRating(min(5, max(1, Int((x / (ST.row * s) * 5).rounded(.up)))))
                    }
                    .accessibilityHidden(true)
                }
                .frame(maxWidth: .infinity)
                .padding(.top, 12 * s)
                .background { GeometryReader { g in Color.clear.preference(key: PitStopReviewRowYKey.self, value: g.frame(in: .named("PitStopReviewPanelContent")).minY + 12 * s) } }
                noteBox(binding: noteBinding, s: s)
                Text("\(presentedNote.count)/280").font(.system(size: 11 * s)).foregroundStyle(Colors.muted)
                    .frame(maxWidth: .infinity, alignment: .trailing).padding(.top, 4 * s)
                Button { share() } label: {
                    Text(sharePresented ? "Sharing…" : "Share review").font(.system(size: 16 * s, weight: .bold))
                        .foregroundStyle(rating > 0 ? Colors.ctaInk : Colors.muted)
                        .frame(maxWidth: .infinity).frame(height: 48 * s)
                        .background(Capsule().fill(Colors.ink))
                        .opacity(rating > 0 ? 1 : 0.55)
                }
                .buttonStyle(.plain).disabled(rating == 0 || sharePresented).padding(.top, 12 * s)
                Button { updateRating(0) } label: {
                    Text("Reset rating").font(.system(size: 14 * s)).foregroundStyle(Colors.muted)
                        .frame(maxWidth: .infinity, minHeight: 44 * s, alignment: .leading).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 24 * s).padding(.top, 24 * s).padding(.bottom, 28 * s)
            .coordinateSpace(name: "PitStopReviewPanelContent")
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Colors.panel)
        .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 28, style: .continuous).strokeBorder(Color.white.opacity(0.12), lineWidth: 1))
        .shadow(color: .black.opacity(0.5), radius: 30)
        .environment(\.colorScheme, .dark)
    }

    private func noteBox(binding: Binding<String>, s: CGFloat) -> some View {
        ZStack(alignment: .topLeading) {
            if presentedNote.isEmpty {
                Text("Add a note (optional)").font(.system(size: 15 * s)).foregroundStyle(Colors.muted).padding(.top, 17 * s).padding(.leading, 18 * s)
                    .allowsHitTesting(false)
            }
            TextEditor(text: binding)
                .focused($noteFocused)
                .font(.system(size: 15 * s)).foregroundStyle(Colors.ink).tint(.white)
                .scrollContentBackground(.hidden)
                .padding(.horizontal, 14 * s - 5).padding(.vertical, 14 * s - 8)
                .accessibilityLabel("Review note")
        }
        .frame(height: 110 * s, alignment: .topLeading)
        .background(Color.white.opacity(0.07), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(Color.white.opacity(0.10), lineWidth: 1))
        .padding(.top, 10 * s)
    }

    private func openEditor() {
        noteFocused = false
        let time = now
        controller.openEditor(at: time)
        wake(at: time)
    }
    private func closeEditor() {
        noteFocused = false
        let time = now
        controller.closeEditor(at: time)
        wake(at: time)
    }
    /// A tap that dismissed the share sheet must not also close the editor.
    private func closeFromBackdrop() {
        if controller.sharing || now < shareLockUntil { return }
        closeEditor()
    }
    private func updateRating(_ value: Int) {
        let n = max(0, min(5, value))
        let changed = !controller.editorOwned || n != controller.rating
        let time = now
        controller.setRating(n, at: time)
        if changed { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        wake(at: time)
    }
    private func setNote(_ value: String) {
        let time = now
        controller.setNote(value, at: time)
        wake(at: time)
    }
    private func share() {
        noteFocused = false
        let time = now
        if controller.beginShare(at: time) { sharePresented = true }
        wake(at: time)
    }
    private func openDestination(_ next: PitStopReviewDestination) {
        noteFocused = false
        if next == .reflex { gameTask?.cancel(); gameText = "Ready"; gameStart = nil }
        destination = next
    }

    // MARK: Destinations

    private func cancelReaction() {
        gameTask?.cancel(); gameTask = nil; gameStart = nil; gameText = "Ready"
    }

    private func destinationSheet(_ destination: PitStopReviewDestination) -> some View {
        NavigationStack {
            Group {
                switch destination {
                case .pass:
                    List { Section("Club Pass") { Text("Weekend notes"); Text("Personal home cards"); Text("Saved race weekends") }; Section { Toggle(passSaved ? "Pass saved" : "Save pass", isOn: $passSaved) } }
                case .profile:
                    Form { TextField("Display name", text: $displayName); Text("Your dashboard display name.").foregroundStyle(.secondary) }
                case .calendar:
                    List { ForEach(["Harbor Circuit", "Juniper Park"], id: \.self) { venue in Toggle("\(venue) · Race weekend", isOn: Binding(get: { savedWeekend.contains(venue) }, set: { isSaved in if isSaved { savedWeekend.insert(venue) } else { savedWeekend.remove(venue) } })) } }
                case .reflex:
                    VStack(spacing: 24) { Text(gameText).font(.system(size: 32, weight: .bold)); Button(gameButtonTitle) { runGameAction() }.buttonStyle(.borderedProminent) }.frame(maxWidth: .infinity, maxHeight: .infinity).background(Color.black)
                case .customize:
                    Form { ForEach(0..<3, id: \.self) { index in Toggle(["Every lap counts", "Race day reflex test", "Custom home cards"][index], isOn: $cards[index]) }; Button("Restore cards") { cards = [true, true, true] } }
                case .laps:
                    VStack(spacing: 20) { Text("Every lap counts").font(.largeTitle.bold()); Text("This week · 18.4 km"); Text("Today's logged laps: \(lapCount)"); Button("Log a lap") { lapCount += 1 }.buttonStyle(.borderedProminent) }.frame(maxWidth: .infinity, maxHeight: .infinity).background(Color.black)
                case .pitcast:
                    List { Section("The weekend brief · Alex Vale / Sam Ives") { Text("A cleaner first lap — Leave room before the first turn."); Text("Watch the weather — Coastal cloud can change quickly."); Text("One thing to notice — How the field settles after the restart.") } }
                case .results:
                    List { ForEach(Array(Self.racers.enumerated()), id: \.offset) { index, racer in HStack { Text("\(index + 1)"); Text(racer.0); Spacer(); Text("\(racer.1) pts") } } }
                case .circuits:
                    List {
                        ForEach(["Harbor", "Juniper", "Mesa"], id: \.self) { circuit in circuitRow(circuit) }
                        Section("Selected circuit") { Text("\(selectedCircuit) Circuit · A short track guide") }
                    }
                }
            }
            .navigationTitle(sheetTitle(destination)).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Done") { self.destination = nil } } }
            .preferredColorScheme(.dark)
        }
        .presentationDetents(destination == .profile || destination == .pass ? [.medium] : [.medium, .large])
        .presentationDragIndicator(.visible)
    }

    private func sheetTitle(_ destination: PitStopReviewDestination) -> String {
        switch destination {
        case .pass: "Club Pass"; case .profile: "Profile"; case .calendar: "Race weekends"; case .reflex: "Reaction test"
        case .customize: "Home cards"; case .laps: "Every lap counts"; case .pitcast: "The weekend brief"; case .results: "Results"; case .circuits: "Circuits"
        }
    }

    private func circuitRow(_ circuit: String) -> some View {
        Button { selectedCircuit = circuit } label: {
            HStack(spacing: 14) {
                circuitOutline(circuit, selected: selectedCircuit == circuit).frame(width: 72, height: 44)
                Text(circuit)
                Spacer()
                if selectedCircuit == circuit { Image(systemName: "checkmark.circle.fill").foregroundStyle(Colors.yellow) }
            }
            .frame(minHeight: 44)
        }
        .accessibilityLabel("\(circuit) circuit")
        .accessibilityValue(selectedCircuit == circuit ? "Selected" : "")
    }

    private func circuitOutline(_ circuit: String, selected: Bool) -> some View {
        Canvas { context, size in
            var path = Path()
            switch circuit {
            case "Juniper":
                path.move(to: CGPoint(x: 8, y: size.height * 0.62)); path.addCurve(to: CGPoint(x: 34, y: 7), control1: CGPoint(x: 4, y: 12), control2: CGPoint(x: 24, y: 4)); path.addCurve(to: CGPoint(x: 64, y: 30), control1: CGPoint(x: 54, y: 9), control2: CGPoint(x: 69, y: 16)); path.addCurve(to: CGPoint(x: 8, y: size.height * 0.62), control1: CGPoint(x: 54, y: 43), control2: CGPoint(x: 18, y: 40))
            case "Mesa":
                path.move(to: CGPoint(x: 7, y: 32)); path.addLine(to: CGPoint(x: 18, y: 8)); path.addLine(to: CGPoint(x: 48, y: 8)); path.addLine(to: CGPoint(x: 65, y: 22)); path.addLine(to: CGPoint(x: 53, y: 39)); path.addLine(to: CGPoint(x: 25, y: 34)); path.closeSubpath()
            default:
                path.move(to: CGPoint(x: 6, y: 26)); path.addCurve(to: CGPoint(x: 30, y: 7), control1: CGPoint(x: 8, y: 10), control2: CGPoint(x: 20, y: 5)); path.addCurve(to: CGPoint(x: 66, y: 20), control1: CGPoint(x: 47, y: 10), control2: CGPoint(x: 62, y: 7)); path.addCurve(to: CGPoint(x: 39, y: 39), control1: CGPoint(x: 68, y: 35), control2: CGPoint(x: 51, y: 42)); path.addCurve(to: CGPoint(x: 6, y: 26), control1: CGPoint(x: 22, y: 35), control2: CGPoint(x: 11, y: 39))
            }
            context.stroke(path, with: .color(selected ? Colors.yellow : .white.opacity(0.55)), style: StrokeStyle(lineWidth: selected ? 3 : 2, lineCap: .round, lineJoin: .round))
        }
        .accessibilityHidden(true)
    }

    private func runGameAction() {
        if gameText.hasSuffix(" ms") { gameText = "Ready"; gameStart = nil; return }
        if gameText == "Ready" || gameText == "Too soon. Try again" {
            gameText = "Get ready…"
            gameTask?.cancel()
            gameTask = Task { @MainActor in
                try? await Task.sleep(for: .seconds(3))
                guard !Task.isCancelled, destination == .reflex, scenePhase == .active else { return }
                gameStart = ProcessInfo.processInfo.systemUptime; gameText = "GO"
            }
        } else if gameText == "GO", let gameStart {
            gameText = "\(Int((ProcessInfo.processInfo.systemUptime - gameStart) * 1000)) ms"
            self.gameStart = nil
        } else if gameText == "Get ready…" {
            gameTask?.cancel(); gameTask = nil
            gameText = "Too soon. Try again"
        }
    }

    private var gameButtonTitle: String {
        if gameText == "Ready" || gameText == "Too soon. Try again" { "Start test" }
        else if gameText.hasSuffix(" ms") { "Try again" }
        else if gameText == "GO" { "Tap now" }
        else { "Wait for GO" }
    }
}

// MARK: - Pieces

/// One composer star: hidden until its start light lands in the slot (or always shown once rated / in quiet mode).
private struct PitStopReviewStarSlot: View {
    let index: Int
    let shown: Bool
    let selected: Bool
    let scene: Double
    let s: CGFloat

    var body: some View {
        let ST = PitStopReviewTheme.Stars.self
        PitStopReviewStar(radius: ST.radius, box: ST.box)
            .fill(selected ? PitStopReviewTheme.Colors.yellow : .clear)
            .overlay { PitStopReviewStar(radius: ST.radius, box: ST.box).stroke(selected ? Color.black.opacity(0.35) : .white, style: StrokeStyle(lineWidth: 2, lineJoin: .round)) }
            .frame(width: ST.box, height: ST.box)
            .scaleEffect(s)
            .frame(width: ST.box * s, height: ST.box * s)
            .opacity(shown ? 1 : PitStopReviewMotion.starLanded(scene, index))
            .scaleEffect(shown ? 1 : PitStopReviewMotion.starScale(scene, index))
            .accessibilityHidden(true)
    }
}

/// The composer's star: an outline star in the 28 pt box, radius 11, inner ratio 0.46.
private struct PitStopReviewStar: Shape {
    let radius: Double
    let box: Double

    func path(in rect: CGRect) -> Path {
        var path = Path()
        for n in 0..<10 {
            let a = -Double.pi / 2 + Double(n) * Double.pi / 5
            let r = n % 2 == 0 ? radius : radius * 0.46
            let point = CGPoint(x: rect.minX + rect.width * (0.5 + cos(a) * r / box), y: rect.minY + rect.height * (0.5 + sin(a) * r / box))
            n == 0 ? path.move(to: point) : path.addLine(to: point)
        }
        path.closeSubpath()
        return path
    }
}

/// Badge sparkle: a full four-point star drawn in a unit box.
private struct PitStopReviewSparkle: Shape {
    func path(in rect: CGRect) -> Path {
        func p(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: rect.minX + rect.width * x, y: rect.minY + rect.height * y) }
        var path = Path()
        path.move(to: p(0.5, 0))
        path.addQuadCurve(to: p(1, 0.5), control: p(0.59, 0.41))
        path.addQuadCurve(to: p(0.5, 1), control: p(0.59, 0.59))
        path.addQuadCurve(to: p(0, 0.5), control: p(0.41, 0.59))
        path.addQuadCurve(to: p(0.5, 0), control: p(0.41, 0.41))
        path.closeSubpath()
        return path
    }
}

/// The CTA: the loop's scene scale times the finger's 0.97 press (90 ms in, 160 ms out).
private struct PitStopReviewCtaStyle: ButtonStyle {
    let sceneScale: Double
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(sceneScale * (configuration.isPressed ? PitStopReviewMotion.M.ctaPressed : 1))
            .animation(.easeInOut(duration: configuration.isPressed ? 0.09 : 0.16), value: configuration.isPressed)
    }
}

/// Draws every pitch frame, the shadow, the car layer's grade + blur and the smoke shader once on mount (2 pt, nearly
/// transparent), so their textures and pipelines exist before the first loop needs them.
private struct PitStopReviewWarmup: View {
    var body: some View {
        ZStack(alignment: .topLeading) {
            ZStack {
                ForEach(0..<PitStopReviewAssets.carFrames.count, id: \.self) { i in
                    Image(uiImage: PitStopReviewAssets.carFrames[i]).resizable().frame(width: 2, height: 2)
                }
                Image(uiImage: PitStopReviewAssets.carShadow).resizable().frame(width: 2, height: 2)
            }
            .frame(width: 2, height: 2)
            .layerEffect(ShaderLibrary.pitStopReviewCarGrade(.float2(2, 2), .float(2)), maxSampleOffset: CGSize(width: 6, height: 0))
            Rectangle().fill(.white).frame(width: 1, height: 1)
                .colorEffect(ShaderLibrary.pitStopReviewSmoke(.floatArray([1, 1, 4, 0.5])))
            Rectangle().fill(.white).frame(width: 1, height: 1)
                .colorEffect(ShaderLibrary.pitStopReviewWorld(.float4(0, 200, 0, 0), .float4(0, 0, 1, 0)))
            Image(uiImage: PitStopReviewTrail.image).resizable().frame(width: 2, height: 2)
        }
        .opacity(0.01)
        .frame(width: 2, height: 2)
        .accessibilityHidden(true)
    }
}

private struct PitStopReviewFeedPositionObserver: ViewModifier {
    var scale: Double
    var onChange: (PitStopReviewScrollSnapshot) -> Void

    @ViewBuilder func body(content: Content) -> some View {
        if #available(iOS 18.0, *) {
            content.onScrollGeometryChange(for: PitStopReviewScrollSnapshot.self) { geometry in
                // This edge-to-edge scroll view adds the key-window safe area to its content itself,
                // matching RN's `contentInsetAdjustmentBehavior="never"`. iOS 26 still reports a
                // system `contentInsets.top` here, so adding it would disable autoplay on mount.
                let offset = geometry.contentOffset.y
                return PitStopReviewScrollSnapshot(offset: -offset, atTop: offset <= 4)
            } action: { _, snapshot in
                onChange(snapshot)
            }
        } else {
            content.onPreferenceChange(PitStopReviewFeedTopKey.self) { minY in
                onChange(PitStopReviewScrollSnapshot(offset: minY, atTop: minY >= -4))
            }
        }
    }
}

private struct PitStopReviewShareTextActivity: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController { UIActivityViewController(activityItems: items, applicationActivities: nil) }
    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
