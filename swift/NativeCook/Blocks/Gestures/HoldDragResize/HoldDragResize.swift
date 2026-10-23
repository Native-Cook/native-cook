import SwiftUI

struct HoldDragResize: View {
    static let loop: Double = HoldDragResizeTokens.loop

    let autoPlay: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @StateObject private var motion: HoldDragResizeMotion

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        _motion = StateObject(wrappedValue: HoldDragResizeMotion(autoPlay: autoPlay))
    }

    var body: some View {
        GeometryReader { proxy in
            let safeTop = proxy.safeAreaInsets.top
            let safeBottom = proxy.safeAreaInsets.bottom
            let previewFrame = HoldDragResizeGeometry.preview(height: motion.shownHeight, width: proxy.size.width)
            ZStack(alignment: .topLeading) {
                Color.black
                HoldDragResizeTimeline(motion: motion, width: proxy.size.width)
                HoldDragResizePreview(motion: motion, width: proxy.size.width, reduceMotion: reduceMotion)
                HoldDragResizeTouchView(motion: motion)
                    .frame(width: previewFrame.width, height: previewFrame.height)
                    .position(x: previewFrame.midX, y: previewFrame.midY)
                    .accessibilityLabel("Video preview, \(HoldDragResizeTokens.sizeNames[motion.sizeIndex])")
                    .accessibilityHint("Hold and drag up or down to resize. Swipe up or down with one finger to change size.")
                    .accessibilityAdjustableAction { direction in motion.step(direction == .increment ? 1 : -1) }
                HoldDragResizeChrome(motion: motion, width: proxy.size.width, height: proxy.size.height, safeTop: safeTop, safeBottom: safeBottom)
                HoldDragResizeCoach(motion: motion, width: proxy.size.width, height: proxy.size.height)
                if autoPlay { HoldDragResizeGhost(motion: motion) }
            }
            .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
            .background(Color.black)
            .onAppear { motion.start(size: proxy.size, safeTop: safeTop, reduceMotion: reduceMotion) }
            .onDisappear { motion.stop() }
            .onChange(of: proxy.size) { _, size in motion.start(size: size, safeTop: safeTop, reduceMotion: reduceMotion) }
            .onChange(of: reduceMotion) { _, value in motion.start(size: proxy.size, safeTop: safeTop, reduceMotion: value) }
        }
        .ignoresSafeArea()
        .preferredColorScheme(.dark)
    }

}

enum HoldDragResizeGeometry {
    typealias T = HoldDragResizeTokens

    static func fullness(_ height: Double) -> Double {
        min(max((height - T.sizes[1]) / (T.sizes[2] - T.sizes[1]), 0), 1)
    }

    static func squeeze(_ height: Double) -> Double {
        min(max((height - T.sizes[0]) / (T.sizes[1] - T.sizes[0]), 0), 1)
    }

    static func along(_ height: Double, values: [Double]) -> Double {
        let index = height <= T.sizes[1] ? 0 : 1
        let u = (height - T.sizes[index]) / (T.sizes[index + 1] - T.sizes[index])
        return values[index] + (values[index + 1] - values[index]) * u
    }

    static func preview(height: Double, width: Double) -> CGRect {
        let previewWidth = along(height, values: T.frames.map(\.width)) * width / T.referenceWidth
        let top = max(along(height, values: T.frames.map(\.top)), 0)
        return CGRect(x: (width - previewWidth) / 2, y: top, width: previewWidth, height: height)
    }

    static func belowShift(_ height: Double) -> Double {
        along(height, values: T.frames.map(\.top)) + height - (T.frames[0].top + T.sizes[0])
    }
}
