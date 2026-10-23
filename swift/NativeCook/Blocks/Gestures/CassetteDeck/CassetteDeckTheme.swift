import SwiftUI

/// Every colour and size of the block. Sizes are points on the source's 402 × 874 pt screen,
/// measured from the rectified frames (3 px per point), and scaled by width / 402.
enum CassetteDeckTheme {
    static let screen = CGSize(width: 402, height: 874)

    enum Palette {
        // Measured: flat #242225 with a faint lift above the cassette and a darker pool under it.
        static let bg = Color(hex: 0x242225)
        static let bgLift = Color.rgba(255, 252, 255, 0.045)
        static let bgPool = Color.rgba(0, 0, 0, 0.16)
        static let grille = Color(hex: 0x19181B)
        static let grilleRim = Color.rgba(255, 255, 255, 0.05)
        static let ledOff = CassetteDeckRGBA(hex: 0x3A393D)
        static let ledOn = CassetteDeckRGBA(hex: 0x35E39A)
        static let ledGlow = Color.rgba(53, 227, 154, 0.55)
        static let glyphOff = CassetteDeckRGBA(hex: 0x2D2C30)
        static let glyphOn = CassetteDeckRGBA(hex: 0xE9E8EC)
        static let glyphGlow = Color.rgba(255, 255, 255, 0.35)
        static let labelInk = Color(hex: 0x1C1B1D)
        static let labelMute = Color(hex: 0x5E5C60)
        static let wordmark = Color(hex: 0xECEAE4)
        static let pillTop = Color(hex: 0xA9A9AB)
        static let pillBottom = Color(hex: 0x7E7E81)
        static let pillInk = Color(hex: 0x3A3A3C)
        static let tape = Color(hex: 0x2B2421)
        static let tapeEdge = Color.rgba(150, 118, 92, 0.35)
        /// A narrow band of the key light, soft at both ends.
        static let sheen: [Gradient.Stop] = [
            .init(color: .rgba(255, 255, 255, 0), location: 0.3),
            .init(color: .rgba(255, 255, 255, 0.06), location: 0.46),
            .init(color: .rgba(255, 255, 255, 0.13), location: 0.5),
            .init(color: .rgba(255, 255, 255, 0.06), location: 0.54),
            .init(color: .rgba(255, 255, 255, 0), location: 0.7)
        ]
        /// The shell's side faces: lit from the top left, so the left face is brighter.
        static let edgeLeft = [Color(hex: 0x2A292C), Color(hex: 0x141316)]
        static let edgeRight = [Color(hex: 0x111013), Color(hex: 0x08080A)]
        /// The room takes the hue of the loaded cassette's cover (track 1 keeps the source's neutral lift).
        static let tints = [
            CassetteDeckRGBA(255, 252, 255, 0.045),
            CassetteDeckRGBA(150, 140, 255, 0.085),
            CassetteDeckRGBA(80, 220, 190, 0.075)
        ]
        static let redSpill = Color.rgba(230, 40, 64, 0.32)
        static let ledSpill = Color.rgba(53, 227, 154, 0.10)
    }

    /// The spacing scale from the React Native block. Source geometry keeps its measured positions;
    /// additions use these named steps.
    enum Space {
        static let xs = 4.0
        static let sm = 8.0
        static let md = 12.0
        static let lg = 16.0
        static let xl = 24.0
        static let xxl = 32.0
    }

    /// Absolute positions in points on the 402 × 874 screen.
    enum Layout {
        static let grille = (cx: 44.3, cy: 88.7, pitch: 6.6, dot: 2.1, radius: 20.5)
        static let led = (cx: 374.3, cy: 77.7, r: 2.1)
        static let cassette = (x: 16.67, y: 223.33, w: 370.0, h: 240.0)
        /// Inside the cassette frame.
        static let reels = [(cx: 119.7, cy: 106.3), (cx: 248.0, cy: 106.3)]
        static let reelSize = 70.0
        /// The dark band through the label, where the tape packs show around the hubs.
        static let window = (x: 95.0, y: 80.7, w: 179.0, h: 49.3)
        static let tapePack = (min: 26.0, max: 62.0)
        static let cover = (x: 38.3, y: 35.0, size: 37.3, radius: 1.0)
        static let title = (x: 86.0, y: 36.5, size: 16.0, tracking: -0.6)
        static let artist = (x: 86.0, y: 56.0, size: 11.2, tracking: -0.1)
        static let wordmark = (right: 43.3, y: 145.5, size: 11.6, tracking: 1.6)
        static let keys = (x0: 60.0, pitch: 53.75, y: 588.3, w: 65.0, h: 96.67, travel: 3.3)
        static let glyphs = (y: 580.7, size: 10.0, x0: 92.5)
        static let pill = (x: 17.7, y: 805.7, w: 31.3, h: 20.0)
        /// The cassette strip sits this far inside the swipe area, above and below (RN's `20`).
        static let swipeMargin = 20.0
        /// The shell's edge faces: top and bottom insets in points, not scaled (RN's `styles.edge`).
        static let edgeInset = (top: 6.0, bottom: 10.0)
    }

    static let tracks = [
        (title: "Glass Hours", artist: "Northern Pilots", cover: 0),
        (title: "Afterglow", artist: "Northern Pilots", cover: 1),
        (title: "Undertow", artist: "Northern Pilots", cover: 2)
    ]

    /// An invented wordmark in the source's bold extended style.
    static let brand = "VELOR"

    static let keyLabels = ["Play", "Pause", "Next track", "Rewind", "Fast forward"]
}
