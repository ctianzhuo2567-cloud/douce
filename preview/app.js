const patterns = [
  { id: 1, title: "甜品小合集", image: "../拼豆测试图纸/1.jpg", status: "待整理", favorite: true, tags: ["合集"] },
  { id: 2, title: "春日采集·黄水仙", image: "../拼豆测试图纸/2.jpg", status: "待制作", favorite: false, tags: [] },
  { id: 3, title: "彩色徽章合集", image: "../拼豆测试图纸/3.jpg", status: "制作中", favorite: true, tags: ["合集"] },
  { id: 4, title: "红色像素人物", image: "../拼豆测试图纸/4.jpg", status: "待整理", favorite: false, tags: ["角色"] },
  { id: 5, title: "粉色角色图纸", image: "../拼豆测试图纸/5.jpg", status: "待制作", favorite: false, tags: ["角色", "大图"] },
  { id: 6, title: "旋转猫猫 Annie", image: "../拼豆测试图纸/6.jpg", status: "制作中", favorite: false, tags: ["角色", "大图"] },
  { id: 7, title: "正视图角色", image: "../拼豆测试图纸/7.jpg", status: "已完成", favorite: false, tags: ["角色"] },
];

const $ = (selector) => document.querySelector(selector);
const grid = $("#patternGrid");
let activePattern = patterns[0];
let mirrorX = false;
let mirrorY = false;
let tagFilter = "all";
let statusFilter = "all";
let stockFilter = "all";
let stockGroup = "全部";
let quickMode = "batch";
let quickGroup = "全部";
let packageOperation = "add";
const stockDrafts = {};
const quantities = Object.fromEntries(window.MARD221.map((color) => [color.code, null]));
Object.assign(quantities, { A1: 500, A4: 80, A20: 300, B5: 900, B22: 60, C3: 240, D4: 100, F5: 700, G8: 1200, H2: 50, H7: 1000, M3: 400 });

function renderPatterns() {
  const query = $("#searchInput").value.trim().toLowerCase();
  const visible = patterns.filter((item) => {
    const matchesQuery = !query || item.title.toLowerCase().includes(query) || item.tags.some((tag) => tag.includes(query));
    const matchesTag = tagFilter === "all" ||
      (tagFilter === "favorite" && item.favorite) ||
      (tagFilter === "untagged" && item.tags.length === 0) ||
      item.tags.includes(tagFilter);
    const matchesStatus = statusFilter === "all" || item.status === statusFilter;
    return matchesQuery && matchesTag && matchesStatus;
  });
  grid.innerHTML = visible.map((item) => `
    <article class="pattern-card" data-id="${item.id}">
      <img src="${item.image}" alt="${item.title}">
      <button class="card-heart" data-favorite="${item.id}" aria-label="收藏">${item.favorite ? "♥" : "♡"}</button>
      <div class="pattern-copy"><strong>${item.title}</strong><div class="pattern-meta"><span class="ticket">${item.status}</span><span>MARD</span></div></div>
    </article>`).join("");
  $("#patternAllStat").textContent = patterns.length;
  $("#patternReadyStat").textContent = patterns.filter((item) => item.status === "待制作").length;
  $("#patternDoingStat").textContent = patterns.filter((item) => item.status === "制作中").length;
  $("#patternDoneStat").textContent = patterns.filter((item) => item.status === "已完成").length;
  $("#patternResultCount").textContent = `显示 ${visible.length} / ${patterns.length} 张`;
}

function showView(id) {
  document.querySelectorAll(".view").forEach((view) => view.classList.remove("active"));
  $(id).classList.add("active");
  const subview = id === "#detailView" || id === "#cropView" || id === "#quickEntryView";
  $("#bottomNav").style.display = subview ? "none" : "grid";
  $("#addButton").style.display = id === "#libraryView" ? "block" : "none";
  document.querySelectorAll("#bottomNav [data-main-view]").forEach((button) => {
    button.classList.toggle("active", `#${button.dataset.mainView}` === id);
  });
}

function renderStockStats() {
  const values = Object.values(quantities);
  $("#ownedStat").textContent = `${values.filter((value) => value > 0).length}/221`;
  $("#lowStat").textContent = values.filter((value) => value > 0 && value <= 100).length;
  $("#totalStat").textContent = `${values.reduce((sum, value) => sum + (value || 0), 0)} 颗`;
}

