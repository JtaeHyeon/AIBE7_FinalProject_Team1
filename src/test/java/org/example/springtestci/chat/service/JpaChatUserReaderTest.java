package org.example.springtestci.chat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import org.example.springtestci.auth.entity.User;
import org.example.springtestci.auth.entity.UserRole;
import org.example.springtestci.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JpaChatUserReaderTest {

  @Mock private UserRepository userRepository;

  @Test
  void returnsNicknamesForExistingUsersOnly() throws Exception {
    when(userRepository.findAllById(List.of(3L, 7L, 99L)))
        .thenReturn(List.of(user(3L, "seller"), user(7L, "buyer")));

    var nicknames = new JpaChatUserReader(userRepository).findNicknamesByIds(List.of(3L, 7L, 99L));

    assertEquals(Map.of(3L, "seller", 7L, "buyer"), nicknames);
  }

  private static User user(long id, String nickname) throws Exception {
    User user = new User(nickname + "@example.com", "hash", nickname, UserRole.USER);
    Field idField = User.class.getDeclaredField("id");
    idField.setAccessible(true);
    idField.set(user, id);
    return user;
  }
}
