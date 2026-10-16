package org.example.springtestci.chat.service;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.example.springtestci.auth.entity.User;
import org.example.springtestci.auth.repository.UserRepository;
import org.springframework.stereotype.Component;

@Component
public class JpaChatUserReader implements ChatUserReader {

  private final UserRepository userRepository;

  public JpaChatUserReader(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public Map<Long, String> findNicknamesByIds(Collection<Long> userIds) {
    return userRepository.findAllById(userIds).stream()
        .collect(Collectors.toMap(User::getId, User::getNickname, (first, ignored) -> first));
  }
}
