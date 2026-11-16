import Foundation

/// One render record, including the lagged channels. Packed arrays follow the source shader declarations.
struct SparkSphereScoreScene {
    private typealias M = SparkSphereScoreMotion
    var v = 0.8, rim = 0.8, hollow = 0.8, film = 0.8, card = 0.8, score = 0.8
    var cyc = 0.0, grab = 0.0, swirl = 0.0, agit = 0.0
    var probeX = 0.0, probeY = 0.0, probeHeat = 0.0, dimple = 0.0, dimpleAngle = 0.0
    var pulseAge = 99.0, tapAge = 99.0
    var bandAlpha = [0.0, 1, 0, 0]

    static func script(_ t: Double) -> Self {
        var f = Self()
        f.v = M.scriptV(t)
        f.rim = M.scriptV(t - M.lag[0]); f.hollow = M.scriptV(t - M.lag[1])
        f.film = M.scriptV(t - M.lag[2]); f.card = M.scriptV(t - M.lag[3]); f.score = M.scriptV(t - M.lag[4])
        f.cyc = t / M.loop; f.grab = M.scriptGrab(t)
        f.swirl = M.lut(M.script.swirl, t); f.agit = M.lut(M.script.agit, t)
        f.pulseAge = M.pulseAge(t); f.tapAge = t >= 4.05 ? t - 4.05 : 99
        f.bandAlpha = (0..<4).map { $0 == M.band(f.card) ? 1 : 0 }
        return f
    }

    func cursorX(_ g: SparkSphereScoreLayout) -> Double { g.rulerX + (v + 1) / 0.05 * g.pitch }
    func discUniforms(_ g: SparkSphereScoreLayout, _ px: Double, _ reduce: Bool) -> [Float] {
        var heat = M.heat(rim), cold = M.cold(rim), fill = M.fill(hollow)
        if reduce {
            cold = 1 - M.smooth(-0.08, 0.12, hollow)
            fill = 0.45 * M.smooth(0.88, 1.12, hollow) + 0.4 * M.smooth(1.88, 2.12, hollow)
            heat = 0
        }
        let cycle = reduce ? 0 : cyc, c0 = M.colour(rim), c1 = M.colour(hollow), c2 = M.colour(film)
        let age = reduce ? 99 : pulseAge, u = age / 0.42
        let bandQ = 1.05 - 1.05 * M.clamp(u, 0, 1), bandA = u < 0 || u > 1 ? 0 : 0.75 * sin(.pi * u)
        let ringA = reduce || age < 0 ? 0 : 4 / 163.6 * exp(-age / 0.3)
        let mem = M.membrane(cycle, reduce ? 0 : heat, cold, ringA, 2 * .pi * age / 0.32)
        let beatPhase = (cycle * 8).truncatingRemainder(dividingBy: 1)
        let breath = 1 + (reduce ? 0 : 1.2 / 163.6) * pow(sin(.pi * beatPhase), 6)
        let tapOn = tapAge < 1.2 && cold > 0.5
        var out = [g.discCx, g.discCy, g.discScale, px] // uGeo
        out += mem // uMemA, uMemB
        out += [dimple, dimpleAngle, 0.55, breath]
        out += [heat, cold, fill, reduce ? 0 : swirl]
        out += [reduce ? 0 : agit, reduce ? 0 : 0.016 * (1 - cold),
                reduce ? 0 : 0.04 * M.smooth(0.1, 0.8, heat) * (1 - cold), reduce ? 0 : 3 + 1.6 * heat]
        out += [1 + 0.3 * heat, (reduce ? 0 : 0.45) * (1 - heat) * (1 - 0.5 * cold),
                reduce ? 0 : -2.62 + 2 * .pi * cycle, reduce ? 0 : 0.55 * heat]
        out += [cycle, reduce ? 0 : 2 * .pi * 7 * cycle, 0.9 * cold, max(0.65 * heat, 0.88 * cold)]
        out += c0; out += c1
        out += [max(c1[0] * 0.375 - 0.063, 0), c1[1] * 0.375, c1[2] * 0.4]
        out += c2.map { $0 * 0.78 }; out += [1]
        out += [1.3 - 0.3 * cold, 0.6 * (1 - cold), 14, 0.07]
        out += [0.8, 0.3 + 0.2 * cold, 0.86, 1]
        out += [0.008, 0.016, 0.02]
        out += [probeX, probeY, 70, probeHeat]
        out += [bandQ, bandA, 30 / 163.6, tapOn ? tapAge * 300 / 163.6 : 2]
        out += [tapOn ? 0.9 * exp(-tapAge / 0.9) : 0, 0.06, 0.3, 0.56]
        return out.map(Float.init)
    }
    func rulerUniforms(_ g: SparkSphereScoreLayout) -> [Float] {
        let rr = pulseAge < 0.5 ? pulseAge * 600 : -1
        let rip = pulseAge < 0.5 ? 4 * exp(-pulseAge / 0.2) : 0
        let c = M.colour(film), tint = M.smooth(0.08, 0.45, abs(film - 0.8))
        var out = [g.rulerX, g.pitch, g.rulerCy, 24 * g.sc]
        out += [1.5 * g.s, 2.4 * g.s, 32.3 * g.sc, cursorX(g)]
        out += [1, grab, rr, rip]
        out += [M.mix(0.93, c[0], tint), M.mix(0.95, c[1], tint), M.mix(0.96, c[2], tint), 1]
        out += [50.0 / 255, 55.0 / 255, 58.0 / 255, 0]
        out += [70.0 / 255, 75.0 / 255, 79.0 / 255, 1]
        return out.map(Float.init)
    }
}
