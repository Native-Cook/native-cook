import SwiftUI
import UIKit

/// A shop's home page with a small ink-black search pill. Tapping it pulls the pill up to the top and
/// across the screen like a drop of ink, over a page that blurs and sinks back; recent searches pop
/// in under it. Typing filters a grid of pieces live, the tiles flowing to their new places as the
/// matches change, and Cancel pours the bar back into the pill.
struct LiquidSearchMorph: View {
    /// Opens the search, types "air", lets the results settle and closes again, over and over.
    var autoPlay = false
    /// The words autoPlay types.
    var script = "air"
    var onSearch: ((String) -> Void)? = nil

    init(autoPlay: Bool = false, script: String = "air", onSearch: ((String) -> Void)? = nil) {
        self.autoPlay = autoPlay
        self.script = script
        self.onSearch = onSearch
        _playing = State(initialValue: autoPlay)
    }

    fileprivate typealias M = LiquidSearchMorphMetrics

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var opened = false
    @State private var query = ""
    @State private var playing: Bool
    @FocusState private var focused: Bool
    // The morph runs on one value, 0 (pill) … 1 (search), at a constant rate; the pill gives each
    // edge its own eased share of it.
    @State private var morph = LiquidSearchMorphTween(0)
    @State private var pillPress = LiquidSearchMorphTween(0)
    @State private var cancelPress = LiquidSearchMorphTween(0)
    // The timeline only ticks while something moves.
    @State private var motionEnds = Date.distantPast
    @State private var live = false

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            TimelineView(.animation(minimumInterval: nil, paused: !live)) { timeline in
                stage(size: size, insets: insets, now: timeline.date)
            }
            .frame(width: size.width, height: size.height)
            .offset(x: -insets.leading, y: -insets.top)
        }
        .ignoresSafeArea(.keyboard)
        .task(id: playing) { await play() }
        .task(id: ResumeKey(playing: playing, opened: opened)) { await resume() }
        .task(id: motionEnds) {
            let wait = motionEnds.timeIntervalSinceNow
            if wait > 0 { try? await Task.sleep(for: .seconds(wait + 0.05)) }
            if !Task.isCancelled, motionEnds.timeIntervalSinceNow <= 0 { live = false }
        }
        .onChange(of: focused) { _, isFocused in
            if isFocused { takeOver() }
        }
    }

    private struct ResumeKey: Equatable {
        let playing: Bool
        let opened: Bool
    }

    // MARK: Layout

    private func boxes(size: CGSize, insets: EdgeInsets) -> (closed: CGRect, open: CGRect) {
        let closed = CGRect(x: M.side, y: insets.top + M.pillTop, width: M.pillWidth, height: M.pillHeight)
        // Never negative: the first layout pass can report a zero size.
        let open = CGRect(x: 16, y: insets.top + 8, width: max(0, size.width - 32 - M.cancelWidth), height: M.barHeight)
        return (closed, open)
    }

    private func stage(size: CGSize, insets: EdgeInsets, now: Date) -> some View {
        let (closed, open) = boxes(size: size, insets: insets)
        let t = morph.value(at: now)
        let press = pillPress.value(at: now)
        let still = reduceMotion
        let p = LiquidSearchMorphShape(t: t, closed: closed, open: open, still: still)
        let tileWidth = max(0, (size.width - M.side * 2 - M.gridGap) / 2)
        let canvasHeight = closed.maxY + 70
        let sinkK = clamp(t * 1.3)
        let sheetK = clamp((t - 0.5) / 0.5)
        let cancelK = clamp((t - 0.45) / 0.55)
        let veilK = clamp(t * 1.6)

        return ZStack(alignment: .topLeading) {
            // The home page.
            // The page blurs as the veil comes up, a light frost like RN's BlurView at intensity 34.
            LiquidSearchMorphHome(size: size, insets: insets, tileWidth: tileWidth)
                .scaleEffect(still ? 1 : 1 - M.homeSink * sinkK)
                .blur(radius: veilK > 0.001 ? M.frost * veilK : 0)
                .allowsHitTesting(!opened)

            // The veil the search opens over.
            M.paper.opacity(0.66)
                .frame(width: size.width, height: size.height)
                .opacity(veilK)
                .allowsHitTesting(false)

            // Under the bar: recent searches, or the results.
            if opened {
                LiquidSearchMorphSheet(
                    query: query,
                    tileWidth: tileWidth,
                    bottomInset: insets.bottom,
                    still: still,
                    pick: { word in
                        takeOver()
                        type(word)
                    }
                )
                .frame(width: size.width, height: max(0, size.height - (open.maxY + 10)), alignment: .top)
                .opacity(sheetK)
                .offset(y: open.maxY + 10 + (still ? 0 : (1 - sheetK) * 18))
            }

            // The ink pill, and what rides on it.
            LiquidSearchMorphPill(t: t, press: press, closed: closed, open: open, still: still, color: M.ink)
                .frame(width: size.width, height: canvasHeight)

            LiquidSearchMorphSymbol(name: "magnifyingglass", size: 18, weight: .semibold, color: M.cream)
                .frame(width: 20, height: 20)
                .opacity(still ? abs(1 - 2 * t) : 1)
                .offset(x: p.dropX - 10, y: p.dropY - 10)
                .allowsHitTesting(false)

            // The pill's own words ride its left edge and fade before it stretches.
            Text("Search \(LiquidSearchMorphCatalog.pieces.count) pieces")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(M.cream)
                .fixedSize()
                .frame(height: closed.height)
                .opacity(clamp(1 - t * 5) * (1 - press * 0.2))
                .offset(x: closed.minX + M.iconInset + 20 + (p.left - closed.minX), y: closed.minY + (p.top + p.bottom) / 2 - closed.midY)
                .allowsHitTesting(false)

            if !opened {
                Color.clear
                    .frame(width: closed.width, height: closed.height)
                    .contentShape(Rectangle())
                    .gesture(pillTouch(closed: closed))
                    .offset(x: closed.minX, y: closed.minY)
                    .accessibilityElement()
                    .accessibilityLabel("Search \(LiquidSearchMorphCatalog.pieces.count) pieces")
                    .accessibilityAddTraits(.isSearchField)
                    .accessibilityAction { takeOver(); openUp(byUser: true) }
            }

            if opened {
                field(now: now)
                    .frame(width: max(0, open.width - M.iconInset - 20 - 10), height: open.height)
                    .opacity(clamp((t - 0.72) / 0.28))
                    .offset(x: open.minX + M.iconInset + 20, y: open.minY)

                Text("Cancel")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(M.ink)
                    .frame(width: M.cancelWidth, height: open.height)
                    .contentShape(Rectangle())
                    .scaleEffect(1 - cancelPress.value(at: now) * 0.08)
                    .opacity(cancelK)
                    .offset(x: size.width - 16 - M.cancelWidth + (still ? 0 : (1 - cancelK) * 24), y: open.minY)
                    .gesture(tap { takeOver(); close(byUser: true) })
                    .accessibilityElement()
                    .accessibilityLabel("Cancel")
                    .accessibilityAddTraits(.isButton)
                    .accessibilityAction { takeOver(); close(byUser: true) }
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .background(M.paper)
        .clipped()
    }

    // MARK: The field

    private var typed: Binding<String> {
        Binding(get: { query }, set: { text in
            guard text != query else { return }
            takeOver()
            type(text)
        })
    }

    private func field(now: Date) -> some View {
        HStack(spacing: 0) {
            ZStack(alignment: .leading) {
                TextField("", text: typed, prompt: Text("Sneakers, scents, sizes").foregroundStyle(M.cream.opacity(0.45)))
                    .font(.system(size: 17, weight: .medium))
                    .foregroundStyle(M.cream)
                    .tint(M.cream)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .submitLabel(.search)
                    .focused($focused)
                    // The field sits on ink, so its keyboard is the dark one.
                    .environment(\.colorScheme, .dark)
                    .accessibilityLabel("Search the shop")
                // autoPlay types behind its own blinking caret.
                if playing && !focused {
                    HStack(spacing: 0) {
                        Text(query)
                            .font(.system(size: 17, weight: .medium))
                            .hidden()
                        LiquidSearchMorphCaret()
                            .padding(.leading, 1)
                    }
                    .allowsHitTesting(false)
                }
            }
            .frame(maxHeight: .infinity)
            if !query.isEmpty {
                LiquidSearchMorphSymbol(name: "xmark.circle.fill", size: 17, weight: .regular, color: M.cream.opacity(0.55))
                    .padding(.leading, 8)
                    .padding(10)
                    .contentShape(Rectangle())
                    .padding(-10)
                    .gesture(tap { takeOver(); type("") })
                    .accessibilityElement()
                    .accessibilityLabel("Clear the search")
                    .accessibilityAddTraits(.isButton)
                    .accessibilityAction { takeOver(); type("") }
            }
        }
    }

    // MARK: Touch

    // Taps are read with a zero-distance drag: it keeps working in views rebuilt every frame.
    private func tap(_ action: @escaping () -> Void) -> some Gesture {
        DragGesture(minimumDistance: 0).onEnded { value in
            if hypot(value.translation.width, value.translation.height) < 12 { action() }
        }
    }

    private func pillTouch(closed: CGRect) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { _ in
                if !pillDown {
                    pillDown = true
                    pillPress.retarget(1, duration: 0.09)
                    kick(pillPress.end)
                }
            }
            .onEnded { value in
                pillDown = false
                pillPress.retarget(0, duration: 0.2)
                kick(pillPress.end)
                let inside = CGRect(origin: .zero, size: closed.size).contains(value.location)
                if inside {
                    takeOver()
                    openUp(byUser: true)
                }
            }
    }

    @State private var pillDown = false
    // Set while the bar pours back into the pill, so a second Cancel or a late keyboard focus is ignored.
    @State private var closing = false

    // MARK: Actions

    private func kick(_ until: Date) {
        if until > motionEnds { motionEnds = until }
        live = true
    }

    private func tick() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    private func pulse(_ press: inout LiquidSearchMorphTween) {
        press.retarget(1, duration: 0.09, then: (0, 0.22))
        kick(press.end)
    }

    private func openUp(byUser: Bool) {
        guard !opened else { return }
        opened = true
        if byUser { tick() }
        morph.retarget(1, duration: M.openDuration, curve: .linear)
        kick(morph.end)
        // The keyboard comes up once the bar has landed, so it does not race the morph.
        if byUser {
            Task { @MainActor in
                try? await Task.sleep(for: .seconds(M.openDuration * 0.75))
                if opened && !closing { focused = true }
            }
        }
    }

    private func close(byUser: Bool) {
        guard opened, !closing else { return }
        closing = true
        if byUser { tick() }
        focused = false
        morph.retarget(0, duration: M.closeDuration, curve: .linear)
        kick(morph.end)
        // What the search showed goes once the pill is whole again.
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(M.closeDuration))
            closing = false
            opened = false
            query = ""
        }
    }

    private func type(_ text: String) {
        query = text
        onSearch?(text)
    }

    private func takeOver() {
        if playing { playing = false }
    }

    // MARK: autoPlay

    /// Every step is timed from the start of its loop, so the period never drifts.
    private func play() async {
        guard playing else { return }
        var times = [M.openAt - M.press, M.openAt]
        for i in 0..<script.count { times.append(M.typeAt + Double(i) * M.key) }
        times.append(contentsOf: [M.closeAt - M.press, M.closeAt])
        let clock = ContinuousClock()
        var origin = clock.now
        while !Task.isCancelled {
            for (index, at) in times.enumerated() {
                do { try await clock.sleep(until: origin + .seconds(at)) } catch { return }
                perform(index)
            }
            do { try await clock.sleep(until: origin + .seconds(M.loop)) } catch { return }
            origin += .seconds(M.loop)
        }
    }

    private func perform(_ step: Int) {
        let count = script.count
        if step == 0 { pulse(&pillPress) }
        else if step == 1 { openUp(byUser: false) }
        else if step < 2 + count { type(String(script.prefix(step - 1))) }
        else if step == 2 + count { pulse(&cancelPress) }
        else { close(byUser: false) }
    }

    /// A touch takes over; once the search is closed and left alone, the loop starts over.
    private func resume() async {
        guard autoPlay, !playing, !opened else { return }
        do { try await Task.sleep(for: .seconds(M.resumeAfter)) } catch { return }
        playing = true
    }

    private func clamp(_ v: Double) -> Double { min(1, max(0, v)) }
}

