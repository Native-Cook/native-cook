import CoreText
import ImageIO
import SwiftUI
import UIKit

final class CashbackCloudCardResources {
    static let shared = CashbackCloudCardResources()
    let balanceFont = Font.custom("Inter-SemiBold", fixedSize: 16)
    let cashFont = Font.custom("Inter24pt-Bold", fixedSize: 44)
    let balanceX: Double
    private let formatter: NumberFormatter = {
        let f = NumberFormatter()
        f.locale = Locale(identifier: "en_US")
        f.numberStyle = .decimal
        f.minimumFractionDigits = 2
        f.maximumFractionDigits = 2
        return f
    }()
    func mainText(_ value: Double) -> String { "$" + (formatter.string(from: NSNumber(value: value)) ?? "1,284.50") }
    let cloud: Image
    let coin: Image
    let edge: Image
    init() {
        for name in ["Inter-Regular", "Inter-SemiBold", "Inter-Bold", "Ionicons"] {
            if let url = Bundle.main.url(forResource: "CashbackCloudCard-" + name, withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
        let balanceLabelFont = UIFont(name: "Inter24pt-Regular", size: 14) ?? .systemFont(ofSize: 14)
        balanceX = 28 + ("Main balance" as NSString).size(withAttributes: [.font: balanceLabelFont]).width
        func image(_ name: String) -> Image {
            guard let url = Bundle.main.url(forResource: "CashbackCloudCard-" + name, withExtension: "png"),
                  let source = CGImageSourceCreateWithURL(url as CFURL, nil),
                  let cg = CGImageSourceCreateImageAtIndex(source, 0, [kCGImageSourceShouldCacheImmediately: true] as CFDictionary)
            else { return Image(uiImage: UIImage()) }
            return Image(uiImage: UIImage(cgImage: cg)).interpolation(.none)
        }
        cloud = image("cloud"); coin = image("coin"); edge = image("coin-edge")
    }
    static func color(_ hex: UInt32) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: 1)
    }
}
