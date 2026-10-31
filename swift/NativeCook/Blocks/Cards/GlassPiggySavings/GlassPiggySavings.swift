import SwiftUI
import UIKit

struct GlassPiggySavings: View {
    static let loop=GlassPiggySavingsMotion.loop
    var autoPlay=false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.displayScale) private var displayScale
    @StateObject private var clock=GlassPiggySavingsClock()
    private let resources=GlassPiggySavingsResources.shared
    private var pinned: Double? {
        #if DEBUG
        if let value=UserDefaults.standard.object(forKey:"glassPiggySavingsTime") {
            return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { safe in
            GeometryReader { proxy in
                let l=GlassPiggySavingsMotion.Layout(w:proxy.size.width,h:proxy.size.height,top:safe.safeAreaInsets.top,bottom:safe.safeAreaInsets.bottom)
                TimelineView(.animation(paused:pinned != nil || scenePhase != .active || reduced || (!autoPlay && !clock.running))) { timeline in
                    let _=clock.revision
                    let f=clock.sample(timeline.date,autoPlay && !reduced,reduced,pinned)
                    ZStack(alignment:.topLeading) {
                        GlassPiggySavingsTheme.background
                        GlassPiggySavingsArt(frame:f,layout:l)
                        if pinned == nil {
                            GlassPiggySavingsTouchView { state,p in clock.touch(state,p,l,reduced) }
                        }
                        head(f,l)
                        let k=f.floatK
                        text("+$\(f.plus)","Black",22,GlassPiggySavingsTheme.color(0xffd25c))
                            .frame(width:80).position(x:l.slotX,y:l.slotY-50+resources.height("Black",22)/2)
                            .offset(y:k>=0 ? -GlassPiggySavingsMotion.easeOut(k)*60 : 0)
                            .opacity(k<0 ? 0 : sin(min(k*1.4,1)*Double.pi))
                            .allowsHitTesting(false).accessibilityHidden(true)
                        HStack(spacing:12) {
                            ForEach(0..<3,id:\.self) { i in chip(i,f,l) }
                        }
                        .position(x:l.w/2,y:l.chipY)
                        systemText("Drag a coin into the piggy",13,.white.opacity(0.45))
                            .frame(width:l.w).position(x:l.w/2,y:l.chipY+34+systemHeight(13)/2)
                            .allowsHitTesting(false)
                        receipt(f,l)
                    }
                    .frame(width:l.w,height:l.h,alignment:.topLeading)
                    .clipped()
                }
            }.ignoresSafeArea()
        }
        .background(GlassPiggySavingsTheme.background)
        .preferredColorScheme(.dark)
        .onAppear { clock.configure(scenePhase == .active,reduced) }
        .onDisappear { clock.configure(false,reduced) }
        .onChange(of:scenePhase) { _,phase in clock.configure(phase == .active,reduced) }
        .onChange(of:reduced) { _,value in clock.configure(scenePhase == .active,value) }
    }
    private func text(_ s: String, _ weight: String, _ size: Double, _ color: Color) -> some View {
        Text(s).font(resources.font(weight,size)).foregroundStyle(color)
            .fixedSize(horizontal:true,vertical:true)
            .frame(height:resources.height(weight,size))
    }
    // These source labels render with the system fallback in the RN capture.
    private func systemHeight(_ size: Double) -> Double { UIFont.systemFont(ofSize:size).lineHeight }
    private func systemText(_ s: String, _ size: Double, _ color: Color) -> some View {
        Text(s).font(.system(size:size)).foregroundStyle(color)
            .fixedSize(horizontal:true,vertical:true).frame(height:systemHeight(size))
    }
    private func head(_ f: GlassPiggySavingsMotion.Frame, _ l: GlassPiggySavingsMotion.Layout) -> some View {
        VStack(alignment:.leading,spacing:0) {
            text("LISBON TRIP · SAVED SO FAR","Black",12,GlassPiggySavingsTheme.color(0x96f0be,0.72)).tracking(1.4)
                .frame(height:12*1.2,alignment:.top)
            text(Self.money(f.total),"Black",54,.white).tracking(-1).monospacedDigit()
                // RN remeasures changed totals with the loaded font. Its cached
                // initial $1,236 measurement retains the system font's line height.
                .frame(height:Int(floor(f.total+0.5))==1236 ? systemHeight(54) : resources.height("Black",54),alignment:.top)
                .opacity(1-f.dip).offset(y:f.dip*6).padding(.top,2)
            GeometryReader { bar in
                ZStack(alignment:.leading) {
                    Color.white.opacity(0.1)
                    Capsule().fill(GlassPiggySavingsTheme.color(0x3fe08f))
                        .frame(width:bar.size.width*GlassPiggySavingsMotion.clamp(f.total/2000))
                }.clipShape(Capsule())
            }.frame(height:8).padding(.top,14)
            text("Goal $2,000 · by June 14","Black",14,.white.opacity(0.58)).padding(.top,8).offset(y:2.0/3)
        }
        .frame(width:l.w-48,alignment:.leading)
        .offset(x:24,y:l.top+18)
        .allowsHitTesting(false)
        .accessibilityElement(children:.combine)
    }
    private func chip(_ i: Int, _ f: GlassPiggySavingsMotion.Frame, _ l: GlassPiggySavingsMotion.Layout) -> some View {
        let hot=i==2
        return HStack(spacing:8) {
            Circle().fill(GlassPiggySavingsTheme.color(0xf2bf45))
                .overlay(Circle().strokeBorder(GlassPiggySavingsTheme.color(0xc98f27),lineWidth:2))
                .frame(width:16,height:16).scaleEffect(f.dot(i))
            systemText("+ $\(GlassPiggySavingsMotion.chips[i])",16,.white)
        }
        .frame(width:100,height:44)
        .background(Capsule().fill(hot ? GlassPiggySavingsTheme.color(0x3fe08f,0.16) : .white.opacity(0.07)))
        .overlay(Capsule().strokeBorder(hot ? GlassPiggySavingsTheme.color(0x3fe08f,0.6) : .white.opacity(0.16),lineWidth:1/displayScale))
        .scaleEffect(f.held.on && f.held.chip==i ? 0.96 : 1)
        .allowsHitTesting(false)
        .accessibilityElement(children:.ignore).accessibilityAddTraits(.isButton)
        .accessibilityLabel("Add $\(GlassPiggySavingsMotion.chips[i]) to the piggy bank")
        .accessibilityAction { if pinned == nil { clock.drop(i,l,reduced) } }
    }
    private func receipt(_ f: GlassPiggySavingsMotion.Frame, _ l: GlassPiggySavingsMotion.Layout) -> some View {
        HStack(spacing:12) {
            systemText("✓",17,GlassPiggySavingsTheme.background)
                .frame(width:32,height:32).background(Circle().fill(GlassPiggySavingsTheme.color(0x3fe08f)))
            VStack(alignment:.leading,spacing:5.0/3) {
                systemText("$\(f.plus) saved to Lisbon trip",15,.white)
                systemText("Just now · from Everyday account",12,.white.opacity(0.55))
            }.frame(maxWidth:.infinity,alignment:.leading)
        }
        .padding(.horizontal,12+1/displayScale).frame(width:l.w-32,height:58)
        .background(RoundedRectangle(cornerRadius:18,style:.circular).fill(GlassPiggySavingsTheme.color(0x0d2a1d)))
        .overlay(RoundedRectangle(cornerRadius:18,style:.circular).strokeBorder(GlassPiggySavingsTheme.color(0x3fe08f,0.45),lineWidth:1/displayScale))
        .position(x:l.w/2,y:l.h-l.bottom-6-29)
        .offset(y:f.receipt.y).opacity(f.receipt.alpha)
        .allowsHitTesting(false).accessibilityElement(children:.combine)
    }
    private static func money(_ v: Double) -> String {
        let s=String(Int(floor(v+0.5)))
        return "$"+s.enumerated().map { index,char in
            (index>0 && (s.count-index)%3==0 ? "," : "")+String(char)
        }.joined()
    }
}

