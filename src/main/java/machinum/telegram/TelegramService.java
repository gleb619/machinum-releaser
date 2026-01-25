package machinum.telegram;

import com.pengrad.telegrambot.model.request.ParseMode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import machinum.book.Book;
import machinum.exception.AppException;
import machinum.image.Image;
import machinum.telegram.TelegramAudio.FileMetadata;
import machinum.telegram.TelegramClient.AudioRecord;
import machinum.telegram.TelegramClient.Response;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import static machinum.telegram.TelegramClient.MPEG_CONTENT_TYPE;
import static machinum.telegram.TelegramClient.TELEGRAM_LIMIT;
import static machinum.telegram.TelegramMessageDSL.dsl;
import static machinum.telegram.TelegramMessageDSL.markdownLegacy;

@Slf4j
@RequiredArgsConstructor
public class TelegramService {

    public static final String EPUB_CONTENT_TYPE = "application/epub+zip";
    public static final int CONTENT_LIMIT = TELEGRAM_LIMIT - 600;

    private final TelegramProperties telegramProperties;
    private final TelegramClient client;

    @SneakyThrows
    public Response publishNewBook(String chatId, Book newBook, List<Image> images) {
        log.info("Prepare to start a telegram session: {}", LocalDateTime.now());

        Book truncatedBook = truncate(newBook);

        //@formatter:off
        var messageCaption = markdownLegacy()
                .customEmoji("\uD83C\uDF1F")
                .bold(" Анонс новой книги! ")
                .customEmoji("\uD83C\uDF1F")
                    .newLine()
                .text("Мы с радостью представляем новый роман в нашей библиотеке! " +
                        "Погрузитесь в эту невероятную историю и исследуйте ее захватывающий мир.")
                    .newLine()
                .customEmoji("\uD83D\uDCCC")
                .bold(" Подробности: ")
                    .newLine()
                .customEmoji("\uD83D\uDDCF")
                .bold(" Название: ")
                    .newLine(1)
                .list(dsl -> dsl.listOf(
                        dsl.bold("Русское: ").text(truncatedBook.getRuName()).dump(),
                        dsl.bold("Английское: ").text(truncatedBook.getEnName()).dump(),
                        dsl.bold("Оригинальное: ").text(truncatedBook.getOriginName()).dump()
                ))
                    .newLine(1)
                .customEmoji("\uD83D\uDD17")
                .bold(" Ссылка: ")
                .link(truncatedBook.getLinkText(), truncatedBook.getLink())
                    .newLine()
                .customEmoji("\uD83D\uDCDA")
                .bold(" Тип: ")
                .text(truncatedBook.getType())
                    .newLine()
                .optionalBlock(unused -> !truncatedBook.getGenre().isEmpty(), dsl -> dsl
                    .customEmoji("\uD83D\uDCD6")
                    .bold(" Жанры: ")
                    .tags(truncatedBook.getGenre())
                        .newLine()
                )
                .optionalBlock(unused -> !truncatedBook.getTags().isEmpty(), dsl -> dsl
                    .customEmoji("♯")
                    .bold(" Теги: ")
                    .tags(truncatedBook.getTags())
                        .newLine()
                )
                .customEmoji("\uD83D\uDCC6")
                .bold(" Год публикации: ")
                .text(truncatedBook.getYear())
                    .newLine()
                .customEmoji("\uD83D\uDCDD")
                .bold(" Количество глав: ")
                .text(truncatedBook.getChapters())
                    .newLine()
                .customEmoji("✍\uFE0F")
                .bold(" Автор: ")
                .text(truncatedBook.getAuthor())
                    .newLine()
                .optionalBlock(unused -> !truncatedBook.getDescription().isEmpty(), dsl -> dsl
                    .customEmoji("\uD83D\uDCDC")
                    .bold(" Синопсис: ")
                    .spoiler(truncatedBook.getDescription())
                        .newLine()
                )
                .customEmoji("\uD83D\uDCAC")
                .text(" Наслаждайтесь этим удивительным дополнением к нашей коллекции. " +
                        "И следите за переводами первых глав в ближайшее время! ")
                    .newLine()
                .text("Счастливого чтения! ")
                .customEmoji("\uD83C\uDF1F")
                .buildCaption();
        //@formatter:on

        //TODO: use messageCaption instead
        @Deprecated
        var message = messageCaption.getText();

        if(message.length() - TELEGRAM_LIMIT > 0) {
            log.warn("Telegram message is bigger than limit: {} <> {}", message.length(), TELEGRAM_LIMIT);
        }

        int totalBytes = images.stream()
                .mapToInt(image -> image.getData().length)
                .sum() + message.getBytes(StandardCharsets.UTF_8).length;

        //https://limits.tginfo.me/en, sendMediaGroupLimit is - up to 1,024 characters
        log.info("Created message: size={} raw, size={} kb, caption={} chars, message={}...", "%,d".formatted(totalBytes),
                (float) totalBytes / 1024, message.length(), message.replaceAll("\n", " ").substring(Math.max(message.length(), 50)));

        // Validate XML message before sending
        if(ParseMode.HTML == messageCaption.getParseMode()) {
            validateMessage(message);
        }

        try {
            Response response = client.sendImagesWithMessage(chatId, messageCaption, images);

            log.info("Published new book: messageId={}", response.messageId());
            return response;
        } catch (TelegramClient.HtmlParseException e) {
            log.error("Failed to publish new book due to HTML parsing error: {}", e.getDescription());
            String errorDetails = detectUnclosedTags(message.getBytes(StandardCharsets.UTF_8), e.getByteOffset());
            log.error("HTML parsing error details:\n{}", errorDetails);
            throw e; // Re-throw the HtmlParseException
        }
    }

