import SwiftUI
import UIKit

/*
 Glow Search Splash — the launch of an invented visual-search app, Trove.
 Everything is laid out in a 393-wide design space; the field sits at 44 % of the height (FY).

 Loop of 12 s, derived from the loop time only:
  0.00–0.60  rest: the white Trove tag mark (38 × 50, top corners cut, a punched hole), centred on FY.
  0.60–0.75  the mark swells 7 %.
  0.75–1.10  it squeezes into a 2.6 × 22 caret (ease in-out cubic).
  1.10–2.00  the caret blinks once (off 1.40–1.70).
  2.00–2.45  the caret spreads into a 296 × 54 pill (ease out cubic) while it slides to the text start; the pill
             is only a faint glass hint with a gradient rim while the glow is on (white 1 % at rest, 4.5 % at full glow, no rim at rest);
             a pink→violet streak flares across it (peak 2.18, gone by 2.9). The text start centres "Search anything".
  2.30–4.05  the prismatic glow (pink / violet / cyan fan to the right of the caret, plus a soft haze) follows the caret.
  2.40–3.00  "Search" types in (0.1 s a letter); 3.00–3.75 " anything" (0.083 s a letter).
  3.45–4.70  twelve art cards bloom out of the field: from 30 % of their offset, 40 % size, blurred and dim,
             to their place in two staggered columns (ease out cubic, staggered by distance from the field);
             4–5 cards show at a time and a black band keeps 110 pt above and below the field empty.
  3.45–10.2  the columns drift up, left 44 pt/s, right 30 pt/s.
  5.50 / 7.20 / 8.90  the word is erased (0.3 s), the next one types (0.045 s a letter) with a softer glow:
             illustration, typography, 3D shapes; from 0.25 s after each erase the cards flip (turn edge-on and back,
             0.4 s, ease in-out) to that category's artworks, top to bottom with a 0.4 s stagger.
  10.20–10.85  the cards fly up off the screen (ease in cubic) while the text erases (10.20–10.70).
  10.70–11.05  the pill folds back into the caret at the centre, with a fainter streak.
  11.10–11.50  the caret grows back into the mark with a small overshoot (ease out back).
  11.50–12.00  rest.
 Touch: drag scrubs the loop (8 s across the screen width); a tap jumps to the next category.
 2 s after the last touch the held frame cross-fades to rest over 0.6 s and the loop starts again.
 Reduced motion: the field with "Search anything" and its cards, still (t = 5.0).

 First draw: the card images are made once per app run (GlowSearchSplashArt.shared), and the loop holds its
 resting frame until they exist and every image, gradient and blend the loop uses has been drawn under that frame
 (GlowSearchSplashScene.warm), and two frames in a row have come quickly (GlowSearchSplashWarmUp, 1.5 s at most),
 so the first bloom and category flip do not stall while textures are uploaded. Time 0 is the first frame after
 that.
*/
struct GlowSearchSplash: View {
    static let loop = 12.0
    var autoPlay = false
    var previewTime: Double? = nil
    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.displayScale) private var displayScale
    /// When the loop's time 0 is: set by a replay; until then the warm-up decides it.
    @State private var epoch: Date?
    @State private var warmUp = GlowSearchSplashWarmUp()
    @State private var heldTime = 0.0
    @State private var dragStart = 0.0
    @State private var releasedAt: Date?
    @State private var touching = false
    @State private var art: GlowSearchSplashArt?

    static let restTime = 5.0
    private static let beats = [5.0, 6.6, 8.3, 10.0]

    /// The loop time to draw, and how far the frame has cross-faded back to rest.
    private func time(_ now: Date) -> (Double, Double) {
        if let previewTime { return (previewTime.truncatingRemainder(dividingBy: Self.loop), 0) }
        if reducedMotion { return (Self.restTime, 0) }
        if touching { return (heldTime, 0) }
        if let releasedAt {
            let elapsed = now.timeIntervalSince(releasedAt)
            if elapsed < 2 { return (heldTime, 0) }
            if elapsed < 2.6 { return (heldTime, GlowSearchSplashScene.ease((elapsed - 2) / 0.6)) }
            return (autoPlay ? (elapsed - 2.6).truncatingRemainder(dividingBy: Self.loop) : 0, 0)
        }
        guard autoPlay, let start = epoch ?? warmUp.start else { return (0, 0) }
        return (max(0, now.timeIntervalSince(start)).truncatingRemainder(dividingBy: Self.loop), 0)
    }

    var body: some View {
        GeometryReader { geometry in
            let scale = geometry.size.width / 393
            let height = geometry.size.height / max(scale, 0.01)
            TimelineView(.animation(paused: reducedMotion || previewTime != nil || scenePhase != .active || (!autoPlay && releasedAt == nil && !touching))) { clock in
                Canvas { context, size in
                    let warming = art != nil && epoch == nil && previewTime == nil && !reducedMotion && warmUp.warming(at: clock.date)
                    let (t, blend) = time(clock.date)
                    let scene = GlowSearchSplashScene(height: height, art: art)
                    if warming {
                        // Still on the resting frame: draw what the loop will draw, then cover it with the black ground.
                        var warm = context
                        warm.scaleBy(x: scale, y: scale)
                        scene.warm(warm)
                    }
                    context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(.black))
                    context.scaleBy(x: scale, y: scale)
                    if blend <= 0 {
                        scene.draw(context, time: t)
                    } else {
                        var held = context; held.opacity = 1 - blend
                        held.drawLayer { scene.draw($0, time: t) }
                        var rest = context; rest.opacity = blend
                        rest.drawLayer { scene.draw($0, time: 0) }
                    }
                }
            }
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0)
                .onChanged { value in
                    guard !reducedMotion, previewTime == nil else { return }
                    if !touching {
                        dragStart = time(Date()).0
                        heldTime = dragStart
                        touching = true
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    }
                    heldTime = min(Self.loop - 0.01, max(0, dragStart + value.translation.width / geometry.size.width * 8))
                }
                .onEnded { value in
                    guard !reducedMotion, previewTime == nil else { return }
                    if abs(value.translation.width) < 8 && abs(value.translation.height) < 8 {
                        heldTime = Self.beats.first { $0 > dragStart + 0.05 } ?? Self.beats[0]
                        UIImpactFeedbackGenerator(style: .soft).impactOccurred()
                    }
                    touching = false
                    releasedAt = Date()
                })
            .task(id: scale) { art = await GlowSearchSplashArt.shared(scale: max(1, min(3, scale * displayScale))) }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Trove. Search anything: illustration, typography, 3D shapes. Tap to skip ahead, drag to scrub.")
            .accessibilityAction(named: "Replay splash") { epoch = Date(); releasedAt = nil }
        }
        .ignoresSafeArea()
        .preferredColorScheme(.dark)
    }
}

