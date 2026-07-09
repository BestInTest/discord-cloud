'use strict';

const Icons = {};

async function loadIcons() {
    const iconFiles = {
        folder: '/icons/folder.svg',
        file: '/icons/file.svg',
        video: '/icons/file_video.svg',
        audio: '/icons/audio_file.svg',
        image: '/icons/file_image.svg',
        archive: '/icons/file_archive.svg',
        doc: '/icons/file_docs.svg',
        upload: '/icons/upload.svg',
        folder_add: '/icons/folder_add.svg',
        edit: '/icons/edit.svg',
    };

    for (const [name, path] of Object.entries(iconFiles)) {
        try {
            const response = await fetch(path);
            Icons[name] = await response.text();
        } catch (err) {
            console.error(`Failed to load icon ${name}:`, err);
            Icons[name] = '';
        }
    }
    const uploadBtnIcon = document.getElementById('upload-btn-icon');
    if (uploadBtnIcon && Icons.upload) uploadBtnIcon.innerHTML = Icons.upload;

    const newFolderBtnIcon = document.getElementById('new-folder-btn-icon');
    if (newFolderBtnIcon && Icons.folder_add) newFolderBtnIcon.innerHTML = Icons.folder_add;
}

const FILE_TYPES = {
    video: ['mp4', 'webm', 'mkv', 'avi', 'mov', 'flv', 'm4v'],
    audio: ['mp3', 'wav', 'ogg', 'flac', 'aac', 'm4a', 'opus'],
    image: ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg', 'ico', 'avif'],
    archive: ['zip', 'tar', 'gz', 'bz2', 'xz', 'rar', '7z'],
    doc: ['pdf', 'txt', 'md', 'doc', 'docx', 'xls', 'xlsx', 'csv'],
};

// Viewable directly in browser (open in new tab instead of download)
const VIEWABLE_TYPES = new Set([...FILE_TYPES.video, ...FILE_TYPES.audio, ...FILE_TYPES.image, 'pdf']);

function getFileType(name) {
    const ext = name.split('.').pop().toLowerCase();
    for (const [type, exts] of Object.entries(FILE_TYPES)) {
        if (exts.includes(ext)) return type;
    }
    return 'other';
}

function getFileIcon(name) {
    const type = getFileType(name);
    return Icons[type] ?? Icons.file;
}



function formatSize(bytes) {
    if (!bytes || bytes === 0) return '';
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
    return `${(bytes / Math.pow(1024, i)).toFixed(i === 0 ? 0 : 1)} ${units[i]}`;
}

function formatDate(ts) {
    if (!ts) return '';
    return new Date(ts).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}


// entry.path is like "movie.mp4.dscl" (root) or "videos/movie.mp4.dscl" (subdir)
function buildFileUrl(entry) {
    const segments = entry.path.split('/');
    const filename = segments.pop();
    const dir = segments.join('/');
    const base = `/file/${encodeURIComponent(filename)}`;
    return dir ? `${base}?path=${encodeURIComponent(dir)}` : base;
}

function encodeHashPath(path) {
    if (!path) return '';
    return path.split('/').map(segment => encodeURIComponent(segment)).join('/');
}

