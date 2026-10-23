import Foundation

/// The canonical `motion.ts` contract. Every automatic frame is a pure function of loop time.
enum RaceResultStandingsMotion {
    static let loop = 10.0
    static let resumeAfter = 2.0

    static let `switch` = 1.7
    static let lap = 1.3
    static let lapEnd = 1.15
    static let fastestRoll = 0.3
    static let tail: [(lag: Double, width: Double, alpha: Double)] = [
        (0.03, 2.6, 1), (0.06, 2.1, 0.8), (0.09, 1.6, 0.6),
        (0.12, 1.15, 0.4), (0.15, 0.7, 0.22),
    ]
    static let glowLag = 0.08

    static let pressDown = 0.1
    static let pressUp = 0.12
    static let pressScale = 0.97
    static let chevronOpen = 1.5

    static let wordStart = 0.0
    static let wordDuration = 0.3
    static let podiumStart = 0.06
    static let podiumStep = 0.07
    static let podiumDuration = 0.32
    static let pointsStart = 0.3
    static let pointsStep = 0.07
    static let pointsDuration = 0.32
    static let rowStart = 0.08
    static let rowStep = 0.07
    static let rowDuration = 1.25
    static let badgeFrom = 0.35
    static let badgeTo = 0.65

    static let rowOmega = 7.2
    static let rowZeta = 0.78
    static let laneRise = 10.0
    static let laneFall = -4.0
    static let leanMax = 1.6
    static let leanSpeed = 600.0
    static let dimFall = 0.6
    static let sinkFall = 0.03
    static let liftRise = 0.025
    static let trailPerSpeed = 0.05
    static let trailAlphaSpeed = 500.0
    static let trailAlpha = 0.85
    static let roll = 0.62

    static let dragSpan = 0.6
    static let dragCommit = 0.5
    static let dragVelocity = 500.0
    static let dragActiveOffset = 14.0
    static let dragFailOffset = 12.0
    static let rubber = 0.55
    static let rubberFalloff = 120.0
    static let settleDamping = 0.8
    static let reducedFade = 0.1

    static let replayAt = 0.8
    static let replayDotsIn = (0.55, 0.9)
    static let replayLightsOn = 0.85
    static let replayGo = 1.95
    static let replayFinish = 5.9
    static let replayClose = 6.15
    static let replayDotsOut = 0.25
    static let replayDimIn = 0.35
    static let replayDimOutFrom = 0.4
    static let replayDimOut = 0.45
    static let replayEnd = 7.1

    static let mapScale = 2.35
    static let mapFrom = CGPoint(x: 340, y: 194)
    static let mapTo = CGPoint(x: 201, y: 240)
    static let mapTilt = 10.0
    static let mapPerspective = 700.0
    static let mapOmega = 9.0
    static let mapZeta = 0.8
    static let mapSettle = 0.85
    static let mapHiResFrom = 0.25
    static let mapPress = 0.97

    static let lightsCount = 5
    static let lightsStep = 0.18
    static let lightsOn = 0.06
    static let lightsOff = 0.08
    static let lightsCenter = CGPoint(x: 201, y: 124)
    static let lightsPitch = 15.0
    static let lightsRadius = 4.5
    static let lightsPill = CGSize(width: 84, height: 17)
    static let lightsGlowRadius = 2.0
    static let lightsGlowBlur = 4.0
    static let lightsGlowAlpha = 0.45
    static let lightsHousingIn = (0.55, 0.8)
    static let lightsHousingOut = (0.35, 0.65)

    static let raceLaps = 2.0
    static let raceLaunch = 0.35
    static let raceGridGap = 0.03
    static let raceGap = 0.022
    static let raceGapSettle = 1.2
    static let racePass = 0.35
    static let raceLateral = 1.0
    static let raceGridStagger = 0.8
    static let raceStaggerOut = 0.6
    static let raceCoolDelay = 0.25
    static let raceCoolDown = 0.5
    static let raceTrailLag = 0.09

