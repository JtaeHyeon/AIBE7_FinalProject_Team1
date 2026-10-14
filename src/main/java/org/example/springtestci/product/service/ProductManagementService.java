package org.example.springtestci.product.service;

import java.util.List;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.product.entity.LaptopDetail;
import org.example.springtestci.product.repository.LaptopDetailRepository;
import org.example.springtestci.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductManagementService {

  private final ProductRepository productRepository;
  private final LaptopDetailRepository laptopDetailRepository;

  public ProductManagementService(
      ProductRepository productRepository, LaptopDetailRepository laptopDetailRepository) {
    this.productRepository = productRepository;
    this.laptopDetailRepository = laptopDetailRepository;
  }

  @Transactional(readOnly = true)
  public List<ProductSummary> findMine(long ownerId) {
    return productRepository
        .findAllByOwnerIdAndArchivedAtIsNullOrderByCreatedAtDescIdDesc(ownerId)
        .stream()
        .map(ProductSummary::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public ProductDetails findMine(long ownerId, long productId) {
    return ProductDetails.from(findOwnedDetail(ownerId, productId));
  }

  @Transactional
  public ProductDetails update(UpdateProductCommand command) {
    LaptopDetail detail = findOwnedDetail(command.ownerId(), command.productId());
    detail.getProduct().updateEditableInformation(command.modelName(), command.purchaseDate());
    detail.updateSpecification(
        command.cpuSpec(),
        command.ramGb(),
        command.storageGb(),
        command.storageType(),
        command.gpuSpec(),
        command.displaySpec(),
        command.batteryHealthPercent(),
        command.osName());
    return ProductDetails.from(detail);
  }

  private LaptopDetail findOwnedDetail(long ownerId, long productId) {
    return laptopDetailRepository
        .findOwnedActiveDetail(productId, ownerId)
        .orElseThrow(() -> new CommonException(ErrorCode.PRODUCT_NOT_FOUND));
  }
}
