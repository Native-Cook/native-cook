import CoreText
import UIKit
import SwiftUI

enum SunriseCocktailPourResources {
    static let register: Void = {
        for name in ["InstrumentSerif-Regular","Inter-Regular","Inter-Medium","Inter-SemiBold"] {
            if let url=Bundle.main.url(forResource:"SunriseCocktailPour-"+name,withExtension:"ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil)
            }
        }
    }()
    static func color(_ hex: Int) -> Color {
        Color(.sRGB,red:Double((hex>>16)&255)/255,green:Double((hex>>8)&255)/255,blue:Double(hex&255)/255)
    }
    private static let display=Font.custom("InstrumentSerif-Regular",fixedSize:40)
    private static let regular=Font.custom("Inter24pt-Regular",fixedSize:15)
    private static let eyebrow=Font.custom("Inter-Medium",fixedSize:12)
    private static let note=Font.custom("Inter-Medium",fixedSize:14)
    private static let semibold=Font.custom("Inter-SemiBold",fixedSize:17)
    static func font(_ family: String, _ size: Double) -> Font {
        switch family {
        case "InstrumentSerif-Regular": return display
        case "Inter24pt-Regular": return regular
        case "Inter-Medium": return size==12 ? eyebrow : note
        default: return semibold
        }
    }
    static let back: Path = {
        var p=Path()
        p.move(to:CGPoint(x:26,y:14)); p.addLine(to:CGPoint(x:18,y:22)); p.addLine(to:CGPoint(x:26,y:30))
        return p
    }()
    static let ctas=["Pour Grenadine","Stir","Serve"]
    static let notes=["Pour slowly down the side. It sinks.","Or leave it: the sunrise is the point.","Garnish with an orange wheel."]
    static let labels=["A highball of orange juice on ice","A tequila sunrise, red settling under orange","A stirred tequila sunrise"]
}

struct SunriseCocktailPourPressStyle: ButtonStyle {
    var reduced: Bool
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed && !reduced ? 0.96 : 1)
            .opacity(configuration.isPressed && reduced ? 0.72 : 1)
            .animation(.timingCurve(0.23,1,0.32,1,duration:0.15),value:configuration.isPressed)
    }
}
