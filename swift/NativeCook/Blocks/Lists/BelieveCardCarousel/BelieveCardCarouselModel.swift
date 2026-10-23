import SwiftUI

enum BelieveCardCarouselPalette {
    static let background = Color(red: 0xFC / 255, green: 0xFC / 255, blue: 0xFC / 255)
    static let ink = Color(red: 0x11 / 255, green: 0x11 / 255, blue: 0x11 / 255)
    static let muted = Color(red: 0x70 / 255, green: 0x70 / 255, blue: 0x70 / 255)
    static let meta = Color(red: 0x55 / 255, green: 0x55 / 255, blue: 0x55 / 255)
    static let primary = Color(red: 0x00 / 255, green: 0xCC / 255, blue: 0x46 / 255)
    static let secondary = Color(red: 0xF7 / 255, green: 0xF7 / 255, blue: 0xF7 / 255)
    static let legal = Color(red: 0x7A / 255, green: 0x7A / 255, blue: 0x7A / 255)
    static let splashMark = Color(red: 0xE2 / 255, green: 0xE2 / 255, blue: 0xE2 / 255)
    static let card = Color.white
}

enum BelieveCardCarouselMetrics {
    enum Title {
        static let top = 140.0
        static let height = 112.0
        static let size = 37.7
        static let line1 = 51.7
        static let line2 = 94.0
    }

    enum Card {
        static let width = 214.0
        static let height = 385.0
        static let radius = 22.0
        static let inset = 5.0
        static let imageWidth = width - 2 * inset
        static let imageHeight = 230.0
        static let imageRadius = 18.0
        static let icon = 125.0
        static let glowRadius = 96.0
        static let glowBlur = 26.0
        static let iconRadius = 30.0
        static let padding = 17.0
        static let believeTop = 252.6
        static let nameTop = 276.3
        static let dateTop = 323.7
        static let byTop = 350.7
    }

    enum Row {
        static let topCTA = 139.6
        static let shiftTitle = 185.0
        static let titleTravel = 0.5
        static let x = [-34.0, 180.4, 389.3]
        static let y = [15.6, 0.0, 29.4]
        static let scale = [0.985, 1.0, 1.0]
        static let rotation = [2.5, -3.0, 1.7]
        static let iconShift = [35.0, 6.0, 15.0]
        static let textShift = [45.0, 0.0, -10.0]
        static let textY = [-4.0, 0.0, 4.0]

        enum Picture {
            static let x = 2.0
            static let y = 6.2
            static let perspective = 800.0
            static let rotation = -2.6
            static let rotationX = -19.0
            static let rotationY = -11.0
            static let skewX = -5.5
            static let scale = 1.08
        }
    }

    enum CTA {
        static let top = 652.6
        static let height = 56.0
        static let side = 25.0
        static let gap = 13.4
        static let legalTop = 803.0
        static let legalSize = 11.3
        static let legalLineHeight = 13.3
    }

    enum Splash {
        static let width = 90.0
        static let height = 105.0
        static let centerY = 435.0
    }
}

enum BelieveCardCarouselMotionSpec {
    static let loopMilliseconds = 4_630.0
    static let loop = loopMilliseconds / 1_000
    static let resumeMilliseconds = 3_500.0
    static let holdMilliseconds = 3_700.0

    static let intro = 1_600.0
    static let markOut = 1_480.0
    static let cards = [1_870.0, 2_035.0, 2_146.0]
    static let morph = 2_420.0
    static let cta = 3_140.0
    static let titleOut = 3_050.0
    static let buttons = 3_110.0

    static let markOutDuration = 150.0
    static let titleInDuration = 140.0
    static let dropAt = 1_660.0
    static let dropDuration = 100.0
    static let drop1 = 8.7
    static let drop2 = 18.0

    static let riseGrow = 0.12
    static let riseFrom = 260.0
    static let riseOmega = 17.3
    static let riseZeta = 0.81
    static let riseFadeDuration = 220.0

