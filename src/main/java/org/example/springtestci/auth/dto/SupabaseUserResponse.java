package org.example.springtestci.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Supabase Auth 응답 중 쓰는 값만 받는다. 가입 응답은 사용자 객체이거나, 세션과 함께 user 안에 들어 있다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SupabaseUserResponse(
    String id,
    @JsonProperty("email_confirmed_at") String emailConfirmedAt,
    @JsonProperty("access_token") String accessToken,
    SupabaseUserResponse user) {

  public String userId() {
    return id != null ? id : user != null ? user.id() : null;
  }

  /** 확인 메일 없이 바로 가입 처리된 응답인지. Confirm email이 꺼져 있을 때 나타난다. */
  public boolean confirmedWithoutMail() {
    return accessToken != null
        || emailConfirmedAt != null
        || (user != null && user.emailConfirmedAt() != null);
  }
}
