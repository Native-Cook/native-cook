import UIKit
import CoreText

/// Exact existing Expo MCI font/glyphs, scoped registration without Info.plist changes.
@MainActor
final class PrismaticGameProfileIcon:UILabel {
    private static let glyphs:[String:UInt32] = [
        "cog":984211,"account-plus":983060,"chart-donut":985007,
        "chevron-left":983361,"chevron-right":983362,"play":984074,
        "home":983772,"account":983044,"gamepad-variant":983703,"cart":983312,
        "chevron-down":983360,"close":983382,"thumb-up":984339,
    ]
    private static let registered:Void = {
        guard let url=Bundle.main.url(forResource:"PrismaticGameProfile-MaterialCommunityIcons",withExtension:"ttf") else{return}
        // Already-registered fonts remain usable; registration is process-local.
        CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil)
    }()
    static func materialFont(_ size:Double)->UIFont {
        _=registered
        return UIFont(name:"MaterialDesignIcons",size:size) ?? .systemFont(ofSize:size)
    }
    static func glyph(_ name:String)->String {
        guard let value=glyphs[name],let scalar=UnicodeScalar(value) else{return ""}
        return String(scalar)
    }
    init(_ name:String,size:Double,color:UIColor){super.init(frame:CGRect(x:0,y:0,width:size,height:size));textAlignment = .center;font=Self.materialFont(size);text=Self.glyph(name);textColor=color;isAccessibilityElement=false;accessibilityElementsHidden=true;isUserInteractionEnabled=false;adjustsFontSizeToFitWidth=false}
    required init?(coder:NSCoder){fatalError("init(coder:) has not been implemented")}
}
