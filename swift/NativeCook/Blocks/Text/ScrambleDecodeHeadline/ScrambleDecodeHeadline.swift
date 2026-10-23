import CoreText
import SwiftUI
import UIKit

/*
 Scramble Decode Headline — a release-notes hero whose headline keeps decoding itself.

 Letters are numbered left to right, line by line, spaces left out (k = 0…26 for the default
 copy "Always / shipping / something / new."). A window of letters scrambles: letter j of the window turns into violet glyphs 0.014·j s
 after the window starts, flickers to a new seeded glyph every 0.05 s, and settles back into its
 white letter `hold + settle·j` s after the start, so the decode sweeps left to right. A settled
 letter cools from violet to white over 0.15 s. A scrambled glyph wider than 1.5 times its letter's
 slot is scaled down towards that width, to 72 % at most. The violet glow behind the headline follows the
 share of letters that are scrambled. Glyphs never move: each one is centred in its letter's slot.

 autoPlay timeline (loop = 8 s):
   0.00         clean headline (also the first look).
   0.50         "Always" (k 0–5)            hold 0.32 s, settle 0.055 s per letter.
   1.60         "ething" (k 17–22)          same.
   2.70         "ng" + "new." (k 21–26)     same, across a line break.
   3.60, 3.75   "ipping" (k 8–13), then "som" (k 14–16): two windows overlapping.
   5.00         the whole headline (k 0–26) hold 0.35 s, settle 0.045 s per letter → clean at 6.52.
   6.52 … 8.00  clean, identical to t = 0.

 Touch: a tap scrambles the whole headline at once and decodes it (hold 0.35 s, settle 0.045 s). Dragging over the
 headline scrambles the letters within 36 pt of the finger; each settles 0.35 s after the finger
 leaves it. 2 s after the last touch the loop starts again from its clean first frame.
 Reduced motion: no loop and no scrambling; the headline stays clean.
 */

struct ScrambleDecodeHeadlineWindow: Equatable {
    /// Seconds into the loop.
    var start: Double
    /// First and last letter, counted without spaces.
    var first: Int
    var last: Int
    var hold = 0.32
    var settle = 0.055
    static let onset = 0.014
}

struct ScrambleDecodeHeadline: View {
    var lines = ["Always", "shipping", "something", "new."]
    var badge = "Release notes · v2.4"
    var subtitle = "Small, careful improvements every Friday, to the tools your team opens every morning."
    var action = "Read what's new"
    var windows = [
        ScrambleDecodeHeadlineWindow(start: 0.5, first: 0, last: 5),
        ScrambleDecodeHeadlineWindow(start: 1.6, first: 17, last: 22),
        ScrambleDecodeHeadlineWindow(start: 2.7, first: 21, last: 26),
        ScrambleDecodeHeadlineWindow(start: 3.6, first: 8, last: 13),
        ScrambleDecodeHeadlineWindow(start: 3.75, first: 14, last: 16),
        ScrambleDecodeHeadlineWindow(start: 5.0, first: 0, last: 26, hold: 0.35, settle: 0.045),
    ]
    var autoPlay = false
    var onAction: (() -> Void)? = nil

    static let loop: Double = 8
    static let flicker: Double = 0.05
    static let cool: Double = 0.15
    static let scrubRadius: CGFloat = 36
    static let scrubLinger: Double = 0.35
    static let charset = Array("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@#$%&!?*+=<>/")
    /// The charset's advances at 1 pt.
    static let charsetAdvances: [Character: CGFloat] = Dictionary(
        uniqueKeysWithValues: zip(charset, ScrambleDecodeHeadlineFont.advances(String(charset)))
    )

