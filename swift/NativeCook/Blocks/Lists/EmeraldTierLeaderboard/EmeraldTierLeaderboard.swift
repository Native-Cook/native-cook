import SwiftUI

/// Emerald Tier Leaderboard — a league screen on glass. The player in fourth place pulls ahead of
/// the podium (Rank Recast): the crown lifts off the old leader, the four plinths trade places on
/// one shader pass, the numerals roll, and the crown lands on the new first place. A drag on the
/// standings scrubs the same journey, the crests browse the other leagues, and the tabs, the bell
/// and the arena replay it.
///
/// Port of the React Native block: `EmeraldTierLeaderboard.tsx` (this file), `motion.ts` /
/// `recastMotion.ts` (`...Motion`), `useEmeraldRankMotion` (`...RankModel`), `LeagueCrests.tsx`
/// (`...Leagues`), the glass and art materials (`...Glass`, `.metal`) and the arena
/// (`...Scene`, `...Surface`).
///
/// Touch: pull the arena up or down (rubber band at the ends, a flick commits, a slow release
/// picks the nearest rest); tap the arena, the bell, "Leagues" or "Tournaments" to replay; drag the
/// crests sideways to browse leagues (a browsed league returns to Emerald after 3.2 s).
/// Reduced motion: the final, legible state, no loop and no drag.
struct EmeraldTierLeaderboard: View {
    /// Plays the 10 s demo loop.
    var autoPlay = true
    /// Freezes the demo at this time (seconds), the RN `previewTime`.
    var previewTime: Double?
    /// Overrides the system Reduce Motion setting, the RN `previewReducedMotion`.
    var previewReducedMotion: Bool?

    static let loop = EmeraldTierMotion.loopSeconds
    static let LOOP = loop

    @Environment(\.accessibilityReduceMotion) private var systemReduced
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @Environment(\.colorSchemeContrast) private var contrast
    @Environment(\.scenePhase) private var scenePhase

    @State private var model: EmeraldTierRankModel
    @State private var leagues = EmeraldTierLeagueFocus()
    @State private var rankPan = EmeraldTierPan(axis: .vertical, activeOffset: 8, failOffset: 14)
    @State private var tab = "Leagues"
    @State private var nav = "Tournaments"
    /// Forces a redraw after an interaction while the timeline is paused.
    @State private var redraw = 0
    /// 0 none, 1 the fourth-place row, 2 the centre target (RectButton press feedback).
    @State private var pressed = 0

    init(autoPlay: Bool = true, previewTime: Double? = nil, previewReducedMotion: Bool? = nil) {
        self.autoPlay = autoPlay
        self.previewTime = previewTime
        self.previewReducedMotion = previewReducedMotion
        _model = State(initialValue: EmeraldTierRankModel(previewTime: previewTime))
    }

