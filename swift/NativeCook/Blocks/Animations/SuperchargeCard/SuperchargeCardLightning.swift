import Foundation

struct SuperchargeCardPoint: Equatable {
    var x: Double
    var y: Double
}

struct SuperchargeCardLightningFrame: Equatable {
    var main: [Double] = []
    var branch: [Double] = []
    var twig: [Double] = []
    var ground: [Double] = []
    var level = 0.0
    var forkLevel: Double?
    var firstPulse = false
}

struct SuperchargeCardBoltGeometry: Equatable {
    var main: [Double] = []
    var branch: [Double] = []
    var twig: [Double] = []
    var ground: [Double] = []
    var groundBranch: [Double] = []
    var groundTwig: [Double] = []
    var groundX: Double
    var meanX = 0.0
}

enum SuperchargeCardLightning {
    private struct Random {
        var seed: UInt32

        mutating func next() -> Double {
            seed = seed &* 1_664_525 &+ 1_013_904_223
            return Double(seed) / 4_294_967_296
        }
    }

    static func channel(from: SuperchargeCardPoint, to: SuperchargeCardPoint, seed: UInt32,
                        levels: Int = 6, roughness: Double = 0.11) -> [SuperchargeCardPoint] {
        var random = Random(seed: seed)
        let dx = to.x - from.x
        let dy = to.y - from.y
        let span = max(1, hypot(dx, dy))
        let vertical = abs(dy) > 1
        let detour = (random.next() * 2 - 1) * span * 0.16
        let bend = 0.3 + random.next() * 0.4
        var samples = [(t: 0.0, offset: 0.0), (t: bend, offset: detour), (t: 1.0, offset: 0.0)]
        var amplitude = span * roughness
        for _ in 0..<levels {
            var next = [samples[0]]
            for index in 1..<samples.count {
                let a = samples[index - 1]
                let b = samples[index]
                let fraction = 0.35 + random.next() * 0.3
                next.append((
                    t: a.t + (b.t - a.t) * fraction,
                    offset: a.offset + (b.offset - a.offset) * fraction + (random.next() * 2 - 1) * amplitude
                ))
                next.append(b)
            }
            samples = next
            amplitude *= 0.55
        }
        let kinkCount = 2 + Int(floor(random.next() * 3))
        var kinks: [Double] = []
        for index in 0..<kinkCount {
            let at = (Double(index) + 0.3 + random.next() * 0.4) / Double(kinkCount)
            let run = 0.025 + random.next() * 0.025
            let height = tan((25 + random.next() * 30) * .pi / 180) * span * run * (random.next() < 0.5 ? -1 : 1)
            kinks.append(contentsOf: [at, run, height])
        }
        return samples.map { sample in
            var offset = sample.offset
            for index in stride(from: 0, to: kinks.count, by: 3) {
                offset += max(0, 1 - abs(sample.t - kinks[index]) / kinks[index + 1]) * kinks[index + 2]
            }
            if vertical {
                return SuperchargeCardPoint(x: from.x + dx * sample.t + offset, y: from.y + dy * sample.t)
            }
            return SuperchargeCardPoint(x: from.x + dx * sample.t, y: from.y + dy * sample.t + offset)
        }
    }

    private static func append(_ points: [SuperchargeCardPoint], to target: inout [Double], start: Double, span: Double) {
        var total = 0.0
        for index in 1..<points.count {
            total += hypot(points[index].x - points[index - 1].x, points[index].y - points[index - 1].y)
        }
        var walked = 0.0
        for index in 1..<points.count {
            let a = points[index - 1]
            let b = points[index]
            let length = hypot(b.x - a.x, b.y - a.y)
            target.append(contentsOf: [
                a.x, a.y, b.x, b.y,
                start + span * walked / max(1, total),
                start + span * (walked + length) / max(1, total)
            ])
            walked += length
        }
    }