function renderStocks() {
  const query = $("#stockSearch").value.trim().toUpperCase();
  const visible = window.MARD221.filter((color) => {
    const quantity = quantities[color.code];
    const matchesQuery = !query || color.code.includes(query);
    const matchesGroup = stockGroup === "全部" || color.group === stockGroup;
    const matchesFilter = stockFilter === "all" ||
      (stockFilter === "owned" && quantity > 0) ||
      (stockFilter === "low" && quantity > 0 && quantity <= 100) ||
      (stockFilter === "empty" && quantity === 0) ||
      (stockFilter === "untracked" && quantity === null);
    return matchesQuery && matchesGroup && matchesFilter;
  });
  $("#stockGrid").innerHTML = visible.map((color) => {
    const quantity = quantities[color.code];
    const low = quantity > 0 && quantity <= 100;
    const label = quantity === null ? "未录入" : quantity === 0 ? "0 颗 · 缺货" : `${quantity} 颗${low ? " · 快用完" : ""}`;
    return `<article class="stock-card" data-stock-code="${color.code}">
      <div class="stock-bead" style="--swatch:${color.hex}"></div>
      <h3>${color.code}</h3><p class="${low ? "low" : ""}">${label}</p>
      <div class="stock-actions"><button data-adjust="-100" ${!quantity ? "disabled" : ""}>−</button><button data-adjust="100">＋</button></div>
    </article>`;
  }).join("");
  renderStockStats();
}

function updateMirror() {
  $("#detailImage").style.transform = `scale(${mirrorX ? -1 : 1}, ${mirrorY ? -1 : 1})`;
  $("#mirrorX").classList.toggle("active", mirrorX);
  $("#mirrorY").classList.toggle("active", mirrorY);
}

function openPattern(pattern) {
  activePattern = pattern;
  mirrorX = false;
  mirrorY = false;
  $("#detailTitle").textContent = pattern.title;
  $("#detailImage").src = pattern.image;
  $("#cropImage").src = pattern.image;
  $("#favoriteButton").textContent = pattern.favorite ? "♥" : "♡";
  updateMirror();
  showView("#detailView");
}

grid.addEventListener("click", (event) => {
  const favoriteButton = event.target.closest("[data-favorite]");
  if (favoriteButton) {
    event.stopPropagation();
    const item = patterns.find((p) => p.id === Number(favoriteButton.dataset.favorite));
    item.favorite = !item.favorite;
    renderPatterns();
    return;
  }
  const card = event.target.closest(".pattern-card");
  if (card) openPattern(patterns.find((p) => p.id === Number(card.dataset.id)));
});

$("#searchInput").addEventListener("input", renderPatterns);
$("#tagFilters").addEventListener("click", (event) => {
  const button = event.target.closest("[data-tag]");
  if (!button) return;
  tagFilter = button.dataset.tag;
  document.querySelectorAll("#tagFilters .chip").forEach((chip) => chip.classList.toggle("active", chip === button));
  renderPatterns();
});
$("#statusFilters").addEventListener("click", (event) => {
  const button = event.target.closest("[data-status]");
  if (!button) return;
  statusFilter = button.dataset.status;
  document.querySelectorAll("#statusFilters .chip").forEach((chip) => chip.classList.toggle("active", chip === button));
  renderPatterns();
});
$("#backButton").addEventListener("click", () => showView("#libraryView"));
$("#cropBackButton").addEventListener("click", () => showView("#detailView"));
$("#mirrorX").addEventListener("click", () => { mirrorX = !mirrorX; updateMirror(); });
$("#mirrorY").addEventListener("click", () => { mirrorY = !mirrorY; updateMirror(); });
$("#favoriteButton").addEventListener("click", () => {
  activePattern.favorite = !activePattern.favorite;
  $("#favoriteButton").textContent = activePattern.favorite ? "♥" : "♡";
  renderPatterns();
});
$("#cropButton").addEventListener("click", () => showView("#cropView"));
$("#saveCropButton").addEventListener("click", () => {
  $("#cropButton").textContent = "⌗　调整色号说明区域";
  showView("#detailView");
});
$("#addButton").addEventListener("click", () => alert("安卓正式版会打开系统图片选择器；电脑预览不读取新文件。"));
$("#bottomNav").addEventListener("click", (event) => {
  const button = event.target.closest("[data-main-view]");
  if (button) {
    showView(`#${button.dataset.mainView}`);
    return;
  }
  const placeholder = event.target.closest("[data-placeholder]");
  if (placeholder) alert(`${placeholder.dataset.placeholder}页在安卓框架中已有入口，本次电脑预览重点补充豆库。`);
});
$("#stockSearch").addEventListener("input", renderStocks);
$("#stockFilters").addEventListener("click", (event) => {
  const button = event.target.closest("[data-stock-filter]");
  if (!button) return;
  stockFilter = button.dataset.stockFilter;
  document.querySelectorAll("#stockFilters .chip").forEach((chip) => chip.classList.toggle("active", chip === button));
  renderStocks();
});
$("#seriesFilters").innerHTML = ["全部", "A", "B", "C", "D", "E", "F", "G", "H", "M"]
  .map((series, index) => `<button class="chip ${index === 0 ? "active" : ""}" data-series="${series}">${series}</button>`).join("");
