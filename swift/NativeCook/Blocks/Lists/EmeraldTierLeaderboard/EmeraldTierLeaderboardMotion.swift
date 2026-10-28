import Foundation

// Port of motion.ts and recastMotion.ts. Pure functions of the rank phase: no state, no clock.

/// JS `Math.round` for the values used here (halves go up).
@inline(__always) private func emeraldRound(_ x: Double) -> Double { (x + 0.5).rounded(.down) }

enum EmeraldTierMotion {
    static let loopSeconds = 10.0
    static let LOOP = loopSeconds
    static let TAKEOVER_MS = 1200.0
    static let CROWN_SEAT_PHASE = 0.98
    static let DEMO_TAKEOVER_START = 2.0
    static let DEMO_RETURN_START = 7.6
    static let SNAP_DECELERATION = 0.995

    static func clamp(_ n: Double, _ a: Double = 0, _ b: Double = 1) -> Double { max(a, min(b, n)) }
    static func range(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }

    static func bezier(_ xIn: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        let x = clamp(xIn)
        var u = x
        for _ in 0..<7 {
            let a = 1 - u
            let f = 3 * a * a * u * x1 + 3 * a * u * u * x2 + u * u * u - x
            let d = 3 * a * a * x1 + 6 * a * u * (x2 - x1) + 3 * u * u * (1 - x2)
            if abs(d) < 0.000001 { break }
            u = clamp(u - f / d)
        }
        return 3 * (1 - u) * (1 - u) * u * y1 + 3 * (1 - u) * u * u * y2 + u * u * u
    }
    static func out(_ x: Double) -> Double { bezier(x, 0.23, 1, 0.32, 1) }
    static func mix(_ a: Double, _ b: Double, _ p: Double) -> Double { a + (b - a) * p }
    static func smooth(_ p: Double) -> Double { p * p * (3 - 2 * p) }

    /// The demo clock has readable holds; touch never enters its backwards return branch.
    static func demoPhase(_ progress: Double) -> Double {
        let seconds = clamp(progress) * LOOP
        if seconds < DEMO_TAKEOVER_START { return 0 }
        if seconds < DEMO_TAKEOVER_START + TAKEOVER_MS / 1000 { return (seconds - DEMO_TAKEOVER_START) / (TAKEOVER_MS / 1000) }
        if seconds < DEMO_RETURN_START { return 1 }
        if seconds < DEMO_RETURN_START + TAKEOVER_MS / 1000 { return 1 - (seconds - DEMO_RETURN_START) / (TAKEOVER_MS / 1000) }
        return 0
    }

    /// Continuous resistance at either endpoint, in the same units as the drag.
    static func rubberPhase(_ value: Double) -> Double {
        let edge = clamp(value), over = value - edge
        return edge + over / (1 + abs(over) / 0.12)
    }
    static func unrubberPhase(_ value: Double) -> Double {
        let edge = clamp(value), over = value - edge
        return edge + over / max(0.00001, 1 - abs(over) / 0.12)
    }
    /// A deliberate flick commits in its direction; a slow release chooses the nearest projected rest.
    static func snapTarget(_ phase: Double, _ velocity: Double) -> Double {
        if velocity > 0.35 { return 1 }
        if velocity < -0.35 { return 0 }
        let projected = phase + (velocity / 1000) * SNAP_DECELERATION / (1 - SNAP_DECELERATION)
        return clamp(projected) >= 0.5 ? 1 : 0
    }

    struct Pose { var x: Double; var y: Double; var scale: Double }
    struct CrownPose { var x: Double; var y: Double; var rotation: Double }

    struct Frame {
        var progress: Double, rawProgress: Double, cycle: Double, rise: Double, travel: Double, arc: Double
        var ari: Pose, milo: Pose, crown: CrownPose
        var crownTravel: Double, age: Double, impact: Bool
        var burst: Double, response: Double, responsePhase: Double, anticipation: Double, settle: Double, labelOpacity: Double
        var score: Double, hold: Double, landing: Double, reduced: Bool, allowResponse: Bool
    }

