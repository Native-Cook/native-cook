import SwiftUI
import UIKit
import CoreText

final class ClawPrizeDropResources {
    static let shared = ClawPrizeDropResources()
    let prizes: [Image]
    private var layers: [String: (Image, Image, Image, Image, Image)] = [:]
    private var fonts: [String: UIFont] = [:]
    init() {
        for name in ["TitanOne-Regular", "Nunito-Black", "Nunito-ExtraBold", "Ionicons"] {
            if let url = Bundle.main.url(forResource: "ClawPrizeDrop-\(name)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
        prizes = ["bear", "bunny", "star", "capsule", "coinbag", "ticket"].map { name in
            let url = Bundle.main.url(forResource: "ClawPrizeDrop-prize-\(name)", withExtension: "png")
            let img = url.flatMap { UIImage(contentsOfFile: $0.path) } ?? UIImage()
            // SkCanvas.drawImage uses nearest sampling unless sampling options are supplied.
            return Image(uiImage: img.preparingForDisplay() ?? img).interpolation(.none)
        }
        for (name, size) in [("TitanOne",26.0),("Nunito-Black",15.0),("Nunito-ExtraBold",13.0),("Nunito-ExtraBold",14.0),("Ionicons",18.0),("Ionicons",22.0),("Ionicons",24.0)] {
            fonts["\(name)-\(size)"] = UIFont(name: name, size: size) ?? UIFont.systemFont(ofSize: size)
        }
    }
    static func ui(_ hex: UInt32) -> UIColor {
        UIColor(red: CGFloat((hex >> 16) & 255) / 255, green: CGFloat((hex >> 8) & 255) / 255, blue: CGFloat(hex & 255) / 255, alpha: 1)
    }
    static func color(_ hex: UInt32) -> Color { Color(uiColor: ui(hex)) }
    func font(_ name: String, _ size: Double) -> UIFont { fonts["\(name)-\(size)"]! }
    func text(_ str: String, _ name: String, _ size: Double, _ color: UInt32, _ x: Double, _ y: Double) {
        (str as NSString).draw(at: CGPoint(x: x, y: y), withAttributes: [.font: font(name,size), .foregroundColor: Self.ui(color)])
    }
    func width(_ str: String, _ name: String, _ size: Double) -> Double {
        (str as NSString).size(withAttributes: [.font: font(name,size)]).width
    }
    private func rr(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double, _ c: UInt32, _ border: Double = 0, _ bc: UInt32 = 0xffffff) {
        Self.ui(c).setFill()
        UIBezierPath(cgPath: CGPath(roundedRect:CGRect(x:x,y:y,width:w,height:h),cornerWidth:r,cornerHeight:r,transform:nil)).fill()
        if border > 0 {
            Self.ui(bc).setStroke()
            let p = UIBezierPath(cgPath:CGPath(roundedRect:CGRect(x:x+border/2,y:y+border/2,width:w-border,height:h-border),cornerWidth:r-border/2,cornerHeight:r-border/2,transform:nil))
            p.lineWidth = border; p.stroke()
        }
    }
    private func render(_ size: CGSize, _ scale: Double, _ draw: () -> Void) -> Image {
        let format = UIGraphicsImageRendererFormat(); format.scale = scale
        return Image(uiImage: UIGraphicsImageRenderer(size: size, format: format).image { _ in draw() })
    }
    func scene(_ size: CGSize, _ top: Double, _ scale: Double) -> (Image, Image, Image, Image, Image) {
        let key = "\(size)-\(top)-\(scale)"
        if let cached = layers[key] { return cached }
        let g = ClawPrizeDropMotion.Geo(size,top)
        let back = render(size,scale) {
            Self.ui(0x8fd3ff).setFill(); UIRectFill(CGRect(origin:.zero,size:size))
            rr(16,top+8,44,44,22,0x3d8bff,3)
            let close = "\u{f24a}"
            text(close,"Ionicons",22,0xffffff,38-width(close,"Ionicons",22)/2,top+30-font("Ionicons",22).lineHeight/2)
            let cw = 12*2 + 18 + 6 + width("1,200","Nunito-Black",15)
            let cx = size.width-16-cw
            rr(cx,top+12,cw,36,18,0xffffff)
            rr(cx+12,top+21,18,18,9,0xffc23d,3,0xffde7a)
            text("1,200","Nunito-Black",15,0x2c3446,cx+36,top+30-font("Nunito-Black",15).lineHeight/2)
            let sw = width("Grabbit","TitanOne",26)+52+8
            rr((size.width-sw)/2,top+64,sw,52,18,0xff5a5a,4)
            text("Grabbit","TitanOne",26,0xffffff,(size.width-width("Grabbit","TitanOne",26))/2,top+90-font("TitanOne",26).lineHeight/2)
            let ctx = UIGraphicsGetCurrentContext()!
            ctx.saveGState()
            UIBezierPath(cgPath:CGPath(roundedRect:CGRect(x:18,y:g.y-26,width:g.w+20,height:34),cornerWidth:10,cornerHeight:10,transform:nil)).addClip()
            for i in 0..<9 {
                Self.ui(i%2 == 0 ? 0xff5a5a : 0xffffff).setFill()
                UIRectFill(CGRect(x:18+Double(i)*(g.w+20)/9,y:g.y-26,width:(g.w+20)/9,height:34))
            }
            ctx.restoreGState()
        }
        let front = render(size,scale) {
            let by = g.y+g.h-6, bw = g.w+20
            rr(18,by,bw,200,18,0xe84a4a)
            rr(32,by+14,bw-28,84,14,0x2c3446)
            text("Slide the claw, then grab · 3 tries left","Nunito-ExtraBold",14,0xffe3e3,32,by+142-font("Nunito-ExtraBold",14).lineHeight/2)
        }
        let grab = render(CGSize(width:64,height:64),scale) {
            rr(0,0,64,64,32,0xff8a3d,4)
            let icon = "\u{f35b}"
            text(icon,"Ionicons",24,0xffffff,32-width(icon,"Ionicons",24)/2,32-font("Ionicons",24).lineHeight/2)
        }
        let machineBack = render(size,scale) {
            rr(g.x,g.y,g.w,g.h,18,0xcdebff)
            // Match the reference Skia canvas's 8-bit sRGB paint quantisation (#E5F5FF → E5F4FF).
            rr(g.x+8,g.y+8,g.w-16,g.h-16,12,0xe5f4ff)
            Self.ui(0xffffff).setFill()
            for k in 0..<7 {
                let cx = g.x+20+Double(k)*(g.w-40)/6
                UIBezierPath(ovalIn:CGRect(x:cx-34,y:g.floorY-16,width:68,height:68)).fill()
            }
            rr(g.x+6,g.railY-8,g.w-12,8,0,0x9aa9bf)
        }
        let machineFront = render(size,scale) {
            let ctx = UIGraphicsGetCurrentContext()!
            ctx.setAlpha(0.35); rr(g.x+18,g.y+20,10,g.h-60,0,0xffffff); ctx.setAlpha(1)
            // Skia strokes the frame on its centreline, unlike the View borders above.
            Self.ui(0xff6b6b).setStroke()
            let frame = UIBezierPath(cgPath:CGPath(roundedRect:CGRect(x:g.x,y:g.y,width:g.w,height:g.h),cornerWidth:18,cornerHeight:18,transform:nil))
            frame.lineWidth = 6; frame.stroke()
            // Preserve 0.85 alpha as the falling prize crosses behind this opening.
            // Match Skia's premultiplied byte rounding for #2C3446 at that alpha.
            ctx.setAlpha(0.85); rr(g.chuteX-34,g.floorY-6,68,16,6,0x2d3445); ctx.setAlpha(1)
        }
        for i in 0..<6 { _ = coupon(i,g.w-32,scale) }
        let result = (back,front,grab,machineBack,machineFront); layers[key] = result; return result
    }
    private var coupons: [Int: Image] = [:]
    func coupon(_ prize: Int, _ width: Double, _ scale: Double) -> Image {
        // One cache per current screen width, rebuilt only after a size change.
        let key = prize * 10000 + Int(width*scale)
        if let cached = coupons[key] { return cached }
        let img = render(CGSize(width:width,height:58),scale) {
            rr(0,0,width,58,12,0xfff4e6)
            text("\u{f337}","Ionicons",18,0xff5a5a,12,29-ceil(font("Ionicons",18).lineHeight*scale)/scale/2)
            // RN rounds each Text node's measured line box to the pixel grid.
            let titleHeight = ceil(font("Nunito-Black",15).lineHeight*scale)/scale
            let lh = titleHeight + ceil(font("Nunito-ExtraBold",13).lineHeight*scale)/scale
            text(ClawPrizeDropMotion.names[prize],"Nunito-Black",15,0x2c3446,40,(58-lh)/2)
            text(ClawPrizeDropMotion.rewards[prize],"Nunito-ExtraBold",13,0xe84a4a,40,(58-lh)/2+titleHeight)
        }
        coupons[key] = img; return img
    }
}
