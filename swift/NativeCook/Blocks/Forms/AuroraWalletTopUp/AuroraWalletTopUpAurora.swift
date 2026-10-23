import SwiftUI

/// The clock of a top-up, in seconds since Continue was released. `duration` is the wait: the
/// time from Continue to the moment the light gathers and leaves.
enum AuroraWalletTopUpMotion {
    /// autoPlay: one top-up every `loop` seconds, Continue pressed at `pressAt` for `pressFor`.
    static let loop = 8.5
    static let pressAt = 0.8
    static let pressFor = 0.15
    /// After a touch autoPlay waits this long, and for the form to be idle, before it starts over.
    static let userHold = 3.0

    static let blurIn = 0.3
    static let sweep = 0.9
    /// The result replaces the spinner this long after the sweep starts, and holds.
    static let resultAfter = 0.3
    static let resultIn = 0.25
    static let resultHold = 1.8
    static let statusOut = 0.3
    static let formBackAfter = 0.1
    static let formBackIn = 0.4
    static let countAfter = 0.15
    static let countFor = 0.7
    /// The form takes touches again this long after it started to come back.
    static let settle = 0.9

    static func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

    static func smooth(_ t: Double, _ from: Double, _ to: Double) -> Double {
        let u = clamp01((t - from) / (to - from))
        return u * u * (3 - 2 * u)
    }

    static func easeOut(_ t: Double) -> Double {
        let u = 1 - clamp01(t)
        return 1 - u * u * u
    }

    /// When the result starts to leave and the form to come back.
    static func returnAt(_ duration: Double) -> Double { duration + resultAfter + resultHold }
    static func end(_ duration: Double) -> Double { returnAt(duration) + settle }
}

/// One soft ellipse of light. `x` and `rx` are fractions of the screen's width, `y` and `ry` of its
/// height, so the picture is the same on every phone.
private struct Light {
    var x: Double
    var y: Double
    var rx: Double
    var ry: Double
    var alpha: Double
    var red: Double
    var green: Double
    var blue: Double

    init(_ x: Double, _ y: Double, _ rx: Double, _ ry: Double, _ alpha: Double, _ hex: UInt32) {
        self.x = x
        self.y = y
        self.rx = rx
        self.ry = ry
        self.alpha = alpha
        red = Double((hex >> 16) & 0xff) / 255
        green = Double((hex >> 8) & 0xff) / 255
        blue = Double(hex & 0xff) / 255
    }

    func mixed(_ other: Light, _ t: Double) -> Light {
        var out = self
        out.x += (other.x - x) * t
        out.y += (other.y - y) * t
        out.rx += (other.rx - rx) * t
        out.ry += (other.ry - ry) * t
        out.alpha += (other.alpha - alpha) * t
        out.red += (other.red - red) * t
        out.green += (other.green - green) * t
        out.blue += (other.blue - blue) * t
        return out
    }
}

/// A light through the wait and the sweep: where it rests, at the hottest moment, as it pales
/// before the end, gathered round the rising centre (x and y are offsets from that centre), and
/// as the last trace along the top edge. `wander` is its own slow sine: reach in x and y,
/// speed in radians a second, phase, and how much its size breathes.
private struct Source {
    let rest: Light
    let hot: Light
    let pale: Light
    let gathered: Light
    let trace: Light
    let wander: (x: Double, y: Double, speed: Double, phase: Double, pulse: Double)
}

private let sources = [
    // Deep violet in the lower left corner.
    Source(
        rest: Light(0.14, 0.888, 0.40, 0.200, 1.00, 0x2a06b4), hot: Light(0.16, 0.890, 0.50, 0.200, 1.00, 0x2204b6),
        pale: Light(0.20, 0.890, 0.48, 0.195, 1.00, 0x4a0cd8), gathered: Light(-0.44, 0.100, 0.54, 0.340, 1.20, 0x5c14dc),
        trace: Light(0.10, -0.040, 0.42, 0.280, 0.90, 0x8f8af0), wander: (0.03, 0.01, 2.1, 0.0, 0.05)
    ),
    // Magenta through the middle, under the bar.
    Source(
        rest: Light(0.38, 0.965, 0.56, 0.265, 1.00, 0xe060c5), hot: Light(0.39, 0.975, 0.57, 0.245, 1.00, 0xea8ad8),
        pale: Light(0.30, 0.990, 0.42, 0.250, 1.00, 0xffd6ff), gathered: Light(-0.15, 0.180, 0.60, 0.200, 0.85, 0xa050e0),
        trace: Light(0.40, -0.040, 0.50, 0.260, 0.60, 0xb9a8e0), wander: (0.035, 0.008, 1.7, 1.7, 0.04)
    ),
    // Peach across the right, turning orange in the middle of the wait.
    Source(
        rest: Light(0.70, 0.900, 0.84, 0.255, 0.85, 0xffa890), hot: Light(0.70, 0.912, 0.85, 0.266, 0.85, 0xf59b34),
        pale: Light(0.72, 0.891, 0.90, 0.230, 1.00, 0xfdc88c), gathered: Light(0.46, 0.000, 0.50, 0.320, 1.00, 0xffd49a),
        trace: Light(0.88, -0.040, 0.42, 0.280, 0.90, 0xd2f6d0), wander: (0.03, 0.01, 1.9, 3.1, 0.05)
    ),
    // The crest of the hill, which breathes the most.
    Source(
        rest: Light(0.62, 0.795, 0.42, 0.125, 0.40, 0xf39a48), hot: Light(0.56, 0.860, 0.62, 0.205, 0.53, 0xff4a2a),
        pale: Light(0.57, 0.868, 0.51, 0.230, 0.70, 0xffb2b1), gathered: Light(0.08, -0.140, 0.62, 0.220, 0.70, 0xffd8cc),
        trace: Light(0.62, -0.040, 0.50, 0.240, 0.50, 0xdfe6e0), wander: (0.05, 0.012, 2.6, 4.4, 0.12)
    ),
    // Ivory where the light meets the bar; it becomes the core of the blob that rises.
    Source(
        rest: Light(0.85, 0.940, 0.50, 0.220, 0.70, 0xfff6e2), hot: Light(0.82, 0.950, 0.38, 0.160, 0.50, 0xffd0f0),
        pale: Light(0.58, 0.950, 0.70, 0.280, 1.00, 0xffc8e6), gathered: Light(0.00, -0.020, 0.76, 0.380, 1.30, 0xfff4f8),
        trace: Light(0.36, -0.040, 0.40, 0.200, 0.70, 0xd8e6fa), wander: (0.025, 0.006, 1.5, 2.3, 0.04)
    ),
]

