import SwiftUI
import UIKit

// The museum's sections: the word in the menu with its small count, the one photo that floats
// beside the word and then becomes its page's hero, the colour the menu turns to, and the page
// each one opens on the site underneath.

struct EditorialMenuMorphSection {
    var word: String
    /// The small count set after the word in the menu.
    var count: String
    /// The photo beside the word in the menu and the hero of its page: one and the same.
    var photo: String
    /// The menu's colour while this word is under the finger.
    var tint: UInt32
    var kicker: String
    /// The page's headline, already broken into its two lines.
    var title: [String]
    var caption: String
    var note: String
}

extension EditorialMenuMorphSection {
    static let samples: [EditorialMenuMorphSection] = [
        .init(word: "Exhibitions", count: "12", photo: "photo-1705873653125-75312a404bc4", tint: 0x5b1f1b, kicker: "NOW ON VIEW · HALL C", title: ["The Weight", "of Stillness"], caption: "Marble and plaster, 1880–1930", note: "Until 2 March"),
        .init(word: "Collection", count: "4,120", photo: "photo-1705996952322-b070e7982606", tint: 0x1d3247, kicker: "THE COLLECTION · 4,120 WORKS", title: ["Bodies, Busts", "and Fragments"], caption: "Search by room, maker or century", note: "Open daily"),
        .init(word: "Architecture", count: "1911", photo: "photo-1608590766866-ae8d39690bbf", tint: 0x6a4a1e, kicker: "THE BUILDING · 1911", title: ["A Stair Without", "an End"], caption: "Ninety-one steps by Ada Velde", note: "Tours at 11:00"),
        .init(word: "Visit", count: "10–18", photo: "photo-1550773316-169760ebc0d1", tint: 0x1d4943, kicker: "VISIT · KESTREL QUAY 4", title: ["Light Comes", "Down at Four"], caption: "Tuesday to Sunday, 10:00–18:00", note: "Free on Thursdays"),
        .init(word: "Journal", count: "Nº 12", photo: "photo-1680869839348-4c76c37ce0e0", tint: 0x393826, kicker: "JOURNAL · ISSUE 12", title: ["Notes from the", "Plaster Room"], caption: "Essays, interviews and casts", note: "New every month"),
    ]
}

enum EditorialMenuMorphStyle {
    static let paper: UInt32 = 0xefe9df
    static let ink: UInt32 = 0x16130f
    static let muted: UInt32 = 0x7b6f60
    static let baseTint: UInt32 = 0x151310
    static let cardTone: UInt32 = 0x2a2622
    /// What the hero shows while its photo loads, or if it never does.
    static let heroPlaceholder: UInt32 = 0xd9d0c2

    // The page's own entrance, after a hero lands: one timeline from 0 to 1 over `pageInTime`,
    // linearly, each part easing inside its slice.
    static let pageInTime = 0.9
    static let indexRoll = (0.0, 0.45)
    static let kickerIn = (0.0, 0.4)
    static let titleFrom = 0.06
    static let titleStagger = 0.1
    static let titleSpan = 0.5
    static let captionIn = (0.4, 0.85)
    static let footerIn = (0.5, 1.0)
    static let roll = 14.0

    /// Iowan Old Style, the same face React Native uses on iOS: roman 400, italic, or bold.
    static func display(_ size: CGFloat, italic: Bool = false, bold: Bool = false) -> Font {
        .custom(italic ? "IowanOldStyle-Italic" : bold ? "IowanOldStyle-Bold" : "IowanOldStyle-Roman", fixedSize: size)
    }

    static func displayUIFont(_ size: CGFloat, italic: Bool = false) -> UIFont {
        UIFont(name: italic ? "IowanOldStyle-Italic" : "IowanOldStyle-Roman", size: size) ?? .systemFont(ofSize: size)
    }

    static func sans(_ size: CGFloat, _ weight: Font.Weight) -> Font {
        let name = switch weight {
        case .semibold: "AvenirNext-DemiBold"
        case .bold: "AvenirNext-Bold"
        default: "AvenirNext-Medium"
        }
        return .custom(name, fixedSize: size)
    }

