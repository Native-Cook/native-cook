import CoreText
import UIKit

struct CassetteDeckRect {
    let x: CGFloat
    let y: CGFloat
    let width: CGFloat
    let height: CGFloat
    let radius: CGFloat

    init(_ x: CGFloat, _ y: CGFloat, _ width: CGFloat, _ height: CGFloat, _ radius: CGFloat = 0) {
        self.x = x
        self.y = y
        self.width = width
        self.height = height
        self.radius = radius
    }

    var cgRect: CGRect { CGRect(x: x, y: y, width: width, height: height) }
}

struct CassetteDeckPoint {
    let x: CGFloat
    let y: CGFloat
}

enum CassetteDeckTheme {
    static let stage = CGSize(width: 402, height: 778)
    static let edge: CGFloat = 16
    static let brandBaseline: CGFloat = 46
    static let modelBaseline: CGFloat = 66
    static let hintBaseline: CGFloat = 748
    static let fingerSize: CGFloat = 44

    static let plate = UIColor(hex: 0xCFD2D5)
    static let plateLight = UIColor(hex: 0xE6E8EA)
    static let plateDeep = UIColor(hex: 0xA9ADB2)
    static let engrave = UIColor(hex: 0x4A4E54)
    static let engraveMuted = UIColor(hex: 0x7B8086)
    static let wellColor = UIColor(hex: 0x121314)
    static let shell = UIColor(hex: 0x2A2B2E)
    static let shellLight = UIColor(hex: 0x3A3B3F)
    static let label = UIColor(hex: 0xF1E8D4)
    static let labelLine = UIColor(red: 160 / 255, green: 120 / 255, blue: 90 / 255, alpha: 0.35)
    static let labelBand = UIColor(hex: 0xD9573B)
    static let pen = UIColor(hex: 0x1F2A63)
    static let tape = UIColor(hex: 0x3B2418)
    static let tapeLight = UIColor(hex: 0x6B4430)
    static let hub = UIColor(hex: 0xF4F2EE)
    static let counter = UIColor(hex: 0x0E0F10)
    static let digit = UIColor(hex: 0xF2F0EA)
    static let led = UIColor(hex: 0xFF8A2A)
    static let ledOff = UIColor(hex: 0x3A2A20)
    static let meterOn = UIColor(hex: 0x7BE38E)
    static let meterHot = UIColor(hex: 0xFFB34A)
    static let meterPeak = UIColor(hex: 0xFF5A3C)
    static let meterOff = UIColor(hex: 0x23262A)
    static let keyTopColor = UIColor(hex: 0xE9EBED)
    static let keyFront = UIColor(hex: 0x9EA3A9)
    static let keyDeep = UIColor(hex: 0x6E737A)
    static let playMark = UIColor(hex: 0xE2462F)
    static let finger = UIColor(red: 30 / 255, green: 32 / 255, blue: 36 / 255, alpha: 0.16)
    static let fingerEdge = UIColor(red: 30 / 255, green: 32 / 255, blue: 36 / 255, alpha: 0.4)

    static let power = CassetteDeckPoint(x: 370, y: 40)
    static let well = CassetteDeckRect(16, 98, 370, 300, 18)
    static let cassette = CassetteDeckRect(36, 132, 330, 226, 12)
    static let labelRect = CassetteDeckRect(52, 146, 298, 128, 6)
    static let window = CassetteDeckRect(116, 210, 170, 52, 26)
    static let reels = [CassetteDeckPoint(x: 148, y: 236), CassetteDeckPoint(x: 254, y: 236)]
    static let hubRadius: CGFloat = 14
    static let packMin: Double = 17
    static let packMax: Double = 44
    static let readoutY: CGFloat = 436
    static let readoutHeight: CGFloat = 64
    static let counterX: CGFloat = 16
    static let metersX: CGFloat = 178
    static let metersWidth: CGFloat = 208
    static let keysY: CGFloat = 552
    static let keysHeight: CGFloat = 140
    static let keyGap: CGFloat = 8
    static let keyTop: CGFloat = 22
    static let keyWidth = (stage.width - 2 * edge - 4 * keyGap) / 5

    static let brand = "VELOR"
    static let model = "TC-7  STEREO CASSETTE DECK"
    static let side = "A"
    static let title = "Glass Hours"
    static let subtitle = "for Jun, summer mix"
    static let counterLabel = "TAPE COUNTER"
    static let memo = "MEMO"
    static let hints = ["Press play", "Playing side A", "Winding", "Rewinding to 000", "Paused"]
    static let keyNames = ["Rewind", "Play", "Fast forward", "Stop", "Pause"]

    static func keyX(_ index: Int) -> CGFloat {
        edge + CGFloat(index) * (keyWidth + keyGap)
    }
}

struct CassetteDeckFonts {
    let regular: UIFont
    let medium: UIFont
    let semibold: UIFont
    let handwriting: UIFont

    init() {
        Self.registerOnce
        regular = UIFont(name: "Inter24pt-Regular", size: 12) ?? .systemFont(ofSize: 12)
        medium = UIFont(name: "Inter-Medium", size: 12) ?? .systemFont(ofSize: 12, weight: .medium)
        semibold = UIFont(name: "Inter-SemiBold", size: 12) ?? .systemFont(ofSize: 12, weight: .semibold)
        handwriting = UIFont(name: "CoveredByYourGrace", size: 12) ?? .systemFont(ofSize: 12)
    }

    private static let registerOnce: Void = {
        ["Inter-Regular", "Inter-Medium", "Inter-SemiBold", "CoveredByYourGrace"].forEach { name in
            guard let url = Bundle.main.url(forResource: "CassetteDeck-\(name)", withExtension: "ttf") else { return }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()
}

extension UIColor {
    fileprivate convenience init(hex: Int, alpha: CGFloat = 1) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: alpha
        )
    }
}