// MARK: - Metrics

enum LiquidSearchMorphMetrics {
    static let paper = Color(hex: 0xf3f0ea)
    static let ink = Color(hex: 0x17150f)
    static let muted = Color(hex: 0x8b8579)
    static let cream = Color(hex: 0xf6f1e7)
    static let card = Color(hex: 0xffffff)
    static let sand = Color(hex: 0xe5dfd4)

    // Layout, in pt.
    static let side = 20.0
    static let titleTop = 58.0
    static let pillTop = 156.0
    static let pillWidth = 214.0
    static let pillHeight = 46.0
    static let barHeight = 50.0
    static let cancelWidth = 74.0
    static let heroTop = 222.0
    static let heroHeight = 238.0
    static let gridGap = 12.0
    static let rowGap = 20.0
    static let tilePhoto = 196.0
    /// One result tile: photo, its 8 pt margin, the name and the kind/price row (2 pt gaps).
    static let tileHeight = 250.0
    static let iconInset = LiquidSearchMorphInk.iconInset

    // Opening takes openDuration, closing closeDuration (s).
    static let openDuration = 0.76
    static let closeDuration = 0.64
    // The home page shrinks by homeSink while the search is open.
    static let homeSink = 0.06
    /// How far the home page is blurred under the open search (pt).
    static let frost = 7.0
    // Recent searches pop in one after another, chipStagger s apart, once the bar has landed.
    static let chipStagger = 0.045
    static let tileStagger = 0.04
    static let pop = LiquidSearchMorphSpring.animation(duration: 0.42, dampingRatio: 0.62)
    static let flow = LiquidSearchMorphSpring.animation(duration: 0.52, dampingRatio: 0.82)

