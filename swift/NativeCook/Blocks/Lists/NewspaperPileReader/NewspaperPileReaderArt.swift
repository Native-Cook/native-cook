import CoreText
import SwiftUI
import UIKit

/// The six folded papers and the two shadow sprites, drawn once into bitmaps (CRAFT §7): a frame only
/// moves images around.
struct NewspaperPileReaderImages {
    let papers: [Image]
    let contact: Image
    let ambient: Image
}

enum NewspaperPileReaderFonts {
    static let names: (black: String, serif: String) = {
        func register(_ file: String, fallback: String) -> String {
            guard let url = Bundle.main.url(forResource: file, withExtension: "ttf") else { return fallback }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            if let descs = CTFontManagerCreateFontDescriptorsFromURL(url as CFURL) as? [CTFontDescriptor],
               let first = descs.first,
               let name = CTFontDescriptorCopyAttribute(first, kCTFontNameAttribute) as? String {
                return name
            }
            return fallback
        }
        return (register("NewspaperPileReader-UnifrakturMaguntia-Book", fallback: "UnifrakturMaguntia-Book"),
                register("NewspaperPileReader-DMSerifDisplay-Regular", fallback: "DMSerifDisplay-Regular"))
    }()

    static func black(_ size: CGFloat) -> Font { .custom(names.black, fixedSize: size) }
    static func serif(_ size: CGFloat) -> Font { .custom(names.serif, fixedSize: size) }
}

enum NewspaperPileReaderArt {
    typealias M = NewspaperPileReaderModel

    /// The paper image is the 340 × 228 sheet with 6 pt around it for the page edges.
    static let pad = 6.0
    static let contactPad = 10.0
    static let ambientPad = 60.0

    static let ink: UInt32 = 0x1C1B19
    static let rule: UInt32 = 0x34322D
    static let lines: UInt32 = 0x9E9A8F
    static let red: UInt32 = 0xC23A33
    static let blue: UInt32 = 0x2C5BA6
    static let navy: UInt32 = 0x213A66

