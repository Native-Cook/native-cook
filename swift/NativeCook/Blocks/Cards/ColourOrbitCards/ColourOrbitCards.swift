
import SwiftUI
import UIKit

/// All geometry and motion live in this file. `previewTime` selects an exact loop phase.
struct ColourOrbitCards: View {
    static let loop = 8.0
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
                    let p=ColourOrbitCardsPainter(context)
                    p.r(0,0,size.width,size.height,0xFCFBFD)
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
                    if abs(value.translation.width)+abs(value.translation.height)<5 { phase=2.5 }
                    velocity=max(-5,min(5,value.velocity.width/90))
                    lastTouch = .now;gestureStart=nil
                    UIImpactFeedbackGenerator(style:.light).impactOccurred()
                })
                .accessibilityElement(children:.ignore)
                .accessibilityLabel(previewTime.map { "Colour Orbit Cards preview "+String(Int(($0*1000).rounded())) } ?? "Colour Orbit Cards")
                .accessibilityHint("Drag sideways to orbit the cards. Tap to bring the next print forward.")
                .accessibilityValue("Interactive motion preview")
                .accessibilityAdjustableAction { direction in
                    phase=min(Self.loop-0.001,max(0,phase+(direction == .increment ? 1:-1)))
                    lastTouch = .now
                }
            }
        }
        .ignoresSafeArea()
        .background(ColourOrbitCardsPainter.color(0xFCFBFD))
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
private final class ColourOrbitCardsPainter {
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
    func path(_ key: String,_ color: Int,_ width: Double = 0,_ alpha: Double = 1) { guard let path=Self.paths[key] else { return }; if width > 0 { c.stroke(path,with:.color(Self.color(color,alpha)),style:StrokeStyle(lineWidth:width,lineCap:.round,lineJoin:.round)) } else { c.fill(path,with:.color(Self.color(color,alpha))) } }
    func clipPath(_ key: String) { if let p=Self.paths[key] { c.clip(to:p) } }
    static let paths: [String:Path] = ["M -4 84 L -1 93 L 9 96 L -1 99 L -4 108 L -7 99 L -17 96 L -7 93 Z": Path { p in p.move(to:CGPoint(x:-4,y:84));p.addLine(to:CGPoint(x:-1,y:93));p.addLine(to:CGPoint(x:9,y:96));p.addLine(to:CGPoint(x:-1,y:99));p.addLine(to:CGPoint(x:-4,y:108));p.addLine(to:CGPoint(x:-7,y:99));p.addLine(to:CGPoint(x:-17,y:96));p.addLine(to:CGPoint(x:-7,y:93));p.closeSubpath() },
        "M -57 -31 L -70 -43 L -57 -56 L -45 -44 Z": Path { p in p.move(to:CGPoint(x:-57,y:-31));p.addLine(to:CGPoint(x:-70,y:-43));p.addLine(to:CGPoint(x:-57,y:-56));p.addLine(to:CGPoint(x:-45,y:-44));p.closeSubpath() },
        "M -63 25 L -38 10 L -18 23 L -23 55 L -54 61 L -70 42 Z": Path { p in p.move(to:CGPoint(x:-63,y:25));p.addLine(to:CGPoint(x:-38,y:10));p.addLine(to:CGPoint(x:-18,y:23));p.addLine(to:CGPoint(x:-23,y:55));p.addLine(to:CGPoint(x:-54,y:61));p.addLine(to:CGPoint(x:-70,y:42));p.closeSubpath() },
        "M -65 -70 C -82 -90 -95 -60 -56 -39 C -22 -63 -33 -85 -48 -72 C -53 -84 -61 -83 -65 -70 Z": Path { p in p.move(to:CGPoint(x:-65,y:-70));p.addCurve(to:CGPoint(x:-56,y:-39),control1:CGPoint(x:-82,y:-90),control2:CGPoint(x:-95,y:-60));p.addCurve(to:CGPoint(x:-48,y:-72),control1:CGPoint(x:-22,y:-63),control2:CGPoint(x:-33,y:-85));p.addCurve(to:CGPoint(x:-65,y:-70),control1:CGPoint(x:-53,y:-84),control2:CGPoint(x:-61,y:-83));p.closeSubpath() },
        "M -71 82 L -69 64 Q -52 54 -36 64 L -32 82 L -43 82 L -48 75 L -57 75 L -61 82 Z": Path { p in p.move(to:CGPoint(x:-71,y:82));p.addLine(to:CGPoint(x:-69,y:64));p.addQuadCurve(to:CGPoint(x:-36,y:64),control:CGPoint(x:-52,y:54));p.addLine(to:CGPoint(x:-32,y:82));p.addLine(to:CGPoint(x:-43,y:82));p.addLine(to:CGPoint(x:-48,y:75));p.addLine(to:CGPoint(x:-57,y:75));p.addLine(to:CGPoint(x:-61,y:82));p.closeSubpath() },
        "M 18 8 L 53 -4 L 51 32 Z": Path { p in p.move(to:CGPoint(x:18,y:8));p.addLine(to:CGPoint(x:53,y:-4));p.addLine(to:CGPoint(x:51,y:32));p.closeSubpath() },
        "M 34 74 Q 41 79 48 74": Path { p in p.move(to:CGPoint(x:34,y:74));p.addQuadCurve(to:CGPoint(x:48,y:74),control:CGPoint(x:41,y:79)) },
        "M 35 127 L 52 98 L 70 127 Z": Path { p in p.move(to:CGPoint(x:35,y:127));p.addLine(to:CGPoint(x:52,y:98));p.addLine(to:CGPoint(x:70,y:127));p.closeSubpath() },
        "M 45 -103 L 54 -96 L 70 -100 L 75 -94 L 52 -86 L 36 -91 L 39 -96 L 49 -93 Z": Path { p in p.move(to:CGPoint(x:45,y:-103));p.addLine(to:CGPoint(x:54,y:-96));p.addLine(to:CGPoint(x:70,y:-100));p.addLine(to:CGPoint(x:75,y:-94));p.addLine(to:CGPoint(x:52,y:-86));p.addLine(to:CGPoint(x:36,y:-91));p.addLine(to:CGPoint(x:39,y:-96));p.addLine(to:CGPoint(x:49,y:-93));p.closeSubpath() }]
}

