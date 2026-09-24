import SwiftUI

/// Two fields and a sentence. The address is typed once; the code comes
/// from Settings → Assistants on the dashboard and lasts five minutes,
/// which is said here so nobody wonders why a code from yesterday is
/// refused.
struct PairView: View {
    @Environment(AppModel.self) private var model
    @State private var url = ""
    @State private var code = ""

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("Open the dashboard in a browser, go to Settings → Assistants → Pair a phone or a tablet, and type what it shows here.")
                        .font(.callout)
                        .foregroundStyle(.secondary)
                }
                Section {
                    TextField("dashboard.example.com", text: $url)
                        .keyboardType(.URL)
                        .textContentType(.URL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    TextField("123456", text: $code)
                        .keyboardType(.numberPad)
                        .textContentType(.oneTimeCode)
                        .onChange(of: code) { _, new in
                            let digits = String(new.filter(\.isNumber).prefix(6))
                            if digits != new { code = digits }
                        }
                } header: {
                    Text("Address and pairing code")
                }
                Section {
                    Button {
                        Task { await model.pair(url: url, code: code) }
                    } label: {
                        HStack {
                            Text("Pair this device")
                            Spacer()
                            if model.pairing { ProgressView() }
                        }
                    }
                    .disabled(model.pairing || url.trimmingCharacters(in: .whitespaces).isEmpty || code.count != 6)
                }
                if let error = model.error {
                    Section {
                        Text(error).foregroundStyle(Color.loss)
                    }
                }
                Section {
                    Text("The code is good for five minutes and for one device. The token it hands back lives in this phone's Keychain, and “Forget this dashboard” removes it.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Wealth Dashboard")
        }
    }
}
