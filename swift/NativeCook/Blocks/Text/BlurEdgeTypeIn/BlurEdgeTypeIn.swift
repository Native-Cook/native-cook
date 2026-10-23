import CoreText
import SwiftUI
import UIKit

// A writing app's welcome screen on near-black. A four-line headline in tall condensed capitals is
// typed in letter by letter: the last four characters behind the moving front are blurred, faint
// and spread out to the right, and sharpen and close up as the front moves on. An orange caret
// rides the front (and pulses while the headline holds) and a thin bar under the headline fills
// with it. After a hold the headline is taken back the same way from its end. Dragging sideways
// scrubs the front.
//
// The front `f` counts characters over the whole headline (spaces and line breaks included, so a
// line break is a short pause). A character `k` with d = f - k <= 0 is not drawn; with
// 0 < d < 4, u = d / 4: opacity u, blur 0.16 em × (1 - u)², shifted right 0.3 em × (1 - u)²;
// from d >= 4 it is sharp and in place.
//
// autoPlay timeline (BlurEdgeTypeInMotion.loop = 8 s), with N characters and full = N + 4:
//   0.0-0.5   empty (the rest state)
//   0.5-3.4   types in: f 0 -> full, linear (about 14 characters a second, as in the reference)
//   3.4-6.4   holds, sharp, while the caret pulses three times (opacity 0.55 + 0.45 cos, 1 s)
//   6.4-7.6   taken back from the end: f full -> 0, linear (about 34 characters a second)
//   7.6-8.5   empty (wrapping round to 0.5), the caret pulsing once at the start of the line
// While the front moves the caret is fully on. Something always moves, so a screen recording
// never sits on a stale frame.
// The caret is shown the whole loop; it sits 0.3 em ahead of the front while letters are blurred
// and closes up to the text at either end (its lead is scaled by min(f, 1) and by the share of
// the edge still to type), so at rest it stands at the start of the line.
//
// Touch: dragging sideways moves the front 1 character per 8 pt from where it was (eased over
// 0.12 s), with a selection tick at each word's end. 2 s after the finger lifts the headline is
// taken back over 0.9 s and the loop starts over.

enum BlurEdgeTypeInMotion {
    static let loop = 8.0
    /// Characters behind the front that are still blurred.
    static let edge = 4.0
    static let typeStart = 0.5
    static let typeEnd = 3.4
    static let eraseStart = 6.4
    static let eraseEnd = 7.6
    /// The caret pulses every `pulse` seconds while the headline holds, and fades in over
    /// `caretFade` when a finger takes over.
    static let pulse = 1.0
    static let caretFade = 0.08
    /// Points of drag per character.
    static let dragStep = 8.0
    static let userHold = 2.0
    static let settle = 0.9
    /// In ems of the headline size.
    static let maxBlur = 0.16
    static let maxShift = 0.3

    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }

    /// The front `t` seconds into the loop, for a headline whose last front is `full`.
    static func front(at t: Double, full: Double) -> Double {
        if t < eraseStart { return full * clamp((t - typeStart) / (typeEnd - typeStart)) }
        return full * (1 - clamp((t - eraseStart) / (eraseEnd - eraseStart)))
    }

    /// The caret's opacity `t` seconds into the loop.
    static func caret(at t: Double) -> Double {
        if t > typeEnd, t < eraseStart { return 0.55 + 0.45 * cos(2 * .pi * (t - typeEnd) / pulse) }
        if t < typeStart || t > eraseEnd {
            let r = t > eraseEnd ? t - eraseEnd : t + loop - eraseEnd
            return 0.55 + 0.45 * cos(2 * .pi * r / (loop - eraseEnd + typeStart))
        }
        return 1
    }
}

enum BlurEdgeTypeInPalette {
    static let background = Color(hex: 0x070707)
    static let ink = Color.white
    static let caret = Color(hex: 0xFF5B2E)
}

/// One character of the headline, placed by Core Text.
struct BlurEdgeTypeInGlyph {
    let character: String
    let x: CGFloat
    let advance: CGFloat
    let line: Int
    let index: Int
}

struct BlurEdgeTypeInLayout {
    let size: CGFloat
    let glyphs: [BlurEdgeTypeInGlyph]
    let lineCount: Int
    /// Distance between baselines, and from the caps' top to the first baseline.
    var step: CGFloat { size * 0.86 }
    var cap: CGFloat { size * 0.7 }
    var height: CGFloat { cap + CGFloat(lineCount - 1) * step }
    var full: Double { Double(glyphs.count) + BlurEdgeTypeInMotion.edge }

    static let fontName = "BebasNeue-Regular"
    static let registered: Void = {
        if let url = Bundle.main.url(forResource: "BlurEdgeTypeIn-BebasNeue-Regular", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    static func uiFont(_ size: CGFloat) -> UIFont {
        _ = registered
        return UIFont(name: fontName, size: size) ?? .systemFont(ofSize: size, weight: .heavy)
    }

    private static func line(_ text: String, size: CGFloat) -> CTLine {
        CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: [.font: uiFont(size)]))
    }

