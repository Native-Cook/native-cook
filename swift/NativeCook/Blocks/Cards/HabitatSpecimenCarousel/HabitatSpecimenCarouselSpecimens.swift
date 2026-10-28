import CoreText
import SwiftUI

// The three specimens and their copy. Mirrors SPECIMENS in HabitatSpecimenCarousel.tsx.

struct HabitatSpecimenCarouselType {
    let label: String
    /// Name in the MaterialCommunityIcons set.
    let icon: String
    let color: UInt32
}

struct HabitatSpecimenCarouselStage {
    let name: String
    let number: String
    let level: Int?
}

struct HabitatSpecimenCarouselSpecimen {
    let name: String
    let number: String
    let species: String
    let weight: String
    let height: String
    let asset: String
    let types: [HabitatSpecimenCarouselType]
    let copy: String
    let stats: [Int]
    let evolution: [HabitatSpecimenCarouselStage]

    static let all: [HabitatSpecimenCarouselSpecimen] = [
        HabitatSpecimenCarouselSpecimen(
            name: "Charizard", number: "006", species: "Flame Pokémon", weight: "90.5 kg", height: "1.7 m",
            asset: "charizard",
            types: [.init(label: "Fire", icon: "fire", color: 0xEAC28C), .init(label: "Flying", icon: "feather", color: 0xC2CBDD)],
            copy: "Charizard flies around the sky in search of powerful opponents. It breathes fire of such great heat that it melts anything. However, it never turns its fiery breath on any opponent weaker than itself.",
            stats: [78, 84, 78, 109, 85, 100],
            evolution: [.init(name: "Charmander", number: "004", level: nil), .init(name: "Charmeleon", number: "005", level: 16), .init(name: "Charizard", number: "006", level: 36)]
        ),
        HabitatSpecimenCarouselSpecimen(
            name: "Blastoise", number: "009", species: "Shellfish Pokémon", weight: "85.5 kg", height: "1.6 m",
            asset: "blastoise",
            types: [.init(label: "Water", icon: "water", color: 0x9CE0ED)],
            copy: "Blastoise has water spouts that protrude from its shell. The water spouts are very accurate. They can shoot bullets of water with enough accuracy to strike empty cans from over 160 feet away.",
            stats: [79, 83, 100, 85, 105, 78],
            evolution: [.init(name: "Squirtle", number: "007", level: nil), .init(name: "Wartortle", number: "008", level: 16), .init(name: "Blastoise", number: "009", level: 36)]
        ),
        HabitatSpecimenCarouselSpecimen(
            name: "Bulbasaur", number: "001", species: "Seed Pokémon", weight: "6.9 kg", height: "0.7 m",
            asset: "bulbasaur",
            types: [.init(label: "Grass", icon: "leaf", color: 0xC2E9A2), .init(label: "Poison", icon: "bottle-tonic-skull", color: 0xD2B4E6)],
            copy: "A strange seed was planted on its back at birth. The plant sprouts and grows with this Pokémon, soaking up sunlight to store energy for the long journey ahead.",
            stats: [45, 49, 49, 65, 65, 45],
            evolution: [.init(name: "Bulbasaur", number: "001", level: nil), .init(name: "Ivysaur", number: "002", level: 16), .init(name: "Venusaur", number: "003", level: 32)]
        ),
    ]

    /// Back light each habitat casts on the creature's silhouette (fire, water, grass).
    static let rimLight: [(Double, Double, Double)] = [(1, 0.55, 0.22), (0.45, 0.85, 1), (0.75, 0.95, 0.45)]
    /// A heart on the favourite button's burst, per element.
    static let favoriteBurst: [UInt32] = [0xF2A766, 0x7FD3EA, 0xA9DD7F]
    /// Element tints in carousel order, repeated so colour interpolation wraps 2 to 0.
    static let elementColors: [UInt32] = [0xF2A766, 0x7FD3EA, 0xA9DD7F, 0xF2A766]
    /// Heart colours per habitat: ember, spray and petal.
    static let hearts: [(Double, Double, Double)] = [(1, 0.6, 0.42), (0.62, 0.89, 1), (0.78, 0.94, 0.56)]
}

/// The MaterialCommunityIcons glyphs this block draws (the font ships with the block).
enum HabitatSpecimenCarouselIcons {
    private static let glyphs: [String: UInt32] = [
        "fire": 983608, "feather": 984787, "water": 984460, "leaf": 983850, "bottle-tonic-skull": 987442,
        "chevron-left": 983361, "heart-outline": 983765, "heart": 983761, "weight-kilogram": 984482,
        "ruler": 984173, "pokeball": 984093, "gesture-tap": 984897, "gesture-tap-hold": 986487,
    ]
    private static let registered: Void = {
        guard let url = Bundle.main.url(forResource: "HabitatSpecimenCarousel-MaterialCommunityIcons", withExtension: "ttf") else { return }
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
    }()

    static func glyph(_ name: String) -> String {
        guard let value = glyphs[name], let scalar = UnicodeScalar(value) else { return "" }
        return String(Character(scalar))
    }

    static func font(_ size: CGFloat) -> Font {
        _ = registered
        return Font.custom("MaterialDesignIcons", fixedSize: size)
    }
}

struct HabitatSpecimenCarouselIcon: View {
    let name: String
    let size: CGFloat
    let color: Color

    var body: some View {
        Text(HabitatSpecimenCarouselIcons.glyph(name))
            .font(HabitatSpecimenCarouselIcons.font(size))
            .foregroundStyle(color)
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}

extension Color {
    /// 0xRRGGBB with a separate alpha, as the reference writes its `#RRGGBBAA` colours.
    init(habitatSC hex: UInt32, _ alpha: Double = 1) {
        self.init(.sRGB, red: Double((hex >> 16) & 0xff) / 255, green: Double((hex >> 8) & 0xff) / 255, blue: Double(hex & 0xff) / 255, opacity: alpha)
    }
}
