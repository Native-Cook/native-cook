package com.ngocdevv.nativecook.blocks.lists.workoutstacklist

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.ngocdevv.nativecook.R

internal object WorkoutStackListSpec {
    const val ScreenWidth = 402f
    const val LOOP_MS = 11_750L
    const val Crossfit = 7
    const val DeckShift = -1.4f
    val Letters = listOf("M", "T", "W", "T", "F", "S", "S")
    val SessionTimes = listOf("4:30 am", "5:00 am", "5:30 am", "6:00 am")
    val Weeks = listOf(
        listOf(6, 7, 8, 9, 10, 11, 12),
        listOf(13, 14, 15, 16, 17, 18, 19),
        listOf(20, 21, 22, 23, 24, 25, 26),
        listOf(27, 28, 29, 30, 31, 0, 0),
    )
    val DimDays = setOf(6, 11, 13, 18, 20, 22, 24, 25, 27)
    val Dots = mapOf(
        7 to "r", 10 to "rg", 12 to "rg", 14 to "r", 15 to "rg", 16 to "r", 17 to "rg",
        19 to "r", 21 to "rg", 23 to "r", 26 to "r", 28 to "rg", 29 to "rg", 30 to "r", 31 to "r",
    )

    object Colors {
        val Background = Color(0xFF0C1217)
        val White = Color.White
        val Dim = Color(0xFF5F666B)
        val Label = Color(0xFF8E959B)
        val Red = Color(0xFFFE223E)
        val DotRed = Color(0xFFF2273B)
        val DotGreen = Color(0xFF3DDC84)
        val Grabber = Color(0xFF4A5257)
        val Tag = Color(0xFFE8232F)
        val TabInk = Color(0xFF3B3122)
        val TabPlusInk = Color(0xFF9A9A9A)
        val TabWash = Color(0x61FFF8E2)
        val TabSolid = Color(0xF0FAECC8)
    }

    object Header {
        const val Top = 66f
        const val Side = 22f
        const val Icon = 24f
        const val Avatar = 26f
    }

    object Deck {
        const val Width = 391f
        const val Height = 330f
        const val Radius = 26f
        const val TitleSize = 48f
        const val TitleAscent = .2f
        val Positions = floatArrayOf(-1f, 0f, 1f, 2f, 3f, 4f, 5f)
        val Tops = floatArrayOf(930f, 610.7f, 445f, 288.7f, 170.7f, 92.7f, 40f)
        val Scales = floatArrayOf(1.05f, 1f, .919f, .865f, .781f, .647f, .5f)
        val TitleTops = floatArrayOf(84.3f, 84.3f, 97.3f, 89.6f, 66.3f, 37.6f, 20f)
        val TitleScales = floatArrayOf(1f, 1f, 1f, .946f, .897f, .866f, .85f)
        val Opacities = floatArrayOf(0f, 1f, 1f, 1f, 1f, 1f, 0f)
        val Fog = floatArrayOf(0f, 0f, .15f, .3f, .38f, .45f, .5f)
        const val PhotoZoom = 1.6f
        const val PhotoTop = -45f
    }

    object TabBar {
        const val Top = 759f
        const val Left = 18f
        const val Width = 366f
        const val Height = 91f
        const val Radius = 20f
        val Centers = floatArrayOf(67.5f, 129f, 200f, 270f, 332f)
        const val IconY = 794f
        const val DotY = 813.5f
        const val Icon = 23f
        const val Plus = 45f
        const val PlusIcon = 18f
        const val Blur = 30f
    }

    object Detail {
        const val HeroHeight = 412f
        const val HeroScrollAway = 430f
        const val TitleInkTop = 368.3f
        const val TitleLeft = 19f
        const val TitleSize = 42f
        const val TitleTracking = 1.2f
        const val LettersTop = 121f
        const val NumbersTop = 158f
        const val CollapsedShift = 330f
        const val WeekSide = 14f
        const val Selected = 40f
        const val SelectedRadius = 11f
        const val LetterSize = 14f
        const val NumberSize = 15f
        const val MonthRow = 57f
        const val GrabberTop = 190f
        const val GrabberWidth = 30f
        const val LiveRest = 545f
        const val LiveTop = 215f
        const val LiveScale = .867f
        const val QuizTop = 245f
        const val LabelTop = 452f
        const val MondayTop = 485f
        const val MorningTop = 785f
        const val ListSide = 23f
        const val CardRadius = 16f
        val StackTops = floatArrayOf(407f, 482f, 562f, 683f)
        val StackScales = floatArrayOf(.8f, .86f, .92f, .97f)
        val Heights = floatArrayOf(180f, 178f, 275f, 275f)
        val ListTops = floatArrayOf(215f, 245f, 485f, 785f)
        const val ListScroll = 501f
        const val CardWidth = 356f
    }

