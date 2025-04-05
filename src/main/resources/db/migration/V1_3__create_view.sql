drop view if exists book_releases;
create or replace view book_releases as
with data as (
	select
		rt0.id,
		rt0.book_id,
		rt0."name",
		rt0.metadata,
		rt0.created_at,
		r0.release_target_id,
		r0."date",
		r0.chapters,
		r0.executed
	from
		release_targets rt0
	left join releases r0 on r0.release_target_id  = rt0.id
),
main_report as (
	select
		row_number() OVER (PARTITION BY d2.book_id, d2.name ORDER BY d2.created_at DESC) id_num,
		sum(1) OVER (PARTITION BY d2.book_id, d2.name ORDER BY d2.created_at DESC) releases_count,
		sum(d2.chapters) OVER (PARTITION BY d2.book_id, d2.name ORDER BY d2.created_at DESC) chapters_count,
		min(d2.date) OVER (PARTITION BY d2.book_id, d2.name ORDER BY d2.created_at DESC) min_date,
		max(d2.date) OVER (PARTITION BY d2.book_id, d2.name ORDER BY d2.created_at DESC) max_date,
		d2.id,
		d2.book_id,
		d2."name",
		d2.metadata,
		d2.created_at,
		d2.release_target_id,
		d2."date",
		d2.chapters,
		d2.executed
	from data d2
),
next_releases_report as (
	select
		row_number() OVER (PARTITION BY d1.book_id, d1.name ORDER BY d1.created_at DESC) id_num,
		min(d1.date) OVER (PARTITION BY d1.book_id, d1.name ORDER BY d1.created_at DESC) next_release,
		d1.id
	from data d1
	where d1.executed = false
)
select 
	d3.id,
	d3.book_id,
	d3."name",
	d3.metadata,
	d3.created_at,
	d3.chapters_count,
	d3.releases_count,
	(d3.max_date - d3.min_date) as releases_days,
	d4.next_release
from main_report d3
left join next_releases_report d4 on d4.id = d3.id
where d3.id_num = 1 and d4.id_num = 1
order by d3.created_at, d3.name