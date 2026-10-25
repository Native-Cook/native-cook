import SwiftUI
import UIKit

/*
 Chalkboard Search — a café app's menu search on a chalkboard. The query is chalked onto the
 board as you type: each glyph's outline (from the font, nudged and tilted a little) filled with
 a chalk shader, revealed by the pen moving through it, dust falling from under the stroke. The
 menu lines that match are chalked in below and rewritten when the query changes. A swipe across
 the board is a felt eraser: it wipes the chalk where it goes and leaves a haze that clears.

 The board is a Canvas that never changes, so SwiftUI draws it once. Over it one live Canvas
 draws the placeholder, the chalk in one layer the eraser cuts out of (destinationOut), the dust,
 the haze and the eraser. A hidden TextField over the search line takes the keyboard. The loop
 script is in ChalkboardSearchMotion.swift.
*/
struct ChalkboardSearch: View {
    /// Writes "lemon tart", chalks three menu lines, wipes the board, every `loop` seconds.
    var autoPlay = false

    static let loop = 9.0

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            ChalkboardSearchStage(autoPlay: autoPlay, size: size, insetTop: insets.top, insetBottom: insets.bottom)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .ignoresSafeArea(.keyboard)
        .background(ChalkboardSearchArt.hex(0xECE3D3).ignoresSafeArea())
    }
}

private struct ChalkboardSearchStage: View {
    let autoPlay: Bool
    let size: CGSize
    let insetTop: Double
    let insetBottom: Double

