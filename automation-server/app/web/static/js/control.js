const API_BASE = "";
let currentDevice = null;
let adminWebSocket = null;
let selectedDumpNode = null;
const DUMP_ATTRIBUTES_EMPTY_HTML = '<p class="empty">尚未选择节点</p>';
let dumpNodeIndex = [];
let lastHighlightedBounds = null;
const screenshotMeta = { width: null, height: null };
const capabilitySelect = document.getElementById("capability-select");
const capabilityHint = document.getElementById("capability-hint");
const websocketStatus = document.getElementById("websocketStatus");
const refreshUsersBtn = document.getElementById("refreshUsersBtn");
const refreshDeviceBtn = document.getElementById("refreshDeviceBtn");
const deviceNameDisplay = document.getElementById("deviceNameDisplay");
const deviceUsernameDisplay = document.getElementById("deviceUsernameDisplay");
const deviceStatusBadge = document.getElementById("deviceStatusBadge");
const deviceLastOnline = document.getElementById("deviceLastOnline");
const deviceDetails = document.getElementById("device-details");
const commandInput = document.getElementById("command-input");
const clearCommandBtn = document.getElementById("clearCommandBtn");
let currentCapabilities = [];
let authToken = localStorage.getItem("auth_token");
let authRole = localStorage.getItem("auth_role");

localStorage.removeItem("admin_token");
// 记录命令ID与动作类型，便于回传结果时快速定位
const pendingCommands = new Map();

const handlePossibleAuthError = (error) => {
  if (!error || !error.message) {
    return false;
  }
  if (/认证/.test(error.message)) {
    logout();
    return true;
  }
  return false;
};

function parseBounds(boundsStr) {
  if (!boundsStr) {
    return null;
  }
  const match = /\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]/.exec(boundsStr);
  if (!match) {
    return null;
  }
  const left = Number(match[1]);
  const top = Number(match[2]);
  const right = Number(match[3]);
  const bottom = Number(match[4]);
  if ([left, top, right, bottom].some(Number.isNaN) || right <= left || bottom <= top) {
    return null;
  }
  return { left, top, right, bottom };
}

function getNodeBounds(details) {
  if (!details || !details.dataset.bounds) {
    return null;
  }
  try {
    return JSON.parse(details.dataset.bounds);
  } catch (error) {
    return null;
  }
}

function highlightScreenshot(bounds) {
  const highlight = document.getElementById("screenshot-highlight");
  const img = document.getElementById("screenshot-img");
  lastHighlightedBounds = bounds || null;

  if (!highlight || !img) {
    return;
  }

  if (
    !bounds ||
    !screenshotMeta.width ||
    !screenshotMeta.height ||
    !img.complete ||
    img.naturalWidth === 0 ||
    img.naturalHeight === 0
  ) {
    highlight.style.display = "none";
    return;
  }

  const rect = img.getBoundingClientRect();
  if (rect.width === 0 || rect.height === 0) {
    highlight.style.display = "none";
    return;
  }

  const container = img.parentElement;
  const containerRect = container ? container.getBoundingClientRect() : rect;
  const offsetX = rect.left - containerRect.left;
  const offsetY = rect.top - containerRect.top;

  const scaleX = rect.width / screenshotMeta.width;
  const scaleY = rect.height / screenshotMeta.height;

  const left = bounds.left * scaleX;
  const top = bounds.top * scaleY;
  const width = Math.max((bounds.right - bounds.left) * scaleX, 2);
  const height = Math.max((bounds.bottom - bounds.top) * scaleY, 2);

  highlight.style.display = "block";
  highlight.style.left = `${offsetX + left}px`;
  highlight.style.top = `${offsetY + top}px`;
  highlight.style.width = `${width}px`;
  highlight.style.height = `${height}px`;
}

function onScreenshotClick(event) {
  event.preventDefault();
  event.stopPropagation();
  const img = event.currentTarget;
  if (!dumpNodeIndex.length || !screenshotMeta.width || !screenshotMeta.height || !img.complete) {
    return;
  }

  const rect = img.getBoundingClientRect();
  if (rect.width === 0 || rect.height === 0) {
    return;
  }

  const scaleX = screenshotMeta.width / rect.width;
  const scaleY = screenshotMeta.height / rect.height;
  const clickX = (event.clientX - rect.left) * scaleX;
  const clickY = (event.clientY - rect.top) * scaleY;

  let bestNode = null;
  let bestArea = Infinity;

  for (const { details, bounds } of dumpNodeIndex) {
    if (!details || !bounds) {
      continue;
    }
    if (clickX >= bounds.left && clickX <= bounds.right && clickY >= bounds.top && clickY <= bounds.bottom) {
      const area = (bounds.right - bounds.left) * (bounds.bottom - bounds.top);
      if (area < bestArea) {
        bestArea = area;
        bestNode = details;
      }
    }
  }

  if (bestNode) {
    if (bestNode.style.display === "none") {
      const searchInput = document.getElementById("dump-search");
      if (searchInput) {
        searchInput.value = "";
      }
      searchDumpTree("");
    }
    selectDumpNode(bestNode, true);
  }
}

