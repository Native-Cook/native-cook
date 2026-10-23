import Foundation

/// Typed Swift mirror of the React Native `theme.ts` and `motion.ts` contract.
///
/// The active upgrade values, inactive legacy values and QA flags stay here so
/// the Swift port retains the complete React Native source contract.
enum GlassLockKeyConstants {
    // RN: theme.ts:3
    static let stageWidth: Double = 402.0

    enum Colors {
        // RN: theme.ts:7
        static let bgTop: String = "#EEEEF0"
        // RN: theme.ts:8
        static let bgBottom: String = "#F6F6F7"
        // RN: theme.ts:9
        static let shadow: String = "rgba(40, 46, 120, 0.16)"
        // RN: theme.ts:10
        static let bodyTop: String = "#4A54EC"
        // RN: theme.ts:11
        static let bodyBottom: String = "#2F37C9"
        // RN: theme.ts:12
        static let bodyLight: String = "rgba(150, 165, 255, 0.85)"
        // RN: theme.ts:13
        static let bodyLightFar: String = "rgba(150, 165, 255, 0)"
        // RN: theme.ts:14
        static let side: String = "#7F8BFF"
        // RN: theme.ts:15
        static let sideDeep: String = "#5862F0"
        // RN: theme.ts:16
        static let rim: String = "rgba(255, 255, 255, 0.85)"
        // RN: theme.ts:17
        static let rimSoft: String = "rgba(255, 255, 255, 0.35)"
        // RN: theme.ts:18
        static let glass: String = "rgba(132, 146, 255, 0.85)"
        // RN: theme.ts:19
        static let glassDeep: String = "rgba(84, 98, 238, 0.9)"
        // RN: theme.ts:20
        static let glassEdge: String = "rgba(255, 255, 255, 0.9)"
        // RN: theme.ts:21
        static let plug: String = "#2A31B0"
        // RN: theme.ts:22
        static let slot: String = "#151A6E"
        // RN: theme.ts:23
        static let glow: String = "rgba(130, 150, 255, 0.9)"
        // RN: theme.ts:24
        static let glint: String = "rgba(255, 255, 255, 0.75)"
        // RN: theme.ts:25
        static let ink: String = "#16182E"
        // RN: theme.ts:26
        static let mute: String = "rgba(22, 24, 46, 0.55)"
        // RN: theme.ts:27
        static let error: String = "#D6304A"
        // RN: theme.ts:28
        static let sheet: String = "#FFFFFF"
        // RN: theme.ts:29
        static let sheetLine: String = "rgba(22, 24, 46, 0.08)"
        // RN: theme.ts:30
        static let rowIcon: String = "#E9EBFF"
        // RN: theme.ts:31
        static let rowIconInk: String = "#3D46E0"
        // RN: theme.ts:32
        static let button: String = "#16182E"
        // RN: theme.ts:33
        static let buttonInk: String = "#FFFFFF"
    }

    enum Lock {
        // RN: theme.ts:37
        static let topRatio: Double = 0.36
        // RN: theme.ts:38
        static let face: Double = 150.0
        // RN: theme.ts:39
        static let height: Double = 180.0
        // RN: theme.ts:40
        static let side: Double = 22.0
        // RN: theme.ts:41
        static let radius: Double = 26.0
        // RN: theme.ts:42
        static let shackle: Double = 30.0
        // RN: theme.ts:43
        static let opening: Double = 68.0
        // RN: theme.ts:44
        static let shackleRise: Double = 100.0
        // RN: theme.ts:45
        static let shortLeg: Double = 8.0
        // RN: theme.ts:46
        static let longLeg: Double = 60.0
        // RN: theme.ts:47
        static let openLift: Double = 58.0
        // RN: theme.ts:48
        static let openTilt: Double = 0.12
        // RN: theme.ts:49
        static let plugWidth: Double = 28.0
        // RN: theme.ts:50
        static let plugHeight: Double = 9.0
        // RN: theme.ts:51
        static let slot: Double = 3.0
        // RN: theme.ts:52
        static let glowRadius: Double = 34.0
    }