    private typealias A = ChalkboardSearchArt
    private typealias M = ChalkboardSearchMotion

    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.displayScale) private var displayScale
    @State private var model = ChalkboardSearchModel()
    @State private var eraser: Image?
    @State private var awake = false
    /// Bumped when the query changes, so the hidden field and the lines follow.
    @State private var revision = 0
    @FocusState private var focused: Bool

    var body: some View {
        let g = ChalkboardSearchGeo(w: size.width, h: size.height, insetTop: insetTop)
        let auto = autoPlay && !reduced
        model.configure(g: g, auto: auto, reduced: reduced, onSleep: {
            awake = false
            focused = false
            revision += 1
        })
        return ZStack(alignment: .topLeading) {
            ChalkboardSearchBoardView(g: g)
                .equatable()
                .frame(width: g.w, height: g.canvasH)
                .allowsHitTesting(false)

            TimelineView(.animation(paused: !(auto || awake))) { timeline in
                let b = model.frame(timeline.date)
                live(g: g, board: b)
                    .frame(width: g.w, height: g.canvasH)
            }
            .allowsHitTesting(false)

            // The whole board takes the eraser swipe; the search line takes a tap.
            ZStack(alignment: .topLeading) {
                Color.clear
                    .frame(width: g.w, height: g.canvasH)
                    .contentShape(Rectangle())
                    .accessibilityHidden(true)
                searchField(g)
            }
            .frame(width: g.w, height: g.canvasH, alignment: .topLeading)
            .simultaneousGesture(eraseDrag)

            ChalkboardSearchCafeInfo(top: g.boardY + g.boardH + 28, h: g.h, insetBottom: insetBottom)
                .frame(width: g.w, height: g.h, alignment: .topLeading)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
        .onAppear { eraser = A.renderEraser(scale: displayScale) }
        .onChange(of: focused) { _, f in
            wake()
            if f { model.onFocus() } else { model.onBlur() }
            revision += 1
        }
    }

    private func wake() {
        if !awake { awake = true }
    }

    private func searchField(_ g: ChalkboardSearchGeo) -> some View {
        let text = Binding<String>(
            get: { _ = revision; return model.query },
            set: { v in
                wake()
                model.onChange(String(v.prefix(22)))
                revision += 1
            }
        )
        return TextField("", text: text)
            .font(.system(size: 22))
            .foregroundStyle(.clear)
            .tint(.clear)
            .autocorrectionDisabled()
            .textInputAutocapitalization(.never)
            .submitLabel(.search)
            .onSubmit { focused = false }
            .focused($focused)
            .frame(width: max(0, g.slateW - 32), height: 52)
            .offset(x: g.slateX + 16, y: g.qy - 40)
            .accessibilityLabel("Search the menu")
    }

    private var eraseDrag: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                if model.eraseChanged(v) { wake() }
            }
            .onEnded { _ in
                if model.eraseEnded() { revision += 1 }
            }
    }

    // MARK: Drawing

    private func live(g: ChalkboardSearchGeo, board s: ChalkboardSearchBoard) -> some View {
        let lines = model.lines
        let placeholder = model.placeholder
        let layerOpacity = model.layerOpacity
        let erase = model.erasePath
        let dust = model.dustPath()
        let eraserPose = model.eraserPose(s)
        let eraser = eraser
        return Canvas { c, _ in
            // The eraser's haze, under the chalk.
            if s.haze > 0.001 && s.trail > 0, let erase {
                let p = s.trail >= 1 ? erase : erase.trimmedPath(from: 0, to: s.trail)
                c.drawLayer { l in
                    l.opacity = s.haze
                    l.addFilter(.blur(radius: 10))
                    l.stroke(p, with: .color(Color(red: 222 / 255, green: 228 / 255, blue: 220 / 255).opacity(0.16)), style: StrokeStyle(lineWidth: 60 + 6, lineCap: .round, lineJoin: .round))
                }
            }

            // The placeholder, rubbed away while a query is written.
            let ph = s.placeholder * 0.34
            if ph > 0.001 {
                c.drawLayer { l in
                    l.opacity = ph
                    A.drawChalk(&l, placeholder.path, seed: 41)
                }
            }

            // The live chalk, cut out by the eraser.
            c.drawLayer { l in
                l.opacity = layerOpacity
                let qClip = CGRect(x: g.qx - 30, y: g.qy - 46, width: max(0, s.pen - g.qx + 30), height: 70)
                var clips: [(Path, CGRect, Double)] = []
                if qClip.width > 0 { clips.append((lines.query.path, qClip, 21)) }
                for (j, line) in lines.sugs.enumerated() {
                    let wj = (line.end - g.sx + 20) * s.sug[j]
                    if wj > 0 { clips.append((line.path, CGRect(x: g.sx - 10, y: g.sy[j] - 30, width: wj, height: 44), 30 + Double(j) * 7)) }
                }
                // One blurred layer for every line's haze.
                if !clips.isEmpty {
                    l.drawLayer { h in
                        h.addFilter(.blur(radius: 3))
                        for (path, rect, _) in clips {
                            var k = h
                            k.clip(to: Path(rect))
                            k.fill(path, with: .color(Color(red: 238 / 255, green: 240 / 255, blue: 232 / 255).opacity(0.07)))
                        }
                    }
                }
                for (path, rect, seed) in clips {
                    var k = l
                    k.clip(to: Path(rect))
                    A.drawChalk(&k, path, seed: seed, haze: false)
                }
                if s.trail > 0, let erase {
                    let p = s.trail >= 1 ? erase : erase.trimmedPath(from: 0, to: s.trail)
                    l.blendMode = .destinationOut
                    l.stroke(p, with: .color(.black), style: StrokeStyle(lineWidth: 60, lineCap: .round, lineJoin: .round))
                }
            }

            c.fill(dust, with: .color(Color(red: 236 / 255, green: 238 / 255, blue: 230 / 255).opacity(0.62)))

            if s.eraserOn > 0.001, let eraser {
                c.drawLayer { e in
                    e.opacity = s.eraserOn
                    e.translateBy(x: eraserPose.0, y: eraserPose.1)
                    e.rotate(by: .radians(eraserPose.2))
                    e.draw(eraser, in: A.eraserRect)
                }
            }
        }
    }
}

/// The board, drawn once.
private struct ChalkboardSearchBoardView: View, Equatable {
    let g: ChalkboardSearchGeo

