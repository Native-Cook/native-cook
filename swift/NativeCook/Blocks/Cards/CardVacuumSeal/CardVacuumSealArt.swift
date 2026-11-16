import SwiftUI

@MainActor
enum CardVacuumSealArt {
    typealias R = CardVacuumSealResources
    typealias M = CardVacuumSealMotion
    static let bars: [Double] = [3,3,3,3,3,3,3,3,4,6,9,12,15,22,18,30,26,38,33,46,40,56,50,64]

    static func gradient(_ c: GraphicsContext, rect: CGRect, radius: Double, colors: [UInt32],
                         start: CGPoint = CGPoint(x: 0,y: 0), end: CGPoint = CGPoint(x: 0,y: 1)) {
        let from = CGPoint(x: rect.minX+start.x*rect.width,y: rect.minY+start.y*rect.height)
        let to = CGPoint(x: rect.minX+end.x*rect.width,y: rect.minY+end.y*rect.height)
        c.fill(Path(roundedRect: rect,cornerRadius: radius),with: .linearGradient(Gradient(colors: colors.map { R.color($0) }),
                                                                              startPoint: from,endPoint: to))
    }
    static func panel(_ c: GraphicsContext, y: Double, height: Double) {
        func shade(_ y: Double) -> UInt32 {
            let t = M.clamp((y-470)/404)*0.3
            func ch(_ a: Double,_ b: Double) -> UInt32 { UInt32((a*(1-t)+b*t).rounded()) }
            return ch(28,10)<<16 | ch(27,10)<<8 | ch(32,12)
        }
        // Native panels use continuous corners, unlike the shader's circular card SDF.
        let rect = CGRect(x: 24,y: y,width: 354,height: height)
        let path = Path(RoundedRectangle(cornerRadius: 26,style: .continuous).path(in: rect).cgPath)
        c.fill(path,with: .linearGradient(Gradient(colors: [R.color(shade(y)),R.color(shade(y+height))]),
                                        startPoint: CGPoint(x: 24,y: y),endPoint: CGPoint(x: 24,y: y+height)))
    }
    static func page(_ c: GraphicsContext, resources r: R, bottom: Double, typeScale f: Double, scale: Double) {
        let ink = R.color(0xF4F3F7)
        icon(c,"back",x: 24,y: 73,size: 22)
        r.draw(c,"Cards",x: 201,top: 84-17*0.62,size: 17*f,tracking: -0.013/f,semi: true,align: 0.5)
        gradient(c,rect: CGRect(x: 346,y: 68,width: 32,height: 32),radius: 16,colors: [0xF0B2F6,0xE08BEF,0xE08BEF],
                 start: CGPoint(x: 0.2,y: 0),end: CGPoint(x: 0.8,y: 1))
        icon(c,"close",x: 353,y: 75,size: 18)
        panel(c,y: 491,height: 116)
        c.fill(Path(roundedRect: CGRect(x: 44,y: 513,width: 22,height: 22),cornerRadius: 6),with: .color(R.color(0xD8B43E)))
        // A centred native line includes the font descent below its baseline.
        r.draw(c,"$",x: 55,top: 524-13*f*1.303/2,size: 13*f,semi: true,color: R.color(0x2A2208),align: 0.5)
        r.draw(c,"Revenue",x: 75,top: 491+33-15*0.62,size: 15*f,tracking: -0.006/f)
        r.draw(c,"42,220",x: 42,top: 491+92-40*0.92,size: 40*f,tracking: -0.022/f)
        for (i,h) in bars.enumerated() {
            let x = 190+166*Double(i)/23
            let rect = h <= 3 ? CGRect(x: x-1.6,y: 491+92-3.1,width: 3.2,height: 3.2) : CGRect(x: x-1.5,y: 491+92-h,width: 3,height: h)
            c.fill(h <= 3 ? Path(ellipseIn: rect) : Path(roundedRect: rect,cornerRadius: 1.5),with: .color(ink))
        }
        panel(c,y: 619,height: bottom-619+40)
        let see = r.line("See all",size: 15*f,tracking: -0.006/f)
        let headWidth = 314-see.width-12
        var headSize = 19*f
        if r.line("Recent transactions",size: headSize,tracking: -0.017/f,semi: true).width > headWidth {
            headSize = max(headSize*0.85,headSize*headWidth/r.line("Recent transactions",size: headSize,tracking: -0.017/f,semi: true).width)
        }
        let headTop = 619+42-19*0.95
        r.draw(c,"Recent transactions",x: 44,top: headTop,size: headSize,tracking: -0.017/f,semi: true,maxWidth: headWidth)
        r.draw(c,"See all",x: 358,top: headTop+headSize-15*f,size: 15*f,tracking: -0.006/f,align: 1)
        for i in 0..<2 {
            let y = 619+66+Double(i)*64
            c.draw(r.avatars[i],in: CGRect(x: 44,y: y,width: 44,height: 44))
            r.draw(c,i == 0 ? "Tidewell Prints" : "Oren Calloway",x: 100,top: y+2,size: 16*f,tracking: -0.01/f,maxWidth: 176-12/scale)
            r.draw(c,"Money received",x: 100,top: y+24,size: 13*f,color: R.color(0x8E8D96),maxWidth: 176-12/scale)
            r.draw(c,i == 0 ? "$739.65" : "$943.65",x: 358,top: y+2,size: 16*f,tracking: -0.01/f,semi: true,align: 1)
            r.draw(c,"Completed",x: 358,top: y+24,size: 13*f,color: R.color(0x35D27E),align: 1)
        }
    }

