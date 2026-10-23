import Foundation

// The 26 balls and the order they stand in on the loop. Mirrors ballCatalog.ts and
// chooseBallOrder.ts. Adding a ball is a data edit here, plus its shell texture.

enum ChooseYourBallID: String, CaseIterable, Identifiable {
    case poke, great, ultra, premier, `repeat`, timer, nest, net, dive, heal, quick, dusk, luxury
    case master, safari, level, lure, moon, friend, love, heavy, fast, sport, dream, beast, cherish

    var id: String { rawValue }
    var spec: ChooseYourBallSpec { ChooseYourBallCatalog.specs[self]! }
}

/// How the upper shell is painted on the flat thumbnail. The 3D ball takes its paint from a texture.
enum ChooseYourBallArt {
    case solid, stripes, wedge, band, crest, emblem
}

struct ChooseYourBallSpec {
    struct Effects {
        /// The ball's own light: tints the backdrop and the two rim lights.
        let aura: UInt32
        /// Core of the release flash, and the colour of its sparks. Not used by this screen.
        let flash: UInt32
        let spark: UInt32
    }

    let label: String
    let art: ChooseYourBallArt
    /// Base and highlight of the upper shell, sRGB hex.
    let shell: (UInt32, UInt32)
    /// Colour of the thumbnail's stripes, wedges, band or crest.
    let accent: UInt32
    /// Centre of the flat graphic's release button.
    let button: UInt32
    let fx: Effects
    /// Catch multiplier; conditional balls carry their best case.
    let catchMod: Double
    /// 1 standard, 2 situational, 3 rare, 4 cherish.
    let tier: Int
    /// What the ball is for; the third chip is one word lifted out of it.
    let blurb: String
}

enum ChooseYourBallCatalog {
    /// The six balls that open the loop, then the rest in catalog order (chooseBallOrder.ts).
    static let order: [ChooseYourBallID] = {
        let featured: [ChooseYourBallID] = [.poke, .great, .ultra, .dusk, .master, .cherish]
        return featured + ChooseYourBallID.allCases.filter { !featured.contains($0) }
    }()

