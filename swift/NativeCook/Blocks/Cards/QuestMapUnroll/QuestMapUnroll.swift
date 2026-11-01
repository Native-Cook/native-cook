import SwiftUI
import UIKit

struct QuestMapUnroll: View {
    static let loop = QuestMapUnrollMotion.loop
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var clock = QuestMapUnrollClock()
    private var pinned: Double? {
        #if DEBUG
        if let v = UserDefaults.standard.object(forKey:"questMapUnrollTime") {
            return (v as? NSNumber)?.doubleValue ?? Double("\(v)") ?? 0
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { proxy in
            let window = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows).first { $0.isKeyWindow }
            let safe = window?.safeAreaInsets ?? UIEdgeInsets(top:proxy.safeAreaInsets.top,left:0,bottom:proxy.safeAreaInsets.bottom,right:0)
            let g = QuestMapUnrollGeometry(proxy.size,safe)
            TimelineView(.animation(paused:pinned != nil || scenePhase != .active || (!(autoPlay && !reduced) && !clock.running))) { timeline in
                let frame = clock.sample(timeline.date,autoPlay && !reduced,reduced,pinned)
                Canvas(opaque:false,colorMode:.nonLinear,rendersAsynchronously:false) { context,_ in
                    QuestMapUnrollArt(g:g).draw(context,frame,reduced,pressed:clock.pressed)
                }
                .overlay(alignment:.topLeading) {
                    accessibilityAndTouch(g,frame).allowsHitTesting(pinned == nil)
                }
            }
            .frame(width:proxy.size.width,height:proxy.size.height,alignment:.topLeading)
        }
        .background(Color(uiColor:QuestMapUnrollResources.color(0x7A2E4A)))
        .ignoresSafeArea()
        .preferredColorScheme(.dark)
        .onChange(of:scenePhase) { _,_ in clock.resetDate() }
        .onChange(of:reduced) { _,_ in clock.wake() }
        .onChange(of:autoPlay) { _,_ in clock.wake() }
    }
    @ViewBuilder private func accessibilityAndTouch(_ g: QuestMapUnrollGeometry, _ f: QuestMapUnrollMotion.Frame) -> some View {
        ZStack(alignment:.topLeading) {
            if pinned == nil {
                QuestMapUnrollTouchView(pan:{ state,translation,velocity in
                    clock.pan(state,translation,velocity,g)
                },tap:{ p in clock.tap(CGPoint(x:p.x+g.rect.minX,y:p.y+g.rect.minY-20),g) })
                .frame(width:g.rect.width,height:g.rect.height+40)
                .offset(x:g.rect.minX,y:g.rect.minY-20)
            }
            if f.card > 0.001 {
                let i = max(0,min(5,f.level))
                let stars = QuestMapUnrollMotion.stars[i]
                Color.clear.frame(width:g.size.width-40,height:76)
                    .offset(x:20,y:g.size.height-g.bottom-172+(1-f.card)*30)
                    .accessibilityElement()
                    .accessibilityLabel("\(i+1). \(QuestMapUnrollMotion.names[i]). " + (stars > 0 ? "\(stars) of 3 stars" : "New level · 4 spells to learn"))
                    .allowsHitTesting(false)
            }
            transparentButton("Back",CGRect(x:16,y:g.top+18,width:44,height:44))
            transparentButton("Settings",CGRect(x:g.size.width-60,y:g.top+18,width:44,height:44))
            Color.clear.frame(width:210,height:44).offset(x:(g.size.width-210)/2,y:g.top+18)
                .accessibilityElement().accessibilityLabel("Magic power 334").allowsHitTesting(false)
            let labels = ["Daily puzzle","Gift bag","Free hints","Hint shop"]
            ForEach(0..<4,id:\.self) { i in
                Color.clear.frame(width:82,height:QuestMapUnrollControlArt.get(g.size.width).shopHeight)
                    .contentShape(Rectangle())
                    .gesture(DragGesture(minimumDistance:0).onChanged { _ in clock.pressed = i }.onEnded { _ in clock.pressed = -1 })
                    .offset(x:16+Double(i)*(g.size.width-114)/3,y:g.size.height-g.bottom-14-QuestMapUnrollControlArt.get(g.size.width).shopHeight)
                    .accessibilityLabel(labels[i]).accessibilityAddTraits(.isButton)
            }
        }.frame(width:g.size.width,height:g.size.height,alignment:.topLeading)
    }
    private func transparentButton(_ label: String, _ rect: CGRect) -> some View {
        Button(action:{}) { Color.clear.contentShape(Rectangle()) }
            .frame(width:rect.width,height:rect.height).offset(x:rect.minX,y:rect.minY)
            .accessibilityLabel(label)
    }
}
