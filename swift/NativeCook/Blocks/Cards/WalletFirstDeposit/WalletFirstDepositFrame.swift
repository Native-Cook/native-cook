import Foundation

/// Exact scalar choreography shared with the React Native reference; inputs are seconds.
struct WalletFirstDepositFrame {
    let t: Double
    let fill: Double
    let copy: Double
    let cardSqueeze: Double
    let flight: Double
    let lift: Double
    let tilt: Double
    let sheen: Double
    let banner: Double
    let dot: Double
    let balance: Double
    let reflow: Double
    let reflow2: Double
    let pill0: Double
    let pill1: Double
    let txHead: Double
    let txCard: Double
    let txRow0: Double
    let txRow1: Double
    let green: Double
    let actionsOut: Double
    let card0: Double
    let card1: Double
    let card2: Double
    let card3: Double
    let orb0: Double
    let orb1: Double
    let orb2: Double
    let orb3: Double
    let names: Double
    let plot: Double
    let plotA: Double
    let line: Double
    let forecast: Double
    let spentValue: Double
    let label: Double
    var funded: Double { dot }
    var actions: Double { 1 - actionsOut }
    var payees: Double { names }
    var transactions: Double { txCard }
    var pills: Double { pill0 }

    init(at seconds: Double) {
        let t = (seconds.truncatingRemainder(dividingBy: 10) + 10).truncatingRemainder(dividingBy: 10)
        self.t = t
        let seg = WalletFirstDepositMotion.raw
        let easeOut = WalletFirstDepositMotion.out
        let easeInOut = WalletFirstDepositMotion.inOut
        let spring = WalletFirstDepositMotion.spring
        func lerp(_ a: Double, _ b: Double, _ p: Double) -> Double { a + (b - a) * p }
        if t < 8.4 {
            let flightLinear = seg(t, 2.75, 3.3)
            func pills(_ k: Double) -> Double { spring(t - 3.6 - k * 0.06, .ui) }
            func tx(_ k: Double) -> Double { easeOut(seg(t, 3.9 + k * 0.05, 4.35 + k * 0.05)) }
            func card(_ k: Double) -> Double { easeInOut(seg(t, 3.35 + k * 0.04, 4.3 + k * 0.04)) }
            func orb(_ k: Double) -> Double { spring(t - 4.2 - k * 0.06, .pop) }
            fill = lerp(0, 1, easeInOut(seg(t, 1.4, 2.6)))
            copy = easeInOut(seg(t, 2.4, 2.7))
            cardSqueeze = 0.04 * easeOut(seg(t, 2.6, 2.75)) * (1 - easeOut(seg(t, 2.75, 2.95)))
            flight = spring(t - 2.75, .flight)
            lift = sin(.pi * flightLinear)
            tilt = -8 * sin(.pi * pow(flightLinear, 0.75))
            sheen = seg(t, 3.45, 3.95)
            banner = easeOut(seg(t, 2.9, 3.4))
            dot = easeOut(seg(t, 3.3, 3.55))
            balance = seg(t, 3.3, 4.3)
            reflow = easeInOut(seg(t, 3.25, 4.0))
            reflow2 = easeInOut(seg(t, 3.35, 4.1))
            pill0 = pills(0)
            pill1 = pills(1)
            txHead = tx(0)
            txCard = tx(1)
            txRow0 = tx(2)
            txRow1 = tx(3)
            green = easeInOut(seg(t, 4.5, 4.9))
            actionsOut = easeOut(seg(t, 3.2, 3.45))
            card0 = card(0)
            card1 = card(1)
            card2 = card(2)
            card3 = easeOut(seg(t, 4.1, 4.6))
            orb0 = orb(0)
            orb1 = orb(1)
            orb2 = orb(2)
            orb3 = orb(3)
            names = easeOut(seg(t, 4.35, 4.85))
            plot = easeInOut(seg(t, 4.6, 5.4))
            plotA = easeOut(seg(t, 4.6, 5.0))
            line = easeInOut(seg(t, 4.85, 6.0))
            forecast = easeOut(seg(t, 5.3, 6.0))
            spentValue = seg(t, 4.8, 5.6)
            label = easeInOut(seg(t, 5.3, 5.7))
        } else {
            func out(_ a: Double, _ b: Double) -> Double { 1 - easeOut(seg(t, a, b)) }
            let glide = 1 - easeInOut(seg(t, 9, 9.6))
            let bannerIn = easeOut(seg(t, 9.25, 9.7))
            func card(_ k: Double) -> Double { 1 - easeInOut(seg(t, 8.7 + k * 0.03, 9.35 + k * 0.03)) }
            fill = t < 9.1 ? 1 : 0
            copy = t < 9.1 ? 1 : 0
            cardSqueeze = 0
            flight = glide
            lift = 0.45 * sin(.pi * seg(t, 9.0, 9.6))
            tilt = 4 * sin(.pi * seg(t, 9.0, 9.6))
            sheen = 0
            banner = 1 - bannerIn
            dot = out(9.6, 9.9)
            balance = 1 - easeInOut(seg(t, 8.5, 9.1))
            reflow = 1 - easeInOut(seg(t, 8.75, 9.35))
            reflow2 = 1 - easeInOut(seg(t, 8.8, 9.4))
            pill0 = out(8.5, 8.75)
            pill1 = out(8.46, 8.71)
            txHead = out(8.55, 8.8)
            txCard = out(8.5, 8.75)
            txRow0 = out(8.4, 8.62)
            txRow1 = out(8.43, 8.65)
            green = out(8.4, 8.6)
            actionsOut = 1 - easeOut(seg(t, 9.4, 9.85))
            card0 = card(0)
            card1 = card(1)
            card2 = card(2)
            card3 = out(8.45, 8.75)
            orb0 = out(8.4, 8.65)
            orb1 = out(8.43, 8.68)
            orb2 = out(8.46, 8.71)
            orb3 = out(8.4, 8.6)
            names = out(8.4, 8.6)
            plot = 1 - easeInOut(seg(t, 8.6, 9.2))
            plotA = 1 - easeOut(seg(t, 9.0, 9.5))
            line = 1 - easeInOut(seg(t, 8.4, 8.8))
            forecast = out(8.4, 8.7)
            spentValue = 1 - seg(t, 8.5, 9.0)
            label = 1 - easeInOut(seg(t, 8.6, 8.9))
        }
    }
}
