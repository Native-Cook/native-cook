import Foundation

struct KeycapMoodInputLayout {
    let width: Double
    let height: Double
    let u: Double
    let keySize: Double
    let pitch: Double
    let y0: Double

    init(width: Double, height: Double) {
        self.width = width
        self.height = height
        u = min(width / 402, height / 874)
        keySize = 134 * u
        pitch = 142 * u
        y0 = (height - 560 * u) / 2 - 20 * u
    }

    func centre(_ key: Int) -> SIMD2<Double> {
        SIMD2(width / 2, y0 + Double(key) * pitch + keySize / 2)
    }

    func mini(_ index: Int) -> SIMD2<Double> {
        let angle = (155 - 26 * Double(index)) * .pi / 180
        let base = centre(3)
        return SIMD2(base.x + pitch * cos(angle), base.y + pitch * sin(angle))
    }

    func hitKey(_ point: SIMD2<Double>) -> Int {
        for key in 0..<4 {
            let c = centre(key)
            if abs(point.x - c.x) <= (keySize + 24 * u) / 2,
               point.y >= c.y - keySize / 2 - 4 * u,
               point.y < c.y - keySize / 2 - 4 * u + pitch { return key }
        }
        return -1
    }

    func hitMini(_ point: SIMD2<Double>) -> Int {
        var best = 34 * u
        var selected = -1
        for index in 0..<6 {
            let delta = point - mini(index)
            let distance = hypot(delta.x, delta.y)
            if distance < best {
                best = distance
                selected = index
            }
        }
        return selected
    }
}

struct KeycapMoodInputFlip {
    var from = 0
    var to = 0
    var time = -1
    var mode = 0
}

struct KeycapMoodInputMix {
    var from = 0
    var to = 0
    var progress = 1.0
    var origin = SIMD2<Double>(repeating: 0)
    var radial = false
}

enum KeycapMoodInputPalette {
    static let fanOrder = [5, 2, 0, 3, 4, 1]
    static let words = [["i", "we"], ["feel", "felt"], ["so", "very", "a bit"]]
    static let names = ["happy", "in love", "amazed", "calm", "down", "angry"]
    static let linear: [SIMD3<Double>] = [0xB6FF3B, 0xFF4FA3, 0xFFB21F, 0x2FE6D2, 0x8E6BFF, 0xFF4A2E].map { hex in
        let channels = [Double((hex >> 16) & 255), Double((hex >> 8) & 255), Double(hex & 255)]
        let result = channels.map { value -> Double in
            let c = value / 255
            return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4)
        }
        return SIMD3(result[0], result[1], result[2])
    }
}

final class KeycapMoodInputSimulation {
    var layout: KeycapMoodInputLayout
    var reduced: Bool
    let autoPlay: Bool
    var clock = 0
    var remainder = 0.0
    var eventLog: [(Int, Int)] = []
    var x = [Double](repeating: 0, count: 4)
    var velocity = [Double](repeating: 0, count: 4)
    var held = [Bool](repeating: false, count: 4)
    var stroke = [Bool](repeating: false, count: 4)
    var pressAge = [Int](repeating: 0, count: 4)
    var pressFrom = [Double](repeating: 0, count: 4)
    var bottom = [Bool](repeating: false, count: 4)
    var direction = [Int](repeating: 1, count: 4)
    var autoRelease = [Bool](repeating: false, count: 4)
    var choices = [Int](repeating: 0, count: 4)
    var legends = [KeycapMoodInputFlip](repeating: .init(), count: 4)
    var mood = KeycapMoodInputMix()
    var moodAge = 0
    var moodDuration = 200
    var moodPending = false
    var moodTarget = -1
    var origin = SIMD2<Double>(repeating: 0)
    var charge = 0.0
    var chargeAge = 0
    var burst = 1.0
    var burstAge = -1
    var ring = 0.0
    var rim = [Double](repeating: 0, count: 4)
    var rimStart = [Int](repeating: -1, count: 4)
    var scene = KeycapMoodInputPalette.linear[0]
    var fanOpen = false
    var fanSince = 0
    var fanSticky = false
    var fanSession = false
    var fanRequest = false
    var out = [Double](repeating: 0, count: 6)
    var outVelocity = [Double](repeating: 0, count: 6)
    var outFrom = [Double](repeating: 0, count: 6)
    var miniX = [Double](repeating: 0, count: 6)
    var miniVelocity = [Double](repeating: 0, count: 6)
    var miniActive = -1
    var dim = 1.0
    var dimFrom = 1.0
    var pointerDown = false
    var pointerPoint = SIMD2<Double>(repeating: 0)
    var pointerKey = -1
    var pointerStartKey = -1
    var pointerTime = 0
    var pointerEligible = false
    var realDown = false
    var demoActive: Bool
    var demoTime = 0
    var demoIdle = 0

