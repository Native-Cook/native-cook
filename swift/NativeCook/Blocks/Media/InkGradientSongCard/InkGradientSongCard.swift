import CoreText
import SwiftUI
import UIKit

/*
 Ink Gradient Song Card — a "make your first song" sheet whose background is ink being stirred in
 water, over a dimmed music app.

 Look: behind, a dark music app ("Explore" in Instrument Serif 32 pt, a search button, a banner,
 four release cards drawn in code, a tab bar with a glowing red centre tab), dimmed by black 60 %.
 In front, a card (84 % of the width up to 340 pt, 62 % of the height up to 540 pt, corner 26,
 centred at 53 % of the height) filled with a Metal shader (InkGradientSongCard.metal): three
 clouds — burgundy #6B1020, signal red #E0251F, lilac #C98BE8 — from two warps of four-octave value
 noise, with thresholds nudged per 0.5 pt grain cell so every edge is sprayed and stippled, and a
 film grain on top. On it: a round close button, "Make your / first song" (Instrument Serif 40 pt,
 white, two 42 pt lines from 46 pt down), a two-line subtitle (14 pt, white 90 %; both texts carry a soft
 black shadow, 25 %, radius 8 and 6), three tilted
 prints (100 × 126, a 3 pt white border, corner 12, art drawn in code: a studio microphone under a
 lamp, a record on teal, a sun over water) and a pale pink footer (#F8D8DE, 60 pt) reading "Let's
 make music".

 Motion (loop = 8 s), all of it periodic in the loop:
   • the ink drifts on circles of the loop's phase (once and twice a loop);
   • it is stirred by a point travelling a figure eight across the card (x: 28 % of the width,
     cos(2πt/8); y: 20 % of the height, sin(4πt/8), around the middle), swirling the ink around it
     by up to 1.1 rad, fading over about a third of the card;
   • the red and lilac thresholds breathe once a loop (±0.05, ±0.04), so the colours trade places;
   • each print floats ±3 pt and ±1.2° on its own phase.
 Touch: drag on the card to stir the ink yourself: the stirring point follows the finger (eased in
 over 0.25 s) and swirls 2.4 rad, and the prints lean towards the finger by depth (0.03, 0.05, 0.07
 of its distance from the card's centre). 2 s after release the stir eases back to its figure eight
 over 0.8 s. The footer button dips to 97 % while pressed. Light haptic when a stir starts and on
 the button.
 Reduced motion: the ink, the stir and the prints are still; a drag does not stir.
 */

struct InkGradientSongCard: View {
    var autoPlay = false

    static let loop = 8.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var appear = Date.now
    /// A finger stirring the ink: where (card points), and when it came down; the release.
    @State private var stir: (point: CGPoint, at: Date)? = nil
    @State private var released: (point: CGPoint, at: Date, amount: Double)? = nil
    @State private var buttonDown = false
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    static let footer = Color(red: 0.973, green: 0.847, blue: 0.871)
    static let footerInk = Color(red: 0.227, green: 0.102, blue: 0.133)

    struct Layout {
        var size: CGSize
        var insets: EdgeInsets
        var card: CGRect

        init(size: CGSize, insets: EdgeInsets) {
            self.size = size
            self.insets = insets
            let w = min(size.width * 0.84, 340)
            let h = min(size.height * 0.62, 540)
            card = CGRect(x: (size.width - w) / 2, y: size.height * 0.53 - h / 2, width: w, height: h)
        }
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing,
                              height: proxy.size.height + insets.top + insets.bottom)
            let layout = Layout(size: size, insets: insets)
            TimelineView(.animation(paused: reduceMotion)) { timeline in
                let e = now(timeline.date)
                ZStack(alignment: .topLeading) {
                    InkGradientSongCardApp(size: size, insets: insets)
                        .overlay(Color.black.opacity(0.6))
                    card(e, date: timeline.date, layout: layout)
                        .offset(x: layout.card.minX, y: layout.card.minY)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .ignoresSafeArea()
        }
        .background(Color(red: 0.078, green: 0.067, blue: 0.078).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            _ = InkGradientSongCardFont.registered
            appear = .now
            haptic.prepare()
        }
    }

