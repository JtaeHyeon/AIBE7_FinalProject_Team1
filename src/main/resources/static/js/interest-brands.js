// 메인 화면의 "내 관심 브랜드": 사용자가 고른 브랜드만 보여 준다. 선택은 이 브라우저에 계정별로 저장한다.
(() => {
  let userId = null;
  const key = () => 'pastport.interestBrands.' + (userId ?? 'guest');
  const read = () => {
    try { const value = JSON.parse(localStorage.getItem(key()) || '[]'); return Array.isArray(value) ? value : []; } catch { return []; }
  };
  const write = (brands) => { try { localStorage.setItem(key(), JSON.stringify(brands)); } catch { /* 저장 불가 환경에서는 이번 방문에만 반영 */ } };
  const find = (selector) => document.querySelector('#landing ' + selector);

  function render() {
    const list = find('[data-interest-list]'); const editor = find('[data-interest-editor]');
    if (!list || !editor) return;
    const picked = read();
    list.replaceChildren();
    if (!picked.length) {
      const empty = document.createElement('p'); empty.className = 'lpx-interest-empty';
      empty.textContent = '아직 고른 브랜드가 없어요. 오른쪽 위 "브랜드 선택"에서 관심 있는 브랜드를 골라 보세요.';
      list.append(empty);
    }
    picked.forEach((name) => {
      const link = document.createElement('a'); link.href = '/?market=1&manufacturer=' + encodeURIComponent(name); link.textContent = name;
      list.append(link);
    });
    editor.replaceChildren(...(window.PastportCategories?.all() || []).map((name) => {
      const on = picked.includes(name);
      const button = document.createElement('button'); button.type = 'button'; button.textContent = name;
      button.className = 'chip-btn' + (on ? ' active' : ''); button.setAttribute('aria-pressed', String(on));
      button.addEventListener('click', () => { write(read().includes(name) ? read().filter((item) => item !== name) : [...read(), name]); render(); });
      return button;
    }));
  }
  function bind() {
    const toggle = find('[data-interest-edit]'); if (!toggle || toggle.dataset.bound) return;
    toggle.dataset.bound = '1';
    toggle.addEventListener('click', () => {
      const editor = find('[data-interest-editor]'); editor.hidden = !editor.hidden;
      toggle.setAttribute('aria-expanded', String(!editor.hidden)); toggle.textContent = editor.hidden ? '브랜드 선택' : '완료';
    });
    render();
  }
  document.addEventListener('pastport:page-loaded', (event) => { if (event.detail.id === 'landing') bind(); });
  document.addEventListener('pastport:auth-changed', (event) => { userId = event.detail?.userId ?? null; render(); });
  bind();
})();