function decodeHashPath(hash) {
    const value = hash.replace(/^#/, '');
    if (!value) return '';
    return value.split('/').map(segment => decodeURIComponent(segment)).join('/');
}


const Api = {
    async listFiles(path) {
        const url = path ? `/api/files?path=${encodeURIComponent(path)}` : '/api/files';
        const res = await fetch(url, { credentials: 'same-origin' });

        if (res.status === 401)
            throw Object.assign(new Error('UNAUTHORIZED'), { status: 401 });
        if (!res.ok)
            throw new Error(`Server error ${res.status}`);
        return res.json();
    },

    async login(token) {
        const res = await fetch('/api/auth', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ token }),
        });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            throw new Error(data.error || 'Invalid token');
        }
        return res.json();
    },

    async logout() {
        await fetch('/api/logout', { method: 'POST', credentials: 'same-origin' });
    },

    async uploadMethods() {
        const res = await fetch('/api/upload/methods', { credentials: 'same-origin' });
        if (!res.ok) throw new Error('Cannot fetch methods');
        return res.json(); // { hasWebhook, hasBot }
    },

    startUpload(file, path, uploadType, chunkSizeMb, onProgress) {
        return new Promise((resolve, reject) => {
            const form = new FormData();
            form.append('file', file, file.name);
            form.append('path', path || '');
            form.append('uploadType', uploadType);
            if (uploadType === 'BOT' && chunkSizeMb != null) {
                form.append('chunkSizeMb', String(chunkSizeMb));
            }

            const xhr = new XMLHttpRequest();
            xhr.withCredentials = true;

            if (onProgress) {
                xhr.upload.addEventListener('progress', e => {
                    if (e.lengthComputable) {
                        onProgress(e.loaded, e.total);
                    }
                });
                // Fire 100% when server starts processing
                xhr.upload.addEventListener('load', () => onProgress(file.size, file.size));
            }

            xhr.addEventListener('load', () => {
                try {
                    const data = JSON.parse(xhr.responseText);
                    if (xhr.status >= 200 && xhr.status < 300) resolve(data);
                    else reject(new Error(data.error || `HTTP ${xhr.status}`));
                } catch {
                    if (xhr.status >= 200 && xhr.status < 300) resolve({});
                    else reject(new Error(`HTTP ${xhr.status}`));
                }
            });
            xhr.addEventListener('error', () => reject(new Error('Network error')));
            xhr.addEventListener('abort', () => reject(new Error('Upload aborted')));

            xhr.open('POST', '/api/upload');
            xhr.send(form);
        });
    },

    async uploadStatus() {
        const res = await fetch('/api/upload/status', { credentials: 'same-origin' });
        if (!res.ok) throw new Error('Status error');
        return res.json();
    },

    async uploadReset() {
        await fetch('/api/upload/reset', { method: 'POST', credentials: 'same-origin' });
    },

    async createFolder(path, name) {
        const res = await fetch('/api/folder/create', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ path: path || '', name }),
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            throw new Error(data.error || `HTTP ${res.status}`);
        }
        return data;
    },

    async renameFolder(path, newName) {
        const res = await fetch('/api/folder/rename', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ path, newName }),
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            throw new Error(data.error || `HTTP ${res.status}`);
        }
        return data;
    },
};


const $ = id => document.getElementById(id);

function el(tag, attrs = {}, children = []) {
    const node = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs)) {
        if (k === 'class') node.className = v;
        else if (k === 'html') node.innerHTML = v;
        else if (k.startsWith('on')) node.addEventListener(k.slice(2), v);
        else node.setAttribute(k, v);
    }
    children.forEach(c => node.appendChild(typeof c === 'string' ? document.createTextNode(c) : c));
    return node;
}

// UI
let currentPath = '';

const ui = {
    app: $('app'),
    login: $('login-overlay'),
    browser: $('browser'),
    crumb: $('breadcrumb'),
    tokenInput: $('token-input'),
    loginError: $('login-error'),
    loginBtn: $('login-btn'),
    logoutBtn: $('logout-btn'),
    newFolderBtn: $('new-folder-btn'),
    uploadBtn: $('upload-btn'),
    fileInput: $('file-input'),
    uploadOverlay: $('upload-overlay'),
    // confirm panel
    confirmPanel: $('upload-confirm-panel'),
    confirmFileName: $('confirm-file-name'),
    confirmDestPath: $('confirm-dest-path'),
    labelWebhook: $('label-webhook'),
    labelBot: $('label-bot'),
    radioWebhook: $('radio-webhook'),
    radioBot: $('radio-bot'),
    noMethodMsg: $('no-method-msg'),
    chunkSizeSection: $('chunk-size-section'),
    chunkSizeInput: $('chunk-size-input'),
    confirmCancelBtn: $('confirm-cancel-btn'),
    confirmUploadBtn: $('confirm-upload-btn'),
    // progress panel
    progressPanel: $('upload-progress-panel'),
    progressTitle: $('progress-title'),
    progressFileName: $('progress-file-name'),
    progressBarFill: $('progress-bar-fill'),
    progressText: $('progress-text'),
    uploadLog: $('upload-log'),
    progressCloseBtn: $('progress-close-btn'),
};


