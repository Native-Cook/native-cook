package com.ngocdevv.nativecook.blocks.lists.luckytaskhub

internal object LuckyTaskHubTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0
    const val S = 402.0 / 720.0
    const val DY = 96.0
    const val BALANCE = 12480.0
    const val BILL_VALUE = 100.0
    const val STEPS = 14
    const val FLIPS = 30
    data class Game(val name: String, val reward: String)
    object C {
        const val bgTop = "#E6F6C3"
        const val bgMid = "#F6FBEF"
        const val page = "#FFFFFF"
        const val ink = "#1B1C22"
        const val ink2 = "#2A2C33"
        const val grey = "#7B7F87"
        const val line = "#E2E8DA"
        const val yellow = "#FFD21F"
        const val yellowDeep = "#F6B800"
        const val panel = "#FFF6C2"
        const val podium = "#FFE15A"
        const val redCard = "#FF4A2E"
        const val orange = "#FF8A1F"
        const val pink = "#F07ED3"
        const val pinkDeep = "#D95BBE"
        const val green = "#7ED321"
        const val greenDeep = "#56B000"
        const val wheelA = "#B9EE6B"
        const val wheelB = "#8FDB3A"
        const val cardBg = "#F3F7EE"
        const val cardEdge = "#E1E8D6"
        const val pill = "#E7EDE0"
        const val timerBg = "#FFF1D8"
        const val bolt = "#FFB000"
        const val white = "#FFFFFF"
        const val black = "#17181C"
        const val cash = "#5FCB3A"
    }
    object COPY {
        const val header = "Rewards"
        const val ranking = "Ranking"
        const val timer = "05:06:26"
        val tiers = listOf("Bronze", "Silver", "Gold")
        const val flip = "Lucky Flip"
        const val spin = "Lucky Spin"
        const val go = "Go"
        const val newbie = "Newbie Tasks"
        const val newbieTimer = "6D:23:21:10"
        const val node = "Rs27.00"
        const val goal = "500"
        const val offers = "Super Offers"
        const val seeAll = "See All"
        val games = listOf(Game("Tiger Clash", "1,605"), Game("Zap Rush", "270"), Game("Fruit Tiles", "14K"))
        val progress = listOf("You have ", "2", " offer(s) in progress")
        const val chest = "02:48"
        const val casual = "Casual Games Tasks"
        const val row = "Play Fruit Tiles for 10 minutes"
        const val rowReward = "120"
    }
    object UI {
        val header = Header()
        val ranking = Ranking()
        val flip = Flip()
        val spin = Spin()
        val newbie = Newbie()
        val progress = Progress()
        val offers = Offers()
        val inProgress = InProgress()
        val casual = Casual()
        class Header {
            val titleY = -48.0
            val balance = Balance()
            class Balance {
                val x = 498.0
                val y = -78.0
                val w = 190.0
                val h = 60.0
            }
        }
        class Ranking {
            val card = Card()
            val back = Back()
            val title = Title()
            val timer = Timer()
            val panel = Panel()
            val trophy = Trophy()
            val podium = Podium()
            val label = Label()
            val mascot = Mascot()
            val arrow = Arrow()
            val go = Go()
            class Card {
                val x = 40.0
                val y = 50.0
                val w = 310.0
                val h = 395.0
                val r = 30.0
            }
            class Back {
                val x = 34.0
                val y = 38.0
                val w = 300.0
                val h = 410.0
                val r = 30.0
                val rot = -4.0
            }
            class Title {
                val x = 62.0
                val y = 92.0
            }
            class Timer {
                val x = 62.0
                val y = 118.0
                val w = 124.0
                val h = 32.0
            }
            class Panel {
                val x = 56.0
                val y = 196.0
                val w = 282.0
                val h = 170.0
                val r = 26.0
            }
            class Trophy {
                val x = 197.0
                val y = 248.0
                val size = 150.0
            }
            class Podium {
                val x = 195.0
                val y = 318.0
                val rx = 96.0
                val ry = 22.0
            }
            class Label {
                val x = 197.0
                val y = 337.0
                val w = 118.0
                val h = 30.0
            }
            class Mascot {
                val x = 128.0
                val y = 266.0
            }
            class Arrow {
                val x = 262.0
                val y = 250.0
            }
            class Go {
                val x = 66.0
                val y = 388.0
                val w = 86.0
                val h = 36.0
            }
        }
        class Flip {
            val card = Card()
            val title = Title()
            val remaining = Remaining()
            val go = Go()
            val back = Back()
            val front = Front()
            val fire = Fire()
            class Card {
                val x = 368.0
                val y = 50.0
                val w = 320.0
                val h = 193.0
                val r = 30.0
            }
            class Title {
                val x = 390.0
                val y = 92.0
            }
            class Remaining {
                val x = 392.0
                val y = 126.0
            }
            class Go {
                val x = 392.0
                val y = 180.0
                val w = 86.0
                val h = 36.0
            }
            class Back {
                val x = 600.0
                val y = 182.0
                val size = 96.0
                val rot = -16.0
            }
            class Front {
                val x = 634.0
                val y = 170.0
                val size = 104.0
                val rot = 9.0
            }
            class Fire {
                val x = 664.0
                val y = 72.0
                val r = 23.0
            }
        }
        class Spin {
            val card = Card()
            val title = Title()
            val remaining = Remaining()
            val go = Go()
            val wheel = Wheel()
            val eyes = Eyes()
            class Card {
                val x = 368.0
                val y = 258.0
                val w = 320.0
                val h = 192.0
                val r = 30.0
            }
            class Title {
                val x = 390.0
                val y = 298.0
            }
            class Remaining {
                val x = 392.0
                val y = 332.0
            }
            class Go {
                val x = 392.0
                val y = 387.0
                val w = 86.0
                val h = 36.0
            }
            class Wheel {
                val x = 626.0
                val y = 398.0
                val r = 62.0
            }
            class Eyes {
                val x = 626.0
                val y = 346.0
            }
        }
        class Newbie {
            val titleX = 32.0
            val titleY = 524.0
            val timer = Timer()
            class Timer {
                val x = 505.0
                val y = 507.0
                val w = 183.0
                val h = 34.0
            }
        }
        class Progress {
            val card = Card()
            val count = Count()
            val bar = Bar()
            val node = Node()
            val nodeLabel = NodeLabel()
            val goal = Goal()
            val arrow = Arrow()
            class Card {
                val x = 32.0
                val y = 573.0
                val w = 656.0
                val h = 138.0
                val r = 32.0
            }
            class Count {
                val x = 62.0
                val y = 632.0
            }
            class Bar {
                val x = 168.0
                val y = 614.0
                val w = 462.0
                val h = 28.0
            }
            class Node {
                val x = 316.0
                val y = 627.0
                val r = 27.0
            }
            class NodeLabel {
                val y = 675.0
            }
            class Goal {
                val x = 592.0
                val y = 622.0
            }
            class Arrow {
                val x = 640.0
                val y = 607.0
                val w = 60.0
                val h = 48.0
            }
        }
        class Offers {
            val titleY = 786.0
            val cards = listOf(33.0, 247.0, 461.0, 677.0)
            val y = 852.0
            val w = 190.0
            val h = 203.0
            val icon = 92.0
        }
        class InProgress {
            val avatar = Avatar()
            val bubble = Bubble()
            val chest = Chest()
            class Avatar {
                val x = 62.0
                val y = 1122.0
                val r = 42.0
            }
            class Bubble {
                val x = 118.0
                val y = 1090.0
                val w = 560.0
                val h = 64.0
            }
            class Chest {
                val x = 624.0
                val y = 1060.0
                val w = 96.0
                val h = 96.0
            }
        }
        class Casual {
            val titleY = 1241.0
            val row = Row()
            class Row {
                val x = 32.0
                val y = 1282.0
                val w = 656.0
                val h = 120.0
            }
        }
    }
    fun fillAt(done: Double): Double {
        val nodeK = (UI.progress.node.x - UI.progress.bar.x) / UI.progress.bar.w
        return if (done <= 5) done / 5 * nodeK else nodeK + (done - 5) / (STEPS - 5) * (1 - nodeK)
    }
}