    static func color(_ hex: UInt32, _ a: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255,
              blue: Double(hex & 0xFF) / 255, opacity: a)
    }

    /// The colour a little darker (k = 0.04 → 4 %).
    static func darker(_ hex: UInt32, _ k: Double) -> Color {
        func ch(_ s: UInt32) -> Double { Double((hex >> s) & 0xFF) / 255 * (1 - k) }
        return Color(.sRGB, red: ch(16), green: ch(8), blue: ch(0), opacity: 1)
    }

    @MainActor
    static func make(scale: CGFloat) -> NewspaperPileReaderImages {
        _ = NewspaperPileReaderFonts.names
        let papers = M.papers.indices.map { i in
            render(width: M.paperW + 2 * pad, height: M.paperH + 2 * pad, scale: scale) { c in
                c.translateBy(x: pad, y: pad)
                paper(&c, i)
            }
        }
        let contact = render(width: M.paperW + 2 * contactPad, height: M.paperH + 2 * contactPad, scale: scale) { c in
            c.addFilter(.blur(radius: 3))
            c.fill(Path(roundedRect: CGRect(x: contactPad, y: contactPad, width: M.paperW, height: M.paperH), cornerRadius: 3),
                   with: .color(.black))
        }
        let ambient = render(width: M.paperW + 2 * ambientPad, height: M.paperH + 2 * ambientPad, scale: min(scale, 2)) { c in
            c.addFilter(.blur(radius: 20))
            c.fill(Path(roundedRect: CGRect(x: ambientPad, y: ambientPad, width: M.paperW, height: M.paperH), cornerRadius: 8),
                   with: .color(.black))
        }
        return NewspaperPileReaderImages(papers: papers, contact: contact, ambient: ambient)
    }

    @MainActor
    private static func render(width: Double, height: Double, scale: CGFloat,
                               _ draw: @escaping (inout GraphicsContext) -> Void) -> Image {
        let renderer = ImageRenderer(content: Canvas { c, _ in draw(&c) }.frame(width: width, height: height))
        renderer.scale = scale
        renderer.isOpaque = false
        guard let ui = renderer.uiImage else { return Image(systemName: "newspaper") }
        return Image(uiImage: ui)
    }

    // MARK: Text helpers

    static func text(_ s: String, _ font: Font, _ col: Color, tracking: CGFloat = 0) -> Text {
        Text(s).font(font).tracking(tracking).foregroundColor(col)
    }

    /// Draws one line, shrunk to fit `maxWidth` if needed.
    static func fit(_ c: inout GraphicsContext, _ t: Text, at p: CGPoint, anchor: UnitPoint, maxWidth: Double) {
        let r = c.resolve(t)
        let size = r.measure(in: CGSize(width: 2000, height: 400))
        if size.width <= maxWidth {
            c.draw(r, at: p, anchor: anchor)
            return
        }
        var k = c
        k.translateBy(x: p.x, y: p.y)
        let s = maxWidth / size.width
        k.scaleBy(x: s, y: s)
        k.draw(r, at: .zero, anchor: anchor)
    }

    // MARK: Paper

    static func paper(_ c: inout GraphicsContext, _ i: Int) {
        let p = M.papers[i]
        let w = M.paperW
        let h = M.paperH
        // Page edges of the folded paper behind the front page.
        c.fill(Path(CGRect(x: 3.5, y: 4, width: w, height: h)), with: .color(color(0xCCC7BB)))
        c.fill(Path(CGRect(x: 2, y: 2, width: w, height: h)), with: .color(color(0xDAD6CB)))
        let sheet = CGRect(x: 0, y: 0, width: w, height: h)
        c.fill(Path(sheet), with: .linearGradient(Gradient(colors: [color(p.tone), darker(p.tone, 0.045)]),
                                                  startPoint: .zero, endPoint: CGPoint(x: 0, y: h)))
        grain(&c, seed: Double(i) * 101 + 7)

        var body = c
        body.clip(to: Path(sheet))
        switch p.style {
        case 0: tabloid(&body, p, i)
        case 1: quayside(&body, p, i)
        case 2: larkmoor(&body, p, i)
        case 3: fernhill(&body, p, i)
        case 4: saltday(&body, p, i)
        default: orrin(&body, p, i)
        }

        // Fold: the bottom edge is the fold, rounded and a little darker, with a light lip.
        c.fill(Path(CGRect(x: 0, y: h - 20, width: w, height: 20)),
               with: .linearGradient(Gradient(colors: [.black.opacity(0), .black.opacity(0.09)]),
                                     startPoint: CGPoint(x: 0, y: h - 20), endPoint: CGPoint(x: 0, y: h)))
        c.fill(Path(CGRect(x: 0, y: h - 1.2, width: w, height: 0.8)), with: .color(.white.opacity(0.55)))
        // Left edge thickness.
        c.fill(Path(CGRect(x: 0, y: 0, width: 6, height: h)),
               with: .linearGradient(Gradient(colors: [.black.opacity(0.05), .black.opacity(0)]),
                                     startPoint: .zero, endPoint: CGPoint(x: 6, y: 0)))
        // Curl highlight on the top-right corner.
        var tri = Path()
        tri.move(to: CGPoint(x: w - 46, y: 0))
        tri.addLine(to: CGPoint(x: w, y: 0))
        tri.addLine(to: CGPoint(x: w, y: 46))
        tri.closeSubpath()
        c.fill(tri, with: .linearGradient(
            Gradient(stops: [.init(color: .white.opacity(0), location: 0), .init(color: .white.opacity(0.5), location: 0.45),
                             .init(color: .black.opacity(0.05), location: 0.85), .init(color: .black.opacity(0), location: 1)]),
            startPoint: CGPoint(x: w, y: 0), endPoint: CGPoint(x: w - 23, y: 23)))
        c.stroke(Path(sheet.insetBy(dx: 0.25, dy: 0.25)), with: .color(.black.opacity(0.06)), lineWidth: 0.5)
    }

    static func grain(_ c: inout GraphicsContext, seed: Double) {
        var dark = Path()
        for k in 0..<900 {
            let x = M.hash(seed + Double(k) * 1.31) * M.paperW
            let y = M.hash(seed + Double(k) * 2.17 + 0.5) * M.paperH
            let r = 0.35 + 0.3 * M.hash(seed + Double(k) * 0.77)
            dark.addEllipse(in: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r))
        }
        c.fill(dark, with: .color(.black.opacity(0.06)))
        var light = Path()
        for k in 0..<300 {
            let x = M.hash(seed + Double(k) * 3.11 + 9) * M.paperW
            let y = M.hash(seed + Double(k) * 1.93 + 4) * M.paperH
            light.addEllipse(in: CGRect(x: x - 0.5, y: y - 0.5, width: 1, height: 1))
        }
        c.fill(light, with: .color(.white.opacity(0.4)))
    }

    /// Rows of grey "text", with a short last line and a gap at each paragraph end.
    static func textLines(_ c: inout GraphicsContext, _ r: CGRect, seed: Double, tint: UInt32 = lines, alpha: Double = 0.6) {
        var path = Path()
        var y = r.minY + 1
        var k = 0.0
        while y + 1.6 <= r.maxY {
            let hh = M.hash(seed + k)
            let end = M.hash(seed * 1.7 + k * 3.1) < 0.12
            let len = end ? r.width * (0.3 + 0.4 * hh) : r.width * (0.86 + 0.14 * hh)
            path.addRoundedRect(in: CGRect(x: r.minX, y: y, width: len, height: 1.6), cornerSize: CGSize(width: 0.6, height: 0.6))
            y += end ? 8 : 5
            k += 1
        }
        c.fill(path, with: .color(color(tint, alpha)))
    }

    static func columns(_ c: inout GraphicsContext, _ r: CGRect, _ n: Int, seed: Double) {
        let gut = 8.0
        let cw = (r.width - gut * Double(n - 1)) / Double(n)
        for j in 0..<n {
            let x = r.minX + Double(j) * (cw + gut)
            textLines(&c, CGRect(x: x, y: r.minY, width: cw, height: r.height), seed: seed + Double(j) * 31)
            if j > 0 {
                c.fill(Path(CGRect(x: x - gut / 2 - 0.25, y: r.minY, width: 0.5, height: r.height)), with: .color(color(rule, 0.35)))
            }
        }
    }

    static func hrule(_ c: inout GraphicsContext, _ y: Double, _ x0: Double = 12, _ x1: Double = 328, _ wid: Double = 0.5, _ col: UInt32 = rule) {
        c.fill(Path(CGRect(x: x0, y: y - wid / 2, width: x1 - x0, height: wid)), with: .color(color(col)))
    }

    static func dateline(_ c: inout GraphicsContext, left: String, right: String) {
        let f = Font.system(size: 5.5, weight: .semibold)
        c.draw(text(left, f, color(ink, 0.75), tracking: 0.4), at: CGPoint(x: 12, y: 8), anchor: .leading)
        c.draw(text(right, f, color(ink, 0.75), tracking: 0.4), at: CGPoint(x: 328, y: 8), anchor: .trailing)
    }

    static func infoRow(_ c: inout GraphicsContext, y: Double, _ s: String) {
        c.draw(text(s, .system(size: 5, weight: .medium), color(ink, 0.7), tracking: 0.5), at: CGPoint(x: 170, y: y), anchor: .center)
    }

    /// A wrapped serif headline (the rect is only its width and top: it may take two or three lines).
    static func headline(_ c: inout GraphicsContext, _ s: String, _ r: CGRect, size: CGFloat, col: UInt32 = ink) {
        let resolved = c.resolve(text(s, NewspaperPileReaderFonts.serif(size), color(col)))
        c.draw(resolved, in: CGRect(x: r.minX, y: r.minY, width: r.width, height: r.height + 40))
    }

    // MARK: Styles

    static func tabloid(_ c: inout GraphicsContext, _ p: NewspaperPileReaderPaper, _ i: Int) {
        fit(&c, text(p.masthead, NewspaperPileReaderFonts.black(31), color(ink)), at: CGPoint(x: 12, y: 27), anchor: .leading, maxWidth: 236)
        c.fill(Path(CGRect(x: 12, y: 46, width: 236, height: 15)), with: .color(color(red)))
        c.draw(text("WEEKEND PULLOUT · 24 PAGES", .system(size: 7, weight: .bold), .white, tracking: 0.9),
               at: CGPoint(x: 130, y: 53.5), anchor: .center)
        photo(&c, kind: 0, CGRect(x: 256, y: 10, width: 72, height: 98), seed: Double(i))
        fit(&c, text(p.headline, NewspaperPileReaderFonts.serif(34), color(ink)), at: CGPoint(x: 12, y: 86), anchor: .leading, maxWidth: 236)
        c.draw(text(p.deck, NewspaperPileReaderFonts.serif(12.5), color(red)), at: CGPoint(x: 12, y: 114), anchor: .leading)
        hrule(&c, 126)
        columns(&c, CGRect(x: 12, y: 132, width: 316, height: 74), 4, seed: 11)
    }

    static func quayside(_ c: inout GraphicsContext, _ p: NewspaperPileReaderPaper, _ i: Int) {
        dateline(&c, left: "TUESDAY, JUNE 4", right: "$3.00")
        fit(&c, text(p.masthead, NewspaperPileReaderFonts.serif(25), color(ink), tracking: -0.2),
            at: CGPoint(x: 170, y: 30), anchor: .center, maxWidth: 312)
        hrule(&c, 49.5, 12, 328, 1.2)
        hrule(&c, 52)
        infoRow(&c, y: 57, "VOL. CXII  ·  NO. 108  ·  LATE EDITION  ·  ★★★★")
        hrule(&c, 62)
        // "What's Inside" box.
        let box = CGRect(x: 12, y: 68, width: 52, height: 56)
        c.stroke(Path(box), with: .color(color(blue, 0.8)), lineWidth: 0.6)
        c.draw(text("What's", NewspaperPileReaderFonts.serif(9), color(blue)), at: CGPoint(x: 16, y: 76), anchor: .leading)
        c.draw(text("Inside", NewspaperPileReaderFonts.serif(9), color(blue)), at: CGPoint(x: 16, y: 86), anchor: .leading)
        textLines(&c, CGRect(x: 16, y: 94, width: 44, height: 28), seed: 3, tint: blue, alpha: 0.45)
        textLines(&c, CGRect(x: 12, y: 130, width: 52, height: 76), seed: 5)
        c.fill(Path(CGRect(x: 67.75, y: 68, width: 0.5, height: 138)), with: .color(color(rule, 0.35)))
        headline(&c, p.headline, CGRect(x: 72, y: 64, width: 186, height: 46), size: 17)
        columns(&c, CGRect(x: 72, y: 112, width: 186, height: 94), 3, seed: 17)
        c.fill(Path(CGRect(x: 261.75, y: 68, width: 0.5, height: 138)), with: .color(color(rule, 0.35)))
        photo(&c, kind: 1, CGRect(x: 266, y: 68, width: 62, height: 56), seed: Double(i))
        headline(&c, p.deck, CGRect(x: 266, y: 127, width: 62, height: 30), size: 10.5)
        textLines(&c, CGRect(x: 266, y: 158, width: 62, height: 48), seed: 23)
    }

    static func larkmoor(_ c: inout GraphicsContext, _ p: NewspaperPileReaderPaper, _ i: Int) {
        dateline(&c, left: "TUESDAY, JUNE 4", right: "TWO DOLLARS")
        fit(&c, text(p.masthead, NewspaperPileReaderFonts.black(30), color(ink)), at: CGPoint(x: 170, y: 29), anchor: .center, maxWidth: 312)
        hrule(&c, 49.5, 12, 328, 1.2)
        hrule(&c, 52)
        infoRow(&c, y: 57, "LARKMOOR AND THE VALLEY  ·  SINCE 1874  ·  WEATHER: MILD, 18°")
        hrule(&c, 62)
        headline(&c, p.headline, CGRect(x: 12, y: 64, width: 194, height: 46), size: 17)
        columns(&c, CGRect(x: 12, y: 112, width: 194, height: 94), 3, seed: 29)
        photo(&c, kind: 2, CGRect(x: 214, y: 68, width: 114, height: 82), seed: Double(i))
        textLines(&c, CGRect(x: 214, y: 155, width: 114, height: 51), seed: 37)
    }

    static func fernhill(_ c: inout GraphicsContext, _ p: NewspaperPileReaderPaper, _ i: Int) {
        dateline(&c, left: "TUESDAY, JUNE 4", right: "FINAL")
        fit(&c, text(p.masthead, NewspaperPileReaderFonts.black(31), color(ink)), at: CGPoint(x: 170, y: 29), anchor: .center, maxWidth: 312)
        hrule(&c, 49.5, 12, 328, 1.2)
        hrule(&c, 52)
        infoRow(&c, y: 57, "AN INDEPENDENT NEWSPAPER  ·  FERNHILL, TUESDAY  ·  $2.75")
        hrule(&c, 62)
        photo(&c, kind: 3, CGRect(x: 12, y: 68, width: 120, height: 82), seed: Double(i))
        textLines(&c, CGRect(x: 12, y: 155, width: 120, height: 51), seed: 41)
        headline(&c, p.headline, CGRect(x: 140, y: 64, width: 188, height: 46), size: 17)
        columns(&c, CGRect(x: 140, y: 112, width: 188, height: 94), 3, seed: 43)
    }

    static func saltday(_ c: inout GraphicsContext, _ p: NewspaperPileReaderPaper, _ i: Int) {
        hrule(&c, 12, 12, 328, 2, red)
        fit(&c, text(p.masthead, .system(size: 32, weight: .black).italic(), color(navy), tracking: -0.6),
            at: CGPoint(x: 170, y: 31), anchor: .center, maxWidth: 312)
        hrule(&c, 50, 12, 328, 2, red)
        infoRow(&c, y: 57, "TUESDAY, JUNE 4  ·  NORTH SHORE EDITION  ·  $2.00")
        photo(&c, kind: 4, CGRect(x: 12, y: 66, width: 164, height: 84), seed: Double(i))
        columns(&c, CGRect(x: 12, y: 156, width: 164, height: 50), 2, seed: 47)
        headline(&c, p.headline, CGRect(x: 184, y: 63, width: 144, height: 46), size: 16, col: navy)
        columns(&c, CGRect(x: 184, y: 112, width: 144, height: 94), 2, seed: 53)
    }

    static func orrin(_ c: inout GraphicsContext, _ p: NewspaperPileReaderPaper, _ i: Int) {
        dateline(&c, left: "TUESDAY, JUNE 4", right: "$2.50")
        fit(&c, text(p.masthead, NewspaperPileReaderFonts.serif(28), color(ink)), at: CGPoint(x: 170, y: 30), anchor: .center, maxWidth: 312)
        hrule(&c, 49.5, 12, 328, 1.2)
        hrule(&c, 52)
        infoRow(&c, y: 57, "THE BAY'S OWN PAPER  ·  SPRING GARDENS ISSUE")
        hrule(&c, 62)
        fit(&c, text(p.headline, NewspaperPileReaderFonts.serif(19), color(ink)), at: CGPoint(x: 170, y: 78), anchor: .center, maxWidth: 312)
        photo(&c, kind: 5, CGRect(x: 12, y: 94, width: 188, height: 78), seed: Double(i))
        textLines(&c, CGRect(x: 12, y: 177, width: 188, height: 29), seed: 59)
        columns(&c, CGRect(x: 208, y: 94, width: 120, height: 112), 2, seed: 61)
    }

    // MARK: Photos (drawn in code, then printed: halftone, paper tint, hairline)

    static func photo(_ c: inout GraphicsContext, kind: Int, _ r: CGRect, seed: Double) {
        var p = c
        p.clip(to: Path(r))
        switch kind {
        case 0: portrait(&p, r)
        case 1: harbour(&p, r)
        case 2: market(&p, r)
        case 3: train(&p, r)
        case 4: sunset(&p, r)
        default: garden(&p, r)
        }
        // Halftone dots and a paper tint so it reads as print.
        var dots = Path()
        let pitch = 2.4
        var row = 0
        var y = r.minY + 0.6
        while y < r.maxY {
            var x = r.minX + (row % 2 == 0 ? 0.6 : 1.8)
            while x < r.maxX {
                dots.addEllipse(in: CGRect(x: x - 0.42, y: y - 0.42, width: 0.84, height: 0.84))
                x += pitch
            }
            y += pitch
            row += 1
        }
        p.fill(dots, with: .color(.black.opacity(0.12)))
        p.fill(Path(r), with: .color(color(0xF1EEE5, 0.12)))
        c.stroke(Path(r), with: .color(color(rule, 0.3)), lineWidth: 0.5)
    }

    static func vgrad(_ c: inout GraphicsContext, _ r: CGRect, _ cols: [UInt32]) {
        c.fill(Path(r), with: .linearGradient(Gradient(colors: cols.map { color($0) }),
                                              startPoint: CGPoint(x: r.minX, y: r.minY), endPoint: CGPoint(x: r.minX, y: r.maxY)))
    }

    static func portrait(_ c: inout GraphicsContext, _ r: CGRect) {
        vgrad(&c, r, [0xD9A273, 0xC8704F, 0xA9483A])
        let cx = r.midX
        // Shoulders, collar, neck, head, hair.
        c.fill(Path(ellipseIn: CGRect(x: cx - r.width * 0.55, y: r.minY + r.height * 0.68, width: r.width * 1.1, height: r.height * 0.6)),
               with: .color(color(0x24324F)))
        var collar = Path()
        collar.move(to: CGPoint(x: cx - 9, y: r.minY + r.height * 0.69))
        collar.addLine(to: CGPoint(x: cx + 9, y: r.minY + r.height * 0.69))
        collar.addLine(to: CGPoint(x: cx, y: r.minY + r.height * 0.82))
        collar.closeSubpath()
        c.fill(collar, with: .color(color(0xF2EFE8)))
        c.fill(Path(CGRect(x: cx - 6, y: r.minY + r.height * 0.55, width: 12, height: r.height * 0.15)), with: .color(color(0xD9A88C)))
        let head = CGRect(x: cx - 15, y: r.minY + r.height * 0.24, width: 30, height: 37)
        c.fill(Path(ellipseIn: head), with: .color(color(0xE6BCA0)))
        var hair = Path()
        hair.addArc(center: CGPoint(x: cx, y: head.minY + 14), radius: 16.5, startAngle: .degrees(195), endAngle: .degrees(345), clockwise: false)
        hair.closeSubpath()
        c.fill(hair, with: .color(color(0xE9E4DA)))
        c.fill(Path(ellipseIn: CGRect(x: head.minX + 1, y: head.minY + 4, width: head.width - 2, height: 12)), with: .color(color(0xEDE8DF)))
    }

    static func harbour(_ c: inout GraphicsContext, _ r: CGRect) {
        vgrad(&c, r, [0xC4CDD2, 0xDDDCD5])
        let sea = CGRect(x: r.minX, y: r.minY + r.height * 0.62, width: r.width, height: r.height * 0.38)
        vgrad(&c, sea, [0x6E8591, 0x4D6270])
        // Ferry.
        let fx = r.minX + r.width * 0.22
        let fy = sea.minY
        var hull = Path()
        hull.move(to: CGPoint(x: fx, y: fy - 4))
        hull.addLine(to: CGPoint(x: fx + 36, y: fy - 4))
        hull.addLine(to: CGPoint(x: fx + 31, y: fy + 4))
        hull.addLine(to: CGPoint(x: fx + 4, y: fy + 4))
        hull.closeSubpath()
        c.fill(hull, with: .color(color(0x2D3740)))
        c.fill(Path(CGRect(x: fx + 7, y: fy - 11, width: 22, height: 7)), with: .color(color(0xF2F0EA)))
        for k in 0..<4 {
            c.fill(Path(CGRect(x: fx + 9 + Double(k) * 5, y: fy - 9, width: 3, height: 2.5)), with: .color(color(0x3E4B57)))
        }
        c.fill(Path(CGRect(x: fx + 4, y: fy + 5, width: 30, height: 1)), with: .color(.white.opacity(0.35)))
        // Far shore.
        c.fill(Path(CGRect(x: r.minX, y: sea.minY - 3, width: r.width, height: 3)), with: .color(color(0x8E9A9E)))
    }

    static func market(_ c: inout GraphicsContext, _ r: CGRect) {
        vgrad(&c, r, [0x1F2A44, 0x3B3552, 0x5A3F4F])
        // String of lights.
        var wire = Path()
        let y0 = r.minY + 12
        wire.move(to: CGPoint(x: r.minX, y: y0))
        wire.addQuadCurve(to: CGPoint(x: r.maxX, y: y0), control: CGPoint(x: r.midX, y: y0 + 26))
        c.stroke(wire, with: .color(color(0x111522, 0.8)), lineWidth: 0.6)
        for k in 0..<11 {
            let t = (Double(k) + 0.5) / 11
            let x = r.minX + r.width * t
            let y = (1 - t) * (1 - t) * y0 + 2 * (1 - t) * t * (y0 + 26) + t * t * y0
            c.fill(Path(ellipseIn: CGRect(x: x - 4, y: y - 3, width: 8, height: 8)), with: .color(color(0xF4C25B, 0.25)))
            c.fill(Path(ellipseIn: CGRect(x: x - 1.6, y: y - 0.6, width: 3.2, height: 3.2)), with: .color(color(0xFFE3A1)))
        }
        // Stalls.
        let cols: [UInt32] = [0xC23A33, 0x2F8F86, 0xE2A23B]
        for k in 0..<3 {
            let x = r.minX + 6 + Double(k) * (r.width - 12) / 3
            let sw = (r.width - 12) / 3 - 4
            let top = r.minY + r.height * 0.5
            var roof = Path()
            roof.move(to: CGPoint(x: x - 2, y: top + 10))
            roof.addLine(to: CGPoint(x: x + sw / 2, y: top))
            roof.addLine(to: CGPoint(x: x + sw + 2, y: top + 10))
            roof.closeSubpath()
            c.fill(roof, with: .color(color(cols[k])))
            c.fill(Path(CGRect(x: x, y: top + 10, width: sw, height: r.maxY - top - 10)), with: .color(color(0x2A2233)))
            c.fill(Path(CGRect(x: x + 3, y: top + 14, width: sw - 6, height: 9)), with: .color(color(0xF4C25B, 0.75)))
        }
        // People.
        for k in 0..<5 {
            let x = r.minX + 10 + Double(k) * 23
            c.fill(Path(ellipseIn: CGRect(x: x, y: r.maxY - 18, width: 6, height: 6)), with: .color(color(0x14111A)))
            c.fill(Path(roundedRect: CGRect(x: x - 2, y: r.maxY - 12, width: 10, height: 14), cornerRadius: 4), with: .color(color(0x14111A)))
        }
    }

    static func train(_ c: inout GraphicsContext, _ r: CGRect) {
        vgrad(&c, r, [0xB9B8B3, 0xE2E1DC])
        let horizon = r.minY + r.height * 0.58
        vgrad(&c, CGRect(x: r.minX, y: horizon, width: r.width, height: r.maxY - horizon), [0x76746E, 0x4E4D49])
        // Hills.
        var hill = Path()
        hill.move(to: CGPoint(x: r.minX, y: horizon))
        hill.addQuadCurve(to: CGPoint(x: r.maxX, y: horizon), control: CGPoint(x: r.minX + r.width * 0.3, y: horizon - 22))
        c.fill(hill, with: .color(color(0x8F8D87)))
        // Rails converging to the train.
        let vx = r.minX + r.width * 0.62
        for s in [-1.0, 1.0] {
            var rail = Path()
            rail.move(to: CGPoint(x: vx + s * 6, y: horizon + 4))
            rail.addLine(to: CGPoint(x: vx + s * 46, y: r.maxY))
            c.stroke(rail, with: .color(color(0xD7D5CF)), lineWidth: 1)
        }
        // Train front.
        let tr = CGRect(x: vx - 16, y: horizon - 26, width: 32, height: 32)
        c.fill(Path(roundedRect: tr, cornerRadius: 5), with: .color(color(0x3A3936)))
        c.fill(Path(roundedRect: CGRect(x: tr.minX + 4, y: tr.minY + 5, width: tr.width - 8, height: 10), cornerRadius: 2),
               with: .color(color(0xC9C8C2)))
        c.fill(Path(ellipseIn: CGRect(x: tr.minX + 5, y: tr.maxY - 9, width: 5, height: 5)), with: .color(color(0xF5F3EC)))
        c.fill(Path(ellipseIn: CGRect(x: tr.maxX - 10, y: tr.maxY - 9, width: 5, height: 5)), with: .color(color(0xF5F3EC)))
    }

    static func sunset(_ c: inout GraphicsContext, _ r: CGRect) {
        vgrad(&c, r, [0x8E5A8C, 0xE9785A, 0xF2A65A])
        let sea = r.minY + r.height * 0.6
        c.fill(Path(ellipseIn: CGRect(x: r.midX - 16, y: sea - 18, width: 32, height: 32)), with: .color(color(0xFFE2A8)))
        vgrad(&c, CGRect(x: r.minX, y: sea, width: r.width, height: r.maxY - sea), [0x3F5F86, 0x2B4466])
        for k in 0..<6 {
            let w = 26 - Double(k) * 3
            c.fill(Path(roundedRect: CGRect(x: r.midX - w / 2, y: sea + 3 + Double(k) * 5, width: w, height: 1.6), cornerRadius: 0.8),
                   with: .color(color(0xFFD9A0, 0.8)))
        }
        // Boardwalk posts.
        for k in 0..<7 {
            c.fill(Path(CGRect(x: r.minX + 8 + Double(k) * 9, y: sea + 6 + Double(k) * 2.5, width: 2, height: 14)), with: .color(color(0x1E2433)))
        }
    }

    static func garden(_ c: inout GraphicsContext, _ r: CGRect) {
        vgrad(&c, r, [0xDDE6D2, 0xA9C092, 0x6F8E5E])
        // Path.
        var path = Path()
        path.move(to: CGPoint(x: r.midX - 6, y: r.minY + r.height * 0.55))
        path.addLine(to: CGPoint(x: r.midX + 6, y: r.minY + r.height * 0.55))
        path.addLine(to: CGPoint(x: r.midX + 40, y: r.maxY))
        path.addLine(to: CGPoint(x: r.midX - 40, y: r.maxY))
        path.closeSubpath()
        c.fill(path, with: .color(color(0xE6DCC6)))
        // Two magnolias.
        for (k, tx) in [0.22, 0.8].enumerated() {
            let x = r.minX + r.width * tx
            c.fill(Path(CGRect(x: x - 1.5, y: r.minY + r.height * 0.4, width: 3, height: r.height * 0.45)), with: .color(color(0x5A4636)))
            var blossoms = Path()
            for j in 0..<26 {
                let a = M.hash(Double(k * 50 + j) + 0.3) * 2 * .pi
                let d = sqrt(M.hash(Double(k * 50 + j) + 0.7)) * 22
                let bx = x + cos(a) * d * 1.2
                let by = r.minY + r.height * 0.32 + sin(a) * d * 0.7
                blossoms.addEllipse(in: CGRect(x: bx - 3, y: by - 3, width: 6, height: 6))
            }
            c.fill(blossoms, with: .color(color(0xE7A6B8)))
        }
    }
}
