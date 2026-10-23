import SwiftUI

enum TiltPosterParallaxTokens {
    enum Palette {
        static let background = Color(red: 27 / 255, green: 26 / 255, blue: 23 / 255)
        static let backgroundDeep = Color(red: 11 / 255, green: 10 / 255, blue: 9 / 255)
        static let ink = Color.white
        static let inkSoft = Color.white.opacity(0.6)
        static let inkFaint = Color.white.opacity(0.42)
        static let inkGhost = Color.white.opacity(0.2)
        static let pill = Color(red: 27 / 255, green: 26 / 255, blue: 22 / 255)
        static let pillGhost = Color.white.opacity(0.04)
        static let glass = Color.white.opacity(0.12)
        static let glassBorder = Color.white.opacity(0.08)
        static let trophy = Color(red: 245 / 255, green: 192 / 255, blue: 74 / 255).opacity(0.6)
        static let stageLight = Color(red: 140 / 255, green: 168 / 255, blue: 235 / 255).opacity(0.42)
        static let gold = [
            Color(red: 138 / 255, green: 90 / 255, blue: 23 / 255),
            Color(red: 243 / 255, green: 210 / 255, blue: 122 / 255),
            Color(red: 255 / 255, green: 244 / 255, blue: 200 / 255),
            Color(red: 200 / 255, green: 144 / 255, blue: 46 / 255),
            Color(red: 122 / 255, green: 75 / 255, blue: 16 / 255),
        ]
        static let foil = Color.white.opacity(0.85)
        static let rim = Color.white.opacity(0.22)
        static let glare = Color(red: 255 / 255, green: 240 / 255, blue: 220 / 255).opacity(0.55)
        static let shadow = Color.black
        static let glow = Color(red: 214 / 255, green: 104 / 255, blue: 44 / 255).opacity(0.42)
        static let back = [
            Color(red: 38 / 255, green: 24 / 255, blue: 16 / 255).opacity(0.88),
            Color(red: 16 / 255, green: 11 / 255, blue: 8 / 255).opacity(0.96),
        ]
        static let backRule = Color(red: 243 / 255, green: 210 / 255, blue: 122 / 255).opacity(0.5)
        static let backInk = Color(red: 255 / 255, green: 244 / 255, blue: 226 / 255).opacity(0.92)
        static let backMute = Color(red: 255 / 255, green: 244 / 255, blue: 226 / 255).opacity(0.52)
        static let pillSaved = Color(red: 243 / 255, green: 210 / 255, blue: 122 / 255).opacity(0.14)
        static let pillSavedBorder = Color(red: 243 / 255, green: 210 / 255, blue: 122 / 255).opacity(0.5)
        static let goldInk = Color(red: 243 / 255, green: 210 / 255, blue: 122 / 255)
        static let holo = [
            Color(red: 255 / 255, green: 159 / 255, blue: 210 / 255),
            Color(red: 255 / 255, green: 231 / 255, blue: 160 / 255),
            Color(red: 169 / 255, green: 255 / 255, blue: 208 / 255),
            Color(red: 159 / 255, green: 216 / 255, blue: 255 / 255),
            Color(red: 200 / 255, green: 168 / 255, blue: 255 / 255),
            Color(red: 255 / 255, green: 159 / 255, blue: 210 / 255),
        ]
        static let holoMirrored = (0..<10).flatMap { cycle in
            cycle.isMultiple(of: 2) ? holo : Array(holo.reversed())
        }
        static let finger = Color.white.opacity(0.32)
        static let fingerRing = Color.white.opacity(0.7)
    }

    enum Typeface {
        static let script = "Snell Roundhand"
        static let ui = "Helvetica Neue"
        static let titleSize: CGFloat = 26
        static let titleLine: CGFloat = 32
        static let dateSize: CGFloat = 14
        static let dateLine: CGFloat = 18
        static let pillSize: CGFloat = 15
        static let awardSize: CGFloat = 13
        static let bodySize: CGFloat = 15
        static let bodyLine: CGFloat = 17.5
        static let moreSize: CGFloat = 14
        static let posterTitle: CGFloat = 44
        static let backTitle: CGFloat = 28
        static let credit: CGFloat = 7.5
    }

