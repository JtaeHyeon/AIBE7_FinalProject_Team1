package org.example.springtestci.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.example.springtestci.product.entity.StorageType;
import org.example.springtestci.product.service.RegisterProductCommand;

public record RegisterProductRequest(
    @NotBlank @Size(max = 200) String title,
    @NotBlank @Size(max = 100) String manufacturer,
    @NotBlank @Size(max = 100) String modelName,
    @NotBlank @Size(min = 4, max = 100) String serialNumber,
    LocalDate purchaseDate,
    LocalDate warrantyExpiresAt,
    @NotNull @Valid LaptopSpecification laptop) {

  public RegisterProductCommand toCommand(long ownerId) {
    return new RegisterProductCommand(
        ownerId,
        title,
        manufacturer,
        modelName,
        serialNumber,
        purchaseDate,
        warrantyExpiresAt,
        laptop.cpuSpec(),
        laptop.ramGb(),
        laptop.storageGb(),
        laptop.storageType(),
        laptop.gpuSpec(),
        laptop.displaySpec(),
        laptop.batteryHealthPercent(),
        laptop.osName());
  }

  public record LaptopSpecification(
      @Size(max = 100) String cpuSpec,
      @Min(1) Integer ramGb,
      @Min(1) Integer storageGb,
      StorageType storageType,
      @Size(max = 100) String gpuSpec,
      @Size(max = 100) String displaySpec,
      @Min(0) @Max(100) Short batteryHealthPercent,
      @Size(max = 50) String osName) {}
}
