import SwiftUI

struct OwlAlarmRulerSurface: UIViewRepresentable {
    let date: Date
    let top: Double
    let bottom: Double
    let autoPlay: Bool
    let still: Bool
    let active: Bool
    let onActivity: (Bool) -> Void

    func makeUIView(context: Context) -> OwlAlarmRulerTouchView {
        OwlAlarmRulerTouchView()
    }

    func updateUIView(_ view: OwlAlarmRulerTouchView, context: Context) {
        view.onActivity = onActivity
        view.top = top
        view.bottom = bottom
        view.configure(autoPlay: autoPlay, still: still, active: active,
                       at: date.timeIntervalSinceReferenceDate)
    }
}
