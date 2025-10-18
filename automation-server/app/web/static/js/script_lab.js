/* eslint-disable no-console */
/* global pako */

const API_BASE = "";

let authToken = localStorage.getItem("auth_token");
let authRole = localStorage.getItem("auth_role");

const state = {
  users: [],
  devices: [],
  currentUser: null,
  currentDevice: null,
  capabilities: [],
  groupedCapabilities: [],
  activeCapability: null,
  ws: null,
  wsStatusEl: null,
  pendingCommands: new Map(),
  screenshot: {
    blobUrl: null,
    width: null,
    height: null,
    capturedAt: null,
  },
  dump: {
    xml: "",
    summary: "",
    nodes: [],
    selectedNode: null,
    treeIndex: new Map(),
    capturedAt: null,
  },
  fingerprint: {
    sceneId: "auto_scene_id",
    handler: "handle_auto_generated_scene",
    description: "TODO 填写场景描述",
    required_all: [],
    required_any: [],
    forbidden_any: [],
    forbidden_all: [],
  },
  handlerPlan: [],
  lastTapPosition: null,
  isCommandRunning: false,
  currentAction: null,
  chainDumpAfterScreenshot: false,
};

const CAPABILITY_GROUP_DEFS = [
  {
    key: "element",
    label: "元素操作",
    filter: (action) => ["click", "long_click", "tap", "double_click", "press_key", "press_back", "press_home"].some((prefix) => action.startsWith(prefix)),
  },
  {
    key: "interaction",
    label: "输入与滑动",
    filter: (action) => ["input", "clear_text", "swipe", "drag", "set_clipboard", "get_clipboard"].some((prefix) => action.startsWith(prefix)),
  },
  {
    key: "screen",
    label: "屏幕 & 诊断",
    filter: (action) => ["screenshot", "dump_hierarchy", "get_battery", "find_template", "compare_images"].some((prefix) => action.startsWith(prefix)),
  },
  {
    key: "app",
    label: "应用控制",
    filter: (action) => ["launch_app", "stop_app", "clear_app", "list_apps"].some((prefix) => action.startsWith(prefix)),
  },
  {
    key: "script",
    label: "脚本任务",
    filter: (action) => action === "start_task" || action.startsWith("start_task:"),
  },
];

const fingerprintGroupsEl = document.getElementById("fingerprintGroups");
const yamlPreviewEl = document.getElementById("yamlPreview");
const fingerprintStatusChip = document.getElementById("fingerprintStatusChip");
const fingerprintStatusInfo = document.getElementById("fingerprintStatusInfo");
const handlerPlanList = document.getElementById("handlerPlanList");
const consoleTabsEl = document.getElementById("consoleTabs");
const consoleFormArea = document.getElementById("consoleFormArea");
const consoleLog = document.getElementById("consoleLog");
const commandStatusEl = document.getElementById("commandStatus");

const capabilityGroupsEl = document.getElementById("capabilityGroups");
const screenshotImg = document.getElementById("labScreenshot");
const screenshotHighlight = document.getElementById("labScreenshotHighlight");
const screenshotMetaEl = document.getElementById("screenshotMeta");

const dumpTreePane = document.getElementById("dumpTreePane");
const dumpSummary = document.getElementById("dumpSummary");
const dumpAttributeTable = document.getElementById("dumpAttributeTable");
const dumpSearchInput = document.getElementById("dumpSearchInput");

const sceneIdInput = document.getElementById("sceneIdInput");
const sceneHandlerInput = document.getElementById("sceneHandlerInput");
const sceneDescriptionInput = document.getElementById("sceneDescriptionInput");

const toolbarRefreshCapabilities = document.getElementById("toolbarRefreshCapabilities");
const toolbarRefreshUi = document.getElementById("toolbarRefreshUi");
const toolbarValidate = document.getElementById("toolbarValidate");
const toolbarCopyYaml = document.getElementById("toolbarCopyYaml");
const toolbarCopyHandler = document.getElementById("toolbarCopyHandler");
const toolbarLogout = document.getElementById("toolbarLogout");
const toolbarStatus = document.getElementById("deviceStatusChip");
const toolbarRefreshDevices = document.getElementById("toolbarRefreshDevices");

const autoRefreshSnapshot = document.getElementById("autoRefreshSnapshot");

function hasAuth() {
  return authToken && ["admin", "super_admin"].includes(authRole || "");
}

function ensureAuth() {
  if (!hasAuth()) {
    window.location.href = "/login";
  }
}

async function fetchJson(url, options = {}) {
  const headers = options.headers ? { ...options.headers } : {};
  if (authToken) {
    headers.Authorization = `Bearer ${authToken}`;
  }
  const response = await fetch(`${API_BASE}${url}`, { ...options, headers });
  if (response.status === 401) {
    throw new Error("认证失效，请重新登录");
  }
  if (!response.ok) {
    let errMessage = `请求失败: ${response.status}`;
    try {
      const payload = await response.json();
      if (payload?.detail) {
        errMessage = payload.detail;
      }
    } catch (error) {
      // ignore
    }
    throw new Error(errMessage);
  }
  return response.json();
}

function logLine(message, type = "info") {
  if (!consoleLog) return;
  const line = document.createElement("div");
  line.className = `log-line ${type}`;
  const timestamp = new Date().toLocaleTimeString("zh-CN");
  line.textContent = `[${timestamp}] ${message}`;
  consoleLog.appendChild(line);
  consoleLog.scrollTop = consoleLog.scrollHeight;
}

function setCommandStatus(text, variant = "idle") {
  if (!commandStatusEl) {
    return;
  }
  commandStatusEl.textContent = text;
  commandStatusEl.classList.remove("command-status--pending", "command-status--success", "command-status--error");
  if (variant === "pending") {
    commandStatusEl.classList.add("command-status--pending");
  } else if (variant === "success") {
    commandStatusEl.classList.add("command-status--success");
  } else if (variant === "error") {
    commandStatusEl.classList.add("command-status--error");
  }
}

function scheduleIdleStatus(delay = 2000) {
  if (!commandStatusEl) {
    return;
  }
  window.setTimeout(() => {
    if (!state.isCommandRunning) {
      setCommandStatus("暂无指令执行");
    }
  }, delay);
}

function updateFingerprintMetaFromInputs() {
  state.fingerprint.sceneId = sceneIdInput.value.trim() || "auto_scene_id";
  state.fingerprint.handler = sceneHandlerInput.value.trim() || "handle_auto_generated_scene";
  state.fingerprint.description = sceneDescriptionInput.value.trim() || "TODO 填写场景描述";
  refreshYamlPreview();
}

