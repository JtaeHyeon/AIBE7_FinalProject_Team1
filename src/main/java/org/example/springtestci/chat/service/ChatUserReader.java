package org.example.springtestci.chat.service;

import java.util.Collection;
import java.util.Map;

/** 채팅방 상대방 표시용 사용자 닉네임을 일괄 조회하는 읽기 포트다. */
public interface ChatUserReader {

  Map<Long, String> findNicknamesByIds(Collection<Long> userIds);
}
