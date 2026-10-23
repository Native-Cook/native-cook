import Foundation

struct ChromeZeroNumeralFrame: Codable {
    var yaw = -0.33
    var pitch = -0.1
    var scale = 1.0
    var build = 1.0
    var drain = 0.0
    var fuse = 1.0
    var ripple = 0.0
    var rippleX = 0.0
    var rippleY = 0.0
    var sweep = -1.0
    var percent = 1.0
    var flash = 0.0
    var dim = 0.0
    var drift = 0.0
    var card = 1.0
    var cardOpacity = 1.0
    var cardExit = 0.0
    var sparkles = [1.0, 1, 1]
    var sparkleRotation = [0.0, 0, 0]
    var sparkleOpacity = [1.0, 1, 1]
    var twinkle = [1.0, 1, 1]
    var copy = [1.0, 1, 1, 1, 1]
    var copyY = [0.0, 0, 0, 0, 0]
    var buttonPress = 0.0
    var arrow = 0.0
    var glint = 0.0
}

/// Seconds-based functions shared by autoplay and the real gesture handlers.
enum ChromeZeroNumeralTimeline {
    static let tau = Double.pi * 2
    static let restYaw = -0.33
    static let restPitch = -0.1
    static let duration = 12.0

    static func clamp(_ n: Double) -> Double { max(0, min(1, n)) }
    static func progress(_ t: Double, _ start: Double, _ duration: Double) -> Double {
        clamp((t - start) / duration)
    }
    static func lerp(_ a: Double, _ b: Double, _ p: Double) -> Double { a + (b - a) * p }
    static func cubic(_ p: Double) -> Double { 1 - pow(1 - clamp(p), 3) }

    static func bezier(_ p: Double, _ x1: Double = 0.23, _ y1: Double = 1,
                       _ x2: Double = 0.32, _ y2: Double = 1) -> Double {
        if p <= 1e-9 { return 0 }
        if p >= 1 - 1e-9 { return 1 }
        var lo = 0.0
        var hi = 1.0
        var u = p
        for _ in 0..<15 {
            let inv = 1 - u
            let x = 3 * inv * inv * u * x1 + 3 * inv * u * u * x2 + u * u * u
            if x < p { lo = u } else { hi = u }
            u = (lo + hi) / 2
        }
        let inv = 1 - u
        return 3 * inv * inv * u * y1 + 3 * inv * u * u * y2 + u * u * u
    }

    static func spring(_ t: Double, _ from: Double, _ to: Double, _ stiffness: Double,
                       _ damping: Double, _ velocity: Double = 0) -> Double {
        if t <= 0 { return from }
        let decay = damping / 2
        let frequency = sqrt(max(0.001, stiffness - decay * decay))
        let displacement = from - to
        return to + exp(-decay * t) * (displacement * cos(frequency * t)
            + (velocity + decay * displacement) / frequency * sin(frequency * t))
    }

