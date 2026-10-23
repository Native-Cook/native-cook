import CoreText
import SwiftUI
import UIKit

// A split-flap departures board: 4 rows of 7 black flap cells with magenta letters. To change
// message every cell flips through the alphabet towards its new character: the top half of the
// old character folds down over the seam while the new one's top shows behind it, then the new
// bottom half falls into place. A cell takes at most 3 to 8 flips (fixed per cell), each 0.13 s,
// and cells start 60 ms apart left to right and 50 ms apart top to bottom.
//
// autoPlay timeline (SplitFlapBoardMotion.loop = 9 s):
//   0.0-0.6   rest on the first message (also the first frame on screen)
//   0.6       flips to the second message (all cells settled by about 2.2 s)
//   3.6       flips to the third message (settled by about 5.2 s)
//   6.6       flips back to the first message (settled by about 8.2 s); rest until 9.0
// A flip's top flap folds down over 0.065 s and the bottom flap falls over the next 0.065 s,
// both as progress squared (a hinged flap starting slowly), around the seam with a 260 pt
// perspective, darkening up to 55% as they turn edge-on.
// A tap shows the next message at once; 2 s after the last tap the board flips back to the first
// message and the loop starts over.

enum SplitFlapBoardMotion {
    static let loop = 9.0
    static let switches: [(at: Double, to: Int)] = [(0.6, 1), (3.6, 2), (6.6, 0)]
    /// One flip: the top flap folds down for half of it, the bottom flap falls for the other half.
    static let flip = 0.13
    static let columnDelay = 0.06
    static let rowDelay = 0.05
    static let userHold = 2.0
    /// Under reduced motion a message cross-fades over this long instead.
    static let fade = 0.25

    static let alphabet = Array(" ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789:")

    /// The most flips a cell makes, 3 to 8, fixed per cell so neighbours never move in step.
    static func cap(row: Int, column: Int) -> Int {
        3 + (row * 7 + column * 3) % 6
    }

    /// The characters a cell shows on its way from `a` to `b`: `a`, then the last few letters
    /// before `b` in alphabet order, ending on `b`.
    static func sequence(from a: Character, to b: Character, cap: Int) -> [Character] {
        guard a != b, let ia = alphabet.firstIndex(of: a), let ib = alphabet.firstIndex(of: b) else { return [b] }
        let n = alphabet.count
        let distance = (ib - ia + n) % n
        let flips = min(distance, cap)
        return [a] + (0..<flips).map { alphabet[(ib - flips + 1 + $0 + n) % n] }
    }

    static func delay(row: Int, column: Int) -> Double {
        Double(column) * columnDelay + Double(row) * rowDelay
    }
}

enum SplitFlapBoardPalette {
    static let backdropCentre = Color(hex: 0x2B2B2D)
    static let backdropEdge = Color(hex: 0x141415)
    static let panel = Color(hex: 0x1A1A1B)
    static let panelEdge = Color(hex: 0x303032)
    static let cellTop = [Color(hex: 0x313133), Color(hex: 0x282829)]
    static let cellBottom = [Color(hex: 0x252526), Color(hex: 0x1D1D1E)]
    static let seam = Color(hex: 0x0B0B0C)
    static let hinge = Color(hex: 0x1F1F20)
    static let hingeEdge = Color(hex: 0x3A3A3C)
    static let letter = Color(hex: 0xD4146A)
    static let label = Color(hex: 0x8A8A8F)
    static let heading = Color(hex: 0xEDEDEF)
    static let dim = Color(hex: 0x3A3A3D)
}

enum SplitFlapBoardMetrics {
    static let columns = 7
    static let rows = 4
    static let side: CGFloat = 16
    static let panelPadding: CGFloat = 10
    static let columnGap: CGFloat = 6
    static let rowGap: CGFloat = 12
    /// Cell height over width.
    static let aspect: CGFloat = 1.5
    /// Letter size over cell height.
    static let letterScale: CGFloat = 0.66
    static let radius: CGFloat = 5
    /// The gap between a cell's two halves.
    static let seamGap: CGFloat = 1.5
    /// Perspective distance of the flaps, in points.
    static let perspective: CGFloat = 260
}

