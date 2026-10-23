import Foundation

/// Immutable values ported from the React Native pull-cord theme.
///
/// Dimensions are points on a 402 pt-wide stage and are scaled by the view as
/// needed. Colour strings intentionally preserve the source CSS literals so the
/// view's colour parser can apply the same alpha values.
enum PullCordThemeTokens {
    enum Dark {
        // RN theme.ts:32
        static let wallTop: String = "#0B0C0E"
        // RN theme.ts:33
        static let wallBottom: String = "#0D1116"
        // RN theme.ts:34
        static let panelShadow: String = "#030405"
        // RN theme.ts:35
        static let panelLight: String = "#3C7EF2"
        // RN theme.ts:36
        static let spill: String = "rgba(36, 104, 230, 0.34)"
        // RN theme.ts:37
        static let spillFar: String = "rgba(36, 104, 230, 0)"
        // RN theme.ts:38
        static let halo: String = "rgba(52, 132, 255, 0.55)"
        // RN theme.ts:39
        static let glassTint: String = "rgba(40, 112, 255, 0.22)"
        // RN theme.ts:40
        static let glassEdge: String = "rgba(120, 186, 255, 0.55)"
        // RN theme.ts:41
        static let glassEdgeLit: String = "#BFE0FF"
        // RN theme.ts:42
        static let glassSpecular: String = "rgba(235, 246, 255, 0.85)"
        // RN theme.ts:43
        static let filamentCore: String = "#E4F3FF"
        // RN theme.ts:44
        static let filamentGlow: String = "#2F86FF"
        // RN theme.ts:45
        static let support: String = "rgba(170, 210, 255, 0.35)"
        // RN theme.ts:46
        static let wire: String = "#30343A"
        // RN theme.ts:47
        static let metal: [String] = ["#2A2D31", "#7D838C", "#E9EDF2", "#9AA0A9", "#3A3E44", "#1C1E21"]
        // RN theme.ts:48
        static let metalStops: [Double] = [0, 0.22, 0.42, 0.62, 0.84, 1]
        // RN theme.ts:49
        static let thread: String = "rgba(0, 0, 0, 0.55)"
        // RN theme.ts:50
        static let cord: String = "#3A414B"
        // RN theme.ts:51
        static let cordLit: String = "rgba(110, 170, 255, 0.55)"
        // RN theme.ts:52
        static let brass: [String] = ["#2E2414", "#7A5E2E", "#D6B676", "#8E6C34", "#2A2012"]
        // RN theme.ts:53
        static let brassStops: [Double] = [0, 0.25, 0.45, 0.7, 1]
        // RN theme.ts:54
        static let ink: String = "#EEF3FA"
        // RN theme.ts:55
        static let mute: String = "rgba(238, 243, 250, 0.56)"
    }

    enum Light {
        // RN theme.ts:59
        static let wallTop: String = "#F3EEE6"
        // RN theme.ts:60
        static let wallBottom: String = "#E7DFD2"
        // RN theme.ts:61
        static let panelShadow: String = "#CBBFAE"
        // RN theme.ts:62
        static let panelLight: String = "#FFFFFF"
        // RN theme.ts:63
        static let spill: String = "rgba(255, 196, 110, 0.5)"
        // RN theme.ts:64
        static let spillFar: String = "rgba(255, 196, 110, 0)"
        // RN theme.ts:65
        static let halo: String = "rgba(255, 184, 92, 0.6)"
        // RN theme.ts:66
        static let glassTint: String = "rgba(255, 228, 176, 0.3)"
        // RN theme.ts:67
        static let glassEdge: String = "rgba(120, 104, 82, 0.5)"
        // RN theme.ts:68
        static let glassEdgeLit: String = "#FFFFFF"
        // RN theme.ts:69
        static let glassSpecular: String = "rgba(255, 255, 255, 0.95)"
        // RN theme.ts:70
        static let filamentCore: String = "#FFF8E6"
        // RN theme.ts:71
        static let filamentGlow: String = "#FFA733"
        // RN theme.ts:72
        static let support: String = "rgba(120, 96, 60, 0.35)"
        // RN theme.ts:73
        static let wire: String = "#2F2A25"
        // RN theme.ts:74
        static let metal: [String] = ["#4A4D52", "#9DA2AA", "#F6F7F9", "#B4B9C0", "#5A5E64", "#3A3C40"]
        // RN theme.ts:75
        static let metalStops: [Double] = [0, 0.22, 0.42, 0.62, 0.84, 1]
        // RN theme.ts:76
        static let thread: String = "rgba(40, 32, 20, 0.45)"
        // RN theme.ts:77
        static let cord: String = "#3A332B"
        // RN theme.ts:78
        static let cordLit: String = "rgba(255, 255, 255, 0.5)"
        // RN theme.ts:79
        static let brass: [String] = ["#5E461E", "#B48A42", "#F6DFA4", "#C29A52", "#5A4320"]
        // RN theme.ts:80
        static let brassStops: [Double] = [0, 0.25, 0.45, 0.7, 1]
        // RN theme.ts:81
        static let ink: String = "#1D1A16"
        // RN theme.ts:82
        static let mute: String = "rgba(29, 26, 22, 0.58)"
    }

