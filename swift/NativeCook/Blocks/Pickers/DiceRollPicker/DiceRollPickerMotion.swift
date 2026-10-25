import Foundation

/*
 Dice Roll Picker — the autoPlay timeline (loop = 8 s), a port of diceMotion.ts:
   0.0 – 1.2   rest: the dice show 3 and 4 (sum 7), the line reads "Flick the dice to pick who starts".
   1.2 – 1.6   pick up: both dice lift 34 pt and come toward the player, tipping back 10° (ease-out).
   1.6 – 3.8   the throw (die B 0.05 s behind): first landing at +0.55 s, a bounce off the far rim at
               +0.85 s, hops of 96, 34 and 10 pt, two and three whole turns that ease out by +1.85 s,
               then a rock on the face (+1.85 – 2.2 s). They land on 5 and 6.
   3.75 – 4.05 the line rolls to "11 · Priya starts"; Priya's avatar pops (spring, ~20 % overshoot).
   3.8 – 5.6   hold.
   5.6 – 7.2   the dice slide home, tipping over as they turn back to 3 and 4 (ease-in-out, 8 pt hop);
               the line and the avatar settle back (5.6 – 6.0).
   7.2 – 8.0   rest.
 */
enum DiceRollPickerMotion {
    private typealias D = DiceRollPickerMath

    static let loop = 8.0
    static let rimY = D.tableY1 - D.half - 6

    struct Spot {
        let face: Int
        let yaw: Double
        let x: Double
        let y: Double
    }

    static let rest: [Spot] = [Spot(face: 3, yaw: 0.34, x: -50, y: -6), Spot(face: 4, yaw: -0.24, x: 50, y: 8)]
    static let pick: [(Double, Double)] = [(-36, -76), (36, -70)]
    static let final: [Spot] = [Spot(face: 5, yaw: -0.46, x: -38, y: 44), Spot(face: 6, yaw: 0.38, x: 42, y: 26)]
    static let turns: [Double] = [2, 3]
    static let delay: [Double] = [0, 0.05]

    static let restM: [DiceRollPickerMat] = rest.map { D.faceUp($0.face, $0.yaw) }
    static let finalM: [DiceRollPickerMat] = final.map { D.faceUp($0.face, $0.yaw) }
    static let flights: [DiceRollPickerThrow] = (0..<2).map { i in
        D.makeThrow(restM[i], finalM[i], pick[i], (final[i].x, final[i].y), h0: 34, k: turns[i], rimY: rimY)
    }

    static let autoSum = final[0].face + final[1].face

    static func restPose(_ i: Int) -> DiceRollPickerPose {
        DiceRollPickerPose(m: restM[i], x: rest[i].x, y: rest[i].y, hop: 0)
    }

    /// A die's pose at loop time t.
    static func autoPose(_ i: Int, _ t: Double) -> DiceRollPickerPose {
        if t < 1.2 || t >= 7.2 { return restPose(i) }
        if t < 1.6 {
            let u = D.seg(t, 1.2, 1.6)
            let e = D.easeOut(u)
            let tip = D.axisAngle(DiceRollPickerV3(1, 0, 0), -0.18 * sin(.pi * u))
            return DiceRollPickerPose(
                m: tip * restM[i],
                x: D.mix(rest[i].x, pick[i].0, D.easeInOut(u)),
                y: D.mix(rest[i].y, pick[i].1, D.easeInOut(u)),
                hop: 34 * e
            )
        }
        let s = t - 1.6 - delay[i]
        if t < 5.6 {
            if s <= 0 { return DiceRollPickerPose(m: restM[i], x: pick[i].0, y: pick[i].1, hop: 34) }
            return D.throwPose(flights[i], min(s, D.throwEnd))
        }
        return D.slidePose(finalM[i], restM[i], (final[i].x, final[i].y), (rest[i].x, rest[i].y), D.seg(t, 5.6, 7.2))
    }

    /// A spring-like pop from 0 to 1, ~20 % overshoot, after time x ≥ 0.
    static func springIn(_ x: Double) -> Double {
        if x <= 0 { return 0 }
        return 1 - exp(-6 * x) * cos(12 * x)
    }

    /// (result line roll 0 … 1, the chosen avatar's pop).
    static func autoResult(_ t: Double) -> (Double, Double) {
        let out = D.easeInOut(D.seg(t, 5.6, 6.0))
        let k = D.easeInOut(D.seg(t, 3.75, 4.05)) * (1 - out)
        return (k, springIn(t - 3.85) * (1 - out))
    }
}
