import SwiftUI

struct DayOneMedalConfetti: View {
    struct DayOneMedalFlake {
        var x: Double
        var lead: Double
        var speed: Double
        var drift: Double
        var driftRate: Double
        var spin: Double
        var spinRate: Double
        var tumble: Double
        var tumbleRate: Double
        var width: Double
        var height: Double
        var colour: Int
        var depth: Double
    }
    let near: Bool
    let scale: Double
    let time: Double
    private static let colours: [UInt32] = [0xEBC381, 0xDDA85A, 0xF6DCA3, 0xCF9647, 0xF2CD8C, 0x3B3A40]
    private static let flakes: [DayOneMedalFlake] = {
        var random = DayOneMedalRandom()
        return (0..<110).map { _ in
            let depth = random.next()
            let x = random.next()
            let lead = random.next()
            let speed = 62 + 88 * random.next()
            let drift = 6 + 14 * random.next()
            let driftRate = 0.6 + 1.3 * random.next()
            let spin = random.next() * .pi * 2
            let sign = random.next() < 0.5 ? -1.0 : 1.0
            let spinRate = sign * (1.2 + 3.4 * random.next())
            let tumble = random.next() * .pi * 2
            let tumbleRate = 2.2 + 4.6 * random.next()
            let width = 6 + 4 * random.next()
            let height = 3.6 + 2.2 * random.next()
            let colour = random.next() < 0.12 ? 5 : Int(floor(random.next() * 5))
            return DayOneMedalFlake(x: x, lead: lead, speed: speed, drift: drift, driftRate: driftRate,
                spin: spin, spinRate: spinRate, tumble: tumble, tumbleRate: tumbleRate,
                width: width, height: height, colour: colour, depth: depth)
        }
    }()

    var body: some View {
        Canvas { context, size in
            let t = time
            let fall = size.height + 60
            for f in Self.flakes where (f.depth > 0.58) == near {
                let scale = (0.62 + 0.62 * f.depth) * scale
                let travelled = f.speed * (0.7 + 0.5 * f.depth) * t - f.lead * fall * 0.9
                guard travelled >= 0 else { continue }
                let y = travelled.truncatingRemainder(dividingBy: fall) - 30
                let x = f.x * size.width + f.drift * sin(f.driftRate * t + f.spin)
                let flip = cos(f.tumble + f.tumbleRate * t)
                let flash = f.colour < 5 && pow(abs(flip), 28) > 0.5
                let alpha = flash ? 1 : (near ? 0.95 : 0.6) * (0.55 + 0.45 * abs(flip))
                var ink = context
                ink.opacity = y > 480 * self.scale ? min(0.35, alpha * 0.25) : alpha
                ink.translateBy(x: x, y: y)
                ink.rotate(by: .radians(f.spin + f.spinRate * t))
                ink.scaleBy(x: scale, y: scale * (flip < 0 ? -1 : 1) * max(abs(flip), 0.28))
                let path = Path(roundedRect: CGRect(x: -f.width / 2, y: -f.height / 2, width: f.width, height: f.height), cornerRadius: f.height / 2)
                ink.fill(path, with: .color(DayOneMedalColour.hex(flash ? 0xFFF6DC : Self.colours[f.colour])))
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
