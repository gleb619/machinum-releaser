package machinum;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.*;
import io.avaje.validation.Validator;
import io.jooby.Extension;
import io.jooby.Jooby;
import io.jooby.ServiceKey;
import io.jooby.ServiceRegistry;
import io.minio.MinioClient;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import machinum.audio.CoverArt;
import machinum.audio.TTSRestClient;
import machinum.audio.TextXmlReader;
import machinum.audio.TextXmlReader.TextInfo;
import machinum.book.BookRepository;
import machinum.book.BookRestClient;
import machinum.cache.CacheService;
import machinum.chapter.ChapterJsonlConverter;
import machinum.exception.AppException;
import machinum.image.ImageRepository;
import machinum.image.cover.CoverService;
import machinum.markdown.MarkdownConverter;
import machinum.minio.MinioService;
import machinum.pandoc.PandocRestClient;
import machinum.release.ReleaseRepository;
import machinum.release.ReleaseRepository.ReleaseTargetRepository;
import machinum.release.ReleaseScheduleGenerator;
import machinum.scheduler.ActionHandler.ActionsHandler;
import machinum.scheduler.Scheduler;
import machinum.telegram.*;
import machinum.telegram.TelegramAudio.Initializer;
import machinum.util.Pair;
import machinum.website.WebsiteHandler;
import org.apache.commons.io.IOUtils;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.generic.GenericType;
import org.jdbi.v3.core.mapper.ColumnMapper;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.qualifier.QualifiedType;
import org.jdbi.v3.jackson2.Jackson2Plugin;
import org.jdbi.v3.json.internal.JsonColumnMapperFactory;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.File;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static machinum.Config.Constants.*;
import static machinum.Config.Misc.*;
import static machinum.util.Util.firstNonNull;

@Slf4j
public class Config implements Extension {

    @Override
    public void install(@NotNull Jooby application) throws Exception {
        changeLogLevel(App.class.getPackageName(), Level.DEBUG);

        var registry = application.getServices();
        var config = application.getEnvironment().getConfig();
        var objectMapper = createMapper(Function.identity());
        var objectMapperTg = createMapper(tgMapper());
        var objectMapperRest = createMapper(Function.identity());

        registry.get(TemplateEngine.class).getTemplateResolvers().forEach(resolver -> {
            if (resolver instanceof ClassLoaderTemplateResolver cpResolver) {
                cpResolver.setSuffix(".html");
            }
        });

        var cache = new CacheService(Duration.of(10, ChronoUnit.MINUTES));
        registry.putIfAbsent(CacheService.class, cache);

        var jdbi = configureJdbi(registry);

        var bookRepository = new BookRepository(jdbi);
        var imageRepository = new ImageRepository(jdbi);
        registry.putIfAbsent(BookRepository.class, bookRepository);
        registry.putIfAbsent(ImageRepository.class, imageRepository);

        var releaseRepository = new ReleaseRepository(jdbi, objectMapper);
        var targetRepository = new ReleaseTargetRepository(jdbi, objectMapper);
        registry.putIfAbsent(ReleaseRepository.class, releaseRepository);
        registry.putIfAbsent(ReleaseTargetRepository.class, targetRepository);
        registry.putIfAbsent(ReleaseScheduleGenerator.class, new ReleaseScheduleGenerator());
        registry.putIfAbsent(Validator.class, Validator.builder().build());


        var llmUrl = config.getString(MACHINUM_LLM_URL);
        var jsonlConverter = new ChapterJsonlConverter(objectMapperRest);
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        var restClient = new BookRestClient(httpClient, jsonlConverter, cache, llmUrl);
        var markdownConverter = new MarkdownConverter();

        registry.putIfAbsent(ChapterJsonlConverter.class, jsonlConverter);
        registry.putIfAbsent(BookRestClient.class, restClient);
        registry.putIfAbsent(MarkdownConverter.class, markdownConverter);

        var pandocUrl = config.getString(PANDOC_URL);
        var pandocRestClient = new PandocRestClient(httpClient, cache, pandocUrl);
        registry.putIfAbsent(PandocRestClient.class, pandocRestClient);

        var coverService = new CoverService();
        registry.putIfAbsent(CoverService.class, coverService);

        var textInfo = textInfo(config);
        registry.putIfAbsent(TextInfo.class, textInfo);

        var ttsRestClient = ttsRestClient(config);

        var minioClient = minioClient(config);
        var minioService = new MinioService(minioClient, HttpClient.newHttpClient(), config.getString(MINIO_BUCKET_NAME));
        var initializer = initializer(minioService, ttsRestClient, textInfo, config);
        registry.putIfAbsent(MinioService.class, minioService);
        registry.putIfAbsent(Initializer.class, initializer);

        var coverArt = coverArt(minioService, config);

        var tgProperties = telegramProperties(config);
        var telegramAudio = telegramAudio(minioService, ttsRestClient, initializer, coverArt, config);
        var tgClient = new TelegramClient(tgProperties, objectMapperTg);
        var tgService = new TelegramService(tgProperties, tgClient);
        var tgHandler = new TelegramHandler(tgService, tgProperties, releaseRepository, imageRepository,
                restClient, markdownConverter, pandocRestClient, coverService, telegramAudio, textInfo, coverArt);
        registry.putIfAbsent(TelegramClient.class, tgClient);
        registry.putIfAbsent(TelegramService.class, tgService);

        var workDir = config.getString(APP_WORK_DIR);
        var websiteHandler = new WebsiteHandler(releaseRepository, restClient, workDir);
        registry.putIfAbsent(WebsiteHandler.class, websiteHandler);

        var handler = new ActionsHandler(websiteHandler, tgHandler, releaseRepository, targetRepository, bookRepository, restClient);
        registry.putIfAbsent(ActionsHandler.class, handler);
        registry.putIfAbsent(Scheduler.class, new Scheduler(Executors.newScheduledThreadPool(1), releaseRepository, handler));
        registry.putIfAbsent(ServiceKey.key(HttpClient.class, "assets"), HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(300))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());

