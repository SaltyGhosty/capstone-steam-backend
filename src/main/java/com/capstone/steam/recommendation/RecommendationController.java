package com.capstone.steam.recommendation;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.steam.recommendation.dto.ChatRequest;
import com.capstone.steam.recommendation.dto.ChatResponse;
import com.capstone.steam.security.AppUserPrincipal;

import jakarta.validation.Valid;

/** Chat di raccomandazione. {@code /api/**} richiede già la sessione per default in SecurityConfig. */
@RestController
public class RecommendationController {

    private final RecommendationService recommendations;

    public RecommendationController(RecommendationService recommendations) {
        this.recommendations = recommendations;
    }

    @PostMapping("/api/recommendations/chat")
    public ChatResponse chat(@AuthenticationPrincipal AppUserPrincipal user, @Valid @RequestBody ChatRequest request) {
        return recommendations.chat(user.getId(), request.messages());
    }
}