enum SplitFlapBoardType {
    static let registered: Void = {
        if let url = Bundle.main.url(forResource: "SplitFlapBoard-Barlow-SemiBold", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    static func font(_ size: CGFloat) -> Font {
        _ = registered
        return .custom("Barlow-SemiBold", fixedSize: size)
    }
}

struct SplitFlapBoard: View {
    /// Messages of up to 4 lines of up to 7 characters (A–Z, 0–9, colon, space); each line is centred.
    var messages: [[String]] = [
        ["GATE 4", "OPENS", "AT", "09:41"],
        ["NEXT", "TRAIN", "HARBOR", "09:55"],
        ["HAVE A", "GOOD", "TRIP", ""],
    ]
    var title = "Departures"
    var heading = "Platform 2"
    var hint = "Tap the board for the next message"
    /// Flips through the messages by itself, every `SplitFlapBoardMotion.loop` seconds.
    var autoPlay = false

    private typealias M = SplitFlapBoardMotion
    private typealias K = SplitFlapBoardMetrics
    private typealias P = SplitFlapBoardPalette

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shown = 0
    @State private var transition: Transition? = nil
    @State private var touches = 0
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    private struct Transition {
        let id = UUID()
        /// Per row and column, the characters the cell goes through.
        let sequences: [[[Character]]]
        let start: Date
        let duration: Double
    }

    /// A message as a grid of characters, each line centred.
    private func grid(_ index: Int) -> [[Character]] {
        let lines = messages[index]
        return (0..<K.rows).map { r in
            let line = Array((r < lines.count ? lines[r] : "").uppercased().prefix(K.columns))
            let left = (K.columns - line.count) / 2
            return (0..<K.columns).map { c in
                let i = c - left
                let ch = i >= 0 && i < line.count ? line[i] : " "
                return M.alphabet.contains(ch) ? ch : " "
            }
        }
    }

    /// The widest cell that fits the screen's width with the board's margins and gaps.
    static func cellWidth(_ screen: CGFloat) -> CGFloat {
        let columns = CGFloat(SplitFlapBoardMetrics.columns)
        let margins: CGFloat = 2 * SplitFlapBoardMetrics.side + 2 * SplitFlapBoardMetrics.panelPadding
        let gaps: CGFloat = (columns - 1) * SplitFlapBoardMetrics.columnGap
        return floor((screen - margins - gaps) / columns)
    }

    var body: some View {
        GeometryReader { geo in
            let safe = geo.safeAreaInsets
            let full = CGSize(width: geo.size.width + safe.leading + safe.trailing, height: geo.size.height + safe.top + safe.bottom)
            let cellW = Self.cellWidth(full.width)
            let cellH = round(cellW * K.aspect)
            ZStack {
                RadialGradient(colors: [P.backdropCentre, P.backdropEdge], center: .center, startRadius: 0, endRadius: full.height * 0.62)

                VStack(spacing: 0) {
                    Text(title.uppercased())
                        .font(.system(size: 13, weight: .semibold))
                        .tracking(2.4)
                        .foregroundStyle(P.label)
                        .padding(.top, safe.top + 18)
                    Text(heading)
                        .font(SplitFlapBoardType.font(34))
                        .foregroundStyle(P.heading)
                        .padding(.top, 6)

                    Spacer(minLength: 0)

                    TimelineView(.animation(paused: transition == nil)) { context in
                        board(at: context.date, cellW: cellW, cellH: cellH)
                    }
                    .contentShape(Rectangle())
                    .onTapGesture { tapped() }
                    .accessibilityElement()
                    .accessibilityLabel(messages[shown].filter { !$0.isEmpty }.joined(separator: " "))
                    .accessibilityHint("Shows the next message")
                    .accessibilityAddTraits(.isButton)

                    HStack(spacing: 8) {
                        ForEach(messages.indices, id: \.self) { i in
                            Capsule()
                                .fill(i == shown ? P.letter : P.dim)
                                .frame(width: i == shown ? 18 : 6, height: 6)
                        }
                    }
                    .animation(reduceMotion ? nil : .spring(response: 0.35, dampingFraction: 0.8), value: shown)
                    .padding(.top, 28)

                    Spacer(minLength: 0)

                    Text(hint)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(P.label)
                        .padding(.bottom, safe.bottom + 28)
                }
                .frame(width: full.width)
            }
            .frame(width: full.width, height: full.height)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .onAppear { haptic.prepare() }
        .task(id: LoopKey(on: autoPlay && !reduceMotion, touches: touches)) { await loop() }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }

    private func board(at now: Date, cellW: CGFloat, cellH: CGFloat) -> some View {
        let rest = grid(shown)
        return VStack(spacing: K.rowGap) {
            ForEach(0..<K.rows, id: \.self) { r in
                ZStack {
                    HStack(spacing: K.columnGap) {
                        ForEach(0..<K.columns, id: \.self) { c in
                            cell(r, c, rest: rest[r][c], at: now, w: cellW, h: cellH)
                        }
                    }
                    rowSeam(cellW: cellW)
                }
            }
        }
        .padding(K.panelPadding)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(P.panel)
                .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(P.panelEdge, lineWidth: 1))
                .shadow(color: .black.opacity(0.5), radius: 24, y: 14)
        )
    }

    /// The seam line across a row, with a hinge block at both ends and between cells.
    private func rowSeam(cellW: CGFloat) -> some View {
        let width = CGFloat(K.columns) * cellW + CGFloat(K.columns - 1) * K.columnGap
        return ZStack {
            Rectangle().fill(P.seam).frame(width: width + 8, height: 1)
            ForEach(0...K.columns, id: \.self) { i in
                RoundedRectangle(cornerRadius: 1.5)
                    .fill(P.hinge)
                    .overlay(RoundedRectangle(cornerRadius: 1.5).strokeBorder(P.hingeEdge, lineWidth: 0.75))
                    .frame(width: 5, height: 9)
                    .offset(x: i == 0 ? -width / 2 - 1 : i == K.columns ? width / 2 + 1 : -width / 2 + CGFloat(i) * (cellW + K.columnGap) - K.columnGap / 2)
            }
        }
        .allowsHitTesting(false)
    }

    @ViewBuilder
    private func cell(_ r: Int, _ c: Int, rest: Character, at now: Date, w: CGFloat, h: CGFloat) -> some View {
        if let t = transition {
            let seq = t.sequences[r][c]
            let e = now.timeIntervalSince(t.start) - M.delay(row: r, column: c)
            if reduceMotion {
                let p = min(1, max(0, now.timeIntervalSince(t.start) / M.fade))
                ZStack {
                    SplitFlapBoardCell(top: seq.first!, bottom: seq.first!, w: w, h: h).opacity(1 - p)
                    SplitFlapBoardCell(top: seq.last!, bottom: seq.last!, w: w, h: h).opacity(p)
                }
            } else if e <= 0 || seq.count < 2 {
                SplitFlapBoardCell(top: seq[0], bottom: seq[0], w: w, h: h)
            } else {
                let k = Int(e / M.flip)
                if k >= seq.count - 1 {
                    SplitFlapBoardCell(top: seq.last!, bottom: seq.last!, w: w, h: h)
                } else {
                    let f = (e - Double(k) * M.flip) / M.flip
                    SplitFlapBoardCell(top: seq[k + 1], bottom: seq[k], w: w, h: h, flapFrom: seq[k], flapTo: seq[k + 1], progress: f)
                }
            }
        } else {
            SplitFlapBoardCell(top: rest, bottom: rest, w: w, h: h)
        }
    }

    // MARK: Actions

    /// The character a cell shows now (the one being revealed, mid-flip).
    private func showing(_ r: Int, _ c: Int, at now: Date) -> Character {
        guard let t = transition else { return grid(shown)[r][c] }
        let seq = t.sequences[r][c]
        if reduceMotion { return seq.last! }
        let e = now.timeIntervalSince(t.start) - M.delay(row: r, column: c)
        if e <= 0 { return seq[0] }
        return seq[min(seq.count - 1, Int(e / M.flip) + 1)]
    }

    private func show(_ index: Int) {
        let now = Date()
        let target = grid(index)
        var longest = 0.0
        let sequences = (0..<K.rows).map { r in
            (0..<K.columns).map { c -> [Character] in
                let seq = M.sequence(from: showing(r, c, at: now), to: target[r][c], cap: M.cap(row: r, column: c))
                longest = max(longest, M.delay(row: r, column: c) + Double(seq.count - 1) * M.flip)
                return seq
            }
        }
        let t = Transition(sequences: sequences, start: now, duration: reduceMotion ? M.fade : longest)
        transition = t
        shown = index
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(t.duration + 0.05))
            if transition?.id == t.id { transition = nil }
        }
    }

    private func tapped() {
        touches += 1
        haptic.impactOccurred()
        show((shown + 1) % messages.count)
    }

    private func loop() async {
        guard autoPlay, !reduceMotion else { return }
        let clock = ContinuousClock()
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled else { return }
            if shown != 0 {
                show(0)
                try? await Task.sleep(for: .seconds((transition?.duration ?? 0) + 0.6))
                guard !Task.isCancelled else { return }
            }
        }
        var origin = clock.now
        while !Task.isCancelled {
            for s in M.switches where s.to < messages.count {
                try? await clock.sleep(until: origin + .seconds(s.at))
                guard !Task.isCancelled else { return }
                show(s.to)
            }
            origin += .seconds(M.loop)
            try? await clock.sleep(until: origin)
        }
    }
}

