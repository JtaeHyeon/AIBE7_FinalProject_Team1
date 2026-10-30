// 게시된 상세의 진입·조회 담당이다. 화면 구성과 판매자 편집·AI 상태 표시 모듈을 연결한다.
(() => {
  let request = null;
  let data = null;
  let loading = false;
  let initialized = false;
  const currentId = () => new URL(window.location.href).searchParams.get('listingId');
  const find = selector => document.querySelector('#detail ' + selector);

  /** 조회 실패·계정 변경 시 이전 판매글의 채팅 클릭 연결을 제거한다. */
  function resetChat() {
    const button = find('[data-listing-chat]');
    if (!button) return;
    button.hidden = true;
    button.disabled = false;
    button.onclick = null;
  }

  /** 구매자의 문의 진입을 연결한다. 호출 모듈이 요청 이후 계정·화면 변경도 검사하며, 예약 중 접근은 서버가 제한한다. */
  function bindChat(listing) {
    const button = find('[data-listing-chat]');
    if (!button || listing.canManage || !['ACTIVE', 'RESERVED'].includes(listing.status)) return;
    button.hidden = false;
    const label = listing.status === 'RESERVED' ? '기존 문의 대화 이어가기' : '판매자에게 채팅 문의하기';
    button.textContent = label;
    button.onclick = async () => {
      if (button.disabled || data?.listingId !== listing.listingId || currentId() !== String(listing.listingId)) return;
      const openRoom = window.PastportDeals?.openRoomForListing;
      if (!openRoom) {
        find('[data-listing-status]').textContent = '채팅 화면을 준비하지 못했어요. 새로고침 후 다시 시도해 주세요.';
        return;
      }
      button.disabled = true;
      button.textContent = '대화방을 여는 중이에요…';
      try { await openRoom(listing.listingId); }
      finally {
        // 다른 글이나 계정으로 전환된 뒤 도착한 결과가 새 버튼 상태를 바꾸지 않도록 한다.
        if (data === listing) { button.disabled = false; button.textContent = label; }
      }
    };
  }

  /** URL의 판매글을 조회하고 이력 다음 페이지를 합친다. 권한 오류와 이전 글의 지연 응답을 구분한다. */
  async function load(append = false) {
    if (!find('[data-listing-content]')) return;
    resetChat();
    window.PastportProductQuestions?.clear('listings');
    window.PastportListingSummary?.reset();
    window.PastportListingManagement?.reset();
    find('[data-listing-login]').hidden = true;
    const id = currentId();
    if (!id || !/^[1-9]\d*$/.test(id)) {
      request?.abort(); data = null; loading = false;
      find('[data-listing-more]').hidden = true;
      find('[data-listing-status]').textContent = '게시된 판매글을 선택해 주세요.';
      find('[data-listing-content]').replaceChildren(); return;
    }
    request?.abort(); const controller = new AbortController(); request = controller;
    const page = append && data ? data.history.page.number + 1 : 0;
    loading = true; find('[data-listing-more]').disabled = true;
    find('[data-listing-status]').textContent = '판매글을 불러오는 중이에요…';
    if (!append) { data = null; find('[data-listing-content]').replaceChildren(); find('[data-listing-more]').hidden = true; }
    try {
      const fetcher = window.PastportApi?.fetch || window.fetch.bind(window);
      const response = await fetcher('/api/v1/listings/' + id + '?page=' + page + '&size=20', { credentials:'same-origin', cache:'no-store', signal:controller.signal });
      const payload = await response.json().catch(() => null);
      if (!response.ok) {
        const failure = new Error(response.status === 401 ? '로그인 후 판매글 상세를 확인해 주세요.' : response.status === 404 ? '이 판매글은 현재 조회할 수 없어요.' : payload?.message || '판매글을 불러오지 못했어요.');
        failure.status = response.status; throw failure;
      }
      if (!payload?.data) throw new Error('판매글 응답을 확인할 수 없어요. 다시 시도해 주세요.');
      if (controller.signal.aborted || currentId() !== id) return;
      const next = payload.data;
      if (append && data) next.history.items = [...new Map([...data.history.items, ...next.history.items].map(item => [item.eventId,item])).values()];
      data = next; window.PastportListingDetail.render(find('[data-listing-content]'), data);
      bindChat(data);
      window.PastportProductQuestions?.mount(find('[data-listing-content]'), {scope:'listings', id:data.listingId});
      window.PastportListingManagement?.bind(data);
      window.PastportListingSummary?.bind(data);
      find('[data-listing-status]').textContent = '게시된 판매글이에요. 판매 중·예약 중인 글은 로그인한 회원이 확인할 수 있어요.';
      find('[data-listing-more]').hidden = !data.history.page.hasNext;
      if (!append && data.canManage && new URL(window.location.href).searchParams.get('listingManage') === '1') window.PastportListingManagement?.open();
    } catch (error) {
      if (error.name !== 'AbortError' && !controller.signal.aborted) {
        data = null; find('[data-listing-content]').replaceChildren(); find('[data-listing-more]').hidden = true;
        find('[data-listing-status]').textContent = error.message;
        find('[data-listing-login]').hidden = error.status !== 401;
      }
    } finally { if (!controller.signal.aborted) { loading = false; find('[data-listing-more]').disabled = false; } }
  }

  /** 게시된 판매글 주소로 전환해 새로고침 시 같은 상세와 관리 여부를 복원한다. */
  function open(id, options = {}) {
    const url = new URL(window.location.href); url.searchParams.delete('draftProduct'); url.searchParams.delete('market'); url.searchParams.set('listingId', String(id));
    options.manage ? url.searchParams.set('listingManage', '1') : url.searchParams.delete('listingManage');
    window.history.replaceState(window.history.state, '', url);
    if (typeof show === 'function') show('detail');
  }
  /** 늦게 로드된 상세 HTML에 재조회·로그인·이력 페이지 버튼을 한 번만 연결한다. */
  function init() {
    if (initialized || !find('[data-listing-content]')) return;
    initialized = true;
    find('[data-listing-retry]').addEventListener('click', () => load());
    find('[data-listing-login]').addEventListener('click', () => { if (typeof show === 'function') show('auth'); });
    find('[data-listing-more]').addEventListener('click', () => { if (!loading) load(true); });
    if (currentId()) open(currentId(), { manage:new URL(window.location.href).searchParams.get('listingManage') === '1' });
  }
  document.addEventListener('pastport:page-loaded', event => { if (event.detail.id === 'detail') init(); });
  document.addEventListener('pastport:page-shown', event => {
    if (event.detail.id === 'detail') { load(); return; }
    // 상세를 떠나면 주소의 판매글 번호를 지워, 새로고침 때 상세로 되돌아오지 않게 한다.
    const url = new URL(window.location.href);
    if (url.searchParams.has('listingId')) { url.searchParams.delete('listingId'); window.history.replaceState(window.history.state, '', url); }
  });
  document.addEventListener('pastport:auth-changed', () => {
    request?.abort(); data = null;
    resetChat();
    window.PastportListingManagement?.reset(); window.PastportListingSummary?.reset();
    find('[data-listing-content]')?.replaceChildren();
    if (document.querySelector('#detail.active') && currentId()) load();
  });
  window.addEventListener('pageshow', event => { if (event.persisted && document.querySelector('#detail.active') && currentId()) load(); });
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
  window.PastportPublishedListing = Object.freeze({ open });
})();
