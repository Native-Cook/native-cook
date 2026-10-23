
import SwiftUI
import UIKit

/// All geometry and motion live in this file. `previewTime` selects an exact loop phase.
struct HorseshoeTimeRuler: View {
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
        if reducedMotion { return 3.8 }
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
                    let p=HorseshoeTimeRulerPainter(context)
                    p.r(0,0,size.width,size.height,0x070708)
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
                    let distance = -value.translation.height
                    phase=min(Self.loop-0.001,max(0,(gestureStart ?? 0)+distance/90))
                    lastTouch = .now
                }.onEnded { value in
                    if abs(value.translation.width)+abs(value.translation.height)<5 { phase=3.8 }
                    velocity=max(-5,min(5,-value.velocity.height/90))
                    lastTouch = .now;gestureStart=nil
                    UIImpactFeedbackGenerator(style:.light).impactOccurred()
                })
                .accessibilityElement(children:.ignore)
                .accessibilityLabel(previewTime.map { "Horseshoe Time Ruler preview "+String(Int(($0*1000).rounded())) } ?? "Horseshoe Time Ruler")
                .accessibilityHint("Drag vertically to travel through the day. Tap to explore the afternoon.")
                .accessibilityValue("Interactive motion preview")
                .accessibilityAdjustableAction { direction in
                    phase=min(Self.loop-0.001,max(0,phase+(direction == .increment ? 1:-1)))
                    lastTouch = .now
                }
            }
        }
        .ignoresSafeArea()
        .background(HorseshoeTimeRulerPainter.color(0x070708))
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
private final class HorseshoeTimeRulerPainter {
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
    static let paths: [String:Path] = ["M 82 145 L 82 548 C 82 700 311 700 311 548 L 311 145": Path { p in p.move(to:CGPoint(x:82,y:145));p.addLine(to:CGPoint(x:82,y:548));p.addCurve(to:CGPoint(x:311,y:548),control1:CGPoint(x:82,y:700),control2:CGPoint(x:311,y:700));p.addLine(to:CGPoint(x:311,y:145)) }]
}