function setWebsocketStatus(text, statusClass) {
  if (!websocketStatus) {
    return;
  }
  websocketStatus.textContent = `WebSocket：${text}`;
  websocketStatus.classList.remove("connected", "disconnected", "connecting");
  if (statusClass) {
    websocketStatus.classList.add(statusClass);
  }
}

function formatDateTime(value) {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString("zh-CN");
}

function clearDeviceInfoCard(message = "在上方选择设备后即可查看状态与操作。") {
  if (deviceNameDisplay) {
    deviceNameDisplay.textContent = "尚未选择设备";
  }
  if (deviceUsernameDisplay) {
    deviceUsernameDisplay.textContent = "请先选择用户与设备";
  }
  if (deviceStatusBadge) {
    deviceStatusBadge.textContent = "未连接";
    deviceStatusBadge.classList.remove("online", "offline");
  }
  if (deviceLastOnline) {
    deviceLastOnline.textContent = "最后在线：-";
  }
  if (deviceDetails) {
    deviceDetails.innerHTML = `<p class="empty">${escapeHtml(message)}</p>`;
  }
}

function updateDeviceInfoCard(device) {
  if (!device) {
    clearDeviceInfoCard();
    return;
  }
  if (deviceNameDisplay) {
    deviceNameDisplay.textContent = device.device_name || "未命名设备";
  }
  if (deviceUsernameDisplay) {
    deviceUsernameDisplay.textContent = device.username ? `所属账号：${device.username}` : "所属账号：-";
  }
  if (deviceStatusBadge) {
    deviceStatusBadge.textContent = device.is_online ? "在线" : "离线";
    deviceStatusBadge.classList.toggle("online", !!device.is_online);
    deviceStatusBadge.classList.toggle("offline", !device.is_online);
  }
  if (deviceLastOnline) {
    deviceLastOnline.textContent = `最后在线：${formatDateTime(device.last_online_at)}`;
  }
  if (deviceDetails) {
    const rows = [
      { label: "设备型号", value: device.device_model || "-" },
      { label: "Android版本", value: device.android_version || "-" },
      { label: "内网IP", value: device.local_ip || "-" },
      { label: "公网IP", value: device.public_ip || "-" },
      { label: "创建时间", value: formatDateTime(device.created_at) },
    ];
    deviceDetails.innerHTML = rows
      .map(
        (row) =>
          `<div class="device-meta-row"><span>${escapeHtml(row.label)}</span><strong>${escapeHtml(String(row.value ?? "-"))}</strong></div>`,
      )
      .join("");
  }
}

function defaultValueForType(type) {
  switch ((type || "string").toString().toLowerCase()) {
    case "int":
    case "integer":
      return 0;
    case "float":
    case "double":
    case "number":
      return 0;
    case "bool":
    case "boolean":
      return false;
    case "object":
    case "json":
    case "dict":
    case "map":
      return {};
    case "array":
    case "list":
      return [];
    default:
      return "";
  }
}

function assignNestedValue(target, path, value) {
  if (!path) {
    return;
  }
  const segments = path.split(".");
  let cursor = target;
  for (let i = 0; i < segments.length - 1; i += 1) {
    const key = segments[i];
    if (!Object.prototype.hasOwnProperty.call(cursor, key) || typeof cursor[key] !== "object" || cursor[key] === null || Array.isArray(cursor[key])) {
      cursor[key] = {};
    }
    cursor = cursor[key];
  }
  cursor[segments[segments.length - 1]] = value;
}

function buildCommandTemplate(capability) {
  if (!capability) {
    return "";
  }
  const paramsTemplate = {};
  const params = Array.isArray(capability.params) ? capability.params : [];
  params.forEach((param) => {
    const paramDefault = param && Object.prototype.hasOwnProperty.call(param, "default")
      ? param.default
      : defaultValueForType(param?.type);
    assignNestedValue(paramsTemplate, param?.name, paramDefault);
  });

  const actionValue = capability.action || "";
  const isScriptSpecific = actionValue.startsWith("start_task:");
  if (capability.meta && Array.isArray(capability.meta.scripts) && capability.meta.scripts.length) {
    const [firstScript] = capability.meta.scripts;
    if (isScriptSpecific) {
      assignNestedValue(paramsTemplate, "task_name", firstScript?.name || "");
    }
    if (firstScript && Array.isArray(firstScript.parameters) && firstScript.parameters.length) {
      firstScript.parameters.forEach((spec) => {
        if (!spec?.name) {
          return;
        }
        let value;
        if (spec.default !== undefined && spec.default !== null) {
          value = spec.default;
        } else {
          value = defaultValueForType(spec.type);
        }
        assignNestedValue(paramsTemplate, `config.${spec.name}`, value);
      });
    }
  }

  const template = {
    action: actionValue.startsWith("start_task:") ? "start_task" : actionValue,
  };
  if (Object.keys(paramsTemplate).length) {
    template.params = paramsTemplate;
  }
  return JSON.stringify(template, null, 2);
}

