package com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall

internal object ListeningBuddyPaywallTheme {
    const val REF_W = 402.0
    const val S = 402.0 / 736
    const val DY = 120.0
    const val PIN_W = 736.0
    object C {
        const val sky = 0xFAF0E4
        const val cloud = 0xFDF8F2
        const val hillA = 0xF5E5D5
        const val hillB = 0xFDF4EB
        const val body = 0xF18E4B
        const val bodyLight = 0xF6A56E
        const val bodyShade = 0xE27A36
        const val cheek = 0xEE7B33
        const val leaf = 0x7DB36A
        const val leafDark = 0x5E9A55
        const val phones = 0x555B75
        const val phonesLight = 0x737A96
        const val face = 0x4D5268
        const val bushA = 0x828DAB
        const val bushB = 0xA9B0DA
        const val title = 0x5F6680
        const val sub = 0x504B45
        const val card = 0xF7F3EF
        const val border = 0xF0A25E
        const val header = 0x959DB8
        const val label = 0x5F6680
        const val per = 0x6C6E7B
        const val note = 0x5F6680
        const val fine = 0xB5B3B0
        const val bar = 0x6E7389
        const val close = 0x5F6377
        const val white = 0xFFFFFF
        const val dot = 0xCAC8C9
        const val dotOff = 0xE8DFD6
    }
    data class Plan(val name: String, val price: String, val per: String, val lines: List<String>)
    val PLANS = arrayOf(
    Plan("MONTHLY", "12,99 €", "PER MONTH", listOf("Recurring billing. Cancel anytime.", "*That's 12,99 € a month.")),
    Plan("ANNUAL", "94,99 €*", "PER YEAR", listOf("Recurring billing. Cancel anytime.", "*That's 7,92 € a month.")),
    Plan("LIFETIME", "449,99 €", "ONE PAYMENT", listOf("No subscription. Pay once.", "*Yours for good, every update included."))
    )
    object COPY {
        const val title = "Subscribe now"
        val sub = arrayOf("Get access to hundreds of guided meditations in", "the Stillwater Library.")
        val popular = arrayOf("MOST", "POPULAR")
        val links = arrayOf("Restore Purchase", "Terms & Conditions")
        const val cta = "CONTINUE"
    }
    object UI {
        object close {
            const val x = 668.0
            const val y = 115.0
            const val r = 44.0
        }
        object title {
            const val y = 222.0
        }
        val sub = doubleArrayOf(296.0, 343.0)
        object dots {
            const val y = 593.0
            val x = doubleArrayOf(336.0,368.0,400.0)
        }
        object buddy {
            const val x = 128.0
            const val y = 640.0
        }
        const val white = 655.0
        object cards {
            const val y = 752.0
            const val h = 253.0
            val x = doubleArrayOf(49.0,275.0,501.0)
            const val w = 186.0
            const val r = 10.0
        }
        object header {
            const val h = 66.0
        }
        object frame {
            const val pad = 10.0
            const val r = 20.0
            const val w = 3.0
        }
        val lines = doubleArrayOf(1063.0,1101.0)
        const val links = 1152.0
        object bar {
            const val y = 1183.0
            const val h = 126.0
        }
    }
}
