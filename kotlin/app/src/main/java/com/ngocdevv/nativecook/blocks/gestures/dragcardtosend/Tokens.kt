package com.ngocdevv.nativecook.blocks.gestures.dragcardtosend

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.ngocdevv.nativecook.R

/** Every visual and motion value mirrored from the approved React Native block. */
internal object DragCardToSendTokens {
    object Colors {
        val page = Color(0xFFDCE5EC)
        val ink = Color(0xFF0B0C10)
        val mute = Color(0xFF7A808C)
        val rowFill = Color(0xFFCCD0DA)
        val rowIcon = Color(0xFFABAFBB)
        val headerButton = Color(0xFFDDE1E7)
        val card = Color(0xFF0A1FE8)
        val cardHigh = Color(0xFF0C28F2)
        val cardDeep = Color(0xFF0006A8)
        val cardInk = Color.White
        val cardMute = Color.White.copy(alpha = 0.62f)
        val cardFaint = Color.White.copy(alpha = 0.28f)
        val cardButton = Color(0x57000A78)
        val chip = Color(0xFFD9DEFF)
        val island = Color.Black
        val sheetInk = Color.White
        val sheetMute = Color(0xFF7D828C)
        val sheetLine = Color.White.copy(alpha = 0.08f)
        val sheetHover = Color.White.copy(alpha = 0.09f)
        val chipOff = Color(0xFF1C1D21)
        val chipOn = Color.White
        val ring = Color(0xFF3D5BFF)
        val next = Color(0xFF0010FF)
        val success = Color(0xFF3DDC84)
        val positive = Color(0xFF119C55)
        val shadow = Color(0xFF0A1A8C)
    }

    object Type {
        const val balance = 32.0
        const val cardSmall = 12.0
        const val cardLabel = 11.0
        const val brand = 20.0
        const val title = 22.0
        const val rowName = 15.0
        const val rowMeta = 12.0
        const val sheetTitle = 32.0
        const val sheetSection = 22.0
        const val favName = 12.0
        const val contact = 15.0
        const val chip = 14.0
        const val pill = 15.0
        const val hint = 13.0
    }

    object Space {
        const val xs = 4.0
        const val sm = 8.0
        const val md = 12.0
        const val lg = 16.0
        const val xl = 20.0
        const val xxl = 24.0
    }

    object Layout {
        const val side = 50.0
        const val pillTop = 5.0
        const val pillWidth = 300.0
        const val pillHeight = 48.0
        const val islandWidth = 126.0
        const val islandHeight = 37.0
        const val islandTop = 11.0
        const val headerCenter = 96.5
        const val headerButton = 44.0
        const val headerSpread = 26.5
        const val cardTop = 160.0
        const val dragLabelTop = 305.0
        const val cardHeight = 272.0
        const val cardRadius = 26.0
        const val cardPad = Space.xl
        const val actionSize = 44.0
        const val listTitleTop = 480.0
        const val rowTop = 557.0
        const val rowHeight = 63.0
        const val rowGap = Space.md
        const val rowIcon = 40.0
        const val avatarSmall = 28.0
        const val avatarOverlap = 10.0
    }

    object Sheet {
        const val inset = 19.0
        const val top = 89.0
        const val bottomGap = 40.0
        const val radius = 40.0
        const val pad = 37.0
        const val close = 40.0
        const val closeRight = 18.0
        const val titleTop = 36.5
        const val titleSize = 47.0
        const val titleLine = 50.0
        const val favTop = 160.0
        const val favSize = 42.0
        const val favPad = 12.0
        val favNudge = doubleArrayOf(1.7, -2.0, -6.3, -2.3)
        const val sectionTop = 271.0
        const val rowsTop = 347.0
        const val rowHeight = 59.0
        const val contactSize = 39.0
        const val rowAvatarX = 54.0
        const val nextTop = 681.5
        const val nextHeight = 52.0
        const val nextWidth = 281.0
    }

    object Drop {
        const val width = 230.0
        const val height = 342.0
        const val top = 40.0
        const val fillet = 22.0
        const val neckBreak = 22.0
    }

    object Token {
        const val scale = 0.42
    }

    data class Person(
        val id: String,
        val name: String,
        val initials: String,
        val from: Color,
        val to: Color,
        @DrawableRes val asset: Int,
    )