    /// One monotonic, interruptible rank journey. All coordinates remain local to the existing arena.
    static func rankFrame(_ rankPhase: Double, reduced: Bool = false, allowResponse: Bool = true) -> Frame {
        let t = reduced ? (rankPhase >= 0.5 ? 1.0 : 0.0) : clamp(rankPhase)
        let edgeOffset = reduced ? 0 : (rankPhase - t) * 112
        let rise = out(range(t, 0, 0.24))
        let p = bezier(range(t, 0.28, 0.7175), 0.4, 0, 0.2, 1)
        let arc = sin(p * .pi)
        let anticipation = reduced ? 0 : sin(range(t, 0.20, 0.28) * .pi)
        let landingAge = max(0, (t - 0.7175) * TAKEOVER_MS / 1000)
        let age = max(0, (t - CROWN_SEAT_PHASE) * TAKEOVER_MS / 1000)
        let landed = !reduced && t > 0.7175 && t < 1
        let impact = allowResponse && !reduced && t >= CROWN_SEAT_PHASE && t < 1
        let landing = landed ? sin(range(t, 0.7175, 0.91) * .pi) : 0
        let settle = landed ? -1.1 * exp(-16 * landingAge) * sin(24 * landingAge) : 0
        let responsePhase = range(t, CROWN_SEAT_PHASE, 1)
        let response = impact ? pow(sin(responsePhase * .pi), 2) : 0
        let crownSeat = impact ? -1.2 * exp(-20 * age) * sin(28 * age) : 0
        // Eight extra points on Ari's lower route keep the two circular portraits separated.
        let ari = Pose(x: mix(142, 30, p) + edgeOffset, y: mix(35, 73, p) + arc * 35, scale: mix(1, 0.8, p))
        let milo = Pose(x: mix(30, 142, p) + edgeOffset,
                        y: mix(73, 35, p) - arc * 37 + anticipation * 3 + landing * 1.4 + settle,
                        scale: mix(0.8, 1, p) * (1 - anticipation * 0.012))
        // The crown belongs to the first-place axis, not an interpolation of two moving heads.
        // Its top never rises above -10: the countdown ends at -13 in arena coordinates.
        let lift = out(range(t, 0.20, 0.28)) * (1 - smooth(range(t, 0.7175, CROWN_SEAT_PHASE)))
        let crown = CrownPose(x: 180 + edgeOffset,
                              y: 14 - lift * 24 + (p == 1 ? landing * 1.4 + settle : 0) + crownSeat, rotation: 0)
        // Both text blocks are fully hidden before their boxes can meet the other portrait.
        let labelOpacity = reduced ? 1 : 1 - smooth(range(p, 0.02, 0.12)) + smooth(range(p, 0.88, 0.98))
        return Frame(progress: t, rawProgress: rankPhase, cycle: t, rise: rise, travel: p, arc: arc, ari: ari, milo: milo, crown: crown,
                     crownTravel: range(t, 0.20, CROWN_SEAT_PHASE), age: age, impact: impact,
                     burst: response, response: response, responsePhase: responsePhase, anticipation: anticipation,
                     settle: settle, labelOpacity: labelOpacity,
                     score: 950 + emeraldRound(100 * rise), hold: smooth(range(t, CROWN_SEAT_PHASE, 0.94)), landing: landing,
                     reduced: reduced, allowResponse: allowResponse)
    }

    /// Public deterministic preview: t=0 and t=10 have exactly the same frame.
    static func frame(_ progress: Double, reduced: Bool = false) -> Frame {
        let wrapped = (progress.truncatingRemainder(dividingBy: 1) + 1).truncatingRemainder(dividingBy: 1)
        return rankFrame(demoPhase(wrapped), reduced: reduced, allowResponse: wrapped * LOOP < DEMO_RETURN_START)
    }
}

// MARK: - Rank Recast geometry (recastMotion.ts). All coordinates are local to the 393 x 335 arena.