$("#seriesFilters").addEventListener("click", (event) => {
  const button = event.target.closest("[data-series]");
  if (!button) return;
  stockGroup = button.dataset.series;
  document.querySelectorAll("#seriesFilters .chip").forEach((chip) => chip.classList.toggle("active", chip === button));
  renderStocks();
});
$("#stockGrid").addEventListener("click", (event) => {
  const card = event.target.closest("[data-stock-code]");
  if (!card) return;
  const code = card.dataset.stockCode;
  const adjust = event.target.closest("[data-adjust]");
  if (adjust) {
    quantities[code] = Math.max(0, (quantities[code] || 0) + Number(adjust.dataset.adjust));
  } else {
    const entered = prompt(`${code} 现有多少颗？`, quantities[code] ?? "");
    if (entered === null || !/^\d+$/.test(entered.trim())) return;
    quantities[code] = Number(entered);
  }
  renderStocks();
});

function renderQuickEntry() {
  const visible = window.MARD221.filter((color) => quickGroup === "全部" || color.group === quickGroup);
  $("#batchEntryPanel").hidden = quickMode !== "batch";
  $("#packageEntryPanel").hidden = quickMode !== "package";
  $("#batchRows").innerHTML = visible.map((color) => {
    const draft = stockDrafts[color.code];
    const current = quantities[color.code];
    return `<div class="batch-row" data-quick-code="${color.code}">
      <div class="mini-bead" style="--swatch:${color.hex}"></div>
      <span class="batch-code">${color.code}<small>${current === null ? "未录" : current}</small></span>
      <input class="batch-input" inputmode="numeric" data-draft-input value="${draft ?? ""}" placeholder="数量">
      <button class="quick-add" data-quick-add="500">+500</button>
      <button class="quick-add" data-quick-add="1000">+1000</button>
      <button class="quick-add" data-quick-add="1200">+1200</button>
    </div>`;
  }).join("");
  const draftValues = Object.values(stockDrafts);
  $("#draftColorCount").textContent = draftValues.length;
  $("#draftTotal").textContent = draftValues.reduce((sum, value) => sum + value, 0);
  const packageAmount = Number($("#packageAmount").value);
  $("#applyQuickEntry").disabled = quickMode === "batch" ? draftValues.length === 0 : !Number.isInteger(packageAmount) || packageAmount < 0;
  $("#applyQuickEntry").textContent = quickMode === "batch" ? `确认录入 ${draftValues.length} 个色号` : "确认套装入库";
}

$("#quickEntryButton").addEventListener("click", () => {
  renderQuickEntry();
  showView("#quickEntryView");
});
$("#quickBackButton").addEventListener("click", () => showView("#inventoryView"));
$("#quickModeTabs").addEventListener("click", (event) => {
  const button = event.target.closest("[data-quick-mode]");
  if (!button) return;
  quickMode = button.dataset.quickMode;
  document.querySelectorAll("#quickModeTabs .chip").forEach((chip) => chip.classList.toggle("active", chip === button));
  renderQuickEntry();
});
$("#quickSeriesFilters").innerHTML = ["全部", "A", "B", "C", "D", "E", "F", "G", "H", "M"]
  .map((series, index) => `<button class="chip ${index === 0 ? "active" : ""}" data-quick-series="${series}">${series}</button>`).join("");
