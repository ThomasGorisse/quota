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
                    Text("Capacity left, or capacity spent. The setting lives on the backend, so the widgets, the watch, the web dashboard and the e-ink screen all follow.")
                }

                Section {
                    Toggle("Demo data", isOn: $model.demoData)
                } header: {
                    Text("Preview")
                } footer: {
                    Text("Fills the app, the widgets and the watch with a canned snapshot covering the three severities, so every screen can be checked without the backend.")
                }

                Section("Pairing") {
                    LabeledContent("Host", value: model.host ?? "not paired")
                    LabeledContent("Token", value: model.isPaired ? "stored" : "none")
                    LabeledContent("Storage", value: model.tokenStorage)
                        .font(.footnote)
                    LabeledContent("App Group", value: model.isSharedContainer ? "shared" : "app only")
                }

                Section {
                    Button("Refresh now") { Task { await model.refresh() } }
                    Button("Preview widgets") { showsWidgetPreview = true }
                    Button("Unpair", role: .destructive) { confirmUnpair = true }
                        .disabled(!model.isPaired)
                }

                Section {
                    Text("Quota sends nothing anywhere except the host you paired with. No analytics, no third-party SDK, and the token is never written to a log.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                } header: {
                    Text("Privacy")
                }
            }
            .scrollContentBackground(.hidden)
            .background(Palette.of(scheme).background.ignoresSafeArea())
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
