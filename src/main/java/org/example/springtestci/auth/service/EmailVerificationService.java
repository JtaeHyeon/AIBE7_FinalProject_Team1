package org.example.springtestci.auth.service;

import java.time.Duration;
import org.example.springtestci.auth.repository.UserRepository;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 가입 후 이메일 소유 확인. Supabase가 확인 메일을 보내고, 사용자가 메일의 링크를 누르면 Supabase 쪽 이메일이 확인된다. 우리 서비스는 로그인할 때 그 결과를
 * 조회해 users를 인증 완료로 바꾼다. 발송한 임시 계정의 id는 Redis에 TTL로 둔다.
 */
@Service
public class EmailVerificationService {

  private static final String COOLDOWN_KEY = "auth:email:cooldown:";
  private static final String PENDING_KEY = "auth:email:pending:";
  private static final Duration COOLDOWN = Duration.ofSeconds(60);

  /** 인증 메일을 마지막으로 보낸 뒤 임시 계정 id를 기억해 두는 시간. */
  static final Duration PENDING_TTL = Duration.ofHours(1);

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

  /** 확인 메일을 보낸다. 같은 이메일로는 60초에 한 번만 보낼 수 있다. */
  public void sendLink(String normalizedEmail) {
    if (!Boolean.TRUE.equals(
        redisTemplate.opsForValue().setIfAbsent(COOLDOWN_KEY + normalizedEmail, "1", COOLDOWN))) {
      throw new CommonException(ErrorCode.TOO_MANY_REQUESTS);
    }
    try {
      String pendingId = redisTemplate.opsForValue().get(PENDING_KEY + normalizedEmail);
      if (pendingId == null) {
        String supabaseUserId = supabase.signUp(normalizedEmail);
        redisTemplate.opsForValue().set(PENDING_KEY + normalizedEmail, supabaseUserId, PENDING_TTL);
      } else {
        // 이미 임시 계정이 있으면 가입을 다시 하지 않고 메일만 다시 보내, 기존 id를 그대로 둔다.
        supabase.resend(normalizedEmail);
        redisTemplate.expire(PENDING_KEY + normalizedEmail, PENDING_TTL);
      }
    } catch (CommonException exception) {
      redisTemplate.delete(COOLDOWN_KEY + normalizedEmail);
      throw exception;
    }
  }

  /** 아직 인증하지 못한 계정에 메일을 다시 보낸다. 해당하는 계정이 없으면 아무 일도 하지 않는다(계정 존재 여부를 알리지 않는다). */
  public void resend(String rawEmail) {
    String email = AuthService.normalizeEmail(rawEmail);
    userRepository
        .findByEmail(email)
        .filter(user -> !user.isEmailVerified())
        .ifPresent(user -> sendLink(user.getEmail()));
  }

  /** 메일의 링크를 눌러 이메일이 확인됐는지 본다. 확인됐으면 쓰임이 끝난 Supabase 임시 계정을 정리한다. */
  public boolean confirmIfVerified(String normalizedEmail) {
    return checkVerification(normalizedEmail) == VerificationStatus.CONFIRMED;
  }

  /**
   * 이메일 확인 상태를 조회한다. Supabase 조회에 실패하면 UNKNOWN이라 호출한 쪽은 계정을 지우면 안 되고, 대기 기록(Redis)이 만료됐으면 EXPIRED다.
   * 확인됐으면 쓰임이 끝난 Supabase 임시 계정을 정리한다.
   */
  public VerificationStatus checkVerification(String normalizedEmail) {
    String supabaseUserId = redisTemplate.opsForValue().get(PENDING_KEY + normalizedEmail);
    if (supabaseUserId == null) {
      return VerificationStatus.EXPIRED;
    }
    VerificationStatus status = supabase.confirmationStatus(supabaseUserId);
    if (status == VerificationStatus.CONFIRMED) {
      discard(normalizedEmail);
    }
    return status;
  }

  /** 인증 메일을 마지막으로 보낸 지 window가 지나지 않았는지 본다. 보낼 때마다 대기 기록의 유효 시간이 새로 시작되는 것을 이용한다. */
  public boolean linkSentWithin(String normalizedEmail, Duration window) {
    Long remaining = redisTemplate.getExpire(PENDING_KEY + normalizedEmail);
    return remaining != null
        && remaining > 0
        && Duration.ofSeconds(remaining).compareTo(PENDING_TTL.minus(window)) > 0;
  }

  /** 대기 중인 Supabase 임시 계정을 지운다. */
  public void discard(String normalizedEmail) {
    supabase.deleteUser(redisTemplate.opsForValue().getAndDelete(PENDING_KEY + normalizedEmail));
  }
}
