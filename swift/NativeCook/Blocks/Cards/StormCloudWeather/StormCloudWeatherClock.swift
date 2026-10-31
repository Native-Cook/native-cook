import AVFoundation
import SwiftUI
import UIKit

final class StormCloudWeatherClock: ObservableObject {
    typealias M = StormCloudWeatherMotion
    @Published var revision = 0
    private var date: Date?
    private var now = 0.0, time = 0.0, last = -10.0
    private var from = 0, to = 0
    private var morphAt = -10.0, m = 1.0, flash = 0.0, nextFlash = 1.2
    private var yaw = 0.0, velocity = 0.0, startYaw = 0.0, held = false
    private var players: [String: AVAudioPlayer] = [:]
    private let selection = UISelectionFeedbackGenerator()

    func active(_ enabled: Bool, reduced: Bool) {
        date = nil
        if enabled && !reduced && players.isEmpty {
            try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: .mixWithOthers)
            for name in ["thunder", "morph", "tap"] {
                if let url = Bundle.main.url(forResource: "StormCloudWeather-scw-\(name)", withExtension: "wav"),
                   let player = try? AVAudioPlayer(contentsOf: url) {
                    player.volume = name == "thunder" ? 0.5 : 0.7
                    player.prepareToPlay(); players[name] = player
                }
            }
        }
        if !enabled || reduced { players.values.forEach { $0.stop() } }
    }
    private func cue(_ name: String, _ reduced: Bool) {
        guard !reduced, let player = players[name] else { return }
        player.currentTime = 0; player.play()
    }
    func sample(_ date: Date, autoPlay: Bool, reduced: Bool, pinned: Double?) -> M.Frame {
        if let pinned { return M.at(pinned, reduced) }
        // Wall time drives the script even on slow Xcode Cloud renderers. Only physics clamps dt.
        let elapsed = min(0.5, max(0, self.date.map { date.timeIntervalSince($0) } ?? 0.016))
        let dt = min(elapsed, 0.05)
        self.date = date
        now += elapsed
        let auto = autoPlay && !reduced
        let scripted = auto && !held && now-last >= 2
        if scripted {
            let before = time
            time = (time+elapsed).truncatingRemainder(dividingBy: M.loop)
            let f = M.at(time, reduced)
            from = f.from; to = f.to; m = f.m; flash = f.flash
            if !held { yaw = f.yaw }
            if DemoSound.inAutoPlay {
                for (beat, name) in [(1.2,"thunder"),(2.6,"morph"),(5.6,"morph"),(8.0,"morph")] {
                    if (before <= time && before < beat && time >= beat) || (before > time && (before < beat || time >= beat)) { cue(name, reduced) }
                }
            }
        } else {
            m = reduced ? 1 : M.ease((now-morphAt)/M.morph)
            if to == 0 && !reduced && now > nextFlash {
                nextFlash = now+3.2+Double.random(in: 0..<2)
                flash = 1; cue("thunder", reduced)
            }
            flash *= exp(-dt*9)
            if !held {
                velocity += (-120*yaw-2*sqrt(120)*0.35*velocity)*dt
                yaw += velocity*dt
            }
        }
        return .init(from: from, to: to, m: m, yaw: yaw,
                     time: time+(auto ? 0 : now), phase: auto ? time : now,
                     flash: to == 0 ? flash : 0)
    }
    func pick(_ i: Int, reduced: Bool) {
        last = now
        guard i != to else { return }
        from = m >= 0.5 ? to : from
        to = i; morphAt = now; m = reduced ? 1 : 0; nextFlash = now+1.2
        cue("morph", reduced); selection.selectionChanged(); revision += 1
    }
    func touch(_ state: UIGestureRecognizer.State, translation: Double, velocity: Double) {
        switch state {
        case .began: held = true; startYaw = yaw; last = now
        case .changed: last = now; yaw = max(-0.9,min(0.9,startYaw+translation/220))
        case .ended, .cancelled, .failed: held = false; self.velocity = velocity/220; last = now
        default: break
        }
        revision += 1
    }
}

struct StormCloudWeatherTouchView: UIViewRepresentable {
    let touch: (UIGestureRecognizer.State, Double, Double) -> Void
    func makeUIView(context: Context) -> StormCloudWeatherTouchSurface {
        let view = StormCloudWeatherTouchSurface()
        view.backgroundColor = .clear; view.touch = touch
        return view
    }
    func updateUIView(_ uiView: StormCloudWeatherTouchSurface, context: Context) { uiView.touch = touch }
}

// Begin on finger-down (Gesture.Pan.onBegin), activate translation at exactly 4 pt.
final class StormCloudWeatherTouchSurface: UIView, UIGestureRecognizerDelegate {
    var touch: ((UIGestureRecognizer.State, Double, Double) -> Void)?
    private var start = CGPoint.zero, lastPoint = CGPoint.zero
    private var lastTime = 0.0, velocity = 0.0
    private var activated = false
    private var nativeVelocity = 0.0, hasNativeVelocity = false
    private lazy var panVelocity: UIPanGestureRecognizer = {
        let pan = UIPanGestureRecognizer(target: self, action: #selector(trackVelocity(_:)))
        pan.cancelsTouchesInView = false
        pan.delaysTouchesBegan = false
        pan.delaysTouchesEnded = false
        pan.delegate = self
        return pan
    }()
    override init(frame: CGRect) {
        super.init(frame: frame)
        addGestureRecognizer(panVelocity)
    }
    required init?(coder: NSCoder) {
        super.init(coder: coder)
        addGestureRecognizer(panVelocity)
    }
    override func didMoveToWindow() {
        super.didMoveToWindow()
        // SwiftUI's overlay preference can arrive after the initial UIKit presentation.
        // Ask the hosting controller to re-read it once the window has settled.
        guard window != nil else { return }
        for delay in [0.0, 1.0, 3.0] {
            DispatchQueue.main.asyncAfter(deadline: .now()+delay) { [weak self] in
                guard let root = self?.window?.rootViewController else { return }
                root.setNeedsUpdateOfHomeIndicatorAutoHidden()
            }
        }
    }
    @objc private func trackVelocity(_ pan: UIPanGestureRecognizer) {
        nativeVelocity = pan.velocity(in: self).x
        hasNativeVelocity = true
    }
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                           shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let t = touches.first else { return }
        start = t.location(in: self); lastPoint = start; lastTime = t.timestamp
        velocity = 0; nativeVelocity = 0; hasNativeVelocity = false
        activated = false; touch?(.began,0,0)
    }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let t = touches.first else { return }
        let point = t.location(in:self)
        let dt = t.timestamp-lastTime
        if dt > 0 { velocity = (point.x-lastPoint.x)/dt }
        if hypot(point.x-start.x,point.y-start.y) >= 4 { activated = true }
        if activated { touch?(.changed,point.x-start.x,velocity) }
        lastPoint = point; lastTime = t.timestamp
    }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let t = touches.first else { return }
        if t.timestamp-lastTime > 0.1 { velocity = 0 }
        // RN's iOS Pan event uses UIKit's filtered velocity. A sub-threshold recognizer
        // still has the exact 4 pt translation path above and the sampled fallback.
        let release = hasNativeVelocity ? nativeVelocity : velocity
        touch?(.ended,t.location(in:self).x-start.x,activated ? release:0)
    }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        touch?(.cancelled,lastPoint.x-start.x,hasNativeVelocity ? nativeVelocity : velocity)
    }
}
