// ProfileService.java - FIXED VERSION
package in.bushansirgur.moneymanager.service;

import in.bushansirgur.moneymanager.dto.AuthDTO;
import in.bushansirgur.moneymanager.dto.ProfileDTO;
import in.bushansirgur.moneymanager.entity.ProfileEntity;
import in.bushansirgur.moneymanager.repository.ProfileRepository;
import in.bushansirgur.moneymanager.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    
    @Value("${app.activation.url}")
    private String activationURL;

    public ProfileDTO registerProfile(ProfileDTO profileDTO) {
        // Create new profile entity manually (without builder)
        ProfileEntity newProfile = new ProfileEntity();
        newProfile.setFullName(profileDTO.getFullName());
        newProfile.setEmail(profileDTO.getEmail());
        newProfile.setPassword(passwordEncoder.encode(profileDTO.getPassword()));
        newProfile.setProfileImageUrl(profileDTO.getProfileImageUrl());
        newProfile.setActivationToken(UUID.randomUUID().toString());
        newProfile.setIsActive(true); // Auto-activate
        
        ProfileEntity savedProfile = profileRepository.save(newProfile);
        
        // Send welcome email
        String subject = "Welcome to Money Manager!";
        String body = "Your account has been created successfully.";
        
        try {
            emailService.sendEmail(savedProfile.getEmail(), subject, body);
        } catch (Exception e) {
            // Email failure is not critical
        }
        
        return toDTO(savedProfile);
    }

    public ProfileEntity toEntity(ProfileDTO profileDTO) {
        ProfileEntity entity = new ProfileEntity();
        entity.setId(profileDTO.getId());
        entity.setFullName(profileDTO.getFullName());
        entity.setEmail(profileDTO.getEmail());
        entity.setPassword(passwordEncoder.encode(profileDTO.getPassword()));
        entity.setProfileImageUrl(profileDTO.getProfileImageUrl());
        entity.setIsActive(true);
        return entity;
    }

    public ProfileDTO toDTO(ProfileEntity profileEntity) {
        ProfileDTO dto = new ProfileDTO();
        dto.setId(profileEntity.getId());
        dto.setFullName(profileEntity.getFullName());
        dto.setEmail(profileEntity.getEmail());
        dto.setProfileImageUrl(profileEntity.getProfileImageUrl());
        dto.setCreatedAt(profileEntity.getCreatedAt());
        dto.setUpdatedAt(profileEntity.getUpdatedAt());
        return dto;
    }

    public boolean activateProfile(String activationToken) {
        return profileRepository.findByActivationToken(activationToken)
                .map(profile -> {
                    profile.setIsActive(true);
                    profileRepository.save(profile);
                    return true;
                })
                .orElse(false);
    }

    public boolean isAccountActive(String email) {
        return profileRepository.findByEmail(email)
                .map(profile -> {
                    if (profile.getIsActive() == null || !profile.getIsActive()) {
                        profile.setIsActive(true);
                        profileRepository.save(profile);
                        return true;
                    }
                    return profile.getIsActive();
                })
                .orElse(false);
    }

    public ProfileEntity getCurrentProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return profileRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("Profile not found with email: " + authentication.getName()));
    }

    public ProfileDTO getPublicProfile(String email) {
        ProfileEntity currentUser;
        if (email == null) {
            currentUser = getCurrentProfile();
        } else {
            currentUser = profileRepository.findByEmail(email)
                    .orElseThrow(() -> new UsernameNotFoundException("Profile not found with email: " + email));
        }

        ProfileDTO dto = new ProfileDTO();
        dto.setId(currentUser.getId());
        dto.setFullName(currentUser.getFullName());
        dto.setEmail(currentUser.getEmail());
        dto.setProfileImageUrl(currentUser.getProfileImageUrl());
        dto.setCreatedAt(currentUser.getCreatedAt());
        dto.setUpdatedAt(currentUser.getUpdatedAt());
        return dto;
    }

    public Map<String, Object> authenticateAndGenerateToken(AuthDTO authDTO) {
        try {
            // Auto-activate if account exists but not active
            Optional<ProfileEntity> profileOpt = profileRepository.findByEmail(authDTO.getEmail());
            if (profileOpt.isPresent()) {
                ProfileEntity profile = profileOpt.get();
                if (profile.getIsActive() == null || !profile.getIsActive()) {
                    profile.setIsActive(true);
                    profileRepository.save(profile);
                }
            }
            
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(authDTO.getEmail(), authDTO.getPassword())
            );
            
            // Generate JWT token
            String token = jwtUtil.generateToken(authDTO.getEmail());
            return Map.of(
                    "token", token,
                    "user", getPublicProfile(authDTO.getEmail())
            );
        } catch (Exception e) {
            throw new RuntimeException("Invalid email or password");
        }
    }
    
    public boolean activateProfileByEmail(String email) {
        return profileRepository.findByEmail(email)
                .map(profile -> {
                    boolean wasInactive = !profile.getIsActive();
                    profile.setIsActive(true);
                    profileRepository.save(profile);
                    return true;
                })
                .orElse(false);
    }
}