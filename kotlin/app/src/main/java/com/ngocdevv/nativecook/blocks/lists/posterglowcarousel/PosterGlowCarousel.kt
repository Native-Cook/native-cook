package com.ngocdevv.nativecook.blocks.lists.posterglowcarousel

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.R
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private object PosterGlowCarouselColors {
    val Background = Color(0xFF2B2930)
    val TabBar = Color(0xFF26262C)
    val Card = Color(0xFF1B1B1F)
    val Ink = Color(0xFFFFFFFF)
    val InkSoft = Color(0xFFD9DCE0)
    val InkMute = Color(0xFFB5B5BB)
    val Star = Color(0xFFF3E30F)
    val StarOff = Color(0xFF858DA0)
    val Progress = Color(0xFFFC0001)
    val ProgressTrack = Color(0xFFDCD8D6)
    val Plus = Color(0xFF565152)
    val PosterPlaceholder = Color(0xFF35333B)
    val Badge = Color(0xFFE2231A)
    val PickerTop = Color(0xFF2A136B)
    val PickerBottom = Color(0xFF221C3E)
    val PickerTeal = Color(0xFF1A5D6E)
    val PickerMagenta = Color(0xFF8A2A4E)
    val PickerPlum = Color(0xFF4C184C)
    val GearHole = Color(0xFF4A4F57)
}

private val PosterGlowCarouselPoppins = FontFamily(
    Font(R.font.posterglowcarousel_poppins_regular, FontWeight.Normal),
    Font(R.font.posterglowcarousel_poppins_medium, FontWeight.Medium),
    Font(R.font.posterglowcarousel_poppins_semibold, FontWeight.SemiBold),
)
private val PosterGlowCarouselAnton = FontFamily(Font(R.font.posterglowcarousel_anton_regular))
private val PosterGlowCarouselBebas = FontFamily(Font(R.font.posterglowcarousel_bebasneue_regular))
private val PosterGlowCarouselDmSerif = FontFamily(Font(R.font.posterglowcarousel_dmserifdisplay_regular))
private val PosterGlowCarouselArchivo = FontFamily(Font(R.font.posterglowcarousel_archivoblack_regular))
private val PosterGlowCarouselMarcellus = FontFamily(Font(R.font.posterglowcarousel_marcellus_regular))
private val PosterGlowCarouselAbril = FontFamily(Font(R.font.posterglowcarousel_abrilfatface_regular))
private val PosterGlowCarouselItaliana = FontFamily(Font(R.font.posterglowcarousel_italiana_regular))

private object PosterGlowCarouselMotion {
    const val LOOP_MS = 14_000L
    const val IdleResumeMs = 3_500L
    const val PickerPressAt = 1_300f
    const val PickerPressIn = 110f
    const val PickerPressOut = 150f
    const val DissolveInAt = 1_460f
    const val DissolveOutAt = 11_640f
    const val DissolveMs = 240f
    const val BackPressAt = 11_420f
    const val BackPressMs = 220f
    const val PressScale = 0.97f
    const val PressMs = 120
    const val ManualDissolveMs = 240
    const val ReducedFadeMs = 200
    const val EndLabelY = 739f

    val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    val Flick = CubicBezierEasing(0.3f, 0.1f, 0.12f, 1f)
    val Home = CubicBezierEasing(0.45f, 0f, 0.2f, 1f)
    // Reanimated's withTiming default is inOut(quad), used by manual fades.
    val DefaultTiming = Easing { fraction ->
        if (fraction < 0.5f) 2f * fraction * fraction
        else -1f + (4f - 2f * fraction) * fraction
    }

    val ScrollSteps = listOf(
        PosterGlowCarouselScrollStep(2_900f, 520f, 720f, false),
        PosterGlowCarouselScrollStep(4_100f, 936f, 600f, false),
        PosterGlowCarouselScrollStep(5_100f, 1_144f, 520f, false),
        PosterGlowCarouselScrollStep(5_900f, 1_664f, 720f, false),
        PosterGlowCarouselScrollStep(6_800f, 2_184f, 720f, false),
        PosterGlowCarouselScrollStep(7_700f, -1f, 760f, false),
        PosterGlowCarouselScrollStep(8_950f, 0f, 1_150f, true),
    )
}

private data class PosterGlowCarouselScrollStep(
    val at: Float,
    val to: Float,
    val duration: Float,
    val homeEase: Boolean,
)

private object PosterGlowCarouselPicker {
    const val EditRight = 25f
    const val EditY = 162.5f
    const val EditSize = 12.7f
    const val TitleY = 209f
    const val TitleSize = 30f
    const val Avatar = 135f
    const val PlusArm = 56f
    const val PlusStroke = 7f
    const val FooterFromBottom = 27f
    const val FooterSize = 14.6f
    val ColumnX = listOf(108f, 293f)
    val RowY = listOf(335f, 530.5f)
    val NameGap = listOf(95f, 99f)
    const val NameDx = 5f
    const val TitleDx = 5.7f
    const val NameSize = 20f
}

private object PosterGlowCarouselHome {
    const val Side = 12f
    const val HeroHeight = 512f
    const val HeroFadeFrom = 0.72f
    const val ChromeY = 18f
    const val ChromeX = 42f
    const val ChromeSize = 20f
    const val TitleY = 433f
    const val TitleSize = 17.4f
    const val GenreY = 455f
    const val GenreX = 32f
    const val GenreSize = 14f
    const val StarsY = 471f
    const val StarsX = 36f
    const val Star = 16.5f
    const val StarPitch = 21f
    const val Rating = 3.5f
    const val PlayX = 358.5f
    const val PlayY = 455.3f
    const val Play = 41f
    const val BadgeY = 263f
    const val SubtitleY = 280f
    const val LockupY = 302f
    const val LockupSize = 33f
    const val FirstLabelY = 529f
    const val LabelSize = 15.7f
    const val LabelNudge = 4.8f
    const val LabelToContent = 28f
    const val TabBar = 64f
    const val TabIconY = 31f
    val TabX = listOf(44f, 119f, 198.5f, 284f, 345f)
    val TabIcon = listOf(30f, 24f, 32f, 27f, 35f)

    val Poster = PosterGlowCarouselPosterMetrics(108.5f, 168f, 11.5f, 14f)
    val Wide = PosterGlowCarouselWideMetrics(330f, 180f, 85f, 19f, 13f, 3f, 5.7f, 2f, 19.4f, 5f, 12.5f)
    val Tall = PosterGlowCarouselTallMetrics(203f, 307f, 8.5f, 31f, 29f)
}

private data class PosterGlowCarouselPosterMetrics(val width: Float, val height: Float, val gap: Float, val radius: Float)
private data class PosterGlowCarouselWideMetrics(
    val width: Float,
    val imageHeight: Float,
    val infoHeight: Float,
    val gap: Float,
    val radius: Float,
    val progressHeight: Float,
    val progressInset: Float,
    val progressLift: Float,
    val titleSize: Float,
    val titleNudge: Float,
    val metaSize: Float,
)
private data class PosterGlowCarouselTallMetrics(
    val width: Float,
    val height: Float,
    val gap: Float,
    val wordSize: Float,
    val wordFromBottom: Float,
)

private enum class PosterGlowCarouselFace(
    val family: FontFamily,
    val size: Float,
    val lineHeight: Float,
    val letterSpacing: Float,
    val uppercase: Boolean,
) {
    Bebas(PosterGlowCarouselBebas, 23f, 22f, 1f, true),
    Anton(PosterGlowCarouselAnton, 17f, 19f, 0.2f, true),
    DmSerif(PosterGlowCarouselDmSerif, 17f, 18f, -0.2f, false),
    Archivo(PosterGlowCarouselArchivo, 12.5f, 14f, -0.2f, true),
    Marcellus(PosterGlowCarouselMarcellus, 11.5f, 14f, 2.4f, true),
    Abril(PosterGlowCarouselAbril, 17f, 18f, 0f, false),
    Italiana(PosterGlowCarouselItaliana, 14f, 16f, 2.6f, true),
}

