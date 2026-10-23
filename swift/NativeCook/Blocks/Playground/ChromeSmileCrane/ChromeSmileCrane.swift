
import SwiftUI
import UIKit

/// All geometry and motion live in this file. `previewTime` selects an exact loop phase.
struct ChromeSmileCrane: View {
    static let loop = 8.0
    var autoPlay = false
    var previewTime: Double? = nil
    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var start = Date()
    @State private var lastTouch: Date? = nil
    @State private var phase = 0.0
    @State private var gestureStart: Double? = nil
    @State private var point = CGPoint(x:260,y:426.0)
    @State private var velocity = 0.0

    private func time(_ date: Date) -> Double {
        if let previewTime { return max(0,previewTime).truncatingRemainder(dividingBy:Self.loop) }
        if reducedMotion { return 0 }
        if let lastTouch {
            let age = max(0,date.timeIntervalSince(lastTouch))
            if gestureStart != nil { return phase }
            if age < 2 { return min(Self.loop-0.001,max(0,phase + velocity*age*exp(-8*age))) }
            if age < 2.4 { return phase*(1-smooth((age-2)/0.4)) }
            return autoPlay ? (age-2.4).truncatingRemainder(dividingBy:Self.loop) : 0
        }
        return autoPlay ? max(0,date.timeIntervalSince(start)).truncatingRemainder(dividingBy:Self.loop) : 0
    }
    var body: some View {
        GeometryReader { geometry in
            TimelineView(.animation(minimumInterval:1.0/60,paused:scenePhase != .active || reducedMotion || previewTime != nil || (!autoPlay && lastTouch == nil))) { timeline in
                Canvas { context,size in
                    let p=ChromeSmileCranePainter(context)
                    p.r(0,0,size.width,size.height,0x08ACCB)
                    let scale=min(size.width/393,size.height/852)
                    p.translate((size.width-393*scale)/2,(size.height-852*scale)/2)
                    p.scale(scale,scale)
                    let age=lastTouch.map { max(0,timeline.date.timeIntervalSince($0)) } ?? 100
                    let strength = reducedMotion || previewTime != nil ? 0 : (gestureStart != nil ? 1 : 1-smooth((age-2)/0.4))
                    drawScene(p,time(timeline.date),260+(point.x-260)*strength,426.0+(point.y-426.0)*strength,strength>0)
                }
                .contentShape(Rectangle())
                .gesture(DragGesture(minimumDistance:0).onChanged { value in
                    if gestureStart == nil { gestureStart=time(.now);phase=gestureStart ?? 0 }
                    let scale=min(geometry.size.width/393,geometry.size.height/852)
                    point=CGPoint(x:value.location.x/scale,y:value.location.y/scale)
                    let distance = value.translation.width
                    phase=min(Self.loop-0.001,max(0,(gestureStart ?? 0)+distance/90))
                    lastTouch = .now
                }.onEnded { value in
                    if abs(value.translation.width)+abs(value.translation.height)<5 { phase=3.8 }
                    velocity=max(-5,min(5,value.velocity.width/90))
                    lastTouch = .now;gestureStart=nil
                    UIImpactFeedbackGenerator(style:.light).impactOccurred()
                })
                .accessibilityElement(children:.ignore)
                .accessibilityLabel(previewTime.map { "Chrome Smile Crane preview "+String(Int(($0*1000).rounded())) } ?? "Chrome Smile Crane")
                .accessibilityHint("Drag to guide the claw. Tap to catch a smile.")
                .accessibilityValue("Interactive motion preview")
                .accessibilityAdjustableAction { direction in
                    phase=min(Self.loop-0.001,max(0,phase+(direction == .increment ? 1:-1)))
                    lastTouch = .now
                }
            }
        }
        .ignoresSafeArea()
        .background(ChromeSmileCranePainter.color(0x08ACCB))
        .preferredColorScheme(.light)
        .task(id:lastTouch) {
            guard let touched=lastTouch else { return }
            try? await Task.sleep(for:.seconds(2.4))
            guard !Task.isCancelled,lastTouch == touched,gestureStart == nil else { return }
            start = .now;lastTouch=nil;phase=0
        }
        .onChange(of:scenePhase) { _,newPhase in if newPhase == .active { start = .now;lastTouch=nil } }
    }
}