    private static func x(at y: Double, in points: [SuperchargeCardPoint]) -> Double {
        for index in 1..<points.count where points[index].y >= y {
            let a = points[index - 1]
            let b = points[index]
            return a.x + (b.x - a.x) * max(0, (y - a.y) / max(0.001, b.y - a.y))
        }
        return points.last!.x
    }

    private static func separated(_ fork: [SuperchargeCardPoint], from trunk: [SuperchargeCardPoint], side: Double) -> [SuperchargeCardPoint] {
        var result = [fork[0]]
        let root = fork[0]
        for index in 1..<fork.count {
            let a = fork[index - 1]
            let b = fork[index]
            for trunkPoint in trunk {
                let y = trunkPoint.y
                if y <= a.y || y >= b.y { continue }
                let candidate = a.x + (b.x - a.x) * (y - a.y) / (b.y - a.y)
                let edge = trunkPoint.x + side * (y - root.y) * 0.22
                result.append(SuperchargeCardPoint(x: side > 0 ? max(candidate, edge) : min(candidate, edge), y: y))
            }
            let edge = x(at: b.y, in: trunk) + side * (b.y - root.y) * 0.22
            result.append(SuperchargeCardPoint(x: side > 0 ? max(b.x, edge) : min(b.x, edge), y: b.y))
        }
        return result
    }

    private static func addForks(to points: [SuperchargeCardPoint], count: Int, random: inout Random,
                                 branch: inout [Double], twig: inout [Double]) {
        let top = points[0].y
        let height = points.last!.y - top
        var side = random.next() < 0.5 ? -1.0 : 1.0
        for index in 0..<count {
            let y = top + 8 + Double(index) * (height - 46) / Double(max(1, count - 1)) + random.next() * 5
            let origin = SuperchargeCardPoint(x: x(at: y, in: points), y: y)
            let progress = (y - top) / height
            if random.next() < 0.85 { side *= -1 }
            let local = atan2(x(at: y + 10, in: points) - x(at: y - 10, in: points), 20)
            let angle = (22 + random.next() * 20) * .pi / 180
            let heading = side * max(0.32, min(1.3, side * local + angle))
            let length = 60 + (1 - progress) * (70 + random.next() * 60)
            let end = SuperchargeCardPoint(
                x: origin.x + sin(heading) * length,
                y: origin.y + cos(heading) * length
            )
            let fork = separated(channel(from: origin, to: end, seed: random.seed, levels: 4), from: points, side: side)
            var tapered: [Double] = []
            append(fork, to: &tapered, start: progress, span: (1 - progress) * 0.85)
            for segment in stride(from: 0, to: tapered.count, by: 6) {
                let isBranch = (tapered[segment + 4] - progress) / max(0.001, (1 - progress) * 0.85) < 0.45
                if isBranch {
                    branch.append(contentsOf: tapered[segment..<(segment + 6)])
                } else {
                    twig.append(contentsOf: tapered[segment..<(segment + 6)])
                }
            }
            let subCount = Int(floor(random.next() * 3))
            for _ in 0..<subCount {
                let at = fork[Int(floor(Double(fork.count) * (0.3 + random.next() * 0.35)))]
                let subLength = 25 + random.next() * 35
                let end = SuperchargeCardPoint(x: at.x + side * subLength * 0.78, y: at.y + subLength * 0.63)
                let sub = separated(channel(from: at, to: end, seed: random.seed, levels: 3, roughness: 0.08), from: points, side: side)
                append(sub, to: &twig, start: progress + (1 - progress) * 0.3, span: (1 - progress) * 0.6)
            }
        }
    }

