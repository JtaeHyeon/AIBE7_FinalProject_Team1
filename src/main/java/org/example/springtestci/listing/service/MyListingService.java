package org.example.springtestci.listing.service;

import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.listing.dto.MyListingPageResponse;
import org.example.springtestci.listing.entity.ListingStatus;
import org.example.springtestci.listing.repository.MyListingQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 판매자 본인의 진행·종료 글을 읽는다. 실제 수정·중지는 기존 관리 API에서 권한을 다시 검사한다. */
@Service
public class MyListingService {
  private final MyListingQueryRepository query;

  public MyListingService(MyListingQueryRepository query) {
    this.query = query;
  }

  @Transactional(readOnly = true)
  public MyListingPageResponse list(long sellerId, ListingStatus status, int page, int size) {
    if (sellerId <= 0) throw new CommonException(ErrorCode.UNAUTHORIZED);
    if (page < 0 || size < 1 || size > 100) throw new CommonException(ErrorCode.INVALID_INPUT);
    return MyListingPageResponse.from(query.findBySeller(sellerId, status, page, size));
  }
}