/// One flap cell: the static top and bottom halves, and while it flips, the falling flap.
/// `progress` 0-0.5 folds the top half of `flapFrom` down over the seam; 0.5-1 drops the bottom
/// half of `flapTo` into place. Both fall like a hinged flap, slow at first (progress squared).
struct SplitFlapBoardCell: View {
    let top: Character
    let bottom: Character
    let w: CGFloat
    let h: CGFloat
    var flapFrom: Character? = nil
    var flapTo: Character? = nil
    var progress = 0.0

    private typealias K = SplitFlapBoardMetrics

    var body: some View {
        let halfH = (h - K.seamGap) / 2
        let perspective = max(w, halfH) / K.perspective
        ZStack {
            VStack(spacing: K.seamGap) {
                SplitFlapBoardHalf(char: top, isTop: true, w: w, h: h)
                SplitFlapBoardHalf(char: bottom, isTop: false, w: w, h: h)
            }
            if let from = flapFrom, let to = flapTo {
                if progress < 0.5 {
                    let q = progress / 0.5
                    let angle = -90 * q * q
                    SplitFlapBoardHalf(char: from, isTop: true, w: w, h: h, shade: 0.55 * q)
                        .rotation3DEffect(.degrees(angle), axis: (1, 0, 0), anchor: .bottom, perspective: perspective)
                        .frame(width: w, height: h, alignment: .top)
                } else {
                    let q = (progress - 0.5) / 0.5
                    let angle = 90 * (1 - q * q)
                    SplitFlapBoardHalf(char: to, isTop: false, w: w, h: h, shade: 0.55 * (1 - q * q))
                        .rotation3DEffect(.degrees(angle), axis: (1, 0, 0), anchor: .top, perspective: perspective)
                        .frame(width: w, height: h, alignment: .bottom)
                }
            }
        }
        .frame(width: w, height: h)
    }
}