function showLogin() {
    ui.login.classList.remove('hidden');
    ui.app.classList.add('hidden');
    ui.tokenInput.value = '';
    hideLoginError();
    setTimeout(() => ui.tokenInput.focus(), 50);
}

function showApp() {
    ui.login.classList.add('hidden');
    ui.app.classList.remove('hidden');
}

function showLoginError(msg) {
    ui.loginError.textContent = msg;
    ui.loginError.classList.remove('hidden');
}

function hideLoginError() {
    ui.loginError.classList.add('hidden');
    ui.loginError.textContent = '';
}

$('login-form').addEventListener('submit', async e => {
    e.preventDefault();
    const token = ui.tokenInput.value.trim();
    if (!token) return;

    ui.loginBtn.disabled = true;
    hideLoginError();

    try {
        await Api.login(token);
        showApp();
        navigate('');
    } catch (err) {
        showLoginError(err.message);
    } finally {
        ui.loginBtn.disabled = false;
    }
});

ui.logoutBtn.addEventListener('click', async () => {
    await Api.logout().catch(() => {});
    showLogin();
});


function renderBreadcrumb(path) {
    ui.crumb.innerHTML = '';

    const homeLink = el('span', { class: 'crumb', onclick: () => navigate('') }, ['Home']);
    ui.crumb.appendChild(homeLink);

    if (!path) {
        homeLink.className = 'crumb active';
        return;
    }

    ui.crumb.appendChild(el('span', { class: 'crumb-sep' }, [' / ']));

    const segments = path.split('/');
    segments.forEach((seg, i) => {
        const isLast = i === segments.length - 1;
        const segPath = segments.slice(0, i + 1).join('/');
        const span = el('span', {
            class: isLast ? 'crumb active' : 'crumb',
            onclick: isLast ? undefined : () => navigate(segPath),
        }, [seg]);
        ui.crumb.appendChild(span);
        if (!isLast) {
            ui.crumb.appendChild(el('span', { class: 'crumb-sep' }, [' / ']));
        }
    });
}

// File browser rendering
function renderLoading() {
    ui.browser.innerHTML = '';
    ui.browser.appendChild(
        el('div', { class: 'state-box' }, [
            el('div', { class: 'spinner' }),
        ])
    );
}

function renderEmpty() {
    ui.browser.innerHTML = '';
    ui.browser.appendChild(
        el('div', { class: 'state-box' }, [
            el('div', { class: 'state-icon', html: Icons.file }),
            el('div', { class: 'state-title' }, ['No files here']),
            el('div', { class: 'state-sub' }, ['This directory is empty.']),
        ])
    );
}

function renderError(msg) {
    ui.browser.innerHTML = '';
    ui.browser.appendChild(
        el('div', { class: 'state-box' }, [
            el('div', { class: 'state-title' }, [msg]),
        ])
    );
}