function resetFingerprint() {
  state.fingerprint.sceneId = "auto_scene_id";
  state.fingerprint.handler = "handle_auto_generated_scene";
  state.fingerprint.description = "TODO 填写场景描述";
  state.fingerprint.required_all = [];
  state.fingerprint.required_any = [];
  state.fingerprint.forbidden_any = [];
  state.fingerprint.forbidden_all = [];

  sceneIdInput.value = state.fingerprint.sceneId;
  sceneHandlerInput.value = state.fingerprint.handler;
  sceneDescriptionInput.value = state.fingerprint.description;
  renderFingerprintGroups();
  refreshYamlPreview();
  updateFingerprintStatus(null, "");
}

function updateFingerprintStatus(status, desc) {
  if (!fingerprintStatusChip) return;
  if (status === "matched") {
    fingerprintStatusChip.textContent = "匹配成功";
    fingerprintStatusChip.style.background = "rgba(16, 185, 129, 0.18)";
    fingerprintStatusChip.style.color = "#047857";
  } else if (status === "failed") {
    fingerprintStatusChip.textContent = "匹配失败";
    fingerprintStatusChip.style.background = "rgba(239, 68, 68, 0.18)";
    fingerprintStatusChip.style.color = "#b91c1c";
  } else {
    fingerprintStatusChip.textContent = "指纹未验证";
    fingerprintStatusChip.style.background = "rgba(107, 114, 128, 0.15)";
    fingerprintStatusChip.style.color = "#4b5563";
  }
  fingerprintStatusInfo.textContent = desc || "";
}

function buildYamlFromFingerprint() {
  const indent = (level) => "  ".repeat(level);
  const lines = [];
  lines.push(`${indent(0)}- id: ${state.fingerprint.sceneId}`);
  lines.push(`${indent(1)}description: "${state.fingerprint.description}"`);
  lines.push(`${indent(1)}signature:`);

  const serializeCondition = (condition, level) => {
    const items = Object.entries(condition).map(([key, value]) => {
      const finalValue = typeof value === "string" ? `"${value.replace(/"/g, '\\"')}"` : value;
      return `${key}: ${finalValue}`;
    });
    return `{ ${items.join(", ")} }`;
  };

  ["required_all", "required_any", "forbidden_any", "forbidden_all"].forEach((key) => {
    const list = state.fingerprint[key];
    if (!list || list.length === 0) {
      lines.push(`${indent(2)}${key}: []`);
    } else {
      lines.push(`${indent(2)}${key}:`);
      list.forEach((item) => {
        lines.push(`${indent(3)}- ${serializeCondition(item)}`);
      });
    }
  });

  lines.push(`${indent(1)}handler: ${state.fingerprint.handler}`);
  lines.push(`${indent(1)}del_scenes: []`);
  return lines.join("\n");
}

function refreshYamlPreview() {
  if (yamlPreviewEl) {
    yamlPreviewEl.value = buildYamlFromFingerprint();
  }
}

function renderFingerprintGroups() {
  if (!fingerprintGroupsEl) return;
  fingerprintGroupsEl.innerHTML = "";
  const groupNames = [
    { key: "required_all", label: "必须全部存在 (required_all)" },
    { key: "required_any", label: "至少满足一个 (required_any)" },
    { key: "forbidden_any", label: "禁止出现任意一个 (forbidden_any)" },
    { key: "forbidden_all", label: "禁止同时出现全部 (forbidden_all)" },
  ];

  groupNames.forEach(({ key, label }) => {
    const wrapper = document.createElement("div");
    wrapper.className = "fingerprint-group";
    const header = document.createElement("header");
    const title = document.createElement("span");
    title.textContent = label;
    const count = document.createElement("span");
    count.className = "chip";
    count.textContent = `${state.fingerprint[key]?.length || 0} 条`;
    header.appendChild(title);
    header.appendChild(count);
    wrapper.appendChild(header);

    const list = document.createElement("div");
    list.className = "condition-list";
    const conditions = state.fingerprint[key] || [];
    if (!conditions.length) {
      const empty = document.createElement("p");
      empty.className = "empty";
      empty.textContent = "暂无条件";
      list.appendChild(empty);
    } else {
      conditions.forEach((item, index) => {
        const itemEl = document.createElement("div");
        itemEl.className = "condition-item";
        const content = document.createElement("span");
        content.textContent = Object.entries(item)
          .map(([k, v]) => (typeof v === "boolean" ? `${k}: ${v}` : `${k}: "${v}"`))
          .join(", ");
        itemEl.appendChild(content);
        const actions = document.createElement("div");
        actions.className = "condition-actions";
        const editBtn = document.createElement("button");
        editBtn.className = "btn btn--ghost btn--xs";
        editBtn.type = "button";
        editBtn.textContent = "编辑";
        editBtn.addEventListener("click", () => {
          const newValue = prompt("编辑条件 (JSON)", JSON.stringify(item));
          if (!newValue) return;
          try {
            const parsed = JSON.parse(newValue);
            state.fingerprint[key][index] = parsed;
            renderFingerprintGroups();
            refreshYamlPreview();
            updateFingerprintStatus(null, "");
          } catch (error) {
            alert("JSON 格式错误");
          }
        });
        const removeBtn = document.createElement("button");
        removeBtn.className = "btn btn--ghost btn--xs";
        removeBtn.type = "button";
        removeBtn.textContent = "删除";
        removeBtn.addEventListener("click", () => {
          state.fingerprint[key].splice(index, 1);
          renderFingerprintGroups();
          refreshYamlPreview();
          updateFingerprintStatus(null, "");
        });
        actions.appendChild(editBtn);
        actions.appendChild(removeBtn);
        itemEl.appendChild(actions);
        list.appendChild(itemEl);
      });
    }
    wrapper.appendChild(list);
    fingerprintGroupsEl.appendChild(wrapper);
  });
}

function categorizedCapabilities(capabilities = []) {
  const results = CAPABILITY_GROUP_DEFS.map((group) => ({
    key: group.key,
    label: group.label,
    items: [],
  }));
  const others = { key: "others", label: "其他能力", items: [] };

  capabilities.forEach((cap) => {
    const action = cap.action || "";
    const matchedGroup = CAPABILITY_GROUP_DEFS.find((group) => group.filter(action));
    if (matchedGroup) {
      const target = results.find((g) => g.key === matchedGroup.key);
      target.items.push(cap);
    } else {
      others.items.push(cap);
    }
  });

  const list = results.filter((group) => group.items.length > 0);
  if (others.items.length > 0) {
    list.push(others);
  }
  return list;
}

