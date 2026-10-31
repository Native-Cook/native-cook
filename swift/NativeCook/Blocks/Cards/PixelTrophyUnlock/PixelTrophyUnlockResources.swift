import UIKit
import CoreText
import SwiftUI

// The reference scene grids are rasterised once at the display's 3× capture scale.
final class PixelTrophyUnlockResources {
    static let shared = PixelTrophyUnlockResources()
    static let sweepClip = Path(path(0,0,80,98,8))
    static let smallBounds = Path(CGRect(x:0,y:0,width:80,height:98))
    static let shadowBounds = Path(CGRect(x:-54,y:-64,width:108,height:156))
    static let lockBody = UIBezierPath(roundedRect:CGRect(x:33,y:43.08,width:14,height:11),cornerRadius:2.5).cgPath
    static let shackle: CGPath = {
        let arc = CGMutablePath()
        arc.addArc(center:.zero,radius:1,startAngle:.pi,endAngle:2 * .pi,clockwise:false,transform:CGAffineTransform(a:4.5,b:0,c:0,d:6,tx:40,ty:42.08))
        return arc
    }()
    static let sweepGradient = Gradient(stops:(0...64).map { i in
        let t = Double(i)/64, k = 1-abs(2*t-1)
        return Gradient.Stop(color:Color(.sRGB,red:1,green:1,blue:(255-15*k)/255,opacity:0.85*k),location:t)
    })
    struct Scene {
        let sky: [String]; let sun: [Double]; let sunColor: String; let glow: String
        let moon: Bool; let stars: Bool; let skyline: String; let land: [String]
        let emblem: String; let ink: String; let shade: String
    }
    static let emblems: [String: [String]] = [
        "star": [".....#.....", ".....#.....", "....###....", "....###....", "###########", ".#########.", "..#######..", "..###+###..", ".###...###.", ".##.....##.", ".#.......#."],
        "burst": ["#....#....#", ".#...#...#.", "..#..#..#..", "...#####...", "....###....", "###########", "....###....", "...#####...", "..#..#..#..", ".#...#...#.", "#....#....#"],
        "flame": [".....#.....", "....##.....", "....###....", "...####.#..", "..#######..", "..###+###..", ".###+++###.", ".##+++++##.", ".##++#++##.", "..##+#+##..", "...#####..."],
        "shoe": ["...........", "..####.....", "..#++#.....", "..#++#.....", "..#++####..", "..#+++++##.", ".##+++++++#", ".#########+", ".#########.", "..++.++.++.", "..........."],
        "ten": ["...........", ".##..####..", "###.##..##.", ".##.##..##.", ".##.##..##.", ".##.##..##.", ".##.##..##.", ".##.##..##.", "####.####..", "...........", "+++++++++++"],
        "bolt": ["......###..", ".....###...", "....###....", "...###.....", "..#######..", ".....###...", "....###....", "...###.....", "..###......", ".##........", "##........."],
        "wave": ["...........", "...........", ".###....##.", "##.##..##..", "#...####...", "...........", ".###....##.", "##.##..##..", "#...####...", "...........", "..........."],
        "moonrun": ["....###....", "...#.......", "..#........", "..#........", "..#.....#..", "...#...#...", "....###....", "...........", ".#.#.#.#.#.", "...........", "..........."],
    ]
    static let scenes: [String: Scene] = [
        "supernova": Scene(sky: ["#160b30", "#b62ad8", "#ffcf7a"], sun: [0.5, 0.42, 0.2], sunColor: "#fff7e0", glow: "#ff9be8", moon: false, stars: false, skyline: "mountains", land: ["#0c5c52", "#22c79a"], emblem: "burst", ink: "#ffffff", shade: "#ffd27a"),
        "superstar": Scene(sky: ["#070c2e", "#14246a", "#2c4aa0"], sun: [0, 0, 0], sunColor: "#000000", glow: "#000000", moon: false, stars: true, skyline: "city", land: ["#0b1238", "#1d2c66"], emblem: "star", ink: "#ffe066", shade: "#ff9d2e"),
        "sorcerer": Scene(sky: ["#0e1534", "#3a2f7a", "#7d58c4"], sun: [0, 0, 0], sunColor: "#000000", glow: "#000000", moon: true, stars: true, skyline: "pines", land: ["#0b1a22", "#17323a"], emblem: "flame", ink: "#ff8a3c", shade: "#ffd166"),
        "stepstar": Scene(sky: ["#ff5d3a", "#ff9a3c", "#ffd65a"], sun: [0.5, 0.55, 0.24], sunColor: "#fff3c4", glow: "#ffbf6a", moon: false, stars: false, skyline: "hills", land: ["#5a2a6e", "#8a3f8f"], emblem: "shoe", ink: "#ffffff", shade: "#ffd0e6"),
        "tenfold": Scene(sky: ["#0f4b5c", "#2c8f99", "#9fe3d1"], sun: [0, 0, 0], sunColor: "#000000", glow: "#000000", moon: false, stars: false, skyline: "track", land: ["#a6402e", "#e0694a"], emblem: "ten", ink: "#ffffff", shade: "#ffd166"),
        "fastest": Scene(sky: ["#1f1238", "#6b2f6e", "#ff8d5c"], sun: [0.7, 0.5, 0.16], sunColor: "#ffe0a0", glow: "#ff7a4a", moon: false, stars: false, skyline: "road", land: ["#232032", "#3a3550"], emblem: "bolt", ink: "#fff06a", shade: "#ff9d2e"),
        "momentum": Scene(sky: ["#0e2a24", "#1f5b44", "#6fc28a"], sun: [0, 0, 0], sunColor: "#000000", glow: "#000000", moon: false, stars: false, skyline: "pines", land: ["#0a1d17", "#163a2c"], emblem: "wave", ink: "#c8ff6a", shade: "#ffffff"),
        "nightrun": Scene(sky: ["#06091c", "#141c4a", "#2a2f72"], sun: [0, 0, 0], sunColor: "#000000", glow: "#000000", moon: true, stars: true, skyline: "road", land: ["#0c0f24", "#1a1f3e"], emblem: "moonrun", ink: "#ffffff", shade: "#9fb4ff"),
        "hillclimb": Scene(sky: ["#2a1b4f", "#7a4fb0", "#f2a6c8"], sun: [0.3, 0.45, 0.14], sunColor: "#fff0f5", glow: "#ffc2dc", moon: false, stars: false, skyline: "mountains", land: ["#3b1f4f", "#6a3b80"], emblem: "star", ink: "#ffffff", shade: "#ffd6ec"),
        "streak": Scene(sky: ["#3a0d10", "#a3291f", "#ff8a3c"], sun: [0, 0, 0], sunColor: "#000000", glow: "#000000", moon: false, stars: false, skyline: "hills", land: ["#2a0b0e", "#4f1a1c"], emblem: "flame", ink: "#ffe06a", shade: "#ff6a3c"),
    ]
    var big: [String: Image] = [:]
    var small: [String: Image] = [:]
    private var backgroundKey = ""
    private var backgroundImage: Image?
    func background(_ size: CGSize, _ top: Double) -> Image {
        let key = "\(size.width)|\(size.height)|\(top)"
        if key == backgroundKey, let image = backgroundImage { return image }
        // Skia interpolates these stops before premultiplying alpha. SwiftUI's
        // gradient interpolates premultiplied stops, so cache the exact sRGB field.
        let scale = 3.0, w = Int((size.width*scale).rounded()), h = Int((size.height*scale).rounded())
        var pixels = [UInt8](repeating:0,count:w*h*4)
        let colors = [[150.0,110,40,0.55],[60.0,45,20,0.2],[14.0,14,16,0.0]]
        let base = [14.0,14,16]
        for y in 0..<h {
            for x in 0..<w {
                let d = min(1,hypot((Double(x)+0.5)/scale-size.width/2,(Double(y)+0.5)/scale-(top-20))/(size.width*0.9))
                let i = d<0.45 ? 0 : 1, k = d<0.45 ? d/0.45 : (d-0.45)/0.55
                let a = colors[i][3]+(colors[i+1][3]-colors[i][3])*k
                let offset = (y*w+x)*4
                for ch in 0..<3 {
                    let value = colors[i][ch]+(colors[i+1][ch]-colors[i][ch])*k
                    pixels[offset+ch] = UInt8(max(0,min(255,floor(value*a+base[ch]*(1-a)+0.5))))
                }
                pixels[offset+3] = 255
            }
        }
        let data = Data(pixels) as CFData
        let image = CGImage(width:w,height:h,bitsPerComponent:8,bitsPerPixel:32,bytesPerRow:w*4,space:CGColorSpace(name:CGColorSpace.sRGB)!,bitmapInfo:CGBitmapInfo(rawValue:CGImageAlphaInfo.last.rawValue),provider:CGDataProvider(data:data)!,decode:nil,shouldInterpolate:false,intent:.defaultIntent)!
        let result = Image(uiImage:UIImage(cgImage:image,scale:scale,orientation:.up))
        backgroundKey = key;backgroundImage = result
        return result
    }
    init() {
        for name in ["LilitaOne-Regular", "Inter-Medium", "Inter-SemiBold", "Inter-Bold"] {
            if let url = Bundle.main.url(forResource: "PixelTrophyUnlock-" + name, withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
        for (i, name) in PixelTrophyUnlockTheme.recent.enumerated() {
            big[name] = Image(uiImage: card(name, 128, 176, PixelTrophyUnlockTheme.frames[i], PixelTrophyUnlockTheme.titles[i]))
        }
        big["tenfold"] = Image(uiImage: card("tenfold", 128, 176, "#ff9a2e", ["TENFOLD", "TRIUMPH"]))
        for (i, name) in PixelTrophyUnlockTheme.grid.enumerated() {
            small[name] = Image(uiImage: card(name, 80, 98, i == 0 ? "#ffb23a" : "#5b5d66", nil))
        }
        small["tenfold-color"] = Image(uiImage: card("tenfold", 80, 98, "#ff9a2e", nil))
    }
    static func color(_ hex: String, _ alpha: Double = 1) -> UIColor {
        let n = UInt32(hex.dropFirst(), radix: 16) ?? 0
        return UIColor(red: Double((n >> 16) & 255)/255, green: Double((n >> 8) & 255)/255, blue: Double(n & 255)/255, alpha: alpha)
    }
    static func path(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> CGPath {
        let p = CGMutablePath()
        p.move(to: CGPoint(x:x,y:y+r)); p.addQuadCurve(to: CGPoint(x:x+r,y:y), control: CGPoint(x:x,y:y))
        p.addLine(to: CGPoint(x:x+w-r,y:y));p.addQuadCurve(to: CGPoint(x:x+w,y:y+r), control: CGPoint(x:x+w,y:y))
        p.addLine(to: CGPoint(x:x+w,y:y+h-h*0.14));p.addLine(to: CGPoint(x:x+w/2,y:y+h));p.addLine(to: CGPoint(x:x,y:y+h-h*0.14));p.closeSubpath()
        return p
    }
    static func random(_ i: Double) -> Double { let s = sin(i * 12.9898) * 43758.5453; return s - floor(s) }
    static func lerp(_ a: String, _ b: String, _ k: Double) -> UIColor {
        let A = UInt32(a.dropFirst(), radix:16)!, B = UInt32(b.dropFirst(), radix:16)!
        func ch(_ n: UInt32) -> Double { let x = Double((A >> n) & 255), y = Double((B >> n) & 255); return floor(x+(y-x)*k+0.5)/255 }
        return UIColor(red:ch(16),green:ch(8),blue:ch(0),alpha:1)
    }
    static let bayer = [0,8,2,10,12,4,14,6,3,11,1,9,15,7,13,5]
    static func pixel(_ s: Scene, _ x: Int, _ y: Int, _ W: Double, _ H: Double) -> UIColor {
        let u = (Double(x)+0.5)/W, v = (Double(y)+0.5)/H, horizon = 0.66
        var top = horizon
        switch s.skyline {
        case "mountains": top = horizon - 0.16 * abs(sin(u*7.1+0.6)) - 0.08 * abs(sin(u*13.3))
        case "city": top = horizon - (0.08+0.2*random(floor(u*7)+3)) * (Int(floor(u*7))%2 == 0 ? 1 : 0.7)
        case "pines": top = horizon - 0.18 * max(0, 1-abs((u*6).truncatingRemainder(dividingBy:1)-0.5)*2.4) - 0.03
        case "hills": top = horizon - 0.07 - 0.06*sin(u*5+1)
        default: top = horizon-0.02
        }
        let d = Double(bayer[(y%4)*4+(x%4)])/16
        if v >= horizon {
            let g = (v-horizon)/(1-horizon)
            if s.skyline == "track" && y%3 == 0 { return color("#f6efe6") }
            if s.skyline == "road" && abs(u-0.5)<0.06*(0.4+g) && y%3 != 0 { return color("#fff2a8") }
            return color(g+(d-0.5)*0.25>0.5 ? s.land[1] : s.land[0])
        }
        if v >= top {
            if s.skyline == "city" && x%2 == 1 && y%3 == 1 && random(Double(x*31+y*7))>0.45 { return color("#ffd86a") }
            return color(v-top<0.025 && s.skyline != "city" ? s.land[1] : s.land[0])
        }
        if s.sun[2] > 0 {
            let dd = hypot((u-s.sun[0])*W,(v-s.sun[1])*H), r = s.sun[2]*W
            if dd<r { return color(s.sunColor) }
            if dd<r*1.5 && d<1-(dd-r)/(r*0.5) { return color(s.glow) }
        }
        if s.moon {
            let dx = (u-0.76)*W, dy = (v-0.2)*H, r = W*0.11
            if hypot(dx,dy)<r && hypot(dx+r*0.45,dy-r*0.2)>r*0.85 { return color("#f4f1d0") }
        }
        if s.stars && random(Double(x*17+y*131))>0.965 { return .white }
        let k = v/horizon
        return k+(d-0.5)*0.18<0.5 ? lerp(s.sky[0],s.sky[1],min(1,(k+(d-0.5)*0.18)*2)) : lerp(s.sky[1],s.sky[2],min(1,(k-0.5)*2+(d-0.5)*0.18))
    }
    func card(_ name: String, _ w: Double, _ h: Double, _ frame: String, _ title: [String]?) -> UIImage {
        let format = UIGraphicsImageRendererFormat(); format.scale = 3; format.preferredRange = .standard
        return UIGraphicsImageRenderer(size: CGSize(width:w,height:h), format:format).image { renderer in
            let c = renderer.cgContext, r = w*0.1, b = w*0.045, ib = w*0.075
            c.setFillColor(Self.color(frame).cgColor);c.addPath(Self.path(0,0,w,h,r));c.fillPath()
            c.setFillColor(Self.color("#101218").cgColor);c.addPath(Self.path(b,b,w-2*b,h-2*b,r*0.8));c.fillPath()
            c.saveGState();c.addPath(Self.path(ib,ib,w-2*ib,h-2*ib,r*0.6));c.clip()
            c.translateBy(x:ib,y:ib)
            let W = title == nil ? 24 : 32, H = title == nil ? 30 : 44, px = title == nil ? 3.0 : 4.0
            c.scaleBy(x:(w-2*ib)/(Double(W)*px),y:(h-2*ib)/(Double(H)*px))
            c.setShouldAntialias(true)
            let s = Self.scenes[name]!
            for y in 0..<H { for x in 0..<W {
                c.setFillColor(Self.pixel(s,x,y,Double(W),Double(H)).cgColor)
                c.fill(CGRect(x:Double(x)*px,y:Double(y)*px,width:px+0.35,height:px+0.35))
            }}
            let bm = Self.emblems[s.emblem]!.map(Array.init), ex = floor(Double(W-11)/2), ey = floor(Double(H)*0.2)
            c.setFillColor(Self.color("#0a0814",0.85).cgColor)
            for j in 0..<11 { for i in 0..<11 where bm[j][i] != "." {
                for (ox,oy) in [(1,0),(-1,0),(0,1),(0,-1)] { c.fill(CGRect(x:(ex+Double(i+ox))*px,y:(ey+Double(j+oy))*px,width:px,height:px)) }
            }}
            for j in 0..<11 { for i in 0..<11 where bm[j][i] != "." {
                c.setFillColor(Self.color(bm[j][i] == "#" ? s.ink : s.shade).cgColor)
                c.fill(CGRect(x:(ex+Double(i))*px,y:(ey+Double(j))*px,width:px+0.35,height:px+0.35))
            }}
            c.restoreGState()
            if let title {
                let font = (UIFont(name:"LilitaOne",size:23) ?? UIFont.systemFont(ofSize:23)) as CTFont
                for (i,line) in title.enumerated() {
                    let chars = Array(line.utf16); var glyphs = [CGGlyph](repeating:0,count:chars.count)
                    CTFontGetGlyphsForCharacters(font,chars,&glyphs,chars.count)
                    var widths = [CGSize](repeating:.zero,count:chars.count)
                    CTFontGetAdvancesForGlyphs(font,.horizontal,glyphs,&widths,glyphs.count)
                    let tw = widths.reduce(0) { $0+$1.width }, scale = min(1,(w-2*ib-8)/tw)
                    var offset = -tw/2
                    let positions = widths.map { width -> CGPoint in defer { offset += width.width }; return CGPoint(x:offset,y:0) }
                    c.saveGState();c.translateBy(x:w/2,y:h*0.72+Double(i)*23*0.92*scale);c.scaleBy(x:scale,y:-scale)
                    c.setLineJoin(.round);c.setLineWidth(4);c.setStrokeColor(Self.color("#14101e").cgColor);c.setTextDrawingMode(.stroke)
                    CTFontDrawGlyphs(font,glyphs,positions,glyphs.count,c)
                    c.setFillColor(UIColor.white.cgColor);c.setTextDrawingMode(.fill);CTFontDrawGlyphs(font,glyphs,positions,glyphs.count,c)
                    c.restoreGState()
                }
            }
        }
    }
}
