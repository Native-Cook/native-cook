#if DEBUG
import Foundation

/// Opt-in numeric self-test; normal app launches do not serialize or print motion data.
enum RaffleDrumSpinDebug {
    private static var hasDumped = false

    static func selfTestIfRequested() {
        guard ProcessInfo.processInfo.arguments.contains("-raffleMotionDump"), !hasDumped else { return }
        hasDumped = true
        do { print(String(decoding: try dumpData(), as: UTF8.self)) } catch { assertionFailure("Motion dump failed: \(error)") }
    }

    static func dumpData() throws -> Data {
        var frames: [[String: Any]] = []
        for step in 0...20 {
            frames.append(frameObject(RaffleDrumSpinMotion.frameAt(Double(step) * 0.5)))
        }
        let baked: [[String: Any]] = RaffleDrumSpinMotion.baked.map {
            ["t0": $0.t0, "count": $0.count, "data": $0.data]
        }
        return try JSONSerialization.data(withJSONObject: ["frames": frames, "baked": baked, "driver": driverSession()], options: [.sortedKeys])
    }

    /// Same 1,200 inputs as tools-swift/driver-session.ts; capture every step, including holds.
    static func driverSession() -> [[String: Any]] {
        typealias M = RaffleDrumSpinMotion
        var d = M.makeDriver()
        var states: [[String: Any]] = []
        for i in 0..<1200 {
            let now = Double(i) / 60
            if i == 15 { M.pressIn(&d, 1); _ = M.selectCard(&d, 1, now); M.pressOut(&d) }
            if i == 50 { M.grabStrip(&d, now); d.strip = M.rubberBand(d.strip - 92); M.releaseStrip(&d, -520, now) }
            if i == 90 { M.pressIn(&d, 3, 184); _ = M.pressPlay(&d, now); M.pressOut(&d) }
            if i == 120 { M.touched(&d, now); d.spin += Double.pi * 2; d.spinV = 7.5 }
            if i == 480 { M.grabTicket(&d, now); M.tiltTicket(&d, 70, -35, now); M.releaseTicket(&d, -120, 80, now) }
            if i == 600 { M.pressIn(&d, 3, 184); _ = M.pressPlay(&d, now); M.pressOut(&d) }
            M.stepDriver(&d, 1 / 60, now, true)
            var object: [String: Any] = [:]
            for property in Mirror(reflecting: d).children {
                guard let key = property.label else { continue }
                if let value = property.value as? Double, !value.isFinite { object[key] = NSNull() }
                else { object[key] = property.value }
            }
            states.append(object)
        }
        return states
    }

    private static func ballObject(_ ball: RaffleDrumSpinMotion.Ball) -> [String: Any] {
        [
            "x": ball.x, "y": ball.y, "r": ball.r, "z": ball.z,
            "rx": ball.rx, "ry": ball.ry, "rz": ball.rz,
            "a": ball.a, "glow": ball.glow, "sq": ball.sq,
        ]
    }
    
    private static func frameObject(_ frame: RaffleDrumSpinMotion.Frame) -> [String: Any] {
        var object: [String: Any] = [
            "t": frame.t, "theta": frame.theta, "omega": frame.omega, "speed": frame.speed,
            "from": frame.from, "to": frame.to, "selK": frame.selK, "sweep": frame.sweep,
            "check": frame.check, "strip": frame.strip, "iconA": frame.iconA,
            "balls": frame.balls.map(ballObject), "press": frame.press, "cardPress": frame.cardPress,
            "shimmer": frame.shimmer, "dim": frame.dim, "focus": frame.focus,
            "stitches": frame.stitches, "stitchT": frame.stitchT, "morph": frame.morph,
            "ticketA": frame.ticketA, "number": frame.number, "foil": frame.foil,
            "claimK": frame.claimK, "claimGlint": frame.claimGlint, "claimPress": frame.claimPress,
            "tear": frame.tear, "burn": frame.burn, "absorb": frame.absorb, "land": frame.land,
            "tilt": frame.tilt, "lit": frame.lit, "streak": frame.streak, "streakAt": frame.streakAt,
            "rays": frame.rays, "ring": frame.ring, "ringR": frame.ringR, "aurora": frame.aurora,
            "lens": frame.lens, "fill": frame.fill, "drain": frame.drain, "drainAt": frame.drainAt,
            "idleT": frame.idleT, "entered": frame.entered, "burst": frame.burst,
            "glow": frame.glow, "vortex": frame.vortex,
        ]
        object["hero"] = frame.hero.map(ballObject) ?? NSNull()
        return object
    }
    
}
#endif
