// Explicit private server saves and authenticated draft resumption. Draft content stays out of browser storage.
(() => {
  'use strict';
  let root;
  let productId = null;
  let product = null;
  let draftId = null;
  let selected = [];
  let grade = null;
  let version = null;
  let revision = 0;
  let savedRevision = 0;
  let savedAt = null;
  let saving = false;
  let loading = false;
  let deleting = null;
  let confirming = false;
  let initialized = false;
  let productLoad = null;
  let draftListLoad = null;
  let listing = false;
  let draftItems = [];
  let draftPage = -1;
  let draftHasNext = false;
  let dragImage = null;
  const imageUrls = new Map();
  const find = selector => root.querySelector(selector);
  const dirty = () => revision !== savedRevision;
  const busy = () => saving || loading || deleting !== null || confirming;
  const productPath = id => '/api/v1/products/' + id + '/listing-draft';
  const formatTime = value => new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(value));

  async function api(url, options = {}, allowMissing = false) {
    const headers = new Headers(options.headers || {});
    const csrf = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-Token';
    if (csrf && options.method && options.method !== 'GET') headers.set(csrfHeader, csrf);
    if (options.body) headers.set('Content-Type', 'application/json');
    const request = window.PastportApi?.fetch || window.fetch.bind(window);
    const response = await request(url, { ...options, headers, credentials: 'same-origin', cache: 'no-store' });
    if (!response.ok && !(allowMissing && response.status === 404)) {
      const payload = await response.json().catch(() => ({}));
      let message = payload.message || '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.';
      if (response.status === 401) message = '판매글을 작성하려면 먼저 로그인해 주세요.';
      if (response.status === 404) message = '이 제품이나 저장본을 사용할 수 없어요. 현재 보유한 제품을 다시 확인해 주세요.';
      if (response.status === 409) message = '저장본이 변경되었어요. 작성 중인 내용은 유지했어요. 저장본을 다시 불러온 뒤 확인해 주세요.';
      const failure = new Error(message); failure.status = response.status; throw failure;
    }
    return response;
  }

  function error(message = '') {
    find('[data-sale-error]').hidden = !message;
    find('[data-sale-error] span').textContent = message;
  }

  function counters() {
    find('[data-sale-title-count]').textContent = find('[data-sale-title]').value.length + ' / 60자';
    find('[data-sale-description-count]').textContent = find('[data-sale-description]').value.length + ' / 2,000자';
  }

  function status() {
    const saved = find('[data-sale-saved]');
    if (deleting !== null) saved.textContent = '임시저장본을 삭제하는 중이에요…';
    else if (loading) saved.textContent = '제품과 저장본을 불러오는 중이에요…';
    else if (saving) saved.textContent = '비공개로 저장하는 중이에요…';
    else if (savedAt) saved.textContent = '마지막 임시저장 ' + formatTime(savedAt) + (dirty() ? ' · 저장하지 않은 변경이 있어요.' : ' · 비공개 저장 완료');
    else saved.textContent = dirty() ? '작성한 내용을 임시저장해 주세요.' : '아직 임시저장하지 않았어요.';
    find('[data-sale-save]').disabled = !product || busy();
    find('[data-sale-save]').textContent = saving ? '저장 중…' : '임시저장';
    find('[data-sale-product]').disabled = busy();
    find('[data-sale-reload]').hidden = !product;
    find('[data-sale-reload]').disabled = busy();
    find('[data-sale-delete]').hidden = draftId === null;
    find('[data-sale-delete]').disabled = busy();
    find('[data-sale-drafts-refresh]').disabled = listing || busy();
    find('[data-sale-drafts-more]').disabled = listing || busy();
    root.querySelectorAll('[data-sale-open-draft], [data-sale-delete-draft]').forEach(button => { button.disabled = busy(); });
    root.querySelectorAll('[data-sale-title], [data-sale-price], [data-sale-description], [data-sale-grade]').forEach(input => { input.disabled = !product || loading || deleting !== null; });
    root.querySelectorAll('[data-sale-image]').forEach(button => { button.disabled = loading || deleting !== null; });
  }

  function change() { revision++; counters(); status(); }

  async function confirmAction(message, action) {
    const dialog = find('[data-sale-confirm]');
    find('[data-sale-confirm-message]').textContent = message;
    find('[data-sale-confirm-action]').textContent = action;
    confirming = true; status();
    try {
      return await new Promise(resolve => {
        dialog.returnValue = 'cancel';
        dialog.addEventListener('close', () => resolve(dialog.returnValue === 'confirm'), { once: true });
        dialog.showModal();
      });
    } finally { confirming = false; status(); }
  }

  function reset() {
    product = null; draftId = null; selected = []; grade = null; version = null;
    revision = 0; savedRevision = 0; savedAt = null;
    imageUrls.forEach(url => URL.revokeObjectURL(url)); imageUrls.clear();
    find('[data-sale-title]').value = '';
    find('[data-sale-price]').value = '';
    find('[data-sale-description]').value = '';
    find('[data-sale-product-card]').hidden = true;
    find('[data-sale-specs]').hidden = true;
    find('[data-sale-photos]').replaceChildren();
    find('[data-sale-photos-empty]').hidden = false;
    find('[data-sale-photos-empty]').textContent = '제품을 선택하면 등록된 사진이 보여요.';
    find('[data-sale-photo-count]').textContent = '선택한 사진 0장';
    root.querySelectorAll('[data-sale-grade]').forEach(button => { button.classList.remove('on'); button.setAttribute('aria-checked', 'false'); });
    find('[data-sale-product-step]').className = 'now';
    find('[data-sale-content-step]').className = '';
    counters(); status();
  }

  function setLocation(id) {
    const url = new URL(window.location.href);
    if (id) url.searchParams.set('draftProduct', String(id));
    else url.searchParams.delete('draftProduct');
    window.history.replaceState(window.history.state, '', url);
  }

  function applyDraft(draft) {
    if (!draft) return;
    draftId = draft.draftId; version = draft.version; savedAt = draft.updatedAt;
    find('[data-sale-title]').value = draft.title ?? '';
    find('[data-sale-price]').value = draft.price == null ? '' : String(draft.price);
    find('[data-sale-description]').value = draft.description ?? '';
    grade = draft.conditionGrade;
    selected = draft.selectedImageIds.filter(id => product.images.some(image => image.imageId === id));
    if (selected.length !== draft.selectedImageIds.length) {
      revision++;
      error('저장 후 삭제된 사진이 있어요. 저장본을 다시 불러와 최신 선택을 확인해 주세요.');
    }
    root.querySelectorAll('[data-sale-grade]').forEach(button => {
      const on = Number(button.dataset.saleGrade) === grade;
      button.classList.toggle('on', on); button.setAttribute('aria-checked', String(on));
    });
    counters();
  }

  function renderProduct() {
    find('[data-sale-product-card]').hidden = false;
    find('[data-sale-product-name]').textContent = product.title;
    find('[data-sale-product-meta]').textContent = [product.manufacturer, product.modelName, product.purchaseDate ? product.purchaseDate + ' 구매' : '구매일 미등록'].join(' · ');
    const specs = product.specifications || {};
    const entries = [['CPU', specs.cpu], ['GPU', specs.gpu], ['메모리', specs.ramGb == null ? null : specs.ramGb + 'GB'], ['저장장치', specs.storageGb == null ? null : specs.storageGb + 'GB ' + (specs.storageType || '')], ['화면', specs.display], ['운영체제', specs.os], ['배터리 상태', specs.batteryHealthPercent == null ? null : specs.batteryHealthPercent + '%'], ['보증 만료일', product.warrantyExpiresAt]];
    const list = find('[data-sale-specs]'); list.replaceChildren();
    entries.forEach(([label, value]) => {
      const item = document.createElement('div');
      const key = document.createElement('dt'); key.textContent = label;
      const text = document.createElement('dd'); text.textContent = value ?? '미등록';
      item.append(key, text); list.append(item);
    });
    list.hidden = false;
    find('[data-sale-product-step]').className = 'done';
    find('[data-sale-content-step]').className = 'now';
  }

  function renderPhotos() {
    const grid = find('[data-sale-photos]'); grid.replaceChildren();
    const ordered = [...selected.map(id => product.images.find(image => image.imageId === id)).filter(Boolean), ...product.images.filter(image => !selected.includes(image.imageId))];
    ordered.forEach(image => {
      const position = selected.indexOf(image.imageId);
      const card = document.createElement('div'); card.className = 'sl-photo-choice';
      const choice = document.createElement('button'); choice.type = 'button';
      choice.className = 'sl-photo' + (position >= 0 ? ' selected' : '') + (position === 0 ? ' main' : '');
      choice.dataset.saleImage = String(image.imageId);
      choice.setAttribute('aria-pressed', String(position >= 0));
      choice.setAttribute('aria-label', '제품 사진 ' + image.imageId + (position >= 0 ? ' 선택 해제' : ' 선택'));
      choice.draggable = position >= 0;
      const url = imageUrls.get(image.imageId);
      if (url) { const img = document.createElement('img'); img.src = url; img.alt = ''; choice.append(img); }
      else { const placeholder = document.createElement('div'); placeholder.className = 'laptop'; placeholder.setAttribute('aria-hidden', 'true'); choice.append(placeholder); }
      if (position >= 0) { const order = document.createElement('i'); order.textContent = String(position + 1); choice.append(order); }
      if (position === 0) { const main = document.createElement('em'); main.textContent = '대표'; choice.append(main); }
      card.append(choice);
      if (position >= 0) {
        const controls = document.createElement('div'); controls.className = 'sl-photo-controls';
        [['대표로', 'main', position === 0], ['←', 'left', position === 0], ['→', 'right', position === selected.length - 1]].forEach(([label, action, disabled]) => {
          const button = document.createElement('button'); button.type = 'button'; button.textContent = label;
          button.dataset.saleMove = action; button.dataset.imageId = String(image.imageId); button.disabled = disabled;
          button.setAttribute('aria-label', '사진 ' + image.imageId + ' ' + ({ main: '대표로 설정', left: '앞으로 이동', right: '뒤로 이동' }[action]));
          controls.append(button);
        });
        card.append(controls);
      }
      grid.append(card);
    });
    find('[data-sale-photos-empty]').hidden = product.images.length > 0;
    find('[data-sale-photos-empty]').textContent = '등록된 제품 사진이 없어요. 사진 없이도 임시저장할 수 있어요.';
    find('[data-sale-photo-count]').textContent = '선택한 사진 ' + selected.length + '장' + (selected.length ? ' · 첫 번째 사진이 대표 사진이에요.' : '');
    status();
  }

  function renderDrafts() {
    const list = find('[data-sale-drafts]'); list.replaceChildren();
    draftItems.forEach(item => {
      const row = document.createElement('li'); row.className = 'sl-draft-item';
      row.classList.toggle('is-current', item.productId === productId);
      const info = document.createElement('div'); info.className = 'sl-draft-info';
      const title = document.createElement('h3'); title.textContent = item.title || '제목 없는 임시저장본';
      const meta = document.createElement('p');
      meta.textContent = item.productTitle + ' · ' + (item.price == null ? '가격 미입력' : new Intl.NumberFormat('ko-KR').format(item.price) + '원') + ' · ' + formatTime(item.updatedAt) + ' 저장';
      info.append(title, meta);
      const actions = document.createElement('div'); actions.className = 'sl-draft-actions';
      const open = document.createElement('button'); open.type = 'button'; open.className = 'sc-btn primary';
      open.dataset.saleOpenDraft = String(item.draftId); open.textContent = '이어서 작성';
      open.setAttribute('aria-label', item.productTitle + ' 임시저장본 이어서 작성');
      const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'sc-btn sl-draft-delete';
      remove.dataset.saleDeleteDraft = String(item.draftId); remove.textContent = '삭제';
      remove.setAttribute('aria-label', item.productTitle + ' 임시저장본 삭제');
      actions.append(open, remove); row.append(info, actions); list.append(row);
    });
    find('[data-sale-drafts-more]').hidden = !draftHasNext;
    status();
  }

  async function refreshDrafts(append = false) {
    draftListLoad?.abort();
    const controller = new AbortController(); draftListLoad = controller;
    const page = append ? draftPage + 1 : 0;
    listing = true; status();
    find('[data-sale-drafts-status]').textContent = '임시저장본을 불러오는 중이에요…';
    try {
      const response = await api('/api/v1/listing-drafts?page=' + page + '&size=20', { signal: controller.signal });
      const { data } = await response.json();
      if (controller.signal.aborted) return;
      draftItems = append ? [...new Map([...draftItems, ...data.items].map(item => [item.draftId, item])).values()] : data.items;
      draftPage = page; draftHasNext = data.page.hasNext;
      find('[data-sale-drafts-count]').textContent = '총 ' + data.page.totalElements + '개';
      find('[data-sale-drafts-status]').textContent = draftItems.length ? '' : '아직 저장한 글이 없어요. 아래에서 제품을 선택해 작성을 시작해 주세요.';
      renderDrafts();
    } catch (failure) {
      if (failure.name !== 'AbortError') find('[data-sale-drafts-status]').textContent = '저장 목록을 불러오지 못했어요. 새로고침으로 다시 시도해 주세요.';
    } finally {
      if (!controller.signal.aborted) { listing = false; status(); }
    }
  }

  async function chooseProduct(next, discard = false) {
    if (busy()) return;
    if (!discard && dirty() && !await confirmAction('임시저장하지 않은 내용이 있어요. 저장본을 불러오면 작성 중인 내용이 바뀌어요. 계속할까요?', '불러오기')) {
      find('[data-sale-product]').value = productId || ''; return;
    }
    productLoad?.abort();
    const controller = new AbortController(); productLoad = controller;
    productId = next ? Number(next) : null;
    loading = Boolean(productId); reset(); error();
    find('[data-sale-product]').value = productId || '';
    setLocation(productId);
    if (!productId) { status(); renderDrafts(); return; }
    try {
      const draftResponse = await api(productPath(productId), { signal: controller.signal }, true);
      const draft = draftResponse.status === 404 ? null : (await draftResponse.json()).data;
      // Product ownership is checked again even when no accessible draft exists.
      const response = await api(productPath(productId) + '/composition', { signal: controller.signal });
      const { data } = await response.json();
      if (controller.signal.aborted) return;
      product = data;
      const select = find('[data-sale-product]');
      if (![...select.options].some(option => Number(option.value) === productId)) select.add(new Option(product.title, productId));
      select.value = String(productId);
      applyDraft(draft); renderProduct(); renderPhotos(); renderDrafts();
      product.images.forEach(async image => {
        try {
          const photo = await api(image.url, { signal: controller.signal });
          const blob = await photo.blob();
          if (controller.signal.aborted) return;
          imageUrls.set(image.imageId, URL.createObjectURL(blob)); renderPhotos();
        } catch (failure) {
          if (failure.name !== 'AbortError' && !controller.signal.aborted) error('일부 사진을 불러오지 못했어요. 저장본을 다시 불러오거나 잠시 후 다시 시도해 주세요.');
        }
      });
    } catch (failure) { if (failure.name !== 'AbortError') error(failure.message); }
    finally { if (!controller.signal.aborted) { loading = false; status(); } }
  }

  async function save(event) {
    event.preventDefault();
    if (!product || busy()) return;
    error();
    const rawPrice = find('[data-sale-price]').value.trim();
    const validPrice = /^(?:[1-9]\d{0,11}|[1-9]\d{0,2}(?:,\d{3}){1,3})$/;
    if (rawPrice && !validPrice.test(rawPrice)) { error('가격은 1원 이상, 최대 12자리의 정수로 입력해 주세요.'); find('[data-sale-price]').focus(); return; }
    const price = rawPrice ? Number(rawPrice.replaceAll(',', '')) : null;
    const content = { title: find('[data-sale-title]').value || null, description: find('[data-sale-description]').value || null, price, conditionGrade: grade, selectedImageIds: [...selected], version };
    const savingRevision = revision;
    saving = true; status();
    try {
      const path = productPath(productId) + (draftId === null ? '' : '?draftId=' + draftId);
      const response = await api(path, { method: draftId === null ? 'POST' : 'PUT', body: JSON.stringify(content) });
      const { data } = await response.json();
      draftId = data.draftId; version = data.version; savedAt = data.updatedAt; savedRevision = savingRevision;
      await refreshDrafts();
    } catch (failure) { error(failure.message); }
    finally { saving = false; status(); }
  }

  async function deleteDraft(item) {
    if (busy()) return;
    const current = item.productId === productId;
    const message = '임시저장본을 삭제할까요? 제품과 등록된 사진은 그대로 유지돼요.' + (current && dirty() ? '\n화면에서 작성 중인 변경 내용도 지워져요.' : '');
    if (!await confirmAction(message, '삭제하기')) return;
    deleting = item.draftId; status(); error();
    let removed = false;
    try {
      await api(productPath(item.productId) + '?draftId=' + item.draftId + '&version=' + item.version, { method: 'DELETE' });
      removed = true;
      await refreshDrafts();
    } catch (failure) {
      error(failure.message);
      await refreshDrafts();
    } finally { deleting = null; status(); }
    if (removed && current) await chooseProduct(String(productId), true);
  }

  async function loadProducts() {
    try {
      const response = await api('/api/v1/listing-draft-products');
      const { data } = await response.json();
      const select = find('[data-sale-product]'); select.replaceChildren(new Option('판매할 제품을 선택해 주세요.', ''));
      data.forEach(item => select.add(new Option(item.title, item.productId)));
      find('[data-sale-product-empty]').hidden = data.length > 0;
    } catch (failure) {
      find('[data-sale-product]').replaceChildren(new Option('제품을 불러올 수 없어요.', '')); error(failure.message);
    }
  }

  async function init() {
    if (initialized || !document.querySelector('#sell [data-sale-form]')) return;
    initialized = true; root = document.querySelector('#sell');
    root.addEventListener('input', event => { if (event.target.matches('[data-sale-title], [data-sale-price], [data-sale-description]')) change(); });
    find('[data-sale-product]').addEventListener('change', event => chooseProduct(event.target.value));
    find('[data-sale-form]').addEventListener('submit', save);
    find('[data-sale-reload]').addEventListener('click', () => chooseProduct(String(productId)));
    find('[data-sale-delete]').addEventListener('click', () => deleteDraft({ draftId, productId, version }));
    find('[data-sale-drafts-refresh]').addEventListener('click', () => refreshDrafts());
    find('[data-sale-drafts-more]').addEventListener('click', () => refreshDrafts(true));
    root.addEventListener('click', event => {
      const open = event.target.closest('[data-sale-open-draft]');
      if (open) { const item = draftItems.find(draft => draft.draftId === Number(open.dataset.saleOpenDraft)); if (item) chooseProduct(String(item.productId)); return; }
      const remove = event.target.closest('[data-sale-delete-draft]');
      if (remove) { const item = draftItems.find(draft => draft.draftId === Number(remove.dataset.saleDeleteDraft)); if (item) deleteDraft(item); return; }
      if (!product || loading || deleting !== null) return;
      const gradeButton = event.target.closest('[data-sale-grade]');
      if (gradeButton && !gradeButton.disabled) {
        const next = Number(gradeButton.dataset.saleGrade); grade = grade === next ? null : next;
        root.querySelectorAll('[data-sale-grade]').forEach(button => { const on = Number(button.dataset.saleGrade) === grade; button.classList.toggle('on', on); button.setAttribute('aria-checked', String(on)); });
        change();
      }
      const image = event.target.closest('[data-sale-image]');
      if (image && !image.disabled) {
        const id = Number(image.dataset.saleImage);
        if (selected.includes(id)) selected = selected.filter(value => value !== id);
        else if (selected.length < 20) selected.push(id);
        else { error('사진은 최대 20장까지 선택할 수 있어요.'); return; }
        renderPhotos(); change();
      }
      const move = event.target.closest('[data-sale-move]');
      if (move && !move.disabled) {
        const id = Number(move.dataset.imageId); const index = selected.indexOf(id);
        const next = move.dataset.saleMove === 'main' ? 0 : index + (move.dataset.saleMove === 'left' ? -1 : 1);
        selected.splice(index, 1); selected.splice(next, 0, id); renderPhotos(); change();
      }
    });
    root.addEventListener('dragstart', event => { const image = event.target.closest('[data-sale-image]'); if (image && image.draggable && !busy()) { dragImage = Number(image.dataset.saleImage); event.dataTransfer.setData('text/plain', String(dragImage)); } });
    root.addEventListener('dragover', event => { const image = event.target.closest('[data-sale-image]'); if (!busy() && image && selected.includes(Number(image.dataset.saleImage))) event.preventDefault(); });
    root.addEventListener('drop', event => {
      const image = event.target.closest('[data-sale-image]'); const target = Number(image?.dataset.saleImage);
      if (busy() || !selected.includes(dragImage) || !selected.includes(target)) return;
      event.preventDefault(); const index = selected.indexOf(target); selected = selected.filter(id => id !== dragImage); selected.splice(index, 0, dragImage); dragImage = null; renderPhotos(); change();
    });
    root.addEventListener('dragend', () => { dragImage = null; });
    window.addEventListener('beforeunload', event => { if (dirty()) { event.preventDefault(); event.returnValue = ''; } });
    await Promise.all([loadProducts(), refreshDrafts()]);
    const requested = new URL(window.location.href).searchParams.get('draftProduct');
    if (requested && /^[1-9]\d*$/.test(requested)) await chooseProduct(requested);
    status();
  }

  function onPageReady() {
    const requested = new URL(window.location.href).searchParams.get('draftProduct');
    if (requested && /^[1-9]\d*$/.test(requested) && typeof show === 'function') show('sell');
    if (document.querySelector('#sell.active')) init();
  }
  document.addEventListener('pastport:page-loaded', event => { if (event.detail.id === 'sell') onPageReady(); });
  document.addEventListener('pastport:page-shown', event => {
    if (event.detail.id !== 'sell') return;
    if (!initialized) init();
    else if (!busy()) refreshDrafts();
  });
  if (document.querySelector('#sell [data-sale-form]')) onPageReady();
})();
