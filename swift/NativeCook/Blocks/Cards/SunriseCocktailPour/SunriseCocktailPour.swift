import SwiftUI

struct SunriseCocktailPour: View {
    static let loop=SunriseCocktailPourMotion.loop
    var autoPlay=false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.displayScale) private var displayScale
    @StateObject private var clock=SunriseCocktailPourClock()
    private var pinned: Double? {
        #if DEBUG
        if let value=UserDefaults.standard.object(forKey:"sunriseCocktailPourTime") {
            return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
        }
        #endif
        return nil
    }
    private func label(_ text:String,_ family:String,_ size:Double,_ color:Int,_ height:Double,_ spacing:Double=0) -> some View {
        Text(text).font(SunriseCocktailPourResources.font(family,size)).kerning(spacing)
            .foregroundStyle(SunriseCocktailPourResources.color(color))
            .lineLimit(1).fixedSize(horizontal:true,vertical:false)
            .frame(height:height)
    }
    var body: some View {
        let _ = SunriseCocktailPourResources.register
        GeometryReader { safe in
            let top=safe.safeAreaInsets.top, bottom=safe.safeAreaInsets.bottom
            GeometryReader { proxy in
                let l=SunriseCocktailPourLayout(proxy.size,top,bottom)
                TimelineView(.animation(paused:pinned != nil || scenePhase != .active || (reduced && !clock.running))) { timeline in
                    let sample=clock.sample(timeline.date,autoPlay && !reduced,reduced,pinned)
                    let f=sample.0
                    ZStack(alignment:.topLeading) {
                        SunriseCocktailPourRenderer(layout:l,frame:f,scale:displayScale)
                            .frame(width:l.w,height:l.h)
                            .accessibilityHidden(true)
                        if pinned==nil {
                            SunriseCocktailPourTouch(began:{ clock.takeOver(reduced) }) { state,translation,velocity in
                                clock.drag(state,translation,velocity,l.heroS,reduced)
                            }
                            .frame(width:l.heroS*1.8,height:l.heroS*2.5)
                            .position(x:l.heroX,y:l.heroY+l.heroS*0.05)
                            .accessibilityLabel(SunriseCocktailPourResources.labels[min(2,f.step)])
                            .accessibilityAddTraits(.isImage)
                            .accessibilityHint(f.step==1 ? "Drag round the glass to stir" : "")
                        }
                        Button {} label: {
                            SunriseCocktailPourResources.back.stroke(SunriseCocktailPourResources.color(0xF5EEE4),style:StrokeStyle(lineWidth:2.2,lineCap:.round,lineJoin:.round))
                            .frame(width:44,height:44).padding(4).contentShape(Rectangle())
                        }
                        .buttonStyle(SunriseCocktailPourPressStyle(reduced:reduced))
                        .position(x:30,y:l.barTop+22).accessibilityLabel("Back")
                        label("$14","Inter-SemiBold",17,0xC9A46A,44).monospacedDigit().offset(y:1.0/3)
                            .frame(width:l.w-16,alignment:.trailing).position(x:(l.w-16)/2,y:l.barTop+22)
                            .accessibilityLabel("14 dollars")
                        label("NO. 07 · HOUSE CLASSIC","Inter-Medium",12,0xC9A46A,16,1.4)
                            .position(x:l.w/2,y:l.eyebrowTop+8+1.0/3)
                        label("Tequila Sunrise","InstrumentSerif-Regular",40,0xF5EEE4,44)
                            .position(x:l.w/2,y:l.nameTop+22-4).accessibilityAddTraits(.isHeader)
                        label("Blanco tequila, fresh orange, grenadine","Inter24pt-Regular",15,0x9A8E80,20)
                            .position(x:l.w/2,y:l.recipeTop+10)
                        ForEach(0..<3,id:\.self) { i in
                            let n=pinned != nil ? (i==min(2,f.step) ? 1.0 : 0.0,0.0) : (sample.1.map { SunriseCocktailPourMotion.note(i,$0) } ?? clock.note(i,reduced))
                            label(SunriseCocktailPourResources.notes[i],"Inter-Medium",14,0xC9A46A,20)
                                .opacity(n.0).position(x:l.w/2,y:l.noteTop+10+n.1)
                                .accessibilityHidden(i != min(2,f.step))
                        }
                        Button { clock.next(reduced) } label: {
                            // Match Yoga's pixel-rounded centering of the first two CTA strings.
                            label(SunriseCocktailPourResources.ctas[min(2,f.step)],"Inter-SemiBold",17,0x14100C,56)
                                .offset(x:min(2,f.step)<2 ? -1/displayScale : 0,y:1.0/3)
                                .frame(width:l.w-32,height:56)
                                .background(SunriseCocktailPourResources.color(0xF5EEE4),in:Capsule())
                        }
                        .buttonStyle(SunriseCocktailPourPressStyle(reduced:reduced))
                        .position(x:l.w/2,y:l.ctaTop+28).disabled(pinned != nil)
                        Circle().fill(SunriseCocktailPourResources.color(0xF5EEE4).opacity(0.14*f.finger))
                            .overlay(Circle().stroke(SunriseCocktailPourResources.color(0xF5EEE4).opacity(0.5*f.finger),lineWidth:1.5))
                            .frame(width:44*(1-0.12*f.fingerPress),height:44*(1-0.12*f.fingerPress))
                            .position(x:l.w/2,y:l.ctaTop+28).allowsHitTesting(false).accessibilityHidden(true)
                    }.frame(width:l.w,height:l.h,alignment:.topLeading).clipped()
                }
            }.ignoresSafeArea()
        }
        .background(SunriseCocktailPourResources.color(0x100D0B))
        .preferredColorScheme(.dark)
        .onAppear { _=SunriseCocktailPourResources.register; clock.reset(autoPlay && !reduced) }
        .onChange(of:scenePhase) { _,_ in clock.active() }
        .onChange(of:reduced) { _,_ in clock.reset(autoPlay && !reduced) }
        .onChange(of:autoPlay) { _,_ in clock.reset(autoPlay && !reduced) }
    }
}