private func smooth(_ value: Double) -> Double { let u = min(1, max(0, value)); return u*u*u*(10+u*(-15+6*u)) }
private func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { smooth((t-a)/(b-a)) }
private func pick<T>(_ condition: Bool, _ a: T, _ b: T) -> T { condition ? a : b }
private func str(_ value: Double) -> String { String(Int(value)) }
private final class ChromeSmileCranePainter {
    var c: GraphicsContext
    var stack: [GraphicsContext] = []
    init(_ context: GraphicsContext) { c = context }
    static func color(_ hex: Int, _ alpha: Double = 1) -> Color { Color(red: Double((hex >> 16)&255)/255, green: Double((hex >> 8)&255)/255, blue: Double(hex&255)/255).opacity(min(1,max(0,alpha))) }
    func save() { stack.append(c) }
    func restore() { c = stack.removeLast() }
    func translate(_ x: Double, _ y: Double) { c.translateBy(x:x,y:y) }
    func scale(_ x: Double, _ y: Double) { c.scaleBy(x:x,y:y) }
    func rotate(_ angle: Double) { c.rotate(by:.degrees(angle)) }
    func skew(_ x:Double,_ y:Double) { c.concatenate(CGAffineTransform(a:1,b:y,c:x,d:1,tx:0,ty:0)) }
    func blur(_ radius:Double) { if radius>0 { c.addFilter(.blur(radius:radius)) } }
    func r(_ x: Double,_ y: Double,_ w: Double,_ h: Double,_ color: Int,_ radius: Double = 0,_ alpha: Double = 1) { c.fill(Path(roundedRect:CGRect(x:x,y:y,width:max(0,w),height:max(0,h)),cornerRadius:radius),with:.color(Self.color(color,alpha))) }
    func e(_ x: Double,_ y: Double,_ rx: Double,_ ry: Double,_ color: Int,_ alpha: Double = 1) { c.fill(Path(ellipseIn:CGRect(x:x-rx,y:y-ry,width:rx*2,height:ry*2)),with:.color(Self.color(color,alpha))) }
    func line(_ x: Double,_ y: Double,_ xx: Double,_ yy: Double,_ color: Int,_ width: Double = 1,_ alpha: Double = 1) { var path = Path();path.move(to:CGPoint(x:x,y:y));path.addLine(to:CGPoint(x:xx,y:yy));c.stroke(path,with:.color(Self.color(color,alpha)),style:StrokeStyle(lineWidth:width,lineCap:.round)) }
    func grad(_ x: Double,_ y: Double,_ w: Double,_ h: Double,_ a: Int,_ b: Int,_ d: Int,_ radius: Double = 0,_ horizontal: Bool = false,_ alpha: Double = 1) { let path=Path(roundedRect:CGRect(x:x,y:y,width:max(0,w),height:max(0,h)),cornerRadius:radius);c.fill(path,with:.linearGradient(Gradient(colors:[Self.color(a,alpha),Self.color(b,alpha),Self.color(d,alpha)]),startPoint:CGPoint(x:x,y:y),endPoint:CGPoint(x:horizontal ? x+w:x,y:horizontal ? y:y+h))) }
    func radial(_ x: Double,_ y: Double,_ radius: Double,_ a: Int,_ b: Int,_ alpha: Double = 1) { let path=Path(ellipseIn:CGRect(x:x-radius,y:y-radius,width:radius*2,height:radius*2));c.fill(path,with:.radialGradient(Gradient(colors:[Self.color(a,alpha),Self.color(b,0)]),center:CGPoint(x:x,y:y),startRadius:0,endRadius:radius)) }
    func clip(_ x: Double,_ y: Double,_ w: Double,_ h: Double,_ radius: Double = 0) { c.clip(to:Path(roundedRect:CGRect(x:x,y:y,width:w,height:h),cornerRadius:radius)) }
    func text(_ value: String,_ x: Double,_ y: Double,_ size: Double,_ color: Int,_ style: String = "regular",_ align: Double = 0,_ alpha: Double = 1) {
        let weight: Font.Weight = style == "heavy" || style == "condensed" ? .black : style == "bold" || style == "serif" ? .bold : .regular
        var font = Font.system(size:size,weight:weight,design:style == "mono" ? .monospaced : style == "serif" || style == "italic" ? .serif : .default)
        if style == "condensed" { font = font.width(.condensed) }
        if style == "italic" { font = font.italic().weight(.bold) }
        c.draw(Text(value).font(font).foregroundColor(Self.color(color,alpha)),at:CGPoint(x:x,y:y),anchor:align == 1 ? .top : align == 2 ? .topTrailing : .topLeading)
    }
    func fit(_ value:String,_ x:Double,_ y:Double,_ size:Double,_ width:Double,_ color:Int,_ style:String = "heavy") {
        let weight:Font.Weight = style == "heavy" || style == "condensed" ? .black : .bold
        var font=Font.system(size:size,weight:weight,design:style == "serif" || style == "italic" ? .serif : .default)
        if style == "condensed" { font=font.width(.condensed) }
        if style == "italic" { font=font.italic() }
        let measured=c.resolve(Text(value).font(font)).measure(in:CGSize(width:10000,height:1000)).width
        save();translate(x,y);scale(width/max(1,measured),1);text(value,0,0,size,color,style,1);restore()
    }

