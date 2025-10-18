// 客户端控制台：脚本中心 + 模板管理 + 设备概览

const customerState = {
  token: localStorage.getItem("auth_token"),
  role: localStorage.getItem("auth_role"),
  username: localStorage.getItem("auth_username"),
  scripts: [],
  selectedScript: null,
  templates: [],
  currentFormParameters: [],
  editingTemplate: null,
};

const customerElements = {
  info: document.getElementById("customerInfo"),
  logoutBtn: document.getElementById("customerLogoutBtn"),
  tabs: document.querySelectorAll(".tabs .tab"),
  tabContents: {
    scripts: document.getElementById("scriptsContent"),
    templates: document.getElementById("templatesContent"),
    devices: document.getElementById("devicesContent"),
  },
  scriptList: document.getElementById("scriptList"),
  scriptDetail: document.getElementById("scriptDetail"),
  refreshScriptsBtn: document.getElementById("refreshScriptsBtn"),
  templateTable: document.getElementById("templateTable"),
  refreshTemplatesBtn: document.getElementById("refreshTemplatesBtn"),
  deviceTable: document.getElementById("customerDeviceTable"),
  templateFormModal: document.getElementById("templateFormModal"),
  templateForm: document.getElementById("templateForm"),
  templateFormTitle: document.getElementById("templateFormTitle"),
  templateFormMode: document.getElementById("templateFormMode"),
  templateFormMessage: document.getElementById("templateFormMessage"),
  templateFormSubmitBtn: document.getElementById("templateFormSubmitBtn"),
  templateScriptName: document.getElementById("templateScriptName"),
  templateScriptVersion: document.getElementById("templateScriptVersion"),
  templateTitle: document.getElementById("templateTitle"),
  templateNotes: document.getElementById("templateNotes"),
  templateParameters: document.getElementById("templateParameters"),
  templateInfoModal: document.getElementById("templateInfoModal"),
  templateInfoTitle: document.getElementById("templateInfoTitle"),
  templateInfoBody: document.getElementById("templateInfoBody"),
  templateModalCloseButtons: document.querySelectorAll("[data-close-modal]"),
};

localStorage.removeItem("customer_token");

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function handleLogout() {
  localStorage.removeItem("auth_token");
  localStorage.removeItem("auth_role");
  localStorage.removeItem("auth_username");
  localStorage.removeItem("customer_token");
  localStorage.removeItem("customer_username");
  window.location.href = "/login";
}

async function requestJson(url, options = {}) {
  if (!customerState.token) {
    handleLogout();
    throw new Error("认证失效");
  }
  const headers = options.headers ? { ...options.headers } : {};
  headers.Authorization = `Bearer ${customerState.token}`;
  let body = options.body;
  if (body && !(body instanceof FormData)) {
    headers["Content-Type"] = "application/json";
    body = typeof body === "string" ? body : JSON.stringify(body);
  }
  const response = await fetch(url, { ...options, headers, body });
  if (response.status === 401 || response.status === 403) {
    handleLogout();
    throw new Error("认证失败");
  }
  if (!response.ok) {
    const payload = await response.json().catch(() => ({}));
    const message = payload.detail || payload.message || "请求失败";
    throw new Error(message);
  }
  if (response.status === 204) {
    return {};
  }
  const isJson = response.headers.get("content-type")?.includes("application/json");
  return isJson ? response.json() : {};
}

function switchTab(target) {
  customerElements.tabs.forEach((tab) => {
    tab.classList.toggle("active", tab.dataset.tab === target);
  });
  Object.entries(customerElements.tabContents).forEach(([key, el]) => {
    if (el) {
      el.classList.toggle("active", key === target);
    }
  });
}

async function loadProfile() {
  const profile = await requestJson("/api/customer/me");
  customerState.username = profile.username;
  localStorage.setItem("auth_username", profile.username);
  if (profile.role) {
    localStorage.setItem("auth_role", profile.role);
    customerState.role = profile.role;
  }
  if (customerElements.info) {
    customerElements.info.textContent = `欢迎, ${profile.username}`;
  }
}