    static let carCore = 2.9
    static let carCentre = 1.2
    static let carGlow = 7.0
    static let carGlowBlur = 4.0
    static let carGlowAlpha = 0.55
    static let raceTrails: [(part: Double, width: Double, alpha: Double)] = [
        (1, 3.4, 0.22), (0.6, 2.6, 0.45), (0.28, 2, 0.9),
    ]
    static let tagSize = 7.5
    static let tagPadX = 4.0
    static let tagHeight = 12.0
    static let tagAbove = 9.0
    static let tagRise = 3.0
    static let tagRadius = 6.0
    static let tagShow = (0.3, 0.14)
    static let tagHide = (0.55, 0.7)
    static let finishFlashDuration = 0.35
    static let finishFlashFrom = 2.0
    static let finishFlashTo = 13.0
    static let finishFlashStroke = 1.4
    static let pointsHide = (0.1, 0.35)

    static let rewind: [(at: Double, up: Int, down: Int)] = [
        (0.15, 2, 1), (0.22, 4, 3),
    ]
    static let passes: [(at: Double, up: Int, down: Int)] = [
        (2.55, 1, 2), (3.05, 3, 4), (3.6, 1, 0),
        (4.15, 5, 4), (4.75, 0, 1), (5.3, 4, 5),
    ]
    static let gridSlot = [0, 2, 1, 4, 3, 5]
    static let rewindOmega = 12.0
    static let rewindZeta = 0.999
    static let rewindDuration = 0.6

    struct Frame: Equatable {
        var from = 0
        var u = 0.0
        var lap = 0.0
        var fastestFrom = 0
        var fastestU = 0.0
        var press = 0.0
        var replay = 0.0
    }

    struct ReplayRow {
        let y: Double
        let speed: Double
        let bump: Double
        let direction: Int
    }

    struct ReplayPoints {
        let alpha: Double
        let rise: Double
    }

    static func clamp(_ value: Double) -> Double { min(1, max(0, value)) }

    static func segment(_ time: Double, _ start: Double, _ end: Double) -> Double {
        clamp((time - start) / (end - start))
    }

    static func lerp(_ from: Double, _ to: Double, _ progress: Double) -> Double {
        from + (to - from) * progress
    }

    static func easeOut(_ value: Double) -> Double {
        cubicBezier(clamp(value), 0.23, 1, 0.32, 1)
    }

    static func cubicBezier(_ progress: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        if progress == 0 || progress == 1 { return progress }
        func coordinate(_ t: Double, _ a: Double, _ b: Double) -> Double {
            let k = 1 - t
            return 3 * k * k * t * a + 3 * k * t * t * b + t * t * t
        }
        var low = 0.0
        var high = 1.0
        for _ in 0..<18 {
            let time = (low + high) / 2
            if coordinate(time, x1, x2) < progress { low = time } else { high = time }
        }
        return coordinate((low + high) / 2, y1, y2)
    }

    static func spring(_ time: Double, omega: Double, zeta: Double) -> Double {
        guard time > 0 else { return 0 }
        let damped = omega * sqrt(1 - zeta * zeta)
        let envelope = exp(-zeta * omega * time)
        return 1 - envelope * (cos(damped * time) + zeta * omega / damped * sin(damped * time))
    }

    static func springVelocity(_ time: Double, omega: Double, zeta: Double) -> Double {
        guard time > 0 else { return 0 }
        let damped = omega * sqrt(1 - zeta * zeta)
        return omega * omega / damped * exp(-zeta * omega * time) * sin(damped * time)
    }

    static func smoothstep(_ value: Double) -> Double {
        let x = clamp(value)
        return x * x * (3 - 2 * x)
    }

    static func rowProgress(_ u: Double, destination: Int) -> Double {
        let local = (u * `switch` - rowStart - rowStep * Double(destination)) / rowDuration
        guard local > 0 else { return 0 }
        guard local < 1 else { return 1 }
        return spring(local * rowDuration, omega: rowOmega, zeta: rowZeta)
    }

    static func rowSpeed(_ u: Double, destination: Int) -> Double {
        let local = (u * `switch` - rowStart - rowStep * Double(destination)) / rowDuration
        guard local > 0, local < 1 else { return 0 }
        let time = local * rowDuration
        let h = 0.004
        let before = max(0, time - h)
        return (spring(time + h, omega: rowOmega, zeta: rowZeta) - spring(before, omega: rowOmega, zeta: rowZeta)) / (time + h - before)
    }

    static func wordProgress(_ u: Double) -> Double {
        easeOut(segment(u * `switch`, wordStart, wordStart + wordDuration))
    }

