import CoreText
import SwiftUI
import UIKit

// A podcast list whose page title is a menu. Tapping the big condensed title springs a small
// popover out of it; picking the other list morphs the title letter by letter: each old letter
// sinks, shrinks and blurs away while the new word's letter drops in from above, sharpening as it
// lands, one slot after another from the left. The title's width follows, the chevron with it,
// and the list underneath cross-fades.
//
// autoPlay timeline (MorphingTitleMenuMotion.loop = 6 s):
//   0.00        rest on "Queue", popover closed (also the first frame on screen)
//   0.60        the title is tapped: the popover springs open from under it
//               (response 0.42 s, damping 0.64; scale 0.45 -> 1 from its top-left corner, blur 10 -> 0)
//   1.45        "Favorites" is tapped: the highlight pill and its check slide down (spring 0.32 / 0.78)
//   1.68        the popover shrinks back into the title (ease-in, 0.2 s)
//   1.80        the title morphs "Queue" -> "Faves" (0.63 s, see MorphingTitleMenuMorph);
//               the list cross-fades (0.35 s, ease-out)
//   2.43-3.60   rest on "Faves"
//   3.60        open; 4.45 pick "Queue"; 4.68 close; 4.80 the title morphs back
//   5.43-6.00   rest on "Queue"

enum MorphingTitleMenuMotion {
    static let loop = 6.0
    /// The second half of the loop does the same, back to the first page.
    static let half = 3.0
    static let openAt = 0.6
    static let pickAt = 1.45
    /// From a pick to the popover closing, and from closing to the title morph.
    static let closeAfterPick = 0.23
    static let commitAfterClose = 0.12
    /// Wait after the last touch before the loop takes over again.
    static let userHold = 2.0

    static let open = Animation.spring(response: 0.42, dampingFraction: 0.64)
    static let close = Animation.easeIn(duration: 0.2)
    static let highlight = Animation.spring(response: 0.32, dampingFraction: 0.78)
    static let content = Animation.easeOut(duration: 0.35)
}

/// The per-letter morph of the title, as functions of the time since it started.
enum MorphingTitleMenuMorph {
    /// Delay between one letter slot and the next, left to right.
    static let stagger = 0.06
    /// An old letter sinks out over this long.
    static let outDuration = 0.28
    /// A new letter starts this long after the old letter of its slot, and lands over `inDuration`.
    static let inDelay = 0.05
    static let inDuration = 0.34
    /// How far a letter travels, how small it gets and how much it blurs, in units of the font size.
    static let drop = 0.48
    static let minScale = 0.55
    static let blur = 0.09
    /// Under reduced motion the words only cross-fade, over this long.
    static let fade = 0.25

    static func duration(slots: Int) -> Double {
        Double(max(slots, 1) - 1) * stagger + max(outDuration, inDelay + inDuration)
    }

    struct Pose {
        var dy: Double, scale: Double, blur: Double, opacity: Double
    }

    /// The old letter of a slot, `p` from 0 (in place) to 1 (gone).
    static func out(_ p: Double, size: Double) -> Pose {
        let e = p * p
        return Pose(dy: drop * size * e, scale: 1 - (1 - minScale) * e, blur: blur * size * p, opacity: 1 - p)
    }

    /// The new letter of a slot, `q` from 0 (above, small, blurred) to 1 (landed).
    static func `in`(_ q: Double, size: Double) -> Pose {
        let e = 1 - pow(1 - q, 3)
        let soft = 1 - (1 - q) * (1 - q)
        return Pose(dy: -drop * size * (1 - e), scale: minScale + (1 - minScale) * e, blur: blur * size * (1 - soft), opacity: min(1, q * 2))
    }

    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }

    static func easeInOutCubic(_ t: Double) -> Double {
        t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
    }
}