    // MARK: Time

    /// Loop time in seconds (0 under reduced motion).
    private func now(_ date: Date) -> Double {
        if reduceMotion { return 0 }
        let c = date.timeIntervalSince(appear)
        return c - floor(c / Self.loop) * Self.loop
    }

    /// The figure eight the ink is stirred along when no finger is on it.
    static func path(_ t: Double, card: CGSize) -> CGPoint {
        let a = 2 * Double.pi * t / loop
        return CGPoint(x: card.width * (0.5 + 0.28 * cos(a)), y: card.height * (0.45 + 0.2 * sin(2 * a)))
    }

    /// The stirring point, how hard it stirs, and how much a finger leads (for the prints).
    private func stirring(_ t: Double, date: Date, card: CGSize) -> (point: CGPoint, strength: Double, finger: Double) {
        let auto = Self.path(t, card: card)
        var w = 0.0
        var p = auto
        if let stir {
            w = easeOut(date.timeIntervalSince(stir.at) / 0.25)
            p = stir.point
        } else if let released {
            // Held where the finger left for 2 s, then back to the figure eight over 0.8 s.
            w = released.amount * (1 - easeInOut((date.timeIntervalSince(released.at) - 2) / 0.8))
            p = released.point
        }
        let point = CGPoint(x: auto.x + (p.x - auto.x) * w, y: auto.y + (p.y - auto.y) * w)
        return (point, 1.1 + 1.3 * w, w)
    }

    private func easeOut(_ x: Double) -> Double {
        let t = min(max(x, 0), 1)
        return 1 - pow(1 - t, 3)
    }

    private func easeInOut(_ x: Double) -> Double {
        let t = min(max(x, 0), 1)
        return t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
    }

    // MARK: The card

