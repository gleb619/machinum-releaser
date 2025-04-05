package machinum;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.avaje.validation.Validator;
import io.jooby.Extension;
import io.jooby.Jooby;
import lombok.extern.slf4j.Slf4j;
import machinum.book.BookRepository;
import machinum.image.ImageRepository;
import machinum.release.ReleaseRepository;
import machinum.release.ReleaseRepository.ReleaseTargetRepository;
import machinum.release.ReleaseScheduleGenerator;
import machinum.scheduler.ActionHandler.ActionsHandler;
import machinum.scheduler.Scheduler;
import machinum.telegram.TelegramClient;
import machinum.telegram.TelegramHandler;
import machinum.telegram.TelegramService;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.generic.GenericType;
import org.jdbi.v3.core.mapper.ColumnMapper;
import org.jdbi.v3.core.qualifier.QualifiedType;
import org.jdbi.v3.jackson2.Jackson2Plugin;
import org.jdbi.v3.json.internal.JsonColumnMapperFactory;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;

import static machinum.Util.firstNonNull;

@Slf4j
public class Config implements Extension {

    public static void changeLogLevel(String name, Level level) {
        var factory = LoggerFactory.getILoggerFactory();
        var appLogger = factory.getLogger(name);
        if (appLogger instanceof ch.qos.logback.classic.Logger log) {
            log.setLevel(level);
        }
    }

    private static ColumnMapper<Map<String, Object>> jsonMapper() {
        return (r, columnNumber, ctx) -> {
            var mapColumnMapper = new JsonColumnMapperFactory().build(Map.class, ctx.getConfig())
                    .orElse((r1, columnNumber1, ctx1) -> new HashMap<>());

            return (Map) firstNonNull(mapColumnMapper.map(r, columnNumber, ctx), Collections.emptyMap());
        };
    }

    /* ============= */

    private static ColumnMapper<List<String>> listMapper() {
        return (r, columnNumber, ctx) -> {
            var value = r.getString(columnNumber);
            if (value == null || value.isEmpty()) {
                return Collections.emptyList();
            }

            var list = Arrays.asList(value.split(","));
            return new ArrayList<>(list);
        };
    }

    private static ColumnMapper<LocalDateTime> localDateTimeMapper() {
        return (r, columnNumber, ctx) -> r.getObject(columnNumber, LocalDateTime.class);
    }

    private static ColumnMapper<LocalDate> localDateMapper() {
        return (r, columnNumber, ctx) -> r.getObject(columnNumber, LocalDate.class);
    }

    @Override
    public void install(@NotNull Jooby application) throws Exception {
        changeLogLevel(App.class.getPackageName(), Level.DEBUG);

        var registry = application.getServices();
        var config = application.getEnvironment().getConfig();
        var objectMapper = new ObjectMapper().findAndRegisterModules()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        registry.get(TemplateEngine.class).getTemplateResolvers().forEach(resolver -> {
            if (resolver instanceof ClassLoaderTemplateResolver cpResolver) {
                cpResolver.setSuffix(".html");
            }
        });

        Jdbi jdbi = registry.require(Jdbi.class);
        jdbi.registerColumnMapper(QualifiedType.of(new GenericType<List<String>>() {
        }), listMapper());
        jdbi.registerColumnMapper(QualifiedType.of(new GenericType<Map<String, Object>>() {
        }), jsonMapper());
        jdbi.registerColumnMapper(QualifiedType.of(new GenericType<LocalDateTime>() {
        }), localDateTimeMapper());
        jdbi.registerColumnMapper(QualifiedType.of(new GenericType<LocalDate>() {
        }), localDateMapper());

        jdbi.installPlugin(new PostgresPlugin());
        jdbi.installPlugin(new Jackson2Plugin());

        registry.putIfAbsent(BookRepository.class, new BookRepository(jdbi));
        registry.putIfAbsent(ImageRepository.class, new ImageRepository(jdbi));

        var repository = new ReleaseRepository(jdbi, objectMapper);
        registry.putIfAbsent(ReleaseRepository.class, repository);
        registry.putIfAbsent(ReleaseTargetRepository.class, new ReleaseTargetRepository(jdbi, objectMapper));
        registry.putIfAbsent(ReleaseScheduleGenerator.class, new ReleaseScheduleGenerator());
        registry.putIfAbsent(Validator.class, Validator.builder().build());

        var token = config.getString("telegram.token");
        var chatId = config.getString("telegram.chatId");
        var tgClient = new TelegramClient(token, chatId, objectMapper);
        var tgService = new TelegramService(tgClient);
        var tgHandler = new TelegramHandler(tgService);
        registry.putIfAbsent(TelegramClient.class, tgClient);
        registry.putIfAbsent(TelegramService.class, tgService);

        var handler = new ActionsHandler(tgHandler, repository);
        registry.putIfAbsent(ActionsHandler.class, handler);
        registry.putIfAbsent(Scheduler.class, new Scheduler(Executors.newScheduledThreadPool(1), repository, handler));

        application.onStop(() -> {
            application.require(Scheduler.class).close();
            application.require(TelegramClient.class).close();
        });
    }

}
