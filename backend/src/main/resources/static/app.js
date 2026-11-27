'use strict';

/**
 * Web-клиент службы доставки (КР №3).
 *
 * Access JWT хранится только в памяти вкладки, refresh-токен браузер получает
 * в HttpOnly/SameSite cookie. Кнопки скрываются по ролям только для удобства —
 * реальная проверка прав всегда выполняется на сервере.
 */
const state = {
    accessToken: null,
    user: null,
    clients: [],
    users: [],
    orders: { page: 0, size: 20, sort: 'created,desc', query: '', content: [], totalPages: 0, totalElements: 0, first: true, last: true },
    usersPage: { page: 0 },
    auditPage: { page: 0 },
    selectedOrderIds: new Set(),
    activeOrderForNotes: null,
    refreshPromise: null
};

const STATUSES = ['CREATED', 'CONFIRMED', 'ASSIGNED_TO_COURIER', 'PICKED_UP', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED'];
const NEXT_STATUSES = {
    CREATED: ['CONFIRMED', 'CANCELLED'],
    CONFIRMED: ['ASSIGNED_TO_COURIER', 'CANCELLED'],
    ASSIGNED_TO_COURIER: ['PICKED_UP', 'CANCELLED'],
    PICKED_UP: ['IN_TRANSIT'],
    IN_TRANSIT: ['DELIVERED'],
    DELIVERED: [],
    CANCELLED: []
};

const $ = (id) => document.getElementById(id);
const escapeHtml = (value) => String(value ?? '').replace(/[&<>'"]/g, ch => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'}[ch]));
const roles = () => new Set((state.user?.roles || []).map(String));
const isAdmin = () => roles().has('ADMIN');
const canEdit = () => roles().has('DISPATCHER') || isAdmin();

function roleLabel(value) {
    return ({ADMIN: 'Администратор', DISPATCHER: 'Диспетчер', VIEWER: 'Наблюдатель'})[value] || value;
}

function statusLabel(value) {
    return ({
        CREATED: 'Создан', CONFIRMED: 'Подтверждён', ASSIGNED_TO_COURIER: 'Назначен курьеру',
        PICKED_UP: 'Забран курьером', IN_TRANSIT: 'В пути', DELIVERED: 'Доставлен', CANCELLED: 'Отменён'
    })[value] || value;
}

function priorityLabel(value) {
    return ({NOT_APPLICABLE: 'Не применяется', LOW: 'Низкий', NORMAL: 'Обычный', HIGH: 'Высокий', URGENT: 'Срочный'})[value] || value;
}

function fieldLabel(value) {
    return ({
        orderNumber: 'номер заказа', clientId: 'клиент', pickupAddress: 'адрес отправления',
        deliveryAddress: 'адрес доставки', description: 'описание', weight: 'вес', price: 'стоимость',
        fragile: 'хрупкий', status: 'статус', tags: 'теги', version: 'версия',
        fullName: 'ФИО', phone: 'телефон', email: 'email', address: 'адрес',
        displayName: 'имя', temporaryPassword: 'временный пароль', newPassword: 'новый пароль', roles: 'роли'
    })[value] || value;
}

const money = (value) => value == null ? '—' : Number(value).toLocaleString('ru-RU', {minimumFractionDigits: 2, maximumFractionDigits: 2});
const formatDate = (value) => value ? new Intl.DateTimeFormat('ru-RU', {dateStyle: 'short', timeStyle: 'short'}).format(new Date(value)) : '—';
const empty = (value) => { const normalized = String(value ?? '').trim(); return normalized || null; };

function toast(message, error = false) {
    const node = $('toast');
    node.textContent = message;
    node.className = `toast show${error ? ' error' : ''}`;
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => node.className = 'toast', 4000);
}

function showFormError(form, message) {
    const box = form.querySelector('[data-form-error]');
    if (!box) return toast(message, true);
    box.textContent = message;
    box.hidden = false;
}

async function parseError(response) {
    try {
        const body = await response.json();
        const fields = body.validationErrors
            ? Object.entries(body.validationErrors).map(([key, value]) => `${fieldLabel(key)}: ${value}`).join('; ')
            : '';
        return `${body.message || response.statusText}${fields ? ` — ${fields}` : ''}${body.correlationId ? ` [${body.correlationId}]` : ''}`;
    } catch (_) {
        return `${response.status} ${response.statusText}`;
    }
}

/** Общий HTTP-вызов: при 401 один раз пробует обновить access JWT по refresh-cookie. */
async function api(path, options = {}, retry = true) {
    const headers = new Headers(options.headers || {});
    if (state.accessToken) headers.set('Authorization', `Bearer ${state.accessToken}`);
    if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
    const response = await fetch(`/api${path}`, {...options, headers, credentials: 'include'});
    if (response.status === 401 && retry) {
        if (await refreshAccessToken()) return api(path, options, false);
        showLogin();
    }
    if (!response.ok) throw new Error(await parseError(response));
    if (response.status === 204) return null;
    const type = response.headers.get('content-type') || '';
    return type.includes('application/json') ? response.json() : response;
}

