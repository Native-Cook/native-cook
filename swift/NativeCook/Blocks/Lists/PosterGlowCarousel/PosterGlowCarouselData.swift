import CoreText
import SwiftUI
import UIKit

// MARK: - Palette and type

enum PosterGlowCarouselColor {
    static let background = Color(hex: 0x2B2930)
    static let tabBar = Color(hex: 0x26262C)
    static let card = Color(hex: 0x1B1B1F)
    static let ink = Color.white
    static let inkSoft = Color(hex: 0xD9DCE0)
    static let inkMute = Color(hex: 0xB5B5BB)
    static let star = Color(hex: 0xF3E30F)
    static let starOff = Color(hex: 0x858DA0)
    static let progress = Color(hex: 0xFC0001)
    static let progressTrack = Color(hex: 0xDCD8D6)
    static let plus = Color(hex: 0x565152)
    static let posterPlaceholder = Color(hex: 0x35333B)
    static let badge = Color(hex: 0xE2231A)
    static let pickerTop = Color(hex: 0x2A136B)
    static let pickerBottom = Color(hex: 0x221C3E)
    static let pickerTeal = Color(hex: 0x1A5D6E)
    static let pickerMagenta = Color(hex: 0x8A2A4E)
    static let pickerPlum = Color(hex: 0x4C184C)

    static func color(_ value: UInt32, opacity: Double = 1) -> Color {
        Color(.sRGB,
              red: Double((value >> 16) & 0xFF) / 255,
              green: Double((value >> 8) & 0xFF) / 255,
              blue: Double(value & 0xFF) / 255,
              opacity: opacity)
    }
}

enum PosterGlowCarouselFont {
    static let regular = "Poppins-Regular"
    static let medium = "Poppins-Medium"
    static let semibold = "Poppins-SemiBold"
    static let display = "Anton-Regular"
    static let bebas = "BebasNeue-Regular"
    static let dmserif = "DMSerifDisplay-Regular"
    static let archivo = "ArchivoBlack-Regular"
    static let marcellus = "Marcellus-Regular"
    static let abril = "AbrilFatface-Regular"
    static let italiana = "Italiana-Regular"

    static let all = [regular, medium, semibold, display, bebas, dmserif, archivo, marcellus, abril, italiana]

    static func register() {
        _ = registered
    }

