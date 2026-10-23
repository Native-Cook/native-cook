import CoreText
import SwiftUI

enum CardBalanceWalletAssets {
    static let poppinsRegular = "Poppins-Regular"
    static let poppinsMedium = "Poppins-Medium"
    static let poppinsSemibold = "Poppins-SemiBold"

    static let profile: UIImage = image(named: "CardBalanceWallet-carmen.jpg")
    static let field: UIImage = image(named: "CardBalanceWallet-card-field.png")

    static func font(_ name: String, size: Double) -> Font {
        _ = registeredFonts
        return .custom(name, fixedSize: size)
    }

    static func uiFont(_ name: String, size: Double) -> UIFont {
        _ = registeredFonts
        guard let font = UIFont(name: name, size: size) else {
            assertionFailure("Missing Card Balance Wallet font: \(name)")
            return .systemFont(ofSize: size)
        }
        return font
    }

    private static let registeredFonts: Void = {
        let files = [
            "CardBalanceWallet-Poppins-Regular",
            "CardBalanceWallet-Poppins-Medium",
            "CardBalanceWallet-Poppins-SemiBold",
            "CardBalanceWallet-InterDisplay-Bold",
        ]
        for file in files {
            guard let url = Bundle.main.url(forResource: file, withExtension: "ttf") else {
                assertionFailure("Missing Card Balance Wallet font file: \(file).ttf")
                continue
            }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    private static func image(named name: String) -> UIImage {
        guard let image = UIImage(named: name) else {
            assertionFailure("Missing Card Balance Wallet image: \(name)")
            return UIImage()
        }
        return image
    }
}
