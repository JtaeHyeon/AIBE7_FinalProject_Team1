package org.example.springtestci.auth.service;

import java.time.Duration;
import org.example.springtestci.auth.repository.UserRepository;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** 가입 전 이메일 소유 확인. Supabase가 확인 메일을 보내고, 사용자가 메일의 링크를 누르면 인증 완료로 표시한다. 진행 상태는 Redis에 TTL로 둔다. */
@Service
public class EmailVerificationService {

  private static final String COOLDOWN_KEY = "auth:email:cooldown:";
  private static final String PENDING_KEY = "auth:email:pending:";
  private static final String VERIFIED_KEY = "auth:email:verified:";
  private static final Duration COOLDOWN = Duration.ofSeconds(60);
  private static final Duration PENDING_TTL = Duration.ofMinutes(60);
  private static final Duration VERIFIED_TTL = Duration.ofMinutes(30);

  private final StringRedisTemplate redisTemplate;
  private final UserRepository userRepository;
  private final SupabaseAuthClient supabase;

  public EmailVerificationService(
      StringRedisTemplate redisTemplate,
      UserRepository userRepository,
      SupabaseAuthClient supabase) {
    this.redisTemplate = redisTemplate;
    this.userRepository = userRepository;
    this.supabase = supabase;
  }

  public void sendLink(String rawEmail) {
    String email = AuthService.normalizeEmail(rawEmail);
    if (userRepository.existsByEmail(email)) {
      throw new CommonException(ErrorCode.DUPLICATE_EMAIL);
    }
    if (!Boolean.TRUE.equals(
        redisTemplate.opsForValue().setIfAbsent(COOLDOWN_KEY + email, "1", COOLDOWN))) {
      throw new CommonException(ErrorCode.TOO_MANY_REQUESTS);
    }
    try {
      String supabaseUserId = supabase.signUp(email);
      redisTemplate.opsForValue().set(PENDING_KEY + email, supabaseUserId, PENDING_TTL);
    } catch (CommonException exception) {
      redisTemplate.delete(COOLDOWN_KEY + email);
      throw exception;
    }
  }

  /** 링크를 눌러 이메일이 확인됐는지 본다. 확인됐으면 인증 완료로 표시한다. */
  public boolean isVerified(String rawEmail) {
    String email = AuthService.normalizeEmail(rawEmail);
    if (Boolean.TRUE.equals(redisTemplate.hasKey(VERIFIED_KEY + email))) {
      return true;
    }
    String supabaseUserId = redisTemplate.opsForValue().get(PENDING_KEY + email);
    if (supabaseUserId == null || !supabase.isConfirmed(supabaseUserId)) {
      return false;
    }
    redisTemplate.opsForValue().set(VERIFIED_KEY + email, "1", VERIFIED_TTL);
    return true;
  }

  /** 인증 완료 표시를 한 번만 쓸 수 있게 삭제하며 확인하고, 쓰임이 끝난 Supabase 임시 계정을 정리한다. */
  public boolean consumeVerified(String normalizedEmail) {
    boolean verified = Boolean.TRUE.equals(redisTemplate.delete(VERIFIED_KEY + normalizedEmail));
    if (verified) {
      supabase.deleteUser(redisTemplate.opsForValue().getAndDelete(PENDING_KEY + normalizedEmail));
    }
    return verified;
  }
}