async function loadScripts() {
  if (!customerElements.scriptList) return;
  customerElements.scriptList.innerHTML = '<div class="loading">加载中...</div>';
  try {
    const data = await requestJson("/api/customer/templates/scripts");
    customerState.scripts = data.scripts || [];
    renderScriptList();
  } catch (error) {
    console.error("加载脚本失败", error);
    customerElements.scriptList.innerHTML = `<div class="empty-state">${error.message || "脚本加载失败"}</div>`;
  }
}

function renderScriptList() {
  if (!customerElements.scriptList) return;
  const scripts = customerState.scripts;
  if (!scripts.length) {
    customerElements.scriptList.innerHTML = '<div class="empty-state">暂无在线设备上报脚本能力</div>';
    customerState.selectedScript = null;
    renderScriptDetail(null);
    return;
  }
  const items = scripts
    .map((script) => {
      const scriptKey = encodeURIComponent(script.script_name);
      const isActive = customerState.selectedScript && customerState.selectedScript.script_name === script.script_name;
      const devices = script.source_devices?.length || 0;
      const version = script.version ? `版本 ${script.version}` : "版本未提供";
      const title = escapeHtml(script.script_title || script.script_name);
      return `
        <div class="script-item ${isActive ? "active" : ""}" data-script="${scriptKey}">
            <h3>${title}</h3>
            <div class="script-meta">
                <span>${version}</span>
                <span>参数 ${script.parameters.length}</span>
                <span>在线设备 ${devices}</span>
            </div>
        </div>
      `;
    })
    .join("");
  customerElements.scriptList.innerHTML = items;
  if (!customerState.selectedScript && scripts.length) {
    setSelectedScript(scripts[0]);
  }
}

function setSelectedScript(script) {
  customerState.selectedScript = script;
  const items = customerElements.scriptList?.querySelectorAll(".script-item") || [];
  items.forEach((item) => {
    const key = item.dataset.script;
    const name = key ? decodeURIComponent(key) : "";
    item.classList.toggle("active", script && name === script.script_name);
  });
  renderScriptDetail(script);
}

function renderScriptDetail(script) {
  if (!customerElements.scriptDetail) return;
  if (!script) {
    customerElements.scriptDetail.innerHTML = '<div class="empty-state">请选择左侧脚本以查看详情</div>';
    return;
  }
  const description = escapeHtml(script.description || "该脚本暂无描述");
  const parameters = script.parameters || [];
  const parameterRows = parameters.length
    ? parameters
        .map((param) => `
            <tr>
                <td>${escapeHtml(param.name)}${param.required ? " <span class=\\"badge badge-warning\\">必填</span>" : ""}</td>
                <td>${escapeHtml(param.type || "string")}</td>
                <td>${param.default !== undefined && param.default !== null ? escapeHtml(formatDisplayValue(param.default)) : "-"}</td>
                <td>${param.description ? escapeHtml(param.description) : "-"}</td>
            </tr>
          `)
        .join("")
    : '<tr><td colspan="4">脚本未声明参数</td></tr>';

  customerElements.scriptDetail.innerHTML = `
    <div class="script-detail-header">
        <div>
            <h2>${escapeHtml(script.script_title || script.script_name)}</h2>
            <p class="muted">${description}</p>
        </div>
        <div class="template-actions">
            <button class="btn btn--primary" id="createTemplateBtn">基于此脚本创建模板</button>
        </div>
    </div>
    <div class="script-detail-body">
        <div class="muted">版本：${escapeHtml(script.version || "未提供")} · 支持设备：${script.source_devices?.length || 0}</div>
        <div class="table-wrapper">
            <table class="parameter-table">
                <thead>
                    <tr>
                        <th>参数</th>
                        <th>类型</th>
                        <th>默认值</th>
                        <th>说明</th>
                    </tr>
                </thead>
                <tbody>${parameterRows}</tbody>
            </table>
        </div>
    </div>
  `;
  const createBtn = document.getElementById("createTemplateBtn");
  if (createBtn) {
    createBtn.addEventListener("click", () => openTemplateForm("create", script));
  }
}

async function loadTemplates() {
  if (!customerElements.templateTable) return;
  customerElements.templateTable.innerHTML = '<div class="loading">加载中...</div>';
  try {
    const data = await requestJson("/api/customer/templates");
    customerState.templates = data.templates || [];
    renderTemplateTable();
  } catch (error) {
    console.error("加载模板失败", error);
    customerElements.templateTable.innerHTML = `<div class="empty-state">${error.message || "模板加载失败"}</div>`;
  }
}

