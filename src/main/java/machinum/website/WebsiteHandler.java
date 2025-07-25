package machinum.website;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import machinum.book.BookRestClient;
import machinum.chapter.Chapter;
import machinum.exception.AppException;
import machinum.machinimaserver.BookApiExporter.CheckResponse;
import machinum.machinimaserver.MachinimaServer;
import machinum.machinimaserver.MachinimaServer.Context;
import machinum.machinimaserver.MachinimaServer.CorsConfig;
import machinum.machinimaserver.MachinimaServer.JacksonModule;
import machinum.machinimaserver.MachinimaServer.SessionStorage;
import machinum.release.Release;
import machinum.release.ReleaseRepository;
import machinum.scheduler.ActionHandler;
import machinum.util.Pair;
import machinum.util.Util;

import java.sql.Time;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static machinum.release.Release.ReleaseConstants.*;
import static machinum.release.Release.ReleaseStatus.MANUAL_ACTION_REQUIRED;
import static machinum.util.Util.md5;
import static machinum.website.WebsiteHandler.NumberAllocator.allocator;

@Slf4j
@RequiredArgsConstructor
public class WebsiteHandler implements ActionHandler, AutoCloseable {

    public static final String CONTENT_TYPE_HTML = "text/html";

    private final ReleaseRepository releaseRepository;
    private final BookRestClient bookRestClient;
    private final String workDir;

    private final AtomicReference<MachinimaServer> server = new AtomicReference<>();

    @Override
    public HandlerResult handle(ActionContext context) {
        var book = context.getBook();
        var target = context.getReleaseTarget();
        var siteUrl = (String) target.metadata(SITE_URL_PARAM);
        var sitePort = (Integer) target.metadata(SITE_PORT_PARAM, 8080);

        log.info("Starting chapter release for website: title={}, site={}", book.getRuName(), siteUrl);
        var release = context.getRelease();
        var chaptersRequest = release.toPageRequest();
        var remoteBookId = context.getRemoteBookId();

        log.debug("Fetching ready chapters for: bookID={}", remoteBookId);

        var chapterList = bookRestClient.getReadyChaptersCached(remoteBookId, chaptersRequest.first(), chaptersRequest.second());
        var releaseSessionId = md5(chaptersRequest.toString());

        acquireServer(sitePort, releaseSessionId, chapterList, release);

        return switch (release.status()) {
            case DRAFT -> {
                log.debug("Will generate external link to start publishing process for: {}", releaseSessionId);
                var link = generateLink(siteUrl, sitePort, releaseSessionId, chaptersRequest);

                yield HandlerResult.builder()
                        .status(MANUAL_ACTION_REQUIRED)
                        .metadata("link", link)
                        .build();
            }
            case MANUAL_ACTION_REQUIRED -> {
                log.debug("Await user's action for: {}", releaseSessionId);
                yield HandlerResult.noChanges();
            }
            default -> throw new AppException("Unknown state of release: id={}, state={}", release.getId(), release.getStatus());
        };
    }

    /* ============= */

    private String generateLink(String siteUrl, Integer sitePort, String releaseSessionId, Pair<Integer, Integer> chaptersRequest) {
        String withSessionId = Util.addQueryParam(siteUrl, "releaseSessionId", releaseSessionId);
        String withSitePort = Util.addQueryParam(withSessionId, "sitePort", String.valueOf(sitePort));
        String withFrom = Util.addQueryParam(withSitePort, "from", String.valueOf(chaptersRequest.first()));
        String withTo = Util.addQueryParam(withFrom, "to", String.valueOf(chaptersRequest.second()));
        return withTo;
    }