    static func landed(_ t: Double, _ from: Double, _ to: Double, _ stiffness: Double,
                       _ damping: Double, _ end: Double = 1.2, _ velocity: Double = 0) -> Double {
        if t >= end { return to }
        return lerp(spring(t, from, to, stiffness, damping, velocity), to,
                    cubic(progress(t, end - 0.18, 0.18)))
    }
    static func nearestRest(_ yaw: Double) -> Double {
        restYaw + floor((yaw - restYaw) / tau + 0.5) * tau
    }
    static func decayTravel(_ velocity: Double, _ elapsed: Double) -> Double {
        velocity / 2.6 * (1 - exp(-2.6 * max(0, elapsed)))
    }
    static func coast(_ yaw: Double, _ velocity: Double, _ elapsed: Double) -> Double {
        if elapsed < 0.95 { return yaw + decayTravel(velocity, elapsed) }
        let at = yaw + decayTravel(velocity, 0.95)
        return landed(elapsed - 0.95, at, nearestRest(at), 100, 18, 0.93, velocity * exp(-2.6 * 0.95))
    }
    static func rubberPitch(_ pitch: Double) -> Double {
        let excess = max(0, abs(pitch) - 0.35)
        return (pitch < 0 ? -1 : 1) * (min(abs(pitch), 0.35) + 0.12 * (1 - exp(-excess / 0.12)))
    }
    static func loosen(_ t: Double) -> Double { 1 - 0.78 * (1 - exp(-max(t, 0) / 0.26)) }
    static func glint(_ t: Double) -> Double {
        t < 0 || t >= 0.3 ? 0 : sin(.pi * t / 0.3)
    }
    static func project(_ x: Double, _ y: Double, _ z: Double, _ yaw: Double,
                        _ pitch: Double, _ scale: Double) -> (x: Double, y: Double) {
        let x1 = scale * (x * cos(yaw) + z * sin(yaw))
        let z1 = scale * (-x * sin(yaw) + z * cos(yaw))
        let y2 = y * scale * cos(pitch) - z1 * sin(pitch)
        let z2 = y * scale * sin(pitch) + z1 * cos(pitch)
        let k = 7 / (7 - z2)
        return (214 + 158 * x1 * k, 286 - 158 * y2 * k)
    }
    static func rest(_ t: Double = 0, reduced: Bool = false) -> ChromeZeroNumeralFrame {
        var f = ChromeZeroNumeralFrame()
        f.yaw += reduced ? 0 : 0.17 * sin(tau * t / 6)
        f.drift = reduced ? 0 : sin(tau * t / 12)
        for i in 0..<3 {
            let wave = sin(.pi * t / 2.4 + Double(i) * .pi / 3)
            f.twinkle[i] = reduced ? 1 : 1 - 0.1 * wave * wave
        }
        return f
    }

