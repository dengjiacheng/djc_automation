// 客户端控制台：脚本中心 + 模板管理 + 设备概览

const customerState = {
  token: localStorage.getItem("auth_token"),
  role: localStorage.getItem("auth_role"),
  username: localStorage.getItem("auth_username"),
  scripts: [],
  selectedScript: null,
  templates: [],
  currentFormParameters: [],
  currentScriptName: null,
  editingTemplate: null,
  scriptMap: {},
  jobs: [],
  executeContext: null,
  jobFilter: "all",
  ws: null,
};

const customerElements = {
  info: document.getElementById("customerInfo"),
  logoutBtn: document.getElementById("customerLogoutBtn"),
  tabs: document.querySelectorAll(".tabs .tab"),
  tabContents: {
    scripts: document.getElementById("scriptsContent"),
    templates: document.getElementById("templatesContent"),
    jobs: document.getElementById("jobsContent"),
    devices: document.getElementById("devicesContent"),
  },
  scriptList: document.getElementById("scriptList"),
  scriptDetail: document.getElementById("scriptDetail"),
  refreshScriptsBtn: document.getElementById("refreshScriptsBtn"),
  templateTable: document.getElementById("templateTable"),
  refreshTemplatesBtn: document.getElementById("refreshTemplatesBtn"),
  jobsTable: document.getElementById("jobsTable"),
  refreshJobsBtn: document.getElementById("refreshJobsBtn"),
  exportJobsBtn: document.getElementById("exportJobsBtn"),
  jobStatusFilter: document.getElementById("jobStatusFilter"),
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
  executeModal: document.getElementById("executeModal"),
  executeModalTitle: document.getElementById("executeModalTitle"),
  executeMeta: document.getElementById("executeMeta"),
  executeDeviceList: document.getElementById("executeDeviceList"),
  executeCostSummary: document.getElementById("executeCostSummary"),
  executeConfigPreview: document.getElementById("executeConfigPreview"),
  executeMessage: document.getElementById("executeMessage"),
  executeSubmitBtn: document.getElementById("executeSubmitBtn"),
  toastContainer: document.getElementById("toastContainer"),
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

function showToast(message, type = "success") {
  if (!customerElements.toastContainer) {
    alert(message);
    return;
  }
  const toast = document.createElement("div");
  toast.className = `toast toast-${type}`;
  toast.innerHTML = `<p>${escapeHtml(message)}</p>`;
  customerElements.toastContainer.appendChild(toast);
  setTimeout(() => {
    toast.classList.add("fade-out");
    toast.addEventListener("transitionend", () => toast.remove(), { once: true });
  }, 2800);
}

function handleLogout() {
  if (customerState.ws) {
    try {
      customerState.ws.close();
    } catch (error) {
      console.error("关闭 WebSocket 失败", error);
    }
    customerState.ws = null;
  }
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

function initCustomerWebSocket() {
  if (customerState.ws || !customerState.token) {
    return;
  }
  try {
    const protocol = window.location.protocol === "https:" ? "wss" : "ws";
    const socket = new WebSocket(`${protocol}://${window.location.host}/ws/web?token=${customerState.token}`);
    customerState.ws = socket;
    socket.addEventListener("message", (event) => {
      try {
        const payload = JSON.parse(event.data);
        if (payload.type === "script_job_update") {
          handleJobUpdate(payload.data || {});
        }
      } catch (error) {
        console.error("解析消息失败", error);
      }
    });
    socket.addEventListener("close", () => {
      customerState.ws = null;
      setTimeout(initCustomerWebSocket, 5000);
    });
    socket.addEventListener("error", () => {
      socket.close();
    });
  } catch (error) {
    console.error("建立 WebSocket 失败", error);
  }
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
  const labLink = document.getElementById("scriptLabLink");
  if (labLink) {
    const isAdmin = profile.role === "admin" || profile.role === "super_admin";
    labLink.hidden = !isAdmin;
    labLink.style.display = isAdmin ? "inline-block" : "none";
  }
}

async function loadScripts() {
  if (!customerElements.scriptList) return;
  customerElements.scriptList.innerHTML = '<div class="loading">加载中...</div>';
  try {
    const data = await requestJson("/api/customer/templates/scripts");
    customerState.scripts = data.scripts || [];
    customerState.scriptMap = Object.fromEntries(
      customerState.scripts.map((script) => [script.script_name, script])
    );
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
  const priceInfo = formatPrice(script.unit_price, script.currency || "CNY");
  const parameterRows = parameters.length
    ? parameters
        .map((param) => `
            <tr>
                <td>${escapeHtml(param.name)}${param.required ? " <span class=\"badge badge-warning\">必填</span>" : ""}</td>
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
        <div class="muted">版本：${escapeHtml(script.version || "未提供")} · 支持设备：${script.source_devices?.length || 0} · 单价：${priceInfo}</div>
        ${renderPricingDetails(script.pricing)}
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

function renderPricingDetails(pricing) {
  if (!pricing) {
    return "";
  }
  const rows = [];
  if (pricing.tiers && pricing.tiers.length) {
    const tierRows = pricing.tiers
      .map((tier) => `
        <tr>
            <td>${escapeHtml(tier.label || `${tier.threshold || "-"}`)}</td>
            <td>${tier.threshold !== undefined ? escapeHtml(String(tier.threshold)) : "-"}</td>
            <td>${tier.price !== undefined ? escapeHtml(String(tier.price)) : "-"}</td>
        </tr>
      `)
      .join("");
    rows.push(`
      <div class="pricing-card">
          <h4>阶梯定价</h4>
          <table class="parameter-table">
              <thead><tr><th>描述</th><th>阈值</th><th>价格</th></tr></thead>
              <tbody>${tierRows}</tbody>
          </table>
      </div>
    `);
  }
  if (pricing.description) {
    rows.push(`<p class="muted">${escapeHtml(pricing.description)}</p>`);
  }
  return rows.join("\n");
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
      const executeDisabled = template.compatibility !== "active" ? "disabled" : "";
      return `
        <tr data-template-id="${escapeHtml(template.id)}">
            <td>${escapeHtml(template.script_title || template.script_name)}</td>
            <td>${escapeHtml(template.script_name)}</td>
            <td>${escapeHtml(template.script_version || "-" )}</td>
            <td>${statusBadge}</td>
            <td>${updatedAt}</td>
            <td class="text-right">
                <div class="template-actions">
                    <button class="btn btn--primary btn--sm" data-action="execute" ${executeDisabled}>执行</button>
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

function buildJobStatusBadge(status) {
  switch (status) {
    case "running":
      return '<span class="badge badge-warning">执行中</span>';
    case "completed":
      return '<span class="badge badge-success">已完成</span>';
    case "partial":
      return '<span class="badge badge-warning">部分完成</span>';
    case "failed":
      return '<span class="badge badge-danger">失败</span>';
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

async function loadJobs() {
  if (!customerElements.jobsTable) return;
  customerElements.jobsTable.innerHTML = '<div class="loading">加载中...</div>';
  try {
    const data = await requestJson("/api/customer/script-jobs");
    customerState.jobs = data.jobs || [];
    renderJobTable();
  } catch (error) {
    console.error("加载任务失败", error);
    customerElements.jobsTable.innerHTML = `<div class="empty-state">${error.message || "任务加载失败"}</div>`;
  }
}

function renderJobTable() {
  if (!customerElements.jobsTable) return;
  const jobs = customerState.jobs;
  if (!jobs.length) {
    customerElements.jobsTable.innerHTML = '<div class="empty-state">暂无任务记录，创建模板后即可执行脚本。</div>';
    return;
  }
  const filtered = customerState.jobFilter === "all"
    ? jobs
    : jobs.filter((job) => job.status === customerState.jobFilter);
  if (!filtered.length) {
    customerElements.jobsTable.innerHTML = '<div class="empty-state">当前筛选条件下暂无任务</div>';
    return;
  }
  const rows = filtered
    .map((job) => {
      const createdAt = job.created_at ? new Date(job.created_at).toLocaleString("zh-CN") : "-";
      const totalPrice = job.total_price !== null ? formatPrice(job.total_price, job.currency || "CNY") : "-";
      const successCount = job.targets.filter((target) => target.status === "success").length;
      const failCount = job.targets.filter((target) => target.status === "failed").length;
      const statusBadge = buildJobStatusBadge(job.status);
      const templateLabel = job.template_title
        ? escapeHtml(job.template_title)
        : `<span class="muted">未命名</span>`;
      return `
        <tr data-job-id="${escapeHtml(job.id)}">
            <td>${templateLabel}</td>
            <td>${escapeHtml(job.script_name)}</td>
            <td>${escapeHtml(job.script_version || "-")}</td>
            <td>${job.total_targets}</td>
            <td>${successCount}/${failCount}</td>
            <td>${totalPrice}</td>
            <td>${createdAt}</td>
            <td>${statusBadge}</td>
            <td class="text-right"><button class="btn btn--ghost btn--sm" data-action="job-detail">详情</button></td>
        </tr>
      `;
    })
    .join("");
  customerElements.jobsTable.innerHTML = `
    <table>
        <thead>
            <tr>
                <th>模板</th>
                <th>脚本</th>
                <th>版本</th>
                <th>目标设备</th>
                <th>成功/失败</th>
                <th>费用</th>
                <th>创建时间</th>
                <th>状态</th>
                <th class="text-right">操作</th>
            </tr>
        </thead>
        <tbody>${rows}</tbody>
    </table>
  `;
  customerElements.jobsTable.querySelectorAll("[data-action='job-detail']").forEach((btn) => {
    btn.addEventListener("click", async (event) => {
      const jobRow = event.target.closest("tr[data-job-id]");
      if (!jobRow) return;
      await openJobDetail(jobRow.dataset.jobId);
    });
  });
}

function openTemplateForm(mode, script, template = null) {
  if (!customerElements.templateFormModal || !script) return;
  customerElements.templateFormMode.value = mode;
  customerState.currentFormParameters = script.parameters || [];
  customerState.currentScriptName = script.script_name;
  customerState.editingTemplate = template;
  customerElements.templateFormMessage.textContent = "";
  customerElements.templateFormSubmitBtn.disabled = false;
  customerElements.templateScriptName.value = script.script_name;
  customerElements.templateScriptVersion.value = script.version || "-";
  customerElements.templateTitle.value = template?.script_title || script.script_title || script.script_name;
  customerElements.templateNotes.value = template?.notes || "";
  customerElements.templateFormTitle.textContent = mode === "create" ? "创建模板" : "编辑模板";
  customerElements.templateFormSubmitBtn.textContent = mode === "create" ? "保存模板" : "保存修改";
  populateParameterInputs(script.parameters || [], template?.config || {}, script.script_name);
  openModal(customerElements.templateFormModal);
}

const FILE_PARAM_TYPES = new Set(["file", "image"]);

function populateParameterInputs(parameters, existingConfig, scriptName) {
  if (!customerElements.templateParameters) return;
  if (!parameters.length) {
    customerElements.templateParameters.innerHTML = '<p class="muted">脚本未声明参数。</p>';
    return;
  }
  const rows = parameters
    .map((param) => {
      const paramType = (param.type || "").toLowerCase();
      const paramNameAttr = param.name.replace(/"/g, "&quot;");
      const inputId = `param-${param.name.replace(/[^a-zA-Z0-9_-]+/g, "-")}`;
      const requiredBadge = param.required ? '<span class="badge badge-warning">必填</span>' : "";
      const typeMeta = param.type ? `<span class="parameter-type"> · 类型: ${escapeHtml(param.type)}</span>` : "";
      const descriptionLine = param.description ? `<span class="parameter-hint">${escapeHtml(param.description)}</span>` : "";
      const isTaskName = param.name === "task_name";
      const existingValue = getValueByPath(existingConfig, param.name);

      if (FILE_PARAM_TYPES.has(paramType)) {
        const metadata = existingValue && typeof existingValue === "object" ? existingValue : null;
        const currentLabel = metadata
          ? escapeHtml(metadata.name || metadata.file_name || metadata.asset_id || metadata.value || "已上传")
          : "尚未选择文件";
        const sizeLabel = metadata?.size ? ` · ${formatBytes(metadata.size)}` : "";
        const hiddenValue = metadata ? escapeHtml(JSON.stringify(metadata)) : "";
        const accept = paramType === "image" ? "image/*" : "*/*";
        return `
          <div class="form-row parameter-file" data-parameter data-param-name="${paramNameAttr}" data-param-type="${paramType}">
              <label class="param-label" for="${inputId}">
                  ${escapeHtml(param.name)} ${requiredBadge}${typeMeta}
              </label>
              <input type="hidden" data-file-metadata value="${hiddenValue}">
              <div class="file-input-group">
                  <input class="form-control file-upload-input" type="file" id="${inputId}" data-param-file="${paramNameAttr}" accept="${accept}">
                  <button type="button" class="btn btn--ghost btn--sm" data-action="clear-file" data-param-file="${paramNameAttr}">清除</button>
              </div>
              <div class="parameter-hint parameter-file-status" data-param-file-status="${paramNameAttr}">
                  当前：${currentLabel}${sizeLabel}
              </div>
              ${descriptionLine}
          </div>
        `;
      }

      const defaultDisplay =
        param.default !== undefined && param.default !== null
          ? escapeHtml(formatDisplayValue(param.default))
          : "未设置";
      const effectiveValue =
        isTaskName && scriptName ? scriptName : existingValue !== undefined ? existingValue : param.default;
      const displayValue = formatInputValue(effectiveValue, param.type);
      const readOnlyAttr = isTaskName ? " readonly" : "";
      const readonlyHint = isTaskName
        ? '<span class="parameter-hint">该字段由系统自动填写，需与脚本能力保持一致。</span>'
        : "";
      const isObjectLike = ["object", "array"].includes(paramType);
      const escapedValue = escapeHtml(displayValue);
      const inputField = isObjectLike
        ? `<textarea class="form-control"${readOnlyAttr} rows="3" id="${inputId}" data-param-name="${paramNameAttr}">${escapedValue}</textarea>`
        : `<input class="form-control"${readOnlyAttr} type="text" id="${inputId}" data-param-name="${paramNameAttr}" value="${escapedValue}">`;
      return `
        <div class="form-row" data-parameter data-param-name="${paramNameAttr}" data-param-type="${paramType}">
            <label class="param-label" for="${inputId}">
                ${escapeHtml(param.name)} ${requiredBadge}${typeMeta}
            </label>
            ${inputField}
            <span class="parameter-hint">默认值: ${defaultDisplay}</span>
            ${descriptionLine}
            ${readonlyHint}
        </div>
      `;
    })
    .join("");
  customerElements.templateParameters.innerHTML = rows;
  bindParameterFileInputs(parameters);
}

function bindParameterFileInputs(parameters) {
  if (!customerElements.templateParameters) return;
  const fileParams = parameters.filter((param) => FILE_PARAM_TYPES.has((param.type || "").toLowerCase()));
  if (!fileParams.length) return;

  fileParams.forEach((param) => {
    const selector = `[data-parameter][data-param-name="${CSS.escape(param.name)}"]`;
    const row = customerElements.templateParameters.querySelector(selector);
    if (!row) return;
    const fileInput = row.querySelector(`[data-param-file]`);
    const clearBtn = row.querySelector('[data-action="clear-file"]');
    if (fileInput) {
      fileInput.addEventListener("change", async (event) => {
        const file = event.target.files?.[0];
        const statusEl = row.querySelector(".parameter-file-status");
        if (!file) {
          setFileFieldMetadata(row, null);
          if (statusEl) statusEl.textContent = "尚未选择文件";
          return;
        }
        if (statusEl) {
          statusEl.textContent = "上传中...";
          statusEl.classList.remove("error");
        }
        try {
          const asset = await uploadTemplateAsset(file);
          const payload = {
            type: (param.type || "file").toLowerCase(),
            source: "asset",
            asset_id: asset.id,
            name: asset.file_name,
            mime: asset.content_type,
            size: asset.size_bytes,
            checksum: asset.checksum_sha256,
            download_url: asset.download_url || "",
          };
          if (asset.download_url) {
            try {
              const url = new URL(asset.download_url, window.location.origin);
              payload.download_path = url.pathname;
            } catch (e) {
              payload.download_path = asset.download_url;
            }
          }
          setFileFieldMetadata(row, payload);
          if (statusEl) {
            statusEl.textContent = `当前：${asset.file_name || asset.id} · ${formatBytes(asset.size_bytes)}`;
            statusEl.classList.remove("error");
          }
        } catch (error) {
          console.error("上传脚本资源失败", error);
          setFileFieldMetadata(row, null);
          if (statusEl) {
            statusEl.textContent = `上传失败: ${error.message || "网络错误"}`;
            statusEl.classList.add("error");
          }
        } finally {
          event.target.value = "";
        }
      });
    }
    if (clearBtn) {
      clearBtn.addEventListener("click", () => {
        setFileFieldMetadata(row, null);
        const statusEl = row.querySelector(".parameter-file-status");
        if (statusEl) {
          statusEl.textContent = "尚未选择文件";
          statusEl.classList.remove("error");
        }
      });
    }
  });
}

async function uploadTemplateAsset(file) {
  if (!customerState.token) {
    throw new Error("未登录或会话失效");
  }
  const formData = new FormData();
  formData.append("file", file);
  const response = await fetch("/api/customer/assets", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${customerState.token}`,
    },
    body: formData,
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(payload.detail || payload.message || "上传失败");
  }
  return payload;
}

function setFileFieldMetadata(row, metadata) {
  const hidden = row.querySelector("input[data-file-metadata]");
  if (!hidden) return;
  hidden.value = metadata ? JSON.stringify(metadata) : "";
}

function formatBytes(size) {
  if (!size || Number.isNaN(size)) return "0 B";
  const units = ["B", "KB", "MB", "GB"];
  let index = 0;
  let value = size;
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024;
    index += 1;
  }
  return `${value.toFixed(index === 0 ? 0 : 1)} ${units[index]}`;
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
    customerState.currentScriptName = null;
  }
  if (modal === customerElements.templateInfoModal) {
    customerElements.templateInfoBody.innerHTML = "";
  }
  if (modal === customerElements.executeModal) {
    customerState.executeContext = null;
    customerElements.executeMessage.textContent = "";
    if (customerElements.executeDeviceList) {
      customerElements.executeDeviceList.innerHTML = "";
    }
    if (customerElements.executeCostSummary) {
      customerElements.executeCostSummary.innerHTML = '<p class="muted">选择设备后自动计算费用。</p>';
    }
  }
}

function buildConfigFromForm(parameters) {
  const config = {};
  const missing = [];
  parameters.forEach((param) => {
    const container = customerElements.templateParameters?.querySelector(
      `[data-parameter][data-param-name="${CSS.escape(param.name)}"]`
    );
    const paramType = (param.type || "string").toLowerCase();
    if (FILE_PARAM_TYPES.has(paramType)) {
      const hidden = container?.querySelector("input[data-file-metadata]");
      const jsonValue = hidden?.value?.trim();
      if (!jsonValue) {
        if (param.required) {
          missing.push(param.name);
        }
        return;
      }
      try {
        const payload = JSON.parse(jsonValue);
        assignPath(config, param.name, payload);
      } catch (error) {
        throw new Error(`参数 ${param.name} 格式错误: ${error.message}`);
      }
      return;
    }
    const field = container?.querySelector(`[data-param-name="${CSS.escape(param.name)}"]`);
    if (!field) {
      if (param.required && (param.default === undefined || param.default === null)) {
        missing.push(param.name);
      }
      return;
    }
    if (param.name === "task_name") {
      const fallbackDefault = param.default !== undefined && param.default !== null ? param.default : "";
      const fieldValue = field ? field.value.trim() : "";
      const enforcedValue = customerState.currentScriptName || fieldValue || fallbackDefault;
      if (!enforcedValue) {
        if (param.required) {
          missing.push(param.name);
        }
        return;
      }
      assignPath(config, param.name, enforcedValue);
      return;
    }
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

function formatPrice(cents, currency = "CNY") {
  if (cents === null || cents === undefined) {
    return "暂无定价";
  }
  const amount = (cents / 100).toFixed(2);
  return `${amount} ${currency}`;
}

function handleJobUpdate(data) {
  if (!data || !data.job_id) return;
  const existing = customerState.jobs.find((job) => job.id === data.job_id);
  if (!existing) {
    loadJobs();
    return;
  }
  if (data.status) {
    existing.status = data.status;
  }
  if (typeof data.success_count === "number") {
    existing.success_count = data.success_count;
  }
  if (typeof data.failed_count === "number") {
    existing.failed_count = data.failed_count;
  }
  if (data.target) {
    const target = existing.targets.find((item) => item.device_id === data.target.device_id);
    if (target) {
      target.status = data.target.status;
      target.completed_at = data.target.completed_at;
      target.command_id = data.target.command_id;
      target.error_message = data.target.error_message;
    } else {
      existing.targets.push(data.target);
    }
  }
  showToast(`任务 ${data.job_id.slice(0, 8)} 状态更新为 ${data.status}`, "success");
  renderJobTable();
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
        script_title: title || undefined,
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
      case "execute":
        await openExecuteModal(templateId);
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

async function openJobDetail(jobId) {
  if (!customerElements.templateInfoBody) return;
  customerElements.templateInfoBody.innerHTML = '<div class="loading">加载中...</div>';
  openModal(customerElements.templateInfoModal);
  const job = await requestJson(`/api/customer/script-jobs/${jobId}`);
  customerState.currentJobDetail = job;
  customerElements.templateInfoTitle.textContent = `任务 ${job.id.slice(0, 8)}...`;
  const templateDisplay = job.template_title
    ? `${escapeHtml(job.template_title)} <span class="muted">(ID: ${escapeHtml(job.template_id)})</span>`
    : escapeHtml(job.template_id);
  const targetsRows = job.targets
    .map((target) => {
      const statusBadge = buildJobStatusBadge(target.status);
      const completedAt = target.completed_at ? new Date(target.completed_at).toLocaleString("zh-CN") : "-";
      const deviceName = target.device_name ? escapeHtml(target.device_name) : '<span class="muted">未命名</span>';
      return `
        <tr>
            <td>${deviceName}</td>
            <td>${escapeHtml(target.device_id)}</td>
            <td>${statusBadge}</td>
            <td>${escapeHtml(target.command_id || "-")}</td>
            <td>${completedAt}</td>
            <td>${escapeHtml(target.error_message || "-")}</td>
        </tr>
      `;
    })
    .join("");
  const totalPrice = job.total_price !== null ? formatPrice(job.total_price, job.currency || "CNY") : "-";
  customerElements.templateInfoBody.innerHTML = `
    <section>
        <h4>任务概览</h4>
        <p class="muted">脚本：${escapeHtml(job.script_name)} · 模板：${templateDisplay}</p>
        <p class="muted">状态：${buildJobStatusBadge(job.status)} · 总费用：${totalPrice}</p>
        <p class="muted">创建时间：${job.created_at ? new Date(job.created_at).toLocaleString("zh-CN") : "-"}</p>
    </section>
    <section class="job-detail-toolbar">
        ${job.targets.some((target) => target.status !== "success") ? '<button class="btn btn--primary btn--sm" id="retryJobBtn">重试失败设备</button>' : ""}
    </section>
    <section>
        <h4>设备执行详情</h4>
        <table class="parameter-table">
            <thead>
                <tr>
                    <th>设备名称</th>
                    <th>设备ID</th>
                    <th>状态</th>
                    <th>指令ID</th>
                    <th>完成时间</th>
                    <th>错误信息</th>
                </tr>
            </thead>
            <tbody>${targetsRows}</tbody>
        </table>
    </section>
  `;
  const retryBtn = document.getElementById("retryJobBtn");
  if (retryBtn) {
    retryBtn.addEventListener("click", () => retryFailedTargets(job));
  }
}

async function deleteTemplate(templateId) {
  const template = customerState.templates.find((item) => item.id === templateId);
  if (!template) return;
  const confirmed = window.confirm(`确定要删除模板 “${template.script_title || template.script_name}” 吗？`);
  if (!confirmed) return;
  await requestJson(`/api/customer/templates/${templateId}`, { method: "DELETE" });
  await loadTemplates();
}

async function openExecuteModal(templateId) {
  if (!customerElements.executeModal) return;
  customerElements.executeMessage.textContent = "";
  customerElements.executeSubmitBtn.disabled = true;
  customerElements.executeDeviceList.innerHTML = '<div class="loading">加载中...</div>';
  customerElements.executeCostSummary.innerHTML = '<p class="muted">选择设备后自动计算费用。</p>';
  try {
    const templateDetail = await requestJson(`/api/customer/templates/${templateId}`);
    const script = customerState.scriptMap[templateDetail.script_name];
    if (!script) {
      throw new Error("脚本当前不可用，请刷新脚本列表");
    }
    if (templateDetail.compatibility !== "active") {
      throw new Error("模板与脚本参数不一致，请先更新模板");
    }

    const devicesResp = await requestJson(`/api/customer/scripts/${encodeURIComponent(templateDetail.script_name)}/devices`);
    const selectableDevices = (devicesResp.devices || []).map((device) => ({
      ...device,
      isSelectable: device.compatibility === "active",
    }));

    const defaultSelection = new Set(selectableDevices.filter((d) => d.isSelectable).map((d) => d.device_id));

    customerState.executeContext = {
      template: templateDetail,
      script,
      devices: selectableDevices,
      unitPrice: script.unit_price,
      currency: script.currency || "CNY",
      selectedDeviceIds: defaultSelection,
    };

    customerElements.executeModalTitle.textContent = templateDetail.script_title || templateDetail.script_name;
    customerElements.executeMeta.innerHTML = `模板：${escapeHtml(templateDetail.script_title || templateDetail.script_name)} · 脚本：${escapeHtml(templateDetail.script_name)} · 版本：${escapeHtml(templateDetail.script_version || "-")}`;
    customerElements.executeConfigPreview.textContent = JSON.stringify(templateDetail.config || {}, null, 2);
    renderExecuteDeviceList();
    updateExecuteCostSummary();
    customerElements.executeSubmitBtn.disabled = false;
    openModal(customerElements.executeModal);
  } catch (error) {
    console.error("打开执行弹窗失败", error);
    showToast(error.message || "无法执行", "error");
  }
}

function renderExecuteDeviceList() {
  if (!customerElements.executeDeviceList) return;
  const context = customerState.executeContext;
  if (!context) {
    customerElements.executeDeviceList.innerHTML = '<div class="empty-state">数据加载失败</div>';
    return;
  }
  if (!context.devices.length) {
    customerElements.executeDeviceList.innerHTML = '<div class="empty-state">暂无可用设备</div>';
    return;
  }
  const items = context.devices
    .map((device) => {
      const disabled = !device.isSelectable;
      const checked = context.selectedDeviceIds.has(device.device_id);
      const compatibility = device.compatibility === "active"
        ? '<span class="badge badge-success">可执行</span>'
        : device.compatibility === "stale"
          ? '<span class="badge badge-warning">参数失配</span>'
          : '<span class="badge badge-danger">不可用</span>';
      return `
        <div class="device-item ${disabled ? "is-disabled" : ""}">
            <input type="checkbox" ${checked ? "checked" : ""} ${disabled ? "disabled" : ""} data-device-id="${escapeHtml(device.device_id)}">
            <label>
                <span class="device-name">${escapeHtml(device.device_name || device.device_id)} ${compatibility}</span>
                <span class="device-meta-line">${escapeHtml(device.device_model || "型号未知")}</span>
            </label>
        </div>
      `;
    })
    .join("");
  customerElements.executeDeviceList.innerHTML = items;
}

function updateExecuteCostSummary() {
  if (!customerElements.executeCostSummary) return;
  const context = customerState.executeContext;
  if (!context) {
    customerElements.executeCostSummary.innerHTML = '<p class="muted">暂无数据</p>';
    if (customerElements.executeSubmitBtn) customerElements.executeSubmitBtn.disabled = true;
    return;
  }
  const count = context.selectedDeviceIds.size;
  if (!count) {
    customerElements.executeCostSummary.innerHTML = '<p class="muted">请选择设备以计算费用</p>';
    if (customerElements.executeSubmitBtn) customerElements.executeSubmitBtn.disabled = true;
    return;
  }
  const unitPriceText = formatPrice(context.unitPrice, context.currency);
  const total = context.unitPrice !== null ? formatPrice(context.unitPrice * count, context.currency) : "-";
  customerElements.executeCostSummary.innerHTML = `
    <div>选中设备：<strong>${count}</strong> 台</div>
    <div>单价：${unitPriceText}</div>
    <div class="cost-total">预计费用：${total}</div>
    ${context.script.pricing && context.script.pricing.description ? `<p class="muted">${escapeHtml(context.script.pricing.description)}</p>` : ""}
    ${context.script.pricing && context.script.pricing.tiers ? `<p class="muted">阶梯说明：${context.script.pricing.tiers.map((t) => escapeHtml(`${t.label || t.threshold || ""}:${t.price}`)).join(" / ")}</p>` : ""}
  `;
  if (customerElements.executeSubmitBtn) customerElements.executeSubmitBtn.disabled = false;
}

async function submitExecuteJob() {
  if (!customerState.executeContext) return;
  const context = customerState.executeContext;
  customerElements.executeMessage.textContent = "";
  if (!context.selectedDeviceIds.size) {
    customerElements.executeMessage.textContent = "请至少选择一个设备";
    return;
  }
  customerElements.executeSubmitBtn.disabled = true;
  try {
    await requestJson("/api/customer/script-jobs", {
      method: "POST",
      body: {
        template_id: context.template.id,
        device_ids: Array.from(context.selectedDeviceIds),
      },
    });
    showToast("任务已创建，正在下发指令", "success");
    closeModal(customerElements.executeModal);
    await loadJobs();
  } catch (error) {
    console.error("执行脚本失败", error);
    customerElements.executeMessage.textContent = error.message || "执行失败";
  } finally {
    customerElements.executeSubmitBtn.disabled = false;
  }
}

function exportJobsToCsv() {
  if (!customerState.jobs.length) {
    showToast("暂无任务可导出", "error");
    return;
  }
  const rows = [
    ["job_id", "script_name", "script_version", "status", "total_targets", "success_count", "failed_count", "total_price", "currency", "created_at"],
  ];
  customerState.jobs.forEach((job) => {
    const successCount = job.targets.filter((t) => t.status === "success").length;
    const failedCount = job.targets.filter((t) => t.status === "failed").length;
    rows.push([
      job.id,
      job.script_name,
      job.script_version || "",
      job.status,
      job.total_targets,
      successCount,
      failedCount,
      job.total_price !== null ? (job.total_price / 100).toFixed(2) : "",
      job.currency || "",
      job.created_at ? new Date(job.created_at).toISOString() : "",
    ]);
  });
  const csvContent = rows
    .map((row) => row.map((cell) => `"${String(cell).replace(/"/g, '""')}"`).join(","))
    .join("\n");
  const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = `script-jobs-${Date.now()}.csv`;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  URL.revokeObjectURL(url);
  showToast("导出成功", "success");
}

async function retryFailedTargets(job) {
  const failedDevices = job.targets.filter((target) => target.status !== "success").map((target) => target.device_id);
  if (!failedDevices.length) {
    showToast("没有失败的设备", "error");
    return;
  }
  try {
    await requestJson(`/api/customer/script-jobs/${job.id}/retry`, {
      method: "POST",
    });
    showToast("已重新触发失败设备执行", "success");
    closeModal(customerElements.templateInfoModal);
    await loadJobs();
  } catch (error) {
    showToast(error.message || "重试失败", "error");
  }
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
      } else if (tab.dataset.tab === "jobs") {
        loadJobs();
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
  if (customerElements.refreshJobsBtn) {
    customerElements.refreshJobsBtn.addEventListener("click", loadJobs);
  }
  if (customerElements.exportJobsBtn) {
    customerElements.exportJobsBtn.addEventListener("click", exportJobsToCsv);
  }
  if (customerElements.jobStatusFilter) {
    customerElements.jobStatusFilter.addEventListener("change", (event) => {
      customerState.jobFilter = event.target.value;
      renderJobTable();
    });
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
  if (customerElements.executeDeviceList) {
    customerElements.executeDeviceList.addEventListener("change", (event) => {
      const checkbox = event.target.closest("input[type='checkbox'][data-device-id]");
      if (!checkbox || !customerState.executeContext) return;
      const deviceId = checkbox.dataset.deviceId;
      if (checkbox.checked) {
        customerState.executeContext.selectedDeviceIds.add(deviceId);
      } else {
        customerState.executeContext.selectedDeviceIds.delete(deviceId);
      }
      updateExecuteCostSummary();
    });
  }
  if (customerElements.executeSubmitBtn) {
    customerElements.executeSubmitBtn.addEventListener("click", submitExecuteJob);
  }
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      closeModal(customerElements.templateFormModal);
      closeModal(customerElements.templateInfoModal);
      closeModal(customerElements.executeModal);
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
    await Promise.all([loadScripts(), loadTemplates(), loadJobs(), loadDevices()]);
    initCustomerWebSocket();
  } catch (error) {
    console.error("初始化客户控制台失败", error);
    handleLogout();
  }
}

window.addEventListener("DOMContentLoaded", initCustomerApp);
