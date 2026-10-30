document.querySelector('#trade').innerHTML=`
  <div class="handover-screen">
    <div class="handover-bar"><b>Pastport</b><span>도움말</span></div>
    <div class="handover-body">
      <div class="handover-title"><h1>소유권 이전</h1><p>제품의 이력은 이어가고, 이전 소유자의 개인정보는 안전하게 분리합니다.</p></div>
      <div class="handover-grid">
        <section class="handover-panel">
          <h2>이전할 제품</h2>
          <div class="transfer-product"><div class="laptop"></div><div><span class="eyebrow">여권 인증 완료</span><strong>MacBook Pro 14″ M3 Pro</strong><span>DFP-81A-7D39 · 보유 중</span></div></div>
          <div class="check-title">전달되는 정보</div>
          <div class="check-line">제품 기본 정보와 공개된 이력</div>
          <div class="check-line">공개 문서와 보증 기간</div>
          <div class="check-line">제품 사용·관리 이력</div>
          <div class="check-title">전달되지 않는 정보</div>
          <div class="meta">이전 소유자의 이름, 연락처, 결제 정보와 비공개 문서는 전달되지 않습니다.</div>
        </section>
        <section class="handover-panel">
          <h2>일회성 인계 QR</h2>
          <div class="qr-box" aria-label="소유권 이전 QR 코드">
            <svg viewBox="0 0 150 150" width="112" height="112" aria-hidden="true"><rect width="150" height="150" fill="#fff"/><g fill="#25262d"><path d="M10 10h38v38H10zM17 17h24v24H17zM24 24h10v10H24zM102 10h38v38h-38zM109 17h24v24h-24zM116 24h10v10h-10zM10 102h38v38H10zM17 109h24v24H17zM24 116h10v10H24zM62 12h10v11H62zM78 20h11v10H78zM56 35h12v12H56zM74 42h10v10H74zM88 54h12v12H88zM56 62h10v10H56zM70 66h12v12H70zM88 74h10v10H88zM106 58h11v11h-11zM122 66h10v10h-10zM56 86h12v12H56zM72 94h10v10H72zM88 90h12v12H88zM106 86h12v12h-12zM122 102h10v12h-10zM56 110h10v10H56zM72 118h12v12H72zM92 110h10v12H92zM108 122h10v10h-10z"/></g></svg>
          </div>
          <div class="qr-copy">30분 후 만료 · 1회 사용<br><button class="button small" style="margin-top:7px">QR 코드 다시 생성</button></div>
        </section>
      </div>
      <div class="handover-footer"><button class="button primary">안전하게 소유권 이전</button></div>
    </div>
  </div>`;
