import SwiftUI

struct HealthAgeDialTimelineSchedule: TimelineSchedule {
    let end: Date?
    let interval: TimeInterval

    func entries(from startDate: Date, mode: TimelineScheduleMode) -> Entries {
        Entries(start: startDate, end: end, interval: interval)
    }

    struct Entries: Sequence {
        let start: Date
        let end: Date?
        let interval: TimeInterval

        func makeIterator() -> Iterator {
            Iterator(nextDate: start, end: end, interval: interval)
        }
    }

    struct Iterator: IteratorProtocol {
        var nextDate: Date?
        let end: Date?
        let interval: TimeInterval

        mutating func next() -> Date? {
            guard let current = nextDate else { return nil }
            let following = current.addingTimeInterval(interval)
            if let end, following > end { nextDate = nil } else { nextDate = following }
            return current
        }
    }
}
