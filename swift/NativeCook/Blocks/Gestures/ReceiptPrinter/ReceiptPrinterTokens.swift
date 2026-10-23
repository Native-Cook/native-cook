import SwiftUI

/// A receipt row measured from its capital top on the source's 402 pt stage.
struct ReceiptPrinterRow {
    let cap: Double
    let text: String
    let align: String
    let x: Double?
    let size: Double
    let bold: Bool
    let color: Color?

    init(cap: Double, text: String, align: String, x: Double? = nil, size: Double, bold: Bool = false, color: Color? = nil) {
        self.cap = cap
        self.text = text
        self.align = align
        self.x = x
        self.size = size
        self.bold = bold
        self.color = color
    }
}

struct ReceiptPrinterCard {
    let date: [String]
    let km: String
    let seed: Int
}

/// SwiftUI values ported verbatim from the receipt-printer React Native theme.
enum ReceiptPrinterTokens {
    enum COLOR {
        static func hex(_ value: UInt32) -> Color {
            Color(
                .sRGB,
                red: Double((value >> 16) & 0xFF) / 255.0,
                green: Double((value >> 8) & 0xFF) / 255.0,
                blue: Double(value & 0xFF) / 255.0,
                opacity: 1.0
            )
        }

        static func rgba(_ red: Double, _ green: Double, _ blue: Double, _ alpha: Double) -> Color {
            Color(.sRGB, red: red / 255.0, green: green / 255.0, blue: blue / 255.0, opacity: alpha)
        }

        // RN theme.ts:7-31.
        static let bg: Color = hex(0xEFEFEF)
        static let card: Color = hex(0xE5E5E5)
        static let cardPressed: Color = hex(0xFFFFFF)
        static let ink: Color = hex(0x1F2226)
        static let inkSoft: Color = hex(0x8A8A8A)
        static let date: Color = hex(0x767676)
        static let inkFaint: Color = hex(0xA8A8A8)
        static let icon: Color = hex(0x3C3C3C)
        static let slot: Color = hex(0x1E1E1E)
        static let slotSheen: Color = hex(0x3A3A3A)
        static let slit: Color = hex(0x8E8E8E)
        static let paper: Color = hex(0xFAF8F2)
        static let paperShade: Color = rgba(0.0, 0.0, 0.0, 0.05)
        static let dash: Color = hex(0xBDBDBD)
        static let grid: Color = hex(0xD9D9D9)
        static let gridFaint: Color = hex(0xF4F4F4)
        static let axis: Color = hex(0x3A3A3A)
        static let zone: Color = hex(0x7EE06E)
        static let tag: Color = hex(0x111111)
        static let tagInk: Color = hex(0xFFFFFF)
        static let menu: Color = hex(0xFFFFFF)
        static let shadow: Color = hex(0x000000)
        static let buttonDark: Color = hex(0x111111)
        static let buttonLight: Color = hex(0xFFFFFF)
        static let buttonInk: Color = hex(0x333333)
        static let led: Color = hex(0x7EE06E)
        static let develop: Color = rgba(255.0, 255.0, 255.0, 0.9)
        static let indicator: Color = rgba(0.0, 0.0, 0.0, 0.28)
    }

    // RN theme.ts:34-39. These aliases are the names passed to the RN font loader.
    enum FONT {
        static let regular: String = "ReceiptPrinter-Mono"
        static let medium: String = "ReceiptPrinter-MonoMedium"
        static let bold: String = "ReceiptPrinter-MonoBold"
        static let heavy: String = "ReceiptPrinter-MonoExtraBold"
    }

    static func mono(_ bold: Bool = false) -> String {
        bold ? FONT.bold : FONT.regular
    }

    // JetBrains Mono: capitals start (1020 - 730) / 1000 em below the top of a line box without lineHeight.
    static let CAP_TOP: Double = 0.29