private func drawScene(_ p:ColourOrbitCardsPainter,_ t:Double,_ tx:Double,_ ty:Double,_ touching:Bool) {
    let turn = 120*(seg(t,1,2)+seg(t,3.1,4.1)+seg(t,5.2,6.3))
    p.r(0,0,393,852,0xFCFBFD)
    p.e(196.5,508,130,8,0xBDBDC4,0.08)
    var order = [0,1,2]
    for idx in 0..<3 {
        for jdx in 0..<2 {
            let a = (Double(order[jdx])*120-turn)*3.141592653589793/180
            let b = (Double(order[jdx+1])*120-turn)*3.141592653589793/180
            if cos(a) > cos(b) {
                let hold = order[jdx]
                order[jdx] = order[jdx+1]
                order[jdx+1] = hold
            }
        }
    }
    for idx in 0..<3 {
        let card = order[idx]
        let theta = (Double(card)*120-turn)*3.141592653589793/180
        let depth = cos(theta)
        let scale = 0.72+0.28*(depth+1)/2
        let yaw = 0.5+0.5*abs(depth)
        let x = 196.5+143*sin(theta)
        let y = 335-13*depth
        p.save()
        p.translate(x,y)
        p.scale(scale*yaw,scale)
        p.skew(0,-0.12*sin(theta))
        p.rotate(2.5*sin(theta))
        p.e(4,152,80,9,0x6C7074,0.07)
        p.blur(1.8*max(0,1-depth))
        p.r(-85,-135,172,272,0x8A9493,12,0.7)
        p.grad(-84,-136,168,270,pick(card == 0,0xD6DEDC,pick(card == 1,0xF5F5F6,0xEEEFF0)),pick(card == 0,0xB9C7C5,pick(card == 1,0xFDFCFD,0xE3E5E6)),pick(card == 0,0xCAD3D0,pick(card == 1,0xF2F2F2,0xD8DCDD)),12)
        p.save()
        p.clip(-84,-136,168,270,12)
        if card == 0 {
            p.text("h.",-70,-127,18,0x172224,"heavy")
            p.text("ONE",12,-129,24,0x202B2B,"heavy")
            p.save()
            p.translate(-68,104)
            p.rotate(-90)
            p.text("MORE GOOD",0,0,21,0x202B2B,"heavy")
            p.restore()
            p.save()
            p.translate(72,-86)
            p.rotate(90)
            p.text("DAYS",0,0,22,0x202B2B,"heavy")
            p.restore()
            for jdx in 0..<2 {
                let j = Double(jdx)
                let cx = -10+19*j
                let cy = -36+108*j
                p.e(cx-22,cy-28,12,13,0xEDB532)
                p.e(cx+22,cy-28,12,13,0xEDB532)
                p.e(cx,cy+3,38,36,0xC78A29,0.25)
                p.e(cx,cy,38,37,0xF9C840)
                p.e(cx,cy+17,29,19,0xDE9E20,0.20)
                p.e(cx-8,cy-11,24,26,0xFFE16D,0.30)
                p.e(cx-22,cy-28,6,8,0xFFE781,0.45)
                p.e(cx+22,cy-28,6,8,0xFFE781,0.45)
                p.radial(cx-12,cy-14,41,0xFFE783,0xFBC937,0.8)
                p.e(cx-11,cy-2,2.5,3,0x302720)
                p.e(cx+10,cy-2,2.5,3,0x302720)
                p.e(cx-19,cy+8,5,3,0xEF8A53,0.8)
                p.e(cx+18,cy+8,5,3,0xEF8A53,0.8)
                p.e(cx,cy+9,9,7,0xFFF3CA)
                p.e(cx,cy+6,3,2.1,0x4D3422)
                p.line(cx,cy+9,cx,cy+12,0x4D3422,1.1)
            }
        }
        if card == 1 {
            p.text("h.",-71,-127,18,0x191D21,"heavy")
            p.save()
            p.translate(33,-95)
            p.rotate(24)
            p.r(-24,-12,65,27,0x3CEB69,2)
            p.text("PLAY",7,-5,10,0x122A17,"heavy",1)
            p.restore()
            p.path("M -65 -70 C -82 -90 -95 -60 -56 -39 C -22 -63 -33 -85 -48 -72 C -53 -84 -61 -83 -65 -70 Z",0x4547DF)
            p.save()
            p.rotate(-17)
            p.r(-88,-26,176,31,0xFFDE11)
            p.restore()
            p.path("M 18 8 L 53 -4 L 51 32 Z",0x8F50D7)
            p.path("M -63 25 L -38 10 L -18 23 L -23 55 L -54 61 L -70 42 Z",0xFE7724)
            p.e(41,67,26,26,0xFFE30D)
            p.e(34,63,1.5,2,0x342F1A)
            p.e(48,63,1.5,2,0x342F1A)
            p.path("M 34 74 Q 41 79 48 74",0x342F1A,1.2)
            p.save()
            p.translate(-47,98)
            p.rotate(-39)
            p.r(-38,-14,76,30,0x5153E8,3)
            p.text("GOOD STUFF",0,-6,9,0xE3E4FF,"heavy",1)
            p.restore()
            p.save()
            p.translate(72,116)
            p.rotate(14)
            p.r(-24,-24,48,48,0x25AB60,2)
            p.text("+",0,-17,29,0x0C6A37,"heavy",1)
            p.restore()
            p.e(-4,96,18,18,0xFBD519)
            p.path("M -4 84 L -1 93 L 9 96 L -1 99 L -4 108 L -7 99 L -17 96 L -7 93 Z",0xFEF6A1)
        }
        if card == 2 {
            p.text("h.",-72,-128,20,0xC8CECE,"heavy")
            p.text("travel",-73,-82,37,0xCBD1D2)
            p.text("love",-7,-33,39,0xCBD1D2)
            p.text("sports",-73,16,36,0xCBD1D2)
            p.text("game",-22,59,37,0xCBD1D2)
            p.text("hike",-72,100,34,0xCBD1D2)
            p.path("M -57 -31 L -70 -43 L -57 -56 L -45 -44 Z",0xC9CFD0,2.2)
            p.path("M 45 -103 L 54 -96 L 70 -100 L 75 -94 L 52 -86 L 36 -91 L 39 -96 L 49 -93 Z",0xC7CDCF,2)
            p.path("M 35 127 L 52 98 L 70 127 Z",0xBFC8C8,2.2)
            p.path("M -71 82 L -69 64 Q -52 54 -36 64 L -32 82 L -43 82 L -48 75 L -57 75 L -61 82 Z",0xC6CDCF,2.4)
            p.line(-63,67,-63,73,0xC6CDCF,2)
            p.line(-66,70,-60,70,0xC6CDCF,2)
        }
        // The same contact chip sits above every printed surface.
        p.r(-13,-108,29,37,0x8A9192,5)
        p.grad(-11.5,-106.5,26,34,0xD0D3CE,0xAFB6B4,0xDDE0D8,4,true)
        p.line(-3,-106,-3,-97,0x7D8784,0.65)
        p.line(6,-106,6,-97,0x7D8784,0.65)
        p.r(-4,-97,11,15,0xB8BFBB,2)
        p.line(-11,-89,-4,-89,0x7D8784,0.65)
        p.line(7,-89,14,-89,0x7D8784,0.65)
        p.line(-3,-82,-3,-73,0x7D8784,0.65)
        p.line(6,-82,6,-73,0x7D8784,0.65)
        p.line(-81,-133,80,-133,0xFFFFFF,0.9,0.65)
        p.line(-82,-125,-82,125,0xFFFFFF,0.7,0.27+0.2*max(0,depth))
        p.line(82,-125,82,125,0x4A5552,0.8,0.2)
        p.restore()
        p.blur(0)
        p.restore()
    }
    p.text("Your everyday,",196.5,585,31,0x161719,"bold",1)
    p.text("with more play.",196.5,623,31,0x161719,"bold",1)
    p.text("Three little ways to make it yours.",196.5,681,14,0x939194,"regular",1)
    p.line(191,746,196.5,752,0xB1ADB3,1.3)
    p.line(196.5,752,202,746,0xB1ADB3,1.3)

}
