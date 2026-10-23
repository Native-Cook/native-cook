import SwiftUI
import UIKit

// The four pages of "Slow Departures", a photo journal of trains and window seats: the journal
// feed, one entry, the route drawn as a map and the writer's profile. They are still pictures of
// an app, laid out at the full size of the screen; the switcher shrinks and turns them.

struct SwitcherDeckNavPageInfo {
    var title: String
    /// Background of the page, also used for the switcher's chip icon.
    var tint: Color
    var ink: Color
}

extension SwitcherDeckNavPageInfo {
    static let samples: [SwitcherDeckNavPageInfo] = [
        .init(title: "Journal", tint: Color(hex: 0xf2ede4), ink: Color(hex: 0x1d1a16)),
        .init(title: "Window seat", tint: Color(hex: 0x12110f), ink: Color(hex: 0xf1ece3)),
        .init(title: "Route map", tint: Color(hex: 0xdfe6e4), ink: Color(hex: 0x24302f)),
        .init(title: "Profile", tint: Color(hex: 0x17212b), ink: Color(hex: 0xefe7da)),
    ]
}

/// The page with index `index` (0 journal, 1 entry, 2 map, 3 profile), drawn at the screen's size.
/// Its inputs are plain values, so SwiftUI skips it while the deck moves it every frame.
struct SwitcherDeckNavPage: View, Equatable {
    let index: Int
    let width: CGFloat
    let height: CGFloat
    let top: CGFloat
    let bottom: CGFloat

    var body: some View {
        Group {
            switch index {
            case 0: SwitcherDeckNavJournalPage(width: width, top: top, bottom: bottom)
            case 1: SwitcherDeckNavEntryPage(width: width, height: height, top: top, bottom: bottom)
            case 2: SwitcherDeckNavMapPage(width: width, top: top, bottom: bottom)
            default: SwitcherDeckNavProfilePage(width: width, top: top, bottom: bottom)
            }
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .clipped()
    }
}

// MARK: - Type

enum SwitcherDeckNavType {
    static func sans(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        .custom(sansName(weight), fixedSize: size)
    }

    static func serif(_ size: CGFloat, bold: Bool = false, italic: Bool = false) -> Font {
        .custom(serifName(bold: bold, italic: italic), fixedSize: size)
    }

    static func sansName(_ weight: Font.Weight) -> String {
        switch weight {
        case .bold, .heavy, .black: "AvenirNext-Bold"
        case .semibold: "AvenirNext-DemiBold"
        case .medium: "AvenirNext-Medium"
        default: "AvenirNext-Regular"
        }
    }

    static func serifName(bold: Bool, italic: Bool) -> String {
        bold ? (italic ? "IowanOldStyle-BoldItalic" : "IowanOldStyle-Bold") : (italic ? "IowanOldStyle-Italic" : "IowanOldStyle-Roman")
    }

    /// The extra space between lines that gives a font the line height React Native sets.
    static func spacing(_ name: String, _ size: CGFloat, lineHeight: CGFloat) -> CGFloat {
        let natural = UIFont(name: name, size: size)?.lineHeight ?? size * 1.2
        return max(0, lineHeight - natural)
    }
}

private typealias T = SwitcherDeckNavType

// MARK: - Photos

private func switcherDeckNavPhotoURL(_ id: String, _ width: CGFloat) -> String {
    "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=\(Int((width * 3).rounded()))&q=80"
}

/// Downloads a photo once and keeps it decoded for every page that shows it.
enum SwitcherDeckNavPhotos {
    private static let cache = NSCache<NSString, UIImage>()

    static func cached(_ address: String) -> UIImage? { cache.object(forKey: address as NSString) }

    static func load(_ address: String) async -> UIImage? {
        if let hit = cached(address) { return hit }
        guard let url = URL(string: address),
              let (data, _) = try? await URLSession.shared.data(from: url),
              let image = UIImage(data: data)
        else { return nil }
        let ready = await image.byPreparingForDisplay() ?? image
        cache.setObject(ready, forKey: address as NSString)
        return ready
    }
}

/// An Unsplash photo cropped to fill its frame, fading in over 250 ms once it arrives.
private struct SwitcherDeckNavPhoto: View {
    let id: String
    let width: CGFloat
    let height: CGFloat
    var radius: CGFloat = 0
    var placeholder: Color = .clear

    @State private var image: UIImage?

