package org.example.springtestci.document.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 문서에서 추출하는 항목(DOC-04). 이름·주소·연락처·카드·계좌 등 개인정보 필드는 의도적으로 두지 않는다. 모든 필드는 문서에 없을 수 있으므로 null을 허용한다.
 */
public record ExtractedFields(
    @Size(max = 100) String productName,
    @Size(max = 150) String modelName,
    LocalDate purchaseDate,
    @Size(max = 200) String vendor,
    @PositiveOrZero Long price,
    @PositiveOrZero @Max(120) Integer warrantyMonths,
    LocalDate repairDate,
    @Size(max = 2000) String repairDescription) {}
