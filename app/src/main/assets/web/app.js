(function () {
    'use strict';

    const LOGIN_PAGE = 'login.html';
    const WORK_PAGE = 'work.html';

    /**
     * 显示一个自定义 Modal 弹窗，兼容 WebView 环境（不依赖 alert）。
     * @param {string} title - 弹窗标题
     * @param {string} message - 弹窗正文（支持 \n 换行）
     */
    function showModal(title, message) {
        var existing = document.getElementById('_appModal');
        if (existing) {
            existing.parentNode.removeChild(existing);
        }

        var overlay = document.createElement('div');
        overlay.id = '_appModal';
        overlay.className = 'app-modal-overlay';

        var box = document.createElement('div');
        box.className = 'app-modal-box';

        var titleEl = document.createElement('div');
        titleEl.className = 'app-modal-title';
        titleEl.textContent = title;

        var bodyEl = document.createElement('div');
        bodyEl.className = 'app-modal-body';
        // 将 \n 转为 <br>
        message.split('\n').forEach(function (line, i, arr) {
            bodyEl.appendChild(document.createTextNode(line));
            if (i < arr.length - 1) {
                bodyEl.appendChild(document.createElement('br'));
            }
        });

        var btn = document.createElement('button');
        btn.className = 'app-modal-btn';
        btn.textContent = '确定';
        btn.addEventListener('click', function () {
            overlay.parentNode && overlay.parentNode.removeChild(overlay);
        });

        box.appendChild(titleEl);
        box.appendChild(bodyEl);
        box.appendChild(btn);
        overlay.appendChild(box);
        document.body.appendChild(overlay);

        btn.focus();
    }

    function hasBridgeMethod(name) {
        return typeof window.Bridge !== 'undefined' && window.Bridge && typeof window.Bridge[name] === 'function';
    }

    function safeParseJson(text, fallback) {
        if (!text) {
            return fallback;
        }
        try {
            return JSON.parse(text);
        } catch (error) {
            console.error('JSON parse failed:', error, text);
            return fallback;
        }
    }

    function redirect(page) {
        if (location.pathname.endsWith('/' + page) || location.href.endsWith('/' + page) || location.href.endsWith(page)) {
            return;
        }
        location.href = page;
    }

    function getAuthState() {
        if (hasBridgeMethod('getAuthState')) {
            return safeParseJson(window.Bridge.getAuthState(), { isLoggedIn: false, jwtValid: false });
        }
        return { isLoggedIn: false, jwtValid: false, browserMode: true };
    }

    function setStatus(element, text, type) {
        if (!element) {
            return;
        }
        element.textContent = text;
        element.className = 'status ' + (type || 'info');
    }

    function readFormDraft() {
        return {
            studentId: localStorage.getItem('cg.studentId') || '',
            studentName: localStorage.getItem('cg.studentName') || '',
            km: localStorage.getItem('cg.km') || '5',
            routeSel: localStorage.getItem('cg.routeSel') || '15',
            routeId: localStorage.getItem('cg.routeId') || ''
        };
    }

    function saveFormDraft(studentId, studentName) {
        localStorage.setItem('cg.studentId', studentId || '');
        localStorage.setItem('cg.studentName', studentName || '');
    }

    function saveRouteDraft(km, routeSel, routeId) {
        localStorage.setItem('cg.km', km || '5');
        localStorage.setItem('cg.routeSel', routeSel || '15');
        localStorage.setItem('cg.routeId', routeId || '');
    }

    function resolveRouteSelection(routeSelect, customRouteField, routeIdInput) {
        const sel = routeSelect ? routeSelect.value : '15';
        if (sel === 'custom') {
            const rid = (routeIdInput && routeIdInput.value || '').trim();
            return { routeId: rid, ok: rid.length > 0 };
        }
        return { routeId: sel, ok: true };
    }

    function readSportIdDraft() {
        const fetchedAtMs = Number(localStorage.getItem('cg.sportIdFetchedAtMs') || '0');
        return {
            sportId: (localStorage.getItem('cg.sportId') || '').trim(),
            identityKey: localStorage.getItem('cg.sportIdIdentityKey') || '',
            fetchedAtMs: isFinite(fetchedAtMs) && fetchedAtMs > 0 ? fetchedAtMs : 0
        };
    }

    function saveSportIdDraft(sportId, identityKey, fetchedAtMs) {
        localStorage.setItem('cg.sportId', (sportId || '').trim());
        localStorage.setItem('cg.sportIdIdentityKey', identityKey || '');
        localStorage.setItem('cg.sportIdFetchedAtMs', fetchedAtMs ? String(fetchedAtMs) : '0');
    }

    function clearSportIdDraft() {
        localStorage.removeItem('cg.sportId');
        localStorage.removeItem('cg.sportIdIdentityKey');
        localStorage.removeItem('cg.sportIdFetchedAtMs');
    }

    function initIndexPage() {
        const status = document.getElementById('indexStatus');
        const auth = getAuthState();
        if (auth.jwtValid) {
            setStatus(status, '检测到有效登录，正在进入工作页...', 'success');
            redirect(WORK_PAGE);
        } else {
            setStatus(status, '未检测到有效登录，正在进入登录页...', 'info');
            redirect(LOGIN_PAGE);
        }
    }

    function initLoginPage() {
        const status = document.getElementById('loginStatus');
        const loginButton = document.getElementById('loginButton');
        const dualLoginButton = document.getElementById('dualLoginButton');
        const refreshStatusButton = document.getElementById('refreshStatusButton');

        function refresh() {
            const auth = getAuthState();
            if (auth.jwtValid) {
                setStatus(status, '检测到有效 JWT，正在跳转到工作页...', 'success');
                redirect(WORK_PAGE);
                return;
            }

            if (auth.browserMode) {
                setStatus(status, '当前处于浏览器预览模式，无法调用原生登录。请在 App WebView 中打开。', 'error');
                return;
            }

            setStatus(status, '当前未登录或 JWT 已过期，请先完成统一认证。', 'info');
        }

        if (loginButton) {
            loginButton.addEventListener('click', function () {
                if (!hasBridgeMethod('startOAuthPage')) {
                    setStatus(status, '当前环境不支持原生桥接，无法打开统一认证。', 'error');
                    return;
                }
                setStatus(status, '正在打开统一认证页面...', 'info');
                window.Bridge.startOAuthPage();
            });
        }

        if (dualLoginButton) {
            dualLoginButton.addEventListener('click', function () {
                if (!hasBridgeMethod('performDualLogin')) {
                    setStatus(status, '当前环境不支持同时登录功能。', 'error');
                    return;
                }
                setStatus(status, '正在从官方应用读取登录信息...', 'info');
                window.Bridge.performDualLogin();
            });
        }

        if (refreshStatusButton) {
            refreshStatusButton.addEventListener('click', refresh);
        }

        // 定义回调函数用于双重登录结果处理
        window.onDualLoginSuccess = function(success) {
            if (success) {
                setStatus(status, '同时登录成功！正在进入工作页...', 'success');
                setTimeout(function() {
                    redirect(WORK_PAGE);
                }, 1500);
            }
        };

        window.onDualLoginError = function(errorMessage) {
            setStatus(status, '同时登录失败: ' + (errorMessage || '未知错误'), 'error');
        };

        refresh();
    }

    function initWorkTabs(onTabChanged) {
        const tabButtons = Array.prototype.slice.call(document.querySelectorAll('.tab-button'));
        const tabPanels = Array.prototype.slice.call(document.querySelectorAll('.tab-panel'));

        if (!tabButtons.length || !tabPanels.length) {
            return;
        }

        function setActiveTab(targetId) {
            tabButtons.forEach(function (button) {
                const active = button.dataset.tabTarget === targetId;
                button.classList.toggle('is-active', active);
                button.setAttribute('aria-selected', active ? 'true' : 'false');
            });

            tabPanels.forEach(function (panel) {
                const active = panel.id === targetId;
                panel.classList.toggle('is-active', active);
                panel.hidden = !active;
            });

            if (typeof onTabChanged === 'function') {
                onTabChanged(targetId);
            }
        }

        tabButtons.forEach(function (button) {
            button.addEventListener('click', function () {
                const targetId = button.dataset.tabTarget;
                if (!targetId) {
                    return;
                }
                setActiveTab(targetId);
            });
        });

        const hashTarget = (location.hash || '').replace('#', '').trim();
        const isValidHashTarget = tabPanels.some(function (panel) {
            return panel.id === hashTarget;
        });
        setActiveTab(isValidHashTarget ? hashTarget : 'workPanel');
    }

    function initRecordsPanel() {
        const status = document.getElementById('recordsStatus');
        const summary = document.getElementById('recordsSummary');
        const list = document.getElementById('recordsList');
        const refreshButton = document.getElementById('refreshRecordsButton');
        const clearButton = document.getElementById('clearRecordsButton');
        const workStudentIdInput = document.getElementById('studentId');
        const hiddenStudentIdInput = document.getElementById('recordsStudentId');
        const endpointInput = document.getElementById('recordsEndpoint');

        const RECORDS_CACHE_KEY = 'cg.recordsCache';
        const REAL_VALID_TIP = '有效，纳入考核的评分里程0.0KM,评分次数1次';
        let hasLoadedOnce = false;
        let requestSeq = 0;

        function setRecordsStatus(text, type) {
            setStatus(status, text, type || 'info');
        }

        function readQuery() {
            const studentId = (
                (workStudentIdInput && workStudentIdInput.value) ||
                (hiddenStudentIdInput && hiddenStudentIdInput.value) ||
                ''
            ).trim();
            return {
                endpoint: (endpointInput && endpointInput.value || '').trim() || '/api/l/v7/sportlist',
                studentId: studentId,
                type: 1
            };
        }

        function loadRecordsCache() {
            const raw = sessionStorage.getItem(RECORDS_CACHE_KEY);
            if (!raw) {
                return null;
            }
            const parsed = safeParseJson(raw, null);
            if (!parsed || !Array.isArray(parsed.items)) {
                return null;
            }
            return parsed;
        }

        function saveRecordsCache(result) {
            try {
                sessionStorage.setItem(RECORDS_CACHE_KEY, JSON.stringify({
                    total: result.total,
                    hasMore: result.hasMore,
                    items: Array.isArray(result.items) ? result.items : []
                }));
            } catch (error) {
                // ignore cache failure
            }
        }

        function stringifyRecord(record) {
            if (!record || typeof record !== 'object') {
                return String(record || '');
            }
            return JSON.stringify(record, null, 2);
        }

        function firstNonEmpty(record, keys, fallback) {
            if (!record || typeof record !== 'object') {
                return fallback || '';
            }
            for (let i = 0; i < keys.length; i += 1) {
                const value = record[keys[i]];
                if (value !== null && value !== undefined && String(value).trim() !== '') {
                    return String(value);
                }
            }
            return fallback || '';
        }

        function renderRecords(items) {
            if (!list) {
                return;
            }
            list.innerHTML = '';

            if (!Array.isArray(items) || !items.length) {
                list.innerHTML = '<div class="record-item"><div class="record-item-meta">当前没有可展示的记录。</div></div>';
                return;
            }

            items.forEach(function (item, index) {
                // 提取关键字段
                const beginTime = item.beginTime ? item.beginTime.substring(5, 16) : '-';  // 取 MM-DD HH:MM
                const endTime = item.endTime ? item.endTime.substring(5, 16) : '-';
                const duration = item.activeTime || '-';
                const distance = item.distance ? item.distance + ' km' : '-';
                const avgSpeed = item.avgSpeed ? item.avgSpeed + ' km/h' : '-';
                const avgPace = item.avgPace || '-';
                const stepCount = item.stepCount ? item.stepCount + ' 步' : '-';
                var status = item.isValid === '1' ? '有效' : (item.checkStatus === '1' ? '待审核' : '无效');
                var statusClass = item.isValid === '1' ? 'valid' : (item.checkStatus === '1' ? 'pending' : 'invalid');
                const tip = item.tip || '-';

                if (item.tip === '提交成功，待审核') {
                    status = '待审核';
                    statusClass = 'pending';
                }
                if (item.tip === REAL_VALID_TIP) {
                    status = '真实有效';
                    statusClass = 'valid';
                }

                const card = document.createElement('div');
                card.className = 'record-item record-item-clickable';
                card.style.cursor = 'pointer';
                // Photo thumbnail: server path (uploads/sportimages/...) fetched from cgapp.
                let thumbHtml = '';
                const imgPath = item.sportImages || '';
                if (imgPath && imgPath.indexOf('uploads/') === 0 && item.serverUrl) {
                    const imgUrl = item.serverUrl + '/' + imgPath;
                    thumbHtml =
                        '<div class="record-item-photo">' +
                        '<img class="record-thumb" src="' + imgUrl + '" alt="运动照片" loading="lazy" ' +
                        'onerror="this.style.display=\'none\';this.parentNode.querySelector(\'.record-thumb-fallback\').style.display=\'flex\';" />' +
                        '<div class="record-thumb-fallback" style="display:none">📷 无照片</div>' +
                        '</div>';
                } else {
                    thumbHtml = '<div class="record-item-photo"><div class="record-thumb-fallback is-visible">📷 无照片</div></div>';
                }
                card.innerHTML =
                    '<div class="record-item-header">' +
                    '<div class="record-item-time">' + beginTime + ' ~ ' + endTime + '</div>' +
                    '<div class="record-item-status ' + statusClass + '">' + status + '</div>' +
                    '</div>' +
                    '<div class="record-item-main">' +
                    '<div class="record-item-stat"><span class="label">距离:</span> <span class="value">' + distance + '</span></div>' +
                    '<div class="record-item-stat"><span class="label">时长:</span> <span class="value">' + duration + '</span></div>' +
                    '<div class="record-item-stat"><span class="label">速度:</span> <span class="value">' + avgSpeed + '</span></div>' +
                    '<div class="record-item-stat"><span class="label">配速:</span> <span class="value">' + avgPace + '</span></div>' +
                    '<div class="record-item-stat"><span class="label">步数:</span> <span class="value">' + stepCount + '</span></div>' +
                    '</div>' +
                    thumbHtml +
                    '<div class="record-item-tip">' + tip + '</div>';

                // 添加点击事件打开详情页面
                card.addEventListener('click', function() {
                    if (window.RecordDetailModule) {
                        window.RecordDetailModule.openDetail(item);
                    }
                });

                list.appendChild(card);
            });
        }

        function renderSummary(result, count) {
            if (!summary) {
                return;
            }
            const total = result && result.total != null ? result.total : '未知';
            const hasMore = result && result.hasMore ? '是' : '否';
            const items = result && Array.isArray(result.items) ? result.items : [];
            const realValidCount = items.filter(function (item) {
                return item && item.tip === REAL_VALID_TIP;
            }).length;
            summary.textContent = '已加载 ' + count + ' 条记录；真实有效 ' + realValidCount + ' 次；总数 ' + total + '；是否还有更多：' + hasMore + '。';
        }

        function requestSportRecordsAsync(query) {
            if (hasBridgeMethod('getSportRecordsAsync')) {
                return new Promise(function (resolve, reject) {
                    const requestId = 'records_' + Date.now() + '_' + (++requestSeq);
                    const timeoutId = window.setTimeout(function () {
                        reject(new Error('请求超时，请稍后重试'));
                    }, 30000);

                    window.onSportRecordsResult = function (callbackRequestId, raw) {
                        if (callbackRequestId !== requestId) {
                            return;
                        }
                        window.clearTimeout(timeoutId);
                        resolve(raw || '');
                    };

                    try {
                        window.Bridge.getSportRecordsAsync(JSON.stringify(query), requestId);
                    } catch (error) {
                        window.clearTimeout(timeoutId);
                        reject(error);
                    }
                });
            }

            if (hasBridgeMethod('getSportRecords')) {
                return Promise.resolve(window.Bridge.getSportRecords(JSON.stringify(query)));
            }

            return Promise.reject(new Error('当前环境不支持运动记录查询接口。'));
        }

        function fetchRecords() {
            const query = readQuery();
            if (!hasBridgeMethod('getSportRecords') && !hasBridgeMethod('getSportRecordsAsync')) {
                setRecordsStatus('当前环境不支持运动记录查询接口。', 'error');
                return;
            }

            setRecordsStatus('正在查询运动记录，请稍候...', 'info');
            refreshButton && (refreshButton.disabled = true);

            requestSportRecordsAsync(query).then(function (raw) {
                const result = safeParseJson(raw, null);
                if (!result) {
                    throw new Error('Java 侧返回不是有效 JSON');
                }
                if (result.error) {
                    throw new Error(result.error);
                }

                const items = Array.isArray(result.items) ? result.items : [];
                renderRecords(items);
                renderSummary(result, items.length);
                saveRecordsCache(result);
                setRecordsStatus('记录加载完成。', 'success');
                hasLoadedOnce = true;
            }).catch(function (error) {
                renderRecords([]);
                renderSummary({}, 0);
                setRecordsStatus('查询失败：' + (error && error.message ? error.message : '未知错误'), 'error');
            }).finally(function () {
                refreshButton && (refreshButton.disabled = false);
            });
        }

        if (refreshButton) {
            refreshButton.addEventListener('click', fetchRecords);
        }

        if (clearButton) {
            clearButton.addEventListener('click', function () {
                sessionStorage.removeItem(RECORDS_CACHE_KEY);
                renderRecords([]);
                if (summary) {
                    summary.textContent = '记录已清空。';
                }
                setRecordsStatus('已清空当前展示结果。', 'info');
            });
        }

        renderRecords([]);

        const cached = loadRecordsCache();
        if (cached) {
            renderRecords(cached.items);
            renderSummary(cached, cached.items.length);
            setRecordsStatus('已恢复上次加载结果，点击“刷新运动记录”可手动更新。', 'info');
            hasLoadedOnce = true;
        }

        return {
            ensureLoaded: function () {
                if (!hasLoadedOnce) {
                    fetchRecords();
                }
            },
            refresh: fetchRecords
        };
    }

    function initWorkPage() {
        const auth = getAuthState();
        const status = document.getElementById('workStatus');
        const studentIdInput = document.getElementById('studentId');
        const studentNameInput = document.getElementById('studentName');
        const sportIdInput = document.getElementById('sportId');
        const sportIdWaitHint = document.getElementById('sportIdWaitHint');
        const sportIdButton = document.getElementById('sportIdButton');
        const routeIdInput = document.getElementById('routeId');
        const routeSelect = document.getElementById('routeSelect');
        const customRouteField = document.getElementById('customRouteField');
        const kmInput = document.getElementById('kmInput');
        const routeButton = document.getElementById('routeButton');
        const sportConfigButton = document.getElementById('sportConfigButton');
        const sportImageInput = document.getElementById('sportImageInput');
        const sportImageHint = document.getElementById('sportImageHint');
        const imageAuthButton = document.getElementById('imageAuthButton');
        const uploadImageButton = document.getElementById('uploadImageButton');
        const applyImageButton = document.getElementById('applyImageButton');
        const generateButton = document.getElementById('generateButton');
        const clearOutputButton = document.getElementById('clearOutputButton');
        const logoutButton = document.getElementById('logoutButton');
        const output = document.getElementById('output');

        if (!auth.jwtValid) {
            setStatus(status, '登录已失效，正在返回登录页重新认证...', 'error');
            redirect(LOGIN_PAGE);
            return;
        }

        const recordsPanel = initRecordsPanel();
        initWorkTabs(function (targetId) {
            if (targetId === 'recordsPanel' && recordsPanel) {
                recordsPanel.ensureLoaded();
            }
        });

        const draft = readFormDraft();
        if (studentIdInput) {
            studentIdInput.value = draft.studentId;
        }
        if (studentNameInput) {
            studentNameInput.value = draft.studentName;
        }
        if (kmInput) {
            kmInput.value = draft.km;
        }
        if (routeSelect) {
            routeSelect.value = draft.routeSel;
            if (routeSelect.selectedIndex < 0) {
                routeSelect.value = '15';
            }
            if (customRouteField) {
                customRouteField.hidden = routeSelect.value !== 'custom';
            }
        }
        if (routeIdInput) {
            routeIdInput.value = draft.routeId;
        }
        if (routeSelect) {
            routeSelect.addEventListener('change', function () {
                if (customRouteField) {
                    customRouteField.hidden = routeSelect.value !== 'custom';
                }
            });
        }

        let currentSportId = '';
        let sportIdForIdentity = '';
        let sportIdFetchedAtMs = 0;
        let sportIdWaitTimer = 0;
        let uploadedSportImage = '';

        function setSportImageHint(text) {
            if (sportImageHint) {
                sportImageHint.textContent = text;
            }
        }

        const savedSportDraft = readSportIdDraft();
        currentSportId = savedSportDraft.sportId;
        sportIdForIdentity = savedSportDraft.identityKey;
        sportIdFetchedAtMs = savedSportDraft.fetchedAtMs;
        if (sportIdInput) {
            sportIdInput.value = currentSportId;
        }

        function getCurrentIdentityKey() {
            const studentId = (studentIdInput && studentIdInput.value || '').trim();
            const studentName = (studentNameInput && studentNameInput.value || '').trim();
            if (!studentId && !studentName) {
                return '';
            }
            return studentId + '|' + studentName;
        }

        function formatSportIdTime(timestampMs) {
            if (!timestampMs) {
                return '未知';
            }
            const date = new Date(timestampMs);
            if (isNaN(date.getTime())) {
                return '未知';
            }
            return date.toLocaleString('zh-CN', { hour12: false });
        }

        function formatWaitDuration(timestampMs) {
            if (!timestampMs) {
                return '0分0秒';
            }
            const diffMs = Math.max(0, Date.now() - timestampMs);
            const minutes = Math.floor(diffMs / 60000);
            const seconds = Math.floor((diffMs % 60000) / 1000);
            return minutes + '分' + seconds + '秒';
        }

        function parseTimestampMs(rawTimestamp) {
            if (rawTimestamp == null || rawTimestamp === '') {
                return 0;
            }
            const numeric = Number(rawTimestamp);
            if (!isFinite(numeric) || numeric <= 0) {
                return 0;
            }
            // 兼容秒级时间戳
            return numeric < 1000000000000 ? numeric * 1000 : numeric;
        }

        function stopSportIdWaitTimer() {
            if (sportIdWaitTimer) {
                clearInterval(sportIdWaitTimer);
                sportIdWaitTimer = 0;
            }
        }

        function renderSportIdWaitHint() {
            if (!sportIdWaitHint) {
                return;
            }
            if (!currentSportId || !sportIdFetchedAtMs) {
                sportIdWaitHint.textContent = '尚未记录 SportId 时间。你可以手动填写，或点击“第一步：获取 SportId”。';
                sportIdWaitHint.className = 'status info';
                return;
            }

            const elapsedMs = Math.max(0, Date.now() - sportIdFetchedAtMs);
            const passedMinutes = Math.floor(elapsedMs / 60000);
            sportIdWaitHint.textContent = '当前 SportId：' + currentSportId + '；记录时间：' + formatSportIdTime(sportIdFetchedAtMs) + '；已等待 ' + formatWaitDuration(sportIdFetchedAtMs) + '（前端每秒自动刷新，仅供你判断时机）';
            sportIdWaitHint.className = 'status ' + (passedMinutes >= 15 ? 'success' : 'info');
        }

        function startSportIdWaitTimer() {
            stopSportIdWaitTimer();
            renderSportIdWaitHint();
            if (!currentSportId || !sportIdFetchedAtMs) {
                return;
            }
            sportIdWaitTimer = window.setInterval(renderSportIdWaitHint, 1000);
        }

        function syncSportIdDraftFromInput(options) {
            const trimmedSportId = (sportIdInput && sportIdInput.value || '').trim();
            const nextFetchedAtMs = options && options.keepTimestamp ? (sportIdFetchedAtMs || Date.now()) : Date.now();
            const nextIdentityKey = getCurrentIdentityKey();

            currentSportId = trimmedSportId;
            sportIdForIdentity = trimmedSportId ? nextIdentityKey : '';
            sportIdFetchedAtMs = trimmedSportId ? nextFetchedAtMs : 0;

            if (trimmedSportId) {
                saveSportIdDraft(trimmedSportId, sportIdForIdentity, sportIdFetchedAtMs);
            } else {
                clearSportIdDraft();
            }
            startSportIdWaitTimer();
        }

        function showSportIdSavedStatus(prefix) {
            if (!currentSportId) {
                return;
            }
            setStatus(
                status,
                (prefix || 'SportId 已保存') + '：' + currentSportId + '；记录时间：' + formatSportIdTime(sportIdFetchedAtMs) + '。你现在可以继续生成 JSON。',
                'success'
            );
            renderSportIdWaitHint();
        }

        function getIdentityInput() {
            const studentId = (studentIdInput && studentIdInput.value || '').trim();
            const studentName = (studentNameInput && studentNameInput.value || '').trim();

            if (!studentId) {
                setStatus(status, '请先填写学号，再继续下一步。', 'error');
                studentIdInput && studentIdInput.focus();
                return null;
            }
            if (!studentName) {
                setStatus(status, '请先填写姓名，再继续下一步。', 'error');
                studentNameInput && studentNameInput.focus();
                return null;
            }

            return {
                studentId: studentId,
                studentName: studentName,
                identityKey: studentId + '|' + studentName
            };
        }

        function parseSportIdResponse(rawResponse) {
            if (rawResponse && typeof rawResponse === 'object') {
                return {
                    sportId: (rawResponse.sportId || '').toString().trim(),
                    error: rawResponse.error || '',
                    timestampMs: parseTimestampMs(rawResponse.timestamp || rawResponse.time || rawResponse.fetchedAt)
                };
            }

            const text = (rawResponse == null ? '' : String(rawResponse)).trim();
            if (!text) {
                return { sportId: '', error: '未返回 SportId，请稍后重试。', timestampMs: 0 };
            }

            const json = safeParseJson(text, null);
            if (json) {
                return {
                    sportId: (json.sportId || '').toString().trim(),
                    error: json.error || '',
                    timestampMs: parseTimestampMs(json.timestamp || json.time || json.fetchedAt)
                };
            }

            // 兼容直接返回纯文本 sportId 的场景
            return { sportId: text, error: '', timestampMs: 0 };
        }

        function requestSportId(studentId, studentName) {
            // TODO: 在这里补充你自己的请求逻辑（fetch / Bridge / 其他方式均可）。
            // 约定返回值：
            // 1) 字符串 sportId，例如 "123456"
            // 2) JSON 字符串或对象，例如 {"sportId":"123456"} 或 {"error":"错误信息"}
            if (hasBridgeMethod('requestSportId')) {
                return Promise.resolve(window.Bridge.requestSportId(studentId, studentName));
            }
            return Promise.reject(new Error('当前环境还没有实现 SportId 请求入口，请先在 app.js 中补充 requestSportId()。'));
        }

        if (studentIdInput) {
            studentIdInput.addEventListener('input', function () {
                saveFormDraft(studentIdInput.value, studentNameInput && studentNameInput.value);
            });
        }

        if (studentNameInput) {
            studentNameInput.addEventListener('input', function () {
                saveFormDraft(studentIdInput && studentIdInput.value, studentNameInput.value);
            });
        }

        if (sportIdInput) {
            sportIdInput.addEventListener('input', function () {
                syncSportIdDraftFromInput();
            });

            sportIdInput.addEventListener('change', function () {
                syncSportIdDraftFromInput();
                if (currentSportId) {
                    showSportIdSavedStatus('已手动保存 SportId');
                } else {
                    setStatus(status, '已清空已保存的 SportId；如需继续，可重新填写或点击第一步获取。', 'info');
                    renderSportIdWaitHint();
                }
            });
        }

        if (sportIdButton) {
            sportIdButton.addEventListener('click', function () {
                const identity = getIdentityInput();
                if (!identity) {
                    return;
                }

                saveFormDraft(identity.studentId, identity.studentName);
                setStatus(status, '正在获取 SportId，请稍候...', 'info');
                sportIdButton.disabled = true;

                Promise.resolve(requestSportId(identity.studentId, identity.studentName)).then(function (rawResponse) {
                    const parsed = parseSportIdResponse(rawResponse);
                    if (parsed.error) {
                        throw new Error(parsed.error);
                    }
                    if (!parsed.sportId) {
                        throw new Error('请求成功，但没有拿到可用的 SportId。');
                    }

                    currentSportId = parsed.sportId;
                    sportIdForIdentity = identity.identityKey;
                    sportIdFetchedAtMs = parsed.timestampMs || Date.now();
                    sportIdInput && (sportIdInput.value = currentSportId);
                    saveSportIdDraft(currentSportId, sportIdForIdentity, sportIdFetchedAtMs);
                    startSportIdWaitTimer();
                    setStatus(
                        status,
                        'SportId 获取成功：' + currentSportId + '；获取时间：' + formatSportIdTime(sportIdFetchedAtMs) + '。下一步可以直接生成 UploadJsonSports。',
                        'success'
                    );
                    showModal('✅ 第一步完成：SportId 已获取',
                        'SportId：' + currentSportId + '\n获取时间：' + formatSportIdTime(sportIdFetchedAtMs) +
                        '\n\n下一步：点击「第二步：生成 UploadJsonSports」。');
                }).catch(function (error) {
                    currentSportId = '';
                    sportIdForIdentity = '';
                    sportIdFetchedAtMs = 0;
                    sportIdInput && (sportIdInput.value = '');
                    clearSportIdDraft();
                    stopSportIdWaitTimer();
                    renderSportIdWaitHint();
                    setStatus(status, '获取 SportId 失败：' + (error && error.message ? error.message : '未知错误') + '。你也可以手动填写 SportId。', 'error');
                }).finally(function () {
                    sportIdButton.disabled = false;
                });
            });
        }

        if (routeButton) {
            routeButton.addEventListener('click', function () {
                const routeId = (routeIdInput && routeIdInput.value || '').trim();
                if (!routeId) {
                    setStatus(status, '请输入路线 ID。', 'error');
                    routeIdInput && routeIdInput.focus();
                    return;
                }
                if (!hasBridgeMethod('getRoute')) {
                    setStatus(status, '当前环境不支持路线查询。', 'error');
                    return;
                }

                routeButton.disabled = true;
                setStatus(status, '正在查询路线配置...', 'info');
                try {
                    const resultText = window.Bridge.getRoute(routeId);
                    const result = safeParseJson(resultText, null);
                    output.value = result ? JSON.stringify(result, null, 2) : (resultText || '');
                    setStatus(status, result && result.error ? '路线查询失败：' + result.error : '路线配置已返回，请检查原始 JSON。', result && result.error ? 'error' : 'success');
                } catch (error) {
                    setStatus(status, '路线查询失败：' + (error && error.message ? error.message : '未知错误'), 'error');
                } finally {
                    routeButton.disabled = false;
                }
            });
        }

        if (sportConfigButton) {
            sportConfigButton.addEventListener('click', function () {
                if (!hasBridgeMethod('getSportConfig')) {
                    setStatus(status, '当前环境不支持运动配置查询。', 'error');
                    return;
                }

                sportConfigButton.disabled = true;
                setStatus(status, '正在读取运动配置...', 'info');
                try {
                    const resultText = window.Bridge.getSportConfig();
                    const result = safeParseJson(resultText, null);
                    output.value = result ? JSON.stringify(result, null, 2) : (resultText || '');
                    setStatus(status, result && result.error ? '运动配置读取失败：' + result.error : '运动配置已返回，请检查路线字段。', result && result.error ? 'error' : 'success');
                } catch (error) {
                    setStatus(status, '运动配置读取失败：' + (error && error.message ? error.message : '未知错误'), 'error');
                } finally {
                    sportConfigButton.disabled = false;
                }
            });
        }

        if (sportImageInput) {
            sportImageInput.addEventListener('change', function () {
                const file = sportImageInput.files && sportImageInput.files[0];
                uploadedSportImage = '';
                setSportImageHint(file ? ('已选择：' + file.name) : '未选择图片。');
            });
        }

        if (imageAuthButton) {
            imageAuthButton.addEventListener('click', function () {
                if (!hasBridgeMethod('requestImageUploadAuth')) {
                    setStatus(status, '当前环境不支持图片凭据查询。', 'error');
                    return;
                }
                const file = sportImageInput && sportImageInput.files && sportImageInput.files[0];
                const sportId = (sportIdInput && sportIdInput.value || currentSportId || '').trim();
                if (!file || !sportId) {
                    setStatus(status, '请先选择图片并确认 SportId。', 'error');
                    return;
                }
                imageAuthButton.disabled = true;
                try {
                    const resultText = window.Bridge.requestImageUploadAuth(sportId, file.name);
                    const result = safeParseJson(resultText, null);
                    output.value = result ? JSON.stringify(result, null, 2) : (resultText || '');
                    setStatus(status, '图片上传凭据响应已显示。', 'info');
                } catch (error) {
                    setStatus(status, '图片凭据查询失败：' + (error && error.message ? error.message : '未知错误'), 'error');
                } finally {
                    imageAuthButton.disabled = false;
                }
            });
        }

        if (uploadImageButton) {
            uploadImageButton.addEventListener('click', function () {
                const file = sportImageInput && sportImageInput.files && sportImageInput.files[0];
                const identity = getIdentityInput();
                if (!file) {
                    setStatus(status, '请先选择图片。', 'error');
                    return;
                }
                if (!identity || !hasBridgeMethod('uploadSportImage')) {
                    setStatus(status, '图片上传环境未就绪。', 'error');
                    return;
                }
                if (file.size > 10 * 1024 * 1024) {
                    setStatus(status, '图片不能超过 10MB。', 'error');
                    return;
                }
                // The photo hangs off an existing sport record — savesports must succeed first.
                const pendingPayload = safeParseJson(output.value, null);
                const alreadySubmitted = pendingPayload && pendingPayload.__submitted === true;
                if (!alreadySubmitted) {
                    setStatus(status, '请先完成「第三步：提交 HTTP 请求」——运动记录建立后照片才能上传（否则服务端报“运动记录不存在”）。', 'error');
                    return;
                }
                uploadImageButton.disabled = true;
                setStatus(status, '正在上传图片...', 'info');
                const reader = new FileReader();
                reader.onload = function () {
                    try {
                        const sportId = (sportIdInput && sportIdInput.value || currentSportId || '').trim();
                        if (!sportId) {
                            uploadImageButton.disabled = false;
                            setStatus(status, '请先获取或填写 SportId。', 'error');
                            return;
                        }
                        const resultText = window.Bridge.uploadSportImage(String(reader.result || ''), file.name, identity.studentId, sportId);
                        const result = safeParseJson(resultText, null);
                        const upResp = result && result.uploadResponse ? result.uploadResponse : null;
                        const upCode = upResp ? upResp.code : null;
                        const upMsg = upResp ? upResp.message : '';
                        if (upCode === 200) {
                            // IMPORTANT: do NOT resubmit and do NOT overwrite sportImages with
                            // a local filename. Official flow: savesports carries sportImages=1,
                            // uploadpic delivers the file, and the SERVER renames + binds it,
                            // filling the record's sportImages with its own
                            // 'uploads/sportimages/<term>/<xh>/<uuid>.jpg' path (verified by
                            // probing: our auto-resubmit with a local name OVERWROTE the
                            // server path and broke the official app's photo rendering).
                            setStatus(status, '✅ 第四步完成：图片上传成功（code:200）。服务端已将照片关联到本条记录。', 'success');
                            showModal('✅ 第四步完成：图片已上传（code:200）',
                                'sportId：' + sportId +
                                '\n\n照片已由服务端重命名并绑定到本条记录（官方 App 详情页即可看到）。\n无需重新提交——重新提交反而会用本地文件名覆盖服务端路径。');
                        } else {
                            output.value = resultText || '';
                            setStatus(status, '图片上传未受理（code:' + upCode + ' ' + upMsg + '）。', 'error');
                            showModal('❌ 第四步失败：图片未受理（code:' + upCode + '）',
                                '服务端返回：' + upMsg +
                                '\n\n常见原因：\n· 运动记录未建立/已过期 → 回到第三步重新提交\n· 500 → 稍后重试');
                        }
                    } catch (error) {
                        setStatus(status, '图片上传失败：' + (error && error.message ? error.message : '未知错误'), 'error');
                    } finally {
                        uploadImageButton.disabled = false;
                    }
                };
                reader.onerror = function () {
                    uploadImageButton.disabled = false;
                    setStatus(status, '读取图片失败。', 'error');
                };
                reader.readAsDataURL(file);
            });
        }

        if (applyImageButton) {
            applyImageButton.addEventListener('click', function () {
                const payload = safeParseJson(output.value, null);
                if (!uploadedSportImage) {
                    setStatus(status, '没有可写入的图片引用。', 'error');
                    return;
                }
                if (!payload || !payload.sportId) {
                    setStatus(status, '请先生成 UploadJsonSports，再写入图片。', 'error');
                    return;
                }
                payload.sportImages = uploadedSportImage;
                output.value = JSON.stringify(payload, null, 2);
                setStatus(status, '已写入 sportImages，尚未提交。', 'success');
            });
        }

        if (generateButton) {
            generateButton.addEventListener('click', function () {
                const identity = getIdentityInput();
                if (!identity) {
                    return;
                }
                if (!currentSportId || sportIdForIdentity !== identity.identityKey) {
                    setStatus(status, '当前 SportId 与这组学号/姓名未匹配，系统仍会继续生成，但请你自行确认是否可用。', 'info');
                }

                if (currentSportId && sportIdFetchedAtMs) {
                    setStatus(
                        status,
                        '正在生成 UploadJsonSports...（参考：SportId 记录时间 ' + formatSportIdTime(sportIdFetchedAtMs) + '，已等待 ' + formatWaitDuration(sportIdFetchedAtMs) + '）',
                        'info'
                    );
                }

                const routePick = resolveRouteSelection(routeSelect, customRouteField, routeIdInput);
                if (!routePick.ok) {
                    setStatus(status, '自定义路线 ID 未填写。', 'error');
                    routeIdInput && routeIdInput.focus();
                    return;
                }
                const kmValue = kmInput ? parseFloat(kmInput.value) : 5;
                if (!isFinite(kmValue) || kmValue <= 0 || kmValue > 40) {
                    setStatus(status, '公里数需在 0–40 之间。', 'error');
                    kmInput && kmInput.focus();
                    return;
                }
                saveRouteDraft(String(kmValue), routeSelect ? routeSelect.value : '15', routeIdInput ? routeIdInput.value : '');

                saveFormDraft(identity.studentId, identity.studentName);
                if (!currentSportId || !sportIdFetchedAtMs) {
                    setStatus(status, '正在生成 UploadJsonSports...', 'info');
                }
                // 把持久化保存的 sportIdFetchedAtMs 传给 Java 侧，避免重启后丢失时间基准；
                // km/routeId 走 Ex 版本，支持自定义距离与三条标准跑道。
                const options = JSON.stringify({ km: kmValue, routeId: routePick.routeId });
                const resultText = hasBridgeMethod('buildUploadJsonSportsEx')
                    ? window.Bridge.buildUploadJsonSportsEx(identity.studentId, identity.studentName, String(sportIdFetchedAtMs || 0), options)
                    : window.Bridge.buildUploadJsonSports(identity.studentId, identity.studentName, String(sportIdFetchedAtMs || 0));
                const result = safeParseJson(resultText, null);

                if (!result) {
                    setStatus(status, '生成失败：返回内容不是有效 JSON，请检查原始输出。', 'error');
                    output.value = resultText || '';
                    return;
                }
                if (result.error) {
                    setStatus(status, '生成失败：' + result.error, 'error');
                    output.value = JSON.stringify(result, null, 2);
                    return;
                }

                if (typeof result === 'object' && result !== null) {
                    result.sportId = currentSportId;
                    result.__submitted = false;
                }
                setStatus(status, '生成成功。建议先检查 JSON 内容，再决定是否提交。', 'success');
                output.value = JSON.stringify(result, null, 2);
                const endAt = result.endTime ? result.endTime.replace(' ', 'T') : '';
                const waitMin = endAt && !isNaN(Date.parse(endAt))
                    ? Math.max(0, Math.ceil((Date.parse(endAt) - Date.now()) / 60000)) : 0;
                showModal('✅ 第二步完成：UploadJsonSports 已生成',
                    'sportId：' + (result.sportId || '') + '\n距离：' + (result.odometer || '?') +
                    ' km · 路线：' + (result.planRouteName || '?') +
                    '\nendTime：' + (result.endTime || '?') +
                    (waitMin > 0 ? '\n\n⏳ 需等约 ' + waitMin + ' 分钟（endTime 过后）才能提交。' : '\n\n已满足提交时间条件，可直接进行第三步。') +
                    '\n\n下一步：点击「第三步：提交 HTTP 请求」。');
            });
        }

        const formatButton = document.getElementById('formatButton');
        if (formatButton) {
            formatButton.addEventListener('click', function () {
                const currentText = output.value.trim();
                if (!currentText) {
                    setStatus(status, '当前没有可格式化的内容，请先生成结果。', 'error');
                    return;
                }

                const parsed = safeParseJson(currentText, null);
                if (!parsed) {
                    setStatus(status, '格式化失败：当前内容不是合法 JSON。', 'error');
                    return;
                }

                output.value = JSON.stringify(parsed, null, 2);
                setStatus(status, '格式化完成，已按缩进整理好 JSON。', 'success');
            });
        }

        const copyButton = document.getElementById('copyButton');
        if (copyButton) {
            copyButton.addEventListener('click', function () {
                const text = output.value.trim();
                if (!text) {
                    setStatus(status, '当前没有可复制的内容，请先生成结果。', 'error');
                    return;
                }

                if (navigator.clipboard && navigator.clipboard.writeText) {
                    navigator.clipboard.writeText(text).then(function () {
                        setStatus(status, '已复制结果到剪贴板。', 'success');
                    }).catch(function () {
                        fallbackCopy(text);
                    });
                } else {
                    fallbackCopy(text);
                }

                function fallbackCopy(text) {
                    const textarea = document.createElement('textarea');
                    textarea.value = text;
                    document.body.appendChild(textarea);
                    textarea.select();
                    try {
                        document.execCommand('copy');
                        setStatus(status, '已复制结果到剪贴板。', 'success');
                    } catch (err) {
                        setStatus(status, '复制失败，请稍后重试。', 'error');
                    }
                    document.body.removeChild(textarea);
                }
            });
        }

        const submitButton = document.getElementById('submitButton');
        if (submitButton) {
            submitButton.addEventListener('click', function () {
                const currentOutput = output.value.trim();
                if (!currentOutput || currentOutput === '{}') {
                    setStatus(status, '请先完成第二步生成 JSON，再进行提交。', 'error');
                    return;
                }

                const resultJson = safeParseJson(currentOutput, null);
                if (!resultJson || resultJson.error) {
                    setStatus(status, '当前输出不是可提交的 UploadJsonSports JSON，请先检查内容。', 'error');
                    return;
                }

                if (!hasBridgeMethod('submitSportsData')) {
                    setStatus(status, '当前环境不支持提交接口。', 'error');
                    return;
                }

                // 判断当前真实时间是否已超过 endTime
                if (resultJson.endTime) {
                    // endTime 格式：'yyyy-MM-dd HH:mm:ss'，直接 replace 空格为 'T' 让 Date 能解析
                    var endTimeMs = Date.parse(resultJson.endTime.replace(' ', 'T'));
                    if (!isNaN(endTimeMs)) {
                        var nowMs = Date.now();
                        var diffMs = endTimeMs - nowMs;
                        if (diffMs > 0) {
                            var diffMinutes = Math.ceil(diffMs / 60000);
                            showModal(
                                '⏳ 运动还未结束',
                                '请在约 ' + diffMinutes + ' 分钟后再提交。\n\nendTime：' + resultJson.endTime + "\n或者重新生成一次换个时间"
                            );
                            setStatus(
                                status,
                                '⏳ 运动还未结束！请在约 ' + diffMinutes + ' 分钟后再提交（endTime：' + resultJson.endTime + '）。\n或者重新生成一次换个时间',
                                'error'
                            );
                            return;
                        }
                    }
                }

                setStatus(status, '正在提交 HTTP 请求，请稍候...', 'info');
                const submitResult = window.Bridge.submitSportsData(currentOutput);
                const submitResponse = safeParseJson(submitResult, null);

                if (!submitResponse) {
                    setStatus(status, '服务器响应不是有效 JSON，请检查返回内容。', 'error');
                    output.value = submitResult || '';
                    return;
                }

                if (submitResponse.error) {
                    setStatus(status, '提交失败：' + submitResponse.error, 'error');
                    output.value = JSON.stringify(submitResponse, null, 2);
                    return;
                }

                // Stamp the (still editable) payload so the photo step knows a record exists.
                const serverCode = submitResponse.response ? submitResponse.response.code : null;
                const serverData = submitResponse.response ? submitResponse.response.data : null;
                const serverMsg = submitResponse.response ? submitResponse.response.message : '';
                resultJson.__submitted = serverCode === 200;
                output.value = JSON.stringify(resultJson, null, 2);

                if (resultJson.__submitted) {
                    const tip = serverData && serverData.tip ? String(serverData.tip) : '';
                    const isPhotoRefresh = String(resultJson.sportImages || '').length > 1;
                    setStatus(status, 'HTTP 请求成功（code:200），运动记录已建立。现在可以上传照片/凭据。', 'success');
                    if (isPhotoRefresh) {
                        showModal('🎉 全流程完成：照片已关联到记录',
                            'sportId：' + (resultJson.sportId || '') +
                            '\nsportImages：' + resultJson.sportImages +
                            (tip ? '\n服务端复核提示：' + tip : '') +
                            '\n\n照片与运动记录已绑定，官方 App 里即可查看照片。');
                    } else {
                        showModal('✅ 第三步完成：运动记录已提交（code:200）',
                            'sportId：' + (resultJson.sportId || '') +
                            (tip ? '\n\n服务端复核提示：' + tip + '\n（复核提示不影响记录受理，常见为“不在指定运动时段内”，时段以学校规定为准。）' : '') +
                            '\n\n下一步：选择照片 → 点击「上传选中图片」。');
                    }
                } else {
                    setStatus(status, '提交已发出，服务端未受理（code:' + serverCode + ' ' + serverMsg + '）。照片上传会被拒绝。', 'error');
                    showModal('❌ 第三步失败：服务端未受理（code:' + serverCode + '）',
                        '服务端返回：' + serverMsg +
                        '\n\n常见原因：\n· 500 内部错误 → 稍后重试一次\n· 时段类提示 → 等待学校允许的运动时段\n\n未受理时照片无法上传。');
                }
            });
        }

        if (clearOutputButton) {
            clearOutputButton.addEventListener('click', function () {
                output.value = '{}';
                setStatus(status, '生成区内容已清空，不影响已保存的 SportId。', 'info');
                renderSportIdWaitHint();
            });
        }

        if (logoutButton) {
            logoutButton.addEventListener('click', function () {
                stopSportIdWaitTimer();
                if (hasBridgeMethod('logout')) {
                    window.Bridge.logout();
                    return;
                }
                redirect(LOGIN_PAGE);
            });
        }

        window.addEventListener('beforeunload', stopSportIdWaitTimer);

        if (currentSportId) {
            startSportIdWaitTimer();
            showSportIdSavedStatus('已恢复已保存的 SportId');
        } else {
            renderSportIdWaitHint();
            setStatus(status, '已登录。先填写信息，再按 3 个步骤依次操作。', 'success');
        }

        // 初始化高级设置面板
        initAdvancedPanel();
    }

    function initAdvancedPanel() {
        const advancedStatus = document.getElementById('advancedStatus');
        const credentialsOutput = document.getElementById('credentialsOutput');
        const refreshCredsButton = document.getElementById('refreshCredsButton');
        const copyCredsButton = document.getElementById('copyCresButton');
        const pasteCredsButton = document.getElementById('pasteCredsButton');
        const clearCredsButton = document.getElementById('clearCredsButton');

        function setAdvancedStatus(text, type) {
            if (!advancedStatus) {
                return;
            }
            advancedStatus.textContent = text;
            advancedStatus.className = 'status ' + (type || 'info');
        }

        function loadCredentials() {
            const authState = getAuthState();

            if (authState.browserMode) {
                credentialsOutput.value = JSON.stringify({
                    jwt: '',
                    secret: ''
                }, null, 2);
                setAdvancedStatus('当前处于浏览器预览模式，请在 App WebView 中查看当前凭据。', 'info');
                return;
            }

            if (!authState.jwtValid) {
                credentialsOutput.value = JSON.stringify({
                    jwt: authState.jwt || '',
                    secret: authState.secret || ''
                }, null, 2);
                setAdvancedStatus('登录已失效，请重新登录后再查看或切换凭据。', 'error');
                return;
            }

            if (!authState.jwt || !authState.secret) {
                credentialsOutput.value = JSON.stringify({
                    jwt: authState.jwt || '',
                    secret: authState.secret || ''
                }, null, 2);
                setAdvancedStatus('当前登录态有效，但凭据字段缺失，已尝试读取本地存储。请重新登录一次以刷新凭据。', 'error');
                return;
            }

            credentialsOutput.value = JSON.stringify({
                jwt: authState.jwt,
                secret: authState.secret
            }, null, 2);
            setAdvancedStatus('已读取当前登录凭据。', 'success');
        }

        if (refreshCredsButton) {
            refreshCredsButton.addEventListener('click', loadCredentials);
        }

        if (copyCredsButton) {
            copyCredsButton.addEventListener('click', function () {
                const text = credentialsOutput.value.trim();
                if (!text) {
                    setAdvancedStatus('没有内容可复制。', 'error');
                    return;
                }

                if (navigator.clipboard && navigator.clipboard.writeText) {
                    navigator.clipboard.writeText(text).then(function () {
                        setAdvancedStatus('已复制凭据到剪贴板。', 'success');
                    }).catch(function () {
                        fallbackCopy(text);
                    });
                } else {
                    fallbackCopy(text);
                }

                function fallbackCopy(text) {
                    const textarea = document.createElement('textarea');
                    textarea.value = text;
                    document.body.appendChild(textarea);
                    textarea.select();
                    try {
                        document.execCommand('copy');
                        setAdvancedStatus('已复制凭据到剪贴板。', 'success');
                    } catch (err) {
                        setAdvancedStatus('复制失败。', 'error');
                    }
                    document.body.removeChild(textarea);
                }
            });
        }

        if (pasteCredsButton) {
            pasteCredsButton.addEventListener('click', function () {
                const currentText = credentialsOutput.value.trim();
                if (!currentText) {
                    setAdvancedStatus('请粘贴有效的 JSON 凭据。', 'error');
                    return;
                }

                const parsed = safeParseJson(currentText, null);
                if (!parsed || typeof parsed !== 'object') {
                    setAdvancedStatus('JSON 格式不合法。', 'error');
                    return;
                }

                if (!parsed.jwt || !parsed.secret) {
                    setAdvancedStatus('凭据必须包含 jwt 和 secret 字段。', 'error');
                    return;
                }

                if (!hasBridgeMethod('applyCredentials')) {
                    setAdvancedStatus('当前环境不支持应用凭据。', 'error');
                    return;
                }

                setAdvancedStatus('正在应用凭据...', 'info');
                const result = window.Bridge.applyCredentials(JSON.stringify(parsed));
                const resultObj = safeParseJson(result, null);

                if (!resultObj) {
                    setAdvancedStatus('应用凭据失败：返回数据不合法。', 'error');
                    return;
                }

                if (resultObj.error) {
                    setAdvancedStatus('应用凭据失败：' + resultObj.error, 'error');
                    return;
                }

                loadCredentials();
                setAdvancedStatus('凭据已成功应用，当前用户已切换。', 'success');
            });
        }

        if (clearCredsButton) {
            clearCredsButton.addEventListener('click', function () {
                credentialsOutput.value = '{}';
                setAdvancedStatus('凭据已清空。', 'info');
            });
        }

        // 初始加载凭据
        loadCredentials();
    }

    document.addEventListener('DOMContentLoaded', function () {
        const page = document.body && document.body.dataset ? document.body.dataset.page : '';
        if (page === 'index') {
            initIndexPage();
            return;
        }
        if (page === 'login') {
            initLoginPage();
            return;
        }
        if (page === 'work') {
            initWorkPage();
        }
    });
})();
