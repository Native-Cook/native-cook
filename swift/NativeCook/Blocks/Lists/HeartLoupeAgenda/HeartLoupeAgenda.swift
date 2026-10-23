
import SwiftUI
import UIKit
import CoreText

/// All geometry and motion live in this file. `previewTime` selects an exact loop phase.
struct HeartLoupeAgenda: View {
    static let loop = 9.0
    var autoPlay = false
    var previewTime: Double? = nil
    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var start = Date()
    @State private var lastTouch: Date? = nil
    @State private var phase = 0.0
    @State private var gestureStart: Double? = nil
    @State private var point = CGPoint(x:196.5,y:420.0)
    @State private var velocity = 0.0

    private func time(_ date: Date) -> Double {
        if let previewTime { return max(0,previewTime).truncatingRemainder(dividingBy:Self.loop) }
        if reducedMotion { return 4.5 }
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
                    let p=HeartLoupeAgendaPainter(context)
                    p.r(0,0,size.width,size.height,0xC71D35)
                    let scale=min(size.width/393,size.height/852)
                    p.translate((size.width-393*scale)/2,(size.height-852*scale)/2)
                    p.scale(scale,scale)
                    let age=lastTouch.map { max(0,timeline.date.timeIntervalSince($0)) } ?? 100
                    let strength = reducedMotion || previewTime != nil ? 0 : (gestureStart != nil ? 1 : 1-smooth((age-2)/0.4))
                    drawScene(p,time(timeline.date),196.5+(point.x-196.5)*strength,420.0+(point.y-420.0)*strength,strength>0)
                }
                .contentShape(Rectangle())
                .gesture(DragGesture(minimumDistance:0).onChanged { value in
                    if gestureStart == nil { gestureStart=time(.now);phase=gestureStart ?? 0 }
                    let scale=min(geometry.size.width/393,geometry.size.height/852)
                    point=CGPoint(x:value.location.x/scale,y:value.location.y/scale)
                    let distance = -value.translation.height
                    phase=min(Self.loop-0.001,max(0,(gestureStart ?? 0)+distance/90))
                    lastTouch = .now
                }.onEnded { value in
                    if abs(value.translation.width)+abs(value.translation.height)<5 { phase=4.5 }
                    velocity=max(-5,min(5,-value.velocity.height/90))
                    lastTouch = .now;gestureStart=nil
                    UIImpactFeedbackGenerator(style:.light).impactOccurred()
                })
                .accessibilityElement(children:.ignore)
                .accessibilityLabel(previewTime.map { "Heart Loupe Agenda preview "+String(Int(($0*1000).rounded())) } ?? "Heart Loupe Agenda")
                .accessibilityHint("Drag the glass heart to look closer. Swipe upward to explore the schedule.")
                .accessibilityValue("Interactive motion preview")
                .accessibilityAdjustableAction { direction in
                    phase=min(Self.loop-0.001,max(0,phase+(direction == .increment ? 1:-1)))
                    lastTouch = .now
                }
            }
        }
        .ignoresSafeArea()
        .background(HeartLoupeAgendaPainter.color(0xC71D35))
        .preferredColorScheme(.dark)
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
private final class HeartLoupeAgendaPainter {
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
    func layerAlpha(_ alpha:Double) { save();c.opacity *= alpha }