    var body: some View {
        let address = switcherDeckNavPhotoURL(id, width)
        ZStack {
            placeholder
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: width, height: height)
                    .clipped()
                    .transition(.opacity.animation(.linear(duration: 0.25)))
            }
        }
        .frame(width: width, height: height)
        .clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
        .task(id: address) {
            if image == nil, let hit = SwitcherDeckNavPhotos.cached(address) {
                image = hit
                return
            }
            let loaded = await SwitcherDeckNavPhotos.load(address)
            withAnimation(.linear(duration: 0.25)) { image = loaded }
        }
    }
}

// Train station photos (s) and airplane window photos (w), each used once.
private enum S {
    static let hero = "photo-1553184257-604db3e574a8"
    static let tall = "photo-1532105956626-9569c03602f6"
    static let dusk = "photo-1693572322416-9ea4b36482ce"
    static let stop = "photo-1527839321757-ad3a2f2be351"
    static let arch = "photo-1495313196544-7d1adf4e628f"
    static let hall = "photo-1604212399401-5fd807a65e18"
    static let rails = "photo-1521075821520-ae40128c1ea0"
}

private enum W {
    static let hero = "photo-1543797414-a0c3ad076f7c"
    static let a = "photo-1488085061387-422e29b40080"
    static let b = "photo-1545132147-d037e6c54cfd"
    static let c = "photo-1603277103691-756354934c2d"
    static let d = "photo-1507812984078-917a274065be"
    static let e = "photo-1591536813111-b6dccb6f67e0"
    static let f = "photo-1542640662-fd5f54cc23b8"
}

// MARK: - SVG paths

/// A path from SVG path data with absolute M, L, H, V, C, Q and Z commands, the ones the drawings use.
enum SwitcherDeckNavSVG {
    static func path(_ data: String) -> Path {
        var path = Path()
        var tokens: [String] = []
        var number = ""
        func flush() {
            if !number.isEmpty { tokens.append(number); number = "" }
        }
        for ch in data {
            if ch.isLetter {
                flush()
                tokens.append(String(ch))
            } else if ch == " " || ch == "," {
                flush()
            } else if ch == "-" && !number.isEmpty && number.last != "e" {
                flush()
                number.append(ch)
            } else {
                number.append(ch)
            }
        }
        flush()
        var i = 0
        var command = "M"
        var current = CGPoint.zero
        func next() -> CGFloat {
            defer { i += 1 }
            return CGFloat(Double(tokens[i]) ?? 0)
        }
        while i < tokens.count {
            if let first = tokens[i].first, first.isLetter {
                command = tokens[i]
                i += 1
                if command == "Z" || command == "z" {
                    path.closeSubpath()
                    continue
                }
            }
            switch command {
            case "M":
                current = CGPoint(x: next(), y: next())
                path.move(to: current)
                command = "L"
            case "L":
                current = CGPoint(x: next(), y: next())
                path.addLine(to: current)
            case "H":
                current.x = next()
                path.addLine(to: current)
            case "V":
                current.y = next()
                path.addLine(to: current)
            case "C":
                let c1 = CGPoint(x: next(), y: next())
                let c2 = CGPoint(x: next(), y: next())
                current = CGPoint(x: next(), y: next())
                path.addCurve(to: current, control1: c1, control2: c2)
            case "Q":
                let c = CGPoint(x: next(), y: next())
                current = CGPoint(x: next(), y: next())
                path.addQuadCurve(to: current, control: c)
            default:
                i += 1
            }
        }
        return path
    }
}

// MARK: - Shared pieces

/// The small pill at the bottom of every page that the switcher is pulled up from. It sits
/// `SwitcherDeckNavHandleMetrics.bottom` pt above the bottom safe area.
private struct SwitcherDeckNavHandle: View {
    let bottom: CGFloat
    let ink: Color

    var body: some View {
        Capsule()
            .fill(ink)
            .frame(width: 54, height: SwitcherDeckNavHandleMetrics.height)
            .opacity(0.5)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
            .padding(.bottom, bottom + SwitcherDeckNavHandleMetrics.bottom)
    }
}

/// Where the handle pill sits; the demo's virtual finger pulls its centre.
enum SwitcherDeckNavHandleMetrics {
    static let bottom: CGFloat = 12
    static let height: CGFloat = 5
}

/// Text with React Native's line height for its font.
private struct SwitcherDeckNavText: View {
    let text: String
    let font: String
    let size: CGFloat
    var lineHeight: CGFloat? = nil
    var color: Color
    var kerning: CGFloat = 0

