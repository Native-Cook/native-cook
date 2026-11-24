import Foundation

struct QuickAddMorphTask: Identifiable, Equatable {
    var id: String
    var title: String
    var meta: String
    var priority = false
    var done = false

    static let all: [QuickAddMorphTask] = [
        .init(id: "rent", title: "Pay the November rent", meta: "Home · 9:00", done: true),
        .init(id: "deck", title: "Send the Q4 deck to Priya", meta: "Work · 11:30", priority: true),
        .init(id: "plants", title: "Water the balcony plants", meta: "Home · 12:00", done: true),
        .init(id: "review", title: "Review the onboarding copy", meta: "Work · 14:00"),
        .init(id: "parcel", title: "Pick up the parcel", meta: "Errands · 16:15"),
        .init(id: "gym", title: "Leg day at the gym", meta: "Health · 18:00"),
        .init(id: "groceries", title: "Buy oat milk and eggs", meta: "Errands · 18:45"),
        .init(id: "call", title: "Call Mum about Sunday", meta: "Home · 20:00"),
    ]

    static let lists = ["Inbox", "Home", "Work", "Errands"]

    /// What the autoPlay loop types and adds.
    static let script = QuickAddMorphTask(id: "script", title: "Book the dentist", meta: "Home · Today", priority: true)
}

/// What the composer hands over when Add is pressed.
struct QuickAddMorphDraft {
    var title: String
    var today: Bool
    var priority: Bool
    var list: String
}
