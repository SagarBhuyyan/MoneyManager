package in.bushansirgur.moneymanager.repository;

import in.bushansirgur.moneymanager.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, Long> {
    
    // Find products by category ID and profile ID
    List<ProductEntity> findByCategoryIdAndProfileId(Long categoryId, Long profileId);
    
    // Find products by profile ID
    List<ProductEntity> findByProfileId(Long profileId);
    
    // Find product by ID and profile ID (for security)
    Optional<ProductEntity> findByIdAndProfileId(Long id, Long profileId);
    
    // Calculate total product value by category and profile
    @Query("SELECT COALESCE(SUM(p.totalValue), 0) FROM ProductEntity p WHERE p.category.id = :categoryId AND p.profile.id = :profileId")
    BigDecimal findTotalProductValueByCategory(@Param("categoryId") Long categoryId, @Param("profileId") Long profileId);
    
    // Calculate total inventory value by profile
    @Query("SELECT COALESCE(SUM(p.totalValue), 0) FROM ProductEntity p WHERE p.profile.id = :profileId")
    BigDecimal findTotalInventoryValueByProfile(@Param("profileId") Long profileId);
    
    // Find products by category
    List<ProductEntity> findByCategoryId(Long categoryId);
}