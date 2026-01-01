package in.bushansirgur.moneymanager.controller;

import in.bushansirgur.moneymanager.dto.AuthDTO;
import in.bushansirgur.moneymanager.dto.ProfileDTO;
import in.bushansirgur.moneymanager.service.ProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1.0")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:3000")
public class ProfileController {

    private final ProfileService profileService;


    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testEndpoint() {
        log.info("Test endpoint called successfully");
        return ResponseEntity.ok(Map.of(
            "message", "API is working!",
            "timestamp", java.time.LocalDateTime.now().toString(),
            "status", "SUCCESS"
        ));
    }

    // ✅ Register
    @PostMapping("/register")
    public ResponseEntity<?> registerProfile(@RequestBody ProfileDTO profileDTO) {
        try {
            log.info("Registering user: {}", profileDTO.getEmail());
            ProfileDTO registeredProfile = profileService.registerProfile(profileDTO);
            return ResponseEntity.status(HttpStatus.CREATED).body(registeredProfile);
        } catch (Exception e) {
            log.error("Registration failed for email: {}", profileDTO.getEmail(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "Registration failed",
                    "message", e.getMessage()
            ));
        }
    }

    // ✅ Login
    @PostMapping("/login")
public ResponseEntity<Map<String, Object>> login(@RequestBody AuthDTO authDTO) {
    try {
        // ✅ REMOVE this activation check - it's now handled automatically
        // if (!profileService.isAccountActive(authDTO.getEmail())) {
        //     return ResponseEntity.status(HttpStatus.FORBIDDEN).body(...);
        // }
        
        Map<String, Object> response = profileService.authenticateAndGenerateToken(authDTO);
        return ResponseEntity.ok(response);
    } catch (Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "message", e.getMessage()
        ));
    }
}

    // ✅ Account activation
    @GetMapping("/activate")
    public ResponseEntity<String> activateProfile(@RequestParam String token) {
        boolean isActivated = profileService.activateProfile(token);
        if (isActivated) {
            return ResponseEntity.ok("Profile activated successfully");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Activation token not found or already used");
        }
    }

    // ✅ Get logged-in profile (JWT protected)
    @GetMapping("/profile")
    public ResponseEntity<ProfileDTO> getProfile(@RequestParam String email) {
        ProfileDTO profileDTO = profileService.getPublicProfile(email);
        return ResponseEntity.ok(profileDTO);
    }
}