async function refreshAccessToken() {
    if (!state.refreshPromise) {
        state.refreshPromise = fetch('/api/auth/refresh', {
            method: 'POST', credentials: 'include', headers: {'Content-Type': 'application/json'}, body: '{}'
        }).then(async response => {
            if (!response.ok) return false;
            const result = await response.json();
            state.accessToken = result.accessToken;
            state.user = result.user;
            return true;
        }).catch(() => false).finally(() => state.refreshPromise = null);
    }
    return state.refreshPromise;
}

async function boot() {
    bindEvents();
    if (await refreshAccessToken()) await enterApplication();
    else showLogin();
}

function showLogin() {
    $('app-view').hidden = true;
    $('login-view').hidden = false;
    state.accessToken = null;
    state.user = null;
}

async function enterApplication() {
    $('login-view').hidden = true;
    $('app-view').hidden = false;
    applyRoleVisibility();
    $('current-user').innerHTML = `<strong>${escapeHtml(state.user.displayName)}</strong><br>${escapeHtml(state.user.email)}<br>${escapeHtml(state.user.roles.map(roleLabel).join(', '))}`;

    if (state.user.passwordChangeRequired) {
        toast('Для продолжения необходимо сменить временный пароль', true);
        if (!await openPasswordDialog(true)) return;
    }
    try {
        await Promise.all([loadClients(), loadDashboard()]);
    } catch (error) {
        toast(`Вход выполнен, но часть данных не загрузилась: ${error.message}`, true);
    }
}

function applyRoleVisibility() {
    document.querySelectorAll('.admin-only').forEach(node => node.hidden = !isAdmin());
    document.querySelectorAll('.editor-only').forEach(node => node.hidden = !canEdit());
}

function switchTab(name) {
    document.querySelectorAll('.tab').forEach(tab => tab.classList.toggle('active', tab.id === `tab-${name}`));
    document.querySelectorAll('.nav-item').forEach(button => button.classList.toggle('active', button.dataset.tab === name));
    const titles = {
        dashboard: ['Аналитика', 'Сводные показатели по заказам'],
        orders: ['Заказы', 'Поиск, фильтры, карточки и массовые операции'],
        clients: ['Клиенты', 'Справочник клиентов службы доставки'],
        users: ['Пользователи', 'Роли, блокировка и временные пароли'],
        audit: ['Журнал аудита', 'История входов и изменений данных']
    };
    [$('page-title').textContent, $('page-subtitle').textContent] = titles[name];
    if (name === 'orders') loadOrders();
    if (name === 'clients') loadClients();
    if (name === 'users' && isAdmin()) loadUsers(0);
    if (name === 'audit' && isAdmin()) loadAudit(0);
    if (name === 'dashboard') loadDashboard();
}

// ------------------------------------------------------------------ Аналитика

async function loadDashboard() {
    try {
        const data = await api('/analytics/summary');
        const metrics = [
            ['Всего заказов', data.total], ['Активные', data.active], ['В архиве', data.archived],
            ['Хрупкие (активные)', data.fragile], ['Средняя цена, ₽', money(data.averagePrice)], ['Средний вес, кг', money(data.averageWeight)]
        ];
        $('metric-grid').innerHTML = metrics.map(([name, value]) => `<article class="metric card"><span class="muted">${name}</span><strong>${value}</strong></article>`).join('');
        renderBars('chart-clients', data.byClient, v => v);
        renderBars('chart-status', data.byStatus, statusLabel);
        renderBars('chart-priority', data.byPriority, priorityLabel);
    } catch (error) { toast(error.message, true); }
}

function renderBars(id, values, label) {
    const max = Math.max(1, ...values.map(item => item.count));
    $(id).innerHTML = values.length
        ? values.map(item => `<div class="bar-row"><span>${escapeHtml(label(item.name))}</span><progress value="${item.count}" max="${max}"></progress><strong>${item.count}</strong></div>`).join('')
        : '<p class="muted">Нет данных</p>';
}

// ------------------------------------------------------------------ Клиенты

async function loadClients() {
    try {
        state.clients = await api('/clients?activeOnly=false');
        renderClients();
    } catch (error) { toast(error.message, true); }
}

function renderClients() {
    $('clients-body').innerHTML = state.clients.map(client => `<tr class="${client.active ? '' : 'archived'}">
        <td><strong>${escapeHtml(client.fullName)}</strong></td><td>${escapeHtml(client.phone)}</td>
        <td>${escapeHtml(client.email || '—')}</td><td class="address">${escapeHtml(client.address)}</td>
        <td><span class="badge ${client.active ? 'success' : ''}">${client.active ? 'Да' : 'Нет'}</span></td>
        <td class="admin-only"><div class="actions"><button class="secondary" data-client-edit="${client.id}">Изменить</button><button class="secondary" data-client-toggle="${client.id}">${client.active ? 'Отключить' : 'Включить'}</button></div></td>
    </tr>`).join('') || '<tr><td colspan="6" class="muted">Клиентов нет</td></tr>';
    applyRoleVisibility();
}

