package org.example.springtestci.product.service;

import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.product.entity.LaptopDetail;
import org.example.springtestci.product.entity.Product;
import org.example.springtestci.product.repository.LaptopDetailRepository;
import org.example.springtestci.product.repository.ProductRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductRegistrationService {

  private final ProductRepository productRepository;
  private final LaptopDetailRepository laptopDetailRepository;
  private final SerialNumberProtector serialNumberProtector;

  public ProductRegistrationService(
      ProductRepository productRepository,
      LaptopDetailRepository laptopDetailRepository,
      SerialNumberProtector serialNumberProtector) {
    this.productRepository = productRepository;
    this.laptopDetailRepository = laptopDetailRepository;
    this.serialNumberProtector = serialNumberProtector;
  }

  @Transactional
  public RegisteredProduct register(RegisterProductCommand command) {
    ProtectedSerialNumber serialNumber =
        serialNumberProtector.protect(command.manufacturer(), command.serialNumber());
    rejectDuplicate(serialNumber.fingerprint());

    Product product =
        Product.laptop(
            command.ownerId(),
            command.title(),
            serialNumber.normalizedManufacturer(),
            command.modelName(),
            command.purchaseDate(),
            command.warrantyExpiresAt(),
            serialNumber.fingerprint(),
            serialNumber.last4());

    Product savedProduct = saveProduct(product);
    laptopDetailRepository.save(
        new LaptopDetail(
            savedProduct,
            command.cpuSpec(),
            command.ramGb(),
            command.storageGb(),
            command.storageType(),
            command.gpuSpec(),
            command.displaySpec(),
            command.batteryHealthPercent(),
            command.osName()));
    return RegisteredProduct.from(savedProduct);
  }

  private void rejectDuplicate(String fingerprint) {
    if (productRepository.existsBySerialNumberFingerprint(fingerprint)) {
      throw new CommonException(ErrorCode.PRODUCT_ALREADY_REGISTERED);
    }
  }

  private Product saveProduct(Product product) {
    try {
      return productRepository.saveAndFlush(product);
    } catch (DataIntegrityViolationException exception) {
      throw new CommonException(ErrorCode.PRODUCT_ALREADY_REGISTERED);
    }
  }
}
