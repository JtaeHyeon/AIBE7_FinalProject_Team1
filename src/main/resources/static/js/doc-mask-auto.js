// 문서 마스킹 화면의 "자동 가리기": 브라우저 안에서 글자를 읽고(OCR), 개인정보 형태의 글자 위치를 찾아 가릴 영역으로 제안한다.
// 원본 이미지는 서버로 보내지 않는다. 외부에서 받는 것은 OCR 라이브러리와 언어 데이터뿐이다.
(() => {
  'use strict';

  const TESSERACT_URL = 'https://cdn.jsdelivr.net/npm/tesseract.js@5.1.1/dist/tesseract.min.js';
  const LANGUAGES = ['kor', 'eng'];

  const CITY = '(?:서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충북|충남|전북|전남|경북|경남|제주)';
  // OCR이 한글을 글자 단위로 끊어 읽는 경우가 있어서 라벨은 글자 사이 공백을 허용한다. 예: "주 문 번호"
  const label = (words) => '(?:' + words.map((word) => Array.from(word).join('\\s*')).join('|') + ')';
  const NAME_LABEL = label(['성명', '이름', '고객명', '구매자', '주문자', '수령인', '받는분', '예금주']);
  const ID_LABEL = label(['주문번호', '승인번호', '결제번호', '송장번호', '운송장번호', '거래번호']);
  const DATE_LIKE = /^(?:19|20)\d{2}-\d{1,2}-\d{1,2}$/;

  // group은 가릴 부분이 정규식의 몇 번째 묶음인지(0이면 일치한 전체). accept가 false를 돌려주면 건너뛴다.
  const RULES = [
    { kind: '주민등록번호', pattern: /\d{6}\s*[-–]?\s*[1-4]\d{6}/dg },
    { kind: '카드번호', pattern: /(?:\d{4}[-\s]){3}\d{4}/dg },
    { kind: '전화번호', pattern: /(?<![\dA-Za-z])(?:0\d{1,2}[\s.-]?\d{3,4}[\s.-]\d{4}|01\d\d{7,8})/dg },
    { kind: '이메일', pattern: /[\w.+-]+@[\w-]+(?:\.[\w-]+)+/dg },
    {
      kind: '계좌번호',
      pattern: /\d{2,6}-\d{2,6}-\d{2,8}(?:-\d{1,4})?/dg,
      accept: (text) => !DATE_LIKE.test(text) && text.replace(/\D/g, '').length >= 10,
    },
    { kind: '주문번호', pattern: new RegExp(`${ID_LABEL}\\s*[:：]?\\s*([A-Za-z0-9-]{6,})`, 'dg'), group: 1 },
    { kind: '이름', pattern: new RegExp(`${NAME_LABEL}\\s*[:：]?\\s*([가-힣](?:\\s?[가-힣]){1,3})(?![가-힣])`, 'dg'), group: 1 },
    { kind: '주소', pattern: new RegExp(`${CITY}(?:특별시|광역시|특별자치시|특별자치도|도)?\\s*[가-힣]{1,6}\\s*(?:시|군|구)\\s.*`, 'dg') },
    { kind: '주소', pattern: /(?:주소|배송지)\s*[:：]?\s*(.{4,})/dg, group: 1 },
  ];

  // lines: [{ words: [{ text, x0, y0, x1, y1 }] }] → 가릴 영역 [{ x, y, w, h, kind }]
  function findPiiBoxes(lines) {
    const boxes = [];
    for (const line of lines) {
      const words = (line.words || []).filter((word) => word.text && word.text.trim());
      if (!words.length) continue;
      let text = '';
      const spans = words.map((word) => {
        const start = text.length;
        text += word.text.trim();
        const span = { word, start, end: text.length };
        text += ' ';
        return span;
      });
      for (const rule of RULES) {
        for (const match of text.matchAll(rule.pattern)) {
          const group = rule.group || 0;
          if (!match.indices || !match.indices[group]) continue;
          const [from, to] = match.indices[group];
          if (rule.accept && !rule.accept(text.slice(from, to))) continue;
          const hit = spans.filter((span) => span.start < to && span.end > from).map((span) => span.word);
          if (!hit.length) continue;
          boxes.push(union(hit, rule.kind));
        }
      }
    }
    return boxes.filter((box, index) => !boxes.some((other, otherIndex) => otherIndex !== index && covers(other, box, otherIndex < index)));
  }

  function union(words, kind) {
    const x0 = Math.min(...words.map((word) => word.x0));
    const y0 = Math.min(...words.map((word) => word.y0));
    const x1 = Math.max(...words.map((word) => word.x1));
    const y1 = Math.max(...words.map((word) => word.y1));
    const pad = Math.max(2, Math.round((y1 - y0) * 0.15));
    return { x: Math.max(0, x0 - pad), y: Math.max(0, y0 - pad), w: x1 - x0 + pad * 2, h: y1 - y0 + pad * 2, kind };
  }

  // outer가 inner를 완전히 덮으면 true. 크기가 같으면 먼저 나온 것만 남기도록 sameWins로 가른다.
  function covers(outer, inner, sameWins) {
    const inside = outer.x <= inner.x && outer.y <= inner.y && outer.x + outer.w >= inner.x + inner.w && outer.y + outer.h >= inner.y + inner.h;
    if (!inside) return false;
    const same = outer.w === inner.w && outer.h === inner.h && outer.x === inner.x && outer.y === inner.y;
    return same ? sameWins : true;
  }

  function loadTesseract() {
    if (window.Tesseract) return Promise.resolve(window.Tesseract);
    return new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = TESSERACT_URL;
      script.crossOrigin = 'anonymous';
      script.onload = () => (window.Tesseract ? resolve(window.Tesseract) : reject(new Error('tesseract')));
      script.onerror = () => reject(new Error('tesseract'));
      document.head.appendChild(script);
    });
  }

  // canvas의 글자를 읽어 가릴 영역을 돌려준다. onProgress(0~1)로 진행률을 알린다.
  async function detect(canvas, onProgress) {
    const Tesseract = await loadTesseract();
    const worker = await Tesseract.createWorker(LANGUAGES, 1, {
      logger: (message) => {
        if (onProgress && message.status === 'recognizing text') onProgress(message.progress);
      },
    });
    try {
      const { data } = await worker.recognize(canvas);
      const lines = (data.lines || []).map((line) => ({
        words: (line.words || []).map((word) => ({
          text: word.text,
          x0: word.bbox.x0,
          y0: word.bbox.y0,
          x1: word.bbox.x1,
          y1: word.bbox.y1,
        })),
      }));
      return findPiiBoxes(lines);
    } finally {
      await worker.terminate();
    }
  }

  window.PastportDocMaskAuto = { detect, findPiiBoxes };
})();