    static func strikeGeometry(seed: UInt32) -> SuperchargeCardBoltGeometry {
        var random = Random(seed: seed)
        let start = SuperchargeCardPoint(x: 90 + random.next() * 222, y: -30)
        let points = channel(from: start, to: SuperchargeCardConstants.Layout.boltTop, seed: random.seed)
        let groundX = 150 + random.next() * 100
        var result = SuperchargeCardBoltGeometry(groundX: groundX)
        append(points, to: &result.main, start: 0, span: 1)
        for point in points { result.meanX += point.x / Double(points.count) }
        addForks(to: points, count: 5 + Int(floor(random.next() * 3)), random: &random,
                 branch: &result.branch, twig: &result.twig)
        let groundEnd = SuperchargeCardPoint(x: groundX + (random.next() - 0.5) * 150, y: 900)
        let ground = channel(from: SuperchargeCardPoint(x: groundX, y: 628), to: groundEnd, seed: random.seed)
        append(ground, to: &result.ground, start: 0, span: 1)
        addForks(to: ground, count: 2 + Int(floor(random.next() * 2)), random: &random,
                 branch: &result.groundBranch, twig: &result.groundTwig)
        return result
    }

    private static func cut(_ lines: [Double], progress: Double, shimmer: Double) -> [Double] {
        var result: [Double] = []
        for index in stride(from: 0, to: lines.count, by: 6) {
            if lines[index + 4] >= progress { continue }
            let amount = min(1, (progress - lines[index + 4]) / max(0.00001, lines[index + 5] - lines[index + 4]))
            let jitter = index == 0 ? 0 : shimmer * sin(Double(index) * 7.13)
            let endJitter = index + 6 == lines.count ? 0 : shimmer * sin(Double(index + 6) * 7.13)
            result.append(contentsOf: [
                lines[index] + jitter,
                lines[index + 1],
                lines[index] + (lines[index + 2] - lines[index]) * amount + endJitter,
                lines[index + 1] + (lines[index + 3] - lines[index + 1]) * amount
            ])
        }
        return result
    }

    static func strikeFrame(_ geometry: SuperchargeCardBoltGeometry, age: Double, reduced: Bool) -> SuperchargeCardLightningFrame {
        if reduced {
            let level = age >= SuperchargeCardConstants.Reduced.channel ? 0 : max(0, sin(.pi * max(0, age) / SuperchargeCardConstants.Reduced.channel)) * SuperchargeCardConstants.Reduced.channelLevel
            return SuperchargeCardLightningFrame(main: cut(geometry.main, progress: 1.01, shimmer: 0), level: level)
        }
        if age >= SuperchargeCardConstants.Strike.channelEnd { return SuperchargeCardLightningFrame() }
        let leader = age < SuperchargeCardConstants.Strike.leader
        let front = leader
            ? min(1, Double(Int(floor(age / SuperchargeCardConstants.Strike.leaderJump)) + 1) / Double(SuperchargeCardConstants.Strike.leaderSteps))
            : 1.01
        var pulse = 0
        while pulse < SuperchargeCardConstants.Strike.pulseEnds.count && age >= SuperchargeCardConstants.Strike.pulseEnds[pulse] {
            pulse += 1
        }
        let tail = SuperchargeCardConstants.Strike.pulseEnds.last!
        let level = leader ? SuperchargeCardConstants.Strike.leaderLevel : SuperchargeCardConstants.Strike.pulseLevels[pulse] * (pulse == SuperchargeCardConstants.Strike.pulseEnds.count ? 1 - (age - tail) / (SuperchargeCardConstants.Strike.channelEnd - tail) : 1)
        let shimmer = leader || age < SuperchargeCardConstants.Strike.pulseEnds[1]
            ? 0
            : Double(Int(floor(age / SuperchargeCardConstants.Strike.shimmerInterval)) % SuperchargeCardConstants.Strike.shimmerVariants) * SuperchargeCardConstants.Strike.shimmerPoints
        var branch = age < SuperchargeCardConstants.Strike.branchEnd ? cut(geometry.branch, progress: front, shimmer: 0) : []
        var twig = age < SuperchargeCardConstants.Strike.branchEnd ? cut(geometry.twig, progress: front, shimmer: 0) : []
        if !leader && age < SuperchargeCardConstants.Strike.branchEnd {
            branch += cut(geometry.groundBranch, progress: 1.01, shimmer: 0)
            twig += cut(geometry.groundTwig, progress: 1.01, shimmer: 0)
        }
        return SuperchargeCardLightningFrame(
            main: cut(geometry.main, progress: front, shimmer: shimmer),
            branch: branch,
            twig: twig,
            ground: leader ? [] : cut(geometry.ground, progress: 1.01, shimmer: shimmer),
            level: level,
            forkLevel: leader ? SuperchargeCardConstants.Strike.leaderLevel : SuperchargeCardConstants.Strike.branchLevel * min(1, max(0, (SuperchargeCardConstants.Strike.branchEnd - age) / 0.03)),
            firstPulse: !leader && age < SuperchargeCardConstants.Strike.pulseEnds[0]
        )
    }