private sealed interface PosterGlowCarouselSection {
    val label: String
    val gap: Float
    val labelX: Float?
    val toContent: Float?

    data class Posters(
        override val label: String,
        override val gap: Float,
        val items: List<PosterGlowCarouselPoster>,
        override val labelX: Float? = null,
        override val toContent: Float? = null,
    ) : PosterGlowCarouselSection

    data class Wide(
        override val label: String,
        override val gap: Float,
        val items: List<PosterGlowCarouselStill>,
        override val labelX: Float? = null,
        override val toContent: Float? = null,
    ) : PosterGlowCarouselSection

    data class Tall(
        override val label: String,
        override val gap: Float,
        val items: List<PosterGlowCarouselTall>,
        override val labelX: Float? = null,
        override val toContent: Float? = null,
    ) : PosterGlowCarouselSection
}

private data class PosterGlowCarouselPoster(
    val title: String,
    val image: Int,
    val face: PosterGlowCarouselFace,
    val tint: Color,
    val credit: String,
    val top: Boolean = false,
)

private data class PosterGlowCarouselStill(
    val title: String,
    val meta: String,
    val image: Int,
    val progress: Float,
)

private data class PosterGlowCarouselTall(
    val word: String,
    val image: Int,
    val face: PosterGlowCarouselFace,
    val top: Boolean = false,
)

private fun posterGlowCarouselPoster(
    title: String,
    image: Int,
    face: PosterGlowCarouselFace,
    tint: Long,
    credit: String,
    top: Boolean = false,
) = PosterGlowCarouselPoster(title, image, face, Color(tint), credit, top)

private val PosterGlowCarouselProfiles = listOf(
    "Jarvis3d" to R.drawable.posterglowcarousel_avatar_jonas,
    "Isaiah" to R.drawable.posterglowcarousel_avatar_isaiah,
    "Mia" to R.drawable.posterglowcarousel_avatar_mia,
)

