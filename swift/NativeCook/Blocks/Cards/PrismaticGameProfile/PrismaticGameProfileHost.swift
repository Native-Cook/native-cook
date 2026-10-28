import SwiftUI

struct PrismaticGameProfileHost:UIViewRepresentable {
    var autoPlay:Bool
    var previewTime:Double?
    var reduced:Bool
    var foreground:Bool
    func makeUIView(context:Context)->PrismaticGameProfileStage {PrismaticGameProfileStage()}
    func updateUIView(_ view:PrismaticGameProfileStage,context:Context){view.configure(autoPlay:autoPlay,previewTime:previewTime,reduced:reduced,foreground:foreground)}
    static func dismantleUIView(_ view:PrismaticGameProfileStage,coordinator:()){view.stop()}
}