    private var reduced: Bool { previewReducedMotion ?? systemReduced }
    /// Both preferences call for an opaque, clearly separated material.
    private var solidMaterial: Bool { reduceTransparency || contrast == .increased }

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + insets.leading + insets.trailing,
                              height: geo.size.height + insets.top + insets.bottom)
            screen(size: size, insets: insets)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(EmeraldTierPalette.color("#080a09").ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            sync()
            model.applyPreviewTime()
            model.applyReduced()
            model.setFocused(true)
        }
        .onDisappear { model.setFocused(false) }
        .onChange(of: scenePhase) { _, phase in model.setForeground(phase == .active) }
        .onChange(of: reduced) { _, _ in sync(); model.applyReduced(); redraw += 1 }
        .onChange(of: previewTime) { _, _ in sync(); model.applyPreviewTime(); redraw += 1 }
    }

    /// Pushes the props into the reference models (the equivalent of the hooks' dependency arrays).
    private func sync() {
        model.autoPlay = autoPlay
        model.previewTime = previewTime
        model.reduced = reduced
        leagues.systemReduced = systemReduced
        leagues.enabled = previewTime == nil && tab == "Leagues"
    }

    private func screen(size: CGSize, insets: EdgeInsets) -> some View {
        let widthScale = Double(size.width) / 393
        let usableHeight = Double(size.height - insets.top - insets.bottom) - 78 * widthScale
        let scale = min(widthScale, max(0.65, usableHeight / 714))
        let solid = solidMaterial
        // The timeline stops only for a frozen preview or Reduce Motion; interactions bump `redraw`.
        let paused = previewTime != nil || systemReduced
        return TimelineView(.animation(paused: paused)) { timeline in
            let now = timeline.date.timeIntervalSinceReferenceDate
            let rank = frameStep(now: now, scale: scale)
            let focus = leagues.focus
            let recede = EmeraldTierLeagues.recede(focus)
            let _ = redraw
            ZStack(alignment: .topLeading) {
                LinearGradient(stops: [
                    .init(color: EmeraldTierPalette.color("#00c88e"), location: 0),
                    .init(color: EmeraldTierPalette.color("#008c67"), location: 0.23),
                    .init(color: EmeraldTierPalette.color("#064b37"), location: 0.40),
                    .init(color: EmeraldTierPalette.color("#071e14"), location: 0.58),
                    .init(color: EmeraldTierPalette.color("#080a09"), location: 0.82),
                ], startPoint: .top, endPoint: .bottom)
                EmeraldTierLeagueTint(focus: focus)

                canvas(rank: rank, focus: focus, recede: recede, solid: solid, scale: scale, size: size, insets: insets)
                    .frame(width: 393, height: 714, alignment: .topLeading)
                    .scaleEffect(scale, anchor: .topLeading)
                    .offset(x: (size.width - 393 * scale) / 2, y: insets.top + 8)

                bottomNav(widthScale: widthScale, size: size, insets: insets, solid: solid)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .clipped()
            .accessibilityElement(children: .contain)
        }
    }

    /// One frame of the clock: league mirrors first, then the rank model, then the league springs.
    private func frameStep(now: Double, scale: Double) -> EmeraldTierMotion.Frame {
        sync()
        model.dragWidth = max(120, 241.5 * scale)
        model.tick(now: now)
        leagues.demoTime = model.demoTime
        leagues.demoRunning = model.demoRunning
        leagues.tick(now: now)
        return model.frame
    }

    // MARK: 393 x 714 design canvas

    private func canvas(rank: EmeraldTierMotion.Frame, focus: Double, recede: (opacity: Double, scale: Double),
                        solid: Bool, scale: Double, size: CGSize, insets: EdgeInsets) -> some View {
        ZStack(alignment: .topLeading) {
            header(solid: solid)

            EmeraldTierCrestRow(focus: focus) { league, index in
                crest(league: league, index: index, rank: rank, focus: focus)
            }
            .contentShape(Rectangle())
            .gesture(leaguePanGesture(scale: scale))
            .offset(y: 57)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Leagues")
            .accessibilityValue(EmeraldTierLeagues.all[EmeraldTierLeagues.CURRENT_LEAGUE].name)
            .accessibilityHint("Swipe to browse other leagues")
            .accessibilityAdjustableAction { direction in
                let step = direction == .increment ? 1 : -1
                leagues.select(Int((focus + 0.5).rounded(.down)) + step)
                redraw += 1
            }
            .zIndex(3)

            EmeraldTierLeagueHeadline(focus: focus, solid: solid, challenges: tab != "Leagues")

            arena(rank: rank, recede: recede, solid: solid, scale: scale)
                .offset(y: 273)

            standings(solid: solid)
                .opacity(recede.opacity)
                .scaleEffect(recede.scale)
                .offset(x: 19, y: 614)
        }
    }

    @ViewBuilder
    private func crest(league: EmeraldTierLeague, index: Int, rank: EmeraldTierMotion.Frame, focus: Double) -> some View {
        if index == EmeraldTierLeagues.CURRENT_LEAGUE {
            EmeraldTierArtMaterial(image: EmeraldTierImages.crest, width: 100, height: 112,
                                   responsePhase: rank.responsePhase, response: rank.response)
        } else if league.state == .locked {
            EmeraldTierLockedCrest(index: index, tone: league.key == "diamond" ? .diamond : .sapphire, focus: focus)
        } else {
            EmeraldTierCrest(pale: league.key == "amber", letter: String(league.name.prefix(1)))
                .frame(width: 100, height: 112)
        }
    }

    // MARK: Header

    private func header(solid: Bool) -> some View {
        HStack(alignment: .top, spacing: 10) {
            ZStack {
                EmeraldTierGlass(kind: .green, radius: 25, solid: solid)
                HStack(spacing: 0) {
                    ForEach(["Leagues", "Challenges"], id: \.self) { label in
                        let selected = tab == label
                        Button {
                            tab = label
                            if label == "Leagues" { model.replay() }
                            sync(); redraw += 1
                        } label: {
                            ZStack {
                                if selected { EmeraldTierGlass(kind: .selected, radius: 22, solid: solid) }
                                Text(label)
                                    .font(.system(size: 12, weight: selected ? .bold : .regular))
                                    .foregroundStyle(selected ? Color.white : EmeraldTierPalette.color("#94bbac"))
                            }
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                        }
                        .buttonStyle(EmeraldTierRectButtonStyle(radius: 22))
                        .accessibilityLabel(label)
                        .accessibilityAddTraits(selected ? [.isSelected] : [])
                    }
                }
                .padding(4)
            }
            .frame(width: 304, height: 42)

            Button {
                model.replay(); redraw += 1
            } label: {
                ZStack {
                    Image(systemName: "bell.fill")
                        .font(.system(size: 27))
                        .foregroundStyle(EmeraldTierPalette.color("#77f38a"))
                    Circle().fill(EmeraldTierPalette.color("#ff8278")).frame(width: 8, height: 8)
                        .offset(x: 11, y: -14)
                }
                .frame(width: 44, height: 44)
            }
            .buttonStyle(EmeraldTierRectButtonStyle(radius: 0))
            .accessibilityLabel("Replay league update")
        }
        .frame(width: 357, height: 44, alignment: .topLeading)
        .offset(x: 20, y: 0)
    }

    // MARK: Arena

    private func arena(rank: EmeraldTierMotion.Frame, recede: (opacity: Double, scale: Double), solid: Bool, scale: Double) -> some View {
        ZStack(alignment: .topLeading) {
            EmeraldTierScene(rank: rank, solid: solid) { id, size in
                EmeraldTierAvatar(name: Self.avatarName(id), size: size)
            }
            // RectButton press feedback (underlay #BDFBE0 at 0.12) of the two replay targets.
            RoundedRectangle(cornerRadius: 13).fill(EmeraldTierPalette.color("#BDFBE0"))
                .opacity(pressed == 1 ? 0.12 : 0).frame(width: 355, height: 47).offset(x: 19, y: 288).allowsHitTesting(false)
            RoundedRectangle(cornerRadius: 5).fill(EmeraldTierPalette.color("#BDFBE0"))
                .opacity(pressed == 2 ? 0.12 : 0).frame(width: 123, height: 105).offset(x: 135, y: 169).allowsHitTesting(false)
        }
        .frame(width: 393, height: 335, alignment: .topLeading)
        .contentShape(Rectangle())
        .gesture(rankPanGesture(scale: scale))
        .opacity(recede.opacity)
        .scaleEffect(recede.scale)
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Rank Recast. Pull the fourth-place row upward to preview the new leader; pull downward to reverse.")
        .accessibilityAction(named: "Replay the fourth-place row becoming the leader") { model.replay(); redraw += 1 }
        .accessibilityAction(named: "Replay Rank Recast") { model.replay(); redraw += 1 }
    }

    private static func avatarName(_ id: EmeraldTierRecast.ActorID) -> String {
        switch id { case .ari: return "ari"; case .milo: return "milo"; case .juno: return "juno"; case .theo: return "theo" }
    }

    // MARK: Standings rows 05 and 06

    private func standings(solid: Bool) -> some View {
        VStack(spacing: 6) {
            ForEach([("05", "Noa Ellis", "noa", 730), ("06", "Kai Rowan", "kai", 710)], id: \.0) { row in
                HStack(spacing: 12) {
                    Text(row.0).font(.system(size: 12).monospacedDigit()).foregroundStyle(EmeraldTierPalette.color("#b5b9b5"))
                    EmeraldTierAvatar(name: row.2, size: 30)
                    Text(row.1).font(.system(size: 12, weight: .medium)).foregroundStyle(Color.white)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Text(String(row.3)).font(.system(size: 12).monospacedDigit()).foregroundStyle(Color.white)
                    Text("ϟ").font(.system(size: 20)).foregroundStyle(EmeraldTierPalette.color("#ffc846"))
                        .padding(.leading, -8)
                }
                .padding(.horizontal, 14)
                .frame(width: 355, height: 47)
                .background { EmeraldTierGlass(kind: .row, radius: 13, solid: solid) }
            }
        }
        .frame(width: 355, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    // MARK: Bottom navigation (outside the scaled canvas)

    private func bottomNav(widthScale: Double, size: CGSize, insets: EdgeInsets, solid: Bool) -> some View {
        let height = 52 * widthScale
        let items: [(String, String)] = [("Home", "house.fill"), ("Tournaments", "trophy.fill"), ("Profile", "person.fill")]
        return ZStack {
            EmeraldTierGlass(kind: .smoke, radius: 29, solid: solid)
            // border 1 (transparent) + padding 2
            HStack(spacing: 0) {
                ForEach(items, id: \.0) { item in
                    let selected = nav == item.0
                    let tint = selected ? Color.white : EmeraldTierPalette.color("#a7ada6")
                    Button {
                        nav = item.0
                        if item.0 == "Tournaments" { model.replay() }
                        redraw += 1
                    } label: {
                        ZStack {
                            if selected { EmeraldTierGlass(kind: .selected, radius: 25, solid: solid) }
                            VStack(spacing: 2) {
                                Image(systemName: item.1).font(.system(size: 20 * widthScale)).foregroundStyle(tint)
                                Text(item.0).font(.system(size: 10)).foregroundStyle(tint)
                            }
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                    .buttonStyle(EmeraldTierRectButtonStyle(radius: 25))
                    .accessibilityLabel(item.0)
                    .accessibilityAddTraits(selected ? [.isSelected] : [])
                }
            }
            .padding(3)
        }
        .frame(width: size.width - 38 * widthScale, height: height)
        .offset(x: 19 * widthScale, y: size.height - max(8, insets.bottom) - height)
    }

    // MARK: Gestures

    private func leaguePanGesture(scale: Double) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                guard leagues.enabled else { return }
                let t = CGSize(width: v.translation.width * scale, height: v.translation.height * scale)
                switch leagues.pan.changed(globalTranslation: t, location: v.location, localTranslation: v.translation, time: v.time) {
                case .began(let total):
                    leagues.panBegan(); leagues.panUpdated(translationX: total)
                case .updated(let total):
                    leagues.panUpdated(translationX: total)
                default: break
                }
                redraw += 1
            }
            .onEnded { v in
                guard leagues.enabled else { return }
                let t = CGSize(width: v.translation.width * scale, height: v.translation.height * scale)
                if case .ended(let velocity) = leagues.pan.ended(globalTranslation: t, time: v.time) { leagues.panEnded(velocityX: velocity) }
                redraw += 1
            }
    }

    private func rankPanGesture(scale: Double) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                // Gesture enabled(previewTime === undefined && !reduced)
                guard previewTime == nil && !reduced else { return }
                let t = CGSize(width: v.translation.width * scale, height: v.translation.height * scale)
                switch rankPan.changed(globalTranslation: t, location: v.location, localTranslation: v.translation, time: v.time) {
                case .began(let total):
                    pressed = 0
                    model.panStart(translationY: total); model.panUpdate(translationY: total)
                case .updated(let total):
                    model.panUpdate(translationY: total)
                case .none:
                    if rankPan.isPossible && pressed == 0 { pressed = Self.target(at: rankPan.downLocation) }
                    if rankPan.isFailed { pressed = 0 }
                default: break
                }
                redraw += 1
            }
            .onEnded { v in
                guard previewTime == nil && !reduced else { return }
                let t = CGSize(width: v.translation.width * scale, height: v.translation.height * scale)
                switch rankPan.ended(globalTranslation: t, time: v.time) {
                case .ended(let velocity): model.panEnd(velocityY: velocity)
                case .tap(let point): if Self.target(at: point) != 0 { model.replay() }
                default: model.panCancel()
                }
                pressed = 0
                redraw += 1
            }
    }

    /// The two RectButtons laid over the arena: the fourth-place row (19, 288, 355 x 47) and the centre target (135, 169, 123 x 105).
    private static func target(at point: CGPoint) -> Int {
        if CGRect(x: 19, y: 288, width: 355, height: 47).contains(point) { return 1 }
        if CGRect(x: 135, y: 169, width: 123, height: 105).contains(point) { return 2 }
        return 0
    }
}

/// RectButton feedback: an `underlayColor` #BDFBE0 fading in to 0.12 while pressed.
struct EmeraldTierRectButtonStyle: ButtonStyle {
    let radius: Double

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .overlay {
                RoundedRectangle(cornerRadius: radius)
                    .fill(EmeraldTierPalette.color("#BDFBE0"))
                    .opacity(configuration.isPressed ? 0.12 : 0)
                    .allowsHitTesting(false)
            }
            .contentShape(Rectangle())
    }
}
