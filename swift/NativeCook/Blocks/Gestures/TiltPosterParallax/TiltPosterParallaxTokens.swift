import SwiftUI

enum TiltPosterParallaxTokens {
    enum Palette {
        static let background = Color(red: 14 / 255, green: 12 / 255, blue: 16 / 255)
        static let ink = Color(red: 245 / 255, green: 241 / 255, blue: 234 / 255)
        static let inkSoft = ink.opacity(0.62)
        static let inkFaint = ink.opacity(0.42)
        static let glass = Color.white.opacity(0.10)
        static let glassBorder = Color.white.opacity(0.14)
        static let play = ink
        static let playInk = Color(red: 22 / 255, green: 19 / 255, blue: 26 / 255)
        static let trophy = Color(red: 233 / 255, green: 196 / 255, blue: 122 / 255)
        static let gold = [
            Color(red: 138 / 255, green: 90 / 255, blue: 23 / 255),
            Color(red: 243 / 255, green: 210 / 255, blue: 122 / 255),
            Color(red: 255 / 255, green: 244 / 255, blue: 200 / 255),
            Color(red: 200 / 255, green: 144 / 255, blue: 46 / 255),
            Color(red: 122 / 255, green: 75 / 255, blue: 16 / 255),
        ]
        static let foil = Color.white.opacity(0.85)
        static let embossDark = Color(red: 20 / 255, green: 10 / 255, blue: 20 / 255).opacity(0.55)
        static let embossLight = Color(red: 255 / 255, green: 236 / 255, blue: 200 / 255).opacity(0.35)
        static let posterInk = Color(red: 246 / 255, green: 226 / 255, blue: 200 / 255).opacity(0.72)
        static let rim = Color.white.opacity(0.20)
        static let glare = Color(red: 255 / 255, green: 240 / 255, blue: 220 / 255).opacity(0.50)
        static let shadow = Color.black
        static let glow = Color(red: 230 / 255, green: 140 / 255, blue: 90 / 255).opacity(0.40)
        static let edge = [
            Color(red: 232 / 255, green: 220 / 255, blue: 198 / 255),
            Color(red: 185 / 255, green: 168 / 255, blue: 140 / 255),
        ]
        static let back = [
            Color(red: 34 / 255, green: 26 / 255, blue: 34 / 255),
            Color(red: 18 / 255, green: 14 / 255, blue: 20 / 255),
        ]
        static let backRule = Color(red: 243 / 255, green: 210 / 255, blue: 122 / 255).opacity(0.50)
        static let backInk = Color(red: 255 / 255, green: 244 / 255, blue: 226 / 255).opacity(0.92)
        static let backMute = Color(red: 255 / 255, green: 244 / 255, blue: 226 / 255).opacity(0.52)
        static let pillSaved = Color(red: 243 / 255, green: 210 / 255, blue: 122 / 255).opacity(0.14)
        static let pillSavedBorder = Color(red: 243 / 255, green: 210 / 255, blue: 122 / 255).opacity(0.50)
        static let goldInk = Color(red: 243 / 255, green: 210 / 255, blue: 122 / 255)
        static let holo = [
            Color(red: 255 / 255, green: 159 / 255, blue: 210 / 255),
            Color(red: 255 / 255, green: 231 / 255, blue: 160 / 255),
            Color(red: 169 / 255, green: 255 / 255, blue: 208 / 255),
            Color(red: 159 / 255, green: 216 / 255, blue: 255 / 255),
            Color(red: 200 / 255, green: 168 / 255, blue: 255 / 255),
            Color(red: 255 / 255, green: 159 / 255, blue: 210 / 255),
        ]
        static let holoMirrored = (0..<10).flatMap { $0.isMultiple(of: 2) ? holo : Array(holo.reversed()) }
        static let finger = Color.white.opacity(0.14)
        static let fingerRing = Color.white.opacity(0.42)
    }

    enum Typeface {
        static let serif = "InstrumentSerif-Regular"
        static let regular = "Inter24pt-Regular"
        static let medium = "Inter-Medium"
        static let semibold = "Inter-SemiBold"
        static let titleSize: CGFloat = 36
        static let titleLine: CGFloat = 44
        static let metaSize: CGFloat = 14
        static let metaLine: CGFloat = 20
        static let pillSize: CGFloat = 15
        static let awardSize: CGFloat = 13
        static let bodySize: CGFloat = 15
        static let bodyLine: CGFloat = 22
        static let moreSize: CGFloat = 14
        static let chipSize: CGFloat = 11
        static let chipLine: CGFloat = 14
        static let posterTitle: CGFloat = 50
        static let posterLine: CGFloat = 46
        static let backTitle: CGFloat = 30
        static let credit: CGFloat = 8
    }

    enum Layout {
        static let gutter: CGFloat = 16
        static let topBar: CGFloat = 4
        static let close: CGFloat = 44
        static let closeIcon: CGFloat = 20
        static let shareIcon: CGFloat = 19
        static let awardHeight: CGFloat = 32
        static let awardHorizontalPadding: CGFloat = 12
        static let awardGap: CGFloat = 6
        static let cardTop: CGFloat = 64
        static let backdropShare: CGFloat = 0.62
        static let cardWidthRatio: CGFloat = 0.60
        static let cardAspect: CGFloat = 1.48
        static let cardRadius: CGFloat = 16
        static let titleGap: CGFloat = 24
        static let infoGap: CGFloat = 4
        static let pillHeight: CGFloat = 52
        static let pillGap: CGFloat = 12
        static let pillHorizontalPadding: CGFloat = 16
        static let pillIcon: CGFloat = 20
        static let pillContentGap: CGFloat = 8
        static let pillsMarginTop: CGFloat = 16
        static let pillsMarginBottom: CGFloat = 16
        static let formatsMarginTop: CGFloat = 16
        static let formatsPaddingTop: CGFloat = 16
        static let chipHeight: CGFloat = 24
        static let chipPadding: CGFloat = 8
        static let chipRadius: CGFloat = 6
        static let titleY: CGFloat = 0.20
        static let fingerRadius: CGFloat = 22
        static let moreGap: CGFloat = 4
        static let cardMargin: CGFloat = 72
        static let backdropBleed: CGFloat = 60
    }