    enum Layout {
        static let referenceWidth: CGFloat = 402
        static let gutter: CGFloat = 25
        static let topBar: CGFloat = -5
        static let close: CGFloat = 36
        static let awardHeight: CGFloat = 30
        static let cardTop: CGFloat = 69
        static let stageLight: CGFloat = 230
        static let backdropAspect: CGFloat = 520 / 402
        static let cardWidthRatio: CGFloat = 0.343
        static let cardAspect: CGFloat = 1.486
        static let cardRadius: CGFloat = 18
        static let titleGap: CGFloat = 18
        static let pillHeight: CGFloat = 51
        static let pillGap: CGFloat = 13
        static let bodyGap: CGFloat = 20
        static let plateOverscan: CGFloat = 1.24
        static let titleY: CGFloat = 0.66
        static let titleSkew: CGFloat = -0.08
        static let fingerRadius: CGFloat = 22
        static let cardMargin: CGFloat = 72
        static let backdropBleed: CGFloat = 60
        static let topBarSideInset: CGFloat = 17
        static let awardHorizontalPadding: CGFloat = 12
        static let awardGap: CGFloat = 6
        static let infoGap: CGFloat = 4
        static let pillHorizontalPadding: CGFloat = 20
        static let pillIcon: CGFloat = 18
        static let pillContentGap: CGFloat = 8
        static let moreGap: CGFloat = 4
    }

    enum Back {
        static let plateOpacity = 0.5
        static let plateBlur: CGFloat = 10
        static let titleY: CGFloat = 0.27
        static let ruleY: CGFloat = 0.33
        static let rule: CGFloat = 28
        static let creditsY: CGFloat = 0.43
        static let lineStep: CGFloat = 11
    }

    enum Space {
        static let extraSmall: CGFloat = 4
        static let small: CGFloat = 8
        static let medium: CGFloat = 12
        static let large: CGFloat = 16
        static let extraLarge: CGFloat = 20
        static let doubleExtra: CGFloat = 24
    }

    enum Copy {
        struct Credit: Identifiable {
            let id: Int
            let text: String
            let strong: Bool
            let gap: CGFloat
        }

        static let title = "Solenne"
        static let date = "April 24, 2026 · In 2 days"
        static let award = ["1", " nomination, ", "2", " wins"]
        static let watchlist = "Watchlist"
        static let added = "Added"
        static let watched = "Watch"
        static let body = "Discover the story of Solenne Marchetti, the voice that filled arenas from a single silver microphone, and the life she lived beyond the music: the towns, the losses and the songs she never released."
        static let credits = [
            Credit(id: 0, text: "A FILM BY", strong: false, gap: 0),
            Credit(id: 1, text: "ILSE VARGA", strong: true, gap: 0),
            Credit(id: 2, text: "NARRATED BY", strong: false, gap: 8),
            Credit(id: 3, text: "TEO BRANDT", strong: true, gap: 8),
            Credit(id: 4, text: "Documentary · 1 h 48 min", strong: false, gap: 16),
            Credit(id: 5, text: "Marchetti Pictures", strong: false, gap: 16),
        ]
        static let more = "Show more"
        static let less = "Show less"
        static let posterLabel = "Poster of Solenne: a singer in a red stage jacket under warm arena lights"
        static let posterHint = "Tilt your phone or drag the poster to look around it. Activate to turn it over."
    }

    enum Tilt {
        static let perHalfCard = 14 * Double.pi / 180
        static let max = 18 * Double.pi / 180
        static let rubber = 0.35
        static let sensorGain = 0.9
        static let sensorMax = 14 * Double.pi / 180
        static let sensorSmoothing = 0.12
        static let driftAmplitude = 1.2 * Double.pi / 180
        static let driftPeriod = 12.0
        static let perspective: CGFloat = 420
        static let lift = 1.34
        static let reducedMax = 4 * Double.pi / 180
    }

    enum Parallax {
        static let plate: CGFloat = -38
        static let figure: CGFloat = 26
        static let title: CGFloat = 42
        static let backdrop: CGFloat = -30
        static let glare: CGFloat = -2.2
        static let foil: CGFloat = 2.6
    }

