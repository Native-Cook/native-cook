import SwiftUI
import UIKit

struct CardVacuumSeal: View {
    static let loop = 6.0
    var autoPlay: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.displayScale) private var displayScale
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @StateObject private var driver = CardVacuumSealDriver()
    @State private var resources = CardVacuumSealResources()
    @GestureState private var gestureActive = false
    @State private var gesturePending = false

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        _ = CardVacuumSealResources.register
    }
    private var pinnedTime: Double? {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        if let i = args.firstIndex(of: "-cardVacuumSealTime"), i+1 < args.count,
           let t = Double(args[i+1]), t.isFinite, t >= 0 { return t }
        #endif
        return nil
    }
    private var typeScale: Double {
        // RN maxFontSizeMultiplier=1.3; card typography never scales with Dynamic Type.
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
        return min(1.3,UIFontMetrics.default.scaledValue(for: 17,compatibleWith: UITraitCollection(preferredContentSizeCategory: category))/17)
    }
    var body: some View {
        GeometryReader { geo in
            let inset = geo.safeAreaInsets
            let layout = CardVacuumSealLayout(width: geo.size.width+inset.leading+inset.trailing,
                                             height: geo.size.height+inset.top+inset.bottom,top: inset.top,bottom: inset.bottom)
            stage(layout)
                .frame(width: layout.width,height: layout.height,alignment: .topLeading)
                .offset(x: -inset.leading,y: -inset.top)
        }
        .preferredColorScheme(.dark)
        .onAppear { configure() }
        .onDisappear { driver.stop() }
        .onChange(of: autoPlay) { configure() }
        .onChange(of: reduceMotion) { configure() }
        .onChange(of: scenePhase) { driver.setActive(scenePhase == .active) }
        .onChange(of: gestureActive) {
            if !gestureActive && gesturePending { gesturePending = false; driver.end() }
        }
    }
    private func configure() { driver.configure(autoPlay: autoPlay,reduced: reduceMotion,pin: pinnedTime) }

    private func stage(_ l: CardVacuumSealLayout) -> some View {
        ZStack(alignment: .topLeading) {
            CardVacuumSealResources.color(0x0A0A0C)
            ScrollView(.vertical) {
                ZStack(alignment: .topLeading) {
                    page(l)
                    CardVacuumSealFrame(frame: driver.frame,layout: l,resources: resources,displayScale: displayScale,typeScale: typeScale,reduced: reduceMotion)
                    controls(l)
                }
                .frame(width: l.width,height: l.contentHeight,alignment: .topLeading)
                .opacity(driver.frame.shown)
                .background(CardVacuumSealScrollBehavior())
            }
            .scrollIndicators(.hidden)
            .scrollBounceBehavior(.basedOnSize)
            .scrollDisabled(l.contentHeight <= l.height)
        }
        .frame(width: l.width,height: l.height,alignment: .topLeading)
        .clipped()
    }
    private func page(_ l: CardVacuumSealLayout) -> some View {
        Canvas(opaque: false,colorMode: .nonLinear) { context,_ in
            var bloom = context
            // SVG user-space elliptical radial gradient starts at the physical page origin.
            bloom.scaleBy(x: 1,y: 1.15/0.95)
            bloom.fill(Path(CGRect(x: 0,y: 0,width: l.width,height: l.contentHeight)),with: .radialGradient(
                Gradient(stops: [.init(color: CardVacuumSealResources.color(0x6B3FD8,0.2),location: 0),
                                 .init(color: CardVacuumSealResources.color(0x6B3FD8,0.06),location: 0.45),
                                 .init(color: CardVacuumSealResources.color(0x6B3FD8,0),location: 1)]),
                center: .zero,startRadius: 0,endRadius: l.width*0.95))
            var c = context
            c.scaleBy(x: l.scale,y: l.scale); c.translateBy(x: 0,y: l.dy/l.scale)
            CardVacuumSealArt.page(c,resources: resources,bottom: (l.contentHeight-l.dy)/l.scale,typeScale: typeScale,scale: l.scale)
        }
        .frame(width: l.width,height: l.contentHeight,alignment: .topLeading)
        .accessibilityHidden(true)
        .allowsHitTesting(false)
    }
    private func controls(_ l: CardVacuumSealLayout) -> some View {
        let s = l.scale
        return ZStack(alignment: .topLeading) {
            target("Back",x: (24-11)*s,y: l.y(84)-22,w: 44,h: 44)
            target("Close",x: (346+16)*s-22,y: l.y(84)-22,w: 44,h: 44)
            target("Cards",x: 80*s,y: l.y(70),w: 242*s,h: 28*s,header: true)
            target("Show details",x: 24*s,y: l.y(367),w: 112*s,h: 112*s)
            target("Settings",x: 266*s,y: l.y(367),w: 112*s,h: 112*s)
            Color.clear.contentShape(Rectangle())
                .frame(width: 112*s,height: 112*s)
                .gesture(holdGesture(s))
                .accessibilityRepresentation {
                    Toggle("Protect card",isOn: Binding(get: { driver.frame.sealed },set: { _ in driver.toggleAccessible() }))
                        .accessibilityHint("Seals the card so it cannot be used. Hold to watch the film tighten.")
                }
                .scaleEffect(1-0.04*driver.frame.look.press)
                .offset(x: 145*s,y: l.y(367)-2*s*driver.frame.look.tug)
            Color.clear.contentShape(Rectangle())
                .frame(width: 354*s,height: 223*s)
                .gesture(filmGesture(s))
                .accessibilityRepresentation {
                    Toggle(driver.frame.sealed ? "Halden card, North Studio, protected" : "Halden card, North Studio",
                           isOn: Binding(get: { driver.frame.sealed },set: { _ in driver.toggleAccessible() }))
                }
                .offset(x: 24*s,y: l.y(128))
            target("Revenue 42,220",x: 24*s,y: l.y(491),w: 354*s,h: 116*s,button: false)
            target("Recent transactions",x: 44*s,y: l.y(643),w: 230*s,h: 28*s,header: true)
            target("See all",x: 294*s,y: l.y(643),w: 64*s,h: 44)
            target("Tidewell Prints, Money received, $739.65, Completed",x: 24*s,y: l.y(685),w: 354*s,h: 44*s,button: false)
            target("Oren Calloway, Money received, $943.65, Completed",x: 24*s,y: l.y(749),w: 354*s,h: 44*s,button: false)
        }
    }
    private func target(_ label: String,x: Double,y: Double,w: Double,h: Double,header: Bool = false,button: Bool = true) -> some View {
        Color.clear.frame(width: w,height: h).contentShape(Rectangle())
            .accessibilityElement(children: .ignore).accessibilityLabel(label)
            .accessibilityAddTraits(header ? .isHeader : button ? .isButton : .isStaticText)
            .offset(x: x,y: y)
    }
    private func holdGesture(_ s: Double) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($gestureActive) { _,state,_ in state = true }
            .onChanged { v in
                if !gesturePending { gesturePending = true; driver.beginHold() }
                driver.moveHold(v.location,scale: s)
            }
            .onEnded { _ in gesturePending = false; driver.end() }
    }
    private func filmGesture(_ s: Double) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($gestureActive) { _,state,_ in state = true }
            .onChanged { v in
                if !gesturePending {
                    gesturePending = true
                    driver.beginFilm(CGPoint(x: v.startLocation.x/s,y: v.startLocation.y/s))
                } else { driver.moveFilm(CGPoint(x: v.location.x/s,y: v.location.y/s)) }
            }
            .onEnded { _ in gesturePending = false; driver.end() }
    }
}
