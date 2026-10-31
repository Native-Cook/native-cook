import SwiftUI

struct GlassOrbShelf: View {
    static let loop = GlassOrbShelfMotion.loop
    let autoPlay: Bool
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.displayScale) private var displayScale
    @StateObject private var driver=GlassOrbShelfDriver()
    @State private var cards: [UIImage] = []
    init(autoPlay: Bool = false) { self.autoPlay=autoPlay }
    var body: some View {
        GeometryReader { safe in
            GeometryReader { full in
                let layout=GlassOrbShelfLayout(width:full.size.width,top:safe.safeAreaInsets.top)
                TimelineView(.animation(paused:scenePhase != .active || reduced)) { timeline in
                    let t=driver.time(timeline.date)
                    let o=driver.offset(t)
                    let current=min(3,max(0,Int(o.rounded())))
                    let lean=driver.lean(t)
                    let frac=o-o.rounded()
                    let radius=layout.orbR*(1-0.03*sin(min(1,abs(frac)*2) * .pi*0.5))
                    let stripTop=layout.orbY-layout.orbR-8
                    ZStack(alignment:.topLeading) {
                        Color(uiColor:GlassOrbShelfTheme.page)
                        Text("Featured").font(.custom(GlassOrbShelfTheme.font("Bold",28).fontName,fixedSize:28*min(1.3,fontScale)))
                            .tracking(-0.6*min(1.3,fontScale)).foregroundStyle(Color(uiColor:GlassOrbShelfTheme.ink))
                            .fixedSize().offset(x:24,y:layout.headerTop).accessibilityAddTraits(.isHeader)
                        Rectangle().fill(.white)
                            .colorEffect(ShaderLibrary.GlassOrbShelfMain(
                                .float2(Float(layout.orbX),Float(layout.orbY)), .float(Float(radius)), .float(Float(displayScale)),
                                .float(Float(reduced ? max(0,o).rounded() : min(3,max(0,o)))), .float(Float(driver.spin(t))),
                                .float2(Float(lean),Float(0.12+lean*0.3)), .float(Float(driver.pop(t))),
                                .float(Float(t.truncatingRemainder(dividingBy:Self.loop))), .float3(Float(13.0/255),Float(14.0/255),Float(17.0/255))))
                            .mask(alignment:.topLeading) {
                                Rectangle().frame(width:layout.orbR*2.6,height:layout.orbR*2.6)
                                    .offset(x:layout.orbX-layout.orbR*1.3,y:layout.orbY-layout.orbR*1.25)
                            }.allowsHitTesting(false).accessibilityHidden(true)
                        ForEach(0..<cards.count,id:\.self) { i in
                            let d=Double(i)-o, k=min(1,abs(d))
                            Image(uiImage:cards[i]).resizable().frame(width:layout.cardW,height:196)
                                .scaleEffect(1-0.06*k).opacity(1-0.45*k)
                                .position(x:layout.w/2+d*layout.step,y:layout.cardTop+98)
                                .accessibilityHidden(true)
                        }
                        HStack(spacing:6) {
                            ForEach(0..<4,id:\.self) { i in
                                let k=max(0,1-abs(Double(i)-o))
                                Capsule().fill(Color(uiColor:GlassOrbShelfTheme.ink)).frame(width:6+12*k,height:6).opacity(0.35+0.65*k)
                            }
                        }.frame(width:layout.w,height:6).offset(y:layout.dotsTop).accessibilityHidden(true)
                        GlassOrbShelfTouchView(label:GlassOrbShelfTheme.apps[current].label,onAdjust:driver.adjust) { state,point,translation,velocity in
                            driver.touch(state,point:point,translation:translation,velocity:velocity,layout:layout,stripTop:stripTop)
                        }.frame(width:layout.w,height:layout.cardTop+196+12-stripTop).offset(y:stripTop)
                    }.onChange(of:current) { _,_ in
                        if !autoPlay || t>0 && o != GlassOrbShelfMotion.offsetAt(driver.loopTime(t)) { UISelectionFeedbackGenerator().selectionChanged() }
                    }
                }
                .onAppear { rebuild(layout) }
                .onChange(of:dynamicTypeSize) { _,_ in rebuild(layout) }
                .onChange(of:full.size.width) { _,_ in rebuild(layout) }
                .clipped()
            }.ignoresSafeArea()
        }.preferredColorScheme(.dark)
        .onAppear { driver.configure(auto:autoPlay,reduce:reduced) }
        .onChange(of:scenePhase) { _,value in driver.setActive(value == .active) }
        .onChange(of:reduced) { _,value in driver.configure(auto:autoPlay,reduce:value) }
        .onChange(of:autoPlay) { _,value in driver.configure(auto:value,reduce:reduced) }
    }
    private var fontScale: Double {
        let category: UIContentSizeCategory
        switch dynamicTypeSize {
        case .xSmall: category = .extraSmall
        case .small: category = .small
        case .medium: category = .medium
        case .large: category = .large
        case .xLarge: category = .extraLarge
        case .xxLarge: category = .extraExtraLarge
        case .xxxLarge: category = .extraExtraExtraLarge
        case .accessibility1: category = .accessibilityMedium
        case .accessibility2: category = .accessibilityLarge
        case .accessibility3: category = .accessibilityExtraLarge
        case .accessibility4: category = .accessibilityExtraExtraLarge
        default: category = .accessibilityExtraExtraExtraLarge
        }
        return UIFont.preferredFont(forTextStyle:.body,compatibleWith:UITraitCollection(preferredContentSizeCategory:category)).pointSize/17
    }
    private func rebuild(_ layout: GlassOrbShelfLayout) {
        cards=GlassOrbShelfTheme.apps.map { GlassOrbShelfTheme.cardImage($0,width:layout.cardW,fontScale:fontScale) }
    }
}