function renderCapabilities() {
  if (!capabilityGroupsEl) return;
  capabilityGroupsEl.innerHTML = "";
  if (!state.capabilities.length) {
    const empty = document.createElement("p");
    empty.className = "empty";
    empty.textContent = "设备未上报能力或离线。";
    capabilityGroupsEl.appendChild(empty);
    return;
  }
  state.groupedCapabilities = categorizedCapabilities(state.capabilities);
  state.groupedCapabilities.forEach((group) => {
    const groupEl = document.createElement("div");
    groupEl.className = "capability-group";
    const header = document.createElement("h3");
    header.textContent = group.label;
    groupEl.appendChild(header);

    group.items.forEach((capability) => {
      const item = document.createElement("div");
      item.className = "capability-item";
      if (state.activeCapability && state.activeCapability.action === capability.action) {
        item.classList.add("active");
      }
      const title = document.createElement("div");
      title.className = "item-title";
      title.innerHTML = `<span>${capability.action}</span><span>${capability.params?.length || 0} 参数</span>`;
      const desc = document.createElement("div");
      desc.className = "item-desc";
      desc.textContent = capability.description || "无描述";
      item.appendChild(title);
      item.appendChild(desc);
      item.addEventListener("click", () => {
        selectCapability(capability);
        document.querySelectorAll(".capability-item").forEach((el) => el.classList.remove("active"));
        item.classList.add("active");
      });
      groupEl.appendChild(item);
    });

    capabilityGroupsEl.appendChild(groupEl);
  });
}

function formatDefault(value) {
  if (value === null || value === undefined) return "";
  if (typeof value === "object") return JSON.stringify(value);
  return value;
}

function selectCapability(capability) {
  state.activeCapability = capability;
  renderConsoleTabs();
  renderCapabilityForm(capability);
}

function renderConsoleTabs() {
  if (!consoleTabsEl) return;
  consoleTabsEl.innerHTML = "";
  const tabs = state.groupedCapabilities.map((group) => {
    const tab = document.createElement("button");
    tab.type = "button";
    tab.className = "console-tab";
    if (state.activeCapability && group.items.some((item) => item.action === state.activeCapability.action)) {
      tab.classList.add("active");
    }
    tab.textContent = `${group.label} (${group.items.length})`;
    tab.addEventListener("click", () => {
      const first = group.items[0];
      if (first) {
        selectCapability(first);
      }
    });
    return tab;
  });
  tabs.forEach((tab) => consoleTabsEl.appendChild(tab));
}

function renderCapabilityForm(capability) {
  if (!consoleFormArea) return;
  if (!capability) {
    consoleFormArea.innerHTML = '<p class="empty">选择能力以填写参数。</p>';
    return;
  }
  const params = Array.isArray(capability.params) ? capability.params : [];
  const form = document.createElement("div");
  form.className = "capability-form";
  if (!params.length) {
    const info = document.createElement("p");
    info.className = "empty";
    info.textContent = "该能力无需参数，可直接执行。";
    form.appendChild(info);
  } else {
    params.forEach((param) => {
      const row = document.createElement("div");
      row.className = "form-row";
      const label = document.createElement("label");
      label.textContent = `${param.name}${param.required ? " *" : ""} (${param.type})`;
      row.appendChild(label);
      if (param.description) {
        const hint = document.createElement("span");
        hint.className = "form-hint";
        hint.textContent = param.description;
        row.appendChild(hint);
      }
      const input = document.createElement(param.type === "bool" ? "input" : "textarea");
      if (param.type === "bool") {
        input.type = "checkbox";
        input.checked = Boolean(param.default);
      } else {
        input.className = "form-control";
        input.value = formatDefault(param.default);
        input.rows = 2;
      }
      input.dataset.paramName = param.name;
      input.dataset.paramType = param.type;
      row.appendChild(input);
      form.appendChild(row);
    });
  }

  const controls = document.createElement("div");
  controls.className = "inline-controls";
  const runBtn = document.createElement("button");
  runBtn.className = "btn btn--primary";
  runBtn.textContent = `执行 ${capability.action}`;
  runBtn.addEventListener("click", () => executeCapability(capability));
  const addPlanBtn = document.createElement("button");
  addPlanBtn.className = "btn btn--outline";
  addPlanBtn.textContent = "加入序列";
  addPlanBtn.addEventListener("click", () => addCapabilityToPlan(capability));
  const fillNodeBtn = document.createElement("button");
  fillNodeBtn.className = "btn btn--ghost";
  fillNodeBtn.textContent = "用选中节点填充";
  fillNodeBtn.addEventListener("click", () => fillParamsFromSelectedNode(capability));
  controls.appendChild(runBtn);
  controls.appendChild(addPlanBtn);
  controls.appendChild(fillNodeBtn);

  form.appendChild(controls);
  consoleFormArea.innerHTML = "";
  consoleFormArea.appendChild(form);
}

function parseFormParams(capability) {
  const params = Array.isArray(capability.params) ? capability.params : [];
  if (!params.length) {
    return {};
  }
  const result = {};
  const inputs = consoleFormArea.querySelectorAll("[data-param-name]");
  inputs.forEach((input) => {
    const name = input.dataset.paramName;
    const type = input.dataset.paramType;
    if (type === "bool") {
      result[name] = input.checked;
    } else {
      const raw = input.value.trim();
      if (!raw) {
        result[name] = "";
      } else if (type === "int" || type === "number") {
        const num = Number(raw);
        result[name] = Number.isNaN(num) ? raw : num;
      } else if (type === "object" || type === "array") {
        try {
          result[name] = JSON.parse(raw);
        } catch (error) {
          result[name] = raw;
        }
      } else {
        result[name] = raw;
      }
    }
  });
  return result;
}

async function executeCapability(capability, overrideParams = undefined) {
  if (!state.currentDevice) {
    logLine("请先选择设备", "error");
    setCommandStatus("请选择设备后再执行指令", "error");
    return;
  }
  try {
    const action = capability.action;
    if (state.isCommandRunning) {
      logLine(`指令正在执行中: ${state.currentAction || "其他任务"}`, "info");
      setCommandStatus(`等待设备确认: ${state.currentAction || "进行中"}`, "pending");
      return;
    }
    const payload = {
      device_id: state.currentDevice.id,
      action,
      params: overrideParams !== undefined ? overrideParams : parseFormParams(capability),
    };
    if (payload.params === undefined || payload.params === null) {
      payload.params = {};
    }
    state.isCommandRunning = true;
    state.currentAction = action;
    setCommandStatus(`等待设备确认: ${action}`, "pending");
    const response = await fetchJson("/api/admin/commands", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(payload),
    });
    if (response.command_id) {
      state.pendingCommands.set(response.command_id, action);
    }
    logLine(`已发送指令: ${action}`, "info");
    return response;
  } catch (error) {
    state.isCommandRunning = false;
    state.currentAction = null;
    if (capability?.action === "screenshot") {
      state.chainDumpAfterScreenshot = false;
    }
    logLine(`指令发送失败: ${error.message}`, "error");
    setCommandStatus(`指令发送失败: ${error.message}`, "error");
    scheduleIdleStatus();
    throw error;
  }
}

function addCapabilityToPlan(capability) {
  const params = parseFormParams(capability);
  const snapshotParams = JSON.parse(JSON.stringify(params));
  state.handlerPlan.push({
    capability,
    params: snapshotParams,
  });
  renderHandlerPlan();
}