function renderEntries(entries) {
    ui.browser.innerHTML = '';

    const dirs = entries.filter(e => e.isDirectory);
    const files = entries.filter(e => !e.isDirectory);

    if (dirs.length === 0 && files.length === 0) {
        renderEmpty();
        return;
    }

    // directories
    if (dirs.length > 0) {
        ui.browser.appendChild(el('div', { class: 'section-label' }, ['Folders']));
        const list = el('div', { class: 'dir-list' });
        dirs.forEach(dir => {
            const renameBtn = el('button', {
                class: 'dir-action-btn',
                title: 'Rename folder',
                html: Icons.edit,
                onclick: e => { e.stopPropagation(); openRenameFolderModal(dir); },
            });
            const item = el('div', { class: 'dir-item', onclick: () => navigate(dir.path) }, [
                el('span', { class: 'dir-icon', html: Icons.folder }),
                el('span', { class: 'dir-name' }, [dir.name]),
                renameBtn,
            ]);
            list.appendChild(item);
        });
        ui.browser.appendChild(list);
    }

    // files
    if (files.length > 0) {
        ui.browser.appendChild(el('div', { class: 'section-label' }, ['Files']));
        const grid = el('div', {
            class: 'file-grid'
        });
        files.forEach(file => grid.appendChild(buildFileCard(file)));
        ui.browser.appendChild(grid);
    }
}

function buildFileCard(entry) {
    const type = getFileType(entry.name);
    const card = el('div', { class: `file-card type-${type}`, onclick: () => openFile(entry) });

    // Thumbnail
    const thumb = el('div', { class: 'file-thumb' });
    if (entry.thumbnail) {
        const img = el('img', { src: `data:image/jpeg;base64,${entry.thumbnail}`, alt: '' });
        thumb.appendChild(img);
    } else {
        thumb.appendChild(el('div', { class: 'file-icon-bg', html: getFileIcon(entry.name) }));
    }
    card.appendChild(thumb);

    // Info area
    const info = el('div', { class: 'file-info' });
    info.appendChild(el('div', { class: 'file-name', title: entry.name }, [entry.name]));

    const meta = el('div', { class: 'file-meta' });
    const size = formatSize(entry.size);
    const date = formatDate(entry.uploadTimestamp);
    if (size) meta.appendChild(el('span', {}, [size]));
    if (date) meta.appendChild(el('span', {}, [date]));
    if (entry.parts > 1) meta.appendChild(el('span', {}, [`${entry.parts} parts`]));
    info.appendChild(meta);

    card.appendChild(info);
    return card;
}

// File opening
function openFile(entry) {
    const url = buildFileUrl(entry);
    const ext = entry.name.split('.').pop().toLowerCase();

    if (VIEWABLE_TYPES.has(ext)) {
        window.open(url, '_blank', 'noopener');
    } else {
        // download
        const a = document.createElement('a');
        a.href = url;
        a.download = entry.name;
        a.style.display = 'none';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
    }
}

//Navigation
async function navigate(path) {
    currentPath = path ?? '';
    renderBreadcrumb(currentPath);
    renderLoading();

    // Reflect the current path in the URL hash for browser history support
    history.pushState(null, '', currentPath ? `#${encodeHashPath(currentPath)}` : '#');

    try {
        const entries = await Api.listFiles(currentPath);
        renderEntries(entries);
    } catch (err) {
        if (err.status === 401) {
            showLogin();
        } else {
            renderError('Failed to load files: ' + err.message);
        }
    }
}

// back/forward support
window.addEventListener('popstate', () => {
    const path = decodeHashPath(window.location.hash);
    navigate(path);
});



const folderModal = {
    overlay: $('folder-modal-overlay'),
    title: $('folder-modal-title'),
    input: $('folder-name-input'),
    error: $('folder-modal-error'),
    cancelBtn: $('folder-cancel-btn'),
    confirmBtn: $('folder-confirm-btn'),
};

let folderModalMode = null; // "create" or "rename"
let folderModalTarget = null;

function showFolderModal(mode, dir = null) {
    folderModalMode = mode;
    folderModalTarget = dir;
    folderModal.error.classList.add('hidden');
    folderModal.confirmBtn.disabled = false;

    if (mode === 'create') {
        folderModal.title.textContent = 'New folder';
        folderModal.input.value = '';
        folderModal.confirmBtn.textContent = 'Create';
    } else {
        folderModal.title.textContent = 'Rename folder';
        folderModal.input.value = dir.name;
        folderModal.confirmBtn.textContent = 'Rename';
    }

    folderModal.overlay.classList.remove('hidden');
    setTimeout(() => {
        folderModal.input.focus();
        folderModal.input.select(); },
        50);
}

