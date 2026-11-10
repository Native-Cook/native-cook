import CoreText
import ImageIO
import SwiftUI
import UIKit

final class SerumDropperRoutineResources {
    static let shared = SerumDropperRoutineResources()
    let products: [String:UIImage]
    let check: UIImage
    let fullRing: UIImage
    let emptyRing: UIImage
    let finger: UIImage
    private(set) var buttons: [UIImage] = []
    lazy var checkView = Image(uiImage:check)
    lazy var fullRingView = Image(uiImage:fullRing)
    lazy var emptyRingView = Image(uiImage:emptyRing)
    lazy var fingerView = Image(uiImage:finger)
    private var backgroundView = Image(uiImage:UIImage()), foregroundView = Image(uiImage:UIImage())
    private var decoded: [String:UIImage] = [:]
    private var cachedLayout: SerumDropperRoutineLayout?
    private var background = UIImage(), foreground = UIImage()
    static let names = ["100% Organic\nFruit Oil","Mineral UV\nFilters SPF","Niacinamide\n10% + Zinc 1%","Hyaluronic\nAcid Powder"]
    static let colors = [0xE7EDF8,0xFBEDE3,0xEEEAF8,0xFBEDE3]
    static func color(_ hex: Int, _ alpha: Double = 1) -> UIColor {
        UIColor(red:Double((hex>>16)&255)/255,green:Double((hex>>8)&255)/255,blue:Double(hex&255)/255,alpha:alpha)
    }
    static func bitmap(_ size: CGSize, _ draw: (CGContext)->Void) -> UIImage {
        let format = UIGraphicsImageRendererFormat(); format.scale = UIScreen.main.scale
        return UIGraphicsImageRenderer(size:size,format:format).image { draw($0.cgContext) }
    }
    static func path(_ c: CGContext, _ xy: [CGPoint], _ hex: Int = 0x151518, _ width: Double = 1.8) {
        c.beginPath(); if let p = xy.first { c.move(to:p) }
        for p in xy.dropFirst() { c.addLine(to:p) }
        c.setStrokeColor(color(hex).cgColor); c.setLineWidth(width); c.setLineCap(.round); c.setLineJoin(.round); c.strokePath()
    }
    init() {
        for weight in ["Regular","Medium","SemiBold"] {
            if let url = Bundle.main.url(forResource:"SerumDropperRoutine-Poppins-\(weight)",withExtension:"ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil)
            }
        }
        products = Dictionary(uniqueKeysWithValues:["bottle","dropper","tube","jar","white"].map { name in
            let url = Bundle.main.url(forResource:"SerumDropperRoutine-sdr-\(name)",withExtension:"png")
            let source = url.flatMap { CGImageSourceCreateWithURL($0 as CFURL,nil) }
            let image = source.flatMap { CGImageSourceCreateImageAtIndex($0,0,[kCGImageSourceShouldCacheImmediately:true] as CFDictionary) }
            return (name,image.map { UIImage(cgImage:$0) } ?? UIImage())
        })
        check = Self.bitmap(CGSize(width:13,height:10)) { c in
            Self.path(c,[CGPoint(x:1.5,y:5.2),CGPoint(x:4.8,y:8.4),CGPoint(x:11.5,y:1.6)])
        }
        fullRing = Self.bitmap(CGSize(width:30,height:30)) { c in
            c.setFillColor(UIColor.white.withAlphaComponent(0.95).cgColor); c.fillEllipse(in:CGRect(x:0,y:0,width:30,height:30))
        }
        emptyRing = Self.bitmap(CGSize(width:30,height:30)) { c in
            c.setStrokeColor(UIColor.white.withAlphaComponent(0.95).cgColor); c.setLineWidth(1.4)
            c.strokeEllipse(in:CGRect(x:0.7,y:0.7,width:28.6,height:28.6))
        }
        finger = Self.bitmap(CGSize(width:44,height:44)) { c in
            c.setFillColor(Self.color(0x141414,0.06).cgColor)
            c.fillEllipse(in:CGRect(x:0,y:0,width:44,height:44))
            c.setStrokeColor(Self.color(0x141414,0.35).cgColor); c.setLineWidth(1.5)
            c.strokeEllipse(in:CGRect(x:0.75,y:0.75,width:42.5,height:42.5))
        }
    }
    // Match RCTDecodeImageWithData's cover thumbnail, including Yoga's Float dimensions.
    // ImageIO work happens only when the layout changes, never while a drop is animating.
    func image(_ name: String,_ width: Double,_ height: Double) -> UIImage {
        let original = products[name]!
        guard let sourceImage = original.cgImage else { return original }
        let scale = UIScreen.main.scale
        let w = Double(Float(width)), h = Double(Float(height))
        let aspect = Double(sourceImage.width)/Double(sourceImage.height)
        let targetWidth = w/h <= aspect ? h*aspect : w
        let targetHeight = w/h <= aspect ? h : w/aspect
        let pixelWidth = ceil(targetWidth*scale), pixelHeight = ceil(targetHeight*scale)
        let key = "\(name)/\(pixelWidth)/\(pixelHeight)"
        if let image = decoded[key] { return image }
        guard sourceImage.width > Int(pixelWidth) || sourceImage.height > Int(pixelHeight),
              let url = Bundle.main.url(forResource:"SerumDropperRoutine-sdr-\(name)",withExtension:"png"),
              let source = CGImageSourceCreateWithURL(url as CFURL,nil),
              let thumb = CGImageSourceCreateThumbnailAtIndex(source,0,[
                kCGImageSourceShouldAllowFloat:true,
                kCGImageSourceCreateThumbnailWithTransform:true,
                kCGImageSourceCreateThumbnailFromImageAlways:true,
                kCGImageSourceThumbnailMaxPixelSize:max(pixelWidth,pixelHeight)
              ] as CFDictionary) else { return original }
        let image = UIImage(cgImage:thumb,scale:scale,orientation:.up)
        decoded[key] = image
        return image
    }
    func layers(_ l: SerumDropperRoutineLayout) -> (Image,Image) {
        if cachedLayout != l {
            cachedLayout = l
            background = Self.bitmap(CGSize(width:l.w,height:l.h)) { c in drawBackground(c,l) }
            foreground = Self.bitmap(CGSize(width:l.w,height:l.h)) { c in drawForeground(c,l) }
            buttons = (0..<3).map { i in
                let size = i == 2 ? 72.0 : 44.0
                return Self.bitmap(CGSize(width:size,height:size)) { c in
                    if i == 2 {
                        c.translateBy(x:36-200*l.sx,y:36-l.y(795)); drawFab(c,l)
                    } else {
                        rect(c,CGRect(x:0,y:0,width:44,height:44),12,0xffffff)
                        if i == 0 {
                            c.translateBy(x:22-4.5,y:22-7.5)
                            Self.path(c,[CGPoint(x:7.5,y:1.5),CGPoint(x:1.8,y:7.5),CGPoint(x:7.5,y:13.5)])
                        } else {
                            c.setFillColor(Self.color(0x151518).cgColor)
                            for x in [3.0,11.0] { c.fillEllipse(in:CGRect(x:15+x-1.8,y:22-1.8,width:3.6,height:3.6)) }
                        }
                    }
                }
            }
            backgroundView = Image(uiImage:background)
            foregroundView = Image(uiImage:foreground)
        }
        return (backgroundView,foregroundView)
    }
    private func rect(_ c: CGContext,_ box: CGRect,_ radius: Double,_ hex: Int,_ alpha: Double = 1, corners: UIRectCorner = .allCorners) {
        c.setFillColor(Self.color(hex,alpha).cgColor)
        c.addPath(UIBezierPath(roundedRect:box,byRoundingCorners:corners,cornerRadii:CGSize(width:radius,height:radius)).cgPath); c.fillPath()
    }
    private func text(_ s: String,_ x: Double,_ y: Double,_ size: Double,_ weight: String = "Regular",_ color: Int = 0x151518,_ lineHeight: Double? = nil,center: Bool = false) {
        let f = UIFont(name:"Poppins-\(weight)",size:size) ?? UIFont.systemFont(ofSize:size)
        let p = NSMutableParagraphStyle()
        if let lineHeight { p.minimumLineHeight = lineHeight; p.maximumLineHeight = lineHeight }
        let attributes: [NSAttributedString.Key:Any] = [.font:f,.foregroundColor:Self.color(color),.paragraphStyle:p]
        let str = NSAttributedString(string:s,attributes:attributes)
        let w = center ? str.size().width : 180
        str.draw(in:CGRect(x:center ? x-w/2 : x,y:y,width:w,height:100))
    }
    private func drawBackground(_ c: CGContext,_ l: SerumDropperRoutineLayout) {
        c.setFillColor(UIColor.white.cgColor); c.fill(CGRect(x:0,y:0,width:l.w,height:l.h))
        rect(c,CGRect(x:0,y:0,width:l.w,height:l.y(270)),28,0xF6F6F7,corners:[.bottomLeft,.bottomRight])
        text("Daily routine",80*l.sx,l.y(100)-12,16,"Medium")
        let xs = [25.0,105.0,185.0,264.0,345.0], days = ["Mon","Tue","Wed","Thu","Fri"]
        let numberFont = UIFont(name:"Poppins-Medium",size:24)!
        let dayFont = UIFont(name:"Poppins-Regular",size:12)!
        for i in 0..<5 {
            let box = CGRect(x:xs[i]*l.sx,y:l.y(150),width:75*l.sx,height:100*l.k)
            if i == 1 {
                c.saveGState(); c.setShadow(offset:CGSize(width:0,height:4),blur:20,color:UIColor.black.withAlphaComponent(0.05).cgColor)
                rect(c,box,22,0xffffff); c.restoreGState()
            }
            let nh = ceil(numberFont.lineHeight), dh = ceil(dayFont.lineHeight)
            let y = box.midY-(nh+dh+2)/2
            text("\(i+5)",box.midX,y,24,"Medium",center:true)
            text(days[i],box.midX,y+nh+2-1/UIScreen.main.scale,12,i == 1 ? "Medium" : "Regular",i == 1 ? 0x151518 : 0xB3B3B8,center:true)
        }
        for (i,b) in SerumDropperRoutineLayout.cards.enumerated() {
            rect(c,CGRect(x:b.0*l.sx,y:l.y(b.1),width:b.2*l.sx,height:b.3*l.k),22,Self.colors[i])
        }
    }
    private func drawForeground(_ c: CGContext,_ l: SerumDropperRoutineLayout) {
        for (i,p) in SerumDropperRoutineLayout.labels.enumerated() {
            text(Self.names[i],p.0*l.sx,l.y(p.1)-10,15,"Regular",0x151518,20)
            text("Step \(i+1)",p.0*l.sx,l.y(p.1)-10+40+4,11,"Regular",0xB3B3B8)
        }
        rect(c,CGRect(x:0,y:l.y(780),width:l.w,height:l.h-l.y(780)),28,0xffffff,corners:[.topLeft,.topRight])
        for (i,x) in [66.0,122.0,276.0,326.0].enumerated() {
            c.saveGState(); c.translateBy(x:x*l.sx-11,y:l.y(780)+22+18-(i == 3 ? 10 : 11))
            navIcon(c,i); c.restoreGState()
        }
    }
    private func drawFab(_ c: CGContext,_ l: SerumDropperRoutineLayout) {
        let box = CGRect(x:200*l.sx-36,y:l.y(795)-36,width:72,height:72)
        rect(c,box,36,0xffffff)
        rect(c,box.insetBy(dx:4,dy:4),32,0x151518)
        c.saveGState(); c.translateBy(x:200*l.sx-13,y:l.y(795)-13)
        c.setStrokeColor(UIColor.white.cgColor); c.setLineWidth(1.8); c.setLineCap(.round)
        for (x,y,flipX,flipY) in [(2.0,2.0,1.0,1.0),(24.0,2.0,-1.0,1.0),(24.0,24.0,-1.0,-1.0),(2.0,24.0,1.0,-1.0)] {
            c.move(to:CGPoint(x:x,y:y+6*flipY)); c.addLine(to:CGPoint(x:x,y:y+3*flipY))
            c.addArc(tangent1End:CGPoint(x:x,y:y),tangent2End:CGPoint(x:x+3*flipX,y:y),radius:3)
            c.addLine(to:CGPoint(x:x+6*flipX,y:y))
        }
        c.strokePath(); c.setFillColor(UIColor.white.cgColor)
        for x in [9.5,16.5] { c.fillEllipse(in:CGRect(x:x-1.3,y:9.2,width:2.6,height:2.6)) }
        c.setLineWidth(1.6); c.move(to:CGPoint(x:9,y:16)); c.addQuadCurve(to:CGPoint(x:17,y:16),control:CGPoint(x:13,y:19)); c.strokePath()
        c.restoreGState()
    }
    private func navIcon(_ c: CGContext,_ kind: Int) {
        c.setStrokeColor(Self.color(0x9A9AA4).cgColor); c.setFillColor(Self.color(0x9A9AA4).cgColor)
        c.setLineWidth(1.6); c.setLineJoin(.round)
        switch kind {
        case 0:
            c.move(to:CGPoint(x:11,y:2)); c.addLine(to:CGPoint(x:19.5,y:7)); c.addLine(to:CGPoint(x:19.5,y:16))
            c.addArc(tangent1End:CGPoint(x:19.5,y:19),tangent2End:CGPoint(x:16.5,y:19),radius:3)
            c.addLine(to:CGPoint(x:5.5,y:19)); c.addArc(tangent1End:CGPoint(x:2.5,y:19),tangent2End:CGPoint(x:2.5,y:16),radius:3)
            c.addLine(to:CGPoint(x:2.5,y:7)); c.closePath(); c.strokePath()
            rect(c,CGRect(x:9.5,y:12,width:3,height:4),1,0x9A9AA4)
        case 1:
            let xy = [CGPoint(x:3,y:4),CGPoint(x:19,y:4),CGPoint(x:19,y:15),CGPoint(x:9,y:15),CGPoint(x:5,y:19),CGPoint(x:5,y:15),CGPoint(x:3,y:15),CGPoint(x:3,y:4)]
            Self.path(c,xy,0x9A9AA4,1.6)
            for x in [7.0,11.0,15.0] { c.fillEllipse(in:CGRect(x:x-1,y:8.5,width:2,height:2)) }
        case 2:
            Self.path(c,[CGPoint(x:11,y:2),CGPoint(x:18.8,y:6.5),CGPoint(x:18.8,y:15.5),CGPoint(x:11,y:20),CGPoint(x:3.2,y:15.5),CGPoint(x:3.2,y:6.5),CGPoint(x:11,y:2)],0x9A9AA4,1.6)
            c.strokeEllipse(in:CGRect(x:8,y:8,width:6,height:6))
        default:
            c.move(to:CGPoint(x:11,y:18.2)); c.addCurve(to:CGPoint(x:1.6,y:6.6),control1:CGPoint(x:4,y:13.4),control2:CGPoint(x:1.6,y:10.2))
            // SVG scales the 4.8 radii to half the endpoint distance here.
            let radius = hypot(4.7,1.0)
            let a = atan2(1.0,-4.7), b = atan2(-1.0,-4.7)
            c.addArc(center:CGPoint(x:6.3,y:5.6),radius:radius,startAngle:a,endAngle:a + .pi,clockwise:false)
            c.addArc(center:CGPoint(x:15.7,y:5.6),radius:radius,startAngle:b,endAngle:b + .pi,clockwise:false)
            c.addCurve(to:CGPoint(x:11,y:18.2),control1:CGPoint(x:20.4,y:10.2),control2:CGPoint(x:18,y:13.4)); c.closePath(); c.strokePath()
        }
    }
}
