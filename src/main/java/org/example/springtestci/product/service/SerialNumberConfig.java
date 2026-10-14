package org.example.springtestci.product.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SerialNumberProperties.class)
public class SerialNumberConfig {}