    @MainActor static func tiles(_ c: GraphicsContext, resources r: R, look: M.Look, label: Double, reduced: Bool, typeScale f: Double) {
        for i in 0..<3 {
            var tile = c
            let x = 24+Double(i)*121, tug = i == 1 ? look.tug : look.tugSide
            tile.translateBy(x: x+56,y: 367+56-2*tug)
            let scale = i == 1 ? 1-0.04*look.press : 1
            tile.scaleBy(x: scale,y: scale); tile.translateBy(x: -56,y: -56)
            let box = CGRect(x: 0,y: 0,width: 112,height: 112)
            tile.clip(to: RoundedRectangle(cornerRadius: 26,style: .continuous).path(in: box))
            tile.fill(Path(box),with: .color(R.color(0x1C1B20)))
            var disc = tile
            disc.translateBy(x: 28,y: 14)
            disc.clip(to: Path(ellipseIn: CGRect(x: 0,y: 0,width: 56,height: 56)))
            let colors: [UInt32] = i == 0 ? [0x8A6FFB,0xB25CF6,0xE04DEE] : i == 1 ? [0xF0525A,0xDA3540,0xB8242F] : [0xF2F6FF,0xC3D5F3,0x8FA8DA]
            gradient(disc,rect: CGRect(x: 0,y: 0,width: 56,height: 56),radius: 28,colors: colors,
                     start: CGPoint(x: i == 0 ? 0.15 : 0.2,y: i == 0 ? 0.1 : 0.05),
                     end: CGPoint(x: i == 0 ? 0.85 : 0.8,y: i == 0 ? 0.95 : 1))
            if i == 1 {
                var amber = disc
                amber.translateBy(x: 28,y: 28); amber.scaleBy(x: max(label,0.001),y: max(label,0.001)); amber.translateBy(x: -28,y: -28)
                gradient(amber,rect: CGRect(x: 0,y: 0,width: 56,height: 56),radius: 28,colors: [0xF2D46A,0xD8B43E,0xB9922A],
                         start: CGPoint(x: 0.2,y: 0.05),end: CGPoint(x: 0.8,y: 1))
                disc.fill(Path(CGRect(x: 0,y: 0,width: 56,height: 56)),with: .color(R.color(0xF7B8C0,0.75*min(1,look.flash))))
                disc.fill(Path(CGRect(x: 0,y: 0,width: 56,height: 56)),with: .color(.white.opacity(0.5*min(1,look.discGlint))))
            }
            // SVG radial gloss: cx=.32, cy=.26, rx=.62, ry=.55, all relative to the 56 pt disc.
            var gloss = disc
            gloss.translateBy(x: 56*0.32,y: 56*0.26); gloss.scaleBy(x: 1,y: 0.55/0.62)
            gloss.fill(Path(CGRect(x: -100,y: -100,width: 200,height: 200)),with: .radialGradient(
                Gradient(stops: [.init(color: .white.opacity(0.42),location: 0),.init(color: .white.opacity(0.08),location: 0.55),.init(color: .white.opacity(0),location: 1)]),
                center: .zero,startRadius: 0,endRadius: 56*0.62))
            if i == 0 { icon(disc,"eye",x: 16,y: 16,size: 24) }
            else if i == 2 { icon(disc,"nut",x: 15,y: 15,size: 26) }
            else {
                icon(disc,"shield",x: 15,y: 15,size: 26)
                let o = M.band(0,0.45,label), k = M.band(0.5,1,label)
                var open = disc
                open.opacity *= 1-o
                open.translateBy(x: 28,y: 28); open.scaleBy(x: 1,y: 1-0.6*o); open.translateBy(x: -28,y: -28)
                icon(open,"open",x: 15,y: 15,size: 26)
                var check = disc
                check.opacity *= k; check.translateBy(x: 28,y: 28); check.scaleBy(x: 0.75+0.25*k,y: 0.75+0.25*k); check.translateBy(x: -28,y: -28)
                icon(check,"check",x: 15,y: 15,size: 26)
            }
            let top = 94-14.0
            if i != 1 { r.draw(tile,i == 0 ? "Show details" : "Settings",x: 56,top: top,size: 14*f,tracking: -0.004/f,align: 0.5) }
            else {
                let ws = r.line("Protect",size: 14*f,tracking: -0.004/f).width
                let wa = r.line(" Card",size: 14*f,tracking: -0.004/f).width
                let wb = r.line("ed",size: 14*f,tracking: -0.004/f).width
                let left = (112-ws-wa+(wa-wb)*M.band(0.1,0.9,label))/2
                let o = M.band(0,0.45,label), k = M.band(0.5,1,label), dy = reduced ? 0.0 : 5.0
                r.draw(tile,"Protect",x: left,top: top,size: 14*f,tracking: -0.004/f)
                r.draw(tile," Card",x: left+ws,top: top+dy*o,size: 14*f,tracking: -0.004/f,opacity: 1-o)
                r.draw(tile,"ed",x: left+ws,top: top-dy*(1-k),size: 14*f,tracking: -0.004/f,opacity: k)
            }
        }
    }

