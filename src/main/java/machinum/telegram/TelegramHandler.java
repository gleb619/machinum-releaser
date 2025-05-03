package machinum.telegram;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.book.Book;
import machinum.book.BookRestClient;
import machinum.image.ImageRepository;
import machinum.markdown.MarkdownConverter;
import machinum.pandoc.PandocRestClient;
import machinum.release.ReleaseRepository;
import machinum.scheduler.ActionHandler;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static machinum.Util.getKeyByValue;
import static machinum.pandoc.PandocRestClient.PandocRequest.createNew;
import static machinum.release.Release.ReleaseConstants.PAGES_PARAM;
import static machinum.telegram.TelegramHandler.TelegramConstants.TELEGRAM_BOOK_ID;
import static machinum.telegram.TelegramHandler.TelegramConstants.TELEGRAM_CHAPTER_ID;

/**
 * Instance is used for interacting with the telegram API for scheduled releases.
 */
@Slf4j
@RequiredArgsConstructor
public class TelegramHandler implements ActionHandler {

    private final TelegramService telegramService;
    private final ReleaseRepository repository;
    private final ImageRepository imageRepository;
    private final BookRestClient bookRestClient;
    private final MarkdownConverter markdownConverter;
    private final PandocRestClient pandocRestClient;
    private final String channelName;

    /**
     * Handles the action context based on whether it's the first or subsequent release.
     *
     * @param context The ActionContext to handle.
     */
    @Override
    public void handle(ActionContext context) {
        if (context.isFirstRelease()) {
            releaseBook(context);
            releaseChapters(context);
        } else {
            releaseChapters(context);
        }
    }

    /* ============= */

    /**
     * Converts a Book instance to a filename in lowercase, replacing non-alphanumeric characters with underscores.
     *
     * @param book The Book instance to convert.
     * @return A String representing the filename derived from the given Book.
     */
    private String convertToFileName(Book book) {
        return book.getEnName().toLowerCase()
                .replaceAll("[^0-9a-zA-Z]", "_")
                .trim()
                .concat(".epub");
    }

    /**
     * Releases a new book on telegram if it's the first release.
     *
     * @param context The ActionContext containing the Book to be released.
     */
    private void releaseBook(ActionContext context) {
        String imageId = context.getBook().getImageId();
        String originImageId = context.getBook().getOriginImageId();
        var images = imageRepository.findByIds(List.of(imageId, originImageId));
        var response = telegramService.publishNewBook(context.getBook(), images);
        var tgBookId = response.messageId();
        context.set(TELEGRAM_BOOK_ID, tgBookId);

        context.getRelease().addMetadata(TELEGRAM_BOOK_ID, tgBookId);
    }

    /**
     * Releases new chapters on telegram for a given book and release context.
     *
     * @param context The ActionContext containing the Book and Release to be released.
     */
    private void releaseChapters(ActionContext context) {
        var status = context.isLastRelease() ? "Завершена ветка перевода" : "Перевод продолжается";
        var book = context.getBook();
        var release = context.getRelease();
        var bookName = convertToFileName(book);
        var tgBookId = (Integer) context.getOr(TELEGRAM_BOOK_ID, () ->
                repository.findSingleMetadata(release.getReleaseTargetId(), TELEGRAM_BOOK_ID));

        var chapters = (String) release.metadata(PAGES_PARAM);
        var chaptersRequest = release.toPageRequest();
        var remoteBookId = getRemoteBookId(book);
        var chapterList = bookRestClient.getReadyChaptersCached(remoteBookId, chaptersRequest.first(), chaptersRequest.second());
        var markdowns = chapterList.stream()
                .map(markdownConverter::toMarkdown)
                .map(s -> s.getBytes(StandardCharsets.UTF_8))
                .toList();
        var image = imageRepository.getById(book.getImageId());

        var epubBytes = pandocRestClient.convertToEpubCached(createNew(b -> b
                .startIndex(chaptersRequest.first())
                .markdownFiles(markdowns)
                .title(book.getRuName())
                .author(book.getAuthor())
                .coverImage(image.getData())
                .coverContentType(image.getContentType())
                .tocDepth(2)
                .outputFilename(bookName)
        ));

        if (Objects.nonNull(epubBytes) && epubBytes.length > 0) {
            var response = telegramService.publishNewChapter(book.getRuName(),
                    tgBookId,
                    channelName,
                    chapters,
                    status,
                    epubBytes);
            context.set(TELEGRAM_CHAPTER_ID, response.messageId());

            context.getRelease().addMetadata(TELEGRAM_CHAPTER_ID, response.messageId());
        } else {
            throw new IllegalStateException("Epub generation is failed");
        }
    }

    private String getRemoteBookId(Book book) {
        var result = getKeyByValue(bookRestClient.getAllBookTitlesCached(), book.getUniqueId());

        return Objects.requireNonNull(result, "Can't find remote book for given title: %s".formatted(book.getUniqueId()));
    }

    /* ============= */

    /**
     * A nested class containing constants used throughout the TelegramHandler class.
     */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class TelegramConstants {

        public static final String TELEGRAM_BOOK_ID = "tgBookId";

        public static final String TELEGRAM_CHAPTER_ID = "tgChapterId";

    }

}