private val PosterGlowCarouselSections = listOf<PosterGlowCarouselSection>(
    PosterGlowCarouselSection.Posters(
        "Now Playing",
        0f,
        listOf(
            posterGlowCarouselPoster("Into the Rift", R.drawable.posterglowcarousel_posters_np_1, PosterGlowCarouselFace.Bebas, 0xFFFFD23F, "A FILM BY TOMÁS REYES"),
            posterGlowCarouselPoster("Velocity", R.drawable.posterglowcarousel_posters_np_2, PosterGlowCarouselFace.Archivo, 0xFFFFFFFF, "IN CINEMAS JUNE 14"),
            posterGlowCarouselPoster("Iron Colossi", R.drawable.posterglowcarousel_posters_np_3, PosterGlowCarouselFace.Marcellus, 0xFFEDE6D6, "FROM THE STUDIO OF DUSTLINE"),
            posterGlowCarouselPoster("Nebula Misfits 3", R.drawable.posterglowcarousel_posters_np_4, PosterGlowCarouselFace.Bebas, 0xFFFFE3F6, "THE ROAD GETS WEIRDER"),
        ),
    ),
    PosterGlowCarouselSection.Wide(
        "Continue Watching",
        29f,
        listOf(
            PosterGlowCarouselStill("Clash III", "56 minutes remaining", R.drawable.posterglowcarousel_stills_cw_1, 0.615f),
            PosterGlowCarouselStill("Kaiju Rising", "24 minutes remaining", R.drawable.posterglowcarousel_stills_cw_2, 0.8f),
        ),
    ),
    PosterGlowCarouselSection.Tall(
        "Set The Mood",
        24f,
        listOf(
            PosterGlowCarouselTall("Action", R.drawable.posterglowcarousel_mood_action, PosterGlowCarouselFace.Anton),
            PosterGlowCarouselTall("Comedy", R.drawable.posterglowcarousel_mood_comedy, PosterGlowCarouselFace.Anton),
            PosterGlowCarouselTall("Drama", R.drawable.posterglowcarousel_mood_drama, PosterGlowCarouselFace.Anton),
        ),
    ),
    PosterGlowCarouselSection.Posters(
        "Trending",
        34f,
        listOf(
            posterGlowCarouselPoster("Exfil 2", R.drawable.posterglowcarousel_posters_tr_1, PosterGlowCarouselFace.Anton, 0xFFF2F6FA, "NO WAY BACK · DECEMBER"),
            posterGlowCarouselPoster("Round Twelve", R.drawable.posterglowcarousel_posters_tr_2, PosterGlowCarouselFace.Archivo, 0xFF16181C, "A FILM BY ADA OKAFOR"),
            posterGlowCarouselPoster("The Pact", R.drawable.posterglowcarousel_posters_tr_3, PosterGlowCarouselFace.Marcellus, 0xFFF1E6C8, "BASED ON A TRUE STORY", true),
            posterGlowCarouselPoster("Ember Blade", R.drawable.posterglowcarousel_posters_tr_4, PosterGlowCarouselFace.DmSerif, 0xFFF0B27A, "ONLY IN CINEMAS"),
        ),
    ),
    PosterGlowCarouselSection.Posters(
        "For You",
        30f,
        listOf(
            posterGlowCarouselPoster("Glitch Hero", R.drawable.posterglowcarousel_posters_fy_1, PosterGlowCarouselFace.Abril, 0xFFFFFFFF, "THIS SUMMER", true),
            posterGlowCarouselPoster("Red Kick", R.drawable.posterglowcarousel_posters_fy_2, PosterGlowCarouselFace.Bebas, 0xFFFFFFFF, "ONE DOJO · ONE FIGHT"),
            posterGlowCarouselPoster("Two Friends", R.drawable.posterglowcarousel_posters_fy_3, PosterGlowCarouselFace.DmSerif, 0xFFFFFFFF, "A STORY OF SECOND CHANCES"),
            posterGlowCarouselPoster("Blue Dash", R.drawable.posterglowcarousel_posters_fy_4, PosterGlowCarouselFace.Abril, 0xFFFFFFFF, "A FAMILY ADVENTURE", true),
        ),
    ),
    PosterGlowCarouselSection.Posters(
        "New On FilmFlow",
        30f,
        listOf(
            posterGlowCarouselPoster("Snow Beast", R.drawable.posterglowcarousel_posters_new_1, PosterGlowCarouselFace.Anton, 0xFFE2231A, "SOMETHING IS OUT THERE"),
            posterGlowCarouselPoster("Questfall", R.drawable.posterglowcarousel_posters_new_2, PosterGlowCarouselFace.Marcellus, 0xFFFFE7B0, "EVERY LEGEND BEGINS SOMEWHERE", true),
            posterGlowCarouselPoster("Tidewater", R.drawable.posterglowcarousel_posters_new_3, PosterGlowCarouselFace.Italiana, 0xFFDDF6FF, "FROM THE DIRECTOR OF HOLLOW"),
            posterGlowCarouselPoster("Elementa", R.drawable.posterglowcarousel_posters_new_4, PosterGlowCarouselFace.DmSerif, 0xFFFFFFFF, "FALL IN LOVE AFTER DARK"),
        ),
        labelX = 11.4f,
    ),
    PosterGlowCarouselSection.Tall(
        "Games Based On Movies",
        30f,
        listOf(
            PosterGlowCarouselTall("Rooftop Run 2", R.drawable.posterglowcarousel_games_game_1, PosterGlowCarouselFace.Bebas, true),
            PosterGlowCarouselTall("Ravenhold", R.drawable.posterglowcarousel_games_game_2, PosterGlowCarouselFace.Marcellus),
            PosterGlowCarouselTall("Dune Patrol", R.drawable.posterglowcarousel_games_game_3, PosterGlowCarouselFace.Archivo),
        ),
        labelX = 11.4f,
        toContent = 37f,
    ),
    PosterGlowCarouselSection.Posters(
        "Documentary",
        32.2f,
        listOf(
            posterGlowCarouselPoster("Golden Boy", R.drawable.posterglowcarousel_posters_doc_1, PosterGlowCarouselFace.Bebas, 0xFFFFFFFF, "A DOCUMENTARY SERIES"),
            posterGlowCarouselPoster("The Final Run", R.drawable.posterglowcarousel_posters_doc_2, PosterGlowCarouselFace.Archivo, 0xFF1B1B1B, "THE STORY OF A DYNASTY", true),
            posterGlowCarouselPoster("Wild Planet", R.drawable.posterglowcarousel_posters_doc_3, PosterGlowCarouselFace.Italiana, 0xFFFFFFFF, "NARRATED BY HUGH ALDEN"),
            posterGlowCarouselPoster("Ice Line", R.drawable.posterglowcarousel_posters_doc_4, PosterGlowCarouselFace.Bebas, 0xFF0E2A33, "A FILM ABOUT WHAT REMAINS", true),
        ),
        labelX = 11.4f,
    ),
    PosterGlowCarouselSection.Posters(
        "Coming Soon",
        30f,
        listOf(
            posterGlowCarouselPoster("The Hunter", R.drawable.posterglowcarousel_posters_cs_1, PosterGlowCarouselFace.Anton, 0xFFEDE6D6, "FEBRUARY 2025"),
            posterGlowCarouselPoster("Mercenaries 4", R.drawable.posterglowcarousel_posters_cs_2, PosterGlowCarouselFace.Archivo, 0xFFFFFFFF, "THE LAST JOB"),
            posterGlowCarouselPoster("Azure Shell", R.drawable.posterglowcarousel_posters_cs_3, PosterGlowCarouselFace.Bebas, 0xFFD6E9FF, "COMING SOON"),
            posterGlowCarouselPoster("Ashen Rider", R.drawable.posterglowcarousel_posters_cs_4, PosterGlowCarouselFace.Marcellus, 0xFFFFD2A6, "RIDE OR BURN", true),
        ),
        labelX = 11.4f,
    ),
    PosterGlowCarouselSection.Posters(
        "Award Winners",
        35.4f,
        listOf(
            posterGlowCarouselPoster("Neon Tide", R.drawable.posterglowcarousel_posters_aw_1, PosterGlowCarouselFace.Italiana, 0xFFA9F0FF, "WINNER · BEST PICTURE"),
            posterGlowCarouselPoster("Lower Floor", R.drawable.posterglowcarousel_posters_aw_2, PosterGlowCarouselFace.DmSerif, 0xFFFFFFFF, "GRAND JURY PRIZE", true),
            posterGlowCarouselPoster("Open Road", R.drawable.posterglowcarousel_posters_aw_3, PosterGlowCarouselFace.DmSerif, 0xFF3D3029, "THREE FESTIVAL AWARDS", true),
            posterGlowCarouselPoster("Painted Smile", R.drawable.posterglowcarousel_posters_aw_4, PosterGlowCarouselFace.Anton, 0xFFFFE15A, "BEST DIRECTOR · LIDO"),
        ),
        labelX = 11.4f,
    ),
    PosterGlowCarouselSection.Posters(
        "Movie Night",
        30.8f,
        listOf(
            posterGlowCarouselPoster("Bramble Bear", R.drawable.posterglowcarousel_posters_mn_1, PosterGlowCarouselFace.Abril, 0xFFFFD34E, "A FAMILY FILM"),
            posterGlowCarouselPoster("Spark", R.drawable.posterglowcarousel_posters_mn_2, PosterGlowCarouselFace.Archivo, 0xFFFFFFFF, "GOOD BOY. BIG DAY.", true),
            posterGlowCarouselPoster("The Grey Halls", R.drawable.posterglowcarousel_posters_mn_3, PosterGlowCarouselFace.Marcellus, 0xFFE8E2D0, "PART ONE"),
            posterGlowCarouselPoster("Meadow Kite", R.drawable.posterglowcarousel_posters_mn_4, PosterGlowCarouselFace.Abril, 0xFF2E2A10, "FROM THE MAKERS OF SPARK", true),
        ),
        labelX = 11.4f,
    ),
    PosterGlowCarouselSection.Posters(
        "Staff Recommend",
        34.2f,
        listOf(
            posterGlowCarouselPoster("Rain City", R.drawable.posterglowcarousel_posters_sr_1, PosterGlowCarouselFace.Italiana, 0xFFFFFFFF, "A NOIR IN THREE NIGHTS"),
            posterGlowCarouselPoster("Gold Orbit", R.drawable.posterglowcarousel_posters_sr_2, PosterGlowCarouselFace.Bebas, 0xFFFFF1D6, "STAFF PICK"),
            posterGlowCarouselPoster("Last Waltz", R.drawable.posterglowcarousel_posters_sr_3, PosterGlowCarouselFace.DmSerif, 0xFFF6E7CF, "ONE MORE DANCE"),
            posterGlowCarouselPoster("Aurora Wolf", R.drawable.posterglowcarousel_posters_sr_4, PosterGlowCarouselFace.Anton, 0xFFE9FFF4, "HUNT THE LIGHT", true),
        ),
        labelX = 11.4f,
    ),
)

private fun posterGlowCarouselSectionHeight(section: PosterGlowCarouselSection): Float = when (section) {
    is PosterGlowCarouselSection.Posters -> PosterGlowCarouselHome.Poster.height
    is PosterGlowCarouselSection.Wide -> PosterGlowCarouselHome.Wide.imageHeight + PosterGlowCarouselHome.Wide.infoHeight
    is PosterGlowCarouselSection.Tall -> PosterGlowCarouselHome.Tall.height
}

private val PosterGlowCarouselLayout = run {
    val labels = ArrayList<Float>(PosterGlowCarouselSections.size)
    var label = PosterGlowCarouselHome.FirstLabelY
    var bottom = 0f
    PosterGlowCarouselSections.forEachIndexed { index, section ->
        if (index > 0) label = bottom + section.gap
        labels += label
        val top = label + (section.toContent ?: PosterGlowCarouselHome.LabelToContent)
        bottom = top + posterGlowCarouselSectionHeight(section)
    }
    labels to bottom
}

private fun posterGlowCarouselRamp(time: Float, at: Float, duration: Float): Float =
    ((time - at) / duration).coerceIn(0f, 1f)

private fun posterGlowCarouselTap(time: Float, at: Float, inMs: Float, outMs: Float): Float {
    if (time < at || time > at + inMs + outMs) return 0f
    return if (time < at + inMs) posterGlowCarouselRamp(time, at, inMs)
    else 1f - posterGlowCarouselRamp(time, at + inMs, outMs)
}

private fun posterGlowCarouselScrollTarget(step: PosterGlowCarouselScrollStep, maxScroll: Float): Float =
    if (step.to < 0f) maxScroll else min(maxScroll, step.to)

