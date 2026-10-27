import SwiftUI

/// Deliberately separate from the reconstructed product composition. Hold Back
/// (or the five-step progress) for 0.8 s to open. No automatic replay on launch.
struct TradingBotsQAHarness: View {
    let step: TradingBotsFlow.Step
    @Binding var reduced: Bool
    let show: (TradingBotsFlow.Step) -> Void
    let replay: () -> Void
    let reset: () -> Void
    var body: some View {
        NavigationStack {
            Form {
                Section("Inspect a scene") {
                    ForEach(TradingBotsFlow.Step.allCases) { destination in
                        Button { show(destination) } label: {
                            HStack { Text(destination.name); Spacer(); if destination == step { Image(systemName: "checkmark") } }
                        }
                    }
                }
                Section("Motion") {
                    Toggle("Force reduced motion", isOn: $reduced)
                    Text("The system Reduce Motion setting is always respected. This override allows repeatable QA without changing device settings.").font(.footnote)
                    Button("Play walkthrough (about 20 seconds)", action: replay)
                    Button("Reset to wallet", action: reset)
                }
                Section("Local prototype") {
                    Text("All money, bot settings and subscription values are fixtures. Trial, terms and privacy actions open local demo sheets. No network, trading or purchase is performed.")
                }
            }.navigationTitle("Showcase QA").navigationBarTitleDisplayMode(.inline)
        }.preferredColorScheme(.dark)
    }
}

enum TradingBotsDetail: String, Identifiable {
    case overview, trial, terms, privacy, finish
    var id: String { rawValue }
    var title: String {
        switch self {
        case .overview: "Simulation overview"
        case .trial: "Demo trial selected"
        case .terms: "Showcase terms"
        case .privacy: "Showcase privacy"
        case .finish: "You're ready to explore"
        }
    }
}

struct TradingBotsDetailSheet: View {
    @Environment(\.dismiss) private var dismiss
    let detail: TradingBotsDetail
    let risk: String
    let allocation: Int
    let replay: () -> Void
    var body: some View {
        NavigationStack {
            VStack(spacing: 25) {
                TradingBotsMark(size: 55)
                Text(detail.title).font(.title2.weight(.semibold))
                if detail == .overview {
                    Text("Simulated return: +$1,284.50\nCapital allocation: \(allocation)%\nRisk per trade: \(risk)")
                        .font(.body.monospacedDigit()).lineSpacing(9)
                }
                Text(detail == .privacy ? "This showcase makes no network requests and does not collect wallet, account or payment details. Settings live only for this demo session." : detail == .terms ? "This is an interactive design reconstruction. Balances, simulated returns and subscription prices are fictional local fixtures; no financial service or subscription is provided." : "This is a local showcase. No wallet is connected, no trades are placed, and no trial or purchase is activated.")
                    .foregroundStyle(.secondary).multilineTextAlignment(.center)
                Button("Keep exploring") { dismiss() }.buttonStyle(.borderedProminent)
                Button("Replay onboarding", action: replay)
            }.padding(30).navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Done") { dismiss() } } }
        }.presentationDetents([.medium, .large]).preferredColorScheme(.dark)
    }
}
