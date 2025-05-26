package machinum.image;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.exception.AppException;
import org.jdbi.v3.core.Jdbi;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static machinum.util.Util.saveSortOrder;

@Slf4j
@RequiredArgsConstructor
public class ImageRepository {

    private final Jdbi jdbi;

    public Image getById(String id) {
        return findById(id)
                .orElseThrow(() -> new IllegalStateException("Image for given id is not found: " + id));
    }

    public Optional<Image> findById(String id) {
        return jdbi.withHandle(handle -> handle.createQuery("SELECT * FROM images WHERE id = :id")
                .bind("id", id)
                .mapToBean(Image.class)
                .findFirst());
    }

    public List<Image> findByIds(List<String> ids) {
        List<Image> result = jdbi.withHandle(handle -> handle.createQuery("SELECT * FROM images WHERE id in (<ids>)")
                .bindList("ids", ids)
                .mapToBean(Image.class)
                .list());

        return saveSortOrder(ids, result, Image::getId);
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

    public void updateCoverId(String originImageId, String coverImageId) {
        jdbi.withHandle(handle -> handle.createUpdate(//language=sql
                        """
                                UPDATE images SET cover_id = :coverId WHERE id = :id
                                """)
                .bind("id", originImageId)
                .bind("coverId", coverImageId)
                .execute());
    }

    public Optional<Image> findCoverByOriginId(String originImageId) {
        return jdbi.withHandle(handle -> handle.createQuery(//language=sql
                        """
                                SELECT
                                	i1.id
                                FROM
                                	images i0
                                JOIN images i1 ON i1.id = i0.cover_id
                                WHERE
                                	i0.id = :id;
                                """)
                .bind("id", originImageId)
                .mapToBean(Image.class)
                .findFirst());
    }

    public Image findOrCreateCover(String originImageId, Function<Image, Image> creator) {
        return findCoverByOriginId(originImageId).orElseGet(() -> {
            var originImage = findById(originImageId)
                    .orElseThrow(() -> new AppException("Image not found for id: ", originImageId));
            var coverImage = creator.apply(originImage);
            var coverImageId = create(coverImage);
            updateCoverId(originImageId, coverImageId);

            return Image.builder()
                    .id(coverImageId)
                    .build();
        });
    }

}
