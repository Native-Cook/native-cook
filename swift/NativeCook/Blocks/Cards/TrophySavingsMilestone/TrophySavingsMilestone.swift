import SwiftUI
import UIKit

@MainActor
struct TrophySavingsMilestone: View {
    static let loop = 5.0
    var autoPlay = false
    var previewTime: Double? = nil
    var previewReducedMotion = false
    @Environment(\.accessibilityReduceMotion) private var systemReducedMotion
    @State private var composerPresented = false
    @State private var summaryPresented = false
    @State private var goalName = ""
    @State private var goalAmount = ""
    @State private var goalReady = false
    @FocusState private var formFocus: TrophyFormField?
    private enum TrophyFormField { case name,amount }
    private var reduced: Bool { systemReducedMotion || previewReducedMotion }
    private static let backdrop: UIImage = {
        let format=UIGraphicsImageRendererFormat();format.scale=3;format.opaque=true
        return UIGraphicsImageRenderer(size:CGSize(width:393,height:852),format:format).image { renderer in
            UIColor(red:239/255,green:239/255,blue:239/255,alpha:1).setFill();renderer.fill(CGRect(x:0,y:0,width:393,height:852))
            for y in stride(from:7.25,to:380.0,by:11.5) {
                let fade=max(0,min(1,(380-y)/30));UIColor.black.withAlphaComponent(0.09*fade).setFill()
                for x in stride(from:0.25,to:393.0,by:11.5) { renderer.cgContext.fillEllipse(in:CGRect(x:x-0.55,y:y-0.55,width:1.1,height:1.1)) }
            }
        }
    }()
    var body: some View {
        GeometryReader { geometry in
            let scale=min(geometry.size.width/393,geometry.size.height/852)
            ZStack(alignment:.topLeading) {
                Image(uiImage:Self.backdrop).accessibilityHidden(true)
                TrophySavingsMilestoneHero(autoPlay:autoPlay,previewTime:previewTime,previewReducedMotion:previewReducedMotion,overlay:composerPresented || summaryPresented)
                stationaryContent
            }.frame(width:393,height:852).scaleEffect(scale,anchor:.topLeading)
                .frame(width:393*scale,height:852*scale,alignment:.topLeading).frame(maxWidth:.infinity,maxHeight:.infinity)
        }.background(Color(hex:0xEFEFEF).ignoresSafeArea()).ignoresSafeArea().preferredColorScheme(.light)
            .sheet(isPresented:$composerPresented) { goalComposer }
            .alert("Savings Summary",isPresented:$summaryPresented) { Button("Done",role:.cancel) {} }
                message: { Text("School Fees\nTarget reached: ₦70,000\nDuration: 3 months\nTransfers: 5") }
    }
    private var stationaryContent: some View {
        ZStack(alignment: .topLeading) {
            Text("You Did It!").font(.system(size:31,weight:.bold)).tracking(0.15).foregroundStyle(Color(hex:0x222222))
                .frame(width:150,height:38,alignment:.leading).position(x:179,y:381.5).allowsHitTesting(false)
                .accessibilityLabel("You Did It!").accessibilityAddTraits(.isHeader)
            TrophySavingsMilestoneTargetGlyph().position(x:271.5,y:382)
            Text("You've successfully reached your savings\ngoal for School Fees. Every little step added\nup to something great!")
                .font(.system(size: 14.5)).foregroundStyle(Color(hex: 0x444444)).multilineTextAlignment(.center)
                .lineSpacing(2).fixedSize(horizontal: false, vertical: true).frame(width: 340, height: 60).position(x: 196.5, y: 442.5)
            Text("Milestone Details").font(.system(size: 15, weight: .medium)).foregroundStyle(Color(hex: 0x222222))
                .frame(width: 353, height: 20, alignment: .leading).position(x: 196.5, y: 513)
            metric("Target Reached", "₦70,000", kind: 0, accent: 0x1BA14D, fill: 0xF5FEF9, width: 353).position(x: 196.5, y: 573.5)
            metric("Duration", "3 months", kind: 1, accent: 0x327FE9, fill: 0xF6F9FE, width: 173).position(x: 106.5, y: 653.5)
            metric("Transfers", "5", kind: 2, accent: 0xDB0DD1, fill: 0xFFF5FE, width: 173).position(x: 286.5, y: 653.5)
            Button {
                goalName = ""; goalAmount = ""; goalReady = false; composerPresented = true
            } label: { Text("Set New Goal").frame(width: 187, height: 49).background(Color(hex: 0x222222), in: Capsule()) }
                .foregroundStyle(.white).position(x: 122.5, y: 762.5)
            Button { summaryPresented = true } label: {
                Text("View Summary").frame(width: 134, height: 49).background(Color(hex: 0xF7F7F7), in: Capsule())
            }.foregroundStyle(Color(hex: 0x222222)).position(x: 299, y: 762.5)
        }.font(.system(size: 14, weight: .medium)).buttonStyle(TrophySavingsMilestonePressStyle(reduced: reduced))
    }
    private func metric(_ title: String, _ value: String, kind: Int, accent: UInt32, fill: UInt32, width: CGFloat) -> some View {
        HStack(spacing: 8) {
            TrophySavingsMilestoneMetricIcon(kind: kind, color: Color(hex: accent)).accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.system(size: 13)).foregroundStyle(Color(hex: 0x868686))
                Text(value).font(.system(size: 17, weight: .semibold)).foregroundStyle(Color(hex: 0x222222))
            }
            Spacer(minLength: 0)
        }.padding(.horizontal, 16).frame(width: width, height: 73)
            .background(Color(hex: fill), in: RoundedRectangle(cornerRadius: 24))
            .accessibilityElement(children: .combine)
    }
    private var validAmount: Double? {
        guard let value = Double(goalAmount.replacingOccurrences(of: ",", with: "")), value.isFinite, value > 0 else { return nil }
        return value
    }
    private var validGoal: Bool { !goalName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && validAmount != nil }
    private var goalComposer: some View {
        NavigationStack {
            Form {
                if goalReady {
                    Section("Goal Ready") {
                        Text(goalName.trimmingCharacters(in: .whitespacesAndNewlines))
                        Text("Target: ₦\((validAmount ?? 0).formatted(.number.precision(.fractionLength(0...2))))")
                        Text("Your new goal is ready on this device.").foregroundStyle(.secondary)
                    }
                } else {
                    Section("New savings goal") {
                        TextField("Goal name", text: $goalName).accessibilityLabel("Goal name").focused($formFocus, equals: .name)
                            .submitLabel(.next).onSubmit { formFocus = .amount }
                        TextField("Target amount", text: $goalAmount).accessibilityLabel("Target amount")
                            .keyboardType(.decimalPad).focused($formFocus, equals: .amount)
                    }
                    Section { Text("This creates a local goal. Your completed School Fees milestone stays unchanged.").font(.footnote).foregroundStyle(.secondary) }
                }
            }.navigationTitle(goalReady ? "Goal Ready" : "Set New Goal").navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { if !goalReady { Button("Cancel") { composerPresented = false } } }
                    ToolbarItem(placement: .confirmationAction) {
                        if goalReady { Button("Done") { composerPresented = false } }
                        else { Button("Create Goal") { formFocus = nil; goalReady = true }.disabled(!validGoal) }
                    }
                    ToolbarItemGroup(placement: .keyboard) { Spacer(); Button("Done") { formFocus = nil } }
                }
        }.presentationDetents([.medium, .large])
    }
}

private struct TrophySavingsMilestonePressStyle: ButtonStyle {
    let reduced: Bool
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.opacity(configuration.isPressed ? 0.88 : 1)
    }
}
