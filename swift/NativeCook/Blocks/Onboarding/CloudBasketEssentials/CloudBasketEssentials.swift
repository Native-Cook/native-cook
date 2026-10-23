
import SwiftUI
import UIKit

/// All geometry and motion live in this file. `previewTime` selects an exact loop phase.
struct CloudBasketEssentials: View {
    static let loop = 10.0
    var autoPlay = false
    var previewTime: Double? = nil
    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var start = Date()
    @State private var lastTouch: Date? = nil
    @State private var phase = 0.0
    @State private var gestureStart: Double? = nil
    @State private var point = CGPoint(x:196.5,y:426.0)
    @State private var velocity = 0.0

    private func time(_ date: Date) -> Double {
        if let previewTime { return max(0,previewTime).truncatingRemainder(dividingBy:Self.loop) }
        if reducedMotion { return 7.9 }
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
                    let p=CloudBasketEssentialsPainter(context)
                    p.r(0,0,size.width,size.height,0x89ACB0)
                    let scale=min(size.width/393,size.height/852)
                    p.translate((size.width-393*scale)/2,(size.height-852*scale)/2)
                    p.scale(scale,scale)
                    let age=lastTouch.map { max(0,timeline.date.timeIntervalSince($0)) } ?? 100
                    let strength = reducedMotion || previewTime != nil ? 0 : (gestureStart != nil ? 1 : 1-smooth((age-2)/0.4))
                    drawScene(p,time(timeline.date),196.5+(point.x-196.5)*strength,426.0+(point.y-426.0)*strength,strength>0)
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
                    if abs(value.translation.width)+abs(value.translation.height)<5 { phase=7.9 }
                    velocity=max(-5,min(5,value.velocity.width/90))
                    lastTouch = .now;gestureStart=nil
                    UIImpactFeedbackGenerator(style:.light).impactOccurred()
                })
                .accessibilityElement(children:.ignore)
                .accessibilityLabel(previewTime.map { "Cloud Basket Essentials preview "+String(Int(($0*1000).rounded())) } ?? "Cloud Basket Essentials")
                .accessibilityHint("Drag the basket to catch your essentials. Tap to pack it.")
                .accessibilityValue("Interactive motion preview")
                .accessibilityAdjustableAction { direction in
                    phase=min(Self.loop-0.001,max(0,phase+(direction == .increment ? 1:-1)))
                    lastTouch = .now
                }
            }
        }
        .ignoresSafeArea()
        .background(CloudBasketEssentialsPainter.color(0x89ACB0))
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
private final class CloudBasketEssentialsPainter {
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
    static let paths: [String:Path] = ["M -107 -1 Q 0 12 107 -1": Path { p in p.move(to:CGPoint(x:-107,y:-1));p.addQuadCurve(to:CGPoint(x:107,y:-1),control:CGPoint(x:0,y:12)) },
        "M -107 0 L -84 103 L 84 103 L 107 0": Path { p in p.move(to:CGPoint(x:-107,y:0));p.addLine(to:CGPoint(x:-84,y:103));p.addLine(to:CGPoint(x:84,y:103));p.addLine(to:CGPoint(x:107,y:0)) },
        "M -107 0 L 107 0 L 84 103 L -84 103 Z": Path { p in p.move(to:CGPoint(x:-107,y:0));p.addLine(to:CGPoint(x:107,y:0));p.addLine(to:CGPoint(x:84,y:103));p.addLine(to:CGPoint(x:-84,y:103));p.closeSubpath() },
        "M -107 0 Q 0 -16 107 0": Path { p in p.move(to:CGPoint(x:-107,y:0));p.addQuadCurve(to:CGPoint(x:107,y:0),control:CGPoint(x:0,y:-16)) },
        "M -107 0 Q 0 13 107 0": Path { p in p.move(to:CGPoint(x:-107,y:0));p.addQuadCurve(to:CGPoint(x:107,y:0),control:CGPoint(x:0,y:13)) },
        "M -19 -14 C -25 -21 -30 -10 -18 -4 C -7 -10 -13 -21 -19 -14 Z": Path { p in p.move(to:CGPoint(x:-19,y:-14));p.addCurve(to:CGPoint(x:-18,y:-4),control1:CGPoint(x:-25,y:-21),control2:CGPoint(x:-30,y:-10));p.addCurve(to:CGPoint(x:-19,y:-14),control1:CGPoint(x:-7,y:-10),control2:CGPoint(x:-13,y:-21));p.closeSubpath() },
        "M -21 -10 C -30 -29 -17 -37 -2 -29 C 15 -38 29 -26 19 -10 L 18 26 L -19 26 Z": Path { p in p.move(to:CGPoint(x:-21,y:-10));p.addCurve(to:CGPoint(x:-2,y:-29),control1:CGPoint(x:-30,y:-29),control2:CGPoint(x:-17,y:-37));p.addCurve(to:CGPoint(x:19,y:-10),control1:CGPoint(x:15,y:-38),control2:CGPoint(x:29,y:-26));p.addLine(to:CGPoint(x:18,y:26));p.addLine(to:CGPoint(x:-19,y:26));p.closeSubpath() },
        "M -23 -42 L -5 -42 L -5 5 Q 9 7 9 18 Q 8 30 -6 31 L -30 27 Q -36 21 -29 12 L -23 5 Z": Path { p in p.move(to:CGPoint(x:-23,y:-42));p.addLine(to:CGPoint(x:-5,y:-42));p.addLine(to:CGPoint(x:-5,y:5));p.addQuadCurve(to:CGPoint(x:9,y:18),control:CGPoint(x:9,y:7));p.addQuadCurve(to:CGPoint(x:-6,y:31),control:CGPoint(x:8,y:30));p.addLine(to:CGPoint(x:-30,y:27));p.addQuadCurve(to:CGPoint(x:-29,y:12),control:CGPoint(x:-36,y:21));p.addLine(to:CGPoint(x:-23,y:5));p.closeSubpath() },
        "M -25 -11 C -38 -33 -19 -46 -2 -36 C 18 -46 36 -27 24 -11 L 23 32 L -24 32 Z": Path { p in p.move(to:CGPoint(x:-25,y:-11));p.addCurve(to:CGPoint(x:-2,y:-36),control1:CGPoint(x:-38,y:-33),control2:CGPoint(x:-19,y:-46));p.addCurve(to:CGPoint(x:24,y:-11),control1:CGPoint(x:18,y:-46),control2:CGPoint(x:36,y:-27));p.addLine(to:CGPoint(x:23,y:32));p.addLine(to:CGPoint(x:-24,y:32));p.closeSubpath() },
        "M -27 15 L -27 -14 C -27 -53 28 -53 28 -14 L 28 15": Path { p in p.move(to:CGPoint(x:-27,y:15));p.addLine(to:CGPoint(x:-27,y:-14));p.addCurve(to:CGPoint(x:28,y:-14),control1:CGPoint(x:-27,y:-53),control2:CGPoint(x:28,y:-53));p.addLine(to:CGPoint(x:28,y:15)) },
        "M -28 -13 C -29 -48 27 -48 28 -13": Path { p in p.move(to:CGPoint(x:-28,y:-13));p.addCurve(to:CGPoint(x:28,y:-13),control1:CGPoint(x:-29,y:-48),control2:CGPoint(x:27,y:-48)) },
        "M -5 -38 L 14 -38 L 14 9 Q 31 11 31 22 Q 29 34 15 34 L -9 29 Q -15 22 -8 13 L -5 9 Z": Path { p in p.move(to:CGPoint(x:-5,y:-38));p.addLine(to:CGPoint(x:14,y:-38));p.addLine(to:CGPoint(x:14,y:9));p.addQuadCurve(to:CGPoint(x:31,y:22),control:CGPoint(x:31,y:11));p.addQuadCurve(to:CGPoint(x:15,y:34),control:CGPoint(x:29,y:34));p.addLine(to:CGPoint(x:-9,y:29));p.addQuadCurve(to:CGPoint(x:-8,y:13),control:CGPoint(x:-15,y:22));p.addLine(to:CGPoint(x:-5,y:9));p.closeSubpath() },
        "M 38 -45 L 46 -36 L 46 10 L 38 15 Z": Path { p in p.move(to:CGPoint(x:38,y:-45));p.addLine(to:CGPoint(x:46,y:-36));p.addLine(to:CGPoint(x:46,y:10));p.addLine(to:CGPoint(x:38,y:15));p.closeSubpath() },
        "M 4 -16 L 6 -10 L 13 -10 L 8 -6 L 10 1 L 4 -3 L -2 1 L 0 -6 L -5 -10 L 2 -10 Z": Path { p in p.move(to:CGPoint(x:4,y:-16));p.addLine(to:CGPoint(x:6,y:-10));p.addLine(to:CGPoint(x:13,y:-10));p.addLine(to:CGPoint(x:8,y:-6));p.addLine(to:CGPoint(x:10,y:1));p.addLine(to:CGPoint(x:4,y:-3));p.addLine(to:CGPoint(x:-2,y:1));p.addLine(to:CGPoint(x:0,y:-6));p.addLine(to:CGPoint(x:-5,y:-10));p.addLine(to:CGPoint(x:2,y:-10));p.closeSubpath() },
        "M 65.028 217.280 L 64.084 222.000 L 47.092 222.000 L 63.140 146.952 L 80.604 144.592 L 66.444 210.790 Q 69.748 213.150 73.170 213.150 Q 79.896 213.150 85.029 205.362 Q 90.162 197.574 92.994 185.538 Q 95.826 173.502 95.826 161.230 Q 95.826 149.666 91.283 144.297 Q 86.740 138.928 78.598 138.928 Q 69.748 138.928 62.727 142.409 Q 55.706 145.890 51.694 152.380 Q 47.682 158.870 47.682 167.602 Q 47.682 171.968 48.508 173.679 Q 49.334 175.390 49.334 175.626 Q 43.198 175.626 39.953 173.030 Q 36.708 170.434 36.708 164.652 Q 36.708 156.628 42.726 149.430 Q 48.744 142.232 58.833 137.866 Q 68.922 133.500 80.604 133.500 Q 96.770 133.500 104.440 143.058 Q 112.110 152.616 112.110 167.484 Q 112.110 180.936 107.567 193.975 Q 103.024 207.014 95.472 215.274 Q 87.920 223.534 79.424 223.534 Q 72.344 223.534 65.028 217.280 Z": Path { p in p.move(to:CGPoint(x:65.028,y:217.280));p.addLine(to:CGPoint(x:64.084,y:222.000));p.addLine(to:CGPoint(x:47.092,y:222.000));p.addLine(to:CGPoint(x:63.140,y:146.952));p.addLine(to:CGPoint(x:80.604,y:144.592));p.addLine(to:CGPoint(x:66.444,y:210.790));p.addQuadCurve(to:CGPoint(x:73.170,y:213.150),control:CGPoint(x:69.748,y:213.150));p.addQuadCurve(to:CGPoint(x:85.029,y:205.362),control:CGPoint(x:79.896,y:213.150));p.addQuadCurve(to:CGPoint(x:92.994,y:185.538),control:CGPoint(x:90.162,y:197.574));p.addQuadCurve(to:CGPoint(x:95.826,y:161.230),control:CGPoint(x:95.826,y:173.502));p.addQuadCurve(to:CGPoint(x:91.283,y:144.297),control:CGPoint(x:95.826,y:149.666));p.addQuadCurve(to:CGPoint(x:78.598,y:138.928),control:CGPoint(x:86.740,y:138.928));p.addQuadCurve(to:CGPoint(x:62.727,y:142.409),control:CGPoint(x:69.748,y:138.928));p.addQuadCurve(to:CGPoint(x:51.694,y:152.380),control:CGPoint(x:55.706,y:145.890));p.addQuadCurve(to:CGPoint(x:47.682,y:167.602),control:CGPoint(x:47.682,y:158.870));p.addQuadCurve(to:CGPoint(x:48.508,y:173.679),control:CGPoint(x:47.682,y:171.968));p.addQuadCurve(to:CGPoint(x:49.334,y:175.626),control:CGPoint(x:49.334,y:175.390));p.addQuadCurve(to:CGPoint(x:39.953,y:173.030),control:CGPoint(x:43.198,y:175.626));p.addQuadCurve(to:CGPoint(x:36.708,y:164.652),control:CGPoint(x:36.708,y:170.434));p.addQuadCurve(to:CGPoint(x:42.726,y:149.430),control:CGPoint(x:36.708,y:156.628));p.addQuadCurve(to:CGPoint(x:58.833,y:137.866),control:CGPoint(x:48.744,y:142.232));p.addQuadCurve(to:CGPoint(x:80.604,y:133.500),control:CGPoint(x:68.922,y:133.500));p.addQuadCurve(to:CGPoint(x:104.440,y:143.058),control:CGPoint(x:96.770,y:133.500));p.addQuadCurve(to:CGPoint(x:112.110,y:167.484),control:CGPoint(x:112.110,y:152.616));p.addQuadCurve(to:CGPoint(x:107.567,y:193.975),control:CGPoint(x:112.110,y:180.936));p.addQuadCurve(to:CGPoint(x:95.472,y:215.274),control:CGPoint(x:103.024,y:207.014));p.addQuadCurve(to:CGPoint(x:79.424,y:223.534),control:CGPoint(x:87.920,y:223.534));p.addQuadCurve(to:CGPoint(x:65.028,y:217.280),control:CGPoint(x:72.344,y:223.534));p.closeSubpath() }]
}