function renderHandlerPlan() {
  if (!handlerPlanList) return;
  handlerPlanList.innerHTML = "";
  if (!state.handlerPlan.length) {
    const empty = document.createElement("p");
    empty.className = "empty";
    empty.textContent = "从下方能力控制台添加步骤。";
    handlerPlanList.appendChild(empty);
    return;
  }
  state.handlerPlan.forEach((step, index) => {
    const row = document.createElement("div");
    row.className = "handler-step";
    const info = document.createElement("div");
    info.innerHTML = `<strong>${index + 1}.</strong> ${step.capability.action}`;
    const actions = document.createElement("div");
    actions.className = "condition-actions";
    const runBtn = document.createElement("button");
    runBtn.className = "btn btn--ghost btn--xs";
    runBtn.textContent = "执行";
    runBtn.addEventListener("click", () => runPlanStep(index));
    const delBtn = document.createElement("button");
    delBtn.className = "btn btn--ghost btn--xs";
    delBtn.textContent = "删除";
    delBtn.addEventListener("click", () => {
      state.handlerPlan.splice(index, 1);
      renderHandlerPlan();
    });
    actions.appendChild(runBtn);
    actions.appendChild(delBtn);
    row.appendChild(info);
    row.appendChild(actions);
    handlerPlanList.appendChild(row);
  });
}

async function runPlanStep(index) {
  const step = state.handlerPlan[index];
  if (!step) return;
  selectCapability(step.capability);
  // Fill form with stored params
  const inputs = consoleFormArea.querySelectorAll("[data-param-name]");
  inputs.forEach((input) => {
    const name = input.dataset.paramName;
    if (!(name in step.params)) return;
    const value = step.params[name];
    if (input.type === "checkbox") {
      input.checked = Boolean(value);
    } else {
      input.value = typeof value === "object" ? JSON.stringify(value) : value;
    }
  });
  await executeCapability(step.capability, step.params);
}

async function runHandlerPlanSequential() {
  for (let i = 0; i < state.handlerPlan.length; i += 1) {
    await runPlanStep(i); // eslint-disable-line no-await-in-loop
  }
}

function handlerPlanToJson() {
  return state.handlerPlan.map((step) => ({
    action: step.capability.action,
    params: step.params,
  }));
}

function copyHandlerTemplate() {
  const plan = handlerPlanToJson();
  const handlerName = state.fingerprint.handler || "handle_auto_generated_scene";
  const body = plan
    .map((step) => `    // TODO: 根据能力执行 ${step.capability?.description || step.capability?.action}\n    await ctx.command("${step.action}", ${JSON.stringify(step.params)});`)
    .join("\n\n");
  const template = `async function ${handlerName}(ctx) {\n${body || "    // TODO: 实现 Handler 逻辑"}\n}`;
  navigator.clipboard.writeText(template).then(() => {
    logLine("Handler 模版已复制", "success");
  });
}

function copyYamlToClipboard() {
  navigator.clipboard.writeText(yamlPreviewEl.value).then(() => {
    logLine("指纹 YAML 已复制", "success");
  });
}

function applyDeviceMeta(device) {
  toolbarStatus.textContent = device
    ? `${device.device_name || device.device_model || "设备"} · ${device.is_online ? "在线" : "离线"}`
    : "未连接设备";
  const onlineEl = document.getElementById("labDeviceOnline");
  const lastOnlineEl = document.getElementById("labDeviceLastOnline");
  const androidEl = document.getElementById("labDeviceAndroid");
  const modelEl = document.getElementById("labDeviceModel");
  if (!device) {
    onlineEl.textContent = "-";
    lastOnlineEl.textContent = "-";
    androidEl.textContent = "-";
    modelEl.textContent = "-";
    return;
  }
  onlineEl.textContent = device.is_online ? "在线" : "离线";
  lastOnlineEl.textContent = device.last_online_at || "-";
  androidEl.textContent = device.android_version || "-";
  modelEl.textContent = device.device_model || "-";
}

function parseBounds(str) {
  if (!str) return null;
  const match = /\[(\d+),(\d+)]\[(\d+),(\d+)]/.exec(str);
  if (!match) return null;
  return {
    left: Number(match[1]),
    top: Number(match[2]),
    right: Number(match[3]),
    bottom: Number(match[4]),
  };
}

function highlightBounds(bounds) {
  if (!screenshotHighlight) return;
  if (!bounds) {
    screenshotHighlight.style.display = "none";
    return;
  }
  const { width, height } = state.screenshot;
  if (!width || !height || !screenshotImg) return;
  const rect = screenshotImg.getBoundingClientRect();
  const container = screenshotImg.parentElement?.getBoundingClientRect();
  if (!container) return;
  const scaleX = rect.width / width;
  const scaleY = rect.height / height;
  const offsetX = rect.left - container.left;
  const offsetY = rect.top - container.top;
  const highlightWidth = (bounds.right - bounds.left) * scaleX;
  const highlightHeight = (bounds.bottom - bounds.top) * scaleY;
  screenshotHighlight.style.display = "block";
  screenshotHighlight.style.left = `${bounds.left * scaleX + offsetX}px`;
  screenshotHighlight.style.top = `${bounds.top * scaleY + offsetY}px`;
  screenshotHighlight.style.width = `${highlightWidth}px`;
  screenshotHighlight.style.height = `${highlightHeight}px`;
}

function selectDumpNode(nodeId, scrollIntoView = false) {
  if (state.dump.selectedNode && state.dump.selectedNode.element) {
    state.dump.selectedNode.element.classList.remove("selected");
  }
  const target = state.dump.treeIndex.get(nodeId);
  state.dump.selectedNode = target || null;
  if (target && target.element) {
    let ancestor = target.element;
    while (ancestor) {
      if (ancestor.tagName === "DETAILS") {
        ancestor.open = true;
      }
      ancestor = ancestor.parentElement;
    }
    target.element.classList.add("selected");
    if (scrollIntoView) {
      target.element.scrollIntoView({ block: "center", behavior: "smooth" });
    }
  }
  const bounds = target?.bounds;
  highlightBounds(bounds);
  renderAttributeTable(target);
}

function createConditionFromNode(node, strategy) {
  if (!node) return null;
  const condition = {};
  switch (strategy) {
    case "resourceId":
      if (node.resourceId) condition.resourceId = node.resourceId;
      break;
    case "resourceIdText":
      if (node.resourceId) condition.resourceId = node.resourceId;
      if (node.text) condition.text = node.text;
      break;
    case "text":
      if (node.text) condition.text = node.text;
      break;
    case "contentDesc":
      if (node.contentDescription) condition.contentDescription = node.contentDescription;
      break;
    case "class":
      if (node.className) condition.className = node.className;
      break;
    case "bounds":
      if (node.bounds) condition.bounds = `[${node.bounds.left},${node.bounds.top}][${node.bounds.right},${node.bounds.bottom}]`;
      break;
    default:
      break;
  }
  if (!Object.keys(condition).length) {
    return null;
  }
  return condition;
}

