package machinum.telegram;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import machinum.book.Book;

import java.io.File;
import java.time.LocalDateTime;

import static machinum.telegram.TelegramMessageDSL.dsl;

@Slf4j
@RequiredArgsConstructor
public class TelegramService {

    public static final String EPUB_CONTENT_TYPE = "application/epub+zip";

    private final TelegramClient client;

    @SneakyThrows
    public Integer publishNewBook(Book newBook, File... images) {
        log.info("Prepare to start a telegram session: {}", LocalDateTime.now());

        String message = dsl()
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
                        dsl.bold("Русское: ").text(newBook.getRuName()).dump(),
                        dsl.bold("Английское: ").text(newBook.getEnName()).dump(),
                        dsl.bold("Оригинальное: ").text(newBook.getOriginName()).dump()
                ))
                .newLine(1)
                .customEmoji("\uD83D\uDD17")
                .bold(" Ссылка: ")
                .link(newBook.getLinkText(), newBook.getLink())
                .newLine()
                .customEmoji("\uD83D\uDCDA")
                .bold(" Тип: ")
                .text(newBook.getType())
                .newLine()
                .customEmoji("\uD83D\uDCD6")
                .bold(" Жанры: ")
                .tags(newBook.getGenre())
                .newLine()
                .customEmoji("#")
                .bold(" Теги: ")
                .tags(newBook.getTags())
                .newLine()
                .customEmoji("\uD83D\uDCC6")
                .bold(" Год публикации: ")
                .text(newBook.getYear())
                .newLine()
                .customEmoji("\uD83D\uDCDD")
                .bold(" Количество глав: ")
                .text(newBook.getChapters())
                .newLine()
                .customEmoji("✍\uFE0F")
                .bold(" Автор: ")
                .text(newBook.getAuthor())
//                .newLine()
//                    .customEmoji("\uD83D\uDCDC")
//                    .bold(" Синопсис: ")
//                    .spoiler(newBook.description())
                .newLine()
                .customEmoji("\uD83D\uDCAC")
                .text(" Наслаждайтесь этим удивительным дополнением к нашей коллекции. " +
                        "И следите за переводами первых глав в ближайшее время! ")
                .newLine()
                .text("Счастливого чтения! ")
                .customEmoji("\uD83C\uDF1F")
                .build();

        log.info("Created message: message={}", message);

        Integer messageId = client.sendImagesWithMessage(message, images);

        log.info("Published new book: messageId={}", messageId);

        return messageId;
    }

    @SneakyThrows
    public void publishNewChapter(String chatId, String name, Integer synopsisMessageId, String channel, String chapters, String status, File document) {
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
                        dsl.bold("Канал: ").mention(channel).dump(),
                        dsl.bold("Название: ").replyTo(channel, name, synopsisMessageId).dump(),
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

        log.info("Created message: message={}", message);

        client.sendFileWithMessage(chatId, message, EPUB_CONTENT_TYPE, document);
    }

    @SneakyThrows
    public void publishNewChapter(String name, Integer synopsisMessageId, String channel, String chapters, String status, File document) {
        publishNewChapter(client.getDefaultChatId(), name, synopsisMessageId, channel, chapters, status, document);
    }

    @SneakyThrows
    public Integer publishFile(String text, String contentType, File file) {
        log.info("Prepare to send a file to telegram: {}", LocalDateTime.now());

        return client.sendFileWithMessage(text, contentType, file);
    }

    @SneakyThrows
    public Integer publishFile(@NonNull String chatId, String text, String contentType, File file) {
        log.info("Prepare to send a file to telegram: {}", LocalDateTime.now());

        return client.sendFileWithMessage(chatId, text, contentType, file);
    }

    @SneakyThrows
    public Integer publishFiles(String text, String contentType, File... files) {
        log.info("Prepare to send a files to telegram: {}", LocalDateTime.now());

        return client.sendFilesWithMessage(text, contentType, files);
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
    public Integer reply(Integer messageId, String text) {
        log.info("Prepare to reply to a message in telegram: {}", LocalDateTime.now());

        return client.replyToMessage(messageId, text);
    }

    @SneakyThrows
    public Integer sendMessage(@NonNull String chatId, String text) {
        log.info("Prepare to send a message to telegram: {}", LocalDateTime.now());

        return client.sendMessage(chatId, text);
    }

}