function hideFolderModal() {
    folderModal.overlay.classList.add('hidden');
    folderModalMode = null;
    folderModalTarget = null;
}

function showFolderError(msg) {
    folderModal.error.textContent = msg;
    folderModal.error.classList.remove('hidden');
}

function openRenameFolderModal(dir) {
    showFolderModal('rename', dir);
}

ui.newFolderBtn.addEventListener('click', () => showFolderModal('create'));

folderModal.cancelBtn.addEventListener('click', hideFolderModal);

folderModal.overlay.addEventListener('click', e => {
    if (e.target === folderModal.overlay) {
        hideFolderModal();
    }
});

folderModal.input.addEventListener('keydown', e => {
    if (e.key === 'Enter') {
        folderModal.confirmBtn.click();
    }
    if (e.key === 'Escape') {
        hideFolderModal();
    }
});

folderModal.confirmBtn.addEventListener('click', async () => {
    const name = folderModal.input.value.trim();
    if (!name) {
        showFolderError('Folder name is required');
        return;
    }

    folderModal.confirmBtn.disabled = true;
    folderModal.error.classList.add('hidden');

    try {
        if (folderModalMode === 'create') {
            await Api.createFolder(currentPath, name);
        } else {
            await Api.renameFolder(folderModalTarget.path, name);
        }
        hideFolderModal();
        navigate(currentPath);
    } catch (err) {
        showFolderError(err.message);
        folderModal.confirmBtn.disabled = false;
    }
});


// Uploading

let uploadMethods = { hasWebhook: false, hasBot: false };
let uploadPollTimer = null;
let lastLogCount = 0;

async function loadUploadMethods() {
    try {
        uploadMethods = await Api.uploadMethods();
    } catch (_) {
        uploadMethods = { hasWebhook: false, hasBot: false };
    }
}

function showUploadModal() {
    ui.uploadOverlay.classList.remove('hidden');
    ui.confirmPanel.classList.remove('hidden');
    ui.progressPanel.classList.add('hidden');
}

function hideUploadModal() {
    ui.uploadOverlay.classList.add('hidden');
    stopPoll();
}

function showUploadMethodOptions(methods) {
    const { hasWebhook, hasBot, defaultChunkSizeMb } = methods;

    if (hasWebhook) {
        ui.labelWebhook.classList.remove('hidden');
    } else {
        ui.labelWebhook.classList.add('hidden');
    }
    if (hasBot) {
        ui.labelBot.classList.remove('hidden');
    } else {
        ui.labelBot.classList.add('hidden');
    }

    if (!hasWebhook && !hasBot) {
        ui.noMethodMsg.classList.remove('hidden');
        ui.confirmUploadBtn.disabled = true;
    } else {
        ui.noMethodMsg.classList.add('hidden');
        ui.confirmUploadBtn.disabled = false;
        // Auto select first available method
        if (hasWebhook) {
            ui.radioWebhook.checked = true;
        } else if (hasBot) {
            ui.radioBot.checked = true;
        }
    }

    // Set default chunk size from server config
    if (defaultChunkSizeMb && defaultChunkSizeMb > 0) {
        ui.chunkSizeInput.value = defaultChunkSizeMb;
    }

    // Show/hide chunk size based on selected method
    updateChunkSizeVisibility();
}

function getSelectedMethod() {
    if (ui.radioWebhook.checked) return 'WEBHOOK';
    if (ui.radioBot.checked) return 'BOT';
    return null;
}

function updateChunkSizeVisibility() {
    if (ui.radioBot.checked) {
        ui.chunkSizeSection.classList.remove('hidden');
    } else {
        ui.chunkSizeSection.classList.add('hidden');
    }
}