function resetCapabilities(message = "请先选择设备") {
  currentCapabilities = [];
  if (capabilitySelect) {
    capabilitySelect.innerHTML = `<option value="">-- ${escapeHtml(message)} --</option>`;
    capabilitySelect.disabled = true;
    capabilitySelect.selectedIndex = 0;
  }
    if (capabilityHint) {
    capabilityHint.textContent = message === "请先选择设备" ? "选择能力可快速填充自定义指令" : message;
  }
}

function renderCapabilities(capabilities = []) {
  currentCapabilities = Array.isArray(capabilities) ? capabilities.slice() : [];
  if (!capabilitySelect) {
    return;
  }
  if (!currentCapabilities.length) {
    resetCapabilities("该设备未上报能力");
    return;
  }
  capabilitySelect.innerHTML = '<option value="">-- 请选择能力 --</option>';
  currentCapabilities.forEach((capability, index) => {
    const option = document.createElement("option");
    option.value = String(index);
    const scriptLabel = capability?.meta && Array.isArray(capability.meta.scripts) && capability.meta.scripts.length
      ? capability.meta.scripts.map((script) => script.name || "").filter(Boolean).join(" | ")
      : "";
    if (scriptLabel) {
      option.textContent = scriptLabel;
    } else {
      const parts = [];
      if (capability.action) {
        parts.push(capability.action);
      }
      if (capability.description) {
        parts.push(capability.description);
      }
      option.textContent = parts.length ? parts.join(" · ") : `能力 ${index + 1}`;
    }
    capabilitySelect.appendChild(option);
  });
  capabilitySelect.disabled = false;
  capabilitySelect.selectedIndex = 0;
  if (capabilityHint) {
    capabilityHint.textContent = "选择能力后会自动填入模板";
  }
}

function describeScriptParameters(capability) {
  if (!capability?.meta || !Array.isArray(capability.meta.scripts) || !capability.meta.scripts.length) {
    return "";
  }
  const segments = capability.meta.scripts.map((script) => {
    const scriptName = script?.name || "未知脚本";
    if (!Array.isArray(script?.parameters) || !script.parameters.length) {
      return `${scriptName} → 无额外参数`;
    }
    const paramsDesc = script.parameters.map((param) => {
      const pieces = [];
      pieces.push(param?.name || "param");
      if (param?.type) {
        pieces.push(`(${param.type})`);
      }
      if (param?.required) {
        pieces.push("必填");
      }
      if (param?.default !== undefined && param?.default !== null) {
        try {
          pieces.push(`默认: ${JSON.stringify(param.default)}`);
        } catch (err) {
          pieces.push("默认: [无法序列化]");
        }
      }
      return pieces.join(" ");
    });
    return `${scriptName} → ${paramsDesc.join("；")}`;
  });
  return segments.join(" | ");
}

function applySelectedCapability(indexValue) {
  if (!currentCapabilities.length) {
    return;
  }
  const idx = Number.parseInt(indexValue, 10);
  if (Number.isNaN(idx) || idx < 0 || idx >= currentCapabilities.length) {
    return;
  }
  const capability = currentCapabilities[idx];
  const template = buildCommandTemplate(capability);
  if (commandInput && template) {
    commandInput.value = template;
    commandInput.focus();
    if (typeof commandInput.setSelectionRange === "function") {
      commandInput.setSelectionRange(template.length, template.length);
    }
    const displayAction = capability.action && capability.action.startsWith("start_task:") ? "start_task" : (capability.action || "指令");
    showResult(`已填入 ${displayAction} 模板到自定义指令`, "info");
  }
  if (capabilityHint) {
    const scripts = describeScriptParameters(capability);
    const displayAction = capability.action && capability.action.startsWith("start_task:") ? "start_task" : (capability.action || "指令");
    capabilityHint.textContent = scripts ? `可用脚本：${scripts}` : `已填入 ${displayAction} 模板`;
  }
}


window.addEventListener("resize", () => {
  if (lastHighlightedBounds) {
    highlightScreenshot(lastHighlightedBounds);
  }
});

// 页面加载完成后初始化鉴权、数据与 WebSocket 连接
window.onload = async () => {
  if (!authToken) {
    window.location.href = "/login";
    return;
  }

  if (!["admin", "super_admin"].includes(authRole || "")) {
    window.location.href = "/customer";
    return;
  }

  await checkAuth();
  await loadUsers();
  connectAdminWebSocket();
};

async function checkAuth() {
  const adminInfoEl = document.getElementById("admin-info");
  try {
    const res = await fetch(`${API_BASE}/api/admin/me`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });
    if (res.ok) {
      const admin = await res.json();
      if (adminInfoEl) {
        adminInfoEl.textContent = `管理员: ${admin.username}`;
      }
      if (admin.role) {
        authRole = admin.role;
        localStorage.setItem("auth_role", admin.role);
      }
      if (admin.username) {
        localStorage.setItem("auth_username", admin.username);
      }
    } else {
      throw new Error("认证失败");
    }
  } catch (error) {
    authToken = null;
    localStorage.removeItem("auth_token");
    localStorage.removeItem("auth_role");
    localStorage.removeItem("auth_username");
    window.location.href = "/login";
  }
}

