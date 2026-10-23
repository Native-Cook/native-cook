
import SwiftUI
import UIKit

/// All geometry and motion live in this file. `previewTime` selects an exact loop phase.
struct FoldingWeekAgenda: View {
    static let loop = 7.2
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
                    let p=FoldingWeekAgendaPainter(context)
                    p.r(0,0,size.width,size.height,0xFCFCFA)
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
                    if abs(value.translation.width)+abs(value.translation.height)<5 { phase=2.4 }
                    velocity=max(-5,min(5,-value.velocity.height/90))
                    lastTouch = .now;gestureStart=nil
                    UIImpactFeedbackGenerator(style:.light).impactOccurred()
                })
                .accessibilityElement(children:.ignore)
                .accessibilityLabel(previewTime.map { "Folding Week Agenda preview "+String(Int(($0*1000).rounded())) } ?? "Folding Week Agenda")
                .accessibilityHint("Drag upward to fold the calendar. Tap to preview the compact agenda.")
                .accessibilityValue("Interactive motion preview")
                .accessibilityAdjustableAction { direction in
                    phase=min(Self.loop-0.001,max(0,phase+(direction == .increment ? 1:-1)))
                    lastTouch = .now
                }
            }
        }
        .ignoresSafeArea()
        .background(FoldingWeekAgendaPainter.color(0xFCFCFA))
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
private final class FoldingWeekAgendaPainter {
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
    static let paths: [String:Path] = ["M 12 45 L 12 40 C 14 34 22 36 22 29 C 16 28 17 24 14 23 L 17 17 C 12 3 36 -1 39 12 C 42 20 35 27 32 31 L 33 36 L 40 49 L 14 49 Z": Path { p in p.move(to:CGPoint(x:12,y:45));p.addLine(to:CGPoint(x:12,y:40));p.addCurve(to:CGPoint(x:22,y:29),control1:CGPoint(x:14,y:34),control2:CGPoint(x:22,y:36));p.addCurve(to:CGPoint(x:14,y:23),control1:CGPoint(x:16,y:28),control2:CGPoint(x:17,y:24));p.addLine(to:CGPoint(x:17,y:17));p.addCurve(to:CGPoint(x:39,y:12),control1:CGPoint(x:12,y:3),control2:CGPoint(x:36,y:-1));p.addCurve(to:CGPoint(x:32,y:31),control1:CGPoint(x:42,y:20),control2:CGPoint(x:35,y:27));p.addLine(to:CGPoint(x:33,y:36));p.addLine(to:CGPoint(x:40,y:49));p.addLine(to:CGPoint(x:14,y:49));p.closeSubpath() },
        "M 143 786 L 149 780 L 155 786 L 155 792 L 143 792 Z": Path { p in p.move(to:CGPoint(x:143,y:786));p.addLine(to:CGPoint(x:149,y:780));p.addLine(to:CGPoint(x:155,y:786));p.addLine(to:CGPoint(x:155,y:792));p.addLine(to:CGPoint(x:143,y:792));p.closeSubpath() }]
}

