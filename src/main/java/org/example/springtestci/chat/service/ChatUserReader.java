package org.example.springtestci.chat.service;

import java.util.Collection;
import java.util.Map;

public interface ChatUserReader {

  Map<Long, String> findNicknamesByIds(Collection<Long> userIds);
}
