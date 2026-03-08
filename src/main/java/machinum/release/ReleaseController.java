package machinum.release;

import static machinum.release.Release.ReleaseStatus.DRAFT;
import static machinum.release.Release.ReleaseStatus.EXECUTED;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.avaje.validation.Validator;
import io.jooby.Context;
import io.jooby.Jooby;
import io.jooby.StatusCode;
import io.jooby.annotation.DELETE;
import io.jooby.annotation.GET;
import io.jooby.annotation.PATCH;
import io.jooby.annotation.POST;
import io.jooby.annotation.PUT;
import io.jooby.annotation.Path;
import io.jooby.annotation.PathParam;
import io.jooby.annotation.QueryParam;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.exception.AppException;
import machinum.release.Release.ReleaseTarget;
import machinum.release.Release.ReleaseView;
import machinum.release.ReleaseRepository.ReleaseTargetRepository;
import machinum.scheduler.ActionHandler.ActionType;
import machinum.scheduler.Scheduler;

@Slf4j
@Path("/api")
@RequiredArgsConstructor
public class ReleaseController {

    private final ReleaseRepository repository;
    private final ReleaseTargetRepository targetRepository;
    private final Validator validator;
    private final ReleaseScheduleGenerator generator;
    private final Scheduler scheduler;
    private final ObjectMapper objectMapper;

    @POST("/books/{bookId}/generate-release")
    public void generateRelease(@PathParam("bookId") String bookId,
                                @QueryParam("preview") Boolean preview,
                                Context ctx,
                                ScheduleRequest scheduleRequest) {
        var settings = scheduleRequest.migrate(objectMapper);
        validator.validate(settings);

        if(Boolean.TRUE.equals(preview)) {
            ctx.setResponseCode(StatusCode.ACCEPTED);
            ctx.render(generator.generate("-1", settings));
        } else if(Objects.nonNull(scheduleRequest.releases())) {
            var target = generator.createTarget(bookId, settings);
            var targetId = targetRepository.create(target);

            try {
                scheduleRequest.releases().forEach(r -> r.setReleaseTargetId(targetId));
                repository.create(scheduleRequest.releases());

                ctx.setResponseCode(StatusCode.OK);
            } catch (Exception e) {
                targetRepository.delete(targetId);
                throw new AppException("Can't generate schedule", e);
            }
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

    @GET("/release-targets/{releaseTargetId}/releases")
    public List<Release> getTargetReleases(@PathParam("releaseTargetId") String releaseTargetId, Context ctx) {
        return repository.findByTargetId(releaseTargetId);
    }

    @GET("/release-targets/{releaseTargetId}")
    public ReleaseTarget getReleaseTarget(@PathParam("releaseTargetId") String releaseTargetId, Context ctx) {
        Optional<ReleaseTarget> result = targetRepository.findById(releaseTargetId);

        result.ifPresentOrElse(releaseTarget -> {
            ctx.setResponseCode(StatusCode.OK);
        }, () -> {
            ctx.setResponseCode(StatusCode.NOT_FOUND);
        });

        return result.orElse(null);
    }

    @DELETE("/releases/{id}")
    public StatusCode deleteRelease(@PathParam("id") String id, Context ctx) {
        var result = targetRepository.delete(id);
        return result ? StatusCode.NO_CONTENT : StatusCode.NOT_FOUND;
    }

    @POST("/releases/{id}/execute")
    public void createRelease(@PathParam("id") String id, Context ctx) {
        var value = repository.findById(id);
        value.ifPresent(release ->
                scheduler.executeAsync(release.copy(b -> b.date(LocalDate.now()))));

        ctx.setResponseCode(value.isEmpty() ? StatusCode.NOT_FOUND : StatusCode.OK);
    }

    @PATCH("/releases/{id}/{field}")
    public void changeReleaseExecutedFlag(@PathParam("id") String id,
                                   @PathParam("field") String field,
                                   Context ctx) {
        var result = repository.findById(id).map(releaseFromDb -> {
            if(field.equals("executed")) {
                releaseFromDb.status(releaseFromDb.isExecuted() ? DRAFT : EXECUTED);
            }
            releaseFromDb.setUpdatedAt(LocalDateTime.now());
            return repository.update(releaseFromDb);
        }).orElse(Boolean.FALSE);

        ctx.setResponseCode(result ? StatusCode.NO_CONTENT : StatusCode.NOT_FOUND);
    }

    @PUT("/releases/{id}")
    public void updateRelease(@PathParam("id") String id, Context ctx, Release release) {
        validator.validate(release);
        var result = repository.findById(id).map(releaseFromDb -> {
            releaseFromDb.setDate(release.getDate());
            releaseFromDb.setChapters(release.getChapters());
            releaseFromDb.status(release.status());
            releaseFromDb.setMetadata(release.getMetadata());
            releaseFromDb.setUpdatedAt(LocalDateTime.now());
            return repository.update(releaseFromDb);
        }).orElse(Boolean.FALSE);

        ctx.setResponseCode(result ? StatusCode.NO_CONTENT : StatusCode.NOT_FOUND);
    }

    @PUT("/release-targets/{id}")
    public void updateReleaseTarget(@PathParam("id") String id, Context ctx, ReleaseScheduleRequest request) {
        validator.validate(request.migrate(objectMapper));
        // TODO: Implement updating ReleaseTarget fields from request
        ctx.setResponseCode(StatusCode.NOT_IMPLEMENTED);
    }

    @PATCH("/release-targets/{id}/{field}")
    public void changeTargetExecutedFlag(@PathParam("id") String id,
                                   @PathParam("field") String field,
                                   Context ctx) {
        var result = targetRepository.findById(id).map(targetFromDb -> {
            if(field.equals("enabled")) {
                targetFromDb.setEnabled(!targetFromDb.isEnabled());
            }
            targetFromDb.setUpdatedAt(LocalDateTime.now());
            return targetRepository.update(targetFromDb);
        }).orElse(Boolean.FALSE);

        ctx.setResponseCode(result ? StatusCode.NO_CONTENT : StatusCode.NOT_FOUND);
    }

    @GET("/action-types")
    public List<ActionType> getActionTypes() {
        return List.of(ActionType.values());
    }

    public static ReleaseController_ releaseController(Jooby jooby) {
        return new ReleaseController_(jooby.require(ReleaseRepository.class),
                jooby.require(ReleaseTargetRepository.class),
                jooby.require(Validator.class),
                jooby.require(ReleaseScheduleGenerator.class),
                jooby.require(Scheduler.class),
                jooby.require(ObjectMapper.class)
        );
    }

    public record ScheduleRequest(ReleaseScheduleRequest settings, List<Release> releases) {

        public ReleaseScheduleRequest migrate(ObjectMapper objectMapper) {
            return settings.migrate(objectMapper);
        }

    }

}
