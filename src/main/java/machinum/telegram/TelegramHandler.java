package machinum.telegram;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import machinum.audio.CoverArt;
import machinum.audio.TTSRestClient.Metadata;
import machinum.book.Book;
import machinum.book.BookRestClient;
import machinum.exception.AppException;
import machinum.image.ImageRepository;
import machinum.image.cover.CoverService;
import machinum.image.cover.CoverService.CoverInfo;
import machinum.markdown.MarkdownConverter;
import machinum.pandoc.PandocRestClient;
import machinum.pandoc.PandocRestClient.PandocRequest;
import machinum.release.ReleaseRepository;
import machinum.scheduler.ActionHandler;
import machinum.telegram.TelegramAudio.FileMetadata;
import machinum.telegram.TelegramProperties.ChatType;
import machinum.audio.TextXmlReader.TextInfo;
import machinum.util.Pair;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

import static machinum.release.Release.ReleaseConstants.PAGES_PARAM;
import static machinum.telegram.TelegramHandler.TelegramConstants.TELEGRAM_BOOK_ID;
import static machinum.telegram.TelegramHandler.TelegramConstants.TELEGRAM_CHAPTER_ID;
import static machinum.telegram.TelegramProperties.ChatType.TEST;
import static machinum.telegram.TelegramProperties.ChatType.of;
import static machinum.util.ZipUtil.readZipFile;

/**
 * Instance is used for interacting with the telegram API for scheduled releases.
 */
@Slf4j
@RequiredArgsConstructor
public class TelegramHandler implements ActionHandler {

    public static final String TELEGRAM_CHAT_TYPE = "chatType";

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final TelegramService telegramService;
    private final TelegramProperties telegramProperties;
    private final ReleaseRepository repository;
    private final ImageRepository imageRepository;
    private final BookRestClient bookRestClient;
    private final MarkdownConverter markdownConverter;
    private final PandocRestClient pandocRestClient;
    private final CoverService coverService;
    private final TelegramAudio telegramAudio;
    private final TextInfo textInfo;
    private final CoverArt coverArt;

    /**
     * Handles the action context based on whether it's the first or subsequent release.
     *
     * @param context The ActionContext to handle.
     * @return
     */
    @Override
    public HandlerResult handle(ActionContext context) {
        if(ActionType.TELEGRAM.equals(context.getActionType())) {
            if (context.isFirstRelease()) {
                releaseBook(context);
                releaseChapters(context);
            } else {
                releaseChapters(context);
            }
        } else if(ActionType.TELEGRAM_AUDIO.equals(context.getActionType())) {
            releaseAudio(context);
        } else {
            throw new AppException("Unknown actionType: %s", context.getActionType());
        }

        return HandlerResult.executed();
    }

    /* ============= */

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
        var imageId = context.getBook().getImageId();
        var originImageId = context.getBook().getOriginImageId();
        var chatType = of(context.getReleaseTarget().getMetadata().getOrDefault(TELEGRAM_CHAT_TYPE, "test").toString());
        var chatId = telegramProperties.getChatId(chatType);
        var images = imageRepository.findByIds(List.of(imageId, originImageId));
        var response = telegramService.publishNewBook(chatId, context.getBook(), images);
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
        var tgContext = resolveTgContext(context);

        log.debug("Fetching ready chapters for: bookID={}, mode={}", tgContext.getRemoteBookId(), tgContext.getChatType());

        var chapterList = bookRestClient.getReadyChaptersCached(tgContext.getRemoteBookId(), tgContext.getChaptersRequest().first(), tgContext.getChaptersRequest().second());
        var markdowns = chapterList.stream()
                .map(markdownConverter::toMarkdown)
                .map(s -> s.getBytes(StandardCharsets.UTF_8))
                .toList();
        var image = imageRepository.getById(tgContext.getBook().getImageId());
        var partIndex = context.getReleasePosition() + 1;

        var number = partIndex + "";

        log.debug("Generating cover image for book: {}, mode={}", tgContext.getBook().getRuName(), tgContext.getChatType());

        var coverInfo = new CoverInfo(number, tgContext.getBook().getRuName(), "M T.\nNOVELS", telegramProperties.getChannelLink(), "@mt_novel", "Subscribe");
        var coverImage = coverService.generateBookCover(image, coverInfo);
        var fileName = NameUtil.toFileSnakeCase(tgContext.getBook().getEnName()) + "_%s.epub".formatted(partIndex);
        var chatId = telegramProperties.getChatId(tgContext.getChatType());