/// Holds the loop on its resting frame while the images are drawn out of sight and uploaded, until two frames in a row
/// have come within 50 ms (nothing left to upload); the frame after that is the loop's time 0. A reference, so it changes
/// per frame without updating the view.
final class GlowSearchSplashWarmUp {
    private(set) var start: Date?
    private var first: CFTimeInterval?
    private var last: CFTimeInterval?
    private var frames = 0
    private var quick = 0

    /// Called once a frame before the loop has started. Returns whether this frame draws the images out of sight (the
    /// second to fourth do: the first shows the resting frame at once); the loop starts on the first frame after two
    /// in a row came within 50 ms, or after 1.5 s at most.
    func warming(at date: Date) -> Bool {
        if start != nil { return false }
        let now = CACurrentMediaTime()
        quick = last.map { now - $0 < 0.05 } == true ? quick + 1 : 0
        last = now
        if first == nil { first = now }
        frames += 1
        if frames > 4 && (quick >= 2 || now - (first ?? now) > 1.5) { start = date; return false }
        return frames >= 2 && frames <= 4
    }
}

/// Draws one frame from the loop time alone.
struct GlowSearchSplashScene {
    let height: Double
    let art: GlowSearchSplashArt?

    static let words = ["anything", "illustration", "typography", "3D shapes"]
    /// When each word after the first starts to be erased.
    static let erases = [5.5, 7.2, 8.9]
    static let letter = 0.045
    static let centerX = 196.5
    static let pillWidth = 296.0
    static let pillHeight = 54.0
    static let pink: UInt32 = 0xFF4FD8
    static let violet: UInt32 = 0xA855F7
    static let cyan: UInt32 = 0x22D3EE
    private static let font = UIFont.systemFont(ofSize: 17)

    var fieldY: Double { (height * 0.44).rounded() }