    // autoPlay (s): open, type, read the results, close, rest.
    static let loop = 8.6
    static let openAt = 0.8
    static let typeAt = 1.95
    static let key = 0.36
    static let closeAt = 5.9
    static let press = 0.16
    // After a touch, autoPlay starts over once the search has been closed and empty this long.
    static let resumeAfter = 4.0
}

// MARK: - Home page

private struct LiquidSearchMorphHome: View {
    let size: CGSize
    let insets: EdgeInsets
    let tileWidth: Double

    private typealias M = LiquidSearchMorphMetrics

    var body: some View {
        let hero = LiquidSearchMorphCatalog.pieces[4]
        let trendingTop = insets.top + M.heroTop + M.heroHeight + 60
        ZStack(alignment: .topLeading) {
            Color.clear

            HStack {
                Text("ATELIER NORD")
                    .font(.system(size: 13, weight: .heavy))
                    .tracking(3.2)
                    .foregroundStyle(M.ink)
                Spacer()
                ZStack(alignment: .topTrailing) {
                    Circle()
                        .fill(M.card)
                        .shadow(color: M.ink.opacity(0.08), radius: 4, y: 2)
                        .frame(width: 40, height: 40)
                        .overlay {
                            LiquidSearchMorphSymbol(name: "bag", size: 18, weight: .medium, color: M.ink)
                        }
                    Text("2")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundStyle(M.cream)
                        .frame(minWidth: 15, minHeight: 15)
                        .background(Circle().fill(M.ink))
                        .offset(x: -4, y: 4)
                }
            }
            .frame(width: max(0, size.width - M.side * 2), height: 40)
            .offset(x: M.side, y: insets.top + 12)

            // Two lines 40 pt apart, as the RN title's line height sets them.
            VStack(alignment: .leading, spacing: 0) {
                ForEach(["Walk soft,", "smell of rain."], id: \.self) { line in
                    Text(line)
                        .font(.system(size: 36, weight: .bold))
                        .tracking(-1.2)
                        .foregroundStyle(M.ink)
                        .frame(height: 40)
                }
            }
            .offset(x: M.side, y: insets.top + M.titleTop)

            ZStack(alignment: .bottomLeading) {
                LiquidSearchMorphPhoto(id: hero.photo, width: 900)
                LinearGradient(colors: [.black.opacity(0), .black.opacity(0.5)], startPoint: .top, endPoint: .bottom)
                    .frame(height: M.heroHeight * 0.55)
                VStack(alignment: .leading, spacing: 3) {
                    Text("NEW THIS WEEK")
                        .font(.system(size: 10.5, weight: .bold))
                        .tracking(1.8)
                        .foregroundStyle(.white.opacity(0.8))
                    HStack(alignment: .firstTextBaseline) {
                        Text(hero.name)
                            .font(.system(size: 22, weight: .bold))
                            .tracking(-0.4)
                        Spacer()
                        Text(hero.price)
                            .font(.system(size: 17, weight: .semibold))
                            .monospacedDigit()
                    }
                    .foregroundStyle(.white)
                }
                .padding(.horizontal, 18)
                .padding(.bottom, 16)
            }
            .frame(width: max(0, size.width - M.side * 2), height: M.heroHeight)
            .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
            .offset(x: M.side, y: insets.top + M.heroTop)

            HStack(alignment: .firstTextBaseline) {
                Text("Trending now")
                    .font(.system(size: 19, weight: .bold))
                    .tracking(-0.3)
                    .foregroundStyle(M.ink)
                Spacer()
                Text("See all")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(M.muted)
            }
            .frame(width: max(0, size.width - M.side * 2))
            .offset(x: M.side, y: insets.top + M.heroTop + M.heroHeight + 24)

            HStack(alignment: .top, spacing: M.gridGap) {
                ForEach([LiquidSearchMorphCatalog.pieces[0], LiquidSearchMorphCatalog.pieces[8]]) { piece in
                    VStack(alignment: .leading, spacing: 0) {
                        LiquidSearchMorphPhoto(id: piece.photo, width: 500)
                            .frame(width: tileWidth, height: 172)
                            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                        Text(piece.name)
                            .font(.system(size: 15, weight: .semibold))
                            .tracking(-0.2)
                            .foregroundStyle(M.ink)
                            .lineLimit(1)
                            .padding(.top, 8)
                        Text(piece.price)
                            .font(.system(size: 13))
                            .foregroundStyle(M.muted)
                    }
                    .frame(width: tileWidth, alignment: .leading)
                }
            }
            .frame(height: max(0, size.height - trendingTop), alignment: .top)
            .clipped()
            .offset(x: M.side, y: trendingTop)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }
}

// MARK: - Sheet

/// What sits under the bar: recent searches and the popular list, or the results.
private struct LiquidSearchMorphSheet: View {
    let query: String
    let tileWidth: Double
    let bottomInset: Double
    let still: Bool
    let pick: (String) -> Void

