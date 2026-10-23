import Foundation

/// The measured tracks and particle responses from the original TypeScript demo.
/// Everything is a function of the playhead: replay has no residual velocity.
enum SignInMotion {
    static let stageSize = 331.0
    static let tau = Double.pi * 2
    static let iconBoxSize = 90.384615385
    static let restingScale = 0.182995745
    static let iconFirstStart = 800.0
    static let iconInterval = 1500.0
    static let iconDuration = 1700.0
    static let iconNames = ["photos", "weather", "app-store", "messages"]

    struct RGB {
        let r: Double
        let g: Double
        let b: Double
        func mixed(with other: RGB, progress: Double) -> RGB {
            RGB(r: mix(r, other.r, progress), g: mix(g, other.g, progress), b: mix(b, other.b, progress))
        }
    }

    struct Particle {
        let angle: Double
        let radiusFactor: Double
        let dotRadiusFactor: Double
        let revealDelay: Double
    }

    struct DotFrame {
        let x: Double
        let y: Double
        let radius: Double
        let color: RGB
        let opacity: Double
    }

    struct IconFrame {
        let scale: Double
        let glyphOpacity: Double
        let opacity: Double
    }

    struct ArtworkFrame {
        let name: String
        let x: Double
        let y: Double
        let scale: Double
        let opacity: Double
    }

    struct IdentityFrame {
        let appleOpacity: Double
        let appleScale: Double
        let blueAppleOpacity: Double
        let blackAppleOpacity: Double
        let avatarOpacity: Double
        let avatarScale: Double
    }

    struct Frame {
        let dots: [DotFrame]
        let artwork: [ArtworkFrame]
        let identity: IdentityFrame
    }

    static let particles: [Particle] = (0..<96).map { index in
        let lane = index % 4
        let slot = index / 4
        let radii = [0.304229607, 0.346394763, 0.383635448, 0.437734139]
        let dotRadii = [0.014431017, 0.018650554, 0.017945619, 0.024984894]
        return Particle(
            angle: Double(slot) / 24 * tau + (lane % 2 == 0 ? 0 : tau / 48),
            radiusFactor: radii[lane],
            dotRadiusFactor: dotRadii[lane],
            revealDelay: Double((slot * 5 + lane * 11) % 24) / 24 * 0.42
        )
    }

    // Same nearest-particle selection, including the strict tie-break, as motion.ts.
    static let carrierIndices: [Int] = (0..<4).map { icon in
        let target = -Double.pi / 2 + Double(icon) * Double.pi / 4
        var closest = 3
        var closestDistance = Double.infinity
        for index in stride(from: 3, to: particles.count, by: 4) {
            let delta = particles[index].angle - target
            let distance = abs(atan2(sin(delta), cos(delta)))
            if distance < closestDistance {
                closest = index
                closestDistance = distance
            }
        }
        return closest
    }

    static let introBlue = RGB(r: 0.24, g: 0.53, b: 0.92)
    static let carrierColors = [
        RGB(r: 244 / 255, g: 244 / 255, b: 244 / 255),
        RGB(r: 22 / 255, g: 139 / 255, b: 214 / 255),
        RGB(r: 22 / 255, g: 138 / 255, b: 244 / 255),
        RGB(r: 40 / 255, g: 220 / 255, b: 74 / 255)
    ]
    static let colorStops: [(Double, RGB)] = [
        (0, RGB(r: 0.6481, g: 0.3644, b: 0.8077)),
        (0.12, RGB(r: 0.5204, g: 0.4438, b: 0.9178)),
        (0.25, RGB(r: 0.5047, g: 0.7263, b: 0.9175)),
        (0.38, RGB(r: 0.7629, g: 0.7879, b: 0.7529)),
        (0.5, RGB(r: 0.8747, g: 0.7479, b: 0.6191)),
        (0.62, RGB(r: 0.8898, g: 0.6237, b: 0.5132)),
        (0.75, RGB(r: 0.8345, g: 0.4229, b: 0.4616)),
        (0.87, RGB(r: 0.7759, g: 0.3873, b: 0.6345)),
        (1, RGB(r: 0.6481, g: 0.3644, b: 0.8077))
    ]
    static let colorLUT: [RGB] = (0..<256).map { index in
        let at = Double(index) / 255
        var stop = 0
        while stop < colorStops.count - 2 && at > colorStops[stop + 1].0 { stop += 1 }
        let from = colorStops[stop]
        let to = colorStops[stop + 1]
        return from.1.mixed(with: to.1, progress: (at - from.0) / (to.0 - from.0))
    }

