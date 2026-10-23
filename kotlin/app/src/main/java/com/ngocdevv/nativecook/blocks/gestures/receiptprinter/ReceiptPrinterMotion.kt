package com.ngocdevv.nativecook.blocks.gestures.receiptprinter

/** Runtime aliases keep the state machine tied to the RN fidelity tokens. */
internal object ReceiptPrinterMotion {
    const val LOOP_MS = Tokens.LOOP_MS
    const val QA_SLOWMO = Tokens.QA_SLOWMO
    const val DEBUG_FREEZE_MS = Tokens.DEBUG_FREEZE_MS
    val LONG_PRESS_MS = Tokens.PRESS.longPressMs.toFloat()
    const val MENU_OUT_MS = Tokens.PRESS.menuOutMs
    const val GRID_OUT_MS = Tokens.SCREEN.gridOutMs
    const val GRID_IN_MS = Tokens.SCREEN.gridInMs
    /** The Dynamic Island opens before any paper emerges. */
    const val ISLAND_LEAD_MS = 220f
    const val FEED_MS = Tokens.FEED.ms
    const val FEED_STEPS = Tokens.FEED.steps
    const val FEED_PUSH = Tokens.FEED.push
    const val FEED_SWING = Tokens.FEED.swing
    const val FEED_SWING_DAMPING = Tokens.FEED.swingDamping
    const val FEED_SWING_HZ = Tokens.FEED.swingFreq
    const val RUBBER = Tokens.SCROLL.rubber
    const val DECELERATION = Tokens.SCROLL.deceleration
    const val DISMISS_BY = Tokens.SCROLL.dismissBy
    const val DISMISS_VELOCITY = Tokens.SCROLL.dismissVelocity
    const val BAR_WITHIN = Tokens.SCROLL.barWithin
    const val BAR_MS = Tokens.SCROLL.barMs
    const val RETRACT_MS = Tokens.RETRACT.ms
    const val MORPH_MS = Tokens.UPGRADE.morphMs
    const val RETURN_MS = Tokens.POLISH.returnMs
    const val DEVELOP_FADE_MS = Tokens.UPGRADE.developFadeMs
    const val CUT_DROP = Tokens.UPGRADE.cutDrop
    const val CUT_DOWN_MS = Tokens.UPGRADE.cutDownMs
    const val LED_BLINK_HZ = Tokens.UPGRADE.ledBlinkHz
    const val LIFT_MS = Tokens.UPGRADE.liftMs
    const val SWAY_DEG = Tokens.UPGRADE.swayDeg
    const val SWAY_MS = Tokens.UPGRADE.swayMs
    const val TUG = Tokens.UPGRADE.tug
    const val TUG_MS = Tokens.UPGRADE.tugMs
    const val HOLD_SCALE = Tokens.POLISH.holdScale
    const val PRESS_SCALE = Tokens.POLISH.pressScale
    const val PRESS_IN_MS = Tokens.POLISH.pressInMs
    const val PRESS_OUT_MS = Tokens.POLISH.pressOutMs
    const val PAPER_LENGTH = Tokens.RECEIPT.length
    val PAPER_EDGE = Tokens.RECEIPT.edge.depth
    val PAPER_TOTAL = PAPER_LENGTH + PAPER_EDGE
    const val BAR_TOP = Tokens.BAR.top
    // RN AUTOPLAY.resumeDelayMs stays 3500 in tokens; the user's current touch contract overrides its use.
    const val RESUME_DELAY_MS = Tokens.USER_RESUME_DELAY_MS
}
