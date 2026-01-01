// ProductService.java - FIXED VERSION
package in.bushansirgur.moneymanager.service;

import in.bushansirgur.moneymanager.dto.ProductDTO;
import in.bushansirgur.moneymanager.entity.CategoryEntity;
import in.bushansirgur.moneymanager.entity.ProductEntity;
import in.bushansirgur.moneymanager.entity.ProfileEntity;
import in.bushansirgur.moneymanager.repository.CategoryRepository;
import in.bushansirgur.moneymanager.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProfileService profileService;

    public ProductDTO addProduct(ProductDTO productDTO) {
        ProfileEntity profile = profileService.getCurrentProfile();
        CategoryEntity category = categoryRepository.findByIdAndProfileId(productDTO.getCategoryId(), profile.getId())
                .orElseThrow(() -> new RuntimeException("Category not found or not accessible"));

        ProductEntity newProduct = toEntity(productDTO, profile, category);
        ProductEntity savedProduct = productRepository.save(newProduct);
        return toDTO(savedProduct);
    }

    public List<ProductDTO> getProductsByCategory(Long categoryId) {
        ProfileEntity profile = profileService.getCurrentProfile();
        List<ProductEntity> products = productRepository.findByCategoryIdAndProfileId(categoryId, profile.getId());
        return products.stream().map(this::toDTO).toList();
    }

    public List<ProductDTO> getAllProductsForCurrentUser() {
        ProfileEntity profile = profileService.getCurrentProfile();
        List<ProductEntity> products = productRepository.findByProfileId(profile.getId());
        return products.stream().map(this::toDTO).toList();
    }

    public ProductDTO updateProduct(Long productId, ProductDTO productDTO) {
        ProfileEntity profile = profileService.getCurrentProfile();
        ProductEntity existingProduct = productRepository.findByIdAndProfileId(productId, profile.getId())
                .orElseThrow(() -> new RuntimeException("Product not found or not accessible"));

        existingProduct.setName(productDTO.getName());
        existingProduct.setDescription(productDTO.getDescription());
        existingProduct.setQuantity(productDTO.getQuantity());
        existingProduct.setUnitPrice(productDTO.getUnitPrice());
        existingProduct.setImageUrl(productDTO.getImageUrl());
        existingProduct.calculateTotalValue();

        ProductEntity updatedProduct = productRepository.save(existingProduct);
        return toDTO(updatedProduct);
    }

    public void deleteProduct(Long productId) {
        ProfileEntity profile = profileService.getCurrentProfile();
        ProductEntity existingProduct = productRepository.findByIdAndProfileId(productId, profile.getId())
                .orElseThrow(() -> new RuntimeException("Product not found or not accessible"));
        productRepository.delete(existingProduct);
    }

    public BigDecimal getTotalProductValueByCategory(Long categoryId) {
        ProfileEntity profile = profileService.getCurrentProfile();
        BigDecimal total = productRepository.findTotalProductValueByCategory(categoryId, profile.getId());
        return total != null ? total : BigDecimal.ZERO;
    }

    public BigDecimal getTotalInventoryValue() {
        ProfileEntity profile = profileService.getCurrentProfile();
        BigDecimal total = productRepository.findTotalInventoryValueByProfile(profile.getId());
        return total != null ? total : BigDecimal.ZERO;
    }

    private ProductEntity toEntity(ProductDTO dto, ProfileEntity profile, CategoryEntity category) {
        ProductEntity entity = new ProductEntity();
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setQuantity(dto.getQuantity());
        entity.setUnitPrice(dto.getUnitPrice());
        entity.setImageUrl(dto.getImageUrl());
        entity.setCategory(category);
        entity.setProfile(profile);
        entity.calculateTotalValue(); // Calculate total value
        return entity;
    }

    private ProductDTO toDTO(ProductEntity entity) {
        ProductDTO dto = new ProductDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setQuantity(entity.getQuantity());
        dto.setUnitPrice(entity.getUnitPrice());
        dto.setImageUrl(entity.getImageUrl());
        dto.setTotalValue(entity.getTotalValue());
        
        if (entity.getCategory() != null) {
            dto.setCategoryId(entity.getCategory().getId());
            dto.setCategoryName(entity.getCategory().getName());
        }
        
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}