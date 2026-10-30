package com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive

internal object CashbackPickFiveTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0
    const val S = 402.0 / 736
    const val PIN_W = 736.0
    const val MAX_PICKS = 5

    object C {
        const val bg = 0xEFF2FB
        const val ink = 0x10131C
        const val grey = 0x8A8E9C
        const val card = 0xFFFFFF
        const val bannerA = 0xDCCEFF
        const val bannerB = 0xF6F0FF
        const val chip = 0xDCD3FA
        const val box = 0xE6E8EF
        const val boxOn = 0x3E414D
        const val pay = 0xEF6155
        const val purple = 0x8528FF
        const val pink = 0xF795C6
        const val button = 0x1F222B
        const val disabled = 0xC9CCD6
        const val disabledText = 0xF2F3F7
        const val slot = 0xC9CDD8
        const val fine = 0x9DA1AE
        const val link = 0x7A4BE0
        const val panel = 0xFFFFFF
        const val star = 0xFFFFFF
    }

    data class Category(val pct: String, val name: String, val color: Int, val sub: String? = null)
    val cats = listOf(
        Category("2%", "All purchases", 0xE9B8F2),
        Category("10%", "Cafés, bars and restaurants", 0xF9AE1F),
        Category("3%", "Supermarkets", 0xF2B232),
        Category("10%", "Clothes and shoes", 0xCC7ACE),
        Category("10%", "Rideo Taxi", 0xFFD60A, "Cashback on rides in every fare"),
        Category("30%", "Reelhouse", 0x16161A, "Tickets in the app or on the site"),
        Category("5%", "Pharmacies", 0x3CC48A),
        Category("7%", "Beauty", 0xF7849E),
        Category("4%", "Fuel", 0x4E8CF5)
    )

    object Copy {
        const val brand = "ORBIT PLUS"
        val title = listOf("Choose 5 of 9 categories", "for June")
        const val banner = "100 000 Plus points"
        const val bannerSub = "Raffle on June 30. Join in?"
        const val section = "With your card"
        const val pay = "Pay"
        val connect = listOf("Connect Orbit Plus to start", "spending your points")
        const val choose = "Choose"
        val fine = listOf("14 days free, then $4.99 a month. By tapping the button you agree", "to the promotion terms and the Orbit Plus subscription terms")
    }

    object UI {
        const val backX = 64.0
        const val backY = 172.0
        const val logoX = 364.0
        const val logoY = 168.0
        const val titleX = 44.0
        val titleY = doubleArrayOf(288.0, 346.0)
        const val bannerX = 30.0
        const val bannerY = 456.0
        const val bannerW = 676.0
        const val bannerH = 134.0
        const val sectionY = 660.0
        const val listX = 29.0
        const val listY = 708.0
        const val listW = 677.0
        const val listTop = 774.0
        const val pitch = 102.0
        const val iconX = 91.0
        const val iconR = 33.0
        const val textX = 156.0
        const val boxX = 654.0
        const val box = 48.0
        const val disabledX = 44.0
        const val disabledY = 1394.0
        const val disabledW = 648.0
        const val disabledH = 102.0
        const val panelY = 1190.0
        const val toggleY = 1262.0
        const val buttonX = 44.0
        const val buttonY = 1326.0
        const val buttonW = 648.0
        const val buttonH = 102.0
        val fineY = doubleArrayOf(1462.0, 1486.0)
    }
}