    static func loop(_ seconds: Double) -> ChromeZeroNumeralFrame {
        let wrapped = (seconds.truncatingRemainder(dividingBy: 12) + 12).truncatingRemainder(dividingBy: 12)
        let t = ((wrapped * 1e9).rounded() / 1e9).truncatingRemainder(dividingBy: 12)
        var f = rest(t)
        if t >= 0.9 && t < 2 {
            f.fuse = 1 - bezier(progress(t, 0.9, 0.26))
            f.drain = progress(t, 1.16, 0.62)
            f.percent = 1 - bezier(progress(t, 0.9, 0.32))
            f.dim = bezier(progress(t, 0.9, 0.4))
            f.yaw = lerp(f.yaw, 0.38, bezier(progress(t, 1.16, 0.62)))
            f.cardExit = sin(.pi * progress(t, 0.9, 0.9))
            for i in 0..<3 {
                f.sparkleOpacity[i] = 1 - progress(t, 0.9, 0.18)
                f.sparkles[i] = 1 - 0.4 * bezier(progress(t, 0.9, 0.18))
            }
            for i in 0..<5 {
                let p = progress(t, 0.9 + Double(4 - i) * 0.03, 0.2)
                f.copy[i] = 1 - p
                f.copyY[i] = 8 * bezier(p)
            }
        } else if t >= 2 && t < 5.54 {
            f.build = progress(t, 2, 1.4)
            f.fuse = 0
            f.percent = 0
            f.dim = 1
            f.yaw = 0.38
            f.scale = lerp(1, 0.955, bezier(progress(t, 3.4, 0.46)))
            f.sweep = lerp(-1, 1, progress(t, 3.4, 0.46))
            f.sparkles = [0.4, 0.4, 0.4]
            f.sparkleRotation = [-.pi / 2, -.pi / 2, -.pi / 2]
            f.sparkleOpacity = [0, 0, 0]
            f.copy = [0, 0, 0, 0, 0]
            f.copyY = [16, 16, 16, 16, 16]
            if t >= 3.86 {
                let p = t - 3.86
                let pour = 1 - min(1, p / 0.72)
                f.fuse = 1 - pour * pour
                f.ripple = p < 0.76 ? cubic(p / 0.76) : 0
                f.flash = 0.55 * (p < 0.09 ? p / 0.09 : 1 - cubic((p - 0.09) / 0.6))
                f.dim = 1 - bezier(p / 0.6)
                f.scale = landed(p, 0.955, 1, 260, 14)
                f.yaw = landed(p, 0.38, restYaw, 60, 9, 1.5)
                    + 0.17 * sin(tau * t / 6) * bezier(p / 1.68)
                f.sweep = p < 0.9 ? lerp(-1, 1, p / 0.9) : -1
                f.percent = p < 0.16 ? 0 : landed(p - 0.16, 0, 1, 170, 15)
                f.glint = max(glint(p), glint(p - 0.3))
                for i in 0..<3 {
                    let at = p - 0.42 - Double(i) * 0.08
                    let pop = at < 0 ? 0 : landed(at, 0, 1, 180, 15, 0.9)
                    f.sparkles[i] = 0.4 + 0.6 * pop
                    f.sparkleRotation[i] = -(1 - pop) * .pi / 2
                    f.sparkleOpacity[i] = clamp(at / 0.16)
                }
                for i in 0..<5 {
                    let enter = bezier(progress(p, 0.36 + Double(i) * 0.06, 0.52))
                    f.copy[i] = enter
                    f.copyY[i] = 16 * (1 - enter)
                }
            }
        } else if t >= 5.94 && t < 8.34 {
            let at = t - 5.94
            let dragged = progress(at, 0.1, 0.42)
            let initial = restYaw + 0.17 * sin(tau * 5.94 / 6)
            f.yaw = at <= 0.52 ? initial + dragged * 150 / 110
                : coast(initial + 150 / 110, 2800 / 110, at - 0.52)
            if at > 2.1 { f.yaw += 0.17 * sin(tau * t / 6) * bezier(progress(at, 2.1, 0.3)) }
        } else if t >= 8.64 && t < 10.04 {
            let at = ((t - 8.64) * 1e9).rounded() / 1e9
            if at < 0.7 {
                f.fuse = loosen(at)
                f.scale = lerp(1, 0.97, bezier(at / 0.22))
            } else {
                let release = at - 0.7
                f.fuse = min(1, landed(release, loosen(0.7), 1, 220, 18, 0.6))
                f.scale = landed(release, 0.97, 1, 260, 14, 0.6)
                f.ripple = cubic(release / 0.7)
                let releaseYaw = rest(9.34).yaw
                let origin = project(-0.42, -0.2, 0.26, releaseYaw, restPitch, 0.97)
                f.rippleX = max(-0.64, min(0.64, (origin.x - 214) / (158 * max(cos(releaseYaw), 0.3))))
                f.rippleY = max(-1, min(1, -(origin.y - 286) / 158))
                f.flash = 0.35 * (1 - cubic(release / 0.6))
                f.glint = glint(release)
            }
        } else if t >= 10.24 && t < 11.64 {
            let at = t - 10.24
            f.buttonPress = at < 0.16 ? bezier(at / 0.12) : landed(at - 0.16, 1, 0, 240, 20, 0.65)
            if at >= 0.16 {
                let p = progress(at, 0.16, 1.1)
                f.yaw += tau * bezier(p, 0.77, 0, 0.175, 1)
                f.sweep = p < 1 ? lerp(-1, 1, p) : -1
                f.arrow = 4 * sin(.pi * progress(at, 0.16, 0.5))
                f.glint = glint(at - 0.53)
                for i in 0..<3 { f.twinkle[i] += 0.12 * sin(.pi * p) }
            }
        }
        return f
    }

    static func entrance(_ playhead: Double) -> ChromeZeroNumeralFrame {
        if playhead >= 2 { return loop(playhead) }
        var f = loop(2)
        let at = ((playhead - 1.1) * 1e9).rounded() / 1e9
        f.card = at <= 0 ? 0 : landed(at, 0, 1, 140, 17, 0.9)
        f.cardOpacity = at < 0 ? 0 : 1
        return f
    }
}
