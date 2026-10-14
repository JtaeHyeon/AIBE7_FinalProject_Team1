package org.example.springtestci.product.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.product.serial")
public record SerialNumberProperties(@NotBlank @Size(min = 32) String fingerprintSecret) {}