function addConditionToGroup(groupKey, strategy) {
  const node = state.dump.selectedNode;
  if (!node) {
    alert("请先选择节点");
    return;
  }
  const condition = createConditionFromNode(node, strategy);
  if (!condition) {
    alert("当前策略无法生成条件，请尝试其他策略");
    return;
  }
  state.fingerprint[groupKey].push(condition);
  renderFingerprintGroups();
  refreshYamlPreview();
  updateFingerprintStatus(null, "");
}

function addConditionObjectToGroup(groupKey, condition) {
  if (!condition || !Object.keys(condition).length) {
    alert("无法生成条件，请检查节点属性");
    return;
  }
  if (!state.fingerprint[groupKey]) {
    alert("不支持的约束类型");
    return;
  }
  state.fingerprint[groupKey].push(condition);
  renderFingerprintGroups();
  refreshYamlPreview();
  updateFingerprintStatus(null, "");
}

function renderAttributeTable(node) {
  if (!dumpAttributeTable) return;
  dumpAttributeTable.innerHTML = "";
  const actionsContainer = document.getElementById("dumpActionContainer");
  if (actionsContainer) {
    actionsContainer.innerHTML = "";
  }
  if (!node) {
    dumpAttributeTable.innerHTML = "<p class='empty'>选择节点以查看属性</p>";
    return;
  }
  const actions = document.getElementById("dumpActionContainer");
  if (actions) {
    actions.className = "attribute-actions";

    const groupWrapper = document.createElement("div");
    groupWrapper.className = "attribute-actions__group";
    const groupLabel = document.createElement("label");
    groupLabel.textContent = "目标约束";
    const groupSelect = document.createElement("select");
    groupSelect.innerHTML = `
      <option value="required_all">必须全部满足</option>
      <option value="required_any">满足任意一个</option>
      <option value="forbidden_any">禁止出现任意一个</option>
      <option value="forbidden_all">禁止同时出现全部</option>
    `;
    groupWrapper.appendChild(groupLabel);
    groupWrapper.appendChild(groupSelect);
    actions.appendChild(groupWrapper);

    const combos = [];
    if (node.resourceId) combos.push({ keys: ["resourceId"], label: "resourceId" });
    if (node.text) combos.push({ keys: ["text"], label: "text" });
    if (node.contentDescription) combos.push({ keys: ["contentDescription"], label: "content-desc" });
    if (node.resourceId && node.text) combos.push({ keys: ["resourceId", "text"], label: "resourceId + text" });
    if (node.resourceId && node.contentDescription) combos.push({ keys: ["resourceId", "contentDescription"], label: "resourceId + content-desc" });
    if (node.text && node.contentDescription) combos.push({ keys: ["text", "contentDescription"], label: "text + content-desc" });
    if (node.resourceId && node.text && node.contentDescription) combos.push({ keys: ["resourceId", "text", "contentDescription"], label: "resourceId + text + content-desc" });

    const createConditionByKeys = (keys) => {
      const condition = {};
      keys.forEach((key) => {
        switch (key) {
          case "resourceId":
            condition.resourceId = node.resourceId;
            break;
          case "text":
            condition.text = node.text;
            break;
          case "contentDescription":
            condition.contentDescription = node.contentDescription;
            break;
          default:
            break;
        }
      });
      return condition;
    };

    combos.forEach((combo) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "btn btn--ghost btn--xs";
      button.textContent = combo.label;
      button.addEventListener("click", () => {
        const selectedGroup = groupSelect.value;
        const condition = createConditionByKeys(combo.keys);
        addConditionObjectToGroup(selectedGroup, condition);
      });
      actions.appendChild(button);
    });

    if (!combos.length) {
      const tip = document.createElement("span");
      tip.className = "attribute-actions__empty";
      tip.textContent = "该节点暂无可用的资源 ID / 文本 / 描述";
      actions.appendChild(tip);
    }
  }

  const table = document.createElement("table");
  Object.entries(node.rawAttributes).forEach(([key, value]) => {
    const row = document.createElement("tr");
    const keyCell = document.createElement("td");
    keyCell.textContent = key;
    const valueCell = document.createElement("td");
    valueCell.textContent = value;
    row.appendChild(keyCell);
    row.appendChild(valueCell);
    table.appendChild(row);
  });
  dumpAttributeTable.appendChild(table);
}

function domNodeToDumpNode(xmlNode, element, depth = 0) {
  const nodeId = `node-${state.dump.nodes.length + 1}`;
  const attrs = {};
  Array.from(xmlNode.attributes).forEach((attr) => {
    attrs[attr.name] = attr.value;
  });
  const bounds = parseBounds(attrs.bounds);

  const data = {
    id: nodeId,
    element,
    depth,
    text: attrs.text || "",
    contentDescription: attrs["content-desc"] || "",
    resourceId: attrs["resource-id"] || "",
    className: attrs.class || "",
    packageName: attrs.package || "",
    clickable: attrs.clickable === "true",
    enabled: attrs.enabled === "true",
    selected: attrs.selected === "true",
    checkable: attrs.checkable === "true",
    checked: attrs.checked === "true",
    focusable: attrs.focusable === "true",
    focused: attrs.focused === "true",
    scrollable: attrs.scrollable === "true",
    longClickable: attrs["long-clickable"] === "true",
    bounds,
    rawAttributes: attrs,
  };
  state.dump.nodes.push(data);
  state.dump.treeIndex.set(nodeId, data);
  return data;
}

function buildDumpTree(xmlString) {
  state.dump.nodes = [];
  state.dump.treeIndex = new Map();
  const parser = new DOMParser();
  const doc = parser.parseFromString(xmlString, "application/xml");
  const parseError = doc.querySelector("parsererror");
  if (parseError) {
    throw new Error(parseError.textContent || "XML 解析失败");
  }
  const root = doc.documentElement;

  const createDetails = (node, depth) => {
    const details = document.createElement("details");
    details.dataset.depth = depth;
    const summary = document.createElement("summary");
    const attrs = Array.from(node.attributes)
      .map((attr) => `<span class="dump-attr"><span class="dump-attr-key">${attr.name}</span>="${attr.value}"</span>`)
      .join(" ");
    summary.innerHTML = `<span class="dump-tag">&lt;${node.tagName}&gt;</span> ${attrs}`;
    details.appendChild(summary);

    const data = domNodeToDumpNode(node, details, depth);

    data.element.dataset.nodeId = data.id;

    summary.addEventListener("click", (event) => {
      event.preventDefault();
      const shouldOpen = !details.open;
      details.open = shouldOpen;
      selectDumpNode(data.id);
    });

    Array.from(node.children).forEach((child) => {
      details.appendChild(createDetails(child, depth + 1));
    });
    details.open = true;
    return details;
  };

  const treeRoot = createDetails(root, 0);
  treeRoot.classList.add("tree-root");
  dumpTreePane.innerHTML = "";
  dumpTreePane.appendChild(treeRoot);
  selectDumpNode(state.dump.nodes[0]?.id || null);
}

