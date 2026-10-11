import * as pdfjsLib from '../build/pdf.min.mjs';

pdfjsLib.GlobalWorkerOptions.workerSrc = '../build/pdf.worker.min.mjs';

function getParam(name) {
  const url = new URL(window.location.href);
  return url.searchParams.get(name);
}

const fileUrl = getParam('file') || './compressed.tracemonkey-pldi-09.pdf';

const toolbar = document.createElement('div');
toolbar.id = 'toolbar';
toolbar.innerHTML = `
  <button id="prevBtn">上一页</button>
  <button id="nextBtn">下一页</button>
  <span>页码 <input id="pageInput" type="number" min="1" value="1"> / <span id="pageCount">?</span></span>
  <div class="spacer"></div>
  <label>缩放</label>
  <input id="scaleInput" type="number" step="0.1" min="0.25" max="4" value="1.0">
  <button id="fitBtn">自适应</button>
`;
document.body.prepend(toolbar);

const viewer = document.createElement('div');
viewer.id = 'viewer';
const canvas = document.createElement('canvas');
viewer.appendChild(canvas);
document.body.appendChild(viewer);

const ctx = canvas.getContext('2d');
let pdfDoc = null;
let currentPage = 1;
let currentScale = 1.0;

async function renderPage(num) {
  const page = await pdfDoc.getPage(num);
  const viewport = page.getViewport({ scale: currentScale });
  const outputScale = window.devicePixelRatio || 1;
  canvas.width = Math.floor(viewport.width * outputScale);
  canvas.height = Math.floor(viewport.height * outputScale);
  canvas.style.width = Math.floor(viewport.width) + 'px';
  canvas.style.height = Math.floor(viewport.height) + 'px';
  const transform = outputScale !== 1 ? [outputScale, 0, 0, outputScale, 0, 0] : null;
  const renderContext = {
    canvasContext: ctx,
    viewport,
    transform
  };
  await page.render(renderContext).promise;
  document.getElementById('pageInput').value = String(num);
}

async function queueRenderPage(num) {
  currentPage = num;
  await renderPage(num);
}

function onPrevPage() {
  if (currentPage <= 1) return;
  queueRenderPage(currentPage - 1);
}
function onNextPage() {
  if (currentPage >= pdfDoc.numPages) return;
  queueRenderPage(currentPage + 1);
}
function onScaleChange(val) {
  const v = Math.min(4, Math.max(0.25, Number(val) || 1));
  currentScale = v;
  queueRenderPage(currentPage);
}

document.getElementById('prevBtn').addEventListener('click', onPrevPage);
document.getElementById('nextBtn').addEventListener('click', onNextPage);
document.getElementById('scaleInput').addEventListener('change', e => onScaleChange(e.target.value));
document.getElementById('fitBtn').addEventListener('click', () => {
  currentScale = 1.0;
  document.getElementById('scaleInput').value = '1.0';
  queueRenderPage(currentPage);
});
document.getElementById('pageInput').addEventListener('change', e => {
  let num = Number(e.target.value) || 1;
  num = Math.min(pdfDoc.numPages, Math.max(1, num));
  queueRenderPage(num);
});

pdfjsLib.getDocument(fileUrl).promise.then(async doc => {
  pdfDoc = doc;
  document.getElementById('pageCount').textContent = String(pdfDoc.numPages);
  await renderPage(1);
}).catch(err => {
  const msg = document.createElement('div');
  msg.style.color = 'red';
  msg.style.padding = '12px';
  msg.textContent = 'PDF 加载失败: ' + err;
  document.body.appendChild(msg);
});
