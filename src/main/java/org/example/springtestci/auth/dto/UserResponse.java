package org.example.springtestci.auth.dto;

import org.example.springtestci.auth.entity.User;

public record UserResponse(Long id, String email, String nickname) {

  public static UserResponse from(User user) {
    return new UserResponse(user.getId(), user.getEmail(), user.getNickname());
  }
}