function renderTemplateTable() {
  if (!customerElements.templateTable) return;
  const templates = customerState.templates;
  if (!templates.length) {
    customerElements.templateTable.innerHTML = '<div class="empty-state">尚未创建任何模板，前往“脚本中心”选择脚本并创建。</div>';
    return;
  }
  const rows = templates
    .map((template) => {
      const updatedAt = template.updated_at ? new Date(template.updated_at).toLocaleString("zh-CN") : "-";
      const statusBadge = buildCompatibilityBadge(template.compatibility);
      return `
        <tr data-template-id="${escapeHtml(template.id)}">
            <td>${escapeHtml(template.script_title || template.script_name)}</td>
            <td>${escapeHtml(template.script_name)}</td>
            <td>${escapeHtml(template.script_version || "-" )}</td>
            <td>${statusBadge}</td>
            <td>${updatedAt}</td>
            <td class="text-right">
                <div class="template-actions">
                    <button class="btn btn--ghost btn--sm" data-action="view">详情</button>
                    <button class="btn btn--outline btn--sm" data-action="edit">编辑</button>
                    <button class="btn btn--danger btn--sm" data-action="delete">删除</button>
                </div>
            </td>
        </tr>
      `;
    })
    .join("");

  customerElements.templateTable.innerHTML = `
    <table>
        <thead>
            <tr>
                <th>模板名称</th>
                <th>脚本标识</th>
                <th>脚本版本</th>
                <th>兼容状态</th>
                <th>更新时间</th>
                <th class="text-right">操作</th>
            </tr>
        </thead>
        <tbody>${rows}</tbody>
    </table>
  `;
}

function buildCompatibilityBadge(status) {
  switch (status) {
    case "active":
      return '<span class="badge badge-success">正常</span>';
    case "stale":
      return '<span class="badge badge-warning">待更新</span>';
    case "unavailable":
      return '<span class="badge badge-danger">不可用</span>';
    default:
      return `<span class="badge">${status || "未知"}</span>`;
  }
}

async function loadDevices() {
  if (!customerElements.deviceTable) return;
  customerElements.deviceTable.innerHTML = '<div class="loading">加载中...</div>';
  try {
    const data = await requestJson("/api/customer/devices");
    renderDevices(data.devices || []);
  } catch (error) {
    console.error("加载设备失败", error);
    customerElements.deviceTable.innerHTML = `<div class="empty-state">${error.message || "设备加载失败"}</div>`;
  }
}

function renderDevices(devices) {
  if (!customerElements.deviceTable) return;
  if (!devices.length) {
    customerElements.deviceTable.innerHTML = '<div class="empty-state">暂无绑定设备，如需添加请联系管理员</div>';
    return;
  }
  const rows = devices
    .map((device) => {
      const onlineClass = device.is_online ? "status-online" : "status-offline";
      const onlineText = device.is_online ? "在线" : "离线";
      const createdAt = device.created_at ? new Date(device.created_at).toLocaleString("zh-CN") : "-";
      return `
        <tr>
            <td>${escapeHtml(device.device_name || "-")}</td>
            <td>${escapeHtml(device.device_model || "-")}</td>
            <td>${escapeHtml(device.android_version || "-")}</td>
            <td>${escapeHtml(device.local_ip || "-")}</td>
            <td>${escapeHtml(device.public_ip || "-")}</td>
            <td><span class="status-badge ${onlineClass}">${onlineText}</span></td>
            <td>${createdAt}</td>
        </tr>
      `;
    })
    .join("");
  customerElements.deviceTable.innerHTML = `
    <table>
        <thead>
            <tr>
                <th>设备名称</th>
                <th>设备型号</th>
                <th>Android版本</th>
                <th>内网IP</th>
                <th>外网IP</th>
                <th>状态</th>
                <th>创建时间</th>
            </tr>
        </thead>
        <tbody>${rows}</tbody>
    </table>
  `;
}

