package org.example.springtestci.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.example.springtestci.auth.entity.User;
import org.example.springtestci.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 가입 후 정해진 시간 안에 이메일 인증을 하지 않은 계정을 지워, 이메일과 닉네임을 다시 쓸 수 있게 한다. */
@Component
public class UnverifiedUserCleaner {

  private static final Logger log = LoggerFactory.getLogger(UnverifiedUserCleaner.class);

  private final UserRepository userRepository;
  private final EmailVerificationService emailVerificationService;
  private final Clock clock;
  private final Duration ttl;

  /** 정리에 필요한 구성 요소와 인증 유예 시간을 주입받는다. */
  public UnverifiedUserCleaner(
      UserRepository userRepository,
      EmailVerificationService emailVerificationService,
      Clock clock,
      @Value("${app.auth.unverified-user-ttl:30m}") Duration ttl) {
    this.userRepository = userRepository;
    this.emailVerificationService = emailVerificationService;
    this.clock = clock;
    this.ttl = ttl;
  }

  /** 유예 시간이 지난 미인증 계정을 정리한다. 확인할 수 없거나 아직 기다릴 계정은 지우지 않는다. */
  @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
  @Transactional
  public void deleteExpired() {
    List<User> expired =
        userRepository.findByEmailVerifiedFalseAndCreatedAtBefore(clock.instant().minus(ttl));
    int deleted = 0;
    for (User user : expired) {
      VerificationStatus status = emailVerificationService.checkVerification(user.getEmail());
      // 링크는 눌렀지만 아직 로그인하지 않은 사용자는 지우지 않고 인증 완료로 바꾼다.
      if (status == VerificationStatus.CONFIRMED) {
        user.markEmailVerified();
        continue;
      }
      // 조회에 실패해 확인 여부를 알 수 없으면, 인증을 마친 계정을 잃지 않도록 다음 주기에 다시 확인한다.
      if (status == VerificationStatus.UNKNOWN) {
        log.warn("이메일 확인 여부를 알 수 없어 계정 삭제를 건너뜁니다: userId={}", user.getId());
        continue;
      }
      // 인증 메일을 다시 보냈다면 그 시점부터 다시 기다린다.
      if (status == VerificationStatus.UNCONFIRMED
          && emailVerificationService.linkSentWithin(user.getEmail(), ttl)) {
        continue;
      }
      // 대기 기록이 만료됐어도 가입 직후라면 확인할 기회가 남았을 수 있어 기록 보관 시간이 지난 뒤에만 지운다.
      if (status == VerificationStatus.EXPIRED
          && user.getCreatedAt()
              .isAfter(clock.instant().minus(EmailVerificationService.PENDING_TTL))) {
        continue;
      }
      emailVerificationService.discard(user.getEmail());
      userRepository.delete(user);
      deleted++;
    }
    if (deleted > 0) {
      log.info("인증하지 않은 계정 {}개를 삭제했습니다.", deleted);
    }
  }
}