    init(layout: KeycapMoodInputLayout, reduced: Bool, autoPlay: Bool) {
        self.layout = layout
        self.reduced = reduced
        self.autoPlay = autoPlay
        demoActive = autoPlay
        legends[3].mode = reduced ? 2 : 1
        if reduced { for i in 0..<3 { legends[i].mode = 2 } }
    }

    private func reset() {
        let fresh = KeycapMoodInputSimulation(layout: layout, reduced: reduced, autoPlay: autoPlay)
        copy(from: fresh)
    }

    private func copy(from other: KeycapMoodInputSimulation) {
        clock = other.clock; remainder = other.remainder
        x = other.x; velocity = other.velocity; held = other.held; stroke = other.stroke
        pressAge = other.pressAge; pressFrom = other.pressFrom; bottom = other.bottom
        direction = other.direction; autoRelease = other.autoRelease; choices = other.choices
        legends = other.legends; mood = other.mood; moodAge = other.moodAge
        moodDuration = other.moodDuration; moodPending = other.moodPending; moodTarget = other.moodTarget
        origin = other.origin; charge = other.charge; chargeAge = other.chargeAge
        burst = other.burst; burstAge = other.burstAge; ring = other.ring
        rim = other.rim; rimStart = other.rimStart; scene = other.scene
        fanOpen = other.fanOpen; fanSince = other.fanSince; fanSticky = other.fanSticky
        fanSession = other.fanSession; fanRequest = other.fanRequest
        out = other.out; outVelocity = other.outVelocity; outFrom = other.outFrom
        miniX = other.miniX; miniVelocity = other.miniVelocity; miniActive = other.miniActive
        dim = other.dim; dimFrom = other.dimFrom; pointerDown = other.pointerDown
        pointerPoint = other.pointerPoint; pointerKey = other.pointerKey
        pointerStartKey = other.pointerStartKey; pointerTime = other.pointerTime
        pointerEligible = other.pointerEligible; realDown = other.realDown
        demoActive = other.demoActive; demoTime = other.demoTime; demoIdle = other.demoIdle
    }

    private func unit(_ value: Double) -> Double { min(1, max(0, value)) }
    private func easeOut(_ value: Double) -> Double { 1 - pow(1 - unit(value), 3) }

    private func bezier(_ time: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        if time <= 0 || time >= 1 { return unit(time) }
        var low = 0.0
        var high = 1.0
        var q = time
        for _ in 0..<14 {
            q = (low + high) / 2
            let value = 3 * pow(1 - q, 2) * q * x1 + 3 * (1 - q) * q * q * x2 + q * q * q
            if value < time { low = q } else { high = q }
        }
        return 3 * pow(1 - q, 2) * q * y1 + 3 * (1 - q) * q * q * y2 + q * q * q
    }

    private func emit(_ event: Int, _ index: Int) { eventLog.append((event, index)) }

    private func flip(_ key: Int, _ target: Int) {
        let from = choices[key]
        if from == target { return }
        choices[key] = target
        legends[key] = .init(from: from, to: target, time: 0, mode: reduced ? 2 : key == 3 ? 1 : 0)
        emit(128, key)
    }

    private func mixMood(_ target: Int, radial: Bool, duration: Int) {
        let from = mood.to
        flip(3, target)
        mood = .init(from: from, to: target, progress: from == target ? 1 : 0, origin: origin, radial: radial && !reduced)
        moodAge = 0
        moodDuration = reduced ? 200 : duration
    }

    private func triggerBurst() {
        burstAge = 0
        charge = 0
        if !reduced {
            burst = 2.4
            for index in 0..<3 { rimStart[index] = clock + (3 - index) * 70 }
        }
        emit(16, 3)
    }

