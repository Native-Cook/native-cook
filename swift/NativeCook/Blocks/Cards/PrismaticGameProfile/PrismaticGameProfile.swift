import SwiftUI

/// Layout B and the approved reversible Peel, using native text and one actor owner.
struct PrismaticGameProfile: View {
    static let loop=10.0
    var autoPlay=false
    var previewTime:Double?=nil
    var previewReducedMotion=false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    var body:some View {
        PrismaticGameProfileHost(autoPlay:autoPlay,previewTime:previewTime,reduced:reduceMotion || previewReducedMotion,foreground:scenePhase == .active)
            .ignoresSafeArea().background(Color.white).preferredColorScheme(.dark)
    }
}