private fun posterGlowCarouselScriptedScroll(time: Float, maxScroll: Float): Float {
    var y = 0f
    var from = 0f
    PosterGlowCarouselMotion.ScrollSteps.forEach { step ->
        if (time < step.at) return@forEach
        val progress = posterGlowCarouselRamp(time, step.at, step.duration)
        val eased = if (step.homeEase) PosterGlowCarouselMotion.Home.transform(progress)
        else PosterGlowCarouselMotion.Flick.transform(progress)
        val target = posterGlowCarouselScrollTarget(step, maxScroll)
        y = from + (target - from) * eased
        from = target
    }
    return y
}

enum class PosterGlowCarouselTabIdentity {
    Home,
    Search,
    Cinema,
    Downloads,
    Profile,
}

/**
 * The full FilmFlow list screen. Its one clock drives only graphics layers and the scroll state;
 * cards and rows remain ordinary Compose content so autoplay never recomposes the whole screen.
 */
@Composable
fun PosterGlowCarousel(
    autoPlay: Boolean = false,
    modifier: Modifier = Modifier,
    onSettings: (() -> Unit)? = null,
    onTabSelected: ((PosterGlowCarouselTabIdentity) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val statusTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val navigationBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val reducedMotion = rememberReducedMotion()
    val feedScroll = rememberScrollState()
    val rowStates = remember { PosterGlowCarouselSections.map { LazyListState() } }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val autoModeState = remember { mutableStateOf(autoPlay) }
    val pickerOpacity = remember { Animatable(1f) }
    val manualPickerOpacity = remember { Animatable(1f) }
    val autoPress = remember { Animatable(0f) }
    val backPress = remember { Animatable(0f) }
    val pickerInputEnabled = remember { mutableStateOf(true) }
    var resumeJob by remember { mutableStateOf<Job?>(null) }
    val maxScroll = PosterGlowCarouselLayout.first.last() - PosterGlowCarouselMotion.EndLabelY
    val fadeMs = if (reducedMotion) PosterGlowCarouselMotion.ReducedFadeMs else PosterGlowCarouselMotion.DissolveMs

    fun takeOver() {
        resumeJob?.cancel()
        resumeJob = null
        if (!autoPlay || !autoModeState.value) return
        autoModeState.value = false
        pickerInputEnabled.value = pickerOpacity.value > 0.5f
        scope.launch {
            manualPickerOpacity.snapTo(if (pickerOpacity.value > 0.5f) 1f else 0f)
            autoPress.snapTo(0f)
            backPress.snapTo(0f)
        }
    }

    fun scheduleResume() {
        if (!autoPlay || reducedMotion || autoModeState.value) return
        resumeJob?.cancel()
        resumeJob = scope.launch {
            delay(PosterGlowCarouselMotion.IdleResumeMs)
            if (reducedMotion) return@launch
            manualPickerOpacity.animateTo(
                1f,
                tween(PosterGlowCarouselMotion.ManualDissolveMs, easing = PosterGlowCarouselMotion.DefaultTiming),
            ) { pickerInputEnabled.value = value > 0.5f }
            feedScroll.scrollTo(0)
            rowStates.forEach { it.scrollToItem(0) }
            autoModeState.value = true
        }
    }

    LaunchedEffect(reducedMotion, autoPlay) {
        if (reducedMotion) {
            resumeJob?.cancel()
            resumeJob = null
            pickerOpacity.snapTo(1f)
            manualPickerOpacity.snapTo(1f)
            autoPress.snapTo(0f)
            backPress.snapTo(0f)
            pickerInputEnabled.value = true
            feedScroll.scrollTo(0)
            rowStates.forEach { it.scrollToItem(0) }
        }
    }

    LaunchedEffect(autoModeState.value, autoPlay, reducedMotion, maxScroll) {
        if (reducedMotion) return@LaunchedEffect
        if (!autoModeState.value || !autoPlay) return@LaunchedEffect
        var startedAt = 0L
        while (isActive && autoModeState.value) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            if (startedAt == 0L) startedAt = now
            val time = ((now - startedAt) / 1_000_000L % PosterGlowCarouselMotion.LOOP_MS).toFloat()
            val dissolveOut = PosterGlowCarouselMotion.EaseOut.transform(
                posterGlowCarouselRamp(time, PosterGlowCarouselMotion.DissolveInAt, fadeMs.toFloat()),
            )
            val dissolveBack = PosterGlowCarouselMotion.EaseOut.transform(
                posterGlowCarouselRamp(time, PosterGlowCarouselMotion.DissolveOutAt, fadeMs.toFloat()),
            )
            pickerOpacity.snapTo(1f - dissolveOut + dissolveBack)
            pickerInputEnabled.value = pickerOpacity.value > 0.5f
            autoPress.snapTo(
                if (reducedMotion) 0f else posterGlowCarouselTap(
                    time,
                    PosterGlowCarouselMotion.PickerPressAt,
                    PosterGlowCarouselMotion.PickerPressIn,
                    PosterGlowCarouselMotion.PickerPressOut,
                ),
            )
            backPress.snapTo(
                posterGlowCarouselTap(
                    time,
                    PosterGlowCarouselMotion.BackPressAt,
                    80f,
                    PosterGlowCarouselMotion.BackPressMs - 80f,
                ),
            )
            if (!reducedMotion) {
                val scriptedDp = posterGlowCarouselScriptedScroll(time, maxScroll)
                feedScroll.scrollTo((scriptedDp * density.density).toInt())
            }
        }
    }

    val interactionTracker = Modifier.pointerInput(autoPlay) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            takeOver()
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                if (event.changes.none { it.pressed }) break
            }
            scheduleResume()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(PosterGlowCarouselColors.Background)
            .then(interactionTracker),
    ) {
        val width = maxWidth.value
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (pickerInputEnabled.value) Modifier.semantics { hideFromAccessibility() } else Modifier),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(feedScroll),
                ) {
                    PosterGlowCarouselHero(width)
                    PosterGlowCarouselSections.forEachIndexed { index, section ->
                        PosterGlowCarouselSectionRow(section, index == 0, rowStates[index], reducedMotion)
                    }
                }

                val chromeY = statusTop.value + PosterGlowCarouselHome.ChromeY - PosterGlowCarouselHome.ChromeSize / 2f - 12f
                PosterGlowCarouselChrome(
                    modifier = Modifier.offset(
                        x = (PosterGlowCarouselHome.ChromeX - 22f).dp,
                        y = chromeY.dp,
                    ),
                    backPress = backPress,
                    onBack = {
                        scope.launch {
                            manualPickerOpacity.animateTo(
                                1f,
                                tween(fadeMs.toInt(), easing = PosterGlowCarouselMotion.DefaultTiming),
                            ) { pickerInputEnabled.value = value > 0.5f }
                        }
                    },
                )
                PosterGlowCarouselSettings(
                    modifier = Modifier.offset(
                        x = (width - PosterGlowCarouselHome.ChromeX - 22f).dp,
                        y = chromeY.dp,
                    ),
                    onClick = onSettings,
                )

                PosterGlowCarouselTabBar(
                    width = width,
                    bottomInset = navigationBottom.value,
                    onTabSelected = onTabSelected,
                )
            }

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = if (autoModeState.value) pickerOpacity.value else manualPickerOpacity.value
                    }
                    .then(if (pickerInputEnabled.value) Modifier else Modifier.semantics { hideFromAccessibility() }),
            ) {
                // A canvas backdrop has no pointer input, so it would otherwise let empty-space
                // touches fall through to the feed. Keep this below the profile controls.
                if (pickerInputEnabled.value) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Final)
                                        if (event.changes.none { it.pressed }) break
                                    }
                                }
                            },
                    )
                }
                PosterGlowCarouselPickerView(
                    autoPress = autoPress,
                    enabled = pickerInputEnabled.value,
                    reducedMotion = reducedMotion,
                    onPick = { index ->
                        if (index < PosterGlowCarouselProfiles.size) {
                            if (!autoModeState.value) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            scope.launch {
                                manualPickerOpacity.animateTo(
                                    0f,
                                    tween(fadeMs.toInt(), easing = PosterGlowCarouselMotion.DefaultTiming),
                                ) { pickerInputEnabled.value = value > 0.5f }
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PosterGlowCarouselPickerView(
    autoPress: Animatable<Float, *>,
    enabled: Boolean,
    reducedMotion: Boolean,
    onPick: (Int) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        PosterGlowCarouselPickerBackdrop(Modifier.fillMaxSize())
        Text(
            text = "Edit Profile",
            color = PosterGlowCarouselColors.Ink,
            fontFamily = PosterGlowCarouselPoppins,
            fontSize = PosterGlowCarouselPicker.EditSize.sp,
            lineHeight = 16.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = PosterGlowCarouselPicker.EditRight.dp)
                .offset(y = (PosterGlowCarouselPicker.EditY - 8f).dp),
        )
        Text(
            text = "Who’s Watching?",
            color = PosterGlowCarouselColors.Ink,
            fontFamily = PosterGlowCarouselPoppins,
            fontWeight = FontWeight.SemiBold,
            fontSize = PosterGlowCarouselPicker.TitleSize.sp,
            lineHeight = 40.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = PosterGlowCarouselPicker.TitleDx.dp, y = (PosterGlowCarouselPicker.TitleY - 20f).dp)
                .semantics { heading() },
        )
        PosterGlowCarouselProfiles.forEachIndexed { index, profile ->
            val row = index / 2
            PosterGlowCarouselProfileButton(
                index = index,
                row = row,
                x = PosterGlowCarouselPicker.ColumnX[index % 2],
                y = PosterGlowCarouselPicker.RowY[row],
                name = profile.first,
                image = profile.second,
                autoPress = autoPress,
                enabled = enabled,
                reducedMotion = reducedMotion,
                onPick = onPick,
            )
        }
        PosterGlowCarouselProfileButton(
            index = PosterGlowCarouselProfiles.size,
            row = 1,
            x = PosterGlowCarouselPicker.ColumnX[1],
            y = PosterGlowCarouselPicker.RowY[1],
            name = "New Profile",
            image = null,
            autoPress = autoPress,
            enabled = enabled,
            reducedMotion = reducedMotion,
            onPick = onPick,
        )
        Text(
            text = "FilmFlow, 2023",
            color = PosterGlowCarouselColors.InkSoft,
            fontFamily = PosterGlowCarouselPoppins,
            fontSize = PosterGlowCarouselPicker.FooterSize.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .offset(y = -(PosterGlowCarouselPicker.FooterFromBottom - 9f).dp),
        )
    }
}

@Composable
private fun PosterGlowCarouselPickerBackdrop(modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        drawRect(
            Brush.verticalGradient(
                colors = listOf(PosterGlowCarouselColors.PickerTop, PosterGlowCarouselColors.PickerBottom),
                startY = 0f,
                endY = size.height,
            ),
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(PosterGlowCarouselColors.PickerTeal, Color.Transparent),
                center = Offset(0f, size.height * 0.5f),
                radius = size.width * 0.62f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(PosterGlowCarouselColors.PickerMagenta.copy(alpha = 0.95f), Color.Transparent),
                center = Offset(size.width * 0.74f, size.height * 0.31f),
                radius = size.width * 0.6f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(PosterGlowCarouselColors.PickerPlum.copy(alpha = 0.9f), Color.Transparent),
                center = Offset(size.width * 0.72f, size.height * 0.76f),
                radius = size.width * 0.5f,
            ),
        )
    }
}

