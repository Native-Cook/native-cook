import SwiftUI

/// Font geometry is measured once per layout, never in the frame renderer.
struct NeonContourMoodArt {
    typealias R = NeonContourMoodResources
    let title: [R.Line]
    let question: [R.Line]
    let endpoints: [[R.Line]]
    let tags: [R.Line]
    let button: R.Line
    let widths: [Double]
    let lengths: [Int]

    init(_ l: NeonContourMoodLayout) {
        let s = l.scale, fs = l.fontScale
        title = l.titleLines.map { R.line($0, "Inter-Medium", 18 * s * fs) }
        question = l.questionLines.map { R.line($0, "Inter-SemiBold", 28 * s * fs) }
        endpoints = l.endpointLines.map { lines in lines.map { R.line($0, "Inter-Regular", 12 * s * fs) } }
        tags = NeonContourMoodTheme.moods.map { R.line($0.tag, "Inter-Medium", 13 * s * fs, 1.25 * s) }
        button = R.line("Next", "Inter-SemiBold", 18 * s * fs)
        widths = tags.map { $0.width + 18 * s }
        lengths = NeonContourMoodTheme.moods.map { $0.word.count }
    }
}
