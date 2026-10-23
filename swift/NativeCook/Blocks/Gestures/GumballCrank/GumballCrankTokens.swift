import CoreText
import Foundation
import UIKit

enum GumballCrankColor {
    static let wallTop = UIColor(rgb: 0xF1E9DC)
    static let wallBottom = UIColor(rgb: 0xE5D8C4)
    static let tableTop = UIColor(rgb: 0xC79B6B)
    static let tableEdge = UIColor(rgb: 0xE2BE92)
    static let tableFront = UIColor(rgb: 0xA87B4D)
    static let tableDeep = UIColor(rgb: 0x8A6038)
    static let ink = UIColor(rgb: 0x2A211B)
    static let mute = UIColor(rgb: 0x7A6B5E)
    static let enamel = UIColor(rgb: 0xB4232C)
    static let enamelLight = UIColor(rgb: 0xE0484E)
    static let enamelDeep = UIColor(rgb: 0x6E1016)
    static let chromeHi = UIColor(rgb: 0xFBFBF9)
    static let chromeMid = UIColor(rgb: 0xB9BCC1)
    static let chromeLow = UIColor(rgb: 0x6B6F76)
    static let chromeDeep = UIColor(rgb: 0x3E4248)
    static let slot = UIColor(rgb: 0x1C1514)
    static let card = UIColor(rgb: 0xFFFCF6)
    static let coin = UIColor(rgb: 0xE9B640)
    static let coinLight = UIColor(rgb: 0xFFE08A)
    static let coinDeep = UIColor(rgb: 0xA8761C)
    static let mint = UIColor(rgb: 0x4FBFA5)
    static let mintDeep = UIColor(rgb: 0x2B8C75)
    static let mintLight = UIColor(rgb: 0x9BE3CF)
    static let balls = [0xE8433F, 0xF6C744, 0x7CC46A, 0xF08A3C, 0xF4EFE6, 0x4C8FD6, 0xEE8DB5, 0x8B5FBF, 0x47B9A8].map { UIColor(rgb: $0) }
    static let scrim = UIColor(red: 42.0 / 255, green: 26.0 / 255, blue: 18.0 / 255, alpha: 0.42)
    static let finger = UIColor(red: 42.0 / 255, green: 33.0 / 255, blue: 27.0 / 255, alpha: 0.14)
    static let fingerEdge = UIColor(red: 42.0 / 255, green: 33.0 / 255, blue: 27.0 / 255, alpha: 0.36)
}

extension UIColor {
    convenience init(rgb: Int, alpha: CGFloat = 1) {
        self.init(red: CGFloat((rgb >> 16) & 0xFF) / 255, green: CGFloat((rgb >> 8) & 0xFF) / 255, blue: CGFloat(rgb & 0xFF) / 255, alpha: alpha)
    }
}

enum GumballCrankCopy {
    static let title = "Daily treat"
    static let subtitle = "Day 7 of your streak"
    static let hint = "Turn the handle once around"
    static let busy = "Here it comes"
    static let done = "Come back tomorrow for day 8"
    static let eyebrow = "DAY 7 REWARD"
    static let prize = "+50"
    static let unit = "coins"
    static let cardSubtitle = "Spend them on themes and streak shields."
    static let collect = "Collect"
}

enum GumballCrankMetrics {
    static let stage = CGSize(width: 402, height: 778)
    static let edge: CGFloat = 16
    static let wallet = CGRect(x: 294, y: 12, width: 92, height: 40)
    static let globe = CGPoint(x: 201, y: 254)
    static let globeRadius: CGFloat = 122
    static let globeInnerRadius: Double = 117
    static let collar = CGRect(x: 103, y: 370, width: 196, height: 22)
    static let bodyTop: CGFloat = 392
    static let bodyBottom: CGFloat = 600
    static let bodyTopWidth: CGFloat = 172
    static let bodyBottomWidth: CGFloat = 228
    static let plate = CGPoint(x: 201, y: 462)
    static let plateRadius: CGFloat = 42
    static let arm: Double = 34
    static let door = CGRect(x: 151, y: 532, width: 100, height: 50)
    static let foot = CGRect(x: 79, y: 600, width: 244, height: 14)
    static let tableY: CGFloat = 614
    static let trayY: Double = 690
    static let card = CGRect(x: 42, y: 420, width: 318, height: 236)
    static let button = CGRect(x: 66, y: 580, width: 270, height: 52)
    static let hintBaseline: CGFloat = 752
}

struct GumballCrankLayout: Equatable {
    let width: CGFloat
    let height: CGFloat
    let scale: CGFloat
    let origin: CGPoint

    static func make(width: CGFloat, height: CGFloat, top: CGFloat, bottom: CGFloat) -> Self {
        let room = height - top - max(bottom, 16)
        let scale = min(width / GumballCrankMetrics.stage.width, room / GumballCrankMetrics.stage.height)
        return .init(width: width, height: height, scale: scale, origin: .init(x: (width - GumballCrankMetrics.stage.width * scale) / 2, y: top + (room - GumballCrankMetrics.stage.height * scale) / 2))
    }
}

enum GumballCrankMotion {
    static let loop = 10.0
    static let resume = 2.0
    static let tau = Double.pi * 2
    static let quarter = Double.pi / 2
    static let grabRadius = 70.0
    static let back = 0.22
    static let kick = 220.0
    static let gravity = 900.0

    enum Demo { static let down = 0.5; static let turn = 0.7 ... 2.5; static let up = 2.65; static let tap = 5.7; static let walletBack = 9.0 }
    enum Dispense { static let drop = 0.25; static let roll = 0.3 ... 0.95; static let wobble = 0.95 ... 1.3; static let split = 1.32 ... 1.7; static let card = 1.45; static let ready = 1.95 }
    enum Collect { static let coins = 8; static let stagger = 0.05; static let fly = 0.5; static let card = 0.35 ... 0.75; static let capsule = 0.45 ... 0.85; static let done = 0.95 }
}

enum GumballCrankFonts {
    private static var didRegister = false
    static func register() {
        guard !didRegister else { return }
        didRegister = true
        ["GumballCrank-InstrumentSerif-Regular", "GumballCrank-Inter-Regular", "GumballCrank-Inter-Medium", "GumballCrank-Inter-SemiBold"].forEach { name in
            guard let url = Bundle.main.url(forResource: name, withExtension: "ttf") ?? Bundle.main.url(forResource: name, withExtension: "ttf", subdirectory: "Assets") else { return }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }
    static func serif(_ size: CGFloat) -> UIFont { register(); return UIFont(name: "InstrumentSerif-Regular", size: size) ?? .systemFont(ofSize: size) }
    static func regular(_ size: CGFloat) -> UIFont { register(); return UIFont(name: "Inter-Regular", size: size) ?? .systemFont(ofSize: size) }
    static func medium(_ size: CGFloat) -> UIFont { register(); return UIFont(name: "Inter-Medium", size: size) ?? .systemFont(ofSize: size, weight: .medium) }
    static func semibold(_ size: CGFloat) -> UIFont { register(); return UIFont(name: "Inter-SemiBold", size: size) ?? .systemFont(ofSize: size, weight: .semibold) }
}
