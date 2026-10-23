package com.ngocdevv.nativecook.blocks.animations.superchargecard

import kotlin.math.*

/** Seeded channel geometry; all distances are in the original 402 × 874 design space. */
internal object SuperchargeLightning {
    data class Point(val x: Double, val y: Double)

    class Random(var seed: Long) {
        fun next(): Double {
            seed = (seed * 1664525L + 1013904223L) and 0xffffffffL
            return seed / 4294967296.0
        }
    }

    data class Frame(
        val main: MutableList<Double> = mutableListOf(),
        val branch: MutableList<Double> = mutableListOf(),
        val twig: MutableList<Double> = mutableListOf(),
        val ground: MutableList<Double> = mutableListOf(),
        var level: Double = 0.0,
        val forkLevel: Double? = null,
        val firstPulse: Boolean = false,
    )

    class Geometry(val groundX: Double) {
        val main = mutableListOf<Double>()
        val branch = mutableListOf<Double>()
        val twig = mutableListOf<Double>()
        val ground = mutableListOf<Double>()
        val groundBranch = mutableListOf<Double>()
        val groundTwig = mutableListOf<Double>()
        var meanX = 0.0
    }

    private data class Sample(val t: Double, val offset: Double)

    val bolt =
        listOf(
            Point(240.0, 220.0),
            Point(222.0, 273.0),
            Point(281.0, 271.0),
            Point(156.0, 421.0),
            Point(193.0, 320.0),
            Point(149.0, 327.0),
        )

    fun channel(
        from: Point,
        to: Point,
        seed: Long,
        levels: Int = 6,
        roughness: Double = .11,
    ): List<Point> {
        val rng = Random(seed)
        val dx = to.x - from.x
        val dy = to.y - from.y
        val span = max(1.0, hypot(dx, dy))
        val vertical = abs(dy) > 1
        val detour = (rng.next() * 2 - 1) * span * .16
        val bend = .3 + rng.next() * .4
        var samples = listOf(Sample(0.0, 0.0), Sample(bend, detour), Sample(1.0, 0.0))
        var amplitude = span * roughness
        repeat(levels) {
            val next = mutableListOf(samples[0])
            for (i in 1 until samples.size) {
                val a = samples[i - 1]
                val b = samples[i]
                val fraction = .35 + rng.next() * .3
                next.add(
                    Sample(
                        a.t + (b.t - a.t) * fraction,
                        a.offset +
                            (b.offset - a.offset) * fraction +
                            (rng.next() * 2 - 1) * amplitude,
                    )
                )
                next.add(b)
            }
            samples = next
            amplitude *= .55
        }
        val kinkCount = 2 + floor(rng.next() * 3).toInt()
        val kinks = mutableListOf<Double>()
        repeat(kinkCount) { i ->
            val at = (i + .3 + rng.next() * .4) / kinkCount
            val run = .025 + rng.next() * .025
            val height =
                tan((25 + rng.next() * 30) * PI / 180) * span * run * if (rng.next() < .5) -1 else 1
            kinks.addAll(listOf(at, run, height))
        }
        return samples.map { sample ->
            var offset = sample.offset
            for (j in kinks.indices step 3) offset +=
                max(0.0, 1 - abs(sample.t - kinks[j]) / kinks[j + 1]) * kinks[j + 2]
            if (vertical) Point(from.x + dx * sample.t + offset, from.y + dy * sample.t)
            else Point(from.x + dx * sample.t, from.y + dy * sample.t + offset)
        }
    }

