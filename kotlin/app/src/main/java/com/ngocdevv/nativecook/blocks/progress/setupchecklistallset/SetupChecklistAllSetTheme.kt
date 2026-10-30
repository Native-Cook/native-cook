package com.ngocdevv.nativecook.blocks.progress.setupchecklistallset

internal object SetupChecklistAllSetTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0
    object C {
        val page = "#F3F3F1"
        val ink = "#121212"
        val ink2 = "#2B2B2B"
        val grey = "#6E6E6A"
        val mute = "#A8A8A4"
        val dim = "#000000"
        val sheet = "#FFFFFF"
        val purple = "#7C41FF"
        val badge = "#8022FE"
        val barFrom = "#2865DA"
        val barTo = "#7943FD"
        val track = "#E0E1D9"
        val green = "#0C7324"
        val ring = "#DFE0DB"
        val divider = "#EFEFEC"
        val close = "#EFEFED"
        val chip = "#E9E9E6"
        val lilac = "#EDC2ED"
        val popper = "#1F1A6B"
        val cta = "#8129D9"
        val pro = "#5B2BD1"
        val confetti = listOf("#8022FE", "#EDC2ED", "#FFC83D", "#2865DA", "#0C7324", "#FF6FA8")
    }
    object COPY {
        val handle = "alexsmith"
        val url = "tapline.me/alexsmith"
        val pro = "Try Pro for free"
        val chips = listOf("Products", "Design", "Settings")
        val title = "Your setup checklist"
        val allSet = "You're all set!"
        val allSetBody = listOf("Now that you’ve shared your Tapline,", "keep that momentum going by sharing it in", "even more places.")
        val cta = "How to keep growing your traffic"
        val later = "Maybe later"
        val skip = "Skip"
        val ring = listOf("17%", "set up")
    }
    object UI {
        object sheet {
            val y = 299.0
            val r = 34.0
        }
        object title {
            val x = 17.0
            val y = 338.0
        }
        object badge {
            val x = 179.0
            val y = 328.0
            val w = 37.0
            val h = 20.0
        }
        object close {
            val x = 371.0
            val y = 338.0
            val r = 14.0
        }
        object bar {
            val x = 16.5
            val y = 369.0
            val w = 369.5
            val h = 11.4
        }
        object rows {
            val y0 = 396.5
            val h = 54.3
            val open = 115.5
            object check {
                val x = 28.3
                val r = 11.0
            }
            val textX = 49.4
            val chevronX = 377.0
        }
        object desc {
            val dy = listOf(39.5, 57.8)
        }
        object action {
            val dy = 100.5
            val h = 34.0
            val padX = 13.0
        }
        val skipDx = 22.0
        object page {
            object avatar {
                val x = 37.5
                val y = 97.0
                val r = 20.0
            }
            object pro {
                val x = 76.0
                val y = 77.0
                val w = 134.0
                val h = 40.0
            }
            object share {
                val x = 316.0
                val y = 97.0
            }
            object bell {
                val x = 373.0
                val y = 97.0
            }
            object handle {
                val x = 17.0
                val y = 150.0
            }
            object url {
                val x = 17.0
                val y = 192.0
            }
            object chips {
                val y = 249.0
                val x = listOf(17.0, 145.0, 259.0)
                val w = listOf(121.0, 107.0, 117.0)
                val h = 40.0
            }
            object cards {
                val y = 296.0
                val h = 207.0
            }
            object ring {
                val x = 41.0
                val y = 572.0
                val r = 31.0
            }
        }
        object allSet {
            val y = 68.0
            object icon {
                val x = 201.0
                val y = 390.0
                val r = 51.0
            }
            val title = 486.5
            val body = listOf(528.0, 552.0, 576.0)
            object cta {
                val x = 16.0
                val y = 689.0
                val w = 370.0
                val h = 51.0
            }
            val later = 775.5
        }
    }
    data class Item(val title: String, val body: List<String>, val action: String)
    val items = listOf(
        Item("Add your name and bio", listOf("This will be the first thing visitors see when they", "land on your Tapline."), "Add name and bio"),
        Item("Add your profile image", listOf("A friendly face tells visitors they have found", "the right page."), "Add profile image"),
        Item("Add your socials", listOf("Link the places people already follow you,", "all in one row."), "Add socials"),
        Item("Add your other important links", listOf("Your shop, your newsletter, your latest video:", "one tap away."), "Add links"),
        Item("Customize your design", listOf("Pick a look and feel that perfectly reflects who you", "are and how you want to appear to the world."), "Customize design"),
        Item("Share your Tapline", listOf("Put your Tapline in your bio so every visitor", "finds the rest of you."), "Copy link")
    )
}
