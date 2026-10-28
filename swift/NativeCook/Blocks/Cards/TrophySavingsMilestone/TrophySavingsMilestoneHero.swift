import SwiftUI
import UIKit

@MainActor
struct TrophySavingsMilestoneHero: View {
    let autoPlay: Bool
    let previewTime: Double?
    let previewReducedMotion: Bool
    let overlay: Bool
    @Environment(\.accessibilityReduceMotion) private var systemReduced
    @Environment(\.scenePhase) private var scenePhase
    @GestureState private var dragging = false
    @State private var controller = TrophySavingsMilestoneController()
    private var reduced: Bool { systemReduced || previewReducedMotion }
    private static let artwork: UIImage? = {
        let renderer = ImageRenderer(content:TrophySavingsMilestoneArtwork().frame(width:176,height:214)); renderer.scale = 3; return renderer.uiImage
    }()
    var body: some View {
        ZStack(alignment:.topLeading) {
            TimelineView(.animation(minimumInterval:1.0/120.0,paused:!controller.ticking)) { timeline in
                let snapshot = controller.frame(at:timeline.date)
                let paper = TrophySavingsMilestoneStreamerGeometry.frame(progress:snapshot.progress,pose:snapshot.pose)
                ZStack(alignment:.topLeading) {
                    TrophySavingsMilestonePaperField(scene:snapshot.scene,layer:.rear)
                    TrophySavingsMilestoneRibbon(frame:paper,foreground:false)
                    Group {
                        if let image = Self.artwork { Image(uiImage:image).resizable() } else { TrophySavingsMilestoneArtwork() }
                    }.frame(width:176,height:214).position(x:196,y:203)
                        .phaseAnimator([1.0,0.88,1.0],trigger:controller.reducedFeedback) { content,alpha in content.opacity(alpha) } animation: { alpha in .easeOut(duration:alpha < 1 ? 0.12 : 0.1) }
                    TrophySavingsMilestoneCupLight(scene:snapshot.scene)
                    TrophySavingsMilestoneShimmer(scene:snapshot.scene)
                    TrophySavingsMilestoneRibbon(frame:paper,foreground:true)
                    TrophySavingsMilestonePaperField(scene:snapshot.scene,layer:.front)
                }.allowsHitTesting(false).accessibilityHidden(true)
            }
            Button { controller.demonstrate() } label: { Color.clear.frame(width:353,height:260).contentShape(Rectangle()) }
                .buttonStyle(.plain).position(x:196,y:203)
                .highPriorityGesture(gesture,including:reduced ? .subviews : .all)
                .accessibilityLabel("Savings trophy")
                .accessibilityHint(reduced ? "Replay brief celebration feedback." : "Replay the celebration. Drag horizontally to feed the paper streamer.")
        }.frame(width:393,height:350)
            .onAppear { configure(); if overlay { controller.pause() } }
            .onChange(of:autoPlay) { _,_ in configure() }.onChange(of:previewTime) { _,_ in configure() }
            .onChange(of:reduced) { _,_ in configure() }.onChange(of:scenePhase) { _,_ in configure() }
            .onChange(of:overlay) { _,visible in visible ? controller.pause() : controller.resume() }
            .onChange(of:dragging) { old,value in if old && !value { controller.release(cancelled:true) } }
            .onDisappear { controller.stop() }
    }
    private func configure() { controller.configure(autoPlay:autoPlay,previewTime:previewTime,reduced:reduced,active:scenePhase == .active) }
    private var gesture: some Gesture {
        DragGesture(minimumDistance:6).updating($dragging) { _,state,_ in state = true }
            .onChanged { value in controller.drag(translation:Double(value.translation.width),velocity:Double(value.velocity.width)) }
            .onEnded { _ in controller.release(cancelled:false) }
    }
}