function openClient(id = null) {
    const client = id ? state.clients.find(c => c.id === id) : null;
    const form = $('entity-form');
    form.innerHTML = `<div class="dialog-head"><h3>${client ? 'Изменение клиента' : 'Новый клиент'}</h3><button type="button" data-close-dialog="entity-dialog" class="icon-button">×</button></div>
        <label>ФИО<input name="fullName" required maxlength="150" value="${escapeHtml(client?.fullName || '')}"></label>
        <label>Телефон<input name="phone" required maxlength="16" pattern="\\+?[0-9]{10,15}" title="10–15 цифр, можно с + в начале" placeholder="+79990000000" value="${escapeHtml(client?.phone || '')}"></label>
        <label>Email<input name="email" type="email" maxlength="150" value="${escapeHtml(client?.email || '')}"></label>
        <label>Адрес<input name="address" required maxlength="500" value="${escapeHtml(client?.address || '')}"></label>
        <label class="inline"><input name="active" type="checkbox" ${client?.active !== false ? 'checked' : ''}> Активен</label>
        <div class="form-message error" data-form-error hidden></div>
        <button class="primary">Сохранить</button>`;
    form.onsubmit = async event => {
        event.preventDefault();
        if (!form.reportValidity()) return;
        const data = new FormData(form);
        const payload = {
            fullName: data.get('fullName'), phone: data.get('phone'), email: empty(data.get('email')),
            address: data.get('address'), active: data.get('active') === 'on', version: client?.version ?? null
        };
        try {
            await api(id ? `/clients/${id}` : '/clients', {method: id ? 'PUT' : 'POST', body: JSON.stringify(payload)});
            $('entity-dialog').close();
            toast('Клиент сохранён');
            await loadClients();
        } catch (error) { showFormError(form, error.message); }
    };
    $('entity-dialog').showModal();
}

async function toggleClient(id) {
    const client = state.clients.find(c => c.id === id);
    try {
        await api(`/clients/${id}/active?active=${!client.active}&version=${client.version}`, {method: 'PATCH'});
        await loadClients();
    } catch (error) { toast(error.message, true); }
}

// ------------------------------------------------------------------ Заказы

async function loadOrders(page = state.orders.page) {
    try {
        const o = state.orders;
        const result = await api(`/orders?q=${encodeURIComponent(o.query)}&page=${page}&size=${o.size}&sort=${encodeURIComponent(o.sort)}`);
        state.orders = {...o, ...result};
        state.selectedOrderIds.clear();
        renderOrders();
    } catch (error) { toast(error.message, true); }
}

function renderOrders() {
    const o = state.orders;
    $('orders-body').innerHTML = o.content.map(order => `<tr class="${order.archived ? 'archived' : ''}">
        <td class="editor-only"><input type="checkbox" data-order-select="${order.id}"></td>
        <td><span class="order-number">${escapeHtml(order.orderNumber)}</span>${order.archived ? '<br><small class="muted">в архиве</small>' : ''}</td>
        <td><strong>${escapeHtml(order.clientName)}</strong><br><small class="muted">${escapeHtml(order.clientPhone)}</small></td>
        <td class="address">${escapeHtml(order.pickupAddress)} → ${escapeHtml(order.deliveryAddress)}${order.description ? `<br><small class="muted">${escapeHtml(order.description)}</small>` : ''}</td>
        <td>${money(order.weight)}</td>
        <td>${money(order.price)}</td>
        <td>${escapeHtml(statusLabel(order.status))}</td>
        <td><span class="badge prio-${order.priority}">${escapeHtml(priorityLabel(order.priority))}</span>${order.fragile ? ' <span class="badge fragile" title="Хрупкий груз">хрупкий</span>' : ''}</td>
        <td>${order.tags.map(tag => `<span class="badge">${escapeHtml(tag)}</span>`).join(' ') || '—'}</td>
        <td><small>${formatDate(order.createdAt)}</small></td>
        <td><div class="actions">
            <button class="secondary" data-order-view="${order.id}">Карточка</button>
            <button class="secondary" data-order-notes="${order.id}">Комментарии</button>
            ${canEdit() ? `<button class="secondary" data-order-edit="${order.id}">Изменить</button>
                <button class="danger-outline" data-order-archive="${order.id}">${order.archived ? 'Восстановить' : 'В архив'}</button>` : ''}
            ${isAdmin() ? `<button class="danger-outline" data-order-delete="${order.id}">Удалить</button>` : ''}
        </div></td>
    </tr>`).join('') || '<tr><td colspan="11" class="muted">Заказы не найдены</td></tr>';
    $('orders-page').textContent = `Страница ${o.page + 1} из ${Math.max(1, o.totalPages)} · записей ${o.totalElements}`;
    $('orders-prev').disabled = o.first;
    $('orders-next').disabled = o.last;
    $('select-all-orders').checked = false;
    const [field, dir] = o.sort.split(',');
    document.querySelectorAll('th.sortable').forEach(th => th.dataset.dir = th.dataset.sort === field ? dir : '');
    applyRoleVisibility();
}

function statusOptions(current, isNew) {
    // Для существующего заказа предлагаем только допустимые переходы (сервер всё равно проверит).
    const allowed = isNew ? STATUSES : [current, ...NEXT_STATUSES[current]];
    return allowed.map(v => `<option value="${v}" ${v === current ? 'selected' : ''}>${statusLabel(v)}</option>`).join('');
}

