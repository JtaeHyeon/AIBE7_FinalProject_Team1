(() => {
  'use strict';

  let currentRoom = null;
  let currentUserId = null;
  let polling = null;
  let rooms = [];
  let roomSequence = 0;
  let roomScope = null;
  let roomsGeneration = 0;
  let roomsController = null;
  let sessionGeneration = 0;
  let openGeneration = 0;
  let openController = null;

  const find = (selector) => document.querySelector('#deals ' + selector);
  const findAll = (selector) => document.querySelectorAll('#deals ' + selector);

  /** 인증된 API를 호출한다. 방·계정 전환의 취소 신호와 15초 제한으로 끝나지 않는 조회를 정리한다. */
  async function api(path, options = {}) {
    const fetcher = window.PastportApi?.fetch || window.fetch.bind(window);
    const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
    const timeout = AbortSignal.timeout(15000);
    const signal = options.signal ? AbortSignal.any([options.signal, timeout]) : timeout;
    const response = await fetcher(path, { credentials: 'same-origin', ...options, headers, signal });
    const payload = await response.json().catch(() => null);
    if (!response.ok) {
      const error = new Error(payload?.message || payload?.fieldErrors?.[0]?.message || '요청 처리에 실패했습니다.');
      error.status = response.status;
      error.code = payload?.code;
      throw error;
    }
    return payload?.data;
  }

  /** 요청 당시 세션의 사용자만 조회한다. 공유 상태 갱신은 세대를 검사한 호출자가 맡는다. */
  async function initUser(signal) {
    try {
      const user = await api('/api/auth/me', { method: 'GET', signal });
      return user?.id || null;
    } catch (error) {
      if (error.status === 401) return null;
      throw error;
    }
  }

  /** 마지막 목록 조회의 성공·실패만 표시한다. 계정 변경 전에 시작한 응답은 화면과 사용자 ID를 바꾸지 못한다. */
  async function loadRooms() {
    const container = find('[data-deals-rooms]');
    if (!container || !document.querySelector('#deals.active')) return false;
    const generation = ++roomsGeneration;
    roomsController?.abort();
    const controller = new AbortController();
    roomsController = controller;
    const isCurrent = () => generation === roomsGeneration && !controller.signal.aborted;
    try {
      const userId = await initUser(controller.signal);
      if (!isCurrent()) return false;
      currentUserId = userId;
      if (!currentUserId) {
        container.innerHTML = '<div style="padding:40px 20px;text-align:center;color:var(--muted)"><p style="margin-bottom:8px">로그인이 필요한 서비스입니다.</p><button type="button" class="sc-btn primary sm" data-page="auth">로그인하기</button></div>';
        return false;
      }
      const data = await api('/api/v1/chat-rooms?page=0&size=50', { method: 'GET', signal: controller.signal });
      if (!isCurrent()) return false;
      rooms = data?.items || [];
      if (rooms.length === 0) {
        container.innerHTML = '<p class="sub" style="padding:40px 20px;text-align:center;color:var(--muted)">진행 중인 대화가 없습니다.<br><small style="margin-top:6px;display:inline-block">마켓에서 관심 있는 상품에 문의해 보세요!</small></p>';
        return true;
      }
      renderRoomList();
      return true;
    } catch (e) {
      if (!isCurrent() || e.name === 'AbortError') return false;
      container.innerHTML = '<p class="sub" style="padding:20px;text-align:center;color:var(--muted)">대화방 목록을 불러오지 못했습니다.</p>';
      return false;
    }
  }

  // 글이 삭제됐거나(상태 없음) 판매 중지된 방은 구매 요청·소유권 이전을 막는다. 대화는 명세대로 계속할 수 있다.
  const isClosed = (status) => !status || status === 'CANCELLED';
  const statusLabel = (status) => ({ ACTIVE: '판매 중', RESERVED: '예약 중', SOLD: '판매 완료', CANCELLED: '판매 중지' }[status] || '삭제·종료된 글');
  const statusClass = (status) => (status === 'RESERVED' ? 'warn' : status === 'ACTIVE' ? 'ok' : '');

  /** 로그아웃·세션 만료 시 조회 세대를 먼저 바꾸고 이전 계정의 목록·대화·이동 요청을 취소한다. */
  function resetDeals() {
    sessionGeneration++;
    roomsGeneration++;
    roomsController?.abort();
    cancelOpen();
    closeRoom();
    currentUserId = null; rooms = [];
    find('[data-deals-rooms]')?.replaceChildren();
    find('[data-deals-messages]')?.replaceChildren();
    const chatSection = find('[data-deals-chat-section]'); if (chatSection) chatSection.style.display = 'none';
    const infoSection = find('[data-deals-info]'); if (infoSection) infoSection.style.display = 'none';
  }

  /** 대화방 나가기는 서버 기록을 삭제하지 않고 현재 화면과 조회만 닫는다. */
  function leaveRoom() {
    closeRoom();
    find('[data-deals-messages]')?.replaceChildren();
    const chatSection = find('[data-deals-chat-section]'); if (chatSection) chatSection.style.display = 'none';
    const infoSection = find('[data-deals-info]'); if (infoSection) infoSection.style.display = 'none';
    renderRoomList();
  }

  /** 서버가 허용한 대화 목록을 그린다. 판매글 상태와 상대방 문자열은 화면 출력용으로만 사용한다. */
  function renderRoomList() {
    const container = find('[data-deals-rooms]');
    if (!container) return;
    container.replaceChildren(...rooms.map((room) => {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'dl-row' + (currentRoom?.roomId === room.roomId ? ' on' : '');
      const dateStr = room.latestMessageAt ? new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit' }).format(new Date(room.latestMessageAt)) : '';
      btn.innerHTML = `
        <span class="laptop"></span>
        <span>
          <b>${escapeHtml(room.counterpartyNickname || '상대방')} <time>${dateStr}</time></b>
          <span class="t">${escapeHtml(room.listingTitle || '판매글')}</span>
          <span class="m">${escapeHtml(room.latestMessagePreview || '대화를 시작해 보세요')}</span>
          <span class="tags"><span class="sc-badge ${statusClass(room.listingStatus)}">${statusLabel(room.listingStatus)}</span></span>
        </span>
        ${room.hasUnreadMessages ? '<i class="dl-dot" aria-label="새 메시지"></i>' : ''}
      `;
      btn.addEventListener('click', () => selectRoom(room));
      return btn;
    }));
  }

  /** 선택한 방마다 조회 범위를 새로 만든다. 이전 방의 응답과 finally 처리는 새 방의 갱신을 막지 못한다. */
  async function selectRoom(room) {
    stopPolling();
    roomScope?.controller.abort();
    const sequence = ++roomSequence;
    currentRoom = room;
    const scope = { room, sequence, userId: currentUserId, controller: new AbortController() };
    roomScope = scope;
    window.PastportTradeCompletion?.clear();
    find('[data-deals-completion]')?.setAttribute('hidden', '');
    find('.dl')?.classList.add('dl-has-room');
    renderRoomList();

    const chatSection = find('[data-deals-chat-section]');
    const infoSection = find('[data-deals-info]');
    if (chatSection) chatSection.style.display = 'flex';
    if (infoSection) infoSection.style.display = 'block';

    const titleEl = find('[data-deals-room-title]');
    if (titleEl) {
      titleEl.innerHTML = `<b>${escapeHtml(room.listingTitle || '판매글')}</b><span>${escapeHtml(room.counterpartyNickname)}님과의 대화</span>`;
    }
    const statusEl = find('[data-deals-listing-status]');
    if (statusEl) {
      statusEl.textContent = statusLabel(room.listingStatus);
      statusEl.className = 'sc-badge ' + statusClass(room.listingStatus);
    }

    find('[data-deals-info-title]').textContent = room.listingTitle || '–';
    find('[data-deals-info-price]').textContent = '–';
    const buyBtn = find('[data-deals-request-buy]');
    if (buyBtn) buyBtn.hidden = true;

    await loadMessages(scope);
    if (!roomCurrent(scope)) return;
    await checkPurchaseRequest(scope);

    if (roomCurrent(scope)) startPolling(scope);
  }

  /** 방·사용자·세대가 모두 유지되는 요청만 현재 대화를 갱신할 수 있다. */
  function roomCurrent(scope) {
    return !!scope && scope === roomScope && scope.sequence === roomSequence && scope.userId === currentUserId && !scope.controller.signal.aborted;
  }

  /** 선택 당시 방의 메시지와 읽음 위치를 갱신한다. 취소되거나 이전 계정의 401 응답은 무시한다. */
  async function loadMessages(scope) {
    if (!roomCurrent(scope)) return;
    const roomId = scope.room.roomId;
    try {
      const data = await api(`/api/v1/chat-rooms/${roomId}/messages?page=0&size=50`, { method: 'GET', signal: scope.controller.signal });
      const messages = (data?.items || []).reverse();
      if (!roomCurrent(scope)) return;
      renderMessages(messages);

      if (messages.length > 0) {
        const lastMsg = messages[messages.length - 1];
        api(`/api/v1/chat-rooms/${roomId}/read-position`, {
          method: 'PUT',
          signal: scope.controller.signal,
          body: JSON.stringify({ lastReadMessageId: lastMsg.messageId })
        }).catch(() => {});
      }
    } catch (e) {
      if (!roomCurrent(scope) || e.name === 'AbortError') return;
      if (e.status === 401) { resetDeals(); return; }
      console.error('메시지 로드 실패:', e);
    }
  }

  /** 현재 방의 메시지를 이스케이프해 표시하고 최신 메시지 위치로 스크롤한다. */
  function renderMessages(messages) {
    const container = find('[data-deals-messages]');
    if (!container) return;
    container.replaceChildren();

    messages.forEach((msg) => {
      const isMe = currentUserId != null && msg.senderId === currentUserId;
      const box = document.createElement('div');
      box.className = 'msg ' + (isMe ? 'me' : 'you');
      const timeStr = msg.createdAt ? new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit' }).format(new Date(msg.createdAt)) : '';
      box.innerHTML = `<p>${escapeHtml(msg.content)}</p><time>${timeStr}</time>`;
      container.appendChild(box);
    });

    container.scrollTop = container.scrollHeight;
  }

  /** 송수신 방향으로 판매자·구매자 역할을 구분한다. 현재 방에만 예약·완료 조작을 연결한다. */
  async function checkPurchaseRequest(scope) {
    if (!roomCurrent(scope)) return;
    const { room, userId } = scope;
    const isCurrent = () => roomCurrent(scope);
    const buyBtn = find('[data-deals-request-buy]');
    if (buyBtn) buyBtn.hidden = true;
    // 종료된 글에서도 대화는 유지하되, 이전 방의 예약·완료 조작은 남기지 않는다.
    if (isClosed(room.listingStatus)) {
      window.PastportTradeCompletion?.clear();
      find('[data-deals-messages]')?.querySelector('[data-deals-pending-card]')?.remove();
      const status = find('[data-deals-info-trade-status]');
      if (status) status.textContent = '종료된 거래';
      return;
    }
    try {
      const [sent, received] = await Promise.all(['SENT', 'RECEIVED'].map(direction => api(`/api/v1/purchase-requests?direction=${direction}&roomId=${room.roomId}&page=0&size=100`, { method: 'GET', signal: scope.controller.signal })));
      if (!isCurrent()) return;
      const sentItems = sent?.items || [];
      const receivedItems = received?.items || [];
      const items = [...sentItems, ...receivedItems].sort((a, b) => b.requestId - a.requestId);
      const req = items.find(item => item.requestStatus === 'PENDING' || item.requestStatus === 'ACCEPTED');
      room.listingStatus = req?.listingStatus || sent?.roomContext?.listingStatus || received?.roomContext?.listingStatus || room.listingStatus;
      let isSeller = !!req && receivedItems.some(item => item.requestId === req.requestId);
      const status = find('[data-deals-info-trade-status]');
      const setStep = step => findAll('[data-flow-step]').forEach(node => node.classList.toggle('now', node.dataset.flowStep === step));
      const badge = find('[data-deals-listing-status]');
      if (badge) { badge.textContent = statusLabel(room.listingStatus); badge.className = 'sc-badge ' + statusClass(room.listingStatus); }
      renderRoomList();
      if (!req) {
        window.PastportTradeCompletion?.clear();
        setStep('inquiry');
        if (status) status.textContent = ['SOLD', 'CANCELLED'].includes(room.listingStatus) ? '종료된 거래' : '문의 진행 중';
        if (room.listingStatus === 'ACTIVE') {
          const detail = await api(`/api/v1/listings/${room.listingId}`, { method: 'GET', signal: scope.controller.signal });
          if (!isCurrent()) return;
          isSeller = detail.canManage;
          const price = find('[data-deals-info-price]');
          if (price) price.textContent = detail.content?.price == null ? '–' : new Intl.NumberFormat('ko-KR').format(detail.content.price) + '원';
          if (buyBtn) { buyBtn.hidden = isSeller; buyBtn.onclick = () => requestBuy(room.listingId, room.roomId); }
        }
      } else if (req.requestStatus === 'PENDING') {
        window.PastportTradeCompletion?.clear();
        setStep('pending');
        if (status) status.textContent = isSeller ? '구매 요청 확인 필요' : '판매자 수락 대기 중';
        renderPendingCard(req, isSeller, scope);
      } else {
        const done = req.transactionStatus === 'COMPLETED';
        setStep(done ? 'completed' : 'reserved');
        if (status) status.textContent = done ? '거래 완료' : '예약 완료';
        await window.PastportTradeCompletion?.mount({
          request: req, role: isSeller ? 'seller' : 'buyer', userId, roomId: room.roomId,
          title: room.listingTitle || '이 제품', isCurrent,
          onChanged: () => isCurrent() ? checkPurchaseRequest(scope) : Promise.resolve()
        });
      }
    } catch (error) {
      if (!isCurrent()) return;
      window.PastportTradeCompletion?.clear();
      const status = find('[data-deals-info-trade-status]');
      if (status) status.textContent = error.message || '거래 상태를 확인하지 못했어요.';
    }
  }

  /** 현재 방의 판매자에게만 예약 수락·거절을 제공한다. 서버가 실제 권한과 예약 상태를 최종 검사한다. */
  function renderPendingCard(req, isSeller, scope) {
    const container = find('[data-deals-messages]');
    if (!container) return;
    container.querySelector('[data-deals-pending-card]')?.remove();
    if (!isSeller) return;
    const card = document.createElement('div');
    card.className = 'dl-act';
    card.dataset.dealsPendingCard = '';
    card.innerHTML = `
      <i>⇄</i>
      <div><b>구매 예약 요청이 접수되었습니다</b><span>수락하면 판매글이 예약 상태로 전환되고 다른 구매자의 예약이 차단됩니다.</span></div>
      <div class="btns">
        <button type="button" class="sc-btn sm" data-trade-reject>거절</button>
        <button type="button" class="sc-btn primary sm" data-trade-accept>수락하기</button>
      </div>
    `;

    card.querySelector('[data-trade-accept]')?.addEventListener('click', async () => {
      if (!roomCurrent(scope)) return;
      if (!confirm('구매 예약을 수락하시겠습니까? 판매글이 예약 상태가 됩니다.')) return;
      try {
        await api(`/api/v1/purchase-requests/${req.requestId}/accept`, { method: 'POST', signal: scope.controller.signal });
        if (!roomCurrent(scope)) return;
        alert('예약이 수락되었습니다!');
        if (currentRoom) selectRoom(currentRoom);
      } catch (e) {
        if (!roomCurrent(scope)) return;
        alert(e.message);
      }
    });

    card.querySelector('[data-trade-reject]')?.addEventListener('click', async () => {
      if (!roomCurrent(scope)) return;
      if (!confirm('구매 예약을 거절하시겠습니까?')) return;
      try {
        await api(`/api/v1/purchase-requests/${req.requestId}/reject`, { method: 'POST', signal: scope.controller.signal });
        if (!roomCurrent(scope)) return;
        alert('예약이 거절되었습니다.');
        if (currentRoom) selectRoom(currentRoom);
      } catch (e) {
        if (!roomCurrent(scope)) return;
        alert(e.message);
      }
    });

    container.appendChild(card);
    container.scrollTop = container.scrollHeight;
  }

  /** 선택한 방·판매글의 구매 예약을 요청하고 같은 방에 남아 있을 때만 결과를 표시한다. */
  async function requestBuy(listingId, roomId) {
    const scope = roomScope;
    if (!scope || !roomCurrent(scope) || scope.room.roomId !== roomId) return;
    if (!confirm('이 판매글에 대해 구매 예약을 요청하시겠습니까?')) return;
    try {
      await api(`/api/v1/market/listings/${listingId}/purchase-requests`, {
        method: 'POST',
        signal: scope.controller.signal,
        body: JSON.stringify({ roomId })
      });
      if (!roomCurrent(scope)) return;
      alert('구매 예약 요청을 보냈습니다! 판매자의 수락을 기다려 주세요.');
      if (currentRoom) selectRoom(currentRoom);
    } catch (e) {
      if (!roomCurrent(scope)) return;
      alert(e.message);
    }
  }

  /** 입력한 메시지를 선택 당시 방에 보낸다. 계정·방이 바뀌면 늦은 결과는 화면에 반영하지 않는다. */
  async function sendMessage() {
    const input = find('[data-deals-input]');
    const scope = roomScope;
    if (!input || !scope || !roomCurrent(scope)) return;
    const content = input.value.trim();
    if (!content) return;
    input.value = '';

    try {
      await api(`/api/v1/chat-rooms/${scope.room.roomId}/messages`, {
        method: 'POST',
        signal: scope.controller.signal,
        body: JSON.stringify({ content })
      });
      await loadMessages(scope);
    } catch (e) {
      if (!roomCurrent(scope)) return;
      if (e.status === 401) resetDeals();
      alert('메시지 전송에 실패했습니다: ' + e.message);
    }
  }

  /** 방마다 별도 busy 상태를 소유한다. 오래된 폴링의 finally가 새 방의 요청 상태를 변경하지 않는다. */
  function startPolling(scope) {
    stopPolling();
    const poll = { busy: false, timer: null };
    polling = poll;
    poll.timer = setInterval(async () => {
      if (polling !== poll || !roomCurrent(scope) || !document.querySelector('#deals.active') || poll.busy) return;
      poll.busy = true;
      try {
        await loadMessages(scope);
        if (polling === poll && roomCurrent(scope)) await checkPurchaseRequest(scope);
      } finally { poll.busy = false; }
    }, 3000);
  }

  /** 선택한 방의 조회를 취소하고 완료 확인 화면도 정리한다. 서버 대화 기록은 유지한다. */
  function closeRoom() {
    stopPolling();
    roomSequence++;
    roomScope?.controller.abort();
    roomScope = null;
    currentRoom = null;
    window.PastportTradeCompletion?.clear();
    find('.dl')?.classList.remove('dl-has-room');
    const chat = find('[data-deals-chat-section]');
    const info = find('[data-deals-info]');
    if (chat) chat.style.display = 'none';
    if (info) info.style.display = 'none';
    renderRoomList();
  }

  /** 주기 호출만 중지한다. 진행 중 응답은 해당 방의 취소 신호와 세대로 별도 차단한다. */
  function stopPolling() {
    if (polling) clearInterval(polling.timer);
    polling = null;
  }

  /** 서버의 제목·닉네임·메시지를 HTML 문자열에 넣기 전에 특수문자를 이스케이프한다. */
  function escapeHtml(str) {
    return String(str || '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;');
  }

  /** 페이지가 로드되면 대화 이동·전송 버튼을 연결한다. */
  function init() {
    find('[data-deals-back]')?.addEventListener('click', closeRoom);
    const sendBtn = find('[data-deals-send]');
    const input = find('[data-deals-input]');
    sendBtn?.addEventListener('click', sendMessage);
    find('[data-deals-leave]')?.addEventListener('click', leaveRoom);
    input?.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendMessage();
      }
    });

    findAll('[data-deals-tab]').forEach((tab) => {
      tab.addEventListener('click', () => {
        findAll('[data-deals-tab]').forEach(t => t.classList.remove('active'));
        tab.classList.add('active');
      });
    });
  }

  /** 화면 이동 중이거나 이전 계정에서 시작한 대화방 생성의 후속 이동을 무효화한다. */
  function cancelOpen() {
    openGeneration++;
    openController?.abort();
  }

  /** 상세에서 시작한 문의만 연다. POST와 목록 조회 후에도 같은 계정·이동 요청인지 확인한다. */
  async function openRoomForListing(listingId) {
    cancelOpen();
    const generation = openGeneration;
    const session = sessionGeneration;
    const sourceId = new URL(window.location.href).searchParams.get('listingId');
    if (sourceId !== String(listingId) || !document.querySelector('#detail.active')) return;
    const controller = new AbortController();
    openController = controller;
    const isCurrent = () => generation === openGeneration && session === sessionGeneration && !controller.signal.aborted;
    try {
      const room = await api(`/api/v1/market/listings/${listingId}/chat-rooms`, { method: 'POST', signal: controller.signal });
      if (!isCurrent() || new URL(window.location.href).searchParams.get('listingId') !== sourceId || !document.querySelector('#detail.active')) return;
      if (typeof show === 'function') show('deals');
      const loaded = await loadRooms();
      if (!loaded || !isCurrent() || !document.querySelector('#deals.active')) return;
      const target = rooms.find(r => r.roomId === room.roomId);
      if (target) selectRoom(target);
    } catch (e) {
      if (!isCurrent() || e.name === 'AbortError') return;
      alert('채팅방을 열 수 없습니다: ' + e.message);
    }
  }

  document.addEventListener('pastport:page-loaded', (e) => {
    if (e.detail.id === 'deals') {
      init();
      loadRooms();
    }
  });

  document.addEventListener('pastport:page-shown', (e) => {
    if (e.detail.id === 'deals') {
      loadRooms();
    } else {
      roomsGeneration++;
      roomsController?.abort();
      cancelOpen();
      closeRoom();
    }
  });

  document.addEventListener('pastport:auth-changed', (event) => {
    resetDeals();
    if (event.detail?.signedIn && document.querySelector('#deals.active')) loadRooms();
  });

  window.PastportDeals = Object.freeze({
    openRoomForListing
  });
})();
