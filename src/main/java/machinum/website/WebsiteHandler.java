package machinum.website;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import machinum.book.BookRestClient;
import machinum.chapter.Chapter;
import machinum.machinimaserver.BookApiExporter.CheckResponse;
import machinum.machinimaserver.MachinimaServer;
import machinum.machinimaserver.MachinimaServer.Context;
import machinum.machinimaserver.MachinimaServer.CorsConfig;
import machinum.machinimaserver.MachinimaServer.JacksonModule;
import machinum.machinimaserver.MachinimaServer.SessionStorage;
import machinum.scheduler.ActionHandler;
import machinum.util.Pair;
import machinum.util.Util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static machinum.release.Release.ReleaseConstants.*;
import static machinum.release.Release.ReleaseStatus.MANUAL_ACTION_REQUIRED;
import static machinum.website.WebsiteHandler.NumberAllocator.allocator;

@Slf4j
@RequiredArgsConstructor
public class WebsiteHandler implements ActionHandler, AutoCloseable {

    private final BookRestClient bookRestClient;
    private final String workDir;

    private final AtomicReference<MachinimaServer> server = new AtomicReference<>();

    @Override
    public HandlerResult handle(ActionContext context) {
        var book = context.getBook();
        var target = context.getReleaseTarget();
        var siteUrl = (String) target.metadata(SITE_URL_PARAM);
        var sitePort = (Integer) target.metadata(SITE_PORT_PARAM, 8080);

        log.info("Starting chapter release for website: {}, site={}", book.getRuName(), siteUrl);
        var release = context.getRelease();
        var chaptersRequest = release.toPageRequest();
        var remoteBookId = context.getRemoteBookId();

        log.debug("Fetching ready chapters for: bookID={}", remoteBookId);

        var chapterList = bookRestClient.getReadyChaptersCached(remoteBookId, chaptersRequest.first(), chaptersRequest.second());
        var releaseSessionId = Long.toHexString(Double.doubleToLongBits(Math.random()));

        acquireServer(sitePort, releaseSessionId, chapterList);
        var link = generateLink(siteUrl, sitePort, releaseSessionId, chaptersRequest);

        return HandlerResult.builder()
                .status(MANUAL_ACTION_REQUIRED)
                .metadata("link", link)
                .build();
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
    private void acquireServer(int port, String releaseSessionId, List<Chapter> chapters) {
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
                            .customize(str -> str.add(releaseSessionId, allocator(first.getNumber(), last.getNumber()))))
                    .registry(sg -> sg.module(JacksonModule.class))
                    .handlers(chain -> chain
                            .get("/api/check/{tabId}", ctx -> ctx.render(registerTab(ctx, releaseSessionId)))
                            .get("/api/chapter/{chapterNumber}", ctx -> ctx.render(findChapter(ctx, chapters)))
                            .before(ctx -> log.info(">> {}", ctx.url()))
                            .after(ctx -> log.info("<< {} {}", ctx.url(), ctx.status()))
                            .exception(ctx -> ctx.status(500).render("{ \"status\": 500 }").header("Content-Type", "application/json"))
                    )
                    .onStarted(() -> log.debug("Release server has been started"))
            ).get()); //CompletableFuture of HttpServer
        }
    }

    @Synchronized
    private CheckResponse registerTab(Context ctx, String awaitedReleaseSessionId) {
        log.debug("Prepare to register tab for next communication");
        var tabId = ctx.path("tabId");
        var releaseSessionId = ctx.queryParam("releaseSessionId");

        if(!releaseSessionId.equals(awaitedReleaseSessionId)) {
            log.debug("The request failed verification due wrong releaseSessionId: {} <> {}", releaseSessionId, awaitedReleaseSessionId);
            return new CheckResponse(Boolean.FALSE);
        }

        if(!ctx.session().exists("tabId") || ctx.session().contains("tabId", tabId)) {
            //Continue work only with one tab in browser, that support current release flow
            return new CheckResponse(Boolean.TRUE);
        } else {
            log.debug("The request failed verification due wrong releaseSessionId: {} <> {}", releaseSessionId, awaitedReleaseSessionId);
            return new CheckResponse(Boolean.FALSE);
        }
    }

    private ChapterResponse findChapter(Context ctx, List<Chapter> chapters) {
        var releaseSessionId = ctx.queryParam("releaseSessionId");
        var allocator = (NumberAllocator) ctx.session().get(releaseSessionId);
        var chapterNumber = Integer.parseInt(ctx.path("chapterNumber"));

        if(allocator.isAllocated(chapterNumber)) {
           return new ChapterResponse("N/A", "Chapter has already been published");
        }

        return chapters.stream()
                .filter(chapter -> chapter.getNumber().equals(chapterNumber))
                .findFirst()
                .map(chapter -> {
                    allocator.allocate(chapter.getNumber());

                    return new ChapterResponse(
                            chapter.getTranslatedTitle(),
                            chapter.getTranslatedText());
                })
                .orElseThrow(() -> new IllegalArgumentException("Unknown chapter: %s".formatted(chapterNumber)));
    }

    @Override
    public void close() throws Exception {
        var httpServer = server.get();
        if(Objects.nonNull(httpServer)) {
            httpServer.stop();
        }
    }

    public record ChapterResponse(String title, String body){}

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
                throw new IllegalArgumentException("Number %d is out of the managed range [%d, %d].".formatted(number, from, to));
            }
            return allocationMatrix.getOrDefault(number, false);
        }

    }

}