    static let morphDuration = 220.0
    static let morphBlur = 6.0

    static let liftStagger = [-37.0, 0.0, 39.0]
    static let liftOmega = 20.0
    static let liftZeta = 0.7
    static let titleFadeDuration = 140.0
    static let buttonsFrom = 34.0
    static let buttonsFadeDuration = 60.0

    static let pitch = 225.0
    static let snapDuration = 0.4
    static let snapDampingRatio = 0.8
    static let velocityProjection = 0.15
    static let rubberBand = 0.3
    static let pressScale = 0.97
    static let pressDuration = 0.12
    static let reducedFadeDuration = 0.2
}

struct BelieveCardCarouselCardData: Identifiable {
    enum Artwork: Equatable {
        case mark
        case figo
        case loop
    }

    let id: String
    let name: String
    let date: String
    let maker: String
    let hasAvatar: Bool
    let artwork: Artwork
    let imageColors: [Color]
}

let believeCardCarouselCards = [
    BelieveCardCarouselCardData(
        id: "loop",
        name: "Halo",
        date: "12 Apr 25",
        maker: "Ira Koshal",
        hasAvatar: false,
        artwork: .loop,
        imageColors: [Color(hex: 0xB0CAD0), Color(hex: 0xB4CFD4), Color(hex: 0xADC7CB), Color(hex: 0xC6E2E8)]
    ),
    BelieveCardCarouselCardData(
        id: "beehive",
        name: "Beehive",
        date: "16 Oct 25",
        maker: "Beehive",
        hasAvatar: false,
        artwork: .mark,
        imageColors: [Color(hex: 0x83F2B2), Color(hex: 0xA6EEBF), Color(hex: 0x74E69B), Color(hex: 0xE4FBEB)]
    ),
    BelieveCardCarouselCardData(
        id: "figo",
        name: "aro",
        date: "29 May 25",
        maker: "Anita Dhaliya",
        hasAvatar: true,
        artwork: .figo,
        imageColors: [Color(hex: 0x79B3C8), Color(hex: 0x78BBD1), Color(hex: 0x7EB3C9), Color(hex: 0xCFCBAF)]
    ),
]

struct BelieveCardCarouselBeat {
    let mark: Double
    let titleIn: Double
    let line1Drop: Double
    let line2Drop: Double
    let morph: Double
    let lift: Double
    let titleOut: Double
    let buttonsIn: Double
    let buttonsY: Double
}

struct BelieveCardCarouselCardBeat {
    let rise: Double
    let scale: Double
    let shown: Double
    let lift: Double
}

enum BelieveCardCarouselTimeline {
    static func beat(at time: Double) -> BelieveCardCarouselBeat {
        BelieveCardCarouselBeat(
            mark: 1 - ramp(time, BelieveCardCarouselMotionSpec.markOut, BelieveCardCarouselMotionSpec.markOutDuration),
            titleIn: ramp(time, BelieveCardCarouselMotionSpec.intro, BelieveCardCarouselMotionSpec.titleInDuration),
            line1Drop: -BelieveCardCarouselMotionSpec.drop1 * (
                1 - easeOut((time - BelieveCardCarouselMotionSpec.dropAt) / BelieveCardCarouselMotionSpec.dropDuration)
            ),
            line2Drop: -BelieveCardCarouselMotionSpec.drop2 * (
                1 - easeOut((time - BelieveCardCarouselMotionSpec.dropAt) / BelieveCardCarouselMotionSpec.dropDuration)
            ),
            morph: ramp(time, BelieveCardCarouselMotionSpec.morph, BelieveCardCarouselMotionSpec.morphDuration),
            lift: 1 - spring(
                time,
                BelieveCardCarouselMotionSpec.cta,
                BelieveCardCarouselMotionSpec.liftOmega,
                BelieveCardCarouselMotionSpec.liftZeta
            ),
            titleOut: 1 - ramp(
                time,
                BelieveCardCarouselMotionSpec.titleOut,
                BelieveCardCarouselMotionSpec.titleFadeDuration
            ),
            buttonsIn: ramp(
                time,
                BelieveCardCarouselMotionSpec.buttons,
                BelieveCardCarouselMotionSpec.buttonsFadeDuration
            ),
            buttonsY: BelieveCardCarouselMotionSpec.buttonsFrom * spring(
                time,
                BelieveCardCarouselMotionSpec.buttons,
                BelieveCardCarouselMotionSpec.liftOmega,
                BelieveCardCarouselMotionSpec.liftZeta
            )
        )
    }

