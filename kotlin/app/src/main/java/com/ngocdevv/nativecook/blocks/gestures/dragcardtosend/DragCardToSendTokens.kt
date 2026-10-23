package com.ngocdevv.nativecook.blocks.gestures.dragcardtosend

import androidx.annotation.ColorInt

/** Exact layout, content, colour, and motion tokens from the approved React Native block. */
internal object DragCardToSendTokens {
    object Colors {
        @ColorInt const val Page = 0xFFF4F5F7.toInt()
        @ColorInt const val Ink = 0xFF0B0C0F.toInt()
        @ColorInt const val Mute = 0xFF5E636D.toInt()
        @ColorInt const val Accent = 0xFF2B50F0.toInt()
        @ColorInt const val Island = 0xFF000000.toInt()
        @ColorInt const val White = 0xFFFFFFFF.toInt()
        @ColorInt const val CardShadow = 0xFF0A1246.toInt()
        @ColorInt const val AvatarPlaceholder = 0xFFDADDE3.toInt()
        @ColorInt const val CoffeeTile = 0xFFF1E8DF.toInt()
        @ColorInt const val CoffeeInk = 0xFF5B3B27.toInt()
        @ColorInt const val GrocerTile = 0xFFE3EEE5.toInt()
        @ColorInt const val GrocerInk = 0xFF2D5F40.toInt()
        @ColorInt const val TransitTile = 0xFFF3ECD8.toInt()
        @ColorInt const val TransitInk = 0xFF76560F.toInt()
    }

    object Type {
        const val LargeTitle = 32f
        const val Section = 19f
        const val Hint = 15f
        const val Name = 13f
        const val Row = 16f
        const val Meta = 13f
        const val CardLabel = 12f
        const val Balance = 32f
        const val Pan = 14f
        const val DropAmount = 26f
        const val ReceiptMeta = 13f
        const val ReceiptAmount = 22f
        const val Undo = 15f
    }

    object Layout {
        const val Edge = 16f
        const val HeaderTop = 84f
        const val HeaderHeight = 40f
        const val HeaderAvatar = 36f
        const val GapHeader = 16f
        const val GapSection = 32f
        const val CardRatio = 1.586f
        const val CardRadius = 18f
        const val CardPad = 24f
        const val CardShadowPad = 96f
        const val SectionHeight = 24f
        const val AvatarGap = 16f
        const val Avatar = 52f
        const val NameGap = 8f
        const val NameHeight = 16f
        const val RingGap = 4f
        const val RingStroke = 2.5f
        const val RowsGap = 8f
        const val RowHeight = 56f
        const val RowIcon = 40f
        const val VisibleRows = 4
        const val ChipX = 24f
        const val ChipY = 80f
        const val ChipW = 46f
        const val ChipH = 35f
        const val ChipRadius = 6f
    }

    object Island {
        const val Width = 94f
        const val Height = 36f
        const val Top = 14.35f
    }

    object Receipt {
        const val Top = 10f
        const val Height = 64f
        const val Avatar = 40f
        const val Badge = 18f
        const val Pad = 12f
        const val Gap = 10f
        const val UndoWidth = 92f
        const val UndoHeight = 36f
        const val Ring = 16f
        const val RingStroke = 2f
    }

    object Pull {
        const val Dead = 20f
        const val Step = 16f
        const val Tether = 34f
        const val UpResistance = 0.12f
        const val TapTravel = 6f
        const val Falloff = 80f
    }

    object Drop {
        const val BaseRadius = 12f
        const val RadiusScale = 5.2f
        const val Hang = 0.58f
        const val Blend = 15f
        const val Bevel = 0.9f
    }

    object Flight {
        const val DurationMs = 700f
        const val Travel = 0.78f
        const val AbsorbFrom = 0.64f
        const val Contact = 0.7f
        const val Stretch = 0.2f
        const val RecoilMs = 520f
        const val RecoilHz = 5.2f
    }

    object Send {
        const val RippleMs = 520f
        const val RippleScale = 0.34f
        const val CatchSquash = 0.1f
        const val ReceiptContentFrom = 0.85f
        const val ReceiptContentLag = 0.06f
        const val ReceiptContentFromReduced = 0.9f
        const val CloseMs = 240f
        const val RollMs = 400f
        const val UndoMs = 4_000f
        const val PressScale = 0.98f
        const val PressInMs = 150f
        const val PressOutMs = 100f
        const val RingInMs = 200f
        const val RingOutMs = 160f
        const val Nod = 12f
        const val Bead = 11f
        const val NodMs = 140f
        const val ReducedMs = 200f
        const val ReducedHoldMs = 120f
        const val ResumeAfterMs = 3_500f
    }

