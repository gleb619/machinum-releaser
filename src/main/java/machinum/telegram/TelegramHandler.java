package machinum.telegram;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.book.Book;
import machinum.book.BookRestClient;
import machinum.exception.AppException;
import machinum.image.Image;
import machinum.image.ImageRepository;
import machinum.image.cover.CoverService;
import machinum.image.cover.CoverService.CoverInfo;
import machinum.markdown.MarkdownConverter;
import machinum.pandoc.PandocRestClient;
import machinum.release.ReleaseRepository;
import machinum.scheduler.ActionHandler;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

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

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final TelegramService telegramService;
    private final TelegramProperties telegramProperties;
    private final ReleaseRepository repository;
    private final ImageRepository imageRepository;
    private final BookRestClient bookRestClient;
    private final MarkdownConverter markdownConverter;
    private final PandocRestClient pandocRestClient;
    private final CoverService coverService;

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

    public static String formatDate(LocalDate now) {
        return now.format(DATE_TIME_FORMATTER);
    }

    public static String startOfYear(int year) {
        LocalDate firstDay = LocalDate.of(year, 1, 1);
        return formatDate(firstDay);
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
        var partIndex = context.getReleasePosition() + 1;

        String number = partIndex + "";
        CoverInfo coverInfo = new CoverInfo(number, book.getRuName(), "M T.\nNOVELS", telegramProperties.getChannelLink(), "@mt_novel", "Subscribe");
        Image coverImage = coverService.generateBookCover(image, coverInfo);
        String fileName = NameUtil.toSnakeCase(book.getEnName()) + "_%s.epub".formatted(partIndex);

        var epubBytes = pandocRestClient.convertToEpubCached(createNew(b -> b
                .startIndex(chaptersRequest.first())
                .markdownFiles(markdowns)
                .coverImage(coverImage.getData())
                .coverContentType(image.getContentType())
                .title(book.getRuName())
                .subtitle("Часть %s".formatted(partIndex))
                .author(book.getAuthor())
                .publisher(telegramProperties.getChatId())
                .publisherInfo("""
                        Мы — скромная, но талантливая команда энтузиастов-переводчиков, собравшихся не ради славы, денег или мирового господства (пока), а ради одной простой, почти наивной идеи: делиться историями и знаниями с каждым, кто готов слушать.
                                                
                        Нам не нужны награды и признание — мы просто верим, что хорошие книги не должны пылиться на полках, прятаться за paywall'ами или исчезать в темноте авторских прав. Их место — в головах и сердцах живых людей, а не в архивах и сейфах.
                                                
                        Так что да, мы здесь, чтобы книги жили. Иногда с опечатками. Иногда с примечаниями в духе "переводчик плакал". Но — жили.
                        """)
                //.edition("")
                .rights("Без авторских прав. Используйте свободно.")
                //.legalRights("")
                //.disclaimer("")
                .description(book.getDescription())
                .keywords(String.join(", ", book.getGenre()))
                .date(startOfYear(book.getYear()))
                .pubdate(formatDate(context.getReleaseTarget().getCreatedAt().toLocalDate()))
                //.website("")
                .socialLinks(List.of(
                        telegramProperties.getChannelLink()
                ))
                .outputFilename(fileName)
        ));

        if (Objects.nonNull(epubBytes) && epubBytes.length > 0) {
            var response = telegramService.publishNewChapter(book.getRuName(),
                    tgBookId,
                    telegramProperties.getChannelName(),
                    chapters,
                    status,
                    fileName,
                    epubBytes);
            context.set(TELEGRAM_CHAPTER_ID, response.messageId());

            context.getRelease().addMetadata(TELEGRAM_CHAPTER_ID, response.messageId());
        } else {
            throw new IllegalStateException("Epub generation is failed");
        }
    }

    private String getRemoteBookId(Book book) {
        var list = bookRestClient.getAllBookTitlesCached();
        return list.stream()
                .filter(dto -> Objects.equals(dto.title(), book.getUniqueId()))
                .findFirst()
                .orElseThrow(() -> new AppException("Can't find remote book for given title: %s", book.getUniqueId()))
                .id();
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

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class NameUtil {

        private static final Pattern ARTICLES = Pattern.compile("\\b(a|an|the)\\b\\s+", Pattern.CASE_INSENSITIVE);
        private static final Pattern CAMEL_CASE_REGEX = Pattern.compile("(?<=[a-z])(?=[A-Z])");
        private static final Pattern NON_ALPHANUMERIC_REGEX = Pattern.compile("[^a-zA-Z0-9\\s]+");
        private static final Pattern WHITESPACE_REGEX = Pattern.compile("\\s+");

        public static String toSnakeCase(String input) {
            if (input == null || input.isEmpty()) {
                return input;
            }

            String withoutArticles = ARTICLES.matcher(input).replaceAll("");
            String withoutNonAlphanumeric = NON_ALPHANUMERIC_REGEX.matcher(withoutArticles).replaceAll("");
            String withSpacesBetweenCamelCase = CAMEL_CASE_REGEX.matcher(withoutNonAlphanumeric).replaceAll(" ");
            String lowerCaseWithSpaces = withSpacesBetweenCamelCase.toLowerCase(Locale.ENGLISH);
            return WHITESPACE_REGEX.matcher(lowerCaseWithSpaces).replaceAll("_");
        }
    }

}
