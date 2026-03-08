package machinum.telegram;

import static machinum.telegram.TelegramHandler.NameUtil.toSnakeCase;

import com.pengrad.telegrambot.model.request.ParseMode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import machinum.exception.AppException;
import machinum.telegram.TelegramClient.Caption;
import net.fellbaum.jemoji.Emoji;
import net.fellbaum.jemoji.EmojiManager;

public class TelegramMessageDSL {

    private final StringBuilder messageBuilder = new StringBuilder();
    private final TelegramMessageFormatter formatter;
    private int indent;

    public TelegramMessageDSL(int indent) {
        this(new HtmlFormatter(), indent);
    }

    public TelegramMessageDSL(TelegramMessageFormatter formatter, int indent) {
        this.formatter = formatter;
        this.indent = indent;
    }

    public static TelegramMessageDSL dsl() {
        return new TelegramMessageDSL(2);
    }

    public static TelegramMessageDSL dsl(ParseMode mode) {
        return switch (mode) {
            case HTML -> new TelegramMessageDSL(new HtmlFormatter(), 2);
            case Markdown -> new TelegramMessageDSL(new MarkdownLegacyFormatter(), 2);
            case MarkdownV2 -> throw new UnsupportedOperationException("MarkdownV2 not supported yet");
        };
    }

    public static TelegramMessageDSL html() {
        return dsl(ParseMode.HTML);
    }

    public static TelegramMessageDSL markdownLegacy() {
        return dsl(ParseMode.Markdown);
    }

    public static TelegramMessageDSL markdown() {
        return dsl(ParseMode.MarkdownV2);
    }


    public TelegramMessageDSL text(Integer text) {
        return text(String.valueOf(text));
    }

    public TelegramMessageDSL text(String text) {
        messageBuilder.append(text);
        return this;
    }

    public TelegramMessageDSL bold(String text) {
        messageBuilder.append(formatter.bold(text));
        return this;
    }

    public TelegramMessageDSL italic(String text) {
        messageBuilder.append(formatter.italic(text));
        return this;
    }

    public TelegramMessageDSL underline(String text) {
        messageBuilder.append(formatter.underline(text));
        return this;
    }

    public TelegramMessageDSL strikethrough(String text) {
        messageBuilder.append(formatter.strikethrough(text));
        return this;
    }

    public TelegramMessageDSL spoiler(String text) {
        messageBuilder.append(formatter.spoiler(text));
        return this;
    }

    public TelegramMessageDSL code(String text) {
        messageBuilder.append(formatter.code(text));
        return this;
    }

    public TelegramMessageDSL pre(String text, String language) {
        messageBuilder.append(formatter.pre(text, language));
        return this;
    }

    public TelegramMessageDSL link(String text, String url) {
        messageBuilder.append(formatter.link(text, url));
        return this;
    }

    public TelegramMessageDSL replyTo(String channel, String text, Integer messageId) {
        return link(text, "https://t.me/" + channel + "/" + messageId);
    }

    public TelegramMessageDSL mention(String username) {
        return link("@" + username, "https://t.me/" + username);
    }

    public TelegramMessageDSL emoji(Emoji emojiId) {
        messageBuilder.append(formatter.emoji(emojiId));
        return this;
    }

    public TelegramMessageDSL customEmoji(String emojiId) {
        messageBuilder.append(formatter.customEmoji(emojiId));
        return this;
    }

    public Caption buildCaption() {
        return Caption.builder()
                .text(build())
                .parseMode(getParseMode())
                .build();
    }

    public String build() {
        String result = messageBuilder.toString();

        if (result.length() > 4096) {
            throw new AppException("Message is too big: min=4096, actual=%s".formatted(result.length()));
        }

        return result;
    }

    public ParseMode getParseMode() {
        return formatter.getParseMode();
    }

    public TelegramMessageDSL newLine() {
        return newLine(2);
    }

    public TelegramMessageDSL newLine(int size) {
        messageBuilder.append("\n".repeat(size));

        return this;
    }

    public TelegramMessageDSL tags(List<String> values) {
        String text = values.stream()
                .map(s -> "#" + toSnakeCase(s))
                .collect(Collectors.joining(", "));

        messageBuilder.append(text);

        return this;
    }