    private static func appendSegments(_ points: [SuperchargeCardPoint], to target: inout [Double]) {
        for index in 1..<points.count {
            target.append(contentsOf: [points[index - 1].x, points[index - 1].y, points[index].x, points[index].y])
        }
    }

    private static func thread(_ points: [SuperchargeCardPoint], through result: inout SuperchargeCardLightningFrame, strong: Bool) {
        var total = 0.0
        for index in 1..<points.count {
            total += hypot(points[index].x - points[index - 1].x, points[index].y - points[index - 1].y)
        }
        let root = strong ? total * 0.35 : 0
        var walked = 0.0
        for index in 1..<points.count {
            let a = points[index - 1]
            let b = points[index]
            let length = hypot(b.x - a.x, b.y - a.y)
            if walked < root && walked + length > root {
                let amount = (root - walked) / length
                let x = a.x + (b.x - a.x) * amount
                let y = a.y + (b.y - a.y) * amount
                result.branch.append(contentsOf: [a.x, a.y, x, y])
                result.twig.append(contentsOf: [x, y, b.x, b.y])
            } else if walked < root {
                result.branch.append(contentsOf: [a.x, a.y, b.x, b.y])
            } else {
                result.twig.append(contentsOf: [a.x, a.y, b.x, b.y])
            }
            walked += length
        }
    }

    static func filaments(x: Double, y: Double, energy: Double, seed: UInt32, charged: Bool = false) -> SuperchargeCardLightningFrame {
        var random = Random(seed: seed)
        var result = SuperchargeCardLightningFrame(level: charged ? 1 : SuperchargeCardConstants.Filament.base + SuperchargeCardConstants.Filament.gain * energy)
        var candidates: [SuperchargeCardPoint] = []
        let bolt = SuperchargeCardConstants.Layout.bolt
        for index in bolt.indices {
            let a = bolt[index]
            let b = bolt[(index + 1) % bolt.count]
            candidates.append(a)
            candidates.append(SuperchargeCardPoint(x: (a.x + b.x) / 2, y: (a.y + b.y) / 2))
        }
        let count = charged || energy > SuperchargeCardConstants.Filament.thirdAt ? 3 : 2
        for filament in 0..<count {
            var nearest = 0
            var distance = Double.infinity
            for index in candidates.indices {
                let candidateDistance = hypot(candidates[index].x - x, candidates[index].y - y)
                if candidateDistance >= 26 && candidateDistance < distance {
                    distance = candidateDistance
                    nearest = index
                }
            }
            let from = candidates.remove(at: nearest)
            _ = random.next()
            let points = channel(from: from, to: SuperchargeCardPoint(x: x, y: y), seed: random.seed, levels: 4, roughness: 0.2)
            thread(points, through: &result, strong: filament == 0)
            let forkCount = Int(floor(random.next() * 3))
            for _ in 0..<forkCount {
                let at = points[Int(floor((0.2 + random.next() * 0.5) * Double(points.count - 1)))]
                let side = random.next() < 0.5 ? -1.0 : 1.0
                let end = SuperchargeCardPoint(x: at.x + side * (12 + random.next() * 20), y: at.y - 8 - random.next() * 18)
                appendSegments(channel(from: at, to: end, seed: random.seed, levels: 3, roughness: 0.2), to: &result.twig)
            }
        }
        return result
    }

