import SwiftUI
import UIKit

/// The reusable piece: "<lead> / <word> <tail>" where the word rolls letter by letter on vertical wheels, its width
/// morphs so the tail slides along by offset, and a coloured underline stretches to match. Word widths are measured
/// once (per font size) from the font itself; nothing is laid out again while the words roll.
struct RotatingWordHeadlineText: View {
    typealias M = RotatingWordHeadlineMotion

    let words: [String]
    let accents: [Color]
    let lead: String
    let tail: String
    /// Largest font size; shrinks (at most twice) if the longest line does not fit `maxWidth`.
    let fontSize: CGFloat
    let maxWidth: CGFloat
    let ink: Color
    let driver: RotatingWordHeadlineDriver

    @State private var gesture = GesturePhase.idle
    @State private var touchDown = Date.distantPast

    private enum GesturePhase { case idle, undecided, drag, failed }

    var body: some View {
        let m = RotatingWordHeadlineMetrics(words: words, tail: tail, fontSize: fontSize, maxWidth: maxWidth)
        let fs = m.fs
        let slotH = fs * 1.26
        VStack(alignment: .leading, spacing: 0) {
            Text(lead)
                .font(.system(size: fs, weight: .heavy))
                .tracking(-fs * 0.03)
                .foregroundStyle(ink)
                .lineLimit(1)
                .frame(height: fs * 1.12, alignment: .center)

            TimelineView(.animation(paused: !driver.isMoving(at: .now))) { tl in
                let f = driver.frame(at: tl.date)
                line(m: m, f: f)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .frame(height: slotH + 14, alignment: .topLeading)
            .contentShape(Rectangle())
            .gesture(drag(fs: fs))
        }
        .accessibilityElement(children: .ignore)
        .accessibilityAddTraits(.isHeader)
        .accessibilityLabel("\(lead) \(words.first ?? "") \(tail)")
    }

    @ViewBuilder
    private func line(m: RotatingWordHeadlineMetrics, f: RotatingWordHeadlineFrame) -> some View {
        let fs = m.fs
        let slotH = fs * 1.26
        let gap = fs * 0.27
        let p = f.pos
        let a = M.wrapIndex(Int(floor(p)), M.words)
        let b = M.wrapIndex(Int(floor(p)) + 1, M.words)
        let w = M.mix(m.widths[a], m.widths[b], M.morphAmount(p, M.widthLag))
        let u = M.morphAmount(p, M.lineLag)
        let lineW = M.mix(m.widths[a], m.widths[b], u)
        let full = max(1, m.slotW)
        let ghostWord = M.wrapIndex(Int(p.rounded()), M.words)

        ZStack(alignment: .topLeading) {
            // The slot: all words stacked, clipped to one line; each letter on its own wheel.
            ZStack(alignment: .topLeading) {
                ForEach(0..<words.count, id: \.self) { k in
                    word(k: k, m: m, pos: p)
                }
            }
            .frame(width: m.slotW, height: slotH, alignment: .topLeading)
            .clipped()

            Capsule()
                .fill(RotatingWordHeadlineColor.mix(accents[a], accents[b], u))
                .frame(width: m.slotW, height: max(5, fs * 0.13))
                .scaleEffect(x: lineW / full, y: 1, anchor: .leading)
                .offset(y: slotH + 2)

            Text(tail)
                .font(.system(size: fs, weight: .heavy))
                .tracking(-fs * 0.03)
                .foregroundStyle(ink)
                .lineLimit(1)
                .fixedSize()
                .frame(height: fs * 1.2)
                .offset(x: w + gap, y: (slotH - fs * 1.2) / 2)

            Circle()
                .fill(Color.white.opacity(0.55))
                .overlay(Circle().strokeBorder(Color(red: 40 / 255, green: 36 / 255, blue: 30 / 255).opacity(0.18), lineWidth: 1))
                .shadow(color: Color(red: 0x2a / 255, green: 0x24 / 255, blue: 0x1c / 255).opacity(0.18), radius: 10, y: 4)
                .frame(width: 44, height: 44)
                .scaleEffect(f.ghostScale)
                .opacity(f.ghostOpacity * 0.9)
                .offset(x: m.widths[ghostWord] * 0.5 - 22, y: slotH / 2 - 22)
                .allowsHitTesting(false)
        }
    }

    private func word(k: Int, m: RotatingWordHeadlineMetrics, pos: Double) -> some View {
        let fs = m.fs
        let letters = m.letters[k]
        return ZStack(alignment: .topLeading) {
            ForEach(0..<letters.count, id: \.self) { i in
                let wv = M.letterWheel(pos, k, i)
                Text(letters[i].ch)
                    .font(.system(size: fs, weight: .heavy))
                    .tracking(-fs * 0.03)
                    .foregroundStyle(ink)
                    .fixedSize()
                    .frame(width: letters[i].w, height: fs * 1.2, alignment: .leading)
                    .modifier(RotatingWordHeadlineWheel(
                        rise: driver.reducedMotion ? 0 : wv * fs * M.rollRise,
                        degrees: driver.reducedMotion ? 0 : -wv * M.rollDeg,
                        perspective: fs * 9))
                    .opacity(1 - M.easeInOutCubic(min(1, abs(wv) * 1.15)))
                    .offset(x: letters[i].x, y: fs * 0.03)
                    // One label for the whole headline (above); single letters on wheels are never read.
                    .accessibilityHidden(true)
            }
        }
    }

    private func drag(fs: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                let dx = v.translation.width
                let dy = v.translation.height
                switch gesture {
                case .idle:
                    gesture = .undecided
                    touchDown = .now
                    fallthrough
                case .undecided:
                    if abs(dx) >= 24 {
                        gesture = .failed
                    } else if abs(dy) >= 8 {
                        gesture = .drag
                        driver.dragBegan()
                        driver.dragChanged(translationY: dy, fontSize: fs)
                    }
                case .drag:
                    driver.dragChanged(translationY: dy, fontSize: fs)
                case .failed:
                    break
                }
            }
            .onEnded { v in
                switch gesture {
                case .drag:
                    driver.dragEnded(velocityY: v.velocity.height, fontSize: fs)
                case .undecided, .idle:
                    if Date.now.timeIntervalSince(touchDown) < 0.4 { driver.tap(fontSize: fs) }
                case .failed:
                    break
                }
                gesture = .idle
            }
    }
}

