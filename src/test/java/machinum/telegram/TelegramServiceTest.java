package machinum.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.pengrad.telegrambot.model.request.ParseMode;
import machinum.book.Book;
import machinum.exception.AppException;
import machinum.image.Image;
import machinum.telegram.TelegramClient.Caption;
import machinum.telegram.TelegramClient.Response;
import org.apache.commons.text.StringSubstitutor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramServiceTest {

    @Mock
    TelegramProperties telegramProperties;
    @Mock
    TelegramClient telegramClient;

    TelegramService telegramService;


    @BeforeEach
    void setUp() {
        telegramService = new TelegramService(telegramProperties, telegramClient, (values, template) -> {
            StringSubstitutor substitutor = new StringSubstitutor(values);
            substitutor.setVariablePrefix("{");
            substitutor.setVariableSuffix("}");

            return substitutor.replace(template);
        });
    }

    @Test
    void testValidateMessage_ValidHtml() {
        String validHtml = "<b>Bold text</b><i>Italic text</i><a href=\"http://example.com\">Link</a>";

        // Should not throw
        telegramService.validateMessage(validHtml);
    }

    @Test
    void testValidateMessage_InvalidHtml_UnclosedTag() {
        String invalidHtml = "<b>Bold text<i>Italic text</i>"; // Missing </b>

        AppException exception = Assertions.assertThrows(
                AppException.class,
                () -> telegramService.validateMessage(invalidHtml)
        );

        Assertions.assertTrue(exception.getMessage().contains("HTML validation failed"));
    }

    @Test
    void testValidateMessage_InvalidHtml_MismatchedTags() {
        String invalidHtml = "<b>Bold text</i>"; // </i> without <i>

        AppException exception = Assertions.assertThrows(
                AppException.class,
                () -> telegramService.validateMessage(invalidHtml)
        );

        Assertions.assertTrue(exception.getMessage().contains("HTML validation failed"));
    }

    @Test
    void testValidateMessage_EmptyString() {
        String emptyHtml = "";

        // Should not throw
        telegramService.validateMessage(emptyHtml);
    }

    @Test
    void testValidateMessage_PlainText() {
        String plainText = "Just plain text without any HTML tags.";

        // Should not throw
        telegramService.validateMessage(plainText);
    }

    @Test
    void testPublishNewBook_WithData() throws Exception {
        when(telegramClient.sendImagesWithMessage(any(), any(Caption.class), anyList())).thenReturn(Response.of(123));

        Book book = Book.builder()
                .ruName("Test Book")
                .genre(List.of("боевик", "драма"))
                .tags(List.of("аристократия", "армия"))
                .description("Test synopsis")
                .year(2020)
                .chapters(152)
                .author("Author")
                .type("Web novel")
                .linkText("Novel Updates")
                .link("http://example.com")
                .originName("Original")
                .enName("English")
                .build();

        Response response = telegramService.publishNewBook("chatId", book, List.of());

        assertThat(response.messageId()).isEqualTo(123);

        ArgumentCaptor<Caption> captionCaptor = ArgumentCaptor.forClass(Caption.class);
        verify(telegramClient).sendImagesWithMessage(eq("chatId"), captionCaptor.capture(), eq(List.of()));

        Caption caption = captionCaptor.getValue();
        assertThat(caption.getText()).contains("Test Book");
        assertThat(caption.getText()).contains("#боевик");
        assertThat(caption.getText()).contains("#драма");
        assertThat(caption.getText()).contains("#аристократия");
        assertThat(caption.getText()).contains("#армия");
        assertThat(caption.getText()).contains("Test synopsis");
        assertThat(caption.getParseMode()).isEqualTo(ParseMode.MarkdownV2);
    }

    @Test
    void testPublishNewBook_EmptyFields() throws Exception {
        when(telegramClient.sendImagesWithMessage(any(), any(Caption.class), anyList())).thenReturn(Response.of(456));

        Book book = Book.builder()
                .ruName("Empty Book")
                .genre(List.of())
                .tags(List.of())
                .description(null)
                .year(2021)
                .chapters(50)
                .author("No Author")
                .type("Novel")
                .linkText("No Link")
                .link("http://no.com")
                .originName("No Orig")
                .enName("No Eng")
                .build();

        Response response = telegramService.publishNewBook("chatId2", book, List.of());

        assertThat(response.messageId()).isEqualTo(456);

        ArgumentCaptor<Caption> captionCaptor = ArgumentCaptor.forClass(Caption.class);
        verify(telegramClient).sendImagesWithMessage(eq("chatId2"), captionCaptor.capture(), eq(List.of()));

        Caption caption = captionCaptor.getValue();
        assertThat(caption.getText()).contains("Empty Book");
        assertThat(caption.getText()).contains("📖*Жанры:* ");
        assertThat(caption.getText()).contains("♯*Теги:* ");
        assertThat(caption.getText()).contains("📜*Синопсис:* ||||");
        assertThat(caption.getParseMode()).isEqualTo(ParseMode.MarkdownV2);
    }

}
