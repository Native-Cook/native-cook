package com.ngocdevv.nativecook.blocks.gestures.chooseyourball

// The 26 balls (ballCatalog.ts). Colours are sRGB 0xRRGGBB, read off the official sprites. The
// order of the entries is the catalog's own; the carousel's order is `chooseBallOrder()`.

/** How the upper shell is painted on the 2D thumbnail (PokeBallGraphic). */
internal enum class BallArt { SOLID, STRIPES, WEDGE, BAND, CREST, EMBLEM }

/**
 * The light a ball gives off. This screen uses `aura`, for the page's ground and the two rim
 * lights; `flash` (the release flash) and `spark` (sparks and embers) are the catalog's data for
 * screens that open or throw the ball.
 */
internal class BallFx(val aura: Int, val flash: Int, val spark: Int)

private const val WHITE_BUTTON = 0xF2F5F8

/**
 * `tier`: 1 standard, 2 situational, 3 rare, 4 cherish. `catchMod` is the best-case multiplier.
 * `shell` and `shellShade` are the base and the shade of the upper shell, `accent` its marking
 * and `button` the centre of the release button on the 2D graphic.
 */
internal enum class BallId(
    val label: String,
    val tier: Int,
    val catchMod: Double,
    val art: BallArt,
    val shell: Int,
    val shellShade: Int,
    val accent: Int,
    val button: Int,
    val fx: BallFx,
    val blurb: String,
) {
    // Tier 1: the standard line.
    POKE(
        label = "Poké Ball", tier = 1, catchMod = 1.0, art = BallArt.SOLID,
        shell = 0xFF5A5F, shellShade = 0xD91022, accent = 0xFFD539, button = WHITE_BUTTON,
        fx = BallFx(aura = 0x8BE5FF, flash = 0xFFFFFF, spark = 0xFFD539),
        blurb = "The standard ball of every trainer.",
    ),
    GREAT(
        label = "Great Ball", tier = 1, catchMod = 1.5, art = BallArt.STRIPES,
        shell = 0x3994FF, shellShade = 0x3152D5, accent = 0xBD3131, button = WHITE_BUTTON,
        fx = BallFx(aura = 0x3994FF, flash = 0xEAF6FF, spark = 0x39D5FF),
        blurb = "A step up from the Poké Ball, noticeably better.",
    ),
    ULTRA(
        label = "Ultra Ball", tier = 1, catchMod = 2.0, art = BallArt.WEDGE,
        shell = 0x738394, shellShade = 0x52626A, accent = 0xFFD539, button = WHITE_BUTTON,
        fx = BallFx(aura = 0xFFD539, flash = 0xFFF6DC, spark = 0xFFF65A),
        blurb = "High-grade gear for hard-to-catch Pokémon.",
    ),
    // Tier 2: situational balls.
    PREMIER(
        label = "Premier Ball", tier = 2, catchMod = 1.0, art = BallArt.SOLID,
        shell = 0xFFFFFF, shellShade = 0xDECDF6, accent = 0xDECDF6, button = WHITE_BUTTON,
        fx = BallFx(aura = 0xDECDF6, flash = 0xFFFFFF, spark = 0xFFFFFF),
        blurb = "A commemorative ball, as effective as a Poké Ball.",
    ),
    REPEAT(
        label = "Repeat Ball", tier = 2, catchMod = 3.5, art = BallArt.BAND,
        shell = 0xFF9439, shellShade = 0xDE5A39, accent = 0xF6EE39, button = WHITE_BUTTON,
        fx = BallFx(aura = 0xF6EE39, flash = 0xFFF8E2, spark = 0xDECD39),
        blurb = "Very strong on species already in your Pokédex.",
    ),
    TIMER(
        label = "Timer Ball", tier = 2, catchMod = 4.0, art = BallArt.BAND,
        shell = 0xFFFFFF, shellShade = 0xDECDF6, accent = 0xDE5A39, button = WHITE_BUTTON,
        fx = BallFx(aura = 0xDE5A39, flash = 0xFFFFFF, spark = 0xFF9439),
        blurb = "The longer the battle runs, the better it works.",
    ),
    NEST(
        label = "Nest Ball", tier = 2, catchMod = 3.5, art = BallArt.WEDGE,
        shell = 0x7BC539, shellShade = 0x629441, accent = 0xE6E683, button = WHITE_BUTTON,
        fx = BallFx(aura = 0x7BC539, flash = 0xF4FFE4, spark = 0xB4F639),
        blurb = "The lower the Pokémon's level, the better it works.",
    ),
    NET(
        label = "Net Ball", tier = 2, catchMod = 3.5, art = BallArt.STRIPES,
        shell = 0x4AB4C5, shellShade = 0x4A7B8B, accent = 0x4A5252, button = WHITE_BUTTON,
        fx = BallFx(aura = 0x4AB4C5, flash = 0xE4FFFB, spark = 0x39E6EE),
        blurb = "A specialist for Water- and Bug-type Pokémon.",
    ),
    DIVE(
        label = "Dive Ball", tier = 2, catchMod = 3.5, art = BallArt.BAND,
        shell = 0x8BCDFF, shellShade = 0x5AA4FF, accent = 0xDECDF6, button = WHITE_BUTTON,
        fx = BallFx(aura = 0x8BCDFF, flash = 0xE6FAFF, spark = 0x94EEF6),
        blurb = "Made for Pokémon that live underwater.",
    ),
    HEAL(
        label = "Heal Ball", tier = 2, catchMod = 1.0, art = BallArt.CREST,
        shell = 0xFF6AD5, shellShade = 0xEE52C5, accent = 0xFFF6D5, button = WHITE_BUTTON,
        fx = BallFx(aura = 0xFF6AD5, flash = 0xFFF0F7, spark = 0xFFB4E6),
        blurb = "Fully heals the Pokémon it catches.",
    ),
    QUICK(
        label = "Quick Ball", tier = 2, catchMod = 5.0, art = BallArt.WEDGE,
        shell = 0x73BDFF, shellShade = 0x298BD5, accent = 0xFFFF00, button = WHITE_BUTTON,
        fx = BallFx(aura = 0x73BDFF, flash = 0xF0FEFF, spark = 0xFFFF00),
        blurb = "Extremely strong if thrown on the very first turn.",
    ),
    DUSK(
        label = "Dusk Ball", tier = 2, catchMod = 3.0, art = BallArt.WEDGE,
        shell = 0x525252, shellShade = 0x404040, accent = 0x62D539, button = 0x62D539,
        fx = BallFx(aura = 0x41AC41, flash = 0xDFFFE9, spark = 0x94FF52),
        blurb = "Shines in dark caves and at night.",
    ),
    LUXURY(
        label = "Luxury Ball", tier = 2, catchMod = 1.0, art = BallArt.BAND,
        shell = 0x7B7B83, shellShade = 0x4A5252, accent = 0xD59C31, button = 0xD59C31,
        fx = BallFx(aura = 0xD59C31, flash = 0xFFF6E0, spark = 0xFFEE52),
        blurb = "The Pokémon inside grows friendly faster.",
    ),
    // Tier 3: rare balls.
    MASTER(
        label = "Master Ball", tier = 3, catchMod = 255.0, art = BallArt.EMBLEM,
        shell = 0xB439F6, shellShade = 0x7331CD, accent = 0xE620C5, button = WHITE_BUTTON,
        fx = BallFx(aura = 0xB439F6, flash = 0xF7ECFF, spark = 0xE620C5),
        blurb = "Catches any wild Pokémon without fail.",
    ),
    SAFARI(
        label = "Safari Ball", tier = 3, catchMod = 1.5, art = BallArt.STRIPES,
        shell = 0x4A9462, shellShade = 0x105A31, accent = 0xC5B439, button = WHITE_BUTTON,
        fx = BallFx(aura = 0xC5B439, flash = 0xF6FFE6, spark = 0xDED57B),
        blurb = "A special ball used only in the Safari Zone.",
    ),
    LEVEL(
        label = "Level Ball", tier = 3, catchMod = 8.0, art = BallArt.BAND,
        shell = 0xDE4A39, shellShade = 0x9C6239, accent = 0xEEBD94, button = WHITE_BUTTON,
        fx = BallFx(aura = 0xEEBD94, flash = 0xFFDEBD, spark = 0xDE945A),
        blurb = "Strong when your Pokémon is the higher level.",
    ),
    LURE(
        label = "Lure Ball", tier = 3, catchMod = 3.0, art = BallArt.BAND,
        shell = 0x4AB4DE, shellShade = 0x2994CD, accent = 0xDE5A6A, button = WHITE_BUTTON,
        fx = BallFx(aura = 0x4AB4DE, flash = 0xEAFBFF, spark = 0xFFA4B4),
        blurb = "Made for Pokémon hooked while fishing.",
    ),
    MOON(
        label = "Moon Ball", tier = 3, catchMod = 4.0, art = BallArt.CREST,
        shell = 0x7B8B9C, shellShade = 0x627394, accent = 0xF6CD41, button = WHITE_BUTTON,
        fx = BallFx(aura = 0xF6CD41, flash = 0xFFE6A4, spark = 0x4AC5EE),
        blurb = "Works best on species that evolve by Moon Stone.",
    ),
    FRIEND(
        label = "Friend Ball", tier = 3, catchMod = 1.0, art = BallArt.CREST,
        shell = 0x8BC539, shellShade = 0x7BA439, accent = 0xC55A4A, button = WHITE_BUTTON,
        fx = BallFx(aura = 0x8BC539, flash = 0xF2FFE9, spark = 0xC55A4A),
        blurb = "The Pokémon it catches starts out friendly.",
    ),
    LOVE(
        label = "Love Ball", tier = 3, catchMod = 8.0, art = BallArt.CREST,
        shell = 0xDE83B4, shellShade = 0xBD5A9C, accent = 0xFFC5E6, button = WHITE_BUTTON,
        fx = BallFx(aura = 0xDE83B4, flash = 0xFFF0F7, spark = 0xFFC5E6),
        blurb = "Very strong on the same species, opposite gender.",
    ),
    HEAVY(
        label = "Heavy Ball", tier = 3, catchMod = 1.0, art = BallArt.STRIPES,
        shell = 0x94A4B4, shellShade = 0x738394, accent = 0x527BDE, button = WHITE_BUTTON,
        fx = BallFx(aura = 0x527BDE, flash = 0xEFF4F8, spark = 0x83ACEE),
        blurb = "The heavier the Pokémon, the better it works.",
    ),
    FAST(
        label = "Fast Ball", tier = 3, catchMod = 4.0, art = BallArt.WEDGE,
        shell = 0xEE8B41, shellShade = 0xCD6A29, accent = 0xF6CD39, button = WHITE_BUTTON,
        fx = BallFx(aura = 0xF6CD39, flash = 0xFFFBE6, spark = 0xFFB483),
        blurb = "Made for Pokémon that are quick to flee.",
    ),
    SPORT(
        label = "Sport Ball", tier = 3, catchMod = 1.5, art = BallArt.STRIPES,
        shell = 0xFF9439, shellShade = 0xDE5A39, accent = 0xFFD539, button = WHITE_BUTTON,
        fx = BallFx(aura = 0xFFD539, flash = 0xFFF3E0, spark = 0xFF9439),
        blurb = "The ball of the Bug-Catching Contest.",
    ),
    DREAM(
        label = "Dream Ball", tier = 3, catchMod = 4.0, art = BallArt.CREST,
        shell = 0xFFB4D5, shellShade = 0xE69CBD, accent = 0xFF62A4, button = WHITE_BUTTON,
        fx = BallFx(aura = 0xFFB4D5, flash = 0xFBF0FF, spark = 0xFF62A4),
        blurb = "Brought back from dreams in the Entree Forest.",
    ),
    BEAST(
        label = "Beast Ball", tier = 3, catchMod = 5.0, art = BallArt.WEDGE,
        shell = 0x416AD5, shellShade = 0x3939B4, accent = 0x41ACFF, button = WHITE_BUTTON,
        fx = BallFx(aura = 0x41ACFF, flash = 0xE6FEFF, spark = 0x8BCDFF),
        blurb = "Purpose-built for catching Ultra Beasts.",
    ),
    // Tier 4: gifted only.
    CHERISH(
        label = "Cherish Ball", tier = 4, catchMod = 1.0, art = BallArt.CREST,
        shell = 0xFF4131, shellShade = 0xCD3920, accent = 0xFF9C9C, button = WHITE_BUTTON,
        fx = BallFx(aura = 0xFF4131, flash = 0xFFF3F3, spark = 0xFF9C9C),
        blurb = "A rare ball reserved for event Pokémon.",
    );

    /** The texture of the upper shell: `assets/chooseyourball/shells/<assetName>.png`. */
    val assetName: String get() = name.lowercase()
}

/** Balls that lead the carousel, in this order; the rest follow in catalog order. */
internal val FEATURED_BALLS = listOf(
    BallId.POKE, BallId.GREAT, BallId.ULTRA, BallId.DUSK, BallId.MASTER, BallId.CHERISH,
)