ui.radioWebhook.addEventListener('change', updateChunkSizeVisibility);
ui.radioBot.addEventListener('change', updateChunkSizeVisibility);

function showServerUploadPhase(fileName) {
    ui.confirmPanel.classList.add('hidden');
    ui.progressPanel.classList.remove('hidden');
    ui.progressTitle.textContent = 'Sending to server...';
    ui.progressFileName.textContent = fileName;
    ui.progressBarFill.style.width = '0%';
    ui.progressBarFill.classList.remove('progress-done', 'progress-error');
    ui.progressText.textContent = 'Preparing...';
    ui.uploadLog.innerHTML = '';
    ui.progressCloseBtn.disabled = true;
    lastLogCount = 0;
}

function switchToDiscordUploadPhase(fileName) {
    ui.confirmPanel.classList.add('hidden');
    ui.progressPanel.classList.remove('hidden');
    ui.progressTitle.textContent = 'Uploading to Discord...';
    ui.progressFileName.textContent = fileName;
    ui.progressBarFill.style.width = '0%';
    ui.progressBarFill.classList.remove('progress-done', 'progress-error');
    ui.progressText.textContent = 'Starting...';
    ui.uploadLog.innerHTML = '';
    ui.progressCloseBtn.disabled = true;
    lastLogCount = 0;
}

function appendLogs(logs) {
    if (!logs || logs.length <= lastLogCount) return;
    const newLogs = logs.slice(lastLogCount);
    lastLogCount = logs.length;
    newLogs.forEach(msg => {
        const line = document.createElement('div');
        line.className = msg.startsWith('ERROR:') ? 'log-line log-error' : 'log-line';
        line.textContent = msg;
        ui.uploadLog.appendChild(line);
    });
    // Auto scroll to bottom
    ui.uploadLog.scrollTop = ui.uploadLog.scrollHeight;
}

function updateProgressBar(current, total) {
    if (total > 0) {
        const pct = Math.round((current / total) * 100);
        ui.progressBarFill.style.width = pct + '%';
        ui.progressText.textContent = `Part ${current} / ${total} (${pct}%)`;
    }
}

function startPoll() {
    stopPoll();
    uploadPollTimer = setInterval(pollUploadStatus, 1000);
}

function stopPoll() {
    if (uploadPollTimer) {
        clearInterval(uploadPollTimer);
        uploadPollTimer = null;
    }
}

async function pollUploadStatus() {
    let status;
    try {
        status = await Api.uploadStatus();
    } catch (_) {
        return;
    }

    appendLogs(status.logs);

    if (status.state === 'uploading') {
        if (status.totalParts > 0) {
            updateProgressBar(status.currentPart, status.totalParts);
        }

    } else if (status.state === 'done') {
        stopPoll();
        updateProgressBar(status.totalParts || 1, status.totalParts || 1);
        ui.progressText.textContent = 'Completed successfully!';
        ui.progressTitle.textContent = 'Done';
        ui.progressBarFill.classList.add('progress-done');
        ui.progressCloseBtn.disabled = false;

    } else if (status.state === 'error') {
        stopPoll();
        ui.progressTitle.textContent = 'Error';
        ui.progressText.textContent = status.error || 'Unknown error';
        ui.progressBarFill.classList.add('progress-error');
        ui.progressCloseBtn.disabled = false;
    }
}

ui.uploadBtn.addEventListener('click', async () => {
    // Check if upload already running
    try {
        const status = await Api.uploadStatus();
        if (status.state === 'uploading') {  // Already uploading to Discord
            switchToDiscordUploadPhase(status.fileName || '');
            ui.uploadOverlay.classList.remove('hidden');
            startPoll();
            return;
        }
        if (status.state === 'done' || status.state === 'error') {
            await Api.uploadReset().catch(() => {});
        }
    } catch (_) {}

    await loadUploadMethods();
    ui.fileInput.value = '';
    ui.fileInput.click();
});

