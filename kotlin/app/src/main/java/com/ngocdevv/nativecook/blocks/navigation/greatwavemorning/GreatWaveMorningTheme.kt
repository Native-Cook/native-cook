package com.ngocdevv.nativecook.blocks.navigation.greatwavemorning

/** All coordinates remain in the 736-pixel pin, mapped into a 402 × 874 reference screen. */
internal object GreatWaveMorningTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0
    const val S = 402.0 / 736
    const val DY = 40.0
    const val PIN_W = 736.0

    object C {
        const val sky = "#133157"
        const val skyLow = "#1B3D69"
        const val pill = "#3D6BAE"
        const val cloud = "#E4D1B0"
        const val cloudShade = "#CDB48C"
        val bands = arrayOf("#2A4F82", "#3B67A2", "#24497C", "#4F7FBA", "#2D578F", "#5A8BC6", "#264C85", "#4373B0")
        const val river = "#7AA6D6"
        const val riverLow = "#5A8CC2"
        const val riverEdge = "#3A6BA0"
        const val crest = "#EFE9DF"
        const val crestShade = "#C9C2B6"
        const val page = "#F2ECDF"
        const val card = "#EEE5CF"
        const val cardEdge = "#C9B99B"
        const val navy = "#0F2347"
        const val blue = "#2F5E9E"
        const val tan = "#C39E67"
        const val green = "#21704A"
        const val grey = "#6B6457"
        const val time = "#A08F70"
        const val line = "#C2AE8E"
        const val row = "#FAF6EF"
        const val tabBg = "#F7F0E4"
        const val tabIcon = "#B49E7C"
        const val avatar = "#FAF6EE"
        const val avatarRing = "#C9A56A"
        const val boat = "#F3EEE6"
        const val sail = "#FBF8F2"
        const val sailShade = "#D9D2C6"
    }

    object COPY {
        const val hello = "Good morning, Yuna"
        const val short = "Today"
        const val initials = "YS"
        val sprint = arrayOf("Sprint 8", "·", "14 tasks due this week")
        val stats = listOf(
            Stat("Open", "38", arrayOf("", "across 6 projects"), C.blue, "#4E7FBE"),
            Stat("Due today", "6", arrayOf("3", " high priority"), C.tan, "#C9A86E"),
            Stat("Done this sprint", "94", arrayOf("", "of 147 total"), C.green, "#5E9A55"),
        )
        const val focus = "Today's Focus"
        val rows = listOf(
            Row("Review new component specs with dev", "09:30", "Due now", "#133F7A", "#D8B477", "#0F2347"),
            Row("Update sprint board before standup", "11:00", "In progress", "#5689C0", "#4C82BF", "#FFFFFF"),
            Row("Approve Q2 contractor invoices", "13:30", "Pending", "#D9B579", "#E6CFA6", "#0F2347"),
            Row("QA walkthrough for Tide app v2.3", "15:00", "Not started", "#133F7A", "#D9D7D1", "#3B4256"),
            Row("Resolve blocked API auth issue", "17:00", "Blocked", "#133F7A", "#102A4F", "#FFFFFF"),
        )
        val done = Row("", "", "Done", "#21704A", "#CFE3D3", "#1B5A3B")
        val tabs = arrayOf("Home", "Tasks", "Projects", "Employees", "Reports")
    }

    data class Stat(val label: String, val value: String, val sub: Array<String>, val color: String, val wave: String)

    data class Row(val title: String, val time: String, val status: String, val bar: String, val pill: String, val ink: String)

    object UI {
        object hello {
            const val x = 42.0
            const val y = 78.0
        }
        object avatar {
            const val x = 663.0
            const val y = 77.0
            const val r = 40.0
        }
        object pill {
            const val x = 40.0
            const val y = 113.0
            const val h = 42.0
        }
        object header {
            const val bottom = 508.0
            const val collapsed = 168.0
        }
        object cards {
            const val y = 538.0
            const val h = 192.0
            val x = doubleArrayOf(33.0, 261.0, 488.0)
            const val w = 212.0
            const val r = 10.0
        }
        object dots {
            const val y = 762.0
            val x = doubleArrayOf(331.0, 361.0, 391.0)
        }
        object focus {
            const val y = 814.0
            const val lineY = 821.0
        }
        object rows {
            const val y0 = 845.0
            const val h = 84.0
            const val pitch = 100.0
            const val x = 33.0
            const val w = 678.0
        }
        object tab {
            const val y = 1366.0
            const val h = 106.0
            val x = doubleArrayOf(80.0, 222.0, 366.0, 512.0, 660.0)
            const val tile = 84.0
        }
        object boat {
            const val x = 368.0
            const val y = 400.0
        }
    }
}