private func drawScene(_ p:CloudBasketEssentialsPainter,_ t:Double,_ tx:Double,_ ty:Double,_ touching:Bool) {
    p.grad(0,0,393,852,0x86ADB0,0x8AB2B4,0xA8C2C2)
    // Scalloped cloud silhouettes sit over a softer diagonal body.
    for idx in 0..<7 {
        let i=Double(idx)
        let x = -65+i*79
        let y=435+i*39
        p.save()
        p.blur(13)
        p.e(x+4,y+54,110,82,0xD9E2DC,0.64)
        p.e(x+27,y+71,107,85,0xEEF0E6,0.74)
        p.restore()
        p.blur(0)
    }
    for idx in 0..<18 {
        let i=Double(idx)
        let x = -25+i*28
        let y=420+i*15+13*sin(i*2.1)
        let radius=24+7*sin(i*1.7+1)
        p.save()
        p.blur(5)
        p.e(x+7,y+14,radius*1.14,radius*1.1,0xBCCFCB,0.40)
        p.e(x,y,radius,radius,0xEDF0E5,0.83)
        p.e(x-5,y-5,radius*0.76,radius*0.70,0xF8F7EB,0.34)
        p.restore()
        p.blur(0)
    }
    for idx in 0..<5 {
        let i=Double(idx)
        p.save()
        p.blur(25)
        p.e(-53+124*i,747+21*sin(i*2.7),130,114,0xE5EBE3,0.42)
        p.restore()
        p.blur(0)
    }
    let pixelX = [48.0, 52.2, 56.4, 60.6, 64.8, 48.0, 48.0, 48.0, 52.2, 56.4, 60.6, 48.0, 48.0, 48.0, 52.2, 56.4, 60.6, 64.8, 78.3, 82.5, 86.7, 90.9, 95.1, 78.3, 78.3, 78.3, 82.5, 86.7, 90.9, 95.1, 95.1, 95.1, 78.3, 82.5, 86.7, 90.9, 95.1, 108.6, 112.8, 117.0, 121.2, 125.4, 108.6, 108.6, 108.6, 112.8, 117.0, 121.2, 125.4, 125.4, 125.4, 108.6, 112.8, 117.0, 121.2, 125.4, 138.9, 143.1, 147.3, 151.5, 155.7, 138.9, 138.9, 138.9, 143.1, 147.3, 151.5, 138.9, 138.9, 138.9, 143.1, 147.3, 151.5, 155.7, 169.2, 186.0, 169.2, 173.4, 186.0, 169.2, 173.4, 186.0, 169.2, 177.6, 186.0, 169.2, 181.8, 186.0, 169.2, 181.8, 186.0, 169.2, 186.0, 199.5, 203.7, 207.9, 212.1, 216.3, 207.9, 207.9, 207.9, 207.9, 207.9, 207.9, 229.8, 234.0, 238.2, 242.4, 246.6, 238.2, 238.2, 238.2, 238.2, 238.2, 229.8, 234.0, 238.2, 242.4, 246.6, 264.3, 268.5, 272.7, 260.1, 276.9, 260.1, 276.9, 260.1, 264.3, 268.5, 272.7, 276.9, 260.1, 276.9, 260.1, 276.9, 260.1, 276.9, 290.4, 290.4, 290.4, 290.4, 290.4, 290.4, 290.4, 294.6, 298.8, 303.0, 307.2, 320.7, 324.9, 329.1, 333.3, 337.5, 320.7, 320.7, 320.7, 324.9, 329.1, 333.3, 337.5, 337.5, 337.5, 320.7, 324.9, 329.1, 333.3, 337.5]
    let pixelY = [94.0, 94.0, 94.0, 94.0, 94.0, 98.8, 103.6, 108.4, 108.4, 108.4, 108.4, 113.2, 118.0, 122.8, 122.8, 122.8, 122.8, 122.8, 94.0, 94.0, 94.0, 94.0, 94.0, 98.8, 103.6, 108.4, 108.4, 108.4, 108.4, 108.4, 113.2, 118.0, 122.8, 122.8, 122.8, 122.8, 122.8, 94.0, 94.0, 94.0, 94.0, 94.0, 98.8, 103.6, 108.4, 108.4, 108.4, 108.4, 108.4, 113.2, 118.0, 122.8, 122.8, 122.8, 122.8, 122.8, 94.0, 94.0, 94.0, 94.0, 94.0, 98.8, 103.6, 108.4, 108.4, 108.4, 108.4, 113.2, 118.0, 122.8, 122.8, 122.8, 122.8, 122.8, 94.0, 94.0, 98.8, 98.8, 98.8, 103.6, 103.6, 103.6, 108.4, 108.4, 108.4, 113.2, 113.2, 113.2, 118.0, 118.0, 118.0, 122.8, 122.8, 94.0, 94.0, 94.0, 94.0, 94.0, 98.8, 103.6, 108.4, 113.2, 118.0, 122.8, 94.0, 94.0, 94.0, 94.0, 94.0, 98.8, 103.6, 108.4, 113.2, 118.0, 122.8, 122.8, 122.8, 122.8, 122.8, 94.0, 94.0, 94.0, 98.8, 98.8, 103.6, 103.6, 108.4, 108.4, 108.4, 108.4, 108.4, 113.2, 113.2, 118.0, 118.0, 122.8, 122.8, 94.0, 98.8, 103.6, 108.4, 113.2, 118.0, 122.8, 122.8, 122.8, 122.8, 122.8, 94.0, 94.0, 94.0, 94.0, 94.0, 98.8, 103.6, 108.4, 108.4, 108.4, 108.4, 108.4, 113.2, 118.0, 122.8, 122.8, 122.8, 122.8, 122.8]
    for idx in 0..<167 {
        p.r(Double(pixelX[idx]),Double(pixelY[idx]),3.8,4.2,0xF0A0CC)
    }
    // Lobster capital D as one cached outline keeps the decorative initial identical across ports.
    p.path("M 65.028 217.280 L 64.084 222.000 L 47.092 222.000 L 63.140 146.952 L 80.604 144.592 L 66.444 210.790 Q 69.748 213.150 73.170 213.150 Q 79.896 213.150 85.029 205.362 Q 90.162 197.574 92.994 185.538 Q 95.826 173.502 95.826 161.230 Q 95.826 149.666 91.283 144.297 Q 86.740 138.928 78.598 138.928 Q 69.748 138.928 62.727 142.409 Q 55.706 145.890 51.694 152.380 Q 47.682 158.870 47.682 167.602 Q 47.682 171.968 48.508 173.679 Q 49.334 175.390 49.334 175.626 Q 43.198 175.626 39.953 173.030 Q 36.708 170.434 36.708 164.652 Q 36.708 156.628 42.726 149.430 Q 48.744 142.232 58.833 137.866 Q 68.922 133.500 80.604 133.500 Q 96.770 133.500 104.440 143.058 Q 112.110 152.616 112.110 167.484 Q 112.110 180.936 107.567 193.975 Q 103.024 207.014 95.472 215.274 Q 87.920 223.534 79.424 223.534 Q 72.344 223.534 65.028 217.280 Z",0xF0A0CC)
    p.fit("ESIGNERS",236,120,92,237,0xF0A0CC,"condensed")
    p.save()
    p.translate(107,135)
    p.rotate(-8)
    p.e(0,0,19,9.5,0xF4EFD6)
    p.text("for",0,-8,13,0x799C9D,"italic",1)
    p.restore()
    var basketX=196.5+29*sin(6.283185307179586*t/10+0.8)*seg(t,0.65,1)*(1-seg(t,8.4,9.2))
    if touching { basketX=min(257,max(136,tx)) }
    var shake=0.0
    for idx in 0..<6 {
        let age=t-(1.95+Double(idx)*1.15)
        if age >= 0 { shake=shake+2.3*exp(-9*age)*sin(19*age) }
    }
    let basketY=651+shake
    // Back wire and handles sit behind the falling objects.
    p.e(basketX,764,109,9,0x506B71,0.13)
    p.e(basketX,757,87,3,0x4A5964,0.19)
    p.save()
    p.translate(basketX,basketY)
    p.scale(0.86,1)
    p.path("M -107 0 L 107 0 L 84 103 L -84 103 Z",0xD99CB7,2)
    p.line(-96,-7,-9,-1,0xA62F67,4)
    p.line(9,-1,96,-7,0xB92A61,4)
    p.line(-95,-8,-10,-2,0xF17CA7,1.1)
    p.line(10,-2,95,-8,0xF69EB9,1.1)
    p.path("M -107 0 Q 0 -16 107 0",0xF8CBDD,3)
    p.restore()
    let xs=[201,160,239,192,221,258]
    let ys=[694,704,708,717,712,704]
    let angles=[0,-14,3,0,5,5]
    for idx in 0..<6 {
        let i=Double(idx)
        let begin=0.85+i*1.15
        let raw=min(1,max(0,(t-begin)/1.1))
        let landed=max(0,t-begin-1.1)
        let exit=seg(t,8.4+i*0.025,9.1+i*0.025)
        let alpha=seg(t,begin,begin+0.12)*(1-exit)
        let targetY=Double(ys[idx])
        let y=300+(targetY-300)*raw*raw-14*exp(-11*landed)*abs(sin(15*landed))-110*exit
        let x=Double(xs[idx])+(basketX-196.5)*raw*raw+18*sin(raw*3.141592653589793)*(pick(idx % 2 == 0,1.0,-1.0))
        p.save()
        p.translate(x,y)
        p.rotate(Double(angles[idx])+(1-raw)*pick(idx % 2 == 0,-14.0,14.0)-13*exit+5*exp(-10*landed)*sin(17*landed))
        p.scale(0.9+0.1*raw,0.9+0.1*raw)
        p.e(4,34,35,6,0x587073,0.13*alpha)
        if idx == 0 {
            p.grad(-38,-50,76,65,0xF0EAD1,0xD9D2B8,0xB9BAA5,6,false,alpha)
            p.path("M 38 -45 L 46 -36 L 46 10 L 38 15 Z",0xA0AA9B,0,alpha)
            p.r(-32,-44,62,47,0x898E80,5,alpha)
            p.grad(-29,-41,56,41,0x223B46,0x64858A,0x263E47,5,false,alpha)
            p.e(-10,-31,16,5,0x99B5AD,0.23*alpha)
            p.text("hello.",0,-28,14,0xF7F2D7,"italic",1,alpha)
            p.r(-22,15,44,9,0xB9BEAC,2,alpha)
            p.grad(-37,25,81,17,0xE5E4CE,0xD1D4C4,0xADB9AA,2,false,alpha)
            for row in 0..<3 {
                for col in 0..<11 {
        p.r(-31+Double(col)*6.5,28+Double(row)*3,5,2,0x798F87,0.4,0.65*alpha)
    }
            }
            p.e(29,8,1.5,1.5,0x79A65E,alpha)
        }
        if idx == 1 {
            p.r(-28,-42,57,80,0xA8B0AF,2,alpha)
            p.grad(-31,-45,57,79,0x234FD6,0x1C41BA,0x142C91,3,true,alpha)
            p.line(-26,-41,-26,29,0x90A0EA,1,0.75*alpha)
            p.text("Thoughts",-23,-30,11,0xF4EAD5,"italic",0,alpha)
            p.text("& tiny",-21,-11,12,0xF4EAD5,"italic",0,alpha)
            p.text("plans",-20,5,14,0xF4EAD5,"italic",0,alpha)
            p.e(6,24,5,5,0xF6DC55,alpha)
        }
        if idx == 2 {
            p.path("M -23 -42 L -5 -42 L -5 5 Q 9 7 9 18 Q 8 30 -6 31 L -30 27 Q -36 21 -29 12 L -23 5 Z",0xECEBD9,0,alpha)
            p.path("M -5 -38 L 14 -38 L 14 9 Q 31 11 31 22 Q 29 34 15 34 L -9 29 Q -15 22 -8 13 L -5 9 Z",0x28A65E,0,alpha)
            p.line(-23,-31,-7,-31,0xD8D7C8,3,alpha)
            p.line(-5,-27,12,-27,0xEAEADB,4,alpha)
            p.path("M -19 -14 C -25 -21 -30 -10 -18 -4 C -7 -10 -13 -21 -19 -14 Z",0xD94E4C,0,alpha)
            p.path("M 4 -16 L 6 -10 L 13 -10 L 8 -6 L 10 1 L 4 -3 L -2 1 L 0 -6 L -5 -10 L 2 -10 Z",0xE2D94D,0,alpha)
        }
        if idx == 3 {
            p.e(19,0,15,19,0x2B57AD,alpha)
            p.e(19,0,9,12,0xEFEBD7,alpha)
            p.grad(-23,-22,44,48,0xE7EEDB,0xFFF4DA,0xCACBB5,6,false,alpha)
            for col in 0..<5 {
        p.r(-19+Double(col)*8,-19,3.5,40,0x244AAB,1,alpha)
    }
            p.e(-1,-21,22,7,0xE8B4A0,alpha)
            p.e(-1,-21,17,4,0x8B332E,alpha)
            p.e(-5,-23,10,1.5,0xC57958,alpha)
        }
        if idx == 4 {
            p.path("M -25 -11 C -38 -33 -19 -46 -2 -36 C 18 -46 36 -27 24 -11 L 23 32 L -24 32 Z",0xAC602A,0,alpha)
            p.path("M -21 -10 C -30 -29 -17 -37 -2 -29 C 15 -38 29 -26 19 -10 L 18 26 L -19 26 Z",0xF1C46A,0,alpha)
            p.e(-4,-2,16,19,0xFFE1A1,0.8*alpha)
            for col in 0..<7 {
                let j=Double(col)
                p.e(-14+4.5*j,-12+23*abs(sin(j*4.3)),1,1.3,0xB37B36,0.55*alpha)
            }
            p.r(-10,-7,18,13,0xF9DC5C,2,alpha)
            p.line(-9,-6,6,-6,0xFFF1A4,1,alpha)
        }
        if idx == 5 {
            p.path("M -27 15 L -27 -14 C -27 -53 28 -53 28 -14 L 28 15",0x731D35,12,alpha)
            p.path("M -27 15 L -27 -14 C -27 -53 28 -53 28 -14 L 28 15",0xB92B4B,6,alpha)
            p.path("M -28 -13 C -29 -48 27 -48 28 -13",0xE27587,1.5,0.8*alpha)
            p.e(-27,13,11,18,0xA82745,alpha)
            p.e(-27,13,7,12,0x5B112C,alpha)
            p.e(-29,9,4,7,0xE06478,0.6*alpha)
            p.e(27,13,11,18,0xA82745,alpha)
            p.e(27,13,7,12,0x5B112C,alpha)
            p.e(25,9,4,7,0xE06478,0.6*alpha)
            p.r(-30,0,5,28,0xF28B95,2,0.45*alpha)
        }
        p.restore()
    }
    // Front wires occlude the packed objects instead of sitting behind them.
    p.save()
    p.translate(basketX,basketY)
    p.scale(0.86,1)
    p.path("M -107 0 L -84 103 L 84 103 L 107 0",0xD99CB7,3)
    p.path("M -107 0 L -84 103 L 84 103 L 107 0",0xFBC9DD,1.1)
    for idx in 0..<12 {
        let x = -98+Double(idx)*17.8
        p.line(x+1.7,6,x*0.8+1.7,101,0x7C4169,3,0.13)
        p.line(x,4,x*0.8,99,0xE3A5C1,1.8)
        p.line(x-0.8,4,x*0.8-0.8,99,0xF9D5E0,0.7)
    }
    for idx in 0..<4 {
        let y=21+Double(idx)*24
        let w=107-y*0.225
        p.line(-w,y,w,y,0xE2A0BB,1.8)
        p.line(-w,y-1,w,y-1,0xFBD4E0,0.7)
    }
    p.path("M -107 0 Q 0 13 107 0",0xD692B2,3)
    p.path("M -107 -1 Q 0 12 107 -1",0xFFD8E6,1)
    p.restore()
    p.grain(0,0,393,852,0.08)

}
