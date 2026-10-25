import SwiftUI

/*
 The four pages of "Basecamp". Each page's artwork sits in a layer that lags behind the page by
 30 % of the slide (`p` is the knot's place), so the pages cross-slide with a parallax. The
 artwork and the rows take no `p`, so only the offsets change while the knot moves.
 */
enum RopeTabBarStyle {
    static let ink = Color(hex: 0xF2EBDD)
    static let inkSoft = Color(hex: 0x9FB0A5)
    static let card = Color(hex: 0x1D2C25)
    static let line = Color(.sRGB, red: 226 / 255, green: 238 / 255, blue: 229 / 255, opacity: 0.09)
    static let accent = Color(hex: 0xE8743B)

    /// A seeded wobble, so the contour map and the ridges look hand-surveyed but never change.
    static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898) * 43758.5453
        return s - floor(s)
    }
}

private typealias S = RopeTabBarStyle

struct RopeTabBarPages: View {
    let p: Double
    let width: Double

    var body: some View {
        HStack(alignment: .top, spacing: 0) {
            RopeTabBarExplore(p: p, width: width)
            RopeTabBarRoutes(p: p, width: width)
            RopeTabBarGear(p: p, width: width)
            RopeTabBarProfile(p: p, width: width)
        }
        .padding(.top, 2)
    }
}

private struct RopeTabBarRow: View {
    let title: String
    let detail: String
    var trailing = ""

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(S.ink)
                    .lineLimit(1)
                Text(detail)
                    .font(.system(size: 13).monospacedDigit())
                    .foregroundStyle(S.inkSoft)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if !trailing.isEmpty {
                Text(trailing)
                    .font(.system(size: 14, weight: .medium).monospacedDigit())
                    .foregroundStyle(S.inkSoft)
            }
        }
        .frame(minHeight: 60)
    }
}

private struct RopeTabBarList<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 20, style: .continuous)
        VStack(spacing: 0) { content }
            .padding(.horizontal, 16)
            .background(shape.fill(S.card))
            .overlay(
                shape.strokeBorder(
                    LinearGradient(colors: [.white.opacity(0.05), .clear], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.06)),
                    lineWidth: 1
                )
            )
    }
}

private struct RopeTabBarDivider: View {
    var body: some View {
        Rectangle().fill(S.line).frame(height: 0.5)
    }
}

private struct RopeTabBarHero<Art: View, Content: View>: View {
    var color = S.card
    @ViewBuilder let art: Art
    @ViewBuilder let content: Content

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 24, style: .continuous)
        // The art sits over the card's fill (it is wider than the card and clipped to it).
        Color.clear
            .frame(maxWidth: .infinity)
            .frame(height: 196)
            .background(alignment: .topLeading) {
                ZStack(alignment: .topLeading) {
                    color
                    art
                }
            }
            .overlay(alignment: .topLeading) {
                ZStack(alignment: .topLeading) { content }
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            }
            .clipShape(shape)
            .background(
                shape.fill(color)
                    .shadow(color: .black.opacity(0.3), radius: 1, y: 1)
                    .shadow(color: .black.opacity(0.25), radius: 16, y: 16)
            )
    }
}

private struct RopeTabBarChip: View {
    let text: String
    var dark = false

    var body: some View {
        Text(text)
            .font(.system(size: 12, weight: .semibold))
            .foregroundStyle(S.ink)
            .padding(.horizontal, 12)
            .frame(height: 28)
            .background(Capsule().fill(dark
                ? Color(.sRGB, red: 29 / 255, green: 44 / 255, blue: 37 / 255, opacity: 0.85)
                : Color(.sRGB, red: 16 / 255, green: 26 / 255, blue: 21 / 255, opacity: 0.55)))
            .padding(14)
    }
}

private struct RopeTabBarSection: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.system(size: 17, weight: .semibold))
            .foregroundStyle(S.ink)
            .padding(.top, 8)
    }
}