function orderForm(order, readOnly) {
    const dis = readOnly ? 'disabled' : '';
    const clientOptions = state.clients.filter(c => c.active || c.id === order?.clientId)
        .map(c => `<option value="${c.id}" ${c.id === order?.clientId ? 'selected' : ''}>${escapeHtml(c.fullName)} (${escapeHtml(c.phone)})</option>`).join('');
    const title = order ? (readOnly ? `Заказ ${escapeHtml(order.orderNumber)}` : `Изменение заказа ${escapeHtml(order.orderNumber)}`) : 'Новый заказ';
    return `<div class="dialog-head"><h3>${title}</h3><button type="button" data-close-dialog="entity-dialog" class="icon-button">×</button></div>
    <div class="form-grid">
      <label>Номер заказа<input name="orderNumber" maxlength="40" pattern="[A-Za-z0-9\\-]+" placeholder="сгенерируется автоматически" value="${escapeHtml(order?.orderNumber || '')}" ${dis}></label>
      <label>Клиент<select name="clientId" required ${dis}><option value="">— выберите —</option>${clientOptions}</select></label>
      <label class="span-2">Адрес отправления<input name="pickupAddress" required maxlength="500" value="${escapeHtml(order?.pickupAddress || '')}" ${dis}></label>
      <label class="span-2">Адрес доставки<input name="deliveryAddress" required maxlength="500" value="${escapeHtml(order?.deliveryAddress || '')}" ${dis}></label>
      <label class="span-2">Описание груза<input name="description" maxlength="2000" value="${escapeHtml(order?.description || '')}" ${dis}></label>
      <label>Вес, кг<input name="weight" type="number" required min="0.01" max="10000" step="0.01" value="${order?.weight ?? ''}" ${dis}></label>
      <label>Стоимость, ₽<input name="price" type="number" required min="0.01" step="0.01" value="${order?.price ?? ''}" ${dis}></label>
      <label>Статус<select name="status" ${dis}>${statusOptions(order?.status || 'CREATED', !order)}</select></label>
      <label class="inline"><input name="fragile" type="checkbox" ${order?.fragile ? 'checked' : ''} ${dis}> Хрупкий груз</label>
      <label class="span-2">Теги через запятую<input name="tags" maxlength="1000" value="${escapeHtml((order?.tags || []).join(', '))}" ${dis}></label>
    </div>
    ${order ? `<p class="muted">Приоритет: <strong>${priorityLabel(order.priority)}</strong> (рассчитывается сервером) · создан ${formatDate(order.createdAt)} · изменён ${formatDate(order.updatedAt)} · версия ${order.version}</p>` : '<p class="muted">Приоритет рассчитается сервером автоматически.</p>'}
    ${readOnly ? '' : '<div class="form-message error" data-form-error hidden></div><button class="primary" type="submit">Сохранить</button>'}`;
}

async function openOrder(id = null, readOnly = false) {
    try {
        if (!state.clients.length) await loadClients();
        const order = id ? await api(`/orders/${id}`) : null;
        const form = $('entity-form');
        form.innerHTML = orderForm(order, readOnly);
        form.onsubmit = readOnly ? null : async event => {
            event.preventDefault();
            if (!form.reportValidity()) return;
            const data = new FormData(form);
            const payload = {
                orderNumber: empty(data.get('orderNumber')), clientId: data.get('clientId'),
                pickupAddress: data.get('pickupAddress'), deliveryAddress: data.get('deliveryAddress'),
                description: empty(data.get('description')), weight: Number(data.get('weight')), price: Number(data.get('price')),
                fragile: data.get('fragile') === 'on', status: data.get('status'),
                tags: String(data.get('tags') || '').split(',').map(v => v.trim()).filter(Boolean),
                version: order?.version ?? null
            };
            try {
                await api(id ? `/orders/${id}` : '/orders', {method: id ? 'PUT' : 'POST', body: JSON.stringify(payload)});
                $('entity-dialog').close();
                toast('Заказ сохранён');
                await Promise.all([loadOrders(), loadDashboard()]);
            } catch (error) { showFormError(form, error.message); }
        };
        $('entity-dialog').showModal();
    } catch (error) { toast(error.message, true); }
}

async function archiveOrder(id) {
    const order = state.orders.content.find(item => item.id === id);
    if (!confirm(`${order.archived ? 'Восстановить' : 'Переместить в архив'} заказ ${order.orderNumber}?`)) return;
    try {
        await api(order.archived ? `/orders/${id}/restore` : `/orders/${id}`, {method: order.archived ? 'POST' : 'DELETE'});
        toast('Операция выполнена');
        await Promise.all([loadOrders(), loadDashboard()]);
    } catch (error) { toast(error.message, true); }
}

async function deleteOrder(id) {
    const order = state.orders.content.find(item => item.id === id);
    if (!confirm(`Удалить заказ ${order.orderNumber} БЕЗВОЗВРАТНО? Вместе с ним удалятся комментарии и теги.`)) return;
    try {
        await api(`/orders/${id}/permanent`, {method: 'DELETE', headers: {'X-Confirm-Delete': 'PERMANENTLY_DELETE'}});
        toast('Заказ удалён');
        await Promise.all([loadOrders(), loadDashboard()]);
    } catch (error) { toast(error.message, true); }
}