    @SneakyThrows
    public Response publishNewChapter(String chatId, String name, Integer synopsisMessageId,
                                      String chapters, String status, String fileName, byte[] document) {
        log.info("Prepare to start a telegram session: {}", LocalDateTime.now());

        String message = dsl()
                .customEmoji("\uD83D\uDCD6")
                .bold(" Обновление!")
                .newLine()
                .customEmoji("\uD83D\uDE80")
                .text(" Мы рады сообщить, что новые главы уже доступны! " +
                        "Погрузитесь глубже в историю и насладитесь последними событиями.")
                .newLine()
                .customEmoji("\uD83D\uDCCC")
                .bold(" Подробности:").newLine()
                .list(dsl -> dsl.listOf(
                        dsl.bold("Канал: ").mention(telegramProperties.getChannelName()).dump(),
                        dsl.bold("Название: ").replyTo(telegramProperties.getChannelName(), name, synopsisMessageId).dump(),
                        dsl.bold("Номер главы: ").text(chapters).dump(),
                        dsl.bold("Статус: ").text(status).dump()
                ))
                .newLine(1)
                .customEmoji("\uD83D\uDCAC")
                .text(" Как всегда, ваши отзывы и мысли приветствуются. Дайте нам знать, что вы думаете о новой главе!")
                .newLine()
                .text("Следите за обновлениями и приятного чтения! ")
                .customEmoji("\uD83C\uDF1F")
                .build();

        log.info("Created message: chatId={}, message={}", chatId, message);

        return client.sendFileWithMessage(chatId, message, EPUB_CONTENT_TYPE, fileName, document);
    }

    @SneakyThrows
    public Response publishNewAudio(String chatId, String name, Integer synopsisMessageId,
                                    String chapters, String status, List<FileMetadata> audioFiles,
                                    byte[] thumbnail) {
        log.info("Prepare to start a telegram session: {}", LocalDateTime.now());

        String message = dsl()
                .customEmoji("\uD83C\uDFA7")
                .bold(" Обновление!")
                .newLine()
                .customEmoji("\uD83D\uDE80")
                .text(" Мы рады сообщить, что вышла ").bold("новая аудиоверсия")
                .text(" глав — теперь вы можете не только читать, но и ").bold("слушать")
                .text(" любимую историю! Погрузитесь в атмосферу романа в любом месте и в любое время.")
                .newLine()
                .customEmoji("\uD83D\uDCCC")
                .bold(" Подробности:").newLine()
                .list(dsl -> dsl.listOf(
                        dsl.bold("Канал: ").mention(telegramProperties.getChannelName()).dump(),
                        dsl.bold("Название: ").replyTo(telegramProperties.getChannelName(), name, synopsisMessageId).dump(),
                        dsl.bold("Главы: ").text(chapters).dump(),
                        dsl.bold("Формат: ").text("Аудио").dump(),
                        dsl.bold("Статус: ").text(status).dump()
                ))
                .newLine(1)
                .customEmoji("\ud83d\udcac")
                .text(" Будем рады вашим отзывам — расскажите, как звучит история в новом формате!")
                .newLine()
                .text("Оставайтесь с нами и приятного прослушивания! ")
                .customEmoji("\ud83c\udf99️")
                .customEmoji("\ud83d\udcda")
                .build();

        log.info("Created message: chatId={}, message={}", chatId, message);

        var chaps = chapters.split("-");
        var counter = new AtomicInteger(Integer.parseInt(chaps[0]));

        var audioRecords = audioFiles.stream()
                .map(metadata -> new AudioRecord(metadata.getFilename(), "Chapter: %s".formatted(counter.getAndIncrement()), metadata.getMp3Data(), (int) metadata.getDurationSeconds()))
                .collect(Collectors.toList());

        return client.sendAudioFilesWithMessage(chatId, message, MPEG_CONTENT_TYPE,
                telegramProperties.getChannelName(), audioRecords, thumbnail);
    }

