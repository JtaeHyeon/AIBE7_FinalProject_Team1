package org.example.springtestci.product.service;

public record ProtectedSerialNumber(
    String normalizedManufacturer, String fingerprint, String last4) {}
