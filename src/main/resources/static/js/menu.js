// 전체메뉴(메가 메뉴): 상단 네비게이션의 그룹을 읽어 한 번에 펼쳐 보여준다.
(() => {
  const toggle = document.querySelector('#nav-all');
  const mega = document.querySelector('#mega');
  const grid = document.querySelector('#mega-grid');
  if (!toggle || !mega || !grid) return;

  const groups = [...document.querySelectorAll('.landing-nav .nav-item')].filter((item) => item.querySelector('.nav-drop'));
  grid.append(
    ...groups.map((item) => {
      const column = document.createElement('div');
      column.className = 'mega-col';
      const title = item.querySelector(':scope > button').cloneNode(true);
      title.className = 'mega-title';
      column.append(title, ...[...item.querySelectorAll('.nav-drop button')].map((button) => button.cloneNode(true)));
      return column;
    })
  );

  const setOpen = (open) => {
    mega.hidden = !open;
    toggle.setAttribute('aria-expanded', String(open));
    toggle.classList.toggle('open', open);
  };

  toggle.addEventListener('click', () => setOpen(mega.hidden));
  mega.addEventListener('click', (event) => {
    if (event.target.closest('[data-page]')) setOpen(false);
  });
  document.addEventListener('click', (event) => {
    if (!mega.hidden && !event.target.closest('#mega, #nav-all')) setOpen(false);
  });
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && !mega.hidden) {
      setOpen(false);
      toggle.focus();
    }
  });
})();
