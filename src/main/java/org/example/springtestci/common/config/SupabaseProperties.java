package org.example.springtestci.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** Supabase 연결 정보. url은 프로젝트 주소(https://xxxx.supabase.co), secret은 서버 전용 비밀 키다. */
@ConfigurationProperties(prefix = "app.supabase")
public record SupabaseProperties(String url, String secret) {

  /** 주소와 비밀 키가 모두 있고 올바른 형식인지 확인한다. */
  public boolean configured() {
    return StringUtils.hasText(url)
        && StringUtils.hasText(secret)
        && (url.strip().startsWith("https://") || url.strip().startsWith("http://"));
  }

  /** 끝의 슬래시나 /rest/v1 이 붙어 있어도 프로젝트 주소만 남긴다. */
  public String baseUrl() {
    return url.strip().replaceAll("(/rest/v1)?/*$", "");
  }
}
