// Add these methods to IncomeRepository.java
package in.bushansirgur.moneymanager.repository;

import in.bushansirgur.moneymanager.entity.IncomeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface IncomeRepository extends JpaRepository<IncomeEntity, Long> {
    
    List<IncomeEntity> findByProfileId(Long profileId);
    
    List<IncomeEntity> findByProfileIdOrderByDateDesc(Long profileId);
    
    List<IncomeEntity> findByProfileIdAndDateBetween(Long profileId, LocalDate startDate, LocalDate endDate);
    
    List<IncomeEntity> findByProfileIdAndDate(Long profileId, LocalDate date);
    
    List<IncomeEntity> findTop5ByProfileIdOrderByDateDesc(Long profileId);
    
    @Query("SELECT SUM(i.amount) FROM IncomeEntity i WHERE i.profile.id = :profileId")
    BigDecimal findTotalIncomeByProfileId(@Param("profileId") Long profileId);
    
    List<IncomeEntity> findByProfileIdAndCategoryId(Long profileId, Long categoryId);
    
    List<IncomeEntity> findByProfileIdAndDateBetweenAndNameContainingIgnoreCase(
        Long profileId, LocalDate startDate, LocalDate endDate, String keyword);
    
    // ✅ ADD THESE METHODS FOR AI ANALYSIS
    List<IncomeEntity> findByProfileIdAndDateAfter(Long profileId, LocalDate date);
    
    @Query("SELECT i FROM IncomeEntity i WHERE i.profile.id = :profileId AND i.date >= :date ORDER BY i.date DESC")
    List<IncomeEntity> findByProfileIdAndDateAfterOrderByDateDesc(
        @Param("profileId") Long profileId, 
        @Param("date") LocalDate date
    );
}