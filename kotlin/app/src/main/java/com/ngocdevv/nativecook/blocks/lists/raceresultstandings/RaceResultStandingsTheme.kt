package com.ngocdevv.nativecook.blocks.lists.raceresultstandings

/** Reference tokens for the 402-point Race Result Standings layout. */
internal object RaceResultStandingsTheme {
    const val REF_W: Double = 402.0
    const val REF_TOP: Double = 62.0
    const val ASCENT: Double = 0.968

    object FONT {
        const val medium: String = "RaceMontserrat-Medium"
        const val semibold: String = "RaceMontserrat-SemiBold"
        const val bold: String = "RaceMontserrat-Bold"
        const val extrabold: String = "RaceMontserrat-ExtraBold"
    }

    object colors {
        val ink: Int = 0xFFFFFFFF.toInt()
        val muted: Int = 0xFF9B9B9B.toInt()
        val hairline: Int = 0xFF413D41.toInt()
        val base: Int = 0xFF020002.toInt()
        val backFill: Int = 0xFF484848.toInt()
        val backRing: Int = 0xFF686868.toInt()
        val stopwatch: Int = 0xFF5A42DB.toInt()
        val award: Int = 0xFFE4232D.toInt()
        val wheel: Int = 0xFFF8C91C.toInt()
        val up: Int = 0xFF83D165.toInt()
        val down: Int = 0xFFE4232D.toInt()
        val comet: Int = 0xFFFFF6D8.toInt()
        val cometGlow: Int = 0xFFF8C91C.toInt()
        val shade: Int = 0xFF000000.toInt()
        val plate: Int = 0xFF141216.toInt()
        val plateEdge: Int = 0xFF2E2A30.toInt()
        val lightOff: Int = 0xFF2A2629.toInt()
    }

    data class Stop(val y: Double, val grey: Int)

    data class Backdrop(
        val centre: Array<Stop>,
        val edge: Array<Stop>,
        val edgeProfile: DoubleArray,
    )

    val backdrop: Backdrop = Backdrop(
        centre = arrayOf(
            Stop(0.0, 17),
            Stop(16.7, 23),
            Stop(33.3, 29),
            Stop(50.0, 35),
            Stop(66.7, 40),
            Stop(83.3, 45),
            Stop(100.0, 47),
            Stop(116.7, 46),
            Stop(133.3, 43),
            Stop(150.0, 40),
            Stop(166.7, 33),
            Stop(183.3, 28),
            Stop(200.0, 22),
            Stop(216.7, 18),
            Stop(233.3, 13),
            Stop(250.0, 9),
            Stop(266.7, 6),
            Stop(283.3, 4),
            Stop(300.0, 3),
            Stop(316.7, 1),
            Stop(333.3, 0),
        ),
        edge = arrayOf(
            Stop(0.0, 23),
            Stop(33.3, 22),
            Stop(50.0, 18),
            Stop(66.7, 17),
            Stop(83.3, 12),
            Stop(100.0, 10),
            Stop(150.0, 9),
            Stop(166.7, 11),
            Stop(200.0, 11),
            Stop(233.3, 9),
            Stop(250.0, 8),
            Stop(266.7, 5),
            Stop(300.0, 4),
            Stop(316.7, 2),
            Stop(333.3, 1),
            Stop(350.0, 0),
        ),
        edgeProfile = doubleArrayOf(1.0, 0.64, 0.36, 0.16, 0.04, 0.0, 0.04, 0.16, 0.36, 0.64, 1.0),
    )

    data class Space(
        val screenInset: Double,
        val rowInset: Double,
        val headerRuleInset: Double,
        val pointsInset: Double,
        val textColumn: Double,
        val nameColumn: Double,
        val statColumn: Double,
        val markToCode: Double,
        val rowPitch: Double,
        val statPitch: Double,
        val labelGap: Double,
        val teamGap: Double,
        val badgeToDigit: Double,
        val hit: Double,
    )

    val space: Space = Space(
        screenInset = 21.67,
        rowInset = 18.33,
        headerRuleInset = 20.33,
        pointsInset = 19.33,
        textColumn = 53.67,
        nameColumn = 66.33,
        statColumn = 227.67,
        markToCode = 5.67,
        rowPitch = 83.15,
        statPitch = 48.67,
        labelGap = 17.33,
        teamGap = 18.92,
        badgeToDigit = 5.0,
        hit = 44.0,
    )

    data class Back(val cx: Double, val cy: Double, val d: Double, val ring: Double)

    data class Title(val x: Double, val baseline: Double, val size: Double)

    data class Sort(val x: Double, val y: Double, val w: Double, val h: Double, val stroke: Double)

    data class TextPosition(val x: Double, val baseline: Double, val size: Double)

    data class Podium(
        val markRight: DoubleArray,
        val codeX: DoubleArray,
        val markY: Double,
        val markW: Double,
        val markH: Double,
        val baseline: Double,
        val size: Double,
    )

    data class Track(val x: Double, val y: Double, val w: Double, val h: Double)

    data class StatColumn(val x: Double, val labelX: Double, val baseline: Double)