    var body: some View {
        let spacing = lineHeight.map { T.spacing(font, size, lineHeight: $0) } ?? 0
        Text(text)
            .font(.custom(font, fixedSize: size))
            .kerning(kerning)
            .lineSpacing(spacing)
            .padding(.vertical, spacing / 2)
            .foregroundStyle(color)
    }
}

private func kicker(_ text: String, _ color: Color) -> some View {
    Text(text).font(T.sans(11, .bold)).kerning(1.6).foregroundStyle(color)
}

private func meta(_ text: String, _ color: Color) -> some View {
    Text(text).font(T.sans(11, .bold)).kerning(1.4).foregroundStyle(color)
}

private func statLabel(_ text: String, _ color: Color = Color(hex: 0x8a8073)) -> some View {
    Text(text.uppercased()).font(T.sans(11, .semibold)).kerning(0.6).foregroundStyle(color)
}

private func statValue(_ text: String, _ color: Color) -> some View {
    Text(text).font(T.sans(17, .semibold)).monospacedDigit().foregroundStyle(color)
}

private func tileTitle(_ text: String, _ color: Color, size: CGFloat = 17) -> some View {
    Text(text).font(T.serif(size, bold: true)).foregroundStyle(color)
}

private func tileMeta(_ text: String, _ color: Color = Color(hex: 0x8a8073)) -> some View {
    Text(text).font(T.sans(12, .medium)).foregroundStyle(color)
}

private struct SwitcherDeckNavHairlines: View {
    let color: Color

    var body: some View {
        let hair = 1 / UIScreen.main.scale
        VStack(spacing: 0) {
            color.frame(height: hair)
            Spacer(minLength: 0)
            color.frame(height: hair)
        }
    }
}

// MARK: - Journal

private struct SwitcherDeckNavJournalPage: View {
    let width: CGFloat
    let top: CGFloat
    let bottom: CGFloat

    var body: some View {
        let ink = Color(hex: 0x1d1a16)
        let rust = Color(hex: 0xa0502f)
        let inner = width - 40
        let half = (inner - 12) / 2
        ZStack(alignment: .topLeading) {
            Color(hex: 0xf2ede4)
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    kicker("WEEK 41 · THE AUTUMN LINE", rust)
                    Spacer(minLength: 0)
                    Circle()
                        .fill(ink)
                        .frame(width: 34, height: 34)
                        .overlay(Text("IM").font(T.serif(13, bold: true)).foregroundStyle(Color(hex: 0xf2ede4)))
                }
                Text("Slow Departures")
                    .font(T.serif(40, bold: true))
                    .kerning(-0.8)
                    .foregroundStyle(ink)
                    .padding(.top, 6)
                Text("Notes from platforms and window seats")
                    .font(T.serif(16, italic: true))
                    .foregroundStyle(Color(hex: 0x6f665b))
                    .padding(.top, 2)
                SwitcherDeckNavPhoto(id: S.hero, width: inner, height: inner * 0.56, radius: 18, placeholder: Color(hex: 0xddd5c8))
                    .padding(.top, 20)
                meta("06:12 · LISBOURNE CENTRAL", rust).padding(.top, 14)
                SwitcherDeckNavText(text: "The first train hums before the light does", font: T.serifName(bold: true, italic: false), size: 22, lineHeight: 27, color: ink, kerning: -0.2)
                    .padding(.top, 4)
                SwitcherDeckNavText(
                    text: "Steam off the coffee cart, a conductor counting under his breath, and the departures board flipping to green one line at a time.",
                    font: T.serifName(bold: false, italic: false), size: 15, lineHeight: 21, color: Color(hex: 0x6f665b)
                )
                .lineLimit(2)
                .padding(.top, 6)
                HStack(alignment: .top, spacing: 12) {
                    tile(S.tall, "Calder Bay", "8 Oct · 4 photos", half, ink)
                    tile(S.dusk, "Platform 9, dusk", "6 Oct · 7 photos", half, ink)
                }
                .padding(.top, 22)
            }
            .frame(width: inner, alignment: .leading)
            .padding(.top, top + 10)
            .padding(.horizontal, 20)

            LinearGradient(colors: [Color(hex: 0xf2ede4, opacity: 0), Color(hex: 0xf2ede4)], startPoint: .top, endPoint: .bottom)
                .frame(height: 150)
                .frame(maxHeight: .infinity, alignment: .bottom)
            SwitcherDeckNavHandle(bottom: bottom, ink: ink)
        }
    }

    private func tile(_ id: String, _ title: String, _ info: String, _ side: CGFloat, _ ink: Color) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            SwitcherDeckNavPhoto(id: id, width: side, height: side, radius: 14, placeholder: Color(hex: 0x3a3631))
            tileTitle(title, ink)
            tileMeta(info)
        }
        .frame(width: side, alignment: .leading)
    }
}