    @Environment(\.displayScale) private var displayScale

    private typealias M = LiquidSearchMorphMetrics

    var body: some View {
        ZStack(alignment: .top) {
            if query.isEmpty {
                suggestions
                    .transition(.asymmetric(insertion: .identity, removal: .opacity.animation(.easeInOut(duration: 0.15))))
            } else {
                LiquidSearchMorphResults(query: query, tileWidth: tileWidth, bottomInset: bottomInset, still: still)
                    .transition(.identity)
            }
        }
    }

    private var suggestions: some View {
        VStack(alignment: .leading, spacing: 0) {
            label("RECENT")
                .padding(.bottom, 12)
            LiquidSearchMorphFlow(spacing: 8) {
                ForEach(Array(LiquidSearchMorphCatalog.recent.enumerated()), id: \.element) { index, word in
                    Button { pick(word) } label: {
                        HStack(spacing: 6) {
                            LiquidSearchMorphSymbol(name: "clock.arrow.circlepath", size: 13, weight: .regular, color: M.muted)
                            Text(word)
                                .font(.system(size: 15, weight: .medium))
                                .foregroundStyle(M.ink)
                        }
                        .padding(.horizontal, 14)
                        .frame(height: 36)
                    }
                    .buttonStyle(LiquidSearchMorphChipStyle())
                    .modifier(LiquidSearchMorphPop(delay: 0.22 + Double(index) * M.chipStagger, still: still))
                }
            }
            label("POPULAR")
                .padding(.top, 30)
                .padding(.bottom, 4)
            ForEach(Array(LiquidSearchMorphCatalog.popular.enumerated()), id: \.element) { index, name in
                Button { pick(String(name.split(separator: " ")[0])) } label: {
                    HStack(spacing: 14) {
                        Text(String(format: "%02d", index + 1))
                            .font(.system(size: 13, weight: .semibold))
                            .monospacedDigit()
                            .foregroundStyle(M.muted)
                            .frame(width: 22, alignment: .leading)
                        Text(name)
                            .font(.system(size: 17, weight: .medium))
                            .foregroundStyle(M.ink)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        LiquidSearchMorphSymbol(name: "arrow.up.left", size: 13, weight: .regular, color: M.muted)
                    }
                    .frame(height: 52)
                    .overlay(alignment: .bottom) {
                        Rectangle().fill(M.ink.opacity(0.12)).frame(height: 1 / displayScale)
                    }
                }
                .buttonStyle(LiquidSearchMorphRowStyle())
                .modifier(LiquidSearchMorphPop(delay: 0.42 + Double(index) * M.chipStagger, still: still))
            }
        }
        .padding(.horizontal, M.side)
        .padding(.top, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func label(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 11, weight: .bold))
            .tracking(1.6)
            .foregroundStyle(M.muted)
    }
}

/// The results: a count line and a two-column grid whose tiles pop in, fade out when they stop
/// matching and spring to their new places.
private struct LiquidSearchMorphResults: View {
    let query: String
    let tileWidth: Double
    let bottomInset: Double
    let still: Bool