    object RowsMotion {
        const val ExitMs = 100f
        const val MoveMs = 250f
        const val EnterAfterMs = 175f
        const val EnterMs = 200f
        const val EnterScale = 0.97f
    }

    object Script {
        const val Down = 700f
        const val TouchX = 205f
        const val TouchY = 150f
        const val PullStart = 820f
        const val PullEnd = 2_050f
        const val PullBy = 156f
        const val SlideStart = 2_250f
        const val SlideEnd = 2_750f
        const val SlideTo = 1
        const val Release = 3_150f
        const val Undo = 6_350f
        const val LoopMs = 8_500f
    }

    enum class Merchant { Coffee, Grocer, Transit }

    data class Person(val id: String, val name: String, val fullName: String, val asset: String)

    data class Transaction(
        val id: String,
        val name: String,
        val meta: String,
        val cents: Int,
        val person: Int? = null,
        val merchant: Merchant? = null,
    )

    val People = listOf(
        Person("maya", "Maya", "Maya Lindqvist", "dragcardtosend/dragcardtosend_maya.jpg"),
        Person("theo", "Theo", "Theo Brandt", "dragcardtosend/dragcardtosend_theo.jpg"),
        Person("priya", "Priya", "Priya Shah", "dragcardtosend/dragcardtosend_priya.jpg"),
        Person("jonas", "Jonas", "Jonas Weber", "dragcardtosend/dragcardtosend_jonas.jpg"),
        Person("ana", "Ana", "Ana Costa", "dragcardtosend/dragcardtosend_ana.jpg"),
    )
    const val HeaderAsset = "dragcardtosend/dragcardtosend_jules.jpg"
    val Amounts = intArrayOf(5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 60, 70, 80, 90, 100, 125, 150, 175, 200)
    const val StartBalance = 248_120
    const val CardLast4 = "4821"
    val Transactions = listOf(
        Transaction("r1", "Arlo’s Coffee", "Today, 8:12 AM", -480, merchant = Merchant.Coffee),
        Transaction("r2", "Priya Shah", "Yesterday · Concert tickets", 3_200, person = 2),
        Transaction("r3", "Northside Grocer", "Mon, Oct 19", -6_317, merchant = Merchant.Grocer),
        Transaction("r4", "City Transit", "Sun, Oct 18 · Monthly pass", -8_900, merchant = Merchant.Transit),
    )
}

internal data class DragCardToSendLayout(
    val width: Float,
    val cardWidth: Float,
    val cardHeight: Float,
    val cardTop: Float,
    val sectionTop: Float,
    val avatarTop: Float,
    val activityTop: Float,
    val rowTop: Float,
    val pitch: Float,
    val avatarX: FloatArray,
) {
    val avatarY: Float = avatarTop + DragCardToSendTokens.Layout.Avatar / 2f
    val islandCenterX: Float = width / 2f
    val islandCenterY: Float = DragCardToSendTokens.Island.Top + DragCardToSendTokens.Island.Height / 2f
    val receiptCenterY: Float = DragCardToSendTokens.Receipt.Top + DragCardToSendTokens.Receipt.Height / 2f
}

internal fun dragCardToSendLayout(width: Float): DragCardToSendLayout {
    val token = DragCardToSendTokens.Layout
    val cardWidth = width - token.Edge * 2f
    val cardHeight = kotlin.math.round(cardWidth / token.CardRatio)
    val cardTop = token.HeaderTop + token.HeaderHeight + token.GapHeader
    val sectionTop = kotlin.math.ceil((cardTop + cardHeight + token.GapSection) / 4f) * 4f
    val avatarTop = sectionTop + token.SectionHeight + token.AvatarGap
    val activityTop = avatarTop + token.Avatar + token.NameGap + token.NameHeight + token.GapSection
    val rowTop = activityTop + token.SectionHeight + token.RowsGap
    val pitch = (cardWidth - token.Avatar) / (DragCardToSendTokens.People.size - 1)
    return DragCardToSendLayout(
        width = width,
        cardWidth = cardWidth,
        cardHeight = cardHeight,
        cardTop = cardTop,
        sectionTop = sectionTop,
        avatarTop = avatarTop,
        activityTop = activityTop,
        rowTop = rowTop,
        pitch = pitch,
        avatarX = FloatArray(DragCardToSendTokens.People.size) { token.Edge + token.Avatar / 2f + pitch * it },
    )
}