    // RN theme.ts:43-58.
    enum TYPE {
        static let title: Double = 27.5
        static let subtitle: Double = 12.5
        static let pill: Double = 16.5
        static let cardDate: Double = 12.0
        static let cardKm: Double = 16.5
        static let menu: Double = 12.5
        static let rcTitle: Double = 22.0
        static let body: Double = 13.3
        static let head: Double = 12.5
        static let total: Double = 19.0
        static let small: Double = 11.2
        static let tiny: Double = 10.0
        static let chartNote: Double = 8.6
        static let button: Double = 11.5
    }

    // RN theme.ts:65-66.
    enum SPACE {
        static let xs: Double = 4.0
        static let sm: Double = 8.0
        static let md: Double = 12.0
        static let lg: Double = 16.0
        static let xl: Double = 24.0
        static let xxl: Double = 32.0
    }

    enum GRID {
        struct Info {
            let cx: Double
            let cy: Double
            let r: Double
        }

        struct Menu {
            let x: Double
            let y: Double
            let w: Double
            let h: Double
            let radius: Double
            let rowH: Double
            let iconX: Double
            let textX: Double
        }

        static let gutter: Double = 16.0
        static let gap: Double = 14.0
        static let cardW: Double = 114.0
        static let titleCap: Double = 78.5
        static let subtitleCap: Double = 111.5
        static let info = Info(cx: 372.0, cy: 88.0, r: 12.0)
        static let pillTop: Double = 144.7
        static let pillH: Double = 40.6
        static let rowTop: Double = 203.0
        static let rowPitch: Double = 158.3
        static let cardH: Double = 140.7
        static let radius: Double = 12.0
        static let dateInset: Double = 12.0
        static let dateCap: Double = 14.5
        static let iconTop: Double = 50.0
        static let iconW: Double = 46.0
        static let iconH: Double = 33.0
        static let kmCapFromBottom: Double = 29.0
        static let menu = Menu(x: 16.0, y: 203.0, w: 254.0, h: 93.0, radius: 18.0, rowH: 38.0, iconX: 22.0, textX: 52.0)
    }

    enum SLOT {
        struct Led {
            let x: Double
            let y: Double
            let r: Double
            let glow: Double
        }

        static let x0: Double = 16.0
        static let x1: Double = 386.0
        static let y0: Double = 70.5
        static let y1: Double = 111.0
        static let ring: Double = 14.5
        static let slitY0: Double = 85.5
        static let slitY1: Double = 96.5
        static let paperX0: Double = 35.0
        static let paperX1: Double = 368.0
        static let paperTop: Double = 86.0
        static let shadowH: Double = 16.0
        static let led = Led(x: 386.0 - SPACE.xl, y: (70.5 + 85.5) / 2.0, r: 2.6, glow: 9.0)
    }

    enum ISLAND {
        static let top: Double = 14.0
        static let w: Double = 94.0
        static let h: Double = 36.0
        static let noIslandGap: Double = 6.0
        static let openW: Double = SLOT.paperX1 - SLOT.paperX0 + 28.0
        static let openH: Double = 46.0
        static let tuck: Double = 4.0
        static let ledInset: Double = 22.0
        static let color: Color = COLOR.hex(0x000000)
    }

    enum RECEIPT {
        struct Logo {
            let top: Double
            let w: Double
            let h: Double
        }

        struct Paragraph {
            let cap: Double
            let pitch: Double
            let lines: [String]
        }

        struct Fast {
            let x: Double
            let top: Double
            let w: Double
            let h: Double
            let radius: Double
            let size: Double
        }

        struct Zone {
            let x: Double
            let ys: [Double]
            let r: Double
        }

        struct Chart {
            let top: Double
            let bottom: Double
            let base: Double
            let baseW: Double
            let columns: Int
            let dotted: Int
        }

        struct Edge {
            let period: Double
            let depth: Double
        }

