const savedKey = 'tripmind_saved';

function getSaved() {
  try {
    const arr = JSON.parse(localStorage.getItem(savedKey) || '[]');
    return Array.isArray(arr) ? arr : [];
  } catch (_) {
    return [];
  }
}

function setSaved(items) {
  localStorage.setItem(savedKey, JSON.stringify(items));
}

function formatNum(n) {
  return Number(n || 0).toLocaleString('en-IN');
}

function renderSaved() {
  const grid = document.getElementById('savedGrid');
  const empty = document.getElementById('emptyState');
  if (!grid || !empty) return;

  const items = getSaved().slice().reverse();
  if (items.length === 0) {
    empty.style.display = 'block';
    grid.innerHTML = '';
    return;
  }

  empty.style.display = 'none';

  grid.innerHTML = items.map((it, idx) => {
    const dest = it.destination || 'Unknown';
    const total = it.costBreakdown?.total ?? it.budgetEstimate?.total ?? 0;
    const savedAt = it.savedAt ? new Date(it.savedAt).toLocaleString('en-IN') : '';
    const reason = (it.justification || '').toString().slice(0, 140);

    return `
      <div class="card history-card">
        <div style="display:flex; justify-content:space-between; align-items:start; gap:10px; margin-bottom:12px;">
          <div style="font-weight:900; font-size:1.15rem;">${dest}</div>
          <span style="font-size:0.78rem; color:var(--text-muted); background:var(--bg-secondary); padding:4px 10px; border-radius:999px;">
            ${savedAt || 'Saved'}
          </span>
        </div>
        <div style="color:var(--text-secondary); font-size:0.95rem; line-height:1.5;">${reason}${reason.length >= 140 ? '…' : ''}</div>
        <div style="display:flex; justify-content:space-between; align-items:center; margin-top:14px;">
          <div style="font-family:var(--font-primary); font-weight:900; color:var(--accent-tertiary);">₹${formatNum(total)}</div>
          <div style="display:flex; gap:8px;">
            <button class="btn btn-secondary btn-sm" onclick="openSaved(${items.length - 1 - idx})">Open</button>
            <button class="btn btn-secondary btn-sm" onclick="removeSaved(${items.length - 1 - idx})">Remove</button>
          </div>
        </div>
      </div>
    `;
  }).join('');
}

function openSaved(indexFromOldest) {
  const items = getSaved();
  const item = items[indexFromOldest];
  if (!item) return;
  sessionStorage.setItem('tripResult', JSON.stringify(item));
  window.location.href = 'result.html';
}

function removeSaved(indexFromOldest) {
  const items = getSaved();
  items.splice(indexFromOldest, 1);
  setSaved(items);
  renderSaved();
}

function clearSaved() {
  if (!confirm('Clear all saved trips?')) return;
  setSaved([]);
  renderSaved();
}

function exportSaved() {
  const items = getSaved();
  const blob = new Blob([JSON.stringify(items, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = 'tripmind_saved.json';
  a.click();
  URL.revokeObjectURL(url);
}

document.addEventListener('DOMContentLoaded', renderSaved);