    enum Flip {
        static let tapDuration = 0.260
        static let slop: CGFloat = 8
        static let tapSpeed: CGFloat = 400
        static let rise = 0.05
    }

    enum Holo {
        static let travelX: CGFloat = 2.4
        static let travelY: CGFloat = 1.6
        static let flipTravel: CGFloat = 0.5
        static let period: CGFloat = 0.7
        static let rest = 0.18
        static let max = 0.75
        static let gain = 2.2
    }

    enum Edge {
        static let depth: CGFloat = 4.5
        static let from = 6 * Double.pi / 180
        static let to = 10 * Double.pi / 180
    }

    enum Float {
        static let amplitude: CGFloat = 2
        static let period = 6.0
    }

    struct Spring {
        let damping: Double
        let stiffness: Double
        let mass: Double
    }

    enum Springs {
        static let flip = Spring(damping: 18, stiffness: 140, mass: 1.2)
        static let release = Spring(damping: 9, stiffness: 120, mass: 1)
        static let lift = Spring(damping: 16, stiffness: 260, mass: 1)
        static let morph = Spring(damping: 14, stiffness: 220, mass: 1)
        static let press = Spring(damping: 20, stiffness: 420, mass: 1)
    }

    enum Timing {
        static let reduced = 0.2
        static let referenceMaxFrameDelta = 0.250
        static let maxFrameDelta = 0.1
        static let interactionResumeDelay = 2.0
    }

    static let loop: Double = 12

    enum QA {
        enum Hold: Hashable {
            case rest
            case a
            case b
        }

        static let hold: Hold? = nil
        static let holdAt: [Hold: Double] = [.rest: 0, .a: 2.2, .b: 3.8]
    }

    enum Autoplay {
        enum Target: Equatable {
            case card
            case watch
        }

        struct Tap {
            let at: Double
            let target: Target
        }

        static let referenceResumeDelay = 3.5
        static let holdStart = 0.8
        static let holdEnd = 5.2
        static let amplitudeX = 1.1
        static let amplitudeY = 0.75
        static let ramp = 0.6
        static let pathCycles = 1.15
        static let taps = [
            Tap(at: 6.0, target: .card),
            Tap(at: 8.2, target: .card),
            Tap(at: 9.6, target: .watch),
            Tap(at: 10.4, target: .watch),
        ]
        static let press = 0.110
        static let fingerLead = 0.280
        static let fingerFade = 0.140
        static let releaseVelocitySample = 0.016
    }

    enum Render {
        static let foilHalfWidth: CGFloat = 40
        static let foilTop: CGFloat = -40
        static let foilBottom: CGFloat = 10
        static let titleShadowX: CGFloat = 1.5
        static let titleShadowY: CGFloat = 2
        static let titleShadow = Color(red: 40 / 255, green: 16 / 255, blue: 0 / 255).opacity(0.55)
        static let glareRestOpacity = 0.18
        static let glareRange = 0.32
        static let glareGain = 0.9
        static let glareRadiusRatio: CGFloat = 0.9
        static let glareRestY: CGFloat = 0.3
        static let shadowXGain: CGFloat = -60
        static let shadowRestY: CGFloat = 18
        static let shadowYGain: CGFloat = -40
        static let shadowScale: CGFloat = 0.92
        static let shadowEdgeMinimum: CGFloat = 0.3
        static let shadowBaseOpacity = 0.55
        static let shadowLiftGain = 0.6
        static let shadowBlur: CGFloat = 22
        static let glowXGain: CGFloat = 40
        static let glowRestY: CGFloat = 26
        static let glowYGain: CGFloat = 30
        static let glowInsetXRatio: CGFloat = 0.06
        static let glowInsetYRatio: CGFloat = 0.1
        static let glowWidthRatio: CGFloat = 0.88
        static let glowHeightRatio: CGFloat = 0.86
        static let glowBlur: CGFloat = 34
        static let glowLiftGain = 0.7
        static let glowDeadZone = 2 * Double.pi / 180
        static let glowTiltGain = 1.2
        static let rimWidth: CGFloat = 1
        static let fingerRingWidth: CGFloat = 1.5
    }
}
