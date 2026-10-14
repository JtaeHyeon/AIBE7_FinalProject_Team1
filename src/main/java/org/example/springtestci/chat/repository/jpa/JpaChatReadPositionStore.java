package org.example.springtestci.chat.repository.jpa;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaChatReadPositionStore extends JpaRepository<ChatReadPositionEntity, Long> {

  Optional<ChatReadPositionEntity> findByRoomIdAndUserId(long roomId, long userId);
}
