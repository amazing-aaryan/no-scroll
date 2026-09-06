import SwiftUI
import FamilyControls
import ReplayKit
import NoScrollCore

struct ProtectionScreen: View {
    @ObservedObject var model: AppModel
    @State private var showPicker = false
    @State private var showMapping = false
    @State private var draftSelection = FamilyActivitySelection()
    @State private var confirmInstagram = false
    @State private var acknowledgeCapture = false
    private let refreshTimer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        NavigationStack {
            Form {
                Section("Experimental native protection") {
                    Text(model.captureStatus).accessibilityIdentifier("capture.status")
                    Text("Last recognized mapping: \(model.state.capture.lastMatch). Interruptions: \(model.state.interruptionCount).")
                        .font(.caption)
                    if let issue = model.state.capture.issue { Text("Capture issue: \(issue)").font(.caption) }
                    if let message = model.message { Text(message).foregroundStyle(.red) }
                }
                Section("1. Map your Instagram screens") {
                    Text("Map three small, distinctive static controls on each screen. Add blocked Reels/Home and allowed Messages/Profile/Story examples. Theme or layout changes need new maps.")
                    ForEach(model.profiles) { profile in
                        HStack {
                            Label(profile.name, systemImage: profile.disposition == .block ? "hand.raised" : "checkmark")
                            Spacer()
                            Button(role: .destructive) { model.deleteProfile(id: profile.id) } label: { Image(systemName: "trash") }
                                .accessibilityLabel("Delete \(profile.name)")
                        }
                    }
                    Button("Add screen map") { showMapping = true }.disabled(model.profiles.count >= 8)
                    Text("Saving or deleting a map ends the protection session. Start with observation and verify matches before enabling blocking.").font(.caption)
                }
                Section("2. Screen capture consent") {
                    Text("iOS will provide the entire screen, including messages and other apps, while broadcasting. NoScroll analyzes frames locally and discards them; it does not record, upload, or analyze audio. Saved maps contain only selected landmark feature vectors. Stop at any time with the iOS recording control.")
                    Toggle("I understand and consent to local screen analysis", isOn: $acknowledgeCapture)
                    Button("Prepare observation") { model.perform { try $0.consentToObservation() } }
                        .disabled(!acknowledgeCapture || model.profiles.isEmpty)
                    BroadcastPicker().frame(width: 52, height: 52)
                        .disabled(model.state.consentVersion != ControlState.currentConsentVersion || model.state.mode == .off)
                    Text("Tap the system broadcast button above, then select Start Broadcast. Locking or restarting can stop capture; this app cannot silently restart it.").font(.caption)
                }
                Section("3. Optional experimental blocking") {
                    Button("Authorize Screen Time") { Task { await model.requestAuthorization() } }
                    Button("Choose Instagram") { draftSelection = model.selection; confirmInstagram = false; showPicker = true }
                    Text("Selecting an app ends the session; choose it before starting capture. Apple supplies an opaque selection token. NoScroll cannot verify the selected app's identity. Select Instagram only.").font(.caption)
                    Button("Enable experimental blocking") { model.perform { try $0.setMode(.protect) } }
                    Button("Observe without blocking") { model.perform { try $0.setMode(.observe) } }
                    Text("A visual match restricts the entire selected app, not just its Reels rectangle. Similar-looking screens in other apps can cause false matches; this is not release-validated.").font(.caption)
                }
                Section("Reading and recovery") {
                    Button("Open reader") { model.openReader() }
                    Button("Allow reading notifications") { Task { await model.requestNotifications() } }
                    Button("Pause blocking to navigate") { model.perform { try $0.recover() } }
                    Text("Navigation pause gives full access until a mapped allowed screen is stably recognized. It can stay paused indefinitely. No automatic Back tap is performed.").font(.caption)
                    Button("Disable & clear restrictions", role: .destructive) {
                        acknowledgeCapture = false
                        model.perform { try $0.disable() }
                    }.accessibilityIdentifier("protection.disable")
                    Text("Disabling invalidates capture processing. If the recording indicator remains, stop broadcasting in Control Center. Capture stopping alone does not release an existing shield.").font(.caption)
                }
            }
            .navigationTitle("NoScroll")
            .onReceive(refreshTimer) { _ in model.refresh() }
            .sheet(isPresented: $showMapping) { ScreenMappingView { model.addProfile($0) } }
            .sheet(isPresented: $showPicker) {
                NavigationStack {
                    VStack {
                        FamilyActivityPicker(selection: $draftSelection)
                        Toggle("I selected Instagram only", isOn: $confirmInstagram).padding()
                    }
                    .navigationTitle("Select one app")
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) { Button("Cancel") { showPicker = false } }
                        ToolbarItem(placement: .confirmationAction) {
                            Button("Save") { model.perform { try $0.saveSelection(draftSelection) }; showPicker = false }
                                .disabled(!confirmInstagram || draftSelection.applicationTokens.count != 1 ||
                                          !draftSelection.categoryTokens.isEmpty || !draftSelection.webDomainTokens.isEmpty)
                        }
                    }
                }
            }
        }
    }
}

private struct BroadcastPicker: UIViewRepresentable {
    func makeUIView(context: Context) -> RPSystemBroadcastPickerView {
        let view = RPSystemBroadcastPickerView(frame: CGRect(x: 0, y: 0, width: 52, height: 52))
        view.preferredExtension = Bundle.main.object(forInfoDictionaryKey: "NoScrollBroadcastExtension") as? String
        view.showsMicrophoneButton = false
        return view
    }
    func updateUIView(_ uiView: RPSystemBroadcastPickerView, context: Context) {}
}
