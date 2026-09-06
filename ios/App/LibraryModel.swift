import Foundation
import Combine
import PDFKit
import UIKit
import NoScrollCore

struct LocalBook: Codable, Identifiable, Equatable {
    let id: UUID
    var title: String
    var lastPage: Int
    var addedAt: Date
    var filename: String { id.uuidString + ".pdf" }
}

@MainActor
final class LibraryModel: ObservableObject {
    @Published private(set) var books: [LocalBook] = []
    @Published var message: String?
    private var directory: URL?
    private var catalog: URL? { directory?.appendingPathComponent("library.json") }

    init() {
        do {
            let root = try FileManager.default.url(for: .documentDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
                .appendingPathComponent("Books", isDirectory: true)
            try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
            directory = root
            if let catalog, FileManager.default.fileExists(atPath: catalog.path) {
                let data = try Data(contentsOf: catalog)
                guard data.count <= 2_097_152 else { throw NativeError.invalidArchive }
                books = try JSONDecoder().decode([LocalBook].self, from: data)
            } else { try addWelcomeBook() }
        } catch { message = "The local library could not be opened. Existing files have not been deleted." }
    }
    func url(for book: LocalBook) -> URL? { directory?.appendingPathComponent(book.filename) }

    func importPDF(from source: URL) {
        let access = source.startAccessingSecurityScopedResource()
        defer { if access { source.stopAccessingSecurityScopedResource() } }
        do {
            let size = try source.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
            guard size > 0, size <= 104_857_600, let document = PDFDocument(url: source), !document.isLocked, document.pageCount > 0 else {
                message = "Choose an unlocked PDF smaller than 100 MB."; return
            }
            let book = LocalBook(id: UUID(), title: String(source.deletingPathExtension().lastPathComponent.prefix(120)), lastPage: 0, addedAt: Date())
            guard let destination = url(for: book) else { throw NativeError.appGroupUnavailable }
            try FileManager.default.copyItem(at: source, to: destination)
            let old = books; books.insert(book, at: 0)
            do { try persist(); message = nil }
            catch { books = old; try? FileManager.default.removeItem(at: destination); throw error }
        } catch { message = "This PDF could not be imported. No existing books were changed." }
    }
    func saveProgress(id: UUID, page: Int, pageCount: Int) {
        guard let index = books.firstIndex(where: { $0.id == id }) else { return }
        let page = ReadingProgress.clampedPage(page, pageCount: pageCount)
        guard books[index].lastPage != page else { return }
        books[index].lastPage = page
        do { try persist() } catch { message = "Reading position could not be saved." }
    }
    private func persist() throws {
        guard let catalog else { throw NativeError.appGroupUnavailable }
        try JSONEncoder().encode(books).write(to: catalog, options: [.atomic, .completeFileProtection])
    }
    private func addWelcomeBook() throws {
        let book = LocalBook(id: UUID(), title: "A page instead of a feed", lastPage: 0, addedAt: Date())
        guard let destination = url(for: book) else { throw NativeError.appGroupUnavailable }
        let bounds = CGRect(x: 0, y: 0, width: 612, height: 792)
        let data = UIGraphicsPDFRenderer(bounds: bounds).pdfData { context in
            let pages = [
                ("A page instead of a feed", "Pause for one breath.\n\nYou do not need to finish a book today. You only need a place to begin.\n\nRead a paragraph. Notice one idea. Let the next action be a choice rather than a reflex.\n\nThis original sample is included so you can test NoScroll before importing a PDF."),
                ("Make room for attention", "A useful break is not another task to complete perfectly.\n\nChoose a book you actually want to read. Keep a question in mind as you turn the page.\n\nWhen you return to Instagram, NoScroll's navigation pause remains unprotected until one of your mapped allowed screens is recognized. You can always disable the session explicitly.")
            ]
            for (title, body) in pages {
                context.beginPage()
                (title as NSString).draw(in: CGRect(x: 54, y: 64, width: 504, height: 100), withAttributes: [.font: UIFont.boldSystemFont(ofSize: 28)])
                (body as NSString).draw(in: CGRect(x: 54, y: 176, width: 504, height: 540), withAttributes: [.font: UIFont.systemFont(ofSize: 20)])
            }
        }
        try data.write(to: destination, options: [.atomic, .completeFileProtection])
        books = [book]; try persist()
    }
}