    private func commitMood() {
        let target = moodTarget >= 0 ? moodTarget : (choices[3] + direction[3] + 6) % 6
        mixMood(target, radial: true, duration: 460)
        moodPending = false
        moodTarget = -1
        triggerBurst()
    }

    private func openFan() {
        fanOpen = true; fanSince = clock; fanSticky = false
        fanSession = true; fanRequest = false; moodPending = false; held[3] = true
        dimFrom = dim
        emit(32, 3)
    }

    private func closeFan(celebrate: Bool) {
        fanOpen = false; fanSince = clock; fanSticky = false
        dimFrom = dim; outFrom = out
        fanSession = false; held[3] = false; miniActive = -1
        emit(256, 3)
        if celebrate { triggerBurst() }
    }

    private func selectMini(_ index: Int) {
        miniActive = index
        if choices[3] == KeycapMoodInputPalette.fanOrder[index] { return }
        mixMood(KeycapMoodInputPalette.fanOrder[index], radial: false, duration: 160)
        emit(64, index)
    }

    private func press(_ key: Int) {
        held[key] = true
        if stroke[key] { return }
        stroke[key] = true; bottom[key] = false; pressAge[key] = 0; pressFrom[key] = x[key]
        if key == 3 { chargeAge = 0; charge = 0 }
    }

    private func release(_ key: Int, cancelled: Bool) {
        held[key] = false
        if key == 3 && !fanSession && !cancelled {
            if bottom[3] { commitMood() } else { moodPending = true }
        }
    }

    private func pointer(down: Bool, point: SIMD2<Double>, cancelled: Bool) {
        let wasDown = pointerDown
        let key = down ? layout.hitKey(point) : -1
        if down && !wasDown {
            pointerStartKey = key; pointerTime = clock; pointerEligible = key == 3
        }
        if cancelled && fanOpen { closeFan(celebrate: false) }
        if fanOpen {
            let mini = layout.hitMini(point)
            if down && mini >= 0 { selectMini(mini) }
            if down && !wasDown && fanSticky && mini < 0 { closeFan(celebrate: false) }
            else if !down && wasDown {
                if mini >= 0 && !cancelled { closeFan(celebrate: true) }
                else { fanSticky = true }
            }
            pointerKey = -1
        } else {
            if key != 3 { pointerEligible = false }
            if pointerKey != key {
                if pointerKey >= 0 { release(pointerKey, cancelled: cancelled) }
                if key >= 0 { direction[key] = 1; press(key) }
            }
            pointerKey = key
        }
        if key == 3 { origin = point - layout.centre(3) }
        pointerDown = down; pointerPoint = point
    }

    private func stopDemo() {
        if demoActive { pointer(down: false, point: pointerPoint, cancelled: false) }
        demoActive = false; demoIdle = 0
    }

    func input(down: Bool, point: SIMD2<Double>, cancelled: Bool = false) {
        eventLog.removeAll(keepingCapacity: true)
        if down || realDown {
            stopDemo()
            realDown = down
            pointer(down: down, point: point, cancelled: cancelled)
        }
    }

    enum Action { case next(Int), previous(Int), selectMood(Int), toggleFan }

    func action(_ action: Action) {
        eventLog.removeAll(keepingCapacity: true)
        stopDemo()
        switch action {
        case .toggleFan:
            if fanOpen { closeFan(celebrate: false) }
            else { press(3); fanRequest = true; fanSince = clock }
        case .selectMood(let moodIndex) where fanOpen:
            if let mini = KeycapMoodInputPalette.fanOrder.firstIndex(of: moodIndex) { selectMini(mini) }
            closeFan(celebrate: true)
        default:
            let key: Int
            switch action {
            case .next(let index), .previous(let index): key = min(3, max(0, index))
            case .selectMood: key = 3
            case .toggleFan: key = 3
            }
            if fanOpen { closeFan(celebrate: false) }
            if case .previous = action { direction[key] = -1 } else { direction[key] = 1 }
            if case .selectMood(let index) = action { moodTarget = min(5, max(0, index)) }
            origin = .zero
            press(key)
            autoRelease[key] = true
        }
    }

