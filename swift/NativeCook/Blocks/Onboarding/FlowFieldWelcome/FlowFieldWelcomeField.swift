import SwiftUI

// The field is a square lattice of short strokes. Each one's place and angle are computed every
// frame from how far the field has opened (0 packed, 1 the open vortex), so nothing is tweened
// between two stored pictures.

enum FlowFieldWelcomeMotion {
    /// autoPlay: the field opens and closes twice a `loop`.
    static let loop = 8.6
    static let cycle = 4.3
    static let openAt = 0.3
    static let closeAt = 2.7
    /// Seconds from packed to the open vortex, and back.
    static let morph = 1.4
    /// Reduced motion: the two figures cross-fade in this long.
    static let fade = 0.3
    /// After a touch autoPlay waits this long, then closes the field and starts over.
    static let userHold = 3.0
    /// The angle of the vortex's eye round the centre when the field opens, and its radians a second.
    static let eyeFrom = -Double.pi / 2
    static let eyeSpeed = 1.2
    /// The finger's pull comes on fast and lets go slowly.
    static let pullIn = 0.15
    static let pullOut = 0.45
}

private enum Lattice {
    static let grid = 18
    static let count = grid * grid
    static let openSize = 260.0
    static let pitch = openSize / Double(grid - 1)
    /// The packed lattice is this much of the open one: 150 points.
    static let packed = 0.58
    /// Before it opens, and as the last thing when it closes, the block squeezes to 140 points.
    static let squeeze = 0.042
    static let squeezeFor = 0.3
    /// Half-way the lattice is twisted, the outside more than the inside, which tears its corners out.
    static let twist = 50 * Double.pi / 180
    static let twistInside = 0.25
    /// The outside starts this much of the way later than the inside: the motion ripples outwards.
    static let ripple = 0.06
    /// Half-way a few strokes are flung further out, and all of them lean off their line.
    static let fling = 26.0
    static let stray = 40 * Double.pi / 180
    /// Packed, a stroke points along the swirl give or take `spread`, and twitches by `jitter` a few times a second.
    static let spread = 70 * Double.pi / 180
    static let jitter = 10 * Double.pi / 180
    static let jitterHz = 5.0
    static let jitterSteps = Int((FlowFieldWelcomeMotion.loop * jitterHz).rounded())
    static let jitterBlend = 0.5
    /// Open, the strokes circle an eye that itself circles the centre.
    static let eye = 24.0
    static let dash = 5.0
    static let thick = 1.2
    /// A finger pushes the strokes within `repelReach` away by up to `repel`, and turns those within `turnReach` towards itself.
    static let repel = 14.0
    static let repelReach = 60.0
    static let turnReach = 120.0

    /// Per stroke: its home on the open lattice, how far that is from the centre (0 to 1), and its
    /// own share of the spread, the fling and the stray.
    struct Stroke {
        let x: Double
        let y: Double
        let far: Double
        let spread: Double
        let fling: Double
        let stray: Double
    }

    static let strokes: [Stroke] = (0..<count).map { i in
        let x = (Double(i % grid) - Double(grid - 1) / 2) * pitch
        let y = (Double(i / grid) - Double(grid - 1) / 2) * pitch
        return Stroke(
            x: x, y: y,
            far: hypot(x, y) / (openSize / 2 * 2.0.squareRoot()),
            spread: (hash(i * 4 + 1) * 2 - 1) * spread,
            fling: pow(hash(i * 4 + 2), 3) * fling,
            stray: (hash(i * 4 + 3) * 2 - 1) * stray
        )
    }

    /// A number from 0 to 1 that depends on `n` alone, the same on every platform.
    static func hash(_ n: Int) -> Double {
        var h = (UInt32(truncatingIfNeeded: n) ^ 0x9e37_79b9) &* 0x85eb_ca6b
        h ^= h >> 13
        h = h &* 0xc2b2_ae35
        h ^= h >> 16
        return Double(h) / 4_294_967_296
    }

    static func clamp01(_ t: Double) -> Double {
        min(1, max(0, t))
    }

    static func ease(_ t: Double) -> Double {
        t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
    }
}

