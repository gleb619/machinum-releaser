package machinum.image;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;

import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class ImageRepository {

    private final Jdbi jdbi;

    public Optional<Image> findById(String id) {
        return jdbi.withHandle(handle -> handle.createQuery("SELECT * FROM images WHERE id = :id")
                .bind("id", id)
                .mapToBean(Image.class)
                .findFirst());
    }

    public String create(Image image) {
        return jdbi.withHandle(handle -> handle.createUpdate("""
                            INSERT INTO images (name, content_type, data) VALUES (:name, :contentType, :data) RETURNING id
                        """)
                .bindBean(image)
                .executeAndReturnGeneratedKeys("id")
                .mapTo(String.class)
                .one());
    }

}