    static func ease(_ value: Double) -> Double { let u = min(1, max(0, value)); return u * u * (3 - 2 * u) }
    static func clamp(_ value: Double) -> Double { min(1, max(0, value)) }
    static func easeOut(_ value: Double) -> Double { let u = clamp(value); return 1 - pow(1 - u, 3) }
    static func easeInOut(_ value: Double) -> Double { let u = clamp(value); return u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2 }
    static func easeOutBack(_ value: Double) -> Double { let u = clamp(value) - 1; return 1 + 2.70158 * u * u * u + 1.70158 * u * u }
    static func lerp(_ a: Double, _ b: Double, _ u: Double) -> Double { a + (b - a) * u }
    static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: alpha)
    }
    private static func prefix(_ text: String, _ count: Int) -> String { String(text.prefix(max(0, count))) }

    /// The placeholder at time t, whether a letter is being typed or erased, and when the current pause began.
    static func typed(_ t: Double) -> (text: String, active: Bool, hold: Double) {
        if t < 2.4 { return ("", false, 0) }
        if t < 3.0 { return (prefix("Search", Int((t - 2.4) / 0.1) + 1), true, 0) }
        if t < 3.75 { return ("Search" + prefix(" anything", Int((t - 3.0) / (0.75 / 9)) + 1), true, 0) }
        var hold = 3.75
        for k in 1...3 {
            let e = erases[k - 1], previous = words[k - 1], next = words[k]
            if t < e { return ("Search " + previous, false, hold) }
            if t < e + 0.3 { return ("Search " + prefix(previous, previous.count - Int((t - e) / (0.3 / Double(previous.count))) - 1), true, 0) }
            let typing = letter * Double(next.count)
            if t < e + 0.3 + typing { return ("Search " + prefix(next, Int((t - e - 0.3) / letter) + 1), true, 0) }
            hold = e + 0.3 + typing
        }
        if t < 10.2 { return ("Search 3D shapes", false, hold) }
        if t < 10.7 { return (prefix("Search 3D shapes", 16 - Int((t - 10.2) / (0.5 / 16)) - 1), true, 0) }
        return ("", false, 0)
    }

    static func caretAlpha(_ t: Double, _ typing: (text: String, active: Bool, hold: Double)) -> Double {
        if t < 1.1 || t >= 11.1 { return 0 }
        if t < 2.0 { return 1 - (ease((t - 1.40) / 0.05) - ease((t - 1.70) / 0.05)) }
        if t < 2.4 || t >= 10.2 || typing.active || typing.hold == 0 { return 1 }
        let phase = (t - typing.hold).truncatingRemainder(dividingBy: 1.0)
        return phase < 0.5 ? 1 - ease((phase - 0.45) / 0.05) : ease((phase - 0.95) / 0.05)
    }

    /// 0 = caret, 1 = the open pill.
    static func pillOpen(_ t: Double) -> Double {
        if t < 2.0 || t >= 11.05 { return 0 }
        if t < 2.45 { return easeOut((t - 2.0) / 0.45) }
        if t < 10.7 { return 1 }
        return 1 - easeInOut((t - 10.7) / 0.35)
    }

    static func streak(_ t: Double) -> Double {
        ease((t - 2.0) / 0.18) * (1 - ease((t - 2.3) / 0.6)) + 0.8 * ease((t - 10.7) / 0.15) * (1 - ease((t - 10.9) / 0.3))
    }

    static func prism(_ t: Double) -> Double {
        var g = ease((t - 2.3) / 0.3) * (1 - ease((t - 3.6) / 0.45))
        for k in 1...3 {
            let start = erases[k - 1] + 0.3, typing = letter * Double(words[k].count)
            g += 0.55 * ease((t - start) / 0.15) * (1 - ease((t - start - typing) / 0.3))
        }
        return g
    }

    /// 0 = the page mark, 1 = the caret; scale is the swell before the squeeze.
    static func mark(_ t: Double) -> (morph: Double, scale: Double) {
        if t < 0.6 { return (0, 1) }
        if t < 0.75 { return (0, 1 + 0.07 * ease((t - 0.6) / 0.15)) }
        if t < 1.1 { let u = (t - 0.75) / 0.35; return (easeInOut(u), 1 + 0.07 * (1 - ease(u))) }
        if t < 11.1 { return (1, 1) }
        return (1 - easeOutBack((t - 11.1) / 0.4), 1)
    }

    /// The Trove tag: a rounded rectangle whose top corners are cut at 45° by `cut`, with a punched hole.
    /// Once `cut` is no larger than `r` it is a plain rounded rectangle (the caret).
    static func markPath(_ cx: Double, _ cy: Double, _ w: Double, _ h: Double, _ r: Double, _ cut: Double, _ hole: Double) -> Path {
        let x = cx - w / 2, y = cy - h / 2
        var p = Path()
        if cut > r {
            p.move(to: CGPoint(x: x + cut, y: y))
            p.addLine(to: CGPoint(x: x + w - cut, y: y))
            p.addLine(to: CGPoint(x: x + w, y: y + cut))
        } else {
            p.move(to: CGPoint(x: x + r, y: y))
            p.addLine(to: CGPoint(x: x + w - r, y: y))
            p.addQuadCurve(to: CGPoint(x: x + w, y: y + r), control: CGPoint(x: x + w, y: y))
        }
        p.addLine(to: CGPoint(x: x + w, y: y + h - r))
        p.addQuadCurve(to: CGPoint(x: x + w - r, y: y + h), control: CGPoint(x: x + w, y: y + h))
        p.addLine(to: CGPoint(x: x + r, y: y + h))
        p.addQuadCurve(to: CGPoint(x: x, y: y + h - r), control: CGPoint(x: x, y: y + h))
        if cut > r {
            p.addLine(to: CGPoint(x: x, y: y + cut))
        } else {
            p.addLine(to: CGPoint(x: x, y: y + r))
            p.addQuadCurve(to: CGPoint(x: x + r, y: y), control: CGPoint(x: x, y: y))
        }
        p.closeSubpath()
        if hole > 0.2 { p.addEllipse(in: CGRect(x: cx - hole, y: y + 12 - hole, width: 2 * hole, height: 2 * hole)) }
        return p
    }

    /// The text starts where "Search anything" is centred, like the pin; longer words run on to the right.
    static let textX = centerX - textWidth("Search anything") / 2

    static func textWidth(_ text: String) -> Double {
        text.isEmpty ? 0 : Double(NSAttributedString(string: text, attributes: [.font: font]).size().width)
    }

    private func radial(_ c: GraphicsContext, _ x: Double, _ y: Double, _ r: Double, _ hex: UInt32, _ alpha: Double) {
        guard alpha > 0.002 else { return }
        c.fill(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r)),
               with: .radialGradient(Gradient(colors: [Self.color(hex, alpha), Self.color(hex, 0)]), center: CGPoint(x: x, y: y), startRadius: 0, endRadius: r))
    }

    func draw(_ context: GraphicsContext, time t: Double) {
        let fy = fieldY
        drawCards(context, t)
        // A black band keeps the field clear of the cards (it comes in as they bloom); a scrim keeps the status bar legible.
        var band = context
        band.opacity = Self.ease((t - 3.7) / 0.7)
        band.fill(Path(CGRect(x: -20, y: fy - 170, width: 433, height: 340)), with: .linearGradient(
            Gradient(stops: [.init(color: .black.opacity(0), location: 0), .init(color: .black, location: 0.18), .init(color: .black, location: 0.82), .init(color: .black.opacity(0), location: 1)]),
            startPoint: CGPoint(x: 0, y: fy - 170), endPoint: CGPoint(x: 0, y: fy + 170)))
        context.fill(Path(CGRect(x: -20, y: -20, width: 433, height: 110)), with: .linearGradient(
            Gradient(colors: [.black.opacity(0.75), .black.opacity(0)]), startPoint: CGPoint(x: 0, y: 0), endPoint: CGPoint(x: 0, y: 90)))

        let typing = Self.typed(t)
        let open = Self.pillOpen(t), streak = Self.streak(t), prism = Self.prism(t)
        let typedWidth = Self.textWidth(typing.text)
        let caretX = Self.lerp(Self.centerX, Self.textX + typedWidth + 2.5, open)

        // Haze and pill glow.
        radial(context, caretX + 60, fy - 70, 240, 0xFFFFFF, 0.10 * prism)
        radial(context, caretX + 30, fy, 120, Self.pink, 0.12 * prism)
        radial(context, Self.centerX - 70, fy, 150, Self.pink, 0.22 * streak)
        radial(context, Self.centerX + 70, fy, 150, Self.violet, 0.22 * streak)

        // The field: the caret spreads into a pill that is only a faint glass hint while the glow is on, and nearly invisible at rest.
        if open > 0 {
            let w = Self.lerp(2.6, Self.pillWidth, open), h = Self.lerp(22, Self.pillHeight, open)
            let pill = Path(roundedRect: CGRect(x: Self.centerX - w / 2, y: fy - h / 2, width: w, height: h), cornerRadius: h / 2)
            let glow = min(1, streak + prism)
            context.fill(pill, with: .color(.white.opacity(Self.lerp(1, 0.01 + 0.035 * glow, Self.ease(open / 0.6)))))
            var rim = context
            rim.opacity = min(1, 0.7 * streak + 0.3 * prism) * open
            rim.stroke(pill, with: .linearGradient(Gradient(colors: [Self.color(Self.pink), Self.color(Self.violet), Self.color(Self.cyan)]),
                                                   startPoint: CGPoint(x: Self.centerX - w / 2, y: fy), endPoint: CGPoint(x: Self.centerX + w / 2, y: fy)),
                       lineWidth: 1.2)
        }

        // The streak flaring across the field.
        if streak > 0.002 {
            var flare = context
            flare.translateBy(x: Self.centerX, y: fy)
            flare.scaleBy(x: 1, y: 0.09)
            flare.fill(Path(ellipseIn: CGRect(x: -170, y: -170, width: 340, height: 340)), with: .radialGradient(
                Gradient(colors: [Self.color(Self.pink, 0.9 * streak), Self.color(Self.violet, 0.5 * streak), Self.color(Self.violet, 0)]),
                center: .zero, startRadius: 0, endRadius: 170))
            let half = Self.pillWidth / 2 * max(open, 0.25)
            context.fill(Path(CGRect(x: Self.centerX - half, y: fy - 0.75, width: 2 * half, height: 1.5)), with: .linearGradient(
                Gradient(colors: [Self.color(Self.pink, 0), Self.color(Self.pink, streak), .white.opacity(streak), Self.color(Self.violet, streak), Self.color(Self.violet, 0)]),
                startPoint: CGPoint(x: Self.centerX - half, y: fy), endPoint: CGPoint(x: Self.centerX + half, y: fy)))
        }

        // The placeholder.
        if !typing.text.isEmpty {
            context.draw(Text(typing.text).font(.system(size: 17)).foregroundColor(.white.opacity(0.92)), at: CGPoint(x: Self.textX, y: fy), anchor: .leading)
        }

        // The prismatic fan to the right of the caret.
        if prism > 0.002 {
            var fan = context
            fan.opacity = min(1, prism) * 0.95
            fan.drawLayer { layer in
                var quad = Path()
                quad.move(to: CGPoint(x: caretX + 2, y: fy - 12)); quad.addLine(to: CGPoint(x: caretX + 34, y: fy - 24))
                quad.addLine(to: CGPoint(x: caretX + 34, y: fy + 20)); quad.addLine(to: CGPoint(x: caretX + 2, y: fy + 12)); quad.closeSubpath()
                layer.fill(quad, with: .linearGradient(Gradient(colors: [Self.color(Self.pink), Self.color(Self.violet), Self.color(Self.cyan)]),
                                                       startPoint: CGPoint(x: 0, y: fy - 24), endPoint: CGPoint(x: 0, y: fy + 20)))
                layer.blendMode = .destinationIn
                layer.fill(Path(CGRect(x: caretX, y: fy - 30, width: 40, height: 60)), with: .linearGradient(Gradient(colors: [.white, .white.opacity(0)]),
                                                                                                      startPoint: CGPoint(x: caretX + 2, y: 0), endPoint: CGPoint(x: caretX + 34, y: 0)))
            }
            radial(context, caretX + 12, fy - 8, 26, Self.pink, 0.5 * prism)
            radial(context, caretX + 12, fy + 10, 18, Self.cyan, 0.35 * prism)
            radial(context, caretX, fy, 14, 0xFFFFFF, 0.35 * prism)
        }

        // The caret, or the mark it comes from.
        let mark = Self.mark(t)
        if t < 1.1 || t >= 11.1 {
            let m = mark.morph, s = mark.scale
            let w = Self.lerp(38, 2.6, m) * s, h = Self.lerp(50, 22, m) * s, r = Self.lerp(8, 1.3, m) * s
            let cut = min(13, max(0, 12 * (1 - m * 1.7))) * s
            let hole = 4.2 * Self.clamp(1 - m * 2) * s
            context.fill(Self.markPath(Self.centerX, fy, w, h, r, cut, hole), with: .color(.white), style: FillStyle(eoFill: true))
        } else {
            let alpha = Self.caretAlpha(t, typing)
            if alpha > 0 {
                context.fill(Path(roundedRect: CGRect(x: caretX - 1.3, y: fy - 11, width: 2.6, height: 22), cornerRadius: 1.3), with: .color(.white.opacity(alpha)))
            }
        }
    }

    /// Draws every card image at its size and the scene at the moments that first use each gradient, blend and
    /// text, so their textures and pipelines exist before the loop needs them. The caller covers it all with the
    /// opaque ground, so nothing of it shows.
    func warm(_ context: GraphicsContext) {
        guard let art else { return }
        for (index, card) in GlowSearchSplashCard.all.enumerated() {
            let rect = CGRect(x: 20, y: 120, width: card.width, height: card.height)
            var clipped = context
            clipped.clip(to: Path(roundedRect: rect, cornerSize: CGSize(width: GlowSearchSplashCard.radius, height: GlowSearchSplashCard.radius)))
            clipped.draw(art.soft[index], in: rect)
            for images in art.sharp { context.draw(images[index], in: rect) }
            var faded = context
            faded.opacity = 0.5
            faded.draw(art.sharp[0][index], in: rect)
        }
        for t in [2.1, 2.5, 3.9, 4.6, 5.9, 6.3, 8.0, 9.7, 10.4, 10.8, 11.3] { draw(context, time: t) }
    }

    private func drawCards(_ context: GraphicsContext, _ t: Double) {
        guard let art, t >= 3.45, t < 10.95 else { return }
        let fy = fieldY, flow = t - 3.45
        for (index, card) in GlowSearchSplashCard.all.enumerated() {
            let y = card.baseY - GlowSearchSplashCard.speeds[card.column] * flow
            let cx = card.x + card.width / 2, cy = y + card.height / 2
            // Bloom out of the field.
            let delay = min(1, abs(cy - fy) / 500) * 0.45 + Double(card.column) * 0.04
            let p = Self.clamp((t - 3.45 - delay) / 0.8)
            if p <= 0 { continue }
            let grow = Self.easeOut(p)
            let reach = Self.lerp(0.3, 1, grow)
            var x = Self.centerX + (cx - Self.centerX) * reach
            var yy = fy + (cy - fy) * reach
            let size = Self.lerp(0.4, 1, grow)
            var alpha = Self.ease(p * 1.4)
            let sharp = Self.ease((p - 0.3) / 0.6)
            // Fly away at the end.
            let exitDelay = Double(card.column) * 0.05 + Self.clamp(cy / height) * 0.1
            let away = Self.clamp((t - 10.2 - exitDelay) / 0.5)
            yy -= 1100 * away * away * away
            alpha *= 1 - Self.ease((t - 10.55 - exitDelay) / 0.2)
            // Category swaps, top to bottom.
            let swapDelay = Self.clamp(cy / height) * 0.4 + Double(card.column) * 0.05
            var from = 0, swap = 0.0
            for k in 1...3 {
                let u = Self.clamp((t - Self.erases[k - 1] - 0.25 - swapDelay) / 0.4)
                if u >= 1 { from = k } else { swap = Self.ease(u); break }
            }
            // The card turns edge-on and back, and shows the next category's artwork once past edge-on.
            let flip = abs(cos(.pi * swap))
            let shown = swap < 0.5 ? from : from + 1
            alpha *= 0.55 + 0.45 * flip
            let w = card.width * size * flip, h = card.height * size
            x -= w / 2; yy -= h / 2
            if yy > height + 20 || yy + h < -20 || alpha <= 0.002 || w < 0.5 { continue }
            let rect = CGRect(x: x, y: yy, width: w, height: h)
            var c = context
            c.opacity = alpha
            if sharp < 1 && shown == 0 {
                var soft = c
                soft.clip(to: Path(roundedRect: rect, cornerSize: CGSize(width: min(w / 2, GlowSearchSplashCard.radius * size * flip), height: GlowSearchSplashCard.radius * size)))
                soft.draw(art.soft[index], in: rect)
                c.opacity = alpha * sharp
            }
            c.draw(art.sharp[shown][index], in: rect)
        }
    }
}