/// How a light fades from its centre to its edge: cos², so it has no rim and no bright point.
/// A light stronger than 1 is solid in the middle and fades from further out.
private let falloff: [(at: Double, alpha: Double)] = [(0, 1), (0.2, 0.905), (0.4, 0.655), (0.6, 0.345), (0.8, 0.095), (1, 0)]

/// The gathered light starts at this height and leaves towards the top edge.
private let riseFrom = 0.90
private let riseTo = 0.10
private let barBlur = 7.0

private typealias M = AuroraWalletTopUpMotion

/// The light of a top-up: five soft ellipses blended as screen so that where they overlap they
/// glow, and the Continue button as a blurred bar of light in front of them.
struct AuroraWalletTopUpAurora: View {
    /// Seconds since Continue was released.
    let elapsed: Double
    /// Seconds from Continue to the sweep.
    let duration: Double
    /// Reduced motion: a still glow that fades in and out.
    let still: Bool
    /// The Continue button's frame.
    let bar: CGRect

    var body: some View {
        Canvas { context, size in
            let e = elapsed
            let grow = M.smooth(e, 0.10, 0.75)
            let heat = still ? 0 : M.smooth(e, 0.34 * duration, 0.66 * duration) * (1 - M.smooth(e, 0.74 * duration, 0.92 * duration))
            let pale = still ? 0 : M.smooth(e, 0.78 * duration, duration)
            let s = M.clamp01((e - duration) / M.sweep)
            let gather = still ? 0 : M.smooth(s, 0, 0.25)
            let centre = riseFrom + (riseTo - riseFrom) * M.smooth(s, 0.05, 0.50)
            let settle = still ? 0 : M.smooth(s, 0.40, 0.62)
            let fade = 1 - (still ? M.smooth(s, 0, 0.35) : M.smooth(s, 0.60, 1))
            let shown = M.smooth(grow, 0, 0.6)

            var glow = context
            glow.blendMode = .screen
            for source in sources {
                var light = source.rest.mixed(source.hot, heat).mixed(source.pale, pale)
                if !still {
                    let w = source.wander
                    light.x += w.x * sin(e * w.speed + w.phase)
                    light.y += w.y * cos(e * w.speed * 1.3 + w.phase)
                    let breath = 1 + w.pulse * sin(e * w.speed * 1.7 + w.phase * 2)
                    light.rx *= breath
                    light.ry *= breath
                    // It grows out of the bar: lower, narrower and flatter at first.
                    light.x = 0.5 + (light.x - 0.5) * (0.7 + 0.3 * grow)
                    light.y += 0.07 * (1 - grow)
                    light.rx *= 0.6 + 0.4 * grow
                    light.ry *= 0.45 + 0.55 * grow
                }
                light.alpha *= shown
                var gathered = source.gathered
                gathered.x += 0.5
                gathered.y += centre
                light = light.mixed(gathered, gather).mixed(source.trace, settle)
                light.alpha *= fade

                let colour = Color(.sRGB, red: light.red, green: light.green, blue: light.blue, opacity: 1)
                glow.fillEllipse(
                    center: CGPoint(x: light.x * size.width, y: light.y * size.height),
                    radii: CGSize(width: light.rx * size.width, height: light.ry * size.height),
                    gradient: Gradient(stops: falloff.map { .init(color: colour.opacity(min(1, light.alpha * $0.alpha)), location: $0.at) })
                )
            }

            let barAlpha = (0.6 - 0.2 * grow) * M.smooth(e, 0, 0.18) * (1 - gather) * (still ? fade : 1)
            if barAlpha > 0.001 {
                var soft = context
                soft.addFilter(.blur(radius: barBlur))
                soft.fill(capsule(bar), with: .color(.white.opacity(barAlpha)))
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