    enum Liquid {
        // RN theme.ts:87
        static let top: String = "rgba(255, 249, 232, 0.96)"
        // RN theme.ts:88
        static let bottom: String = "rgba(255, 186, 92, 0.92)"
        // RN theme.ts:89
        static let meniscus: String = "#FFFFFF"
        // RN theme.ts:90
        static let lightAlpha: Double = 0.5
        // RN theme.ts:91
        static let glow: String = "#FFA733"
        // RN theme.ts:92
        static let filament: String = "#FFC261"
        // RN theme.ts:93
        static let core: String = "#FFF8E6"
        // RN theme.ts:94
        static let spill: String = "rgba(255, 190, 100, 0.42)"
        // RN theme.ts:95
        static let spillFar: String = "rgba(255, 190, 100, 0)"
        // RN theme.ts:96
        static let halo: String = "rgba(255, 184, 92, 0.6)"
        // RN theme.ts:97
        static let haloFar: String = "rgba(255, 184, 92, 0)"
    }

    enum Space {
        // RN theme.ts:107
        static let xs: Double = 4
        static let sm: Double = 8
        static let md: Double = 12
        static let lg: Double = 16
        static let xl: Double = 24
        static let xxl: Double = 32
    }

    enum Bulb {
        // RN theme.ts:104
        static let socketTopRatio: Double = 0.265
        // RN theme.ts:105
        static let wire: Double = 700
        // RN theme.ts:106
        static let socketWidth: Double = 56
        // RN theme.ts:107
        static let socketHeight: Double = 50
        // RN theme.ts:108
        static let capWidth: Double = 40
        // RN theme.ts:109
        static let capHeight: Double = 6
        // RN theme.ts:110
        static let threads: Double = 5
        // RN theme.ts:111
        static let neckTop: Double = 46
        // RN theme.ts:112
        static let neckHalf: Double = 25
        // RN theme.ts:113
        static let radius: Double = 68.5
        // RN theme.ts:114
        static let centerY: Double = 156.5
        // RN theme.ts:115
        static let bottom: Double = 225
        // RN theme.ts:116
        static let stemHalf: Double = 8
        // RN theme.ts:117
        static let stemEnd: Double = 108
        // RN theme.ts:118
        static let armHalf: Double = 30
        // RN theme.ts:119
        static let foot: Double = 146
        // RN theme.ts:120
        static let wireWidth: Double = 2.6
        // RN theme.ts:121
        static let halo: Double = 170
    }

    enum Cord {
        // RN theme.ts:132
        static let xRatio: Double = 1 - PullCordThemeTokens.Space.xxl / PullCordThemeTokens.stageWidth
        // RN theme.ts:126
        static let top: Double = -14
        // RN theme.ts:127
        static let restYRatio: Double = 0.56
        // RN theme.ts:128
        static let points: Double = 18
        // RN theme.ts:129
        static let width: Double = 2.6
        // RN theme.ts:130
        static let beadWidth: Double = 15
        // RN theme.ts:131
        static let beadHeight: Double = 36
        // RN theme.ts:132
        static let beadRadius: Double = 7.5
        // RN theme.ts:133
        static let crimpWidth: Double = 7
        // RN theme.ts:134
        static let crimpHeight: Double = 6
        // RN theme.ts:135
        static let hit: Double = 56
    }