    @SneakyThrows
    public Response publishFile(@NonNull String chatId, String text, String contentType, String fileName, byte[] file) {
        log.info("Prepare to send a file to telegram: {}", LocalDateTime.now());

        return client.sendFileWithMessage(chatId, text, contentType, fileName, file);
    }

    @SneakyThrows
    public Integer publishFiles(@NonNull String chatId, String text, String contentType, File... files) {
        log.info("Prepare to send a files to telegram: {}", LocalDateTime.now());

        return client.sendFilesWithMessage(chatId, text, contentType, files);
    }

    @SneakyThrows
    public Integer reply(@NonNull String chatId, Integer messageId, String text) {
        log.info("Prepare to reply to a message in telegram: {}", LocalDateTime.now());

        return client.replyToMessage(chatId, messageId, text);
    }

    @SneakyThrows
    public Integer sendMessage(@NonNull String chatId, String text) {
        log.info("Prepare to send a message to telegram: {}", LocalDateTime.now());

        return client.sendMessage(chatId, text);
    }

    /* ============= */

    public Book truncate(Book book) {
        Book copy = book.toBuilder().build();

        // Operation 1: Truncate description
        if (getContentLength(copy) > CONTENT_LIMIT && copy.getDescription() != null && !copy.getDescription().isEmpty()) {
            int availableSpace = CONTENT_LIMIT - (getContentLength(copy) - copy.getDescription().length());
            String thumbnail = "...";
            if (availableSpace > thumbnail.length()) {
                int minIndex = Math.min(copy.getDescription().length(), availableSpace - thumbnail.length());
                copy.setDescription(copy.getDescription().substring(0, minIndex) + thumbnail);
            } else {
                copy.setDescription("");
            }
        }

        // Operation 2: Remove tags from end
        if (getContentLength(copy) > CONTENT_LIMIT && copy.getTags() != null && !copy.getTags().isEmpty()) {
            while (getContentLength(copy) > CONTENT_LIMIT && !copy.getTags().isEmpty()) {
                copy.getTags().removeLast();
            }
            if (getContentLength(copy) > CONTENT_LIMIT) {
                copy.setTags(new ArrayList<>());
            }
        }

        // Operation 3: Remove genres from end
        if (getContentLength(copy) > CONTENT_LIMIT && copy.getGenre() != null && !copy.getGenre().isEmpty()) {
            while (getContentLength(copy) > CONTENT_LIMIT && !copy.getGenre().isEmpty()) {
                copy.getGenre().removeLast();
            }
            if (getContentLength(copy) > CONTENT_LIMIT) {
                copy.setGenre(new ArrayList<>());
            }
        }

        // Final check
        int contentLength = getContentLength(copy);
        if (contentLength > CONTENT_LIMIT) {
            throw new AppException("Cannot truncate object to fit Telegram limit of %s characters", CONTENT_LIMIT);
        } else {
            log.debug("Book info took only: {} chars", contentLength);
        }

        return copy;
    }

    private int getContentLength(Book book) {
        int length = 0;

        if (book.getRuName() != null) length += book.getRuName().length();
        if (book.getEnName() != null) length += book.getEnName().length();
        if (book.getOriginName() != null) length += book.getOriginName().length();
        if (book.getLink() != null) length += book.getLink().length();
        if (book.getLinkText() != null) length += book.getLinkText().length();
        if (book.getType() != null) length += book.getType().length();
        if (book.getAuthor() != null) length += book.getAuthor().length();
        if (book.getDescription() != null) length += book.getDescription().length();

        if (book.getGenre() != null) {
            for (String g : book.getGenre()) {
                if (g != null) length += g.length();
            }
        }

        if (book.getTags() != null) {
            for (String tag : book.getTags()) {
                if (tag != null) length += tag.length();
            }
        }

        if (book.getYear() != null) length += book.getYear().toString().length();
        if (book.getChapters() != null) length += book.getChapters().toString().length();

        return length;
    }

