import SwiftUI
import AVFoundation
import UIKit

/// The RN worklet driver. Only scalar invalidation is published; frame data stays off @State.
final class WindChimeTonesClock: ObservableObject {
    typealias M = WindChimeTonesMotion
    @Published var running = false
    @Published var isOn = true
    private var date: Date?
    private var now = 0.0, autoStart = 0.0, lastTouch = -100.0
    private var manual = false
    private var playStart = 0.0, pausedAt = -1.0, pauseClock = 0.0
    private var powerFrom = 1.0, powerTarget = 1.0, powerAt = -100.0
    private var fingerFrom = 0.0, fingerAt = -100.0
    private var frame = M.Frame()
    private var player: AVAudioPlayer?
    private var recordingPlaying = false
    private let selection = UISelectionFeedbackGenerator()
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private var pinnedCache: (Double, M.Frame)?

    func reset(_ auto: Bool) {
        date=nil; now=0; autoStart=0; lastTouch = -100; manual = !auto
        playStart=0; pausedAt = -1; pauseClock=0
        powerFrom=1; powerTarget=1; powerAt = -100; frame=M.Frame()
        isOn=true; running=false; recordingPlaying=false; player?.pause()
        if player == nil {
            try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: .mixWithOthers)
            if let url = Bundle.main.url(forResource: "WindChimeTones-wct-chimes", withExtension: "wav"),
               let audio = try? AVAudioPlayer(contentsOf: url) {
                audio.numberOfLoops = -1; audio.volume=0.8; audio.prepareToPlay(); player=audio
            }
        }
    }
    func configure(_ auto: Bool) {
        autoStart=now; date=nil
        if auto { manual=false; return }
        manual=true; frame.playing=false; pausedAt = -1; frame.finger=0
        fingerFrom=0; fingerAt = -100
        running=frame.glow > 0.000001 || now < powerAt+0.4
        player?.pause(); recordingPlaying=false
    }
    func active(_ active: Bool) {
        date=nil
        if !active { player?.pause(); recordingPlaying=false }
        else if manual && frame.playing { audio(true, (now-playStart).truncatingRemainder(dividingBy: M.piece)) }
    }
    private func audio(_ on: Bool, _ at: Double) {
        guard on else { player?.pause(); return }
        player?.currentTime=at; player?.play()
    }
    func sample(_ date: Date, _ auto: Bool, _ reduced: Bool, _ pin: Double?) -> M.Frame {
        if let pin {
            if pinnedCache?.0 != pin { pinnedCache=(pin,M.pinned(pin)) }
            return pinnedCache!.1
        }
        // The demo recording must follow wall time even on a slow, GPU-less simulator.
        let elapsed=min(0.5,max(0,self.date.map { date.timeIntervalSince($0) } ?? 0.016))
        self.date=date; now += elapsed
        let dt=min(0.05,elapsed) // Only the RN glow integrator keeps its 50 ms clamp.
        if !manual && auto {
            let glow=frame.glow
            frame=M.frameAt((now-autoStart).truncatingRemainder(dividingBy: M.loop))
            frame.glow=glow
        } else if frame.playing {
            let t=now-playStart
            frame.music=t.truncatingRemainder(dividingBy:M.piece); frame.cut=M.piece; frame.laps=t >= M.piece ? 2 : 1
        } else if pausedAt >= 0 {
            frame.music=pausedAt.truncatingRemainder(dividingBy:M.piece)+(now-pauseClock)
            frame.cut=pausedAt.truncatingRemainder(dividingBy:M.piece); frame.laps=pausedAt >= M.piece ? 2 : 1
        } else { frame.music = -1 }
        if manual {
            frame.power=powerFrom+(powerTarget-powerFrom)*M.timing(M.seg(now,powerAt,powerAt+0.4))
            frame.finger=fingerFrom*(1-M.timing(M.seg(now,fingerAt,fingerAt+0.15)))
        }
        frame.time=now.truncatingRemainder(dividingBy:600)
        frame.glow += (min(1,abs(frame.kick))-frame.glow)*min(1,dt*6)
        let recording = DemoSound.inAutoPlay && auto && !manual && frame.playing && frame.music >= 0
        if recording && !recordingPlaying { audio(true,max(0,frame.music)) }
        if !recording && recordingPlaying { audio(false,0) }
        recordingPlaying=recording
        if manual && auto && !frame.playing && now-lastTouch >= 2 && (frame.music < 0 || frame.music-frame.cut > 2.4) {
            pausedAt = -1; frame.power=1; powerFrom=1; powerTarget=1; autoStart=now; manual=false
        }
        if manual && !auto && !frame.playing && now > powerAt+0.4 && now > fingerAt+0.15 && (frame.music < 0 || frame.music-frame.cut > 2.4) && frame.glow < 0.000001 && running {
            DispatchQueue.main.async { [weak self] in self?.running=false }
        }
        return frame
    }
    private func takeOver() {
        if !running { date=nil }
        lastTouch=now
        if !manual {
            manual=true; fingerFrom=frame.finger; fingerAt=now; frame.press=0
            pausedAt=frame.music >= 0 ? min(frame.music,frame.cut) : -1
            pauseClock=now; frame.playing=false
            if recordingPlaying { audio(false,0); recordingPlaying=false }
        }
        running=true
    }
    private func setPlay(_ on: Bool) {
        if on {
            let from=pausedAt >= 0 ? pausedAt : 0
            playStart=now-from; pausedAt = -1; frame.playing=true
            audio(true,from.truncatingRemainder(dividingBy:M.piece))
        } else if frame.playing {
            pausedAt=now-playStart; pauseClock=now; frame.playing=false; audio(false,0)
        }
        selection.selectionChanged()
    }
    func togglePlay() {
        takeOver()
        if frame.power < 0.5 { return }
        setPlay(!frame.playing)
        objectWillChange.send()
    }
    func togglePower() {
        medium.impactOccurred()
        isOn.toggle(); takeOver()
        powerFrom=frame.power; powerTarget=isOn ? 1 : 0; powerAt=now
        if !isOn { setPlay(false) }
    }
}
