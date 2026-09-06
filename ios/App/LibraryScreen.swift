import SwiftUI
import PDFKit
import UniformTypeIdentifiers
import NoScrollCore

struct LibraryScreen: View {
    @ObservedObject var library: LibraryModel
    let readerRequest: UUID
    @State private var showImport = false
    @State private var activeBook: LocalBook?
    var body: some View {
        NavigationStack {
            List {
                if let message = library.message { Text(message).foregroundStyle(.red) }
                ForEach(library.books) { book in
                    Button { activeBook = book } label: {
                        VStack(alignment: .leading, spacing: 5) {
                            Text(book.title).font(.headline)
                            Text("Continue on page \(book.lastPage + 1)").font(.caption)
                        }
                    }
                }
            }
            .navigationTitle("Your reading")
            .toolbar { Button("Import PDF", systemImage: "plus") { showImport = true } }
            .fileImporter(isPresented: $showImport, allowedContentTypes: [.pdf], allowsMultipleSelection: false) { result in
                switch result {
                case .success(let urls): if let first = urls.first { library.importPDF(from: first) }
                case .failure: library.message = "PDF selection was not completed."
                }
            }
            .sheet(item: $activeBook) { book in
                if let url = library.url(for: book) {
                    NavigationStack {
                        PDFReader(url: url, initialPage: book.lastPage) { page, count in
                            library.saveProgress(id: book.id, page: page, pageCount: count)
                        }
                        .navigationTitle(book.title)
                        .navigationBarTitleDisplayMode(.inline)
                        .toolbar { Button("Done") { activeBook = nil } }
                    }
                }
            }
            .onChange(of: readerRequest) { _, _ in activeBook = library.books.first }
        }
    }
}

private struct PDFReader: UIViewRepresentable {
    let url: URL
    let initialPage: Int
    let onPageChange: (Int, Int) -> Void
    func makeCoordinator() -> Coordinator { Coordinator(onPageChange: onPageChange) }
    func makeUIView(context: Context) -> PDFView {
        let view = PDFView()
        view.autoScales = true; view.displayMode = .singlePageContinuous; view.displayDirection = .vertical
        view.document = PDFDocument(url: url)
        if let document = view.document,
           let page = document.page(at: ReadingProgress.clampedPage(initialPage, pageCount: document.pageCount)) {
            view.go(to: page)
        }
        context.coordinator.observe(view)
        return view
    }
    func updateUIView(_ uiView: PDFView, context: Context) {}
    static func dismantleUIView(_ uiView: PDFView, coordinator: Coordinator) { coordinator.stop() }
    final class Coordinator {
        private var token: NSObjectProtocol?
        private let onPageChange: (Int, Int) -> Void
        init(onPageChange: @escaping (Int, Int) -> Void) { self.onPageChange = onPageChange }
        func observe(_ view: PDFView) {
            token = NotificationCenter.default.addObserver(forName: .PDFViewPageChanged, object: view, queue: .main) { [weak self, weak view] _ in
                guard let view, let document = view.document, let page = view.currentPage else { return }
                self?.onPageChange(document.index(for: page), document.pageCount)
            }
        }
        func stop() { if let token { NotificationCenter.default.removeObserver(token) }; token = nil }
        deinit { stop() }
    }
}
