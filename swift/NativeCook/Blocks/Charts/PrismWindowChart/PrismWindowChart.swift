import SwiftUI
import UIKit

struct PrismWindowChart: View {
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
    private let ink = Color(hex: 0x272832)
    private let accent = Color(hex: 0x685BEE)
    private func ease(_ value: Double) -> Double { let u = min(1, max(0, value)); return u*u*(3-2*u) }
    private func pose(_ date: Date) -> Double {
        if reduced { return 0.5 }
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
                    Color(hex: 0xE9ECFF)
                    HStack {
                        Image(systemName: "waveform.path").font(.system(size: 15,weight: .medium))
                        Text("PRISM / OBSERVATORY").font(.system(size: 10,weight: .semibold)).tracking(1.8)
                        Spacer()
                        Text("NOVA").font(.system(size: 10,design: .monospaced)).opacity(0.55)
                    }.frame(width: 337).position(x:196.5,y:32)
                    Text("See the\nwhole picture.").font(.system(size:38,weight:.semibold,design: .default)).tracking(-1.2)
                        .multilineTextAlignment(.center).lineSpacing(-1).frame(width:350).position(x:196.5,y:118)
                    Text("A closer look at your daily index.").font(.system(size:13)).opacity(0.6).position(x:196.5,y:186)
                    artwork(p).frame(width:337,height:330).contentShape(Rectangle())
                        .gesture(DragGesture(minimumDistance:1).updating($gestureActive) { _,active,_ in active=true }.onChanged { value in
                            if !touching { start=pose(.now);touching=true;detent=false }
                            let next = min(1,max(0,start + value.translation.width / (180*scale)))
                            manual=next
                            if next > 0.85 && !detent { detent=true;UIImpactFeedbackGenerator(style:.light).impactOccurred() }
                        }.onEnded { _ in touching=false;release = .now })
                        .accessibilityElement(children:.ignore).accessibilityLabel("Prism Window Chart")
                        .accessibilityValue(p > 0.7 ? "Friday observation":(p < 0.3 ? "Monday observation":"Midweek observation"))
                        .accessibilityHint("Scrub the lens horizontally")
                        .accessibilityAdjustableAction { direction in manual=direction == .increment ? 1:0;release = .now }
                        .position(x:196.5,y:385)
                    detail.position(x:196.5,y:591)
                    Button { activate() } label: {
                        HStack(spacing:9) { Image(systemName: activated ? "checkmark":"arrow.up.right");Text(activated ? "Reset view" : "Reset view").fontWeight(.semibold) }
                            .font(.system(size:15)).frame(width:337,height:54)
                            .background(accent,in:RoundedRectangle(cornerRadius:18))
                            .foregroundStyle(Color.white)
                    }.buttonStyle(PrismWindowChartPressStyle(reduced: reduced)).position(x:196.5,y:680)
                    Text("SCRUB THE LENS HORIZONTALLY").font(.system(size:9,weight:.medium)).tracking(1.6).opacity(0.45).position(x:196.5,y:735)
                }.foregroundStyle(ink).frame(width:393,height:760,alignment:.topLeading)
                    .scaleEffect(scale).frame(width:geo.size.width,height:geo.size.height)
            }
        }.background(Color(hex:0xE9ECFF).ignoresSafeArea()).preferredColorScheme(.light)
        .task(id:release) {
            guard manual != nil,!touching else { return }
            do { try await Task.sleep(for:.seconds(2.45)) } catch { return }
            guard !touching else { return };manual=nil;epoch = .now
        }
        .onChange(of:gestureActive) { _,active in if !active && touching { touching=false;release = .now } }
        .onChange(of:scenePhase) { _,value in if value == .active { manual=nil;touching=false;epoch = .now } }
        .onChange(of:reduced) { _,_ in manual=nil;touching=false;epoch = .now }
    }
    private func activate() { selection=0; manual=0;release = .now;UISelectionFeedbackGenerator().selectionChanged() }
    private func label(_ text:String,at point:CGPoint,size:CGFloat,color:Color,context:GraphicsContext,weight:Font.Weight = .regular) {
        context.draw(Text(text).font(.system(size:size,weight:weight)).foregroundStyle(color),at:point)
    }
    
    private func sample(_ x:Double)->Double { 178-90*x+26*sin(2*Double.pi*x)+18*cos(6*Double.pi*x+Double(selection)*0.5) }
    private func line()->Path { Path { path in for i in 0...96 { let x=Double(i)/96;let point=CGPoint(x:24+x*289,y:sample(x));if i==0 { path.move(to:point) } else { path.addLine(to:point) } } } }
    private func artwork(_ p:Double) -> some View {
        Canvas { c,_ in
            let panel=Path(roundedRect:CGRect(x:0,y:15,width:337,height:275),cornerRadius:28)
            c.fill(panel,with:.linearGradient(Gradient(colors:[Color.white.opacity(0.85),Color(hex:0xCDD4FD).opacity(0.7)]),startPoint:.zero,endPoint:CGPoint(x:337,y:290)))
            c.stroke(panel,with:.color(.white),lineWidth:1)
            for i in 0..<4 { var grid=Path();grid.move(to:CGPoint(x:24,y:70+Double(i)*48));grid.addLine(to:CGPoint(x:313,y:70+Double(i)*48));c.stroke(grid,with:.color(accent.opacity(0.09)),lineWidth:1) }
            let chart=line();c.stroke(chart,with:.color(accent.opacity(0.45)),style:StrokeStyle(lineWidth:2,lineCap:.round,lineJoin:.round))
            let x=74+p*208,y=sample((x-24)/289),lens=Path(ellipseIn:CGRect(x:x-53,y:y-53,width:106,height:106))
            c.fill(lens,with:.linearGradient(Gradient(colors:[Color.white.opacity(0.85),Color(hex:0xDDD9FF).opacity(0.8)]),startPoint:CGPoint(x:x-53,y:y-53),endPoint:CGPoint(x:x+53,y:y+53)))
            var zoom=c;zoom.clip(to:lens);zoom.translateBy(x:x,y:y);zoom.scaleBy(x:1.35,y:1.35);zoom.translateBy(x:-x,y:-y)
            zoom.stroke(chart,with:.color(accent),style:StrokeStyle(lineWidth:2.5,lineCap:.round,lineJoin:.round))
            c.stroke(lens,with:.linearGradient(Gradient(colors:[Color.white,Color(hex:0x8CE0E2),accent.opacity(0.6),Color.white]),startPoint:CGPoint(x:x-53,y:y-53),endPoint:CGPoint(x:x+53,y:y+53)),lineWidth:3)
            c.stroke(Path(ellipseIn:CGRect(x:x-48,y:y-48,width:96,height:96)),with:.color(.white.opacity(0.75)),lineWidth:0.7)
            c.fill(Path(ellipseIn:CGRect(x:x-4,y:y-4,width:8,height:8)),with:.color(accent))
            var cursor=Path();cursor.move(to:CGPoint(x:x,y:y+56));cursor.addLine(to:CGPoint(x:x,y:247));c.stroke(cursor,with:.color(accent.opacity(0.4)),style:StrokeStyle(lineWidth:1,dash:[3,3]))
            label("OBSERVATION WINDOW",at:CGPoint(x:168.5,y:40),size:9,color:accent.opacity(0.65),context:c,weight:.medium)
            for (i,text) in ["MON","TUE","WED","THU","FRI"].enumerated() { label(text,at:CGPoint(x:35+Double(i)*66,y:263),size:8,color:ink.opacity(0.5),context:c) }
            label("128.6",at:CGPoint(x:168.5,y:318),size:24,color:ink,context:c,weight:.semibold)
        }
    }

    private var detail: some View { HStack(spacing:8) { ForEach(0..<3,id:\.self) { i in Button { selection=i;manual=0;release = .now;UISelectionFeedbackGenerator().selectionChanged() } label: { Text(["Week","Month","Year"][i]).font(.system(size:12,weight:.medium)).frame(width:104,height:46).background(selection==i ? accent:Color.white.opacity(0.6),in:RoundedRectangle(cornerRadius:15)).foregroundStyle(selection==i ? Color.white:ink) }.buttonStyle(.plain).accessibilityAddTraits(selection==i ? .isSelected:[]) } } }
}

private struct PrismWindowChartPressStyle: ButtonStyle {
    let reduced: Bool
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.scaleEffect(configuration.isPressed && !reduced ? 0.97:1)
            .opacity(configuration.isPressed ? 0.8:1)
            .animation(.easeOut(duration:0.13),value:configuration.isPressed)
    }
}