    private typealias M = LiquidSearchMorphMetrics

    var body: some View {
        let results = LiquidSearchMorphCatalog.search(query)
        let rows = (results.count + 1) / 2
        let gridHeight = rows == 0 ? 0 : Double(rows) * (M.tileHeight + M.rowGap) - M.rowGap
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 0) {
                Text(results.isEmpty ? "Nothing for “\(query)”" : "\(results.count) \(results.count == 1 ? "piece" : "pieces") for “\(query)”")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(M.muted)
                    .padding(.top, 12)
                    .padding(.bottom, 14)
                if results.isEmpty {
                    Text("Try a shape, a scent or a colour.")
                        .font(.system(size: 17, weight: .medium))
                        .foregroundStyle(M.ink)
                        .transition(.opacity.animation(.easeInOut(duration: 0.2)))
                }
                ZStack(alignment: .topLeading) {
                    ForEach(Array(results.enumerated()), id: \.element.id) { index, piece in
                        LiquidSearchMorphTile(piece: piece, width: tileWidth)
                            .modifier(LiquidSearchMorphPop(delay: Double(min(index, 6)) * M.tileStagger, still: still))
                            .offset(x: Double(index % 2) * (tileWidth + M.gridGap), y: Double(index / 2) * (M.tileHeight + M.rowGap))
                            .animation(still ? nil : M.flow, value: index)
                            .transition(.opacity.animation(.easeInOut(duration: 0.16)))
                    }
                }
                .frame(width: tileWidth * 2 + M.gridGap, height: gridHeight, alignment: .topLeading)
            }
            .padding(.horizontal, M.side)
            .padding(.bottom, bottomInset + 40)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .scrollDismissesKeyboard(.immediately)
    }
}