function renderDumpSearch(keyword) {
  const term = (keyword || "").trim().toLowerCase();
  const details = Array.from(dumpTreePane.querySelectorAll("details"));
  let firstMatchId = null;
  details.forEach((detail) => {
    detail.style.display = "";
    if (term) {
      const summaryText = detail.querySelector("summary")?.textContent?.toLowerCase() || "";
      if (summaryText.includes(term)) {
        if (!firstMatchId && detail.dataset.nodeId) {
          firstMatchId = detail.dataset.nodeId;
        }
        detail.open = true;
      }
    } else {
      detail.open = true;
    }
  });
  if (term) {
    if (firstMatchId) {
      selectDumpNode(firstMatchId, true);
    } else {
      logLine("未找到匹配的节点", "info");
    }
  }
}

function parseSnapshotNode(node) {
  const attrs = node.rawAttributes;
  return {
    resourceId: attrs["resource-id"] || null,
    resourceIdMatches: null,
    text: attrs.text || null,
    textContains: null,
    textStartsWith: null,
    contentDescription: attrs["content-desc"] || null,
    contentDescriptionContains: null,
    contentDescriptionStartsWith: null,
    className: attrs.class || null,
    packageName: attrs.package || null,
    clickable: attrs.clickable === "true" ? true : attrs.clickable === "false" ? false : undefined,
    enabled: attrs.enabled === "true" ? true : attrs.enabled === "false" ? false : undefined,
    selected: attrs.selected === "true" ? true : attrs.selected === "false" ? false : undefined,
    checkable: attrs.checkable === "true" ? true : attrs.checkable === "false" ? false : undefined,
    checked: attrs.checked === "true" ? true : attrs.checked === "false" ? false : undefined,
    focusable: attrs.focusable === "true" ? true : attrs.focusable === "false" ? false : undefined,
    focused: attrs.focused === "true" ? true : attrs.focused === "false" ? false : undefined,
    scrollable: attrs.scrollable === "true" ? true : attrs.scrollable === "false" ? false : undefined,
    longClickable: attrs["long-clickable"] === "true" ? true : attrs["long-clickable"] === "false" ? false : undefined,
    bounds: attrs.bounds || null,
  };
}

function nodeMatchesCondition(snapshotNode, condition) {
  const compareText = (actual, expected) => actual === expected;
  const compareBool = (actual, expected) => actual === expected;

  if (condition.resourceId && !compareText(snapshotNode.resourceId, condition.resourceId)) return false;
  if (condition.resourceIdMatches) {
    try {
      const regex = new RegExp(condition.resourceIdMatches);
      if (!regex.test(snapshotNode.resourceId || "")) return false;
    } catch (error) {
      return false;
    }
  }
  if (condition.text && !compareText(snapshotNode.text, condition.text)) return false;
  if (condition.textContains && !(snapshotNode.text || "").includes(condition.textContains)) return false;
  if (condition.textStartsWith && !(snapshotNode.text || "").startsWith(condition.textStartsWith)) return false;
  if (condition.contentDescription && !compareText(snapshotNode.contentDescription, condition.contentDescription)) return false;
  if (condition.contentDescriptionContains && !(snapshotNode.contentDescription || "").includes(condition.contentDescriptionContains)) return false;
  if (condition.contentDescriptionStartsWith && !(snapshotNode.contentDescription || "").startsWith(condition.contentDescriptionStartsWith)) return false;
  if (condition.className && !compareText(snapshotNode.className, condition.className)) return false;
  if (condition.packageName && !compareText(snapshotNode.packageName, condition.packageName)) return false;
  if (typeof condition.clickable === "boolean" && !compareBool(snapshotNode.clickable, condition.clickable)) return false;
  if (typeof condition.enabled === "boolean" && !compareBool(snapshotNode.enabled, condition.enabled)) return false;
  if (typeof condition.selected === "boolean" && !compareBool(snapshotNode.selected, condition.selected)) return false;
  if (typeof condition.checkable === "boolean" && !compareBool(snapshotNode.checkable, condition.checkable)) return false;
  if (typeof condition.checked === "boolean" && !compareBool(snapshotNode.checked, condition.checked)) return false;
  if (typeof condition.focusable === "boolean" && !compareBool(snapshotNode.focusable, condition.focusable)) return false;
  if (typeof condition.focused === "boolean" && !compareBool(snapshotNode.focused, condition.focused)) return false;
  if (typeof condition.scrollable === "boolean" && !compareBool(snapshotNode.scrollable, condition.scrollable)) return false;
  if (typeof condition.longClickable === "boolean" && !compareBool(snapshotNode.longClickable, condition.longClickable)) return false;
  if (condition.bounds && snapshotNode.bounds !== condition.bounds) return false;
  return true;
}

function matchFingerprint() {
  if (!state.dump.nodes.length) {
    updateFingerprintStatus("failed", "请先获取最新界面 Dump");
    return;
  }
  const snapshotNodes = state.dump.nodes.map((node) => parseSnapshotNode(node));
  const sig = state.fingerprint;
  const result = {
    matchedNodes: [],
    missingAll: [],
    missingAny: [],
    forbiddenHit: [],
  };

  const exists = (condition) => snapshotNodes.some((node) => nodeMatchesCondition(node, condition));

  if (sig.forbidden_any.length) {
    const hit = sig.forbidden_any.filter((condition) => exists(condition));
    if (hit.length) {
      result.forbiddenHit = hit;
      updateFingerprintStatus("failed", `命中 forbidden_any 条件 ${hit.length} 个`);
      return result;
    }
  }

  if (sig.forbidden_all.length) {
    const allPresent = sig.forbidden_all.every((condition) => exists(condition));
    if (allPresent) {
      updateFingerprintStatus("failed", "forbidden_all 条件全部命中");
      return result;
    }
  }

  if (sig.required_all.length) {
    const missing = sig.required_all.filter((condition) => !exists(condition));
    if (missing.length) {
      result.missingAll = missing;
      updateFingerprintStatus("failed", `缺少 required_all 条件 ${missing.length} 个`);
      return result;
    }
  }

  if (sig.required_any.length) {
    const anyMatched = sig.required_any.some((condition) => exists(condition));
    if (!anyMatched) {
      result.missingAny = sig.required_any;
      updateFingerprintStatus("failed", "required_any 条件均未命中");
      return result;
    }
  }

  updateFingerprintStatus("matched", "所有条件满足");
  return result;
}

