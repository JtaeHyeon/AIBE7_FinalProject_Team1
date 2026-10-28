(() => {
  const TYPES = {
    PURCHASE: '구매',
    INSPECTION: '점검',
    REPAIR: '수리',
    PART_REPLACEMENT: '부품 교체',
    SPEC_CHANGE: '사양 변경',
    TRANSFER: '소유권 이전'
  };
  const CREATABLE = Object.keys(TYPES).filter((type) => type !== 'TRANSFER');
  const state = { productId: null, events: [], me: null, editing: null };

  const escapeHtml = (value) => String(value ?? '')
    .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;').replaceAll("'", '&#039;');
  const date = (value) => value ? new Intl.DateTimeFormat('ko-KR').format(new Date(`${value}T00:00:00`)) : '–';
  const today = () => new Date().toLocaleDateString('sv-SE');
  const root = () => document.querySelector('#passport [data-product-history]');

  const toast = (message, kind = '') => {
    document.querySelector('.product-toast')?.remove();
    const box = document.createElement('div');
    box.className = `product-toast ${kind}`.trim();
    box.textContent = message;
    document.body.append(box);
    window.setTimeout(() => box.remove(), 3600);
  };

  const api = async (path, options = {}) => {
    const headers = options.body ? { 'Content-Type': 'application/json', ...options.headers } : options.headers;
    const response = await window.PastportApi.fetch(path, { ...options, headers });
    const payload = await response.json().catch(() => null);
    if (!response.ok) {
      const error = new Error(payload?.message || '요청을 처리하지 못했습니다.');
      error.status = response.status;
      throw error;
    }
    return payload?.data;
  };

  const loadMe = async () => {
    if (state.me) return;
    try {
      state.me = (await api('/api/auth/me'))?.id ?? null;
    } catch {
      state.me = null;
    }
  };

  const eventItem = (event) => {
    const isVoid = event.status === 'VOID';
    // 이전 소유자가 남긴 기록은 새 소유자가 수정·무효 처리할 수 없다.
    const mine = state.me != null && Number(event.authorId) === Number(state.me);
    const facts = [
      event.serviceProvider && `처리 업체 ${escapeHtml(event.serviceProvider)}`,
      event.replacedPart && `교체 부품 ${escapeHtml(event.replacedPart)}`
    ].filter(Boolean).join(' · ');
    const corrections = (event.corrections || []).map((item) => `<li>${isVoid ? '무효' : '정정'} 사유: ${escapeHtml(item.reason)}</li>`).join('');
    const actions = isVoid ? '' : mine
      ? `<button class="button" type="button" data-history-visibility="${event.eventId}" data-visibility-next="${event.visibility === 'PUBLIC' ? 'PRIVATE' : 'PUBLIC'}">${event.visibility === 'PUBLIC' ? '비공개로 전환' : '공개로 전환'}</button><button class="button" type="button" data-history-correct="${event.eventId}">정정</button><button class="button danger" type="button" data-history-void="${event.eventId}">무효 처리</button>`
      : '<span class="ph-lock">이전 소유자 기록 · 수정할 수 없어요</span>';
    return `<li class="ph-item${isVoid ? ' void' : ''}" data-history-item="${event.eventId}">
      <div class="ph-head">
        <span class="ph-type">${escapeHtml(TYPES[event.eventType] || event.eventType)}</span>
        <span class="ph-date">${date(event.occurredOn)}</span>
        <span class="ph-badge ${event.visibility === 'PUBLIC' ? 'public' : 'private'}">${event.visibility === 'PUBLIC' ? '공개' : '비공개'}</span>
        ${isVoid ? '<span class="ph-badge void">무효</span>' : ''}
        ${!isVoid && corrections ? '<span class="ph-badge fix">정정됨</span>' : ''}
      </div>
      <strong class="ph-title">${escapeHtml(event.title)}</strong>
      ${event.content ? `<p>${escapeHtml(event.content)}</p>` : ''}
      ${facts ? `<p class="ph-facts">${facts}</p>` : ''}
      ${corrections ? `<ul class="ph-corrections">${corrections}</ul>` : ''}
      ${actions ? `<div class="ph-actions">${actions}</div>` : ''}
      <div data-history-editor="${event.eventId}"></div>
    </li>`;
  };

  const createForm = () => `
    <form class="ph-form" data-history-create hidden>
      <div class="ph-grid">
        <div class="field"><label for="ph-type">유형 <b>*</b></label><select id="ph-type" name="eventType" required>${CREATABLE.map((type) => `<option value="${type}">${TYPES[type]}</option>`).join('')}</select></div>
        <div class="field"><label for="ph-date">발생일 <b>*</b></label><input id="ph-date" name="occurredOn" type="date" max="${today()}" value="${today()}" required></div>
        <div class="field"><label for="ph-visibility">공개 범위</label><select id="ph-visibility" name="visibility"><option value="PRIVATE">비공개 (나만 보기)</option><option value="PUBLIC">공개 (판매글·다음 소유자)</option></select></div>
      </div>
      <div class="field"><label for="ph-title">제목 <b>*</b></label><input id="ph-title" name="title" maxlength="100" required placeholder="예: 배터리 교체"></div>
      <div class="field"><label for="ph-content">내용</label><input id="ph-content" name="content" placeholder="상세 내용을 적어 주세요."></div>
      <div class="ph-grid two">
        <div class="field"><label for="ph-provider">처리 업체</label><input id="ph-provider" name="serviceProvider"></div>
        <div class="field"><label for="ph-part">교체 부품</label><input id="ph-part" name="replacedPart"></div>
      </div>
      <p class="ph-help">개인정보(이름·연락처·주소·시리얼 번호)는 적지 마세요. 공개로 저장한 이력은 판매글에 보일 수 있어요.</p>
      <div class="product-form-actions"><button class="button" type="button" data-history-create-cancel>취소</button><button class="button primary" type="submit">이력 저장</button></div>
    </form>`;

  const render = () => {
    const box = root();
    if (!box) return;
    const list = state.events.length
      ? `<ol class="ph-list">${state.events.map(eventItem).join('')}</ol>`
      : '<div class="product-status">아직 기록된 이력이 없어요. 구매·점검·수리 내역을 남기면 중고 거래 때 신뢰를 줄 수 있어요.</div>';
    box.innerHTML = `
      <div class="ph-top"><div><h2>제품 이력</h2><p>기록은 삭제되지 않고, 잘못된 내용은 정정하거나 무효로 표시해요.</p></div><button class="button primary" type="button" data-history-create-open>＋ 이력 추가</button></div>
      ${createForm()}${list}`;
  };

  const load = async (productId) => {
    state.productId = productId;
    const box = root();
    if (!box) return;
    box.innerHTML = '<div class="product-status">이력을 불러오는 중입니다.</div>';
    try {
      await loadMe();
      state.events = (await api(`/api/v1/products/${productId}/events`)) || [];
      render();
    } catch (error) {
      box.innerHTML = `<div class="product-status"><strong class="product-empty-title">이력을 불러오지 못했습니다.</strong>${escapeHtml(error.message)}<br><button class="button" type="button" data-history-retry>다시 시도</button></div>`;
    }
  };

  const openEditor = (eventId, mode) => {
    document.querySelectorAll('[data-history-editor]').forEach((el) => el.replaceChildren());
    const event = state.events.find((item) => item.eventId === eventId);
    const host = document.querySelector(`[data-history-editor="${eventId}"]`);
    if (!event || !host) return;
    state.editing = { eventId, mode };
    const reason = '<div class="field"><label>사유 <b>*</b></label><input name="reason" maxlength="500" required placeholder="왜 바꾸나요?"></div>';
    host.innerHTML = mode === 'void'
      ? `<form class="ph-form" data-history-editor-form>${reason}<div class="product-form-actions"><button class="button" type="button" data-history-editor-cancel>취소</button><button class="button danger" type="submit">무효 처리</button></div></form>`
      : `<form class="ph-form" data-history-editor-form>
          <div class="field"><label>제목 <b>*</b></label><input name="title" maxlength="100" required value="${escapeHtml(event.title)}"></div>
          <div class="field"><label>내용</label><input name="content" value="${escapeHtml(event.content)}"></div>
          <div class="ph-grid two"><div class="field"><label>처리 업체</label><input name="serviceProvider" value="${escapeHtml(event.serviceProvider)}"></div><div class="field"><label>교체 부품</label><input name="replacedPart" value="${escapeHtml(event.replacedPart)}"></div></div>
          ${reason}<div class="product-form-actions"><button class="button" type="button" data-history-editor-cancel>취소</button><button class="button primary" type="submit">정정 저장</button></div>
        </form>`;
    host.querySelector('input')?.focus();
  };

  const clean = (value) => (value ?? '').trim() || null;

  document.addEventListener('pastport:product-detail-rendered', (event) => load(event.detail.productId));

  document.addEventListener('click', async (event) => {
    const target = event.target;
    if (!target.closest?.('[data-product-history]')) return;
    const visibility = target.closest('[data-history-visibility]');
    if (visibility) {
      visibility.disabled = true;
      try {
        await api(`/api/v1/events/${visibility.dataset.historyVisibility}/visibility`, {
          method: 'PUT',
          body: JSON.stringify({ visibility: visibility.dataset.visibilityNext })
        });
        toast(visibility.dataset.visibilityNext === 'PUBLIC' ? '이력을 공개로 바꿨습니다.' : '이력을 비공개로 바꿨습니다.');
        await load(state.productId);
      } catch (error) {
        toast(error.status === 401 ? '로그인이 필요합니다.' : error.message, 'error');
        visibility.disabled = false;
      }
      return;
    }
    if (target.closest('[data-history-retry]')) load(state.productId);
    if (target.closest('[data-history-create-open]')) {
      const form = root().querySelector('[data-history-create]');
      form.hidden = false;
      form.querySelector('input[name="title"]')?.focus();
    }
    if (target.closest('[data-history-create-cancel]')) root().querySelector('[data-history-create]').hidden = true;
    const correct = target.closest('[data-history-correct]');
    if (correct) openEditor(Number(correct.dataset.historyCorrect), 'correct');
    const voided = target.closest('[data-history-void]');
    if (voided) openEditor(Number(voided.dataset.historyVoid), 'void');
    if (target.closest('[data-history-editor-cancel]')) {
      document.querySelectorAll('[data-history-editor]').forEach((el) => el.replaceChildren());
      state.editing = null;
    }
  });

  document.addEventListener('submit', async (event) => {
    const form = event.target;
    if (!form.closest?.('[data-product-history]')) return;
    event.preventDefault();
    const submit = form.querySelector('[type="submit"]');
    const data = Object.fromEntries(new FormData(form));
    submit.disabled = true;
    try {
      if (form.matches('[data-history-create]')) {
        await api(`/api/v1/products/${state.productId}/events`, {
          method: 'POST',
          body: JSON.stringify({
            productId: state.productId,
            eventType: data.eventType,
            occurredOn: data.occurredOn,
            title: data.title.trim(),
            content: clean(data.content),
            serviceProvider: clean(data.serviceProvider),
            replacedPart: clean(data.replacedPart),
            visibility: data.visibility
          })
        });
        toast('이력을 저장했습니다.');
      } else if (form.matches('[data-history-editor-form]') && state.editing) {
        const { eventId, mode } = state.editing;
        if (mode === 'void') {
          await api(`/api/v1/events/${eventId}/void`, { method: 'POST', body: JSON.stringify({ reason: data.reason.trim() }) });
          toast('이력을 무효 처리했습니다.');
        } else {
          await api(`/api/v1/events/${eventId}/correct`, {
            method: 'PUT',
            body: JSON.stringify({
              reason: data.reason.trim(),
              title: data.title.trim(),
              content: clean(data.content),
              serviceProvider: clean(data.serviceProvider),
              replacedPart: clean(data.replacedPart)
            })
          });
          toast('이력을 정정했습니다.');
        }
        state.editing = null;
      }
      await load(state.productId);
    } catch (error) {
      toast(error.status === 401 ? '로그인이 필요합니다.' : error.message, 'error');
      submit.disabled = false;
    }
  });
})();
