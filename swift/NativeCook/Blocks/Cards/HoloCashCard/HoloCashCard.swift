import SwiftUI

struct HoloCashCard: View {
    static let loop = HoloCashCardMotion.loop
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var clock = HoloCashCardClock()
    private var pinned: Double? {
        #if DEBUG
        if let value=UserDefaults.standard.object(forKey:"holoCashCardTime") {
            return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { proxy in
            let l=HoloCashCardLayout(width:proxy.size.width,
                height:proxy.size.height+proxy.safeAreaInsets.top+proxy.safeAreaInsets.bottom,
                topInset:proxy.safeAreaInsets.top,bottomInset:proxy.safeAreaInsets.bottom)
            let _ = HoloCashCardResources.shared
            TimelineView(.animation(paused:pinned != nil || scenePhase != .active)) { timeline in
                let s=clock.sample(timeline.date,autoPlay,reduced,pinned)
                ZStack(alignment:.topLeading) {
                    HoloCashCardResources.color(0xeeeeef)
                    HoloCashCardBar(l:l,hidden:s.hideTarget==1,hide:clock.toggleHidden)
                    HoloCashCardBalance(l:l,s:s,boost:clock.toggleBoost)
                    // The original Picture is clipped to M=26 around the untransformed card.
                    ZStack(alignment:.topLeading) {
                        RoundedRectangle(cornerRadius:16)
                            .fill(.black.opacity(0.26)).frame(width:l.cw-16,height:l.ch-10)
                            .blur(radius:12)
                            .offset(x:34-s.ry*0.5,y:38+s.rx*0.5)
                        HoloCashCardFace(l:l,s:s).offset(x:26,y:26)
                    }
                    .frame(width:l.cw+52,height:l.ch+52,alignment:.topLeading).clipped()
                    .offset(x:-8,y:l.cardY-26).accessibilityHidden(true)
                    if pinned == nil {
                        HoloCashCardTouchView(event:clock.cardTouch)
                            .frame(width:l.cw+52,height:l.ch+52).offset(x:-8,y:l.cardY-26)
                            .accessibilityElement().accessibilityAddTraits(.isImage).accessibilityLabel("LOUDCASH debit card")
                    }
                    HoloCashCardActionRow(l:l,add: { if pinned == nil { clock.addFifty() } })
                    HoloCashCardLoyalty(l:l)
                    HoloCashCardTabs(l:l)
                }
                .frame(width:l.width,height:l.height,alignment:.topLeading)
                .allowsHitTesting(pinned == nil)
            }
            .frame(width:l.width,height:l.height,alignment:.topLeading)
            .offset(y:-proxy.safeAreaInsets.top)
        }
        .background(HoloCashCardResources.color(0xeeeeef).ignoresSafeArea())
        .preferredColorScheme(.light)
        .onAppear { clock.active(scenePhase == .active,reduced,pinned != nil) }
        .onDisappear { clock.active(false,reduced,pinned != nil) }
        .onChange(of:scenePhase) { _,p in clock.active(p == .active,reduced,pinned != nil) }
        .onChange(of:reduced) { _,r in clock.active(scenePhase == .active,r,pinned != nil) }
    }
}

struct HoloCashCardActionRow: View {
    let l:HoloCashCardLayout
    let add:()->Void
    var body: some View {
        HStack(spacing:10) {
            action("down","Add Balance","Add 50 dollars",add)
            action("up","Withdraw","Withdraw",{})
        }
        .frame(width:l.cw,height:50).offset(x:18,y:l.actionsY)
    }
    private func action(_ icon:String,_ text:String,_ label:String,_ tap:@escaping ()->Void)->some View {
        Button(action:tap) {
            HStack(spacing:8) {
                Canvas { c,_ in HoloCashCardResources.shared.drawIcon(c,icon,0,0,18) }.frame(width:18,height:18)
                Text(text).font(.custom("Inter24pt-Bold",fixedSize:15)).foregroundStyle(HoloCashCardResources.color(0x111111)).offset(y:-1.0/3)
            }
            .frame(maxWidth:.infinity).frame(height:50).background(.white,in:RoundedRectangle(cornerRadius:14))
        }.buttonStyle(HoloCashCardPressStyle()).accessibilityLabel(label)
    }
}
struct HoloCashCardPressStyle: ButtonStyle {
    func makeBody(configuration:Configuration)->some View {
        configuration.label.scaleEffect(configuration.isPressed ? 0.97:1)
    }
}