    enum Glow {
        // RN theme.ts:140
        static let wide: Double = 12
        // RN theme.ts:141
        static let wideBlur: Double = 10
        // RN theme.ts:142
        static let wideAlpha: Double = 0.6
        // RN theme.ts:143
        static let tight: Double = 5
        // RN theme.ts:144
        static let tightBlur: Double = 3
        // RN theme.ts:145
        static let core: Double = 2.2
        // RN theme.ts:146
        static let innerAlpha: Double = 0.5
    }

    enum Panel {
        // RN theme.ts:150
        static let insetRatio: Double = 0.125
        // RN theme.ts:151
        static let bottomFromEnd: Double = 36
        // RN theme.ts:152
        static let bevel: Double = 3
        // RN theme.ts:153
        static let line: Double = 1.5
        // RN theme.ts:154
        static let lip: Double = 4
        // RN theme.ts:155
        static let lipBlur: Double = 3
    }

    enum Typography {
        // RN theme.ts:166
        static let margin: Double = Space.xxl
        // RN theme.ts:167
        static let bottom: Double = Space.xxl + Space.lg
        // RN theme.ts:161
        static let eyebrowSize: Double = 12
        // RN theme.ts:162
        static let eyebrowTracking: Double = 1.8
        // RN theme.ts:163
        static let titleSize: Double = 48
        // RN theme.ts:164
        static let titleFamily: String = "Georgia"
        // RN theme.ts:165
        static let titleLine: Double = 54
        // RN theme.ts:166
        static let hintSize: Double = 15
        // RN theme.ts:167
        static let hintLine: Double = 20
        // RN theme.ts:175
        static let gap: Double = Space.sm
    }

    enum Copy {
        // RN theme.ts:172
        static let eyebrow: String = "APPEARANCE"
        // RN theme.ts:173
        static let dark: String = "Dark"
        // RN theme.ts:174
        static let light: String = "Light"
        // RN theme.ts:175
        static let hint: String = "Pull the cord to switch"
        // RN theme.ts:176
        static let a11yLabel: String = "Light cord"
        // RN theme.ts:177
        static let a11yHint: String = "Switches the app between dark and light theme"
    }

    // RN theme.ts:182
    static let replica: Bool = false

    enum Fit {
        // RN theme.ts:183
        static let k: Double = 2.048 / 3.0
        // RN theme.ts:183
        static let cropX: Double = -134.0 / 3.0
    }