    data class Stats(
        val valueSize: Double,
        val labelSize: Double,
        val lap: StatColumn,
        val day: StatColumn,
        val pit: StatColumn,
    )

    data class HeaderRule(val y: Double)

    data class Badge(val x: Double, val y: Double, val w: Double, val h: Double)

    data class Plate(val x: Double, val w: Double, val inset: Double, val radius: Double)

    data class Rows(
        val firstRule: Double,
        val numberX: Double,
        val numberBaseline: Double,
        val numberSize: Double,
        val nameX: Double,
        val nameBaseline: Double,
        val nameSize: Double,
        val teamX: Double,
        val teamBaseline: Double,
        val teamSize: Double,
        val pointsRight: Double,
        val pointsBaseline: Double,
        val pointsSize: Double,
        val ptsRight: Double,
        val ptsBaseline: Double,
        val ptsSize: Double,
        val badge: Badge,
        val digitX: Double,
        val digitBaseline: Double,
        val digitSize: Double,
        val plate: Plate,
        val trailX: Double,
        val trailW: Double,
        val trailMax: Double,
    )

    data class Layout(
        val back: Back,
        val title: Title,
        val sort: Sort,
        val round: TextPosition,
        val event: TextPosition,
        val podium: Podium,
        val track: Track,
        val stats: Stats,
        val headerRule: HeaderRule,
        val rows: Rows,
    )

    val layout: Layout = Layout(
        back = Back(cx = 29.17, cy = 82.5, d = 42.33, ring = 0.67),
        title = Title(x = 123.92, baseline = 93.97, size = 20.17),
        sort = Sort(x = 272.0, y = 80.0, w = 8.33, h = 14.33, stroke = 1.55),
        round = TextPosition(x = 20.34, baseline = 147.18, size = 14.17),
        event = TextPosition(x = 20.07, baseline = 177.03, size = 22.83),
        podium = Podium(
            markRight = doubleArrayOf(50.33, 121.33, 194.67),
            codeX = doubleArrayOf(54.89, 126.22, 199.22),
            markY = 203.0,
            markW = 30.0,
            markH = 8.33,
            baseline = 211.07,
            size = 11.83,
        ),
        track = Track(x = 295.0, y = 145.67, w = 90.0, h = 96.67),
        stats = Stats(
            valueSize = 12.17,
            labelSize = 10.17,
            lap = StatColumn(x = 53.39, labelX = 53.26, baseline = 257.07),
            day = StatColumn(x = 53.13, labelX = 53.26, baseline = 305.36),
            pit = StatColumn(x = 226.53, labelX = 226.26, baseline = 305.36),
        ),
        headerRule = HeaderRule(y = 356.5),
        rows = Rows(
            firstRule = 447.83,
            numberX = 18.56,
            numberBaseline = 405.12,
            numberSize = 19.83,
            nameX = 65.62,
            nameBaseline = 403.48,
            nameSize = 18.17,
            teamX = 65.19,
            teamBaseline = 422.4,
            teamSize = 12.17,
            pointsRight = 383.09,
            pointsBaseline = 403.64,
            pointsSize = 18.17,
            ptsRight = 383.34,
            ptsBaseline = 420.31,
            ptsSize = 9.83,
            badge = Badge(x = 21.0, y = 418.0, w = 6.67, h = 7.33),
            digitX = 32.59,
            digitBaseline = 424.88,
            digitSize = 9.83,
            plate = Plate(x = 52.0, w = 262.0, inset = 6.0, radius = 16.0),
            trailX = 58.0,
            trailW = 2.33,
            trailMax = 44.0,
        ),
    )

    data class Icon(val cx: Double, val cy: Double, val r: Double, val stroke: Double)

    data class Icons(val stopwatch: Icon, val award: Icon, val wheel: Icon)

    val icons: Icons = Icons(
        stopwatch = Icon(cx = 32.67, cy = 258.67, r = 9.55, stroke = 1.5),
        award = Icon(cx = 32.67, cy = 302.5, r = 6.25, stroke = 1.5),
        wheel = Icon(cx = 205.83, cy = 305.67, r = 10.3, stroke = 1.7),
    )

    enum class TeamId {
        scarlet,
        tidewave,
        kestrel,
        bluecrest,
        crest,
    }

    enum class MarkKind {
        fin,
        twin,
        stair,
        chevron,
    }

    data class Team(val name: String, val text: Int, val mark: Int)

    val teams: Map<TeamId, Team> = mapOf(
        TeamId.scarlet to Team(name = "Scarlet", text = 0xFFD72A36.toInt(), mark = 0xFFE92432.toInt()),
        TeamId.tidewave to Team(name = "Tidewave", text = 0xFF16BFB7.toInt(), mark = 0xFF00C9C0.toInt()),
        TeamId.kestrel to Team(name = "Kestrel", text = 0xFFEC601B.toInt(), mark = 0xFFEC601B.toInt()),
        TeamId.bluecrest to Team(name = "Bluecrest", text = 0xFF2966CC.toInt(), mark = 0xFF2966CC.toInt()),
        TeamId.crest to Team(name = "Crest Junior", text = 0xFF2B66CB.toInt(), mark = 0xFF2B66CB.toInt()),
    )

