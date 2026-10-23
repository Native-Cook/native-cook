import CoreGraphics
import Foundation

/// What the canvas draws for one frame, read off the simulation (MoodOrbJar.tsx `publish`). Values only:
/// the drawing code never touches the simulation.
struct MoodOrbJarFrame {
    typealias Motion = MoodOrbJarTokens.Motion
    typealias Layout = MoodOrbJarTokens.Layout

    struct Sprite {
        var x: Double
        var y: Double
        var spin: Double
        var scale: Double
        var opacity: Double
        var mood: Int
        var squash: Double
        var vx: Double
        var vy: Double
        var isFlying: Bool
    }

    /// The landed orb's copy that flies to the new entry's avatar.
    struct Ghost {
        var x: Double
        var y: Double
        var scale: Double
        var mood: Int
    }

    struct Ripple {
        var x: Double
        var y: Double
        var progress: Double
        var mood: Int
    }

    var sprites: [Sprite] = []
    /// The picker orbs' scales.
    var slots = [Double](repeating: 1, count: MoodOrbJarTokens.moods.count)
    var lid = 0.0
    var tilt = 0.0
    var ghost: Ghost?
    var ripple: Ripple?
    var aim: [CGPoint] = []

    static let empty = MoodOrbJarFrame()

    init() {}

    init(sim s: MoodOrbJarSim, geometry g: MoodOrbJarGeometry) {
        let small = Layout.pickerOrb / Layout.orb
        sprites = s.orbs.map { orb in
            var scale = 1.0
            switch orb.state {
            case .held:
                scale = small * Motion.Throw.lift
            case .returning:
                scale = small
            case .leaving:
                scale = 1 - (1 - small) * min(1, orb.time / (Motion.Undo.rise + Motion.Undo.fly))
            default:
                break
            }
            return Sprite(
                x: orb.x,
                y: orb.y,
                spin: orb.spin,
                scale: scale,
                opacity: orb.state == .off ? 0 : orb.fade,
                mood: orb.mood,
                squash: orb.squash,
                vx: orb.vx,
                vy: orb.vy,
                isFlying: orb.state == .flying
            )
        }
        slots = s.slotScale
        lid = s.lid
        tilt = s.tilt
        ghost = Self.ghostState(s, g)
        if s.rippleTime >= 0 {
            ripple = Ripple(x: s.rippleX, y: s.rippleY, progress: s.rippleTime / Motion.Ripple.duration, mood: s.rippleMood)
        }
        aim = Self.aimArc(s, g)
    }

    /// Where the new entry's avatar is, where the ghost lands.
    static func avatar(_ g: MoodOrbJarGeometry) -> CGPoint {
        CGPoint(
            x: Layout.side * g.scale + MoodOrbJarTokens.Space.cardPadding + Layout.entryOrb,
            y: g.rowY + MoodOrbJarTokens.Space.avatarTop + Layout.entryOrb
        )
    }

    /// Lifts straight out of the jar, then arcs (ease-in-out) to the new entry's avatar, shrinking to its size.
    private static func ghostState(_ s: MoodOrbJarSim, _ g: MoodOrbJarGeometry) -> Ghost? {
        guard s.ghostTime >= 0 else { return nil }
        let target = avatar(g)
        let u = min(1, s.ghostTime / Motion.Ghost.duration)
        let l = min(1, u / Motion.Ghost.lift)
        let lift = 1 - pow(1 - l, 3)
        let sx = s.ghostX
        let sy = s.ghostY - Motion.Ghost.rise * g.scale * lift
        let a = max(0, (u - Motion.Ghost.lift) / (1 - Motion.Ghost.lift))
        let e = MoodOrbJarSim.easeInOut(a)
        let end = Layout.entryOrb / Layout.orb
        return Ghost(
            x: sx + (target.x - sx) * e,
            y: sy + (target.y - sy) * e - sin(Double.pi * e) * Motion.Ghost.arc * g.scale,
            scale: 1 + 0.08 * lift * (1 - e) + (end - 1) * e,
            mood: s.ghostMood
        )
    }

    /// The new card's growth, 0 ... 1 (MoodOrbJar.tsx `grow`).
    static func grow(_ s: MoodOrbJarSim) -> Double {
        if s.reduced || s.growTime == -1 {
            return 1
        }
        return s.growTime < 0 ? 0 : min(1, s.growTime / Motion.Ghost.grow)
    }

    /// The solved launch from where a held orb is now, sampled as an aim arc.
    private static func aimArc(_ s: MoodOrbJarSim, _ g: MoodOrbJarGeometry) -> [CGPoint] {
        guard s.held >= 0 else { return [] }
        let orb = s.orbs[s.held]
        let gravity = Motion.Throw.gravity * g.scale
        let apex = min(orb.y, g.mouthY) - Motion.Throw.apex * g.scale
        let vy = -sqrt(2 * gravity * max(1, orb.y - apex))
        let t = (-vy + sqrt(vy * vy + 2 * gravity * max(0, g.mouthY + 40 * g.scale - orb.y))) / gravity
        let vx = (g.centerX - orb.x) / max(0.2, t)
        let points = Motion.Aim.points
        return (1...points).map { k in
            let tt = (t * Double(k)) / Double(points)
            return CGPoint(x: orb.x + vx * tt, y: orb.y + vy * tt + 0.5 * gravity * tt * tt)
        }
    }
}