    enum ReplicaArt {
        // RN theme.ts:194
        static let wallX: Double = (58.0 - 148.0 * 1.2) / 3.0
        // RN theme.ts:195
        static let wallW: Double = (1206.0 * 1.2) / 3.0
        // RN theme.ts:198
        static let spriteScale: Double = 1.26 / 3.0
        // RN theme.ts:199
        static let spriteW: Double = 480
        // RN theme.ts:200
        static let spriteH: Double = 720
        // RN theme.ts:201
        static let anchorX: Double = 240
        // RN theme.ts:202
        static let anchorY: Double = 30
        // RN theme.ts:203
        static let socketTopSrc: Double = 370.6
        // RN theme.ts:204
        static let glassCenterSrc: Double = 205
        // RN theme.ts:205
        static let swingSrc: Double = 82
        // RN theme.ts:206
        static let centreSrc: Double = 367.9
        // RN theme.ts:207
        static let halo: Double = 170
        // RN theme.ts:208
        static let haloColor: String = "rgba(40, 120, 240, 0.12)"
        // RN theme.ts:209
        static let spill: String = "rgba(20, 90, 255, 0.24)"
        // RN theme.ts:210
        static let spillR: Double = 0.8
        // RN theme.ts:211-212
        static let spillFall: [String] = [
            "rgba(30, 95, 235, 0.2)",
            "rgba(30, 95, 235, 0.1)",
            "rgba(30, 95, 235, 0)"
        ]
        static let spillStops: [Double] = [0, 0.55, 1]
        // RN theme.ts:213-214
        static let ambient: [String] = [
            "rgba(40, 90, 210, 0.05)",
            "rgba(40, 90, 210, 0.025)"
        ]
        static let ambientR: Double = 1.6
        // RN theme.ts:215
        static let grabRadius: Double = 110
        // RN theme.ts:216
        static let wire: Double = 2000
        // RN theme.ts:217
        static let wireColor: String = "#2E3136"
        // RN theme.ts:218
        static let wireWidth: Double = 6
        // RN theme.ts:222
        static let interior: [Double] = [1.12, 0, 0, 0, -0.05, 0, 1.12, 0, 0, -0.05, 0, 0, 1.12, 0, -0.05, 0, 0, 0, 1, 0]
        // RN theme.ts:224
        static let brightPass: [Double] = [0.25, 0, 0, 0, -0.12, 0, 1, 0, 0, -0.5, 0, 0, 2, 0, -1, 0, 0, 0, 1, 0]
        // RN theme.ts:225
        static let bloom: Double = 10
        // RN theme.ts:226
        static let bloomOpacity: Double = 0.55
        // RN theme.ts:228-229
        static let lightMap: Double = 1
        static let wallTint: [Double] = [0.48, 0, 0, 0, 0, 0, 0.6, 0, 0, 0, 0, 0, 0.6, 0, 0, 0, 0, 0, 1, 0]
        // RN theme.ts:232
        static let stileLeft: [Double] = [28, 78]
        // RN theme.ts:233
        static let stileRight: [Double] = [1128, 1182]
        // RN theme.ts:234
        static let stileGlow: [Double] = [850, 1300, 1800]
        // RN theme.ts:235
        static let stileColor: String = "rgba(36, 104, 210, 0.85)"
        // RN theme.ts:236
        static let railY: [Double] = [2498, 2524]
        // RN theme.ts:237
        static let railColor: String = "rgba(18, 52, 120, 0.3)"
    }

    // RN theme.ts:100
    static let stageWidth: Double = 402
}

/// Motion values ported from the React Native pull-cord theme.
/// Times are seconds unless the name ends in `MS`; distances are points.
enum PullCordThemeMotion {
    // RN motion.ts:3
    static let step: Double = 1.0 / 120.0
    // RN motion.ts:4
    static let maxSteps: Double = 30

    enum Rope {
        // RN motion.ts:7
        static let gravity: Double = 2600
        // RN motion.ts:8
        static let damping: Double = 0.988
        // RN motion.ts:9
        static let passes: Double = 10
        // RN motion.ts:10
        static let beadWeight: Double = 0.25
        // RN motion.ts:11
        static let sideReach: Double = 72
        // RN motion.ts:12
        static let liftReach: Double = 90
        // RN motion.ts:13
        static let releaseSpeed: Double = 1800
        // RN motion.ts:14
        static let snapRest: Double = 1.5
        // RN motion.ts:15
        static let settleSteps: Double = 600
    }

    enum Switch {
        // RN motion.ts:19
        static let linear: Double = 36
        // RN motion.ts:20
        static let travel: Double = 66
        // RN motion.ts:21
        static let detent: Double = 44
        // RN motion.ts:22
        static let rearm: Double = 20
        // RN motion.ts:23
        static let stiffness: Double = 300
        // RN motion.ts:24
        static let damping: Double = 26
        // RN motion.ts:25
        static let reducedDamping: Double = 46
    }

    enum BulbMotion {
        // RN motion.ts:29
        static let period: Double = 6
        // RN motion.ts:30
        static let idleAmp: Double = 14
        // RN motion.ts:31
        static let reducedIdleAmp: Double = 0
        // RN motion.ts:32
        static let damping: Double = 0.3
        // RN motion.ts:33
        static let maxThrow: Double = 0.02
        // RN motion.ts:34
        static let maxAngle: Double = 0.025
    }

    enum Reveal {
        // RN motion.ts:38
        static let toLight: Double = 0.72
        // RN motion.ts:39
        static let toDark: Double = 0.62
        // RN motion.ts:40
        static let reducedFade: Double = 0.2
        // RN motion.ts:41
        static let margin: Double = 40
        // RN motion.ts:42
        static let textBand: Double = 70
    }

