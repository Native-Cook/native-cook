import SwiftUI
import CoreText
import UIKit

enum VoiceOrbScannerResources {
    static let registered: Void = {
        for name in ["Outfit-Medium", "Inter-Regular", "Inter-Medium", "Inter-SemiBold"] {
            if let url = Bundle.main.url(forResource: "VoiceOrbScanner-\(name)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()
    private struct Metric { let font: Font; let height: Double }
    private static let metrics: [String: Metric] = {
        _ = registered
        let entries: [(String, Double)] = [("Inter-Medium",15),("Outfit-Medium",22),("Inter-Medium",12),("Inter-Regular",14),("Outfit-Medium",17),("Inter-Regular",12),("Inter-SemiBold",16),("Outfit-Medium",18),("Inter-Regular",12.5)]
        return Dictionary(uniqueKeysWithValues: entries.map { face, size in
            let postScript = face == "Inter-Regular" ? "Inter24pt-Regular" : face
            return ("\(face)-\(size)",Metric(font:.custom(postScript,fixedSize:size),height:ceil((UIFont(name:postScript,size:size) ?? .systemFont(ofSize:size)).lineHeight)))
        })
    }()
    static func font(_ face: String, _ size: Double) -> Font { metrics["\(face)-\(size)"]!.font }
    static func line(_ face: String, _ size: Double) -> Double { metrics["\(face)-\(size)"]!.height }
    // React Native style colours round CSS alpha into their packed 8-bit channel.
    static func color(_ hex: UInt, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16)&255)/255, green: Double((hex >> 8)&255)/255, blue: Double(hex&255)/255, opacity: (alpha * 255).rounded() / 255)
    }
}
