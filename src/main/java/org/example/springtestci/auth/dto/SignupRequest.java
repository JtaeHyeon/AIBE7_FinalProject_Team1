package org.example.springtestci.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank
        @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d])[\\x21-\\x7E]{8,64}$",
            message = "비밀번호는 8~64자의 영문, 숫자, 특수문자를 모두 포함해야 합니다.")
        String password,
    @NotBlank
        @Pattern(
            regexp = "^[가-힣A-Za-z0-9_]{2,12}$",
            message = "닉네임은 2~12자의 한글, 영문, 숫자, 밑줄만 사용할 수 있습니다.")
        String nickname) {}