    enum Key {
        // RN: theme.ts:56
        static let restRatio: Double = 0.70
        // RN: theme.ts:57
        static let ring: Double = 64.0
        // RN: theme.ts:58
        static let rim: Double = 11.0
        // RN: theme.ts:59
        static let shaft: Double = 11.0
        // RN: theme.ts:60
        static let blade: Double = 100.0
        // RN: theme.ts:61
        static let bitWidth: Double = 18.0
        // RN: theme.ts:62
        static let bitHeight: Double = 30.0
        // RN: theme.ts:63
        static let bitFromTip: Double = 8.0
        // RN: theme.ts:64
        static let collar: Double = 18.0
        // RN: theme.ts:65
        static let hit: Double = 88.0
    }

    enum Typography {
        // RN: theme.ts:69
        static let side: Double = 32.0
        // RN: theme.ts:70
        static let top: Double = 24.0
        // RN: theme.ts:71
        static let eyebrowSize: Double = 12.0
        // RN: theme.ts:72
        static let eyebrowTracking: Double = 1.8
        // RN: theme.ts:73
        static let titleSize: Double = 40.0
        // RN: theme.ts:74
        static let titleLine: Double = 46.0
        // RN: theme.ts:75
        static let titleFamily: String = "Georgia"
        // RN: theme.ts:76
        static let hintSize: Double = 15.0
        // RN: theme.ts:77
        static let hintLine: Double = 20.0
        // RN: theme.ts:78
        static let gap: Double = 8.0
        // RN: theme.ts:79
        static let bottom: Double = 32.0
    }

    enum Sheet {
        // RN: theme.ts:83
        static let heightRatio: Double = 0.5
        // RN: theme.ts:84
        static let radius: Double = 28.0
        // RN: theme.ts:85
        static let pad: Double = 24.0
        // RN: theme.ts:86
        static let row: Double = 64.0
        // RN: theme.ts:87
        static let icon: Double = 40.0
        // RN: theme.ts:88
        static let iconRadius: Double = 12.0
        // RN: theme.ts:89
        static let titleSize: Double = 17.0
        // RN: theme.ts:90
        static let subSize: Double = 13.0
        // RN: theme.ts:91
        static let headSize: Double = 22.0
        // RN: theme.ts:92
        static let button: Double = 52.0
        // RN: theme.ts:93
        static let rise: Double = 0.075
        // RN: theme.ts:94
        static let shrink: Double = 0.62
    }

    enum Space {
        // RN: theme.ts:99
        static let xs: Double = 4.0
        static let sm: Double = 8.0
        static let md: Double = 12.0
        static let lg: Double = 16.0
        static let xl: Double = 24.0
        static let xxl: Double = 32.0
    }

    enum Copy {
        struct Row {
            let title: String
            let sub: String
            let icon: String
            let symbol: String
        }

        // RN: theme.ts:102
        static let eyebrow: String = "VAULT"
        // RN: theme.ts:103
        static let locked: String = "Locked"
        // RN: theme.ts:104
        static let unlocked: String = "Unlocked"
        // RN: theme.ts:105
        static let hint: String = "Slide the key in, then turn it right"
        // RN: theme.ts:106
        static let wrongWay: String = "The other way"
        // RN: theme.ts:107
        static let meta: String = "3 items · end-to-end encrypted"
        // RN: theme.ts:108
        static let head: String = "Personal vault"
        // RN: theme.ts:110
        static let sheetMeta: String = "Opened just now"
        static let rows: [Row] = [
            // RN: theme.ts:111
            Row(title: "Recovery codes", sub: "12 codes · added 3 days ago", icon: "#", symbol: "number"),
            // RN: theme.ts:112
            Row(title: "Passport scan", sub: "PDF · 2.4 MB", icon: "P", symbol: "doc.text"),
            // RN: theme.ts:113
            Row(title: "Home Wi-Fi", sub: "Password · shared with 2", icon: "W", symbol: "wifi")
        ]
        // RN: theme.ts:115
        static let lock: String = "Lock vault"
        // RN: theme.ts:116
        static let unlock: String = "Unlock vault"
        // RN: theme.ts:117
        static let keyLabel: String = "Key"
        // RN: theme.ts:118
        static let keyHint: String = "Swipe up to insert and turn the key"
    }

