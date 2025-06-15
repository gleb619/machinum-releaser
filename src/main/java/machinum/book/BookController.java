package machinum.book;

import io.avaje.validation.Validator;
import io.jooby.Context;
import io.jooby.Jooby;
import io.jooby.StatusCode;
import io.jooby.annotation.*;
import io.jooby.exception.StatusCodeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.book.BookRestClient.BookExportResult;
import machinum.novel.NovelScraper;
import machinum.novel.NovelScraper.ImageData;

import java.util.List;
import java.util.Optional;

import static machinum.telegram.TelegramHandler.NameUtil.toSnakeCase;

@Slf4j
@Path("/api")
@RequiredArgsConstructor
public class BookController {

    private final Validator validator;
    private final BookRepository repository;
    private final BookRestClient bookRestClient;

    public static BookController_ bookController(Jooby jooby) {
        return new BookController_(jooby.require(Validator.class),
                jooby.require(BookRepository.class),
                jooby.require(BookRestClient.class));
    }

    @GET("/books")
    public List<Book> booksList(@QueryParam("query") String queryParam,
                                @QueryParam("page") Integer pageParam,
                                @QueryParam("size") Integer sizeParam,
                                Context ctx) {
        var query = Optional.ofNullable(queryParam).orElse("");
        var page = Optional.ofNullable(pageParam).orElse(0);
        var size = Optional.ofNullable(sizeParam).orElse(10);

        var books = repository.list(query, page, size);

        // Fetch total count of items matching the query
        var totalItems = repository.count(query);
        var totalPages = (int) Math.ceil((double) totalItems / size);

        // Add pagination metadata to response headers
        ctx.setResponseHeader("X-Total-Items", String.valueOf(totalItems));
        ctx.setResponseHeader("X-Total-Pages", String.valueOf(totalPages));
        ctx.setResponseHeader("X-Current-Page", String.valueOf(page));
        ctx.setResponseHeader("X-Page-Size", String.valueOf(size));

        return books;
    }

    @GET("/books/{id}")
    public Book book(@PathParam("id") String id,
                     Context ctx) {
        return repository.findById(id)
                .orElseThrow(() -> new StatusCodeException(StatusCode.NOT_FOUND));
    }

    @POST("/books")
    public Book createBook(Book item, Context ctx) {
        validator.validate(item);

        var result = repository.create(item);

        ctx.setResponseCode(StatusCode.CREATED);
        return Book.builder()
                .id(result)
                .build();
    }

    @PUT("/books/{id}")
    public void updateBook(@PathParam("id") String id,
                           Book book, Context ctx) {
        if (!book.getId().equals(id)) {
            log.error("Malicious operation: {} <> {}", id, book.getId());
            throw new StatusCodeException(StatusCode.FORBIDDEN);
        }
        repository.update(id, book);

        ctx.setResponseCode(StatusCode.OK);
    }

    @DELETE("/books/{id}")
    public void removeBook(@PathParam("id") String id, Context ctx) {
        repository.delete(id);

        ctx.setResponseCode(StatusCode.OK);
    }

    @GET("/books/titles")
    public List<BookExportResult> getBookTitles(Context ctx) {
        // Set caching headers manually to 1 hour
        ctx.setResponseHeader("Cache-Control", "max-age=3600");
        ctx.setResponseHeader("Pragma", "cache");
        ctx.setResponseHeader("Expires", "3600");

        return bookRestClient.getAllBookTitlesCached();
    }

    @Deprecated
    @GET("/books/remote-import")
    public Context importFromRemoteUrl(@QueryParam("url") String url, Context ctx) {
        //Doesn't work via headless client(cookies and js are needed)
        NovelScraper.forUrl(url)
                .scrape()
                .ifPresentOrElse(novel -> {
                    var imageData = NovelScraper.ImageDownloader.defaultOne()
                            .fetch(novel.getCoverUrl());

                    Book book = Book.builder()
                            .uniqueId(toSnakeCase(novel.getTitle()))
                            .ruName("Безымянный")
                            .enName(novel.getTitle())
                            .originName(novel.getAltTitle())
                            .link(url)
                            .linkText("Novel updates")
                            .type(novel.getNovelType())
                            .genre(novel.getGenres())
                            .tags(novel.getTags())
                            .year(Integer.parseInt(novel.getYear()))
                            .chapters(novel.getChaptersCount())
                            .author(novel.getAuthor())
                            .description(novel.getDescription())
                            .imageData(imageData.map(ImageData::toBase64).orElse(null))
                            .build();

                    // Set caching headers manually to 1 hour
                    ctx.setResponseHeader("Cache-Control", "max-age=3600");
                    ctx.setResponseHeader("Pragma", "cache");
                    ctx.setResponseHeader("Expires", "3600");

                    ctx.render(book);
                }, () -> ctx.setResponseCode(404));

        return ctx;
    }

}