function openBulkDialog() {
    if (!state.selectedOrderIds.size) return toast('Выберите хотя бы один заказ', true);
    const form = $('entity-form');
    form.innerHTML = `<div class="dialog-head"><h3>Массовая операция (${state.selectedOrderIds.size} шт.)</h3><button type="button" data-close-dialog="entity-dialog" class="icon-button">×</button></div>
        <label>Действие<select name="action">
            <option value="ARCHIVE">Переместить в архив</option><option value="RESTORE">Восстановить из архива</option>
            <option value="SET_STATUS">Изменить статус</option><option value="ADD_TAG">Добавить тег</option><option value="REMOVE_TAG">Удалить тег</option>
        </select></label>
        <label data-for="SET_STATUS" hidden>Новый статус<select name="status">${STATUSES.map(s => `<option value="${s}">${statusLabel(s)}</option>`).join('')}</select></label>
        <label data-for="TAG" hidden>Тег<input name="tag" maxlength="50"></label>
        <pre class="result-box" data-bulk-result hidden></pre>
        <button class="primary">Выполнить</button>`;
    const sync = () => {
        const action = form.elements.action.value;
        form.querySelector('[data-for="SET_STATUS"]').hidden = action !== 'SET_STATUS';
        form.querySelector('[data-for="TAG"]').hidden = !action.endsWith('_TAG');
    };
    form.elements.action.addEventListener('change', sync);
    form.onsubmit = async event => {
        event.preventDefault();
        const action = form.elements.action.value;
        const payload = {
            ids: [...state.selectedOrderIds], action,
            status: action === 'SET_STATUS' ? form.elements.status.value : null,
            tag: action.endsWith('_TAG') ? empty(form.elements.tag.value) : null
        };
        try {
            const result = await api('/orders/bulk', {method: 'POST', body: JSON.stringify(payload)});
            const box = form.querySelector('[data-bulk-result]');
            box.hidden = false;
            box.textContent = `Запрошено: ${result.requested}\nУспешно: ${result.succeeded}\nОшибок: ${result.failures.length}` +
                (result.failures.length ? '\n\n' + result.failures.map(f => '• ' + f.message).join('\n') : '');
            await Promise.all([loadOrders(), loadDashboard()]);
        } catch (error) { toast(error.message, true); }
    };
    $('entity-dialog').showModal();
}

async function exportCsv() {
    try {
        const response = await api(`/orders/export?q=${encodeURIComponent(state.orders.query)}`);
        const url = URL.createObjectURL(await response.blob());
        const link = document.createElement('a');
        link.href = url;
        link.download = `orders-${new Date().toISOString().slice(0, 10)}.csv`;
        link.click();
        URL.revokeObjectURL(url);
    } catch (error) { toast(error.message, true); }
}

async function runCsv(apply) {
    const file = $('csv-file').files[0];
    if (!file) return toast('Выберите CSV-файл', true);
    const form = new FormData();
    form.append('file', file);
    try {
        const r = await api(`/orders/import/${apply ? 'apply' : 'preview'}?mode=${$('csv-mode').value}`, {method: 'POST', body: form});
        const issues = r.issues.map(i => `  строка ${i.row}, ${fieldLabel(i.field)}: ${i.message}`).join('\n');
        $('csv-result').textContent = apply
            ? `Импорт выполнен.\nВсего строк: ${r.totalRows}\nСоздано: ${r.created}\nОбновлено: ${r.updated}\nПропущено: ${r.skipped}${issues ? '\n\nПроблемы:\n' + issues : ''}`
            : `Проверка файла.\nВсего строк: ${r.totalRows}\nКорректных: ${r.validRows}\nС ошибками: ${r.invalidRows}${issues ? '\n\nОшибки:\n' + issues : ''}`;
        $('csv-apply').disabled = apply || r.validRows === 0;
        if (apply) {
            toast('Импорт завершён');
            await Promise.all([loadOrders(), loadDashboard()]);
        }
    } catch (error) {
        toast(error.message, true);
        $('csv-result').textContent = error.message;
    }
}

// ------------------------------------------------------------------ Комментарии

async function openNotes(orderId) {
    state.activeOrderForNotes = orderId;
    const order = state.orders.content.find(o => o.id === orderId);
    $('notes-title').textContent = `Комментарии к заказу ${order?.orderNumber || ''}`;
    await renderNotes();
    $('notes-dialog').showModal();
}

