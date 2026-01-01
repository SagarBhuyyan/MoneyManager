// Add these methods to ExpenseRepository.java
package in.bushansirgur.moneymanager.repository;

import in.bushansirgur.moneymanager.entity.ExpenseEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<ExpenseEntity, Long> {

    List<ExpenseEntity> findByProfileIdOrderByDateDesc(Long profileId);

    List<ExpenseEntity> findTop5ByProfileIdOrderByDateDesc(Long profileId);

    @Query("SELECT SUM(e.amount) FROM ExpenseEntity e WHERE e.profile.id = :profileId")
    BigDecimal findTotalExpenseByProfileId(@Param("profileId") Long profileId);

    List<ExpenseEntity> findByProfileIdAndDateBetweenAndNameContainingIgnoreCase(
            Long profileId,
            LocalDate startDate,
            LocalDate endDate,
            String keyword
    );

    List<ExpenseEntity> findByProfileIdAndDateBetween(Long profileId, LocalDate startDate, LocalDate endDate);

    List<ExpenseEntity> findByProfileIdAndDate(Long profileId, LocalDate date);
    
    List<ExpenseEntity> findByProfileIdAndDateBetweenOrderByDateDesc(Long profileId, LocalDate startDate, LocalDate endDate);
    
    List<ExpenseEntity> findByProfileIdAndDateBetweenOrderByDateAsc(Long profileId, LocalDate startDate, LocalDate endDate);
    
    List<ExpenseEntity> findByProfileIdAndDateBetweenOrderByAmountDesc(Long profileId, LocalDate startDate, LocalDate endDate);
    
    List<ExpenseEntity> findByProfileIdAndDateBetweenOrderByAmountAsc(Long profileId, LocalDate startDate, LocalDate endDate);
    
    // Add this method to ExpenseRepository.java
    List<ExpenseEntity> findByProfileId(Long profileId);
    // ✅ ADD THESE METHODS FOR AI ANALYSIS
    List<ExpenseEntity> findByProfileIdAndDateAfter(Long profileId, LocalDate date);
    
    @Query("SELECT e FROM ExpenseEntity e WHERE e.profile.id = :profileId AND e.date >= :date ORDER BY e.date DESC")
    List<ExpenseEntity> findByProfileIdAndDateAfterOrderByDateDesc(
        @Param("profileId") Long profileId, 
        @Param("date") LocalDate date
    );
}