private struct RopeTabBarPage<Content: View>: View {
    let width: Double
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 12) { content }
            .padding(.horizontal, 20)
            .frame(width: width, alignment: .topLeading)
    }
}

private struct RopeTabBarLead: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.system(size: 15, weight: .medium))
            .foregroundStyle(S.inkSoft)
            .lineLimit(1)
    }
}

/// Paths for the landscape and the contour map, sized for an artwork 1.7 × the page width.
private enum RopeTabBarDrawings {
    static func ridge(_ width: Double, _ height: Double, _ base: Double, _ rough: Double, _ seed: Double) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: 0, y: height))
        var x = 0.0
        while x <= width + 0.001 {
            let y = base - rough * (0.5 + 0.5 * sin(x * 0.018 + seed) + 0.35 * S.hash((x).rounded() + seed * 31))
            p.addLine(to: CGPoint(x: x, y: y))
            x += width / 14
        }
        p.addLine(to: CGPoint(x: width, y: height))
        p.closeSubpath()
        return p
    }

    static func contour(_ cx: Double, _ cy: Double, _ r: Double, _ seed: Double) -> Path {
        var p = Path()
        for k in 0...36 {
            let a = Double(k) / 36 * .pi * 2
            let rr = r * (1 + 0.16 * sin(3 * a + seed) + 0.08 * sin(5 * a + seed * 2))
            let pt = CGPoint(x: cx + cos(a) * rr * 1.35, y: cy + sin(a) * rr)
            if k == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
        }
        p.closeSubpath()
        return p
    }
}

private struct RopeTabBarLandscape: View {
    let w: Double

    var body: some View {
        let W = w * 1.7
        Canvas { c, _ in
            c.fill(Path(CGRect(x: 0, y: 0, width: W, height: 220)), with: .linearGradient(
                Gradient(stops: [
                    .init(color: Color(hex: 0xF6D6B0), location: 0),
                    .init(color: Color(hex: 0xE9A986), location: 0.45),
                    .init(color: Color(hex: 0x9DB0BD), location: 1),
                ]),
                startPoint: .zero, endPoint: CGPoint(x: 0, y: 220)
            ))
            c.fill(Path(ellipseIn: CGRect(x: W * 0.55 - 22, y: 96 - 22, width: 44, height: 44)), with: .color(Color(hex: 0xFFF1D6, opacity: 0.9)))
            c.fill(RopeTabBarDrawings.ridge(W, 220, 112, 46, 1.3), with: .color(Color(hex: 0x8D9BB0, opacity: 0.85)))
            c.fill(RopeTabBarDrawings.ridge(W, 220, 136, 40, 4.1), with: .color(Color(hex: 0x5A6F84)))
            c.fill(Path(CGRect(x: 0, y: 150, width: W, height: 70)), with: .linearGradient(
                Gradient(colors: [Color(hex: 0x7D97A6), Color(hex: 0x2B4246)]),
                startPoint: CGPoint(x: 0, y: 150), endPoint: CGPoint(x: 0, y: 220)
            ))
            c.fill(RopeTabBarDrawings.ridge(W, 220, 176, 24, 7.7), with: .color(Color(hex: 0x253B35)))
            c.fill(RopeTabBarDrawings.ridge(W, 220, 204, 18, 2.2), with: .color(Color(hex: 0x16261F)))
        }
        .frame(width: W, height: 220)
    }
}

private struct RopeTabBarTopo: View {
    let w: Double

