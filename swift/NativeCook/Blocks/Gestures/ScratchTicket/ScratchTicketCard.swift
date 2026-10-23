import SwiftUI
import UIKit

/// Everything drawn on the ticket that does not move: its outline with the tear-off perforation,
/// the barcode on the stub and the sunflower mark.
enum ScratchTicketArt {
    /// The ticket's outline: a rounded card with a notch at each end of the perforation and a row
    /// of small punched holes between them, cut out so the background shows through.
    static func ticketShape(width: Double, height: Double, perfY: Double, radius: Double) -> Path {
        let card = Path(roundedRect: CGRect(x: 0, y: 0, width: width, height: height), cornerRadius: radius, style: .circular)
        var cuts = Path()
        let notch = 13.0
        cuts.addEllipse(in: CGRect(x: -notch, y: perfY - notch, width: notch * 2, height: notch * 2))
        cuts.addEllipse(in: CGRect(x: width - notch, y: perfY - notch, width: notch * 2, height: notch * 2))
        let hole = 2.3
        let step = 10.0
        let from = notch + 12
        let count = Int(floor((width - from * 2) / step))
        let start = (width - Double(count) * step) / 2
        // `0...count` traps when the ticket is narrower than the notches (a zero first layout).
        if count >= 0 {
            for k in 0...count {
                let x = start + Double(k) * step
                cuts.addEllipse(in: CGRect(x: x - hole, y: perfY - hole, width: hole * 2, height: hole * 2))
            }
        }
        return card.subtracting(cuts)
    }

    /// A barcode of bars of 1–3 units, fixed by its seed so every ticket prints the same one.
    static func barcode(width: Double, height: Double, seed: Int) -> Path {
        var t = seed
        func rand() -> Double {
            t = (t * 9301 + 49297) % 233_280
            return Double(t) / 233_280
        }
        var path = Path()
        var x = 0.0
        while x < width {
            let bar = 1 + floor(rand() * 3)
            let gap = 1 + floor(rand() * 2.4)
            if x + bar > width { break }
            path.addRect(CGRect(x: x, y: 0, width: bar, height: height))
            x += bar + gap
        }
        return path
    }

    /// A sunflower mark: petals around a seed head, centred on (0, 0) with radius `r`.
    static func sunflower(_ r: Double) -> (petals: Path, head: Path, seeds: Path) {
        let count = 14
        var petals = Path()
        let inner = r * 0.36
        let half = r * 0.17
        for k in 0..<count {
            let a = Double(k) / Double(count) * tau
            let c = cos(a), s = sin(a)
            // A point (u along the petal, v across it) turned onto the petal's angle.
            func at(_ u: Double, _ v: Double) -> CGPoint { CGPoint(x: u * c - v * s, y: u * s + v * c) }
            petals.move(to: at(inner, 0))
            petals.addCurve(to: at(r, 0), control1: at(inner + (r - inner) * 0.35, half), control2: at(r - (r - inner) * 0.1, half * 0.55))
            petals.addCurve(to: at(inner, 0), control1: at(r - (r - inner) * 0.1, -half * 0.55), control2: at(inner + (r - inner) * 0.35, -half))
            petals.closeSubpath()
        }
        let head = Path(ellipseIn: CGRect(x: -r * 0.4, y: -r * 0.4, width: r * 0.8, height: r * 0.8))
        var seeds = Path()
        // Seeds on a golden-angle spiral, the way a real seed head packs them.
        for k in 0..<22 {
            let a = Double(k) * 2.39996
            let d = sqrt(Double(k) / 22) * r * 0.32
            let sr = r * 0.035
            seeds.addEllipse(in: CGRect(x: cos(a) * d - sr, y: sin(a) * d - sr, width: sr * 2, height: sr * 2))
        }
        return (petals, head, seeds)
    }
}

/// Downloads a prize photo once and keeps it decoded.
@MainActor
final class ScratchTicketPhotos {
    private(set) var images: [Int: UIImage] = [:]
    private var started = false

    func load(_ prizes: [ScratchTicketPrize]) async {
        guard !started else { return }
        started = true
        await withTaskGroup(of: (Int, UIImage?).self) { group in
            for (k, prize) in prizes.enumerated() {
                group.addTask {
                    guard let url = prize.url,
                          let (data, _) = try? await URLSession.shared.data(from: url),
                          let image = UIImage(data: data)
                    else { return (k, nil) }
                    return (k, await image.byPreparingForDisplay() ?? image)
                }
            }
            for await (k, image) in group {
                if let image { images[k] = image }
            }
        }
    }
}

