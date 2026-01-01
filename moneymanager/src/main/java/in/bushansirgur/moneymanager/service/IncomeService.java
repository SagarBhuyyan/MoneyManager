package in.bushansirgur.moneymanager.service;

import in.bushansirgur.moneymanager.dto.IncomeDTO;
import in.bushansirgur.moneymanager.entity.CategoryEntity;
import in.bushansirgur.moneymanager.entity.IncomeEntity;
import in.bushansirgur.moneymanager.entity.ProfileEntity;
import in.bushansirgur.moneymanager.repository.CategoryRepository;
import in.bushansirgur.moneymanager.repository.IncomeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IncomeService {

    private final IncomeRepository incomeRepository;
    private final CategoryRepository categoryRepository;
    private final ProfileService profileService;

    // ✅ KEEP ONLY THIS addIncome METHOD (the flexible one)
    public IncomeDTO addIncome(IncomeDTO dto) {
        ProfileEntity profile = profileService.getCurrentProfile();
        CategoryEntity category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found"));

        // FLEXIBLE VALIDATION: Accept both "income" and "incomes"
        String categoryType = category.getType();
        if (categoryType != null) {
            categoryType = categoryType.toLowerCase();
            boolean isValidIncomeType = "income".equals(categoryType) || 
                                       "incomes".equals(categoryType);
            
            if (!isValidIncomeType) {
                throw new RuntimeException("Category must be of type 'income' or 'incomes', but found: " + category.getType());
            }
        } else {
            throw new RuntimeException("Category type is null for category: " + category.getName());
        }

        IncomeEntity newIncome = toEntity(dto, profile, category);
        IncomeEntity savedIncome = incomeRepository.save(newIncome);
        return toDTO(savedIncome);
    }

    // ❌ REMOVE THIS DUPLICATE METHOD (the one below)
    /*
    // Add new income
    public IncomeDTO addIncome(IncomeDTO dto) {
        ProfileEntity profile = profileService.getCurrentProfile();
        CategoryEntity category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found"));

        // Validate category type is income
        if (!"income".equalsIgnoreCase(category.getType())) {
            throw new RuntimeException("Category must be of type 'income'");
        }

        IncomeEntity newIncome = toEntity(dto, profile, category);
        IncomeEntity savedIncome = incomeRepository.save(newIncome);
        return toDTO(savedIncome);
    }
    */

    // Get all incomes for current user
    public List<IncomeDTO> getAllIncomesForCurrentUser() {
        ProfileEntity profile = profileService.getCurrentProfile();
        List<IncomeEntity> incomes = incomeRepository.findByProfileIdOrderByDateDesc(profile.getId());
        return incomes.stream().map(this::toDTO).toList();
    }

    // ✅ OPTIONAL: Add this method if you want to use getAllIncomes() in controller
    public List<IncomeDTO> getAllIncomes() {
        return getAllIncomesForCurrentUser();
    }

    // Get current month incomes
    public List<IncomeDTO> getCurrentMonthIncomesForCurrentUser() {
        ProfileEntity profile = profileService.getCurrentProfile();
        LocalDate now = LocalDate.now();
        LocalDate startDate = now.withDayOfMonth(1);
        LocalDate endDate = now.withDayOfMonth(now.lengthOfMonth());
        List<IncomeEntity> list = incomeRepository.findByProfileIdAndDateBetween(profile.getId(), startDate, endDate);
        return list.stream().map(this::toDTO).toList();
    }

    // Get latest 5 incomes
    public List<IncomeDTO> getLatest5IncomesForCurrentUser() {
        ProfileEntity profile = profileService.getCurrentProfile();
        List<IncomeEntity> list = incomeRepository.findTop5ByProfileIdOrderByDateDesc(profile.getId());
        return list.stream().map(this::toDTO).toList();
    }

    // Get total income amount
    public BigDecimal getTotalIncomeForCurrentUser() {
        ProfileEntity profile = profileService.getCurrentProfile();
        BigDecimal total = incomeRepository.findTotalIncomeByProfileId(profile.getId());
        return total != null ? total : BigDecimal.ZERO;
    }

    // Delete income
    public void deleteIncome(Long incomeId) {
        ProfileEntity profile = profileService.getCurrentProfile();
        IncomeEntity entity = incomeRepository.findById(incomeId)
                .orElseThrow(() -> new RuntimeException("Income not found"));

        if (!entity.getProfile().getId().equals(profile.getId())) {
            throw new RuntimeException("Unauthorized to delete this income");
        }

        incomeRepository.delete(entity);
    }

    // Filter incomes
    public List<IncomeDTO> filterIncomes(LocalDate startDate, LocalDate endDate, String keyword, Sort sort) {
        ProfileEntity profile = profileService.getCurrentProfile();
        
        // Set default dates if not provided
        if (startDate == null) {
            startDate = LocalDate.now().withDayOfMonth(1);
        }
        if (endDate == null) {
            endDate = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
        }
        
        // Get incomes for the date range
        List<IncomeEntity> incomes = incomeRepository.findByProfileIdAndDateBetween(profile.getId(), startDate, endDate);
        
        // Filter by keyword if provided
        if (keyword != null && !keyword.trim().isEmpty()) {
            String searchTerm = keyword.trim().toLowerCase();
            incomes = incomes.stream()
                .filter(income -> income.getName().toLowerCase().contains(searchTerm))
                .toList();
        }
        
        // Apply sorting
        if (sort != null && sort.isSorted()) {
            java.util.Comparator<IncomeEntity> comparator = null;
            
            for (org.springframework.data.domain.Sort.Order order : sort) {
                switch (order.getProperty()) {
                    case "date":
                        comparator = order.isAscending() ? 
                            java.util.Comparator.comparing(IncomeEntity::getDate) :
                            java.util.Comparator.comparing(IncomeEntity::getDate).reversed();
                        break;
                    case "amount":
                        comparator = order.isAscending() ? 
                            java.util.Comparator.comparing(IncomeEntity::getAmount) :
                            java.util.Comparator.comparing(IncomeEntity::getAmount).reversed();
                        break;
                    case "name":
                        comparator = order.isAscending() ? 
                            java.util.Comparator.comparing(IncomeEntity::getName) :
                            java.util.Comparator.comparing(IncomeEntity::getName).reversed();
                        break;
                }
            }
            
            if (comparator != null) {
                incomes = incomes.stream().sorted(comparator).toList();
            }
        } else {
            // Default sort by date descending
            incomes = incomes.stream()
                .sorted(java.util.Comparator.comparing(IncomeEntity::getDate).reversed())
                .toList();
        }
        
        return incomes.stream().map(this::toDTO).toList();
    }

    // Helper methods
    private IncomeEntity toEntity(IncomeDTO dto, ProfileEntity profile, CategoryEntity category) {
        return IncomeEntity.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .amount(dto.getAmount())
                .date(dto.getDate() != null ? dto.getDate() : LocalDate.now())
                .icon(dto.getIcon())
                .profile(profile)
                .category(category)
                .build();
    }

    private IncomeDTO toDTO(IncomeEntity entity) {
        return IncomeDTO.builder()
                .id(entity.getId())
                .profileId(entity.getProfile().getId())
                .categoryId(entity.getCategory().getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .amount(entity.getAmount())
                .date(entity.getDate())
                .icon(entity.getIcon())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .categoryName(entity.getCategory().getName())
                .categoryType(entity.getCategory().getType())
                .build();
    }
}