package com.ngocdevv.nativecook.blocks.onboarding.tradingbotsonboarding

/** Local fixtures only. No wallet, orders, networking, billing or persisted entitlement. */
internal val TradingBotsSteps = listOf("Wallet", "Choose bots", "Setup", "Allocation", "Results", "Pro")
internal const val TradingBotsPro = 5
internal val TradingBotsRisks = listOf("2.0%", "0.5%", "0.25%")

internal data class TradingBotsFlow(
    val step: Int = 0,
    val selectedBot: Int = 1,
    val proFocus: Int = 0,
    val allocation: Float = 53f,
    val risk: String = "2.0%",
    val autoTrading: Boolean = true,
    val maxDailyLoss: Boolean = true,
    /** Seconds (uptime) of the last accepted CTA press. */
    val lastAdvance: Double = Double.NEGATIVE_INFINITY,
) {
    /**
     * Coalesce duplicate CTA input during the exit/entry handoff (480 ms). Back/Skip can
     * interrupt at any time and reset this gate. Returns null when the press is ignored.
     */
    fun advance(time: Double): TradingBotsFlow? {
        if (time - lastAdvance < 0.48 || step == TradingBotsPro) return null
        val next = step + 1
        return copy(step = next, lastAdvance = time, proFocus = if (next == TradingBotsPro) 0 else proFocus)
    }

    fun back() = copy(step = maxOf(0, step - 1), lastAdvance = Double.NEGATIVE_INFINITY)

    fun show(destination: Int) = copy(
        step = destination,
        proFocus = if (destination == TradingBotsPro) 0 else proFocus,
        lastAdvance = Double.NEGATIVE_INFINITY,
    )

    fun chooseBot(index: Int) = copy(selectedBot = index.coerceIn(0, 2))
    fun focusPro(index: Int) = copy(proFocus = index.coerceIn(0, 2))
    fun allocate(amount: Float) = copy(allocation = amount.coerceIn(0f, 100f))

    companion object {
        fun start(step: Int) = TradingBotsFlow(step = step.coerceIn(0, TradingBotsPro))
    }
}
