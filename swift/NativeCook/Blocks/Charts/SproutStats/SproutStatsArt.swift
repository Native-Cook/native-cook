import SwiftUI
import CoreText
import UIKit

final class SproutStatsResources {
    static let shared = SproutStatsResources()
    struct Label {
        var line: CTLine
        var width: Double
        var ascent: Double
        var height: Double
        var glyphs: [CGGlyph]
        var positions: [CGPoint]
        var font: CTFont
    }
    private var labels: [String: Label] = [:]
    private var chromeKey = ""
    private var chromeImage: CGImage?
    func chrome(size: CGSize, top: Double, bottom: Double, scale: Double) -> CGImage? {
        let key = "\(size.width),\(size.height),\(top),\(bottom),\(scale)"
        if key != chromeKey {
            let format = UIGraphicsImageRendererFormat()
            format.scale = scale
            chromeImage = UIGraphicsImageRenderer(size: size, format: format).image { renderer in
                SproutStatsArt(scale: scale).header(renderer.cgContext, size: size, top: top, bottom: bottom, frame: .init(), drawStatic: true, drawDynamic: false)
            }.cgImage
            chromeKey = key
        }
        return chromeImage
    }
    init() {
        for name in ["Nunito-Black", "Nunito-ExtraBold"] {
            if let url = Bundle.main.url(forResource: "SproutStats-\(name)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
        add("kicker", "THIS WEEK", 12, track: 1.3)
        add("title", "Mindful minutes", 28, black: true)
        add("summary", "128 min · 18% more than last week", 15)
        for i in 0..<7 { add("day\(i)", "\(SproutStatsMotion.days[i]) · \(SproutStatsMotion.minutes[i]) min", 15) }
        add("Garden", "Garden", 14); add("Chart", "Chart", 14)
        for s in ["M", "T", "W", "F", "S", "12", "20", "8", "25", "18", "30", "15"] { add(s, s, 14) }
        for s in ["5", "30", "18"] { add("tip"+s, s, 22, black: true) }
        for s in ["day streak", "best day · Sat", "daily average"] { add(s, s, 12) }
    }
    private func add(_ key: String, _ value: String, _ size: Double, black: Bool = false, track: Double = 0) {
        let font = UIFont(name: black ? "Nunito-Black" : "Nunito-ExtraBold", size: size) ?? UIFont.systemFont(ofSize: size)
        var attributes: [NSAttributedString.Key: Any] = [
            .font: font, NSAttributedString.Key(kCTForegroundColorFromContextAttributeName as String): true
        ]
        // RN Text leaves kerning at the font default unless letterSpacing is specified.
        if track != 0 { attributes[.kern] = track }
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: value, attributes: attributes))
        let ct = font as CTFont
        let chars = Array(value.utf16)
        var glyphs = [CGGlyph](repeating: 0, count: chars.count)
        CTFontGetGlyphsForCharacters(ct, chars, &glyphs, chars.count)
        var advances = [CGSize](repeating: .zero, count: chars.count)
        CTFontGetAdvancesForGlyphs(ct, .horizontal, glyphs, &advances, chars.count)
        var cursor = 0.0
        let positions = advances.map { advance -> CGPoint in
            defer { cursor += advance.width.rounded() }
            return CGPoint(x: cursor, y: 0)
        }
        // Skia measureText returns raster ink bounds, rather than the typographic advance.
        // These are the 14 pt Nunito bounds used by the reference's unshaped Canvas labels.
        let skiaWidths: [String: Double] = ["M":13,"T":12,"W":18,"F":10,"S":11,"12":17,"20":18,"8":10,"25":18,"18":17,"30":18,"15":17]
        labels[key] = Label(line: line, width: skiaWidths[key] ?? CTLineGetTypographicBounds(line, nil, nil, nil), ascent: font.ascender, height: font.lineHeight, glyphs: glyphs, positions: positions, font: ct)
    }
    func label(_ key: String) -> Label { labels[key]! }
}

