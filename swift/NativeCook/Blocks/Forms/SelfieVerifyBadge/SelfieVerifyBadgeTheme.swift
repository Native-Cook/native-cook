import SwiftUI
import CoreText
import UIKit

enum SelfieVerifyBadgeTheme {
    static let dark=Color(hexValue: 0x121212), title=Color(hexValue: 0xF5F5F5)
    static let body=Color(hexValue: 0x9A9A9A), ink=Color(hexValue: 0x141414)
    static let inkBody=Color(hexValue: 0x6B6B70), lime=Color(hexValue: 0xA6F23D)
    static let limeInk=Color(hexValue: 0x16210A), button=Color(hexValue: 0x2A2A2A), bracket=Color(hexValue: 0xE8E8E8)
    static let fonts: Void = {
        for weight in ["Regular","SemiBold","Bold"] {
            if let url=Bundle.main.url(forResource: "SelfieVerifyBadge-Inter-\(weight)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil)
            }
        }
    }()
    static func mix(_ a: UInt32, _ b: UInt32, _ t: Double) -> Color {
        func channel(_ shift: UInt32) -> Double {
            let x=Double((a>>shift)&255)/255, y=Double((b>>shift)&255)/255
            return pow(pow(x,2.2)+(pow(y,2.2)-pow(x,2.2))*t,1/2.2)
        }
        return Color(.sRGB,red:channel(16),green:channel(8),blue:channel(0),opacity:1)
    }
    static func font(_ weight: String, _ size: Double) -> Font { _=fonts; return .custom(weight == "SemiBold" ? "Inter-SemiBold" : "Inter24pt-\(weight)",fixedSize:size) }
}
private extension Color {
    init(hexValue v: UInt32) { self.init(.sRGB,red:Double((v>>16)&255)/255,green:Double((v>>8)&255)/255,blue:Double(v&255)/255,opacity:1) }
}
