package machinum.book;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class BookRepository {

    private final Jdbi jdbi;

    public List<Book> list(String query, Integer page, Integer size) {
        int offset = (page - 1) * size;
        if (query == null || query.isEmpty()) {
            return jdbi.withHandle(handle -> handle.createQuery("SELECT * FROM books LIMIT :size OFFSET :offset")
                    .bind("size", size)
                    .bind("offset", offset)
                    .mapToBean(Book.class)
                    .list());
        } else {
            return jdbi.withHandle(handle -> handle.createQuery("SELECT * FROM books WHERE ru_name ILIKE :query OR en_name ILIKE :query LIMIT :size OFFSET :offset")
                    .bind("query", "%" + query + "%")
                    .bind("size", size)
                    .bind("offset", offset)
                    .mapToBean(Book.class)
                    .list());
        }
    }

    public Book getById(String id) {
        return findById(id)
                .orElseThrow(() -> new IllegalStateException("Book for given id is not found: " + id));
    }

    public Optional<Book> findById(String id) {
        return jdbi.withHandle(handle -> handle.createQuery("SELECT * FROM books WHERE id = :id")
                .bind("id", id)
                .mapToBean(Book.class)
                .findFirst());
    }

    public int count(String query) {
        if (query == null || query.isEmpty()) {
            return jdbi.withHandle(handle -> handle.createQuery("SELECT COUNT(*) FROM books")
                    .mapTo(Integer.class)
                    .one());
        } else {
            return jdbi.withHandle(handle -> handle.createQuery("SELECT COUNT(*) FROM books WHERE ru_name ILIKE :query OR en_name ILIKE :query")
                    .bind("query", "%" + query + "%")
                    .mapTo(Integer.class)
                    .one());
        }
    }

    public String create(Book book) {
        return jdbi.withHandle(handle -> handle.createUpdate("""
                            INSERT INTO books (
                                unique_id, ru_name, en_name, origin_name, link, link_text, type, genre, tags, year, chapters, author, description
                            ) VALUES (
                                :uniqueId, :ruName, :enName, :originName, :link, :linkText, :type, :genreString, :tagsString, :year, :chapters, :author, :description
                            ) RETURNING id
                        """)
                .bind("genreString", getGenreString(book))
                .bind("tagsString", getTagsString(book))
                .bindBean(book)
                .executeAndReturnGeneratedKeys("id")
                .mapTo(String.class)
                .one());
    }

    public boolean update(String id, Book book) {
        return jdbi.withHandle(handle -> handle.createUpdate("""
                            UPDATE books SET
                                unique_id = :uniqueId,
                                ru_name = :ruName,
                                en_name = :enName,
                                origin_name = :originName,
                                link = :link,
                                link_text = :linkText,
                                type = :type,
                                genre = :genreString,
                                tags = :tagsString,
                                year = :year,
                                chapters = :chapters,
                                author = :author,
                                description = :description,
                                image_id = :imageId,
                                origin_image_id = :originImageId,
                                updated_at = :updatedAtTime
                            WHERE id = :id
                        """)
                .bind("id", id)
                .bind("genreString", getGenreString(book))
                .bind("tagsString", getTagsString(book))
                .bind("updatedAtTime", LocalDateTime.now())
                .bindBean(book)
                .execute() > 0);
    }

    public boolean delete(String id) {
        return jdbi.withHandle(handle -> handle.createUpdate("DELETE FROM books WHERE id = :id")
                .bind("id", id)
                .execute() > 0);
    }

    /* ============= */

    public String getGenreString(Book book) {
        return String.join(",", book.getGenre());
    }

    public String getTagsString(Book book) {
        return String.join(",", book.getTags());
    }

}
