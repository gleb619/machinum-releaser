package machinum;

import machinum.book.BookController;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UnitTest {

    @Test
    public void welcome() {
        var controller = new BookController(null, null, null);
        assertEquals("Welcome to Jooby!", controller.booksList("", 0, 10, null));
    }

}
