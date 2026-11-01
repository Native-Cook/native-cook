import AVFoundation
import SwiftUI
import UIKit

final class HoloCashCardClock: ObservableObject {
    var state = HoloCashCardMotion.State()
    private var date: Date?
    private var pinnedTime: Double?
    private var pinnedState = HoloCashCardMotion.State()
    private var players: [String: AVAudioPlayer] = [:]
    private var reduced = false
    private var frozen = false
    @Published var revision = 0
    func active(_ active: Bool, _ reduced: Bool, _ pinned: Bool) {
        date = nil; self.reduced = reduced; frozen = pinned
        if active && !reduced && !pinned && players.isEmpty {
            try? AVAudioSession.sharedInstance().setCategory(.ambient, mode:.default, options:.mixWithOthers)
            for name in ["shimmer","boost","tap"] {
                if let url=Bundle.main.url(forResource:"HoloCashCard-hcc-\(name)",withExtension:"wav"),
                   let player=try? AVAudioPlayer(contentsOf:url) {
                    player.volume=name=="tap" ? 0.5:0.7; player.prepareToPlay(); players[name]=player
                }
            }
        }
        if !active || reduced || pinned { for player in players.values { player.stop() } }
    }
    func sample(_ at: Date, _ auto: Bool, _ reduced: Bool, _ pinned: Double?) -> HoloCashCardMotion.State {
        if let pinned {
            if pinnedTime != pinned { pinnedTime=pinned; pinnedState=HoloCashCardMotion.at(pinned) }
            return pinnedState
        }
        guard let previous=date else { date=at; return state }
        // Advance the script by elapsed wall time, including on slow recording simulators.
        let dt=min(max(0,at.timeIntervalSince(previous)),0.5); date=at
        if dt>0 {
            let steps=max(1,Int(ceil(dt*60))), h=dt/Double(steps)
            for _ in 0..<steps {
                let before=state.time
                state.step(h,auto:auto,reduced:reduced)
                // Same opt-in as Shared/DemoSound.inAutoPlay. The standalone port gate
                // includes only the minimal Shared files, so read its launch setting here.
                if auto && !reduced && !(UserDefaults.standard.string(forKey:"recordingSound") ?? "").isEmpty {
                    for (beat,name) in [(1.0,"shimmer"),(3.2,"boost"),(6.4,"tap")] {
                        if before < beat && state.time >= beat { cue(name) }
                    }
                }
            }
        }
        return state
    }
    private func cue(_ name: String) {
        guard !reduced, let p=players[name] else { return }
        p.currentTime=0; p.play()
    }
    private func buzz(_ kind: Int) {
        if kind==0 { UISelectionFeedbackGenerator().selectionChanged() }
        else if kind==2 { UINotificationFeedbackGenerator().notificationOccurred(.success) }
        else { UIImpactFeedbackGenerator(style:.light).impactOccurred() }
    }
    func cardTouch(_ phase: UIGestureRecognizer.State, _ translation: CGSize) {
        guard !frozen else { return }
        state.last=state.clock
        switch phase {
        case .began:
            state.on=true; state.son=false; state.tx=state.rx; state.ty=state.ry; cue("shimmer")
        case .changed:
            state.tx=max(-16,min(16,-translation.height*0.14))
            state.ty=max(-16,min(16,translation.width*0.12))
        case .ended,.cancelled,.failed: state.on=false; buzz(1)
        default: break
        }
        revision += 1
    }
    func toggleBoost() {
        guard !frozen else { return }
        state.last=state.clock; state.setBoost(!state.boost)
        cue(state.boost ? "boost":"tap"); buzz(state.boost ? 2:0); revision += 1
    }
    func addFifty() {
        guard !frozen else { return }
        state.last=state.clock; state.extra += 5000000; state.value += 5000000
        cue("tap"); buzz(2); revision += 1
    }
    func toggleHidden() {
        guard !frozen else { return }
        state.hideTarget=state.hideTarget==0 ? 1:0
        buzz(0); revision += 1
    }
}

struct HoloCashCardTouchView: UIViewRepresentable {
    let event: (UIGestureRecognizer.State,CGSize)->Void
    func makeUIView(context: Context) -> HoloCashCardTouchSurface {
        let v=HoloCashCardTouchSurface(); v.event=event; return v
    }
    func updateUIView(_ view: HoloCashCardTouchSurface, context: Context) { view.event=event }
}
final class HoloCashCardTouchSurface: UIView, UIGestureRecognizerDelegate {
    var event: ((UIGestureRecognizer.State,CGSize)->Void)?
    private var start=CGPoint.zero
    override init(frame:CGRect) {
        super.init(frame:frame); backgroundColor = .clear; isAccessibilityElement=false
        let g=UILongPressGestureRecognizer(target:self,action:#selector(handle(_:)))
        g.minimumPressDuration=0; g.allowableMovement = .greatestFiniteMagnitude; g.delegate=self
        addGestureRecognizer(g)
    }
    @available(*, unavailable) required init?(coder:NSCoder) { nil }
    func gestureRecognizer(_ gestureRecognizer:UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer:UIGestureRecognizer)->Bool { true }
    @objc private func handle(_ g:UILongPressGestureRecognizer) {
        let p=g.location(in:self)
        if g.state == .began { start=p }
        event?(g.state,CGSize(width:p.x-start.x,height:p.y-start.y))
    }
}