function openTemplateForm(mode, script, template = null) {
  if (!customerElements.templateFormModal) return;
  customerElements.templateFormMode.value = mode;
  customerState.currentFormParameters = script.parameters || [];
  customerState.editingTemplate = template;
  customerElements.templateFormMessage.textContent = "";
  customerElements.templateFormSubmitBtn.disabled = false;
  customerElements.templateScriptName.value = script.script_name;
  customerElements.templateScriptVersion.value = script.version || "-";
  customerElements.templateTitle.value = template?.script_title || script.script_title || script.script_name;
  customerElements.templateNotes.value = template?.notes || "";
  customerElements.templateFormTitle.textContent = mode === "create" ? "创建模板" : "编辑模板";
  customerElements.templateFormSubmitBtn.textContent = mode === "create" ? "保存模板" : "保存修改";
  populateParameterInputs(script.parameters || [], template?.config || {});
  openModal(customerElements.templateFormModal);
}

function populateParameterInputs(parameters, existingConfig) {
  if (!customerElements.templateParameters) return;
  if (!parameters.length) {
    customerElements.templateParameters.innerHTML = '<p class="muted">脚本未声明参数。</p>';
    return;
  }
  const items = parameters
    .map((param) => {
      const inputId = `param-${param.name.replace(/[^a-zA-Z0-9_-]+/g, "-")}`;
      const isObjectLike = ["object", "array"].includes((param.type || "").toLowerCase());
      const value = getValueByPath(existingConfig, param.name);
      const displayValue = formatInputValue(value !== undefined ? value : param.default, param.type);
      const requiredBadge = param.required ? '<span class="badge badge-warning">必填</span>' : "";
      const hintParts = [];
      if (param.description) hintParts.push(escapeHtml(param.description));
      if (param.type) hintParts.push(`类型: ${escapeHtml(param.type)}`);
      const hint = hintParts.join(" · ");
      const escapedValue = escapeHtml(displayValue);
      const inputField = isObjectLike
        ? `<textarea class="form-control" rows="3" id="${inputId}" data-param-name="${escapeHtml(param.name)}">${escapedValue}</textarea>`
        : `<input class="form-control" type="text" id="${inputId}" data-param-name="${escapeHtml(param.name)}" value="${escapedValue}">`;
      const defaultInfo = param.default !== undefined && param.default !== null
        ? `<span class="parameter-hint">默认值: ${escapeHtml(formatDisplayValue(param.default))}</span>`
        : "";
      const infoLine = [hint, defaultInfo].filter(Boolean).join("<br>");
      return `
        <div class="form-row" data-parameter>
            <label for="${inputId}">${escapeHtml(param.name)} ${requiredBadge}</label>
            ${inputField}
            ${infoLine ? `<span class="parameter-hint">${infoLine}</span>` : ""}
        </div>
      `;
    })
    .join("");
  customerElements.templateParameters.innerHTML = items;
}

function openModal(modal) {
  if (!modal) return;
  modal.classList.add("is-open");
}

function closeModal(modal) {
  if (!modal) return;
  modal.classList.remove("is-open");
  if (modal === customerElements.templateFormModal) {
    customerElements.templateForm.reset();
    customerElements.templateFormMessage.textContent = "";
    if (customerElements.templateParameters) {
      customerElements.templateParameters.innerHTML = "";
    }
    customerState.currentFormParameters = [];
    customerState.editingTemplate = null;
  }
  if (modal === customerElements.templateInfoModal) {
    customerElements.templateInfoBody.innerHTML = "";
  }
}

function buildConfigFromForm(parameters) {
  const config = {};
  const missing = [];
  parameters.forEach((param) => {
    const field = customerElements.templateParameters?.querySelector(
      `[data-param-name="${CSS.escape(param.name)}"]`
    );
    if (!field) return;
    const rawValue = field.value.trim();
    if (!rawValue) {
      if (param.required && (param.default === undefined || param.default === null)) {
        missing.push(param.name);
      }
      return;
    }
    try {
      const parsed = parseParameterValue(rawValue, param.type);
      assignPath(config, param.name, parsed);
    } catch (error) {
      throw new Error(`参数 ${param.name} 无法解析: ${error.message}`);
    }
  });
  if (missing.length) {
    throw new Error(`缺少必填参数: ${missing.join(", ")}`);
  }
  return config;
}