        static let length: Double = 1281.0
        static let left: Double = 60.0
        static let right: Double = 340.0
        static let center: Double = 201.5
        static let logo = Logo(top: 153.0, w: 73.0, h: 44.0)
        static let dashes: [Double] = [363.0, 410.0, 505.0, 751.0, 947.0, 1181.0]
        static let rows: [ReceiptPrinterRow] = [
            ReceiptPrinterRow(cap: 222.3, text: "RUN RECEIPT", align: "center", size: TYPE.rcTitle, bold: true),
            ReceiptPrinterRow(cap: 272.0, text: "RUN #0418", align: "center", size: TYPE.body),
            ReceiptPrinterRow(cap: 296.3, text: "WED, JANUARY 28, 2026", align: "center", size: TYPE.body),
            ReceiptPrinterRow(cap: 321.3, text: "RIVERSIDE LOOP – TEMPO", align: "center", size: TYPE.body),
            ReceiptPrinterRow(cap: 381.7, text: "ITEM", align: "left", x: 60.0, size: TYPE.head, bold: true),
            ReceiptPrinterRow(cap: 381.7, text: "PACE", align: "left", x: 172.0, size: TYPE.head, bold: true),
            ReceiptPrinterRow(cap: 381.7, text: "ZONE", align: "left", x: 217.0, size: TYPE.head, bold: true),
            ReceiptPrinterRow(cap: 381.7, text: "TIME", align: "left", x: 262.0, size: TYPE.head, bold: true),
            ReceiptPrinterRow(cap: 381.7, text: "HR", align: "right", x: 340.0, size: TYPE.head, bold: true),
            ReceiptPrinterRow(cap: 435.3, text: "5 KM", align: "left", x: 60.0, size: TYPE.body),
            ReceiptPrinterRow(cap: 435.3, text: "05'28\"", align: "left", x: 163.0, size: TYPE.body),
            ReceiptPrinterRow(cap: 435.3, text: "27:21", align: "left", x: 258.0, size: TYPE.body),
            ReceiptPrinterRow(cap: 435.3, text: "154", align: "right", x: 340.0, size: TYPE.body),
            ReceiptPrinterRow(cap: 470.5, text: "1.21 KM", align: "left", x: 60.0, size: TYPE.body),
            ReceiptPrinterRow(cap: 470.5, text: "05'11\"", align: "left", x: 163.0, size: TYPE.body),
            ReceiptPrinterRow(cap: 470.5, text: "06:17", align: "left", x: 258.0, size: TYPE.body),
            ReceiptPrinterRow(cap: 470.5, text: "168", align: "right", x: 340.0, size: TYPE.body),
            ReceiptPrinterRow(cap: 558.7, text: "PACE", align: "left", x: 60.0, size: TYPE.head, bold: true),
            ReceiptPrinterRow(cap: 561.0, text: "05'04\"–05'42\"/KM | AVG 05'25\"", align: "right", x: 340.0, size: TYPE.chartNote, color: COLOR.inkSoft),
            ReceiptPrinterRow(cap: 715.0, text: "0KM", align: "left", x: 60.0, size: TYPE.tiny, color: COLOR.inkSoft),
            ReceiptPrinterRow(cap: 715.0, text: "6.2KM", align: "right", x: 340.0, size: TYPE.tiny, color: COLOR.inkSoft),
            ReceiptPrinterRow(cap: 789.0, text: "TOTAL:", align: "left", x: 60.0, size: TYPE.total, bold: true),
            ReceiptPrinterRow(cap: 789.0, text: "6.21 KM", align: "right", x: 340.0, size: TYPE.total, bold: true),
            ReceiptPrinterRow(cap: 827.0, text: "DURATION", align: "left", x: 60.0, size: TYPE.body),
            ReceiptPrinterRow(cap: 827.0, text: "00:33:38", align: "right", x: 340.0, size: TYPE.body),
            ReceiptPrinterRow(cap: 851.7, text: "AVG PACE", align: "left", x: 60.0, size: TYPE.body),
            ReceiptPrinterRow(cap: 851.7, text: "05'25\"/KM", align: "right", x: 340.0, size: TYPE.body),
            ReceiptPrinterRow(cap: 876.3, text: "BEST 1K", align: "left", x: 60.0, size: TYPE.body),
            ReceiptPrinterRow(cap: 876.3, text: "05'09\"/KM", align: "right", x: 340.0, size: TYPE.body),
            ReceiptPrinterRow(cap: 901.3, text: "AVG HR", align: "left", x: 60.0, size: TYPE.body),
            ReceiptPrinterRow(cap: 901.3, text: "156 BPM", align: "right", x: 340.0, size: TYPE.body),
            ReceiptPrinterRow(cap: 983.0, text: "*** DUPLICATE COPY ***", align: "center", size: TYPE.body, bold: true),
            ReceiptPrinterRow(cap: 1018.3, text: "KEEP FOR YOUR RECORDS", align: "center", size: TYPE.small, color: COLOR.inkSoft),
            ReceiptPrinterRow(cap: 1210.3, text: "PRINTED", align: "center", size: TYPE.small, bold: true),
            ReceiptPrinterRow(cap: 1223.7, text: "WED 28 JAN 18:51", align: "center", size: TYPE.small, bold: true),
            ReceiptPrinterRow(cap: 1261.7, text: "EVEN PACE, STRONG FINISH.", align: "center", size: TYPE.small, bold: true),
            ReceiptPrinterRow(cap: 1294.7, text: "GENERATED LOCALLY VIA STRIDE OS.", align: "center", size: TYPE.tiny, color: COLOR.inkFaint),
        ]
        static let paragraph = Paragraph(
            cap: 1053.0,
            pitch: 15.6,
            lines: [
                "Every run leaves a trace. Run Receipt",
                "turns it into something you can keep and",
                "share, capturing distance, pace, and",
                "effort as a simple receipt of movement –",
                "a small record of where you ran and how",
                "the moment felt. Learn more at",
                "receipt.strideos.run.",
            ]
        )
        static let fast = Fast(x: 120.0, top: 466.5, w: 34.0, h: 17.7, radius: 3.5, size: 9.5)
        static let zone = Zone(x: 231.3, ys: [440.2, 475.3], r: 4.2)
        static let chart = Chart(top: 585.0, bottom: 705.0, base: 689.6, baseW: 2.4, columns: 6, dotted: 4)
        static let edge = Edge(period: 12.8, depth: 6.0)
    }