    static func clamp(_ value: Double) -> Double { min(1, max(0, value)) }
    static func mix(_ from: Double, _ to: Double, _ progress: Double) -> Double { from + (to - from) * progress }
    static func smoothstep(_ from: Double, _ to: Double, _ value: Double) -> Double {
        let p = clamp((value - from) / (to - from))
        return p * p * (3 - 2 * p)
    }
    static func positiveModulo(_ value: Double, _ divisor: Double) -> Double {
        (value.truncatingRemainder(dividingBy: divisor) + divisor).truncatingRemainder(dividingBy: divisor)
    }
    static func track(_ value: Double, _ times: [Double], _ values: [Double]) -> Double {
        if value <= times[0] { return values[0] }
        for index in 1..<times.count where value <= times[index] {
            return mix(values[index - 1], values[index], clamp((value - times[index - 1]) / (times[index] - times[index - 1])))
        }
        return values[values.count - 1]
    }

    static func radialScale(_ ms: Double) -> Double {
        track(max(0, ms), [0, 50, 100, 150, 200, 250, 300, 350, 400, 450, 500, 550, 600, 650, 700, 750, 800],
              [0.307917, 0.317723, 0.345109, 0.385948, 0.457981, 0.543629, 0.635995, 0.7246, 0.801949, 0.864439, 0.911718, 0.945457, 0.96833, 0.983003, 0.991954, 0.997012, 1])
    }

    static func orbitRotation(_ ms: Double) -> Double {
        if ms < 800 {
            let degrees = track(max(0, ms),
                [0, 16.667, 33.333, 50, 66.667, 83.333, 100, 116.667, 135, 150, 201.7, 251.7, 301.7, 351.7, 401.7, 503.3, 553.3, 605, 656.7, 705, 753.3, 800],
                [-73.7132, -73.0679, -72.3491, -71.5324, -70.6177, -69.605, -68.4943, -67.3151, -65.8897, -64.6, -57, -47.3, -37.5, -29.5, -23.1, -13.7, -10.3, -7.4, -5, -3, -1.3, 0])
            return (degrees - 0.314) * Double.pi / 180
        }
        return -0.314 * Double.pi / 180 + (ms - 800) / 12000 * tau
    }

    static func introSize(_ progress: Double, _ lane: Int) -> Double {
        track(clamp(progress) * 800, [0, 150, 200, 250, 300, 350, 400, 500, 650, 800],
              lane >= 2 ? [0.4, 0.471, 0.532, 0.606, 0.693, 0.776, 0.843, 0.934, 0.99, 1] : [0.2, 0.227, 0.333, 0.437, 0.548, 0.654, 0.745, 0.881, 0.98, 1])
    }

    static func colorBloom(_ progress: Double) -> Double {
        track(clamp(progress) * 800, [0, 100, 150, 200, 250, 300, 350, 400, 800],
              [0.180758, 0.259296, 0.468568, 0.602909, 0.74193, 0.837831, 0.913511, 0.997463, 1])
    }

    static func entrance(_ progress: Double, _ delay: Double, _ lane: Int) -> Double {
        let contrast = mix(0.7, 1, smoothstep(0, 0.25, clamp(progress)))
        if lane % 2 == 1 { return contrast }
        let start = max(0, delay) * 2 / 3
        return contrast * smoothstep(start, start + 0.04, clamp(progress))
    }

    static func identity(_ ms: Double) -> IdentityFrame {
        let progress = clamp(ms / 1050)
        let grow = smoothstep(0, 0.62, progress)
        let crossfade = smoothstep(0.78, 0.98, progress)
        let black = clamp((progress - 0.08) / 0.72)
        return IdentityFrame(appleOpacity: 1 - crossfade, appleScale: mix(0.42, 1.03, grow),
                             blueAppleOpacity: 1 - black, blackAppleOpacity: black,
                             avatarOpacity: crossfade, avatarScale: mix(0.74, 1, crossfade))
    }

    static func iconFrame(_ ms: Double, _ index: Int) -> IconFrame {
        let first = iconFirstStart + Double(index) * iconInterval
        let hidden = IconFrame(scale: restingScale, glyphOpacity: 0, opacity: 0)
        if ms < first { return hidden }
        let local = positiveModulo(ms - first, iconInterval * 4)
        if local > iconDuration { return hidden }
        let morph = track(local, [0, 36.7, 101.7, 168.3, 233.3, 401.7, 900, 1203.3, 1403.3, 1505, 1538.3, 1591.7, 1690, 1700],
                          [0, 0.114, 0.531, 0.91, 0.995, 0.916, 0.9, 0.812, 0.663, 0.541, 0.487, 0.395, 0.092, 0])
        let scale = mix(restingScale, 1.04, morph)
        return IconFrame(scale: scale, glyphOpacity: smoothstep(restingScale, restingScale + 0.3, scale),
                         opacity: smoothstep(0, 150, local) * (1 - smoothstep(1400, 1700, local)))
    }