/// The field of strokes, drawn in one pass in a 360-point box scaled to the frame.
struct FlowFieldWelcomeField: View, Animatable {
    /// How far the field has opened, 0 to 1, moving at a steady pace.
    var progress: Double
    var opening: Bool
    /// Seconds since the screen appeared, for the twitch.
    var clock: Double
    /// The angle of the vortex's eye round the centre.
    var eye: Double
    /// The touch, from the field's centre, and how strongly it pulls (0 to 1).
    var finger: CGPoint
    var pull: Double
    /// Reduced motion: the packed and the open figure cross-fade, and nothing twitches.
    var still: Bool
    var color: Color

    /// The canvas: the open lattice plus room for the strokes that are flung out.
    static let size = 360.0

    var animatableData: Double {
        get { progress }
        set { progress = newValue }
    }

    var body: some View {
        Canvas { context, size in
            var context = context
            context.scaleBy(x: size.width / Self.size, y: size.height / Self.size)
            let style = StrokeStyle(lineWidth: Lattice.thick, lineCap: .round)
            if still {
                let packed = Self.path(progress: 0, opening: true, clock: 0, eye: FlowFieldWelcomeMotion.eyeFrom, finger: .zero, pull: 0)
                let open = Self.path(progress: 1, opening: true, clock: 0, eye: FlowFieldWelcomeMotion.eyeFrom, finger: .zero, pull: 0)
                context.stroke(packed, with: .color(color.opacity(1 - progress)), style: style)
                context.stroke(open, with: .color(color.opacity(progress)), style: style)
            } else {
                let path = Self.path(progress: progress, opening: opening, clock: clock, eye: eye, finger: finger, pull: pull)
                context.stroke(path, with: .color(color), style: style)
            }
        }
    }

    /// Every stroke of the field as one path.
    static func path(progress: Double, opening: Bool, clock: Double, eye: Double, finger: CGPoint, pull: Double) -> Path {
        typealias L = Lattice
        var path = Path()
        let ticks = clock.truncatingRemainder(dividingBy: FlowFieldWelcomeMotion.loop) * L.jitterHz
        let step = Int(ticks.rounded(.down)) % L.jitterSteps
        let before = (step + L.jitterSteps - 1) % L.jitterSteps
        let u = L.clamp01((ticks - ticks.rounded(.down)) / L.jitterBlend)
        let blend = u * u * (3 - 2 * u)
        let eyeOut = L.eye * L.ease(progress)
        let eyeX = eyeOut * cos(eye)
        let eyeY = eyeOut * sin(eye)
        let half = size / 2
        for (i, stroke) in L.strokes.enumerated() {
            let far = stroke.far
            // The inside leads both ways.
            let own = L.clamp01(progress * (1 + L.ripple) - L.ripple * (opening ? far : 1 - far))
            let q = L.ease(own)
            let tear = sin(.pi * q)
            let squeeze = sin(.pi * min(1, own / L.squeezeFor))
            let scale = L.packed + (1 - L.packed) * q - L.squeeze * squeeze * squeeze
            let twist = L.twist * tear * (L.twistInside + (1 - L.twistInside) * far * far)
            var x = (stroke.x * cos(twist) - stroke.y * sin(twist)) * scale
            var y = (stroke.x * sin(twist) + stroke.y * cos(twist)) * scale
            let reach = max(hypot(x, y), 0.0001)
            let fling = stroke.fling * tear
            x += x / reach * fling
            y += y / reach * fling
            let twitch = L.hash(i * 64 + 1000 + before) * (1 - blend) + L.hash(i * 64 + 1000 + step) * blend
            var angle = atan2(y - eyeY, x - eyeX) + .pi / 2 + twist + (1 - q) * (stroke.spread + L.jitter * (twitch * 2 - 1)) + tear * stroke.stray
            if pull > 0 {
                let dx = x - finger.x
                let dy = y - finger.y
                let gap = max(hypot(dx, dy), 0.0001)
                if gap < L.repelReach {
                    let k = 1 - gap / L.repelReach
                    x += dx / gap * L.repel * pull * k * k
                    y += dy / gap * L.repel * pull * k * k
                }
                if gap < L.turnReach {
                    let k = 1 - gap / L.turnReach
                    // A stroke has no head: it turns the short way to the line through the finger.
                    var turn = atan2(dy, dx) - angle
                    turn -= .pi * (turn / .pi).rounded()
                    angle += turn * pull * k * k
                }
            }
            let hx = cos(angle) * L.dash / 2
            let hy = sin(angle) * L.dash / 2
            path.move(to: CGPoint(x: half + x - hx, y: half + y - hy))
            path.addLine(to: CGPoint(x: half + x + hx, y: half + y + hy))
        }
        return path
    }
}
