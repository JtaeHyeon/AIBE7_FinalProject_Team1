package org.example.springtestci.product.service;

import java.time.LocalDate;
import org.example.springtestci.product.entity.StorageType;

public record UpdateProductCommand(
    long ownerId,
    long productId,
    String modelName,
    LocalDate purchaseDate,
    String cpuSpec,
    Integer ramGb,
    Integer storageGb,
    StorageType storageType,
    String gpuSpec,
    String displaySpec,
    Short batteryHealthPercent,
    String osName) {}