    // A deterministic raster, built once and reused by every animation frame.
    static let grainImage:CGImage = {
        let width=192,height=384
        var data=[UInt8](repeating:255,count:width*height*4)
        var seed:UInt32=41389
        for index in 0..<(width*height) {
            seed=1664525 &* seed &+ 1013904223
            let shade=UInt8(96+Int(seed >> 24)%80)
            data[index*4]=shade;data[index*4+1]=shade;data[index*4+2]=shade
        }
        let provider=CGDataProvider(data:Data(data) as CFData)!
        return CGImage(width:width,height:height,bitsPerComponent:8,bitsPerPixel:32,bytesPerRow:width*4,space:CGColorSpaceCreateDeviceRGB(),bitmapInfo:CGBitmapInfo(rawValue:CGImageAlphaInfo.premultipliedLast.rawValue),provider:provider,decode:nil,shouldInterpolate:true,intent:.defaultIntent)!
    }()
    func grain(_ x:Double,_ y:Double,_ w:Double,_ h:Double,_ alpha:Double) {
        save();c.opacity *= alpha;c.draw(Image(decorative:Self.grainImage,scale:1),in:CGRect(x:x,y:y,width:w,height:h));restore()
    }

    func path(_ key: String,_ color: Int,_ width: Double = 0,_ alpha: Double = 1) { guard let path=Self.paths[key] else { return }; if width > 0 { c.stroke(path,with:.color(Self.color(color,alpha)),style:StrokeStyle(lineWidth:width,lineCap:.round,lineJoin:.round)) } else { c.fill(path,with:.color(Self.color(color,alpha))) } }
    func clipPath(_ key: String) { if let p=Self.paths[key] { c.clip(to:p) } }
    static let paths: [String:Path] = ["M -12 11 Q 0 25 13 11 Q 1 19 -12 11 Z": Path { p in p.move(to:CGPoint(x:-12,y:11));p.addQuadCurve(to:CGPoint(x:13,y:11),control:CGPoint(x:0,y:25));p.addQuadCurve(to:CGPoint(x:-12,y:11),control:CGPoint(x:1,y:19));p.closeSubpath() },
        "M -12 12 Q 0 25 13 11 Q 1 19 -12 12 Z": Path { p in p.move(to:CGPoint(x:-12,y:12));p.addQuadCurve(to:CGPoint(x:13,y:11),control:CGPoint(x:0,y:25));p.addQuadCurve(to:CGPoint(x:-12,y:12),control:CGPoint(x:1,y:19));p.closeSubpath() },
        "M -15 4 L -31 31 L -25 47 L -19 34 L -5 10 Z": Path { p in p.move(to:CGPoint(x:-15,y:4));p.addLine(to:CGPoint(x:-31,y:31));p.addLine(to:CGPoint(x:-25,y:47));p.addLine(to:CGPoint(x:-19,y:34));p.addLine(to:CGPoint(x:-5,y:10));p.closeSubpath() },
        "M -15 5 L -27 31": Path { p in p.move(to:CGPoint(x:-15,y:5));p.addLine(to:CGPoint(x:-27,y:31)) },
        "M -15 5 L -27 31 L -23 38 L -18 31 L -7 10 Z": Path { p in p.move(to:CGPoint(x:-15,y:5));p.addLine(to:CGPoint(x:-27,y:31));p.addLine(to:CGPoint(x:-23,y:38));p.addLine(to:CGPoint(x:-18,y:31));p.addLine(to:CGPoint(x:-7,y:10));p.closeSubpath() },
        "M -15 5 L -27 31 L -24 32 L -11 7 Z": Path { p in p.move(to:CGPoint(x:-15,y:5));p.addLine(to:CGPoint(x:-27,y:31));p.addLine(to:CGPoint(x:-24,y:32));p.addLine(to:CGPoint(x:-11,y:7));p.closeSubpath() },
        "M -5 5 L -7 32 L -1 41 L 4 30 L 6 5 Z": Path { p in p.move(to:CGPoint(x:-5,y:5));p.addLine(to:CGPoint(x:-7,y:32));p.addLine(to:CGPoint(x:-1,y:41));p.addLine(to:CGPoint(x:4,y:30));p.addLine(to:CGPoint(x:6,y:5));p.closeSubpath() },
        "M -5 5 L -7 32 L -3 34 L 0 5 Z": Path { p in p.move(to:CGPoint(x:-5,y:5));p.addLine(to:CGPoint(x:-7,y:32));p.addLine(to:CGPoint(x:-3,y:34));p.addLine(to:CGPoint(x:0,y:5));p.closeSubpath() },
        "M 124 654 L 136 664 L 136 730 L 124 743 Z": Path { p in p.move(to:CGPoint(x:124,y:654));p.addLine(to:CGPoint(x:136,y:664));p.addLine(to:CGPoint(x:136,y:730));p.addLine(to:CGPoint(x:124,y:743));p.closeSubpath() },
        "M 136 730 L 256 730 L 270 743 L 124 743 Z": Path { p in p.move(to:CGPoint(x:136,y:730));p.addLine(to:CGPoint(x:256,y:730));p.addLine(to:CGPoint(x:270,y:743));p.addLine(to:CGPoint(x:124,y:743));p.closeSubpath() },
        "M 270 654 L 256 664 L 256 730 L 270 743 Z": Path { p in p.move(to:CGPoint(x:270,y:654));p.addLine(to:CGPoint(x:256,y:664));p.addLine(to:CGPoint(x:256,y:730));p.addLine(to:CGPoint(x:270,y:743));p.closeSubpath() },
        "M 296 304 L 307 307 L 307 496 L 301 516 Z": Path { p in p.move(to:CGPoint(x:296,y:304));p.addLine(to:CGPoint(x:307,y:307));p.addLine(to:CGPoint(x:307,y:496));p.addLine(to:CGPoint(x:301,y:516));p.closeSubpath() },
        "M 338 262 L 326 277 L 321 610 L 333 606 Z": Path { p in p.move(to:CGPoint(x:338,y:262));p.addLine(to:CGPoint(x:326,y:277));p.addLine(to:CGPoint(x:321,y:610));p.addLine(to:CGPoint(x:333,y:606));p.closeSubpath() },
        "M 55 262 L 67 277 L 72 610 L 61 606 Z": Path { p in p.move(to:CGPoint(x:55,y:262));p.addLine(to:CGPoint(x:67,y:277));p.addLine(to:CGPoint(x:72,y:610));p.addLine(to:CGPoint(x:61,y:606));p.closeSubpath() },
        "M 58 558 L 333 558 L 351 585 L 42 585 Z": Path { p in p.move(to:CGPoint(x:58,y:558));p.addLine(to:CGPoint(x:333,y:558));p.addLine(to:CGPoint(x:351,y:585));p.addLine(to:CGPoint(x:42,y:585));p.closeSubpath() },
        "M 63 562 L 329 562 L 341 582 L 52 582 Z": Path { p in p.move(to:CGPoint(x:63,y:562));p.addLine(to:CGPoint(x:329,y:562));p.addLine(to:CGPoint(x:341,y:582));p.addLine(to:CGPoint(x:52,y:582));p.closeSubpath() },
        "M 75 304 L 317 304 L 303 316 L 81 311 Z": Path { p in p.move(to:CGPoint(x:75,y:304));p.addLine(to:CGPoint(x:317,y:304));p.addLine(to:CGPoint(x:303,y:316));p.addLine(to:CGPoint(x:81,y:311));p.closeSubpath() },
        "M 89 299 L 106 299 L 86 432 L 86 365 Z": Path { p in p.move(to:CGPoint(x:89,y:299));p.addLine(to:CGPoint(x:106,y:299));p.addLine(to:CGPoint(x:86,y:432));p.addLine(to:CGPoint(x:86,y:365));p.closeSubpath() }]
}