    static func corona(seed: UInt32, count: Int) -> [Double] {
        var random = Random(seed: seed)
        var result: [Double] = []
        let tips = [0, 2, 3, 5]
        for _ in 0..<count {
            let point = SuperchargeCardConstants.Layout.bolt[tips[Int(floor(random.next() * Double(tips.count)))]]
            let theta = atan2(point.y - 312, point.x - 214) + (random.next() - 0.5) * 1.2
            let length = 16 + random.next() * 24
            let end = SuperchargeCardPoint(x: point.x + cos(theta) * length, y: point.y + sin(theta) * length)
            appendSegments(channel(from: point, to: end, seed: random.seed, levels: 3, roughness: 0.22), to: &result)
        }
        return result
    }

    static func rimArcs(seed: UInt32, energy: Double, count: Int) -> SuperchargeCardLightningFrame {
        var random = Random(seed: seed)
        var result = SuperchargeCardLightningFrame(level: 0.6 + energy * 0.4)
        var candidates: [SuperchargeCardPoint] = []
        let bolt = SuperchargeCardConstants.Layout.bolt
        for index in bolt.indices {
            let a = bolt[index]
            let b = bolt[(index + 1) % bolt.count]
            if [0, 2, 3, 5].contains(index) { candidates.append(a) }
            candidates.append(SuperchargeCardPoint(x: (a.x + b.x) / 2, y: (a.y + b.y) / 2))
        }
        for arc in 0..<min(3, count) {
            var from = candidates[0]
            var to = SuperchargeCardPoint(x: SuperchargeCardConstants.Layout.glassX, y: 320)
            for _ in 0..<24 {
                from = candidates[Int(floor(random.next() * Double(candidates.count)))]
                let target = random.next()
                if energy >= 0.7 && target < 0.58 {
                    let theta = -Double.pi + random.next() * .pi
                    to = SuperchargeCardPoint(
                        x: SuperchargeCardConstants.Layout.ring.x + cos(theta) * SuperchargeCardConstants.Layout.ringRadius,
                        y: SuperchargeCardConstants.Layout.ring.y + sin(theta) * SuperchargeCardConstants.Layout.ringRadius
                    )
                } else if target < 0.72 {
                    to = SuperchargeCardPoint(x: SuperchargeCardConstants.Layout.glassX + random.next() * SuperchargeCardConstants.Layout.glassWidth,
                                              y: SuperchargeCardConstants.Layout.glassY)
                } else {
                    to = SuperchargeCardPoint(
                        x: random.next() < 0.5 ? SuperchargeCardConstants.Layout.glassX : SuperchargeCardConstants.Layout.glassX + SuperchargeCardConstants.Layout.glassWidth,
                        y: SuperchargeCardConstants.Layout.glassY + 12 + random.next() * 142
                    )
                }
                let length = hypot(to.x - from.x, to.y - from.y)
                if length >= 20 && length <= 140 { break }
            }
            let points = channel(from: from, to: to, seed: random.seed, levels: 4)
            thread(points, through: &result, strong: arc == 0)
            let forkCount = 1 + Int(floor(random.next() * 3))
            for _ in 0..<forkCount {
                let at = points[Int(floor((0.2 + random.next() * 0.5) * Double(points.count - 1)))]
                let angle = random.next() * .pi * 2
                let length = 10 + random.next() * 20
                let end = SuperchargeCardPoint(x: at.x + cos(angle) * length, y: at.y + sin(angle) * length)
                appendSegments(channel(from: at, to: end, seed: random.seed, levels: 3, roughness: 0.1), to: &result.twig)
            }
        }
        return result
    }
}