    private fun append(
        target: MutableList<Double>,
        points: List<Point>,
        start: Double,
        span: Double,
    ) {
        var total = 0.0
        for (i in 1 until points.size) total +=
            hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
        var walked = 0.0
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val length = hypot(b.x - a.x, b.y - a.y)
            target.addAll(
                listOf(
                    a.x,
                    a.y,
                    b.x,
                    b.y,
                    start + span * walked / max(1.0, total),
                    start + span * (walked + length) / max(1.0, total),
                )
            )
            walked += length
        }
    }

    private fun xAt(points: List<Point>, y: Double): Double {
        for (i in 1 until points.size) {
            if (points[i].y >= y) {
                val a = points[i - 1]
                val b = points[i]
                return a.x + (b.x - a.x) * max(0.0, (y - a.y) / max(.001, b.y - a.y))
            }
        }
        return points.last().x
    }

    private fun separateFork(fork: List<Point>, trunk: List<Point>, side: Int): List<Point> {
        val result = mutableListOf(fork[0])
        val root = fork[0]
        for (i in 1 until fork.size) {
            val a = fork[i - 1]
            val b = fork[i]
            for (point in trunk) {
                val y = point.y
                if (y <= a.y || y >= b.y) continue
                val x = a.x + (b.x - a.x) * (y - a.y) / (b.y - a.y)
                val edge = point.x + side * (y - root.y) * .22
                result.add(Point(if (side > 0) max(x, edge) else min(x, edge), y))
            }
            val edge = xAt(trunk, b.y) + side * (b.y - root.y) * .22
            result.add(Point(if (side > 0) max(b.x, edge) else min(b.x, edge), b.y))
        }
        return result
    }

    private fun forks(
        points: List<Point>,
        count: Int,
        rng: Random,
        branch: MutableList<Double>,
        twig: MutableList<Double>,
    ) {
        val top = points[0].y
        val height = points.last().y - top
        var side = if (rng.next() < .5) -1 else 1
        repeat(count) { i ->
            val y = top + 8 + i * (height - 46) / max(1, count - 1) + rng.next() * 5
            val origin = Point(xAt(points, y), y)
            val u = (y - top) / height
            if (rng.next() < .85) side *= -1
            val local = atan2(xAt(points, y + 10) - xAt(points, y - 10), 20.0)
            val angle = (22 + rng.next() * 20) * PI / 180
            val heading = side * max(.32, min(1.3, side * local + angle))
            val length = 60 + (1 - u) * (70 + rng.next() * 60)
            val end = Point(origin.x + sin(heading) * length, origin.y + cos(heading) * length)
            val fork = separateFork(channel(origin, end, rng.seed, 4, .11), points, side)
            val tapered = mutableListOf<Double>()
            append(tapered, fork, u, (1 - u) * .85)
            for (j in tapered.indices step 6) {
                val target =
                    if ((tapered[j + 4] - u) / max(.001, (1 - u) * .85) < .45) branch else twig
                for (k in 0 until 6) target.add(tapered[j + k])
            }
            repeat(floor(rng.next() * 3).toInt()) {
                val at = fork[floor(fork.size * (.3 + rng.next() * .35)).toInt()]
                val subLength = 25 + rng.next() * 35
                val sub =
                    separateFork(
                        channel(
                            at,
                            Point(at.x + side * subLength * .78, at.y + subLength * .63),
                            rng.seed,
                            3,
                            .08,
                        ),
                        points,
                        side,
                    )
                append(twig, sub, u + (1 - u) * .3, (1 - u) * .6)
            }
        }
    }

    fun strikeGeometry(seed: Long): Geometry {
        val rng = Random(seed)
        val start = Point(90 + rng.next() * 222, -30.0)
        val points = channel(start, bolt[0], rng.seed)
        val groundX = 150 + rng.next() * 100
        val result = Geometry(groundX)
        append(result.main, points, 0.0, 1.0)
        for (p in points) result.meanX += p.x / points.size
        forks(points, 5 + floor(rng.next() * 3).toInt(), rng, result.branch, result.twig)
        val ground =
            channel(
                Point(groundX, 628.0),
                Point(groundX + (rng.next() - .5) * 150, 900.0),
                rng.seed,
            )
        append(result.ground, ground, 0.0, 1.0)
        forks(
            ground,
            2 + floor(rng.next() * 2).toInt(),
            rng,
            result.groundBranch,
            result.groundTwig,
        )
        return result
    }

    private fun cut(lines: List<Double>, progress: Double, shimmer: Double): MutableList<Double> {
        val out = mutableListOf<Double>()
        for (i in lines.indices step 6) {
            if (lines[i + 4] >= progress) continue
            val t = min(1.0, (progress - lines[i + 4]) / max(.00001, lines[i + 5] - lines[i + 4]))
            val jitter = if (i == 0) 0.0 else shimmer * sin(i * 7.13)
            val endJitter = if (i + 6 == lines.size) 0.0 else shimmer * sin((i + 6) * 7.13)
            out.addAll(
                listOf(
                    lines[i] + jitter,
                    lines[i + 1],
                    lines[i] + (lines[i + 2] - lines[i]) * t + endJitter,
                    lines[i + 1] + (lines[i + 3] - lines[i + 1]) * t,
                )
            )
        }
        return out
    }

    fun strikeFrame(g: Geometry, age: Double, reduced: Boolean): Frame {
        if (reduced)
            return Frame(
                main = cut(g.main, 1.01, 0.0),
                level =
                    if (age >= M.reduced.channel) 0.0
                    else
                        max(0.0, sin(PI * max(0.0, age) / M.reduced.channel)) *
                            M.reduced.channelLevel,
            )
        if (age >= M.strike.channelEnd) return Frame()
        val leader = age < M.strike.leader
        val front =
            if (leader) min(1.0, (floor(age / M.strike.leaderJump) + 1) / M.strike.leaderSteps)
            else 1.01
        var pulse = 0
        while (pulse < M.strike.pulseEnds.size && age >= M.strike.pulseEnds[pulse]) pulse++
        val tail = M.strike.pulseEnds.last()
        val level =
            if (leader) M.strike.leaderLevel
            else
                M.strike.pulseLevels[pulse] *
                    (if (pulse == M.strike.pulseEnds.size)
                        1 - (age - tail) / (M.strike.channelEnd - tail)
                    else 1.0)
        val shimmer =
            if (leader || age < M.strike.pulseEnds[1]) 0.0
            else
                floor(age / M.strike.shimmerInterval) % M.strike.shimmerVariants *
                    M.strike.shimmerPoints
        val branch = if (age < M.strike.branchEnd) cut(g.branch, front, 0.0) else mutableListOf()
        val twig = if (age < M.strike.branchEnd) cut(g.twig, front, 0.0) else mutableListOf()
        if (!leader && age < M.strike.branchEnd) {
            branch.addAll(cut(g.groundBranch, 1.01, 0.0))
            twig.addAll(cut(g.groundTwig, 1.01, 0.0))
        }
        return Frame(
            cut(g.main, front, shimmer),
            branch,
            twig,
            if (leader) mutableListOf() else cut(g.ground, 1.01, shimmer),
            level,
            if (leader) M.strike.leaderLevel
            else M.strike.branchLevel * min(1.0, max(0.0, (M.strike.branchEnd - age) / .03)),
            !leader && age < M.strike.pulseEnds[0],
        )
    }

    private fun segments(points: List<Point>, target: MutableList<Double>) {
        for (i in 1 until points.size) target.addAll(
            listOf(points[i - 1].x, points[i - 1].y, points[i].x, points[i].y)
        )
    }

    private fun thread(points: List<Point>, result: Frame, strong: Boolean) {
        var total = 0.0
        for (i in 1 until points.size) total +=
            hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
        val root = if (strong) total * .35 else 0.0
        var walked = 0.0
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val length = hypot(b.x - a.x, b.y - a.y)
            if (walked < root && walked + length > root) {
                val t = (root - walked) / length
                val x = a.x + (b.x - a.x) * t
                val y = a.y + (b.y - a.y) * t
                result.branch.addAll(listOf(a.x, a.y, x, y))
                result.twig.addAll(listOf(x, y, b.x, b.y))
            } else {
                val target = if (walked < root) result.branch else result.twig
                target.addAll(listOf(a.x, a.y, b.x, b.y))
            }
            walked += length
        }
    }

    fun filaments(
        x: Double,
        y: Double,
        energy: Double,
        seed: Long,
        charged: Boolean = false,
    ): Frame {
        val rng = Random(seed)
        val result = Frame(level = if (charged) 1.0 else M.filament.base + M.filament.gain * energy)
        val candidates = mutableListOf<Point>()
        for (i in bolt.indices) {
            val a = bolt[i]
            val b = bolt[(i + 1) % bolt.size]
            candidates.add(a)
            candidates.add(Point((a.x + b.x) / 2, (a.y + b.y) / 2))
        }
        repeat(if (charged || energy > M.filament.thirdAt) 3 else 2) { n ->
            var nearest = 0
            var distance = Double.POSITIVE_INFINITY
            for (i in candidates.indices) {
                val d = hypot(candidates[i].x - x, candidates[i].y - y)
                if (d >= 26 && d < distance) {
                    distance = d
                    nearest = i
                }
            }
            val from = candidates.removeAt(nearest)
            rng.next()
            val points = channel(from, Point(x, y), rng.seed, 4, .2)
            thread(points, result, n == 0)
            repeat(floor(rng.next() * 3).toInt()) {
                val at = points[floor((.2 + rng.next() * .5) * (points.size - 1)).toInt()]
                val side = if (rng.next() < .5) -1 else 1
                segments(
                    channel(
                        at,
                        Point(at.x + side * (12 + rng.next() * 20), at.y - 8 - rng.next() * 18),
                        rng.seed,
                        3,
                        .2,
                    ),
                    result.twig,
                )
            }
        }
        return result
    }

    fun corona(seed: Long, count: Int): MutableList<Double> {
        val rng = Random(seed)
        val result = mutableListOf<Double>()
        val tips = intArrayOf(0, 2, 3, 5)
        repeat(count) {
            val p = bolt[tips[floor(rng.next() * tips.size).toInt()]]
            val theta = atan2(p.y - 312, p.x - 214) + (rng.next() - .5) * 1.2
            val length = 16 + rng.next() * 24
            segments(
                channel(
                    p,
                    Point(p.x + cos(theta) * length, p.y + sin(theta) * length),
                    rng.seed,
                    3,
                    .22,
                ),
                result,
            )
        }
        return result
    }

    fun rimArcs(seed: Long, energy: Double, count: Int): Frame {
        val rng = Random(seed)
        val result = Frame(level = .6 + energy * .4)
        val candidates = mutableListOf<Point>()
        for (i in bolt.indices) {
            val a = bolt[i]
            val b = bolt[(i + 1) % bolt.size]
            if (i == 0 || i == 2 || i == 3 || i == 5) candidates.add(a)
            candidates.add(Point((a.x + b.x) / 2, (a.y + b.y) / 2))
        }
        repeat(min(3, count)) { n ->
            var from = candidates[0]
            var to = Point(65.0, 320.0)
            for (attempt in 0 until 24) {
                from = candidates[floor(rng.next() * candidates.size).toInt()]
                val target = rng.next()
                if (energy >= .7 && target < .58) {
                    val theta = -PI + rng.next() * PI
                    to = Point(268 + cos(theta) * 30, 466 + sin(theta) * 30)
                } else if (target < .72) to = Point(65 + rng.next() * 272, 288.0)
                else to = Point(if (rng.next() < .5) 65.0 else 337.0, 288 + 12 + rng.next() * 142)
                val length = hypot(to.x - from.x, to.y - from.y)
                if (length >= 20 && length <= 140) break
            }
            val points = channel(from, to, rng.seed, 4, .11)
            thread(points, result, n == 0)
            repeat(1 + floor(rng.next() * 3).toInt()) {
                val at = points[floor((.2 + rng.next() * .5) * (points.size - 1)).toInt()]
                val angle = rng.next() * PI * 2
                val length = 10 + rng.next() * 20
                segments(
                    channel(
                        at,
                        Point(at.x + cos(angle) * length, at.y + sin(angle) * length),
                        rng.seed,
                        3,
                        .1,
                    ),
                    result.twig,
                )
            }
        }
        return result
    }
}