private func drawScene(_ p:HorseshoeTimeRulerPainter,_ t:Double,_ tx:Double,_ ty:Double,_ touching:Bool) {
    // A single path bends the linear day into a continuous horseshoe.
    let travel = 410*seg(t,1,3.8)*(1-seg(t,4.8,7))
    p.r(0,0,393,852,0x070708)
    p.save()
    p.translate(0,-22)
    p.grad(0,55,393,102,0x232225,0x262528,0x242326)
    p.r(53,97,70,30,0x2D2C2F,5)
    p.r(130,97,130,30,0x2D2C2F,5)
    p.r(267,97,72,30,0x2D2C2F,5)
    p.text("Friday",88,105,12,0xD6D4D9,"regular",1)
    p.text("23 Oct 2026",195,105,12,0xD6D4D9,"regular",1)
    p.text("13:36",303,105,12,0xD6D4D9,"regular",1)
    p.restore()
    p.save()
    p.translate(0,-32)
    p.clip(20,155,353,575)
    p.path("M 82 145 L 82 548 C 82 700 311 700 311 548 L 311 145",0x171619,111)
    p.path("M 82 145 L 82 548 C 82 700 311 700 311 548 L 311 145",0x050506,105)
    p.path("M 82 145 L 82 548 C 82 700 311 700 311 548 L 311 145",0x27262A,100)
    p.path("M 82 145 L 82 548 C 82 700 311 700 311 548 L 311 145",0x09090B,94)
    p.path("M 82 145 L 82 548 C 82 700 311 700 311 548 L 311 145",0x2F2E32,88)
    p.path("M 82 145 L 82 548 C 82 700 311 700 311 548 L 311 145",0x08080A,82)
    p.path("M 82 145 L 82 548 C 82 700 311 700 311 548 L 311 145",0x252428,74)
    let names = ["Morning pages","Studio review","A walk outside","Time to make","Lunch with team","Creative hour","Evening reset","Plan tomorrow"]
    let times = ["08:00","09:30","11:00","12:15","13:30","15:00","17:30","19:00"]
    for idx in 0..<64 {
        let i = Double(idx)
        var distance = i*18+travel
        if distance >= 1150 { distance = distance-1150 }
        var x = 82.0
        var y = 155+distance
        var angle = 90.0
        if distance > 393 && distance < 753 {
            let theta = 3.141592653589793-(distance-393)/114.5
            x = 196.5+114.5*cos(theta)
            y = 548+114.5*sin(theta)
            angle = theta*180/3.141592653589793-90
        }
        if distance >= 753 {
            x = 311
            y = 548-(distance-753)
            angle = -90
        }
        p.save()
        p.translate(x,y)
        p.rotate(angle)
        p.line(0,-36,0,-32,0x545158,0.7,0.55)
        p.line(0,32,0,36,0x545158,0.7,0.55)
        p.restore()
    }
    for idx in 0..<8 {
        let i = Double(idx)
        var distance = 45+i*143+travel
        if distance >= 1150 { distance = distance-1150 }
        var x = 82.0
        var y = 155+distance
        var angle = 90.0
        if distance > 393 && distance < 753 {
            let theta = 3.141592653589793-(distance-393)/114.5
            x = 196.5+114.5*cos(theta)
            y = 548+114.5*sin(theta)
            angle = theta*180/3.141592653589793-90
        }
        if distance >= 753 {
            x = 311
            y = 548-(distance-753)
            angle = -90
        }
        p.save()
        p.translate(x,y)
        p.rotate(angle)
        p.line(0,-36,0,36,pick(idx == 4,0xEF3B59,0x434147),pick(idx == 4,1.8,0.8))
        p.text(times[idx],8,-22,9,0x969199,"mono")
        p.text(names[idx],8,-7,10,0x8B858F)
        p.restore()
    }
    p.restore()
    p.save()
    p.translate(0,-48)
    p.line(196.5,616,196.5,729,0x117AFF,1.5,0.9)
    p.line(194.5,623,194.5,690,0x0A4D91,0.7,0.7)
    for idx in 0..<8 {
        let i = Double(idx)
        p.e(196.5,626+14*i,1.5,1.5,0x50A7FF,0.45)
    }
    p.radial(196.5,593,35,0x208EFF,0x070708,0.13)
    p.r(169,565,55,58,0x070708,28)
    p.e(196.5,593,25,25,0x0B1826)
    p.e(196.5,593,23,23,0x1987F9)
    p.e(196.5,593,20,20,0x050A0E)
    p.save()
    p.translate(196.5,593)
    p.rotate(-42+travel*0.085)
    p.line(0,0,12,-10,0x258FFF,2.2)
    p.line(0,0,-3,6,0x258FFF,2)
    p.e(0,0,2.2,2.2,0x59B9FF)
    p.restore()
    p.save()
    p.translate(196.5,553)
    p.rotate(-90)
    p.text("IN 18 HOURS",0,-6,9,0x77717D,"mono")
    p.restore()
    p.restore()
    p.save()
    p.translate(0,-14)
    p.e(42,756,7,7,0x157DE0)
    p.e(42,756,5.8,5.8,0x070708)
    p.line(42,756,42,752,0x268DF7,1)
    p.line(42,756,45,757,0x268DF7,1)
    p.text("Days",42,780,13,0x368FE6,"regular",1)
    p.text("Follow the day",196.5,780,14,0x368FE6,"regular",1)
    p.e(351,786,8,8,0x1F7FE5)
    p.e(351,786,6.5,6.5,0x070708)
    p.line(347,789,355,781,0x368FE6,1.2)
    p.line(350,781,355,781,0x368FE6,1.2)

    p.restore()

}
