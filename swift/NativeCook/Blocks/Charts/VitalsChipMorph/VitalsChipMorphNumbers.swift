import SwiftUI
import UIKit

struct VitalsChipMorphNumbers: View {
    let blend: VitalsChipMorphMotion.Blend
    let quiet: Bool
    let k: Double
    private typealias T = VitalsChipMorphTheme
    private typealias M = VitalsChipMorphMotion

    var body: some View {
        let f = M.heroFrame(blend.lo, blend.hi, quiet ? (blend.u < 0.5 ? 0 : 1) : blend.u)
        ZStack(alignment: .topLeading) {
            if f.sevenOpacity > 0 {
                seven(roll: quiet ? 7 : f.sevenRoll).opacity(f.sevenOpacity)
            }
            if f.bpm0 > 0 || f.bpm1 > 0 {
                bpm(f)
            }
        }
        .frame(width: 1206 * k, height: 1420 * k, alignment: .topLeading)
        .allowsHitTesting(false).accessibilityHidden(true)
    }

    private func seven(roll: Double) -> some View {
        let lineH = T.type.readiness.size * 3 * 1.2
        let cap = VitalsChipMorphText.CAP_OFFSET * T.type.readiness.size * 3
        return ZStack(alignment: .topLeading) {
            ForEach(0..<8, id: \.self) { digit in
                VitalsChipMorphText(value: "\(digit)", spec: T.type.readiness, x: 0,
                                    capTop: cap + (Double(digit) - roll) * lineH, k: k,
                                    width: 300, alignment: .center)
            }
        }
        .frame(width: 300 * k, height: lineH * k, alignment: .topLeading)
        .clipped()
        .offset(x: (T.layout.seven.cx - 150) * k, y: (T.layout.seven.capTop - cap) * k)
    }

    private func bpm(_ f: M.Hero) -> some View {
        let s = 3 * k, size = T.type.bpm.size * s
        let font = UIFont.monospacedDigitSystemFont(ofSize: size, weight: .semibold)
        // The two single-character RN Texts are laid out by their actual advances.
        let width6 = ("6" as NSString).size(withAttributes: [.font: font, .kern: T.type.bpm.ls * s]).width
        let width2 = ("2" as NSString).size(withAttributes: [.font: font, .kern: T.type.bpm.ls * s]).width
        let x = (1206 * k - width6 - width2) / 2
        return Group {
            VitalsChipMorphText(value: "6", spec: T.type.bpm, x: x / k,
                                capTop: T.layout.bpm.capTop + (1 - f.bpm0) * T.type.bpm.size * 0.9, k: k)
                .opacity(f.bpm0)
            VitalsChipMorphText(value: "2", spec: T.type.bpm, x: (x + width6) / k,
                                capTop: T.layout.bpm.capTop + (1 - f.bpm1) * T.type.bpm.size * 0.9, k: k)
                .opacity(f.bpm1)
            VitalsChipMorphText(value: "BPM", spec: T.type.bpmUnit, x: 0,
                                capTop: T.layout.bpm.unitCapTop + (1 - f.bpmUnit) * T.type.bpmUnit.size * 0.9,
                                k: k, width: 1206, alignment: .center)
                .opacity(f.bpmUnit)
        }
    }
}