$("#quickSeriesFilters").addEventListener("click", (event) => {
  const button = event.target.closest("[data-quick-series]");
  if (!button) return;
  quickGroup = button.dataset.quickSeries;
  document.querySelectorAll("#quickSeriesFilters .chip").forEach((chip) => chip.classList.toggle("active", chip === button));
  renderQuickEntry();
});
$("#batchRows").addEventListener("input", (event) => {
  if (!event.target.matches("[data-draft-input]")) return;
  const code = event.target.closest("[data-quick-code]").dataset.quickCode;
  const digits = event.target.value.replace(/\D/g, "").slice(0, 7);
  event.target.value = digits;
  if (digits === "") delete stockDrafts[code]; else stockDrafts[code] = Number(digits);
  const values = Object.values(stockDrafts);
  $("#draftColorCount").textContent = values.length;
  $("#draftTotal").textContent = values.reduce((sum, value) => sum + value, 0);
  $("#applyQuickEntry").disabled = values.length === 0;
  $("#applyQuickEntry").textContent = `确认录入 ${values.length} 个色号`;
});
$("#batchRows").addEventListener("click", (event) => {
  const button = event.target.closest("[data-quick-add]");
  if (!button) return;
  const row = button.closest("[data-quick-code]");
  const code = row.dataset.quickCode;
  stockDrafts[code] = (stockDrafts[code] ?? quantities[code] ?? 0) + Number(button.dataset.quickAdd);
  renderQuickEntry();
});
$("#packageAmount").addEventListener("input", (event) => {
  event.target.value = event.target.value.replace(/\D/g, "").slice(0, 7);
  renderQuickEntry();
});
$("#packageOperations").addEventListener("click", (event) => {
  const button = event.target.closest("[data-operation]");
  if (!button) return;
  packageOperation = button.dataset.operation;
  document.querySelectorAll("#packageOperations .chip").forEach((chip) => chip.classList.toggle("active", chip === button));
  $("#packageHint").textContent = packageOperation === "add" ? "在现有数量上增加；未录入色号从 0 开始。" : "把每个色号直接改成填写数量，适合第一次建立豆库。";
});
$("#applyQuickEntry").addEventListener("click", () => {
  if (quickMode === "batch") {
    const count = Object.keys(stockDrafts).length;
    if (!count || !confirm(`确认保存 ${count} 个色号的新数量？未填写的色号保持不变。`)) return;
    Object.assign(quantities, stockDrafts);
    Object.keys(stockDrafts).forEach((code) => delete stockDrafts[code]);
  } else {
    const amount = Number($("#packageAmount").value);
    const operationLabel = packageOperation === "add" ? "追加" : "设为";
    if (!Number.isInteger(amount) || amount < 0 || !confirm(`确认将 MARD221 全部色号${operationLabel}每色 ${amount} 颗？`)) return;
    window.MARD221.forEach((color) => {
      quantities[color.code] = packageOperation === "add" ? (quantities[color.code] ?? 0) + amount : amount;
    });
  }
  renderStocks();
  showView("#inventoryView");
});

const cropStage = $("#cropStage");
const cropBox = $("#cropBox");
const resizeHandle = $("#resizeHandle");
let gesture = null;

function pointerRatio(event) {
  const rect = cropStage.getBoundingClientRect();
  return { x: (event.clientX - rect.left) / rect.width, y: (event.clientY - rect.top) / rect.height };
}
cropBox.addEventListener("pointerdown", (event) => {
  const stage = cropStage.getBoundingClientRect();
  const box = cropBox.getBoundingClientRect();
  gesture = {
    mode: event.target === resizeHandle ? "resize" : "move",
    start: pointerRatio(event),
    left: (box.left - stage.left) / stage.width,
    top: (box.top - stage.top) / stage.height,
    width: box.width / stage.width,
    height: box.height / stage.height,
  };
  cropBox.setPointerCapture(event.pointerId);
});
cropBox.addEventListener("pointermove", (event) => {
  if (!gesture) return;
  const point = pointerRatio(event);
  const dx = point.x - gesture.start.x;
  const dy = point.y - gesture.start.y;
  if (gesture.mode === "move") {
    cropBox.style.left = `${Math.max(0, Math.min(1 - gesture.width, gesture.left + dx)) * 100}%`;
    cropBox.style.top = `${Math.max(0, Math.min(1 - gesture.height, gesture.top + dy)) * 100}%`;
  } else {
    cropBox.style.width = `${Math.max(.12, Math.min(1 - gesture.left, gesture.width + dx)) * 100}%`;
    cropBox.style.height = `${Math.max(.1, Math.min(1 - gesture.top, gesture.height + dy)) * 100}%`;
  }
});
cropBox.addEventListener("pointerup", () => { gesture = null; });
cropBox.addEventListener("pointercancel", () => { gesture = null; });

renderPatterns();
renderStocks();