struct GlassPiggySavingsTouchView: UIViewRepresentable {
    var onEvent:(UIGestureRecognizer.State,CGPoint)->Void
    func makeUIView(context: Context) -> GlassPiggySavingsTouchSurface {
        let view=GlassPiggySavingsTouchSurface(); view.onEvent=onEvent; return view
    }
    func updateUIView(_ view: GlassPiggySavingsTouchSurface,context:Context) { view.onEvent=onEvent }
}
final class GlassPiggySavingsTouchSurface: UIView,UIGestureRecognizerDelegate {
    var onEvent:((UIGestureRecognizer.State,CGPoint)->Void)?
    override init(frame:CGRect) {
        super.init(frame:frame); backgroundColor = .clear; isOpaque=false; isAccessibilityElement=false
        let gesture=UILongPressGestureRecognizer(target:self,action:#selector(handle(_:)))
        gesture.minimumPressDuration=0; gesture.allowableMovement = .greatestFiniteMagnitude
        gesture.cancelsTouchesInView=false; gesture.delegate=self; addGestureRecognizer(gesture)
    }
    @available(*,unavailable) required init?(coder:NSCoder) { nil }
    func gestureRecognizer(_ gestureRecognizer:UIGestureRecognizer,shouldRecognizeSimultaneouslyWith otherGestureRecognizer:UIGestureRecognizer)->Bool { true }
    @objc private func handle(_ gesture:UILongPressGestureRecognizer) {
        onEvent?(gesture.state,gesture.location(in:self))
    }
}