struct SproutStatsArt {
    typealias M = SproutStatsMotion
    let r = SproutStatsResources.shared
    var scale = 3.0
    func height(_ key: String) -> Double { ceil(r.label(key).height*scale)/scale }
    static func color(_ hex: Int, _ alpha: Double = 1) -> CGColor {
        CGColor(srgbRed: Double((hex>>16)&255)/255, green: Double((hex>>8)&255)/255, blue: Double(hex&255)/255, alpha: min(1, max(0, alpha)))
    }
    func text(_ c: CGContext, _ key: String, _ x: Double, _ y: Double, _ color: Int, center: Bool = false, alpha: Double = 1, baseline: Bool = false) {
        let lab = r.label(key)
        c.saveGState()
        c.setFillColor(Self.color(color, alpha))
        c.translateBy(x: x-(center ? lab.width/2 : 0), y: y+(baseline ? 0 : lab.ascent))
        c.scaleBy(x: 1, y: -1)
        c.textMatrix = .identity; c.textPosition = .zero
        if baseline { CTFontDrawGlyphs(lab.font, lab.glyphs, lab.positions, lab.glyphs.count, c) }
        else { CTLineDraw(lab.line, c) }
        c.restoreGState()
    }
    func round(_ c: CGContext, _ rect: CGRect, _ radius: Double, _ color: Int) {
        let radius = min(radius, min(rect.width, rect.height)/2)
        c.setFillColor(Self.color(color)); c.addPath(CGPath(roundedRect: rect, cornerWidth: radius, cornerHeight: radius, transform: nil)); c.fillPath()
    }
    func header(_ c: CGContext, size: CGSize, top: Double, bottom: Double, frame: M.Frame, drawStatic: Bool = false, drawDynamic: Bool = true) {
        var y = top+10
        if drawStatic { text(c, "kicker", 24, y, 0xB09A7A) }; y += height("kicker")+2
        if drawStatic { text(c, "title", 24, y, 0x2A2633) }; y += height("title")+4
        let d = frame.scrub >= 0 ? Int(floor(frame.scrub+0.5)) : -1
        if drawDynamic { text(c, d >= 0 ? "day\(d)" : "summary", 24, y, 0x7D7488) }; y += height("summary")+16
        if drawStatic { round(c, CGRect(x:24, y:y, width:212, height:40), 20, 0xF0E8DC) }
        if drawDynamic {
        round(c, CGRect(x:28+M.clamp(frame.m)*104, y:y+4, width:104, height:32), 16, 0xFFFFFF)
        text(c, "Garden", 79, y+(40-height("Garden"))/2, frame.m <= 0.5 ? 0x2A2633 : 0x9A93A6, center: true)
        text(c, "Chart", 181, y+(40-height("Chart"))/2, frame.m > 0.5 ? 0x2A2633 : 0x9A93A6, center: true)
        }
        guard drawStatic else { return }
        let tipW = (size.width-60)/3, ty = size.height-bottom-20-74
        for i in 0..<3 {
            let x = 20+Double(i)*(tipW+10)
            round(c, CGRect(x:x, y:ty, width:tipW, height:74), 20, 0xFFFFFF)
            let keys = ["5", "30", "18"], subtitles = ["day streak", "best day · Sat", "daily average"]
            let h = height("tip"+keys[i])+height(subtitles[i])
            // RN rounds the centered number line to the device pixel grid independently.
            text(c, "tip"+keys[i], x+12, ty+(74-h)/2-1/scale, 0x2A2633)
            text(c, subtitles[i], x+12, ty+(74-h)/2+height("tip"+keys[i]), 0x9A93A6)
        }
    }
    var segmentY: Double { height("kicker")+2+height("title")+4+height("summary")+16+10 }
    func garden(_ c: CGContext, size: CGSize, frame o: M.Frame, reduced: Bool) {
        let base = (size.height*0.7).rounded(), hmax = (size.height*0.34).rounded(), step = (size.width-92)/6, m = o.m
        func line(_ x: Double, _ y: Double, _ xx: Double, _ yy: Double, _ color: Int, _ alpha: Double, _ width: Double) {
            c.setStrokeColor(Self.color(color, alpha)); c.setLineWidth(width > 0 ? width : 1/scale); c.setLineCap(.round)
            c.move(to: CGPoint(x:x,y:y)); c.addLine(to: CGPoint(x:xx,y:yy)); c.strokePath()
        }
        func circle(_ x: Double, _ y: Double, _ radius: Double, _ color: Int) {
            c.setFillColor(Self.color(color)); c.fillEllipse(in: CGRect(x:x-radius,y:y-radius,width:radius*2,height:radius*2))
        }
        line(16,base,size.width-16,base,0xE7DED0,1,M.mix(10,2,m))
        for k in 1...3 { let y = base-hmax*Double(k)/3; line(16,y,size.width-16,y,0xE7DED0,m*0.8,1) }
        for i in 0..<7 {
            let x = 46+step*Double(i), h = Double(M.minutes[i])/30*hmax
            let sel = o.scrub >= 0 ? M.clamp(1-abs(o.scrub-Double(i))) : 0
            let potH = 26*(1-m), sway = reduced ? 0 : sin(o.t*2*Double.pi*Double(i%2 == 1 ? 1 : 2)/M.loop+Double(i)*1.3)*7*(1-m)
            let topY = base-h-sel*6*m, startY = M.mix(base-potH,base-13,m)
            let width = M.mix(5,26,m), bend = (1-m)*Double(i%2 == 1 ? 10 : -10)
            let ck = M.smoother((m-0.15)/0.55), a = 0x5DAE5B, b = M.colors[i]
            let rgb = [16,8,0].map { shift in Int(M.mix(Double((a>>shift)&255),Double((b>>shift)&255),ck).rounded()) }
            c.setStrokeColor(Self.color(rgb[0]<<16 | rgb[1]<<8 | rgb[2])); c.setLineWidth(max(0, width)); c.setLineCap(.round)
            c.move(to: CGPoint(x:x,y:startY))
            c.addCurve(to: CGPoint(x:x+sway,y:topY+width/2), control1: CGPoint(x:x+bend,y:M.mix(startY,topY,0.35)), control2: CGPoint(x:x-bend+sway*0.5,y:M.mix(startY,topY,0.7)))
            if width > 0 { c.strokePath() } else { c.setFillColor(Self.color(rgb[0]<<16 | rgb[1]<<8 | rgb[2])); c.fillPath() }
            let lk = 1-M.smoother(m*1.6)
            if lk > 0.01 {
                for k in 0..<2 {
                    let q = 0.35+Double(k)*0.25, dir = k%2 == 1 ? 1.0 : -1.0
                    c.saveGState(); c.translateBy(x:x+sway*q,y:M.mix(startY,topY,q)); c.rotate(by:(dir * -35-sway)*Double.pi/180); c.scaleBy(x:lk,y:lk)
                    c.setFillColor(Self.color(0x6CC46A)); c.fillEllipse(in:CGRect(x:dir > 0 ? 0 : -18,y:-5,width:18,height:10)); c.restoreGState()
                }
            }
            let pk = 1-m, fx = x+sway, fy = topY+width/2
            if pk > 0.01 {
                for k in 0..<6 {
                    let a = Double(k)/6*Double.pi*2+o.t*(Double.pi/9)*(reduced ? 0 : 1)
                    circle(fx+cos(a)*9*pk,fy-6*pk+sin(a)*9*pk,6.5*pk,M.colors[i])
                }
                circle(fx,fy-6*pk,5*pk,0xFFE07A)
            }
            if potH > 0.5 {
                c.move(to:CGPoint(x:x-17,y:base-potH)); c.addLine(to:CGPoint(x:x+17,y:base-potH)); c.addLine(to:CGPoint(x:x+13,y:base)); c.addLine(to:CGPoint(x:x-13,y:base)); c.closePath(); c.setFillColor(Self.color(0xD9825B)); c.fillPath()
                round(c,CGRect(x:x-19,y:base-potH-2,width:38,height:min(8,potH)),3,0xE89A73)
            }
            text(c,["M","T","W","T","F","S","S"][i],x,base+24,sel > 0.5 ? 0x2A2633 : 0x9A93A6,center:true,baseline:true)
            let vk = M.clamp((m-0.6)/0.4)
            if vk > 0 { text(c,"\(M.minutes[i])",x,topY-8,0x2A2633,center:true,alpha:vk*(0.55+0.45*sel),baseline:true) }
        }
    }
}
