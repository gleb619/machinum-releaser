DROP VIEW IF EXISTS book_releases;
CREATE OR REPLACE VIEW book_releases AS
WITH data AS (
 SELECT rt0.id,
    rt0.book_id,
    rt0.name,
    rt0.metadata,
    rt0.created_at,
    r0.release_target_id,
    r0.date,
    r0.chapters,
    r0.executed
   FROM release_targets rt0
     LEFT JOIN releases r0 ON r0.release_target_id::text = rt0.id::text
), main_report AS (
 SELECT row_number() OVER (PARTITION BY d2.id ORDER BY d2.created_at DESC) AS id_num,
    COUNT(d2.id) OVER (PARTITION BY d2.id ORDER BY d2.created_at DESC) AS releases_count,
    SUM(d2.chapters) OVER (PARTITION BY d2.id ORDER BY d2.created_at DESC) AS chapters_count,
    MIN(d2.date) OVER (PARTITION BY d2.id ORDER BY d2.created_at DESC) AS min_date,
    MAX(d2.date) OVER (PARTITION BY d2.id ORDER BY d2.created_at DESC) AS max_date,
    d2.id,
    d2.book_id,
    d2.name,
    d2.metadata,
    d2.created_at,
    d2.release_target_id,
    d2.date,
    d2.chapters,
    d2.executed
   FROM data d2
), next_releases_report AS (
 SELECT row_number() OVER (PARTITION BY d1.id ORDER BY d1.created_at DESC) AS id_num,
    MIN(d1.date) OVER (PARTITION BY d1.id ORDER BY d1.created_at DESC) AS next_release,
    d1.id
   FROM data d1
  WHERE d1.executed = false
)
 SELECT d3.id,
    d3.book_id,
    d3.name,
    d3.metadata,
    d3.created_at,
    d3.chapters_count,
    d3.releases_count,
    d3.max_date - d3.min_date AS releases_days,
    d4.next_release
   FROM main_report d3
     LEFT JOIN next_releases_report d4 ON d4.id = d3.id
  WHERE d3.id_num = 1 AND d4.id_num = 1
  ORDER BY d3.created_at, d3.name;