function parseParameterValue(raw, type = "string") {
  const valueType = (type || "string").toLowerCase();
  if (valueType === "int" || valueType === "integer") {
    const parsed = parseInt(raw, 10);
    if (Number.isNaN(parsed)) throw new Error("需要整数");
    return parsed;
  }
  if (valueType === "number" || valueType === "float" || valueType === "double") {
    const parsed = parseFloat(raw);
    if (Number.isNaN(parsed)) throw new Error("需要数值");
    return parsed;
  }
  if (valueType === "bool" || valueType === "boolean") {
    if (["true", "false"].includes(raw.toLowerCase())) {
      return raw.toLowerCase() === "true";
    }
    throw new Error("需要 true/false");
  }
  if (valueType === "object" || valueType === "array") {
    try {
      return JSON.parse(raw);
    } catch (error) {
      throw new Error("需要合法的 JSON 字符串");
    }
  }
  return raw;
}

function assignPath(target, path, value) {
  const segments = path.split(".");
  let cursor = target;
  segments.forEach((segment, index) => {
    if (index === segments.length - 1) {
      cursor[segment] = value;
    } else {
      if (!cursor[segment] || typeof cursor[segment] !== "object") {
        cursor[segment] = {};
      }
      cursor = cursor[segment];
    }
  });
}

function getValueByPath(source, path) {
  if (!source) return undefined;
  return path.split(".").reduce((acc, key) => (acc && acc[key] !== undefined ? acc[key] : undefined), source);
}

function formatDisplayValue(value) {
  if (value === null || value === undefined) return "-";
  if (typeof value === "object") {
    try {
      return JSON.stringify(value);
    } catch (error) {
      return String(value);
    }
  }
  return String(value);
}

function formatInputValue(value, type) {
  if (value === null || value === undefined) return "";
  const valueType = (type || "string").toLowerCase();
  if (valueType === "object" || valueType === "array") {
    try {
      return JSON.stringify(value, null, 2);
    } catch (error) {
      return "";
    }
  }
  return String(value);
}

async function submitTemplateForm(event) {
  event.preventDefault();
  if (!customerElements.templateForm) return;
  customerElements.templateFormMessage.textContent = "";
  customerElements.templateFormSubmitBtn.disabled = true;
  const mode = customerElements.templateFormMode.value;
  const scriptName = customerElements.templateScriptName.value;
  const scriptVersion = customerElements.templateScriptVersion.value;
  const title = customerElements.templateTitle.value.trim();
  const notes = customerElements.templateNotes.value.trim();
  try {
    const config = buildConfigFromForm(customerState.currentFormParameters);
    if (mode === "create") {
      const payload = {
        script_name: scriptName,
        script_title: title || undefined,
        script_version: scriptVersion && scriptVersion !== "-" ? scriptVersion : undefined,
        config,
        notes: notes || undefined,
      };
      await requestJson("/api/customer/templates", {
        method: "POST",
        body: payload,
      });
    } else if (mode === "edit" && customerState.editingTemplate) {
      const payload = {
        config,
        notes: notes || undefined,
      };
      await requestJson(`/api/customer/templates/${customerState.editingTemplate.id}`, {
        method: "PATCH",
        body: payload,
      });
    }
    await loadTemplates();
    closeModal(customerElements.templateFormModal);
  } catch (error) {
    console.error("保存模板失败", error);
    customerElements.templateFormMessage.textContent = error.message || "保存失败";
  } finally {
    customerElements.templateFormSubmitBtn.disabled = false;
  }
}

async function handleTemplateAction(event) {
  const actionButton = event.target.closest("[data-action]");
  if (!actionButton) return;
  const row = actionButton.closest("tr[data-template-id]");
  if (!row) return;
  const templateId = row.dataset.templateId;
  try {
    switch (actionButton.dataset.action) {
      case "view":
        await openTemplateDetail(templateId);
        break;
      case "edit":
        await openTemplateEditor(templateId);
        break;
      case "delete":
        await deleteTemplate(templateId);
        break;
      default:
        break;
    }
  } catch (error) {
    alert(error.message || "操作失败");
  }
}