    enum Back {
        static let titleY: CGFloat = 0.26
        static let ruleY: CGFloat = 0.31
        static let rule: CGFloat = 28
        static let creditsY: CGFloat = 0.41
        static let lineStep: CGFloat = 13
    }

    enum Copy {
        struct Credit: Identifiable {
            let id: Int
            let text: String
            let strong: Bool
            let gap: CGFloat
        }

        static let title = "The Long Light"
        static let posterTitle = ["The Long", "Light"]
        static let posterCredit = "A FILM BY ILSE VARGA"
        static let meta = "2026  ·  Drama  ·  1 h 52 min  ·  12"
        static let award = ["3", " festival awards"]
        static let watchlist = "Watchlist"
        static let added = "Added"
        static let play = "Play trailer"
        static let body = "After her father’s funeral, a lighthouse keeper’s daughter walks the old coast path north to scatter his ashes, and finds the people he never mentioned waiting at every stop along the way."
        static let credits = [
            Credit(id: 0, text: "A FILM BY", strong: false, gap: 0),
            Credit(id: 1, text: "ILSE VARGA", strong: true, gap: 0),
            Credit(id: 2, text: "STARRING", strong: false, gap: 10),
            Credit(id: 3, text: "MARA OKAFOR   TEO BRANDT", strong: true, gap: 10),
            Credit(id: 4, text: "Drama · 1 h 52 min", strong: false, gap: 20),
            Credit(id: 5, text: "Northlight Pictures", strong: false, gap: 20),
        ]
        static let formats = ["4K", "HDR", "Dolby Atmos", "AD"]
        static let more = "Show more"
        static let less = "Show less"
        static let posterLabel = "Poster of The Long Light: a walker on a ridge at dusk, a low sun behind the hills"
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
        static let driftPeriod = 10.0
        static let perspective: CGFloat = 420
        static let lift = 1.04
        static let reducedMax = 4 * Double.pi / 180
    }

    enum Parallax {
        static let layers: [CGFloat] = [-40, -30, -14, 10, 26]
        static let title: CGFloat = 44
        static let backdrop: CGFloat = -30
        static let glare: CGFloat = -2.2
        static let foil: CGFloat = 2.6
    }

    enum Flip {
        static let tapDuration = 0.260
        static let slop: CGFloat = 8
        static let tapSpeed: CGFloat = 400
        static let rise = 0.03
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
        static let period = 5.0
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
        static let maxFrameDelta = 0.25
        static let interactionResumeDelay = 2.0
    }

    static let loop: Double = 10

    enum QA {
        enum Hold: Hashable { case rest, a, b }
        static let hold: Hold? = nil
        static let holdAt: [Hold: Double] = [.rest: 0, .a: 2.2, .b: 3.8]
    }

    enum Autoplay {
        enum Target: Equatable { case card, watch }
        struct Tap { let at: Double; let target: Target }

        static let holdStart = 0.6
        static let holdEnd = 4.2
        static let amplitudeX = 1.1
        static let amplitudeY = 0.75
        static let ramp = 0.6
        static let pathCycles = 1.15
        static let taps = [
            Tap(at: 4.9, target: .card),
            Tap(at: 6.8, target: .card),
            Tap(at: 8.0, target: .watch),
            Tap(at: 8.8, target: .watch),
        ]
        static let press = 0.110
        static let fingerLead = 0.280
        static let fingerFade = 0.140
        static let releaseVelocitySample = 0.016
    }

    enum Render {
        static let foilHalfWidth: CGFloat = 46
        static let titleShadowX: CGFloat = 1.2
        static let titleShadowY: CGFloat = 1.8
        static let titleLightX: CGFloat = -0.6
        static let titleLightY: CGFloat = -0.8
        static let glareRestOpacity = 0.10
        static let glareRange = 0.30
        static let glareGain = 0.9
        static let glareRadiusRatio: CGFloat = 0.9
        static let glareRestY: CGFloat = 0.3
        static let shadowXGain: CGFloat = -60
        static let shadowRestY: CGFloat = 22
        static let shadowYGain: CGFloat = -40
        static let shadowScaleX: CGFloat = 0.9
        static let shadowScaleY: CGFloat = 0.92
        static let shadowEdgeMinimum: CGFloat = 0.3
        static let shadowBaseOpacity = 0.6
        static let shadowLiftGain = 2.0
        static let shadowBlur: CGFloat = 24
        static let glowXGain: CGFloat = 40
        static let glowRestY: CGFloat = 30
        static let glowYGain: CGFloat = 30
        static let glowInsetXRatio: CGFloat = 0.08
        static let glowInsetYRatio: CGFloat = 0.20
        static let glowWidthRatio: CGFloat = 0.84
        static let glowHeightRatio: CGFloat = 0.78
        static let glowBlur: CGFloat = 40
        static let glowLiftGain = 0.6
        static let glowDeadZone = 2 * Double.pi / 180
        static let glowTiltGain = 1.2
        static let rimWidth: CGFloat = 1
        static let fingerRingWidth: CGFloat = 1
    }
}
