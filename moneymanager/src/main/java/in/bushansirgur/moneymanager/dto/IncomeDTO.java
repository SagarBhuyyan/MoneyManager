package in.bushansirgur.moneymanager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class IncomeDTO {
    private Long id;
    private Long profileId;  // ✅ Make sure this field exists
    private Long categoryId;
    private String categoryName;
    private String categoryType;
    private String name;
    private String description;  // ✅ Make sure this field exists
    private BigDecimal amount;
    private LocalDate date;
    private String icon;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Category details
    
}