    var body: some View {
        let W = w * 1.7
        let cx = W * 0.5
        Canvas { c, _ in
            c.fill(Path(CGRect(x: 0, y: 0, width: W, height: 220)), with: .color(Color(hex: 0xE9E1CF)))
            let brown = Color(hex: 0xA2906C)
            for k in 0..<7 {
                let kk = Double(k)
                c.stroke(RopeTabBarDrawings.contour(cx - 40, 104, 14 + kk * 15, 0.7 + kk * 0.4), with: .color(brown.opacity(0.8)), lineWidth: k % 3 == 0 ? 1.4 : 0.8)
            }
            for k in 0..<4 {
                let kk = Double(k)
                c.stroke(RopeTabBarDrawings.contour(cx + 150, 60, 10 + kk * 13, 2.1 + kk), with: .color(brown.opacity(0.7)), lineWidth: 0.8)
            }
            var trail = Path()
            trail.move(to: CGPoint(x: cx - 150, y: 190))
            trail.addCurve(to: CGPoint(x: cx - 50, y: 100), control1: CGPoint(x: cx - 90, y: 150), control2: CGPoint(x: cx - 120, y: 110))
            // SVG "S": the first control point mirrors the previous one about the current point.
            trail.addCurve(to: CGPoint(x: cx + 110, y: 70), control1: CGPoint(x: cx + 20, y: 90), control2: CGPoint(x: cx + 40, y: 60))
            c.stroke(trail, with: .color(S.accent), style: StrokeStyle(lineWidth: 3, lineCap: .round, dash: [7, 5]))
            let start = Path(ellipseIn: CGRect(x: cx - 156, y: 184, width: 12, height: 12))
            c.fill(start, with: .color(Color(hex: 0x1D2C25)))
            c.stroke(start, with: .color(S.ink), lineWidth: 2)
            let end = Path(ellipseIn: CGRect(x: cx + 103, y: 63, width: 14, height: 14))
            c.fill(end, with: .color(S.accent))
            c.stroke(end, with: .color(S.ink), lineWidth: 2)
        }
        .frame(width: W, height: 220)
    }
}

private struct RopeTabBarSparkline: View {
    let seed: Double

    var body: some View {
        Canvas { c, _ in
            var p = Path()
            p.move(to: CGPoint(x: 0, y: 22))
            for k in 0...12 {
                let kk = Double(k)
                p.addLine(to: CGPoint(x: kk * 5, y: 22 - 16 * (0.3 + 0.7 * abs(sin(kk * 0.55 + seed)) * S.hash(kk + seed * 7))))
            }
            c.stroke(p, with: .color(S.accent), style: StrokeStyle(lineWidth: 1.6, lineJoin: .round))
        }
        .frame(width: 60, height: 26)
    }
}

/// The hero artwork, wider than the card, lagging behind the page by 30 % of the slide.
private struct RopeTabBarParallax<Content: View>: View {
    let shift: Double
    let width: Double
    @ViewBuilder let content: Content

    var body: some View {
        content
            .frame(width: width * 1.7, height: 220, alignment: .topLeading)
            .offset(x: -width * 0.35 + shift)
    }
}

private struct RopeTabBarExplore: View {
    let p: Double
    let width: Double

    var body: some View {
        RopeTabBarPage(width: width) {
            RopeTabBarLead(text: "Near Alder Ridge · 14 °C and clear")
            RopeTabBarHero {
                RopeTabBarParallax(shift: p * width * 0.3, width: width) {
                    RopeTabBarLandscape(w: width)
                }
            } content: {
                LinearGradient(
                    stops: [
                        .init(color: Color(.sRGB, red: 12 / 255, green: 22 / 255, blue: 17 / 255, opacity: 0), location: 0),
                        .init(color: Color(.sRGB, red: 12 / 255, green: 22 / 255, blue: 17 / 255, opacity: 0.35), location: 0.4),
                        .init(color: Color(.sRGB, red: 12 / 255, green: 22 / 255, blue: 17 / 255, opacity: 0.78), location: 1),
                    ],
                    startPoint: .top, endPoint: .bottom
                )
                .frame(height: 196 * 0.65)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                RopeTabBarChip(text: "Trail of the week")
                VStack(alignment: .leading, spacing: 2) {
                    Text("Kestrel Lake sunrise")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(S.ink)
                    Text("4.2 km · 2 h 10 min · Moderate")
                        .font(.system(size: 13, weight: .medium).monospacedDigit())
                        .foregroundStyle(S.ink.opacity(0.75))
                }
                .padding(.horizontal, 18)
                .padding(.bottom, 16)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
            }
            RopeTabBarSection(text: "Nearby")
            RopeTabBarList {
                HStack(spacing: 0) {
                    RopeTabBarRow(title: "Pinecrest Loop", detail: "6.8 km · ↑ 410 m · 3 h")
                    RopeTabBarSparkline(seed: 1.2)
                }
                RopeTabBarDivider()
                HStack(spacing: 0) {
                    RopeTabBarRow(title: "Granite Steps", detail: "3.1 km · ↑ 280 m · 1 h 40")
                    RopeTabBarSparkline(seed: 3.4)
                }
            }
        }
    }
}