async function loadUsers(options = {}) {
  const { showToast = false } = options;
  try {
    if (!authToken) {
      throw new Error("认证失效，请重新登录");
    }
    const res = await fetch(`${API_BASE}/api/admin/users`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    if (!res.ok) throw new Error("获取用户列表失败");

    const users = await res.json();
   const select = document.getElementById("username");
   select.innerHTML = '<option value="">-- 请选择用户 --</option>';

   users.forEach((user) => {
     const option = document.createElement("option");
     option.value = user;
     option.textContent = user;
     select.appendChild(option);
   });
    if (showToast) {
      showResult("用户列表已刷新", "success");
    }
  } catch (error) {
    if (handlePossibleAuthError(error)) {
      return;
    }
    showResult("错误: " + error.message, "error");
  }
}

async function onUsernameChange() {
  const username = document.getElementById("username").value;
  const deviceSelect = document.getElementById("device");
  const controlArea = document.getElementById("control-area");
  const deviceInfo = document.getElementById("device-info");

  controlArea.style.display = "none";
  if (deviceInfo) {
    deviceInfo.style.display = "block";
  }
  currentDevice = null;
  resetDumpView();
  resetCapabilities("请先选择设备");
  clearDeviceInfoCard();

  if (!username) {
    deviceSelect.innerHTML = '<option value="">-- 请先选择用户 --</option>';
    return;
  }

  try {
    if (!authToken) {
      throw new Error("认证失效，请重新登录");
    }
    const res = await fetch(`${API_BASE}/api/admin/devices?username=${username}`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    if (!res.ok) throw new Error("获取设备列表失败");

    const response = await res.json();
    const devices = response.devices || response;
    deviceSelect.innerHTML = '<option value="">-- 请选择设备 --</option>';

    if (!devices || devices.length === 0) {
      deviceSelect.innerHTML = '<option value="">-- 该用户暂无在线设备 --</option>';
      resetCapabilities("该用户暂无在线设备");
      clearDeviceInfoCard("暂未查询到设备，请稍后再试。");
    } else {
      devices.forEach((device) => {
        const option = document.createElement("option");
        option.value = device.id;
        option.textContent = `${device.device_name} (${device.local_ip || "未知IP"})`;
        deviceSelect.appendChild(option);
      });
      resetCapabilities("请先选择设备");
      clearDeviceInfoCard();
    }
  } catch (error) {
    if (handlePossibleAuthError(error)) {
      return;
    }
    showResult("错误: " + error.message, "error");
  }
}

async function onDeviceChange() {
  const deviceId = document.getElementById("device").value;
  const controlArea = document.getElementById("control-area");
  const deviceInfo = document.getElementById("device-info");

  resetDumpView();

  if (!deviceId) {
    controlArea.style.display = "none";
    if (deviceInfo) {
      deviceInfo.style.display = "block";
    }
    currentDevice = null;
    resetCapabilities("请先选择设备");
    clearDeviceInfoCard();
    return;
  }

  resetCapabilities("加载中...");
  clearDeviceInfoCard("正在加载设备信息...");
  controlArea.style.display = "block";
  if (deviceInfo) {
    deviceInfo.style.display = "block";
  }

  try {
    if (!authToken) {
      throw new Error("认证失效，请重新登录");
    }
    const res = await fetch(`${API_BASE}/api/admin/devices/${deviceId}`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    if (!res.ok) throw new Error("获取设备信息失败");

    currentDevice = await res.json();

    updateDeviceInfoCard(currentDevice);
    if (deviceInfo) {
      deviceInfo.style.display = "block";
    }
    controlArea.style.display = "block";

    showResult(`已选择设备: ${currentDevice.device_name}`, "success");
    await loadDeviceCapabilities(deviceId);
  } catch (error) {
    if (handlePossibleAuthError(error)) {
      return;
    }
    resetCapabilities("加载设备信息失败");
    clearDeviceInfoCard("加载设备信息失败，请稍后重试。");
    showResult("错误: " + error.message, "error");
  }
}

async function refreshDeviceInformation(options = {}) {
  const { silent = false } = options;
  if (!currentDevice) {
    showResult("提示: 请选择设备后再刷新状态", "info");
    return;
  }
  try {
    if (!authToken) {
      throw new Error("认证失效，请重新登录");
    }
    const res = await fetch(`${API_BASE}/api/admin/devices/${currentDevice.id}`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });
    if (!res.ok) {
      throw new Error("刷新设备信息失败");
    }
    currentDevice = await res.json();
    updateDeviceInfoCard(currentDevice);
    await loadDeviceCapabilities(currentDevice.id);
    if (!silent) {
      showResult(`已刷新设备状态: ${currentDevice.device_name}`, "info");
    }
  } catch (error) {
    if (handlePossibleAuthError(error)) {
      return;
    }
    resetCapabilities(`刷新设备能力失败: ${error.message}`);
    clearDeviceInfoCard("刷新设备信息失败，请稍后重试。");
    if (!silent) {
      showResult("错误: " + error.message, "error");
    }
  }
}

async function loadDeviceCapabilities(deviceId) {
  resetCapabilities("加载中...");

  try {
    if (!authToken) {
      throw new Error("认证失效，请重新登录");
    }
    const res = await fetch(`${API_BASE}/api/admin/devices/${deviceId}/capabilities`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    if (res.status === 404) {
      resetCapabilities("设备离线或未上报能力");
      if (currentDevice && currentDevice.id === deviceId) {
        currentDevice.is_online = false;
        updateDeviceInfoCard(currentDevice);
      }
      return;
    }

    if (!res.ok) {
      throw new Error("获取设备能力失败");
    }

    const data = await res.json();
    renderCapabilities(data.capabilities || []);
  } catch (error) {
    if (handlePossibleAuthError(error)) {
      return;
    }
    resetCapabilities(`加载设备能力失败: ${error.message}`);
  }
}

async function sendCommand() {
  if (!currentDevice) {
    showResult("错误: 请先选择设备", "error");
    return;
  }

  if (!commandInput) {
    showResult("错误: 指令输入框未找到", "error");
    return;
  }

  const commandText = commandInput.value.trim();
  if (!commandText) {
    showResult("错误: 请输入指令内容", "error");
    return;
  }

  try {
    const command = JSON.parse(commandText);
    const action = command.action;
    const params = command.params || {};
    await sendCommandToDevice(action, params);
  } catch (error) {
    showResult('错误: 指令格式不正确，请使用JSON格式: {"action": "xxx", "params": {...}}', "error");
  }
}

async function captureScreenshot() {
  if (!currentDevice) {
    showResult("错误: 请先选择设备", "error");
    return;
  }

  setButtonLoading("screenshot-text", true);
  const preview = document.getElementById("screenshot-preview");
  if (preview) {
    preview.classList.remove("active");
  }
  screenshotMeta.width = null;
  screenshotMeta.height = null;
  highlightScreenshot(lastHighlightedBounds);

  try {
    await sendCommandToDevice("screenshot", { quality: 80 });
    showResult("截图指令已发送，等待设备响应...", "info");
  } catch (error) {
    setButtonLoading("screenshot-text", false);
    if (handlePossibleAuthError(error)) {
      return;
    }
    showResult("错误: " + error.message, "error");
  }
}

async function dumpView() {
  if (!currentDevice) {
    showResult("错误: 请先选择设备", "error");
    return;
  }

  setButtonLoading("dump-text", true);

  try {
    await sendCommandToDevice("dump_hierarchy", { compress: true });
    showResult("Dump指令已发送，等待设备响应...", "info");
  } catch (error) {
    setButtonLoading("dump-text", false);
    if (handlePossibleAuthError(error)) {
      return;
    }
    showResult("错误: " + error.message, "error");
  }
}

async function sendCommandToDevice(action, params = {}) {
  if (!authToken) {
    throw new Error("认证失效，请重新登录");
  }
  const res = await fetch(`${API_BASE}/api/admin/commands`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${authToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      device_id: currentDevice.id,
      action: action,
      params: params,
    }),
  });

  if (!res.ok) {
    const error = await res.json();
    throw new Error(error.detail || "发送指令失败");
  }

  const command = await res.json();
  if (command.command_id) {
    pendingCommands.set(command.command_id, command.action);
  }
  showResult(`指令已发送: ${command.action}`, "success");
  return command;
}

if (capabilitySelect) {
  capabilitySelect.addEventListener("change", (event) => {
    const value = event.target.value;
    applySelectedCapability(value);
  });
}


function setButtonLoading(elementId, isLoading) {
  const element = document.getElementById(elementId);
  if (!element) return;

  if (isLoading) {
    element.innerHTML = `处理中 <span class="loading"></span>`;
  } else {
    element.textContent = elementId === "screenshot-text" ? "截图" : "Dump界面";
  }
}

function showResult(message, type = "info") {
  const result = document.getElementById("result");
  if (!result) return;
  const color = type === "error" ? "#ef4444" : type === "success" ? "#10b981" : "#2563eb";
  const timestamp = new Date().toLocaleTimeString("zh-CN");
  result.innerHTML += `<div style="color: ${color}; margin-bottom: 8px;">[${timestamp}] ${message}</div>`;
  result.scrollTop = result.scrollHeight;
}

function connectAdminWebSocket() {
  if (adminWebSocket) {
    adminWebSocket.close();
  }

  if (!authToken) {
    window.location.href = "/login";
    return;
  }

  const wsUrl = new URL(`${window.location.origin.replace(/^http/, "ws")}/ws/web`);
  wsUrl.searchParams.set("token", authToken);

  setWebsocketStatus("连接中...", "connecting");
  adminWebSocket = new WebSocket(wsUrl.toString());

  adminWebSocket.onopen = () => {
    setWebsocketStatus("已连接", "connected");
    showResult("WebSocket 已连接", "success");
  };

  adminWebSocket.onmessage = (event) => {
    try {
      const message = JSON.parse(event.data);
      if (message.type === "command_result") {
        handleCommandResult(message.data);
      } else if (message.type === "command_progress") {
        handleCommandProgress(message.data);
      } else {
        console.warn("未知的消息类型:", message.type);
      }
    } catch (error) {
      console.error("解析 WebSocket 消息失败:", error);
    }
  };

  adminWebSocket.onclose = () => {
    setWebsocketStatus("已断开，5 秒后重试", "disconnected");
    showResult("WebSocket 已断开，5 秒后重试", "error");
    setTimeout(connectAdminWebSocket, 5000);
  };

  adminWebSocket.onerror = (error) => {
    console.error("WebSocket 错误:", error);
    setWebsocketStatus("连接异常，正在重试", "disconnected");
  };
}

function handleCommandProgress(progress) {
  if (!progress) return;
  const { command_id: commandId, stage, message, percent } = progress;
  const rendered = percent != null ? `${message || stage} (${percent}%)` : (message || stage || "执行进度更新");
  showResult(`[进度] ${rendered}`, "info");
  if (commandId) {
    pendingCommands.set(commandId, pendingCommands.get(commandId));
  }
}

function handleCommandResult(result) {
  if (!result) {
    return;
  }

  const { status, command_id: commandId, result: data, error_message: errorMessage } = result;
  const action = pendingCommands.get(commandId) || result.action;
  if (commandId) {
    pendingCommands.delete(commandId);
  }

  if (status === "failed") {
    showResult(`指令失败: ${errorMessage || "未知错误"}`, "error");
    setButtonLoading("screenshot-text", false);
    setButtonLoading("dump-text", false);
    return;
  }

  if (!data) {
    showResult("指令执行成功，无返回内容", "success");
    setButtonLoading("screenshot-text", false);
    setButtonLoading("dump-text", false);
    return;
  }

  try {
    const parsed = JSON.parse(data);
    if (!action) {
      showResult(`指令成功: ${JSON.stringify(parsed)}`, "success");
    } else if (action === "screenshot") {
      handleScreenshotResult(parsed);
    } else if (action === "dump_hierarchy") {
      handleDumpResult(parsed);
    } else {
      showResult(`指令成功: ${JSON.stringify(parsed)}`, "success");
    }
  } catch (error) {
    console.warn("指令返回值不是 JSON，按文本处理:", error);
    showResult(`指令成功: ${data}`, "success");
  } finally {
    setButtonLoading("screenshot-text", false);
    setButtonLoading("dump-text", false);
  }
}

function handleScreenshotResult(result) {
  try {
    const base64Data = result.image;
    const binaryString = atob(base64Data);
    const bytes = new Uint8Array(binaryString.length);
    for (let i = 0; i < binaryString.length; i++) {
      bytes[i] = binaryString.charCodeAt(i);
    }
    let imageData = bytes;
    if (result.gzipped) {
      imageData = pako.ungzip(bytes);
    }

    const blob = new Blob([imageData], { type: `image/${result.format || "jpeg"}` });
    const url = URL.createObjectURL(blob);

    const img = document.getElementById("screenshot-img");
    const preview = document.getElementById("screenshot-preview");
    if (img && preview) {
      img.onload = () => {
        const meta = result.metadata || {};
        screenshotMeta.width = meta.width || img.naturalWidth;
        screenshotMeta.height = meta.height || img.naturalHeight;
        highlightScreenshot(lastHighlightedBounds);
      };
      img.src = url;
      preview.classList.add("active");
    }

    showResult(
      `截图成功，大小 ${formatBytes(result.original_size)} → ${formatBytes(result.compressed_size)}，质量 ${result.quality}`,
      "success"
    );
  } catch (error) {
    console.error("处理截图失败:", error);
    showResult("处理截图失败: " + error.message, "error");
  }
}

function formatBytes(bytes) {
  if (!bytes && bytes !== 0) return "未知";
  const units = ["B", "KB", "MB", "GB"];
  let index = 0;
  let value = bytes;
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024;
    index += 1;
  }
  return `${value.toFixed(1)} ${units[index]}`;
}

