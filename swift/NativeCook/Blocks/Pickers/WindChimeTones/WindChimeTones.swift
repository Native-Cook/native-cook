import SwiftUI

struct WindChimeTones: View {
    static let loop=WindChimeTonesMotion.loop
    var autoPlay=false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scene
    @StateObject private var clock=WindChimeTonesClock()
    private var pinned: Double? {
        #if DEBUG
        if let value=UserDefaults.standard.object(forKey:"windChimeTonesTime") {
            return (value as? NSNumber)?.doubleValue ?? Double("\(value)")
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { proxy in
            let safe=proxy.safeAreaInsets
            let l=WindChimeTonesLayout(proxy.size.width,proxy.size.height+safe.top+safe.bottom,safe.top,safe.bottom)
            TimelineView(.animation(paused:pinned != nil || scene != .active || (!(autoPlay && !reduced) && !clock.running))) { timeline in
                let frame=clock.sample(timeline.date,autoPlay && !reduced,reduced,pinned)
                screen(l,frame)
            }.frame(width:l.w,height:l.h,alignment:.topLeading).offset(y:-safe.top)
        }
        .background(WindChimeTonesResources.page.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear { clock.reset(autoPlay && !reduced) }
        .onDisappear { clock.active(false) }
        .onChange(of:scene) { _, phase in clock.active(phase == .active) }
        .onChange(of:reduced) { _, _ in clock.configure(autoPlay && !reduced) }
        .onChange(of:autoPlay) { _, _ in clock.configure(autoPlay && !reduced) }
    }
    private func screen(_ l: WindChimeTonesLayout, _ f: WindChimeTonesMotion.Frame) -> some View {
        let r=WindChimeTonesResources.self
        return ZStack(alignment:.topLeading) {
            WindChimeTonesMetal(layout:l,frame:f,reduced:reduced).frame(width:l.w,height:l.h).allowsHitTesting(false).accessibilityHidden(true)
            button("Back",r.back) {}.frame(width:44,height:44).offset(x:45*l.sx-22,y:l.y(103)-22)
            button("More",r.more) {}.frame(width:44,height:44).offset(x:356*l.sx-22,y:l.y(104)-22)
            Button { clock.togglePower() } label: {
                WindChimeTonesIcon(icon:r.power,tint:clock.isOn ? r.red : .white)
                    .frame(width:64,height:64)
                    .background(clock.isOn ? .white : r.color(0x5A5A5C),in:Circle())
                    .compositingGroup()
                    .shadow(color:.black.opacity(0.35),radius:12,x:0,y:6)
                    .contentShape(Circle())
            }.buttonStyle(WindChimeTonesPressStyle(reduced:reduced))
                .accessibilityLabel("Power").accessibilityValue(clock.isOn ? "On" : "Off")
                .accessibilityAddTraits(.isToggle)
                .modifier(WindChimeTonesRide(pose:f.ride(l.powerBody,reduced)))
                .offset(x:317*l.sx-32,y:l.y(292)-32)
            Text("Halden Audio").font(r.title).foregroundStyle(.white).fixedSize()
                .frame(width:l.w,alignment:.top).offset(y:l.y(90)-10+1/3).accessibilityAddTraits(.isHeader)
            Text("Speaker").font(r.title).foregroundStyle(.white).fixedSize()
                .frame(width:l.w,alignment:.top).offset(y:l.y(117)-10+1/3)
            ForEach(0..<3) { i in
                Button {} label: {
                    VStack(spacing:12) {
                        WindChimeTonesIcon(icon:[r.airPlay,r.eq,r.pair][i])
                        Text(r.tools[i]).font(r.toolText).foregroundStyle(.white).fixedSize().offset(y:1/3)
                    }.frame(width:94*l.sx,height:90*l.k).contentShape(Rectangle())
                }.buttonStyle(WindChimeTonesPressStyle(reduced:reduced))
                    .frame(width:94*l.sx,height:90*l.k)
                    .background(r.card,in:RoundedRectangle(cornerRadius:16))
                    .accessibilityLabel(r.tools[i])
                    .modifier(WindChimeTonesRide(pose:f.ride(l.toolBody(i),reduced)))
                    .offset(x:l.toolX(i),y:l.y(510))
            }
            player(l,f).modifier(WindChimeTonesRide(pose:f.ride(l.playerBody,reduced)))
                .offset(x:37*l.sx,y:l.y(625))
            Circle().fill(.white.opacity(0.1)).overlay(Circle().strokeBorder(.white.opacity(0.6),lineWidth:1.5))
                .frame(width:44,height:44).scaleEffect(1-0.12*f.press).opacity(f.finger)
                .offset(x:203*l.sx-22,y:l.y(776)-22).allowsHitTesting(false).accessibilityHidden(true)
        }.frame(width:l.w,height:l.h,alignment:.topLeading).clipped().allowsHitTesting(pinned == nil)
    }
    private func button(_ label: String, _ icon: WindChimeTonesResources.Icon, action: @escaping () -> Void) -> some View {
        Button(action:action) { WindChimeTonesIcon(icon:icon).frame(maxWidth:.infinity,maxHeight:.infinity).contentShape(Rectangle()) }
            .buttonStyle(WindChimeTonesPressStyle(reduced:reduced)).accessibilityLabel(label)
    }
    private func player(_ l: WindChimeTonesLayout, _ f: WindChimeTonesMotion.Frame) -> some View {
        let r=WindChimeTonesResources.self
        return ZStack(alignment:.topLeading) {
            RoundedRectangle(cornerRadius:20).fill(r.card)
            WindChimeTonesIcon(icon:r.speaker).offset(x:(83-37)*l.sx-15,y:l.y(676)-l.y(625)-15)
            Text("Now playing").font(r.title).foregroundStyle(.white).fixedSize()
                .offset(x:(128-37)*l.sx,y:l.y(664)-l.y(625)-10+1/3)
            Text("Harbour Chimes – Tones at Dusk").font(r.track).foregroundStyle(.white.opacity(0.32))
                .lineLimit(1).truncationMode(.tail).frame(width:200*l.sx,alignment:.leading)
                .offset(x:(128-37)*l.sx,y:l.y(694)-l.y(625)-9)
            Capsule().fill(.white.opacity(0.18)).frame(width:(338-63)*l.sx,height:2)
                .overlay(alignment:.leading) { Rectangle().fill(r.color(0xE6E6E8)).frame(width:(338-63)*l.sx*f.progress,height:2) }
                .clipShape(Capsule()).offset(x:(63-37)*l.sx,y:l.y(724)-l.y(625))
            ForEach(0..<3) { i in
                button(i == 0 ? "Previous" : i == 2 ? "Next" : f.playing ? "Pause" : "Play",i == 0 ? r.previous : i == 2 ? r.next : f.playing ? r.pause : r.play) {
                    if i == 1 { clock.togglePlay() }
                }.frame(width:48,height:48)
                    .offset(x:([108.0,203,290][i]-37)*l.sx-24,y:l.y(776)-l.y(625)-24)
            }
        }.frame(width:327*l.sx,height:195*l.k,alignment:.topLeading)
    }
}

/// RN's T · R · S matrix, centred on the card: scale locally, rotate, then translate in screen points.
private struct WindChimeTonesRide: ViewModifier {
    var pose: WindChimeTonesMotion.Pose
    func body(content: Content) -> some View {
        content.scaleEffect(x:pose.scaleX,y:pose.scaleY)
            .rotationEffect(.radians(pose.rotation))
            .offset(x:pose.x,y:pose.y)
    }
}

/// PressScale's 150 ms curve and 16 pt press-retention bounds.
struct WindChimeTonesPressStyle: PrimitiveButtonStyle {
    var reduced: Bool
    func makeBody(configuration: Configuration) -> some View {
        WindChimeTonesPressBody(configuration:configuration,reduced:reduced)
    }
}

struct WindChimeTonesPressBody: View {
    var configuration: PrimitiveButtonStyleConfiguration
    var reduced: Bool
    @GestureState private var down=false
    var body: some View {
        configuration.label
            .scaleEffect(down && !reduced ? 0.96 : 1)
            .opacity(down && reduced ? 0.72 : 1)
            .animation(.timingCurve(0.23,1,0.32,1,duration:0.15),value:down)
            .overlay {
                GeometryReader { proxy in
                    let retention=CGRect(origin:.zero,size:proxy.size).insetBy(dx:-16,dy:-16)
                    Color.clear.contentShape(Rectangle())
                        .gesture(DragGesture(minimumDistance:0)
                            .updating($down) { value,state,_ in state=retention.contains(value.location) }
                            .onEnded { value in
                                if retention.contains(value.location) { configuration.trigger() }
                            })
                }.accessibilityHidden(true)
            }
            .accessibilityAction { configuration.trigger() }
    }
}
