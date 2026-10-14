package org.example.springtestci.product.service;

import java.time.LocalDate;
import org.example.springtestci.product.entity.StorageType;

public record RegisterProductCommand(
    long ownerId,
    String title,
    String manufacturer,
    String modelName,
    String serialNumber,
    LocalDate purchaseDate,
    LocalDate warrantyExpiresAt,
    String cpuSpec,
    Integer ramGb,
    Integer storageGb,
    StorageType storageType,
    String gpuSpec,
    String displaySpec,
    Short batteryHealthPercent,
    String osName) {}