    static func podiumProgress(_ u: Double, slot: Int) -> Double {
        let start = podiumStart + podiumStep * Double(slot)
        return easeOut(segment(u * `switch`, start, start + podiumDuration))
    }

    static func pointsProgress(_ u: Double, slot: Int) -> Double {
        let start = pointsStart + pointsStep * Double(slot)
        return easeOut(segment(u * `switch`, start, start + pointsDuration))
    }

    static func towards(from: Int, _ progress: Double) -> Double {
        from == 0 ? progress : 1 - progress
    }

    static func lapPlace(_ value: Double) -> Double {
        if value <= 0 { return 0 }
        if value >= 1 { return 1 }
        return value - sin(2 * .pi * value) / (2 * .pi)
    }

    static func cometAlpha(_ value: Double) -> Double {
        guard value > 0, value < lapEnd else { return 0 }
        return clamp(value / 0.06) * (1 - clamp((value - 1) / (lapEnd - 1)))
    }

    static func flash(_ value: Double) -> Double {
        value < 1 || value >= lapEnd ? 0 : (value - 1) / (lapEnd - 1)
    }

    private static let raceTime = replayFinish - replayGo
    private static let raceVelocity = raceLaps / (raceTime - raceLaunch * (1 - exp(-raceTime / raceLaunch)))
    private static let coolAt = raceTime + raceCoolDelay
    private static let coolDistance = raceVelocity * (coolAt - raceLaunch * (1 - exp(-coolAt / raceLaunch)))
    private static let coolVelocity = raceVelocity * (1 - exp(-coolAt / raceLaunch))

    static func leaderLaps(_ replay: Double) -> Double {
        let time = replay - replayGo
        if time <= 0 { return 0 }
        if time <= coolAt {
            return raceVelocity * (time - raceLaunch * (1 - exp(-time / raceLaunch)))
        }
        return coolDistance + coolVelocity * raceCoolDown * (1 - exp(-(time - coolAt) / raceCoolDown))
    }

    static func leaderPace(_ replay: Double) -> Double {
        let time = replay - replayGo
        if time <= 0 { return 0 }
        if time <= coolAt { return 1 - exp(-time / raceLaunch) }
        return coolVelocity / raceVelocity * exp(-(time - coolAt) / raceCoolDown)
    }

    static func trackRank(_ index: Int, replay: Double) -> Double {
        var rank = Double(gridSlot[index])
        for event in passes {
            let direction = index == event.up ? -1 : index == event.down ? 1 : 0
            if direction != 0 {
                rank += Double(direction) * smoothstep((replay - event.at + racePass) / (2 * racePass))
            }
        }
        return rank
    }

    static func currentRaceGap(_ replay: Double) -> Double {
        lerp(raceGridGap, raceGap, smoothstep(segment(replay, replayGo, replayGo + raceGapSettle)))
    }

    static func carLaps(_ index: Int, replay: Double) -> Double {
        leaderLaps(replay) - currentRaceGap(replay) * trackRank(index, replay: replay)
    }

    static func carLateral(_ index: Int, replay: Double) -> Double {
        let grid = gridSlot[index].isMultiple(of: 2) ? raceGridStagger : -raceGridStagger
        var side = grid * (1 - smoothstep(segment(replay, replayGo, replayGo + raceStaggerOut)))
        for event in passes {
            let wave = sin(.pi * segment(replay, event.at - racePass, event.at + racePass))
            if index == event.up { side += raceLateral * wave }
            else if index == event.down { side -= raceLateral * 0.5 * wave }
        }
        return side
    }

    static func carAlpha(_ replay: Double) -> Double {
        smoothstep(segment(replay, replayDotsIn.0, replayDotsIn.1)) *
            (1 - smoothstep(segment(replay, replayClose, replayClose + replayDotsOut)))
    }

    static let finishAt: [Double] = {
        (0..<6).map { slot in
            var low = replayFinish - 0.01
            var high = replayClose
            for _ in 0..<40 {
                let middle = (low + high) / 2
                if leaderLaps(middle) - raceGap * Double(slot) >= raceLaps { high = middle }
                else { low = middle }
            }
            return high
        }
    }()

    static func tagAlpha(_ index: Int, replay: Double) -> Double {
        var alpha = 0.0
        for event in passes where event.up == index {
            let value = easeOut(segment(replay, event.at - tagShow.0, event.at - tagShow.1)) *
                (1 - easeOut(segment(replay, event.at + tagHide.0, event.at + tagHide.1)))
            alpha = max(alpha, value)
        }
        return alpha
    }