    private func card(_ t: Double, date: Date, layout: Layout) -> some View {
        let cs = layout.card.size
        let st = stirring(t, date: date, card: cs)
        let tau = 2 * Double.pi * t / Self.loop
        return ZStack(alignment: .topLeading) {
            Canvas { ctx, size in
                let shader = ShaderLibrary.inkGradientSongCard(
                    .float2(size), .float(0.5), .float(tau), .float2(st.point), .float(reduceMotion ? 1.1 : st.strength))
                ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .shader(shader, bounds: CGRect(origin: .zero, size: size)))
            }
            .frame(width: cs.width, height: cs.height)
            .accessibilityHidden(true)

            // Close.
            ZStack {
                Circle().fill(.white.opacity(0.2))
                Path { p in
                    p.move(to: CGPoint(x: 0, y: 0)); p.addLine(to: CGPoint(x: 10, y: 10))
                    p.move(to: CGPoint(x: 10, y: 0)); p.addLine(to: CGPoint(x: 0, y: 10))
                }
                .stroke(.white, style: StrokeStyle(lineWidth: 1.8, lineCap: .round))
                .frame(width: 10, height: 10)
            }
            .frame(width: 30, height: 30)
            .offset(x: cs.width - 45, y: 15)
            .accessibilityLabel("Close")

            VStack(spacing: 0) {
                Text("Make your").frame(height: 42)
                Text("first song").frame(height: 42)
            }
            .font(InkGradientSongCardFont.font(size: 40))
            .foregroundStyle(.white)
            .shadow(color: .black.opacity(0.25), radius: 8, y: 2)
            .frame(width: cs.width)
            .offset(y: 46)

            Text("Hum a tune, describe a mood, or add a photo to start a song.")
                .font(.system(size: 14))
                .foregroundStyle(.white.opacity(0.9))
                .shadow(color: .black.opacity(0.25), radius: 6, y: 1)
                .multilineTextAlignment(.center)
                .lineLimit(2)
                .frame(width: cs.width - 64, height: 40)
                .offset(x: 32, y: 142)

            prints(t, finger: st.finger, point: st.point, card: cs)

            // Footer button.
            Text("Let's make music")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Self.footerInk)
                .frame(width: cs.width, height: 60)
                .background(Self.footer)
                .scaleEffect(buttonDown ? 0.97 : 1)
                .animation(.easeOut(duration: 0.12), value: buttonDown)
                .offset(y: cs.height - 60)
                .accessibilityAddTraits(.isButton)
        }
        .frame(width: cs.width, height: cs.height, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
        .shadow(color: .black.opacity(0.45), radius: 30, y: 16)
        .contentShape(Rectangle())
        .gesture(touch(card: cs))
    }

    private func touch(card: CGSize) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let onButton = value.startLocation.y > card.height - 60
                if onButton {
                    if !buttonDown {
                        buttonDown = true
                        haptic.impactOccurred(intensity: 0.7)
                    }
                    return
                }
                guard !reduceMotion else { return }
                if stir == nil {
                    stir = (value.location, .now)
                    released = nil
                    haptic.impactOccurred(intensity: 0.5)
                } else {
                    stir?.point = value.location
                }
            }
            .onEnded { value in
                buttonDown = false
                guard let stir else { return }
                let amount = easeOut(Date.now.timeIntervalSince(stir.at) / 0.25)
                released = (value.location, .now, amount)
                self.stir = nil
            }
    }

    // MARK: Prints

    /// Back, left, right: offset from the stack's centre, tilt, float phase, depth for the lean.
    static let printSpots: [(dx: Double, dy: Double, rot: Double, phase: Double, depth: Double)] = [
        (6, -26, 4, 0, 0.03), (-64, 10, -12, 2.1, 0.05), (60, 20, 10, 4.2, 0.07),
    ]

    private func prints(_ t: Double, finger: Double, point: CGPoint, card: CGSize) -> some View {
        let center = CGPoint(x: card.width / 2, y: card.height - 60 - 128)
        let a = 2 * Double.pi * t / Self.loop
        return ZStack(alignment: .topLeading) {
            ForEach(0..<3, id: \.self) { i in
                let s = Self.printSpots[i]
                let float = reduceMotion ? 0 : sin(a + s.phase)
                let lean = CGSize(width: (point.x - card.width / 2) * s.depth * finger,
                                  height: (point.y - card.height / 2) * s.depth * finger)
                InkGradientSongCardPrint(kind: i)
                    .frame(width: 100, height: 126)
                    .rotationEffect(.degrees(s.rot + 1.2 * float + lean.width * 0.15))
                    .shadow(color: .black.opacity(0.3), radius: 10, y: 6)
                    .offset(x: center.x + s.dx - 50 + lean.width, y: center.y + s.dy - 63 + 3 * float + lean.height)
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

/// A print: a 3 pt white border around art drawn in code.
struct InkGradientSongCardPrint: View {
    var kind: Int

    var body: some View {
        Canvas { ctx, size in
            let outer = Path(roundedRect: CGRect(origin: .zero, size: size), cornerRadius: 12, style: .continuous)
            ctx.fill(outer, with: .color(.white))
            let r = CGRect(x: 3, y: 3, width: size.width - 6, height: size.height - 6)
            ctx.clip(to: Path(roundedRect: r, cornerRadius: 9, style: .continuous))
            ctx.translateBy(x: r.minX, y: r.minY)
            Self.art(&ctx, kind: kind, w: r.width, h: r.height)
        }
    }

    static func color(_ hex: UInt32) -> Color {
        Color(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255)
    }

    static func art(_ ctx: inout GraphicsContext, kind: Int, w: Double, h: Double) {
        let all = Path(CGRect(x: 0, y: 0, width: w, height: h))
        switch kind {
        case 0:
            // A studio microphone under a warm lamp.
            ctx.fill(all, with: .linearGradient(Gradient(colors: [color(0xF4C27A), color(0xB4562E)]),
                                                startPoint: .zero, endPoint: CGPoint(x: 0, y: h)))
            ctx.fill(all, with: .radialGradient(Gradient(colors: [.white.opacity(0.6), .white.opacity(0)]),
                                                center: CGPoint(x: 0.72 * w, y: 0.24 * h), startRadius: 0, endRadius: 0.4 * w))
            let ink = color(0x2A1A14)
            ctx.fill(Path(roundedRect: CGRect(x: 0.38 * w, y: 0.28 * h, width: 0.18 * w, height: 0.3 * h), cornerRadius: 0.09 * w), with: .color(ink))
            var stand = Path()
            stand.move(to: CGPoint(x: 0.47 * w, y: 0.58 * h))
            stand.addLine(to: CGPoint(x: 0.47 * w, y: 0.9 * h))
            ctx.stroke(stand, with: .color(ink), lineWidth: 3)
            ctx.fill(Path(ellipseIn: CGRect(x: 0.3 * w, y: 0.87 * h, width: 0.34 * w, height: 0.07 * h)), with: .color(ink))
        case 1:
            // A record on teal.
            ctx.fill(all, with: .linearGradient(Gradient(colors: [color(0x2E6F73), color(0x1D3F4A)]),
                                                startPoint: .zero, endPoint: CGPoint(x: w, y: h)))
            let c = CGPoint(x: 0.5 * w, y: 0.55 * h)
            func disc(_ r: Double) -> Path { Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)) }
            ctx.fill(disc(0.4 * w), with: .color(color(0x141414)))
            for k in [0.33, 0.27, 0.21] { ctx.stroke(disc(k * w), with: .color(.white.opacity(0.1)), lineWidth: 1) }
            ctx.fill(disc(0.13 * w), with: .color(color(0xE0251F)))
            ctx.fill(disc(0.025 * w), with: .color(color(0x1D3F4A)))
        default:
            // A sun over water.
            ctx.fill(all, with: .linearGradient(Gradient(colors: [color(0xFF9A8B), color(0x6B2C5C)]),
                                                startPoint: .zero, endPoint: CGPoint(x: 0, y: 0.64 * h)))
            ctx.fill(Path(ellipseIn: CGRect(x: 0.3 * w, y: 0.34 * h, width: 0.4 * w, height: 0.4 * w)), with: .color(color(0xFFE1A8)))
            ctx.fill(Path(CGRect(x: 0, y: 0.64 * h, width: w, height: 0.36 * h)), with: .color(color(0x2A0F22)))
            for (k, y) in [0.7, 0.77, 0.84].enumerated() {
                let half = (0.16 - 0.04 * Double(k)) * w
                ctx.fill(Path(roundedRect: CGRect(x: 0.5 * w - half, y: y * h, width: 2 * half, height: 2), cornerRadius: 1),
                         with: .color(color(0xFFE1A8).opacity(0.7)))
            }
        }
    }
}

/// The music app behind the sheet, drawn simply; it is dimmed and never moves.
struct InkGradientSongCardApp: View {
    var size: CGSize
    var insets: EdgeInsets

    static let releases: [(title: String, artist: String, from: UInt32, to: UInt32)] = [
        ("Neon Harbor", "Ivy Lane", 0xFF8A3D, 0x7B2CBF),
        ("Slow Bloom", "The Quiet Hours", 0x2EC4B6, 0x1B4DB1),
        ("Paper Moon", "Ada Rook", 0xF15BB5, 0x3A0CA3),
        ("Low Tide", "Marlow", 0xFEE440, 0xF3722C),
    ]

    var body: some View {
        let w = size.width
        let top = insets.top
        let colW = (w - 52) / 2
        Canvas { ctx, sz in
            ctx.fill(Path(CGRect(origin: .zero, size: sz)), with: .color(Color(red: 0.078, green: 0.067, blue: 0.078)))
            // Search button.
            ctx.fill(Path(ellipseIn: CGRect(x: w - 58, y: top + 10, width: 36, height: 36)), with: .color(.white.opacity(0.08)))
            ctx.stroke(Path(ellipseIn: CGRect(x: w - 47, y: top + 20, width: 11, height: 11)), with: .color(.white.opacity(0.85)), lineWidth: 1.8)
            var handle = Path()
            handle.move(to: CGPoint(x: w - 37.5, y: top + 30.5))
            handle.addLine(to: CGPoint(x: w - 33, y: top + 35))
            ctx.stroke(handle, with: .color(.white.opacity(0.85)), style: StrokeStyle(lineWidth: 1.8, lineCap: .round))
            // Banner.
            ctx.fill(Path(roundedRect: CGRect(x: 20, y: top + 64, width: w - 40, height: 48), cornerRadius: 14),
                     with: .color(Color(red: 0.165, green: 0.137, blue: 0.157)))
            // Releases.
            for (i, r) in Self.releases.enumerated() {
                let x = 20 + Double(i % 2) * (colW + 12)
                let y = top + 128 + Double(i / 2) * 292
                let rect = CGRect(x: x, y: y, width: colW, height: 220)
                ctx.fill(Path(roundedRect: rect, cornerRadius: 18), with: .linearGradient(
                    Gradient(colors: [InkGradientSongCardPrint.color(r.from), InkGradientSongCardPrint.color(r.to)]),
                    startPoint: rect.origin, endPoint: CGPoint(x: rect.maxX, y: rect.maxY)))
                ctx.draw(Text(r.title).font(.system(size: 15, weight: .semibold)).foregroundColor(.white),
                         at: CGPoint(x: x, y: y + 232), anchor: .topLeading)
                ctx.draw(Text(r.artist).font(.system(size: 13)).foregroundColor(.white.opacity(0.55)),
                         at: CGPoint(x: x, y: y + 252), anchor: .topLeading)
            }
            // Tab bar with a glowing red centre tab.
            let barY = sz.height - max(insets.bottom, 12) - 56
            ctx.fill(Path(CGRect(x: 0, y: barY, width: w, height: sz.height - barY)), with: .color(Color(red: 0.055, green: 0.047, blue: 0.055)))
            for k in 0..<5 where k != 2 {
                let x = w * (0.1 + 0.2 * Double(k))
                ctx.fill(Path(roundedRect: CGRect(x: x - 9, y: barY + 19, width: 18, height: 18), cornerRadius: 5),
                         with: .color(.white.opacity(0.35)))
            }
            let mid = CGPoint(x: w / 2, y: barY + 28)
            ctx.drawLayer { g in
                g.addFilter(.blur(radius: 14))
                g.fill(Path(ellipseIn: CGRect(x: mid.x - 26, y: mid.y - 26, width: 52, height: 52)),
                       with: .color(Color(red: 0.878, green: 0.145, blue: 0.122).opacity(0.8)))
            }
            ctx.fill(Path(ellipseIn: CGRect(x: mid.x - 22, y: mid.y - 22, width: 44, height: 44)),
                     with: .color(Color(red: 0.878, green: 0.145, blue: 0.122)))
        }
        .frame(width: size.width, height: size.height)
        .overlay(alignment: .topLeading) {
            ZStack(alignment: .topLeading) {
                Text("Explore")
                    .font(InkGradientSongCardFont.font(size: 32))
                    .foregroundStyle(.white)
                    .frame(height: 40)
                    .offset(x: 20, y: top + 8)
                Text("New: remix any track with your voice")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(.white.opacity(0.8))
                    .lineLimit(1)
                    .frame(width: w - 72, height: 48, alignment: .leading)
                    .offset(x: 36, y: top + 64)
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

enum InkGradientSongCardFont {
    static let name = "InstrumentSerif-Regular"
    static let registered: Void = {
        if let url = Bundle.main.url(forResource: "InkGradientSongCard-InstrumentSerif-Regular", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    static func font(size: CGFloat) -> Font {
        _ = registered
        return .custom(name, fixedSize: size)
    }
}