private struct RopeTabBarRoutes: View {
    let p: Double
    let width: Double

    var body: some View {
        RopeTabBarPage(width: width) {
            RopeTabBarLead(text: "3 saved · 21.3 km planned this month")
            RopeTabBarHero {
                RopeTabBarParallax(shift: (p - 1) * width * 0.3, width: width) {
                    RopeTabBarTopo(w: width)
                }
            } content: {
                RopeTabBarChip(text: "Fernwater Trail · offline", dark: true)
            }
            RopeTabBarSection(text: "Saved routes")
            RopeTabBarList {
                RopeTabBarRow(title: "Fernwater Trail", detail: "11.4 km · ↑ 620 m", trailing: "Sat")
                RopeTabBarDivider()
                RopeTabBarRow(title: "Pinecrest Loop", detail: "6.8 km · ↑ 410 m", trailing: "Sun")
            }
        }
    }
}

/// Wraps its items onto as many rows as they need (the pack legend).
private struct RopeTabBarFlow: Layout {
    var columnGap = 14.0
    var rowGap = 6.0

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? .infinity
        var x = 0.0, y = 0.0, row = 0.0, widest = 0.0
        for s in subviews {
            let size = s.sizeThatFits(.unspecified)
            if x > 0 && x + size.width > width {
                y += row + rowGap
                x = 0
                row = 0
            }
            x += size.width + columnGap
            widest = max(widest, x - columnGap)
            row = max(row, size.height)
        }
        return CGSize(width: min(width, widest), height: y + row)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = 0.0, y = 0.0, row = 0.0
        for s in subviews {
            let size = s.sizeThatFits(.unspecified)
            if x > 0 && x + size.width > bounds.width {
                y += row + rowGap
                x = 0
                row = 0
            }
            s.place(at: CGPoint(x: bounds.minX + x, y: bounds.minY + y), proposal: .unspecified)
            x += size.width + columnGap
            row = max(row, size.height)
        }
    }
}

private struct RopeTabBarGear: View {
    let p: Double
    let width: Double

    private static let pack: [(String, Double, UInt32)] = [
        ("Shelter", 2.6, 0xE8743B),
        ("Sleep", 2.1, 0xD9B36C),
        ("Kitchen", 1.4, 0x7FA58E),
        ("Clothing", 2.3, 0x6F8FA8),
    ]

    var body: some View {
        RopeTabBarPage(width: width) {
            RopeTabBarLead(text: "Weekend pack · 2 nights")
            RopeTabBarHero(color: Color(hex: 0x22342B)) {
                EmptyView()
            } content: {
                RopeTabBarGearCard()
                    .padding(20)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
                    .offset(x: (p - 2) * width * 0.12)
            }
            RopeTabBarSection(text: "Still to pack")
            RopeTabBarList {
                RopeTabBarRow(title: "Shell jacket", detail: "Clothing", trailing: "420 g")
                RopeTabBarDivider()
                RopeTabBarRow(title: "Water filter", detail: "Kitchen", trailing: "210 g")
            }
        }
    }