    /// The lines at the size that makes the widest span `width`, at most `limit`. A line break
    /// between lines is a character too (a pause), placed at the end of its line.
    init(_ lines: [String], width: CGFloat, limit: CGFloat) {
        let widest = lines.map { CGFloat(CTLineGetTypographicBounds(Self.line($0, size: 100), nil, nil, nil)) }.max() ?? 1
        size = min(limit, floor(100 * width / max(widest, 1)))
        var glyphs: [BlurEdgeTypeInGlyph] = []
        for (row, text) in lines.enumerated() {
            let ct = Self.line(text, size: size)
            let chars = Array(text)
            let width = CGFloat(CTLineGetTypographicBounds(ct, nil, nil, nil))
            var offset = 0
            for (i, ch) in chars.enumerated() {
                let x = CTLineGetOffsetForStringIndex(ct, offset, nil)
                offset += String(ch).utf16.count
                let next = i + 1 < chars.count ? CTLineGetOffsetForStringIndex(ct, offset, nil) : width
                glyphs.append(BlurEdgeTypeInGlyph(character: String(ch), x: x, advance: next - x, line: row, index: glyphs.count))
            }
            if row < lines.count - 1 {
                glyphs.append(BlurEdgeTypeInGlyph(character: "", x: width, advance: 0, line: row, index: glyphs.count))
            }
        }
        self.glyphs = glyphs
        lineCount = lines.count
    }

    /// Where the front `f` is: the line it is on and its x.
    func caret(_ f: Double) -> (line: Int, x: CGFloat) {
        guard !glyphs.isEmpty else { return (0, 0) }
        let k = min(glyphs.count - 1, max(0, Int(floor(f))))
        let g = glyphs[k]
        let frac = CGFloat(min(1, max(0, f - Double(k))))
        return (g.line, g.x + frac * g.advance)
    }
}

struct BlurEdgeTypeIn: View {
    var lines = ["ONE WORD", "AT A TIME.", "THEN THE", "NEXT ONE."]
    var label = "Daily pages"
    var day = "Day 12"
    var message = "A quiet editor that shows only the line you are on. Everything else can wait."
    var action = "Start writing"
    var hint = "Drag sideways to scrub"
    /// Types the headline in and takes it back by itself, every `BlurEdgeTypeInMotion.loop` seconds.
    var autoPlay = false

    private typealias M = BlurEdgeTypeInMotion
    private typealias P = BlurEdgeTypeInPalette

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Where the loop's time starts.
    @State private var origin = Date()
    /// True from a touch until the headline is back at rest and the loop has started over.
    @State private var manual = false
    @State private var front = Tween(0)
    @State private var caretShown = Tween(0)
    /// The front under the finger when the drag began.
    @State private var dragFrom: Double? = nil
    @State private var lastWord = 0
    /// Changes on every touch, which restarts the loop behind a pause.
    @State private var touches = 0
    @State private var tick = UISelectionFeedbackGenerator()

    /// Room around the headline for the blur.
    private let pad: CGFloat = 24

