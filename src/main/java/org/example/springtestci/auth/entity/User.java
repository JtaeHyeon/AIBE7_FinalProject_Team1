package org.example.springtestci.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.example.springtestci.common.audit.BaseTimeEntity;

@Entity
@Table(name = "users")
public class User extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 254, unique = true)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Column(nullable = false, length = 12, unique = true)
  private String nickname;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserRole role;

  @Column(name = "email_verified", nullable = false)
  private boolean emailVerified;

  /** JPA가 사용하는 기본 생성자다. */
  protected User() {}

  /** 이메일, 비밀번호 해시, 닉네임, 역할로 인증 전 회원을 만든다. */
  public User(String email, String passwordHash, String nickname, UserRole role) {
    this.email = email;
    this.passwordHash = passwordHash;
    this.nickname = nickname;
    this.role = role;
  }

  /** 이메일 인증을 마쳤는지 돌려준다. */
  public boolean isEmailVerified() {
    return emailVerified;
  }

  /** 이메일 인증을 마친 상태로 바꾼다. */
  public void markEmailVerified() {
    this.emailVerified = true;
  }

  /** 회원 id를 돌려준다. */
  public Long getId() {
    return id;
  }

  /** 정규화된 이메일을 돌려준다. */
  public String getEmail() {
    return email;
  }

  /** 단방향 해시로 저장한 비밀번호를 돌려준다. */
  public String getPasswordHash() {
    return passwordHash;
  }

  /** 닉네임을 돌려준다. */
  public String getNickname() {
    return nickname;
  }

  /** 회원 역할(USER, ADMIN)을 돌려준다. */
  public UserRole getRole() {
    return role;
  }
}