@Composable
private fun PosterGlowCarouselProfileButton(
    index: Int,
    row: Int,
    x: Float,
    y: Float,
    name: String,
    image: Int?,
    autoPress: Animatable<Float, *>,
    enabled: Boolean,
    reducedMotion: Boolean,
    onPick: (Int) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val manualPress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed && !reducedMotion) 1f else 0f,
        animationSpec = tween(PosterGlowCarouselMotion.PressMs, easing = PosterGlowCarouselMotion.EaseOut),
        label = "profile press",
    )
    val size = PosterGlowCarouselPicker.Avatar.dp
    Box(
        modifier = Modifier
            .offset(x = (x - PosterGlowCarouselPicker.Avatar / 2f).dp, y = (y - PosterGlowCarouselPicker.Avatar / 2f).dp)
            .size(PosterGlowCarouselPicker.Avatar.dp, (PosterGlowCarouselPicker.Avatar + 70f).dp)
            .semantics {
                contentDescription = name
                role = Role.Button
            }
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = { onPick(index) },
            ),
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(PosterGlowCarouselColors.Plus)
                .graphicsLayer {
                    val scripted = if (index == 0) autoPress.value else 0f
                    val amount = max(scripted, manualPress)
                    scaleX = 1f - (1f - PosterGlowCarouselMotion.PressScale) * amount
                    scaleY = scaleX
                },
        ) {
            if (image != null) {
                Image(
                    painter = painterResource(image),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PosterGlowCarouselColors.Plus),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.width(PosterGlowCarouselPicker.PlusArm.dp).height(PosterGlowCarouselPicker.PlusStroke.dp).background(PosterGlowCarouselColors.Ink, RoundedCornerShape(4.dp)))
                    Box(Modifier.width(PosterGlowCarouselPicker.PlusStroke.dp).height(PosterGlowCarouselPicker.PlusArm.dp).background(PosterGlowCarouselColors.Ink, RoundedCornerShape(4.dp)))
                }
            }
        }
        Text(
            text = name,
            color = PosterGlowCarouselColors.Ink,
            fontFamily = PosterGlowCarouselPoppins,
            fontSize = PosterGlowCarouselPicker.NameSize.sp,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .requiredWidth(195.dp)
                .offset(x = (-30f + PosterGlowCarouselPicker.NameDx).dp, y = (PosterGlowCarouselPicker.Avatar / 2f + PosterGlowCarouselPicker.NameGap[row] - 12f).dp),
        )
    }
}