    /// An Unsplash photo, cropped to `width` × `height` points at 3x.
    static func photo(_ id: String, _ width: Double, _ height: Double) -> String {
        "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=\(Int((width * 3).rounded()))&h=\(Int((height * 3).rounded()))&q=80"
    }

    /// Draws `image` "cover" into `rect`, zoomed about its centre: the card and the hero share it,
    /// so the card hands off to the hero pixel for pixel.
    static func drawCover(_ context: GraphicsContext, _ image: UIImage, in rect: CGRect, zoom: Double = 1) {
        let iw0 = image.size.width
        let ih0 = image.size.height
        guard iw0 > 0, ih0 > 0, rect.width > 0, rect.height > 0 else { return }
        let fit = max(rect.width / iw0, rect.height / ih0) * zoom
        let iw = iw0 * fit
        let ih = ih0 * fit
        context.draw(Image(uiImage: image), in: CGRect(x: rect.midX - iw / 2, y: rect.midY - ih / 2, width: iw, height: ih))
    }
}

/// The page, laid out by hand so the menu's card knows exactly where the hero is: the kicker row
/// 74 pt below the safe area, two title lines of 48 pt, the hero 18 pt under them, 20 pt from the
/// sides, at most 44% of the screen tall and never into the footer.
struct EditorialMenuMorphLayout {
    static let titleSize = 40.0
    static let titleLine = 48.0

    let kickerY: Double
    let titleY: Double
    let heroX = 20.0
    let heroY: Double
    let heroW: Double
    let heroH: Double
    let captionY: Double

    init(width: Double, height: Double, top: Double, bottom: Double) {
        kickerY = top + 74
        titleY = kickerY + 28
        heroY = titleY + 2 * Self.titleLine + 18
        heroW = max(1, width - 40)
        let room = height - bottom - 110 - heroY
        heroH = max(1, min(height * 0.44, heroW * 1.08, room))
        captionY = heroY + heroH + 12
    }
}

/// Downloads a photo once and keeps it decoded for every view that shows it. A photo that fails
/// stays nil and the placeholder shows.
enum EditorialMenuMorphPhotos {
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

/// The site under the menu: one section's page, as a quiet editorial layout. Plain values in, so
/// SwiftUI skips it while only the menu moves.
struct EditorialMenuMorphPage: View, Equatable {
    let section: Int
    let previous: Int
    let stage: EditorialMenuMorphStage
    let image: UIImage?
    let pageIn: Double
    let heroShown: Double
    let reduced: Bool

    static func == (a: Self, b: Self) -> Bool {
        a.section == b.section && a.previous == b.previous && a.stage == b.stage && a.image === b.image
            && a.pageIn == b.pageIn && a.heroShown == b.heroShown && a.reduced == b.reduced
    }

    private typealias S = EditorialMenuMorphStyle
    private typealias E = EditorialMenuMorphEase

    private func slice(_ s: (Double, Double)) -> Double { E.out(E.part(pageIn, s)) }

