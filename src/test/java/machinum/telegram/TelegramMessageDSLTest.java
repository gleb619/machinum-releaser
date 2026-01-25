package machinum.telegram;

import com.pengrad.telegrambot.model.request.ParseMode;
import net.fellbaum.jemoji.Emojis;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramMessageDSLTest {

    @Test
    void testMain() {
        TelegramMessageDSL message = new TelegramMessageDSL(1)
                .text("Hello, ")
                .bold("world!")
                .text(" Here's a ")
                .italic("cool")
                .text(" message.")
                .link("Click here", "https://www.example.com")
                .mention("username")
                .pre("System.out.println(\"Hello, world!\");", "java")
                .emoji(Emojis.THUMBS_UP);

        assertThat(message.build())
                .isNotEmpty();
    }

    @Test
    void testHtmlMode() {
        TelegramMessageDSL message = TelegramMessageDSL.html()
                .text("Hello, ")
                .bold("world!")
                .italic("test")
                .link("Click", "http://example.com")
                .code("code")
                .pre("pre text", "java");

        String result = message.build();
        assertThat(result)
                .contains("<b>world!</b>")
                .contains("<i>test</i>")
                .contains("<a href=\"http://example.com\">Click</a>")
                .contains("<code>code</code>")
                .contains("<pre><code class=\"language-java\">pre text</code></pre>");
        assertThat(message.getParseMode()).isEqualTo(ParseMode.HTML);
    }

    @Test
    void testMarkdownLegacyMode() {
        TelegramMessageDSL message = TelegramMessageDSL.markdownLegacy()
                .text("Hello, ")
                .bold("world!")
                .italic("test")
                .link("Click", "http://example.com")
                .code("code")
                .pre("pre text", "java");

        String result = message.build();
        assertThat(result)
                .contains("*world!* ")
                .contains("_test_ ")
                .contains("[Click](http://example.com) ")
                .contains("`code` ")
                .contains("```java\npre text\n``` ");
        assertThat(message.getParseMode()).isEqualTo(ParseMode.MarkdownV2);
    }

    @Test
    void testDslWithParseMode() {
        TelegramMessageDSL htmlMessage = TelegramMessageDSL.dsl(ParseMode.HTML);
        assertThat(htmlMessage.getParseMode()).isEqualTo(ParseMode.HTML);

        TelegramMessageDSL markdownMessage = TelegramMessageDSL.dsl(ParseMode.MarkdownV2);
        assertThat(markdownMessage.getParseMode()).isEqualTo(ParseMode.MarkdownV2);
    }

    @Test
    void testPublishNewBookStyleMessage() {
        // Simulate the message construction from TelegramService.publishNewBook
        TelegramMessageDSL message = TelegramMessageDSL.markdownLegacy()
                .customEmoji("\uD83C\uDF1F")
                .bold(" Анонс новой книги! ")
                .customEmoji("\uD83C\uDF1F")
                .newLine()
                .text("Мы с радостью представляем новый роман в нашей библиотеке! Погрузитесь в эту невероятную историю и исследуйте ее захватывающий мир.")
                .newLine()
                .customEmoji("\uD83D\uDCCC")
                .bold(" Подробности: ")
                .newLine()
                .customEmoji("\uD83D\uDDCF")
                .bold(" Название: ")
                .newLine(1)
                .list(dsl -> dsl.listOf(
                        dsl.bold("Русское: ").text("Наполеон в 1812").dump(),
                        dsl.bold("Английское: ").text("Napoleon in 1812").dump(),
                        dsl.bold("Оригинальное: ").text("나폴레옹 in 1812").dump()
                ))
                .newLine(1)
                .customEmoji("\uD83D\uDD17")
                .bold(" Ссылка: ")
                .link("Novel Updates", "https://www.novelupdates.com/series/napoleon-in-1812/")
                .newLine()
                .customEmoji("\uD83D\uDCDA")
                .bold(" Тип: ")
                .text("Web novel")
                .newLine()
                .customEmoji("\uD83D\uDCD6")
                .bold(" Жанры: ")
                .tags(List.of("#боевик", "#драма"))
                .newLine()
                .customEmoji("♯")
                .bold(" Теги: ")
                .tags(List.of("#аристократия", "#армия"))
                .newLine()
                .customEmoji("\uD83D\uDCC6")
                .bold(" Год публикации: ")
                .text("2020")
                .newLine()
                .customEmoji("\uD83D\uDCDD")
                .bold(" Количество глав: ")
                .text("152")
                .newLine()
                .customEmoji("✍\uFE0F")
                .bold(" Автор: ")
                .text("Aisiluseu")
                .newLine()
                .customEmoji("\uD83D\uDCDC")
                .bold(" Синопсис: ")
                .spoiler("Наполеон Бонапарт, который победил Австрию, Пруссию, Россию и Великобританию и стал истинным правителем Европы.")
                .newLine()
                .customEmoji("\uD83D\uDCAC")
                .text(" Наслаждайтесь этим удивительным дополнением к нашей коллекции. И следите за переводами первых глав в ближайшее время! ")
                .newLine()
                .text("Счастливого чтения! ")
                .customEmoji("\uD83C\uDF1F");

        String result = message.build();

        // Check that bold text doesn't have spaces inside
        assertThat(result)
                .contains("*Анонс новой книги!* ")
                .contains("*Подробности:* ")
                .contains("*Название:* ")
                .contains("*Русское:* ")
                .contains("*Английское:* ")
                .contains("*Оригинальное:* ")
                .contains("*Ссылка:* ")
                .contains("*Тип:* ")
                .contains("*Жанры:* ")
                .contains("*Теги:* ")
                .contains("*Год публикации:* ")
                .contains("*Количество глав:* ")
                .contains("*Автор:* ")
                .contains("*Синопсис:* ")
                .doesNotContain("* Анонс новой книги! *")
                .doesNotContain("* Подробности: *")
                .doesNotContain("* Название: *");

        // Ensure emojis and other elements have trailing spaces where appropriate
        assertThat(result).contains("🌟 ");
    }

}