    static let titleArtwork:UIImage = {
        if let url=Bundle.main.url(forResource:"HeartLoupeAgenda-Lobster-Regular",withExtension:"ttf") { CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil) }
        let font=UIFont(name:"Lobster-Regular",size:100) ?? UIFont.boldSystemFont(ofSize:100)
        let value="My Little Loop" as NSString
        let width=value.size(withAttributes:[.font:font]).width+40
        let format=UIGraphicsImageRendererFormat();format.scale=2
        return UIGraphicsImageRenderer(size:CGSize(width:width,height:170),format:format).image { _ in
            let origin=CGPoint(x:20,y:125-font.ascender)
            value.draw(at:CGPoint(x:25,y:origin.y+7),withAttributes:[.font:font,.foregroundColor:UIColor(red:0.39,green:0.04,blue:0.13,alpha:0.6),.strokeColor:UIColor(red:0.39,green:0.04,blue:0.13,alpha:0.6),.strokeWidth:-20])
            value.draw(at:origin,withAttributes:[.font:font,.foregroundColor:UIColor(red:1,green:0.94,blue:0.86,alpha:1),.strokeColor:UIColor(red:1,green:0.94,blue:0.86,alpha:1),.strokeWidth:-18])
            value.draw(at:origin,withAttributes:[.font:font,.foregroundColor:UIColor(red:0.09,green:0.05,blue:0.08,alpha:1),.strokeColor:UIColor(red:0.09,green:0.05,blue:0.08,alpha:1),.strokeWidth:-10])
            value.draw(at:origin,withAttributes:[.font:font,.foregroundColor:UIColor(red:0.77,green:0.07,blue:0.13,alpha:1)])
        }
    }()
    func sticker(_ x:Double,_ y:Double,_ width:Double) { c.draw(Image(uiImage:Self.titleArtwork),in:CGRect(x:x-width/2,y:y,width:width,height:width*0.27)) }

    func path(_ key: String,_ color: Int,_ width: Double = 0,_ alpha: Double = 1) { guard let path=Self.paths[key] else { return }; if width > 0 { c.stroke(path,with:.color(Self.color(color,alpha)),style:StrokeStyle(lineWidth:width,lineCap:.round,lineJoin:.round)) } else { c.fill(path,with:.color(Self.color(color,alpha))) } }
    func clipPath(_ key: String) { if let p=Self.paths[key] { c.clip(to:p) } }
    static let paths: [String:Path] = ["M -10 -3 L -21 -12 L -25 -3 L -20 7 L -9 4 Z": Path { p in p.move(to:CGPoint(x:-10,y:-3));p.addLine(to:CGPoint(x:-21,y:-12));p.addLine(to:CGPoint(x:-25,y:-3));p.addLine(to:CGPoint(x:-20,y:7));p.addLine(to:CGPoint(x:-9,y:4));p.closeSubpath() },
        "M -116 -48 C -118 -88 -76 -119 -42 -94 M 32 -97 C 71 -128 117 -101 127 -66": Path { p in p.move(to:CGPoint(x:-116,y:-48));p.addCurve(to:CGPoint(x:-42,y:-94),control1:CGPoint(x:-118,y:-88),control2:CGPoint(x:-76,y:-119));p.move(to:CGPoint(x:32,y:-97));p.addCurve(to:CGPoint(x:127,y:-66),control1:CGPoint(x:71,y:-128),control2:CGPoint(x:117,y:-101)) },
        "M -131 -9 C -127 51 -63 95 -8 133": Path { p in p.move(to:CGPoint(x:-131,y:-9));p.addCurve(to:CGPoint(x:-8,y:133),control1:CGPoint(x:-127,y:51),control2:CGPoint(x:-63,y:95)) },
        "M -132 -8 Q -112 61 -45 99": Path { p in p.move(to:CGPoint(x:-132,y:-8));p.addQuadCurve(to:CGPoint(x:-45,y:99),control:CGPoint(x:-112,y:61)) },
        "M -139 -35 C -136 -96 -72 -126 -16 -77 C -38 -87 -97 -82 -112 -31 Z": Path { p in p.move(to:CGPoint(x:-139,y:-35));p.addCurve(to:CGPoint(x:-16,y:-77),control1:CGPoint(x:-136,y:-96),control2:CGPoint(x:-72,y:-126));p.addCurve(to:CGPoint(x:-112,y:-31),control1:CGPoint(x:-38,y:-87),control2:CGPoint(x:-97,y:-82));p.closeSubpath() },
        "M -144 -31 L -69 -31 C -88 -72 -28 -72 -45 -31 L 2 -31 L 2 16 C -39 -2 -39 55 2 39 L 2 124 M -70 -127 L -70 -74 C -29 -92 -29 -33 -70 -49 L -70 11 L -116 11 C -99 52 -155 52 -139 11": Path { p in p.move(to:CGPoint(x:-144,y:-31));p.addLine(to:CGPoint(x:-69,y:-31));p.addCurve(to:CGPoint(x:-45,y:-31),control1:CGPoint(x:-88,y:-72),control2:CGPoint(x:-28,y:-72));p.addLine(to:CGPoint(x:2,y:-31));p.addLine(to:CGPoint(x:2,y:16));p.addCurve(to:CGPoint(x:2,y:39),control1:CGPoint(x:-39,y:-2),control2:CGPoint(x:-39,y:55));p.addLine(to:CGPoint(x:2,y:124));p.move(to:CGPoint(x:-70,y:-127));p.addLine(to:CGPoint(x:-70,y:-74));p.addCurve(to:CGPoint(x:-70,y:-49),control1:CGPoint(x:-29,y:-92),control2:CGPoint(x:-29,y:-33));p.addLine(to:CGPoint(x:-70,y:11));p.addLine(to:CGPoint(x:-116,y:11));p.addCurve(to:CGPoint(x:-139,y:11),control1:CGPoint(x:-99,y:52),control2:CGPoint(x:-155,y:52)) },
        "M -20 -9 L -20 -34 C -20 -66 23 -66 23 -34 L 23 -9": Path { p in p.move(to:CGPoint(x:-20,y:-9));p.addLine(to:CGPoint(x:-20,y:-34));p.addCurve(to:CGPoint(x:23,y:-34),control1:CGPoint(x:-20,y:-66),control2:CGPoint(x:23,y:-66));p.addLine(to:CGPoint(x:23,y:-9)) },
        "M -29 -5 Q -31 -22 -16 -17 Q -1 -12 2 -4 Q 11 -22 27 -16 Q 38 -10 28 8 L 24 24 L -20 24 Z": Path { p in p.move(to:CGPoint(x:-29,y:-5));p.addQuadCurve(to:CGPoint(x:-16,y:-17),control:CGPoint(x:-31,y:-22));p.addQuadCurve(to:CGPoint(x:2,y:-4),control:CGPoint(x:-1,y:-12));p.addQuadCurve(to:CGPoint(x:27,y:-16),control:CGPoint(x:11,y:-22));p.addQuadCurve(to:CGPoint(x:28,y:8),control:CGPoint(x:38,y:-10));p.addLine(to:CGPoint(x:24,y:24));p.addLine(to:CGPoint(x:-20,y:24));p.closeSubpath() },
        "M -29 0 L 29 0 M 0 -29 L 0 29 M -21 -21 C 8 -13 10 12 -21 21 M 21 -21 C -8 -13 -10 12 21 21": Path { p in p.move(to:CGPoint(x:-29,y:0));p.addLine(to:CGPoint(x:29,y:0));p.move(to:CGPoint(x:0,y:-29));p.addLine(to:CGPoint(x:0,y:29));p.move(to:CGPoint(x:-21,y:-21));p.addCurve(to:CGPoint(x:-21,y:21),control1:CGPoint(x:8,y:-13),control2:CGPoint(x:10,y:12));p.move(to:CGPoint(x:21,y:-21));p.addCurve(to:CGPoint(x:21,y:21),control1:CGPoint(x:-8,y:-13),control2:CGPoint(x:-10,y:12)) },
        "M -34 -6 Q -36 -27 -17 -22 Q -1 -17 2 -9 Q 8 -27 29 -21 Q 44 -14 33 9 L 28 29 L -25 29 Z": Path { p in p.move(to:CGPoint(x:-34,y:-6));p.addQuadCurve(to:CGPoint(x:-17,y:-22),control:CGPoint(x:-36,y:-27));p.addQuadCurve(to:CGPoint(x:2,y:-9),control:CGPoint(x:-1,y:-17));p.addQuadCurve(to:CGPoint(x:29,y:-21),control:CGPoint(x:8,y:-27));p.addQuadCurve(to:CGPoint(x:33,y:9),control:CGPoint(x:44,y:-14));p.addLine(to:CGPoint(x:28,y:29));p.addLine(to:CGPoint(x:-25,y:29));p.closeSubpath() },
        "M -5 -6 Q -1 -10 5 -5": Path { p in p.move(to:CGPoint(x:-5,y:-6));p.addQuadCurve(to:CGPoint(x:5,y:-5),control:CGPoint(x:-1,y:-10)) },
        "M -56 -8 C -71 -31 -92 -2 -50 27 C -13 -1 -34 -30 -47 -10 Z": Path { p in p.move(to:CGPoint(x:-56,y:-8));p.addCurve(to:CGPoint(x:-50,y:27),control1:CGPoint(x:-71,y:-31),control2:CGPoint(x:-92,y:-2));p.addCurve(to:CGPoint(x:-47,y:-10),control1:CGPoint(x:-13,y:-1),control2:CGPoint(x:-34,y:-30));p.closeSubpath() },
        "M -74 -97 Q -31 -115 -5 -75": Path { p in p.move(to:CGPoint(x:-74,y:-97));p.addQuadCurve(to:CGPoint(x:-5,y:-75),control:CGPoint(x:-31,y:-115)) },
        "M -83 -85 L -52 28 L 43 21 L 79 -91 M -52 28 L 0 -74 L 43 21 M -52 28 L 0 140 L 43 21": Path { p in p.move(to:CGPoint(x:-83,y:-85));p.addLine(to:CGPoint(x:-52,y:28));p.addLine(to:CGPoint(x:43,y:21));p.addLine(to:CGPoint(x:79,y:-91));p.move(to:CGPoint(x:-52,y:28));p.addLine(to:CGPoint(x:0,y:-74));p.addLine(to:CGPoint(x:43,y:21));p.move(to:CGPoint(x:-52,y:28));p.addLine(to:CGPoint(x:0,y:140));p.addLine(to:CGPoint(x:43,y:21)) },
        "M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z": Path { p in p.move(to:CGPoint(x:0,y:-74));p.addCurve(to:CGPoint(x:-136,y:0),control1:CGPoint(x:-90,y:-160),control2:CGPoint(x:-160,y:-70));p.addCurve(to:CGPoint(x:0,y:140),control1:CGPoint(x:-118,y:75),control2:CGPoint(x:-52,y:99));p.addCurve(to:CGPoint(x:136,y:0),control1:CGPoint(x:52,y:100),control2:CGPoint(x:118,y:75));p.addCurve(to:CGPoint(x:0,y:-74),control1:CGPoint(x:160,y:-70),control2:CGPoint(x:88,y:-160));p.closeSubpath() },
        "M 0 -74 L -83 -85 L -136 0 L -52 28 L 0 140 L 43 21 L 136 0 L 79 -91 Z": Path { p in p.move(to:CGPoint(x:0,y:-74));p.addLine(to:CGPoint(x:-83,y:-85));p.addLine(to:CGPoint(x:-136,y:0));p.addLine(to:CGPoint(x:-52,y:28));p.addLine(to:CGPoint(x:0,y:140));p.addLine(to:CGPoint(x:43,y:21));p.addLine(to:CGPoint(x:136,y:0));p.addLine(to:CGPoint(x:79,y:-91));p.closeSubpath() },
        "M 0 136 L -102 226 L -265 74": Path { p in p.move(to:CGPoint(x:0,y:136));p.addLine(to:CGPoint(x:-102,y:226));p.addLine(to:CGPoint(x:-265,y:74)) },
        "M 0 139 L -102 229 L -265 77": Path { p in p.move(to:CGPoint(x:0,y:139));p.addLine(to:CGPoint(x:-102,y:229));p.addLine(to:CGPoint(x:-265,y:77)) },
        "M 0 141 L -102 231 L -265 79": Path { p in p.move(to:CGPoint(x:0,y:141));p.addLine(to:CGPoint(x:-102,y:231));p.addLine(to:CGPoint(x:-265,y:79)) },
        "M 0 145 L -102 235 L -265 83": Path { p in p.move(to:CGPoint(x:0,y:145));p.addLine(to:CGPoint(x:-102,y:235));p.addLine(to:CGPoint(x:-265,y:83)) },
        "M 10 -3 L 21 -12 L 25 -3 L 20 7 L 9 4 Z": Path { p in p.move(to:CGPoint(x:10,y:-3));p.addLine(to:CGPoint(x:21,y:-12));p.addLine(to:CGPoint(x:25,y:-3));p.addLine(to:CGPoint(x:20,y:7));p.addLine(to:CGPoint(x:9,y:4));p.closeSubpath() },
        "M 121 26 Q 77 94 8 131": Path { p in p.move(to:CGPoint(x:121,y:26));p.addQuadCurve(to:CGPoint(x:8,y:131),control:CGPoint(x:77,y:94)) },
        "M 5 -75 C 57 -132 129 -100 139 -42": Path { p in p.move(to:CGPoint(x:5,y:-75));p.addCurve(to:CGPoint(x:139,y:-42),control1:CGPoint(x:57,y:-132),control2:CGPoint(x:129,y:-100)) },
        "M 85 -108 C 159 -86 159 -5 106 59 C 145 -15 131 -65 85 -108 Z": Path { p in p.move(to:CGPoint(x:85,y:-108));p.addCurve(to:CGPoint(x:106,y:59),control1:CGPoint(x:159,y:-86),control2:CGPoint(x:159,y:-5));p.addCurve(to:CGPoint(x:85,y:-108),control1:CGPoint(x:145,y:-15),control2:CGPoint(x:131,y:-65));p.closeSubpath() }]
}

