/** 조회 전용 관리자 화면. 응답은 텍스트로만 표시하고 화면을 떠나면 업무 데이터를 지운다. */
(() => {
  const $ = id => document.getElementById(id);
  const form = $('filters');
  let tab = 'trades', page = 0, version = 0, controller, filters = new URLSearchParams(), opener;
  const labels = {
    PENDING: '구매 요청', ACCEPTED: '수락', REJECTED: '요청 거절', RESERVED: '예약', COMPLETED: '완료', CANCELLED: '취소',
    SUCCESS: '성공', SELF_CIRCUMSTANCES: '개인 사정', MUTUAL_AGREEMENT: '상호 합의', COUNTERPART_UNREACHABLE: '상대방 연락 두절', CONDITION_MISMATCH: '제품 상태 불일치',
    HISTORY_CORRECTED: '이력 정정', HISTORY_VOIDED: '이력 무효', HISTORY_VISIBILITY_CHANGED: '이력 공개 범위 변경',
    PURCHASE_REQUEST_ACCEPTED: '구매 요청 수락', PURCHASE_REQUEST_CANCELLED: '구매 요청 취소', PURCHASE_REQUEST_REJECTED: '구매 요청 거절',
    TRADE_COMPLETED: '거래 완료', TRADE_RESERVATION_CANCELLED: '예약 취소',
    OWNERSHIP_TRANSFER_CREATED: '소유권 이전 요청', OWNERSHIP_TRANSFERRED: '소유권 이전 완료', OWNERSHIP_TRANSFER_CANCELLED: '소유권 이전 취소',
    LISTING_PUBLISHED: '판매글 게시', LISTING_UPDATED: '판매글 수정', LISTING_CANCELLED: '판매글 판매 종료', PRODUCT_INFO_CORRECTED: '제품 기본 정보 정정',
    VALID: '유효', VOID: '무효', PUBLIC: '공개', PRIVATE: '비공개', ACTIVE: '판매 중', SOLD: '판매 완료',
    PURCHASE: '구매', INSPECTION: '점검', REPAIR: '수리', PART_REPLACEMENT: '부품 교체', SPEC_CHANGE: '사양 변경', TRANSFER: '소유권 이전'
  };
  const fieldLabels = { id: 'ID', productId: '제품 ID', authorId: '작성자 ID', eventType: '이력 종류', occurredOn: '발생일', title: '제목', content: '내용', serviceProvider: '처리 업체', cost: '비용 (원)', replacedPart: '교체 부품', status: '상태', createdAt: '생성 시각', updatedAt: '변경 시각', changeType: '변경 종류', visibility: '공개 범위', requestStatus: '요청 상태', transactionStatus: '거래 상태', listingStatus: '판매글 상태', confirmedFactsVersion: '확인한 제품 버전', cancelledAt: '취소 시각', completedAt: '완료 시각', specsBefore: '변경 전 사양', specsAfter: '변경 후 사양', ramGb: 'RAM (GB)', storageGb: '저장 용량 (GB)', storageType: '저장 방식', batteryHealthPercent: '배터리 상태 (%)', cpu: 'CPU', gpu: 'GPU', display: '디스플레이', os: '운영체제' };
  const text = value => value == null ? '—' : Object.hasOwn(labels, value) ? labels[value] : String(value);
  const time = value => value ? new Date(value).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul', hour12: false }) + ' (KST)' : '—';
  const node = (tag, value) => { const n = document.createElement(tag); if (value != null) n.textContent = value; return n; };
  const status = value => { const n = node('span', text(value)); n.className = 'status'; n.dataset.status = value || ''; return n; };
  const pair = (primary, secondary) => {
    const n = node('div'), top = node('span', primary); top.className = 'cell-primary'; n.append(top);
    if (secondary != null) { const bottom = node('span', secondary); bottom.className = 'cell-secondary'; n.append(bottom); }
    return n;
  };
  const productName = product => product?.title || product?.modelName || '제품 정보 없음';
  const productModel = product => [product?.manufacturer, product?.modelName].filter(Boolean).join(' · ') || '모델 정보 없음';
  const memberName = member => member?.nickname || member?.email || '탈퇴한 회원';
  const memberText = member => member ? [memberName(member), member.email].filter((value, index, values) => value && values.indexOf(value) === index).join('\n') : '정보 없음';
  const memberCell = (member, role) => { const n = pair((role ? role + ' · ' : '') + memberName(member), member?.email || '이메일 정보 없음'); n.className = 'member-summary'; return n; };
  const dateParts = value => {
    const date = new Date(value);
    const parts = Object.fromEntries(new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' }).formatToParts(date).map(p => [p.type, p.value]));
    return [`${parts.year}-${parts.month}-${parts.day}`, `${parts.hour}:${parts.minute}:${parts.second}`];
  };
  const dateCell = value => value ? pair(...dateParts(value)) : pair('—');
  const factText = (key, value) => value && ['변경 시각', '생성 시각', '취소 시각', '완료 시각'].includes(key) && !Number.isNaN(Date.parse(value)) ? dateParts(value).join('\n') + ' KST' : text(value);
  const hideDetail = (restore = false) => {
    if ($('detail').open) $('detail').close();
    $('detail').hidden = true; $('detail-body').replaceChildren(); document.body?.classList.remove('detail-open');
    if (restore && opener?.isConnected) opener.focus();
    $('rows').querySelectorAll('tr').forEach(row => row.classList.remove('selected'));
  };

  /** 취소된 요청이 늦게 완료돼도 다른 탭·계정의 화면을 다시 채우지 못하게 세대를 함께 확인한다. */
  const invalidate = () => { version++; controller?.abort(); controller = new AbortController(); return version; };
  const clear = () => { hideDetail(); $('rows').replaceChildren(); $('count').textContent = ''; $('range').textContent = ''; $('updated').textContent = ''; $('previous').disabled = true; $('next').disabled = true; $('results').setAttribute('aria-busy', 'false'); };
  const request = async (path, current) => {
    const response = await window.fetch('/api/v1/admin/' + path, { credentials: 'same-origin', cache: 'no-store', signal: controller.signal });
    if (current !== version) return null;
    if (response.status === 401 || response.status === 403) {
      invalidate(); clear(); $('message').textContent = '관리자 권한을 확인할 수 없어요. 홈에서 로그인한 계정을 확인해 주세요.';
      return null;
    }
    const payload = await response.json().catch(() => null);
    if (!response.ok) throw new Error(payload?.message || '기록을 불러오지 못했어요. 새로고침으로 다시 시도해 주세요.');
    return payload.data;
  };
  const fields = entries => {
    const dl = node('dl');
    for (const [key, value] of entries) { const row = node('div'); row.className = 'field'; row.append(node('dt', key), node('dd', value)); dl.append(row); }
    return dl;
  };
  const auditFields = a => [['제품', productName(a.product)], ['제조사·모델', productModel(a.product)], ['처리 주체', a.actorId == null ? '시스템' : memberText(a.actor)], ['행위', text(a.action)], ['결과', text(a.result)], ['처리 시각', time(a.createdAt)]];
  const section = (title, entries) => { const n = node('section'); n.className = 'detail-section'; n.append(node('h3', title), fields(entries)); return n; };
  const identifiers = entries => { const n = node('details'); n.className = 'identifiers'; n.append(node('summary', '기록 식별 정보'), fields(entries)); return n; };
  const flatten = data => Object.fromEntries(Object.entries(data.values).flatMap(([key, value]) => value && typeof value === 'object' ? Object.entries(value).map(([part, detail]) => [`${fieldLabels[key] || key} · ${fieldLabels[part] || part}`, detail]) : [[fieldLabels[key] || key, value]]));
  /** 두 사본에서 실제 달라진 값을 같은 행에 놓아 정정·무효의 변경 경위를 비교한다. */
  const comparison = (before, after) => {
    const block = node('section'); block.className = 'comparison'; block.append(node('h3', '변경 내용'));
    if (!before.available || !after.available) block.append(node('p', '일부 기록은 세부 변경 정보를 제공하지 않아요.'));
    const previous = flatten(before), next = flatten(after);
    const keys = [...new Set([...Object.keys(previous), ...Object.keys(next)])].filter(key => JSON.stringify(previous[key]) !== JSON.stringify(next[key]));
    if (!keys.length) { block.append(node('p', '비교할 수 있는 변경 값이 없어요.')); return block; }
    const header = node('div'); header.className = 'comparison-head'; header.append(node('span', '항목'), node('span', '변경 전'), node('span', '변경 후')); block.append(header);
    for (const key of keys) { const row = node('div'); row.className = 'comparison-row changed'; const beforeCell = node('span', factText(key, previous[key])), afterCell = node('span', factText(key, next[key])); beforeCell.title = text(previous[key]); afterCell.title = text(next[key]); row.append(node('span', key), beforeCell, afterCell); block.append(row); }
    return block;
  };
  const open = async (id, button, ordinal) => {
    const current = invalidate();
    opener = button; $('detail-title').textContent = `${tab === 'trades' ? '거래 상세' : '업무 기록 상세'} · No. ${ordinal}`;
    $('detail').hidden = false; $('detail-body').replaceChildren(node('p', '상세 기록을 불러오는 중이에요.'));
    if ($('detail').showModal && !$('detail').open) $('detail').showModal();
    document.body?.classList.add('detail-open');
    $('rows').querySelectorAll('tr').forEach(row => row.classList.remove('selected')); button.closest('tr').classList.add('selected');
    try {
      const data = await request(`${tab}/${id}`, current);
      if (!data || current !== version) return;
      if (tab === 'trades') {
        const summary = node('div'); summary.className = 'detail-summary'; summary.append(status(data.transactionStatus || data.requestStatus), node('p', productName(data.product)));
        const content = [summary,
          section('제품 및 당사자', [['제품', productName(data.product)], ['제조사·모델', productModel(data.product)], ['판매글', data.listingTitle || '판매글 정보 없음'], ['판매자', memberText(data.seller)], ['구매자', memberText(data.buyer)]]),
          section('거래 진행', [['요청 상태', text(data.requestStatus)], ['거래 상태', text(data.transactionStatus)], ['예약 시각', time(data.reservedAt)], ['완료 시각', time(data.completedAt)]])];
        if (data.cancelledAt || data.cancelReason) content.push(section('취소 내역', [['취소 시각', time(data.cancelledAt)], ['취소 수행자', memberText(data.cancelledBy)], ['취소 사유', text(data.cancelReason)]]));
        content.push(identifiers([['거래 ID', data.id], ['제품 ID', data.productId], ['판매글 ID', data.listingId], ['판매자 회원 ID', data.sellerId], ['구매자 회원 ID', data.buyerId], ['취소 수행자 회원 ID', data.cancelledById ?? '—']]));
        $('detail-body').replaceChildren(...content);
      } else {
        const content = [section('처리 정보', auditFields(data.record))];
        if (data.reason) { const reason = node('section'); reason.className = 'detail-section'; const p = node('p', data.reason); p.className = 'reason'; reason.append(node('h3', '처리 사유'), p); content.push(reason); }
        content.push(comparison(data.before, data.after), identifiers([['기록 ID', data.record.id], ['제품 ID', data.record.productId], ['대상 ID', data.record.targetId], ['이력 ID', data.record.eventId ?? '—'], ['처리 주체 회원 ID', data.record.actorId ?? '시스템']])); $('detail-body').replaceChildren(...content);
      }
      $('detail').focus();
    } catch (error) { if (current === version && error.name !== 'AbortError') $('detail-body').replaceChildren(node('p', error.message)); }
  };
  const load = async () => {
    const current = invalidate(); clear(); $('message').textContent = '기록을 불러오는 중이에요.'; $('message').dataset.kind = 'loading'; $('results').setAttribute('aria-busy', 'true');
    const headings = tab === 'trades' ? ['번호', '제품', '거래 당사자', '상태', '예약 시각 (KST)'] : ['번호', '업무', '제품', '처리 주체', '결과', '처리 시각 (KST)'];
    $('records-table').dataset.kind = tab;
    const header = node('tr'); headings.forEach(value => { const th = node('th', value); th.setAttribute('scope', 'col'); header.append(th); }); $('columns').replaceChildren(header);
    const query = new URLSearchParams(filters); query.set('page', page); query.set('size', '20');
    try {
      const data = await request(`${tab}?${query}`, current);
      if (!data || current !== version) return;
      $('message').textContent = data.items.length ? '' : '조건에 맞는 기록이 없어요.';
      $('message').dataset.kind = data.items.length ? '' : 'empty';
      $('count').textContent = `총 ${data.total.toLocaleString()}건`;
      $('updated').textContent = new Date().toLocaleTimeString('ko-KR', { timeZone: 'Asia/Seoul', hour12: false }) + ' 기준';
      for (const [index, item] of data.items.entries()) {
        const ordinal = page * data.size + index + 1;
        const row = node('tr'), sequence = node('td', ordinal), cell = node('td'), button = node('button');
        sequence.className = 'sequence'; row.append(sequence);
        button.append(tab === 'trades' ? pair(productName(item.product), productModel(item.product)) : pair(text(item.action), '상세 보기 →'));
        button.type = 'button'; button.className = 'row-link'; button.setAttribute('aria-label', `${ordinal}번 ${productName(item.product)} 상세 조회`); button.addEventListener('click', () => open(item.id, button, ordinal)); cell.append(button); row.append(cell);
        const participants = node('div'); participants.className = 'participants';
        participants.append(memberCell(item.seller, '판매자'), memberCell(item.buyer, '구매자'));
        const values = tab === 'trades'
          ? [participants, status(item.transactionStatus || item.requestStatus), dateCell(item.reservedAt)]
          : [pair(productName(item.product), productModel(item.product)), item.actorId == null ? pair('시스템', '자동 처리') : memberCell(item.actor), status(item.result), dateCell(item.createdAt)];
        values.forEach((value, index) => { const td = node('td'); td.dataset.label = headings[index + 2]; td.append(value); row.append(td); }); $('rows').append(row);
      }
      const first = data.total && data.items.length ? page * 20 + 1 : 0;
      $('range').textContent = `${first}–${first ? page * 20 + data.items.length : 0} / ${data.total.toLocaleString()}건`;
      $('previous').disabled = page === 0; $('next').disabled = (page + 1) * 20 >= data.total;
    } catch (error) { if (current === version && error.name !== 'AbortError') { $('message').textContent = error.message; $('message').dataset.kind = 'error'; } }
    finally { if (current === version) $('results').setAttribute('aria-busy', 'false'); }
  };
  const changeTab = value => {
    tab = value; page = 0; form.reset(); filters = new URLSearchParams();
    for (const [id, active] of [['trades-tab', tab === 'trades'], ['audits-tab', tab !== 'trades']]) {
      if (active) $(id).setAttribute('aria-current', 'page'); else $(id).removeAttribute('aria-current');
    }
    $('status-field').hidden = tab !== 'trades'; $('action-field').hidden = tab === 'trades';
    $('actor-label').textContent = tab === 'trades' ? '거래 회원 검색' : '처리 회원 검색';
    $('date-label').textContent = tab === 'trades' ? '예약일 시작 (UTC)' : '처리일 시작 (UTC)';
    $('scope-note').textContent = tab === 'trades' ? '예약·완료 시각은 처리 기록을 기준으로 해요. 시각이 남아 있지 않은 기록은 ‘—’로 표시해요.' : '정정·무효 기록은 상세에서 변경 전후 내용과 사유를 확인할 수 있어요.';
    const title = tab === 'trades' ? '거래·취소 기록' : '업무 변경 기록';
    $('page-title').textContent = title; $('breadcrumb').textContent = title; document.title = `${title} · PastPort 관리자`;
    $('page-description').textContent = tab === 'trades' ? '제품별 거래 진행 상황과 취소 내역을 확인해요.' : '누가 무엇을 변경했는지, 처리 내용과 사유를 확인해요.';
    $('result-title').textContent = tab === 'trades' ? '거래 목록' : '업무 기록 목록'; load();
  };
  form.addEventListener('submit', event => {
    event.preventDefault(); filters = new URLSearchParams();
    for (const [key, value] of new FormData(form)) if (value && (key !== 'status' || tab === 'trades') && (key !== 'action' || tab !== 'trades')) filters.set(key, String(value).trim());
    page = 0; load();
  });
  $('reset-filters').addEventListener('click', () => changeTab(tab));
  $('trades-tab').addEventListener('click', () => changeTab('trades'));
  $('audits-tab').addEventListener('click', () => changeTab('audit-logs'));
  $('refresh').addEventListener('click', load);
  $('previous').addEventListener('click', () => { if (page > 0) { page--; load(); } });
  $('next').addEventListener('click', () => { if (!$('next').disabled) { page++; load(); } });
  const closeDetail = () => { invalidate(); hideDetail(true); };
  $('close-detail').addEventListener('click', closeDetail);
  $('detail').addEventListener('cancel', event => { event.preventDefault(); closeDetail(); });
  document.addEventListener('visibilitychange', () => { if (document.hidden) { invalidate(); clear(); } else load(); });
  window.addEventListener('pagehide', () => { invalidate(); clear(); });
  window.addEventListener('pageshow', event => { if (event.persisted) load(); });
  window.addEventListener('focus', load);
  load();
})();