        application.onStarted(() -> {
            boolean ttsCheckEnabled = config.getBoolean(TTS_CHECK_ENABLED);
            if(ttsCheckEnabled) {
                application.require(Initializer.class).init();
            }

            application.require(Scheduler.class).init();
            application.require(CacheService.class)
                    .scheduleCleanup(1, TimeUnit.HOURS);
        });
        application.onStop(() -> {
            application.require(Scheduler.class).close();
            application.require(CacheService.class).close();
            application.require(TelegramClient.class).close();
            application.require(WebsiteHandler.class).close();
        });
    }

    /* ============= */

    private Jdbi configureJdbi(ServiceRegistry registry) {
        Jdbi jdbi = registry.require(Jdbi.class);
        jdbi.registerColumnMapper(QualifiedType.of(new GenericType<List<String>>() {
        }), listMapper());
        jdbi.registerColumnMapper(QualifiedType.of(new GenericType<Map<String, Object>>() {
        }), jsonMapper());
        jdbi.registerColumnMapper(QualifiedType.of(new GenericType<LocalDateTime>() {
        }), localDateTimeMapper());
        jdbi.registerColumnMapper(QualifiedType.of(new GenericType<LocalDate>() {
        }), localDateMapper());
        jdbi.registerRowMapper(new GenericType<Pair<String, Object>>() {
        }, pairRowMapper());
        jdbi.registerRowMapper(new GenericType<Object>() {
        }, objectRowMapper());

        jdbi.installPlugin(new PostgresPlugin());
        jdbi.installPlugin(new Jackson2Plugin());

        return jdbi;
    }

    private Function<ObjectMapper, ObjectMapper> tgMapper() {
        return mapper -> mapper.setVisibility(mapper.getSerializationConfig().getDefaultVisibilityChecker()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withCreatorVisibility(JsonAutoDetect.Visibility.NONE));
    }

    private ObjectMapper createMapper(Function<ObjectMapper, ObjectMapper> customizer) {
        return customizer.apply(new ObjectMapper().findAndRegisterModules()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    }

    private TelegramProperties telegramProperties(com.typesafe.config.Config config) {
        return TelegramProperties.builder()
                .token(config.getString(TELEGRAM_TOKEN))
                .testChatId(config.getString(TELEGRAM_TEST_CHAT_ID))
                .mainChatId(config.getString(TELEGRAM_MAIN_CHAT_ID))
                .channelName(config.getString(TELEGRAM_CHANNEL_NAME))
                .channelLink(config.getString(TELEGRAM_CHANNEL_LINK))
                .build();
    }

    private TextInfo textInfo(com.typesafe.config.Config config) throws IOException {
        var file = new File(config.getString(APP_TEXTS_FILE)).getCanonicalFile();
        if(!file.exists()) {
            throw new AppException("Texts doesn't found at: %s", file.getAbsolutePath());
        }

        return new TextXmlReader().work(Files.readString(file.toPath(), StandardCharsets.UTF_8))
                .fromEnv();
    }

    private MinioClient minioClient(com.typesafe.config.Config config) {
        var minioEndpoint = config.getString(MINIO_ENDPOINT);
        var minioAccessKey = config.getString(MINIO_ACCESS_KEY);
        var minioSecretKey = config.getString(MINIO_SECRET_KEY);

        return MinioClient.builder()
                .endpoint(minioEndpoint)
                .credentials(minioAccessKey, minioSecretKey)
                .build();
    }

    private TTSRestClient ttsRestClient(com.typesafe.config.Config config) {
        var ttsEndpoint = config.getString(TTS_ENDPOINT);

        return new TTSRestClient(ttsEndpoint, HttpClient.newHttpClient(), new ObjectMapper());
    }

    private Initializer initializer(MinioService minioService, TTSRestClient ttsRestClient,
                                                 TextInfo textInfo, com.typesafe.config.Config config) {
        var advertisingKey = config.getString(TTS_ADVERTISING_KEY);
        var disclaimerKey = config.getString(TTS_DISCLAIMER_KEY);

        return new Initializer(minioService, ttsRestClient, advertisingKey, disclaimerKey, textInfo);
    }

    @SneakyThrows
    private TelegramAudio telegramAudio(MinioService minioService, TTSRestClient ttsRestClient,
                                        Initializer initializer, CoverArt coverArt, com.typesafe.config.Config config) {
        var advertisingKey = config.getString(TTS_ADVERTISING_KEY);
        var disclaimerKey = config.getString(TTS_DISCLAIMER_KEY);

        return new TelegramAudio(minioService, ttsRestClient, initializer, new ObjectMapper()
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE), advertisingKey, disclaimerKey,
                coverArt);
    }

    @SneakyThrows
    private CoverArt coverArt(MinioService minioService, com.typesafe.config.Config config) {
        var coverUrl = config.getString(TTS_COVER_URL);

        @Cleanup
        var inputStream = getClass().getClassLoader().getResourceAsStream("web/android-chrome-512x512.png");
        var defaultCover = IOUtils.toByteArray(Objects.requireNonNull(inputStream, "Default cover wa not found"));

        return CoverArt.builder()
                .content(CoverArt.resolveCoverArt(minioService, coverUrl, defaultCover))
                .build();
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Misc {

        public static void changeLogLevel(String name, Level level) {
            var factory = LoggerFactory.getILoggerFactory();
            var appLogger = factory.getLogger(name);
            if (appLogger instanceof ch.qos.logback.classic.Logger log) {
                log.setLevel(level);
            }
        }

        public static ColumnMapper<Map<String, Object>> jsonMapper() {
            return (r, columnNumber, ctx) -> {
                var mapColumnMapper = new JsonColumnMapperFactory().build(Map.class, ctx.getConfig())
                        .orElse((r1, columnNumber1, ctx1) -> new HashMap<>());

                return (Map) firstNonNull(mapColumnMapper.map(r, columnNumber, ctx), Collections.emptyMap());
            };
        }

        public static ColumnMapper<List<String>> listMapper() {
            return (r, columnNumber, ctx) -> {
                var value = r.getString(columnNumber);
                if (value == null || value.isEmpty()) {
                    return Collections.emptyList();
                }

                var list = Arrays.asList(value.split(","));
                return new ArrayList<>(list);
            };
        }

        public static ColumnMapper<LocalDateTime> localDateTimeMapper() {
            return (r, columnNumber, ctx) -> r.getObject(columnNumber, LocalDateTime.class);
        }

        public static ColumnMapper<LocalDate> localDateMapper() {
            return (r, columnNumber, ctx) -> r.getObject(columnNumber, LocalDate.class);
        }

        public static RowMapper<Pair<String, Object>> pairRowMapper() {
            return (rs, ctx) -> Pair.of(rs.getString(1), rs.getObject(2));
        }

        public static RowMapper<Object> objectRowMapper() {
            return (rs, ctx) -> rs.getObject(1);
        }

    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Constants {

        public static final String MACHINUM_LLM_URL = "machinum-llm.url";
        public static final String PANDOC_URL = "pandoc.url";
        public static final String TELEGRAM_TOKEN = "telegram.token";
        public static final String TELEGRAM_TEST_CHAT_ID = "telegram.testChatId";
        public static final String TELEGRAM_MAIN_CHAT_ID = "telegram.mainChatId";
        public static final String TELEGRAM_CHANNEL_NAME = "telegram.channelName";
        public static final String TELEGRAM_CHANNEL_LINK = "telegram.channelLink";
        public static final String APP_WORK_DIR = "app.workDir";
        public static final String APP_TEXTS_FILE = "app.textsFile";
        public static final String TTS_ENDPOINT = "tts.url";
        public static final String TTS_CHECK_ENABLED = "tts.checkEnabled";
        public static final String TTS_ADVERTISING_KEY = "tts.advertisingKey";
        public static final String TTS_DISCLAIMER_KEY = "tts.disclaimerKey";
        public static final String TTS_COVER_URL = "tts.coverUrl";
        public static final String MINIO_ENDPOINT = "minio.url";
        public static final String MINIO_ACCESS_KEY = "minio.accessKey";
        public static final String MINIO_SECRET_KEY = "minio.secretKey";
        public static final String MINIO_BUCKET_NAME = "minio.bucketName";

    }

}
