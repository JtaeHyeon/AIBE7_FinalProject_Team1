package org.example.springtestci.listing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** 게시할 초안의 ID와 버전만 받는다. 판매 내용은 서버에 저장된 초안에서 읽는다. */
public record PublishListingRequest(
    @NotNull @Positive Long draftId, @NotNull @PositiveOrZero Long version) {}
