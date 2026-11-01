import SwiftUI

struct SetupDomeLoader: View {
    static let loop = 9.0
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scene
    @StateObject private var clock = SetupDomeLoaderClock()
    @State private var pressed = false
    private var pinned: Double? {
        #if DEBUG
        if let value = UserDefaults.standard.object(forKey:"setupDomeLoaderTime") {
            return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { safe in
            GeometryReader { full in
                let size = full.size
                let geo = SetupDomeLoaderGeo(size)
                let _ = clock.wake
                TimelineView(.animation(paused:pinned != nil || scene != .active || (!autoPlay && clock.settled))) { timeline in
                    let output = clock.sample(timeline.date,autoPlay,pinned)
                    let copyPhase = SetupDomeLoaderMotion.phase(output.t).swap
                    let swap = SetupDomeLoaderMotion.clamp(copyPhase*2-1)
                    let oldCopy = 1-SetupDomeLoaderMotion.clamp(copyPhase*2)
                    Canvas { context, _ in
                        context.withCGContext { cv in
                            SetupDomeLoaderArt(cv:cv).rect(0,0,size.width,size.height,0x0D0D12)
                            SetupDomeLoaderArt(cv:cv).scene(geo,output,reduced)
                            SetupDomeLoaderResources.shared.ui(cv,size,safe.safeAreaInsets.top,safe.safeAreaInsets.bottom,output.t,pressed)
                        }
                    }
                    .overlay(alignment:.topLeading) {
                        if pinned == nil {
                            SetupDomeLoaderTouch(clock:clock)
                                .frame(width:size.width-48,height:geo.h+geo.r)
                                .offset(x:24,y:geo.by-geo.h-geo.r*0.5)
                            Text("Kinto").foregroundStyle(.clear)
                                .frame(width:80,height:25)
                                .offset(x:(size.width-80)/2,y:safe.safeAreaInsets.top+13.5)
                            Button {} label: {
                                Color.clear.frame(width:56,height:56).contentShape(Rectangle())
                            }.buttonStyle(.plain).accessibilityLabel("Back").offset(x:6,y:safe.safeAreaInsets.top-2)
                            Button {} label: {
                                Color.clear.frame(width:56,height:56).contentShape(Rectangle())
                            }.buttonStyle(.plain).accessibilityLabel("Help").offset(x:size.width-62,y:safe.safeAreaInsets.top-2)
                            Button { clock.restart() } label: { Color.clear.contentShape(Rectangle()) }
                                .buttonStyle(SetupDomeLoaderPressStyle(pressed:$pressed))
                                .frame(width:size.width-40,height:54)
                                .offset(x:20,y:size.height-safe.safeAreaInsets.bottom-16-54+(1-swap)*16)
                                .accessibilityLabel("Continue")
                                .allowsHitTesting(swap > 0.01)
                                .accessibilityHidden(swap <= 0.01)
                        }
                    }
                    .overlay(alignment:.top) {
                        VStack {
                            if oldCopy > 0.01 {
                                Text("We're setting up your account").accessibilityAddTraits(.isHeader)
                                Text("Verifying your details")
                                Text("Creating your card")
                                Text("Securing your wallet")
                                Text("This can take up to 2 minutes")
                            } else if swap > 0.01 {
                                Text("You're all set").accessibilityAddTraits(.isHeader)
                                Text("Your card and wallet are ready to use.")
                            }
                        }
                        .foregroundStyle(.clear).allowsHitTesting(false).offset(y:geo.by+geo.ry*1.42+52)
                    }
                }
                .frame(width:size.width,height:size.height,alignment:.topLeading)
            }.ignoresSafeArea()
        }
        .background(Color(red:13/255,green:13/255,blue:18/255))
        .preferredColorScheme(.dark)
        .onChange(of:scene) { _, _ in clock.resetDate() }
    }
}

struct SetupDomeLoaderPressStyle: ButtonStyle {
    @Binding var pressed: Bool
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.onChange(of:configuration.isPressed) { _, value in pressed = value }
    }
}
