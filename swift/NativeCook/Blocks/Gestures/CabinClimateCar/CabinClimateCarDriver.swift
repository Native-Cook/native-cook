import Foundation

final class CabinClimateCarDriver {
  typealias M = CabinClimateCarMotion
  var t = 0.0, last = -10.0, amb = 0.0
  var mode = 0
  var temp = 70.0, from = 70.0, tempAt = -10.0, heatFrom = 70.0
  var leg = false
  var legAt = -10.0, legFrom = 0.0
  var fan = 0, fanAt = -10.0, pressing = 0
  var pressAt = -10.0, releaseAt = -10.0, repeatAt = -10.0
  var released = 0, homing = 0
  func takeOver(_ now: Double) {
    last = now
    homing = 0
    if mode == 2 { return }
    let f = M.frameAt(t)
    mode = 2
    temp = f.temp
    from = f.from
    tempAt = now - f.tempAt
    heatFrom = f.from
    leg = f.legOn
    legAt = now - f.legAt
    legFrom = f.legOn ? 0 : 1
    fan = 0
  }
  func heatOf(_ now: Double) -> Double { M.mix(heatFrom, temp, M.easeOut((now - tempAt) / 0.9)) }
  @discardableResult func setTemp(_ value: Double, _ now: Double) -> Bool {
    let next = max(60, min(80, value))
    if next == temp { return false }
    heatFrom = heatOf(now)
    from = temp
    temp = next
    tempAt = now
    return true
  }
  func toggleLeg(_ now: Double) {
    leg.toggle()
    legAt = now
  }
  func step(_ dt: Double, _ now: Double, _ auto: Bool) -> Bool {
    amb = (amb + dt).truncatingRemainder(dividingBy: M.LOOP)
    if mode == 0 {
      if auto { t = (t + dt).truncatingRemainder(dividingBy: M.LOOP) }
      return false
    }
    if (pressing == 1 || pressing == 2) && now - pressAt > 0.45 && now - repeatAt > 0.16 {
      repeatAt = now
      return setTemp(temp + (pressing == 1 ? 1 : -1), now)
    }
    if !auto || pressing > 0 || now - last < M.RESUME { return false }
    homing = 1
    if leg { toggleLeg(now) }
    if fan != 0 {
      fan = 0
      fanAt = now
    }
    if temp != 70 && now - tempAt > 0.22 {
      setTemp(temp + (temp > 70 ? -1 : 1), now)
      return false
    }
    if temp == 70 && now - tempAt > 1 && now - legAt > 0.8 && now - fanAt > 0.5 {
      mode = 0
      t = 0
      homing = 0
    }
    return false
  }
  func frame(_ now: Double) -> M.Frame {
    if mode == 0 { return M.frameAt(t) }
    func pk(_ id: Int) -> Double {
      pressing == id
        ? M.smooth((now - pressAt) / 0.15)
        : released == id ? 1 - M.smooth((now - releaseAt) / 0.15) : 0
    }
    let lk = M.spring(now - legAt)
    let card = (3...5).contains(pressing) ? pressing : released
    return M.Frame(
      amb: amb, temp: temp, from: from, tempAt: now - tempAt, heat: heatOf(now),
      plusK: pk(1), minusK: pk(2), leg: leg ? lk : 1 - lk, legOn: leg,
      legAt: now - legAt, fan: fan, fanAt: now - fanAt,
      cardK: (3...5).contains(card) ? pk(card) : 0, card: card)
  }
}
