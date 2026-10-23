import SwiftUI
import UIKit

struct CityEventHeroSurface: View {
    let frame: CityEventHeroMotion.Frame
    let controller: CityEventHeroController
    let reduceMotion: Bool

    var body: some View {
        GeometryReader { proxy in
            let safeTop = CityEventHeroSafeArea.insets.top
            ZStack(alignment: .topLeading) {
                Color.black
                CityEventHeroNightScene(
                    frame: frame,
                    size: proxy.size,
                    safeTop: safeTop
                )
                CityEventHeroUIKitSurface(frame: frame, controller: controller, reduceMotion: reduceMotion, safeTop: safeTop, pass: .fireworks)
                    .blendMode(.plusLighter)
                    .allowsHitTesting(false)
                CityEventHeroUIKitSurface(frame: frame, controller: controller, reduceMotion: reduceMotion, safeTop: safeTop, pass: .backdrop)
                    .allowsHitTesting(false)
                CityEventHeroUIKitSurface(frame: frame, controller: controller, reduceMotion: reduceMotion, safeTop: safeTop, pass: .glows)
                    .blendMode(.plusLighter)
                    .allowsHitTesting(false)
                CityEventHeroUIKitSurface(
                    frame: frame,
                    controller: controller,
                    reduceMotion: reduceMotion,
                    safeTop: safeTop
                )
            }
            .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
        }
    }
}

private struct CityEventHeroUIKitSurface: UIViewRepresentable {
    let frame: CityEventHeroMotion.Frame
    let controller: CityEventHeroController
    let reduceMotion: Bool
    let safeTop: Double
    var pass: CityEventHeroRenderView.Pass = .content

    func makeUIView(context: Context) -> CityEventHeroRenderView {
        let view = CityEventHeroRenderView(pass: pass)
        view.update(frame: frame, controller: controller, reduceMotion: reduceMotion, safeTop: safeTop)
        return view
    }

    func updateUIView(_ view: CityEventHeroRenderView, context: Context) {
        view.update(frame: frame, controller: controller, reduceMotion: reduceMotion, safeTop: safeTop)
    }
}

/// The block is edge-to-edge, so GeometryReader and the hosted UIKit view both report zero insets.
/// Read the key window exactly as the canonical React Native safe-area provider does.
enum CityEventHeroSafeArea {
    @MainActor static var insets: EdgeInsets {
        let window = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }
        let value = window?.safeAreaInsets ?? .zero
        return EdgeInsets(top: value.top, leading: value.left, bottom: value.bottom, trailing: value.right)
    }
}