function buildDumpTreeNode(node, depth = 0) {
  const details = document.createElement("details");
  details.className = "tree-node";
  details.dataset.search = [node.tagName || "", node.getAttribute("resource-id") || "", node.getAttribute("text") || "", node.getAttribute("content-desc") || ""]
    .join(" ")
    .toLowerCase();
  details.dataset.depth = depth;

  const summary = document.createElement("summary");
  summary.innerHTML = `
        <span class="dump-tag">&lt;${node.tagName}&gt;</span>
        ${Array.from(node.attributes)
          .map((attr) => `<span class="dump-attr"><span class="dump-attr-key">${attr.name}</span>="${attr.value}"</span>`)
          .join(" ")}
    `;
  details.appendChild(summary);

  const boundsStr = node.getAttribute("bounds");
  const bounds = parseBounds(boundsStr);
  if (bounds) {
    details.dataset.bounds = JSON.stringify(bounds);
    dumpNodeIndex.push({ details, bounds });
  }

  Array.from(node.children).forEach((child) => {
    details.appendChild(buildDumpTreeNode(child, depth + 1));
  });

  return details;
}

function countDumpNodes(node) {
  let count = 1;
  Array.from(node.children).forEach((child) => {
    count += countDumpNodes(child);
  });
  return count;
}

