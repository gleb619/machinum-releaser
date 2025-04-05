package machinum.book;

import io.avaje.validation.Validator;
import io.jooby.Context;
import io.jooby.StatusCode;
import io.jooby.annotation.*;
import io.jooby.exception.StatusCodeException;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

@Slf4j
@Path("/api")
public class BookController {

    @GET("/books")
    public List<Book> booksList(@QueryParam("query") String queryParam,
                                @QueryParam("page") Integer pageParam,
                                @QueryParam("size") Integer sizeParam,
                                Context ctx) {
        var query = Optional.ofNullable(queryParam).orElse("");
        var page = Optional.ofNullable(pageParam).orElse(1);
        var size = Optional.ofNullable(sizeParam).orElse(10);

        var repository = ctx.require(BookRepository.class);

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
        var repository = ctx.require(BookRepository.class);

        return repository.findById(id)
                .orElseThrow(() -> new StatusCodeException(StatusCode.NOT_FOUND));
    }

    @POST("/books")
    public Book createBook(Book item, Context ctx) {
        var repository = ctx.require(BookRepository.class);
        var validator = ctx.require(Validator.class);
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
        var repository = ctx.require(BookRepository.class);
        repository.update(id, book);

        ctx.setResponseCode(StatusCode.OK);
    }

    @DELETE("/books/{id}")
    public void removeBook(@PathParam("id") String id, Context ctx) {
        var repository = ctx.require(BookRepository.class);
        repository.delete(id);

        ctx.setResponseCode(StatusCode.OK);
    }

}
