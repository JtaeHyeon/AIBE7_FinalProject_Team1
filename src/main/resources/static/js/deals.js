(() => {
  'use strict';

  let currentRoom = null;
  let currentUserId = null;
  let pollingTimer = null;
  let rooms = [];
  let roomSequence = 0;
  let pollingBusy = false;

  const find = (selector) => document.querySelector('#deals ' + selector);
  const findAll = (selector) => document.querySelectorAll('#deals ' + selector);

  async function api(path, options = {}) {
    const fetcher = window.PastportApi?.fetch || window.fetch.bind(window);
    const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
    const response = await fetcher(path, { credentials: 'same-origin', ...options, headers });
    const payload = await response.json().catch(() => null);
    if (!response.ok) {
      const error = new Error(payload?.message || payload?.fieldErrors?.[0]?.message || '요청 처리에 실패했습니다.');
      error.status = response.status;
      error.code = payload?.code;
      throw error;
    }
    return payload?.data;
  }

  async function initUser() {
    try {
      const user = await api('/api/auth/me', { method: 'GET' });
      currentUserId = user?.id || null;
    } catch {
      currentUserId = null;
    }
  }

  async function loadRooms() {
    const container = find('[data-deals-rooms]');
    if (!container) return;
    try {
      await initUser();
      if (!currentUserId) {
        container.innerHTML = '<div style="padding:40px 20px;text-align:center;color:var(--muted)"><p style="margin-bottom:8px">로그인이 필요한 서비스입니다.</p><button type="button" class="sc-btn primary sm" data-page="auth">로그인하기</button></div>';
        return;
      }
      const data = await api('/api/v1/chat-rooms?page=0&size=50', { method: 'GET' });
      rooms = data?.items || [];
      if (rooms.length === 0) {
        container.innerHTML = '<p class="sub" style="padding:40px 20px;text-align:center;color:var(--muted)">진행 중인 대화가 없습니다.<br><small style="margin-top:6px;display:inline-block">마켓에서 관심 있는 상품에 문의해 보세요!</small></p>';
        return;
      }
      renderRoomList();
    } catch (e) {
      container.innerHTML = '<p class="sub" style="padding:20px;text-align:center;color:var(--muted)">대화방 목록을 불러오지 못했습니다.</p>';
    }
  }

  // 글이 삭제됐거나(상태 없음) 판매 중지된 방은 구매 요청·소유권 이전을 막는다. 대화는 명세대로 계속할 수 있다.
  const isClosed = (status) => !status || status === 'CANCELLED';
  const statusLabel = (status) => ({ ACTIVE: '판매 중', RESERVED: '예약 중', SOLD: '판매 완료', CANCELLED: '판매 중지' }[status] || '삭제·종료된 글');
  const statusClass = (status) => (status === 'RESERVED' ? 'warn' : status === 'ACTIVE' ? 'ok' : '');

  // 로그아웃·세션 만료 시 열려 있던 대화와 3초 갱신을 모두 끊고 화면을 비운다.
  function resetDeals() {
    closeRoom();
    currentUserId = null; rooms = [];
    find('[data-deals-rooms]')?.replaceChildren();
    find('[data-deals-messages]')?.replaceChildren();
    const chatSection = find('[data-deals-chat-section]'); if (chatSection) chatSection.style.display = 'none';
    const infoSection = find('[data-deals-info]'); if (infoSection) infoSection.style.display = 'none';
  }

  // 열려 있는 대화를 닫고 목록으로 돌아간다. 대화 기록은 지우지 않고, 방은 목록에 그대로 남는다.
  function leaveRoom() {
    closeRoom();
    find('[data-deals-messages]')?.replaceChildren();
    const chatSection = find('[data-deals-chat-section]'); if (chatSection) chatSection.style.display = 'none';
    const infoSection = find('[data-deals-info]'); if (infoSection) infoSection.style.display = 'none';
    renderRoomList();
  }

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

  async function selectRoom(room) {
    stopPolling();
    const sequence = ++roomSequence;
    currentRoom = room;
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

    await loadMessages(room.roomId);
    if (sequence !== roomSequence || currentRoom?.roomId !== room.roomId) return;
    await checkPurchaseRequest(room);

    startPolling();
  }

  async function loadMessages(roomId) {
    try {
      const data = await api(`/api/v1/chat-rooms/${roomId}/messages?page=0&size=50`, { method: 'GET' });
      const messages = (data?.items || []).reverse();
      if (currentRoom?.roomId !== roomId) return;
      renderMessages(messages);

      if (messages.length > 0) {
        const lastMsg = messages[messages.length - 1];
        api(`/api/v1/chat-rooms/${roomId}/read-position`, {
          method: 'PUT',
          body: JSON.stringify({ lastReadMessageId: lastMsg.messageId })
        }).catch(() => {});
      }
    } catch (e) {
      if (e.status === 401) { resetDeals(); return; }
      console.error('메시지 로드 실패:', e);
    }
  }

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

  // 송수신 방향으로 역할을 구분한다. 사용자 ID를 요청 본문에서 지정하지 않는다.
  async function checkPurchaseRequest(room) {
    if (currentRoom?.roomId !== room.roomId) return;
    const sequence = roomSequence;
    const userId = currentUserId;
    const isCurrent = () => currentRoom?.roomId === room.roomId && roomSequence === sequence && currentUserId === userId;
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
      const [sent, received] = await Promise.all(['SENT', 'RECEIVED'].map(direction => api(`/api/v1/purchase-requests?direction=${direction}&roomId=${room.roomId}&page=0&size=100`, { method: 'GET' })));
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
          const detail = await api(`/api/v1/listings/${room.listingId}`, { method: 'GET' });
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
        renderPendingCard(req, isSeller);
      } else {
        const done = req.transactionStatus === 'COMPLETED';
        setStep(done ? 'completed' : 'reserved');
        if (status) status.textContent = done ? '거래 완료' : '예약 완료';
        await window.PastportTradeCompletion?.mount({
          request: req, role: isSeller ? 'seller' : 'buyer', userId, roomId: room.roomId,
          title: room.listingTitle || '이 제품', isCurrent,
          onChanged: () => isCurrent() ? checkPurchaseRequest(room) : Promise.resolve()
        });
      }
    } catch (error) {
      if (!isCurrent()) return;
      window.PastportTradeCompletion?.clear();
      const status = find('[data-deals-info-trade-status]');
      if (status) status.textContent = error.message || '거래 상태를 확인하지 못했어요.';
    }
  }

  function renderPendingCard(req, isSeller) {
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
      if (!confirm('구매 예약을 수락하시겠습니까? 판매글이 예약 상태가 됩니다.')) return;
      try {
        await api(`/api/v1/purchase-requests/${req.requestId}/accept`, { method: 'POST' });
        alert('예약이 수락되었습니다!');
        if (currentRoom) selectRoom(currentRoom);
      } catch (e) {
        alert(e.message);
      }
    });

    card.querySelector('[data-trade-reject]')?.addEventListener('click', async () => {
      if (!confirm('구매 예약을 거절하시겠습니까?')) return;
      try {
        await api(`/api/v1/purchase-requests/${req.requestId}/reject`, { method: 'POST' });
        alert('예약이 거절되었습니다.');
        if (currentRoom) selectRoom(currentRoom);
      } catch (e) {
        alert(e.message);
      }
    });

    container.appendChild(card);
    container.scrollTop = container.scrollHeight;
  }

  async function requestBuy(listingId, roomId) {
    if (!confirm('이 판매글에 대해 구매 예약을 요청하시겠습니까?')) return;
    try {
      await api(`/api/v1/market/listings/${listingId}/purchase-requests`, {
        method: 'POST',
        body: JSON.stringify({ roomId })
      });
      alert('구매 예약 요청을 보냈습니다! 판매자의 수락을 기다려 주세요.');
      if (currentRoom) selectRoom(currentRoom);
    } catch (e) {
      alert(e.message);
    }
  }

  async function sendMessage() {
    const input = find('[data-deals-input]');
    if (!input || !currentRoom) return;
    const content = input.value.trim();
    if (!content) return;
    input.value = '';

    try {
      await api(`/api/v1/chat-rooms/${currentRoom.roomId}/messages`, {
        method: 'POST',
        body: JSON.stringify({ content })
      });
      await loadMessages(currentRoom.roomId);
    } catch (e) {
      if (e.status === 401) resetDeals();
      alert('메시지 전송에 실패했습니다: ' + e.message);
    }
  }

  function startPolling() {
    stopPolling();
    pollingTimer = setInterval(async () => {
      if (!currentRoom || !document.querySelector('#deals.active') || pollingBusy) return;
      pollingBusy = true;
      const room = currentRoom;
      try { await loadMessages(room.roomId); await checkPurchaseRequest(room); }
      finally { pollingBusy = false; }
    }, 3000);
  }

  function closeRoom() {
    stopPolling();
    roomSequence++;
    currentRoom = null;
    window.PastportTradeCompletion?.clear();
    find('.dl')?.classList.remove('dl-has-room');
    const chat = find('[data-deals-chat-section]');
    const info = find('[data-deals-info]');
    if (chat) chat.style.display = 'none';
    if (info) info.style.display = 'none';
    renderRoomList();
  }

  function stopPolling() {
    if (pollingTimer) {
      clearInterval(pollingTimer);
      pollingTimer = null;
    }
  }

  function escapeHtml(str) {
    return String(str || '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;');
  }

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

  async function openRoomForListing(listingId) {
    try {
      const room = await api(`/api/v1/market/listings/${listingId}/chat-rooms`, { method: 'POST' });
      if (typeof show === 'function') show('deals');
      await loadRooms();
      const target = rooms.find(r => r.roomId === room.roomId);
      if (target) selectRoom(target);
    } catch (e) {
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