    var body: some View {
        let sections = EditorialMenuMorphSection.samples
        let s = sections[min(max(section, 0), sections.count - 1)]
        let L = stage.layout
        let ink = Color(hex: S.ink)
        let muted = Color(hex: S.muted)
        let kicker = slice(S.kickerIn)
        let caption = slice(S.captionIn)
        ZStack(alignment: .topLeading) {
            Color(hex: S.paper)

            HStack(spacing: 12) {
                Text(s.kicker).font(S.sans(11, .semibold)).kerning(2).foregroundStyle(muted).lineLimit(1)
                    .opacity(kicker)
                    .offset(y: reduced ? 0 : (1 - kicker) * 10)
                Spacer(minLength: 0)
                HStack(spacing: 0) {
                    index
                    Text(" / " + String(format: "%02d", sections.count)).font(S.sans(11, .semibold)).kerning(1.2).foregroundStyle(ink)
                }
            }
            .frame(width: stage.width - 40, height: 16)
            .offset(x: 20, y: L.kickerY)

            ForEach(0..<s.title.count, id: \.self) { i in
                titleLine(s.title[i], row: i)
                    .offset(x: 20, y: L.titleY + Double(i) * EditorialMenuMorphLayout.titleLine)
            }

            Canvas { context, size in
                let rect = CGRect(origin: .zero, size: size)
                context.fill(Path(rect), with: .color(Color(hex: S.heroPlaceholder)))
                if let image { S.drawCover(context, image, in: rect) }
            }
            .frame(width: L.heroW, height: L.heroH)
            .opacity(heroShown)
            .offset(x: L.heroX, y: L.heroY)

            HStack(alignment: .firstTextBaseline, spacing: 12) {
                Text(s.caption).font(S.display(15, italic: true)).foregroundStyle(ink).lineLimit(1)
                Spacer(minLength: 0)
                Text(s.note).font(S.sans(12, .semibold)).foregroundStyle(muted)
            }
            .padding(.bottom, 12)
            .overlay(alignment: .bottom) { Color(hex: S.ink, opacity: 0.35).frame(height: 1 / 3) }
            .frame(width: stage.width - 40)
            .opacity(caption)
            .offset(x: 20, y: L.captionY + (reduced ? 0 : (1 - caption) * 8))

            HStack(spacing: 10) {
                Text("Plan a visit").font(S.sans(15, .semibold)).kerning(0.2).foregroundStyle(ink)
                Path { p in
                    p.move(to: CGPoint(x: 1, y: 6))
                    p.addLine(to: CGPoint(x: 20, y: 6))
                    p.move(to: CGPoint(x: 15, y: 1))
                    p.addLine(to: CGPoint(x: 20, y: 6))
                    p.addLine(to: CGPoint(x: 15, y: 11))
                }
                .stroke(ink, style: StrokeStyle(lineWidth: 1.4, lineCap: .round, lineJoin: .round))
                .frame(width: 22, height: 12)
            }
            .opacity(slice(S.footerIn))
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
            .padding(.leading, 20)
            .padding(.bottom, stage.bottom + 18)
        }
        .frame(width: stage.width, height: stage.height, alignment: .topLeading)
    }

    /// One headline line, rising out of its own clipped baseline after the one above it.
    private func titleLine(_ text: String, row: Int) -> some View {
        let from = S.titleFrom + Double(row) * S.titleStagger
        let e = slice((from, from + S.titleSpan))
        let line = EditorialMenuMorphLayout.titleLine
        return Text(text)
            .font(S.display(EditorialMenuMorphLayout.titleSize))
            .kerning(-0.4)
            .foregroundStyle(Color(hex: S.ink))
            .lineLimit(1)
            .fixedSize()
            .frame(height: line)
            .rotationEffect(.degrees(reduced ? 0 : (1 - e) * 3))
            .offset(y: reduced ? 0 : (1 - e) * line)
            .opacity(reduced ? e : 1)
            .frame(width: stage.width - 40, height: line, alignment: .topLeading)
            .clipped()
    }

    /// The section number: the old one rolls up and out as the new one rolls in from below.
    private var index: some View {
        let e = section == previous ? 1 : slice(S.indexRoll)
        let font = S.sans(11, .semibold)
        let ink = Color(hex: S.ink)
        return ZStack(alignment: .leading) {
            if section != previous {
                Text(String(format: "%02d", previous + 1)).font(font).kerning(1.2).monospacedDigit().foregroundStyle(ink)
                    .opacity(reduced ? 1 - e : 1)
                    .offset(y: reduced ? 0 : -e * S.roll)
            }
            Text(String(format: "%02d", section + 1)).font(font).kerning(1.2).monospacedDigit().foregroundStyle(ink)
                .opacity(reduced ? e : 1)
                .offset(y: reduced ? 0 : (1 - e) * S.roll)
        }
        .frame(height: S.roll)
        .clipped()
    }
}
