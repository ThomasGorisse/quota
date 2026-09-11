import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var model: StatusViewModel
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var scheme
    @State private var showsWidgetPreview = false
    @State private var confirmUnpair = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("Show", selection: Binding(
                        get: { model.displayMode },
                        set: { mode in Task { await model.setDisplayMode(mode) } }
                    )) {
                        ForEach(DisplayMode.allCases) { mode in
                            Text(mode.title).tag(mode)
                        }
                    }
                    .pickerStyle(.segmented)
                    if let error = model.displayError {
                        Text(error)
                            .font(.footnote)
                            .foregroundStyle(QuotaTheme.critical)
                    }
                } header: {
                    Text("Display")
                } footer: {
                    Text("Choose the number that feels natural. Your widgets, Apple Watch and dashboard follow this preference.")
                }

                Section {
                    Toggle("Demo data", isOn: $model.demoData)
                } header: {
                    Text("Preview")
                } footer: {
                    Text("Explore Quota with sample limits. Turn this off to return to your connected collector.")
                }

                Section("Pairing") {
                    LabeledContent("Host", value: model.host ?? "not paired")
                    LabeledContent("Token", value: model.isPaired ? "stored" : "none")
                    DisclosureGroup("Connection details") {
                        LabeledContent("Storage", value: model.tokenStorage).font(.footnote)
                        LabeledContent("Widget sharing", value: model.isSharedContainer ? "Enabled" : "Unavailable")
                    }
                }

                Section {
                    Button(model.isRefreshing ? "Refreshing…" : "Refresh now") { Task { await model.refresh() } }
                        .disabled(model.isRefreshing)
                    Button("Preview widgets") { showsWidgetPreview = true }
                    Button("Unpair", role: .destructive) { confirmUnpair = true }
                        .disabled(!model.isPaired)
                }

                Section {
                    Text("Your limits go only to your connected collector and paired Apple Watch. No analytics, advertising or tracking.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                } header: {
                    Text("Privacy")
                }
            }
            .scrollContentBackground(.hidden)
            .background(Palette.of(scheme).background.ignoresSafeArea())
            .tint(QuotaTheme.ok)
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .sheet(isPresented: $showsWidgetPreview) {
                WidgetPreviewView(snapshot: model.snapshot ?? DemoData.snapshot())
            }
            .alert("Unpair this device?", isPresented: $confirmUnpair) {
                Button("Unpair", role: .destructive) { model.unpair(); dismiss() }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("The token is deleted from this phone. Pair again with the URL the collector printed.")
            }
        }
    }
}