/// One lottery ticket: cream card stock with a tear-off stub, a holographic foil window over the
/// prize photo, and the printed type laid over the canvas. Its foil, scratches and motion come from
/// its slot, so the parent can drive it with a finger or the autoPlay script alike.
struct ScratchTicketCard: View {
    let slot: ScratchTicketSlot
    let prize: ScratchTicketPrize
    let photo: UIImage?
    let geo: ScratchTicketGeometry
    let phase: Double
    let tilt: CGPoint
    let now: Double
    let reducedMotion: Bool

    private typealias M = ScratchTicketMetrics
    static let paper = Color(hex: 0xf4ecd9)
    private static let ink = Color(hex: 0x1f2a22)
    private static let muted = Color(hex: 0x857c69)
    private static let label = "SCRATCH  TO  REVEAL"
    private static let labelFont = UIFont(name: "AvenirNextCondensed-Bold", size: 13) ?? .systemFont(ofSize: 13, weight: .bold)
    private static let barcode = ScratchTicketArt.barcode(width: M.barWidth, height: M.barHeight, seed: 11)
    private static let mark = ScratchTicketArt.sunflower(11)
    private static let emboss = ScratchTicketArt.sunflower(24)

    var body: some View {
        let w = geo.ticket.width
        let h = geo.ticket.height
        ZStack(alignment: .topLeading) {
            Canvas { ctx, _ in
                var c = ctx
                c.translateBy(x: M.bleed, y: M.bleed)
                draw(in: c)
            }
            .frame(width: w + M.bleed * 2, height: h + M.bleed * 2)
            .offset(x: -M.bleed, y: -M.bleed)

            type
        }
        .frame(width: w, height: h, alignment: .topLeading)
        .scaleEffect(1 + slot.pop.value * 0.035)
        .rotationEffect(.degrees(slot.offset.value / geo.width * 9))
        .offset(x: slot.offset.value)
        .opacity(slot.shown.value)
        .allowsHitTesting(false)
    }

    // MARK: Type

    private func caps(_ text: String) -> some View {
        Text(text)
            .font(.custom("AvenirNext-Bold", fixedSize: 10))
            .kerning(1.8)
            .foregroundStyle(Self.muted)
    }

    @ViewBuilder
    private var type: some View {
        let w = geo.ticket.width
        HStack {
            caps("FIELD DAY LOTTERY")
            Spacer()
            caps("Nº 07").padding(.trailing, 28)
        }
        .frame(width: w - 44)
        .offset(x: 22, y: 22)

        Text("Lucky Bloom")
            .font(.custom("AvenirNextCondensed-Heavy", fixedSize: 42))
            .kerning(-0.4)
            .foregroundStyle(Self.ink)
            .fixedSize()
            .frame(height: 46)
            .offset(x: 21, y: 38)

        Text("Scratch the foil. Every ticket grows something.")
            .font(.custom("AvenirNext-Medium", fixedSize: 13))
            .foregroundStyle(Self.muted)
            .frame(width: w - 44, height: 17, alignment: .leading)
            .offset(x: 22, y: 86)

        VStack(alignment: .leading, spacing: 5) {
            caps("YOUR PRIZE")
            ZStack(alignment: .topLeading) { prizeLines }
                .frame(width: w - 154, height: 40, alignment: .topLeading)
        }
        .offset(x: 24, y: geo.perfY + 26)

        Text("07 · 0427 1186")
            .font(.custom("Menlo-Regular", fixedSize: 8.5))
            .kerning(0.6)
            .foregroundStyle(Self.ink)
            .frame(width: M.barWidth)
            .offset(x: w - 22 - M.barWidth, y: geo.perfY + 30 + M.barHeight + 6)
    }

    /// The stub's line: "Hidden under the foil" until the ticket is revealed, which fades it out
    /// in 120 ms while the prize springs up into place.
    @ViewBuilder
    private var prizeLines: some View {
        let since = slot.revealedAt.map { now - $0 }
        if let since, since < 0.12 {
            line("Hidden under the foil", "Every ticket wins", prizeColor: Self.ink.opacity(0.28))
                .opacity(1 - since / 0.12)
        } else if since == nil {
            line("Hidden under the foil", "Every ticket wins", prizeColor: Self.ink.opacity(0.28))
        }
        if let since {
            let entrance = Self.entrance(since, reducedMotion: reducedMotion)
            line(prize.prize, prize.detail, prizeColor: Self.ink)
                .opacity(entrance.opacity)
                .offset(y: entrance.rise)
        }
    }