    var body: some View {
        Canvas { c, _ in
            ChalkboardSearchArt.drawBoard(&c, g)
        }
    }
}

/// The café details below the board; the specials row shows when the screen is tall enough.
private struct ChalkboardSearchCafeInfo: View {
    let top: Double
    let h: Double
    let insetBottom: Double

    private typealias A = ChalkboardSearchArt
    private static let specials: [(name: String, price: String, icon: String)] = [
        ("Cardamom bun", "$5 · 6 left", "birthday.cake"),
        ("Flat white", "$4 · oat +$0.5", "cup.and.saucer"),
        ("Fresh mint tea", "$3 · pot for two", "leaf"),
    ]

    var body: some View {
        let showSpecials = top + 98 + 116 <= h - insetBottom - 8
        ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .firstTextBaseline) {
                    Text("Open today · 7:00 – 18:00")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(A.hex(0x33261B))
                    Spacer(minLength: 8)
                    Text("0.4 km")
                        .font(.system(size: 13, weight: .medium).monospacedDigit())
                        .foregroundStyle(A.hex(0x7A6A58))
                }
                Text("14 Alder Lane, by the canal")
                    .font(.system(size: 14))
                    .foregroundStyle(A.hex(0x7A6A58))
                HStack(spacing: 8) {
                    ForEach(0..<4, id: \.self) { i in
                        let c = ["Pastry", "Coffee", "Brunch", "Tea"][i]
                        Text(c)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(i == 0 ? A.hex(0xEEF0E8) : A.hex(0x4A3A2A))
                            .padding(.horizontal, 16)
                            .frame(height: 32)
                            .background(Capsule().fill(i == 0 ? A.hex(0x2C3A32) : A.hex(0xE2D6C2)))
                            .overlay(
                                Capsule().strokeBorder(LinearGradient(colors: [.white.opacity(0.6), .white.opacity(0)], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.15)), lineWidth: 1)
                            )
                    }
                }
                .padding(.top, 8)
                if showSpecials {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("AT THE COUNTER NOW")
                            .font(.system(size: 11, weight: .bold))
                            .kerning(1.6)
                            .foregroundStyle(A.hex(0x7A6A58))
                        HStack(spacing: 8) {
                            ForEach(0..<Self.specials.count, id: \.self) { n in
                                let sp = Self.specials[n]
                                VStack(alignment: .leading, spacing: 2) {
                                    Image(systemName: sp.icon)
                                        .font(.system(size: 15))
                                        .foregroundStyle(A.hex(0x2C3A32))
                                        .frame(width: 28, height: 28)
                                        .background(Circle().fill(A.hex(0xE2D6C2)))
                                        .padding(.bottom, 4)
                                    Text(sp.name)
                                        .font(.system(size: 14, weight: .semibold))
                                        .foregroundStyle(A.hex(0x33261B))
                                        .lineLimit(1)
                                    Text(sp.price)
                                        .font(.system(size: 12, weight: .medium).monospacedDigit())
                                        .foregroundStyle(A.hex(0x7A6A58))
                                        .lineLimit(1)
                                        .minimumScaleFactor(0.8)
                                }
                                .padding(10)
                                .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)
                                .background(
                                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                                        .fill(A.hex(0xF6EFE3))
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                                .strokeBorder(LinearGradient(colors: [.white.opacity(0.8), .white.opacity(0)], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.12)), lineWidth: 1)
                                        )
                                        .shadow(color: Color(red: 60 / 255, green: 40 / 255, blue: 20 / 255).opacity(0.12), radius: 1, y: 1)
                                        .shadow(color: Color(red: 60 / 255, green: 40 / 255, blue: 20 / 255).opacity(0.1), radius: 9, y: 8)
                                )
                            }
                        }
                    }
                    .padding(.top, 16)
                }
            }
            .padding(.horizontal, 24)
            .offset(y: top)

            if !showSpecials {
                Text("Tap the board to search · swipe across it to wipe")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Color(red: 74 / 255, green: 58 / 255, blue: 42 / 255).opacity(0.6))
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                    .padding(.horizontal, 24)
                    .padding(.bottom, insetBottom + 16)
            }
        }
    }
}