    public TelegramMessageDSL list(List<String> lines) {
        for (String line : lines) {
            messageBuilder.append(" ".repeat(indent) + "• ").append(line)
                    .append("\n");
        }

        return this;
    }

    public TelegramMessageDSL list(Function<TelegramMessageDSL, List<String>> constructor) {
        return list(constructor.apply(new TelegramMessageDSL(formatter, indent + 2)));
    }

    public List<String> listOf(TelegramMessageDSL... args) {
        List<String> output = new ArrayList<>(args.length);
        for (TelegramMessageDSL arg : args) {
            output.add(arg.clone()
                    .withIndent(4)
                    .build());
        }

        return output;
    }

    public TelegramMessageDSL dump() {
        TelegramMessageDSL output = this.clone();
        messageBuilder.delete(0, messageBuilder.length());

        return output;
    }

    private TelegramMessageDSL withIndent(int newIndent) {
        this.indent = newIndent;

        return this;
    }

    public TelegramMessageDSL clone() {
        TelegramMessageDSL clone = new TelegramMessageDSL(formatter, indent);
        clone.messageBuilder.append(this.messageBuilder);

        return clone;
    }

    public TelegramMessageDSL optionalBlock(Predicate<Void> testFn, Function<TelegramMessageDSL, TelegramMessageDSL> customizer) {
        if(testFn.test(null)) {
            return customizer.apply(this);
        }

        return this;
    }

    public static String formatTagsForMarkdown(List<String> values) {
        return values.stream()
                .map(s -> "#" + toSnakeCase(s))
                .collect(Collectors.joining(", "));
    }

    public static String formatTextForMarkdown(String linkText) {
        return linkText.replace(".", "\\\\.");
    }

}

interface TelegramMessageFormatter {

    String bold(String text);

    String italic(String text);

    String underline(String text);

    String strikethrough(String text);

    String spoiler(String text);

    String code(String text);

    String pre(String text, String language);

    String link(String text, String url);

    String emoji(Emoji emoji);

    String customEmoji(String emojiId);

    ParseMode getParseMode();

}

class HtmlFormatter implements TelegramMessageFormatter {

    @Override
    public String bold(String text) { return "<b>" + text + "</b>"; }

    @Override
    public String italic(String text) { return "<i>" + text + "</i>"; }

    @Override
    public String underline(String text) { return "<u>" + text + "</u>"; }

    @Override
    public String strikethrough(String text) { return "<s>" + text + "</s>"; }

    @Override
    public String spoiler(String text) { return "<tg-spoiler>" + text + "</tg-spoiler>"; }

    @Override
    public String code(String text) { return "<code>" + text + "</code>"; }

    @Override
    public String pre(String text, String language) { return "<pre><code class=\"language-" + language + "\">" + text + "</code></pre>"; }

    @Override
    public String link(String text, String url) { return "<a href=\"" + url + "\">" + text + "</a>"; }

    @Override
    public String emoji(Emoji emoji) { return emoji.getHtmlHexadecimalCode(); }

    @Override
    public String customEmoji(String emojiId) {
        return EmojiManager.getEmoji(emojiId).map(this::emoji).orElse(emojiId);
    }

    @Override
    public ParseMode getParseMode() { return ParseMode.HTML; }

}

class MarkdownLegacyFormatter implements TelegramMessageFormatter {

    @Override
    public String bold(String text) { return "*" + text.trim() + "* "; }

    @Override
    public String italic(String text) { return "_" + text + "_ "; }

    @Override
    public String underline(String text) { return text; }

    @Override
    public String strikethrough(String text) { return text; }

    @Override
    public String spoiler(String text) { return text; }

    @Override
    public String code(String text) { return "`" + text + "` "; }

    @Override
    public String pre(String text, String language) {
        String lang = (language != null && !language.isEmpty()) ? language + "\n" : "\n";
        return "```" + lang + text + "\n``` ";
    }

    @Override
    public String link(String text, String url) { return "[" + text + "](" + url + ") "; }

    @Override
    public String emoji(Emoji emoji) { return emoji.getEmoji() + " "; }

    @Override
    public String customEmoji(String emojiId) {
        return EmojiManager.getEmoji(emojiId).map(Emoji::getEmoji).orElse(emojiId) + " ";
    }

    @Override
    public ParseMode getParseMode() { return ParseMode.MarkdownV2; }

}