    static func card(at time: Double, index: Int) -> BelieveCardCarouselCardBeat {
        let cue = BelieveCardCarouselMotionSpec.cards[index]
        let rest = spring(time, cue, BelieveCardCarouselMotionSpec.riseOmega, BelieveCardCarouselMotionSpec.riseZeta)
        return BelieveCardCarouselCardBeat(
            rise: BelieveCardCarouselMotionSpec.riseFrom * rest,
            scale: 1 - BelieveCardCarouselMotionSpec.riseGrow * rest,
            shown: pow(clamp01((time - cue) / BelieveCardCarouselMotionSpec.riseFadeDuration), 1.5),
            lift: 1 - spring(
                time,
                BelieveCardCarouselMotionSpec.cta + BelieveCardCarouselMotionSpec.liftStagger[index],
                BelieveCardCarouselMotionSpec.liftOmega,
                BelieveCardCarouselMotionSpec.liftZeta
            )
        )
    }

    static func interpolate(_ value: Double, input: [Double], output: [Double], clamp: Bool) -> Double {
        let sample = clamp ? min(max(value, input[0]), input[2]) : value
        let lower = sample <= input[1] ? 0 : 1
        let progress = (sample - input[lower]) / (input[lower + 1] - input[lower])
        return output[lower] + (output[lower + 1] - output[lower]) * progress
    }

    static func snapProgress(
        elapsed: Double,
        duration: Double,
        dampingRatio: Double,
        velocity: Double,
        distance: Double
    ) -> Double {
        guard elapsed > 0 else { return 0 }
        guard elapsed < duration else { return 1 }
        let zeta = min(max(dampingRatio, 0.01), 0.999)
        let omega = 4 / (zeta * duration)
        let damped = omega * sqrt(1 - zeta * zeta)
        let normalizedVelocity = abs(distance) > 0.0001 ? velocity / distance : 0

        func raw(_ time: Double) -> Double {
            let decay = exp(-zeta * omega * time)
            let coefficient = (zeta * omega - normalizedVelocity) / damped
            return 1 - decay * (cos(damped * time) + coefficient * sin(damped * time))
        }

        let end = raw(duration)
        return abs(end) > 0.0001 ? raw(elapsed) / end : elapsed / duration
    }

    private static func clamp01(_ value: Double) -> Double {
        min(max(value, 0), 1)
    }

    private static func ease(_ value: Double) -> Double {
        let x = clamp01(value)
        return x < 0.5 ? 2 * x * x : 1 - pow(-2 * x + 2, 2) / 2
    }

    private static func ramp(_ time: Double, _ cue: Double, _ duration: Double) -> Double {
        ease((time - cue) / duration)
    }

    private static func easeOut(_ value: Double) -> Double {
        let x = clamp01(value)
        return 1 - pow(1 - x, 3)
    }

    private static func spring(_ time: Double, _ cue: Double, _ omega: Double, _ zeta: Double) -> Double {
        let elapsed = (time - cue) / 1_000
        guard elapsed > 0 else { return 1 }
        let damped = omega * sqrt(1 - zeta * zeta)
        return exp(-zeta * omega * elapsed) * (
            cos(damped * elapsed) + (zeta * omega / damped) * sin(damped * elapsed)
        )
    }
}

private extension Color {
    init(hex: Int) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}
