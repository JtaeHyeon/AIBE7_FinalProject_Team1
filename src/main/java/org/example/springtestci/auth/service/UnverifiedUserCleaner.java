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

  @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
  @Transactional
  public void deleteExpired() {
    List<User> expired =
        userRepository.findByEmailVerifiedFalseAndCreatedAtBefore(clock.instant().minus(ttl));
    int deleted = 0;
    for (User user : expired) {
      // 링크는 눌렀지만 아직 로그인하지 않은 사용자는 지우지 않고 인증 완료로 바꾼다.
      if (emailVerificationService.confirmIfVerified(user.getEmail())) {
        user.markEmailVerified();
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
