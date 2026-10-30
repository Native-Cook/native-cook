import Foundation

/// Seven bodies and six stars: two Euler substeps, with the same forces and ordering as RN.
struct GhostFloatWelcomeDriver {
  typealias M = GhostFloatWelcomeMotion
  typealias Theme = GhostFloatWelcomeTheme
  var t = 0.0, mode = 0, last = -10.0, amb = 0.0
  var ox = [Double](repeating: 0, count: 13), oy = [Double](repeating: 0, count: 13)
  var vx = [Double](repeating: 0, count: 13), vy = [Double](repeating: 0, count: 13)
  var or = [Double](repeating: 0, count: 13), vr = [Double](repeating: 0, count: 13)
  var down = 0, fx = 0.0, fy = 0.0, fvx = 0.0, fvy = 0.0
  var grab = -1, gdx = 0.0, gdy = 0.0
  var booAt = -1.0, rollAt = -1.0
  var agree = -1.0, agreeAim = 0.0, btnPress = 0.0, btnAim = 0.0, noAt = -1.0
  var lookX = 0.0, lookY = 0.0

  mutating func touched(_ now: Double) {
    last = now
    mode = 2
  }
  mutating func step(_ dt: Double, _ now: Double, _ auto: Bool) {
    if mode == 2 {
      if auto && down == 0 && now - last >= M.RESUME {
        mode = 0
        agree = -1
      }
    } else if auto {
      t = (t + dt).truncatingRemainder(dividingBy: M.LOOP)
    }
    amb = (amb + dt).truncatingRemainder(dividingBy: M.LOOP)
    if agree >= 0 { agree = M.follow(agree, agreeAim, 16, dt) }
    btnPress = M.follow(btnPress, btnAim, btnAim > 0 ? 40 : 16, dt)
    let spring = 38.0
    let damp = 2 * 0.5 * sqrt(38.0)
    let h = dt / 2
    for _ in 0..<2 {
      for j in 0..<13 {
        let home = M.liveHome(j, t, amb)
        let x = home.x + ox[j]
        let y = home.y + oy[j]
        if j == grab {
          let tx = fx - gdx - home.x
          let ty = fy - gdy - home.y
          vx[j] = (tx - ox[j]) / max(h, 0.004) * 0.35
          vy[j] = (ty - oy[j]) / max(h, 0.004) * 0.35
          ox[j] += (tx - ox[j]) * 0.35
          oy[j] += (ty - oy[j]) * 0.35
          vr[j] += (fvx * 0.08 - vr[j]) * 0.2
          or[j] += vr[j] * h
          continue
        }
        var ax = -spring * ox[j] - damp * vx[j]
        var ay = -spring * oy[j] - damp * vy[j]
        if down > 0 && grab < 0 {
          let dx = x - fx
          let dy = y - fy
          let dist = hypot(dx, dy) == 0 ? 1 : hypot(dx, dy)
          if dist < 150 {
            let weight = pow(1 - dist / 150, 2)
            let speed = min(1600, hypot(fvx, fvy))
            let tx = -dy / dist
            let ty = dx / dist
            let side = fvx * tx + fvy * ty >= 0 ? 1.0 : -1.0
            let gain = 5.5 / home.mass
            ax += weight * gain * (fvx * 0.9 + tx * speed * 0.7 * side)
            ay += weight * gain * (fvy * 0.9 + ty * speed * 0.7 * side)
            let near = home.r + 26
            if dist < near {
              ax += dx / dist * (near - dist) * 260
              ay += dy / dist * (near - dist) * 260
            }
            vr[j] += side * weight * speed * 0.04 * h * 60
          }
        }
        vx[j] += ax * h
        vy[j] += ay * h
        let speed = hypot(vx[j], vy[j])
        if speed > 2400 {
          vx[j] *= 2400 / speed
          vy[j] *= 2400 / speed
        }
        ox[j] += vx[j] * h
        oy[j] += vy[j] * h
        vr[j] += (-30 * or[j] - 2 * 0.45 * sqrt(30) * vr[j]) * h
        or[j] += vr[j] * h
        let px = home.x + ox[j]
        let py = home.y + oy[j]
        let radius = home.r
        if px < radius * 0.4 && vx[j] < 0 { vx[j] *= -0.6 }
        if px > Theme.width - radius * 0.4 && vx[j] > 0 { vx[j] *= -0.6 }
        if py < 60 + radius && vy[j] < 0 { vy[j] *= -0.6 }
        if py > Theme.fieldBottom - radius && vy[j] > 0 { vy[j] *= -0.6 }
      }
      // A soft collision leaves the home springs in charge in the long run.
      for a in 0..<7 {
        for b in (a + 1)..<7 {
          let ha = M.liveHome(a, t, amb)
          let hb = M.liveHome(b, t, amb)
          let dx = hb.x + ox[b] - (ha.x + ox[a])
          let dy = hb.y + oy[b] - (ha.y + oy[a])
          let dist = hypot(dx, dy) == 0 ? 1 : hypot(dx, dy)
          let minimum = ha.r + hb.r + 3
          if dist >= minimum { continue }
          let force = min(9000, (minimum - dist) * 420) * h
          let ux = dx / dist
          let uy = dy / dist
          if a != grab {
            vx[a] -= ux * force
            vy[a] -= uy * force
          }
          if b != grab {
            vx[b] += ux * force
            vy[b] += uy * force
          }
        }
      }
    }
    var lx = 0.35 * sin(M.tau * amb / M.LOOP)
    var ly = 0.25 * sin(M.tau * 2 * amb / M.LOOP + 1)
    let ghost = M.liveHome(0, t, amb)
    let gx = ghost.x + ox[0]
    let gy = ghost.y + oy[0]
    if down > 0 && grab != 0 {
      lx = (fx - gx) / 120
      ly = (fy - gy) / 120
    } else {
      var best = 260.0
      for j in 1..<7 {
        let speed = hypot(vx[j], vy[j])
        if speed > best {
          best = speed
          let home = M.liveHome(j, t, amb)
          lx = (home.x + ox[j] - gx) / 120
          ly = (home.y + oy[j] - gy) / 120
        }
      }
    }
    let length = hypot(lx, ly)
    if length > 1 {
      lx /= length
      ly /= length
    }
    lookX = M.follow(lookX, lx, 9, dt)
    lookY = M.follow(lookY, ly, 9, dt)
  }
  mutating func boo(_ now: Double) {
    booAt = now
    let ghost = M.liveHome(0, t, amb)
    let gx = ghost.x + ox[0]
    let gy = ghost.y + oy[0]
    for j in 1..<13 {
      let home = M.liveHome(j, t, amb)
      let dx = home.x + ox[j] - gx
      let dy = home.y + oy[j] - gy
      let dist = hypot(dx, dy) == 0 ? 1 : hypot(dx, dy)
      let strength = max(160, 900 - dist * 1.6) / (j < 7 ? 1 : 0.6)
      vx[j] += dx / dist * strength
      vy[j] += dy / dist * strength
      vr[j] += (j % 2 == 0 ? 1 : -1) * 260
    }
  }
  mutating func extra(_ now: Double) -> GhostFloatWelcomeLive {
    var live = GhostFloatWelcomeLive()
    live.amb = amb
    live.ox = ox
    live.oy = oy
    live.vx = vx
    live.vy = vy
    live.or = or
    live.lookX = lookX
    live.lookY = lookY
    live.agree = agree
    live.btnPress = btnPress
    if booAt >= 0 {
      let p = now - booAt
      if p > 1.6 {
        booAt = -1
      } else {
        live.puff = M.wobble(p, 0.34, 0.24)
        live.ring = p < 0.65 ? p / 0.65 : -1
        live.wide = 1 - M.seg(p, 0.3, 0.9)
      }
    }
    if rollAt >= 0 {
      let p = (now - rollAt) / 0.75
      if p >= 1 {
        rollAt = -1
      } else {
        live.roll = 360 * M.easeInOut(p)
        live.hop = 22 * sin(Double.pi * p)
      }
    }
    if noAt >= 0 {
      let p = now - noAt
      if p > 0.6 { noAt = -1 } else { live.no = exp(-p / 0.16) * sin(M.tau * p / 0.12) }
    }
    live.noP = noAt >= 0 ? (now - noAt) / 0.6 : -1
    return live
  }
}