    data class Driver(val name: String, val code: String, val team: TeamId, val mark: MarkKind)

    val drivers: Array<Driver> = arrayOf(
        Driver(name = "Mira Chen", code = "CHE", team = TeamId.scarlet, mark = MarkKind.fin),
        Driver(name = "Alex Vale", code = "VAL", team = TeamId.tidewave, mark = MarkKind.twin),
        Driver(name = "Sam Ives", code = "IVE", team = TeamId.scarlet, mark = MarkKind.stair),
        Driver(name = "Noah Reed", code = "REE", team = TeamId.kestrel, mark = MarkKind.chevron),
        Driver(name = "Theo Marsh", code = "MAR", team = TeamId.bluecrest, mark = MarkKind.twin),
        Driver(name = "Kai Lindgren", code = "LIN", team = TeamId.crest, mark = MarkKind.twin),
    )

    /** Circuit dot, trail and overtake-tag colours are per driver, not merely per team. */
    data class CarShade(val fill: Int, val ink: Int)

    val carShade: Array<CarShade> = arrayOf(
        CarShade(fill = 0xFFD72A36.toInt(), ink = 0xFFFFFFFF.toInt()), // Mira Chen, Scarlet
        CarShade(fill = 0xFF16BFB7.toInt(), ink = 0xFFFFFFFF.toInt()), // Alex Vale, Tidewave
        CarShade(fill = 0xFFFF8C93.toInt(), ink = 0xFF2A0407.toInt()), // Sam Ives, Scarlet light
        CarShade(fill = 0xFFEC601B.toInt(), ink = 0xFFFFFFFF.toInt()), // Noah Reed, Kestrel
        CarShade(fill = 0xFF2966CC.toInt(), ink = 0xFFFFFFFF.toInt()), // Theo Marsh, Bluecrest
        CarShade(fill = 0xFF8DB4F5.toInt(), ink = 0xFF06142E.toInt()), // Kai Lindgren, Crest Junior light
    )

    data class Session(
        val word: String,
        val order: IntArray,
        val points: IntArray,
        val delta: IntArray,
        val fastestLap: String,
    )

    val sessions: Array<Session> = arrayOf(
        Session(
            word = "Race",
            order = intArrayOf(0, 1, 2, 3, 4, 5),
            points = intArrayOf(25, 18, 15, 12, 10, 8),
            delta = intArrayOf(1, 2, 0, 2, 0, 4),
            fastestLap = "Jonah Price (1:31.482)",
        ),
        Session(
            word = "Sprint",
            order = intArrayOf(1, 3, 0, 5, 2, 4),
            points = intArrayOf(8, 7, 6, 5, 4, 3),
            delta = intArrayOf(-1, 3, -2, 1, 0, 5),
            fastestLap = "Alex Vale (1:32.906)",
        ),
    )

    object copy {
        const val results: String = " Results"
        const val round: String = "Round 09"
        const val event: String = "Harbor GP"
        const val lapLabel: String = "Fastest Lap"
        const val dayValue: String = "Mira Chen"
        const val dayLabel: String = "Driver of the Day"
        const val pitValue: String = "Tidewave (2.140s)"
        const val pitLabel: String = "Fastest Pitstop"
        const val pts: String = "PTS"
    }

    fun slotsOf(session: Session): IntArray {
        val slots = IntArray(session.order.size)
        session.order.forEachIndexed { slot, driver -> slots[driver] = slot }
        return slots
    }

    val SLOT_OF: Array<IntArray> = arrayOf(slotsOf(sessions[0]), slotsOf(sessions[1]))

    data class Mark(val streak: String?, val bars: String)

    val marks: Map<MarkKind, Mark> = mapOf(
        MarkKind.fin to Mark(
            streak = "M0 1.1L20 0L18.4 4.3Z",
            bars = "M20.82 3.6L28.82 3.6L30.20 0L22.20 0ZM20.60 8.33L28.60 8.33L30.03 4.6L22.03 4.6Z",
        ),
        MarkKind.twin to Mark(
            streak = "M0 1.1L15.5 0L13.9 4.3Z",
            bars = "M14.50 8.33L20.70 8.33L23.90 0L17.70 0ZM22.50 8.33L26.10 8.33L29.30 0L25.70 0Z",
        ),
        MarkKind.stair to Mark(
            streak = null,
            bars = "M5.50 8.33L10.70 8.33L12.67 3.2L7.47 3.2Z" +
                "M12.60 8.33L17.80 8.33L20.39 1.6L15.19 1.6Z" +
                "M19.70 8.33L24.90 8.33L28.10 0L22.90 0Z",
        ),
        MarkKind.chevron to Mark(
            streak = "M0 1.1L17 0L15.4 4.3Z",
            bars = "M16.5 8.33L21 8.33L26.8 4.17L24.2 0L19.7 0L22.3 4.17Z",
        ),
    )
}