enum EmeraldTierRecast {
    static let STAGE_WIDTH = 393.0
    static let STAGE_HEIGHT = 335.0
    static let DURATION_MS = 1200.0
    /// The crown lands on the new leader here; the haptic and the plinth response follow it.
    static let SEAT_PHASE = 0.88
    /// Arena y of the promotion boundary between the podium (top 3) and row 4.
    static let PROMOTION_LINE_Y = 281.5

    enum ActorID: Int, CaseIterable { case ari, milo, juno, theo }
    /// Back-to-front order. Theo's source row is the foreground object throughout the recast.
    static let SURFACE_ORDER: [ActorID] = [.juno, .milo, .ari, .theo]

    struct Surface {
        var x = 0.0, y = 0.0, width = 0.0, height = 0.0, radius = 0.0
        /// The top plane extends UPWARD from y to y-depth.
        var depth = 0.0
        var rank = 1, rankOpacity = 1.0, glow = 0.0
    }
    struct Actor {
        var cx = 0.0, cy = 0.0, size = 0.0
        /// Leading edge of a 110pt name box; center-aligned on podium, left-aligned in row.
        var nameX = 0.0
        /// Top edge of the native 18pt name box.
        var nameY = 0.0
        /// Center of the native score box; scoreY is its 21pt score box's top edge.
        var scoreX = 0.0, scoreY = 0.0
        var rowMix = 0.0, labelOpacity = 1.0, score = 0.0
        var rank = 1
    }
    struct Crown { var x = 0.0, y = 0.0, opacity = 1.0, rotation = 0.0, squash = 0.0, sparkle = 0.0 }
    struct Gain { var x = 0.0, y = 0.0, opacity = 0.0 }
    struct Light { var response = 0.0, phase = 0.0, seat = 0.0 }

    struct PerActor<T> {
        var ari: T, milo: T, juno: T, theo: T
        subscript(id: ActorID) -> T {
            get { switch id { case .ari: return ari; case .milo: return milo; case .juno: return juno; case .theo: return theo } }
            set { switch id { case .ari: ari = newValue; case .milo: milo = newValue; case .juno: juno = newValue; case .theo: theo = newValue } }
        }
    }

    struct Frame {
        var phase: Double, reduced: Bool
        var actors: PerActor<Actor>
        var surfaces: PerActor<Surface>
        /// Crown has the existing 34 x 30 footprint. It never leaves the first-place axis.
        var crown: Crown
        /// 0 to 1 while every plinth numeral rolls from its old rank to its new one.
        var roll: Double
        /// Canonical 68 x 19 XP caption, above Theo with a 3pt portrait gap.
        var gain: Gain
        var light: Light
        /// Renderer aliases for the single docking reflection.
        var response: Double, responsePhase: Double
    }

    private static func clamp(_ v: Double) -> Double { max(0, min(1, v)) }
    private static func mix(_ from: Double, _ to: Double, _ p: Double) -> Double { from + (to - from) * p }
    private static func segment(_ t: Double, _ from: Double, _ to: Double) -> Double {
        let p = clamp((t - from) / (to - from))
        return p * p * (3 - 2 * p)
    }

    /// The crown stays on the throne's axis while the kings change: it pops off Ari, hovers with a
    /// slow wobble while the podium rearranges, then drops onto Theo with gravity and a damped seat.
    private static func crownPose(_ t: Double, _ reduced: Bool) -> Crown {
        if reduced { return Crown(x: 180, y: 14, opacity: 1, rotation: 0, squash: 0, sparkle: 0) }
        let pop = segment(t, 0.05, 0.17)
        let fall = clamp((t - 0.74) / (SEAT_PHASE - 0.74))
        let hover = t > 0.17 && t < 0.74 ? sin((t - 0.17) / 0.57 * .pi) : 0
        let lifted = t < SEAT_PHASE ? pop * (1 - fall * fall) : 0
        // Normalised seat time, so the settle reaches the exact rest pose at the end of the event.
        let u = t >= SEAT_PHASE ? clamp((t - SEAT_PHASE) / (1 - SEAT_PHASE)) : 0
        let seat = t >= SEAT_PHASE ? exp(-u * 4) * sin(u * .pi * 3) * (1 - u) : 0
        return Crown(x: 180,
                     y: 14 - 20 * lifted - 2.5 * hover + 2.2 * seat,
                     opacity: 1,
                     rotation: lifted * (sin(t * 19) * 7 - 4) + seat * -6,
                     squash: t >= SEAT_PHASE ? (1 - u) * (1 - u) : 0,
                     sparkle: u)
    }

