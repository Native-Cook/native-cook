import SwiftUI
import UIKit

struct RibbonResonancePlayer: View {
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
    @State private var playing = true
    @State private var pausedAt = 0.0
    @State private var seconds = 744
    private let cream = Color(hex:0xE8E2FF)
    private func smooth(_ x:Double)->Double { let u=min(1,max(0,x));return u*u*(3-2*u) }
    private func pose(_ date:Date)->Double {
        if reduced { return 0 }
        if let manual { return touching ? manual : manual*(1-smooth((date.timeIntervalSince(release)-2)/0.45)) }
        let t=previewTime ?? (autoPlay ? (playing ? max(0,date.timeIntervalSince(epoch)) : pausedAt).truncatingRemainder(dividingBy:Self.loop) : 0)
        return smooth((t-1.4)/1.8)*(1-smooth((t-4.6)/1.8))
    }
    var body:some View {
        GeometryReader { geo in
            let scale=min(geo.size.width/393,geo.size.height/760)
            TimelineView(.animation(paused:reduced || scenePhase != .active || previewTime != nil || ((!autoPlay || !playing) && manual == nil))) { timeline in
                let p=pose(timeline.date)
                ZStack(alignment:.topLeading) {
                    HStack { Image(systemName:"waveform");Text("RESONANCE / NOW PLAYING").tracking(1.6);Spacer();Image(systemName:"ellipsis") }
                        .font(.system(size:10,weight:.semibold)).frame(width:333).position(x:196.5,y:30)
                    strings(p).frame(width:360,height:360)
                        .contentShape(Rectangle())
                        .gesture(DragGesture(minimumDistance:1).updating($gestureActive) { _, active, _ in active = true }.onChanged { value in
                            if !touching { start=pose(.now);touching=true;UIImpactFeedbackGenerator(style:.light).impactOccurred() }
                            manual=min(1,max(0,start+value.translation.width/200))
                        }.onEnded { _ in touching=false;release = .now })
                        .accessibilityElement(children:.ignore).accessibilityHidden(false)
                        .accessibilityLabel("Resonance strings").accessibilityHint("Drag right to bend the strings")
                        .accessibilityAdjustableAction { direction in manual=direction == .increment ? 1:0;release = .now }
                        .position(x:196.5,y:260)
                    VStack(alignment:.leading,spacing:7) {
                        Text("Morrow Radio").font(.system(size:13)).opacity(0.55)
                        Text("The space between").font(.system(size:24,weight:.semibold))
                    }.frame(width:333,alignment:.leading).position(x:196.5,y:481)
                    VStack(spacing:10) {
                        GeometryReader { bar in
                            Capsule().fill(cream.opacity(0.13))
                            Capsule().fill(Color(hex:0xAEA1FC)).frame(width:bar.size.width*Double(seconds)/2280)
                            Circle().fill(cream).frame(width:10,height:10).offset(x:bar.size.width*Double(seconds)/2280-5,y:-3.5)
                        }.frame(height:3)
                        HStack { Text(String(format:"%d:%02d",seconds/60,seconds%60));Spacer();Text("38:00") }.font(.system(size:11,design:.monospaced)).opacity(0.55)
                    }.frame(width:333).position(x:196.5,y:550)
                    HStack(spacing:26) {
                        Button { seconds=max(0,seconds-15);interrupt() } label: { Image(systemName:"gobackward.15").font(.system(size:25)).frame(width:54,height:62) }.accessibilityLabel("Back 15 seconds")
                        Button(action:togglePlay) { Image(systemName:playing ? "pause.fill":"play.fill").font(.system(size:24)).foregroundStyle(Color(hex:0x231858)).frame(width:112,height:62).background(Color(hex:0xAEA1FC),in:Capsule()) }.accessibilityLabel(playing ? "Pause":"Play")
                        Button { seconds=min(2280,seconds+15);interrupt() } label: { Image(systemName:"goforward.15").font(.system(size:25)).frame(width:54,height:62) }.accessibilityLabel("Forward 15 seconds")
                    }.buttonStyle(.plain).position(x:196.5,y:630)
                    Text("DRAG TO FEEL THE RESONANCE").font(.system(size:9,weight:.medium)).tracking(1.8).opacity(0.5).position(x:196.5,y:714)
                    Capsule().fill(cream.opacity(0.3)).frame(width:34,height:3).position(x:196.5,y:746)
                }.foregroundStyle(cream).frame(width:393,height:760,alignment:.topLeading).scaleEffect(scale).frame(width:geo.size.width,height:geo.size.height)
            }
        }.background(LinearGradient(colors:[Color(hex:0x160D3B),Color(hex:0x231858),Color(hex:0x29205F)],startPoint:.top,endPoint:.bottom).ignoresSafeArea()).preferredColorScheme(.dark)
        .task(id:release) {
            guard manual != nil,!touching else { return }
            do { try await Task.sleep(for:.seconds(2.45)) } catch { return }
            guard !touching else { return };manual=nil;epoch = .now;pausedAt=0
        }
        .onChange(of: gestureActive) { _, active in
            if !active && touching { touching = false; release = .now }
        }
        .onChange(of:scenePhase) { _,value in if value == .active { manual=nil;touching=false;epoch = .now;pausedAt=0 } }
        .onChange(of:reduced) { _,_ in manual=nil;touching=false;epoch = .now;pausedAt=0 }
    }
    private func interrupt() { manual=pose(.now);release = .now;UISelectionFeedbackGenerator().selectionChanged() }
    private func togglePlay() {
        if playing { pausedAt=Date().timeIntervalSince(epoch) } else { epoch=Date().addingTimeInterval(-pausedAt) }
        playing.toggle();UIImpactFeedbackGenerator(style:.light).impactOccurred()
    }
    private func strings(_ p:Double)->some View {
        ZStack {
            Circle().fill(RadialGradient(colors:[Color(hex:0x2DE4CB),Color(hex:0x0C9AAB).opacity(0.65),Color(hex:0x3D4DB9).opacity(0.25),.clear],center:.center,startRadius:30,endRadius:170)).blur(radius:12)
            Canvas { c,size in
                let center=CGPoint(x:size.width/2,y:size.height/2)
                for i in 0..<64 {
                    let a=Double(i)*2*Double.pi/64
                    let angle=a+p*0.32*sin(a)
                    let radius=148+p*18*sin(3*a)
                    let end=CGPoint(x:center.x+cos(angle)*radius,y:center.y+sin(angle)*radius)
                    let squareScale=63/max(abs(cos(a)),abs(sin(a)))
                    let begin=CGPoint(x:center.x+cos(a)*squareScale,y:center.y+sin(a)*squareScale)
                    let mid=CGPoint(x:(begin.x+end.x)/2-p*36*sin(2*a)*sin(a),y:(begin.y+end.y)/2+p*36*sin(2*a)*cos(a))
                    var path=Path();path.move(to:begin);path.addQuadCurve(to:end,control:mid)
                    c.stroke(path,with:.color(Color(hex:0x061E3A).opacity(0.5)),lineWidth:0.8)
                    c.fill(Path(ellipseIn:CGRect(x:end.x-2,y:end.y-2,width:4,height:4)),with:.color(Color(hex:0x130E37)))
                }
            }
            ZStack {
                Rectangle().fill(LinearGradient(colors:[Color(hex:0x140D37),Color(hex:0x241358)],startPoint:.topLeading,endPoint:.bottomTrailing))
                Canvas { c,size in
                    for row in 0..<9 {
                        var path=Path()
                        for j in 0...60 {
                            let x=Double(j)*1.4+21
                            let envelope=pow(sin(Double(j)/60*Double.pi),2)
                            let y=Double(row)*4+47+sin(Double(j)*0.36+Double(row)*0.32)*envelope*(6+4*p)
                            if j==0 { path.move(to:CGPoint(x:x,y:y)) } else { path.addLine(to:CGPoint(x:x,y:y)) }
                        }
                        c.stroke(path,with:.color(Color(hex:0x856DF4).opacity(0.6)),lineWidth:1)
                    }
                }
            }.frame(width:126,height:126).overlay(Rectangle().stroke(Color(hex:0x7566C0).opacity(0.25),lineWidth:1)).rotationEffect(.degrees(p*12)).shadow(color:.black.opacity(0.3),radius:16,y:12)
        }.accessibilityHidden(true)
    }
}