    static func icon(_ context: GraphicsContext,_ name: String,x: Double,y: Double,size: Double) {
        var c = context
        c.translateBy(x: x,y: y); c.scaleBy(x: size/24,y: size/24)
        let d: String
        switch name {
        case "back": d = "M19.5 12H5.2M11 6.2L5.2 12l5.8 5.8"
        case "close": d = "M7.5 7.5l9 9M16.5 7.5l-9 9"
        case "eye": d = "M2.6 12c2.3-4.1 5.6-6.2 9.4-6.2s7.1 2.1 9.4 6.2c-2.3 4.1-5.6 6.2-9.4 6.2S4.9 16.1 2.6 12z"
        case "shield": d = "M12 2.6l7.4 2.9c.5.2.8.6.8 1.1v4.9c0 4.6-3 8.6-7.6 10.1-.4.1-.8.1-1.2 0C6.8 20.1 3.8 16.1 3.8 11.5V6.6c0-.5.3-.9.8-1.1L12 2.6z"
        case "open": d = "M12 8.4v5.2"
        case "check": d = "M9.2 12.4l2 2 3.8-4.4"
        default: d = "M10.6 3.3c.9-.5 1.9-.5 2.8 0l5.6 3.2c.9.5 1.4 1.4 1.4 2.4v6.2c0 1-.5 1.9-1.4 2.4l-5.6 3.2c-.9.5-1.9.5-2.8 0L5 17.5c-.9-.5-1.4-1.4-1.4-2.4V8.9c0-1 .5-1.9 1.4-2.4l5.6-3.2z"
        }
        let path = svg(d), strokes = ["back","close","open","check"].contains(name)
        let color = R.color(name == "open" ? 0xDA3540 : name == "check" ? 0x2A2208 : 0xF4F3F7)
        if strokes { c.stroke(path,with: .color(color),style: StrokeStyle(lineWidth: name == "back" ? 1.9 : name == "close" ? 2.2 : 2.1,lineCap: .round,lineJoin: .round)) }
        else { c.fill(path,with: .color(color)) }
        if name == "eye" || name == "nut" {
            let radius = name == "eye" ? 3.1 : 3.2
            c.fill(Path(ellipseIn: CGRect(x: 12-radius,y: 12-radius,width: 2*radius,height: 2*radius)),with: .color(R.color(name == "eye" ? 0xB25CF6 : 0xA9C2EA)))
            if name == "eye" { c.fill(Path(ellipseIn: CGRect(x: 10.85,y: 10.85,width: 2.3,height: 2.3)),with: .color(R.color(0xF4F3F7))) }
        }
    }

    /// Only the SVG commands used by cvs-icons.tsx; retains every source control point.
    static func svg(_ source: String) -> Path {
        let regex = try! NSRegularExpression(pattern: "[A-Za-z]|[-+]?(?:[0-9]*\\.[0-9]+|[0-9]+)")
        let ns = source as NSString
        let tokens = regex.matches(in: source,range: NSRange(location: 0,length: ns.length)).map { ns.substring(with: $0.range) }
        var i = 0, command = "", p = CGPoint.zero, start = CGPoint.zero, lastControl = CGPoint.zero, previous = ""
        var path = Path()
        func number() -> Double { defer { i += 1 }; return Double(tokens[i])! }
        func point(_ relative: Bool) -> CGPoint { let x = number(), y = number(); return CGPoint(x: x+(relative ? p.x : 0),y: y+(relative ? p.y : 0)) }
        while i < tokens.count {
            if Double(tokens[i]) == nil { command = tokens[i]; i += 1 }
            let relative = command == command.lowercased()
            switch command.uppercased() {
            case "M": p = point(relative); start = p; path.move(to: p); command = relative ? "l" : "L"
            case "L": p = point(relative); path.addLine(to: p)
            case "H": p.x = number()+(relative ? p.x : 0); path.addLine(to: p)
            case "V": p.y = number()+(relative ? p.y : 0); path.addLine(to: p)
            case "C":
                let a = point(relative), b = point(relative), end = point(relative)
                path.addCurve(to: end,control1: a,control2: b); lastControl = b; p = end
            case "S":
                let a = ["C","S"].contains(previous) ? CGPoint(x: 2*p.x-lastControl.x,y: 2*p.y-lastControl.y) : p
                let b = point(relative), end = point(relative)
                path.addCurve(to: end,control1: a,control2: b); lastControl = b; p = end
            case "Z": path.closeSubpath(); p = start; command = ""
            default: return path
            }
            previous = command.uppercased()
        }
        return path
    }
}