function selectDumpNode(details, scrollIntoView = false) {
  if (selectedDumpNode) {
    selectedDumpNode.classList.remove("selected");
  }
  selectedDumpNode = details;
  if (details) {
    details.classList.add("selected");
    details.open = true;
    let parent = details.parentElement;
    while (parent) {
      if (parent.tagName === "DETAILS") {
        parent.open = true;
      }
      parent = parent.parentElement;
    }

    const bounds = getNodeBounds(details);
    highlightScreenshot(bounds);
    showDumpAttributes(details);

    if (scrollIntoView) {
      details.scrollIntoView({ block: "center", behavior: "smooth" });
    }
  } else {
    highlightScreenshot(null);
    showDumpAttributes(null);
  }
}

function showDumpAttributes(details) {
  const container = document.getElementById("dump-attributes-content");
  if (!container) {
    return;
  }
  if (!details) {
    container.innerHTML = DUMP_ATTRIBUTES_EMPTY_HTML;
    return;
  }
  const summary = details.querySelector("summary");
  if (!summary) {
    container.innerHTML = DUMP_ATTRIBUTES_EMPTY_HTML;
    return;
  }
  const tagMatch = summary.innerHTML.match(/&lt;([^&]+)&gt;/);
  const tagName = tagMatch ? tagMatch[1] : "未知";
  const attributes = Array.from(summary.querySelectorAll(".dump-attr"));

  if (attributes.length === 0) {
    container.innerHTML = '<p class="empty">该节点未包含属性</p>';
    return;
  }

  const rows = attributes
    .map((attr) => {
      const key = attr.querySelector(".dump-attr-key")?.textContent || "";
      const value = attr.textContent?.replace(`${key}=`, "") || "";
      return `<tr><td class="attr-key">${key}</td><td>${escapeHtml(value)}</td></tr>`;
    })
    .join("");

  container.innerHTML = `
        <span class="attr-tag">&lt;${tagName}&gt;</span>
        <table class="dump-attr-table">${rows}</table>
    `;
}

