(() => {
  'use strict';

  let currentRoom = null;
  let currentUserId = null;
  let pollingTimer = null;
  let roomList = null;
  let generation = 0;
  let roomGeneration = 0;
  let pendingEntry = null;
  let opening = false;
  let sending = false;

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
      throw error;
    }
    return payload?.data;
  }

  async function initUser(ticket) {
    try {
      const user = await api('/api/auth/me', { method: 'GET' });
      if (ticket === generation) currentUserId = user?.id || null;
    } catch {
      if (ticket === generation) currentUserId = null;
    }
  }

  async function loadRooms(entry = null) {
    resetDeals();
    const ticket = generation;
    const container = find('[data-deals-rooms]');
    if (!container) return;
    try {
      await initUser(ticket);
      if (ticket !== generation) return;
      if (!currentUserId) {
        container.innerHTML = '<div style="padding:40px 20px;text-align:center;color:var(--muted)"><p style="margin-bottom:8px">로그인이 필요한 서비스입니다.</p><button type="button" class="sc-btn primary sm" data-page="auth">로그인하기</button></div>';
        return;
      }
      find('[data-deals-scope]').textContent = entry ? (entry.listingTitle || '이 판매글') + ' · 채팅' : '전체 채팅';
      find('[data-deals-all]').hidden = !entry;
      roomList = window.PastportChatRoomList.mount(container, {
        listingId: entry?.listingId, onSelect: selectRoom
      });
      await roomList.load();
      if (ticket === generation && entry?.roomId) await selectRoom(entry);
    } catch (e) {
      if (ticket !== generation) return;
      container.innerHTML = '<p class="sub" style="padding:20px;text-align:center;color:var(--muted)">대화방 목록을 불러오지 못했습니다.</p>';
    }
  }

  // 글이 삭제됐거나(상태 없음) 판매 중지된 방은 구매 요청·소유권 이전을 막는다. 대화는 명세대로 계속할 수 있다.
  const isClosed = (status) => !status || status === 'CANCELLED';
  const statusLabel = (status) => ({ ACTIVE: '판매 중', RESERVED: '예약 중', SOLD: '판매 완료' }[status] || '삭제·종료된 글');
  const statusClass = (status) => (status === 'RESERVED' ? 'warn' : status === 'ACTIVE' ? 'ok' : '');

  // 로그아웃·세션 만료 시 열려 있던 대화와 3초 갱신을 모두 끊고 화면을 비운다.
  function resetDeals() {
    generation++; roomGeneration++;
    roomList?.dispose(); roomList = null;
    stopPolling();
    currentRoom = null; currentUserId = null;
    find('[data-deals-rooms]')?.replaceChildren();
    find('[data-deals-messages]')?.replaceChildren();
    const chatSection = find('[data-deals-chat-section]'); if (chatSection) chatSection.style.display = 'none';
    const infoSection = find('[data-deals-info]'); if (infoSection) infoSection.style.display = 'none';
  }

  // 열려 있는 대화를 닫고 목록으로 돌아간다. 대화 기록은 지우지 않고, 방은 목록에 그대로 남는다.
  function leaveRoom() {
    if (!currentRoom) return;
    stopPolling();
    roomGeneration++;
    currentRoom = null;
    find('[data-deals-messages]')?.replaceChildren();
    const chatSection = find('[data-deals-chat-section]'); if (chatSection) chatSection.style.display = 'none';
    const infoSection = find('[data-deals-info]'); if (infoSection) infoSection.style.display = 'none';
    renderRoomList();
  }

  function renderRoomList() {
    roomList?.select(currentRoom?.roomId);
  }

  async function selectRoom(room) {
    stopPolling();
    const ticket = ++roomGeneration;
    currentRoom = room;
    find('[data-deals-messages]')?.replaceChildren();
    find('[data-deals-room-title]')?.replaceChildren();
    const scope = find('[data-deals-scope]'); if (scope) scope.textContent = '전체 채팅';
    const all = find('[data-deals-all]'); if (all) all.hidden = true;
    const input = find('[data-deals-input]'); if (input) input.value = '';
    const infoTitle = find('[data-deals-info-title]'); if (infoTitle) infoTitle.textContent = '';
    const input = find('[data-deals-input]'); if (input) input.value = '';
    find('[data-deals-request-buy]').hidden = true;
    find('[data-deals-transfer-area]').hidden = true;
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
    const closed = isClosed(room.listingStatus);

    find('[data-deals-info-title]').textContent = room.listingTitle || '–';

    await loadMessages(room.roomId);
    if (ticket !== roomGeneration) return;
    startPolling();
    if (closed) {
      const buyBtn = find('[data-deals-request-buy]'); if (buyBtn) buyBtn.hidden = true;
      const transferArea = find('[data-deals-transfer-area]'); if (transferArea) transferArea.hidden = true;
      return;
    }
    await checkPurchaseRequest(room, ticket);
  }

  async function loadMessages(roomId) {
    const ticket = roomGeneration;
    try {
      const data = await api(`/api/v1/chat-rooms/${roomId}/messages?page=0&size=50`, { method: 'GET' });
      if (ticket !== roomGeneration || currentRoom?.roomId !== roomId) return;
      const messages = (data?.items || []).reverse();
      renderMessages(messages);

      if (messages.length > 0) {
        const lastMsg = messages[messages.length - 1];
        await api(`/api/v1/chat-rooms/${roomId}/read-position`, {
          method: 'PUT',
          body: JSON.stringify({ lastReadMessageId: lastMsg.messageId })
        });
        if (ticket === roomGeneration && currentRoom?.roomId === roomId) {
          currentRoom.hasUnreadMessages = false;
          roomList?.markRead(roomId);
        }
      }
    } catch (e) {
      if (ticket !== roomGeneration) return;
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

  async function checkPurchaseRequest(room, ticket = roomGeneration) {
    const buyBtn = find('[data-deals-request-buy]');
    const infoStatus = find('[data-deals-info-trade-status]');
    const flowSteps = findAll('[data-flow-step]');

    const setStep = (step) => {
      flowSteps.forEach(el => {
        el.className = el.dataset.flowStep === step ? 'now' : '';
      });
    };

    try {
      const requests = await api(`/api/v1/purchase-requests?roomId=${room.roomId}&page=0&size=10`, { method: 'GET' });
      if (ticket !== roomGeneration) return;
      const items = requests?.items || [];
      const pendingOrAccepted = items.find(r => r.requestStatus === 'PENDING' || r.requestStatus === 'ACCEPTED');

      const transferArea = find('[data-deals-transfer-area]');
      if (!pendingOrAccepted) {
        setStep('inquiry');
        if (infoStatus) infoStatus.textContent = '문의 진행 중';
        if (transferArea) transferArea.hidden = true;
        if (buyBtn) {
          buyBtn.hidden = false;
          buyBtn.onclick = () => requestBuy(room.listingId, room.roomId);
        }
      } else if (pendingOrAccepted.requestStatus === 'PENDING') {
        setStep('pending');
        if (infoStatus) infoStatus.textContent = '구매 요청 대기 중';
        if (transferArea) transferArea.hidden = true;
        if (buyBtn) buyBtn.hidden = true;
        renderPendingCard(pendingOrAccepted);
      } else if (pendingOrAccepted.requestStatus === 'ACCEPTED') {
        setStep('reserved');
        if (infoStatus) infoStatus.textContent = '예약 완료 (거래 준비)';
        if (buyBtn) buyBtn.hidden = true;
        if (transferArea) {
          transferArea.hidden = false;
          const transferBtn = find('[data-deals-btn-transfer]');
          if (transferBtn) {
            transferBtn.onclick = () => handleIssueTransfer(room, pendingOrAccepted);
          }
        }
      }
    } catch {
      if (ticket !== roomGeneration) return;
      setStep('inquiry');
    }
  }

  async function handleIssueTransfer(room, pendingOrAccepted) {
    if (!confirm('구매자에게 전달할 일회성 소유권 이전 링크 및 QR을 생성하시겠습니까?')) return;
    try {
      // listing 상세에서 productId 조회
      const listingRes = await api(`/api/v1/listings/${room.listingId}`);
      const productId = listingRes.productId || listingRes.id;
      const transferRes = await api('/api/v1/ownership/transfers', {
        method: 'POST',
        body: JSON.stringify({
          productId: productId,
          purchaseRequestId: pendingOrAccepted.requestId
        })
      });

      const fullUrl = window.location.origin + transferRes.linkUrl;
      const copyPrompt = prompt(
        '일회성 소유권 이전 링크가 생성되었습니다 (24시간 유효)!\n구매자에게 이 링크를 전달해 수락하도록 안내하세요:\n\n' + fullUrl + '\n\n복사하시겠습니까? (확인 누르면 클립보드 복사 시도)',
        fullUrl
      );
      if (copyPrompt !== null && navigator.clipboard) {
        await navigator.clipboard.writeText(fullUrl);
        alert('소유권 이전 링크가 클립보드에 복사되었습니다.');
      }
    } catch (e) {
      alert('소유권 이전 링크 발급 실패: ' + e.message);
    }
  }

  function renderPendingCard(req) {
    const container = find('[data-deals-messages]');
    if (!container) return;
    const card = document.createElement('div');
    card.className = 'dl-act';
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
      await api(`/api/v1/listings/${listingId}/purchase-requests`, {
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
    if (!input || !currentRoom || sending) return;
    const room = currentRoom, ticket = roomGeneration;
    const content = input.value.trim();
    if (!content) return;
    input.value = '';
    sending = true;
    const button = find('[data-deals-send]'); button.disabled = true;

    try {
      await api(`/api/v1/chat-rooms/${room.roomId}/messages`, {
        method: 'POST',
        body: JSON.stringify({ content })
      });
      if (ticket === roomGeneration) await loadMessages(room.roomId);
    } catch (e) {
      if (ticket !== roomGeneration) return;
      if (e.status === 401) resetDeals();
      alert('메시지 전송에 실패했습니다: ' + e.message);
    } finally {
      sending = false; button.disabled = false;
    }
  }

  function startPolling() {
    stopPolling();
    pollingTimer = setInterval(() => {
      if (currentRoom && document.querySelector('#deals.active')) {
        loadMessages(currentRoom.roomId);
      }
    }, 3000);
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
    const sendBtn = find('[data-deals-send]');
    if (!sendBtn || sendBtn.dataset.bound) return;
    sendBtn.dataset.bound = 'true';
    const input = find('[data-deals-input]');
    sendBtn?.addEventListener('click', sendMessage);
    find('[data-deals-leave]')?.addEventListener('click', leaveRoom);
    input?.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendMessage();
      }
    });

  }

  async function openRoomForListing(listingId) {
    if (opening) return;
    opening = true;
    const ticket = generation;
    try {
      const room = await api(`/api/v1/market/listings/${listingId}/chat-rooms`, { method: 'POST' });
      if (ticket !== generation) return;
      const data = await api(`/api/v1/chat-rooms?listingId=${listingId}&page=0&size=20`);
      if (ticket !== generation) return;
      const summary = data.items.find(item => item.roomId === room.roomId) || room;
      openSummary(summary);
    } catch (e) {
      if (ticket !== generation) return;
      alert('채팅방을 열 수 없습니다: ' + e.message);
    } finally {
      opening = false;
    }
  }

  function openSummary(room) {
    pendingEntry = room;
    if (typeof show === 'function') show('deals');
  }

  async function activate() {
    const entry = pendingEntry; pendingEntry = null;
    resetDeals();
    if (!find('[data-deals-rooms]')) {
      const ticket = generation;
      await new Promise(resolve => {
        const loaded = event => {
          if (event.detail.id !== 'deals') return;
          document.removeEventListener('pastport:page-loaded', loaded); resolve();
        };
        document.addEventListener('pastport:page-loaded', loaded);
      });
      if (ticket !== generation || !document.querySelector('#deals.active')) return;
    }
    init();
    await loadRooms(entry);
  }

  document.addEventListener('pastport:page-loaded', (e) => {
    if (e.detail.id === 'deals') {
      init();
    }
  });

  document.addEventListener('pastport:auth-changed', () => {
    resetDeals();
    if (document.querySelector('#deals.active')) activate();
  });

  document.addEventListener('pastport:page-shown', (e) => {
    if (e.detail.id === 'deals') {
      activate();
    } else {
      resetDeals();
    }
  });

  window.PastportDeals = Object.freeze({
    openRoomForListing, openSummary
  });
})();