    object Motion {
        val Scroll = 1_000f..1_500f
        val Open = 3_200f..3_750f
        val DetailScroll = 4_800f..5_300f
        val Month = 6_000f..6_600f
        const val DayTap = 8_400f
        val Day = 8_450f..8_950f
        val Back = 10_400f..11_000f
        const val ScrollSnapMs = 400L
        const val ScrollDampingRatio = .8f
        const val OpenMs = 550L
        const val MonthMs = 600L
        const val DayMs = 500L
        const val BackMs = 600L
        const val DeckDrag = 150f
        const val ResumeAfterMs = 3_500L
        const val ReducedFadeMs = 200L
        const val PressScale = .97f
        const val PressInMs = 120L
        const val PressOutMs = 150L
        const val BackZoom = 1.12f
    }

    val PoppinsRegular = FontFamily(Font(R.font.workoutstacklist_poppins_regular, FontWeight.Normal))
    val PoppinsMedium = FontFamily(Font(R.font.workoutstacklist_poppins_medium, FontWeight.Medium))
    val PoppinsSemibold = FontFamily(Font(R.font.workoutstacklist_poppins_semibold, FontWeight.SemiBold))
}

internal enum class WorkoutClassKind {
    Live,
    Quiz,
    Session,
}

internal data class WorkoutCategory(
    val name: String,
    val color: Color,
    val image: Int,
)

internal data class WorkoutCoach(
    val name: String,
    val image: Int,
)

internal data class WorkoutClassCard(
    val key: String,
    val title: String,
    val kind: WorkoutClassKind,
    val colors: Pair<Color, Color>,
    val image: Int?,
    val coach: WorkoutCoach?,
    val time: String?,
    val length: String?,
)

internal val WorkoutCategories = listOf(
    WorkoutCategory("Yoga", Color(0xFFF2B21C), R.drawable.workoutstacklist_cat_yoga),
    WorkoutCategory("Aerobics", Color(0xFF9E1530), R.drawable.workoutstacklist_cat_aerobics),
    WorkoutCategory("Cardio", Color(0xFF4FA9D8), R.drawable.workoutstacklist_cat_cardio),
    WorkoutCategory("Acrobatics", Color(0xFF1E8C8C), R.drawable.workoutstacklist_cat_acrobatics),
    WorkoutCategory("Running", Color(0xFFB5182B), R.drawable.workoutstacklist_cat_running),
    WorkoutCategory("Core", Color(0xFFD9A68A), R.drawable.workoutstacklist_cat_core),
    WorkoutCategory("HIIT", Color(0xFFF0A81C), R.drawable.workoutstacklist_cat_hiit),
    WorkoutCategory("Crossfit", Color(0xFF8E1222), R.drawable.workoutstacklist_cat_crossfit),
    WorkoutCategory("Balling", Color(0xFFE2A419), R.drawable.workoutstacklist_cat_balling),
    WorkoutCategory("Strength", Color(0xFF144C52), R.drawable.workoutstacklist_cat_strength),
)

private val CoachOne = WorkoutCoach("Tomás Herrera", R.drawable.workoutstacklist_coach_1)
private val CoachTwo = WorkoutCoach("Amara Okafor", R.drawable.workoutstacklist_coach_2)

internal val WorkoutDayEight = listOf(
    WorkoutClassCard("live8", "Monday\nMotivation", WorkoutClassKind.Live, Color(0xFFF2378F) to Color(0xFF7A5CF0), null, CoachOne, null, null),
    WorkoutClassCard("quiz8", "What is an\nAntioxidant", WorkoutClassKind.Quiz, Color(0xFFF7B31C) to Color(0xFFF39A14), R.drawable.workoutstacklist_class_antioxidant, CoachTwo, "8:30 am", "20 min"),
    WorkoutClassCard("monday8", "Monday\nMotivation", WorkoutClassKind.Session, Color(0xFFEC2B33) to Color(0xFFE02029), R.drawable.workoutstacklist_class_monday, null, null, "35 min."),
    WorkoutClassCard("morning8", "Morning\nFlow", WorkoutClassKind.Session, Color(0xFF2C9A9B) to Color(0xFF258486), R.drawable.workoutstacklist_class_morning, null, null, "30 min."),
)

internal val WorkoutDayNine = listOf(
    WorkoutClassCard("live9", "Break a\nSweat", WorkoutClassKind.Live, Color(0xFF2CB6B0) to Color(0xFF1B8FA3), null, CoachTwo, null, null),
    WorkoutClassCard("quiz9", "Sit and\nStretch", WorkoutClassKind.Quiz, Color(0xFFD9D4C8) to Color(0xFFBFB8A8), null, CoachOne, "7:00 am", "15 min"),
    WorkoutClassCard("run9", "Keep on\nRunning", WorkoutClassKind.Session, Color(0xFF3E7A4A) to Color(0xFF2F6439), R.drawable.workoutstacklist_class_running, null, null, "40 min."),
    WorkoutClassCard("hiit9", "High\nIntensity", WorkoutClassKind.Session, Color(0xFF8E1222) to Color(0xFF6E0D1A), R.drawable.workoutstacklist_class_hiit, null, null, "25 min."),
)
