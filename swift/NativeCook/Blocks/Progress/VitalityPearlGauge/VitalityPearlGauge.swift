import SwiftUI
import UIKit

struct VitalityPearlGauge: View {
    static let loop = 8.0
    var autoPlay = false
    var previewTime: Double? = nil
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @State private var epoch = Date()
    @State private var manual: Double? = nil
    @GestureState private var gestureActive = false
    @State private var touching = false
    @State private var start = 0.0
    @State private var release = Date.distantPast
    @State private var crossed = false
    private let cream=Color(hex:0xF0F5E4)
    private func smooth(_ x:Double)->Double { let u=min(1,max(0,x));return u*u*(3-2*u) }
    private func pose(_ date:Date)->Double {
        if reduced { return 0.5 }
        if let manual { return touching ? manual : manual*(1-smooth((date.timeIntervalSince(release)-2)/0.45)) }
        let t=previewTime ?? (autoPlay ? max(0,date.timeIntervalSince(epoch)).truncatingRemainder(dividingBy:Self.loop) : 0)
        return smooth((t-1.5)/1.8)*(1-smooth((t-4.7)/1.8))
    }
    var body:some View {
        GeometryReader { geo in
            let scale=min(geo.size.width/393,geo.size.height/760)
            TimelineView(.animation(paused:reduced || scenePhase != .active || previewTime != nil || (!autoPlay && manual == nil))) { timeline in
                let p=pose(timeline.date)
                ZStack(alignment:.topLeading) {
                    HStack { Image(systemName:"sun.max");Text("DAILY RECOVERY").tracking(2);Spacer();Text("TODAY") }.font(.system(size:10,weight:.medium)).frame(width:337).position(x:196.5,y:30)
                    HStack(spacing:6) { ForEach(0..<5,id:\.self) { i in Capsule().fill(cream.opacity(i<2 ? 0.65:0.14)).frame(width:i==1 ? 35:9,height:3) } }.position(x:196.5,y:79)
                    Text("Your energy,\ncoming into focus.").font(.system(size:32,weight:.regular,design:.serif)).multilineTextAlignment(.center).frame(width:345).position(x:196.5,y:151)
                    pearl(p,small:false).frame(width:156,height:156).position(x:196.5,y:330-8*sin(p*Double.pi))
                    Text("READY FOR YOUR DAY").font(.system(size:9,weight:.medium)).tracking(2.5).opacity(0.5).position(x:196.5,y:440)
                    arc(p).frame(width:353,height:126)
                        .contentShape(Rectangle())
                        .gesture(DragGesture(minimumDistance:1).updating($gestureActive) { _, active, _ in active = true }.onChanged { value in
                            if !touching { start=pose(.now);touching=true;crossed=false }
                            let next=min(1,max(0,start+value.translation.width/180))
                            if !crossed && (start-0.5)*(next-0.5)<=0 { UISelectionFeedbackGenerator().selectionChanged();crossed=true }
                            manual=next
                        }.onEnded { _ in touching=false;release = .now })
                        .accessibilityElement(children:.ignore).accessibilityLabel("Recovery balance")
                        .accessibilityValue(p < 0.33 ? "Lower balance, recovery index 82" : p > 0.67 ? "Higher balance, recovery index 82" : "Centered balance, recovery index 82")
                        .accessibilityAdjustableAction { direction in manual=direction == .increment ? 1:0;release = .now }
                        .position(x:196.5,y:530)
                    HStack(spacing:14) {
                        stat("moon", "REST", "7h 42m")
                        stat("leaf", "BALANCE", "Steady")
                    }.frame(width:337).position(x:196.5,y:645)
                    Text("Drag to find your balance.").font(.system(size:12)).opacity(0.55).position(x:196.5,y:718)
                    Text("A MOMENT FOR YOURSELF").font(.system(size:8,weight:.medium)).tracking(2).opacity(0.35).position(x:196.5,y:747)
                }.foregroundStyle(cream).frame(width:393,height:760,alignment:.topLeading).scaleEffect(scale).frame(width:geo.size.width,height:geo.size.height)
            }
        }.background(LinearGradient(colors:[Color(hex:0x1C5546),Color(hex:0x102B22),Color(hex:0x101D16)],startPoint:.topLeading,endPoint:.bottomTrailing).ignoresSafeArea()).preferredColorScheme(.dark)
        .task(id:release) {
            guard manual != nil,!touching else { return }
            do { try await Task.sleep(for:.seconds(2.45)) } catch { return }
            guard !touching else { return };manual=nil;epoch = .now
        }
        .onChange(of: gestureActive) { _, active in
            if !active && touching { touching = false; release = .now }
        }
        .onChange(of:scenePhase) { _,value in if value == .active { manual=nil;touching=false;epoch = .now } }
        .onChange(of:reduced) { _,_ in manual=nil;touching=false;epoch = .now }
    }
    private func stat(_ symbol:String,_ title:String,_ value:String)->some View {
        VStack(alignment:.leading,spacing:10) {
            HStack(spacing:6) { Image(systemName:symbol);Text(title).tracking(1.3) }.font(.system(size:9)).opacity(0.5)
            Text(value).font(.system(size:22,weight:.regular,design:.serif))
        }.frame(maxWidth:.infinity,alignment:.leading).padding(16).background(cream.opacity(0.035),in:RoundedRectangle(cornerRadius:20)).overlay(RoundedRectangle(cornerRadius:20).stroke(cream.opacity(0.08),lineWidth:1))
    }
    private func pearl(_ p:Double,small:Bool)->some View {
        GeometryReader { geo in
            let size=geo.size.width
            ZStack {
                Circle().fill(RadialGradient(stops:[.init(color:Color(hex:0x8ED9BA).opacity(0.1),location:0.2),.init(color:Color(hex:0x5C9F86).opacity(0.3),location:0.72),.init(color:Color(hex:0xC8EED0).opacity(0.7),location:0.97),.init(color:Color(hex:0x629D83).opacity(0.2),location:1)],center:UnitPoint(x:0.35+p*0.25,y:0.35),startRadius:0,endRadius:size*0.6))
                Canvas { c,bounds in
                    for i in 0..<6 {
                        let y=Double(i)*bounds.height/5
                        var path=Path();path.move(to:CGPoint(x:0,y:y))
                        path.addQuadCurve(to:CGPoint(x:bounds.width,y:y-12+p*24),control:CGPoint(x:bounds.width*0.5,y:y+(Double(i)-2.5)*15+p*18))
                        c.stroke(path,with:.color(Color(hex:0xA4E2B5).opacity(0.035)),lineWidth:8)
                    }
                }.clipShape(Circle())
                Circle().fill(RadialGradient(colors:[.white.opacity(0.4),.clear],center:.center,startRadius:0,endRadius:size*0.2)).frame(width:size*0.45,height:size*0.45).position(x:size*(0.26+p*0.35),y:size*0.23).blur(radius:size*0.025)
                Circle().fill(Color(hex:0xCBB6EF).opacity(0.08)).frame(width:size*0.34).blur(radius:size*0.12).offset(x:size*0.22,y:size*0.26)
                Circle().stroke(LinearGradient(colors:[cream.opacity(0.55),cream.opacity(0.02),Color(hex:0xA4E2B5).opacity(0.45)],startPoint:.topLeading,endPoint:.bottomTrailing),lineWidth:1)
                Text(small ? "+6":"82").font(.system(size:small ? 18:72,weight:.regular,design:.serif)).foregroundStyle(cream)
            }.frame(width:size,height:size).shadow(color:Color(hex:0xA4E2B5).opacity(0.1),radius:24)
        }.accessibilityElement(children:.ignore).accessibilityLabel(small ? "6 points above baseline":"Recovery index 82")
    }
    private func arc(_ p:Double)->some View {
        let u=0.35+0.3*p
        let x=15+323*u
        let y=75-160*u*(1-u)
        return ZStack(alignment:.topLeading) {
            Canvas { c,_ in
                var base=Path();base.move(to:CGPoint(x:15,y:75));base.addQuadCurve(to:CGPoint(x:338,y:75),control:CGPoint(x:176.5,y:-5))
                c.stroke(base,with:.color(cream.opacity(0.12)),style:StrokeStyle(lineWidth:7,lineCap:.round))
                var active=Path()
                for i in 0...50 {
                    let t=0.12+(u-0.12)*Double(i)/50
                    let pt=CGPoint(x:15+323*t,y:75-160*t*(1-t))
                    if i==0 { active.move(to:pt) } else { active.addLine(to:pt) }
                }
                c.stroke(active,with:.linearGradient(Gradient(colors:[Color(hex:0xA4E2B5),Color(hex:0xCBB6EF)]),startPoint:CGPoint(x:40,y:40),endPoint:CGPoint(x:x,y:y)),style:StrokeStyle(lineWidth:10,lineCap:.round))
                c.fill(Path(ellipseIn:CGRect(x:173,y:32,width:7,height:7)),with:.color(cream))
            }
            pearl(p,small:true).frame(width:46,height:46).position(x:x,y:y)
            Text("LOWER").font(.system(size:9)).tracking(1).opacity(0.4).rotationEffect(.degrees(-20)).position(x:37,y:99)
            Text("HIGHER").font(.system(size:9)).tracking(1).opacity(0.4).rotationEffect(.degrees(20)).position(x:315,y:99)
            Text("CURRENT").font(.system(size:9)).tracking(1.3).opacity(0.5).position(x:176.5,y:72)
        }
    }
}