// MARK: - Entry

private struct SwitcherDeckNavEntryPage: View {
    let width: CGFloat
    let height: CGFloat
    let top: CGFloat
    let bottom: CGFloat

    var body: some View {
        let ink = Color(hex: 0xf1ece3)
        let heroH = height * 0.54
        let thumb = (width - 40 - 16) / 3
        let base = Color(hex: 0x12110f)
        ZStack(alignment: .topLeading) {
            base
            VStack(alignment: .leading, spacing: 0) {
                ZStack {
                    SwitcherDeckNavPhoto(id: W.hero, width: width, height: heroH)
                    LinearGradient(
                        stops: [
                            .init(color: base.opacity(0.55), location: 0),
                            .init(color: base.opacity(0), location: 0.22),
                            .init(color: base.opacity(0), location: 0.55),
                            .init(color: base, location: 1),
                        ],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                }
                .frame(width: width, height: heroH)
                VStack(alignment: .leading, spacing: 8) {
                    meta("ENTRY 17 · 9 OCTOBER", Color(hex: 0xe3a27d))
                    SwitcherDeckNavText(text: "Window seat, over the Vessmark coast", font: T.serifName(bold: true, italic: false), size: 31, lineHeight: 36, color: ink, kerning: -0.4)
                }
                .padding(.horizontal, 20)
                .padding(.top, -118)
                HStack(alignment: .center) {
                    stat("Altitude", "11,280 m", ink)
                    Spacer(minLength: 0)
                    stat("Local time", "07:40", ink)
                    Spacer(minLength: 0)
                    stat("Flight", "2 h 15", ink)
                }
                .padding(.vertical, 14)
                .background(SwitcherDeckNavHairlines(color: Color(hex: 0xf1ece3, opacity: 0.22)))
                .padding(.horizontal, 20)
                .padding(.top, 20)
                SwitcherDeckNavText(
                    text: "The wing caught the sun before the cabin did. Below, the fjords were still ink, and someone three rows back had already fallen asleep against the glass.",
                    font: T.serifName(bold: false, italic: false), size: 16, lineHeight: 24, color: Color(hex: 0xbdb5a8)
                )
                .padding(.horizontal, 20)
                .padding(.top, 18)
                HStack(spacing: 8) {
                    ForEach([W.a, W.b, W.c], id: \.self) { id in
                        SwitcherDeckNavPhoto(id: id, width: thumb, height: thumb, radius: 14, placeholder: Color(hex: 0x3a3631))
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 20)
            }
            .frame(width: width, alignment: .leading)

            HStack {
                ZStack {
                    Circle().fill(base.opacity(0.45))
                    Circle().strokeBorder(ink.opacity(0.35), lineWidth: 1 / UIScreen.main.scale)
                    SwitcherDeckNavSVG.path("M9 2 L3 9 L9 16")
                        .stroke(ink, style: StrokeStyle(lineWidth: 2.2, lineCap: .round, lineJoin: .round))
                        .frame(width: 12, height: 18)
                }
                .frame(width: 40, height: 40)
                Spacer(minLength: 0)
                Text("SEAT 34A")
                    .font(T.sans(11, .bold))
                    .kerning(1.2)
                    .foregroundStyle(base)
                    .padding(.horizontal, 12)
                    .frame(height: 28)
                    .background(Capsule().fill(ink.opacity(0.9)))
            }
            .padding(.horizontal, 20)
            .padding(.top, top + 6)

            SwitcherDeckNavHandle(bottom: bottom, ink: ink)
        }
    }

    private func stat(_ label: String, _ value: String, _ ink: Color) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            statLabel(label)
            statValue(value, ink)
        }
    }
}

// MARK: - Map