/// The user state and the autoPlay clock, stepped once a frame like the RN frame callback.
@MainActor
final class ChalkboardSearchModel {
    private typealias A = ChalkboardSearchArt
    private typealias M = ChalkboardSearchMotion
    private static let idle = 2.0
    private static let returnLen = 0.65
    private static let phText = "search the menu…"

    struct Lines {
        var query: ChalkboardSearchLine
        var sugs: [ChalkboardSearchLine]
    }

    private var g: ChalkboardSearchGeo?
    private var auto = false
    private var reduced = false
    private var onSleep: () -> Void = {}

    private var autoLines: Lines?
    private var userLines: Lines?
    private(set) var placeholder = ChalkboardSearchLine(path: Path(), lefts: [], rights: [], spaces: [], end: 0)
    private var trailPath = Path()
    private var trailSamples: [(Double, Double, Double)] = []

    private var last: Date?
    private var now = 0.0
    private var clock = 0.0
    /// 0 = autoPlay (or rest), 1 = the user drives, 2 = easing back to rest.
    private var mode = 0
    private var lastTouch = 0.0
    private var isFocused = false
    private var returnAt = 0.0
    private(set) var query = ""
    private var births: [Double] = []
    private var sugAt = [-9.0, -9, -9]
    private var uPen = ChalkboardSearchTween(0)
    private var uPh = ChalkboardSearchTween(1)
    private var uHaze = ChalkboardSearchTween(0)
    private var uEraserOn = ChalkboardSearchTween(0)
    private var uEraser = (0.0, 0.0, 0.0)
    /// The finger's eraser stroke.
    private var uErase: [CGPoint] = []
    private var fade = ChalkboardSearchTween(1)
    /// The menu lines on the board, so entering the user's mode does not chalk them again.
    private var sugKeySeen = ""
    private var erasing = 0 // 0 undecided, 1 erasing, −1 a tap or a vertical drag
    private var lastDrag: (CGPoint, Date)?
    private var board = ChalkboardSearchBoard(pen: 0, sug: [0, 0, 0], trail: 0, eraserOn: 0, haze: 0, placeholder: 1)

    func configure(g: ChalkboardSearchGeo, auto: Bool, reduced: Bool, onSleep: @escaping () -> Void) {
        if auto != self.auto { clock = 0 }
        self.auto = auto
        self.reduced = reduced
        self.onSleep = onSleep
        if self.g != g {
            self.g = g
            autoLines = makeLines(M.query, M.autoSuggestions)
            userLines = makeLines(query, M.suggest(query))
            placeholder = A.chalkLine(Self.phText, font: A.phFont, x: g.qx, baseline: g.qy - 2, seed: 41)
            (trailPath, trailSamples) = A.trail(g)
            if mode == 0 { uPen.set(g.qx) }
        }
    }

    private func makeLines(_ q: String, _ sugs: [String]) -> Lines {
        guard let g else { return Lines(query: placeholder, sugs: []) }
        let query = A.chalkLine(q, font: A.queryFont, x: g.qx, baseline: g.qy, seed: 21)
        let lines = sugs.enumerated().map { j, s in
            var line = A.chalkLine(s, font: A.sugFont, x: g.sx + 16, baseline: g.sy[j], seed: 30 + Double(j) * 7)
            line.path.addEllipse(in: CGRect(x: g.sx + 4 - 2.6, y: g.sy[j] - 7 - 2.6, width: 5.2, height: 5.2))
            return line
        }
        return Lines(query: query, sugs: lines)
    }

    var lines: Lines {
        (mode == 0 ? autoLines : userLines) ?? Lines(query: placeholder, sugs: [])
    }

    var layerOpacity: Double { mode == 0 ? 1 : fade.value(now) }