private struct LiquidSearchMorphTile: View {
    let piece: LiquidSearchMorphPiece
    let width: Double

    private typealias M = LiquidSearchMorphMetrics

    var body: some View {
        Button {} label: {
            VStack(alignment: .leading, spacing: 2) {
                LiquidSearchMorphPhoto(id: piece.photo, width: 500, fade: 0.16)
                    .frame(width: width, height: M.tilePhoto)
                    .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                    .padding(.bottom, 8)
                Text(piece.name)
                    .font(.system(size: 15, weight: .semibold))
                    .tracking(-0.2)
                    .foregroundStyle(M.ink)
                    .lineLimit(1)
                    .padding(.top, 8)
                HStack(spacing: 8) {
                    Text(piece.kind)
                        .font(.system(size: 13))
                        .foregroundStyle(M.muted)
                        .lineLimit(1)
                    Spacer(minLength: 0)
                    Text(piece.price)
                        .font(.system(size: 13, weight: .semibold))
                        .monospacedDigit()
                        .foregroundStyle(M.ink)
                }
            }
            .frame(width: width, alignment: .leading)
        }
        .buttonStyle(LiquidSearchMorphTileStyle())
        .accessibilityLabel("\(piece.name), \(piece.price)")
    }
}

// MARK: - Pieces

