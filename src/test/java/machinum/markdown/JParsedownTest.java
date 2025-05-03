package machinum.markdown;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JParsedownTest {

    @Test
    void testMain() {
        String text = new JParsedown().text("*Hello* _world_!");
        assertThat(text)
                .isNotEmpty()
                .isEqualTo("");
    }

}