    // RN: theme.ts:123
    static let replica: Bool = false
    // RN: theme.ts:124
    static let replicaBackground: String = "#EFEFEF"

    enum UpgradeLayout {
        struct Point {
            let x: Double
            let y: Double
        }

        struct RadialCenter {
            let x: Double
            let y: Double
            let r: Double
        }

        struct Glow {
            let r: Double
            let color: String
        }

        // RN: theme.ts:128
        static let keyFront = Point(x: 200.7, y: 436.3)
        // RN: theme.ts:129
        static let keyRest = Point(x: 200.7, y: 436.3)
        // RN: theme.ts:130
        static let plug = Point(x: 145.3, y: 461.0)
        // RN: theme.ts:131
        static let ring = Point(x: 107.0, y: 443.0)
        // RN: theme.ts:132
        static let insertAngle: Double = -1.19
        // RN: theme.ts:133
        static let lockCentre = Point(x: 201.0, y: 445.0)
        // RN: theme.ts:134
        static let darkCentre = RadialCenter(x: 220.0, y: 470.0, r: 120.0)
        // RN: theme.ts:135
        static let darkColor: String = "rgba(20, 24, 120, 0.55)"
        // RN: theme.ts:136
        static let glow = Glow(r: 260.0, color: "rgba(80, 96, 255, 0.35)")
        // RN: theme.ts:137
        static let bg: [String] = ["#F2F2F6", "#E6E8F3"]
    }

    /// Source registration transforms shared by the active upgrade and legacy replica layers.
    /// The arrays intentionally preserve the React Native tuple ordering.
    enum ReplicaFit {
        // RN: theme.ts:143
        static let lockCentre: [Double] = [660.0, 1416.0]
        // RN: theme.ts:144
        static let keyCentre: [Double] = [436.0, 1383.0]
        // RN: theme.ts:145
        static let frontCentre: [Double] = [602.0, 1309.0]
        // RN: theme.ts:146
        static let lock: [Double] = [1.01, 1.01, 0.0, 0.0, 0.0]
        // RN: theme.ts:147
        static let shackleClosed: [Double] = [3.0, 0.0]
        // RN: theme.ts:148
        static let shackleOpen: [Double] = [-4.0, 1.0]
        // RN: theme.ts:149
        static let keyClosed: [Double] = [1.0, 1.0, 0.0, 0.0, 0.0, 0.0]
        // RN: theme.ts:150
        static let keyOpen: [Double] = [0.95, 0.885, 0.0, 39.0, 25.5, -0.075]
        // RN: theme.ts:151
        static let front: [Double] = [1.01, 0.99, 0.0, 0.0, 2.0]
    }

    // RN: motion.ts:3
    static let step: Double = 1.0 / 120.0
    // RN: motion.ts:4
    static let maxSteps: Int = 30

    enum Carry {
        struct Spring {
            let stiffness: Double
            let damping: Double
        }

        // RN: motion.ts:7
        static let lift: Double = 1.04
        // RN: motion.ts:8
        static let magnet: Double = 72.0
        // RN: motion.ts:9
        static let magnetPull: Double = 0.55
        // RN: motion.ts:10
        static let seat: Double = 12.0
        // RN: motion.ts:11
        static let home = Spring(stiffness: 220.0, damping: 22.0)
        // RN: motion.ts:12
        static let bob: Double = 3.0
        // RN: motion.ts:13
        static let bobPeriod: Double = 3.0
        // RN: motion.ts:14
        static let pullOut: Double = 34.0
    }

    enum Insert {
        // RN: motion.ts:17
        static let duration: Double = 0.22
        // RN: motion.ts:17
        static let outDuration: Double = 0.26
    }

    enum Turn {
        struct Spring {
            let stiffness: Double
            let damping: Double
        }

