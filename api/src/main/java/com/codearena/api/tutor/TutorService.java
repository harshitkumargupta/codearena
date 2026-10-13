package com.codearena.api.tutor;

import com.codearena.api.security.AuthUser;
import com.codearena.api.tutor.dto.ExplainFailureRequest;
import com.codearena.api.tutor.dto.HintRequest;
import com.codearena.api.tutor.dto.TutorResponse;
import java.time.Clock;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Hint and failure-explanation flow: authorize and load context, reserve limits, call the model, apply the code
 * guardrail (one stricter retry, then a fixed fallback), and only for a delivered answer commit the limits and write
 * a hint log. Prompts, code and model output are never logged.
 */
@Service
public class TutorService {

    static final String HINT_FALLBACK = "The tutor could not phrase this hint without showing code, so it is holding "
            + "back. Re-read the constraints, think about which technique or data structure they point to, and ask "
            + "again in a moment.";
    static final String EXPLANATION_FALLBACK = "The tutor could not explain this failure without showing code. "
            + "Re-check the edge cases in the constraints and the sample tests, and ask again in a moment.";
    private static final int MAX_ATTEMPTS = 2;

    private static final Logger log = LoggerFactory.getLogger(TutorService.class);

    private final TutorContextLoader contextLoader;
    private final TutorPromptBuilder promptBuilder;
    private final TutorModel model;
    private final TutorLimiter limiter;
    private final HintLogRepository hintLogRepository;
    private final TutorProperties properties;
    private final Clock clock;

    public TutorService(TutorContextLoader contextLoader, TutorPromptBuilder promptBuilder, TutorModel model,
                        TutorLimiter limiter, HintLogRepository hintLogRepository, TutorProperties properties,
                        Clock clock) {
        this.contextLoader = contextLoader;
        this.promptBuilder = promptBuilder;
        this.model = model;
        this.limiter = limiter;
        this.hintLogRepository = hintLogRepository;
        this.properties = properties;
        this.clock = clock;
    }

    public TutorResponse hint(AuthUser user, HintRequest request) {
        TutorContext context = contextLoader.forHint(user, request);
        int level = request.level();
        int allowedCodeLines = level == 3 ? properties.maxCodeBlockLines() : 0;
        return answer(user, context, HintKind.HINT, level, allowedCodeLines,
                strict -> promptBuilder.hint(context, level, strict), HINT_FALLBACK);
    }

    public TutorResponse explainFailure(AuthUser user, ExplainFailureRequest request) {
        TutorContext context = contextLoader.forExplanation(user, request);
        return answer(user, context, HintKind.EXPLANATION, null, properties.maxCodeBlockLines(),
                strict -> promptBuilder.explanation(context, strict), EXPLANATION_FALLBACK);
    }

    private TutorResponse answer(AuthUser user, TutorContext context, HintKind kind, Integer level,
                                 int allowedCodeLines, Function<Boolean, TutorPrompt> prompts, String fallback) {
        long started = System.nanoTime();
        try (TutorReservation reservation = limiter.reserve(user.id(), context.problemId(),
                kind == HintKind.HINT && level == 3)) {
            Optional<String> text = generateWithinRules(kind, level, allowedCodeLines, prompts);
            long ms = (System.nanoTime() - started) / 1_000_000;
            if (text.isEmpty()) {
                log.info("Tutor {} level {} for user {} problem {}: fallback after {} attempts ({} ms)", kind, level,
                        user.id(), context.problemId(), MAX_ATTEMPTS, ms);
                return new TutorResponse(kind, level, fallback, true);
            }
            reservation.commit();
            hintLogRepository.save(new HintLog(user.id(), context.problemId(), context.submissionId(), kind, level,
                    clock.instant()));
            log.info("Tutor {} level {} delivered to user {} for problem {} ({} chars, {} ms)", kind, level, user.id(),
                    context.problemId(), text.get().length(), ms);
            return new TutorResponse(kind, level, text.get(), false);
        }
    }

    private Optional<String> generateWithinRules(HintKind kind, Integer level, int allowedCodeLines,
                                                 Function<Boolean, TutorPrompt> prompts) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String text = model.generate(prompts.apply(attempt > 0));
            Optional<String> violation = CodeBlockGuardrail.violation(text, allowedCodeLines);
            if (violation.isEmpty()) {
                return Optional.of(text);
            }
            log.info("Tutor {} level {} answer rejected by guardrail on attempt {}: {}", kind, level, attempt + 1,
                    violation.get());
        }
        return Optional.empty();
    }
}
