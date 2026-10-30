(() => {
  'use strict';

  let active = null;
  const root = () => document.querySelector('[data-deals-completion]');
  const labels = { PURCHASE: '구매', INSPECTION: '점검', REPAIR: '수리', PART_REPLACEMENT: '부품 교체', SPEC_CHANGE: '사양 변경', TRANSFER: '소유권 이전' };

  async function api(path, options = {}) {
    const response = await (window.PastportApi?.fetch || window.fetch.bind(window))(path, {
      credentials: 'same-origin', cache: 'no-store', ...options,
      headers: { 'Content-Type': 'application/json', ...(options.headers || {}) }
    });
    const payload = await response.json().catch(() => null);
    if (!response.ok) {
      const error = new Error(payload?.message || '거래 상태를 확인하지 못했어요. 다시 확인해 주세요.');
      error.status = response.status;
      throw error;
    }
    return payload?.data;
  }

  function element(tag, text, className) {
    const node = document.createElement(tag);
    if (text != null) node.textContent = text;
    if (className) node.className = className;
    return node;
  }

  function current(state) { return active === state && state.options.isCurrent() && root(); }
  function button(text, action, primary = false) {
    const node = element('button', text, 'sc-btn sm' + (primary ? ' primary' : ''));
    node.type = 'button';
    node.addEventListener('click', action);
    return node;
  }

  function clear() {
    active?.controller.abort();
    active = null;
    const container = root();
    if (container) { container.replaceChildren(); container.hidden = true; }
  }

  function base(state) {
    const container = root();
    if (!container || !current(state)) return null;
    container.hidden = false;
    container.replaceChildren(element('h3', '거래 완료 확인'));
    return container;
  }

  function completed(state) {
    const container = base(state);
    if (!container) return;
    container.append(element('p', '거래가 완료되어 제품과 누적 기록이 구매자에게 이전됐어요.'));
    container.append(element('p', '기존 대화는 계속 확인할 수 있어요.', 'sub'));
    if (state.options.role === 'buyer') container.append(button('내 제품 보기', () => { if (typeof show === 'function') show('home'); }));
  }

  // 제품·이력은 P4가 제공한 공개 DTO만 표시한다. 관리용 제품·증빙 API는 호출하지 않는다.
  function evidence(state) {
    const context = state.context;
    const detail = element('details');
    detail.open = !!state.evidenceOpen;
    detail.addEventListener('toggle', () => { if (detail.isConnected) state.evidenceOpen = detail.open; });
    detail.append(element('summary', '확인할 제품 정보와 이력'));
    const listing = context.listing || {};
    const product = listing.product || {};
    detail.append(element('p', [product.manufacturer, product.modelName].filter(Boolean).join(' ') || listing.content?.title || '제품 정보'));
    const specs = product.specifications || product.specs || {};
    const specLabels = { cpu: 'CPU', cpuSpec: 'CPU', ramGb: '메모리', storageGb: '저장공간', storageType: '저장장치', os: '운영체제', osName: '운영체제' };
    const values = element('ul');
    Object.entries(specLabels).forEach(([key, label]) => {
      if (specs[key] != null) values.append(element('li', `${label}: ${specs[key]}${['ramGb', 'storageGb'].includes(key) ? 'GB' : ''}`));
    });
    detail.append(values);
    const history = listing.history?.items || [];
    const list = element('ul');
    history.forEach(item => list.append(element('li', `${item.occurredOn || '날짜 미등록'} · ${labels[item.eventType] || '제품 이력'}${item.status === 'VOID' ? ' · 무효' : ''}${item.corrections?.length ? ' · 정정 기록 있음' : ''}`)));
    detail.append(history.length ? list : element('p', '등록된 이력이 없어요.', 'sub'));
    if (listing.history?.page?.totalPages > 1) detail.append(element('p', '전체 이력은 판매글에서 함께 확인해 주세요.', 'sub'));
    return detail;
  }

  async function action(state, path, body) {
    if (!current(state) || state.busy) return;
    state.busy = true;
    root().querySelectorAll('button,input,select').forEach(node => { node.disabled = true; });
    state.message = '';
    try {
      await api(path, { method: 'POST', body: JSON.stringify(body) });
      state.acknowledgedVersion = null;
    } catch (error) {
      state.acknowledgedVersion = null;
      state.message = error.status === 409 ? '제품 정보나 거래 상태가 바뀌었어요. 최신 내용을 다시 확인해 주세요.' : error.message;
    } finally {
      state.busy = false;
      state.renderKey = null;
      if (current(state)) await state.options.onChanged();
    }
  }

  function renderChanges(state, container) {
    const changes = state.changes;
    if (!changes || !state.context.unreviewedChangeCount) return;
    const section = element('details');
    section.open = true;
    section.append(element('summary', `확인이 필요한 변경 ${state.context.unreviewedChangeCount}건`));
    changes.items?.forEach(change => {
      const row = element('div', null, 'dl-completion-change');
      const facts = change.changeFacts || {};
      row.append(element('p', `${labels[facts.eventType] || '제품 이력'} · ${facts.occurredOn || '발생일 미등록'}${change.decision ? ' · 확인 완료' : ''}`));
      const before = facts.specsBefore || {};
      const after = facts.specsAfter || {};
      const specLabels = { cpu: 'CPU', gpu: 'GPU', ramGb: '메모리', storageGb: '저장공간', storageType: '저장장치', display: '화면', batteryHealthPercent: '배터리 상태', os: '운영체제' };
      Object.entries(specLabels).forEach(([key, label]) => {
        if (after[key] != null && before[key] !== after[key]) row.append(element('p', `${label}: ${before[key] ?? '미등록'} → ${after[key]}`, 'sub'));
      });
      if (!change.decision && state.options.role === 'buyer') row.append(button('변경 확인 · 예약 유지', () => {
        if (confirm('이 변경 내용을 확인했고 예약을 유지하시겠습니까?')) action(state, `/api/v1/trades/${state.requestId}/change-reviews/${change.eventId}`, { decision: 'KEEP' });
      }));
      section.append(row);
    });
    const paging = element('div', null, 'dl-completion-actions');
    const number = changes.page?.number || 0;
    if (number > 0) paging.append(button('이전 변경', () => { state.reviewPage--; load(state); }));
    if (number + 1 < (changes.page?.totalPages || 0)) paging.append(button('다음 변경', () => { state.reviewPage++; load(state); }));
    section.append(paging);
    if (state.options.role === 'seller') section.append(element('p', '구매자가 변경 내용을 확인하면 거래 완료를 진행할 수 있어요.', 'sub'));
    container.append(section);
  }

  function render(state) {
    const renderKey = JSON.stringify([state.context, state.changes, state.message]);
    if (state.renderKey === renderKey && root()?.childElementCount) return;
    state.renderKey = renderKey;
    const container = base(state);
    if (!container) return;
    const context = state.context;
    if (!context) {
      container.append(element('p', state.message || '거래 상태를 확인하고 있어요.', 'sub'));
      if (state.message) container.append(button('다시 확인', () => load(state)));
      return;
    }
    const status = element('p', `${context.sellerConfirmedCurrentVersion ? '판매자 확인 완료' : '판매자 확인 대기'} · ${context.buyerConfirmedCurrentVersion ? '구매자 확인 완료' : '구매자 확인 대기'}`);
    status.setAttribute('role', 'status');
    container.append(status, evidence(state));
    renderChanges(state, container);
    if (state.message) { const error = element('p', state.message, 'dl-completion-error'); error.setAttribute('role', 'alert'); container.append(error); }
    const mine = state.options.role === 'seller' ? context.sellerConfirmedCurrentVersion : context.buyerConfirmedCurrentVersion;
    const label = element('label', null, 'dl-completion-ack');
    const checkbox = element('input');
    checkbox.type = 'checkbox';
    checkbox.checked = mine || state.acknowledgedVersion === context.factsVersion;
    checkbox.disabled = state.busy || mine || !context.completionAllowed;
    const submit = button(mine ? '상대방 확인 대기 중' : '거래 완료 확인', () => {
      if (state.acknowledgedVersion !== context.factsVersion || !current(state)) return;
      const text = state.options.role === 'seller' ? '양측 확인이 모이면 소유권 이전이 진행됩니다.' : '제품을 실제로 인수하고 현재 정보와 이력을 확인하셨나요?';
      if (confirm(`${state.options.title}\n${text}\n거래 완료를 확인하시겠습니까?`)) action(state, `/api/v1/trades/${state.requestId}/complete`, { confirmedFactsVersion: context.factsVersion });
    }, true);
    checkbox.addEventListener('change', () => { state.acknowledgedVersion = checkbox.checked ? context.factsVersion : null; submit.disabled = !checkbox.checked || mine || !context.completionAllowed; });
    label.append(checkbox, document.createTextNode('현재 제품 정보와 이력을 확인했습니다.'));
    submit.disabled = state.busy || mine || !context.completionAllowed || !checkbox.checked;
    const actions = element('div', null, 'dl-completion-actions');
    actions.append(submit, button('상태 새로고침', () => load(state)));
    container.append(label, actions);
    if (mine) container.append(element('p', '내 확인은 저장됐어요. 상대방이 같은 내용을 확인하면 이전이 완료됩니다.', 'sub'));
    const cancel = element('details');
    cancel.open = !!state.cancelOpen;
    cancel.addEventListener('toggle', () => { if (cancel.isConnected) state.cancelOpen = cancel.open; });
    cancel.append(element('summary', '예약 취소'));
    const cancelLabel = element('label', '취소 사유');
    const select = element('select');
    [['SELF_CIRCUMSTANCES','내 사정'],['MUTUAL_AGREEMENT','상대 요청으로 합의'],['COUNTERPART_UNREACHABLE','상대 연락 두절'],['CONDITION_MISMATCH','제품 상태가 설명과 다름']].forEach(([value, text]) => { const option = element('option', text); option.value = value; select.append(option); });
    select.value = state.cancelReason || 'SELF_CIRCUMSTANCES';
    select.addEventListener('change', () => { state.cancelReason = select.value; });
    cancelLabel.append(select);
    cancel.append(cancelLabel, button('예약 취소하기', () => {
      if (confirm(`${state.options.title}\n예약을 취소하시겠습니까?`)) action(state, `/api/v1/trades/${state.requestId}/cancel`, { reason: select.value });
    }));
    container.append(cancel);
  }

  async function load(state) {
    if (!current(state) || state.busy) return;
    const loadId = ++state.loadId;
    try {
      const context = await api(`/api/v1/trades/${state.requestId}/completion-context?page=0&size=20`, { signal: state.controller.signal });
      if (!current(state) || loadId !== state.loadId) return;
      if (state.context?.factsVersion !== context.factsVersion) {
        if (state.context) state.message = '제품 정보가 변경됐어요. 최신 내용을 다시 확인해 주세요.';
        state.acknowledgedVersion = null;
      }
      state.context = context;
      state.changes = context.unreviewedChangeCount ? await api(`/api/v1/trades/${state.requestId}/change-reviews?page=${state.reviewPage}&size=20`, { signal: state.controller.signal }) : null;
      if (current(state) && loadId === state.loadId) render(state);
    } catch (error) {
      if (!current(state) || loadId !== state.loadId || error.name === 'AbortError') return;
      state.context = null;
      state.acknowledgedVersion = null;
      state.message = error.message;
      render(state);
    }
  }

  async function mount(options) {
    const key = `${options.userId}:${options.roomId}:${options.request.requestId}:${options.role}`;
    if (active?.key !== key) {
      clear();
      active = { key, options, requestId: options.request.requestId, controller: new AbortController(), context: null, acknowledgedVersion: null, reviewPage: 0, busy: false, loadId: 0, message: '' };
      render(active);
    } else active.options = options;
    if (options.request.transactionStatus === 'COMPLETED') { completed(active); return; }
    await load(active);
  }

  document.addEventListener('pastport:page-shown', event => { if (event.detail.id !== 'deals') clear(); });
  window.PastportTradeCompletion = Object.freeze({ mount, clear });
})();
