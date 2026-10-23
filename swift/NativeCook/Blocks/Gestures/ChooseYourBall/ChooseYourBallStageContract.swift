import simd

// The numbers of the 3D scene: camera, lights, materials and which raised markings a ball wears.
// Mirrors BallStage3D.contract.ts and ballFilamentSurface.ts.
//
// The reference renders with Filament and its "ACES legacy" tone mapper; this stage renders with
// RealityKit, whose exposure and tone curve cannot be set on iOS 17. Camera, light positions,
// colours and the parts of the model are the reference's own numbers. Where a number differs,
// it is the reference's value carried across the two renderers, measured on screen against the
// reference (same simulator, same pixels), and the comment next to it gives both sides.

enum ChooseYourBallStageContract {
    // MARK: Camera

    static let cameraDistance: Float = 5.9
    static let cameraFieldOfView: Float = 26
    static let cameraNear: Float = 0.1
    static let cameraFar: Float = 100
    /// The orbit the free look reaches at full tilt, in degrees above or below level.
    static let cameraTiltDegrees: Float = 25

    /// Where the camera stands for a tilt of -1...1; it always looks at the ball's centre.
    static func cameraPosition(tilt: Float) -> SIMD3<Float> {
        let angle = max(-1, min(1, tilt)) * cameraTiltDegrees * .pi / 180
        return [0, sin(angle) * cameraDistance, cos(angle) * cameraDistance]
    }

    // MARK: Parts of the model

    /// The eight meshes of choose-your-ball.usdz, by name.
    enum Part: String, CaseIterable {
        case upper = "shell_upper"
        case lower = "shell_lower"
        case band
        case button
        case markGreat = "mark_great"
        case markUltra = "mark_ultra"
        case markMasterPatch = "mark_master_patch"
        case markMasterEmblem = "mark_master_emblem"
    }

    /// Raised markings modelled as their own meshes, with the paint each one takes.
    ///
    /// Reference paints: great #850007, master emblem #FFFFFF, master patch #EB0997, ultra
    /// #AAA000. Under Filament's tone mapper those show darker and less saturated than they are
    /// painted (the emblem and the patch also sit in baked shadow). RealityKit's curve is close
    /// to linear there, so the same paint would show too strong; these are the paints that show
    /// the reference's colours, worked out like the shell textures (see ChooseYourBallStage).
    static let markPaint: [Part: UInt32] = [
        .markGreat: 0x670C16,
        .markMasterEmblem: 0xF2F2F0,
        .markMasterPatch: 0xBE1D78,
        .markUltra: 0x9E9426,
    ]

    /// The markings a ball shows; every other ball's are hidden.
    static func marks(for ball: ChooseYourBallID) -> [Part] {
        switch ball {
        case .great: [.markGreat]
        case .master: [.markMasterPatch, .markMasterEmblem]
        case .ultra: [.markUltra]
        default: []
        }
    }

    /// How far a marking is lifted off the shell so the two never fight for the same depth.
    static func markLift(_ part: Part) -> Float {
        part == .markMasterEmblem ? 1.02 : 1.006
    }

    // MARK: Lights

    /// The room's light. Reference: Filament image-based light at 30 000 with its default camera
    /// exposure. RealityKit has no such unit, only a power of two on the room as loaded: at 0
    /// even a pure white shell shows 190 where the reference's shows 224; at 1 the reference's
    /// own #E7E7E7 shows that 224, and its white dome 205 as it does there.
    static let environmentExponent: Float = 1
    /// Reflectance. RealityKit's default 0.5 is the reference's (4% at normal incidence), but
    /// the stop added above doubles every reflection with the light, so it is halved here: a
    /// black dome then shows the room as faintly as the reference's does (13 against 11 of 255).
    static let specular: Float = 0.25

    /// The reference's lights are Filament lux and lumens, shown through its camera's exposure
    /// (f/16, 1/125 s, ISO 100, i.e. 1/38 400) and 1.15 on top: a white surface under E lux
    /// shows as E × 0.00003 / π. RealityKit shows it as about E / 1000π (measured), so one
    /// Filament lux is 0.03 RealityKit lux.
    private static let lightScale: Float = 0.03

    /// Where the two directional lights shine from; both aim at the ball.
    static let keyPosition: SIMD3<Float> = [-2.4, 3.2, 3.4]
    static let keyLux: Float = 22_000 * lightScale
    static let fillPosition: SIMD3<Float> = [2.6, -1.4, 1.8]
    static let fillLux: Float = 3_500 * lightScale
    static let fillColor: UInt32 = 0x8FA6C4
    /// Two point lights behind the ball, in the ball's own aura colour.
    static let rimPosition: SIMD3<Float> = [2.1, -0.9, -1.7]
    static let rimLumens: Float = 9_000 * lightScale
    static let rimBackPosition: SIMD3<Float> = [-2.1, 0.7, -1.8]
    static let rimBackLumens: Float = 5_500 * lightScale
    static let rimFalloffRadius: Float = 14

    // MARK: Materials

    /// Reference: roughness 0 on the two shells and the button, which Filament raises to its
    /// own floor of 0.089. RealityKit blurs the room more than Filament at the same number (the
    /// lamps become blobs where the reference has thin streaks), so the shells sit a little
    /// under that floor: thin streaks, and the key light's glint still wide enough to see.
    static let shellRoughness: Float = 0.05
    /// The band and the markings are the reference's values unchanged.
    static let bandRoughness: Float = 0.14
    static let markRoughness: Float = 0.1
    /// The painted shell is one tenth metal, the markings a little less, the emblem not at all.
    static let shellMetallic: Float = 0.1
    static let markMetallic: Float = 0.07

    /// The lower shell and the button. Reference: the linear factor (1.16, 1.32, 1.65), which
    /// is #E7E7E7 corrected for what Filament's tone mapper does to a white under this warm
    /// room; on screen the part is a near-uniform 224. RealityKit shows plain #E7E7E7 as that
    /// same 224 at the front, but without the reference's roll-off the shell runs from 210 to
    /// 234 across. So half of the white is lit by the room (linear 0.4, #AAAAAA) and the rest
    /// is given off evenly, which brings the spread down to the reference's few levels.
    static let hardwareLit: UInt32 = 0xAAAAAA
    static let hardwareGlow: Float = 0.55
}
