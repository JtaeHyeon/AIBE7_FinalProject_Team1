package org.example.springtestci.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SupabasePropertiesTest {

  /** 프로젝트 주소와 비밀 키가 모두 있어야 설정된 것으로 본다. */
  @Test
  void isConfiguredOnlyWithAProjectUrlAndASecret() {
    assertTrue(new SupabaseProperties("https://abc.supabase.co", "secret").configured());
    assertFalse(new SupabaseProperties("", "secret").configured());
    assertFalse(new SupabaseProperties("https://abc.supabase.co", "").configured());
    // 주소 자리에 API 키를 잘못 넣은 경우
    assertFalse(new SupabaseProperties("sb_publishable_abc", "secret").configured());
  }

  /** 주소 끝의 슬래시와 REST 경로를 제거한다. */
  @Test
  void baseUrlDropsTrailingSlashesAndTheRestPath() {
    assertEquals(
        "https://abc.supabase.co",
        new SupabaseProperties("https://abc.supabase.co/", "s").baseUrl());
    assertEquals(
        "https://abc.supabase.co",
        new SupabaseProperties(" https://abc.supabase.co/rest/v1/ ", "s").baseUrl());
  }
}
