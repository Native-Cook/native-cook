import SwiftUI
import UIKit
import CoreText

enum GlassTilesFuseTheme {
    static func color(_ hex: UInt32) -> Color { Color(.sRGB,red:Double((hex>>16)&255)/255,green:Double((hex>>8)&255)/255,blue:Double(hex&255)/255,opacity:1) }
    static let page=color(0x111111), sheet=color(0x141414), grabber=color(0x3A3A3C)
    static let rowName=color(0xE8E8E8), rowSub=color(0x8E8E93), pill=color(0x232323), pillInk=color(0xD6D6D6)
    static let title=color(0xF2F2F2), body=color(0x999999), button=color(0xF2F2F2), buttonInk=color(0x111111)
    static let rows=[("Contacts","On this iPhone"),("Mail","iCloud, Outlook"),("Calendar","Work, Family"),("Cloud Drive","2 folders shared")]
    static let names: [String:String] = {
        var names: [String:String]=[:]
        for weight in ["Regular","SemiBold","Bold"] {
            guard let url=Bundle.main.url(forResource:"GlassTilesFuse-Inter-\(weight)",withExtension:"ttf") else { continue }
            CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil)
            if let descs=CTFontManagerCreateFontDescriptorsFromURL(url as CFURL) as? [CTFontDescriptor],let first=descs.first,
               let name=CTFontDescriptorCopyAttribute(first,kCTFontNameAttribute) as? String { names[weight]=name }
        }
        return names
    }()
    static func font(_ weight: String, _ size: Double) -> Font { .custom(names[weight] ?? "Inter-\(weight)",fixedSize:size) }
}