    enum BAR {
        static let top: Double = 792.0
        static let h: Double = 56.0
        static let x: Double = 33.7
        static let w: Double = 106.3
        static let gap: Double = 13.0
        static let icon: Double = 18.0
        static let iconCap: Double = 10.0
        static let labelCap: Double = 37.0
    }

    static let CARDS: [ReceiptPrinterCard] = [
        ReceiptPrinterCard(date: ["WED", "28 JAN"], km: "6.21 KM", seed: 3), ReceiptPrinterCard(date: ["SUN", "25 JAN"], km: "12.40 KM", seed: 11),
        ReceiptPrinterCard(date: ["THU", "22 JAN"], km: "5.02 KM", seed: 5), ReceiptPrinterCard(date: ["TUE", "20 JAN"], km: "8.15 KM", seed: 8),
        ReceiptPrinterCard(date: ["SAT", "17 JAN"], km: "10.33 KM", seed: 2), ReceiptPrinterCard(date: ["WED", "14 JAN"], km: "6.18 KM", seed: 3),
        ReceiptPrinterCard(date: ["MON", "12 JAN"], km: "4.40 KM", seed: 14), ReceiptPrinterCard(date: ["SAT", "10 JAN"], km: "9.87 KM", seed: 6),
        ReceiptPrinterCard(date: ["THU", "8 JAN"], km: "5.55 KM", seed: 9), ReceiptPrinterCard(date: ["TUE", "6 JAN"], km: "7.20 KM", seed: 12),
        ReceiptPrinterCard(date: ["SUN", "4 JAN"], km: "11.06 KM", seed: 4), ReceiptPrinterCard(date: ["FRI", "2 JAN"], km: "5.00 KM", seed: 7),
        ReceiptPrinterCard(date: ["WED", "31 DEC"], km: "6.42 KM", seed: 10), ReceiptPrinterCard(date: ["MON", "29 DEC"], km: "8.90 KM", seed: 1),
        ReceiptPrinterCard(date: ["SAT", "27 DEC"], km: "3.25 KM", seed: 13),
    ]

    static let PILLS: [String] = ["91.4 KM", "12 RUNS", "5'29\"/KM"]

