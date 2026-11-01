import SwiftUI
import UIKit

struct IconEraScrubber: View {
    static let loop = IconEraScrubberMotion.loop
    private let autoPlay: Bool
    init(autoPlay: Bool = false) { self.autoPlay = autoPlay }
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.displayScale) private var displayScale
    @StateObject private var driver = IconEraScrubberDriver()
    typealias T = IconEraScrubberTheme
    typealias M = IconEraScrubberMotion

    var body: some View {
        GeometryReader { safe in
            GeometryReader { geometry in
                let l = T.Layout(w:geometry.size.width,h:geometry.size.height,
                                 top:safe.safeAreaInsets.top,bottom:safe.safeAreaInsets.bottom)
                TimelineView(.animation(paused:scenePhase != .active || driver.paused)) { timeline in
                    let time = driver.now(timeline.date)
                    let sample = driver.sample(time)
                    let f = sample.frame
                    let shown = Int(floor(min(M.last,max(M.first,f.year))+0.5))
                    ZStack(alignment:.topLeading) {
                        Rectangle().fill(.white)
                            .colorEffect(ShaderLibrary.IconEraScrubberMain(
                                .float4(Float(l.iconX),Float(l.iconY),Float(T.iconHalf),Float(displayScale)),
                                .float4(Float(M.eraOf(f.year)),Float(0.14+0.35*f.lean),
                                        Float(0.1+0.05*abs(f.lean)),Float(sample.phase)),
                                .float4(Float(l.w),Float(l.h),1,0)))
                            .allowsHitTesting(false)
                        words(l,f,shown)
                        ruler(l,f)
                        Circle().fill(.white.opacity(0.35))
                            .overlay(Circle().strokeBorder(.white.opacity(0.8),lineWidth:1.5))
                            .frame(width:44,height:44)
                            .offset(x:l.w*0.68-f.fingerX*T.step-22,y:l.rulerTop+30-22)
                            .opacity(0.85*f.finger).allowsHitTesting(false).accessibilityHidden(true)
                        IconEraScrubberTouchView { state,point,delta,velocity in
                            driver.touch(state,point,delta,velocity,layout:l)
                        }
                        Color.clear.frame(width:l.w,height:96).offset(y:l.rulerTop-24)
                            .allowsHitTesting(false)
                            .accessibilityElement(children:.ignore)
                            .accessibilityLabel("Postbox icon by year")
                            .accessibilityValue("\(shown), \(T.eras[min(3,Int(floor(M.eraOf(Double(shown))+0.5)))].0)")
                            .accessibilityAdjustableAction { action in
                                switch action {
                                case .increment: driver.step(1)
                                case .decrement: driver.step(-1)
                                @unknown default: break
                                }
                            }
                    }
                    .frame(width:l.w,height:l.h,alignment:.topLeading).clipped()
                    .onChange(of:shown) { old,new in
                        if old != new && sample.manual { UISelectionFeedbackGenerator().selectionChanged() }
                    }
                    .onChange(of:driver.moving(time)) { _,moving in
                        if !moving && reduced { driver.settled() }
                    }
                }
            }.ignoresSafeArea()
        }
        .background(T.page).preferredColorScheme(.dark)
        .onAppear { driver.configure(auto:autoPlay,reduced:reduced); driver.active(scenePhase == .active) }
        .onDisappear { driver.active(false) }
        .onChange(of:scenePhase) { _,phase in driver.active(phase == .active) }
        .onChange(of:reduced) { _,value in driver.preferences(auto:autoPlay,reduced:value) }
        .onChange(of:autoPlay) { _,value in driver.preferences(auto:value,reduced:reduced) }
    }
    private func words(_ l: T.Layout, _ f: M.Frame, _ shown: Int) -> some View {
        ZStack(alignment:.topLeading) {
            VStack(spacing:2) {
                Text("App icon history").font(T.font("Medium",13)).tracking(0.4)
                    .foregroundStyle(T.mute).frame(height:18)
                Text("Postbox").font(T.font("SemiBold",20)).foregroundStyle(T.ink).frame(height:26)
                    .accessibilityAddTraits(.isHeader)
            }.frame(width:l.w).offset(y:l.titleTop)
            Text(String(shown)).font(T.font("SemiBold",56)).monospacedDigit()
                .foregroundStyle(T.ink).frame(width:l.w,height:64)
                .offset(y:l.yearTop).accessibilityHidden(true)
            ForEach(0..<4,id:\.self) { i in
                let d = M.eraOf(f.year)-Double(i)
                // the nearer era's words show, swapping in a quick fade at the change's middle
                let a = abs(d), k = max(0,min(1,(0.53-a)/0.06))
                VStack(spacing:2) {
                    Text(T.eras[i].0).font(T.font("SemiBold",17)).foregroundStyle(T.ink).frame(height:24)
                    Text(T.eras[i].1).font(T.font("Regular",14)).foregroundStyle(T.mute).frame(height:20)
                }.frame(width:l.w).offset(y:l.yearTop+68-14*max(-1,min(1,d)))
                    .opacity(k*k).accessibilityHidden(k<0.5)
            }
        }.allowsHitTesting(false).dynamicTypeSize(.medium)
    }
    private func ruler(_ l: T.Layout, _ f: M.Frame) -> some View {
        Canvas { context,_ in
            // unlabelled ticks past both ends, fading out, so the ruler runs off the screen at 2008 and 2026 too
            for away in 1...8 {
                for year in [2008-away, 2026+away] {
                    let x = l.w/2+(Double(year)-f.year)*T.step
                    let rect = CGRect(x:x-0.75,y:l.rulerTop,width:1.5,height:14)
                    context.fill(Path(roundedRect:rect,cornerRadius:1),with:.color(T.tick.opacity(0.55*(1-Double(away)/9))))
                }
            }
            for year in 2008...2026 {
                let x = l.w/2+(Double(year)-f.year)*T.step
                let major = year%2 == 0
                let rect = CGRect(x:x-0.75,y:l.rulerTop,width:1.5,height:major ? 24 : 14)
                context.fill(Path(roundedRect:rect,cornerRadius:1),with:.color(major ? T.mute : T.tick))
                if major {
                    let label = Text(String(format:"'%02d",year%100)).font(T.font("Medium",12))
                        .monospacedDigit().foregroundColor(T.mute)
                    context.draw(label,at:CGPoint(x:x,y:l.rulerTop+32),anchor:.top)
                }
            }
            context.fill(Path(roundedRect:CGRect(x:l.w/2-1,y:l.rulerTop-8,width:2,height:40),
                              cornerRadius:1),with:.color(.white))
        }.allowsHitTesting(false).accessibilityHidden(true)
    }
}