/// Pops a chip, a row or a tile in: from 0.84 scale and 10 pt lower on a spring, fading up over
/// 200 ms, after `delay`. Under reduced motion it only fades in.
private struct LiquidSearchMorphPop: ViewModifier {
    let delay: Double
    let still: Bool
    @State private var shown = false

    private typealias M = LiquidSearchMorphMetrics

    func body(content: Content) -> some View {
        content
            .scaleEffect(still || shown ? 1 : 0.84)
            .offset(y: still || shown ? 0 : 10)
            .animation(still ? nil : M.pop.delay(delay), value: shown)
            .opacity(shown ? 1 : 0)
            .animation(still ? .easeInOut(duration: 0.3) : .timingCurve(0.455, 0.03, 0.515, 0.955, duration: 0.2).delay(delay), value: shown)
            .onAppear { shown = true }
    }
}

/// The caret autoPlay types behind: on for 45% of a second, then off.
private struct LiquidSearchMorphCaret: View {
    var body: some View {
        TimelineView(.animation) { timeline in
            let phase = timeline.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 1)
            let opacity = phase < 0.45 ? 1 : phase < 0.55 ? 1 - (phase - 0.45) / 0.1 : 0
            RoundedRectangle(cornerRadius: 1)
                .fill(LiquidSearchMorphMetrics.cream)
                .frame(width: 2, height: 21)
                .opacity(opacity)
        }
    }
}

/// Lays chips out in rows, wrapping to the next row when one is full.
private struct LiquidSearchMorphFlow: Layout {
    let spacing: Double

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? .infinity
        let frames = place(width: width, subviews: subviews)
        let height = frames.map(\.maxY).max() ?? 0
        return CGSize(width: proposal.width ?? (frames.map(\.maxX).max() ?? 0), height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let frames = place(width: bounds.width, subviews: subviews)
        for (frame, subview) in zip(frames, subviews) {
            subview.place(at: CGPoint(x: bounds.minX + frame.minX, y: bounds.minY + frame.minY), proposal: ProposedViewSize(frame.size))
        }
    }

    private func place(width: Double, subviews: Subviews) -> [CGRect] {
        var frames: [CGRect] = []
        var x = 0.0
        var y = 0.0
        var row = 0.0
        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x > 0, x + size.width > width {
                x = 0
                y += row + spacing
                row = 0
            }
            frames.append(CGRect(origin: CGPoint(x: x, y: y), size: size))
            x += size.width + spacing
            row = max(row, size.height)
        }
        return frames
    }
}

private struct LiquidSearchMorphChipStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(
                Capsule().fill(configuration.isPressed ? Color(hex: 0xebe6dc) : LiquidSearchMorphMetrics.card)
                    .shadow(color: LiquidSearchMorphMetrics.ink.opacity(0.08), radius: 1.5, y: 1)
            )
    }
}

private struct LiquidSearchMorphRowStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(LiquidSearchMorphMetrics.ink.opacity(configuration.isPressed ? 0.04 : 0))
            .contentShape(Rectangle())
    }
}

private struct LiquidSearchMorphTileStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
    }
}

/// An SF Symbol fitted into a square of `size` pt, as RN's SymbolView draws it.
private struct LiquidSearchMorphSymbol: View {
    let name: String
    let size: Double
    var weight: Font.Weight = .regular
    let color: Color

    var body: some View {
        Image(systemName: name)
            .resizable()
            .fontWeight(weight)
            .scaledToFit()
            .foregroundStyle(color)
            .frame(width: size, height: size)
    }
}
