import SwiftUI

/// The approved RN onboarding, with its complete UI and ceramic in one Metal pass.
struct SolidMorphOnboarding: View {
    static let loop = SolidMorphOnboardingMotion.loop
    var autoPlay: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.displayScale) private var displayScale
    @ScaledMetric(relativeTo: .body) private var scaledBody = 17.0
    @State private var driver = SolidMorphOnboardingDriver()
    @State private var resources = SolidMorphOnboardingResources()
    @GestureState private var gestureActive = false
    @State private var gesturePending = false
    @State private var gestureRejected = false

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        _ = SolidMorphOnboardingResources.register
    }
    private var pin: Double? {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        if let i = args.firstIndex(of: "-solidMorphOnboardingTime"), i+1 < args.count,
           let value = Double(args[i+1]), value.isFinite, value >= 0 { return value }
        #endif
        return nil
    }
    private var reduced: Bool { reduceMotion || scaledBody/17 > 1.35 }

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let width = geo.size.width+insets.leading+insets.trailing
            let height = geo.size.height+insets.top+insets.bottom
            let l = SolidMorphOnboardingLayout(width: width,viewportHeight: height,topInset: insets.top,bottomInset: insets.bottom,fontScale: scaledBody/17)
            screen(l)
                .frame(width: width,height: height,alignment: .topLeading)
                .offset(x: -insets.leading,y: -insets.top)
        }
        .preferredColorScheme(driver.selected == 1 ? .light : .dark)
        .onAppear { driver.setActive(scenePhase == .active,at: Date()) }
        .onDisappear { driver.setActive(false,at: Date()) }
        .onChange(of: scenePhase) { driver.setActive(scenePhase == .active,at: Date()) }
        .onChange(of: autoPlay) { driver.reset() }
    }

    private func screen(_ l: SolidMorphOnboardingLayout) -> some View {
        let atlas = resources.atlas(l,displayScale: displayScale)
        let _ = driver.revision
        return ScrollView(.vertical) {
            ZStack(alignment: .topLeading) {
                TimelineView(.animation(paused: scenePhase != .active || (!reduced && pin != nil) || (reduced && !driver.needsFrames))) { timeline in
                    let frame = driver.frame(at: timeline.date,autoPlay: autoPlay,reduced: reduced,pin: pin)
                    SolidMorphOnboardingShaderFrame(frame: frame,layout: l,atlas: atlas,displayScale: displayScale,reduced: reduced)
                }
                .allowsHitTesting(false)
                let current = SolidMorphOnboardingPages.all[driver.selected]
                Color.clear.contentShape(Rectangle())
                    .frame(width: max(0,l.width-20),height: max(0,l.footerY-12),alignment: .topLeading)
                    .offset(x: 20)
                    .simultaneousGesture(DragGesture(minimumDistance: 0,coordinateSpace: .named("SolidMorphOnboarding"))
                        .updating($gestureActive) { _, state, _ in state = true }
                        .onChanged { v in
                            gesturePending = true
                            // Large-text mode reserves vertical movement for the ScrollView.
                            if gestureRejected { return }
                            if l.largeText && abs(v.translation.height) > 12 && abs(v.translation.width) < 10 {
                                gestureRejected = true
                                driver.end(.zero,velocity: .zero,x: 0,layout: l,at: v.time,cancelled: true)
                                return
                            }
                            if !driver.fingerDown { driver.begin(v.startLocation,layout: l,at: v.time) }
                            driver.move(v.location,translation: v.translation,layout: l,at: v.time)
                        }
                        .onEnded { v in
                            gesturePending = false
                            if !gestureRejected { driver.end(v.translation,velocity: v.velocity,x: v.location.x,layout: l,at: v.time) }
                            gestureRejected = false
                        })
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("\(current.title.replacingOccurrences(of: "\n",with: " ")). \(current.body)")
                    .accessibilityValue("Page \(driver.selected+1) of 3")
                    .accessibilityAdjustableAction { direction in
                        driver.go(Double(driver.selected)+(direction == .increment ? 1 : -1),at: Date())
                    }
                Button { driver.go(2,at: Date()) } label: { Color.clear.contentShape(Rectangle()) }
                    .buttonStyle(.plain)
                    .frame(width: l.skipWidth,height: max(48,32*l.scale*l.fontScale))
                    .offset(x: l.width-8*l.scale-l.skipWidth,y: l.progressY-18*l.scale)
                    .accessibilityLabel("Skip introduction")
                button(true,l)
                    .offset(x: 17*l.scale,y: l.footerY)
                button(false,l)
                    .offset(x: l.largeText ? 17*l.scale : 17*l.scale+l.buttonWidth+8*l.scale,
                            y: l.largeText ? l.footerY+l.buttonHeight+8*l.scale : l.footerY)
            }
            .coordinateSpace(name: "SolidMorphOnboarding")
            .frame(width: l.width,height: l.height,alignment: .topLeading)
        }
        .scrollDisabled(l.height <= l.viewportHeight)
        .scrollIndicators(.hidden)
        .scrollBounceBehavior(.basedOnSize)
        .contentMargins(.all,0,for: .scrollContent)
        .ignoresSafeArea(.container)
        .background(Color(.sRGB,red: 10/255,green: 10/255,blue: 11/255))
        .onChange(of: gestureActive) {
            if !gestureActive && gesturePending {
                gesturePending = false
                gestureRejected = false
                driver.end(.zero,velocity: .zero,x: 0,layout: l,at: Date(),cancelled: true)
            }
        }
    }

    private func button(_ primary: Bool, _ l: SolidMorphOnboardingLayout) -> some View {
        Button { driver.go(primary ? Double(min(2,driver.selected+1)) : 0,at: Date()) } label: {
            Color.clear.contentShape(RoundedRectangle(cornerRadius: 28*l.scale))
        }
        .buttonStyle(SolidMorphOnboardingButtonStyle(driver: driver,primary: primary))
        .frame(width: l.buttonWidth,height: l.buttonHeight)
        .accessibilityLabel(primary ? "Create account" : "Log in")
    }
}