    /// The path the eraser wipes along: the loop's trail, or the finger's stroke.
    var erasePath: Path? {
        if mode == 0 { return trailPath }
        guard let first = uErase.first else { return nil }
        var p = Path()
        p.move(to: first)
        for q in uErase.dropFirst() { p.addLine(to: q) }
        if uErase.count == 1 { p.addLine(to: CGPoint(x: first.x + 0.1, y: first.y)) }
        return p
    }

    func eraserPose(_ s: ChalkboardSearchBoard) -> (Double, Double, Double) {
        if mode != 0 { return uEraser }
        guard !trailSamples.isEmpty else { return (0, 0, 0) }
        let k = min(120, Int((s.trail * 120).rounded()))
        let e = trailSamples[k]
        return (e.0, e.1, 0.12 * sin(e.2))
    }

    // MARK: Frame

    func frame(_ date: Date) -> ChalkboardSearchBoard {
        let raw = last.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        last = date
        let dt = min(max(raw, 0), 0.064)
        if dt > 0 { step(dt) }
        board = currentBoard()
        return board
    }

    private func step(_ dt: Double) {
        let n = now + dt
        now = n
        if mode == 0 {
            if auto { clock = (clock + dt).truncatingRemainder(dividingBy: ChalkboardSearch.loop) }
        } else if mode == 1 {
            if !isFocused && uEraserOn.value(n) == 0 && uEraserOn.settled(n) && n - lastTouch > Self.idle {
                mode = 2
                returnAt = n
                fade.animate(to: 0, duration: 0.6, now: n)
            }
        } else if n - returnAt > Self.returnLen {
            uHaze.set(0)
            uErase = []
            births = []
            clock = 0
            mode = 0
            fade.set(1)
            query = ""
            userLines = makeLines("", [])
            if let g { uPen.set(g.qx) }
            let sleep = onSleep
            DispatchQueue.main.async { sleep() }
        }
    }

    private func currentBoard() -> ChalkboardSearchBoard {
        guard let g else { return board }
        if mode == 0 {
            if auto {
                let q = lines.query
                return M.autoBoard(clock, lefts: q.lefts, rights: q.rights)
            }
            return ChalkboardSearchBoard(pen: g.qx, sug: [0, 0, 0], trail: 0, eraserOn: 0, haze: 0, placeholder: 1)
        }
        let n = now
        return ChalkboardSearchBoard(
            pen: uPen.value(n),
            sug: (0..<3).map { j in reduced ? 1 : M.easeInOutSine(M.seg(n, sugAt[j], sugAt[j] + M.sugLen)) },
            trail: 1,
            eraserOn: uEraserOn.value(n),
            haze: uHaze.value(n),
            placeholder: uPh.value(n)
        )
    }