    @SneakyThrows
    private void acquireServer(int port, String releaseSessionId, List<Chapter> chapters, Release release) {
        if(server.get() == null) {
            var first = chapters.getFirst();
            var last = chapters.getLast();
            server.getAndSet(MachinimaServer.start(s -> s
                    .serverConfig(c -> c
                            .port(port)
                            .uploadDir(workDir)
                            .corsConfig(CorsConfig.permissive())
                    )
                    .sessionStorage(SessionStorage.defaultOne()
                            //We initialize the session with information about the distribution (e.g. which chapters need to be published).
                            .customize(session -> session.add(releaseSessionId, allocator(first.getNumber(), last.getNumber()))))
                    .registry(sg -> sg.module(JacksonModule.class))
                    .handlers(chain -> chain
                            .get("/api/check/{tabId}", ctx -> ctx.render(registerTab(ctx, releaseSessionId)))
                            .get("/api/chapter/{chapterNumber}", ctx -> ctx.render(findChapter(ctx, chapters)))
                            .get("/api/release/close-page", ctx -> ctx.render(markReleaseAsExecuted(ctx, releaseSessionId, release)))
                            .before(ctx -> log.info(">> {} {}", ctx.method(), ctx.url()))
                            .after(ctx -> log.info("<< {} {} {}", ctx.method(), ctx.url(), ctx.status()))
                            .exception(ctx -> ctx.status(500).render(Map.of("status", 500)))
                    )
                    .onStarted(() -> log.debug("Release server has been started for: {}", releaseSessionId))
            ).get()); //CompletableFuture of HttpServer
        }
    }

    @Synchronized
    private CheckResponse registerTab(Context ctx, String awaitedReleaseSessionId) {
        var tabId = ctx.path("tabId");
        var releaseSessionId = ctx.queryParam("releaseSessionId");
        log.debug("Prepare to register tab for next communication: tabId={}, sessionId={}", tabId, releaseSessionId);

        if(!releaseSessionId.equals(awaitedReleaseSessionId)) {
            log.debug("The request failed verification due wrong releaseSessionId: {} <> {}", releaseSessionId, awaitedReleaseSessionId);
            return new CheckResponse("Wrong release sessionId", Boolean.FALSE);
        }

        if(!ctx.session().exists("tabId") || ctx.session().contains("tabId", tabId)) {
            //Continue work only with one tab in browser, that support current release flow
            ctx.session().add("tabId", tabId);
            return new CheckResponse("", Boolean.TRUE);
        } else {
            log.debug("The request failed verification due wrong tabId: {} <> {}", tabId, ctx.session().get("tabId"));
            return new CheckResponse("Wrong tabId", Boolean.FALSE);
        }
    }

    private ChapterResponse findChapter(Context ctx, List<Chapter> chapters) {
        var releaseSessionId = ctx.queryParam("releaseSessionId");
        var allocator = (NumberAllocator) ctx.session().get(releaseSessionId);
        var chapterNumber = Integer.parseInt(ctx.path("chapterNumber"));

        boolean isAllocated = allocator.isAllocated(chapterNumber);

        if(chapterNumber > chapters.getLast().getNumber()) {
           return new ChapterResponse("", "", true, true);
        } else if(isAllocated) {
           return new ChapterResponse("N/A", "Chapter has already been published", false, false);
        }

        return chapters.stream()
                .filter(chapter -> chapter.getNumber().equals(chapterNumber))
                .findFirst()
                .map(chapter -> {
                    allocator.allocate(chapter.getNumber());

                    return new ChapterResponse(
                            chapter.getTranslatedTitle(),
                            chapter.getTranslatedText(),
                            true,
                            false);
                })
                .orElseThrow(() -> new IllegalArgumentException("Unknown chapter: %s".formatted(chapterNumber)));
    }

