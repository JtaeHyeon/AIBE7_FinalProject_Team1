package org.example.springtestci.ai.service;

import java.util.List;
import java.util.Map;

/** 모델은 조회 도구 이름만 선택한다. 실제 대상과 권한은 서버의 요청 범위에 고정한다. */
public interface ProductQuestionGateway {
  boolean configured();

  Answer ask(String question, Tools tools);

  interface Tools {
    Map<String, Object> read(String name);
  }

  record Answer(String answer, List<String> sourceKeys) {}
}