    /// Chalk dust: dustPerGlyph grains from under each glyph while it is written.
    func dustPath() -> Path {
        var path = Path()
        guard !reduced, let g else { return path }
        let q = lines.query
        let t = mode == 0 ? clock : now
        for i in 0..<q.lefts.count {
            if q.spaces[i] { continue }
            let birth = mode == 0 ? M.glyphAt(i) : (i < births.count ? births[i] : -9)
            for k in 0..<M.dustPerGlyph {
                let h1 = M.hash(Double(i) * 17 + Double(k) * 3.7)
                let h2 = M.hash(Double(i) * 29 + Double(k) * 5.3 + 1)
                let tau = t - (birth + (M.qStep * Double(k)) / Double(M.dustPerGlyph))
                if tau < 0 || tau > M.dustLife { continue }
                let x = q.lefts[i] + (q.rights[i] - q.lefts[i]) * h1 + (h2 - 0.5) * 22 * tau
                let y = g.qy + 3 + 160 * (0.5 + h2) * tau * tau
                let r = (0.6 + 0.9 * h2) * (1 - tau / M.dustLife)
                path.addEllipse(in: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
            }
        }
        return path
    }

    // MARK: Touch

    private func enterUser(fromSwipe: Bool) {
        lastTouch = now
        if mode == 1 { return }
        var q = ""
        var shown = false
        if mode == 0 && auto {
            let t = clock
            if t < 7.2 {
                var n = 0
                let chars = Array(M.query)
                for i in 0..<chars.count where M.glyphAt(i) + M.qStep <= t { n = i + 1 }
                q = String(chars.prefix(n))
                while q.hasSuffix(" ") { q.removeLast() }
                shown = t > 4.5
            }
        }
        query = q
        let sugs = M.suggest(q)
        userLines = makeLines(q, sugs)
        births = Array(repeating: -9, count: q.count)
        if let g { uPen.set(q.isEmpty ? g.qx : (userLines?.query.end ?? g.qx) + 4) }
        sugAt = shown ? [-9, -9, -9] : [now, now + 0.15, now + 0.3]
        uPh.set(q.isEmpty ? 1 : 0)
        if !fromSwipe { uErase = [] }
        sugKeySeen = sugs.joined(separator: "|")
        fade.set(1)
        mode = 1
    }

    /// Moves the pen to the end of the new line and chalks changed menu lines in.
    private func applyLines() {
        guard let g else { return }
        let sugs = M.suggest(query)
        userLines = makeLines(query, sugs)
        let q = userLines?.query
        let end = (q?.lefts.isEmpty ?? true) ? g.qx : (q?.end ?? g.qx) + 4
        if reduced { uPen.set(end) } else { uPen.animate(to: end, duration: 0.18, now: now) }
        let key = sugs.joined(separator: "|")
        if key != sugKeySeen {
            sugKeySeen = key
            sugAt = [now + 0.05, now + 0.2, now + 0.35]
        }
    }

    func onFocus() {
        enterUser(fromSwipe: false)
        isFocused = true
    }

    func onBlur() {
        isFocused = false
        lastTouch = now
    }

    func onChange(_ v: String) {
        if mode != 1 { enterUser(fromSwipe: false) }
        let old = Array(query)
        let nv = Array(v)
        let n = now
        var next: [Double] = []
        var fresh = 0
        for i in 0..<nv.count {
            if i < old.count && old[i] == nv[i] && i < births.count {
                next.append(births[i])
            } else {
                next.append(n + Double(fresh) * 0.06)
                fresh += 1
            }
        }
        births = next
        uErase = []
        uPh.animate(to: v.isEmpty ? 1 : 0, duration: 0.2, now: n)
        lastTouch = n
        query = v
        applyLines()
    }

    /// The eraser swipe: it starts once the finger has moved 12 pt sideways. Returns true when it
    /// takes over the board.
    func eraseChanged(_ v: DragGesture.Value) -> Bool {
        if erasing == 0 {
            if abs(v.translation.width) >= 12 {
                erasing = 1
                enterUser(fromSwipe: true)
                uErase = [v.location]
                uEraser = (v.location.x, v.location.y, 0)
                uEraserOn.animate(to: 1, duration: 0.12, now: now)
                uHaze.set(1)
                lastDrag = (v.location, v.time)
                return true
            }
            return false
        }
        guard erasing == 1 else { return false }
        uErase.append(v.location)
        var vx = 0.0
        if let (p, t) = lastDrag {
            let dt = v.time.timeIntervalSince(t)
            if dt > 0 { vx = (v.location.x - p.x) / dt }
        }
        lastDrag = (v.location, v.time)
        uEraser = (v.location.x, v.location.y, max(-0.25, min(0.25, vx / 4000)))
        lastTouch = now
        return false
    }

    /// Returns true when the query was cleared.
    func eraseEnded() -> Bool {
        defer {
            erasing = 0
            lastDrag = nil
        }
        guard erasing == 1 else { return false }
        query = ""
        births = []
        uPh.animate(to: 1, duration: 0.5, now: now)
        applyLines()
        uEraserOn.animate(to: 0, duration: 0.2, now: now)
        uHaze.animate(to: 0, duration: reduced ? 0.3 : 1.8, now: now)
        lastTouch = now
        return true
    }
}