function handleScreenshotResult(result) {
  try {
    const base64 = result.image;
    const binaryString = atob(base64);
    const bytes = new Uint8Array(binaryString.length);
    for (let i = 0; i < binaryString.length; i += 1) {
      bytes[i] = binaryString.charCodeAt(i);
    }
    let imageData = bytes;
    if (result.gzipped) {
      imageData = pako.ungzip(bytes);
    }
    if (state.screenshot.blobUrl) {
      URL.revokeObjectURL(state.screenshot.blobUrl);
    }
    const blob = new Blob([imageData], { type: `image/${result.format || "jpeg"}` });
    const url = URL.createObjectURL(blob);
    state.screenshot.blobUrl = url;
    screenshotImg.src = url;
    screenshotImg.onload = () => {
      state.screenshot.width = result.metadata?.width || screenshotImg.naturalWidth;
      state.screenshot.height = result.metadata?.height || screenshotImg.naturalHeight;
      highlightBounds(null);
    };
    state.screenshot.capturedAt = new Date().toLocaleTimeString("zh-CN");
    screenshotMetaEl.textContent = `更新于 ${state.screenshot.capturedAt} · ${Math.round((result.original_size || 0) / 1024)} KB`;
    logLine("截图已更新", "success");
  } catch (error) {
    logLine(`处理截图失败: ${error.message}`, "error");
  }
}

function handleDumpResult(result) {
  try {
    const base64 = result.data;
    const binaryString = atob(base64);
    const bytes = new Uint8Array(binaryString.length);
    for (let i = 0; i < binaryString.length; i += 1) {
      bytes[i] = binaryString.charCodeAt(i);
    }
    const xmlString = result.compressed ? pako.ungzip(bytes, { to: "string" }) : new TextDecoder().decode(bytes);
    state.dump.xml = xmlString;
    state.dump.capturedAt = new Date().toLocaleTimeString("zh-CN");
    buildDumpTree(xmlString);
    dumpSummary.textContent = `节点总数: ${state.dump.nodes.length} · 更新时间 ${state.dump.capturedAt}`;
    logLine("Dump 已更新", "success");
  } catch (error) {
    logLine(`处理 Dump 失败: ${error.message}`, "error");
  }
}

function processCommandResult(message) {
  const { status, command_id: commandId, result, error_message: errorMessage, action } = message;
  const pendingAction = state.pendingCommands.get(commandId) || action;
  if (commandId) {
    state.pendingCommands.delete(commandId);
  }
  state.isCommandRunning = false;
  state.currentAction = null;
  if (status === "failed") {
    logLine(`指令失败：${pendingAction} · ${errorMessage || "未知错误"}`, "error");
    setCommandStatus(`指令失败：${pendingAction} · ${errorMessage || "未知错误"}`, "error");
    if (pendingAction === "screenshot") {
      state.chainDumpAfterScreenshot = false;
    }
    scheduleIdleStatus();
    return;
  }
  logLine(`指令成功：${pendingAction}`, "success");
  setCommandStatus(`设备已确认：${pendingAction}`, "success");
  scheduleIdleStatus();
  if (result) {
    try {
      const parsed = JSON.parse(result);
      if (pendingAction === "screenshot") {
        handleScreenshotResult(parsed);
      } else if (pendingAction === "dump_hierarchy") {
        handleDumpResult(parsed);
      } else {
        logLine(JSON.stringify(parsed), "info");
      }
    } catch (error) {
      logLine(result, "info");
    }
  }
  if (autoRefreshSnapshot.checked && pendingAction !== "screenshot" && pendingAction !== "dump_hierarchy") {
    requestScreenshot(true);
  }
  if (pendingAction === "screenshot") {
    if (state.chainDumpAfterScreenshot) {
      state.chainDumpAfterScreenshot = false;
      requestDump();
    }
  } else if (pendingAction === "dump_hierarchy") {
    state.chainDumpAfterScreenshot = false;
  }
}

async function requestScreenshot(chainDump = false) {
  if (!state.currentDevice) return;
  if (state.isCommandRunning) {
    logLine("当前有指令执行中，请稍后再刷新界面", "info");
    if (chainDump) {
      state.chainDumpAfterScreenshot = true;
    }
    return;
  }
  const capability = state.capabilities.find((cap) => cap.action === "screenshot");
  if (!capability) {
    logLine("设备未上报 screenshot 能力", "error");
    return;
  }
  if (chainDump) {
    state.chainDumpAfterScreenshot = true;
  }
  await executeCapability(capability);
}

async function requestDump() {
  if (!state.currentDevice) return;
  if (state.isCommandRunning) {
    logLine("当前有指令执行中，请稍后再获取 Dump", "info");
    return;
  }
  const capability = state.capabilities.find((cap) => cap.action === "dump_hierarchy");
  if (!capability) {
    logLine("设备未上报 dump_hierarchy 能力", "error");
    return;
  }
  await executeCapability(capability);
}

async function fetchDeviceCapabilities() {
  if (!state.currentDevice) return;
  try {
    const data = await fetchJson(`/api/admin/devices/${state.currentDevice.id}/capabilities`);
    state.capabilities = data.capabilities || [];
    renderCapabilities();
    renderConsoleTabs();
  } catch (error) {
    logLine(`获取能力失败: ${error.message}`, "error");
  }
}

async function fetchDevice(deviceId) {
  try {
    const device = await fetchJson(`/api/admin/devices/${deviceId}`);
    state.currentDevice = device;
    applyDeviceMeta(device);
    await fetchDeviceCapabilities();
    await requestScreenshot(true);
  } catch (error) {
    logLine(`获取设备失败: ${error.message}`, "error");
  }
}

async function fetchDevices(username) {
  try {
    const data = await fetchJson(`/api/admin/devices?username=${encodeURIComponent(username)}`);
    const devices = data.devices || [];
    state.devices = devices;
    const deviceSelect = document.getElementById("labDeviceSelect");
    deviceSelect.innerHTML = '<option value="">-- 请选择设备 --</option>';
    devices.forEach((device) => {
      const option = document.createElement("option");
      option.value = device.id;
      option.textContent = `${device.device_name || device.device_model || device.id}${device.is_online ? "" : " · 离线"}`;
      deviceSelect.appendChild(option);
    });
  } catch (error) {
    logLine(`获取设备列表失败: ${error.message}`, "error");
  }
}

async function fetchUsers() {
  try {
    const users = await fetchJson("/api/admin/users");
    state.users = users;
    const userSelect = document.getElementById("labUserSelect");
    userSelect.innerHTML = '<option value="">-- 请选择用户 --</option>';
    users.forEach((username) => {
      const option = document.createElement("option");
      option.value = username;
      option.textContent = username;
      userSelect.appendChild(option);
    });
  } catch (error) {
    logLine(`获取用户列表失败: ${error.message}`, "error");
  }
}