    enum COPY {
        static let title: String = "RUN RECEIPT"
        static let subtitle: String = "2026 so far · tap a run to print it"
        static let long: String = "LONG RECEIPT"
        static let short: String = "SHORT RECEIPT"
        static let save: String = "SAVE"
        static let tear: String = "TEAR OFF"
        static let sign: String = "SIGNATURE"
    }
}

/// Motion values ported verbatim from the receipt-printer React Native motion.
enum ReceiptPrinterMotion {
    struct Spring {
        let damping: Double
        let stiffness: Double
        let mass: Double
    }

    struct DurationSpring {
        let duration: Double
        let dampingRatio: Double
    }

    struct Scroll {
        let start: Double
        let end: Double
        let by: Double
    }

    enum PRESS {
        static let longPressMs: Double = 380.0
        static let menuIn = Spring(damping: 20.0, stiffness: 320.0, mass: 1.0)
        static let menuOutMs: Double = 140.0
        static let cardLift: Double = 1.03
    }

    enum SCREEN {
        static let gridOutMs: Double = 260.0
        static let slotInMs: Double = 220.0
    }

    static let ISLAND_SPRING = Spring(damping: 17.0, stiffness: 230.0, mass: 1.0)
    /// The paper waits for the Dynamic Island to open, keeping its width inside the island's mouth.
    static let ISLAND_LEAD_MS: Double = 220.0

    enum FEED {
        static let ms: Double = 2300.0
        static let steps: Int = 14
        static let push: Double = 0.55
        static let swing: Double = 0.35
        static let swingDamping: Double = 7.0
        static let swingFreq: Double = 9.0
    }

    enum SCROLL {
        static let rubber: Double = 0.55
        static let deceleration: Double = 0.997
        static let dismissBy: Double = 90.0
        static let dismissVelocity: Double = 1000.0
        static let barWithin: Double = 40.0
        static let barMs: Double = 220.0
    }

    enum RETRACT {
        static let ms: Double = 520.0
        static let gridLeadMs: Double = 130.0
        static let gridInMs: Double = 680.0
        static let reducedMs: Double = 200.0
    }

    static let LOOP_MS: Double = 10000.0

    enum AUTOPLAY {
        static let resumeDelayMs: Double = 2000.0
        static let pressAt: Double = 500.0
        static let pickAt: Double = 1300.0
        static let scrolls: [Scroll] = [
            Scroll(start: 4100.0, end: 5300.0, by: 700.0),
            Scroll(start: 5500.0, end: 6000.0, by: -200.0),
            Scroll(start: 6100.0, end: 6600.0, by: 220.0),
        ]
        static let tearAt: Double = 7600.0
    }

    enum UPGRADE {
        static let morphMs: Double = 420.0
        static let developH: Double = 72.0
        static let developOpacity: Double = 0.7
        static let developFadeMs: Double = 260.0
        static let cutDrop: Double = 5.0
        static let cutDownMs: Double = 90.0
        static let cutSpring = Spring(damping: 11.0, stiffness: 420.0, mass: 0.6)
        static let ledBlinkHz: Double = 6.0
        static let ledIdle: Double = 0.35
        static let liftShadow: [Double] = [0.16, 0.3]
        static let liftMs: Double = 160.0
        static let swayDeg: Double = 0.12
        static let swayMs: Double = 5000.0
        static let tug: Double = 10.0
        static let tugMs: Double = 120.0
    }

    static let QA_SLOWMO: Double = 1.0
    static let DEBUG_FREEZE_MS: Double = -1.0

    enum POLISH {
        static let easeOut: [Double] = [0.23, 1.0, 0.32, 1.0]
        static let easeInOut: [Double] = [0.77, 0.0, 0.175, 1.0]
        static let holdScale: Double = 0.97
        static let releaseSpring = DurationSpring(duration: 300.0, dampingRatio: 0.8)
        static let pressScale: Double = 0.97
        static let pressInMs: Double = 100.0
        static let pressOutMs: Double = 160.0
        static let armedLed: Double = 1.0
    }
}