async function renderNotes() {
    try {
        const list = await api(`/orders/${state.activeOrderForNotes}/notes`);
        state.notes = list;
        const mine = note => note.authorId === state.user.id || isAdmin();
        $('notes-list').innerHTML = list.map(note => `<article class="note ${note.pinned ? 'pinned' : ''}">
            <div class="note-meta"><span>${escapeHtml(note.authorName)} · ${formatDate(note.createdAt)}${note.updatedAt !== note.createdAt ? ' (изменён)' : ''}</span><span>${note.pinned ? '📌 закреплён' : ''}</span></div>
            <p>${escapeHtml(note.body).replace(/\n/g, '<br>')}</p>
            ${canEdit() && mine(note) ? `<div class="actions"><button class="secondary" data-note-edit="${note.id}">Изменить</button><button class="secondary" data-note-pin="${note.id}">${note.pinned ? 'Открепить' : 'Закрепить'}</button><button class="danger-outline" data-note-delete="${note.id}">Удалить</button></div>` : ''}
        </article>`).join('') || '<p class="muted">Комментариев пока нет</p>';
    } catch (error) { toast(error.message, true); }
}

async function updateNote(note, body, pinned) {
    await api(`/orders/${state.activeOrderForNotes}/notes/${note.id}`, {method: 'PUT', body: JSON.stringify({body, pinned, version: note.version})});
    await renderNotes();
}

async function handleNotesClick(event) {
    const find = attr => { const b = event.target.closest(`[${attr}]`); return b && state.notes.find(n => n.id === b.getAttribute(attr)); };
    try {
        const edit = find('data-note-edit'), pin = find('data-note-pin'), del = find('data-note-delete');
        if (edit) {
            const body = prompt('Текст комментария', edit.body);
            if (body && body.trim()) await updateNote(edit, body, edit.pinned);
        }
        if (pin) await updateNote(pin, pin.body, !pin.pinned);
        if (del && confirm('Удалить комментарий?')) {
            await api(`/orders/${state.activeOrderForNotes}/notes/${del.id}`, {method: 'DELETE'});
            await renderNotes();
        }
    } catch (error) { toast(error.message, true); }
}

// ------------------------------------------------------------------ Пользователи

async function loadUsers(page = state.usersPage.page) {
    try {
        const result = await api(`/admin/users?page=${page}&size=20&sort=email,asc`);
        state.usersPage = result;
        state.users = result.content;
        $('users-body').innerHTML = result.content.map(user => `<tr class="${user.enabled ? '' : 'archived'}">
            <td>${escapeHtml(user.email)}</td><td>${escapeHtml(user.displayName)}</td>
            <td>${user.roles.map(r => `<span class="badge">${escapeHtml(roleLabel(r))}</span>`).join(' ')}</td>
            <td>${user.enabled ? 'Да' : 'Нет'}</td><td>${user.passwordChangeRequired ? 'Да' : 'Нет'}</td>
            <td><div class="actions"><button class="secondary" data-user-edit="${user.id}">Изменить</button><button class="secondary" data-user-reset="${user.id}">Сбросить пароль</button></div></td>
        </tr>`).join('');
        $('users-page').textContent = `Страница ${result.page + 1} из ${Math.max(1, result.totalPages)} · всего ${result.totalElements}`;
        $('users-prev').disabled = result.first;
        $('users-next').disabled = result.last;
    } catch (error) { toast(error.message, true); }
}

function openUser(id = null) {
    const user = id ? state.users.find(u => u.id === id) : null;
    const checked = role => user ? user.roles.includes(role) : role === 'VIEWER';
    const form = $('entity-form');
    form.innerHTML = `<div class="dialog-head"><h3>${user ? 'Изменение' : 'Новый'} пользователь</h3><button type="button" data-close-dialog="entity-dialog" class="icon-button">×</button></div>
        <label>Email (логин)<input name="email" type="email" required maxlength="254" value="${escapeHtml(user?.email || '')}"></label>
        <label>Отображаемое имя<input name="displayName" required maxlength="160" value="${escapeHtml(user?.displayName || '')}"></label>
        ${user ? '' : '<label>Временный пароль<input name="temporaryPassword" type="password" required minlength="12" maxlength="128" autocomplete="new-password"></label><p class="muted">При первом входе пользователь обязан сменить пароль.</p>'}
        <fieldset><legend>Роли</legend>${['VIEWER', 'DISPATCHER', 'ADMIN'].map(r => `<label class="inline"><input type="checkbox" name="roles" value="${r}" ${checked(r) ? 'checked' : ''}> ${roleLabel(r)}</label>`).join('')}</fieldset>
        ${user ? `<label class="inline"><input type="checkbox" name="enabled" ${user.enabled ? 'checked' : ''}> Учётная запись активна</label>` : ''}
        <div class="form-message error" data-form-error hidden></div>
        <button class="primary">Сохранить</button>`;
    form.onsubmit = async event => {
        event.preventDefault();
        if (!form.reportValidity()) return;
        const data = new FormData(form);
        const payload = {
            email: data.get('email'), displayName: data.get('displayName'), roles: data.getAll('roles'),
            ...(user ? {enabled: data.get('enabled') === 'on', version: user.version} : {temporaryPassword: data.get('temporaryPassword')})
        };
        try {
            await api(id ? `/admin/users/${id}` : '/admin/users', {method: id ? 'PUT' : 'POST', body: JSON.stringify(payload)});
            $('entity-dialog').close();
            toast('Пользователь сохранён');
            await loadUsers();
        } catch (error) { showFormError(form, error.message); }
    };
    $('entity-dialog').showModal();
}