    private func spring(_ values: inout [Double], _ speeds: inout [Double], _ i: Int, _ target: Double, _ stiffness: Double, _ damping: Double) {
        if values[i] == target && speeds[i] == 0 { return }
        let z = values[i] - target
        let d = damping / 2
        let decay = exp(-d * 0.001)
        if stiffness <= d * d {
            let b = speeds[i] + d * z
            values[i] = target + (z + b * 0.001) * decay
            speeds[i] = (speeds[i] - d * b * 0.001) * decay
        } else {
            let w = sqrt(stiffness - d * d)
            let cs = cos(w * 0.001)
            let sn = sin(w * 0.001)
            values[i] = target + decay * (z * cs + (speeds[i] + d * z) * sn / w)
            speeds[i] = decay * (speeds[i] * cs - (d * speeds[i] + stiffness * z) * sn / w)
        }
        if abs(values[i] - target) < 0.0001 && abs(speeds[i]) < 0.001 {
            values[i] = target; speeds[i] = 0
        }
    }

    private func mechanics() {
        for i in 0..<4 {
            if stroke[i] {
                pressAge[i] += 1
                let age = pressAge[i]
                let old = x[i]
                if reduced {
                    x[i] = pressFrom[i] + (0.6 - pressFrom[i]) * easeOut(Double(age) / 85)
                } else {
                    let drive = age <= 40 ? 0.45 * easeOut(Double(age) / 40)
                        : age <= 60 ? 0.45 : 0.45 + 0.55 * pow(Double(age - 60) / 25, 2)
                    x[i] = pressFrom[i] + (1 - pressFrom[i]) * min(1, drive)
                }
                velocity[i] = (x[i] - old) * 1000
                if age >= 85 {
                    x[i] = reduced ? 0.6 : 1
                    velocity[i] = reduced ? 0 : -velocity[i] * 0.2
                    stroke[i] = false; bottom[i] = true
                    emit(1 << i, i)
                    if i < 3 {
                        let count = i == 2 ? 3 : 2
                        flip(i, (choices[i] + direction[i] + count) % count)
                    } else if moodPending { commitMood() }
                    if autoRelease[i] { autoRelease[i] = false; release(i, cancelled: false) }
                }
            } else if held[i] {
                spring(&x, &velocity, i, reduced ? 0.6 : 1, 6400, 160)
                if x[i] > 1 { x[i] = 1; velocity[i] = 0 }
            } else {
                spring(&x, &velocity, i, 0, reduced ? 1600 : 1100, reduced ? 80 : 42)
                if x[i] < -0.12 { x[i] = -0.12; velocity[i] = 0 }
            }
            if legends[i].time >= 0 {
                legends[i].time += 1
                let duration = reduced ? 140 : i == 3
                    ? Int(110 + (40 * layout.u + hypot(mood.origin.x, mood.origin.y)) * 0.9) : 208
                if legends[i].time >= duration {
                    legends[i].from = legends[i].to
                    legends[i].time = -1
                }
            }
        }
        if held[3] && bottom[3] { chargeAge += 1; charge = easeOut(Double(chargeAge) / 300) }
        else { charge *= exp(-1.0 / 60); if charge < 0.0001 { charge = 0 } }
        if !fanOpen && ((pointerDown && pointerEligible && clock - pointerTime >= 450)
            || (fanRequest && pressAge[3] >= 85 && clock - fanSince >= 450)) { openFan() }
        let age = clock - fanSince
        dim = dimFrom + ((fanOpen ? 0.72 : 1) - dimFrom) * unit(Double(age) / 200)
        for i in 0..<6 {
            let rank = min(abs(i - 2), abs(i - 3))
            if reduced {
                out[i] = fanOpen ? unit(Double(age) / 200) : outFrom[i] * (1 - unit(Double(age) / 200))
            } else if fanOpen {
                if age >= rank * 28 { spring(&out, &outVelocity, i, 1, 260, 20) }
                out[i] = min(1.06, out[i])
            } else {
                out[i] = outFrom[i] * (1 - bezier(unit(Double(age - (2 - rank) * 28) / 280), 0.32, 0.72, 0, 1))
                outVelocity[i] = 0
            }
            let selected = fanOpen && KeycapMoodInputPalette.fanOrder[i] == choices[3]
            spring(&miniX, &miniVelocity, i, selected ? (reduced ? 0.6 : 1) : 0, 1600, reduced ? 80 : 58)
        }
    }