private func drawScene(_ p:FoldingWeekAgendaPainter,_ t:Double,_ tx:Double,_ ty:Double,_ touching:Bool) {
    // A continuous calendar surface folds into the next appointment.
    let fold = seg(t,1.1,2.0)*(1-seg(t,3.6,4.6))
    let oldAlpha = 1-seg(fold,0.25,0.65)
    let compactAlpha = seg(fold,0.35,0.8)
    let panelHeight = 340-280*fold
    p.r(0,0,393,852,0xFCFCFA)
    p.radial(196,478-276*fold,192,0xF3DDB2,0xFCFCFA,0.32*(1-fold))
    p.r(24,108,345,panelHeight,0xCECBC5,24,0.08)
    p.r(24,106,345,panelHeight,0x191A19,24)
    p.save()
    p.clip(24,106,345,panelHeight,24)
    p.text("Upcoming",44,130-27*fold,24,0xF1F1EA,"regular",0,oldAlpha)
    p.text("In the next 2 weeks",44,163-24*fold,21,0x70746F,"regular",0,oldAlpha)
    for idx in 0..<3 {
        let i = Double(idx)
        p.e(335,138+8*i-50*fold,1.3,1.3,0x92978F,oldAlpha)
    }
    let week = ["MON","TUE","WED","THU","FRI","SAT","SUN"]
    for idx in 0..<14 {
        let i = Double(idx)
        let column = Double(idx % 7)
        let row = floor(i/7)
        let x = 58+46*column
        let y = 358+47*row-225*fold
        p.e(x,y,17.5,17.5,pick(idx == 4,0xE5E7DF,0x30332E),oldAlpha)
        p.text(str(i+8),x,y-7,12,pick(idx == 4,0x222922,0xE1E4DB),"regular",1,oldAlpha)
        if idx < 7 { p.text(week[idx],x,325-215*fold,8,0x777D73,"mono",1,oldAlpha) }
        if idx == 4 {
            p.e(x-6,y-10,2,2,0xCB78D8,oldAlpha)
            p.e(x,y-11,2,2,0x9CD657,oldAlpha)
            p.e(x+6,y-10,2,2,0x83C5FF,oldAlpha)
        }
    }
    p.r(42,124,28,28,0xEBEDE4,7,compactAlpha)
    p.line(50,133,62,133,0x6F766B,1.2,compactAlpha)
    p.line(50,139,59,139,0x6F766B,1.2,compactAlpha)
    p.text("Next appointment",83,120,10,0x90998D,"regular",0,compactAlpha)
    p.text("Studio check-in · 10:30",83,137,14,0xF2F3EB,"regular",0,compactAlpha)
    p.line(330,144,340,134,0xE4E6DF,1.5,compactAlpha)
    p.line(332,134,340,134,0xE4E6DF,1.5,compactAlpha)
    p.line(340,134,340,142,0xE4E6DF,1.5,compactAlpha)
    p.restore()
    let titles = ["Studio check-in","Color notes","The season ahead","Wellness review","A quieter morning","Movement session"]
    let subtitles = ["Action needed","Upcoming","Your personal roadmap","Completed","Completed","Completed"]
    let months = ["Mar","Mar","Jan","Jan","Dec","Nov"]
    let days = ["22","12","08","08","18","29"]
    let inks = [0xFFAE23,0xEAEDE5,0xFF8A3B,0xE9EDE5,0xD38A54,0xE4E6DE]
    p.save()
    p.clip(0,180-12*fold,393,640+12*fold)
    for idx in 0..<6 {
        let i = Double(idx)
        let follow = seg(t,1.1+0.035*i,2.0+0.035*i)*(1-seg(t,3.6+0.035*i,4.6+0.035*i))
        let y = 469+91*i-280*follow
        p.r(24,y+2,345,81,0xD9DBD5,17,0.09)
        p.grad(24,y,345,81,0xF6F7F4,0xF8F9F6,0xF5F6F4,17)
        p.text(months[idx],40,y+14,12,0x969D92)
        p.text(days[idx],40,y+33,23,0x747D70)
        p.line(84,y+16,84,y+64,0xE8EBE4,1)
        p.r(100,y+15,49,49,inks[idx],9)
        if idx == 0 {
            p.save()
            p.translate(100,y+15)
            p.path("M 12 45 L 12 40 C 14 34 22 36 22 29 C 16 28 17 24 14 23 L 17 17 C 12 3 36 -1 39 12 C 42 20 35 27 32 31 L 33 36 L 40 49 L 14 49 Z",0x251C15)
            p.restore()
        }
        if idx == 1 || idx == 3 {
            for jdx in 0..<3 {
                let j = Double(jdx)
                p.r(110+10*j,y+26,3,27,0xC4C9BC,1)
                p.r(109+10*j,y+21,5,8,pick(jdx == 0,0xA4CE54,pick(jdx == 1,0xD57AC5,0x89BCEA)),1)
            }
        }
        if idx == 2 {
            for jdx in 0..<4 {
                let j = Double(jdx)
                p.e(111+24*Double(jdx % 2),y+28+23*floor(j/2),10,10,0xFFD755,0.5)
            }
            p.line(103,y+39,145,y+39,0xFFE17F,1.2,0.65)
        }
        if idx > 3 {
            p.e(124,y+31,10,11,0x572E21)
            p.r(111,y+42,26,22,0x674130,9)
        }
        p.text(titles[idx],162,y+21,14,0x373D34)
        p.text(subtitles[idx],162,y+44,12,0x9CA495)
        if idx < 2 {
            p.e(337,y+41,15,15,0xEFF1EA)
            p.r(332,y+39,10,9,0xCAD0C3,3)
            p.line(334,y+38,334,y+34,0xAFBAA7,1)
            p.line(340,y+38,340,y+34,0xAFBAA7,1)
            p.line(334,y+34,340,y+34,0xAFBAA7,1)
        }
        if idx == 2 { p.text("›",338,y+23,29,0x333B2F,"regular",1) }
        if idx > 2 {
            p.e(337,y+40,11,11,0xA2E8CB)
            p.line(332,y+40,336,y+44,0xFAFFF7,1.8)
            p.line(336,y+44,342,y+36,0xFAFFF7,1.8)
        }
    }
    p.restore()
    p.grad(0,808,393,44,0xFCFCFA,0xFCFCFA,0xFCFCFA,0,false,0.95)
    p.save()
    p.translate(196.5,0)
    p.scale(160.0/147.0,1)
    p.translate(-196.5,0)
    p.r(123,762,147,50,0x070C08,26)
    p.e(149,787,18,18,0x2E372E)
    p.path("M 143 786 L 149 780 L 155 786 L 155 792 L 143 792 Z",0xF2F7EC)
    for idx in 0..<3 {
        let i = Double(idx)
        p.r(180+4*i,787-3*i,2.5,8+3*i,0xA2ADA0,1)
        p.line(219+3*i,782+3*i,225+3*i,782+3*i,0x9EAA9C,1.4)
        p.e(253,782+5*i,1.2,1.2,0x9EAA9C)
    }
    p.restore()

}
