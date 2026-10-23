import SwiftUI

struct PrismSphereGeneratingMote {
    let x: Double
    let y: Double
    let z: Double
    let radius: Double
    let alpha: Double
    let colour: Int
    let phase: Double
    let cycles: Double
    let rise: Double
    let sway: Double
    let twinkle: Double
    let thrown: Double
    let bokeh: Bool

    static let all: [Self] = {
        var seed: UInt32 = 62417
        func random() -> Double {
            seed = seed &* 1664525 &+ 1013904223
            return Double(seed) / 4294967296
        }
        return (0..<64).map { i in
            let angle = random() * .pi * 2
            let distance = 124 * (1.08 + 1.52 * random())
            let back = i < 38
            let bokeh = i >= 58
            let z = (back ? -1.0 : 1.0) * (0.05 + 0.95 * random())
            let radius = bokeh ? 4 + 3 * random() : back ? 0.7 + 0.9 * random() : 1 + 1.2 * random()
            let alpha = bokeh ? 0.09 + 0.07 * random() : 0.22 + 0.42 * random()
            let colour = bokeh ? 2 : random() < 0.1 ? 3 : Int(floor(random() * 3))
            return Self(x: cos(angle) * distance * 0.9, y: sin(angle) * distance * 1.25,
                        z: z, radius: radius, alpha: alpha, colour: colour, phase: random(),
                        cycles: random() < 0.5 ? 1 : 2, rise: 40 + 70 * random(),
                        sway: 4 + 4 * random(), twinkle: random(), thrown: 24 + 40 * random(), bokeh: bokeh)
        }
    }()

    private func boxDistance(_ x: Double, _ y: Double, cy: Double, hw: Double, hh: Double) -> Double {
        hypot(max(0, abs(x) - hw), max(0, abs(y - cy) - hh))
    }

    func point(_ frame: PrismSphereGeneratingFrame) -> (x: Double, y: Double, alpha: Double) {
        typealias Curves = PrismSphereGeneratingTimeline
        let clock = frame.reduced ? 0 : frame.clock
        let u = Curves.modulo(cycles * clock / 10 + phase, 1)
        let px = x + sway * sin(2 * .pi * (cycles * clock / 10 + phase))
        let py = y - rise * (u - 0.5)
        let distance = hypot(px, py)
        let b = frame.burstAge
        let impulse = b >= 0 ? thrown * frame.gain * (1 - exp(-b / 0.12)) * exp(-b / 0.9) : 0
        let radial = -0.16 * (distance - 124) * frame.values.charge + impulse
        let ratio = 1 + radial / max(distance, 1)
        let twinkle = 0.75 + 0.25 * sin(2 * .pi * (clock / 10 + self.twinkle))
        var opacity = alpha * pow(sin(.pi * u), 1.5) * twinkle * (1 + 1.5 * frame.values.flash)
        if z < 0 { opacity *= Curves.smooth(124 * frame.scale, 124 * frame.scale + 6, distance * ratio) }
        let finalX = px * ratio
        let finalY = py * ratio
        let reach = radius * (bokeh ? 2.5 : 1)
        opacity *= 0.3 + 0.7 * exp(-(finalX * finalX) / (150 * 150))
        opacity *= Curves.smooth(0, 14, boxDistance(finalX, finalY, cy: -232, hw: 150, hh: 44) - reach)
        opacity *= Curves.smooth(0, 14, boxDistance(finalX, finalY, cy: 200, hw: 96, hh: 12) - reach)
        return (finalX, finalY, min(bokeh ? 0.16 : 1, opacity))
    }

    static func draw(_ context: GraphicsContext, frame: PrismSphereGeneratingFrame,
                     centre: CGPoint, k: Double, back: Bool) {
        let colours = [Color(red: 237 / 255, green: 228 / 255, blue: 1),
                       Color(red: 196 / 255, green: 176 / 255, blue: 1),
                       Color(red: 159 / 255, green: 123 / 255, blue: 1),
                       Color(red: 240 / 255, green: 171 / 255, blue: 252 / 255)]
        for mote in all where (mote.z < 0) == back {
            let point = mote.point(frame)
            let x = centre.x + point.x * k
            let y = centre.y + point.y * k
            let radius = mote.radius * k
            var halo = context
            halo.blendMode = .plusLighter
            halo.opacity = point.alpha * 0.25
            halo.addFilter(.blur(radius: 1.1 * k))
            halo.fill(Path(ellipseIn: CGRect(x: x - radius * 3, y: y - radius * 3,
                                            width: radius * 6, height: radius * 6)), with: .color(colours[mote.colour]))
            var core = context
            core.blendMode = .plusLighter
            core.opacity = point.alpha
            if mote.bokeh { core.addFilter(.blur(radius: 3 * k)) }
            core.fill(Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2)),
                      with: .color(colours[mote.colour]))
        }
    }
}
