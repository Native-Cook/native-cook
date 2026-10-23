import SwiftUI

/// The sample pages under the tabs: a shop's front, two stories and what is new. Replace them
/// with the app's own.
struct OrbitingTopTabsPage: View {
    /// Which of the four sample pages this is.
    var index: Int
    var width: Double
    var height: Double
    /// How far the page's picture trails its text while the page comes in, in points.
    var drift: Double

    /// How far a picture trails its page at most; a photo is this much wider than the page for it.
    static let parallax = 60.0

    private typealias Colors = OrbitingTopTabsSneaker.Colors

    private enum Palette {
        static let ink = Color(hex: 0x111111)
        static let plate = Color(hex: 0xE9E9E6)
        static let olive = Colors(upper: 0x5E6B4A, shade: 0x4A553A, accent: 0x1E1E1C, midsole: 0xF4F2EA, outsole: 0x1B1B1B)
        static let night = Colors(upper: 0x2C2C30, shade: 0x1D1D20, accent: 0x8C8C92, midsole: 0xF4F2EA, outsole: 0x1B1B1B)
        static let rust = Colors(upper: 0x6B3A2E, shade: 0x4F2A21, accent: 0xE9DCC8, midsole: 0xF4F2EA, outsole: 0xC9A26B)
        static let rose = Colors(upper: 0xF2BCCB, shade: 0xE79DB3, accent: 0xFFFFFF, midsole: 0xF8F4EE, outsole: 0xC9A26B)
        static let slate = Colors(upper: 0x5B6770, shade: 0x46515A, accent: 0xE8E4DA, midsole: 0xF4F2EA, outsole: 0x26282B)
    }

    private static let margin = 20.0
    private static let archivePhoto = URL(string: "https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=920&h=880&q=80")
    private static let trailPhoto = URL(string: "https://images.unsplash.com/photo-1526772662000-3f88f10405ff?auto=format&fit=crop&w=920&h=800&q=80")
    private static let archiveText =
        "ARCHIVE REVIVAL pairs washed denim, hooded fleece and track pants from the nineties with new cuts and trims: old favourites and fresh pieces in one wardrobe that talks across the decades."
    private static let trailText =
        "The Ridge Runner has landed. A lugged sole that grips, a ripstop upper and a pull tab at the heel, in two colours: moss or slate. Which one are you taking up the hill?"

    private static let shelf: [(colors: Colors, year: String)] = [
        (Palette.night, "2019"), (Palette.rust, "2020"), (Palette.rose, "2020"), (Palette.slate, "2021"),
    ]
    private static let shelfShoe = 96.0
    private static let shelfStep = 136.0
    /// The first shoe of the shelf is cut by the left edge.
    private static let shelfStart = -38.0
    private static let newIn: [(colors: Colors, name: String)] = [
        (Palette.slate, "RIDGE RUNNER"), (Palette.rose, "COURT LOW"), (Palette.rust, "GUM SOLE 74"), (Palette.olive, "FIELD TRAINER"),
    ]

    var body: some View {
        ZStack(alignment: .topLeading) {
            Color.white
            switch index {
            case 0: newIn
            case 1: browse
            case 2: archive
            default: trail
            }
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .clipped()
    }

    // MARK: Pages

    private var newIn: some View {
        let cell = (width - Self.margin * 3) / 2
        return VStack(spacing: 20) {
            row("THIS WEEK", "8 Styles")
            LazyVGrid(columns: [GridItem(.fixed(cell), spacing: Self.margin), GridItem(.fixed(cell))], spacing: 28) {
                ForEach(Self.newIn, id: \.name) { shoe in
                    VStack(spacing: 10) {
                        OrbitingTopTabsSneaker(width: cell - 16, colors: shoe.colors)
                        caps(shoe.name)
                    }
                }
            }
        }
        .padding(.top, 24)
    }

    private var browse: some View {
        let shoe = min(300, width - 60)
        return ZStack(alignment: .topLeading) {
            OrbitingTopTabsSneaker(width: shoe, colors: Palette.olive)
                .offset(x: (width - shoe) / 2 + drift, y: height * 0.28 - shoe * OrbitingTopTabsSneaker.ratio / 2)
            row("JUST DROPPED", "Shop All")
                .offset(y: height * 0.61)
            ForEach(Array(Self.shelf.enumerated()), id: \.offset) { slot, item in
                VStack(spacing: 14) {
                    OrbitingTopTabsSneaker(width: Self.shelfShoe, colors: item.colors)
                    Text(item.year)
                        .font(.system(size: 8))
                        .foregroundStyle(Palette.ink)
                        .frame(height: 10)
                }
                .offset(x: Self.shelfStart + Double(slot) * Self.shelfStep, y: height * 0.73)
            }
        }
        .frame(width: width, height: height, alignment: .topLeading)
    }

    private var archive: some View {
        VStack(alignment: .leading, spacing: 12) {
            photo(Self.archivePhoto, height: height * 0.6875)
                .overlay(alignment: .bottomLeading) {
                    caps("SHOP NOW", color: .white)
                        .underline()
                        .padding(.leading, Self.margin)
                        .padding(.bottom, 14)
                }
            story(Self.archiveText)
        }
    }

    private var trail: some View {
        let shoe = min(300, width - 60)
        return ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 12) {
                photo(Self.trailPhoto, height: height * 0.625)
                story(Self.trailText)
            }
            // The top of the next shoe, cut by the bar.
            OrbitingTopTabsSneaker(width: shoe, colors: Palette.slate)
                .offset(x: (width - shoe) / 2, y: height - 46)
        }
        .frame(width: width, height: height, alignment: .topLeading)
    }

    // MARK: Parts

    /// A photo behind a window as wide as the page. The photo is wider, and trails the page.
    private func photo(_ url: URL?, height: Double) -> some View {
        AsyncImage(url: url) { image in
            image.resizable().scaledToFill()
        } placeholder: {
            Palette.plate
        }
        .frame(width: width + Self.parallax, height: height)
        .clipped()
        .offset(x: drift - Self.parallax)
        .frame(width: width, height: height, alignment: .leading)
        .background(Palette.plate)
        .clipped()
        .accessibilityHidden(true)
    }

    private func row(_ title: String, _ link: String) -> some View {
        HStack {
            caps(title)
            Spacer(minLength: 8)
            Text(link)
                .font(.system(size: 10))
                .foregroundStyle(Palette.ink)
        }
        .padding(.horizontal, Self.margin)
        .frame(width: width)
    }

    private func caps(_ text: String, color: Color = Palette.ink) -> Text {
        Text(text)
            .font(.system(size: 9, weight: .semibold))
            .tracking(1)
            .foregroundStyle(color)
    }

    private func story(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 15, weight: .semibold))
            .tracking(-0.3)
            .foregroundStyle(Palette.ink)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, Self.margin)
            .frame(width: width, alignment: .leading)
    }
}