@Composable
private fun PosterGlowCarouselHero(width: Float) {
    val shadowDensity = LocalDensity.current
    val shadowBlur = with(shadowDensity) { 4.dp.toPx() }
    val lockupShadow = with(shadowDensity) { 6.dp.toPx() }
    val lockupOffset = with(shadowDensity) { 1.dp.toPx() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height((PosterGlowCarouselHome.FirstLabelY - 10.5f).dp),
    ) {
        Image(
            painter = painterResource(R.drawable.posterglowcarousel_hero),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(PosterGlowCarouselHome.HeroHeight.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PosterGlowCarouselHome.HeroHeight.dp)
                .background(Color.Transparent),
        )
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(PosterGlowCarouselHome.HeroHeight.dp),
        ) {
            drawRect(
                Brush.verticalGradient(
                    colors = listOf(PosterGlowCarouselColors.Background.copy(alpha = 0f), PosterGlowCarouselColors.Background),
                    startY = size.height * PosterGlowCarouselHome.HeroFadeFrom,
                    endY = size.height,
                ),
            )
        }
        Box(
            modifier = Modifier
                .offset(x = (width / 2f - 26f).dp, y = (PosterGlowCarouselHome.BadgeY - 5f).dp)
                .size(52.dp, 10.dp)
                .background(PosterGlowCarouselColors.Badge),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "NOVA STUDIOS",
                color = PosterGlowCarouselColors.Ink,
                fontFamily = PosterGlowCarouselBebas,
                fontSize = 8.sp,
                lineHeight = 9.sp,
                letterSpacing = 0.8.sp,
            )
        }
        Text(
            text = "THE IRON QUEEN",
            color = Color(0xFFF2F2F2),
            fontFamily = PosterGlowCarouselBebas,
            fontSize = 14.sp,
            lineHeight = 16.sp,
            letterSpacing = 1.6.sp,
            textAlign = TextAlign.Center,
            style = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = shadowBlur)),
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (PosterGlowCarouselHome.SubtitleY - 8f).dp),
        )
        Text(
            text = "KINGDOM OF TIDE",
            color = Color(0xFFE9EEF2),
            fontFamily = PosterGlowCarouselMarcellus,
            fontSize = (PosterGlowCarouselHome.LockupSize - 3f).sp,
            lineHeight = 40.sp,
            letterSpacing = 1.2.sp,
            textAlign = TextAlign.Center,
            style = TextStyle(
                shadow = androidx.compose.ui.graphics.Shadow(
                    Color(0xFF0A141E).copy(alpha = 0.85f),
                    Offset(0f, lockupOffset),
                    lockupShadow,
                ),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (PosterGlowCarouselHome.LockupY - 20f).dp),
        )
        Text(
            text = "Kingdom of Tide",
            color = PosterGlowCarouselColors.Ink,
            fontFamily = PosterGlowCarouselPoppins,
            fontSize = PosterGlowCarouselHome.TitleSize.sp,
            lineHeight = 26.sp,
            modifier = Modifier
                .offset(x = PosterGlowCarouselHome.Side.dp, y = (PosterGlowCarouselHome.TitleY - 13f).dp),
        )
        Text(
            text = "Action • Adventure",
            color = PosterGlowCarouselColors.InkSoft,
            fontFamily = PosterGlowCarouselPoppins,
            fontSize = PosterGlowCarouselHome.GenreSize.sp,
            lineHeight = 18.sp,
            modifier = Modifier
                .offset(x = PosterGlowCarouselHome.GenreX.dp, y = (PosterGlowCarouselHome.GenreY - 9f).dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy((PosterGlowCarouselHome.StarPitch - PosterGlowCarouselHome.Star).dp),
            modifier = Modifier.offset(x = PosterGlowCarouselHome.StarsX.dp, y = (PosterGlowCarouselHome.StarsY - PosterGlowCarouselHome.Star / 2f).dp)
                .semantics { contentDescription = "Rated 3.5 out of 5" },
        ) {
            repeat(5) { index ->
                PosterGlowCarouselStar(
                    size = PosterGlowCarouselHome.Star.dp,
                    fill = (PosterGlowCarouselHome.Rating - index).coerceIn(0f, 1f),
                )
            }
        }
        PosterGlowCarouselPressable(
            label = "Play Kingdom of Tide",
            modifier = Modifier
                .offset(x = (PosterGlowCarouselHome.PlayX - PosterGlowCarouselHome.Play / 2f).dp, y = (PosterGlowCarouselHome.PlayY - PosterGlowCarouselHome.Play / 2f).dp)
                .size(PosterGlowCarouselHome.Play.dp)
                .background(PosterGlowCarouselColors.Ink, CircleShape),
            onClick = {},
            contentAlignment = Alignment.Center,
            animatePress = false,
        ) {
            PosterGlowCarouselIcon(
                kind = PosterGlowCarouselIconKind.Play,
                size = 26.dp,
                color = Color(0xFF111114),
                modifier = Modifier.offset(x = 1.5.dp),
            )
        }
    }
}

@Composable
private fun PosterGlowCarouselSectionRow(
    section: PosterGlowCarouselSection,
    first: Boolean,
    state: LazyListState,
    reducedMotion: Boolean,
) {
    val rowHeight = posterGlowCarouselSectionHeight(section)
    val labelMargin = if (first) 0f else section.gap - 10.5f
    val contentMargin = (section.toContent ?: PosterGlowCarouselHome.LabelToContent) - 10.5f
    val labelX = section.labelX ?: PosterGlowCarouselHome.Side + 2f
    val snapProvider = remember(state) { SnapLayoutInfoProvider(state, SnapPosition.Start) }
    val flingBehavior = rememberSnapFlingBehavior(snapProvider)
    Column(modifier = Modifier.padding(top = labelMargin.dp)) {
        Text(
            text = section.label,
            color = PosterGlowCarouselColors.Ink,
            fontFamily = PosterGlowCarouselPoppins,
            fontWeight = FontWeight.Medium,
            fontSize = PosterGlowCarouselHome.LabelSize.sp,
            lineHeight = 21.sp,
            modifier = Modifier
                .padding(start = labelX.dp)
                .height(21.dp)
                .graphicsLayer { translationY = PosterGlowCarouselHome.LabelNudge.dp.toPx() }
                .semantics { heading() },
        )
        Spacer(Modifier.height(contentMargin.dp))
        when (section) {
            is PosterGlowCarouselSection.Posters -> LazyRow(
                state = state,
                modifier = Modifier.height(rowHeight.dp),
                contentPadding = PaddingValues(horizontal = PosterGlowCarouselHome.Side.dp),
                horizontalArrangement = Arrangement.spacedBy(PosterGlowCarouselHome.Poster.gap.dp),
                flingBehavior = flingBehavior,
            ) {
                items(section.items, key = { it.title }) { PosterGlowCarouselPosterCard(it, reducedMotion) }
            }
            is PosterGlowCarouselSection.Wide -> LazyRow(
                state = state,
                modifier = Modifier.height(rowHeight.dp),
                contentPadding = PaddingValues(horizontal = PosterGlowCarouselHome.Side.dp),
                horizontalArrangement = Arrangement.spacedBy(PosterGlowCarouselHome.Wide.gap.dp),
                flingBehavior = flingBehavior,
            ) {
                items(section.items, key = { it.title }) { PosterGlowCarouselStillCard(it, reducedMotion) }
            }
            is PosterGlowCarouselSection.Tall -> LazyRow(
                state = state,
                modifier = Modifier.height(rowHeight.dp),
                contentPadding = PaddingValues(horizontal = PosterGlowCarouselHome.Side.dp),
                horizontalArrangement = Arrangement.spacedBy(PosterGlowCarouselHome.Tall.gap.dp),
                flingBehavior = flingBehavior,
            ) {
                items(section.items, key = { it.word }) { PosterGlowCarouselTallCard(it, reducedMotion) }
            }
        }
    }
}

