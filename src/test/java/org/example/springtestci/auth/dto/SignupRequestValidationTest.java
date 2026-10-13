package org.example.springtestci.auth.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SignupRequestValidationTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void acceptsAValidRequest() {
    assertTrue(
        validator.validate(new SignupRequest("user@example.com", "Passw0rd!", "기록왕")).isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"short1!", "onlyletters!!", "12345678!!", "Password123", "Pass word1!"})
  void rejectsPasswordsThatBreakThePolicy(String password) {
    assertEquals(
        1, validator.validate(new SignupRequest("user@example.com", password, "기록왕")).size());
  }

  @ParameterizedTest
  @ValueSource(strings = {"a", "열두글자를넘어가는닉네임입니다", "bad nick", "bad-nick"})
  void rejectsInvalidNicknames(String nickname) {
    assertEquals(
        1, validator.validate(new SignupRequest("user@example.com", "Passw0rd!", nickname)).size());
  }

  @Test
  void rejectsAMalformedEmail() {
    assertEquals(
        1, validator.validate(new SignupRequest("not-an-email", "Passw0rd!", "기록왕")).size());
  }
}
