import CoreText
import Foundation
import SwiftUI
import UIKit

/// Bundled art and JetBrains Mono faces for the receipt-printer block.
@MainActor
enum ReceiptPrinterAssets {
    private static let fontFiles = [
        "ReceiptPrinter-JetBrainsMono-Regular",
        "ReceiptPrinter-JetBrainsMono-Medium",
        "ReceiptPrinter-JetBrainsMono-Bold",
        "ReceiptPrinter-JetBrainsMono-ExtraBold",
    ]

    private static let registered: Void = {
        for file in fontFiles {
            guard let url = resourceURL(file, fileExtension: "ttf") else {
                assertionFailure("Missing Receipt Printer font: \(file).ttf")
                continue
            }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    static let logo: UIImage = image("ReceiptPrinter-logo.png")

    /// Explicit entry point for views that want to make registration timing clear.
    static func registerFonts() {
        _ = registered
    }

    /// Maps the RN aliases in `ReceiptPrinterTokens.FONT` to the bundled PostScript faces.
    static func font(size: Double, weight: String) -> Font {
        _ = registered
        return Font.custom(postScriptName(for: weight), fixedSize: CGFloat(size))
    }

    /// UIKit counterpart for cap-top measurements in the Canvas-backed views.
    static func uiFont(size: Double, weight: String) -> UIFont {
        _ = registered
        let name = postScriptName(for: weight)
        return UIFont(name: name, size: CGFloat(size))
            ?? UIFont.monospacedSystemFont(ofSize: CGFloat(size), weight: weight == ReceiptPrinterTokens.FONT.regular ? .regular : .bold)
    }

    private static func postScriptName(for weight: String) -> String {
        switch weight {
        case ReceiptPrinterTokens.FONT.regular:
            return "JetBrainsMono-Regular"
        case ReceiptPrinterTokens.FONT.medium:
            return "JetBrainsMono-Medium"
        case ReceiptPrinterTokens.FONT.bold:
            return "JetBrainsMono-Bold"
        case ReceiptPrinterTokens.FONT.heavy:
            return "JetBrainsMono-ExtraBold"
        default:
            return "JetBrainsMono-Regular"
        }
    }

    static func assertAllLoaded() {
        #if DEBUG
        assert(logo.size != .zero, "Missing Receipt Printer asset: ReceiptPrinter-logo.png")
        for file in fontFiles {
            assert(resourceURL(file, fileExtension: "ttf") != nil, "Missing Receipt Printer font: \(file).ttf")
        }
        assert(resourceURL("ReceiptPrinter-JetBrainsMono-OFL", fileExtension: "txt") != nil, "Missing Receipt Printer font license")
        #endif
    }

    private static func image(_ name: String) -> UIImage {
        if let image = UIImage(named: name) {
            return image
        }
        let fileURL = URL(fileURLWithPath: name)
        if let url = resourceURL(fileURL.deletingPathExtension().lastPathComponent, fileExtension: fileURL.pathExtension),
           let image = UIImage(contentsOfFile: url.path) {
            return image
        }
        assertionFailure("Missing Receipt Printer asset: \(name)")
        return UIImage()
    }

    private static func resourceURL(_ name: String, fileExtension ext: String) -> URL? {
        Bundle.main.url(forResource: name, withExtension: ext)
            ?? Bundle.main.url(forResource: name, withExtension: ext, subdirectory: "Assets")
    }
}
