import SwiftUI
import UIKit

final class JellyScaleExchangeDriver: ObservableObject {
  typealias M = JellyScaleExchangeMotion
  @Published var automatic = false
  @Published var epoch = Date()
  @Published var base = M.Frame()
  @Published var pouring = false
  @Published var pourStart = Date()
  @Published var exchangeStart: Date?
  @Published var reduced = false
  @Published var pressPour = 0.0
  @Published var pressSwap = 0.0
  var autoPlay = false
  private var resume: DispatchWorkItem?
  private var landing: DispatchWorkItem?
  private var beanTimer: Timer?
  private var lastCount = 0
  private var takeoverAt: Date?
  private var takeoverFinger = 0.0
  private var pourPressFrom = 0.0, swapPressFrom = 0.0
  private var pourPressAt = Date.distantPast, swapPressAt = Date.distantPast
  private var pourPressDuration = 0.15, swapPressDuration = 0.15
  private var pourPressDefault = false, swapPressDefault = false
  private func pressValue(_ now: Date, pour: Bool) -> Double {
    let u = M.clamp01(now.timeIntervalSince(pour ? pourPressAt : swapPressAt)/(pour ? pourPressDuration : swapPressDuration))
    let defaultCurve = u < 0.5 ? 2*u*u : 1-pow(-2*u+2,2)/2
    let eased = (pour ? pourPressDefault : swapPressDefault) ? defaultCurve : M.bezier(u,0.23,1,0.32,1)
    let from = pour ? pourPressFrom : swapPressFrom, to = pour ? pressPour : pressSwap
    return from+(to-from)*eased
  }
  private func setPress(pour: Bool, to value: Double, duration: Double, defaultCurve: Bool = false) {
    let now = Date(), from = pressValue(Date(),pour:pour)
    if pour { pourPressFrom = from; pourPressAt = now; pourPressDuration = duration; pourPressDefault = defaultCurve; pressPour = value }
    else { swapPressFrom = from; swapPressAt = now; swapPressDuration = duration; swapPressDefault = defaultCurve; pressSwap = value }
    DispatchQueue.main.asyncAfter(deadline:.now()+duration) { [weak self] in self?.objectWillChange.send() }
  }
  private var exchangeBase = M.Frame()
  private var roundingStart = Date()
  private var roundFrom = 0.0
  private var landedAt: Date?
  var busy: Bool { exchangeStart != nil }
  func configure(autoPlay: Bool, reduced: Bool) {
    self.autoPlay = autoPlay; self.reduced = reduced
    automatic = autoPlay && !reduced; epoch = Date()
  }
  var pin: Double? {
    #if DEBUG
    if UserDefaults.standard.object(forKey: "ncPinTime") != nil { return UserDefaults.standard.double(forKey: "ncPinTime") }
    #endif
    return nil
  }
  var moving: Bool { automatic || pouring || busy || Date().timeIntervalSince(pourPressAt) < pourPressDuration || Date().timeIntervalSince(swapPressAt) < swapPressDuration || (takeoverAt.map { Date().timeIntervalSince($0) < 0.15 } ?? false) || (landedAt.map { Date().timeIntervalSince($0) < 0.9 } ?? false) }
  func frame(_ now: Date) -> M.Frame {
    if automatic { return M.frameAt(pin ?? now.timeIntervalSince(epoch).truncatingRemainder(dividingBy: M.loop)) }
    var f = base
    let fadeU = takeoverAt.map { M.seg(now.timeIntervalSince($0),0,0.15) } ?? 1
    let fadeEase = fadeU < 0.5 ? 2*fadeU*fadeU : 1-pow(-2*fadeU+2,2)/2
    f.finger = takeoverFinger*(1-fadeEase)
    f.pressPour = pressValue(now,pour:true); f.pressSwap = pressValue(now,pour:false)
    if pouring { f.pour = min(M.beans,base.pour+max(0,now.timeIntervalSince(pourStart))*(reduced ? 12 : 6)) }
    if let start = exchangeStart {
      let t = max(0,now.timeIntervalSince(start)), m = reduced ? 0.3 : 1.1, c = reduced ? 0.3 : 0.8, flight = reduced ? 0.3 : 0.7
      func e(_ v: Double) -> Double { M.bezier(M.clamp01(v),0.77,0,0.175,1) }
      // withTiming resumes each property from its takeover value for the full RN duration.
      let rounding = M.seg(now.timeIntervalSince(roundingStart),0,0.2)
      let easedRounding = rounding < 0.5 ? 2*rounding*rounding : 1-pow(-2*rounding+2,2)/2
      f.pour = roundFrom+(ceil(roundFrom)-roundFrom)*easedRounding
      f.melt = exchangeBase.melt+(1-exchangeBase.melt)*e(t/m)
      if t >= m { f.coin = exchangeBase.coin+(1-exchangeBase.coin)*e((t-m)/c) }
      if t >= m+c {
        f.fly = exchangeBase.fly+(1-exchangeBase.fly)*e((t-m-c)/flight)
        if !reduced { f.spin = exchangeBase.spin+(2 * .pi-exchangeBase.spin)*e((t-m-c)/0.9) }
      }
    }
    if let at = landedAt { let t = now.timeIntervalSince(at); f.wobbleU = M.seg(t,0,0.7); f.plus = M.seg(t,0,0.9) }
    return f
  }
  func takeOver() {
    resume?.cancel()
    if automatic {
      base = frame(Date()); automatic = false
      takeoverAt = Date(); takeoverFinger = base.finger
      pourPressFrom = base.pressPour; pressPour = base.pressPour; pourPressAt = .distantPast
      swapPressFrom = base.pressSwap; pressSwap = base.pressSwap; swapPressAt = .distantPast
      setPress(pour:true,to:0,duration:0.15,defaultCurve:true)
      setPress(pour:false,to:0,duration:0.15,defaultCurve:true)
      if base.melt > 0.001 && base.fly < 0.999 { exchange() }
    }
    scheduleResume()
  }
  func beginPour() {
    takeOver(); guard !busy else { return }
    base = frame(Date()); pourStart = Date(); pouring = true; landedAt = nil
    setPress(pour:true,to:1,duration:0.1)
    lastCount = Int(floor(base.pour))
    beanTimer?.invalidate()
    beanTimer = Timer.scheduledTimer(withTimeInterval: 1/60, repeats: true) { [weak self] _ in
      guard let self else { return }
      let count = Int(floor(self.frame(Date()).pour))
      if count != self.lastCount { UISelectionFeedbackGenerator().selectionChanged(); self.lastCount = count }
    }
  }
  func endPour() {
    if pouring { base = frame(Date()); pouring = false }
    beanTimer?.invalidate(); beanTimer = nil
    setPress(pour:true,to:0,duration:0.15)
    scheduleResume()
  }
  func beginSwap() { takeOver(); setPress(pour:false,to:1,duration:0.1) }
  func endSwap(activate: Bool) {
    if activate { exchange() }
    setPress(pour:false,to:0,duration:0.15)
    scheduleResume()
  }
  func exchange() {
    guard !busy else { return }
    let f = frame(Date()); guard f.pour >= 0.5 else { return }
    base = f; pouring = false; beanTimer?.invalidate(); beanTimer = nil; landedAt = nil
    roundFrom = f.pour; roundingStart = Date(); exchangeBase = f; exchangeStart = Date()
    resume?.cancel(); landing?.cancel()
    let work = DispatchWorkItem { [weak self] in
      guard let self else { return }
      self.base = M.Frame(); self.exchangeStart = nil; self.landedAt = Date()
      let landed = self.landedAt
      DispatchQueue.main.asyncAfter(deadline:.now()+0.9) { [weak self] in
        guard let self, self.landedAt == landed else { return }
        self.landedAt = nil; self.base.wobbleU = 1; self.base.plus = 1
      }
      UINotificationFeedbackGenerator().notificationOccurred(.success)
      self.scheduleResume(after: 2.9)
    }
    landing = work; DispatchQueue.main.asyncAfter(deadline:.now()+(reduced ? 0.9 : 2.6),execute:work)
  }
  func scheduleResume(after delay: Double = 2) {
    resume?.cancel(); guard autoPlay && !reduced else { return }
    let work = DispatchWorkItem { [weak self] in
      guard let self, !self.pouring, !self.busy else { return }
      if self.base.pour > 0.001 { self.exchange() }
      else { self.base = M.Frame(); self.landedAt = nil; self.epoch = Date(); self.automatic = true }
    }
    resume = work; DispatchQueue.main.asyncAfter(deadline:.now()+delay,execute:work)
  }
  func stop() { beanTimer?.invalidate(); landing?.cancel(); resume?.cancel() }
}
