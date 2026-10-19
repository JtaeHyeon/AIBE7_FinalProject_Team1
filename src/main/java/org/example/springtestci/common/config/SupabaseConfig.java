package org.example.springtestci.common.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SupabaseProperties.class)
public class SupabaseConfig {

  @Bean
  RestClient supabaseAuthRestClient(SupabaseProperties properties) {
    JdkClientHttpRequestFactory requestFactory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    requestFactory.setReadTimeout(Duration.ofSeconds(10));

    RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
    if (properties.configured()) {
      builder.baseUrl("%s/auth/v1".formatted(properties.baseUrl()));
      builder.defaultHeader("apikey", properties.secret());
      // 새 형식 키(sb_secret_...)는 JWT가 아니라 Authorization 헤더에 보내지 않는다.
      if (properties.secret().startsWith("eyJ")) {
        builder.defaultHeader(
            HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(properties.secret()));
      }
    }
    return builder.build();
  }
}