/// A letter on its wheel: rise, then a turn about its horizontal axis seen from `perspective` points away
/// (RN: `[{ perspective }, { translateY }, { rotateX }]` about the letter's centre).
struct RotatingWordHeadlineWheel: GeometryEffect {
    var rise: CGFloat
    var degrees: CGFloat
    var perspective: CGFloat

    func effectValue(size: CGSize) -> ProjectionTransform {
        let cx = size.width / 2
        let cy = size.height / 2
        var p = CATransform3DIdentity
        p.m34 = -1 / perspective
        let toOrigin = CATransform3DMakeTranslation(-cx, -cy, 0)
        let rot = CATransform3DMakeRotation(degrees * .pi / 180, 1, 0, 0)
        let move = CATransform3DMakeTranslation(0, rise, 0)
        let back = CATransform3DMakeTranslation(cx, cy, 0)
        // Row-vector order: centre, turn, rise, perspective, back.
        let t = CATransform3DConcat(CATransform3DConcat(CATransform3DConcat(CATransform3DConcat(toOrigin, rot), move), p), back)
        return ProjectionTransform(t)
    }
}

/// Word, letter and tail widths at the fitted font size, from the font itself (SwiftUI's `.system` font is the
/// same face as `UIFont.systemFont`). Memoised per input, so the body never measures twice.
struct RotatingWordHeadlineMetrics {
    struct Letter { let ch: String; let x: CGFloat; let w: CGFloat }

    let fs: CGFloat
    let widths: [CGFloat]
    let letters: [[Letter]]
    let tail: CGFloat
    var slotW: CGFloat { widths.max() ?? 0 }

    private static var cache: [String: RotatingWordHeadlineMetrics] = [:]

    init(words: [String], tail: String, fontSize: CGFloat, maxWidth: CGFloat) {
        let key = "\(words.joined(separator: "|"))#\(tail)#\(fontSize)#\(maxWidth)"
        if let hit = Self.cache[key] {
            self = hit
            return
        }
        var fs = fontSize
        var shrinks = 0
        while true {
            let font = UIFont.systemFont(ofSize: fs, weight: .heavy)
            let attrs: [NSAttributedString.Key: Any] = [.font: font, .kern: -fs * 0.03]
            func width(_ s: String) -> CGFloat { ceil(NSAttributedString(string: s, attributes: attrs).size().width * 2) / 2 }
            var all: [[Letter]] = []
            var ws: [CGFloat] = []
            for word in words {
                var x: CGFloat = 0
                var row: [Letter] = []
                for ch in word {
                    let w = width(String(ch))
                    row.append(Letter(ch: String(ch), x: x, w: w))
                    x += w
                }
                all.append(row)
                ws.append(x)
            }
            let tailW = width(tail)
            let line = (ws.max() ?? 0) + fs * 0.27 + tailW
            if line > maxWidth + 0.5, shrinks < 2 {
                // Shrink so the longest line fits, then measure again at the new size.
                fs = floor(fs * maxWidth / line)
                shrinks += 1
                continue
            }
            self.fs = fs
            self.widths = ws
            self.letters = all
            self.tail = tailW
            break
        }
        Self.cache[key] = self
    }
}

enum RotatingWordHeadlineColor {
    /// RGB blend of two colours (RN `interpolateColor`).
    static func mix(_ a: Color, _ b: Color, _ u: Double) -> Color {
        let ca = UIColor(a)
        let cb = UIColor(b)
        var r1: CGFloat = 0, g1: CGFloat = 0, b1: CGFloat = 0, a1: CGFloat = 0
        var r2: CGFloat = 0, g2: CGFloat = 0, b2: CGFloat = 0, a2: CGFloat = 0
        ca.getRed(&r1, green: &g1, blue: &b1, alpha: &a1)
        cb.getRed(&r2, green: &g2, blue: &b2, alpha: &a2)
        let t = CGFloat(u)
        return Color(.sRGB, red: r1 + (r2 - r1) * t, green: g1 + (g2 - g1) * t, blue: b1 + (b2 - b1) * t, opacity: a1 + (a2 - a1) * t)
    }

    static func hex(_ v: UInt32, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((v >> 16) & 0xff) / 255, green: Double((v >> 8) & 0xff) / 255, blue: Double(v & 0xff) / 255, opacity: alpha)
    }
}