    enum Level {
        // RN motion.ts:46
        static let follow: Double = 16
        // RN motion.ts:47
        static let fill: Double = 10
    }

    enum Ignite {
        // RN motion.ts:53
        static let times: [Double] = [0, 0.05, 0.11, 0.19, 0.26, 0.36, 0.45, 0.6]
        // RN motion.ts:54
        static let values: [Double] = [0.1, 0.8, 0.15, 0.9, 0.3, 1, 0.6, 1]
    }

    enum Haptic {
        // RN motion.ts:57
        static let grab: Double = 1
        // RN motion.ts:57
        static let detent: Double = 2
        // RN motion.ts:57
        static let home: Double = 3
        // RN motion.ts:57
        static let tick: Double = 4
    }

    enum Feel {
        // RN motion.ts:61
        static let tick: Double = 12
        // RN motion.ts:62
        static let flickSpeed: Double = 900
        // RN motion.ts:63
        static let flickMin: Double = 24
        // RN motion.ts:64
        static let kick: Double = 0.005
        // RN motion.ts:65
        static let preview: Double = 0.2
    }

    enum Hero {
        // RN motion.ts:68
        static let ember: Double = 0.9
        // RN motion.ts:69
        static let flickers: [Double] = [0.12, 0.22, 0.34, 0.42]
        // RN motion.ts:70
        static let flickerDepth: Double = 0.55
        // RN motion.ts:71
        static let flash: Double = 0.28
        // RN motion.ts:72
        static let flashPeak: Double = 0.32
        // RN motion.ts:73
        static let edge: Double = 34
        // RN motion.ts:74
        static let stutter: [Double] = [0.05, 0.11, 0.19, 0.26, 0.36, 0.45]
        // RN motion.ts:76
        static let spec: Double = 26
        // RN motion.ts:78
        static let tilt: Double = 0.05
        // RN motion.ts:79
        static let motes: Double = 26
        // RN motion.ts:80
        static let roll: Double = 0.42

        enum Hum {
            // RN motion.ts:75
            static let period: Double = 3
            // RN motion.ts:75
            static let low: Double = 0.92
        }

        enum Shadow {
            // RN motion.ts:77
            static let dx: Double = 26
            // RN motion.ts:77
            static let dy: Double = 34
            // RN motion.ts:77
            static let alpha: Double = 0.2
            // RN motion.ts:77
            static let blur: Double = 10
        }
    }

    // RN motion.ts:107
    enum ReplicaIgnite {
        // RN motion.ts:107
        static let times: [Double] = [0, 0.25, 0.5, 0.75]
        // RN motion.ts:107
        static let values: [Double] = [0.08, 0.5, 0.5, 1]
    }

    enum Tap {
        // RN motion.ts:102
        static let slop: Double = 8
        // RN motion.ts:102
        static let time: Double = 0.25
    }

    // RN motion.ts:84
    static let loopMS: Double = 12_000
    // RN motion.ts:85
    static let resumeMS: Double = 3_500
    // RN motion.ts:86
    static let lightRestAt: Double = 5
    // RN motion.ts:87-95
    static let script: [[Double]] = [
        [1.2, 2.0, 0, 0, -6, 30, 1, 0],
        [2.0, 2.35, -6, 30, -4, 18, 0, 0],
        [2.35, 2.8, -4, 18, 4, 64, 0, 0],
        [2.8, 2.95, 4, 64, 4, 64, 0, 1],
        [7.2, 7.75, 0, 0, 8, 60, 1, 0],
        [7.75, 7.9, 8, 60, 8, 60, 0, 1]
    ]
    // RN motion.ts:98-101
    static let tapPull: [[Double]] = [
        [0, 0.3, 0, 0, 2, 60, 1, 0],
        [0.3, 0.4, 2, 60, 2, 60, 0, 1]
    ]
    // RN motion.ts:106
    static let replicaLoopMS: Double = 6_000
    // RN motion.ts:108
    static let replicaSwingPhase: Double = -.pi / 2
    // RN motion.ts:109
    static let replicaPeriod: Double = 5.8
    // RN motion.ts:110
    static let replicaHold: Double = -1
    // RN motion.ts:112
    static let qaSpeed: Double = 1
}