    static let background = Color(red: 0.063, green: 0.067, blue: 0.078) // #101114
    static let ink = Color(red: 0.949, green: 0.949, blue: 0.949) // #F2F2F2
    static let violet = Color(red: 0.486, green: 0.361, blue: 1.0) // #7C5CFF

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var autoStart: Date? = nil
    /// A tap's whole-headline decode.
    @State private var tapAt: Date? = nil
    /// When each letter was last under the finger.
    @State private var touched: [Int: Date] = [:]
    @State private var finger: CGPoint? = nil
    @State private var dragging = false
    @State private var moved = false
    @State private var resume: Task<Void, Never>? = nil
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing,
                              height: proxy.size.height + insets.top + insets.bottom)
            let layout = ScrambleDecodeHeadlineLayout(lines: lines, width: size.width, height: size.height)
            ZStack(alignment: .topLeading) {
                Self.background
                TimelineView(.animation(paused: reduceMotion)) { timeline in
                    headline(layout: layout, size: size, date: timeline.date)
                }
                .contentShape(Rectangle())
                .gesture(drag(layout: layout))
                chrome(layout: layout, size: size, insets: insets)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .ignoresSafeArea()
        }
        .onAppear {
            haptic.prepare()
            if autoPlay && !reduceMotion { autoStart = .now }
        }
        .onDisappear { resume?.cancel() }
    }

    // MARK: Letter state

    /// Whether letter k shows a scrambled glyph at `date`, the glyph step, and how long ago it settled (for the cool-down).
    struct LetterState {
        var scrambled: Bool
        var step: Int
        var settledAgo: Double
    }

    private func letter(_ k: Int, slot: ScrambleDecodeHeadlineLayout.Slot, at date: Date) -> LetterState {
        if reduceMotion { return LetterState(scrambled: false, step: 0, settledAgo: .infinity) }
        if let autoStart, date >= autoStart {
            let e = date.timeIntervalSince(autoStart).truncatingRemainder(dividingBy: Self.loop)
            return Self.state(k, windows: windows, e: e, step: Int(e / Self.flicker))
        }
        let abs = date.timeIntervalSinceReferenceDate
        let step = Int(abs / Self.flicker)
        var best = LetterState(scrambled: false, step: step, settledAgo: .infinity)
        if let tapAt {
            let e = date.timeIntervalSince(tapAt)
            let full = ScrambleDecodeHeadlineWindow(start: 0, first: 0, last: Int.max, hold: 0.35, settle: 0.045)
            best = Self.state(k, windows: [full], e: e, step: step)
            if best.scrambled { return best }
        }
        if let finger, dragging, moved, hypot(slot.center.x - finger.x, slot.center.y - finger.y) < Self.scrubRadius {
            return LetterState(scrambled: true, step: step, settledAgo: 0)
        }
        if let t = touched[k] {
            let ago = date.timeIntervalSince(t) - Self.scrubLinger
            if ago < 0 { return LetterState(scrambled: true, step: step, settledAgo: 0) }
            best.settledAgo = min(best.settledAgo, ago)
        }
        return best
    }

    /// Letter k under a set of windows, `e` seconds into their timeline.
    static func state(_ k: Int, windows: [ScrambleDecodeHeadlineWindow], e: Double, step: Int) -> LetterState {
        var settledAgo = Double.infinity
        for w in windows where k >= w.first && k <= w.last {
            let j = Double(k - w.first)
            let on = w.start + j * ScrambleDecodeHeadlineWindow.onset
            let off = w.start + w.hold + j * w.settle
            if e >= on && e < off { return LetterState(scrambled: true, step: step, settledAgo: 0) }
            if e >= off { settledAgo = min(settledAgo, e - off) }
        }
        return LetterState(scrambled: false, step: step, settledAgo: settledAgo)
    }

    /// The seeded glyph letter k shows at a flicker step.
    static func glyph(_ k: Int, step: Int) -> Character {
        charset[Int(ScrambleDecodeHeadlineMath.hash(k, step) * Double(charset.count))]
    }

    // MARK: Touch

    private func takeOver() {
        resume?.cancel()
        autoStart = nil
    }

    private func scheduleResume() {
        resume?.cancel()
        guard autoPlay, !reduceMotion else { return }
        resume = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled else { return }
            tapAt = nil
            touched = [:]
            autoStart = .now
        }
    }

    private func drag(layout: ScrambleDecodeHeadlineLayout) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                guard !reduceMotion else { return }
                if !dragging {
                    dragging = true
                    moved = false
                    takeOver()
                }
                if !moved, hypot(value.translation.width, value.translation.height) > 6 {
                    moved = true
                    haptic.impactOccurred(intensity: 0.5)
                }
                finger = value.location
                if moved {
                    let now = Date.now
                    for (k, slot) in layout.slots.enumerated()
                    where hypot(slot.center.x - value.location.x, slot.center.y - value.location.y) < Self.scrubRadius {
                        touched[k] = now
                    }
                }
            }
            .onEnded { _ in
                guard !reduceMotion else { return }
                dragging = false
                if !moved {
                    tapAt = .now
                    haptic.impactOccurred(intensity: 0.8)
                }
                finger = nil
                scheduleResume()
            }
    }

    // MARK: Drawing

    private func headline(layout: ScrambleDecodeHeadlineLayout, size: CGSize, date: Date) -> some View {
        let states = layout.slots.enumerated().map { letter($0.offset, slot: $0.element, at: date) }
        let activity = Double(states.filter(\.scrambled).count) / Double(max(1, states.count))
        return Canvas { ctx, _ in
            // The glow behind the headline breathes with how much of it is scrambled.
            let glowCenter = CGPoint(x: size.width * 0.3, y: layout.centerY)
            let r = size.width * 0.95
            ctx.fill(
                Path(CGRect(origin: .zero, size: size)),
                with: .radialGradient(
                    Gradient(colors: [Self.violet.opacity(0.10 + 0.22 * activity), Self.violet.opacity(0)]),
                    center: glowCenter, startRadius: 0, endRadius: r
                )
            )
            let font = ScrambleDecodeHeadlineFont.font(size: layout.fontSize)
            for (k, slot) in layout.slots.enumerated() {
                let s = states[k]
                if s.scrambled {
                    let g = Self.glyph(k, step: s.step)
                    // A glyph much wider than its letter's slot is shrunk towards 1.5 slot widths (to 72 % at most), so it hardly runs into its neighbours.
                    let advance = (Self.charsetAdvances[g] ?? 0.6) * layout.fontSize
                    let fit = max(0.72, min(1, slot.width * 1.5 / max(1, advance)))
                    var c = ctx
                    c.translateBy(x: slot.center.x, y: slot.center.y)
                    c.scaleBy(x: fit, y: fit)
                    c.draw(Text(String(g)).font(font).foregroundColor(Self.violet), at: .zero, anchor: .center)
                } else {
                    let cool = min(1, s.settledAgo / Self.cool)
                    let color = cool >= 1 ? Self.ink : ScrambleDecodeHeadlineMath.mix(Self.violet, Self.ink, cool)
                    ctx.draw(Text(String(slot.char)).font(font).foregroundColor(color), at: slot.center, anchor: .center)
                }
            }
        }
        .frame(width: size.width, height: size.height)
        .accessibilityElement()
        .accessibilityLabel(lines.joined(separator: " "))
        .accessibilityAddTraits(.isHeader)
    }

    private func chrome(layout: ScrambleDecodeHeadlineLayout, size: CGSize, insets: EdgeInsets) -> some View {
        ZStack(alignment: .topLeading) {
            // Badge above the headline.
            HStack(spacing: 8) {
                Circle().fill(Self.violet).frame(width: 6, height: 6)
                Text(badge)
                    .font(ScrambleDecodeHeadlineFont.font(size: 13))
                    .foregroundStyle(Self.ink.opacity(0.75))
            }
            .padding(.horizontal, 12)
            .frame(height: 28)
            .background(Capsule().fill(Self.ink.opacity(0.06)))
            .overlay(Capsule().strokeBorder(Self.ink.opacity(0.10), lineWidth: 1))
            .offset(x: 24, y: layout.top - 28 - 24)

            Text(subtitle)
                .font(ScrambleDecodeHeadlineFont.font(size: 17))
                .lineSpacing(4)
                .foregroundStyle(Self.ink.opacity(0.55))
                .frame(width: min(size.width - 48, 320), alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
                .offset(x: 24, y: layout.bottom + 24)

            Button {
                onAction?()
            } label: {
                HStack(spacing: 10) {
                    Text(action)
                    Image(systemName: "arrow.right")
                        .font(.system(size: 15, weight: .semibold))
                }
                .font(ScrambleDecodeHeadlineFont.font(size: 17))
                .foregroundStyle(Self.background)
                .frame(width: size.width - 48, height: 56)
                .background(Capsule().fill(Self.ink))
            }
            .buttonStyle(.plain)
            .offset(x: 24, y: size.height - max(insets.bottom, 20) - 16 - 56)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }
}

// MARK: - Font and layout

enum ScrambleDecodeHeadlineFont {
    static let name = "SpaceGrotesk-Medium"
    static let registered: Void = {
        if let url = Bundle.main.url(forResource: "ScrambleDecodeHeadline-SpaceGrotesk-Medium", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    static func font(size: CGFloat) -> Font {
        _ = registered
        return .custom(name, fixedSize: size)
    }

    /// Advances of a string's characters at 1 pt (no kerning, as in every stack).
    static func advances(_ text: String) -> [CGFloat] {
        _ = registered
        let font = CTFontCreateWithName(name as CFString, 1, nil)
        var chars = Array(text.utf16)
        var glyphs = [CGGlyph](repeating: 0, count: chars.count)
        CTFontGetGlyphsForCharacters(font, &chars, &glyphs, chars.count)
        var adv = [CGSize](repeating: .zero, count: glyphs.count)
        CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, &adv, glyphs.count)
        return adv.map(\.width)
    }
}

/// Where every letter of the headline sits: the font size that fits the longest line, and each
/// letter's slot (spaces take room but have no slot).
struct ScrambleDecodeHeadlineLayout {
    struct Slot {
        var char: Character
        var center: CGPoint
        var width: CGFloat
    }

    static let maxSize: CGFloat = 76
    static let tracking: CGFloat = -0.03 // em
    static let leading: CGFloat = 1.0 // line height in em

    let fontSize: CGFloat
    let slots: [Slot]
    let top: CGFloat
    let bottom: CGFloat
    let centerY: CGFloat

    init(lines: [String], width: CGFloat, height: CGFloat) {
        let advances = lines.map { ScrambleDecodeHeadlineFont.advances($0) }
        let unitWidths = advances.map { a in a.reduce(0, +) + Self.tracking * CGFloat(max(0, a.count - 1)) }
        let size = min(Self.maxSize, (width - 48) / (unitWidths.max() ?? 1))
        let lineHeight = size * Self.leading
        let blockHeight = lineHeight * CGFloat(lines.count)
        let centerY = height * 0.43
        let top = centerY - blockHeight / 2
        var slots: [Slot] = []
        for (li, line) in lines.enumerated() {
            var x: CGFloat = 24
            let y = top + lineHeight * (CGFloat(li) + 0.5)
            for (ci, ch) in line.enumerated() {
                let w = advances[li][ci] * size
                if ch != " " { slots.append(Slot(char: ch, center: CGPoint(x: x + w / 2, y: y), width: w)) }
                x += w + Self.tracking * size
            }
        }
        fontSize = size
        self.slots = slots
        self.top = top
        self.bottom = top + blockHeight
        self.centerY = centerY
    }
}

enum ScrambleDecodeHeadlineMath {
    /// A seeded number in 0..<1 (the same 32-bit integer hash in every stack).
    static func hash(_ i: Int, _ salt: Int) -> Double {
        var x = UInt32(truncatingIfNeeded: i &* 374_761_393 &+ salt &* 668_265_263)
        x = (x ^ (x >> 13)) &* 1_274_126_177
        x = x ^ (x >> 16)
        return Double(x) / 4_294_967_296
    }

    static func mix(_ a: Color, _ b: Color, _ t: Double) -> Color {
        let ca = UIColor(a).cgColor.components ?? [0, 0, 0, 1]
        let cb = UIColor(b).cgColor.components ?? [0, 0, 0, 1]
        return Color(red: ca[0] + (cb[0] - ca[0]) * t, green: ca[1] + (cb[1] - ca[1]) * t, blue: ca[2] + (cb[2] - ca[2]) * t)
    }
}