// The drawn map: coastlines, the rail line, a flight arc and the stops, in a 400 × 520 box.
private enum SwitcherDeckNavMap {
    static let land = [
        "M -10 40 C 60 30 120 70 150 120 C 175 165 140 210 170 250 C 200 290 260 270 300 300 C 340 330 330 400 380 430 L 410 430 L 410 -10 L -10 -10 Z",
        "M -10 360 C 40 340 80 380 90 420 C 100 460 70 500 120 530 L -10 530 Z",
        "M 220 470 C 250 440 300 450 320 480 C 335 505 300 530 260 530 L 215 530 C 205 510 205 490 220 470 Z",
    ].map(SwitcherDeckNavSVG.path)
    static let rail = SwitcherDeckNavSVG.path("M 48 96 C 90 120 120 160 140 210 C 158 255 200 280 250 300 C 300 320 320 360 340 400")
    static let flight = SwitcherDeckNavSVG.path("M 340 400 C 300 470 160 470 70 440")
    static let plane = SwitcherDeckNavSVG.path("M -9 0 L 9 0 M 2 -7 L 5 0 L 2 7 M -7 -3 L -8 0 L -7 3")
    static let stops: [(CGFloat, CGFloat, String, Bool)] = [
        (48, 96, "Lisbourne", true),
        (140, 210, "Hallow Junction", true),
        (250, 300, "Vessmark", false),
        (340, 400, "Orrin Fjord", false),
        (70, 440, "Calder Bay", true),
    ]
}

private struct SwitcherDeckNavMapPage: View {
    let width: CGFloat
    let top: CGFloat
    let bottom: CGFloat

    var body: some View {
        let ink = Color(hex: 0x24302f)
        ZStack(alignment: .topLeading) {
            Color(hex: 0xdfe6e4)
            VStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: 4) {
                    kicker("ROUTE · THE AUTUMN LINE", Color(hex: 0x3f6f69))
                    Text("2,140 km, six stops")
                        .font(T.serif(32, bold: true))
                        .kerning(-0.8)
                        .foregroundStyle(ink)
                        .padding(.top, 6)
                }
                .padding(.horizontal, 20)
                map(ink: ink)
                    .frame(width: width, height: width * 1.3)
                    .padding(.top, 6)
            }
            .padding(.top, top + 10)

