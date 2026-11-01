import CoreText
import Accelerate
import UIKit

final class SetupDomeLoaderResources {
    static let shared = SetupDomeLoaderResources()
    typealias A = SetupDomeLoaderArt
    private var names: [String:String] = [:]
    private var fonts: [String:UIFont] = [:]
    private let scale = UIScreen.main.scale
    private lazy var brandWidth = ceil(("kinto" as NSString).size(withAttributes:[.font:font("Bold",19),.kern:-0.4]).width*scale)/scale
    private lazy var brandHeight = ceil(font("Bold",19).lineHeight*scale)/scale
    struct Label {
        let storage: NSTextStorage
        let manager: NSLayoutManager
        let glyphs: NSRange
        let offsetY: Double
    }
    private var labels: [String:Label] = [:]
    private var copyWidth = -1.0
    struct Primitive {
        var path: CGPath
        var color: Int
        var alpha = 1.0
        var stroke = 0.0
    }
    private var itemPaths: [Int:[Primitive]] = [:]
    private var itemUnit = 0.0
    private var keyGlow: CGImage?
    private var glowRy = 0.0
    private var beadGlows: [CGImage] = []
    private var platformLayer: CGLayer?
    private var platformSize = CGSize.zero
    private var bitmapPlatformLayer: CGLayer?
    private var bitmapPlatformSize = CGSize.zero
    private var renderLayer: CGLayer?
    private var backdropLayer: CGLayer?
    private var renderGeo: SetupDomeLoaderGeo?
    private var backdropPhase = -1.0
    private var keyPath: CGPath?
    private var panels: [Int:(geo:SetupDomeLoaderGeo,a:Double,b:Double,path:CGPath)] = [:]
    private func makeLayer(_ size: CGSize,_ space: CGColorSpace) -> CGLayer? {
        let flags = CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.floatComponents.rawValue | CGBitmapInfo.byteOrder16Little.rawValue
        guard let seed = CGContext(data:nil,width:1,height:1,bitsPerComponent:16,bytesPerRow:8,space:space,bitmapInfo:flags),
              let layer = CGLayer(seed,size:CGSize(width:size.width*scale,height:size.height*scale),auxiliaryInfo:nil),
              let context = layer.context else { return nil }
        context.scaleBy(x:scale,y:scale)
        return layer
    }
    func drawBase(_ target: CGContext,_ g: SetupDomeLoaderGeo,_ phase: SetupDomeLoaderMotion.Phase) {
        // Initialize the static platform from the native Canvas and preserve its exact raster.
        drawPlatform(target,g,0)
        let top = floor((g.by-g.ry*2.7)*scale)/scale
        let bottom = ceil((g.by+g.ry*2.7)*scale)/scale
        let rect = CGRect(x:0,y:top,width:g.cx*2,height:bottom-top)
        if renderGeo != g {
            renderGeo = g
            renderLayer = CGColorSpace(name:CGColorSpace.displayP3).flatMap { makeLayer(rect.size,$0) }
            backdropLayer = makeLayer(rect.size,A.space)
            renderLayer?.context?.translateBy(x:-rect.minX,y:-rect.minY)
            backdropLayer?.context?.translateBy(x:-rect.minX,y:-rect.minY)
            if let context = renderLayer?.context { drawPlatform(context,g,0,true) }
            backdropPhase = -1
        }
        guard phase.k > 0 || phase.burst > 0 else { return }
        guard let layer = renderLayer, let context = layer.context,
              let backdrop = backdropLayer, let background = backdrop.context else {
            drawPlatform(target,g,phase.k)
            A(cv:target).burst(g,phase.burst)
            return
        }
        // Match Skia's green alpha compositing without converting the rest of the Canvas.
        // This landing paint occurs only after the glass and its contents have collapsed.
        let check = SetupDomeLoaderMotion.clamp(phase.chk)
        if backdropPhase != check {
            A(cv:background).rect(rect.minX,rect.minY,rect.width,rect.height,0x0D0D12)
            A(cv:background).backdrop(g,check)
            backdropPhase = check
        }
        context.draw(backdrop,in:rect)
        drawPlatform(context,g,phase.k,true)
        A(cv:context).burst(g,phase.burst)
        target.draw(layer,in:rect)
    }
    func panel(_ slot: Int,_ geo: SetupDomeLoaderGeo,_ a: Double,_ b: Double,_ make: ()->CGPath) -> CGPath {
        if let cached = panels[slot], cached.geo == geo && cached.a == a && cached.b == b { return cached.path }
        let path = make()
        panels[slot] = (geo,a,b,path)
        return path
    }
    init() {
        for name in ["Inter-Regular","Inter-SemiBold","Inter-Bold","Ionicons"] {
            if let url = Bundle.main.url(forResource:"SetupDomeLoader-"+name,withExtension:"ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil)
                if let data = CGDataProvider(url:url as CFURL), let font = CGFont(data), let ps = font.postScriptName {
                    names[name] = ps as String
                }
            }
        }
        // Cache the same TextKit layout used by RN; draw glyphs without resampling a bitmap.
        labels["note"] = label("This can take up to 2 minutes","Regular",13,0x6F6E86,CGSize(width:260,height:17),topAligned:true)
        labels["cta"] = label("Continue","SemiBold",17,0x0D0D12,CGSize(width:160,height:22))
        labels["brand"] = label("kinto","Bold",19,0xF4F3FA,CGSize(width:80,height:25),nil,-0.4)
        for (i,s) in ["Verifying your details","Creating your card","Securing your wallet"].enumerated() {
            labels["step\(i)"] = label(s,"Regular",15,0xD9D8EA,CGSize(width:232,height:20),nil,0,false)
        }
        labels["back"] = label(String(UnicodeScalar(61735)!),"Ionicons",22,0xE9E8F5,CGSize(width:22,height:22))
        labels["help"] = label(String(UnicodeScalar(62334)!),"Ionicons",23,0xE9E8F5,CGSize(width:23,height:23))
        labels["done"] = label(String(UnicodeScalar(61982)!),"Ionicons",18,0x3BE07A,CGSize(width:18,height:18))
        for i in 0...120 {
            let radius = 8+Double(i)*0.05
            beadGlows.append(glow(8) { cv in
                A(cv:cv).circle(50,50,radius,0xFFFFFF)
            })
        }
    }
    private func font(_ weight: String,_ size: Double) -> UIFont {
        let key = "\(weight)-\(size)"
        if let cached = fonts[key] { return cached }
        let name = weight == "Ionicons" ? weight : "Inter-"+weight
        let f = UIFont(name:names[name] ?? name,size:size) ?? UIFont.systemFont(ofSize:size)
        fonts[key] = f; return f
    }
    // A separable Gaussian on a cached eight-bit mask, matching Skia's sigma in points.
    // Accelerate processes 121 masks in a fraction of a second without a GPU or Core Image.
    private func glow(_ sigma: Double,_ size: Double = 100,_ shape: (CGContext)->Void) -> CGImage {
        let n = Int(size*3), count = n*n
        var source = [UInt8](repeating:0,count:count)
        var horizontal = source, blurred = source
        source.withUnsafeMutableBytes { raw in
            if let cv = CGContext(data:raw.baseAddress,width:n,height:n,bitsPerComponent:8,bytesPerRow:n,space:CGColorSpaceCreateDeviceGray(),bitmapInfo:CGImageAlphaInfo.none.rawValue) {
                cv.translateBy(x:0,y:Double(n)); cv.scaleBy(x:3,y:-3)
                shape(cv)
            }
        }
        let pixelSigma = sigma*3, radius = Int(ceil(pixelSigma*3))
        let weights = (-radius...radius).map { exp(-Double($0*$0)/(2*pixelSigma*pixelSigma)) }
        let sum = weights.reduce(0,+)
        let kernel = weights.map { Int16(($0/sum*32768).rounded()) }
        let divisor = Int32(kernel.reduce(0) { Int($0)+Int($1) })
        source.withUnsafeMutableBytes { s in horizontal.withUnsafeMutableBytes { h in blurred.withUnsafeMutableBytes { b in
            var src = vImage_Buffer(data:s.baseAddress,height:vImagePixelCount(n),width:vImagePixelCount(n),rowBytes:n)
            var mid = vImage_Buffer(data:h.baseAddress,height:vImagePixelCount(n),width:vImagePixelCount(n),rowBytes:n)
            var dst = vImage_Buffer(data:b.baseAddress,height:vImagePixelCount(n),width:vImagePixelCount(n),rowBytes:n)
            kernel.withUnsafeBufferPointer { k in
                _ = vImageConvolve_Planar8(&src,&mid,nil,0,0,k.baseAddress!,1,UInt32(kernel.count),divisor,0,vImage_Flags(kvImageEdgeExtend))
                _ = vImageConvolve_Planar8(&mid,&dst,nil,0,0,k.baseAddress!,UInt32(kernel.count),1,divisor,0,vImage_Flags(kvImageEdgeExtend))
            }
        } } }
        let provider = CGDataProvider(data:Data(blurred) as CFData)!
        let decode: [CGFloat] = [1,0]
        return decode.withUnsafeBufferPointer {
            CGImage(maskWidth:n,height:n,bitsPerComponent:8,bitsPerPixel:8,bytesPerRow:n,provider:provider,decode:$0.baseAddress,shouldInterpolate:true)!
        }
    }
    private func drawGlow(_ image: CGImage,_ cv: CGContext,_ x: Double,_ y: Double,_ alpha: Double) {
        let width = Double(image.width)/3, height = Double(image.height)/3
        cv.saveGState()
        cv.translateBy(x:x-width/2,y:y+height/2); cv.scaleBy(x:1,y:-1)
        let rect = CGRect(x:0,y:0,width:width,height:height)
        cv.clip(to:rect,mask:image)
        cv.setFillColor(A.color(0x3BE07A,alpha)); cv.fill(rect)
        cv.restoreGState()
    }
    private func label(_ text: String,_ weight: String,_ size: Double,_ color: Int,_ box: CGSize,_ line: Double? = nil,_ tracking: Double = 0,_ centered: Bool = true,topAligned: Bool = false) -> Label {
        let face = font(weight,size)
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = centered ? .center : .left
        if let line { paragraph.minimumLineHeight = line; paragraph.maximumLineHeight = line }
        var attrs: [NSAttributedString.Key:Any] = [.font:face,.foregroundColor:UIColor(cgColor:A.color(color)),.paragraphStyle:paragraph]
        if tracking != 0 { attrs[.kern] = tracking }
        // Fabric's RCTTextLayoutManager: no font leading; center the font inside an explicit lineHeight.
        if let line, line >= face.lineHeight { attrs[.baselineOffset] = (line-face.lineHeight)/2 }
        let storage = NSTextStorage(string:text,attributes:attrs)
        let manager = NSLayoutManager(); manager.usesFontLeading = false
        let container = NSTextContainer(size:CGSize(width:box.width,height:box.height+10))
        container.lineFragmentPadding = 0; container.lineBreakMode = .byWordWrapping
        manager.addTextContainer(container); storage.addLayoutManager(manager)
        manager.ensureLayout(for:container)
        let glyphs = manager.glyphRange(for:container)
        let height = manager.usedRect(for:container).height
        return Label(storage:storage,manager:manager,glyphs:glyphs,offsetY:line == nil && !topAligned ? (box.height-height)/2 : 0)
    }
    func drawLabel(_ name: String,_ cv: CGContext,_ x: Double,_ y: Double,_ a: Double = 1) {
        guard let label = labels[name] else { return }
        cv.saveGState(); cv.setAlpha(a)
        UIGraphicsPushContext(cv)
        label.manager.drawGlyphs(forGlyphRange:label.glyphs,at:CGPoint(x:x,y:y+label.offsetY))
        UIGraphicsPopContext()
        cv.restoreGState()
    }
    func drawPlatform(_ cv: CGContext,_ g: SetupDomeLoaderGeo,_ lit: Double,_ bitmap: Bool = false) {
        let w = g.r*1.42, h = g.ry*1.42
        let origin = CGPoint(x:g.cx-w-4,y:g.by-h-4)
        let size = CGSize(width:w*2+8,height:h*2+24)
        if platformSize != size || platformLayer == nil {
            platformSize = size
            if let layer = CGLayer(cv,size:size,auxiliaryInfo:nil), let lc = layer.context {
                lc.translateBy(x:-origin.x,y:-origin.y)
                A(cv:lc).platform(g,0)
                platformLayer = layer
            }
            let key = CGMutablePath()
            key.addEllipse(in:CGRect(x:-11,y:-7,width:22,height:12))
            key.move(to:CGPoint(x:-5,y:1)); key.addLine(to:CGPoint(x:5,y:1))
            key.addLine(to:CGPoint(x:9,y:h*0.62)); key.addLine(to:CGPoint(x:-9,y:h*0.62)); key.closeSubpath()
            keyPath = key
            // Warm the only geometry-dependent Gaussian before any glowing frame.
            drawKeyGlow(cv,g,0)
        }
        if bitmap {
            // A layer recorded by SwiftUI's context cannot be drawn into a bitmap context.
            if bitmapPlatformSize != size || bitmapPlatformLayer == nil {
                bitmapPlatformSize = size
                if let layer = makeLayer(size,A.space), let context = layer.context {
                    context.translateBy(x:-origin.x,y:-origin.y)
                    A(cv:context).platform(g,0)
                    bitmapPlatformLayer = layer
                }
            }
            if let layer = bitmapPlatformLayer { cv.draw(layer,in:CGRect(origin:origin,size:size)) }
            else { A(cv:cv).platform(g,0) }
        } else if let layer = platformLayer { cv.draw(layer,at:origin) }
        else { A(cv:cv).platform(g,0) }
        if lit > 0, let key = keyPath {
            cv.saveGState(); cv.translateBy(x:g.cx,y:g.by)
            A(cv:cv).path(key,0x3BE07A,lit)
            cv.restoreGState()
            drawKeyGlow(cv,g,lit)
        }
    }
    func drawItem(_ cv: CGContext,_ kind: Int,_ u: Double,_ sx: Double,_ tilt: Double,_ alpha: Double) {
        if itemUnit != u { itemUnit = u; itemPaths.removeAll() }
        let key = kind*2+(sx<0 ? 1 : 0), back = sx < 0
        if itemPaths[key] == nil {
            var primitives: [Primitive] = []
            func rr(_ x: Double,_ y: Double,_ w: Double,_ h: Double,_ r: Double,_ color: Int,_ stroke: Double = 0) {
                primitives.append(.init(path:CGPath(roundedRect:CGRect(x:x,y:y,width:w,height:h),cornerWidth:r,cornerHeight:r,transform:nil),color:color,stroke:stroke))
            }
            func circle(_ x: Double,_ y: Double,_ r: Double,_ color: Int,_ a: Double = 1,_ stroke: Double = 0) {
                primitives.append(.init(path:CGPath(ellipseIn:CGRect(x:x-r,y:y-r,width:r*2,height:r*2),transform:nil),color:color,alpha:a,stroke:stroke))
            }
            if kind == 0 {
                rr(-u*0.9,-u*0.6,u*1.8,u*1.2,u*0.18,back ? 0x4A2A1A : 0x6B3D24)
                rr(-u*0.9,-u*0.6,u*1.8,u*0.62,u*0.18,back ? 0x5A3320 : 0x80492B)
                let line = CGMutablePath(); line.move(to:CGPoint(x:-u*0.75,y:0)); line.addLine(to:CGPoint(x:u*0.75,y:0))
                primitives.append(.init(path:line,color:0xC08A5C,alpha:0.55,stroke:1.2))
                if !back { circle(u*0.55,u*0.02,u*0.11,0xE8C27A) }
            } else if kind == 1 {
                rr(-u*0.62,-u*0.85,u*1.24,u*1.7,u*0.14,back ? 0x6E5320 : 0x3A2A14)
                rr(-u*0.62,-u*0.85,u*1.24,u*1.7,u*0.14,0xE3B34E,2)
                if !back {
                    let cell = u*0.17
                    for i in 0..<5 { for j in 0..<5 {
                        if (i*7+j*3+i*j)%3 != 0 || (i<2 && j<2) || (i>2 && j<2) || (i<2 && j>2) {
                            primitives.append(.init(path:CGPath(rect:CGRect(x:-u*0.43+Double(i)*cell,y:-u*0.43+Double(j)*cell,width:cell*0.82,height:cell*0.82),transform:nil),color:0xF2C55E))
                        }
                    } }
                }
            } else {
                circle(0,0,u*0.48,0xC9581F)
                circle(0,0,u*0.44,back ? 0xE07030 : 0xF28A3A)
                circle(0,0,u*0.28,0xFFB070,0.7,1.6)
            }
            itemPaths[key] = primitives
        }
        cv.saveGState(); cv.rotate(by:tilt*Double.pi/180); cv.scaleBy(x:max(0.08,abs(sx)),y:1)
        for primitive in itemPaths[key] ?? [] {
            A(cv:cv).path(primitive.path,primitive.color,primitive.alpha*alpha,primitive.stroke)
        }
        cv.restoreGState()
    }
    func drawKeyGlow(_ cv: CGContext,_ g: SetupDomeLoaderGeo,_ alpha: Double) {
        if keyGlow == nil || glowRy != g.ry {
            glowRy = g.ry
            keyGlow = glow(10,140) { c in
                c.translateBy(x:20,y:20)
                let p = CGMutablePath(); p.addEllipse(in:CGRect(x:39,y:43,width:22,height:12))
                p.move(to:CGPoint(x:45,y:51)); p.addLine(to:CGPoint(x:55,y:51))
                p.addLine(to:CGPoint(x:59,y:50+g.ry*1.42*0.62)); p.addLine(to:CGPoint(x:41,y:50+g.ry*1.42*0.62)); p.closeSubpath()
                A(cv:c).path(p,0xFFFFFF)
            }
        }
        if let keyGlow { drawGlow(keyGlow,cv,g.cx,g.by,0.55*alpha) }
    }
    func drawBeadGlow(_ cv: CGContext,_ x: Double,_ y: Double,_ r: Double) {
        let index = max(0,min(120,Int(((r-8)/0.05).rounded())))
        drawGlow(beadGlows[index],cv,x,y,0.5)
    }
    func ui(_ cv: CGContext,_ size: CGSize,_ top: Double,_ bottom: Double,_ t: Double,_ pressed: Bool) {
        if copyWidth != size.width-48 {
            copyWidth = size.width-48
            labels["title"] = label("We're setting up\nyour account","SemiBold",22,0xF4F3FA,CGSize(width:copyWidth,height:58),29)
            labels["success"] = label("You're all set","SemiBold",22,0xF4F3FA,CGSize(width:copyWidth,height:29),29)
            labels["sub"] = label("Your card and wallet are ready to use.","Regular",15,0x9E9DB5,CGSize(width:copyWidth,height:84),21)
        }
        let a = A(cv:cv), g = SetupDomeLoaderGeo(size), ph = SetupDomeLoaderMotion.phase(t)
        drawLabel("back",cv,23,top+15)
        drawLabel("help",cv,size.width-45.5,top+14.5)
        // The header's flex row centers the complete brand including its seven-point dot.
        let brandX = (((size.width-brandWidth-9)/2)*scale).rounded()/scale
        // Yoga rounds the measured brand box and its origin to physical pixels.
        drawLabel("brand",cv,brandX+(brandWidth-80)/2,top+13.5+1/scale)
        let markY = ((top+30-brandHeight/2)*scale).rounded()/scale+3.5
        a.circle(brandX+brandWidth+5.5,markY,3.5,0xF6B73C)
        let y = g.by+g.ry*1.42+52
        let old = 1-SetupDomeLoaderMotion.clamp(ph.swap*2), new = SetupDomeLoaderMotion.clamp(ph.swap*2-1)
        cv.saveGState(); cv.setAlpha(old); cv.translateBy(x:0,y:(1-old)*(-8)); cv.beginTransparencyLayer(auxiliaryInfo:nil)
        drawLabel("title",cv,24,y)
        let sy = y+58+20, x = (size.width-260)/2
        for i in 0..<3 {
            let end = [2.3,4.6,7.0][i], start = [0,2.3,4.6][i]
            let fade = SetupDomeLoaderMotion.ramp(t,8.5,8.9)
            let done = t >= end
            let opacity = t >= start && t < end ? 1.0 : done ? 0.9 + ((i == 0 ? 1.0 : 0.4) - 0.9) * fade : 0.4
            cv.saveGState(); cv.setAlpha(opacity); cv.beginTransparencyLayer(auxiliaryInfo:nil)
            a.circle(x+9,sy+Double(i)*28+14,3.25,0x8E8DB0,1,1.5)
            if done && fade < 1 { cv.saveGState(); cv.setAlpha(1-fade); drawLabel("done",cv,x,sy+Double(i)*28+5); cv.restoreGState() }
            drawLabel("step\(i)",cv,x+28,sy+Double(i)*28+4-1/scale)
            cv.endTransparencyLayer(); cv.restoreGState()
        }
        a.rr(x,sy+98,260,4,2,0xFFFFFF,0.08)
        cv.saveGState(); cv.addPath(CGPath(roundedRect:CGRect(x:x,y:sy+98,width:260,height:4),cornerWidth:2,cornerHeight:2,transform:nil)); cv.clip()
        a.path(CGPath(roundedRect:CGRect(x:x,y:sy+98,width:260*ph.prog,height:4),cornerWidth:2*ph.prog,cornerHeight:2,transform:nil),0x8B89F0)
        cv.restoreGState()
        drawLabel("note",cv,x,sy+112)
        cv.endTransparencyLayer(); cv.restoreGState()
        cv.saveGState(); cv.setAlpha(new); cv.translateBy(x:0,y:(1-new)*10); cv.beginTransparencyLayer(auxiliaryInfo:nil)
        drawLabel("success",cv,24,y)
        drawLabel("sub",cv,24,y+39)
        cv.endTransparencyLayer(); cv.restoreGState()
        cv.saveGState(); cv.setAlpha(new); cv.translateBy(x:size.width/2,y:size.height-bottom-16-27+(1-new)*16)
        let scale = pressed ? 0.96 : 1.0; cv.scaleBy(x:scale,y:scale)
        cv.beginTransparencyLayer(auxiliaryInfo:nil)
        a.rr(-size.width/2+20,-27,size.width-40,54,27,0xF4F3FA)
        drawLabel("cta",cv,-80,-11)
        cv.endTransparencyLayer(); cv.restoreGState()
    }
}
