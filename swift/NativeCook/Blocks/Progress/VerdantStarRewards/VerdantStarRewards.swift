import SwiftUI
import UIKit

struct VerdantStarRewards: View {
    static let loop = 8.0
    var autoPlay = false
    var previewTime: Double? = nil
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @State private var epoch = Date()
    @State private var manual: Double? = nil
    @State private var touching = false
    @GestureState private var gestureActive = false
    @State private var start = 0.0
    @State private var release = Date.distantPast
    @State private var activated = false
    @State private var selection = 0
    @State private var detent = false
    private let ink = Color(hex: 0xF1F6E8)
    private let accent = Color(hex: 0xB2FF52)
    private func ease(_ value: Double) -> Double { let u = min(1, max(0, value)); return u*u*(3-2*u) }
    private func pose(_ date: Date) -> Double {
        if reduced { return 1 }
        if let manual { return touching ? manual : manual*(1-ease((date.timeIntervalSince(release)-2)/0.45)) }
        let t = previewTime ?? (autoPlay ? max(0,date.timeIntervalSince(epoch)).truncatingRemainder(dividingBy: Self.loop) : 0)
        return ease((t-1.4)/1.8)*(1-ease((t-4.8)/1.8))
    }
    var body: some View {
        GeometryReader { geo in
            let scale = min(geo.size.width/393, geo.size.height/760)
            TimelineView(.animation(paused: reduced || scenePhase != .active || previewTime != nil || (!autoPlay && manual == nil))) { timeline in
                let p = pose(timeline.date)
                ZStack(alignment: .topLeading) {
                    Color(hex: 0x12170D)
                    HStack {
                        Image(systemName: "sparkle").font(.system(size: 15,weight: .medium))
                        Text("HABIT / REWARDS").font(.system(size: 10,weight: .semibold)).tracking(1.8)
                        Spacer()
                        Text("DAY 03").font(.system(size: 10,design: .monospaced)).opacity(0.55)
                    }.frame(width: 337).position(x:196.5,y:32)
                    Text("Good days\nadd up.").font(.system(size:38,weight:.semibold,design: .default)).tracking(-1.2)
                        .multilineTextAlignment(.center).lineSpacing(-1).frame(width:350).position(x:196.5,y:118)
                    Text("Your daily reward is ready.").font(.system(size:13)).opacity(0.6).position(x:196.5,y:186)
                    artwork(p).frame(width:337,height:330).contentShape(Rectangle())
                        .gesture(DragGesture(minimumDistance:1).updating($gestureActive) { _,active,_ in active=true }.onChanged { value in
                            if !touching { start=pose(.now);touching=true;detent=false }
                            let next = min(1,max(0,start + -value.translation.height / (180*scale)))
                            manual=next
                            if next > 0.85 && !detent { detent=true;UIImpactFeedbackGenerator(style:.light).impactOccurred() }
                        }.onEnded { _ in touching=false;release = .now })
                        .accessibilityElement(children:.ignore).accessibilityLabel("Verdant Star Rewards")
                        .accessibilityValue(p > 0.7 ? "Star assembled":"Gather the reward star")
                        .accessibilityHint("Drag upward to gather")
                        .accessibilityAdjustableAction { direction in manual=direction == .increment ? 1:0;release = .now }
                        .position(x:196.5,y:385)
                    detail.position(x:196.5,y:591)
                    Button { activate() } label: {
                        HStack(spacing:9) { Image(systemName: activated ? "checkmark":"arrow.up.right");Text(activated ? "150 points collected" : "Claim 150 points").fontWeight(.semibold) }
                            .font(.system(size:15)).frame(width:337,height:54)
                            .background(accent,in:RoundedRectangle(cornerRadius:18))
                            .foregroundStyle(Color(hex:0x18200E))
                    }.buttonStyle(VerdantStarRewardsPressStyle(reduced: reduced)).position(x:196.5,y:680)
                    Text("DRAG UPWARD TO GATHER").font(.system(size:9,weight:.medium)).tracking(1.6).opacity(0.45).position(x:196.5,y:735)
                }.foregroundStyle(ink).frame(width:393,height:760,alignment:.topLeading)
                    .scaleEffect(scale).frame(width:geo.size.width,height:geo.size.height)
            }
        }.background(Color(hex:0x12170D).ignoresSafeArea()).preferredColorScheme(.dark)
        .task(id:release) {
            guard manual != nil,!touching else { return }
            do { try await Task.sleep(for:.seconds(2.45)) } catch { return }
            guard !touching else { return };manual=nil;epoch = .now
        }
        .onChange(of:gestureActive) { _,active in if !active && touching { touching=false;release = .now } }
        .onChange(of:scenePhase) { _,value in if value == .active { manual=nil;touching=false;epoch = .now } }
        .onChange(of:reduced) { _,_ in manual=nil;touching=false;epoch = .now }
    }
    private func activate() { activated=true; manual=1;release = .now;UISelectionFeedbackGenerator().selectionChanged() }
    private func label(_ text:String,at point:CGPoint,size:CGFloat,color:Color,context:GraphicsContext,weight:Font.Weight = .regular) {
        context.draw(Text(text).font(.system(size:size,weight:weight)).foregroundStyle(color),at:point)
    }
    
