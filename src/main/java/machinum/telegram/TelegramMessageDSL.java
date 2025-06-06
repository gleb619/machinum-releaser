package machinum.telegram;

import machinum.exception.AppException;
import net.fellbaum.jemoji.Emoji;
import net.fellbaum.jemoji.EmojiManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static machinum.telegram.TelegramHandler.NameUtil.toFileSnakeCase;
import static machinum.telegram.TelegramHandler.NameUtil.toSnakeCase;

public class TelegramMessageDSL {

    private final StringBuilder messageBuilder = new StringBuilder();
    private int indent;

    public TelegramMessageDSL(int indent) {
        this.indent = indent;
    }

    public static TelegramMessageDSL dsl() {
        return new TelegramMessageDSL(2);
    }

    public TelegramMessageDSL text(Integer text) {
        return text(String.valueOf(text));
    }

    public TelegramMessageDSL text(String text) {
        messageBuilder.append(text);
        return this;
    }

    public TelegramMessageDSL bold(String text) {
        messageBuilder.append("<b>").append(text).append("</b>");
        return this;
    }

    public TelegramMessageDSL italic(String text) {
        messageBuilder.append("<i>").append(text).append("</i>");
        return this;
    }

    public TelegramMessageDSL underline(String text) {
        messageBuilder.append("<u>").append(text).append("</u>");
        return this;
    }

    public TelegramMessageDSL strikethrough(String text) {
        messageBuilder.append("<s>").append(text).append("</s>");
        return this;
    }

    public TelegramMessageDSL spoiler(String text) {
        messageBuilder.append("<tg-spoiler>").append(text).append("</tg-spoiler>");
        return this;
    }

    public TelegramMessageDSL code(String text) {
        messageBuilder.append("<code>").append(text).append("</code>");
        return this;
    }

    public TelegramMessageDSL pre(String text, String language) {
        messageBuilder.append("<pre><code class=\"language-")
                .append(language)
                .append("\">")
                .append(text)
                .append("</code></pre>");
        return this;
    }

    public TelegramMessageDSL link(String text, String url) {
        messageBuilder.append("<a href=\"")
                .append(url)
                .append("\">")
                .append(text)
                .append("</a>");
        return this;
    }

    public TelegramMessageDSL replyTo(String channel, String text, Integer messageId) {
        return link(text, "https://t.me/" + channel + "/" + messageId);
    }

    public TelegramMessageDSL mention(String username) {
        return link("@" + username, "https://t.me/" + username);
    }

    public TelegramMessageDSL emoji(Emoji emojiId) {
        messageBuilder.append(emojiId.getHtmlHexadecimalCode());

        return this;
    }

    public TelegramMessageDSL customEmoji(String emojiId) {
        EmojiManager.getEmoji(emojiId).ifPresentOrElse(
                emoji -> messageBuilder.append(emoji.getHtmlHexadecimalCode()),
                () -> messageBuilder.append(emojiId));

        return this;
    }

    public String build() {
        String result = messageBuilder.toString();

        if (result.length() > 4096) {
            throw new AppException("Message is too big: min=4096, actual=%s".formatted(result.length()));
        }

        return result;
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
        return list(constructor.apply(new TelegramMessageDSL(indent + 2)));
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
        TelegramMessageDSL clone = new TelegramMessageDSL(indent);
        clone.messageBuilder.append(this.messageBuilder);

        return clone;
    }

    public TelegramMessageDSL optionalBlock(Predicate<Void> testFn, Function<TelegramMessageDSL, TelegramMessageDSL> customizer) {
        if(testFn.test(null)) {
            return customizer.apply(this);
        }

        return this;
    }

}