    private static func makeActor(_ cx: Double, _ cy: Double, _ size: Double, _ rowMix: Double, _ surface: Surface,
                              _ opacity: Double, _ score: Double, _ labelRowMix: Double? = nil) -> Actor {
        let lrm = labelRowMix ?? rowMix
        return Actor(cx: cx, cy: cy, size: size,
                     nameX: mix(cx - 55, surface.x + 82, lrm),
                     nameY: mix(cy + size / 2 + 7, surface.y + 15, lrm),
                     scoreX: mix(cx, surface.x + surface.width - 59, lrm),
                     scoreY: mix(cy + size / 2 + 25, surface.y + 14, lrm),
                     rowMix: rowMix, labelOpacity: opacity, score: score, rank: surface.rank)
    }

    /// One deterministic and reversible event: lift 0-.18, space-yield .14-.5, reshape/travel .18-.8,
    /// dock .8-1. No integration, random values, or renderer objects. Reduced motion samples one
    /// complete endpoint at the semantic halfway point.
    static func frame(_ phase: Double, reduced: Bool = false) -> Frame {
        let input = clamp(phase)
        let t = reduced ? (input >= 0.5 ? 1.0 : 0.0) : input
        let lift = segment(t, 0, 0.18)
        let yieldSpace = segment(t, 0.14, 0.50)
        let reshape = segment(t, 0.18, 0.80)
        let junoShape = segment(t, 0.24, 0.80)
        let seated = segment(t, 0.80, 1)
        let responsePhase = clamp((t - 0.80) / 0.20)
        let response = reduced || t <= 0.80 || t >= 1 ? 0 : pow(sin(responsePhase * .pi), 2)
        let contact = reduced ? 0 : response * 1.4
        // Each name is hidden only while its own portrait crosses another lane, then returns at its new seat.
        let podiumLabel = 1 - segment(t, 0.12, 0.18) + segment(t, 0.50, 0.58)
        let junoLabelOpacity = 1 - segment(t, 0.12, 0.18) + segment(t, 0.66, 0.74)
        let theoLabelOpacity = 1 - segment(t, 0.06, 0.12) + segment(t, 0.68, 0.76)
        // The XP gain rides beside Theo during the ascent only, clear of the crown's axis.
        let gainOpacity = reduced ? 0 : segment(t, 0.56, 0.64) * (1 - segment(t, 0.90, 0.98))
        // Plinth numerals stay with their plinths and roll to the new order as they settle; they only
        // dim while two plinths pass through each other, so two numerals never stack.
        let crossing = reduced ? 0 : sin(.pi * segment(t, 0.14, 0.50))
        let podiumRank = 1 - 0.9 * crossing
        let roll = reduced ? (t >= 0.5 ? 1.0 : 0.0) : segment(t, 0.46, 0.68)
        // Rank identities change only while every rank/name label is fully hidden.
        let newOrder = t >= 0.5
        let surfaces = PerActor<Surface>(
            ari: Surface(x: mix(135, 24, yieldSpace), y: mix(175, 202, yieldSpace), width: 123,
                         height: mix(100, 73, yieldSpace), radius: 4, depth: 15,
                         rank: newOrder ? 2 : 1, rankOpacity: podiumRank, glow: mix(0.76, 0.31, yieldSpace)),
            milo: Surface(x: mix(24, 246, yieldSpace), y: mix(202, 214, yieldSpace), width: 123,
                          height: mix(73, 61, yieldSpace), radius: 4, depth: 15,
                          rank: newOrder ? 3 : 2, rankOpacity: podiumRank, glow: 0.31),
            juno: Surface(x: mix(246, 19, junoShape), y: mix(214, 288, junoShape),
                          width: mix(123, 355, junoShape), height: mix(61, 47, junoShape),
                          radius: mix(4, 13, junoShape), depth: mix(15, 0, junoShape),
                          rank: newOrder ? 4 : 3, rankOpacity: reduced ? 1 : 1 - segment(t, 0.18, 0.32), glow: mix(0.31, 0.13, junoShape)),
            theo: Surface(x: mix(19, 135, reshape), y: mix(288 - 18 * lift, 175, reshape) + contact,
                          width: mix(355, 123, reshape), height: mix(47, 100, reshape) - contact,
                          radius: mix(13, 4, reshape), depth: mix(0, 15, reshape),
                          rank: newOrder ? 1 : 4, rankOpacity: reduced ? 1 : segment(t, 0.56, 0.74), glow: mix(0.13, 0.76, reshape)))

        // Ari uses an upper lane; Milo vacates left downward before crossing beneath that lane.
        let ariUp = segment(t, 0.14, 0.30), ariLeft = segment(t, 0.30, 0.50)
        let ariX = mix(mix(197, 150, ariUp), 85, ariLeft)
        let ariY = mix(mix(70, 40, ariUp), 101, ariLeft)
        let ariSize = mix(70, 56, ariUp)
        let miloDown = segment(t, 0.14, 0.26), miloAcross = segment(t, 0.26, 0.43), miloUp = segment(t, 0.43, 0.50)
        let miloX = mix(85, 321, miloAcross)
        let miloY = mix(mix(101, 172, miloDown), 111, miloUp)

        // Juno takes the outer-right lane, then travels along the vacated row baseline.
        let junoDown = segment(t, 0.14, 0.28), junoRow = segment(t, 0.28, 0.43), junoAcross = segment(t, 0.43, 0.68)
        let junoX = mix(mix(321, 350, junoDown), 74, junoAcross)
        let junoY = mix(mix(111, 210, junoDown), 311.5, junoRow)
        let junoSize = mix(mix(56, 42, junoDown), 30, junoRow)

        // Theo waits below the crossing, then owns the now-clear center ascent.
        let theoCenter = segment(t, 0.34, 0.56), theoRise = segment(t, 0.56, 0.80)
        let theoX = mix(74, 197, theoCenter)
        let theoY = mix(mix(mix(311.5, 278, lift), 225, theoCenter), 70, theoRise) + contact
        let theoSize = mix(mix(mix(30, 38, lift), 52, theoCenter), 70, theoRise)
        let actors = PerActor<Actor>(
            ari: makeActor(ariX, ariY, ariSize, 0, surfaces.ari, podiumLabel, 1000),
            milo: makeActor(miloX, miloY, 56, 0, surfaces.milo, podiumLabel, 950),
            juno: makeActor(junoX, junoY, junoSize, junoShape, surfaces.juno, junoLabelOpacity, 900),
            // Finish the hidden label-layout transfer before revealing Theo during the ascent.
            // The score counts up while Theo climbs, so the gain is read as it happens.
            theo: makeActor(theoX, theoY, theoSize, 1 - reshape, surfaces.theo, theoLabelOpacity,
                        850 + emeraldRound(200 * (1 - pow(1 - segment(t, 0.60, 0.88), 2))), 1 - segment(t, 0.18, 0.60)))
        return Frame(phase: t, reduced: reduced, actors: actors, surfaces: surfaces,
                     crown: crownPose(t, reduced), roll: roll,
                     gain: Gain(x: theoX + theoSize / 2 + 6, y: theoY - 10, opacity: gainOpacity),
                     light: Light(response: response, phase: responsePhase, seat: seated),
                     response: response, responsePhase: responsePhase)
    }
}
