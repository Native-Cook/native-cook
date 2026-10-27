import Foundation

/// Local fixtures only. No wallet, orders, networking, billing or persisted entitlement.
struct TradingBotsFlow {
    enum Step: Int, CaseIterable, Identifiable {
        case wallet, selection, setup, allocation, results, pro
        var id: Int { rawValue }
        var name: String { ["Wallet", "Choose bots", "Setup", "Allocation", "Results", "Pro"][rawValue] }
    }
    enum Risk: String, CaseIterable, Identifiable {
        case standard = "2.0%", balanced = "0.5%", cautious = "0.25%"
        var id: String { rawValue }
    }

    private(set) var step: Step = .wallet
    private(set) var selectedBot = 1
    private(set) var proFocus = 0
    private(set) var allocation = 53.0
    var risk: Risk = .standard
    var autoTrading = true
    var maxDailyLoss = true
    private var lastAdvance = -Double.infinity

    init(start: Step = .wallet) { step = start }

    /// Coalesce duplicate CTA input during the exit/entry handoff. Back/Skip can
    /// interrupt at any time and reset this gate; scene tasks are cancelled by identity.
    @discardableResult mutating func advance(at time: TimeInterval) -> Bool {
        guard time - lastAdvance >= 0.48, step != .pro else { return false }
        lastAdvance = time
        step = Step(rawValue: step.rawValue + 1) ?? .pro
        if step == .pro { proFocus = 0 }
        return true
    }
    mutating func back() {
        step = Step(rawValue: max(0, step.rawValue - 1)) ?? .wallet
        lastAdvance = -Double.infinity
    }
    mutating func show(_ destination: Step) {
        step = destination
        if destination == .pro { proFocus = 0 }
        lastAdvance = -Double.infinity
    }
    mutating func chooseBot(_ index: Int) { selectedBot = min(2, max(0, index)) }
    mutating func focusPro(_ index: Int) { proFocus = min(2, max(0, index)) }
    mutating func allocate(_ amount: Double) { allocation = min(100, max(0, amount)) }
    mutating func reset() { self = Self() }
}
