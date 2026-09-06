import SwiftUI
import PhotosUI
import ImageIO
import NoScrollCore

struct ScreenMappingView: View {
    let onSave: (ScreenProfile) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var photo: PhotosPickerItem?
    @State private var image: CGImage?
    @State private var name = ""
    @State private var disposition = ScreenDisposition.block
    @State private var rectangles: [UnitRect] = []
    @State private var busy = false
    @State private var errorMessage: String?
    var body: some View {
        NavigationStack {
            Form {
                Section("Choose a screenshot from this iPhone") {
                    TextField("Name, such as Reels dark or Messages", text: $name)
                    Picker("Screen policy", selection: $disposition) {
                        Text("Block this surface").tag(ScreenDisposition.block)
                        Text("Allow this surface").tag(ScreenDisposition.allow)
                    }
                    PhotosPicker("Choose screenshot", selection: $photo, matching: .images).disabled(busy)
                    Text("Use only static UI controls, never message text, account names, video content, changing counts, or the status bar. The source screenshot is not saved by NoScroll. Feature vectors for your selected regions are saved locally.").font(.caption)
                }
                if let image {
                    Section("Drag three separate small rectangles: \(rectangles.count)/3") {
                        LandmarkCanvas(image: image, rectangles: $rectangles)
                            .frame(height: 420)
                        HStack {
                            Button("Undo") { if !rectangles.isEmpty { rectangles.removeLast() } }
                            Button("Clear") { rectangles.removeAll() }
                        }
                        Text("Each region must be at least 2% wide/high and at most 12% of the screen area. Regions cannot overlap. Use a distinctive title, selected tab, and another stable control.").font(.caption)
                    }
                }
                if busy { ProgressView("Creating local landmark descriptors…") }
                if let errorMessage { Text(errorMessage).foregroundStyle(.red) }
            }
            .navigationTitle("Map screen")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { image = nil; dismiss() }.disabled(busy) }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { Task { await save() } }
                        .disabled(busy || rectangles.count != 3 || name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || name.count > 40)
                }
            }
            .onChange(of: photo) { _, newItem in Task { await load(newItem) } }
        }
        .interactiveDismissDisabled(busy)
    }

    private func load(_ item: PhotosPickerItem?) async {
        guard let item else { return }
        busy = true; errorMessage = nil; rectangles = []; image = nil
        defer { busy = false }
        do {
            guard let data = try await item.loadTransferable(type: Data.self), data.count <= 20_971_520 else { throw NativeError.imageTooLarge }
            let decoded = try await Task.detached(priority: .userInitiated) {
                guard let source = CGImageSourceCreateWithData(data as CFData, [kCGImageSourceShouldCache: false] as CFDictionary),
                      let image = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                        kCGImageSourceCreateThumbnailFromImageAlways: true,
                        kCGImageSourceCreateThumbnailWithTransform: true,
                        kCGImageSourceThumbnailMaxPixelSize: 1600,
                        kCGImageSourceShouldCacheImmediately: true
                      ] as CFDictionary) else { throw NativeError.unreadableImage }
                let ratio = Double(image.width) / Double(image.height)
                guard (0.35...0.8).contains(ratio) else { throw NativeError.invalidMapping }
                return image
            }.value
            image = decoded
        } catch { errorMessage = "Use a portrait iPhone screenshot smaller than 20 MB. This image could not be loaded." }
        photo = nil
    }

    private func save() async {
        guard let image, rectangles.count == 3 else { return }
        busy = true; errorMessage = nil
        defer { busy = false }
        let label = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let regions = rectangles, policy = disposition
        do {
            let profile = try await Task.detached(priority: .userInitiated) {
                try VisionMatcher.makeProfile(image: image, name: label, disposition: policy, rectangles: regions)
            }.value
            onSave(profile); self.image = nil; dismiss()
        } catch { errorMessage = "The mapping could not be created. Check the three regions and try on a physical iPhone." }
    }
}

private struct LandmarkCanvas: View {
    let image: CGImage
    @Binding var rectangles: [UnitRect]
    @State private var pending: UnitRect?
    var body: some View {
        GeometryReader { geometry in
            let ratio = CGFloat(image.width) / CGFloat(image.height)
            let height = min(geometry.size.height, geometry.size.width / ratio)
            let width = height * ratio
            ZStack(alignment: .topLeading) {
                Image(decorative: image, scale: 1).resizable().frame(width: width, height: height)
                ForEach(Array(rectangles.enumerated()), id: \.offset) { index, rect in
                    rectangle(rect, width: width, height: height)
                        .overlay(alignment: .topLeading) { Text("\(index + 1)").font(.caption).foregroundStyle(.white).background(.black) }
                }
                if let pending { rectangle(pending, width: width, height: height) }
            }
            .frame(width: width, height: height)
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 3)
                .onChanged { value in
                    guard rectangles.count < 3 else { return }
                    pending = normalized(start: value.startLocation, end: value.location, width: width, height: height)
                }
                .onEnded { _ in
                    if let rect = pending, rect.isValidLandmark, !rectangles.contains(where: { $0.overlaps(rect) }) { rectangles.append(rect) }
                    pending = nil
                })
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .accessibilityLabel("Screenshot mapping canvas. Drag three rectangles around static interface controls.")
    }
    private func rectangle(_ rect: UnitRect, width: CGFloat, height: CGFloat) -> some View {
        Rectangle().stroke(.orange, lineWidth: 2)
            .frame(width: rect.width * width, height: rect.height * height)
            .offset(x: rect.x * width, y: rect.y * height)
    }
    private func normalized(start: CGPoint, end: CGPoint, width: CGFloat, height: CGFloat) -> UnitRect {
        let x1 = min(max(start.x / width, 0), 1), y1 = min(max(start.y / height, 0), 1)
        let x2 = min(max(end.x / width, 0), 1), y2 = min(max(end.y / height, 0), 1)
        return UnitRect(x: min(x1,x2), y: min(y1,y2), width: abs(x2-x1), height: abs(y2-y1))
    }
}
