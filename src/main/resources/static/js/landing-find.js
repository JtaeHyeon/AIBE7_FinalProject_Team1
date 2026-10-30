// 메인 "어떤 제품을 찾으세요?": 탭 전환, 카테고리에 맞는 제조사 목록, 선택 조건으로 마켓 이동.
(() => {
  const find = (selector) => document.querySelector('#landing ' + selector);

  function fillMakers() {
    const category = find('[data-find-category]'); const maker = find('[data-find-maker]');
    if (!category || !maker) return;
    const names = window.PastportCategories?.makers(category.value) || [];
    maker.replaceChildren(new Option('제조사 전체', ''), ...names.map((name) => new Option(name, name)));
  }
  function showTab(name) {
    document.querySelectorAll('#landing [data-find-tab]').forEach((tab) => tab.setAttribute('aria-selected', String(tab.dataset.findTab === name)));
    document.querySelectorAll('#landing [data-find-panel]').forEach((panel) => { panel.hidden = panel.dataset.findPanel !== name; });
  }
  function go(form) {
    const params = new URLSearchParams({ market: '1' });
    new FormData(form).forEach((value, key) => { if (String(value).trim()) params.set(key, String(value).trim()); });
    window.location.href = '/?' + params.toString();
  }
  document.addEventListener('pastport:page-loaded', (event) => { if (event.detail.id === 'landing') fillMakers(); });
  document.addEventListener('change', (event) => { if (event.target.matches?.('#landing [data-find-category]')) fillMakers(); });
  document.addEventListener('click', (event) => {
    const tab = event.target.closest?.('#landing [data-find-tab]');
    if (tab) showTab(tab.dataset.findTab);
  });
  document.addEventListener('submit', (event) => {
    const form = event.target.closest?.('#landing [data-find-form]');
    if (!form) return;
    event.preventDefault(); go(form);
  });
})();