/// A sneaker seen from the side, toe to the left, drawn with paths so that it sits on the white
/// page without a photo's rectangle.
struct OrbitingTopTabsSneaker: View {
    struct Colors {
        var upper: Color
        /// The toe cap and the lace panel.
        var shade: Color
        /// The heel and the stripes.
        var accent: Color
        var midsole: Color
        var outsole: Color

        init(upper: UInt32, shade: UInt32, accent: UInt32, midsole: UInt32, outsole: UInt32) {
            self.upper = Color(hex: upper)
            self.shade = Color(hex: shade)
            self.accent = Color(hex: accent)
            self.midsole = Color(hex: midsole)
            self.outsole = Color(hex: outsole)
        }
    }

    var width: Double
    var colors: Colors

    /// The drawing's box is 300 × 160.
    static let ratio = 160.0 / 300.0

    private static let outsole = path("M22 131 Q13 140 25 147 L268 147 Q283 146 281 131 Z")
    private static let midsole = path("M20 118 Q9 126 22 134 L281 134 Q287 120 280 103 L196 112 Q120 122 60 118 Z")
    private static let upper = path(
        "M20 118 Q22 96 60 88 Q118 76 148 48 L160 34 Q168 30 176 40 Q200 66 236 54 L258 36 Q268 34 270 46 Q278 74 280 103 L196 112 Q120 122 60 118 Z"
    )
    private static let toe = path("M20 118 Q22 96 60 88 Q78 84 92 82 Q84 102 96 119 Q76 119 60 118 Z")
    private static let heel = path("M236 54 L258 36 Q268 34 270 46 Q278 74 280 103 L232 108 Q244 80 236 54 Z")
    private static let panel = path("M100 80 Q130 70 150 46 L160 34 Q168 30 176 40 L184 50 Q160 80 122 96 Z")
    private static let stripes = path("M150 113 L186 66 L198 70 L164 113 Z M178 112 L208 72 L218 76 L190 111 Z")
    private static let collar = path("M176 40 Q200 66 236 54 Q206 78 180 52 Z")
    private static let laces = path("M112 80 L126 90 M124 72 L140 82 M136 62 L153 72 M147 52 L165 61")
    private static let inside = Color(hex: 0x16161A)
    private static let lace = Color(hex: 0xF6F4EE)

    var body: some View {
        Canvas { context, size in
            context.scaleBy(x: size.width / 300, y: size.width / 300)
            context.fill(Path(ellipseIn: CGRect(x: 14, y: 143, width: 276, height: 14)), with: .color(.black.opacity(0.07)))
            context.fill(Self.outsole, with: .color(colors.outsole))
            context.fill(Self.midsole, with: .color(colors.midsole))
            context.fill(Self.upper, with: .color(colors.upper))
            context.fill(Self.toe, with: .color(colors.shade))
            context.fill(Self.panel, with: .color(colors.shade))
            context.fill(Self.heel, with: .color(colors.accent))
            context.fill(Self.stripes, with: .color(colors.accent))
            context.fill(Self.collar, with: .color(Self.inside))
            context.stroke(Self.laces, with: .color(Self.lace), style: StrokeStyle(lineWidth: 3, lineCap: .round))
        }
        .frame(width: width, height: width * Self.ratio)
        .accessibilityHidden(true)
    }

    /// Reads the outlines above: absolute M, L, Q and Z, each with one set of numbers.
    private static func path(_ data: String) -> Path {
        var path = Path()
        var command: Character = "Z"
        var numbers: [Double] = []
        func draw() {
            switch command {
            case "M" where numbers.count == 2: path.move(to: CGPoint(x: numbers[0], y: numbers[1]))
            case "L" where numbers.count == 2: path.addLine(to: CGPoint(x: numbers[0], y: numbers[1]))
            case "Q" where numbers.count == 4:
                path.addQuadCurve(to: CGPoint(x: numbers[2], y: numbers[3]), control: CGPoint(x: numbers[0], y: numbers[1]))
            default: break
            }
            numbers = []
        }
        for word in data.split(separator: " ") {
            var word = word
            if let first = word.first, first.isLetter {
                draw()
                command = first
                if first == "Z" { path.closeSubpath() }
                word = word.dropFirst()
            }
            if let number = Double(word) { numbers.append(number) }
        }
        draw()
        return path
    }
}
