// 카테고리별로 널리 알려진 제조사. 제조사 필터는 표기가 정확히 같아야 걸리므로 영문 공식 표기를 쓴다.
(() => {
  const popular = ['Samsung', 'LG', 'Apple', 'Sony', 'Lenovo', 'Dyson'];
  const makers = Object.freeze({
    LAPTOP: ['Apple', 'Samsung', 'LG', 'Lenovo', 'ASUS', 'HP', 'Dell', 'MSI'],
    MOBILE: ['Apple', 'Samsung', 'Xiaomi', 'Google', 'Motorola'],
    DISPLAY: ['Samsung', 'LG', 'Sony', 'TCL', 'Dell', 'BenQ'],
    LARGE_APPLIANCE: ['Samsung', 'LG', 'Whirlpool', 'Bosch', 'Electrolux', 'Winia'],
    CLEANING_AIR: ['Dyson', 'Samsung', 'LG', 'Xiaomi', 'Roborock', 'Coway'],
    CLIMATE: ['Samsung', 'LG', 'Carrier', 'Winia', 'Daikin', 'Dyson'],
    KITCHEN: ['Cuckoo', 'Philips', 'Tefal', 'Balmuda', 'Smeg', 'LG'],
    AUDIO_GAME: ['Sony', 'Bose', 'Nintendo', 'Microsoft', 'JBL', 'Samsung'],
  });
  const all = [...new Set(Object.values(makers).flat())].sort((a, b) => a.localeCompare(b));
  window.PastportCategories = Object.freeze({ makers: (category) => makers[category] || popular, all: () => all });
})();