/// Half a cell: its own piece of the rounded card and the matching half of the letter, which is
/// set so the middle of its capitals sits on the seam.
struct SplitFlapBoardHalf: View {
    let char: Character
    let isTop: Bool
    let w: CGFloat
    let h: CGFloat
    var shade = 0.0

    private typealias K = SplitFlapBoardMetrics
    private typealias P = SplitFlapBoardPalette

    var body: some View {
        let halfH = (h - K.seamGap) / 2
        let size = round(h * K.letterScale)
        let r = K.radius
        let shape = UnevenRoundedRectangle(
            topLeadingRadius: isTop ? r : 1, bottomLeadingRadius: isTop ? 1 : r,
            bottomTrailingRadius: isTop ? 1 : r, topTrailingRadius: isTop ? r : 1, style: .continuous
        )
        ZStack {
            shape.fill(LinearGradient(colors: isTop ? P.cellTop : P.cellBottom, startPoint: .top, endPoint: .bottom))
            if isTop {
                // A hairline of light along the card's top edge.
                shape.strokeBorder(LinearGradient(colors: [.white.opacity(0.07), .clear], startPoint: .top, endPoint: .center), lineWidth: 1)
            }
            // Barlow's line box is 1.2 em with the baseline 1 em down, so its middle is 0.05 em
            // below the middle of the capitals (0.35 em above the baseline): lift it that much.
            Text(String(char))
                .font(SplitFlapBoardType.font(size))
                .foregroundStyle(P.letter)
                .frame(width: w, height: h)
                .offset(y: (isTop ? (h - halfH) / 2 : -(h - halfH) / 2) - 0.05 * size)
            if shade > 0 {
                shape.fill(Color.black.opacity(shade))
            }
        }
        .frame(width: w, height: halfH)
        .clipShape(shape)
    }
}

#Preview {
    SplitFlapBoard(autoPlay: true)
}
