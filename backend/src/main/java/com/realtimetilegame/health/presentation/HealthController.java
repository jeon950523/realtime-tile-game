package com.realtimetilegame.health.presentation;

import com.realtimetilegame.common.api.ApiResponse;
import com.realtimetilegame.health.application.HealthService;
import com.realtimetilegame.health.presentation.dto.HealthResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping
    public ApiResponse<HealthResponse> health() {
        return ApiResponse.success(healthService.check());
    }
}
