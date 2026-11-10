import SwiftUI
import CoreText
import UIKit

enum WindChimeTonesResources {
    static let tools=["AirPlay","EQ","Add to pair"]
    static let registered: Void = {
        for name in ["Inter-Regular","Inter-Medium","Inter-SemiBold"] {
            if let url=Bundle.main.url(forResource:"WindChimeTones-"+name,withExtension:"ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil)
            }
        }
    }()
    static func font(_ name: String, _ size: Double) -> Font {
        _ = registered
        return .custom(name,fixedSize:size)
    }
    static let title=font("Inter-SemiBold",14.5)
    static let toolText=font("Inter-Medium",11.5)
    static let track=font("Inter24pt-Regular",13)
    static func color(_ hex: Int, _ alpha: Double=1) -> Color {
        Color(.sRGB,red:Double(hex>>16 & 255)/255,green:Double(hex>>8 & 255)/255,blue:Double(hex & 255)/255,opacity:alpha)
    }
    static let page=color(0x2A2A2B), card=color(0x3A3A3B), red=color(0xF0453A)
    struct Mark {
        var path: Path
        var stroke: Double=0
        var color=Color.white
    }
    struct Icon {
        var width, height: Double
        var marks: [Mark]
    }
    static let back=Icon(width:10,height:16,marks:[Mark(path:path("M8 1.5 L1.8 8 L8 14.5"),stroke:1.9)])
    static let more=Icon(width:4,height:16,marks:[Mark(path:Path(ellipseIn:CGRect(x:0.2,y:1.2,width:3.6,height:3.6))),Mark(path:Path(ellipseIn:CGRect(x:0.2,y:11.2,width:3.6,height:3.6)))])
    static let power=Icon(width:22,height:22,marks:[Mark(path:path("M11 2.5 V10.5 M6.2 5.3 A7.5 7.5 0 1 0 15.8 5.3"),stroke:2)])
    static let airPlay=Icon(width:22,height:20,marks:[Mark(path:Path(roundedRect:CGRect(x:1.5,y:1.5,width:19,height:13),cornerRadius:2),stroke:1.6),Mark(path:path("M11 11 L16 18.5 H6 Z"))])
    static let eq=Icon(width:24,height:20,marks:(0..<6).map { i in
        let x=2+i*4, h=[3,6,9,5,7,2][i]
        return Mark(path:path("M\(x) \(10-h) V\(10+h)"),stroke:1.8)
    })
    static let pair=Icon(width:20,height:20,marks:[Mark(path:path("M8.5 11.5 L11.5 8.5 M7 8 L5 10 A3.5 3.5 0 0 0 10 15 L12 13 M13 12 L15 10 A3.5 3.5 0 0 0 10 5 L8 7"),stroke:1.7)])
    static let speaker=Icon(width:30,height:30,marks:[Mark(path:path("M4 11 H9 L16 5 V25 L9 19 H4 Z"),color:color(0xC9C9CC)),Mark(path:path("M20 10 A6 6 0 0 1 20 20 M23 7 A10 10 0 0 1 23 23"),stroke:1.8,color:color(0xC9C9CC))])
    static let previous=Icon(width:30,height:22,marks:[Mark(path:path("M14 2 L3 11 L14 20 Z M27 2 L16 11 L27 20 Z"))])
    static let next=Icon(width:30,height:22,marks:[Mark(path:path("M3 2 L14 11 L3 20 Z M16 2 L27 11 L16 20 Z"))])
    static let play=Icon(width:24,height:30,marks:[Mark(path:path("M3 2 L22 15 L3 28 Z"))])
    static let pause=Icon(width:24,height:28,marks:[Mark(path:Path(roundedRect:CGRect(x:3,y:2,width:6,height:24),cornerRadius:1.5)),Mark(path:Path(roundedRect:CGRect(x:15,y:2,width:6,height:24),cornerRadius:1.5))])

    /// SVG's absolute M/L/H/V/A/Z commands, resolved once for the reference's static artwork.
    private static func path(_ svg: String) -> Path {
        let regex=try! NSRegularExpression(pattern:"[A-Za-z]|[-+]?(?:[0-9]*\\.)?[0-9]+")
        let tokens=regex.matches(in:svg,range:NSRange(svg.startIndex...,in:svg)).map { String(svg[Range($0.range,in:svg)!]) }
        var p=Path(), point=CGPoint.zero, origin=CGPoint.zero
        var index=0, command="M"
        func number() -> Double { defer { index += 1 }; return Double(tokens[index])! }
        while index < tokens.count {
            if tokens[index].first!.isLetter { command=tokens[index]; index += 1 }
            switch command {
            case "M": point=CGPoint(x:number(),y:number()); p.move(to:point); origin=point; command="L"
            case "L": point=CGPoint(x:number(),y:number()); p.addLine(to:point)
            case "H": point.x=number(); p.addLine(to:point)
            case "V": point.y=number(); p.addLine(to:point)
            case "Z": p.closeSubpath(); point=origin
            case "A":
                var rx=number(), ry=number()
                _ = number() // All reference arcs have zero rotation.
                let large=number() != 0, sweep=number() != 0
                let end=CGPoint(x:number(),y:number())
                let dx=(point.x-end.x)/2, dy=(point.y-end.y)/2
                let factor=sqrt(max(1,dx*dx/(rx*rx)+dy*dy/(ry*ry)))
                rx *= factor; ry *= factor
                let denominator=rx*rx*dy*dy+ry*ry*dx*dx
                let scale=(large == sweep ? -1.0 : 1.0)*sqrt(max(0,(rx*rx*ry*ry-denominator)/max(denominator,1e-12)))
                let ox=scale*rx*dy/ry, oy = -scale*ry*dx/rx
                let cx=ox+(point.x+end.x)/2, cy=oy+(point.y+end.y)/2
                var angle=atan2((dy-oy)/ry,(dx-ox)/rx)
                var delta=atan2((-dy-oy)/ry,(-dx-ox)/rx)-angle
                if sweep && delta < 0 { delta += 2 * .pi }
                if !sweep && delta > 0 { delta -= 2 * .pi }
                let count=max(1,Int(ceil(abs(delta)/(.pi/2)))), step=delta/Double(count)
                for _ in 0..<count {
                    let a=angle, b=angle+step, k=4/3.0*tan(step/4)
                    p.addCurve(to:CGPoint(x:cx+rx*cos(b),y:cy+ry*sin(b)),control1:CGPoint(x:cx+rx*(cos(a)-k*sin(a)),y:cy+ry*(sin(a)+k*cos(a))),control2:CGPoint(x:cx+rx*(cos(b)+k*sin(b)),y:cy+ry*(sin(b)-k*cos(b))))
                    angle=b
                }
                point=end
            default: fatalError("Unsupported WindChimeTones SVG command")
            }
        }
        return p
    }
}

struct WindChimeTonesIcon: View {
    var icon: WindChimeTonesResources.Icon
    var tint: Color? = nil
    var body: some View {
        Canvas { context,_ in
            for mark in icon.marks {
                let paint=GraphicsContext.Shading.color(tint ?? mark.color)
                if mark.stroke > 0 { context.stroke(mark.path,with:paint,style:StrokeStyle(lineWidth:mark.stroke,lineCap:.round,lineJoin:.round)) }
                else { context.fill(mark.path,with:paint) }
            }
        }.frame(width:icon.width,height:icon.height).accessibilityHidden(true)
    }
}