private func drawScene(_ p:ChromeSmileCranePainter,_ t:Double,_ tx:Double,_ ty:Double,_ touching:Bool) {
    let descend = seg(t,2,3)*(1-seg(t,3.4,4.5))
    let grip = seg(t,3,3.4)*(1-seg(t,5.4,5.8))
    var clawX = 260-132*seg(t,1,2)+132*seg(t,4.5,5.4)
    if touching { clawX = min(287,max(105,tx)) }
    let clawY = 337+149*descend
    var ballX = 128.0
    var ballY = 510.0
    var ballAlpha = 1.0
    if t >= 3 && t < 5.4 { ballX=128+(clawX-128)*seg(t,3,3.15);ballY=510+(clawY+32-510)*seg(t,3,3.15) }
    if t >= 5.4 && t < 6.2 {
        let fall = min(1,max(0,(t-5.4)/0.8))
        ballX=260-63*fall
        ballY=369+335*fall*fall
    }
    if t >= 6.2 && t < 7 { ballX=197;ballY=704;ballAlpha=1-seg(t,6.3,6.8) }
    if t >= 7 { ballAlpha=seg(t,7,7.4) }
    p.grad(0,0,393,852,0x04BADD,0x1888AD,0x126B8B)
    p.radial(192,270,410,0x1BD7E3,0x137D9E,0.42)
    // The narrow upper case tapers into the wide, forward-projecting deck.
    p.grad(53,222,287,402,0x47515C,0xCCD0C7,0x707D84,22,true)
    p.path("M 55 262 L 67 277 L 72 610 L 61 606 Z",0x293B48)
    p.path("M 338 262 L 326 277 L 321 610 L 333 606 Z",0x273746)
    p.grad(66,297,261,314,0xA1A4A0,0xC6C8C1,0xA0AAA8,3,true)
    p.save()
    p.translate(196.5,68)
    p.scale(0.92,0.85)
    p.translate(-196.5,0)
    p.grad(47,187,299,91,0x778B95,0x354452,0x152C43,20)
    p.r(56,196,281,70,0xC6CFCE,13)
    p.r(59,199,275,63,0x13233E,11)
    p.grad(63,203,267,53,0x07122D,0x133C88,0x0A1739,8)
    p.radial(197,229,148,0x2D73DC,0x172F68,0.6)
    let letters = ["S","M","I","L","E"]
    for idx in 0..<5 {
        let i = Double(idx)
        p.save()
        p.blur(3)
        p.text(letters[idx],112+42*i,207,32,0x6BAFFF,"heavy",1)
        p.restore()
        p.blur(0)
        p.text(letters[idx],112+42*i,207,32,0xE8FFF8,"heavy",1)
    }
    p.restore()
    // Reflected smiles live behind a shallow upper glass band, as in the source.
    p.grad(69,298,255,49,0x9FA5A5,0xD3D5CE,0x969F9E,3)
    p.save()
    p.clip(74,304,245,34,4)
    p.grad(74,304,245,34,0x737C81,0xADB3AE,0x696E76,4)
    p.blur(2)
    for idx in 0..<3 {
        let i=Double(idx)
        p.e(128+65*i,326,25,12,0xE7C64B,0.32)
        p.e(120+65*i,324,2,3,0x7E6248,0.27)
        p.e(134+65*i,324,2,3,0x7E6248,0.27)
    }
    p.restore()
    p.blur(0)
    p.path("M 75 304 L 317 304 L 303 316 L 81 311 Z",0xF5F2DF,0,0.14)
    p.line(70,347,322,347,0xF8F5E4,1.3,0.7)
    p.save()
    p.translate(196.5,119)
    p.scale(0.92,0.86)
    p.translate(-196.5,0)
    p.r(73,287,247,256,0x546B7B,35)
    p.r(76,289,241,251,0xECF0DF,32)
    p.r(79,292,235,245,0x7E96AE,30)
    p.r(82,295,229,239,0x0B2697,28)
    p.save()
    p.clip(83,296,227,237,27)
    p.grad(83,296,227,237,0x1247D2,0x1628A3,0x121F87)
    p.radial(194,318,158,0x2B8BF4,0x182B9B,0.85)
    p.grad(83,513,227,20,0x2C3CB7,0x3151CC,0x6383E3)
    let xs = [128,244,203,285,166,221,262]
    let ys = [510,457,474,490,495,514,525]
    let radii = [31,34,33,32,31,34,33]
    for idx in 0..<7 {
        let index = pick(idx == 6,0,idx+1)
        let i = Double(index)
        let radius = Double(radii[index])
        var bx = Double(xs[index])
        var by = Double(ys[index])
        var alpha = 1.0
        if index == 0 { bx=ballX;by=ballY;alpha=ballAlpha }
        if index > 0 { by=by+0.6*sin(6.283185307179586*t/8+i)*seg(t,0.8,1.1)*(1-seg(t,7,7.4)) }
        p.e(bx+3,by+6,radius+1,radius,0x070F4B,0.22*alpha)
        p.save()
        p.clip(bx-radius,by-radius,radius*2,radius*2,radius)
        p.grad(bx-radius,by-radius,radius*2,radius*2,0xFFE96D,0xF8C82A,0xAD7414,0,true,alpha)
        p.radial(bx-radius*0.32,by-radius*0.48,radius*1.35,0xFFFFAE,0xFFD332,0.84*alpha)
        p.e(bx-radius*0.4,by-radius*0.64,radius*0.16,radius*0.07,0xFFFBD2,0.5*alpha)
        if index == 0 {
            p.r(bx-24,by-8,21,14,0x27303B,5,alpha)
            p.r(bx+3,by-8,21,14,0x27303B,5,alpha)
            p.line(bx-24,by-9,bx+24,by-9,0x1A2230,2,alpha)
            p.line(bx-3,by-4,bx+3,by-4,0x1A2230,2,alpha)
            p.line(bx-21,by-5,bx-13,by-7,0x8496A4,1.5,alpha*0.65)
            p.line(bx+6,by-5,bx+14,by-7,0x8496A4,1.5,alpha*0.65)
            p.save()
            p.translate(bx,by)
            p.path("M -12 12 Q 0 25 13 11 Q 1 19 -12 12 Z",0x673E1E,0,alpha)
            p.restore()
        }
        if index > 0 && index < 3 {
            p.line(bx-15,by-5,bx-8,by-7,0x86661C,2,alpha)
            p.line(bx+8,by-7,bx+15,by-5,0x86661C,2,alpha)
            p.e(bx-18,by+5,6,3,0xF8A160,0.6*alpha)
            p.e(bx+18,by+5,6,3,0xF8A160,0.6*alpha)
            p.e(bx,by+8,8,6,0xA46B20,alpha)
            p.e(bx,by+5,8,4,0xFFDD53,alpha)
        }
        if index >= 3 && index < 6 {
            p.e(bx-11,by-5,3,5,0x5C481F,alpha)
            p.e(bx+11,by-5,3,5,0x5C481F,alpha)
            p.e(bx,by+10,14,9,0x8E4E22,alpha)
            p.e(bx,by+6,12,4,0xFFFADE,alpha)
            p.e(bx,by+15,6,3,0xF19263,alpha)
        }
        if index >= 6 {
            p.e(bx-11,by-5,3,4,0x604624,alpha)
            p.line(bx+7,by-5,bx+15,by-5,0x604624,2,alpha)
            p.e(bx,by+10,9,5,0x9E6227,alpha)
        }
        p.restore()
    }
    // A twisted cable with bright and dark facets; the fingers close around the ball.
    p.line(clawX,301,clawX,clawY-9,0x1D3766,5,0.55)
    for idx in 0..<15 {
        let i = Double(idx)
        let cy = 304+(clawY-317)*i/14
        p.line(clawX-3,cy,clawX+3,cy+4,0xD4DFD4,1.6)
        p.line(clawX+3,cy+4,clawX-3,cy+8,0x6E8DAD,1.2)
    }
    p.save()
    p.translate(clawX,clawY)
    p.grad(-20,-10,40,17,0x526D81,0xF3F4DD,0x738B9C,2,true)
    p.line(-20,-10,20,-10,0xF8F9E8,1.2)
    for idx in 0..<2 {
        p.save()
        p.scale(pick(idx == 0,1.0,-1.0),1)
        p.rotate(22*(1-grip))
        p.path("M -15 4 L -31 31 L -25 47 L -19 34 L -5 10 Z",0x526D81)
        p.path("M -15 5 L -27 31 L -23 38 L -18 31 L -7 10 Z",0xD2D9D0)
        p.path("M -15 5 L -27 31 L -24 32 L -11 7 Z",0xF5F5DC)
        p.path("M -15 5 L -27 31",0xFCFAE7,0.65,0.75)
        p.restore()
    }
    p.path("M -5 5 L -7 32 L -1 41 L 4 30 L 6 5 Z",0xC7D1D0)
    p.path("M -5 5 L -7 32 L -3 34 L 0 5 Z",0xF6F4DB)
    p.restore()
    p.path("M 89 299 L 106 299 L 86 432 L 86 365 Z",0xD3F7FF,0,0.12)
    p.path("M 296 304 L 307 307 L 307 496 L 301 516 Z",0xA7D2FF,0,0.08)
    p.restore()
    p.line(87,532,307,532,0xDFE7DA,1.1,0.8)
    p.restore()
    // The base continues into the lower crop; the prize window remains fully visible.
    p.save()
    p.translate(196.5,-33.755)
    p.scale(1.08,1.145)
    p.translate(-196.5,0)
    p.grad(49,619,295,155,0x86979F,0xC6CFCA,0x9BA8A9,2,true)
    p.line(56,620,56,771,0xD7E1D8,1,0.5)
    p.grad(62,625,24,140,0xB6C5C4,0xDAE4DA,0xADBEBF,1,true,0.28)
    p.grad(315,625,14,140,0x667C8B,0x91A4AB,0xA3B3B6,1,true,0.28)
    p.line(337,620,337,771,0x344E61,1,0.6)
    p.r(115,646,164,105,0x597B8F,8)
    p.r(119,649,156,99,0xC8D9D6,6)
    p.r(123,653,148,91,0x0C286B,4)
    p.save()
    p.clip(124,654,146,89,3)
    p.grad(124,654,146,89,0x1238B5,0x244DCF,0x5F84D4)
    p.radial(202,686,81,0x6B9DF2,0x122C8B,0.5)
    p.path("M 124 654 L 136 664 L 136 730 L 124 743 Z",0x072777)
    p.path("M 270 654 L 256 664 L 256 730 L 270 743 Z",0x204AB6)
    p.path("M 136 730 L 256 730 L 270 743 L 124 743 Z",0x91AED9)
    if ballY > 630 {
        p.e(ballX+3,ballY+25,28,5,0x071B5F,0.35*ballAlpha)
        p.e(ballX,ballY,29,29,0xE8B81B,ballAlpha)
        p.save()
        p.clip(ballX-29,ballY-29,58,58,29)
        p.radial(ballX-9,ballY-14,44,0xFFF58C,0xE9BE17,0.9*ballAlpha)
        p.r(ballX-23,ballY-7,20,13,0x29303C,5,ballAlpha)
        p.r(ballX+3,ballY-7,20,13,0x29303C,5,ballAlpha)
        p.line(ballX-23,ballY-8,ballX+23,ballY-8,0x202939,1.8,ballAlpha)
        p.line(ballX-3,ballY-3,ballX+3,ballY-3,0x202939,2,ballAlpha)
        p.save()
        p.translate(ballX,ballY)
        p.path("M -12 11 Q 0 25 13 11 Q 1 19 -12 11 Z",0x693F22,0,ballAlpha)
        p.restore()
        p.restore()
    }
    p.restore()
    p.line(122,747,273,747,0xEBF0DB,1.5,0.8)
    p.restore()
    p.save()
    p.translate(196.5,123)
    p.scale(1.045,0.9)
    p.translate(-196.5,0)
    p.path("M 58 558 L 333 558 L 351 585 L 42 585 Z",0xD8DFD9)
    p.path("M 63 562 L 329 562 L 341 582 L 52 582 Z",0x1B3E99)
    p.grad(43,584,307,35,0xAEBFC3,0x7F929C,0xD6E2DF,3,true)
    p.line(46,586,346,586,0xF5F8ED,1.1)
    for idx in 0..<24 {
        let i = Double(idx)
        p.line(59,625+6*i,332,625+6*i,0xFFFFFF,0.45,0.035)
    }
    for idx in 0..<2 {
        let i = Double(idx)
        let press = seg(t,2.8,3)*(1-seg(t,3.2,3.45))
        p.e(91+45*i,574,20,6.5,0x17315A,0.7)
        p.e(91+45*i,571+press,18,6,0x465765)
        p.e(91+45*i,569+press*2,17,5,0xD9DDD4)
        p.e(89+45*i,568+press*2,12,2.3,0xFAFBEC,0.9)
    }
    let lever = (clawX-196.5)/75
    p.e(281,575,25,8,0x0F2A52,0.45)
    p.e(281,573,22,8,0x6C899E)
    p.e(281,571,18,6,0xD7E0D2)
    p.line(281,568,281+lever*8,548,0x3A4E5B,10)
    p.line(279,567,279+lever*8,548,0xD3E1D6,3)
    p.e(281+lever*8,542,10,11,0x202C33)
    p.radial(278+lever*8,537,12,0x7C8984,0x1C2D36,0.5)
    p.restore()
    p.grain(0,0,393,852,0.035)

}
