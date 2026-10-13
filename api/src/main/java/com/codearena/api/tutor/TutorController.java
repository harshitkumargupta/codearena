package com.codearena.api.tutor;

import com.codearena.api.security.AuthUser;
import com.codearena.api.tutor.dto.ExplainFailureRequest;
import com.codearena.api.tutor.dto.HintRequest;
import com.codearena.api.tutor.dto.TutorResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tutor")
public class TutorController {

    private final TutorService tutorService;

    public TutorController(TutorService tutorService) {
        this.tutorService = tutorService;
    }

    @PostMapping("/hint")
    public TutorResponse hint(@Valid @RequestBody HintRequest request, @AuthenticationPrincipal AuthUser user) {
        return tutorService.hint(user, request);
    }

    @PostMapping("/explain-failure")
    public TutorResponse explainFailure(@Valid @RequestBody ExplainFailureRequest request,
                                        @AuthenticationPrincipal AuthUser user) {
        return tutorService.explainFailure(user, request);
    }
}