    private static let registered: Void = {
        for name in all {
            guard let url = Bundle.main.url(forResource: "PosterGlowCarousel-\(name)", withExtension: "ttf") else {
                #if DEBUG
                assertionFailure("Missing Poster Glow Carousel font: \(name)")
                #endif
                continue
            }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()
}

enum PosterGlowCarouselAssets {
    static func uiImage(named name: String) -> UIImage {
        if let image = UIImage(named: name) { return image }
        if let path = Bundle.main.path(forResource: name, ofType: nil),
           let image = UIImage(contentsOfFile: path) { return image }
        #if DEBUG
        assertionFailure("Missing Poster Glow Carousel image: \(name)")
        #endif
        return UIImage()
    }

    static func image(named name: String) -> Image {
        Image(uiImage: uiImage(named: name))
    }

    static func avatar(_ name: String) -> Image {
        image(named: "PosterGlowCarousel-avatar-\(name).jpg")
    }

    static func hero() -> Image {
        image(named: "PosterGlowCarousel-hero.jpg")
    }

    static func poster(_ name: String) -> Image {
        image(named: "PosterGlowCarousel-posters-\(name).jpg")
    }

    static func still(_ name: String) -> Image {
        image(named: "PosterGlowCarousel-stills-\(name).jpg")
    }

    static func mood(_ name: String) -> Image {
        image(named: "PosterGlowCarousel-mood-\(name).jpg")
    }

    static func game(_ name: String) -> Image {
        image(named: "PosterGlowCarousel-games-\(name).jpg")
    }
}

enum PosterGlowCarouselPosterStyle: String {
    case bebas
    case anton
    case dmserif
    case archivo
    case marcellus
    case abril
    case italiana

    var fontName: String {
        switch self {
        case .bebas: return PosterGlowCarouselFont.bebas
        case .anton: return PosterGlowCarouselFont.display
        case .dmserif: return PosterGlowCarouselFont.dmserif
        case .archivo: return PosterGlowCarouselFont.archivo
        case .marcellus: return PosterGlowCarouselFont.marcellus
        case .abril: return PosterGlowCarouselFont.abril
        case .italiana: return PosterGlowCarouselFont.italiana
        }
    }

    var fontSize: Double {
        switch self {
        case .bebas: return 23
        case .anton, .dmserif, .abril: return 17
        case .archivo: return 12.5
        case .marcellus: return 11.5
        case .italiana: return 14
        }
    }

    var lineHeight: Double {
        switch self {
        case .bebas: return 22
        case .anton: return 19
        case .dmserif, .abril: return 18
        case .archivo, .marcellus: return 14
        case .italiana: return 16
        }
    }

    var tracking: Double {
        switch self {
        case .bebas: return 1
        case .anton: return 0.2
        case .dmserif, .archivo: return -0.2
        case .marcellus: return 2.4
        case .abril: return 0
        case .italiana: return 2.6
        }
    }

    var uppercased: Bool {
        switch self {
        case .bebas, .anton, .archivo, .marcellus, .italiana: return true
        case .dmserif, .abril: return false
        }
    }
}

struct PosterGlowCarouselPoster: Identifiable {
    let id: String
    let title: String
    let image: String
    let style: PosterGlowCarouselPosterStyle
    let tint: UInt32
    let credit: String
    let top: Bool
}

struct PosterGlowCarouselStill: Identifiable {
    let id: String
    let title: String
    let meta: String
    let image: String
    let progress: Double
}

struct PosterGlowCarouselProfile: Identifiable {
    let id: String
    let name: String
    let image: String?
}

struct PosterGlowCarouselTall: Identifiable {
    let id: String
    let word: String
    let image: String
    let style: PosterGlowCarouselPosterStyle
    let top: Bool
}

enum PosterGlowCarouselSectionKind {
    case posters([PosterGlowCarouselPoster])
    case wide([PosterGlowCarouselStill])
    case tall([PosterGlowCarouselTall])
}

struct PosterGlowCarouselSection: Identifiable {
    let id: String
    let label: String
    let gap: Double
    let kind: PosterGlowCarouselSectionKind
    let labelX: Double?
    let toContent: Double?
}

enum PosterGlowCarouselContent {
    static let appName = "FilmFlow"

    static let profiles: [PosterGlowCarouselProfile] = [
        PosterGlowCarouselProfile(id: "jarvis3d", name: "Jarvis3d", image: "jonas"),
        PosterGlowCarouselProfile(id: "isaiah", name: "Isaiah", image: "isaiah"),
        PosterGlowCarouselProfile(id: "mia", name: "Mia", image: "mia"),
        PosterGlowCarouselProfile(id: "new-profile", name: "New Profile", image: nil),
    ]

    static let hero = (image: "hero", badge: "NOVA STUDIOS", subtitle: "THE IRON QUEEN",
                       lockup: "KINGDOM OF TIDE", title: "Kingdom of Tide",
                       genres: ["Action", "Adventure"])

    private static func poster(_ title: String, _ image: String, _ style: PosterGlowCarouselPosterStyle,
                               _ tint: UInt32, _ credit: String, _ top: Bool = false) -> PosterGlowCarouselPoster {
        PosterGlowCarouselPoster(id: title, title: title, image: image, style: style, tint: tint, credit: credit, top: top)
    }

    static let sections: [PosterGlowCarouselSection] = [
        PosterGlowCarouselSection(id: "now-playing", label: "Now Playing", gap: 0, kind: .posters([
            poster("Into the Rift", "np-1", .bebas, 0xFFD23F, "A FILM BY TOMÁS REYES"),
            poster("Velocity", "np-2", .archivo, 0xFFFFFF, "IN CINEMAS JUNE 14"),
            poster("Iron Colossi", "np-3", .marcellus, 0xEDE6D6, "FROM THE STUDIO OF DUSTLINE"),
            poster("Nebula Misfits 3", "np-4", .bebas, 0xFFE3F6, "THE ROAD GETS WEIRDER"),
        ]), labelX: nil, toContent: nil),
        PosterGlowCarouselSection(id: "continue-watching", label: "Continue Watching", gap: 29, kind: .wide([
            PosterGlowCarouselStill(id: "clash-iii", title: "Clash III", meta: "56 minutes remaining", image: "cw-1", progress: 0.615),
            PosterGlowCarouselStill(id: "kaiju-rising", title: "Kaiju Rising", meta: "24 minutes remaining", image: "cw-2", progress: 0.8),
        ]), labelX: nil, toContent: nil),
        PosterGlowCarouselSection(id: "set-the-mood", label: "Set The Mood", gap: 24, kind: .tall([
            PosterGlowCarouselTall(id: "action", word: "Action", image: "action", style: .anton, top: false),
            PosterGlowCarouselTall(id: "comedy", word: "Comedy", image: "comedy", style: .anton, top: false),
            PosterGlowCarouselTall(id: "drama", word: "Drama", image: "drama", style: .anton, top: false),
        ]), labelX: nil, toContent: nil),
        PosterGlowCarouselSection(id: "trending", label: "Trending", gap: 34, kind: .posters([
            poster("Exfil 2", "tr-1", .anton, 0xF2F6FA, "NO WAY BACK · DECEMBER"),
            poster("Round Twelve", "tr-2", .archivo, 0x16181C, "A FILM BY ADA OKAFOR"),
            poster("The Pact", "tr-3", .marcellus, 0xF1E6C8, "BASED ON A TRUE STORY", true),
            poster("Ember Blade", "tr-4", .dmserif, 0xF0B27A, "ONLY IN CINEMAS"),
        ]), labelX: nil, toContent: nil),
        PosterGlowCarouselSection(id: "for-you", label: "For You", gap: 30, kind: .posters([
            poster("Glitch Hero", "fy-1", .abril, 0xFFFFFF, "THIS SUMMER", true),
            poster("Red Kick", "fy-2", .bebas, 0xFFFFFF, "ONE DOJO · ONE FIGHT"),
            poster("Two Friends", "fy-3", .dmserif, 0xFFFFFF, "A STORY OF SECOND CHANCES"),
            poster("Blue Dash", "fy-4", .abril, 0xFFFFFF, "A FAMILY ADVENTURE", true),
        ]), labelX: nil, toContent: nil),
        PosterGlowCarouselSection(id: "new-on-filmflow", label: "New On FilmFlow", gap: 30, kind: .posters([
            poster("Snow Beast", "new-1", .anton, 0xE2231A, "SOMETHING IS OUT THERE"),
            poster("Questfall", "new-2", .marcellus, 0xFFE7B0, "EVERY LEGEND BEGINS SOMEWHERE", true),
            poster("Tidewater", "new-3", .italiana, 0xDDF6FF, "FROM THE DIRECTOR OF HOLLOW"),
            poster("Elementa", "new-4", .dmserif, 0xFFFFFF, "FALL IN LOVE AFTER DARK"),
        ]), labelX: 11.4, toContent: nil),
        PosterGlowCarouselSection(id: "games-based-on-movies", label: "Games Based On Movies", gap: 30, kind: .tall([
            PosterGlowCarouselTall(id: "rooftop-run-2", word: "Rooftop Run 2", image: "game-1", style: .bebas, top: true),
            PosterGlowCarouselTall(id: "ravenhold", word: "Ravenhold", image: "game-2", style: .marcellus, top: false),
            PosterGlowCarouselTall(id: "dune-patrol", word: "Dune Patrol", image: "game-3", style: .archivo, top: false),
        ]), labelX: 11.4, toContent: 37),
        PosterGlowCarouselSection(id: "documentary", label: "Documentary", gap: 32.2, kind: .posters([
            poster("Golden Boy", "doc-1", .bebas, 0xFFFFFF, "A DOCUMENTARY SERIES"),
            poster("The Final Run", "doc-2", .archivo, 0x1B1B1B, "THE STORY OF A DYNASTY", true),
            poster("Wild Planet", "doc-3", .italiana, 0xFFFFFF, "NARRATED BY HUGH ALDEN"),
            poster("Ice Line", "doc-4", .bebas, 0x0E2A33, "A FILM ABOUT WHAT REMAINS", true),
        ]), labelX: 11.4, toContent: nil),
        PosterGlowCarouselSection(id: "coming-soon", label: "Coming Soon", gap: 30, kind: .posters([
            poster("The Hunter", "cs-1", .anton, 0xEDE6D6, "FEBRUARY 2025"),
            poster("Mercenaries 4", "cs-2", .archivo, 0xFFFFFF, "THE LAST JOB"),
            poster("Azure Shell", "cs-3", .bebas, 0xD6E9FF, "COMING SOON"),
            poster("Ashen Rider", "cs-4", .marcellus, 0xFFD2A6, "RIDE OR BURN", true),
        ]), labelX: 11.4, toContent: nil),
        PosterGlowCarouselSection(id: "award-winners", label: "Award Winners", gap: 35.4, kind: .posters([
            poster("Neon Tide", "aw-1", .italiana, 0xA9F0FF, "WINNER · BEST PICTURE"),
            poster("Lower Floor", "aw-2", .dmserif, 0xFFFFFF, "GRAND JURY PRIZE", true),
            poster("Open Road", "aw-3", .dmserif, 0x3D3029, "THREE FESTIVAL AWARDS", true),
            poster("Painted Smile", "aw-4", .anton, 0xFFE15A, "BEST DIRECTOR · LIDO"),
        ]), labelX: 11.4, toContent: nil),
        PosterGlowCarouselSection(id: "movie-night", label: "Movie Night", gap: 30.8, kind: .posters([
            poster("Bramble Bear", "mn-1", .abril, 0xFFD34E, "A FAMILY FILM"),
            poster("Spark", "mn-2", .archivo, 0xFFFFFF, "GOOD BOY. BIG DAY.", true),
            poster("The Grey Halls", "mn-3", .marcellus, 0xE8E2D0, "PART ONE"),
            poster("Meadow Kite", "mn-4", .abril, 0x2E2A10, "FROM THE MAKERS OF SPARK", true),
        ]), labelX: 11.4, toContent: nil),
        PosterGlowCarouselSection(id: "staff-recommend", label: "Staff Recommend", gap: 34.2, kind: .posters([
            poster("Rain City", "sr-1", .italiana, 0xFFFFFF, "A NOIR IN THREE NIGHTS"),
            poster("Gold Orbit", "sr-2", .bebas, 0xFFF1D6, "STAFF PICK"),
            poster("Last Waltz", "sr-3", .dmserif, 0xF6E7CF, "ONE MORE DANCE"),
            poster("Aurora Wolf", "sr-4", .anton, 0xE9FFF4, "HUNT THE LIGHT", true),
        ]), labelX: 11.4, toContent: nil),
    ]
}

enum PosterGlowCarouselMetrics {
    static let labelLine = 21.0
    static let side = 12.0
    static let heroHeight = 512.0
    static let heroFadeFrom = 0.72
    static let chromeY = 18.0
    static let chromeX = 42.0
    static let chromeSize = 20.0
    static let titleY = 433.0
    static let titleSize = 17.4
    static let genreY = 455.0
    static let genreX = 32.0
    static let genreSize = 14.0
    static let starsY = 471.0
    static let starsX = 36.0
    static let star = 16.5
    static let starPitch = 21.0
    static let rating = 3.5
    static let playX = 358.5
    static let playY = 455.3
    static let play = 41.0
    static let badgeY = 263.0
    static let subtitleY = 280.0
    static let lockupY = 302.0
    static let lockupSize = 33.0
    static let firstLabelY = 529.0
    static let labelSize = 15.7
    static let labelNudge = 4.8
    static let labelToContent = 28.0
    static let tabBar = 64.0
    static let tabIconY = 31.0
    static let tabX = [44.0, 119.0, 198.5, 284.0, 345.0]
    static let tabIcon = [30.0, 24.0, 32.0, 27.0, 35.0]
    static let posterW = 108.5
    static let posterH = 168.0
    static let posterGap = 11.5
    static let posterRadius = 14.0
    static let wideW = 330.0
    static let wideImage = 180.0
    static let wideInfo = 85.0
    static let wideGap = 19.0
    static let wideRadius = 13.0
    static let progress = 3.0
    static let progressInset = 5.7
    static let progressLift = 2.0
    static let wideTitleSize = 19.4
    static let wideTitleNudge = 5.0
    static let wideMetaSize = 12.5
    static let tallW = 203.0
    static let tallH = 307.0
    static let tallGap = 8.5
    static let tallRadius = 14.0
    static let tallWordSize = 31.0
    static let tallWordFromBottom = 29.0

    static let pickerEditRight = 25.0
    static let pickerEditY = 162.5
    static let pickerEditSize = 12.7
    static let pickerTitleY = 209.0
    static let pickerTitleSize = 30.0
    static let pickerAvatar = 135.0
    static let pickerColX = [108.0, 293.0]
    static let pickerRowY = [335.0, 530.5]
    static let pickerNameGap = [95.0, 99.0]
    static let pickerNameDX = 5.0
    static let pickerTitleDX = 5.7
    static let pickerNameSize = 20.0
    static let pickerPlusArm = 56.0
    static let pickerPlusStroke = 7.0
    static let pickerFooterFromBottom = 27.0
    static let pickerFooterSize = 14.6

    static func sectionHeight(_ section: PosterGlowCarouselSection) -> Double {
        switch section.kind {
        case .posters: return posterH
        case .wide: return wideImage + wideInfo
        case .tall: return tallH
        }
    }

    static func feedLayout(_ sections: [PosterGlowCarouselSection]) -> (labels: [Double], tops: [Double], bottom: Double) {
        var labels: [Double] = []
        var tops: [Double] = []
        var label = firstLabelY
        var bottom = 0.0
        for (index, section) in sections.enumerated() {
            if index > 0 { label = bottom + section.gap }
            labels.append(label)
            let top = label + (section.toContent ?? labelToContent)
            tops.append(top)
            bottom = top + sectionHeight(section)
        }
        return (labels, tops, bottom)
    }

    static func pitch(_ kind: PosterGlowCarouselSectionKind) -> Double {
        switch kind {
        case .posters: return posterW + posterGap
        case .wide: return wideW + wideGap
        case .tall: return tallW + tallGap
        }
    }

    static func gap(_ kind: PosterGlowCarouselSectionKind) -> Double {
        switch kind {
        case .posters: return posterGap
        case .wide: return wideGap
        case .tall: return tallGap
        }
    }
}

private extension Color {
    init(hex: UInt32) {
        self.init(.sRGB,
                  red: Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255,
                  opacity: 1)
    }
}