    static func carrierResponse(_ particleIndex: Int, _ carrierIndex: Int, _ strength: Double, _ ratio: Double) -> (radial: Double, tangential: Double, scale: Double) {
        let strength = clamp(strength)
        let lane = particleIndex % 4
        let delta = particles[particleIndex].angle - particles[carrierIndex].angle
        let angle = atan2(sin(delta), cos(delta))
        let magnitude = abs(angle)
        let halfStep = Double.pi / 24
        let fullStep = Double.pi / 12
        let direction = angle < 0 ? -1.0 : 1.0
        var radial = 0.0
        var tangential = 0.0
        var scale = 1.0
        if lane == 0 && abs(magnitude - halfStep) <= 0.001 {
            radial = -17.4; tangential = direction * 15.1; scale = 0.735
        } else if lane == 0 && abs(magnitude - halfStep * 3) <= 0.001 {
            radial = -9.6; tangential = direction * 11.2; scale = 0.738
        } else if lane == 1 && magnitude <= 0.001 {
            radial = -81.6; scale = 0.75
        } else if lane == 1 && abs(magnitude - fullStep) <= 0.001 {
            radial = -21.2; tangential = direction * 22.6; scale = 0.75
        } else if lane == 2 && abs(magnitude - halfStep) <= 0.001 {
            radial = 33.2; tangential = direction * 120.9; scale = 0.75
        } else if lane == 3 && abs(magnitude - fullStep) <= 0.001 {
            radial = 43.1; tangential = direction * 45.2; scale = 0.7
        }
        return (radial / 3 * ratio * strength, tangential / 3 * ratio * strength, 1 - (1 - scale) * strength)
    }

    static func frame(at ms: Double, size: Double = stageSize) -> Frame {
        let progress = clamp(ms / 800)
        let rotation = orbitRotation(ms)
        let radial = radialScale(ms)
        let ratio = size / stageSize
        let bloom = colorBloom(progress)
        let icons = (0..<4).map { iconFrame(ms, $0) }
        let dots = particles.enumerated().map { index, particle -> DotFrame in
            let angle = particle.angle + rotation
            let orbit = size * particle.radiusFactor * radial
            var x = cos(angle) * orbit
            var y = sin(angle) * orbit
            var scale = 1.0
            for icon in 0..<4 {
                let strength = clamp((icons[icon].scale - restingScale) / (1.04 - restingScale))
                let response = carrierResponse(index, carrierIndices[icon], strength, ratio)
                x += cos(angle) * response.radial - sin(angle) * response.tangential
                y += sin(angle) * response.radial + cos(angle) * response.tangential
                scale = min(scale, response.scale)
            }
            var radius = size * particle.dotRadiusFactor * introSize(progress, index % 4) * scale
            let lutIndex = min(255, Int(floor(positiveModulo(atan2(y, x), tau) / tau * 255)))
            var color = introBlue.mixed(with: colorLUT[lutIndex], progress: bloom)
            var opacity = entrance(progress, particle.revealDelay, index % 4) * 0.96
            if let icon = carrierIndices.firstIndex(of: index), ms >= iconFirstStart + Double(icon) * iconInterval {
                radius = max(radius, iconBoxSize * ratio * max(0, icons[icon].scale) / 2)
                color = color.mixed(with: carrierColors[icon], progress: icons[icon].glyphOpacity)
                opacity = max(opacity, icons[icon].opacity * 0.96)
            }
            return DotFrame(x: x, y: y, radius: radius, color: color, opacity: opacity)
        }
        let artwork = (0..<4).map { index -> ArtworkFrame in
            let particle = particles[carrierIndices[index]]
            let angle = particle.angle + rotation
            let orbit = size * particle.radiusFactor * radial
            return ArtworkFrame(name: iconNames[index], x: cos(angle) * orbit, y: sin(angle) * orbit,
                                scale: icons[index].scale, opacity: icons[index].glyphOpacity)
        }
        return Frame(dots: dots, artwork: artwork, identity: identity(ms))
    }
}
