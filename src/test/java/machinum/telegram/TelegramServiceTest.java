package machinum.telegram;

import machinum.exception.AppException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class TelegramServiceTest {

    @Mock
    TelegramProperties telegramProperties;
    @Mock
    TelegramClient telegramClient;

    TelegramService telegramService;


    @BeforeEach
    void setUp() {
        telegramService = new TelegramService(telegramProperties, telegramClient);
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

}