function escapeHtml(str) {
  return str.replace(/[&<>"']/g, (char) => {
    const map = {
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#039;",
    };
    return map[char];
  });
}

function resetDumpView() {
  const panels = document.getElementById("dump-panels");
  const attributes = document.getElementById("dump-attributes-content");
  const tree = document.getElementById("dump-tree");
  const summaryEl = document.getElementById("dump-summary");
  const searchInput = document.getElementById("dump-search");
  const screenshotPanel = document.getElementById("screenshot-preview");
  const screenshotImg = document.getElementById("screenshot-img");
  if (panels) {
    panels.classList.remove("active");
  }
  if (attributes) {
    attributes.innerHTML = DUMP_ATTRIBUTES_EMPTY_HTML;
  }
  if (tree) {
    tree.innerHTML = "";
  }
  if (summaryEl) {
    summaryEl.textContent = "";
  }
  if (searchInput) {
    searchInput.value = "";
  }
  if (selectedDumpNode) {
    selectedDumpNode.classList.remove("selected");
  }
  selectedDumpNode = null;
  showDumpAttributes(null);

  if (screenshotPanel) {
    screenshotPanel.classList.remove("active");
  }
  if (screenshotImg) {
    screenshotImg.src = "";
  }
  screenshotMeta.width = null;
  screenshotMeta.height = null;
  dumpNodeIndex = [];
  lastHighlightedBounds = null;
  highlightScreenshot(null);
}

function renderDumpTree(xmlString, meta = {}) {
  const parser = new DOMParser();
  const doc = parser.parseFromString(xmlString, "application/xml");
  const parseError = doc.querySelector("parsererror");
  if (parseError) {
    throw new Error(parseError.textContent || "XML解析失败");
  }

  const root = doc.documentElement;
  const treeContainer = document.getElementById("dump-tree");
  const summaryEl = document.getElementById("dump-summary");
  const wrapper = document.getElementById("dump-panels");
  const searchInput = document.getElementById("dump-search");

  dumpNodeIndex = [];
  lastHighlightedBounds = null;
  highlightScreenshot(null);

  treeContainer.innerHTML = "";
  summaryEl.textContent = "";

  const treeRoot = buildDumpTreeNode(root, 0);
  treeRoot.classList.add("tree-node-root");
  treeContainer.appendChild(treeRoot);

  const nodeCount = countDumpNodes(root);
  const sizeInfo =
    meta && meta.original_size != null
      ? `${formatBytes(meta.original_size)} → ${formatBytes(meta.compressed_size)}`
      : "数据大小未知";
  summaryEl.textContent = `根节点: <${root.tagName}> | 节点数: ${nodeCount} | 数据: ${sizeInfo}`;

  wrapper.classList.add("active");
  if (searchInput) {
    searchInput.value = "";
  }

  selectDumpNode(treeRoot);
}

