package org.example.springtestci.product.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.common.storage.FileStorage;
import org.example.springtestci.product.dto.CreateProductRequest;
import org.example.springtestci.product.dto.ProductResponse;
import org.example.springtestci.product.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 노트북 제품 등록·조회와 이미지 업로드. 모든 조회·변경은 제품의 현재 소유자만 할 수 있다. */
@Service
public class ProductService {

  static final int MAX_IMAGES_PER_PRODUCT = 10;

  private static final Logger log = LoggerFactory.getLogger(ProductService.class);

  private final ProductRepository productRepository;
  private final SerialNumberProtector serialNumberProtector;
  private final ProductImageValidator imageValidator;
  private final ObjectProvider<FileStorage> fileStorage;

  public ProductService(
      ProductRepository productRepository,
      SerialNumberProtector serialNumberProtector,
      ProductImageValidator imageValidator,
      ObjectProvider<FileStorage> fileStorage) {
    this.productRepository = productRepository;
    this.serialNumberProtector = serialNumberProtector;
    this.imageValidator = imageValidator;
    this.fileStorage = fileStorage;
  }

  @Transactional
  public ProductResponse create(long ownerId, CreateProductRequest request) {
    String fingerprint = serialNumberProtector.fingerprint(request.serialNumber());
    if (productRepository.existsBySerialFingerprint(fingerprint)) {
      throw new CommonException(ErrorCode.PRODUCT_DUPLICATE_SERIAL);
    }
    long productId;
    try {
      productId =
          productRepository.insertProduct(
              ownerId, request, fingerprint, serialNumberProtector.encrypt(request.serialNumber()));
    } catch (DataIntegrityViolationException exception) {
      // 동시 등록으로 사전 검사를 통과한 경우 DB 유니크 제약에서 걸러진다.
      throw new CommonException(ErrorCode.PRODUCT_DUPLICATE_SERIAL);
    }
    productRepository.insertLaptopDetails(productId, request.specifications());
    return get(ownerId, productId);
  }

  @Transactional(readOnly = true)
  public List<ProductResponse> listMine(long ownerId) {
    return productRepository.findAllOwned(ownerId).stream()
        .map(product -> withImages(product))
        .toList();
  }

  @Transactional(readOnly = true)
  public ProductResponse get(long ownerId, long productId) {
    return productRepository
        .findOwned(productId, ownerId)
        .map(this::withImages)
        .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));
  }

  /** 소유자만 올릴 수 있다. 다른 사람의 제품이 존재하는지 알려 주지 않으려고 404로 응답한다. */
  @Transactional
  public ProductResponse addImage(
      long ownerId, long productId, String filename, String contentType, byte[] content) {
    if (!productRepository.lockOwned(productId, ownerId)) {
      throw new CommonException(ErrorCode.RESOURCE_NOT_FOUND);
    }
    ProductImageValidator.ImageKind kind = imageValidator.validate(filename, contentType, content);
    if (productRepository.countImages(productId) >= MAX_IMAGES_PER_PRODUCT) {
      throw new CommonException(ErrorCode.PRODUCT_IMAGE_LIMIT_EXCEEDED);
    }

    String storageKey = store(productId, kind.extension(), content);
    try {
      productRepository.insertImage(productId, storageKey, kind.contentType());
    } catch (RuntimeException exception) {
      deleteStoredFile(storageKey);
      throw exception;
    }
    return get(ownerId, productId);
  }

  private ProductResponse withImages(ProductResponse product) {
    List<ProductResponse.Image> images =
        productRepository.findImageIds(product.productId()).stream()
            .map(id -> new ProductResponse.Image(id, imageUrl(product.productId(), id)))
            .toList();
    return product.withImages(images);
  }

  /** 초안 기능이 제공하는 소유자 전용 이미지 조회 경로를 그대로 쓴다. */
  private static String imageUrl(long productId, long imageId) {
    return "/api/v1/products/" + productId + "/listing-draft/images/" + imageId;
  }

  /** 저장소 구현이 없는 환경(운영 프로필 등)에서도 앱은 기동하고, 파일 기능만 503으로 응답한다. */
  private FileStorage storage() {
    FileStorage storage = fileStorage.getIfAvailable();
    if (storage == null) {
      throw new CommonException(ErrorCode.DOCUMENT_STORAGE_UNAVAILABLE);
    }
    return storage;
  }

  private String store(long productId, String extension, byte[] content) {
    try {
      return storage()
          .store("product-images/" + productId, extension, new ByteArrayInputStream(content));
    } catch (IOException exception) {
      log.error("Failed to store product image productId={}", productId);
      throw new CommonException(ErrorCode.INTERNAL_ERROR);
    }
  }

  private void deleteStoredFile(String storageKey) {
    try {
      storage().delete(storageKey);
    } catch (IOException | RuntimeException exception) {
      log.warn("Failed to delete stored product image");
    }
  }
}
