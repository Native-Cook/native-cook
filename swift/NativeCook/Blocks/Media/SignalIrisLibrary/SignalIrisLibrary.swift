import SwiftUI
import UIKit

struct SignalIrisLibrary: View {
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
    @State private var selected = 0
    @State private var playing = true
    private let cream=Color(hex:0xFFE7BD)
    private func smooth(_ x:Double)->Double { let u=min(1,max(0,x));return u*u*(3-2*u) }
    private func pose(_ date:Date)->Double {
        if reduced { return 1 }
        if let manual { return touching ? manual : manual*(1-smooth((date.timeIntervalSince(release)-2)/0.45)) }
        let t=previewTime ?? (autoPlay ? max(0,date.timeIntervalSince(epoch)).truncatingRemainder(dividingBy:Self.loop) : 0)
        return smooth((t-1.4)/1.8)*(1-smooth((t-4.7)/1.8))
    }
    var body:some View {
        GeometryReader { geo in
            let scale=min(geo.size.width/393,geo.size.height/760)
            TimelineView(.animation(paused:reduced || scenePhase != .active || previewTime != nil || (!autoPlay && manual == nil))) { timeline in
                let p=pose(timeline.date)
                ZStack(alignment:.topLeading) {
                    Color(hex:0x571D14)
                    Color(hex:0xF64624).frame(height:395)
                    HStack { Image(systemName:"book.closed");Text("SIGNAL / LIBRARY").tracking(1.8);Spacer();Text("02") }.font(.system(size:10,weight:.semibold)).frame(width:337).position(x:196.5,y:30)
                    cover(p).frame(width:393,height:330).offset(y:65)
                        .contentShape(Rectangle())
                        .gesture(DragGesture(minimumDistance:1).updating($gestureActive) { _, active, _ in active = true }.onChanged { value in
                            if !touching { start=pose(.now);touching=true;UIImpactFeedbackGenerator(style:.light).impactOccurred() }
                            manual=min(1,max(0,start+value.translation.height/180))
                        }.onEnded { _ in touching=false;release = .now })
                        .accessibilityElement(children:.ignore).accessibilityLabel("New ways to notice, illustrated cover")
                        .accessibilityHint("Pull down to open the listening window")
                        .accessibilityAdjustableAction { direction in manual=direction == .increment ? 1:0;release = .now }
                    VStack(alignment:.leading,spacing:6) {
                        Text("New ways to notice").font(.system(size:22,weight:.semibold))
                        Text("A field guide for curious ears.").font(.system(size:13)).opacity(0.55)
                    }.frame(width:337,alignment:.leading).position(x:196.5,y:436)
                    episode(0,"01","The quiet art of looking","18 min",0xE8BB87).position(x:196.5,y:520)
                    episode(1,"02","Good ideas take the long way","26 min",0xE67E69).position(x:196.5,y:609)
                    HStack(spacing:12) {
                        thumbnail(selected).frame(width:36,height:36).clipShape(RoundedRectangle(cornerRadius:8))
                        VStack(alignment:.leading,spacing:3) {
                            Text(selected==0 ? "The quiet art of looking":"Good ideas take the long way").font(.system(size:11,weight:.semibold)).lineLimit(1)
                            Text(playing ? "Now playing · Signal Radio":"Paused · Signal Radio").font(.system(size:9)).opacity(0.55)
                        }
                        Spacer(minLength:0)
                        Button { playing.toggle();interrupt() } label: { Image(systemName:playing ? "pause.fill":"play.fill").font(.system(size:17)).frame(width:44,height:44) }.buttonStyle(.plain).accessibilityLabel(playing ? "Pause":"Play")
                    }.padding(.horizontal,12).frame(width:349,height:58).background(cream.opacity(0.12),in:RoundedRectangle(cornerRadius:22)).overlay(RoundedRectangle(cornerRadius:22).stroke(cream.opacity(0.1),lineWidth:1)).position(x:196.5,y:701)
                    Text("PULL THE COVER TO LOOK CLOSER").font(.system(size:8,weight:.semibold)).tracking(1.8).opacity(0.4).position(x:196.5,y:746)
                }.foregroundStyle(cream).frame(width:393,height:760,alignment:.topLeading).scaleEffect(scale).frame(width:geo.size.width,height:geo.size.height)
            }
        }.background(LinearGradient(stops:[.init(color:Color(hex:0xF64624),location:0),.init(color:Color(hex:0xF64624),location:0.5),.init(color:Color(hex:0x571D14),location:0.5)],startPoint:.top,endPoint:.bottom).ignoresSafeArea()).preferredColorScheme(.dark)
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
    private func interrupt() { manual=pose(.now);release = .now;UISelectionFeedbackGenerator().selectionChanged() }
    private func episode(_ id:Int,_ number:String,_ title:String,_ duration:String,_ tint:UInt32)->some View {
        Button { selected=id;playing=true;interrupt() } label: {
            HStack(spacing:14) {
                VStack(alignment:.leading,spacing:7) {
                    Text("EPISODE \(number)").font(.system(size:8,weight:.medium)).tracking(1.5).opacity(0.5)
                    Text(title).font(.system(size:15,weight:.medium)).multilineTextAlignment(.leading).frame(maxWidth:.infinity,alignment:.leading)
                    HStack(spacing:5) { Image(systemName:selected==id && playing ? "waveform":"play.fill");Text(duration) }.font(.system(size:9)).opacity(0.6)
                }
                thumbnail(id).frame(width:64,height:64).clipShape(RoundedRectangle(cornerRadius:8))
            }.frame(width:337,height:84).overlay(alignment:.bottom) { Rectangle().fill(cream.opacity(0.1)).frame(height:1) }
        }.buttonStyle(.plain).accessibilityLabel("Play \(title), \(duration)").accessibilityAddTraits(selected==id ? .isSelected:[])
    }
    private func thumbnail(_ id:Int)->some View {
        GeometryReader { geo in
            ZStack {
                Color(hex:id==0 ? 0xE8BB87:0xE67E69)
                Circle().stroke(Color(hex:0x571D14),lineWidth:geo.size.width*0.035).frame(width:geo.size.width*0.85).offset(x:-geo.size.width*0.12,y:geo.size.height*0.17)
                Rectangle().fill(Color(hex:0x571D14)).frame(width:geo.size.width*0.025).rotationEffect(.degrees(id==0 ? 0:35)).offset(x:geo.size.width*0.2)
                Circle().fill(Color(hex:0x571D14)).frame(width:geo.size.width*0.2).offset(x:-geo.size.width*0.2,y:-geo.size.height*0.19)
            }
        }.accessibilityHidden(true)
    }
    private func cover(_ p:Double)->some View {
        ZStack(alignment:.topLeading) {
            Canvas { c,_ in
                var rules=Path();rules.move(to:CGPoint(x:330,y:0));rules.addLine(to:CGPoint(x:330,y:330));rules.move(to:CGPoint(x:0,y:80));rules.addLine(to:CGPoint(x:330,y:80))
                c.stroke(rules,with:.color(cream.opacity(0.75)),lineWidth:3)
                c.stroke(Path(ellipseIn:CGRect(x:-57,y:80,width:350,height:350)),with:.color(cream.opacity(0.75)),lineWidth:3)
            }
            ZStack {
                Color(hex:0xE7B76A)
                ForEach(0..<12,id:\.self) { i in
                    Rectangle().fill(Color(hex:i%2==0 ? 0x6A2217:0xF64624)).frame(width:22,height:300).rotationEffect(.degrees(Double(i)*30+p*25))
                }
                Circle().fill(Color(hex:0x21130F)).frame(width:102,height:102)
                Text("LISTEN\nCLOSER").font(.system(size:14,weight:.bold)).tracking(1.5).multilineTextAlignment(.center)
            }.frame(width:300,height:300).clipShape(Circle())
                .mask(Circle().frame(width:300*p,height:300*p))
                .overlay(Circle().stroke(cream.opacity(p*0.8),lineWidth:2).frame(width:300*p,height:300*p))
                .position(x:196.5,y:160)
            Text("New ways\nto notice").font(.system(size:48,weight:.bold)).tracking(-2).foregroundStyle(Color(hex:0x21130F))
                .offset(x:27,y:204+p*18).opacity(1-p)
            Capsule().fill(cream.opacity(0.6)).frame(width:32,height:3).position(x:196.5,y:319)
        }.clipped()
    }
}
