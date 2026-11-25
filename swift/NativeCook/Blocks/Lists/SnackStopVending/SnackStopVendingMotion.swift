import Foundation

/// Absolute-time beats from the committed RN reference; no accumulated physics.
enum SnackStopVendingMotion {
    static let keys = ["A", "B", "C", "D", "1", "2", "3", "4", "5", "6", "0", "✕"]
    static let letters = ["A", "B", "C", "D"]
    static let drinks = [["Lime Fizz", "Sparkling · 330 ml", "$1.50"], ["Pink Pop", "Sparkling · 330 ml", "$1.75"], ["Violet Dusk", "Still · 330 ml", "$1.75"], ["Tangerine Twist", "Sparkling · 330 ml", "$1.50"], ["Sky Splash", "Still · 330 ml", "$1.25"]]
    static func color(_ s: Int) -> Int { (2*(s/5)+s%5)%5 }
    static func code(_ s: Int) -> String { letters[s/5]+String(s%5+1) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { min(1, max(0, (t-a)/(b-a))) }
    static func io(_ u: Double) -> Double { u < 0.5 ? 2*u*u : 1-pow(-2*u+2, 2)/2 }
    static func out(_ u: Double) -> Double { 1-pow(1-u, 3) }
    static func phase(_ t: Double) -> Double { (t.truncatingRemainder(dividingBy: 8)+8).truncatingRemainder(dividingBy: 8) }
    struct Can {
        var kind = 0, fall = 0.0, drop = 0.0, bounce = 0.0, rot = 0.0
        var scale = 1.0, alpha = 1.0, taken = 0.0
        var clip = false, veil = false
    }
    struct Frame {
        var slot = -1, can = Can(), glint = -1.0, trayAlpha = 0.0
        var flashA = -1, flashAU = -1.0, flashB = -1, flashBU = -1.0
        var fingerKey = 0, finger = 0.0, lcd = "", lcdShake = 0.0
        var card = false, pill = 0, cardSlot = -1
    }
    static func canAt(_ t: Double) -> Can {
        var c = Can()
        if t < 2 || t >= 7 { return c }
        if t >= 6.4 {
            let r = out(seg(t, 6.4, 7))
            c.kind = 2; c.scale = 0.85+0.15*r; c.alpha = r; return c
        }
        c.kind = 1; c.scale = 1+0.12*io(seg(t, 2, 2.8))
        if t >= 2.8 { c.rot = 0.35*pow(seg(t, 2.8, 3.15), 2); c.scale = 1.12 }
        if t >= 3.15 {
            c.fall = seg(t, 3.15, 3.6); c.drop = c.fall*c.fall
            c.rot = 0.35+(.pi/2-0.35)*c.fall
        }
        if t >= 3.6 {
            let b = seg(t, 3.6, 3.95)
            c.bounce = abs(exp(-4*b)*sin(3 * .pi*b)*8)
            c.rot = .pi/2; c.fall = 1; c.drop = 1
        }
        if t >= 5.8 { let k = seg(t, 5.8, 6.3); c.taken = 40*io(k); c.alpha = 1-k }
        c.clip = t >= 3.45; c.veil = c.clip; return c
    }
    static func glint(_ t: Double) -> Double { let u = seg(t, 2, 2.8); return u > 0 && u < 1 ? u : -1 }
    static func flash(_ t: Double, _ start: Double) -> Double {
        let u = seg(t, start, start+0.35); return u > 0 && u < 1 ? u : -1
    }
    static func loopFrame(_ time: Double) -> Frame {
        let t = phase(time)
        var f = Frame()
        f.slot = 8; f.can = canAt(t); f.glint = glint(t)
        f.flashA = 1; f.flashAU = flash(t, 1); f.flashB = 7; f.flashBU = flash(t, 1.6)
        let a = seg(t, 0.85, 0.97)*(1-seg(t, 1.3, 1.42))
        let b = seg(t, 1.45, 1.57)*(1-seg(t, 1.9, 2.02))
        f.fingerKey = a > 0 ? 1 : 7; f.finger = a > 0 ? a : b
        f.lcd = t >= 6 || t < 1 ? "" : t < 1.6 ? "B_" : "B4"
        f.card = t >= 1.6 && t < 6; f.pill = !f.card ? 0 : t >= 3.6 ? 2 : 1; f.cardSlot = 8; return f
    }
    struct Touch {
        var manual = false, loopStart = 0.0, lastTouch = -100.0, letter = ""
        var slot = -1, dispAt = -1.0, takeAt = -1.0
        var keyA = -1, keyAAt = -100.0, keyB = -1, keyBAt = -100.0, shakeAt = -100.0
        func beat(_ c: Double, _ reduced: Bool) -> Double {
            if slot < 0 { return -1 }
            if reduced {
                if takeAt >= 0 { return c-takeAt >= 0.2 ? 6 : 5.8 }
                return c-dispAt >= 0.4 ? 3.6 : 2
            }
            return takeAt >= 0 ? min(5.8+c-takeAt, 7) : min(2+c-dispAt, 5.79)
        }
        func busy(_ c: Double, _ reduced: Bool) -> Bool {
            slot >= 0 && beat(c, reduced) < 7 && !(takeAt >= 0 && (reduced ? c-takeAt >= 0.4 : beat(c, false) >= 7))
        }
        func inTray(_ c: Double, _ reduced: Bool) -> Bool {
            slot >= 0 && takeAt < 0 && (reduced ? c-dispAt >= 0.4 : beat(c, false) >= 3.95)
        }
        mutating func over(_ c: Double, _ reduced: Bool) {
            lastTouch = c
            if manual { return }
            manual = true
            if reduced { return }
            let t = phase(c-loopStart)
            if t >= 1 && t < 1.6 { letter = "B" }
            if t >= 1.6 && t < 7 { slot = 8; dispAt = c-(t-2); if t >= 5.8 { takeAt = c-(t-5.8) } }
            if t >= 1 && t < 1.35 { keyA = 1; keyAAt = c-(t-1) }
            if t >= 1.6 && t < 1.95 { keyB = 7; keyBAt = c-(t-1.6) }
        }
        mutating func shake(_ c: Double) { letter = ""; shakeAt = c }
        mutating func choose(_ s: Int, _ c: Double, _ reduced: Bool) {
            if busy(c, reduced) { shake(c); return }
            letter = ""; slot = s; dispAt = c; takeAt = -1
        }
        mutating func key(_ key: Int, _ c: Double, _ reduced: Bool) {
            keyB = keyA; keyBAt = keyAAt; keyA = key; keyAAt = c
            if busy(c, reduced) { shake(c); return }
            slot = -1
            let v = keys[key]
            if v == "✕" { letter = ""; return }
            if letters.contains(v) { letter = v; return }
            guard let row = letters.firstIndex(of: letter), let col = Int(v), (1...5).contains(col) else { shake(c); return }
            choose(row*5+col-1, c, reduced)
        }
        mutating func take(_ c: Double, _ reduced: Bool) { if inTray(c, reduced) { takeAt = c } }
        mutating func step(_ c: Double, _ autoplay: Bool, _ reduced: Bool) {
            if !manual { return }
            if slot >= 0 && takeAt >= 0 && (reduced ? c-takeAt >= 0.4 : beat(c, false) >= 7) { slot = -1; dispAt = -1; takeAt = -1 }
            let landed = slot >= 0 && takeAt < 0 ? dispAt+(reduced ? 0.4 : 1.95) : -1e9
            if !autoplay || reduced || c-max(lastTouch, landed) < 2 { return }
            if inTray(c, reduced) { takeAt = c; return }
            if slot >= 0 || c-shakeAt < 0.3 || c-keyAAt < 0.35 { return }
            self = Touch(); loopStart = c
        }
        func frame(_ c: Double, _ reduced: Bool) -> Frame {
            if !manual && !reduced { return loopFrame(c-loopStart) }
            let t = beat(c, reduced)
            var f = Frame()
            f.slot = slot; f.can = canAt(max(0, t)); f.glint = reduced || t < 0 ? -1 : glint(t)
            f.flashA = keyA; f.flashAU = flash(c, keyAAt); f.flashB = keyB; f.flashBU = flash(c, keyBAt)
            f.lcd = !letter.isEmpty ? letter+"_" : ""
            if slot >= 0 && t < 6 { f.lcd = code(slot) }
            let u = seg(c, shakeAt, shakeAt+0.3)
            f.lcdShake = u > 0 && u < 1 ? 4*sin(u * .pi*6) : 0
            f.card = slot >= 0 && t < 6; f.pill = !f.card ? 0 : t >= 3.6 ? 2 : 1; f.cardSlot = slot
            if reduced && slot >= 0 {
                f.can = Can(); f.can.scale = 1
                if takeAt >= 0 {
                    let v = c-takeAt
                    f.can.kind = v >= 0.2 ? 2 : 3; f.can.alpha = v >= 0.2 ? seg(v, 0.2, 0.4) : 0
                    f.trayAlpha = v >= 0.2 ? 0 : 1-seg(v, 0, 0.2)
                } else {
                    let v = c-dispAt
                    f.can.kind = v < 0.2 ? 2 : 3; f.can.alpha = v < 0.2 ? 1-seg(v, 0, 0.2) : 0
                    f.trayAlpha = v < 0.2 ? 0 : seg(v, 0.2, 0.4)
                }
            }
            return f
        }
        func moving(_ c: Double, _ autoplay: Bool, _ reduced: Bool) -> Bool {
            if autoplay && !reduced { return true }
            if c-keyAAt < 0.35 || c-shakeAt < 0.3 { return true }
            if slot < 0 { return false }
            return takeAt >= 0 ? c-takeAt < (reduced ? 0.4 : 1.2) : c-dispAt < (reduced ? 0.4 : 1.95)
        }
    }
}