private struct SolidMorphOnboardingButtonStyle: ButtonStyle {
    let driver: SolidMorphOnboardingDriver
    let primary: Bool
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.onChange(of: configuration.isPressed) { _, pressed in
            driver.button(primary,down: pressed,at: Date())
        }
    }
}

private struct SolidMorphOnboardingShaderFrame: View {
    let frame: SolidMorphOnboardingFrame
    let layout: SolidMorphOnboardingLayout
    let atlas: SolidMorphOnboardingResources.Atlas
    let displayScale: Double
    let reduced: Bool
    private typealias M = SolidMorphOnboardingMotion

    var body: some View {
        Rectangle().fill(.white)
            .colorEffect(ShaderLibrary.solidMorphOnboarding(.floatArray(uniforms),.image(atlas.image)))
            .frame(width: layout.width,height: layout.height,alignment: .topLeading)
            .opacity(frame.visibility)
            .accessibilityHidden(true)
    }
    private var uniforms: [Float] {
        let f = frame, l = layout
        let p = M.clamp(f.progress,0,2), from = min(Int(floor(p)),1), phase = p-Double(from)
        let a = SolidMorphOnboardingPages.all[from], b = SolidMorphOnboardingPages.all[from+1]
        let edge = M.clamp((f.progress-p)*4,-1,1)
        let hover = reduced ? 0 : M.hover(f.clock)
        let turn = reduced ? (Double(from)+phase.rounded())*1.5 : (Double(from)+M.ease(phase))*1.5+edge*20/360
        var u: [Double] = [l.width,l.height,l.heroX,l.heroY,l.heroRadius,f.progress,3]
        u += [a.sides,b.sides,a.roundness,b.roundness,a.split,b.split,a.base,b.base,a.light,b.light]
        u += [turn,reduced ? 0 : M.spinRate(f.progress,f.rate),hover]
        u += [reduced ? 0 : (f.playing ? M.bob(f.clock) : 0)+f.lift]
        u += [reduced ? 0 : (f.playing ? M.base(f.clock) : 0)+f.base,reduced ? 0 : f.cushion]
        u += reduced ? [0,0] : [(f.tiltX+hover*4+edge*8)*Double.pi/180,f.tiltY*Double.pi/180]
        u += [1,1,0,1/max(1,l.heroRadius*displayScale),l.largeText ? 1 : 0]
        let colors: [UInt32] = [0x0A0A0B,0xFCFCFB,0xF4F4F2,0x77777D,0x080809]
        for hex in colors {
            u += [Double(hex >> 16 & 255)/255,Double(hex >> 8 & 255)/255,Double(hex & 255)/255,1]
        }
        u += [f.primaryPress,f.secondaryPress,l.progressY,l.footerY,l.buttonHeight,l.scale]
        u += [f.playing ? M.arrival(f.clock) : 0,reduced ? 1 : 0]
        u += [f.contactPulse,f.playing ? M.basePulse(f.clock) : f.basePulse]
        u += [l.chipY-12*l.scale,l.bodyY+l.bodyHeight+8*l.scale,atlas.density,atlas.glyphX]
        return u.map(Float.init)
    }
}
