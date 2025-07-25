package machinum.release;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import machinum.exception.AppException;
import machinum.release.Release.ReleaseTarget;
import machinum.release.Release.ReleaseView;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.reflect.BeanMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static machinum.util.Util.typedParse;

@Slf4j
@RequiredArgsConstructor
public class ReleaseRepository {

    private final Jdbi jdbi;
    private final ObjectMapper mapper;

    public Optional<Release> findById(String id) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        SELECT r0.*, rt0.action_type as release_action_type 
                        FROM releases r0
                        LEFT JOIN release_targets rt0 on rt0.id = r0.release_target_id
                        WHERE r0.id = :id
                        """)
                .bind("id", id)
                .mapToBean(Release.class)
                .findFirst());
    }

    public List<Release> findByTargetId(String releaseTargetId) {
        return jdbi.withHandle(handle -> handle.createQuery("SELECT * FROM releases WHERE release_target_id = :releaseTargetId")
                .bind("releaseTargetId", releaseTargetId)
                .mapToBean(Release.class)
                .list());
    }

    public List<Release> findAllToExecute() {
        return jdbi.withHandle(handle -> handle.createQuery("""
                SELECT r0.*
                FROM releases r0 
                LEFT JOIN release_targets rt0 ON rt0.id = r0.release_target_id 
                WHERE r0.status IN ('DRAFT', 'MANUAL_ACTION_REQUIRED') 
                AND rt0.enabled IS TRUE
                ORDER BY r0.date, rt0.action_type""")
                .mapToBean(Release.class)
                .list());
    }

    @SneakyThrows
    public void create(List<Release> releases) {
        jdbi.withHandle(handle -> {
            for (Release release : releases) {
                handle.createUpdate("""
                                    INSERT INTO releases (date, release_target_id, chapters, status, metadata, created_at, updated_at) 
                                    VALUES (:date, :releaseTargetId, :chapters, :status, CAST(:metadataString AS JSON), :createdAt, :updatedAt) 
                                    RETURNING id
                                """)
                        .bindBean(release)
                        .bind("metadataString", mapper.writeValueAsString(release.getMetadata()))
                        .executeAndReturnGeneratedKeys("id")
                        .mapTo(String.class)
                        .one();
            }

            return null;
        });
    }

    @SneakyThrows
    public String create(Release release) {
        return jdbi.withHandle(handle -> handle.createUpdate("""
                            INSERT INTO releases (date, release_target_id, chapters, status, metadata, created_at, updated_at) 
                            VALUES (:date, :releaseTargetId, :chapters, :status, CAST(:metadataString AS JSON), :createdAt, :updatedAt) 
                            RETURNING id
                        """)
                .bindBean(release)
                .bind("metadataString", mapper.writeValueAsString(release.getMetadata()))
                .executeAndReturnGeneratedKeys("id")
                .mapTo(String.class)
                .one());
    }

    @SneakyThrows
    public boolean update(Release release) {
        return jdbi.withHandle(handle -> handle.createUpdate("""
                            UPDATE releases SET 
                                date = :date, 
                                chapters = :chapters, 
                                status = :status,
                                metadata = CAST(:metadataString AS JSON), 
                                updated_at = :updatedAtTime 
                            WHERE id = :id
                        """)
                .bind("id", release.getId())
                .bind("metadataString", mapper.writeValueAsString(release.getMetadata()))
                .bind("updatedAtTime", LocalDateTime.now())
                .bindBean(release)
                .execute() > 0);
    }

    public boolean delete(String id) {
        return jdbi.withHandle(handle -> handle.createUpdate("DELETE FROM releases WHERE id = :id")
                .bind("id", id)
                .execute() > 0);
    }

    public List<Release> findByBookId(String bookId) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        SELECT rt0.action_type as release_action_type, r0.* 
                        FROM release_targets rt0 
                        LEFT JOIN releases r0 ON r0.release_target_id = rt0.id 
                        WHERE rt0.book_id = :bookId
                        """)
                .bind("bookId", bookId)
                .mapToBean(Release.class)
                .list());
    }

    @SneakyThrows
    public boolean markAsExecuted(String releaseId) {
        return jdbi.withHandle(handle -> handle.createUpdate("""
                            UPDATE releases SET 
                                status = 'EXECUTED',
                                updated_at = :updatedAtTime 
                            WHERE id = :id
                        """)
                .bind("id", releaseId)
                .bind("updatedAtTime", LocalDateTime.now())
                .execute() > 0);
    }

    public PositionInfo findReleasePosition(String releaseId) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                                WITH data AS ( 
                                    SELECT 
                                        *, 
                                        row_number() OVER (ORDER BY date ASC) AS row_num 
                                    FROM ( 
                                        SELECT 
                                            r0.* 
                                        FROM 
                                            releases r0 
                                        WHERE 
                                            r0.release_target_id IN ( 
                                                SELECT r1.release_target_id FROM releases r1 WHERE r1.id = :id
                                            ) 
                                    ) temp 
                               )
                               SELECT 
                                   COALESCE(
                                       CASE 
                                        WHEN row_num = (SELECT MIN(row_num) FROM data) THEN 0 
                                        WHEN row_num = (SELECT MAX(row_num) FROM data) THEN 2 
                                       ELSE 1 END, 
                                   -1) as item_position,
                                   row_num as index
                               FROM data r2
                               WHERE r2.id = :id
                        """)
                .bind("id", releaseId)
                .mapToBean(PositionInfo.class)
                .one());
    }

    public <T> T findSingleMetadata(String releaseTargetId, String key) {
        var list = findMetadata(releaseTargetId, key);
        if (!list.isEmpty()) {
            var first = list.getFirst();
            if (first instanceof String s) {
                return (T) typedParse(s);
            } else {
                return (T) first;
            }
        }

        return null;
    }

    public List<Object> findMetadata(String releaseTargetId, String key) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                                SELECT
                                    j0.value
                                FROM
                                    releases r0,
                                    LATERAL json_each_text(r0.metadata) j0
                                WHERE
                                    r0.release_target_id = :releaseTargetId
                                    AND j0.key = :key
                                ORDER BY r0."date" DESC
                        """)
                .bind("releaseTargetId", releaseTargetId)
                .bind("key", key)
                .mapTo(Object.class)
                .list());
    }

    @Slf4j
    @RequiredArgsConstructor
    public static class ReleaseTargetRepository {

        private final Jdbi jdbi;
        private final ObjectMapper mapper;

        public ReleaseTarget getById(String id) {
            return findById(id)
                    .orElseThrow(() -> new AppException("ReleaseTarget for given id is not found: " + id));
        }

        public Optional<ReleaseTarget> findById(String id) {
            return jdbi.withHandle(handle -> handle.createQuery("SELECT * FROM release_targets WHERE id = :id")
                    .bind("id", id)
                    .mapToBean(ReleaseTarget.class)
                    .findFirst());
        }

        public List<ReleaseTarget> findByBookId(String bookId) {
            return jdbi.withHandle(handle -> handle.createQuery("SELECT * FROM release_targets WHERE book_id = :bookId")
                    .bind("bookId", bookId)
                    .mapToBean(ReleaseTarget.class)
                    .list());
        }

        public List<ReleaseView> findReleasesByBookId(String bookId) {
            return jdbi.withHandle(handle -> handle.createQuery("SELECT * FROM book_releases WHERE book_id = :bookId")
                    .bind("bookId", bookId)
                    .map((r, columnNumber, ctx) -> {
                        var releaseTarget = BeanMapper.of(ReleaseTarget.class).map(r, ctx);
                        var releaseView = BeanMapper.of(ReleaseView.class).map(r, ctx);

                        return releaseView.toBuilder()
                                .releaseTarget(releaseTarget)
                                .build();
                    })
                    .list());
        }

        @SneakyThrows
        public String create(ReleaseTarget releaseTarget) {
            return jdbi.withHandle(handle -> handle.createUpdate("""
                                INSERT INTO release_targets (book_id, name, action_type, enabled, metadata, created_at)
                                VALUES (:bookId, :name, :actionType, :enabled, CAST(:metadataString AS JSON), :createdAt)
                                RETURNING id
                            """)
                    .bindBean(releaseTarget)
                    .bind("metadataString", mapper.writeValueAsString(releaseTarget.getMetadata()))
                    .executeAndReturnGeneratedKeys("id")
                    .mapTo(String.class)
                    .one());
        }

        @SneakyThrows
        public boolean update(ReleaseTarget target) {
            return jdbi.withHandle(handle -> handle.createUpdate("""
                            UPDATE
                                release_targets
                            SET
                                book_id = :bookId,
                                "name" = :name,
                                action_type = :actionType,
                                metadata = CAST(:metadataString AS JSON),
                                updated_at = :updatedAtTime,
                                enabled = :enabled
                            WHERE
                                id = :id
                        """)
                    .bind("id", target.getId())
                    .bind("metadataString", mapper.writeValueAsString(target.getMetadata()))
                    .bind("updatedAtTime", LocalDateTime.now())
                    .bindBean(target)
                    .execute() > 0);
        }

        public boolean delete(String id) {
            return jdbi.withHandle(handle -> handle.createUpdate("DELETE FROM release_targets WHERE id = :id")
                    .bind("id", id)
                    .execute() > 0);
        }

    }

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class PositionInfo {

        Integer itemPosition;

        Integer index;

    }

}