        // RN: motion.ts:20
        static let radius: Double = 60.0
        // RN: motion.ts:21
        static let open: Double = Double.pi / 2.0
        // RN: motion.ts:22
        static let detents: [Double] = [Double.pi / 6.0, Double.pi / 3.0, Double.pi / 2.0]
        // RN: motion.ts:23
        static let wrong: Double = 0.21
        // RN: motion.ts:24
        static let visual: Double = 0.83
        // RN: motion.ts:25
        static let back = Spring(stiffness: 260.0, damping: 24.0)
        // RN: motion.ts:26
        static let close = Spring(stiffness: 180.0, damping: 22.0)
    }

    enum Shackle {
        // RN: motion.ts:29
        static let stiffness: Double = 320.0
        // RN: motion.ts:29
        static let damping: Double = 14.0
        // RN: motion.ts:29
        static let closeStiffness: Double = 260.0
        // RN: motion.ts:29
        static let closeDamping: Double = 26.0
    }

    enum Bounce {
        // RN: motion.ts:30
        static let stiffness: Double = 420.0
        // RN: motion.ts:30
        static let damping: Double = 16.0
        // RN: motion.ts:30
        static let detentKick: Double = -40.0
        // RN: motion.ts:30
        static let openKick: Double = -120.0
    }

    enum Shake {
        // RN: motion.ts:31
        static let duration: Double = 0.36
        // RN: motion.ts:31
        static let amp: Double = 6.0
        // RN: motion.ts:31
        static let cycles: Int = 3
    }

    enum SheetMotion {
        // RN: motion.ts:32
        static let delay: Double = 0.3
        // RN: motion.ts:32
        static let open: Double = 0.5
        // RN: motion.ts:32
        static let close: Double = 0.38
        // RN: motion.ts:32
        static let stagger: Double = 0.04
        // RN: motion.ts:32
        static let reducedFade: Double = 0.2
        // RN: motion.ts:32
        static let shackleAfter: Double = 0.3
    }

    enum Glint {
        // RN: motion.ts:33
        static let duration: Double = 0.7
    }

    enum Haptic {
        // RN: motion.ts:35
        static let grab: Int = 1
        // RN: motion.ts:35
        static let seat: Int = 2
        // RN: motion.ts:35
        static let detent: Int = 3
        // RN: motion.ts:35
        static let open: Int = 4
        // RN: motion.ts:35
        static let wrong: Int = 5
    }

    // RN: motion.ts:38
    static let loopMS: Int = 12_000
    // RN: motion.ts:39
    static let resumeMS: Int = 3_500

    enum Script {
        // RN: motion.ts:41
        static let carry: [Double] = [0.8, 1.6]
        // RN: motion.ts:42
        static let carryTo: [Double] = [0.0, -100.0]
        // RN: motion.ts:43
        static let turn: [Double] = [1.95, 3.0]
        // RN: motion.ts:44
        static let turnBy: Double = 100.0
        // RN: motion.ts:45
        static let lockAt: Double = 8.4
    }

    // RN: motion.ts:50
    static let replicaLoopMS: Int = 6_000

    enum ReplicaTiming {
        // RN: motion.ts:52
        static let keySpin: [Double] = [0.0, 1.0]
        // RN: motion.ts:53
        static let toLock: [Double] = [1.0, 1.35]
        // RN: motion.ts:54
        static let open: [Double] = [1.9, 2.15]
        // RN: motion.ts:55
        static let close: [Double] = [4.2, 4.45]
        // RN: motion.ts:56
        static let toKey: [Double] = [5.4, 5.75]
    }

    // RN: motion.ts:58
    static let replicaHold: Double = -1.0

    enum UpgradeMotion {
        struct Spring {
            let stiffness: Double
            let damping: Double
        }

        struct ShackleSpring {
            let stiffness: Double
            let damping: Double
            let closeStiffness: Double
            let closeDamping: Double
        }

        struct Shake {
            let duration: Double
            let amp: Double
            let cycles: Int
        }

        struct Show {
            let inRate: Double
            let outRate: Double
            let hold: Double
        }

        struct Tick {
            let duration: Double
            let alpha: Double
        }

        struct Recoil {
            let amp: Double
            let decay: Double
            let hz: Double
        }