enum MorphingTitleMenuMetrics {
    static let side: CGFloat = 30
    /// From the safe area's top to the top of the title's box.
    static let headerTop: CGFloat = 40
    static let titleSize: CGFloat = 46
    /// The title's box; its baseline sits `baseline` from the top.
    static let titleBox: CGFloat = 54
    static let baseline: CGFloat = 47
    static let chevronGap: CGFloat = 7
    static let chevronSize: CGFloat = 22
    /// Room around the title's canvas for letters that fall, rise and blur.
    static let canvasPad: CGFloat = 30
    static let subtitleTop: CGFloat = 8
    static let listTop: CGFloat = 44
    static let episodeGap: CGFloat = 34
    static let menuWidth: CGFloat = 164
    static let menuInset: CGFloat = 7
    static let menuRow: CGFloat = 44
    static let menuRadius: CGFloat = 22
    static let menuX: CGFloat = side - 12
    static let menuGap: CGFloat = 6
}

enum MorphingTitleMenuPalette {
    static let background = Color(hex: 0xF8F6F8)
    static let ink = Color(hex: 0x0E0E10)
    static let ink2 = Color(hex: 0x2C2C2E)
    static let mute = Color(hex: 0x9C9AA0)
    static let faint = Color(hex: 0xB6B4B9)
    static let chip = Color(hex: 0xECEAED)
    static let chevron = Color(hex: 0xA6A4A9)
    static let track = Color(hex: 0xE6E4E7)
    static let fill = Color(hex: 0x5E5D62)
    static let card = Color.white
    static let pill = Color(hex: 0xF0EEF1)
    static let pillOutline = Color(hex: 0xE4E2E5)
}

struct MorphingTitleMenuEpisode {
    var show: String
    var when: String
    var title: String
    /// What the play button says after its verb: time left or the whole length.
    var length: String
    /// How far it has been listened to, when it has been started.
    var progress: Double? = nil
    var playing = false
    var art: [Color]
    var mark: String
}

struct MorphingTitleMenuPage {
    /// The word in the title.
    var title: String
    /// Its row in the popover.
    var label: String
    var subtitle: String
    var episodes: [MorphingTitleMenuEpisode]

    static let pixelHours = [Color(hex: 0x1F2A44), Color(hex: 0x3B5BDB)]
    static let studioNotes = [Color(hex: 0xF59F00), Color(hex: 0xF76707)]
    static let longBuild = [Color(hex: 0x2B8A3E), Color(hex: 0x94D82D)]
    static let fieldwork = [Color(hex: 0xC2255C), Color(hex: 0xF783AC)]

    static let samples = [
        MorphingTitleMenuPage(title: "Queue", label: "Queue", subtitle: "Fri 4 Apr", episodes: [
            MorphingTitleMenuEpisode(show: "Pixel Hours", when: "2d ago", title: "Designing on expert mode", length: "5m", progress: 0.42, playing: true, art: pixelHours, mark: "P"),
            MorphingTitleMenuEpisode(show: "Studio Notes", when: "4d ago", title: "Why every tool ends up a canvas", length: "48m", art: studioNotes, mark: "S"),
            MorphingTitleMenuEpisode(show: "The Long Build", when: "1w ago", title: "Shipping the boring parts first", length: "1h 12m", art: longBuild, mark: "L"),
        ]),
        MorphingTitleMenuPage(title: "Faves", label: "Favorites", subtitle: "2 Episodes", episodes: [
            MorphingTitleMenuEpisode(show: "Fieldwork FM", when: "Jul 11, 2024", title: "Building product at Lumen: craft, metrics and taste", length: "2h 35m", art: fieldwork, mark: "F"),
            MorphingTitleMenuEpisode(show: "Studio Notes", when: "Mar 3, 2025", title: "Small teams, sharp tools", length: "54m", art: studioNotes, mark: "S"),
        ]),
    ]
}