@Composable
private fun PosterGlowCarouselPosterCard(item: PosterGlowCarouselPoster, reducedMotion: Boolean) {
    val metrics = PosterGlowCarouselHome.Poster
    val inkIsDark = item.tint.luminance() < 110f / 255f
    val start = if (inkIsDark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.42f)
    val end = Color.Transparent
    PosterGlowCarouselPressable(
        label = item.title,
        modifier = Modifier
            .size(metrics.width.dp, metrics.height.dp)
            .clip(RoundedCornerShape(metrics.radius.dp))
            .background(PosterGlowCarouselColors.PosterPlaceholder),
        onClick = {},
        reduceMotion = reducedMotion,
    ) {
        Image(
            painter = painterResource(item.image),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Transparent),
        )
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            drawRect(
                Brush.verticalGradient(
                    colors = if (item.top) listOf(start, end) else listOf(end, start),
                    startY = if (item.top) 0f else size.height * 0.58f,
                    endY = if (item.top) size.height * 0.42f else size.height,
                ),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(if (item.top) Alignment.TopCenter else Alignment.BottomCenter)
                .padding(start = 6.dp, end = 6.dp)
                .then(if (item.top) Modifier.padding(top = 10.dp) else Modifier.padding(bottom = 9.dp)),
        ) {
            Text(
                text = if (item.face.uppercase) item.title.uppercase(Locale.ROOT) else item.title,
                color = item.tint,
                fontFamily = item.face.family,
                fontSize = item.face.size.sp,
                lineHeight = item.face.lineHeight.sp,
                letterSpacing = item.face.letterSpacing.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Clip,
            )
            Text(
                text = item.credit,
                color = item.tint.copy(alpha = 0.75f),
                fontFamily = PosterGlowCarouselBebas,
                fontSize = 5.5.sp,
                lineHeight = 7.sp,
                letterSpacing = 0.9.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
private fun PosterGlowCarouselStillCard(item: PosterGlowCarouselStill, reducedMotion: Boolean) {
    val metrics = PosterGlowCarouselHome.Wide
    PosterGlowCarouselPressable(
        label = "${item.title}, ${item.meta}",
        modifier = Modifier
            .size(metrics.width.dp, (metrics.imageHeight + metrics.infoHeight).dp)
            .clip(RoundedCornerShape(metrics.radius.dp))
            .background(PosterGlowCarouselColors.Card),
        onClick = {},
        reduceMotion = reducedMotion,
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(metrics.imageHeight.dp)) {
                Image(
                    painter = painterResource(item.image),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                PosterGlowCarouselIcon(
                    kind = PosterGlowCarouselIconKind.PlayCircle,
                    size = 14.dp,
                    color = PosterGlowCarouselColors.Ink,
                    modifier = Modifier.align(Alignment.BottomStart).offset(x = 8.dp, y = (-17).dp),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = metrics.progressInset.dp)
                        .offset(y = (-metrics.progressLift).dp)
                        .height(metrics.progressHeight.dp)
                        .background(PosterGlowCarouselColors.ProgressTrack),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(item.progress)
                            .background(PosterGlowCarouselColors.Progress),
                    )
                }
            }
            Text(
                text = item.title,
                color = PosterGlowCarouselColors.Ink,
                fontFamily = PosterGlowCarouselPoppins,
                fontSize = metrics.titleSize.sp,
                lineHeight = 24.sp,
                modifier = Modifier.padding(start = 10.dp, top = (11f + metrics.titleNudge).dp),
            )
            Text(
                text = item.meta,
                color = PosterGlowCarouselColors.InkMute,
                fontFamily = PosterGlowCarouselPoppins,
                fontSize = metrics.metaSize.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(start = 10.dp).offset(y = (1f - metrics.titleNudge).dp),
            )
        }
    }
}

@Composable
private fun PosterGlowCarouselTallCard(item: PosterGlowCarouselTall, reducedMotion: Boolean) {
    val metrics = PosterGlowCarouselHome.Tall
    PosterGlowCarouselPressable(
        label = item.word,
        modifier = Modifier
            .size(metrics.width.dp, metrics.height.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(PosterGlowCarouselColors.PosterPlaceholder),
        onClick = {},
        reduceMotion = reducedMotion,
    ) {
        Image(
            painter = painterResource(item.image),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Transparent),
        )
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            drawRect(
                Brush.verticalGradient(
                    colors = if (item.top) listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent)
                    else listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                    startY = if (item.top) 0f else size.height * 0.65f,
                    endY = if (item.top) size.height * 0.3f else size.height,
                ),
            )
        }
        Text(
            text = if (item.face == PosterGlowCarouselFace.Anton) item.word else item.word.uppercase(Locale.ROOT),
            color = PosterGlowCarouselColors.Ink,
            fontFamily = item.face.family,
            fontSize = if (item.face == PosterGlowCarouselFace.Anton) metrics.wordSize.sp else 30.sp,
            lineHeight = if (item.face == PosterGlowCarouselFace.Anton) 44.sp else 36.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .align(if (item.top) Alignment.TopCenter else Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .then(if (item.top) Modifier.padding(top = 18.dp) else Modifier.padding(bottom = (metrics.wordFromBottom - 22f).dp)),
        )
    }
}

@Composable
private fun PosterGlowCarouselPressable(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
    contentAlignment: Alignment = Alignment.TopStart,
    reduceMotion: Boolean = false,
    animatePress: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && animatePress && !reduceMotion) PosterGlowCarouselMotion.PressScale else 1f,
        animationSpec = tween(PosterGlowCarouselMotion.PressMs, easing = PosterGlowCarouselMotion.EaseOut),
        label = "poster press",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = contentAlignment,
        content = content,
    )
}

private enum class PosterGlowCarouselIconKind { Back, Gear, Star, Play, PlayCircle, Home, Search, Seat, Download, User }

@Composable
private fun PosterGlowCarouselStar(size: Dp, fill: Float) {
    PosterGlowCarouselIcon(
        kind = PosterGlowCarouselIconKind.Star,
        size = size,
        color = PosterGlowCarouselColors.Star,
        offColor = PosterGlowCarouselColors.StarOff,
        fill = fill,
    )
}

@Composable
private fun PosterGlowCarouselIcon(
    kind: PosterGlowCarouselIconKind,
    size: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    offColor: Color = Color.Transparent,
    fill: Float = 1f,
) {
    val iconScale = with(LocalDensity.current) { size.toPx() / 24f }
    androidx.compose.foundation.Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = kind.name },
    ) {
        withTransform({ scale(iconScale, iconScale, Offset.Zero) }) {
            when (kind) {
                PosterGlowCarouselIconKind.Back -> {
                    drawLine(color, Offset(20f, 12f), Offset(5f, 12f), 2.2f, StrokeCap.Round)
                    drawLine(color, Offset(11f, 5f), Offset(4f, 12f), 2.2f, StrokeCap.Round)
                    drawLine(color, Offset(4f, 12f), Offset(11f, 19f), 2.2f, StrokeCap.Round)
                }
                PosterGlowCarouselIconKind.Gear -> {
                    val gear = posterGlowCarouselGearPath()
                    drawPath(gear, color, style = androidx.compose.ui.graphics.drawscope.Fill, alpha = 1f)
                    drawPath(gear, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f, join = StrokeJoin.Round))
                    drawCircle(PosterGlowCarouselColors.GearHole, 3.4f, Offset(12f, 12f))
                }
                PosterGlowCarouselIconKind.Star -> {
                    val path = posterGlowCarouselStarPath()
                    drawPath(path, offColor)
                    clipRect(right = 24f * fill) { drawPath(path, color) }
                }
                PosterGlowCarouselIconKind.Play -> drawPath(posterGlowCarouselPlayPath(), color)
                PosterGlowCarouselIconKind.PlayCircle -> {
                    drawCircle(color, 10f, Offset(12f, 12f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8f))
                    drawPath(posterGlowCarouselSmallPlayPath(), color)
                }
                PosterGlowCarouselIconKind.Home -> drawPath(posterGlowCarouselHomePath(), color)
                PosterGlowCarouselIconKind.Search -> {
                    drawCircle(color, 7.2f, Offset(10.6f, 10.6f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                    drawLine(color, Offset(16f, 16f), Offset(21f, 21f), 2f, StrokeCap.Round)
                }
                PosterGlowCarouselIconKind.Seat -> {
                    drawPath(posterGlowCarouselSeatTopPath(), color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6f, join = StrokeJoin.Round))
                    drawPath(posterGlowCarouselSeatBasePath(), color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6f, join = StrokeJoin.Round))
                    drawLine(color, Offset(6f, 17.5f), Offset(6f, 20f), 1.6f, StrokeCap.Round)
                    drawLine(color, Offset(18f, 17.5f), Offset(18f, 20f), 1.6f, StrokeCap.Round)
                    drawLine(color, Offset(9f, 13f), Offset(15f, 13f), 1.6f, StrokeCap.Round)
                }
                PosterGlowCarouselIconKind.Download -> {
                    drawLine(color, Offset(12f, 3.5f), Offset(12f, 15.5f), 2f, StrokeCap.Round)
                    drawLine(color, Offset(6.8f, 10.5f), Offset(12f, 15.7f), 2f, StrokeCap.Round)
                    drawLine(color, Offset(12f, 15.7f), Offset(17.2f, 10.5f), 2f, StrokeCap.Round)
                    drawLine(color, Offset(5f, 20.5f), Offset(19f, 20.5f), 2f, StrokeCap.Round)
                }
                PosterGlowCarouselIconKind.User -> {
                    drawCircle(color, 5.2f, Offset(12f, 7.2f))
                    drawPath(posterGlowCarouselUserBodyPath(), color)
                }
            }
        }
    }
}

