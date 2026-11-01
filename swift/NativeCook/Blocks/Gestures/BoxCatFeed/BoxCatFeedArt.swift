import SwiftUI
import UIKit
import CoreText

/// Cached fonts, text and invariant artwork. No image processing runs in the frame loop.
final class BoxCatFeedArt {
    static let shared = BoxCatFeedArt()
    private var fonts: [String: UIFont] = [:]
    private var labels: [String: UIImage] = [:]
    private var paths: [String: CGPath] = [:]
    private var back: UIImage?
    private var front: UIImage?
    private var layout = CGSize.zero
    private var displayScale = 3.0
    private let colors: [String: UIColor]
    private let gradient: CGGradient
    private let boxGradient: CGGradient
    init() {
        let hexes = ["EDE6FF","F6F2FF","FFF4EC","5B3F8C","C78C55","8E5A33","8E97A8","E9ECF2","F4A9B8","98A1B2","2B2433","FFFFFF","6E7686","F48BA0","5A2A3A","A7AFBE","D69A5E","C48650","A86F3E","FF6B8B","FFB25C","FF9A3D","5A3A20","E8893A","8A7FA8","7A4DFF","6E6488","FFF1DE","C9772F"]
        colors = Dictionary(uniqueKeysWithValues: hexes.map { hex in
            let n = Int(hex, radix: 16)!
            return (hex, UIColor(red: Double(n>>16 & 255)/255, green: Double(n>>8 & 255)/255, blue: Double(n & 255)/255, alpha: 1))
        })
        let space = CGColorSpace(name: CGColorSpace.sRGB)!
        gradient = CGGradient(colorsSpace: space, colors: [colors["EDE6FF"]!.cgColor, colors["F6F2FF"]!.cgColor, colors["FFF4EC"]!.cgColor] as CFArray, locations: [0,0.5,1])!
        boxGradient = CGGradient(colorsSpace: space, colors: [UIColor(red: 227/255, green: 168/255, blue: 108/255, alpha: 1).cgColor, UIColor(red: 207/255, green: 146/255, blue: 88/255, alpha: 1).cgColor] as CFArray, locations: [0,1])!
        for name in ["Nunito-ExtraBold", "Nunito-Black", "TitanOne-Regular", "Ionicons"] {
            if let url = Bundle.main.url(forResource: "BoxCatFeed-\(name)", withExtension: "ttf") { CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil) }
        }
    }
    func font(_ name: String, _ size: Double) -> UIFont {
        let key = "\(name)-\(size)"
        if let f = fonts[key] { return f }
        let f = UIFont(name: name, size: size) ?? UIFont.systemFont(ofSize: size)
        fonts[key] = f
        return f
    }
    private func text(_ cv: CGContext, _ value: String, _ name: String, _ size: Double, _ color: String, _ x: Double, _ y: Double) -> CGSize {
        let key = "\(value)|\(name)|\(size)|\(color)"
        let bitmap: UIImage
        if let image = labels[key] { bitmap = image } else {
            let f = font(name, size)
            let attr = NSAttributedString(string: value, attributes: [.font:f, .foregroundColor: colors[color]!])
            let w = ceil(attr.size().width)
            let h = ceil(f.lineHeight)
            let format = UIGraphicsImageRendererFormat(); format.scale = displayScale
            bitmap = UIGraphicsImageRenderer(size: CGSize(width: max(1,w), height: max(1,h)), format: format).image { _ in
                attr.draw(at: .zero)
            }
            labels[key] = bitmap
        }
        UIGraphicsPushContext(cv); bitmap.draw(at: CGPoint(x: x, y: y)); UIGraphicsPopContext()
        return bitmap.size
    }
    private func fill(_ cv: CGContext, _ color: String, _ alpha: Double = 1) {
        cv.setFillColor(colors[color]!.withAlphaComponent(alpha).cgColor)
    }
    private func oval(_ cv: CGContext, _ x: Double, _ y: Double, _ w: Double, _ h: Double, _ color: String, _ alpha: Double = 1) {
        fill(cv,color,alpha); cv.fillEllipse(in: CGRect(x:x,y:y,width:w,height:h))
    }
    private func circle(_ cv: CGContext, _ x: Double, _ y: Double, _ r: Double, _ color: String, _ alpha: Double = 1) { oval(cv,x-r,y-r,r*2,r*2,color,alpha) }
    private func line(_ cv: CGContext, _ x: Double, _ y: Double, _ x2: Double, _ y2: Double, _ color: String, _ width: Double, _ alpha: Double = 1) {
        cv.setStrokeColor(colors[color]!.withAlphaComponent(alpha).cgColor); cv.setLineWidth(width); cv.setLineCap(.round)
        cv.move(to: CGPoint(x:x,y:y)); cv.addLine(to: CGPoint(x:x2,y:y2)); cv.strokePath()
    }
    private func polygon(_ cv: CGContext, _ points: [Double], _ color: String, key: String) {
        let path: CGPath
        if let p = paths[key] { path = p } else {
            let p = CGMutablePath(); p.move(to: CGPoint(x:points[0],y:points[1]))
            for i in stride(from:2,to:points.count,by:2) { p.addLine(to: CGPoint(x:points[i],y:points[i+1])) }
            p.closeSubpath(); path = p; paths[key] = p
        }
        fill(cv,color); cv.addPath(path); cv.fillPath()
    }
    private func round(_ cv: CGContext, _ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double, _ color: String, key: String) {
        let path = rounded(x,y,w,h,r,key)
        fill(cv,color); cv.addPath(path); cv.fillPath()
    }
    private func rounded(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double, _ key: String) -> CGPath {
        if let p = paths[key] { return p }
        let p = CGPath(roundedRect: CGRect(x:x,y:y,width:w,height:h), cornerWidth:r, cornerHeight:r, transform:nil)
        paths[key] = p; return p
    }
    private func image(_ size: CGSize, draw: (CGContext) -> Void) -> UIImage {
        let format = UIGraphicsImageRendererFormat(); format.scale = displayScale
        return UIGraphicsImageRenderer(size:size,format:format).image { draw($0.cgContext) }
    }
    private func prepare(_ size: CGSize) {
        if layout == size { return }
        layout = size; paths.removeAll()
        let cx = size.width/2, rim = (size.height*0.56).rounded(), s = (size.width*0.2).rounded(), bw = s*2.3, bh = s*1.5
        back = image(size) { cv in
            cv.drawLinearGradient(gradient,start:.zero,end:CGPoint(x:0,y:size.height),options:[])
            oval(cv,cx-bw*0.62,rim+bh-10,bw*1.24,30,"5B3F8C",0.14)
            polygon(cv,[cx-bw/2+8,rim,cx-bw/2+20,rim-s*0.5,cx+bw/2-20,rim-s*0.5,cx+bw/2-8,rim],"C78C55",key:"backFlap")
            fill(cv,"8E5A33"); cv.fill(CGRect(x:cx-bw/2,y:rim-6,width:bw,height:14))
        }
        front = image(size) { cv in
            cv.saveGState(); cv.addPath(rounded(cx-bw/2,rim,bw,bh,10,"box")); cv.clip()
            cv.drawLinearGradient(boxGradient,start:CGPoint(x:0,y:rim),end:CGPoint(x:0,y:rim+bh),options:[]); cv.restoreGState()
            polygon(cv,[cx-bw/2,rim,cx-bw/2-s*0.55,rim+s*0.28,cx-bw/2-s*0.45,rim+s*0.55,cx-bw/2,rim+s*0.22],"D69A5E",key:"leftFlap")
            polygon(cv,[cx+bw/2,rim,cx+bw/2+s*0.55,rim+s*0.28,cx+bw/2+s*0.45,rim+s*0.55,cx+bw/2,rim+s*0.22],"C48650",key:"rightFlap")
            line(cv,cx-bw*0.3,rim+bh*0.55,cx+bw*0.3,rim+bh*0.55,"A86F3E",2,0.5)
        }
    }
    private func fish(_ cv: CGContext, _ x: Double, _ y: Double, _ scale: Double, _ rotation: Double) {
        cv.saveGState(); cv.translateBy(x:x,y:y); cv.rotate(by:rotation * .pi/180); cv.scaleBy(x:scale,y:scale)
        oval(cv,-16,-9,28,18,"FFB25C")
        polygon(cv,[10,0,22,-9,22,9],"FF9A3D",key:"fishTail")
        circle(cv,-8,-2,2.4,"5A3A20"); line(cv,-2,-6,-2,6,"E8893A",1.6); cv.restoreGState()
    }
    func draw(_ cv: CGContext, size: CGSize, top: Double, bottom: Double, displayScale: Double, frame o: BoxCatFeedMotion.Frame, reduced: Bool) {
        if self.displayScale != displayScale { layout = .zero; labels.removeAll() }
        self.displayScale = displayScale
        prepare(size)
        UIGraphicsPushContext(cv); back?.draw(at:.zero); UIGraphicsPopContext()
        let cx = size.width/2, rim = (size.height*0.56).rounded(), s = (size.width*0.2).rounded(), bw = s*2.3
        let c = BoxCatFeedMotion.cat(o.u), lift = reduced ? 0 : c.lift
        let headY = rim-s*0.42-lift*s*1.7+o.duck*s*0.5, sy = 1+c.stretch
        cv.saveGState(); cv.clip(to:CGRect(x:cx-bw,y:-2000,width:bw*2,height:rim+4+2000))
        cv.translateBy(x:cx,y:headY); cv.scaleBy(x:1/sqrt(sy),y:sy)
        round(cv,-s*0.55,s*0.3,s*1.1,s*1.6,s*0.5,"8E97A8",key:"body")
        round(cv,-s*0.3,s*0.45,s*0.6,s*1.2,s*0.3,"E9ECF2",key:"belly")
        for k in [-1.0,1.0] {
            polygon(cv,[k*s*0.55,-s*0.2,k*s*0.42,-s*0.78,k*s*0.08,-s*0.42],"8E97A8",key:"ear\(k)")
            polygon(cv,[k*s*0.45,-s*0.28,k*s*0.38,-s*0.62,k*s*0.18,-s*0.42],"F4A9B8",key:"inner\(k)")
        }
        oval(cv,-s*0.68,-s*0.52,s*1.36,s*1.04,"98A1B2")
        for k in [-1.0,1.0] {
            if c.munch > 0 || o.blink {
                let key = "eye\(k)"
                let p: CGPath
                if let path = paths[key] { p = path } else {
                    let path = CGMutablePath()
                    let transform = CGAffineTransform(translationX:k*s*0.27,y:-s*0.05).scaledBy(x:s*0.1,y:s*0.07)
                    path.addArc(center:.zero,radius:1,startAngle:200 * .pi/180,endAngle:340 * .pi/180,clockwise:false,transform:transform)
                    paths[key] = path; p = path
                }
                cv.setStrokeColor(colors["2B2433"]!.cgColor); cv.setLineWidth(3); cv.setLineCap(.round); cv.addPath(p); cv.strokePath()
            } else {
                circle(cv,k*s*0.27,-s*0.04,s*0.09,"2B2433")
                circle(cv,k*s*0.27+s*0.03,-s*0.07,s*0.03,"FFFFFF")
            }
            line(cv,k*s*0.42,s*0.12,k*s*0.82,s*0.06,"6E7686",1.4)
            line(cv,k*s*0.42,s*0.2,k*s*0.82,s*0.24,"6E7686",1.4)
        }
        circle(cv,-s*0.4,s*0.12,s*0.09,"F4A9B8",0.6); circle(cv,s*0.4,s*0.12,s*0.09,"F4A9B8",0.6)
        oval(cv,-s*0.06,s*0.06,s*0.12,s*0.08,"F48BA0")
        let mouth = 0.05+0.1*c.munch+(lift > 0.6 && !c.caught ? 0.12 : 0)
        oval(cv,-s*0.1,s*0.16,s*0.2,s*mouth,"5A2A3A")
        if c.caught && c.munch > 0 { fish(cv,s*0.02,s*0.26,0.7*(1-BoxCatFeedMotion.clamp((o.u-0.7-1.2)/0.4)),0) }
        cv.restoreGState()
        for k in [-1.0,1.0] { oval(cv,cx+k*s*0.4-s*0.18,rim-s*0.08-lift*s*0.6,s*0.36,s*0.22,"A7AFBE",1-lift) }
        UIGraphicsPushContext(cv); front?.draw(at:.zero); UIGraphicsPopContext()
        if o.u >= 0 && o.u < 0.7 && !reduced {
            let k = o.u/0.7
            fish(cv,cx+s*0.9*(1-k),(size.height-bottom-120)*(1-k)+(rim-s*0.42-s*1.7+s*0.26)*k-sin(.pi*k)*s*2.2,1,k*540)
        }
        if c.hearts >= 0 && c.hearts < 1.4 {
            for i in 0..<3 {
                let h = c.hearts-Double(i)*0.18
                if h < 0 { continue }
                let hx = cx+Double(i-1)*s*0.6+sin(h*6+Double(i))*6, hy = rim-s*1.6-h*s*1.2
                let sz = s*0.16*(0.6+0.4*BoxCatFeedMotion.clamp(h*4))
                let p = CGMutablePath(); p.move(to:CGPoint(x:hx,y:hy+sz))
                p.addCurve(to:CGPoint(x:hx,y:hy-sz*0.5),control1:CGPoint(x:hx-sz*2,y:hy-sz*0.4),control2:CGPoint(x:hx-sz*0.8,y:hy-sz*1.6))
                p.addCurve(to:CGPoint(x:hx,y:hy+sz),control1:CGPoint(x:hx+sz*0.8,y:hy-sz*1.6),control2:CGPoint(x:hx+sz*2,y:hy-sz*0.4))
                fill(cv,"FF6B8B",1-BoxCatFeedMotion.clamp(h)); cv.addPath(p); cv.fillPath()
            }
        }
        drawUI(cv,size,top,bottom,o)
    }
    private func pixelCeil(_ value: Double) -> Double { ceil(value*displayScale)/displayScale }
    private func drawUI(_ cv: CGContext, _ size: CGSize, _ top: Double, _ bottom: Double, _ o: BoxCatFeedMotion.Frame) {
        let titleHeight = pixelCeil(font("Nunito-ExtraBold",14).lineHeight), nameHeight = pixelCeil(font("TitanOne",30).lineHeight)
        _ = text(cv,"Your bank buddy","Nunito-ExtraBold",14,"8A7FA8",20,top+10)
        _ = text(cv,"Mochi","TitanOne",30,"2B2433",20,top+10+titleHeight)
        cv.saveGState(); cv.setAlpha(1-o.dip)
        _ = text(cv,o.count > 0 ? "Purring. Thanks for the snack!" : "Peeking out for a snack…","Nunito-ExtraBold",15,"6E6488",20,top+86)
        cv.restoreGState()
        let pts = "\(1240+o.count*10) pts", pf = font("Nunito-Black",15)
        let tw = pixelCeil((pts as NSString).size(withAttributes:[.font:pf]).width)
        let pw = 28+14+6+tw, px = size.width-20-pw, py = top+10+(titleHeight+nameHeight-36)/2
        let u = o.u-0.7, scale = 1+0.18*(u >= 0 && u < 0.4 ? sin(u/0.4 * .pi) : 0)
        cv.saveGState(); cv.setAlpha(1-o.dip); cv.translateBy(x:px+pw/2,y:py+18); cv.scaleBy(x:scale,y:scale); cv.translateBy(x:-pw/2,y:-18)
        round(cv,0,0,pw,36,18,"FFFFFF",key:"points\(o.count)")
        _ = text(cv,String(UnicodeScalar(62860)!),"Ionicons",14,"7A4DFF",14,(36-pixelCeil(font("Ionicons",14).lineHeight))/2)
        _ = text(cv,pts,"Nunito-Black",15,"2B2433",34,(36-pixelCeil(pf.lineHeight))/2)
        cv.restoreGState()
        let dy = size.height-bottom-110, dw = size.width-40
        round(cv,20,dy,dw,84,26,"FFFFFF",key:"daily\(bottom)")
        let dh = pixelCeil(font("Nunito-Black",16).lineHeight)+16, ty = dy+(84-dh)/2
        _ = text(cv,"Daily treats","Nunito-Black",16,"2B2433",40,ty)
        cv.saveGState(); cv.setAlpha(1-o.dip)
        for i in 0..<3 { round(cv,40+Double(i)*32,ty+dh-8,26,8,4,i < o.count ? "7A4DFF" : "EDE6FF",key:"dot\(i)\(bottom)") }
        cv.restoreGState()
        let shown = BoxCatFeedMotion.treatShown(o.u)
        if shown > 0 {
            let x = size.width-140, y = dy+10
            let ts = 0.8 + 0.2 * shown
            cv.saveGState(); cv.setAlpha(shown); cv.translateBy(x:x+55,y:y+32); cv.scaleBy(x:ts,y:ts); cv.translateBy(x:-x-55,y:-y-32)
            round(cv,x,y,110,64,20,"FFF1DE",key:"treat\(bottom)")
            let ih = pixelCeil(font("Ionicons",26).lineHeight), th = pixelCeil(font("Nunito-Black",12).lineHeight)
            _ = text(cv,String(UnicodeScalar(62218)!),"Ionicons",26,"FF9A3D",x+42,y+(64-ih-th)/2)
            let textW = pixelCeil(("Flick up" as NSString).size(withAttributes:[.font:font("Nunito-Black",12)]).width)
            _ = text(cv,"Flick up","Nunito-Black",12,"C9772F",x+(110-textW)/2,y+(64-ih-th)/2+ih)
            cv.restoreGState()
        }
    }
}