    val favourites = listOf(
        Person("rafi", "Rafi", "R", Color(0xFFFFB199), Color(0xFFFF6A88), R.drawable.dragcardtosend_fav_1),
        Person("nico", "Nico", "N", Color(0xFF9BE7FF), Color(0xFF2F80ED), R.drawable.dragcardtosend_fav_2),
        Person("samir", "Samir", "S", Color(0xFFFDE68A), Color(0xFFF59E0B), R.drawable.dragcardtosend_fav_3),
    )

    val contacts = listOf(
        Person("ada", "Ada Fenwick", "AF", Color(0xFFC4B5FD), Color(0xFF7C3AED), R.drawable.dragcardtosend_face_1),
        Person("ravi", "Ravi Okafor", "RO", Color(0xFFA7F3D0), Color(0xFF059669), R.drawable.dragcardtosend_face_2),
        Person("lena", "Lena Marsh", "LM", Color(0xFFFBCFE8), Color(0xFFDB2777), R.drawable.dragcardtosend_face_3),
        Person("joon", "Joon Park", "JP", Color(0xFFBAE6FD), Color(0xFF0284C7), R.drawable.dragcardtosend_face_4),
        Person("sofia", "Sofia Reyes", "SR", Color(0xFFFED7AA), Color(0xFFEA580C), R.drawable.dragcardtosend_face_5),
    )

    val people = favourites + contacts
    val amounts = intArrayOf(20, 50, 120, 250)
    const val defaultAmountIndex = 2
    const val startBalance = 1_500_075

    data class Transaction(
        val id: String,
        val name: String,
        val date: String,
        val amount: Int,
        val kind: Kind,
    )

    enum class Kind {
        In,
        Out,
    }

    val transactions = listOf(
        Transaction("t1", "Northwind Market", "Oct 12, 2026", -10_356, Kind.Out),
        Transaction("t2", "Salary · Harbor Co", "Oct 10, 2026", 100_097, Kind.In),
        Transaction("t3", "Fern Coffee", "Oct 9, 2026", -650, Kind.Out),
        Transaction("t4", "Juniper Books", "Oct 8, 2026", -2_480, Kind.Out),
    )

    object Pull {
        const val full = 400.0
        const val commit = 380.0
        const val sideFollow = 0.35
        const val upResist = 0.25
        const val shrink = 0.01
        const val fade = 0.0
        const val dropOpacity = 0.97
        const val ghostLead = 95.0
        const val listSink = 0.05
        const val deep = 0.14
        const val arrow = 0.9
    }

    object Ambient {
        const val sheenMs = 6_500.0
        const val sparks = 12
        const val sparkReach = 46.0
    }

    object Press {
        const val scale = 0.97
        const val duration = 90.0
        const val inMs = 100.0
        const val outMs = 160.0
    }

    data class Spring(
        val stiffness: Double,
        val damping: Double,
        val mass: Double,
    )

    object Springs {
        val open = Spring(170.0, 19.0, 1.0)
        val retract = Spring(300.0, 15.0, 1.0)
        val card = Spring(380.0, 30.0, 1.0)
        val token = Spring(420.0, 28.0, 0.8)
        val hover = Spring(520.0, 26.0, 0.7)
        val enter = Spring(260.0, 22.0, 1.0)
        val check = Spring(520.0, 18.0, 0.7)
    }

    object Magnet {
        const val radius = 64.0
        const val over = 48.0
        const val pull = 0.6
        const val targetScale = 1.2
    }

    object Send {
        const val absorbMs = 340.0
        const val absorbScale = 0.08
        val absorbEase = doubleArrayOf(0.77, 0.0, 0.175, 1.0)
        const val ringMs = 620.0
        const val holdMs = 420.0
        const val closeMs = 380.0
        val closeEase = doubleArrayOf(0.23, 1.0, 0.32, 1.0)
        const val enterFrom = 0.92
        const val fadeMs = 220.0
        const val rollMs = 700.0
        const val undoMs = 4_000.0
        const val pillSwapMs = 240.0
    }

    object Fade {
        const val contentFrom = 0.75
        const val contentTo = 1.15
        const val homeDim = 0.3
        const val reducedMs = 200.0
        const val ghost = 0.4
    }

    const val LOOP_MS = 10_000L
    const val LOOP = 10.0
    const val RESUME_AFTER_MS = 3_500.0

    object Script {
        const val down = 600.0
        const val pullEnd = 1_900.0
        const val pullBy = 390.0
        const val hoverStart = 2_300.0
        const val hoverArrive = 3_300.0
        const val release = 3_700.0
        const val undoTap = 7_900.0
    }

    const val QA_SLOWMO = 1.0
    val QA_HOLD_AT: Double? = null
    const val QA_SHEET = false
}