    /// Reanimated's FadeInDown sprung (stiffness 100, damping 14, mass 0.7): up 25 pt into place
    /// while fading in; with reduced motion a 300 ms fade.
    private static func entrance(_ t: Double, reducedMotion: Bool) -> (opacity: Double, rise: Double) {
        if reducedMotion { return (M.inOutQuad(min(1, t / 0.3)), 0) }
        let k = 100.0, c = 14.0, m = 0.7
        let w0 = sqrt(k / m)
        let zeta = c / (2 * sqrt(k * m))
        let wd = w0 * sqrt(1 - zeta * zeta)
        // Share of the way still to go: 1 at the start, 0 at rest.
        let left = exp(-zeta * w0 * t) * (cos(wd * t) + zeta * w0 / wd * sin(wd * t))
        return (min(1, max(0, 1 - left)), 25 * left)
    }

    private func line(_ prizeText: String, _ detail: String, prizeColor: Color) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(prizeText)
                .font(.custom("AvenirNext-Bold", fixedSize: 17))
                .kerning(-0.2)
                .foregroundStyle(prizeColor)
                .lineLimit(1)
                .frame(height: 21)
            Text(detail)
                .font(.custom("AvenirNext-Medium", fixedSize: 12))
                .foregroundStyle(Self.muted)
                .lineLimit(1)
                .frame(height: 16)
        }
    }

    // MARK: Drawing

    private func draw(in ctx: GraphicsContext) {
        let w = geo.ticket.width
        let h = geo.ticket.height
        let shape = ScratchTicketArt.ticketShape(width: w, height: h, perfY: geo.perfY, radius: M.radius)

        // Card stock with a long soft shadow and a tight contact one.
        var card = ctx
        card.addFilter(.shadow(color: .rgba(0, 0, 0, 0.42), radius: 48, x: 0, y: 18))
        card.addFilter(.shadow(color: .rgba(0, 0, 0, 0.25), radius: 6, x: 0, y: 2))
        card.fill(shape, with: .color(Self.paper))

        var stock = ctx
        stock.clip(to: shape)
        // Paper grain, multiplied in: a grey that reaches 10% below white.
        var grain = stock
        grain.blendMode = .multiply
        grain.fill(Path(CGRect(x: 0, y: 0, width: w, height: h)), with: .shader(ShaderLibrary.scratchTicketGrain(.float(0.9), .float(0.9), .float(0.1))))
        let sheen = Gradient(stops: [
            .init(color: .rgba(255, 255, 255, 0.35), location: 0),
            .init(color: .rgba(255, 255, 255, 0), location: 0.6),
            .init(color: .rgba(120, 96, 50, 0.10), location: 1),
        ])
        stock.fill(Path(CGRect(x: 0, y: 0, width: w, height: h)), with: .radialGradient(sheen, center: CGPoint(x: w * 0.3, y: 0), startRadius: 0, endRadius: h))

        // A printed frame round the face and round the stub.
        let dashed = StrokeStyle(lineWidth: 1, dash: [3, 3])
        let frame = Color.rgba(31, 42, 34, 0.22)
        ctx.stroke(Path(roundedRect: CGRect(x: 10, y: 10, width: w - 20, height: geo.perfY - 26), cornerRadius: 12), with: .color(frame), style: dashed)
        ctx.stroke(Path(roundedRect: CGRect(x: 10, y: geo.perfY + 16, width: w - 20, height: h - geo.perfY - 26), cornerRadius: 10), with: .color(frame), style: dashed)

        // The sunflower mark in the header.
        var mark = ctx
        mark.translateBy(x: w - 34, y: 27)
        mark.fill(Self.mark.petals, with: .color(Color(hex: 0xeea41c)))
        mark.fill(Self.mark.head, with: .color(Color(hex: 0x5a3a1c)))

        drawWindow(in: ctx)

        var bars = ctx
        bars.translateBy(x: w - 22 - M.barWidth, y: geo.perfY + 30)
        bars.fill(Self.barcode, with: .color(Self.ink))
    }

    /// The scratch window: the prize photo, and the foil over it with the scratches cut out.
    private func drawWindow(in context: GraphicsContext) {
        let s = geo.scratch
        var ctx = context
        ctx.translateBy(x: s.minX, y: s.minY)
        let window = CGRect(x: 0, y: 0, width: s.width, height: s.height)
        let rounded = Path(roundedRect: window, cornerRadius: M.scratchRadius, style: .circular)
        var inside = ctx
        inside.clip(to: rounded)
        inside.fill(Path(window), with: .color(Color(hex: 0x3b4a2c)))
        let dissolve = slot.dissolve.value
        if let photo {
            // Cover the window, zoomed a little until the foil has gone.
            let zoom = 1 + (reducedMotion ? 0 : M.photoZoom * (1 - dissolve))
            let fill = max(s.width / photo.size.width, s.height / photo.size.height) * zoom
            let pw = photo.size.width * fill, ph = photo.size.height * fill
            inside.draw(Image(uiImage: photo), in: CGRect(x: (s.width - pw) / 2, y: (s.height - ph) / 2, width: pw, height: ph))
        }
        inside.fill(
            Path(window),
            with: .radialGradient(
                Gradient(colors: [.rgba(0, 0, 0, 0), .rgba(20, 14, 4, 0.32)]),
                center: CGPoint(x: s.width / 2, y: s.height / 2), startRadius: 0, endRadius: s.width * 0.75
            )
        )

        if dissolve < 1 {
            // The foil lives in its own layer, so the finger's strokes erase it and nothing under it.
            // The shader's origin is the window's corner in the canvas.
            let origin = CGPoint(x: M.bleed + s.minX, y: M.bleed + s.minY)
            let foil = ShaderLibrary.scratchTicketFoil(
                .float2(origin), .float2(CGSize(width: s.width, height: s.height)),
                .float(phase), .float2(tilt), .float(dissolve)
            )
            let strokes = slot.strokes
            let emboss = max(0, 1 - dissolve * 3)
            inside.drawLayer { layer in
                layer.fill(Path(window), with: .shader(foil))
                if emboss > 0 { drawEmboss(in: layer, opacity: emboss) }
                // The finger rubs the foil away with a soft edge.
                var eraser = layer
                eraser.blendMode = .destinationOut
                eraser.addFilter(.blur(radius: 10))
                eraser.stroke(strokes, with: .color(.black), style: StrokeStyle(lineWidth: M.brush, lineCap: .round, lineJoin: .round))
            }
        }
        ctx.stroke(
            Path(roundedRect: CGRect(x: 0.5, y: 0.5, width: s.width - 1, height: s.height - 1), cornerRadius: M.scratchRadius, style: .circular),
            with: .color(.rgba(31, 42, 34, 0.28)), lineWidth: 1
        )
    }

    /// Pressed into the foil: a sunflower and the instruction, light catching their upper edge.
    private func drawEmboss(in layer: GraphicsContext, opacity: Double) {
        let s = geo.scratch
        var c = layer
        c.opacity = opacity
        var light = c
        light.translateBy(x: s.width / 2 - 1, y: s.height / 2 - 17)
        light.fill(Self.emboss.petals, with: .color(.rgba(255, 255, 255, 0.45)))
        var dark = c
        dark.translateBy(x: s.width / 2, y: s.height / 2 - 16)
        dark.fill(Self.emboss.petals, with: .color(.rgba(58, 62, 74, 0.42)))
        dark.fill(Self.emboss.head, with: .color(.rgba(58, 62, 74, 0.5)))
        dark.fill(Self.emboss.seeds, with: .color(.rgba(255, 255, 255, 0.35)))
        let font = Self.labelFont
        let width = (Self.label as NSString).size(withAttributes: [.font: font]).width
        // Skia draws text from its baseline; SwiftUI places it by its top.
        let top = s.height / 2 + 38 - font.ascender
        func label(_ color: Color) -> Text {
            Text(Self.label).font(.custom("AvenirNextCondensed-Bold", fixedSize: 13)).foregroundStyle(color)
        }
        c.draw(label(.rgba(255, 255, 255, 0.55)), at: CGPoint(x: (s.width - width) / 2 - 0.8, y: top - 1), anchor: .topLeading)
        c.draw(label(.rgba(48, 52, 64, 0.7)), at: CGPoint(x: (s.width - width) / 2, y: top), anchor: .topLeading)
    }
}