async function checkAuth() {
  try {
    const admin = await fetchJson("/api/admin/me");
    authRole = admin.role;
    localStorage.setItem("auth_role", admin.role);
    if (admin.username) {
      localStorage.setItem("auth_username", admin.username);
    }
  } catch (error) {
    authToken = null;
    localStorage.removeItem("auth_token");
    localStorage.removeItem("auth_role");
    localStorage.removeItem("auth_username");
    window.location.href = "/login";
  }
}

function connectWebSocket() {
  if (!authToken) return;
  if (state.ws) {
    state.ws.close();
  }
  const url = new URL(`${window.location.origin.replace(/^http/, "ws")}/ws/web`);
  url.searchParams.set("token", authToken);
  state.ws = new WebSocket(url.toString());
  state.ws.addEventListener("open", () => {
    logLine("WebSocket 已连接", "success");
  });
  state.ws.addEventListener("message", (event) => {
    try {
      const message = JSON.parse(event.data);
      if (message.type === "command_result") {
        processCommandResult(message.data || {});
      } else if (message.type === "command_progress") {
        const { stage, message: msg, percent } = message.data || {};
        const text = percent != null ? `${msg || stage} (${percent}%)` : (msg || stage || "执行中");
        logLine(`[进度] ${text}`, "info");
      }
    } catch (error) {
      console.error("处理 WebSocket 消息失败", error);
    }
  });
  state.ws.addEventListener("close", () => {
    logLine("WebSocket 已断开，将在 5 秒后重连", "error");
    setTimeout(connectWebSocket, 5000);
  });
}

function attachEventListeners() {
  sceneIdInput.addEventListener("input", updateFingerprintMetaFromInputs);
  sceneHandlerInput.addEventListener("input", updateFingerprintMetaFromInputs);
  sceneDescriptionInput.addEventListener("input", updateFingerprintMetaFromInputs);
  dumpSearchInput.addEventListener("input", (event) => renderDumpSearch(event.target.value));

  document.getElementById("resetFingerprintBtn").addEventListener("click", resetFingerprint);
  document.getElementById("clearHandlerPlanBtn").addEventListener("click", () => {
    state.handlerPlan = [];
    renderHandlerPlan();
  });
  document.getElementById("runHandlerPlanBtn").addEventListener("click", () => runHandlerPlanSequential());
  document.getElementById("runStartTaskBtn").addEventListener("click", () => {
    const capability = state.capabilities.find((cap) => cap.action === "start_task");
    if (!capability) {
      logLine("设备未上报 start_task 能力", "error");
      return;
    }
    selectCapability(capability);
    const params = parseFormParams(capability);
    if (!params.task_name) {
      params.task_name = state.fingerprint.sceneId;
    }
    executeCapability(capability, params);
  });

  toolbarRefreshDevices.addEventListener("click", () => {
    fetchUsers();
    if (state.currentUser) {
      fetchDevices(state.currentUser);
    }
  });
  toolbarRefreshCapabilities.addEventListener("click", fetchDeviceCapabilities);
  toolbarRefreshUi.addEventListener("click", () => requestScreenshot(true));
  toolbarValidate.addEventListener("click", () => matchFingerprint());
  toolbarCopyYaml.addEventListener("click", copyYamlToClipboard);
  toolbarCopyHandler.addEventListener("click", copyHandlerTemplate);
  toolbarLogout.addEventListener("click", () => {
    if (state.ws) state.ws.close();
    authToken = null;
    authRole = null;
    localStorage.clear();
    window.location.href = "/login";
  });

screenshotImg.addEventListener("click", (event) => {
  const rect = screenshotImg.getBoundingClientRect();
  const xRatio = state.screenshot.width / rect.width;
  const yRatio = state.screenshot.height / rect.height;
  const x = Math.round((event.clientX - rect.left) * xRatio);
  const y = Math.round((event.clientY - rect.top) * yRatio);
  state.lastTapPosition = { x, y };
  logLine(`已拾取坐标 (${x}, ${y})`, "info");
  const candidates = state.dump.nodes.filter((node) => {
    if (!node.bounds) return false;
    return x >= node.bounds.left && x <= node.bounds.right && y >= node.bounds.top && y <= node.bounds.bottom;
  });
  if (candidates.length) {
    candidates.sort((a, b) => {
      const areaA = (a.bounds.right - a.bounds.left) * (a.bounds.bottom - a.bounds.top);
      const areaB = (b.bounds.right - b.bounds.left) * (b.bounds.bottom - b.bounds.top);
      if (areaA !== areaB) {
        return areaA - areaB; // smaller area first
      }
      return b.depth - a.depth; // deeper node first when area equal
    });
    selectDumpNode(candidates[0].id, true);
  }
});

  document.getElementById("labUserSelect").addEventListener("change", (event) => {
    const value = event.target.value;
    state.currentUser = value || null;
    state.currentDevice = null;
    const deviceSelect = document.getElementById("labDeviceSelect");
    deviceSelect.innerHTML = '<option value="">-- 请选择设备 --</option>';
    if (value) {
      fetchDevices(value);
    }
  });

  document.getElementById("labDeviceSelect").addEventListener("change", (event) => {
    const value = event.target.value;
    if (value) {
      fetchDevice(value);
    } else {
      state.currentDevice = null;
      applyDeviceMeta(null);
    }
  });

  const collapseBtn = document.getElementById("capabilityCollapseBtn");
  collapseBtn.addEventListener("click", () => {
    capabilityGroupsEl.classList.toggle("collapsed");
    collapseBtn.textContent = capabilityGroupsEl.classList.contains("collapsed") ? "展开全部" : "折叠全部";
  });
}

function fillParamsFromSelectedNode(capability) {
  if (!state.dump.selectedNode) {
    alert("请先选择节点");
    return;
  }
  const node = state.dump.selectedNode;
  const inputs = consoleFormArea.querySelectorAll("[data-param-name]");
  inputs.forEach((input) => {
    const name = input.dataset.paramName;
    if (/x$/i.test(name)) {
      const x = node.bounds ? Math.round((node.bounds.left + node.bounds.right) / 2) : state.lastTapPosition?.x || 0;
      if (input.type === "checkbox") {
        input.checked = Boolean(x);
      } else {
        input.value = x;
      }
    } else if (/y$/i.test(name)) {
      const y = node.bounds ? Math.round((node.bounds.top + node.bounds.bottom) / 2) : state.lastTapPosition?.y || 0;
      if (input.type === "checkbox") {
        input.checked = Boolean(y);
      } else {
        input.value = y;
      }
    } else if (name.includes("resource") && node.resourceId) {
      input.value = node.resourceId;
    } else if (name.includes("text") && node.text) {
      input.value = node.text;
    }
  });
}

async function bootstrap() {
  ensureAuth();
  await checkAuth();
  await fetchUsers();
  connectWebSocket();
  resetFingerprint();
  renderHandlerPlan();
  setCommandStatus("暂无指令执行");
  attachEventListeners();
}

window.addEventListener("load", bootstrap);
