package machinum.release;

import io.avaje.validation.Validator;
import io.jooby.Context;
import io.jooby.Jooby;
import io.jooby.StatusCode;
import io.jooby.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.exception.AppException;
import machinum.release.Release.ReleaseView;
import machinum.release.ReleaseRepository.ReleaseTargetRepository;
import machinum.scheduler.Scheduler;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Path("/api")
@RequiredArgsConstructor
public class ReleaseController {

    private final ReleaseRepository repository;
    private final ReleaseTargetRepository targetRepository;
    private final Validator validator;
    private final ReleaseScheduleGenerator generator;
    private final Scheduler scheduler;

    @POST("/books/{bookId}/generate-release")
    public void generateRelease(@PathParam("bookId") String bookId,
                                @QueryParam("preview") Boolean preview,
                                Context ctx,
                                ReleaseScheduleRequest settings) {
        validator.validate(settings);

        if(Boolean.TRUE.equals(preview)) {
            ctx.setResponseCode(StatusCode.ACCEPTED);
            ctx.render(generator.generate("-1", settings));
        } else {
            var target = generator.createTarget(bookId, settings);
            var targetId = targetRepository.create(target);
            try {
                var schedule = generator.generate(targetId, settings);
                repository.create(schedule);

                ctx.setResponseCode(StatusCode.OK);
            } catch (Exception e) {
                targetRepository.delete(targetId);
                throw new AppException("Can't generate schedule", e);
            }
        }
    }

    @GET("/books/{bookId}/releases")
    public List<ReleaseView> getBookReleases(@PathParam("bookId") String bookId, Context ctx) {
        return targetRepository.findReleasesByBookId(bookId);
    }

    @GET("/books/{bookId}/schedule")
    public List<Release> getReleaseSchedule(@PathParam("bookId") String bookId, Context ctx) {
        return repository.findByBookId(bookId);
    }

    @DELETE("/releases/{id}")
    public StatusCode deleteRelease(@PathParam String id, Context ctx) {
        var result = targetRepository.delete(id);
        return result ? StatusCode.NO_CONTENT : StatusCode.NOT_FOUND;
    }

    @POST("/releases/{id}/execute")
    public void createRelease(@PathParam String id, Context ctx) {
        var value = repository.findById(id);
        value.ifPresent(release ->
                scheduler.executeAsync(release.copy(b -> b.date(LocalDate.now()))));

        ctx.setResponseCode(value.isEmpty() ? StatusCode.NOT_FOUND : StatusCode.OK);
    }

    /*
    @POST("/releases")
    public Release createRelease(Context ctx, Release release) {
        var repository = ctx.require(ReleaseRepository.class);

        release.setCreatedAt(LocalDateTime.now());
        release.setUpdatedAt(LocalDateTime.now());
        var result = repository.create(release);

        return Release.builder()
                .id(result)
                .build();
    }

    @PUT("/releases/{id}")
    public void updateRelease(@PathParam("id") String id, Context ctx, Release release) {
        if(!release.getId().equals(id)) {
            log.error("Malicious operation: {} <> {}", id, release.getId());
            throw new StatusCodeException(StatusCode.FORBIDDEN);
        }

        var repository = ctx.require(ReleaseRepository.class);

        release.setId(id);
        release.setUpdatedAt(LocalDateTime.now());
        repository.update(release);

        ctx.setResponseCode(StatusCode.OK);
    }

    @GET("/releases/{id}")
    public Release getRelease(@PathParam String id, Context ctx) {
        var repository = ctx.require(ReleaseRepository.class);

        return repository.findById(id)
                .orElseThrow(() -> new StatusCodeException(StatusCode.NOT_FOUND));
    }

    @POST("/books/{bookId}/targets")
    public ReleaseTarget createTarget(@PathParam("bookId") String bookId, Context ctx, ReleaseTarget target) {
        var repository = ctx.require(ReleaseTargetRepository.class);
        target.setBookId(bookId);
        target.setCreatedAt(LocalDateTime.now());
        repository.create(target);

        return target;
    }

    @DELETE("/targets/{id}")
    public StatusCode deleteReleaseTarget(@PathParam String id, Context ctx) {
        var repository = ctx.require(ReleaseTargetRepository.class);

        var result = repository.delete(id);
        return result ? StatusCode.NO_CONTENT : StatusCode.NOT_FOUND;
    }
    */

    public static ReleaseController_ releaseController(Jooby jooby) {
        return new ReleaseController_(jooby.require(ReleaseRepository.class),
                jooby.require(ReleaseTargetRepository.class),
                jooby.require(Validator.class),
                jooby.require(ReleaseScheduleGenerator.class),
                jooby.require(Scheduler.class));
    }

}