    fileprivate static var packItems: [(String, Double, UInt32)] { pack }
}

private struct RopeTabBarGearCard: View {
    var body: some View {
        let pack = RopeTabBarGear.packItems
        let total = pack.reduce(0) { $0 + $1.1 }
        VStack(alignment: .leading, spacing: 10) {
            Text("BASE WEIGHT")
                .font(.system(size: 11, weight: .bold))
                .kerning(1.4)
                .foregroundStyle(S.inkSoft)
            Text("8.4 kg")
                .font(.system(size: 40, weight: .bold).monospacedDigit())
                .kerning(-1)
                .foregroundStyle(S.ink)
            GeometryReader { geo in
                let free = geo.size.width - Double(pack.count - 1) * 2
                HStack(spacing: 2) {
                    ForEach(pack.indices, id: \.self) { i in
                        Rectangle()
                            .fill(Color(hex: pack[i].2))
                            .frame(width: max(0, free * pack[i].1 / total))
                    }
                }
            }
            .frame(height: 10)
            .clipShape(Capsule())
            RopeTabBarFlow {
                ForEach(pack.indices, id: \.self) { i in
                    HStack(spacing: 6) {
                        Circle().fill(Color(hex: pack[i].2)).frame(width: 8, height: 8)
                        Text("\(pack[i].0) \(String(format: "%.1f", pack[i].1)) kg")
                            .font(.system(size: 12, weight: .medium).monospacedDigit())
                            .foregroundStyle(S.inkSoft)
                            .fixedSize()
                    }
                }
            }
        }
    }
}

private struct RopeTabBarProfile: View {
    let p: Double
    let width: Double

    var body: some View {
        RopeTabBarPage(width: width) {
            RopeTabBarLead(text: "Alder Ridge hiking club · since 2023")
            RopeTabBarHero(color: Color(hex: 0x22342B)) {
                EmptyView()
            } content: {
                RopeTabBarProfileCard()
                    .padding(20)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
                    .offset(x: (p - 3) * width * 0.12)
            }
            RopeTabBarSection(text: "Safety")
            RopeTabBarList {
                RopeTabBarRow(title: "Trip plan shared", detail: "With Theo Lund", trailing: "On")
                RopeTabBarDivider()
                RopeTabBarRow(title: "Offline maps", detail: "3 regions · 412 MB", trailing: "Ready")
            }
        }
    }
}

private struct RopeTabBarProfileCard: View {
    private static let stats = [("23", "Summits"), ("412", "km this year"), ("38", "Nights out")]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 14) {
                Text("MR")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(Color(hex: 0x2A140A))
                    .frame(width: 52, height: 52)
                    .background(Circle().fill(S.accent))
                    .overlay(
                        Circle().strokeBorder(
                            LinearGradient(colors: [.white.opacity(0.3), .clear], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.1)),
                            lineWidth: 1
                        )
                    )
                VStack(alignment: .leading, spacing: 0) {
                    Text("Mara Reyes")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(S.ink)
                    Text("Next out: Kestrel Lake, Sat 25 Oct")
                        .font(.system(size: 13, weight: .medium).monospacedDigit())
                        .foregroundStyle(S.ink.opacity(0.75))
                        .lineLimit(1)
                }
            }
            HStack(spacing: 8) {
                ForEach(Self.stats.indices, id: \.self) { k in
                    let stat = Self.stats[k]
                    VStack(spacing: 0) {
                        Text(stat.0)
                            .font(.system(size: 20, weight: .bold).monospacedDigit())
                            .foregroundStyle(S.ink)
                        Text(stat.1)
                            .font(.system(size: 11, weight: .medium))
                            .foregroundStyle(S.inkSoft)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(S.ink.opacity(0.06)))
                }
            }
            .padding(.top, 6)
        }
    }
}