    private String markReleaseAsExecuted(Context ctx, String awaitedReleaseSessionId, Release release) {
        var releaseSessionId = ctx.queryParam("releaseSessionId");
        boolean success = releaseSessionId.equals(awaitedReleaseSessionId);
        if(success) {
            log.debug("Release marked as executed: {}", release.getId());
            releaseRepository.markAsExecuted(release.getId());
            CompletableFuture.runAsync(() -> {
                try {
                    log.debug("Closing release server: {}", awaitedReleaseSessionId);
                    TimeUnit.SECONDS.sleep(1);
                    close();
                    log.debug("Release server is closed: {}", awaitedReleaseSessionId);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        ctx.header(Context.CONTENT_TYPE, CONTENT_TYPE_HTML);

        return responseHtml(success);
    }

    private String responseHtml(boolean success) {
        var script = success ? "<script> setTimeout(function() { window.close(); }, 1000); </script>" : "";
        var text = success ? "This page will close in 1 seconds..." : "Provided wrong or non existed release";
        return """
           <!DOCTYPE html>
           <html lang="en">
           <head>
               <meta charset="UTF-8">
               <meta name="viewport" content="width=device-width, initial-scale=1.0">
               <title>Close Page</title>
               <style>
                   body {
                       margin: 0;
                       font-family: Arial, sans-serif;
                       background-color: white;
                       color: black;
                       display: flex;
                       justify-content: center;
                       align-items: center;
                       height: 100vh;
                       overflow: hidden;
                   }
                   .container {
                       text-align: center;
                       padding: 2rem;
                       border-radius: 10px;
                       background-color: #ffffff;
                       box-shadow: 0 4px 6px rgba(59, 130, 246, 0.2), 0 1px 3px rgba(59, 130, 246, 0.1);
                   }
                   h1 {
                       color: #3b82f6;
                       font-size: 2rem;
                       margin-bottom: 1rem;
                   }
                   p {
                       font-size: 1rem;
                       color: #333333;
                   }
               </style>
               %s
           </head>
           <body>
               <div class="container">
                   <h1>%s</h1>
                   <p>Thank you for your patience!</p>
               </div>
           </body>
           </html>""".formatted(script, text);
    }

    @Override
    public void close() throws Exception {
        var httpServer = server.get();
        if(Objects.nonNull(httpServer)) {
            httpServer.stop();
            server.set(null);
        }
    }

    public record ChapterResponse(String title, String body, Boolean success, Boolean finished){}

    /**
     * Manages the allocation status of numbers within a specified range.
     * Each number in the range can be either allocated (true) or unallocated (false).
     */
    @RequiredArgsConstructor
    public static class NumberAllocator {

        // A map to store the allocation status of each number.
        // Key: The number itself.
        // Value: Boolean, true if allocated, false if unallocated.
        private final Map<Integer, Boolean> allocationMatrix;
        private final Integer from;
        private final Integer to;

        public static NumberAllocator allocator(Integer from, Integer to) {
            return new NumberAllocator(new HashMap<>(), from, to);
        }

        public NumberAllocator init() {
            // Initialize all numbers in the specified range as unallocated.
            IntStream.rangeClosed(from, to)
                    .forEach(number -> allocationMatrix.put(number, false));
            return this;
        }

        /**
         * Attempts to allocate a given number.
         * If the number is within the managed range and unallocated, its state is changed to allocated (true).
         * If the number is outside the managed range or already allocated, an exception is thrown.
         *
         * @param number The integer number to allocate.
         * @throws IllegalArgumentException If the number is outside the managed range.
         * @throws IllegalStateException    If the number is already allocated.
         */
        public void allocate(int number) {
            // Check if the number is within the defined range.
            if (number < from || number > to) {
                throw new IllegalArgumentException("Number %d is out of the managed range [%d, %d].".formatted(number, from, to));
            }

            // Retrieve the current allocation status.
            // Using getOrDefault to handle cases where a number might somehow not be in the map (though constructor prevents this).
            Boolean isAllocated = allocationMatrix.getOrDefault(number, false);

            if (isAllocated) {
                // If the number is already allocated, throw an exception.
                throw new IllegalStateException("Number %d is already allocated.".formatted(number));
            } else {
                // If the number is not yet allocated, set its status to true (allocated).
                allocationMatrix.put(number, true);
                log.info("Successfully allocated number: " + number);
            }
        }

        /**
         * Checks if a given number is currently allocated.
         *
         * @param number The integer number to check.
         * @return true if the number is allocated, false otherwise.
         * @throws IllegalArgumentException If the number is outside the managed range.
         */
        public boolean isAllocated(int number) {
            if (number < from || number > to) {
                log.warn("Number {} is out of the managed range [{}, {}].", number, from, to);
                return false;
            }
            return allocationMatrix.getOrDefault(number, false);
        }

    }

}