async function openTemplateDetail(templateId) {
  if (!customerElements.templateInfoBody) return;
  customerElements.templateInfoBody.innerHTML = '<div class="loading">加载中...</div>';
  openModal(customerElements.templateInfoModal);
  const detail = await requestJson(`/api/customer/templates/${templateId}`);
  const schema = JSON.stringify(detail.schema || {}, null, 2);
  const config = JSON.stringify(detail.config || {}, null, 2);
  customerElements.templateInfoTitle.textContent = escapeHtml(detail.script_title || detail.script_name);
  customerElements.templateInfoBody.innerHTML = `
    <section>
        <h4>基本信息</h4>
        <p class="muted">脚本：${escapeHtml(detail.script_name)} · 版本：${escapeHtml(detail.script_version || "-" )}</p>
        <p class="muted">状态：${buildCompatibilityBadge(detail.compatibility)}</p>
        ${detail.notes ? `<p class="muted">备注：${escapeHtml(detail.notes)}</p>` : ""}
    </section>
    <section>
        <h4>参数配置</h4>
        <pre>${escapeHtml(config)}</pre>
    </section>
    <section>
        <h4>Schema 快照</h4>
        <pre>${escapeHtml(schema)}</pre>
    </section>
  `;
}

async function openTemplateEditor(templateId) {
  const detail = await requestJson(`/api/customer/templates/${templateId}`);
  const script = customerState.scripts.find((item) => item.script_name === detail.script_name);
  if (!script) {
    throw new Error("当前脚本已不可用，请刷新脚本列表或更新模板");
  }
  customerState.editingTemplate = detail;
  openTemplateForm("edit", script, detail);
}

async function deleteTemplate(templateId) {
  const template = customerState.templates.find((item) => item.id === templateId);
  if (!template) return;
  const confirmed = window.confirm(`确定要删除模板 “${template.script_title || template.script_name}” 吗？`);
  if (!confirmed) return;
  await requestJson(`/api/customer/templates/${templateId}`, { method: "DELETE" });
  await loadTemplates();
}

function registerEventListeners() {
  if (customerElements.logoutBtn) {
    customerElements.logoutBtn.addEventListener("click", handleLogout);
  }
  customerElements.tabs.forEach((tab) => {
    tab.addEventListener("click", () => {
      switchTab(tab.dataset.tab);
      if (tab.dataset.tab === "scripts") {
        loadScripts();
      } else if (tab.dataset.tab === "templates") {
        loadTemplates();
      } else if (tab.dataset.tab === "devices") {
        loadDevices();
      }
    });
  });
  if (customerElements.refreshScriptsBtn) {
    customerElements.refreshScriptsBtn.addEventListener("click", loadScripts);
  }
  if (customerElements.refreshTemplatesBtn) {
    customerElements.refreshTemplatesBtn.addEventListener("click", loadTemplates);
  }
  if (customerElements.scriptList) {
    customerElements.scriptList.addEventListener("click", (event) => {
      const item = event.target.closest(".script-item");
      if (!item) return;
    const scriptName = item.dataset.script ? decodeURIComponent(item.dataset.script) : "";
    const script = customerState.scripts.find((s) => s.script_name === scriptName);
      if (script) {
        setSelectedScript(script);
      }
    });
  }
  if (customerElements.templateTable) {
    customerElements.templateTable.addEventListener("click", handleTemplateAction);
  }
  if (customerElements.templateForm) {
    customerElements.templateForm.addEventListener("submit", submitTemplateForm);
  }
  customerElements.templateModalCloseButtons.forEach((btn) => {
    btn.addEventListener("click", () => {
      const modal = btn.closest(".modal");
      closeModal(modal);
    });
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      closeModal(customerElements.templateFormModal);
      closeModal(customerElements.templateInfoModal);
    }
  });
}

async function initCustomerApp() {
  if (!customerState.token) {
    window.location.href = "/login";
    return;
  }
  if (customerState.role === "admin" || customerState.role === "super_admin") {
    window.location.href = "/admin";
    return;
  }
  try {
    await loadProfile();
    registerEventListeners();
    // 默认加载脚本、模板和设备
    await Promise.all([loadScripts(), loadTemplates(), loadDevices()]);
  } catch (error) {
    console.error("初始化客户控制台失败", error);
    handleLogout();
  }
}

window.addEventListener("DOMContentLoaded", initCustomerApp);