        log.debug("Converting to EPUB for book: {}, mode={}", tgContext.getBook().getRuName(), tgContext.getChatType());
        var epubBytes = pandocRestClient.convertToEpubCached(PandocRequest.createNew(b -> b
                .startIndex(tgContext.getChaptersRequest().first())
                .markdownFiles(markdowns)
                .coverImage(coverImage.getData())
                .coverContentType(image.getContentType())
                .title(tgContext.getBook().getRuName())
                .subtitle("Часть %s".formatted(partIndex))
                .author(tgContext.getBook().getAuthor())
                .publisher(chatId)
                .publisherInfo(textInfo.getEpub().getPublisherInfo())
                //.edition("")
                .rights(textInfo.getEpub().getRights())
                //.legalRights("")
                //.disclaimer("")
                .description(tgContext.getBook().getDescription())
                .keywords(String.join(", ", tgContext.getBook().getGenre()))
                .date(startOfYear(tgContext.getBook().getYear()))
                .pubdate(formatDate(context.getReleaseTarget().getCreatedAt().toLocalDate()))
                //.website("")
                .socialLinks(List.of(
                    telegramProperties.getChannelLink()
                ))
                .outputFilename(fileName)
        ));

        if (Objects.nonNull(epubBytes) && epubBytes.length > 0) {
            log.info("Publishing new chapter for book: {}, mode={}", tgContext.getBook().getRuName(), tgContext.getChatType());
            var response = telegramService.publishNewChapter(chatId, tgContext.getBook().getRuName(),
                    tgContext.getTgBookId(),
                    tgContext.getChapters(),
                    tgContext.getStatus(),
                    fileName,
                    epubBytes);
            context.set(TELEGRAM_CHAPTER_ID, response.messageId());

            context.getRelease().addMetadata(TELEGRAM_CHAPTER_ID, response.messageId());
        } else {
            log.error("Epub generation failed for book: {}, mode={}", tgContext.getBook().getRuName(), tgContext.getChatType());
            throw new AppException("Epub generation is failed");
        }
    }

    private void releaseAudio(ActionContext context) {
        var tgContext = resolveTgContext(context);
        var partIndex = context.getReleasePosition() + 1;
        var image = imageRepository.getById(tgContext.getBook().getImageId());
        var number = partIndex + "";

        log.debug("Generating cover image for book: {}, mode={}", tgContext.getBook().getRuName(), tgContext.getChatType());

        var coverInfo = new CoverInfo(number, tgContext.getBook().getRuName(), "M T.\nNOVELS",
                telegramProperties.getChannelLink(),
                "@mt_novel",
                "Subscribe");
        var coverImage = coverService.generateBookCover(image, coverInfo);

        log.debug("Fetching ready chapters for: bookID={}, mode={}", tgContext.getRemoteBookId(), tgContext.getChatType());

        var from = tgContext.getChaptersRequest().first();
        var fileName = NameUtil.toFileSnakeCase(tgContext.getBook().getEnName()) + "_%s.mp3".formatted(partIndex);
        var to = tgContext.getChaptersRequest().second();
        var zipBytes = bookRestClient.getAudioCached(tgContext.getRemoteBookId(), from, to, coverImage.getData());
        var audioFiles = processZipFile(zipBytes);

        var chatId = telegramProperties.getChatId(tgContext.getChatType());
        var metadata = Metadata.createNew(b -> b
                .title(tgContext.getBook().getRuName())
                //TODO add voice artist name here
                .artist(String.join(",", telegramProperties.getChannelName()))
                .album(tgContext.getBook().getEnName())
                .year(String.valueOf(LocalDate.now().getYear()))
                .genre("Аудиокнига")
                .language("rus")
                .track(number)
                .publisher(chatId)
                .copyright(textInfo.getEpub().getRights())
                .comments(textInfo.getTts().getDisclaimer())
        );
        var firstAudioFile = telegramAudio.putTogether(fileName, metadata, Boolean.TRUE, audioFiles.first(), coverImage.getData());
        var restAudioFiles = telegramAudio.enhance(fileName, metadata, audioFiles.rest(), coverImage.getData());
        var filesToRelease = new ArrayList<FileMetadata>();
        filesToRelease.add(firstAudioFile);
        filesToRelease.addAll(restAudioFiles);


        if (Objects.nonNull(firstAudioFile)) {
            log.info("Publishing new chapter for book: {}, mode={}", tgContext.getBook().getRuName(), tgContext.getChatType());

        var response = telegramService.publishNewAudio(chatId, tgContext.getBook().getRuName(),
                    tgContext.getTgBookId(),
                    tgContext.getChapters(),
                    tgContext.getStatus(),
                    filesToRelease,
                    coverArt.content());
            context.set(TELEGRAM_CHAPTER_ID, response.messageId());

            context.getRelease().addMetadata(TELEGRAM_CHAPTER_ID, response.messageId());
        } else {
            log.error("Mp3 generation failed for book: {}, mode={}", tgContext.getBook().getRuName(), tgContext.getChatType());
            throw new AppException("Mp3 generation is failed");
        }
    }

    private TGContext resolveTgContext(ActionContext context) {
        var book = context.getBook();
        var chatType = of(context.getReleaseTarget().getMetadata()
                .getOrDefault(TELEGRAM_CHAT_TYPE, "none").toString());
        log.info("Starting chapter release for book: {}, mode={}", book.getRuName(), chatType);

        var status = context.isLastRelease() ? "Завершена ветка перевода" : "Перевод продолжается";
        var release = context.getRelease();
        //TODO rewrite to more complex way to resolve parent's message id(due, only text version have book's publication)
        var tgBookId = (Integer) context.getOr(TELEGRAM_BOOK_ID, () ->
                repository.findSingleMetadata(release.getReleaseTargetId(), TELEGRAM_BOOK_ID));

        var chapters = (String) release.metadata(PAGES_PARAM);
        var chaptersRequest = release.toPageRequest();
        var remoteBookId = context.getRemoteBookId();

        return TGContext.builder()
                .book(book)
                .chatType(chatType)
                .status(status)
                .tgBookId(tgBookId)
                .chapters(chapters)
                .chaptersRequest(chaptersRequest)
                .remoteBookId(remoteBookId)
                .build();
    }

    public AudioFiles processZipFile(byte[] zipData) {
        Map<String, byte[]> files = readZipFile(zipData);
        List<Map.Entry<String, byte[]>> sortedEntries = new ArrayList<>(files.entrySet());
        sortedEntries.sort(Map.Entry.comparingByKey());

        byte[] first = null;
        List<byte[]> rest = new ArrayList<>();
        for (int i = 0; i < sortedEntries.size(); i++) {
            if (i == 0) {
                first = sortedEntries.get(i).getValue();
            } else {
                rest.add(sortedEntries.get(i).getValue());
            }
        }

        return new AudioFiles(first, rest);
    }

    public record AudioFiles(byte[] first, List<byte[]> rest) {}

    /* ============= */

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    private static class TGContext {
        
        private Book book;
        private ChatType chatType;
        private String status;
        private Integer tgBookId;
        private String chapters;
        private Pair<Integer, Integer> chaptersRequest;
        private String remoteBookId;

    }

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

        public static String toFileSnakeCase(String input) {
            if (input == null || input.isEmpty()) {
                return input;
            }

            String withoutArticles = ARTICLES.matcher(input).replaceAll("");
            String withoutNonAlphanumeric = NON_ALPHANUMERIC_REGEX.matcher(withoutArticles).replaceAll("");
            String withSpacesBetweenCamelCase = CAMEL_CASE_REGEX.matcher(withoutNonAlphanumeric).replaceAll(" ");
            String lowerCaseWithSpaces = withSpacesBetweenCamelCase.toLowerCase(Locale.ENGLISH);
            return WHITESPACE_REGEX.matcher(lowerCaseWithSpaces).replaceAll("_");
        }

        public static String toSnakeCase(String input) {
            if (input == null || input.isBlank()) {
                return "";
            }

            var result = new StringBuilder();
            boolean previousWasUnderscore = false;

            for (char c : input.trim().toCharArray()) {
                if (Character.isWhitespace(c) || !Character.isLetterOrDigit(c)) {
                    if (!previousWasUnderscore) {
                        result.append('_');
                        previousWasUnderscore = true;
                    }
                } else if (Character.isUpperCase(c)) {
                    if (!previousWasUnderscore) {
                        result.append('_');
                    }
                    result.append(Character.toLowerCase(c));
                    previousWasUnderscore = false;
                } else {
                    result.append(c);
                    previousWasUnderscore = false;
                }
            }

            // Remove leading underscore if present
            var snakeCase = result.toString();
            return snakeCase.startsWith("_") ? snakeCase.substring(1) : snakeCase;
        }

    }

}