    private func lighting() {
        if mood.progress < 1 {
            moodAge += 1
            let t = unit(Double(moodAge) / Double(moodDuration))
            mood.progress = mood.radial ? bezier(t, 0.23, 1, 0.32, 1) : t
            if t == 1 { mood.from = mood.to }
        }
        let target = KeycapMoodInputPalette.linear[choices[3]]
        scene += (target - scene) * (1 - exp(-1.0 / 90))
        if burstAge >= 0 { burstAge += 1 }
        if reduced { burst = 1 }
        else if burstAge >= 0 && burstAge < 700 { burst = 1 + 1.4 * (1 - easeOut(Double(burstAge) / 700)) }
        else {
            let fade = demoActive ? 1 - easeOut(Double(demoTime - 12000) / 1000) : 1
            burst = 1 + 0.05 * sin(Double(clock) * .pi / 2000) * fade
        }
        ring = !reduced && burstAge >= 0 && burstAge < 900 ? Double(burstAge) / 900 : 0
        for i in 0..<3 {
            let age = clock - rimStart[i]
            if !reduced && rimStart[i] >= 0 && age >= 0 && age < 580 {
                if age == 0 && !held[i] && !stroke[i] { velocity[i] += 3.4 }
                rim[i] = age < 60 ? Double(age) / 60
                    : (exp(-5 * Double(age - 60) / 520) - exp(-5.0)) / (1 - exp(-5.0))
            } else { rim[i] = 0 }
        }
    }

    private func quietReset() {
        if fanOpen { closeFan(celebrate: false) }
        for i in 0..<3 { flip(i, 0) }
        mixMood(0, radial: false, duration: 200)
        emit(512, -1)
    }

    private func demoPointer(_ time: Int) -> (Bool, SIMD2<Double>) {
        let t = time % 13000
        let top = layout.centre(0)
        let mood = layout.centre(3)
        var point = top
        var down = false
        func glide(_ a: Double, _ b: Double, _ value: Double) -> Double { a + (b - a) * unit(value) }
        if t >= 1000 && t < 1260 { down = true; point.y = top.y + 2 * layout.pitch }
        else if t >= 2200 && t < 2760 {
            down = true; point.y = glide(top.y, top.y + 2 * layout.pitch, Double(t - 2260) / 380)
        } else if t >= 3900 && t < 4200 { down = true; point.y = mood.y }
        else if t >= 6100 && t < 8850 {
            down = true; point = mood
            let starts = [6850, 7250, 7600, 7950, 8300]
            for i in starts.indices {
                if t < starts[i] { break }
                let mini = layout.mini(4 - i)
                let previous = i == 0 ? mood : layout.mini(5 - i)
                let progress = Double(t - starts[i]) / 200
                point = previous + (mini - previous) * unit(progress)
            }
        } else if t >= 10300 && t < 11000 {
            down = true; point.y = glide(top.y, mood.y, Double(t - 10360) / 500)
        } else if (t >= 1260 && t < 2200) || (t >= 2760 && t < 3900) { point.y = top.y + 2 * layout.pitch }
        else if t >= 4200 && t < 6100 { point.y = mood.y }
        else if t >= 8850 && t < 10300 { point = layout.mini(0) }
        else if t >= 11000 { point.y = mood.y }
        return (down, point)
    }

    func step(milliseconds: Double) {
        eventLog.removeAll(keepingCapacity: true)
        remainder += max(0, min(100, milliseconds))
        while remainder >= 1 - 0.0000001 {
            remainder = max(0, remainder - 1)
            clock += 1
            if demoActive {
                demoTime += 1
                if demoTime == 13000 {
                    let fractional = remainder
                    reset(); remainder = fractional
                    emit(1024, -1)
                    continue
                }
                let (down, point) = demoPointer(demoTime)
                pointer(down: down, point: point, cancelled: false)
            } else if !realDown {
                demoIdle += 1
                if autoPlay && demoIdle == 3500 { quietReset() }
                if autoPlay && demoIdle >= 4200 {
                    let fractional = remainder
                    reset(); remainder = fractional
                    emit(512, -1)
                    continue
                }
            }
            mechanics()
            lighting()
        }
    }
}