            nextCard(ink: ink)
                .padding(.horizontal, 16)
                .frame(maxHeight: .infinity, alignment: .bottom)
                .padding(.bottom, bottom + 36)
            SwitcherDeckNavHandle(bottom: bottom, ink: ink)
        }
    }

    private func map(ink: Color) -> some View {
        Canvas { context, size in
            context.scaleBy(x: size.width / 400, y: size.width / 400)
            let grid = Color(hex: 0xc9d3d0)
            for i in 0..<9 {
                var line = Path()
                line.move(to: CGPoint(x: i * 50, y: 0))
                line.addLine(to: CGPoint(x: i * 50, y: 520))
                context.stroke(line, with: .color(grid), lineWidth: 0.7)
            }
            for i in 0..<11 {
                var line = Path()
                line.move(to: CGPoint(x: 0, y: i * 50))
                line.addLine(to: CGPoint(x: 400, y: i * 50))
                context.stroke(line, with: .color(grid), lineWidth: 0.7)
            }
            for land in SwitcherDeckNavMap.land {
                context.fill(land, with: .color(Color(hex: 0xefe9dc)))
                context.stroke(land, with: .color(Color(hex: 0xb9b29f)), lineWidth: 1)
            }
            let rust = Color(hex: 0xb4562f)
            context.stroke(SwitcherDeckNavMap.rail, with: .color(ink), style: StrokeStyle(lineWidth: 5, lineCap: .round))
            context.stroke(SwitcherDeckNavMap.rail, with: .color(Color(hex: 0xefe9dc)), style: StrokeStyle(lineWidth: 2, lineCap: .butt, dash: [7, 7]))
            context.stroke(SwitcherDeckNavMap.flight, with: .color(rust), style: StrokeStyle(lineWidth: 2, lineCap: .round, dash: [2, 6]))
            var plane = context
            plane.translateBy(x: 205, y: 456)
            plane.rotate(by: .degrees(-168))
            plane.stroke(SwitcherDeckNavMap.plane, with: .color(rust), style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
            for (i, stop) in SwitcherDeckNavMap.stops.enumerated() {
                let r: CGFloat = i == 2 ? 9 : 6.5
                let dot = Path(ellipseIn: CGRect(x: stop.0 - r, y: stop.1 - r, width: r * 2, height: r * 2))
                context.fill(dot, with: .color(i == 2 ? rust : Color(hex: 0xfbf8f1)))
                context.stroke(dot, with: .color(ink), lineWidth: 2.2)
                label(context, stop.2, size: 14, weight: .semibold, ink: ink, x: stop.3 ? stop.0 + 14 : stop.0 - 14, baseline: stop.1 + 5, align: stop.3 ? 0 : 1)
            }
            context.fill(Path(CGRect(x: 300, y: 28, width: 70, height: 1.5)), with: .color(ink))
            label(context, "100 km", size: 11, weight: .regular, ink: ink, x: 335, baseline: 20, align: 0.5)
        }
    }

    /// SVG text: set from its baseline, to the left (0), middle (0.5) or right (1) of `x`.
    private func label(_ context: GraphicsContext, _ text: String, size: CGFloat, weight: Font.Weight, ink: Color, x: CGFloat, baseline: CGFloat, align: CGFloat) {
        let resolved = context.resolve(Text(text).font(T.sans(size, weight)).foregroundStyle(ink))
        let measured = resolved.measure(in: CGSize(width: 1000, height: 1000))
        let base = resolved.firstBaseline(in: measured)
        context.draw(resolved, in: CGRect(x: x - measured.width * align, y: baseline - base, width: measured.width, height: measured.height))
    }

    private func nextCard(ink: Color) -> some View {
        HStack(spacing: 14) {
            SwitcherDeckNavPhoto(id: S.stop, width: 64, height: 64, radius: 14)
            VStack(alignment: .leading, spacing: 2) {
                statLabel("Next stop")
                tileTitle("Vessmark Central", ink, size: 18)
                tileMeta("14:05 · Platform 3")
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            statValue("38 min", Color(hex: 0xb4562f))
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(Color(hex: 0xfbf8f1))
                .shadow(color: Color(hex: 0x24302f, opacity: 0.16), radius: 15, y: 10)
        )
    }
}

// MARK: - Profile

private struct SwitcherDeckNavProfilePage: View {
    let width: CGFloat
    let top: CGFloat
    let bottom: CGFloat

    var body: some View {
        let ink = Color(hex: 0xefe7da)
        let soft = Color(hex: 0x8fa0ad)
        let cell = (width - 40 - 8) / 3
        let grid = [S.arch, W.d, S.hall, W.e, S.rails, W.f]
        ZStack(alignment: .topLeading) {
            Color(hex: 0x17212b)
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 16) {
                    Circle()
                        .fill(Color(hex: 0xe3a27d))
                        .frame(width: 76, height: 76)
                        .overlay(Text("IM").font(T.serif(28, bold: true)).foregroundStyle(Color(hex: 0x17212b)))
                    VStack(alignment: .leading, spacing: 2) {
                        SwitcherDeckNavText(text: "Ines Marlow", font: T.serifName(bold: true, italic: false), size: 26, lineHeight: 30, color: ink, kerning: -0.4)
                        tileMeta("Collecting quiet platforms since 2019", soft)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                HStack(alignment: .center) {
                    number("41", "trips", ink, soft)
                    Spacer(minLength: 0)
                    number("1.3k", "photos", ink, soft)
                    Spacer(minLength: 0)
                    number("18.2k", "km by rail", ink, soft)
                }
                .padding(.vertical, 16)
                .background(SwitcherDeckNavHairlines(color: Color(hex: 0xefe7da, opacity: 0.2)))
                .padding(.top, 26)
                kicker("SAVED FRAMES", Color(hex: 0xe3a27d))
                    .padding(.top, 26)
                    .padding(.bottom, 12)
                VStack(spacing: 4) {
                    ForEach(0..<2, id: \.self) { row in
                        HStack(spacing: 4) {
                            ForEach(0..<3, id: \.self) { column in
                                SwitcherDeckNavPhoto(id: grid[row * 3 + column], width: cell, height: cell * 1.18, radius: 6, placeholder: Color(hex: 0x24303b))
                            }
                        }
                    }
                }
                VStack(alignment: .leading, spacing: 3) {
                    tileTitle("Next trip", ink, size: 16)
                    tileMeta("Orrin Fjord by the night train · 3 Nov", soft)
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .fill(ink.opacity(0.07))
                        .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(ink.opacity(0.16), lineWidth: 1 / UIScreen.main.scale))
                )
                .padding(.top, 16)
            }
            .frame(width: width - 40, alignment: .leading)
            .padding(.top, top + 18)
            .padding(.horizontal, 20)

            SwitcherDeckNavHandle(bottom: bottom, ink: ink)
        }
    }

    private func number(_ value: String, _ label: String, _ ink: Color, _ soft: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value).font(T.serif(28, bold: true)).monospacedDigit().foregroundStyle(ink)
            statLabel(label, soft)
        }
    }
}