/// The bundled display face and the glyphs of each title word, laid out once.
enum MorphingTitleMenuType {
    static let registered: Void = {
        if let url = Bundle.main.url(forResource: "MorphingTitleMenu-Anton-Regular", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    static func title(_ size: CGFloat) -> UIFont {
        _ = registered
        return UIFont(name: "Anton-Regular", size: size) ?? .systemFont(ofSize: size, weight: .black)
    }

    struct Word {
        /// Each letter's outline with its baseline origin at 0, and where that origin sits.
        var paths: [Path]
        var xs: [CGFloat]
        var advances: [CGFloat]
        var width: CGFloat
        var capHeight: CGFloat
    }

    private static var cache: [String: Word] = [:]

    static func word(_ text: String, size: CGFloat) -> Word {
        let key = "\(size)|\(text)"
        if let hit = cache[key] { return hit }
        let font = title(size)
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: [.font: font]))
        var paths: [Path] = []
        var xs: [CGFloat] = []
        for run in CTLineGetGlyphRuns(line) as? [CTRun] ?? [] {
            let count = CTRunGetGlyphCount(run)
            var glyphs = [CGGlyph](repeating: 0, count: count)
            var positions = [CGPoint](repeating: .zero, count: count)
            CTRunGetGlyphs(run, CFRange(location: 0, length: 0), &glyphs)
            CTRunGetPositions(run, CFRange(location: 0, length: 0), &positions)
            let attributes = CTRunGetAttributes(run) as NSDictionary
            let runFont = attributes[kCTFontAttributeName] as! CTFont
            for i in 0..<count {
                var flip = CGAffineTransform(scaleX: 1, y: -1)
                paths.append(CTFontCreatePathForGlyph(runFont, glyphs[i], &flip).map { Path($0) } ?? Path())
                xs.append(positions[i].x)
            }
        }
        let width = CGFloat(CTLineGetTypographicBounds(line, nil, nil, nil))
        let advances = xs.indices.map { i in (i + 1 < xs.count ? xs[i + 1] : width) - xs[i] }
        let word = Word(paths: paths, xs: xs, advances: advances, width: width, capHeight: font.capHeight)
        cache[key] = word
        return word
    }
}

struct MorphingTitleMenu: View {
    var pages = MorphingTitleMenuPage.samples
    /// Opens the popover and switches lists by itself, every `MorphingTitleMenuMotion.loop` seconds.
    var autoPlay = false
    var searchLabel = "Search"
    /// Called with the page's index when a list is picked.
    var onSelect: ((Int) -> Void)? = nil

    private typealias M = MorphingTitleMenuMotion
    private typealias K = MorphingTitleMenuMetrics
    private typealias P = MorphingTitleMenuPalette

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var selected = 0
    @State private var menuOpen = false
    /// 0 closed, 1 open; animated with a spring on the way out and an ease-in on the way back.
    @State private var menu = 0.0
    @State private var highlight = 0
    @State private var morph: Morph? = nil
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    @State private var pickTask: Task<Void, Never>? = nil
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    private struct Morph: Equatable {
        let id = UUID()
        let from: Int
        let to: Int
        let start: Date
    }

