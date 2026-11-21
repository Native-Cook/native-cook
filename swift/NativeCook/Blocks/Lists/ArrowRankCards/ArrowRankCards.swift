import SwiftUI
import UIKit

struct ArrowRankCards: View {
    static let loop=9.0
    var autoPlay=true
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var phase
    @State private var engine=ArrowRankCardsEngine()
    @State private var down=false
    @State private var active=false
    @State private var region=0
    @GestureState private var fingerDown=false
    private let art=ArrowRankCardsArtwork.shared
    private typealias A=ArrowRankCardsArtwork
    private typealias M=ArrowRankCardsMotion
    var body: some View {
        GeometryReader { safe in
            let inset=safe.safeAreaInsets.top
            GeometryReader { geo in
                let s=Double(geo.size.width)/402
                let top=max(0,Double(inset)-62*s)
                let h=(Double(geo.size.height)-top)/s
                let deck=min(1,(h-204)/670)
                let lower=178+596*deck
                TimelineView(.animation(minimumInterval:1/60,paused:phase != .active)) { timeline in
                    let _=update(timeline.date)
                    Canvas { c,_ in draw(&c,engine.frame,h:h,deck:deck,lower:lower) }
                        .frame(width:402,height:h,alignment:.topLeading).scaleEffect(s,anchor:.topLeading)
                        .frame(width:geo.size.width,height:geo.size.height,alignment:.topLeading).offset(y:top)
                        .contentShape(Rectangle()).gesture(finger(s,top,deck,lower))
                }
            }.ignoresSafeArea()
        }
        .background(A.color(0xEBEBEA).ignoresSafeArea()).preferredColorScheme(.light)
        .accessibilityElement(children:.ignore).accessibilityLabel("Popular album chart. Swipe in the arrow direction to turn the nine-prism sign. Drag the ruler to scrub ranks.")
        .accessibilityAction(named:Text("Next chart")) { engine.advance() }
        .accessibilityAction(named:Text("Save album")) { engine.bookmark() }
        .accessibilityAdjustableAction { direction in engine.begin();engine.live=M.rest(engine.live.index+(direction == .increment ? 1 : -1));engine.end(0,0,false) }
        .onChange(of:fingerDown) { _,value in if !value && down { cancel() } }
        .onChange(of:phase) { _,value in if value != .active { cancel() };engine.suspend() }
    }
    private func finger(_ s: Double,_ top: Double,_ deck: Double,_ lower: Double)->some Gesture {
        DragGesture(minimumDistance:0).updating($fingerDown) { _,state,_ in state=true }.onChanged { v in
                            let x=v.startLocation.x/s,y=(v.startLocation.y-top)/s
                            if !down {
                                down=true;active=false
                                region=x>=94 && x<=311 && y>=lower && y<=lower+64 ? 2 : x>=321 && x<=385 && y>=178+57*deck && y<=178+121*deck ? 3 : y>=lower && y<=lower+64 && x>=330 ? 3 : y>=lower && y<=lower+64 && x<=75 ? 4 : x>=349 && y>=68 && y<=112 ? 4 : x>=8 && x<=394 && y>=178+44*deck && y<=178+578*deck ? 1 : 0
                                if region==2 { engine.rulerBegin() }
                            }
                            let dx=v.translation.width/s,dy=v.translation.height/s
                            if region==1 && !active && hypot(v.translation.width,v.translation.height)>2 { active=true;engine.begin() }
                            if region==1 && active { engine.move(dx,dy) }
                            if region==2 { engine.rulerMove(dx) }
                        }.onEnded { v in
                            if region==1 { if active { engine.end(v.velocity.width/s,v.velocity.height/s) } else { engine.hint() } }
                            else if region==2 { engine.rulerEnd(v.velocity.width/s) }
                            else if hypot(v.translation.width,v.translation.height)<10 { if region==3 { engine.bookmark() } else if region==4 { engine.hint() } }
                            down=false;active=false;region=0
                        }
    }
    private func update(_ date: Date) {
        engine.configure(autoPlay,reduced);engine.buzz={ cue in
            if cue=="selection" { UISelectionFeedbackGenerator().selectionChanged() }
            else { UIImpactFeedbackGenerator(style:cue=="rigid" ? .rigid : cue=="medium" ? .medium : .light).impactOccurred() }
        };engine.step(date.timeIntervalSinceReferenceDate)
    }
    private func cancel() { if active && region==1 { engine.end(0,0,false) };if down && region==2 { engine.rulerEnd(0) };down=false;active=false;region=0 }
    private func rect(_ c: inout GraphicsContext,_ x: Double,_ y: Double,_ w: Double,_ h: Double,_ r: Double,_ col: UInt32) { c.fill(Path(roundedRect:CGRect(x:x,y:y,width:w,height:h),cornerRadius:r),with:.color(A.color(col))) }
    private func draw(_ c: inout GraphicsContext,_ f: ArrowRankCardsFrame,h: Double,deck: Double,lower: Double) {
        c.fill(Path(CGRect(x:0,y:0,width:402,height:h)),with:.linearGradient(Gradient(stops:[.init(color:A.color(0xF1F1F0),location:0),.init(color:A.color(0xEBEBEA),location:0.3),.init(color:A.color(0xEBEBEA),location:1)]),startPoint:.zero,endPoint:CGPoint(x:0,y:h)))
        art.text(&c,"Popular",18,70+34*1490/2048,34,0x0B0B0B,false,-0.714);art.text(&c,"Album",18,104+34*1490/2048,34,0xA8A8A8,false,-0.714)
        icon(&c,3,362,84)
        var d=c;d.translateBy(x:0,y:178);d.scaleBy(x:1,y:deck)
        let peek=sin(.pi*f.drum)*8
        rect(&d,81,28+peek,233,40,20,0xF0F0EF);rect(&d,41,36+peek,312,40,22,0xF4F4F3)
        d.drawLayer { shadow in shadow.addFilter(.shadow(color:A.color(0x3A3A38,0.1),radius:12,x:0,y:3));rect(&shadow,8,44,386,534,26,0xF9F9F8) }
        for line in 0...1 {
            var clip=d;clip.clip(to:Path(CGRect(x:26,y:55+Double(line)*27,width:290,height:line==0 ? 27 : 29)))
            let e=M.smooth(M.seg(f.drum,0.08+0.16*Double(line),0.68+0.16*Double(line)))
            for i in [f.index,f.next] {
                var row=clip;row.opacity=f.flat>0 ? (i==f.index ? 1-f.drum : f.drum) : 1
                let offset=f.flat>0 ? 0 : i==f.index ? -29*e : 29*(1-e)
                art.text(&row,A.albums[i][line],26,55+Double(line)*27+(line==1 ? -1 : 0)+24*1490/2048+offset,24,line==0 ? 0x0B0B0B : 0xC8C8C8,false,-0.408)
            }
        }
        var p=d;p.translateBy(x:15,y:196);panel(&p,f)
        rect(&d,321,57,64,64,32,0xFAFAF9);d.stroke(Path(ellipseIn:CGRect(x:321.5,y:57.5,width:63,height:63)),with:.color(A.color(0xE7E7E6)),lineWidth:1)
        icon(&d,0,341,77,saved:engine.saved[f.drum<0.5 ? f.index : f.next],scale:engine.bookmarkScale)
        rect(&c,11,lower,64,64,32,0xF3F3F2);icon(&c,1,31,lower+20)
        rect(&c,330,lower,64,64,32,0xF3F3F2);icon(&c,2,350,lower+20)
        var ruler=c;ruler.clip(to:Path(roundedRect:CGRect(x:94,y:lower,width:217,height:64),cornerRadius:32));rect(&ruler,94,lower,217,64,32,0xF3F3F2)
        let shift=(f.ticks.truncatingRemainder(dividingBy:12)+12).truncatingRemainder(dividingBy:12)
        for i in 0..<21 { for y in [lower+6,lower+52] { rect(&ruler,82+Double(i)*12+shift,y,2,6,0,0xCDCDCC) } }
        art.text(&ruler,"Today",202.5-art.run("Today",13).1/2,lower+32+13*1490/4096,13)
        rect(&c,200,lower-1,2,66,0,0xEF3B2D)
    }
    private func panel(_ c: inout GraphicsContext,_ f: ArrowRankCardsFrame) {
        c.clip(to:Path(roundedRect:CGRect(x:0,y:0,width:375,height:374),cornerRadius:18))
        c.fill(Path(CGRect(x:0,y:0,width:375,height:374)),with:.shader(Shader(function:ShaderFunction(library:.default,name:"arrowRankCardsPrisms"),arguments:[
            .float4(Float(f.q[0]),Float(f.q[1]),Float(f.q[2]),Float(f.q[3])),.float4(Float(f.q[4]),Float(f.q[5]),Float(f.q[6]),Float(f.q[7])),.float(Float(f.q[8])),.float(f.axis),.float(f.reverse),.float(f.twitch),.float(f.flat>0 ? f.drum : -1),.image(Image(uiImage:art.faces[f.index])),.image(Image(uiImage:art.faces[f.next]))])))
        let strength=0.18*M.clamp(8*(f.q.map { $0*(1-$0) }.max() ?? 0))
        if strength>0 { var shadow=c;shadow.opacity=strength;shadow.translateBy(x:0,y:2);sign(&shadow,f) }
        sign(&c,f)
    }
    private func sign(_ c: inout GraphicsContext,_ f: ArrowRankCardsFrame) {
        var old=c;old.opacity=f.flat>0 ? 1-f.drum : 1;var shape=f
        if f.flat>0 { shape.head=M.heading(f.index);shape.tail=shape.head }
        old.stroke(A.arrow(shape),with:.color(A.color(0x0B0B0B)),style:StrokeStyle(lineWidth:22,lineCap:.butt,lineJoin:.miter))
        if f.flat>0 { var next=c;next.opacity=f.drum;shape.head=M.heading(f.next);shape.tail=shape.head;next.stroke(A.arrow(shape),with:.color(A.color(0x0B0B0B)),style:StrokeStyle(lineWidth:22,lineCap:.butt,lineJoin:.miter)) }
        var digits=c;digits.clip(to:Path(CGRect(x:278,y:252,width:97,height:122)))
        for i in [f.index,f.next] {
            var digit=digits;digit.opacity=f.flat>0 ? (i==f.index ? 1-f.drum : f.drum) : 1
            let offset=f.flat>0 ? 0 : (i==f.index ? f.drum : f.drum-1)*110*(f.reverse>0 ? -1 : 1),x=f.axis==0 ? offset : 0,y=f.axis==1 ? offset : 0
            let str=A.ranks[i],width=art.run(str,130,true).1
            if f.blur>0 && f.flat==0 {
                for j in -3...3 { var sample=digit;let weight=exp(-Double(j*j)/2),sum=2.505949878974977;sample.opacity*=weight/sum;let blur=Double(j)*f.blur/4;art.text(&sample,str,364-width+x+(f.axis==0 ? blur : 0),358+y+(f.axis==1 ? blur : 0),130,0x0B0B0B,true) }
            } else { art.text(&digit,str,364-width+x,358+y,130,0x0B0B0B,true) }
        }
    }
    private func icon(_ c: inout GraphicsContext,_ kind: Int,_ x: Double,_ y: Double,saved: Bool=false,scale: Double=1) {
        var p=Path()
        func line(_ pts: [CGPoint],close: Bool=false) { p.addLines(pts);if close { p.closeSubpath() } }
        if kind==0 { line([CGPoint(x:5,y:4),CGPoint(x:19,y:4),CGPoint(x:19,y:20),CGPoint(x:12,y:15.5),CGPoint(x:5,y:20)],close:true) }
        if kind==1 { p.addRect(CGRect(x:4,y:6,width:16,height:15));line([CGPoint(x:8,y:3),CGPoint(x:8,y:8)]);line([CGPoint(x:16,y:3),CGPoint(x:16,y:8)]);line([CGPoint(x:4,y:10),CGPoint(x:20,y:10)]);p.addEllipse(in:CGRect(x:6.75,y:13.75,width:2.5,height:2.5)) }
        if kind==2 { line([CGPoint(x:3,y:7),CGPoint(x:9,y:7),CGPoint(x:11,y:9),CGPoint(x:21,y:9),CGPoint(x:21,y:20),CGPoint(x:3,y:20)],close:true) }
        if kind==3 { line([CGPoint(x:2,y:3),CGPoint(x:9,y:10),CGPoint(x:16,y:3)]) }
        var ctx=c;ctx.translateBy(x:x+12,y:y+12);ctx.scaleBy(x:scale,y:scale);ctx.translateBy(x:-12,y:-12);if saved { ctx.fill(p,with:.color(A.color(0x0B0B0B))) };ctx.stroke(p,with:.color(A.color(0x0B0B0B)),style:StrokeStyle(lineWidth:2,lineCap:.square,lineJoin:.miter))
    }
}