    private func star(_ center: CGPoint, radius: Double) -> Path {
        Path { path in
            for i in 0..<10 {
                let angle = Double(i)*Double.pi/5 - Double.pi/2
                let r = i%2 == 0 ? radius:radius*0.43
                let point=CGPoint(x:center.x+cos(angle)*r,y:center.y+sin(angle)*r)
                if i == 0 { path.move(to:point) } else { path.addLine(to:point) }
            };path.closeSubpath()
        }
    }
    private func artwork(_ p:Double) -> some View {
        Canvas { c,_ in
            let center=CGPoint(x:168.5,y:155)
            let halo=Path(ellipseIn:CGRect(x:38,y:25,width:261,height:261))
            c.fill(halo,with:.radialGradient(Gradient(colors:[accent.opacity(0.25+p*0.15),accent.opacity(0)]),center:center,startRadius:5,endRadius:132))
            for ring in 0..<3 { c.stroke(Path(ellipseIn:CGRect(x:24+Double(ring)*14,y:54+Double(ring)*16,width:289-Double(ring)*28,height:202-Double(ring)*32)),with:.color(accent.opacity(0.10*(1-p))),lineWidth:1) }
            let shape=star(center,radius:99)
            c.fill(shape,with:.linearGradient(Gradient(colors:[Color(hex:0xE3FFA5).opacity(p),accent.opacity(p*0.85),Color(hex:0x347414).opacity(p)]),startPoint:CGPoint(x:110,y:60),endPoint:CGPoint(x:230,y:250)))
            c.stroke(shape,with:.color(Color(hex:0xE5FFB7).opacity(p*0.8)),lineWidth:1.5)
            for i in 0..<72 {
                let index=Double(i),angle=index*2.399963
                let edge=index/72*10,vertex=Int(edge),mix=edge-Double(vertex)
                func vertexPoint(_ n:Int)->CGPoint {
                    let a=Double(n)*Double.pi/5-Double.pi/2,r=n%2==0 ? 99.0:42.57
                    return CGPoint(x:center.x+cos(a)*r,y:center.y+sin(a)*r)
                }
                let v=vertexPoint(vertex),w=vertexPoint(vertex+1)
                let target=CGPoint(x:v.x+(w.x-v.x)*mix,y:v.y+(w.y-v.y)*mix)
                let radius=112+Double(i%7)*4
                let source=CGPoint(x:center.x+cos(angle)*radius,y:center.y+sin(angle)*radius*0.79)
                let q=ease(min(1,p*(1.05+Double(i%5)*0.03)))
                let curl=sin(q*Double.pi)*22
                let x=source.x+(target.x-source.x)*q+cos(angle+1.57)*curl
                let y=source.y+(target.y-source.y)*q+sin(angle+1.57)*curl
                let r=1.4+Double(i%4)*0.55
                c.fill(Path(ellipseIn:CGRect(x:x-r,y:y-r,width:r*2,height:r*2)),with:.color(i%3 == 0 ? Color(hex:0xEEFFC6):accent.opacity(0.65)))
            }
            for i in 0..<5 {
                let a=Double(i)*2*Double.pi/5-Double.pi/2
                var facet=Path();facet.move(to:center);facet.addLine(to:CGPoint(x:center.x+cos(a)*99,y:center.y+sin(a)*99));facet.addLine(to:CGPoint(x:center.x+cos(a+Double.pi/5)*42.57,y:center.y+sin(a+Double.pi/5)*42.57));facet.closeSubpath()
                c.fill(facet,with:.color(Color.white.opacity((i%2==0 ? 0.24:0.06)*p)))
            }
            label(activated ? "REWARD COLLECTED":"150 POINTS",at:CGPoint(x:168.5,y:292),size:12,color:accent,context:c,weight:.semibold)
        }
    }

    private var detail: some View { HStack(spacing:6) { ForEach(0..<7,id:\.self) { i in VStack(spacing:7) { Text("\(i+1)").font(.system(size:10));Image(systemName:i<2 || (i==2 && activated) ? "checkmark":"sparkle").font(.system(size:13)) }.frame(width:41,height:52).background(i==2 ? accent:ink.opacity(0.05),in:RoundedRectangle(cornerRadius:12)).foregroundStyle(i==2 ? Color(hex:0x18200E):ink.opacity(0.45)) } }.accessibilityElement(children:.ignore).accessibilityLabel(activated ? "Day three reward collected":"Day three reward ready") }
}

private struct VerdantStarRewardsPressStyle: ButtonStyle {
    let reduced: Bool
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.scaleEffect(configuration.isPressed && !reduced ? 0.97:1)
            .opacity(configuration.isPressed ? 0.8:1)
            .animation(.easeOut(duration:0.13),value:configuration.isPressed)
    }
}