    var body: some View {
        GeometryReader { geo in
            let safe = geo.safeAreaInsets
            let full = CGSize(width: geo.size.width + safe.leading + safe.trailing, height: geo.size.height + safe.top + safe.bottom)
            let headerY = safe.top + K.headerTop
            ZStack(alignment: .topLeading) {
                P.background

                lists
                    .padding(.top, headerY + K.titleBox + K.subtitleTop)
                    .padding(.horizontal, K.side)
                    .frame(width: full.width, alignment: .leading)

                header
                    .padding(.top, headerY)
                    .padding(.horizontal, K.side)
                    .frame(width: full.width, alignment: .leading)

                if menuOpen {
                    Color.black.opacity(0.001)
                        .frame(width: full.width, height: full.height)
                        .onTapGesture {
                            touched()
                            closeMenu()
                        }
                }

                popover
                    .offset(x: K.menuX, y: headerY + K.titleBox + K.menuGap)

                VStack {
                    Spacer(minLength: 0)
                    miniPlayer
                        .padding(.horizontal, 16)
                        .padding(.bottom, safe.bottom + 8)
                }
                .frame(width: full.width, height: full.height)
                .allowsHitTesting(false)
            }
            .frame(width: full.width, height: full.height, alignment: .topLeading)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .onAppear { haptic.prepare() }
        .onDisappear { pickTask?.cancel() }
        .task(id: LoopKey(on: autoPlay && !reduceMotion, touches: touches)) { await loop() }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }

    // MARK: Header

    private var header: some View {
        HStack(alignment: .top, spacing: 12) {
            TimelineView(.animation(paused: morph == nil)) { context in
                title(at: context.date)
            }
            Spacer(minLength: 0)
            searchPill
                .padding(.top, K.baseline - MorphingTitleMenuType.word("Q", size: K.titleSize).capHeight / 2 - 23)
        }
    }

    /// The title's letters, the chevron after them, and the box that takes the tap.
    private func title(at now: Date) -> some View {
        let size = K.titleSize
        let width = titleWidth(at: now)
        let cap = MorphingTitleMenuType.word(pages[selected].title, size: size).capHeight
        return Color.clear
            .frame(width: width + K.chevronGap + K.chevronSize, height: K.titleBox)
            .overlay(alignment: .topLeading) {
                Canvas { context, _ in drawTitle(in: &context, at: now) }
                    .frame(width: 360, height: K.titleBox + 2 * K.canvasPad)
                    .offset(x: -K.canvasPad, y: -K.canvasPad)
                    .allowsHitTesting(false)
            }
            .overlay(alignment: .topLeading) {
                MorphingTitleMenuIcon(kind: .chevrons, color: P.chevron)
                    .frame(width: K.chevronSize, height: K.chevronSize)
                    .offset(x: width + K.chevronGap, y: K.baseline - cap / 2 - K.chevronSize / 2)
            }
            .contentShape(Rectangle())
            .onTapGesture {
                touched()
                if menuOpen { closeMenu() } else {
                    haptic.impactOccurred()
                    openMenu()
                }
            }
            .accessibilityElement()
            .accessibilityLabel(pages[selected].title)
            .accessibilityHint("Shows the lists")
            .accessibilityAddTraits(.isButton)
    }

    private func titleWidth(at now: Date) -> CGFloat {
        let size = K.titleSize
        guard let m = morph else { return MorphingTitleMenuType.word(pages[selected].title, size: size).width }
        let a = MorphingTitleMenuType.word(pages[m.from].title, size: size)
        let b = MorphingTitleMenuType.word(pages[m.to].title, size: size)
        let e = now.timeIntervalSince(m.start)
        let t: Double
        if reduceMotion {
            t = MorphingTitleMenuMorph.clamp(e / MorphingTitleMenuMorph.fade)
        } else {
            let total = MorphingTitleMenuMorph.duration(slots: max(a.paths.count, b.paths.count))
            t = MorphingTitleMenuMorph.easeInOutCubic(MorphingTitleMenuMorph.clamp((e - 0.08) / (total - 0.08)))
        }
        return a.width + (b.width - a.width) * t
    }

    private func drawTitle(in context: inout GraphicsContext, at now: Date) {
        typealias T = MorphingTitleMenuMorph
        let size = K.titleSize
        let origin = CGPoint(x: K.canvasPad, y: K.canvasPad + K.baseline)
        guard let m = morph else {
            let word = MorphingTitleMenuType.word(pages[selected].title, size: size)
            for i in word.paths.indices {
                draw(word, i, at: origin, pose: T.Pose(dy: 0, scale: 1, blur: 0, opacity: 1), in: &context)
            }
            return
        }
        let a = MorphingTitleMenuType.word(pages[m.from].title, size: size)
        let b = MorphingTitleMenuType.word(pages[m.to].title, size: size)
        let e = now.timeIntervalSince(m.start)
        if reduceMotion {
            let p = T.clamp(e / T.fade)
            for i in a.paths.indices { draw(a, i, at: origin, pose: T.Pose(dy: 0, scale: 1, blur: 0, opacity: 1 - p), in: &context) }
            for i in b.paths.indices { draw(b, i, at: origin, pose: T.Pose(dy: 0, scale: 1, blur: 0, opacity: p), in: &context) }
            return
        }
        for i in 0..<max(a.paths.count, b.paths.count) {
            let start = Double(i) * T.stagger
            if i < a.paths.count {
                let p = T.clamp((e - start) / T.outDuration)
                if p < 1 { draw(a, i, at: origin, pose: T.out(p, size: size), in: &context) }
            }
            if i < b.paths.count {
                let q = T.clamp((e - start - T.inDelay) / T.inDuration)
                if q > 0 { draw(b, i, at: origin, pose: T.in(q, size: size), in: &context) }
            }
        }
    }

    /// One letter, moved, scaled about its centre (half its advance, half the cap height) and blurred.
    private func draw(_ word: MorphingTitleMenuType.Word, _ i: Int, at origin: CGPoint, pose: MorphingTitleMenuMorph.Pose, in context: inout GraphicsContext) {
        guard pose.opacity > 0.001 else { return }
        let half = word.advances[i] / 2
        let cx = origin.x + word.xs[i] + half
        let cy = origin.y - word.capHeight / 2 + pose.dy
        context.drawLayer { layer in
            layer.opacity = pose.opacity
            if pose.blur > 0.05 { layer.addFilter(.blur(radius: pose.blur)) }
            layer.translateBy(x: cx, y: cy)
            layer.scaleBy(x: pose.scale, y: pose.scale)
            layer.translateBy(x: -half, y: word.capHeight / 2)
            layer.fill(word.paths[i], with: .color(P.ink))
        }
    }

    private var searchPill: some View {
        HStack(spacing: 8) {
            Text(searchLabel)
                .font(.system(size: 17, weight: .medium))
                .foregroundStyle(Color(hex: 0x6E6D72))
            MorphingTitleMenuIcon(kind: .search, color: Color(hex: 0x6E6D72))
                .frame(width: 19, height: 19)
        }
        .padding(.horizontal, 18)
        .frame(height: 46)
        .background(
            Capsule()
                .fill(Color(hex: 0xFAF9FA))
                .shadow(color: .black.opacity(0.07), radius: 12, y: 5)
                .shadow(color: .black.opacity(0.05), radius: 1, y: 0.5)
        )
        .overlay(Capsule().strokeBorder(Color.white, lineWidth: 1))
    }

    // MARK: Lists

    private var lists: some View {
        ZStack(alignment: .topLeading) {
            ForEach(pages.indices, id: \.self) { i in
                let on = selected == i
                page(pages[i])
                    .opacity(on ? 1 : 0)
                    .blur(radius: on || reduceMotion ? 0 : 6)
                    .offset(y: on || reduceMotion ? 0 : 8)
                    .allowsHitTesting(on)
                    .accessibilityHidden(!on)
            }
        }
    }

    private func page(_ page: MorphingTitleMenuPage) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(page.subtitle)
                .font(.system(size: 17, weight: .medium))
                .foregroundStyle(P.mute)
            VStack(alignment: .leading, spacing: K.episodeGap) {
                ForEach(page.episodes.indices, id: \.self) { i in
                    MorphingTitleMenuEpisodeRow(episode: page.episodes[i])
                }
            }
            .padding(.top, K.listTop)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: Popover

    private var popover: some View {
        let row = K.menuRow
        return ZStack(alignment: .topLeading) {
            // The highlight and its check travel together from row to row.
            HStack {
                Spacer(minLength: 0)
                ZStack {
                    Circle().fill(P.ink)
                    MorphingTitleMenuIcon(kind: .check, color: .white).frame(width: 14, height: 14)
                }
                .frame(width: 21, height: 21)
                .padding(.trailing, 11)
            }
            .frame(width: K.menuWidth - 2 * K.menuInset, height: row)
            .background(RoundedRectangle(cornerRadius: K.menuRadius - K.menuInset, style: .continuous).fill(P.pill))
            .offset(y: CGFloat(highlight) * row)

            VStack(spacing: 0) {
                ForEach(pages.indices, id: \.self) { i in
                    Text(pages[i].label)
                        .font(.system(size: 16, weight: .medium))
                        .foregroundStyle(P.ink2)
                        .padding(.leading, 14)
                        .frame(width: K.menuWidth - 2 * K.menuInset, height: row, alignment: .leading)
                        .contentShape(Rectangle())
                        .onTapGesture {
                            touched()
                            haptic.impactOccurred()
                            pick(i)
                        }
                        .accessibilityAddTraits(selected == i ? [.isButton, .isSelected] : .isButton)
                }
            }
        }
        .padding(K.menuInset)
        .frame(width: K.menuWidth, alignment: .topLeading)
        .background(
            RoundedRectangle(cornerRadius: K.menuRadius, style: .continuous)
                .fill(P.card)
                .shadow(color: .black.opacity(0.13), radius: 34, y: 16)
                .shadow(color: .black.opacity(0.06), radius: 2, y: 1)
        )
        .modifier(MorphingTitleMenuPop(p: menu, reduced: reduceMotion))
        .allowsHitTesting(menuOpen)
        .accessibilityHidden(!menuOpen)
    }

    // MARK: Mini player

    private var miniPlayer: some View {
        let now = pages[0].episodes[0]
        return HStack(spacing: 12) {
            MorphingTitleMenuArt(colors: now.art, mark: now.mark, size: 44, radius: 12)
            VStack(alignment: .leading, spacing: 2) {
                Text(now.title)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(P.ink)
                    .lineLimit(1)
                Text("\(now.show) · \(now.length) left")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(P.mute)
                    .lineLimit(1)
            }
            Spacer(minLength: 8)
            ZStack {
                Circle().fill(P.ink)
                MorphingTitleMenuIcon(kind: .pause, color: .white).frame(width: 16, height: 16)
            }
            .frame(width: 42, height: 42)
        }
        .padding(.leading, 10)
        .padding(.trailing, 11)
        .frame(height: 64)
        .background(
            Capsule()
                .fill(Color.white)
                .shadow(color: .black.opacity(0.10), radius: 22, y: 10)
                .shadow(color: .black.opacity(0.05), radius: 1, y: 0.5)
        )
    }

    // MARK: Actions

    private func openMenu() {
        highlight = selected
        menuOpen = true
        withAnimation(reduceMotion ? .easeOut(duration: 0.2) : M.open) { menu = 1 }
    }

    private func closeMenu() {
        menuOpen = false
        withAnimation(reduceMotion ? .easeOut(duration: 0.2) : M.close) { menu = 0 }
    }

    /// A row of the popover was picked: the highlight moves to it, the popover closes and the title morphs.
    private func pick(_ i: Int) {
        withAnimation(reduceMotion ? nil : M.highlight) { highlight = i }
        pickTask?.cancel()
        pickTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(M.closeAfterPick))
            guard !Task.isCancelled else { return }
            closeMenu()
            try? await Task.sleep(for: .seconds(M.commitAfterClose))
            guard !Task.isCancelled else { return }
            commit(i)
        }
    }

    private func commit(_ i: Int) {
        guard i != selected else { return }
        let m = Morph(from: selected, to: i, start: .now)
        morph = m
        withAnimation(reduceMotion ? .easeOut(duration: MorphingTitleMenuMorph.fade) : M.content) { selected = i }
        onSelect?(i)
        let slots = max(pages[m.from].title.count, pages[m.to].title.count)
        let total = reduceMotion ? MorphingTitleMenuMorph.fade : MorphingTitleMenuMorph.duration(slots: slots)
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(total + 0.05))
            if morph?.id == m.id { morph = nil }
        }
    }

    /// A touch takes over from the autoPlay loop, which starts again from rest a while later.
    private func touched() {
        touches += 1
    }

    private func loop() async {
        guard autoPlay, !reduceMotion else { return }
        let clock = ContinuousClock()
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled else { return }
            pickTask?.cancel()
            if menuOpen {
                closeMenu()
                try? await Task.sleep(for: .seconds(0.25))
                guard !Task.isCancelled else { return }
            }
            if selected != 0 {
                commit(0)
                try? await Task.sleep(for: .seconds(1.0))
                guard !Task.isCancelled else { return }
            }
        }
        var origin = clock.now
        func at(_ t: Double) async -> Bool {
            try? await clock.sleep(until: origin + .seconds(t))
            return !Task.isCancelled
        }
        while !Task.isCancelled {
            for (offset, target) in [(0.0, 1), (M.half, 0)] where target < pages.count {
                guard await at(offset + M.openAt) else { return }
                openMenu()
                guard await at(offset + M.pickAt) else { return }
                withAnimation(M.highlight) { highlight = target }
                guard await at(offset + M.pickAt + M.closeAfterPick) else { return }
                closeMenu()
                guard await at(offset + M.pickAt + M.closeAfterPick + M.commitAfterClose) else { return }
                commit(target)
            }
            origin += .seconds(M.loop)
            guard await at(0) else { return }
        }
    }
}