function searchDumpTree(keyword) {
  const treeContainer = document.getElementById("dump-tree");
  if (!treeContainer) {
    return;
  }

  const nodes = Array.from(treeContainer.querySelectorAll(".tree-node"));
  const term = (keyword || "").trim().toLowerCase();

  nodes.forEach((node) => {
    node.classList.remove("match", "descendant-match");
    if (!term) {
      node.style.display = "";
      const depth = parseInt(node.dataset.depth || "0", 10);
      if (node !== selectedDumpNode) {
        node.open = depth < 2;
      }
    }
  });

  if (!term) {
    if (selectedDumpNode) {
      let parent = selectedDumpNode.parentElement?.closest?.(".tree-node") || null;
      while (parent) {
        parent.open = true;
        parent = parent.parentElement?.closest?.(".tree-node") || null;
      }
    }
    return;
  }

  nodes.forEach((node) => {
    const haystack = node.dataset.search || "";
    if (haystack.includes(term)) {
      node.classList.add("match");
    }
  });

  nodes.forEach((node) => {
    const isMatch = node.classList.contains("match");
    const hasMatchDesc = node.querySelector(".match") !== null;
    if (isMatch || hasMatchDesc) {
      node.style.display = "";
      node.open = true;
      if (!isMatch && hasMatchDesc) {
        node.classList.add("descendant-match");
      }
      let parent = node.parentElement?.closest?.(".tree-node") || null;
      while (parent) {
        parent.style.display = "";
        parent.open = true;
        parent.classList.add("descendant-match");
        parent = parent.parentElement?.closest?.(".tree-node") || null;
      }
    } else {
      node.style.display = "none";
    }
  });

  if (selectedDumpNode && selectedDumpNode.style.display === "none") {
    selectDumpNode(null);
  }

  if (lastHighlightedBounds) {
    highlightScreenshot(lastHighlightedBounds);
  }
}

async function handleDumpResult(result) {
  try {
    const base64Data = result.data;
    const binaryString = atob(base64Data);
    const bytes = new Uint8Array(binaryString.length);
    for (let i = 0; i < binaryString.length; i++) {
      bytes[i] = binaryString.charCodeAt(i);
    }

    let xmlData;
    if (result.compressed) {
      xmlData = pako.ungzip(bytes, { to: "string" });
    } else {
      xmlData = new TextDecoder().decode(bytes);
    }

    renderDumpTree(xmlData, result);

    showResult(
      `Dump成功，数据大小 ${formatBytes(result.original_size)} → ${formatBytes(result.compressed_size)}，已渲染节点树`,
      "success"
    );
    setButtonLoading("dump-text", false);
  } catch (error) {
    console.error("解压Dump失败:", error);
    showResult("解压Dump失败: " + error.message, "error");
    setButtonLoading("dump-text", false);
  }
}

const dumpTreeElement = document.getElementById("dump-tree");
if (dumpTreeElement) {
  dumpTreeElement.addEventListener("click", (event) => {
    const summary = event.target.closest("summary");
    if (!summary) {
      return;
    }
    const details = summary.parentElement;
    event.preventDefault();
    const shouldOpen = !details.open;
    details.open = shouldOpen;
    if (shouldOpen) {
      selectDumpNode(details, false);
    } else if (selectedDumpNode === details) {
      selectDumpNode(null);
    }
  });
}

const screenshotImgElement = document.getElementById("screenshot-img");
if (screenshotImgElement) {
  screenshotImgElement.addEventListener("click", onScreenshotClick);
}

function logout() {
  if (adminWebSocket) {
    adminWebSocket.close();
  }
  authToken = null;
  authRole = null;
  localStorage.removeItem("auth_token");
  localStorage.removeItem("auth_role");
  localStorage.removeItem("auth_username");
  window.location.href = "/login";
}

const usernameSelect = document.getElementById("username");
if (usernameSelect) {
  usernameSelect.addEventListener("change", onUsernameChange);
}

if (refreshUsersBtn) {
  refreshUsersBtn.addEventListener("click", () => loadUsers({ showToast: true }));
}

const deviceSelect = document.getElementById("device");
if (deviceSelect) {
  deviceSelect.addEventListener("change", onDeviceChange);
}

if (refreshDeviceBtn) {
  refreshDeviceBtn.addEventListener("click", () => refreshDeviceInformation());
}

const captureButton = document.getElementById("captureBtn");
if (captureButton) {
  captureButton.addEventListener("click", captureScreenshot);
}

const dumpButton = document.getElementById("dumpBtn");
if (dumpButton) {
  dumpButton.addEventListener("click", dumpView);
}

const logoutButton = document.getElementById("logoutBtn");
if (logoutButton) {
  logoutButton.addEventListener("click", logout);
}

const dumpSearchInput = document.getElementById("dump-search");
if (dumpSearchInput) {
  dumpSearchInput.addEventListener("input", (event) => searchDumpTree(event.target.value));
}

const sendCommandBtn = document.getElementById("sendCommandBtn");
if (sendCommandBtn) {
  sendCommandBtn.addEventListener("click", (event) => {
    event.preventDefault();
    sendCommand();
  });
}

if (clearCommandBtn) {
  clearCommandBtn.addEventListener("click", (event) => {
    event.preventDefault();
    if (commandInput) {
      commandInput.value = "";
      showResult("已清空自定义指令输入", "info");
    }
  });
}