        struct Rows {
            let stagger: Double
            let rise: Double
            let span: Double
        }

        struct Spin {
            let every: Double
            let turn: Double
        }

        // RN: motion.ts:62
        static let magnet: Double = 72.0
        // RN: motion.ts:63
        static let magnetPull: Double = 0.55
        // RN: motion.ts:64
        static let seat: Double = 14.0
        // RN: motion.ts:65
        static let flick: Double = 900.0
        // RN: motion.ts:66
        static let keyHitW: Double = 50.0
        // RN: motion.ts:67
        static let keyHitH: Double = 90.0
        // RN: motion.ts:68
        static let ringHit: Double = 64.0
        // RN: motion.ts:69
        static let grabBelow: Double = 40.0
        // RN: motion.ts:70
        static let insert: Double = 0.28
        // RN: motion.ts:71
        static let turnPx: Double = 110.0
        // RN: motion.ts:72
        static let wrong: Double = 0.12
        // RN: motion.ts:73
        static let pullOut: Double = 34.0
        // RN: motion.ts:74
        static let outPull: Double = 40.0
        // RN: motion.ts:75
        static let outSpeed: Double = 300.0
        // RN: motion.ts:76
        static let detents: [Double] = [1.0 / 3.0, 2.0 / 3.0, 1.0]
        // RN: motion.ts:77
        static let back = Spring(stiffness: 260.0, damping: 24.0)
        // RN: motion.ts:78
        static let home = Spring(stiffness: 220.0, damping: 22.0)
        // RN: motion.ts:79
        static let shackle = ShackleSpring(stiffness: 320.0, damping: 14.0, closeStiffness: 260.0, closeDamping: 26.0)
        // RN: motion.ts:80
        static let shackleKick: Double = 4.0
        // RN: motion.ts:81
        static let shackleAfter: Double = 0.3
        // RN: motion.ts:82
        static let sheetDelay: Double = 0.3
        // RN: motion.ts:83
        static let sheetIn: Double = 9.0
        // RN: motion.ts:84
        static let sheetOut: Double = 12.0
        // RN: motion.ts:85
        static let glint: Double = 0.7
        // RN: motion.ts:90
        static let shake = Shake(duration: 0.36, amp: 6.0, cycles: 3)
        // RN: motion.ts:86
        static let tick = Tick(duration: 0.16, alpha: 0.35)
        // RN: motion.ts:87
        static let recoil = Recoil(amp: 3.0, decay: 12.0, hz: 7.0)
        // RN: motion.ts:88
        static let appear: Double = 0.96
        // RN: motion.ts:89
        static let rows = Rows(stagger: 0.08, rise: 8.0, span: 0.5)
        // RN: motion.ts:91
        static let spin = Spin(every: 4.0, turn: 1.0)
        // RN: motion.ts:92
        static let show = Show(inRate: 10.0, outRate: 4.0, hold: 1.5)
        // RN: motion.ts:94
        static let rise: Double = 150.0
        // RN: motion.ts:95
        static let shrink: Double = 0.72
    }

    enum UpgradeHaptic {
        // RN: motion.ts:98
        static let grab: Int = 1
        // RN: motion.ts:98
        static let seat: Int = 2
        // RN: motion.ts:98
        static let detent: Int = 3
        // RN: motion.ts:98
        static let open: Int = 4
        // RN: motion.ts:98
        static let wrong: Int = 5
    }

    // RN: motion.ts:99
    static let upgradeLoopMS: Int = 12_000

    enum UpgradeScript {
        // RN: motion.ts:100
        static let carry: [Double] = [0.8, 1.6]
        // RN: motion.ts:100
        static let turn: [Double] = [2.0, 3.0]
        // RN: motion.ts:100
        static let turnBy: Double = 120.0
        // RN: motion.ts:100
        static let lockAt: Double = 8.4
        // RN: motion.ts:100
        static let resumeOpen: Double = 5.0
    }

    // QA-only upgrade values. These remain present even though the active Swift
    // screen uses the same deterministic simulation without a QA speed override.
    // RN: motion.ts:97
    static let gQASpeed: Double = 1.0

}