/// The popover's way in and out, from one number: it grows from its top-left corner (under the
/// title), fades and sharpens. Under reduced motion it only fades.
struct MorphingTitleMenuPop: ViewModifier, Animatable {
    var p: Double
    var reduced: Bool

    var animatableData: Double {
        get { p }
        set { p = newValue }
    }

    func body(content: Content) -> some View {
        content
            .scaleEffect(reduced ? 1 : 0.45 + 0.55 * p, anchor: UnitPoint(x: 0.12, y: 0))
            .opacity(min(1, max(0, reduced ? p : p * 1.8)))
            .blur(radius: reduced ? 0 : max(0, 1 - p) * 10)
    }
}

struct MorphingTitleMenuEpisodeRow: View {
    let episode: MorphingTitleMenuEpisode
    private typealias P = MorphingTitleMenuPalette

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 7) {
                MorphingTitleMenuArt(colors: episode.art, mark: episode.mark, size: 20, radius: 5)
                Text(episode.show)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(P.ink2)
                Text(episode.when)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(P.faint)
                ZStack {
                    RoundedRectangle(cornerRadius: 4, style: .continuous).fill(Color(hex: 0xDDDBDF))
                    MorphingTitleMenuIcon(kind: .check, color: .white).frame(width: 11, height: 11)
                }
                .frame(width: 15, height: 15)
            }
            .lineLimit(1)
            Text(episode.title)
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(P.ink)
                .lineLimit(3)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 9)
            if let progress = episode.progress {
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(P.track)
                        Capsule().fill(P.fill).frame(width: geo.size.width * progress)
                    }
                }
                .frame(height: 3)
                .padding(.top, 13)
            }
            HStack(spacing: 10) {
                HStack(spacing: 7) {
                    MorphingTitleMenuIcon(kind: episode.playing ? .pause : .play, color: P.ink)
                        .frame(width: 15, height: 15)
                    Text(episode.playing ? "Pause" : "Play")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(P.ink)
                    Text(episode.length)
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(P.mute)
                }
                .lineLimit(1)
                .padding(.horizontal, 14)
                .frame(height: 36)
                .background(Capsule().fill(P.chip))
                MorphingTitleMenuIcon(kind: .archive, color: Color(hex: 0x6E6D72))
                    .frame(width: 18, height: 18)
                    .frame(width: 36, height: 36)
                    .background(Circle().fill(P.chip))
                MorphingTitleMenuIcon(kind: .dots, color: P.faint)
                    .frame(width: 20, height: 20)
                    .frame(width: 30, height: 36)
            }
            .padding(.top, 13)
        }
        .accessibilityElement(children: .combine)
    }
}

