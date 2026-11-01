import CoreText
import SwiftUI
import UIKit

enum MailboxInboxTheme {
    static let mail: [(from: String, text: String, time: String, color: UInt32)] = [
        ("Grandma Rose", "Your parcel of cookies is on its way!", "9:41", 0xFF8A65),
        ("Kinto Bank", "Your October statement is ready", "8:20", 0x5A6BD0),
        ("Pip & Co. Studio", "Invitation: spring open house", "Yesterday", 0x2F9E62),
        ("City Library", "The book you reserved is waiting", "Mon", 0xE0A21F)
    ]
    static func color(_ hex: UInt32) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255)/255, green: Double((hex >> 8) & 255)/255, blue: Double(hex & 255)/255)
    }
    static let registered: Void = {
        for w in ["Regular", "SemiBold", "Bold"] {
            if let url = Bundle.main.url(forResource: "MailboxInbox-Inter-\(w)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()
    static func postScript(_ weight: String) -> String {
        weight == "SemiBold" ? "Inter-SemiBold" : "Inter24pt-\(weight)"
    }
    static func font(_ weight: String, _ size: Double) -> Font {
        _ = registered
        return .custom(postScript(weight), fixedSize: size)
    }
    private static let heights: [Double: Double] = {
        _ = registered
        return Dictionary(uniqueKeysWithValues: [13.0,14,15,16,18,30].map { size in
            let font = UIFont(name: postScript("Regular"), size:size)!
            return (size,ceil(font.lineHeight * 3) / 3)
        })
    }()
    static func lineHeight(_ weight: String, _ size: Double) -> Double { heights[size] ?? size }
}

enum MailboxInboxArt {
    private static let s = 88.0
    private static func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> Path {
        Path(roundedRect:CGRect(x:x,y:y,width:w,height:h),cornerSize:CGSize(width:r,height:r))
    }
    // Cache all vector geometry. Only the door/flag transforms and opacities change.
    private static let ground = Path(ellipseIn:CGRect(x:-s*0.9,y:s*1.55,width:s*1.8,height:s*0.3))
    private static let post = rect(-s*0.12,s*0.4,s*0.24,s*1.3,6)
    private static let back = rect(-s*0.62,-s*0.5,s*1.46,s*0.95,s*0.47)
    private static let body = rect(-s*0.7,-s*0.55,s*1.4,s*0.98,s*0.47)
    private static let shine = rect(-s*0.5,-s*0.48,s*0.9,s*0.18,s*0.09)
    private static let mw=s*0.62, mh=s*0.82, mx = -s*0.7-s*0.62*0.1, my = -s*0.48
    private static let mouth = rect(mx,my,mw,mh,mw/2)
    private static let knob = Path(ellipseIn:CGRect(x:mx+mw/2-s*0.06,y:my+mh*0.3-s*0.06,width:s*0.12,height:s*0.12))
    private static let flagStem = rect(-s*0.04,-s*0.05,s*0.62,s*0.1,3)
    private static let flagEnd = rect(s*0.4,-s*0.05,s*0.26,s*0.26,3)
    private static let pivot = Path(ellipseIn:CGRect(x:-s*0.05,y:-s*0.05,width:s*0.1,height:s*0.1))
    static func box(_ context: GraphicsContext, cx: Double, cy: Double, open: Double, reduced: Bool) {
        let door = reduced ? 0 : MailboxInboxMotion.smoother(open * 2.2)
        let flag = 1 - MailboxInboxMotion.smoother(open * 2.4)
        var c=context
        c.translateBy(x:cx,y:cy)
        func fill(_ path: Path, _ color: UInt32, _ alpha: Double = 1) {
            c.fill(path,with:.color(MailboxInboxTheme.color(color).opacity(alpha)))
        }
        fill(ground,0x8C6A50,0.14)
        fill(post,0x9C7356)
        fill(back,0xC8382C)
        fill(body,0xE8473A)
        fill(shine,0xFF7A6B,0.55)
        fill(mouth,0x7A1E18,door)
        var hinged=c
        hinged.translateBy(x:mx+mw/2,y:my+mh)
        hinged.scaleBy(x:1,y:max(0.05,cos(door * .pi * 0.55)))
        hinged.translateBy(x:-(mx+mw/2),y:-(my+mh))
        hinged.fill(mouth,with:.color(MailboxInboxTheme.color(door > 0.5 ? 0xC8382C : 0xF25A4C)))
        hinged.fill(knob,with:.color(MailboxInboxTheme.color(0xFFE07A)))
        var side=c
        side.translateBy(x:s*0.62,y:-s*0.05)
        let pivotContext=side
        side.rotate(by:.degrees(-90*flag))
        side.fill(flagStem,with:.color(MailboxInboxTheme.color(0x3B3F4A)))
        side.fill(flagEnd,with:.color(MailboxInboxTheme.color(0xFFC23D)))
        pivotContext.fill(pivot,with:.color(MailboxInboxTheme.color(0x3B3F4A)))
    }
}