private func drawScene(_ p:HeartLoupeAgendaPainter,_ t:Double,_ tx:Double,_ ty:Double,_ touching:Bool) {
    let open=seg(t,0.9,1.8)*(1-seg(t,6.6,7.6))
    let drift=seg(t,1.8,2.1)*(1-seg(t,5.6,5.9))
    let scroll=310*seg(t,1.8,5.6)*(1-seg(t,5.6,6.6))
    var lensX=196.5+18*sin(6.283185307179586*t/9)*drift
    var lensY=420+95*open+12*sin(12.566370614359172*t/9)*drift
    if touching { lensX=min(278,max(114,tx));lensY=min(648,max(320,ty)) }
    let lensScale=0.65+0.35*open
    p.r(0,0,393,852,0xB51027)
    p.grad(24,0,345,852,0xCA1A2B,0xC8172A,0xBE1429)
    p.line(24,0,24,852,0x6A1525,1,0.7)
    p.line(369,0,369,852,0x731527,1,0.7)
    // Small candy hearts frame one slanted, outlined script masthead.
    let hx=[169,196,117,38,49,341,350,300,316]
    let hy=[72,64,118,345,457,405,477,670,692]
    for idx in 0..<9 {
        let i=Double(idx)
        p.save()
        p.translate(Double(hx[idx]),Double(hy[idx]))
        p.rotate(-28+11*i)
        p.scale(pick(idx < 3,0.055,0.035),pick(idx < 3,0.055,0.035))
        p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0x661729,35,0.35)
        p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xFFF0D8,21)
        p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xE95153,5)
        p.restore()
    }
    p.save()
    p.translate(58,88)
    p.rotate(-28)
    p.line(0,6,0,40,0xE0B8A4,5)
    p.scale(0.22,0.22)
    p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xA20B21)
    p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xF14D58,0,0.35)
    p.path("M -56 -8 C -71 -31 -92 -2 -50 27 C -13 -1 -34 -30 -47 -10 Z",0xFFF1D8)
    p.restore()
    p.save()
    p.translate(196.5,123)
    p.rotate(-10)
    p.sticker(0,0,375)
    p.restore()
    p.save()
    p.translate(80,236)
    p.rotate(-28)
    p.fit("A LITTLE EVERYDAY",0,-6,13,139,0xFFF0D9,"condensed")
    p.fit("CLUB FIELD NOTES",0,9,12,122,0xFFF0D9,"condensed")
    p.restore()
    p.save()
    p.translate(303,231)
    p.rotate(9)
    p.text("OCT.",0,-6,37,0x280D18,"heavy",1)
    p.restore()
    p.save()
    p.translate(332,326)
    p.rotate(18)
    p.e(4,47,47,7,0x6F112E,0.5)
    p.grad(-43,-42,86,79,0x8DADFF,0x4F7BE8,0x315BC9,7,true)
    p.r(-37,-37,74,67,0x244EA5,4)
    p.e(-3,-5,29,29,0xF36D3B)
    p.e(-3,-5,23,23,0xE74236)
    p.e(-3,-5,18,18,0xFF8B4E)
    p.e(-3,-5,8,8,0xFADE8B)
    p.e(-3,-5,2,2,0x3A3B72)
    p.line(28,-27,28,4,0xD8E7CD,3)
    p.line(28,4,11,17,0xD8E7CD,3)
    p.r(6,14,12,5,0x9BC0C9,1)
    p.e(-29,27,3,3,0xE9E1AD)
    p.line(-41,39,42,39,0xF07D8B,2)
    p.restore()
    p.save()
    p.translate(48,384)
    p.rotate(13)
    p.scale(0.20,0.20)
    p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0x751D58)
    p.path("M 0 -74 L -83 -85 L -136 0 L -52 28 L 0 140 L 43 21 L 136 0 L 79 -91 Z",0xD657AF,3)
    p.path("M -83 -85 L -52 28 L 43 21 L 79 -91 M -52 28 L 0 -74 L 43 21 M -52 28 L 0 140 L 43 21",0xF6A8D5,2)
    p.path("M -74 -97 Q -31 -115 -5 -75",0xFAD5E9,5)
    p.restore()
    p.save()
    p.translate(355,639)
    p.rotate(11)
    p.scale(0.8,0.8)
    p.e(3,6,32,32,0x6A1433,0.35)
    p.e(0,0,30,30,0xEA718C)
    p.path("M -29 0 L 29 0 M 0 -29 L 0 29 M -21 -21 C 8 -13 10 12 -21 21 M 21 -21 C -8 -13 -10 12 21 21",0x8A3154,2)
    p.e(-12,-15,7,3,0xF7AFB4,0.6)
    p.restore()
    // A split-colour puzzle heart, a nested candy and a small heart handbag.
    p.save()
    p.translate(43,632)
    p.rotate(-31)
    p.scale(0.28,0.28)
    p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xEAA9BF)
    p.save()
    p.clipPath("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z")
    p.r(-150,-150,150,310,0xA9D5E4)
    p.path("M -144 -31 L -69 -31 C -88 -72 -28 -72 -45 -31 L 2 -31 L 2 16 C -39 -2 -39 55 2 39 L 2 124 M -70 -127 L -70 -74 C -29 -92 -29 -33 -70 -49 L -70 11 L -116 11 C -99 52 -155 52 -139 11",0x5E889C,3)
    p.restore()
    p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xF8D7DB,7)
    p.restore()
    p.save()
    p.translate(66,755)
    p.rotate(-12)
    p.scale(0.36,0.36)
    p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xAA1129)
    p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xF6C7BA,7)
    for idx in 0..<4 {
        let scale=0.8-Double(idx)*0.17
        p.save()
        p.scale(scale,scale)
        p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xFFE0CB,5)
        p.restore()
    }
    p.e(-70,-73,10,7,0xFFF0D5,0.7)
    p.restore()
    p.save()
    p.translate(325,753)
    p.rotate(-8)
    p.path("M -20 -9 L -20 -34 C -20 -66 23 -66 23 -34 L 23 -9",0xFFEBD2,5)
    p.path("M -34 -6 Q -36 -27 -17 -22 Q -1 -17 2 -9 Q 8 -27 29 -21 Q 44 -14 33 9 L 28 29 L -25 29 Z",0xFBE7CE)
    p.path("M -29 -5 Q -31 -22 -16 -17 Q -1 -12 2 -4 Q 11 -22 27 -16 Q 38 -10 28 8 L 24 24 L -20 24 Z",0xB00D2B)
    p.e(6,3,3,4,0xE1B55D)
    p.line(5,7,3,17,0xEED28E,2)
    p.e(3,17,3,3,0xF3D79A)
    p.line(-26,-4,-24,18,0xE77B87,1.5,0.7)
    p.restore()
    let candyX=[52,343,298,102]
    let candyY=[477,472,688,697]
    for idx in 0..<4 {
        p.save()
        p.translate(Double(candyX[idx]),Double(candyY[idx]))
        p.rotate(-30+Double(idx)*37)
        p.path("M -10 -3 L -21 -12 L -25 -3 L -20 7 L -9 4 Z",0xEB4A5F)
        p.path("M 10 -3 L 21 -12 L 25 -3 L 20 7 L 9 4 Z",0xAE0E2D)
        p.e(1,2,12,10,0x790E28,0.5)
        p.e(0,0,11,10,0xD9163B)
        p.e(-2,-3,7,5,0xF96075,0.65)
        p.path("M -5 -6 Q -1 -10 5 -5",0xFFE8D5,1.3,0.8)
        p.line(-19,-6,-13,-1,0xFFACAF,1,0.8)
        p.restore()
    }
    // A metal dispenser remains visible before the ivory schedule unrolls.
    p.r(123,264,149,376,0x690D23,2,0.6)
    p.grad(126,266,143,368,0x55494A,0xE0D1BE,0x66514D,2,true)
    p.r(132,306,131,320,0x332E2D)
    p.grad(136,309,123,313,0x454342,0x8F8A7D,0x5B5751)
    p.grad(142,314,15,302,0x8B8C80,0xB4B3A0,0x7E8175,0,true,0.5)
    p.grad(246,314,8,302,0x26292A,0xAAA99B,0x474D47,0,true,0.5)
    p.grad(132,271,131,36,0x8E7772,0xE5D5C5,0xBEAAA0,1)
    p.r(144,276,107,25,0xF1E9D7,1)
    p.text("MY LITTLE LOOP",197,278,9,0x9B2135,"condensed",1)
    p.text("FIELD SCHEDULE",197,288,10,0x892337,"condensed",1)
    let dates=["19","20","21","22","23","24","25","26","27","28"]
    let first=["OPEN","CONCEPT","PROCESS","COLOUR","LOOP","COVER","STUDIO","SOFT","PAPER","DAWN"]
    let second=["STUDIO","NOTES","FILM","MIX","TEASER","PHOTO","PREVIEW","HOURS","PARTY","CLUB"]
    for layer in 0..<2 {
        if layer == 0 || open > 0.0001 {
            if layer == 0 { p.save() } else { p.layerAlpha(open) }
            if layer == 1 {
                p.translate(lensX,lensY)
                p.scale(lensScale,lensScale)
                p.rotate(-5)
                p.clipPath("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z")
                p.scale(1.7,1.7)
                p.translate(-lensX,-lensY)
                p.r(0,0,393,852,0xC8172A)
            }
            if open > 0.0001 {
                p.r(130,310,135,490*open,0x64142A,0,0.25)
                p.grad(133,308,129,490*open,0xF8F6ED,0xFFFDF4,0xE6E4DA)
                p.save()
                p.clip(134,309,127,max(0.001,488*open))
                for idx in 0..<10 {
                    let i=Double(idx)
                    let y=333+i*118-scroll
                    if y > 180 && y < 820 {
                        p.line(135,y-16,260,y-16,0x463C32,0.9)
                        p.text(dates[idx],139,y-10,12,0xAC253B,"bold")
                        if idx % 3 == 0 {
                            p.fit(first[idx],200,y+9,24,109,0x25201F,"condensed")
                            p.fit(second[idx],200,y+37,28,109,0x25201F,"condensed")
                        }
                        if idx % 3 == 1 {
                            p.fit(first[idx],200,y+9,23,109,0x2B211E,"condensed")
                            p.fit(second[idx],201,y+36,25,105,0x2B211E,"italic")
                        }
                        if idx % 3 == 2 {
                            p.fit(first[idx],200,y+9,23,109,0xAC293C,"condensed")
                            p.fit(second[idx],200,y+35,30,109,0x25201F,"condensed")
                        }
                        p.text("OCT / CLUB FIELD NOTES",198,y+82,5.3,0x73604D,"mono",1)
                    }
                }
                p.restore()
                p.line(134,309,134,308+490*open,0xFFF9E2,0.7)
            }
            p.restore()
        }
    }
    // An articulated metal arm joins the heart without hiding the agenda text.
    p.layerAlpha(open)
    p.translate(lensX,lensY)
    p.scale(lensScale,lensScale)
    p.rotate(-5)
    p.path("M 0 145 L -102 235 L -265 83",0x6C1D38,19,0.45)
    p.path("M 0 141 L -102 231 L -265 79",0x566272,16)
    p.path("M 0 139 L -102 229 L -265 77",0xCED7CE,11)
    p.path("M 0 136 L -102 226 L -265 74",0xFFF3DF,3)
    p.e(-102,229,15,15,0x6B7881)
    p.e(-102,228,12,12,0xE6E8D8)
    p.e(-102,228,7,7,0x818B98)
    p.e(-104,225,3,3,0xFFF5DE)
    p.e(-265,77,17,17,0x9EABB0)
    p.e(-265,77,12,12,0xE7E8D5)
    p.e(-265,77,5,5,0x66758A)
    p.save()
    p.translate(7,11)
    p.blur(7)
    p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0x641A32,22,0.42)
    p.restore()
    p.blur(0)
    p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0x86877F,22)
    p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xE2DFCA,17)
    p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xB9BDB5,10)
    p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xEDE3DA,3)
    p.save()
    p.translate(-1.5,-2)
    p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xFCE6D6,1.5,0.8)
    p.restore()
    p.save()
    p.clipPath("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z")
    p.radial(-40,-55,170,0xFFF5DA,0xFFF5DA,0.12)
    p.path("M -139 -35 C -136 -96 -72 -126 -16 -77 C -38 -87 -97 -82 -112 -31 Z",0xFFF4E2,0,0.16)
    p.path("M 85 -108 C 159 -86 159 -5 106 59 C 145 -15 131 -65 85 -108 Z",0xF5D6D9,0,0.19)
    p.restore()
    p.e(-96+10*open,-82-7*open,8,2,0xFFFCEB,0.7)
    p.line(-97+10*open,-91-7*open,-95+10*open,-73-7*open,0xFFF9E7,1.2,0.65)
    p.path("M -116 -48 C -118 -88 -76 -119 -42 -94 M 32 -97 C 71 -128 117 -101 127 -66",0xFFF9E8,3.5,0.6+0.25*open)
    p.path("M 121 26 Q 77 94 8 131",0xE686A1,2,0.8)
    p.path("M -132 -8 Q -112 61 -45 99",0xA8E1DA,1.5,0.8)
    p.path("M -131 -9 C -127 51 -63 95 -8 133",0x7A2333,5,0.36)
    p.path("M 5 -75 C 57 -132 129 -100 139 -42",0xFFF3DD,4.5,0.8)
    let flash=seg(t,0.95,1.15)*(1-seg(t,1.25,1.6))
    p.path("M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xFFF9E5,0,0.74*flash)
    p.restore()
    p.grain(0,0,393,852,0.055)

}