/// A show's cover: a two-colour square with its initial.
struct MorphingTitleMenuArt: View {
    let colors: [Color]
    let mark: String
    let size: CGFloat
    let radius: CGFloat

    var body: some View {
        RoundedRectangle(cornerRadius: radius, style: .continuous)
            .fill(LinearGradient(colors: colors, startPoint: .topLeading, endPoint: .bottomTrailing))
            .overlay(
                Text(mark)
                    .font(.system(size: size * 0.46, weight: .heavy))
                    .foregroundStyle(.white.opacity(0.92))
            )
            .frame(width: size, height: size)
    }
}

/// The few icons of the screen, drawn in a 24-unit box so every stack draws the same ones.
struct MorphingTitleMenuIcon: View {
    enum Kind { case chevrons, search, check, pause, play, archive, dots }
    let kind: Kind
    let color: Color

    var body: some View {
        Canvas { context, size in
            let s = min(size.width, size.height) / 24
            context.translateBy(x: (size.width - 24 * s) / 2, y: (size.height - 24 * s) / 2)
            context.scaleBy(x: s, y: s)
            let shade = GraphicsContext.Shading.color(color)
            func stroke(_ path: Path, _ width: CGFloat) {
                context.stroke(path, with: shade, style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
            }
            switch kind {
            case .chevrons:
                var p = Path()
                p.move(to: CGPoint(x: 6.5, y: 9)); p.addLine(to: CGPoint(x: 12, y: 3.5)); p.addLine(to: CGPoint(x: 17.5, y: 9))
                p.move(to: CGPoint(x: 6.5, y: 15)); p.addLine(to: CGPoint(x: 12, y: 20.5)); p.addLine(to: CGPoint(x: 17.5, y: 15))
                stroke(p, 2.4)
            case .search:
                var p = Path(ellipseIn: CGRect(x: 4.25, y: 4.25, width: 12.5, height: 12.5))
                p.move(to: CGPoint(x: 15.3, y: 15.3)); p.addLine(to: CGPoint(x: 20, y: 20))
                stroke(p, 2.3)
            case .check:
                var p = Path()
                p.move(to: CGPoint(x: 6.5, y: 12.5)); p.addLine(to: CGPoint(x: 10.2, y: 16)); p.addLine(to: CGPoint(x: 17.5, y: 8.5))
                stroke(p, 2.8)
            case .pause:
                context.fill(Path(roundedRect: CGRect(x: 6, y: 4.5, width: 4, height: 15), cornerRadius: 1.3), with: shade)
                context.fill(Path(roundedRect: CGRect(x: 14, y: 4.5, width: 4, height: 15), cornerRadius: 1.3), with: shade)
            case .play:
                var p = Path()
                p.move(to: CGPoint(x: 7, y: 4.8)); p.addLine(to: CGPoint(x: 19.2, y: 12)); p.addLine(to: CGPoint(x: 7, y: 19.2)); p.closeSubpath()
                context.fill(p, with: shade)
                stroke(p, 1.5)
            case .archive:
                var p = Path(roundedRect: CGRect(x: 3.5, y: 4.5, width: 17, height: 4.5), cornerRadius: 1.4)
                p.move(to: CGPoint(x: 5, y: 9)); p.addLine(to: CGPoint(x: 5, y: 18))
                p.addQuadCurve(to: CGPoint(x: 6.5, y: 19.5), control: CGPoint(x: 5, y: 19.5))
                p.addLine(to: CGPoint(x: 17.5, y: 19.5))
                p.addQuadCurve(to: CGPoint(x: 19, y: 18), control: CGPoint(x: 19, y: 19.5))
                p.addLine(to: CGPoint(x: 19, y: 9))
                p.move(to: CGPoint(x: 10, y: 12.8)); p.addLine(to: CGPoint(x: 14, y: 12.8))
                stroke(p, 1.8)
            case .dots:
                for x in [5.0, 12.0, 19.0] {
                    context.fill(Path(ellipseIn: CGRect(x: x - 1.7, y: 10.3, width: 3.4, height: 3.4)), with: shade)
                }
            }
        }
        .accessibilityHidden(true)
    }
}

#Preview {
    MorphingTitleMenu(autoPlay: true)
}
