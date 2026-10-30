import Foundation

// The RN driver uses numerical integration, not a framework spring. Preserve both substeps.
enum MorphObjectOnboardingMotion {
    static let LOOP = 9.0
    static let RESUME = 2.0
    static let moves: [[Double]] = [[1.5, 2.6, 0, 1], [4.6, 5.7, 1, 2], [7.6, 8.7, 2, 0]]
    static let holdAt = [0.0, 2.6, 5.7]
    static func clamp01(_ value: Double) -> Double { min(1, max(0, value)) }
    static func seg(_ time: Double, _ start: Double, _ end: Double) -> Double { clamp01((time - start) / (end - start)) }
    static func mix(_ a: Double, _ b: Double, _ progress: Double) -> Double { a + (b - a) * progress }
    static func smooth(_ value: Double) -> Double {
        let progress = clamp01(value)
        return progress * progress * (3 - 2 * progress)
    }
    static func easeInOut(_ value: Double) -> Double {
        let progress = clamp01(value)
        return progress < 0.5 ? 16 * pow(progress, 5) : 1 - pow(-2 * progress + 2, 5) / 2
    }
    static func easeInOutCubic(_ value: Double) -> Double {
        let progress = clamp01(value)
        return progress < 0.5 ? 4 * pow(progress, 3) : 1 - pow(-2 * progress + 2, 3) / 2
    }
    static func wobble(_ time: Double, period: Double, decay: Double) -> Double {
        time <= 0 ? 0 : exp(-time / decay) * cos(2 * .pi * time / period)
    }
    // JS Math.round ties toward positive infinity, including negative rubber-band positions.
    static func roundPage(_ value: Double) -> Double { floor(value + 0.5) }
    static func pageAt(_ time: Double) -> Double {
        for (index, move) in moves.enumerated() {
            if time < move[0] { return index == 0 ? 0 : moves[index - 1][3] }
            if time < move[1] { return mix(move[2], move[3], easeInOutCubic(seg(time, move[0], move[1]))) }
        }
        return 0
    }
    static func arrivalAt(_ time: Double) -> Double {
        for index in 0..<2 {
            let elapsed = time - moves[index][1]
            if elapsed >= 0 && elapsed < 1 { return elapsed }
        }
        return -1
    }
    struct Live {
        var ambient = 0.0
        var page = 0.0
        var arrive = -1.0
        var hop = -1.0
        var press = 0.0
    }
    struct Driver {
        var time = 0.0
        var mode = 0
        var last = -10.0
        var ambient = 0.0
        var page = 0.0
        var velocity = 0.0
        var aim = -1.0
        var dragFrom = 0.0
        var arriveAt = -1.0
        var hopAt = -1.0
        var press = 0.0
        var pressAim = 0.0
        var skipping = false
    }
    static func band(_ page: Double) -> Double {
        if page < 0 { return -(1 - 1 / (1 - page * 1.6)) / 1.6 }
        if page > 2 { return 2 + (1 - 1 / (1 + (page - 2) * 1.6)) / 1.6 }
        return page
    }
    @discardableResult static func step(_ driver: inout Driver, dt: Double, now: Double, auto: Bool) -> Double {
        driver.ambient = (driver.ambient + dt).truncatingRemainder(dividingBy: LOOP)
        driver.press += (driver.pressAim - driver.press) * (1 - exp(-dt * (driver.pressAim > 0 ? 40 : 16)))
        if driver.mode == 0 {
            if auto { driver.time = (driver.time + dt).truncatingRemainder(dividingBy: LOOP) }
            driver.page = pageAt(driver.time)
            return -1
        }
        var arrived = -1.0
        if driver.aim >= 0 {
            let stiffness = driver.skipping ? 160.0 : 110.0
            let damping = 2 * (driver.skipping ? 0.95 : 0.78) * sqrt(stiffness)
            let step = dt / 2
            for _ in 0..<2 {
                driver.velocity += (-stiffness * (driver.page - driver.aim) - damping * driver.velocity) * step
                driver.page += driver.velocity * step
            }
            if abs(driver.page - driver.aim) < 0.002 && abs(driver.velocity) < 0.02 {
                driver.page = driver.aim
                driver.velocity = 0
                arrived = driver.aim
                driver.aim = -1
                driver.skipping = false
            }
        }
        if auto && driver.aim < 0 && now - driver.last >= RESUME && abs(driver.page - roundPage(driver.page)) < 0.001 {
            driver.time = holdAt[Int(roundPage(driver.page))] + 0.001
            driver.mode = 0
        }
        return arrived
    }
    static func grab(_ driver: inout Driver, now: Double) {
        driver.last = now
        if driver.mode == 0 { driver.page = pageAt(driver.time); driver.velocity = 0 }
        driver.mode = 2
        driver.aim = -1
        driver.dragFrom = driver.page
    }
    static func release(_ driver: inout Driver, velocity: Double) {
        let from = roundPage(driver.dragFrom)
        let target = max(from - 1, min(from + 1, roundPage(driver.page + velocity * 0.18)))
        driver.aim = max(0, min(2, target))
        driver.velocity = velocity
    }
    static func goTo(_ driver: inout Driver, page: Double, now: Double, skip: Bool = false) {
        if driver.mode == 0 { driver.page = pageAt(driver.time); driver.velocity = 0 }
        driver.mode = 2
        driver.last = now
        driver.aim = page
        driver.skipping = skip
    }
}
