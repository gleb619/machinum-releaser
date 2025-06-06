package machinum;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.jooby.Jooby;
import io.jooby.MapModelAndView;
import io.jooby.OpenAPIModule;
import io.jooby.flyway.FlywayModule;
import io.jooby.handler.AssetHandler;
import io.jooby.handler.AssetSource;
import io.jooby.hikari.HikariModule;
import io.jooby.jackson.JacksonModule;
import io.jooby.jdbi.JdbiModule;
import io.jooby.jetty.JettyServer;
import io.jooby.thymeleaf.ThymeleafModule;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import machinum.book.BookController_;
import machinum.cache.CacheService;
import machinum.exception.AppException;
import machinum.image.ImageController_;
import machinum.image.ImageRepository;
import machinum.image.cover.CoverService;
import machinum.scheduler.Scheduler;
import org.slf4j.Logger;

import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static machinum.Config.changeLogLevel;
import static machinum.release.ReleaseController.releaseController;
import static machinum.util.Util.hasCause;

@Slf4j
@OpenAPIDefinition(info =
@Info(title = "Machinum Releaser",
        version = "1",
        description = """
                A small application for Restapi for for shaping book translation releases
                """),
        tags = {
                @Tag(name = "machinum")
        }
)
public class App extends Jooby {

    {
        install(new JettyServer());
        install(new ThymeleafModule());
        install(new JacksonModule(JacksonModule.create()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)));
        install(new OpenAPIModule());
        install(new HikariModule());
        install(new FlywayModule());
        install(new JdbiModule());

//        use(new AccessLogHandler());

        error((ctx, cause, statusCode) -> {
            // Log the error
            log.error("ERROR: path=%s, status=%s, message=%s".formatted(ctx.path(), ctx.getResponseCode(), cause.getMessage()), cause);
            ctx.setResponseCode(statusCode);
            ctx.setResponseHeader("Content-Type", "application/json");
            var message = hasCause(cause, AppException.class) ? cause.getMessage() : "An unexpected error occurred, please check logs";
            ctx.render(Map.of(
                    "success", Boolean.FALSE,
                    "message", message
            ));
        });

        before(ctx -> log.info("> {}", ctx.getRequestPath()));

        after((ctx, result, failure) -> {
            if (failure == null) {
                log.info("< {} {}", ctx.getRequestPath(), ctx.getResponseCode().value());
            } else {
                log.error("[X] {} {}", ctx.getRequestPath(), ctx.getResponseCode(), failure);
            }
        });

        install(new Config());
        mvc(new BookController_());
        mvc(new ImageController_(require(ImageRepository.class), require(CoverService.class)));
        mvc(releaseController(this));

        get("/", ctx -> new MapModelAndView("index.html", Map.of()));

        AssetSource web = AssetSource.create(Paths.get("src/main/resources/web"));
        assets("/?*", new AssetHandler("index.html", web));
    }

    public static void main(final String[] args) {
        try {
            changeLogLevel(Logger.ROOT_LOGGER_NAME, Level.INFO);
            runApp(args, App::new);
        } catch (Exception e) {
            log.error("ERROR: ", e);
            // Thanks to graceful optimisation, the application will not stop if the
            // application port is busy
            System.exit(1);
        }
    }

}
