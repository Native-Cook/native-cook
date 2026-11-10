import SwiftUI

struct SnowGlobeGift: View {
    static let loop = SnowGlobeGiftMotion.loop
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.displayScale) private var displayScale
    @StateObject private var clock = SnowGlobeGiftClock()
    private let resources = SnowGlobeGiftResources.shared
    private var pinned: Double? {
        #if DEBUG
        if let value = UserDefaults.standard.object(forKey:"snowGlobeGiftTime") {
            return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { safe in
            let insets = safe.safeAreaInsets
            GeometryReader { full in
                let l = SnowGlobeGiftLayout(full.size.width,full.size.height,insets.top,insets.bottom)
                TimelineView(.animation(paused:pinned != nil || scenePhase != .active || (!(autoPlay && !reduced) && !clock.running))) { timeline in
                    let f = clock.sample(timeline.date,autoPlay,reduced,pinned)
                    ZStack(alignment:.topLeading) {
                        SnowGlobeGiftRenderer(layout:l,frame:f,scale:displayScale)
                        Canvas { context, _ in
                            resources.draw("from",context,l.w/2,l.fromTop)
                            resources.draw("title",context,l.w/2,l.titleTop)
                            resources.draw("note",context,l.w/2,l.noteTop)
                            if f.amount > 0 {
                                var amount = context
                                amount.opacity = f.amount
                                amount.translateBy(x:l.w/2,y:l.amountY+(1-f.amount)*18)
                                amount.scaleBy(x:0.9+0.1*f.amount,y:0.9+0.1*f.amount)
                                let noteH = resources.labels["amountNote"]?.size.height ?? 14
                                let top = -(60+noteH)/2
                                resources.draw("amount",amount,0,top)
                                resources.draw("amountNote",amount,0,top+60)
                            }
                        }.allowsHitTesting(false).accessibilityHidden(true)
                        SnowGlobeGiftTouchSurface(clock:clock,radius:l.heroS)
                            .frame(width:l.heroS*2.2,height:l.heroS*2.45)
                            .position(x:l.heroX,y:l.heroY-l.heroS*1.05+l.heroS*2.45/2)
                            .accessibilityElement(children:.ignore)
                            .accessibilityAddTraits(.isButton)
                            .accessibilityLabel("A snow globe with a gift inside")
                            .accessibilityHint("Shake it to stir the snow")
                            .accessibilityAction { if pinned == nil { clock.accessibilityShake() } }
                            .allowsHitTesting(pinned == nil)
                        Button {} label: {
                            SnowGlobeGiftBackShape().stroke(Color(uiColor:SnowGlobeGiftResources.color(0x2A1D1C)),style:StrokeStyle(lineWidth:2.2,lineCap:.round,lineJoin:.round))
                                .frame(width:12,height:20).frame(width:44,height:44)
                                .contentShape(Rectangle().inset(by:-4))
                        }
                        .buttonStyle(SnowGlobeGiftPressStyle(reduced:reduced))
                        .position(x:30,y:l.barTop+22).accessibilityLabel("Back")
                        Button { if pinned == nil { clock.cta() } } label: {
                            Canvas { ctx, _ in
                                let key = (pinned != nil ? f.lid > 0.5 : clock.opened) ? (clock.added ? "added" : "add") : "open"
                                let h = resources.labels[key]?.size.height ?? 21
                                resources.draw(key,ctx,(l.w-32)/2,(56-h)/2)
                            }
                            .frame(width:l.w-32,height:56)
                            .background(Color(uiColor:SnowGlobeGiftResources.color(0x2A1D1C)),in:RoundedRectangle(cornerRadius:28,style:.circular))
                        }
                        .buttonStyle(SnowGlobeGiftPressStyle(reduced:reduced))
                        .position(x:l.w/2,y:l.ctaTop+28)
                        .accessibilityLabel((pinned != nil ? f.lid > 0.5 : clock.opened) ? (clock.added ? "Added to wallet" : "Add $50 to Wallet") : "Open Gift")
                        .allowsHitTesting(pinned == nil)
                        Canvas { ctx, _ in
                            if f.finger > 0 {
                                let x = f.fingerAt == 0 ? l.heroX+f.fingerX*l.heroS : l.w/2
                                let y = f.fingerAt == 0 ? l.heroY+0.1*l.heroS : l.ctaTop+28
                                let r = 22*(1-0.12*f.fingerPress)
                                let path = Path(ellipseIn:CGRect(x:x-r,y:y-r,width:r*2,height:r*2))
                                let ink = Color(uiColor:SnowGlobeGiftResources.color(0x2A1D1C))
                                ctx.fill(path,with:.color(ink.opacity(0.1*f.finger)))
                                ctx.stroke(path,with:.color(ink.opacity(0.32*f.finger)),lineWidth:1.5)
                            }
                        }.allowsHitTesting(false).accessibilityHidden(true)
                        // Keep static Canvas art discoverable by VoiceOver in reading order.
                        Color.clear.frame(width:1,height:1).position(x:l.w/2,y:l.fromTop)
                            .accessibilityLabel("From Mira")
                        Color.clear.frame(width:1,height:1).position(x:l.w/2,y:l.titleTop)
                            .accessibilityLabel("A gift for you").accessibilityAddTraits(.isHeader)
                        Color.clear.frame(width:1,height:1).position(x:l.w/2,y:l.noteTop)
                            .accessibilityLabel("“Happy birthday! The coffee is on me.”")
                        if pinned != nil ? f.lid > 0.5 : clock.opened {
                            Color.clear.frame(width:1,height:1).position(x:l.w/2,y:l.amountY)
                                .accessibilityLabel("$50. Kinto gift balance")
                        }
                    }
                    .frame(width:l.w,height:l.h,alignment:.topLeading).clipped()
                }
            }.ignoresSafeArea()
        }
        .background(Color(uiColor:SnowGlobeGiftResources.color(0xF8F1EF)))
        .preferredColorScheme(.light)
        .onAppear { clock.reset(autoPlay,reduced) }
        .onChange(of:scenePhase) { _, _ in clock.active() }
        .onChange(of:reduced) { _, value in clock.configure(autoPlay,value) }
        .onChange(of:autoPlay) { _, _ in clock.configure(autoPlay,reduced) }
    }
}

struct SnowGlobeGiftBackShape: Shape {
    private static let shape: Path = {
        var p = Path(); p.move(to:CGPoint(x:10,y:2)); p.addLine(to:CGPoint(x:2,y:10)); p.addLine(to:CGPoint(x:10,y:18)); return p
    }()
    func path(in rect: CGRect) -> Path {
        Self.shape
    }
}
struct SnowGlobeGiftPressStyle: ButtonStyle {
    var reduced: Bool
    func makeBody(configuration:Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed && !reduced ? 0.96 : 1)
            .opacity(configuration.isPressed && reduced ? 0.72 : 1)
            .animation(.timingCurve(0.23,1,0.32,1,duration:0.15),value:configuration.isPressed)
    }
}
