import SwiftUI

/// One bar per hour, or one or two lines with a dot at every hour.
enum BarsToLinesMorphVitalChart {
    case bars(low: [Double], high: [Double])
    case lines(first: [Double], second: [Double]? = nil)
}

struct BarsToLinesMorphVital {
    var key: String
    var name: String
    /// Material Community icon name, mapped to a system symbol by the screen.
    var icon: String
    var iconColor: Color
    var value: String
    var unit: String
    var time: String
    var warning = false
    var chartUnit: String
    /// Bottom to top, at fixed heights of the chart.
    var ticks: [Double]
    var above: Double? = nil
    var below: Double? = nil
    var colors: [Color]
    var chart: BarsToLinesMorphVitalChart

    static let samples: [BarsToLinesMorphVital] = [
        BarsToLinesMorphVital(
            key: "heart",
            name: "Heart Rate",
            icon: "heart-pulse",
            iconColor: Palette.gold,
            value: "146",
            unit: "BPM",
            time: "9:32 AM",
            warning: true,
            chartUnit: "BPM",
            ticks: [60, 120, 180],
            above: 140,
            colors: [Palette.gold, Palette.gold],
            chart: .bars(
                low: [68, 62, 64, 72, 72, 82, 64, 72, 78, 66, 62, 70, 86, 52, 64, 74, 82, 60, 70, 66, 74, 92, 98, 86],
                high: [92, 128, 116, 112, 124, 128, 116, 110, 150, 122, 104, 128, 120, 96, 116, 128, 172, 92, 112, 126, 136, 122, 146, 152]
            )
        ),
        BarsToLinesMorphVital(
            key: "spo2",
            name: "SpO2",
            icon: "water-percent",
            iconColor: Color(hex: 0x3d8bff),
            value: "93",
            unit: "%",
            time: "9:34 AM",
            chartUnit: "%",
            ticks: [88, 94, 100],
            below: 90,
            colors: [Color(hex: 0x3d8bff), Color(hex: 0x3d8bff)],
            chart: .lines(
                first: [97, 98, 97, 96, 95, 93, 91, 89, 90, 94, 96, 97, 98, 97, 96, 97, 98, 97, 95, 94, 96, 97, 95, 93]
            )
        ),
        BarsToLinesMorphVital(
            key: "fever",
            name: "Fever",
            icon: "thermometer",
            iconColor: Color(hex: 0xff7a45),
            value: "97.8",
            unit: "°F",
            time: "9:40 AM",
            chartUnit: "°F",
            ticks: [97, 99, 101],
            above: 99.5,
            colors: [Color(hex: 0xff9a5c), Color(hex: 0xff9a5c)],
            chart: .lines(
                first: [97.6, 97.5, 97.4, 97.3, 97.4, 97.6, 97.8, 98, 98.3, 98.6, 98.9, 99.2, 99.6, 99.8, 99.5, 99.1, 98.8, 98.5, 98.3, 98.1, 97.9, 97.8, 97.8, 97.8]
            )
        ),
        BarsToLinesMorphVital(
            key: "pressure",
            name: "Blood Pressure",
            icon: "gauge",
            iconColor: Palette.teal,
            value: "145/95",
            unit: "mmHg",
            time: "9:46 AM",
            chartUnit: "mmHg",
            ticks: [60, 110, 160],
            above: 140,
            below: 60,
            colors: [Palette.teal, Palette.rose],
            chart: .lines(
                first: [124, 128, 122, 131, 126, 119, 134, 129, 138, 127, 121, 133, 142, 148, 136, 129, 124, 131, 137, 128, 122, 126, 133, 145],
                second: [84, 86, 79, 88, 83, 76, 62, 58, 55, 66, 78, 85, 89, 92, 86, 80, 74, 81, 87, 83, 78, 80, 85, 95]
            )
        ),
        BarsToLinesMorphVital(
            key: "glucose",
            name: "Blood Glucose",
            icon: "water",
            iconColor: Color(hex: 0x34c759),
            value: "5.5",
            unit: "mmol/L",
            time: "9:41 AM",
            chartUnit: "mmol/L",
            ticks: [4, 7, 10],
            above: 7.8,
            colors: [Color(hex: 0x34c759), Color(hex: 0x34c759)],
            chart: .bars(
                low: [4.6, 4.4, 4.3, 4.2, 4.4, 4.8, 5.2, 5, 4.8, 5.4, 5, 4.7, 5.6, 5.2, 4.9, 4.8, 5, 5.8, 5.4, 5, 4.8, 4.6, 4.5, 4.9],
                high: [5.2, 4.9, 4.8, 4.7, 5.1, 6.2, 7.4, 6.8, 5.9, 8.2, 6.6, 5.6, 8.6, 7, 5.8, 5.6, 6.4, 8, 7.2, 6, 5.6, 5.2, 5, 5.5]
            )
        ),
        BarsToLinesMorphVital(
            key: "breath",
            name: "Respiratory Rate",
            icon: "lungs",
            iconColor: Color(hex: 0x9b6bff),
            value: "18",
            unit: "br/min",
            time: "9:48 AM",
            chartUnit: "br/min",
            ticks: [12, 18, 24],
            above: 22,
            colors: [Color(hex: 0x9b6bff), Color(hex: 0x9b6bff)],
            chart: .lines(
                first: [15, 14, 14, 15, 14, 15, 16, 17, 18, 17, 16, 18, 19, 20, 23, 22, 19, 18, 17, 17, 16, 16, 15, 18]
            )
        ),
    ]
}

private enum Palette {
    static let gold = Color(hex: 0xffc83d)
    static let teal = Color(hex: 0x29d3c0)
    static let rose = Color(hex: 0xf2748a)
}

/// The axis and limits used by the canvas, and the positions of its extreme labels.
struct BarsToLinesMorphPlot {
    let bars: Bool
    let lines: Int
    let a: [Double]
    let b: [Double]
    let min: Double
    let max: Double
    let above: Double
    let below: Double
    let colors: [Color]
    let peak: Int
    let trough: Int
    let peakAbove: Bool
    let troughBelow: Bool

    private static let none = 1e6

    init(_ vital: BarsToLinesMorphVital) {
        switch vital.chart {
        case let .bars(low, high):
            bars = true; lines = 0; a = low; b = high
        case let .lines(first, second):
            bars = false; lines = second == nil ? 1 : 2; a = first; b = second ?? first
        }
        let pad = (vital.ticks[2] - vital.ticks[0]) / 8
        min = vital.ticks[0] - pad
        max = vital.ticks[2] + pad
        above = vital.above ?? Self.none
        below = vital.below ?? -Self.none
        colors = vital.colors
        let tops = bars ? b : a
        let bottoms = bars ? a : b
        let peakIndex = tops.firstIndex(of: tops.max()!)!
        let troughIndex = bottoms.firstIndex(of: bottoms.min()!)!
        peak = peakIndex
        trough = troughIndex
        peakAbove = vital.above.map { tops[peakIndex] > $0 } ?? false
        troughBelow = vital.below.map { bottoms[troughIndex] < $0 } ?? false
    }

    func extremes(unit: String) -> [String] {
        let tops = bars ? b : a
        let bottoms = bars ? a : b
        return [Self.number(tops[peak]) + " " + unit, Self.number(bottoms[trough]) + " " + unit]
    }

    static func number(_ value: Double) -> String {
        value == value.rounded() ? String(Int(value)) : String(value)
    }
}
