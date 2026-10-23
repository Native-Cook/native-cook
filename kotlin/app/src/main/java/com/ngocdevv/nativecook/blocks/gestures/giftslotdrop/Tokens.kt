package com.ngocdevv.nativecook.blocks.gestures.giftslotdrop

/** Exact design and motion tokens from the approved React Native block. */
internal object GiftSlotDropTokens {
    const val MAX_FRAME_SECONDS = 0.064f

    object Color {
        const val page = "#0D0B09"
        const val ink = "#F6F2EC"
        const val mute = "#94F6F2EC"
        const val faint = "#5CF6F2EC"
        const val amber = "#FFB54A"
        const val amberInk = "#FFD08A"
        const val slotDeep = "#050403"
        const val lip = "#8CFFECC8"
        const val button = "#F6F2EC"
        const val buttonInk = "#16120D"
    }

    object Type {
        const val eyebrow = 11f
        const val title = 28f
        const val nav = 15f
        const val hint = 13f
        const val mark = 14f
        const val offer = 54f
        const val offerLine = 15f
        const val cap = 13f
        const val tag = 10f
        const val codeLabel = 11f
        const val code = 22f
        const val step = 15f
        const val stepNum = 13f
        const val button = 16f
    }

    object Layout {
        const val edge = 16f
        const val eyebrowY = 70f
        const val titleY = 106f
        const val titleLine = 34f
        const val cardY = 356f
        const val cardW = 212f
        const val cardH = 272f
        const val cardR = 22f
        const val sideScale = 0.84f
        const val sideTurn = 20f
        const val gap = 16f
        const val shelfY = 612f
        const val shelfW = 304f
        const val shelfH = 64f
        const val slotW = 236f
        const val slotH = 12f
        const val hintY = 548f
        const val giftW = 248f
        const val giftH = 318f
        const val giftY = 330f
        const val stepsY = 522f
        const val buttonH = 52f
    }

    data class Gift(
        val id: String,
        val shop: String,
        val kind: String,
        val offer: String,
        val line: String,
        val cap: String,
        val code: String,
        val steps: List<String>,
        val action: String,
        val background: String,
        val backgroundEnd: String,
        val ink: String,
        val sub: String,
        val mark: GiftSlotDropMark,
    )

    val gifts = listOf(
        Gift("kiln", "Kiln & Clay", "studio ceramics", "20%", "off your first order", "up to \$30", "KILN-20-H7QM", listOf("Add pieces worth \$40 or more", "Paste the code at checkout"), "Shop Kiln & Clay", "#B9573A", "#8E3B24", "#FFF4EA", "#B8FFF4EA", GiftSlotDropMark.Bowl),
        Gift("northline", "Northline", "rail tickets", "\$15", "off your first trip", "any route, any day", "NORTH-15-K4QZ", listOf("Book any single or return", "Paste the code before paying"), "Book with Northline", "#1F4D40", "#123128", "#EAF4EE", "#B3EAF4EE", GiftSlotDropMark.Rails),
        Gift("fernhouse", "Fernhouse", "houseplants", "2 for 1", "on any houseplant", "pots included", "FERN-241-P2LD", listOf("Pick two plants, pay for one", "Paste the code at checkout"), "Shop Fernhouse", "#EDE6D6", "#D9CFB9", "#1F2A22", "#A81F2A22", GiftSlotDropMark.Leaf),
        Gift("ember", "Ember Roasters", "coffee", "Free", "flat white for a week", "one a day, 7 days", "EMBER-7-W9TB", listOf("Show the code at the counter", "One flat white a day, 7 days"), "Find a café", "#2C1E17", "#1A110C", "#F1D9BC", "#A8F1D9BC", GiftSlotDropMark.Bean),
    )
    const val START_INDEX = 1

    object Drag {
        const val decide = 8f
        const val commit = 150f
        const val full = 210f
        const val minScale = 0.8f
        const val leanDeg = 46f
        const val lift = 1.03f
        const val upResist = 0.2f
        const val sideResist = 0.25f
        const val swipeVelocity = 600f
    }

    data class Spring(val durationMs: Float, val dampingRatio: Float)

    val snapSpring = Spring(420f, 0.86f)
    val backSpring = Spring(380f, 0.8f)
    val liftSpring = Spring(200f, 1f)

    object Sequence {
        const val sink = 380f
        const val read = 1_000f
        const val rise = 700f
        const val pause = 140f
        const val flip = 560f
        const val stagger = 60f
        const val detailsIn = 260f
    }

    object Close {
        const val out = 220f
        const val back = 420f
    }

    object Title {
        const val out = 140f
        const val inside = 220f
        const val rise = 6f
    }

    const val REDUCED_MS = 200f
    const val LOOP_MS = 10_000L
    const val RESUME_MS = 2_000f
    val scriptSwipes = listOf(
        GiftSlotDropScriptSwipe(500f, 460f, -220f),
        GiftSlotDropScriptSwipe(8_950f, 460f, 220f),
    )
    const val scriptPullDown = 1_450f
    const val scriptPullUp = 2_500f
    const val scriptPullBy = 196f
    const val scriptDone = 7_900f
}

internal enum class GiftSlotDropMark { Bowl, Rails, Leaf, Bean }

internal data class GiftSlotDropScriptSwipe(val at: Float, val duration: Float, val deltaX: Float)

internal data class GiftSlotDropLayout(
    val width: Float,
    val height: Float,
    val top: Float,
    val bottom: Float,
) {
    val centerX = width / 2f
    val cardY = top + GiftSlotDropTokens.Layout.cardY
    val spacing = GiftSlotDropTokens.Layout.cardW / 2f +
        GiftSlotDropTokens.Layout.cardW * GiftSlotDropTokens.Layout.sideScale / 2f +
        GiftSlotDropTokens.Layout.gap
    val slotY = top + GiftSlotDropTokens.Layout.shelfY
    val hintY = top + GiftSlotDropTokens.Layout.hintY
    val giftY = top + GiftSlotDropTokens.Layout.giftY
    val stepsY = top + GiftSlotDropTokens.Layout.stepsY
    val buttonY = height - maxOf(bottom, GiftSlotDropTokens.Layout.edge) - 8f - GiftSlotDropTokens.Layout.buttonH
    val dotsY = top + GiftSlotDropTokens.Layout.shelfY + 76f
    val printY = height - maxOf(bottom, GiftSlotDropTokens.Layout.edge) - 20f
}