ui.fileInput.addEventListener('change', () => {
    const file = ui.fileInput.files[0];
    if (!file) return;

    ui.confirmFileName.textContent = file.name;
    ui.confirmDestPath.textContent = currentPath ? `/${currentPath}/` : '/';
    showUploadMethodOptions(uploadMethods);
    showUploadModal();
});

ui.confirmCancelBtn.addEventListener('click', () => {
    hideUploadModal();
    ui.fileInput.value = '';
});

ui.progressCloseBtn.addEventListener('click', async () => {
    stopPoll();
    await Api.uploadReset().catch(() => {});
    hideUploadModal();
    navigate(currentPath); // refresh file list
});

ui.confirmUploadBtn.addEventListener('click', async () => {
    const file = ui.fileInput.files[0];
    if (!file) return;

    const method = getSelectedMethod();
    if (!method) {
        alert('Select upload method.');
        return;
    }

    let chunkSizeMb = null;
    if (method === 'BOT') {
        const raw = parseInt(ui.chunkSizeInput.value, 10);
        if (!raw || raw < 1 || raw > 500) {
            alert('Chunk size must be a number between 1 and 500 MB.');
            return;
        }
        chunkSizeMb = raw;
    }

    showServerUploadPhase(file.name);

    // upload speed
    let speedEma = 0;
    let prevLoaded = 0;
    let prevTime = performance.now();

    const onProgress = (loaded, total) => {
        const now = performance.now();
        const dt = (now - prevTime) / 1000;
        if (dt > 0.05) {
            const instant = (loaded - prevLoaded) / dt;
            if (instant >= 0) {
                speedEma = speedEma < 1 ? instant : 0.3 * instant + 0.7 * speedEma;
            }
            prevLoaded = loaded;
            prevTime = now;
        }
        const pct = total > 0 ? Math.round((loaded / total) * 100) : 0;
        ui.progressBarFill.style.width = pct + '%';
        const speedStr = speedEma > 512 ? ` · ${formatSize(Math.round(speedEma))}/s` : '';
        ui.progressText.textContent = `${formatSize(loaded)} / ${formatSize(total)} (${pct}%)${speedStr}`;
    };

    try {
        await Api.startUpload(file, currentPath, method, chunkSizeMb, onProgress);
        switchToDiscordUploadPhase(file.name);
        startPoll();
    } catch (err) {
        ui.progressTitle.textContent = 'Error';
        ui.progressText.textContent = err.message;
        ui.progressBarFill.classList.add('progress-error');
        ui.progressCloseBtn.disabled = false;
    }
});

// Close modal on overlay click (only when not uploading)
ui.uploadOverlay.addEventListener('click', async e => {
    if (e.target !== ui.uploadOverlay) return;
    const status = await Api.uploadStatus().catch(() => ({ state: 'idle' }));
    if (status.state !== 'uploading') {
        if (status.state === 'done' || status.state === 'error') {
            await Api.uploadReset().catch(() => {});
            navigate(currentPath);
        }
        hideUploadModal();
    }
});


async function boot() {
    await loadIcons();

    const initialPath = decodeHashPath(window.location.hash);

    // If upload was in progress before page refresh, reconnect to it
    try {
        const status = await Api.uploadStatus();
        if (status.state === 'uploading') {
            switchToDiscordUploadPhase(status.fileName || '');
            ui.uploadOverlay.classList.remove('hidden');
            startPoll();
        }
    } catch (_) {}

    try {
        const entries = await Api.listFiles(initialPath);
        currentPath = initialPath;
        showApp();
        renderBreadcrumb(currentPath);
        renderEntries(entries);
    } catch (err) {
        if (err.status === 401) {
            showLogin();
        } else {
            showApp();
            renderError('Failed to connect to server: ' + err.message);
        }
    }
}

boot();