async function resetUserPassword(id) {
    const password = prompt('Новый временный пароль (от 12 символов: верхний и нижний регистр, цифра, спецсимвол)');
    if (!password) return;
    try {
        await api(`/admin/users/${id}/reset-password`, {method: 'POST', body: JSON.stringify({temporaryPassword: password})});
        toast('Пароль сброшен, пользователь сменит его при входе');
        await loadUsers();
    } catch (error) { toast(error.message, true); }
}

// ------------------------------------------------------------------ Аудит

async function loadAudit(page = state.auditPage.page) {
    try {
        const q = encodeURIComponent($('audit-search').value.trim());
        const result = await api(`/admin/audit?q=${q}&page=${page}&size=50&sort=${encodeURIComponent($('audit-sort').value)}`);
        state.auditPage = result;
        $('audit-body').innerHTML = result.content.map(item => `<tr><td><small>${formatDate(item.occurredAt)}</small></td><td>${escapeHtml(item.actorEmail || 'system')}</td>
            <td><code>${escapeHtml(item.eventType)}</code></td><td>${escapeHtml(item.entityType || '—')}</td><td>${escapeHtml(item.summary)}</td>
            <td><code>${escapeHtml(item.correlationId || '—')}</code></td></tr>`).join('') || '<tr><td colspan="6" class="muted">Событий нет</td></tr>';
        $('audit-page').textContent = `Страница ${result.page + 1} из ${Math.max(1, result.totalPages)} · событий ${result.totalElements}`;
        $('audit-prev').disabled = result.first;
        $('audit-next').disabled = result.last;
    } catch (error) { toast(error.message, true); }
}

// ------------------------------------------------------------------ Пароли и выход

function openPasswordDialog(required = false) {
    return new Promise(resolve => {
        const dialog = $('entity-dialog');
        const form = $('entity-form');
        form.innerHTML = `<div class="dialog-head"><h3>${required ? 'Установка постоянного пароля' : 'Смена пароля'}</h3>${required ? '' : '<button type="button" data-close-dialog="entity-dialog" class="icon-button">×</button>'}</div>
            ${required ? '' : '<label>Текущий пароль<input name="current" type="password" required maxlength="128" autocomplete="current-password"></label>'}
            <label>Новый пароль<input name="next" type="password" required minlength="12" maxlength="128" autocomplete="new-password"></label>
            <label>Повторите новый пароль<input name="confirm" type="password" required minlength="12" maxlength="128" autocomplete="new-password"></label>
            <p class="muted">Минимум 12 символов: верхний и нижний регистр, цифра и спецсимвол; пароль не должен содержать логин.</p>
            <div class="form-message error" data-form-error hidden></div>
            <button class="primary">${required ? 'Установить пароль' : 'Изменить'}</button>`;
        let completed = false;
        const onClose = () => { dialog.removeEventListener('close', onClose); if (!completed) resolve(false); };
        dialog.addEventListener('close', onClose);
        form.onsubmit = async event => {
            event.preventDefault();
            const data = new FormData(form);
            const next = String(data.get('next') || '');
            if (next !== String(data.get('confirm') || '')) return showFormError(form, 'Новые пароли не совпадают');
            try {
                if (required) {
                    await api('/auth/change-initial-password', {method: 'POST', body: JSON.stringify({newPassword: next})});
                    // Старый access JWT содержит passwordChangeRequired=true — получаем новый по refresh-cookie.
                    if (!await refreshAccessToken()) {
                        completed = true; dialog.close(); showLogin();
                        toast('Пароль изменён. Войдите с новым паролем.');
                        return resolve(false);
                    }
                    completed = true; dialog.close();
                    toast('Постоянный пароль установлен');
                    return resolve(true);
                }
                await api('/auth/change-password', {method: 'POST', body: JSON.stringify({currentPassword: data.get('current'), newPassword: next})});
                completed = true; dialog.close();
                toast('Пароль изменён. Выполните вход заново.');
                resolve(true);
                await logout();
            } catch (error) { showFormError(form, error.message); }
        };
        dialog.showModal();
    });
}

async function logout() {
    try { await fetch('/api/auth/logout', {method: 'POST', credentials: 'include', headers: {'Content-Type': 'application/json'}, body: '{}'}); } catch (_) { /* сеть недоступна */ }
    showLogin();
}

// ------------------------------------------------------------------ События

