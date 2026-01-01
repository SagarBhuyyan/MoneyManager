package in.bushansirgur.moneymanager.controller;

import in.bushansirgur.moneymanager.service.AIService;
import in.bushansirgur.moneymanager.service.ProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1.0/ai")
@RequiredArgsConstructor
@Slf4j
public class AIController {

    private final AIService aiService;
    private final ProfileService profileService;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("service", "AI Financial Assistant");
        response.put("status", "online");
        response.put("timestamp", new Date());
        response.put("endpoints", Map.of(
            "financialAnalysis", "/api/v1.0/ai/financial-analysis",
            "quickInsights", "/api/v1.0/ai/quick-insights",
            "health", "/api/v1.0/ai/health"
        ));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/financial-analysis")
    public ResponseEntity<Map<String, Object>> getFinancialAnalysis() {
        try {
            log.info("Received request for AI financial analysis");
            
            var profile = profileService.getCurrentProfile();
            if (profile == null) {
                log.warn("No authenticated profile found for AI analysis");
                return ResponseEntity.badRequest()
                    .body(Map.of(
                        "success", false,
                        "error", "No authenticated profile found"
                    ));
            }
            
            log.info("Generating AI analysis for profile: {}", profile.getId());
            Map<String, Object> analysis = aiService.getFinancialAnalysis(profile.getId());
            
            return ResponseEntity.ok(analysis);
            
        } catch (Exception e) {
            log.error("Error generating AI analysis", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "success", false,
                    "error", "Failed to generate analysis: " + e.getMessage(),
                    "details", "Please check backend logs for more information"
                ));
        }
    }

    @GetMapping("/quick-insights")
    public ResponseEntity<Map<String, Object>> getQuickInsights() {
        try {
            var profile = profileService.getCurrentProfile();
            if (profile == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "No authenticated profile found"));
            }
            
            // Simple insights without AI
            Map<String, Object> insights = Map.of(
                "message", "Quick insights feature coming soon",
                "profileId", profile.getId(),
                "timestamp", new Date(),
                "tip", "Add more transactions to get better insights",
                "status", "success"
            );
            return ResponseEntity.ok(insights);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to get insights: " + e.getMessage()));
        }
    }
}