    var body: some View {
        GeometryReader { geo in
            let safe = geo.safeAreaInsets
            let full = CGSize(width: geo.size.width + safe.leading + safe.trailing, height: geo.size.height + safe.top + safe.bottom)
            let layout = BlurEdgeTypeInLayout(lines, width: full.width - 48, limit: 104)
            TimelineView(.animation(paused: reduceMotion && !manual)) { context in
                let now = context.date
                let f = front(at: now, layout: layout)
                let caret = caretOpacity(at: now)
                ZStack(alignment: .top) {
                    P.background

                    VStack(alignment: .leading, spacing: 0) {
                        HStack {
                            Text(label)
                            Spacer()
                            Text(day)
                        }
                        .font(.system(size: 13, weight: .semibold))
                        .tracking(0.6)
                        .foregroundStyle(P.ink.opacity(0.55))
                        .padding(.top, safe.top + 12)
                        .padding(.horizontal, 24)

                        Spacer(minLength: 24)

                        headline(layout, front: f, caret: caret, width: full.width)
                            .padding(.vertical, -pad)

                        progress(min(1, f / layout.full))
                            .padding(.top, 28)
                            .padding(.horizontal, 24)

                        Text(message)
                            .font(.system(size: 17, weight: .regular))
                            .foregroundStyle(P.ink.opacity(0.62))
                            .lineSpacing(3)
                            .fixedSize(horizontal: false, vertical: true)
                            .padding(.horizontal, 24)
                            .padding(.top, 24)

                        // Twice the room below as above, so the headline sits high.
                        Spacer(minLength: 24)
                        Spacer(minLength: 0)

                        Text(action)
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(P.background)
                            .frame(maxWidth: .infinity)
                            .frame(height: 56)
                            .background(Capsule().fill(P.ink))
                            .padding(.horizontal, 24)
                            .accessibilityAddTraits(.isButton)

                        Text(hint)
                            .font(.system(size: 13, weight: .medium))
                            .foregroundStyle(P.ink.opacity(0.4))
                            .frame(maxWidth: .infinity)
                            .padding(.top, 16)
                            .padding(.bottom, safe.bottom + 12)
                    }
                    .frame(width: full.width, height: full.height)
                }
            }
            .frame(width: full.width, height: full.height)
            .contentShape(Rectangle())
            .gesture(drag(layout), including: reduceMotion ? .none : .all)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .onAppear {
            origin = .now
            tick.prepare()
        }
        .task(id: touches) { await settle() }
    }

    private func headline(_ layout: BlurEdgeTypeInLayout, front f: Double, caret: Double, width: CGFloat) -> some View {
        let size = layout.size
        let font = Font.custom(BlurEdgeTypeInLayout.fontName, size: size)
        return Canvas { context, _ in
            for g in layout.glyphs where !g.character.isEmpty && g.character != " " {
                let d = f - Double(g.index)
                guard d > 0 else { continue }
                let u = M.clamp(d / M.edge)
                let k = (1 - u) * (1 - u)
                let blur = M.maxBlur * size * k
                let x = 24 + g.x + M.maxShift * size * k
                let baseline = pad + layout.cap + CGFloat(g.line) * layout.step
                let text = context.resolve(Text(g.character).font(font).foregroundStyle(P.ink))
                // Core Text's line box for Bebas Neue is 0.9 em above the baseline.
                let point = CGPoint(x: x, y: baseline - 0.9 * size)
                if blur > 0.05 {
                    context.drawLayer { layer in
                        layer.opacity = u
                        layer.addFilter(.blur(radius: blur))
                        layer.draw(text, at: point, anchor: .topLeading)
                    }
                } else {
                    var c = context
                    c.opacity = u
                    c.draw(text, at: point, anchor: .topLeading)
                }
            }
            // The caret, just ahead of the newest letter.
            if caret > 0.001 {
                let at = layout.caret(f)
                let lead = M.clamp(f) * M.clamp((layout.full - f) / M.edge)
                let x = 24 + at.x + M.maxShift * size * lead + 4 * M.clamp(f)
                let top = pad + CGFloat(at.line) * layout.step
                let bar = CGRect(x: x, y: top - 0.04 * size, width: 4, height: layout.cap + 0.08 * size)
                context.fill(Path(roundedRect: bar, cornerRadius: 2), with: .color(P.caret.opacity(caret)))
            }
        }
        .frame(width: width, height: layout.height + 2 * pad)
        .accessibilityElement()
        .accessibilityLabel(lines.joined(separator: " "))
    }

    private func progress(_ p: Double) -> some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(P.ink.opacity(0.14))
                Capsule().fill(P.ink.opacity(0.8)).frame(width: geo.size.width * p)
            }
        }
        .frame(height: 2)
        .accessibilityHidden(true)
    }

    private func front(at date: Date, layout: BlurEdgeTypeInLayout) -> Double {
        if manual { return front.value(at: date) }
        guard autoPlay, !reduceMotion else { return layout.full }
        let t = date.timeIntervalSince(origin).truncatingRemainder(dividingBy: M.loop)
        return M.front(at: max(0, t), full: layout.full)
    }

    private func caretOpacity(at date: Date) -> Double {
        if manual { return caretShown.value(at: date) }
        guard autoPlay, !reduceMotion else { return 0 }
        let t = date.timeIntervalSince(origin).truncatingRemainder(dividingBy: M.loop)
        return M.caret(at: max(0, t))
    }

    /// How many words are complete with the front at `f`.
    private func words(_ layout: BlurEdgeTypeInLayout, _ f: Double) -> Int {
        layout.glyphs.filter { ($0.character == " " || $0.character.isEmpty || $0.index == layout.glyphs.count - 1) && Double($0.index) < f - M.edge + 1 }.count
    }

    private func drag(_ layout: BlurEdgeTypeInLayout) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let now = Date()
                if dragFrom == nil {
                    let current = front(at: now, layout: layout)
                    dragFrom = current
                    caretShown = Tween(caretOpacity(at: now))
                    caretShown.retarget(1, at: now, duration: M.caretFade, curve: .linear)
                    manual = true
                    front = Tween(current)
                    lastWord = words(layout, current)
                    touches += 1
                    tick.selectionChanged()
                }
                let target = min(layout.full, max(0, (dragFrom ?? 0) + value.translation.width / M.dragStep))
                front.retarget(target, at: now, duration: 0.12, curve: .easeOut)
                let w = words(layout, target)
                if w != lastWord { tick.selectionChanged() }
                lastWord = w
            }
            .onEnded { _ in
                dragFrom = nil
                touches += 1
            }
    }

    /// 2 s after the last touch the headline is taken back, and the loop starts over from empty.
    private func settle() async {
        guard touches > 0 else { return }
        try? await Task.sleep(for: .seconds(M.userHold))
        guard !Task.isCancelled, dragFrom == nil else { return }
        let now = Date()
        front.retarget(0, at: now, duration: M.settle, curve: .easeInOut)
        try? await Task.sleep(for: .seconds(M.settle))
        guard !Task.isCancelled, dragFrom == nil else { return }
        origin = .now
        manual = false
    }
}

#Preview {
    BlurEdgeTypeIn(autoPlay: true)
}