private fun posterGlowCarouselGearPath(): Path {
    val path = Path()
    repeat(32) { index ->
        val angle = (index / 32f) * (PI.toFloat() * 2f) - PI.toFloat() / 2f
        val radius = if (index % 4 == 0 || index % 4 == 1) 11f else 8.4f
        val pointAngle = angle + PI.toFloat() / 32f
        val x = 12f + cos(pointAngle) * radius
        val y = 12f + sin(pointAngle) * radius
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private fun posterGlowCarouselStarPath() = Path().apply {
    moveTo(12f, 1.6f)
    lineTo(15.1f, 8.2f)
    lineTo(22.3f, 9.1f)
    lineTo(17f, 14f)
    lineTo(18.4f, 21.1f)
    lineTo(12f, 17.6f)
    lineTo(5.6f, 21.1f)
    lineTo(7f, 14f)
    lineTo(1.7f, 9.1f)
    lineTo(8.9f, 8.2f)
    close()
}

private fun posterGlowCarouselPlayPath() = Path().apply {
    moveTo(7f, 4.6f)
    cubicTo(7f, 3.5f, 8.2f, 2.8f, 9.2f, 3.4f)
    lineTo(20.2f, 10.3f)
    cubicTo(21.1f, 10.9f, 21.1f, 12.2f, 20.2f, 12.8f)
    lineTo(9.2f, 19.7f)
    cubicTo(8.2f, 20.4f, 7f, 19.7f, 7f, 18.6f)
    close()
}

private fun posterGlowCarouselSmallPlayPath() = Path().apply {
    moveTo(10f, 8.2f)
    lineTo(16f, 12f)
    lineTo(10f, 15.8f)
    close()
}

private fun posterGlowCarouselHomePath() = Path().apply {
    moveTo(3f, 10.6f)
    cubicTo(3f, 10f, 3.3f, 9.4f, 3.8f, 9f)
    lineTo(10.8f, 3.4f)
    cubicTo(11.5f, 2.8f, 12.5f, 2.8f, 13.2f, 3.4f)
    lineTo(20.2f, 9f)
    cubicTo(20.7f, 9.4f, 21f, 10f, 21f, 10.6f)
    lineTo(21f, 19f)
    cubicTo(21f, 20.1f, 20.1f, 21f, 19f, 21f)
    lineTo(15.4f, 21f)
    lineTo(15.4f, 15.6f)
    cubicTo(15.4f, 14.8f, 14.8f, 14.2f, 14f, 14.2f)
    lineTo(12f, 14.2f)
    cubicTo(11.2f, 14.2f, 10.6f, 14.8f, 10.6f, 15.6f)
    lineTo(10.6f, 21f)
    lineTo(5f, 21f)
    cubicTo(3.9f, 21f, 3f, 20.1f, 3f, 19f)
    close()
}

private fun posterGlowCarouselSeatTopPath() = Path().apply {
    moveTo(6f, 11f)
    lineTo(6f, 6.5f)
    cubicTo(6f, 5.1f, 7.1f, 4f, 8.5f, 4f)
    lineTo(15.5f, 4f)
    cubicTo(16.9f, 4f, 18f, 5.1f, 18f, 6.5f)
    lineTo(18f, 11f)
}

private fun posterGlowCarouselSeatBasePath() = Path().apply {
    moveTo(3.5f, 11f)
    lineTo(6.5f, 11f)
    lineTo(6.5f, 15.5f)
    lineTo(17.5f, 15.5f)
    lineTo(17.5f, 11f)
    lineTo(20.5f, 11f)
    lineTo(20.5f, 17.5f)
    lineTo(3.5f, 17.5f)
    close()
}

private fun posterGlowCarouselUserBodyPath() = Path().apply {
    moveTo(2.5f, 20.2f)
    cubicTo(2.5f, 16.9f, 6.8f, 14.6f, 12f, 14.6f)
    cubicTo(17.2f, 14.6f, 21.5f, 16.9f, 21.5f, 20.2f)
    cubicTo(21.5f, 21.2f, 20.7f, 22f, 19.7f, 22f)
    lineTo(4.3f, 22f)
    cubicTo(3.3f, 22f, 2.5f, 21.2f, 2.5f, 20.2f)
    close()
}

@Composable
private fun PosterGlowCarouselChrome(
    modifier: Modifier,
    backPress: Animatable<Float, *>,
    onBack: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .graphicsLayer { alpha = 1f - 0.6f * backPress.value }
            .semantics {
                contentDescription = "Back to profiles"
                role = Role.Button
            }
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        PosterGlowCarouselIcon(PosterGlowCarouselIconKind.Back, PosterGlowCarouselHome.ChromeSize.dp, PosterGlowCarouselColors.Ink)
    }
}

@Composable
private fun PosterGlowCarouselSettings(modifier: Modifier, onClick: (() -> Unit)?) {
    Box(
        modifier = modifier
            .size(44.dp)
            .semantics {
                contentDescription = "Settings"
                role = Role.Button
            }
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        contentAlignment = Alignment.Center,
    ) {
        PosterGlowCarouselIcon(PosterGlowCarouselIconKind.Gear, (PosterGlowCarouselHome.ChromeSize - 2f).dp, PosterGlowCarouselColors.Ink)
    }
}

@Composable
private fun BoxScope.PosterGlowCarouselTabBar(
    width: Float,
    bottomInset: Float,
    onTabSelected: ((PosterGlowCarouselTabIdentity) -> Unit)?,
) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height((PosterGlowCarouselHome.TabBar + bottomInset).dp)
            .background(PosterGlowCarouselColors.TabBar)
            .semantics { contentDescription = "FilmFlow navigation" },
    ) {
        val icons = listOf(
            PosterGlowCarouselIconKind.Home,
            PosterGlowCarouselIconKind.Search,
            PosterGlowCarouselIconKind.Seat,
            PosterGlowCarouselIconKind.Download,
            PosterGlowCarouselIconKind.User,
        )
        val identities = PosterGlowCarouselTabIdentity.entries
        icons.forEachIndexed { index, icon ->
            Box(
                modifier = Modifier
                    .offset(
                        x = (PosterGlowCarouselHome.TabX[index] / 402f * width - 22f).dp,
                        y = (PosterGlowCarouselHome.TabIconY - 22f).dp,
                    )
                    .size(44.dp)
                    .semantics {
                        contentDescription = listOf("Home", "Search", "Cinema", "Downloads", "Profile")[index]
                        role = Role.Tab
                        selected = index == 0
                    }
                    .clickable(enabled = onTabSelected != null) { onTabSelected?.invoke(identities[index]) },
                contentAlignment = Alignment.Center,
            ) {
                PosterGlowCarouselIcon(icon, PosterGlowCarouselHome.TabIcon[index].dp, PosterGlowCarouselColors.Ink)
            }
        }
    }
}