    static let specs: [ChooseYourBallID: ChooseYourBallSpec] = [
        .poke: ChooseYourBallSpec(
            label: "Poké Ball", art: .solid, shell: (0xFF5A5F, 0xD91022),
            accent: 0xFFD539, button: 0xF2F5F8,
            fx: .init(aura: 0x8BE5FF, flash: 0xFFFFFF, spark: 0xFFD539),
            catchMod: 1, tier: 1,
            blurb: "The standard ball of every trainer."
        ),
        .great: ChooseYourBallSpec(
            label: "Great Ball", art: .stripes, shell: (0x3994FF, 0x3152D5),
            accent: 0xBD3131, button: 0xF2F5F8,
            fx: .init(aura: 0x3994FF, flash: 0xEAF6FF, spark: 0x39D5FF),
            catchMod: 1.5, tier: 1,
            blurb: "A step up from the Poké Ball, noticeably better."
        ),
        .ultra: ChooseYourBallSpec(
            label: "Ultra Ball", art: .wedge, shell: (0x738394, 0x52626A),
            accent: 0xFFD539, button: 0xF2F5F8,
            fx: .init(aura: 0xFFD539, flash: 0xFFF6DC, spark: 0xFFF65A),
            catchMod: 2, tier: 1,
            blurb: "High-grade gear for hard-to-catch Pokémon."
        ),
        .premier: ChooseYourBallSpec(
            label: "Premier Ball", art: .solid, shell: (0xFFFFFF, 0xDECDF6),
            accent: 0xDECDF6, button: 0xF2F5F8,
            fx: .init(aura: 0xDECDF6, flash: 0xFFFFFF, spark: 0xFFFFFF),
            catchMod: 1, tier: 2,
            blurb: "A commemorative ball, as effective as a Poké Ball."
        ),
        .`repeat`: ChooseYourBallSpec(
            label: "Repeat Ball", art: .band, shell: (0xFF9439, 0xDE5A39),
            accent: 0xF6EE39, button: 0xF2F5F8,
            fx: .init(aura: 0xF6EE39, flash: 0xFFF8E2, spark: 0xDECD39),
            catchMod: 3.5, tier: 2,
            blurb: "Very strong on species already in your Pokédex."
        ),
        .timer: ChooseYourBallSpec(
            label: "Timer Ball", art: .band, shell: (0xFFFFFF, 0xDECDF6),
            accent: 0xDE5A39, button: 0xF2F5F8,
            fx: .init(aura: 0xDE5A39, flash: 0xFFFFFF, spark: 0xFF9439),
            catchMod: 4, tier: 2,
            blurb: "The longer the battle runs, the better it works."
        ),
        .nest: ChooseYourBallSpec(
            label: "Nest Ball", art: .wedge, shell: (0x7BC539, 0x629441),
            accent: 0xE6E683, button: 0xF2F5F8,
            fx: .init(aura: 0x7BC539, flash: 0xF4FFE4, spark: 0xB4F639),
            catchMod: 3.5, tier: 2,
            blurb: "The lower the Pokémon's level, the better it works."
        ),
        .net: ChooseYourBallSpec(
            label: "Net Ball", art: .stripes, shell: (0x4AB4C5, 0x4A7B8B),
            accent: 0x4A5252, button: 0xF2F5F8,
            fx: .init(aura: 0x4AB4C5, flash: 0xE4FFFB, spark: 0x39E6EE),
            catchMod: 3.5, tier: 2,
            blurb: "A specialist for Water- and Bug-type Pokémon."
        ),
        .dive: ChooseYourBallSpec(
            label: "Dive Ball", art: .band, shell: (0x8BCDFF, 0x5AA4FF),
            accent: 0xDECDF6, button: 0xF2F5F8,
            fx: .init(aura: 0x8BCDFF, flash: 0xE6FAFF, spark: 0x94EEF6),
            catchMod: 3.5, tier: 2,
            blurb: "Made for Pokémon that live underwater."
        ),
        .heal: ChooseYourBallSpec(
            label: "Heal Ball", art: .crest, shell: (0xFF6AD5, 0xEE52C5),
            accent: 0xFFF6D5, button: 0xF2F5F8,
            fx: .init(aura: 0xFF6AD5, flash: 0xFFF0F7, spark: 0xFFB4E6),
            catchMod: 1, tier: 2,
            blurb: "Fully heals the Pokémon it catches."
        ),
        .quick: ChooseYourBallSpec(
            label: "Quick Ball", art: .wedge, shell: (0x73BDFF, 0x298BD5),
            accent: 0xFFFF00, button: 0xF2F5F8,
            fx: .init(aura: 0x73BDFF, flash: 0xF0FEFF, spark: 0xFFFF00),
            catchMod: 5, tier: 2,
            blurb: "Extremely strong if thrown on the very first turn."
        ),
        .dusk: ChooseYourBallSpec(
            label: "Dusk Ball", art: .wedge, shell: (0x525252, 0x404040),
            accent: 0x62D539, button: 0x62D539,
            fx: .init(aura: 0x41AC41, flash: 0xDFFFE9, spark: 0x94FF52),
            catchMod: 3, tier: 2,
            blurb: "Shines in dark caves and at night."
        ),
        .luxury: ChooseYourBallSpec(
            label: "Luxury Ball", art: .band, shell: (0x7B7B83, 0x4A5252),
            accent: 0xD59C31, button: 0xD59C31,
            fx: .init(aura: 0xD59C31, flash: 0xFFF6E0, spark: 0xFFEE52),
            catchMod: 1, tier: 2,
            blurb: "The Pokémon inside grows friendly faster."
        ),
        .master: ChooseYourBallSpec(
            label: "Master Ball", art: .emblem, shell: (0xB439F6, 0x7331CD),
            accent: 0xE620C5, button: 0xF2F5F8,
            fx: .init(aura: 0xB439F6, flash: 0xF7ECFF, spark: 0xE620C5),
            catchMod: 255, tier: 3,
            blurb: "Catches any wild Pokémon without fail."
        ),
        .safari: ChooseYourBallSpec(
            label: "Safari Ball", art: .stripes, shell: (0x4A9462, 0x105A31),
            accent: 0xC5B439, button: 0xF2F5F8,
            fx: .init(aura: 0xC5B439, flash: 0xF6FFE6, spark: 0xDED57B),
            catchMod: 1.5, tier: 3,
            blurb: "A special ball used only in the Safari Zone."
        ),
        .level: ChooseYourBallSpec(
            label: "Level Ball", art: .band, shell: (0xDE4A39, 0x9C6239),
            accent: 0xEEBD94, button: 0xF2F5F8,
            fx: .init(aura: 0xEEBD94, flash: 0xFFDEBD, spark: 0xDE945A),
            catchMod: 8, tier: 3,
            blurb: "Strong when your Pokémon is the higher level."
        ),
        .lure: ChooseYourBallSpec(
            label: "Lure Ball", art: .band, shell: (0x4AB4DE, 0x2994CD),
            accent: 0xDE5A6A, button: 0xF2F5F8,
            fx: .init(aura: 0x4AB4DE, flash: 0xEAFBFF, spark: 0xFFA4B4),
            catchMod: 3, tier: 3,
            blurb: "Made for Pokémon hooked while fishing."
        ),
        .moon: ChooseYourBallSpec(
            label: "Moon Ball", art: .crest, shell: (0x7B8B9C, 0x627394),
            accent: 0xF6CD41, button: 0xF2F5F8,
            fx: .init(aura: 0xF6CD41, flash: 0xFFE6A4, spark: 0x4AC5EE),
            catchMod: 4, tier: 3,
            blurb: "Works best on species that evolve by Moon Stone."
        ),
        .friend: ChooseYourBallSpec(
            label: "Friend Ball", art: .crest, shell: (0x8BC539, 0x7BA439),
            accent: 0xC55A4A, button: 0xF2F5F8,
            fx: .init(aura: 0x8BC539, flash: 0xF2FFE9, spark: 0xC55A4A),
            catchMod: 1, tier: 3,
            blurb: "The Pokémon it catches starts out friendly."
        ),
        .love: ChooseYourBallSpec(
            label: "Love Ball", art: .crest, shell: (0xDE83B4, 0xBD5A9C),
            accent: 0xFFC5E6, button: 0xF2F5F8,
            fx: .init(aura: 0xDE83B4, flash: 0xFFF0F7, spark: 0xFFC5E6),
            catchMod: 8, tier: 3,
            blurb: "Very strong on the same species, opposite gender."
        ),
        .heavy: ChooseYourBallSpec(
            label: "Heavy Ball", art: .stripes, shell: (0x94A4B4, 0x738394),
            accent: 0x527BDE, button: 0xF2F5F8,
            fx: .init(aura: 0x527BDE, flash: 0xEFF4F8, spark: 0x83ACEE),
            catchMod: 1, tier: 3,
            blurb: "The heavier the Pokémon, the better it works."
        ),
        .fast: ChooseYourBallSpec(
            label: "Fast Ball", art: .wedge, shell: (0xEE8B41, 0xCD6A29),
            accent: 0xF6CD39, button: 0xF2F5F8,
            fx: .init(aura: 0xF6CD39, flash: 0xFFFBE6, spark: 0xFFB483),
            catchMod: 4, tier: 3,
            blurb: "Made for Pokémon that are quick to flee."
        ),
        .sport: ChooseYourBallSpec(
            label: "Sport Ball", art: .stripes, shell: (0xFF9439, 0xDE5A39),
            accent: 0xFFD539, button: 0xF2F5F8,
            fx: .init(aura: 0xFFD539, flash: 0xFFF3E0, spark: 0xFF9439),
            catchMod: 1.5, tier: 3,
            blurb: "The ball of the Bug-Catching Contest."
        ),
        .dream: ChooseYourBallSpec(
            label: "Dream Ball", art: .crest, shell: (0xFFB4D5, 0xE69CBD),
            accent: 0xFF62A4, button: 0xF2F5F8,
            fx: .init(aura: 0xFFB4D5, flash: 0xFBF0FF, spark: 0xFF62A4),
            catchMod: 4, tier: 3,
            blurb: "Brought back from dreams in the Entree Forest."
        ),
        .beast: ChooseYourBallSpec(
            label: "Beast Ball", art: .wedge, shell: (0x416AD5, 0x3939B4),
            accent: 0x41ACFF, button: 0xF2F5F8,
            fx: .init(aura: 0x41ACFF, flash: 0xE6FEFF, spark: 0x8BCDFF),
            catchMod: 5, tier: 3,
            blurb: "Purpose-built for catching Ultra Beasts."
        ),
        .cherish: ChooseYourBallSpec(
            label: "Cherish Ball", art: .crest, shell: (0xFF4131, 0xCD3920),
            accent: 0xFF9C9C, button: 0xF2F5F8,
            fx: .init(aura: 0xFF4131, flash: 0xFFF3F3, spark: 0xFF9C9C),
            catchMod: 1, tier: 4,
            blurb: "A rare ball reserved for event Pokémon."
        ),
    ]
}