    static func replayRow(_ index: Int, replay: Double) -> ReplayRow {
        var y = Double(index)
        var speed = 0.0
        var bump = 0.0
        var direction = 0
        let events = rewind + passes
        for (eventIndex, event) in events.enumerated() {
            let delta = index == event.up ? -1 : index == event.down ? 1 : 0
            if delta == 0 { continue }
            let time = replay - event.at
            if time <= 0 { continue }
            if eventIndex < rewind.count {
                if time >= rewindDuration { y += Double(delta) }
                else {
                    y += Double(delta) * spring(time, omega: rewindOmega, zeta: rewindZeta)
                    direction = delta
                }
                continue
            }
            if time >= rowDuration {
                y += Double(delta)
                continue
            }
            let progress = spring(time, omega: rowOmega, zeta: rowZeta)
            y += Double(delta) * progress
            speed += Double(delta) * springVelocity(time, omega: rowOmega, zeta: rowZeta)
            bump = sin(.pi * clamp(progress))
            direction = delta
        }
        return ReplayRow(y: y, speed: speed, bump: bump, direction: direction)
    }

    /// Added to `replayPlace` once the last car has taken the flag (all points shown).
    static let replayFlagged = 6

    /// Driver `index`'s running place during the replay, for its accessibility label: the rank of its row's y
    /// (ties at the instant of a crossing broken by index), plus `replayFlagged` after the last car's flag; -1
    /// when no replay runs.
    static func replayPlace(_ index: Int, replay: Double) -> Int {
        guard replay > 0 else { return -1 }
        let yi = replayRow(index, replay: replay).y
        var place = 0
        for other in 0..<6 where other != index {
            let yj = replayRow(other, replay: replay).y
            if yj < yi || (yj == yi && other < index) { place += 1 }
        }
        return replay >= finishAt[5] ? place + replayFlagged : place
    }

    static func mapOpen(_ replay: Double) -> Double {
        if replay <= 0 || replay >= replayEnd { return 0 }
        let out = replay >= mapSettle ? 1 : spring(replay, omega: mapOmega, zeta: mapZeta)
        let backTime = replay - replayClose
        let back = replay <= replayClose ? 0 : backTime >= mapSettle ? 1 : spring(backTime, omega: mapOmega, zeta: mapZeta)
        return out - back
    }

    static func headerDim(_ replay: Double) -> Double {
        if replay <= 0 || replay >= replayEnd { return 0 }
        let start = replayClose + replayDimOutFrom
        return easeOut(segment(replay, 0, replayDimIn)) *
            (1 - easeOut(segment(replay, start, start + replayDimOut)))
    }

    static func lightOn(_ index: Int, replay: Double) -> Double {
        let start = replayLightsOn + lightsStep * Double(index)
        return easeOut(segment(replay, start, start + lightsOn)) *
            (1 - segment(replay, replayGo, replayGo + lightsOff))
    }

    static func lightsHousing(_ replay: Double) -> Double {
        smoothstep(segment(replay, lightsHousingIn.0, lightsHousingIn.1)) *
            (1 - smoothstep(segment(replay, replayGo + lightsHousingOut.0, replayGo + lightsHousingOut.1)))
    }

    static func replayPoints(_ slot: Int, replay: Double) -> ReplayPoints {
        if replay <= 0 { return ReplayPoints(alpha: 1, rise: 0) }
        let start = finishAt[slot]
        if replay < start {
            return ReplayPoints(alpha: 1 - easeOut(segment(replay, pointsHide.0, pointsHide.1)), rise: 0)
        }
        let progress = easeOut(segment(replay, start, start + pointsDuration))
        return ReplayPoints(alpha: progress, rise: 1 - progress)
    }

    static func finishFlash(_ replay: Double) -> Double {
        let progress = segment(replay, replayFinish, replayFinish + finishFlashDuration)
        return progress <= 0 || progress >= 1 ? 0 : progress
    }

    static func frame(at date: Double) -> Frame {
        let time = date.truncatingRemainder(dividingBy: loop)
        let replay = time - replayAt
        return Frame(replay: replay > 0 && replay < replayEnd ? replay : 0)
    }
}