function bindEvents() {
    $('login-form').addEventListener('submit', async event => {
        event.preventDefault();
        const button = $('login-form').querySelector('button[type="submit"]');
        $('login-error').hidden = true;
        button.disabled = true;
        try {
            const response = await fetch('/api/auth/login', {
                method: 'POST', credentials: 'include', headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({email: $('login-email').value, password: $('login-password').value, clientType: 'WEB'})
            });
            if (!response.ok) throw new Error(await parseError(response));
            const result = await response.json();
            state.accessToken = result.accessToken;
            state.user = result.user;
            $('login-password').value = '';
            await enterApplication();
        } catch (error) {
            showLogin();
            $('login-error').textContent = error.message || 'Не удалось выполнить вход';
            $('login-error').hidden = false;
        } finally {
            button.disabled = false;
        }
    });

    $('navigation').addEventListener('click', event => {
        const button = event.target.closest('[data-tab]');
        if (button && !button.hidden) switchTab(button.dataset.tab);
    });
    $('logout').addEventListener('click', logout);
    $('change-password').addEventListener('click', () => openPasswordDialog(false));

    // Заказы
    $('search-orders').addEventListener('click', () => { state.orders.query = $('order-search').value.trim(); loadOrders(0); });
    $('order-search').addEventListener('keydown', event => { if (event.key === 'Enter') $('search-orders').click(); });
    $('orders-prev').addEventListener('click', () => loadOrders(state.orders.page - 1));
    $('orders-next').addEventListener('click', () => loadOrders(state.orders.page + 1));
    $('orders-size').addEventListener('change', event => { state.orders.size = Number(event.target.value); loadOrders(0); });
    document.querySelectorAll('th.sortable').forEach(th => th.addEventListener('click', () => {
        const [field, dir] = state.orders.sort.split(',');
        state.orders.sort = `${th.dataset.sort},${field === th.dataset.sort && dir === 'asc' ? 'desc' : 'asc'}`;
        loadOrders(0);
    }));
    $('add-order').addEventListener('click', () => openOrder());
    $('bulk-action').addEventListener('click', openBulkDialog);
    $('export-csv').addEventListener('click', exportCsv);
    $('import-csv').addEventListener('click', () => { $('csv-result').textContent = ''; $('csv-apply').disabled = true; $('csv-dialog').showModal(); });
    $('csv-file').addEventListener('change', () => { $('csv-apply').disabled = true; $('csv-result').textContent = ''; });
    $('csv-mode').addEventListener('change', () => { $('csv-apply').disabled = true; });
    $('csv-preview').addEventListener('click', () => runCsv(false));
    $('csv-apply').addEventListener('click', () => runCsv(true));
    $('orders-body').addEventListener('click', event => {
        const pick = attr => event.target.closest(`[${attr}]`)?.getAttribute(attr);
        const view = pick('data-order-view'), edit = pick('data-order-edit'), notes = pick('data-order-notes'),
            archive = pick('data-order-archive'), del = pick('data-order-delete');
        if (view) openOrder(view, true);
        if (edit) openOrder(edit, false);
        if (notes) openNotes(notes);
        if (archive) archiveOrder(archive);
        if (del) deleteOrder(del);
    });
    $('orders-body').addEventListener('change', event => {
        const checkbox = event.target.closest('[data-order-select]');
        if (checkbox) checkbox.checked ? state.selectedOrderIds.add(checkbox.dataset.orderSelect) : state.selectedOrderIds.delete(checkbox.dataset.orderSelect);
    });
    $('select-all-orders').addEventListener('change', event => {
        document.querySelectorAll('[data-order-select]').forEach(cb => {
            cb.checked = event.target.checked;
            event.target.checked ? state.selectedOrderIds.add(cb.dataset.orderSelect) : state.selectedOrderIds.delete(cb.dataset.orderSelect);
        });
    });

    // Комментарии
    $('note-form').addEventListener('submit', async event => {
        event.preventDefault();
        try {
            await api(`/orders/${state.activeOrderForNotes}/notes`, {method: 'POST', body: JSON.stringify({body: $('note-body').value, pinned: $('note-pinned').checked, version: null})});
            $('note-body').value = '';
            $('note-pinned').checked = false;
            await renderNotes();
        } catch (error) { toast(error.message, true); }
    });
    $('notes-list').addEventListener('click', handleNotesClick);

    // Клиенты, пользователи, аудит
    $('add-client').addEventListener('click', () => openClient());
    $('clients-body').addEventListener('click', event => {
        const edit = event.target.closest('[data-client-edit]'), toggle = event.target.closest('[data-client-toggle]');
        if (edit) openClient(edit.dataset.clientEdit);
        if (toggle) toggleClient(toggle.dataset.clientToggle);
    });
    $('add-user').addEventListener('click', () => openUser());
    $('users-body').addEventListener('click', event => {
        const edit = event.target.closest('[data-user-edit]'), reset = event.target.closest('[data-user-reset]');
        if (edit) openUser(edit.dataset.userEdit);
        if (reset) resetUserPassword(reset.dataset.userReset);
    });
    $('users-prev').addEventListener('click', () => loadUsers(state.usersPage.page - 1));
    $('users-next').addEventListener('click', () => loadUsers(state.usersPage.page + 1));
    $('search-audit').addEventListener('click', () => loadAudit(0));
    $('audit-search').addEventListener('keydown', event => { if (event.key === 'Enter') loadAudit(0); });
    $('audit-sort').addEventListener('change', () => loadAudit(0));
    $('audit-prev').addEventListener('click', () => loadAudit(state.auditPage.page - 1));
    $('audit-next').addEventListener('click', () => loadAudit(state.auditPage.page + 1));

    document.body.addEventListener('click', event => {
        const close = event.target.closest('[data-close-dialog]');
        if (close) $(close.dataset.closeDialog).close();
    });
}

boot().catch(error => {
    console.error('[Delivery] Boot failed', error);
    showLogin();
    toast(error.message || 'Не удалось инициализировать приложение', true);
});
