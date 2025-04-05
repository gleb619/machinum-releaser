package machinum;

import io.jooby.test.JoobyTest;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class IntegrationTest extends BaseTest {

    @DisabledIfEnvironmentVariable(named = "IGNORE_FLAKY", matches = "true")
    @JoobyTest(value = App.class, port = 7077, factoryMethod = "createMockApplication")
    public void sampleTest(int serverPort) throws IOException, InterruptedException {

        var response = get("http://localhost:" + serverPort);

        assertThat(response.statusCode())
                .isEqualTo(200);
        assertThat(response.body())
                .isEqualTo("""
                        Hello World!
                        """);
    }

}