    private String detectUnclosedTags(byte[] htmlBytes, int byteOffset) {
        String html = new String(htmlBytes, StandardCharsets.UTF_8);
        int errorCharPos = byteOffsetToCharPosition(html, byteOffset);

        StringBuilder result = new StringBuilder();
        List<String> stack = new ArrayList<>();
        List<Integer> positions = new ArrayList<>();
        int pos = 0;

        while (pos < html.length()) {
            if (html.charAt(pos) == '<') {
                int end = html.indexOf('>', pos);
                if (end == -1) break;
                String tag = html.substring(pos + 1, end).trim();
                if (tag.startsWith("/")) {
                    // Closing tag
                    String tagName = tag.substring(1).split("\\s+")[0];
                    if (stack.isEmpty() || !stack.get(stack.size() - 1).equals(tagName)) {
                        int bytePos = charPositionToByteOffset(html, pos);
                        result.append("Unclosed end tag </").append(tagName).append("> at byte position ").append(bytePos).append(", char position ").append(pos).append("\n");
                        highlightError(html, pos, result);
                    } else {
                        stack.remove(stack.size() - 1);
                        positions.remove(positions.size() - 1);
                    }
                } else if (!tag.endsWith("/") && !tag.isEmpty()) {
                    // Opening tag, not self-closing
                    String tagName = tag.split("\\s+")[0];
                    stack.add(tagName);
                    positions.add(pos);
                }
                pos = end + 1;
            } else {
                pos++;
            }
        }

        // Remaining unclosed opening tags
        for (int i = 0; i < stack.size(); i++) {
            String tag = stack.get(i);
            int position = positions.get(i);
            int bytePos = charPositionToByteOffset(html, position);
            result.append("Unclosed opening tag <").append(tag).append("> at byte position ").append(bytePos).append(", char position ").append(position).append("\n");
            highlightError(html, position, result);
        }

        // Highlight the error location from byteOffset
        if (errorCharPos < html.length()) {
            result.append("Error location at byte offset ").append(byteOffset).append(" (char ").append(errorCharPos).append("):\n");
            highlightError(html, errorCharPos, result);
        }

        return result.toString().replaceAll("\n", " ");
    }

    private int byteOffsetToCharPosition(String html, int byteOffset) {
        if (byteOffset <= 0) return 0;
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        if (byteOffset >= bytes.length) return html.length();
        int charIndex = 0;
        int byteIndex = 0;
        while (byteIndex < byteOffset && charIndex < html.length()) {
            int charBytes = String.valueOf(html.charAt(charIndex)).getBytes(StandardCharsets.UTF_8).length;
            if (byteIndex + charBytes > byteOffset) {
                break;
            }
            byteIndex += charBytes;
            charIndex++;
        }
        return charIndex;
    }

    private int charPositionToByteOffset(String html, int charPos) {
        if (charPos <= 0) return 0;
        return html.substring(0, charPos).getBytes(StandardCharsets.UTF_8).length;
    }

    private void highlightError(String html, int errorPos, StringBuilder result) {
        int start = Math.max(0, errorPos - 30);
        int end = Math.min(html.length(), errorPos + 30);
        String substr = html.substring(start, end);
        int relativePos = errorPos - start;
        if (relativePos >= 0 && relativePos < substr.length()) {
            String before = substr.substring(0, relativePos);
            String badChar = String.valueOf(substr.charAt(relativePos));
            String after = substr.substring(relativePos + 1);
            result.append(before).append("|").append(badChar).append("|").append(after).append("\n");
        } else {
            result.append(substr).append("\n");
        }
    }

    @SneakyThrows
    void validateMessage(String html) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Disable external entity resolution for security and to prevent URL fetching
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            String wrappedHtml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<root>\n%s\n</root>".formatted(html);
            builder.parse(new java.io.ByteArrayInputStream(wrappedHtml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new AppException("HTML validation failed for message: %s".formatted(e.getMessage()), e);
        }
    }

}
