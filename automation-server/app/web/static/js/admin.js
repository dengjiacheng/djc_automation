// 管理后台脚本：拆分逻辑便于维护与扩展
const adminState = {
  token: "",
  role: "",
  isSuperAdmin: false,
  currentTab: "devices",
  refreshTimer: null,
};

const adminElements = {
  userInfo: document.getElementById("userInfo"),
  tabs: Array.from(document.querySelectorAll(".tab")),
  tabContents: Array.from(document.querySelectorAll(".tab-content")),
  adminsTab: document.getElementById("adminsTab"),
  devicesTableBody: document.getElementById("devicesTableBody"),
  adminsListBody: document.getElementById("adminsListBody"),
  apkInfoBody: document.getElementById("apkInfoBody"),
  apkSummary: document.getElementById("apkSummary"),
  apkUploadForm: document.getElementById("apkUploadForm"),
  apkMessage: document.getElementById("apkMessage"),
  refreshApkBtn: document.getElementById("refreshApkBtn"),
  createDeviceBtn: document.getElementById("createDeviceBtn"),
  createAdminBtn: document.getElementById("createAdminBtn"),
  logoutBtn: document.getElementById("logoutBtn"),
  scriptLabBtn: document.getElementById("scriptLabBtn"),
};

// 初始化入口
document.addEventListener("DOMContentLoaded", async () => {
  adminState.token = localStorage.getItem("auth_token");
  adminState.role = localStorage.getItem("auth_role");

  localStorage.removeItem("admin_token");
  localStorage.removeItem("admin_username");
  localStorage.removeItem("is_super_admin");

  if (!adminState.token) {
    window.location.href = "/login";
    return;
  }

  if (!["admin", "super_admin"].includes(adminState.role || "")) {
    window.location.href = "/customer";
    return;
  }

  bindAdminEvents();

  try {
    await loadUserInfo();
    await Promise.all([loadStats(), loadDevices(), loadApkInfo()]);
    startAutoRefresh();
  } catch (error) {
    console.error("初始化后台页面失败:", error);
  }
});

function bindAdminEvents() {
  adminElements.tabs.forEach((button) => {
    button.addEventListener("click", () => switchTab(button.dataset.tab));
  });

  if (adminElements.createDeviceBtn) {
    adminElements.createDeviceBtn.addEventListener("click", showCreateDeviceModal);
  }

  if (adminElements.createAdminBtn) {
    adminElements.createAdminBtn.addEventListener("click", showCreateAdminModal);
  }

  adminElements.logoutBtn.addEventListener("click", logout);
  if (adminElements.scriptLabBtn) {
    adminElements.scriptLabBtn.addEventListener("click", () => {
      window.location.href = "/script-lab";
    });
  }

  if (adminElements.refreshApkBtn) {
    adminElements.refreshApkBtn.addEventListener("click", () => loadApkInfo());
  }

  if (adminElements.apkUploadForm) {
    adminElements.apkUploadForm.addEventListener("submit", handleApkUpload);
  }

  adminElements.devicesTableBody.addEventListener("click", (event) => {
    const target = event.target.closest("[data-action='delete-device']");
    if (!target) {
      return;
    }
    deleteDevice(target.dataset.deviceId, target.dataset.username);
  });

  if (adminElements.adminsListBody) {
    adminElements.adminsListBody.addEventListener("click", (event) => {
      const target = event.target.closest("[data-action='delete-admin']");
      if (!target) {
        return;
      }
      deleteAdmin(target.dataset.adminId, target.dataset.username);
    });
  }
}

async function loadUserInfo() {
  try {
    const response = await fetchAPI("/api/admin/me");
    const data = await response.json();

    adminElements.userInfo.textContent = `欢迎, ${data.username}`;
    localStorage.setItem("auth_username", data.username);
    if (data.role) {
      localStorage.setItem("auth_role", data.role);
      adminState.role = data.role;
    }
    adminState.isSuperAdmin = data.role === "super_admin";

    if (adminState.isSuperAdmin) {
      adminElements.adminsTab.classList.remove("hidden");
      adminElements.createAdminBtn.classList.remove("hidden");
    }
  } catch (error) {
    console.error("加载用户信息失败:", error);
    logout();
  }
}

async function loadStats() {
  try {
    const response = await fetchAPI("/api/admin/stats");
    const stats = await response.json();

    document.getElementById("totalDevices").textContent = stats.device_total;
    document.getElementById("onlineDevices").textContent = stats.device_online;
    document.getElementById("offlineDevices").textContent = stats.device_offline;
    document.getElementById("todayCommands").textContent = stats.today_commands;
  } catch (error) {
    console.error("加载统计信息失败:", error);
  }
}

async function loadDevices() {
  try {
    const response = await fetchAPI("/api/admin/devices");
    const data = await response.json();

    if (!data.devices.length) {
      adminElements.devicesTableBody.innerHTML = `
        <div class="empty-state">
            <p>暂无设备</p>
        </div>
      `;
      return;
    }

    const rowsHtml = data.devices
      .map(
        (device) => `
          <tr>
              <td>${device.username}</td>
              <td>${device.device_name || "-"}</td>
              <td>${device.device_model || "-"}</td>
              <td>${device.local_ip || "-"}</td>
              <td>${device.public_ip || "-"}</td>
              <td>
                  <span class="status-badge ${device.is_online ? "online" : "offline"}">
                      ${device.is_online ? "在线" : "离线"}
                  </span>
              </td>
              <td>${new Date(device.created_at).toLocaleString("zh-CN")}</td>
              <td>
                  <button
                      class="btn-action btn-danger"
                      data-action="delete-device"
                      data-device-id="${device.id}"
                      data-username="${device.username}"
                  >
                      删除
                  </button>
              </td>
          </tr>
      `
      )
      .join("");

    adminElements.devicesTableBody.innerHTML = `
      <table>
          <thead>
              <tr>
                  <th>用户名</th>
                  <th>设备名称</th>
                  <th>设备型号</th>
                  <th>内网IP</th>
                  <th>外网IP</th>
                  <th>状态</th>
                  <th>创建时间</th>
                  <th>操作</th>
              </tr>
          </thead>
          <tbody>
              ${rowsHtml}
          </tbody>
      </table>
    `;
  } catch (error) {
    console.error("加载设备列表失败:", error);
    adminElements.devicesTableBody.innerHTML = `
      <div class="empty-state">
          <p>加载失败，请刷新重试</p>
      </div>
    `;
  }
}

async function loadApkInfo() {
  if (!adminElements.apkInfoBody) {
    return;
  }

  setApkMessage(null, "");
  if (adminElements.apkSummary) {
    adminElements.apkSummary.textContent = "加载中...";
  }

  adminElements.apkInfoBody.innerHTML = `
    <tr>
        <td colspan="8">
            <div class="loading">加载中...</div>
        </td>
    </tr>
  `;

  try {
    const response = await fetch("/api/apk/test/latest", {
      headers: {
        Authorization: `Bearer ${adminState.token}`,
      },
    });

    if (response.status === 401) {
      logout();
      return;
    }

    if (response.status === 404) {
      renderApkInfo(null);
      return;
    }

    if (!response.ok) {
      throw new Error("请求失败");
    }

    const info = await response.json();
    renderApkInfo(info);
  } catch (error) {
    console.error("加载测试 APK 信息失败:", error);
    adminElements.apkInfoBody.innerHTML = `
      <tr>
          <td colspan="8">
              <div class="empty-state">
                  <p>加载测试 APK 信息失败，请稍后重试</p>
              </div>
          </td>
      </tr>
    `;
    if (adminElements.apkSummary) {
      adminElements.apkSummary.textContent = "加载失败";
    }
  }
}

function renderApkInfo(info) {
  if (!adminElements.apkInfoBody) {
    return;
  }

  if (!info) {
    if (adminElements.apkSummary) {
      adminElements.apkSummary.textContent = "暂无发布记录";
    }
    adminElements.apkInfoBody.innerHTML = `
      <tr>
          <td colspan="8">
              <div class="empty-state">
                  <p>暂无测试 APK</p>
              </div>
          </td>
      </tr>
    `;
    return;
  }

  const summaryPieces = [];
  if (info.version || info.version_code !== undefined) {
    summaryPieces.push(`版本 ${info.version || "-"} (#${info.version_code ?? "-"})`);
  }
  if (info.created_at) {
    summaryPieces.push(`发布于 ${formatDate(info.created_at)}`);
  }
  if (adminElements.apkSummary) {
    adminElements.apkSummary.textContent = summaryPieces.length ? summaryPieces.join(" · ") : "已发布测试套件";
  }

  const rows = [];
  rows.push(renderApkRow("主应用", info.app));
  rows.push(renderApkRow("测试 APK", info.test));

  const availableRows = rows.filter((row) => Boolean(row)).join("");
  adminElements.apkInfoBody.innerHTML = availableRows
    || `
      <tr>
          <td colspan="8">
              <div class="empty-state">
                  <p>暂无测试 APK</p>
              </div>
          </td>
      </tr>
    `;
}

function renderApkRow(title, asset) {
  const data = asset || {};
  const downloadCell = data.download_url
    ? `<a class="btn btn--outline" href="${data.download_url}" target="_blank" rel="noopener">下载</a>`
    : '<span class="muted">无</span>';

  return `
    <tr>
        <td>${title}</td>
        <td>${data.package_name || "-"}</td>
        <td>${data.version_name || "-"}</td>
        <td>${data.version_code ?? "-"}</td>
        <td>${data.file_name || "-"}</td>
        <td>${formatBytes(data.size_bytes)}</td>
        <td><span class="hash-text">${data.checksum_sha256 || "-"}</span></td>
        <td class="text-right">${downloadCell}</td>
    </tr>
  `;
}

async function handleApkUpload(event) {
  event.preventDefault();
  if (!adminElements.apkUploadForm) {
    return;
  }

  setApkMessage(null, "上传中...");

  const formData = new FormData(adminElements.apkUploadForm);

  try {
    const response = await fetch("/api/apk/test/upload", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${adminState.token}`,
      },
      body: formData,
    });

    if (response.status === 401) {
      logout();
      return;
    }

    let payload = null;
    try {
      payload = await response.json();
    } catch (error) {
      if (!response.ok) {
        throw new Error("上传失败");
      }
    }

    if (!response.ok) {
      throw new Error(payload?.detail || "上传失败");
    }

    if (payload) {
      renderApkInfo(payload);
    } else {
      await loadApkInfo();
    }

    setApkMessage("success", "上传成功");
    adminElements.apkUploadForm.reset();
  } catch (error) {
    console.error("上传测试 APK 失败:", error);
    setApkMessage("error", error.message || "上传失败");
  }
}

async function loadAdmins() {
  try {
    const response = await fetchAPI("/api/admin/accounts");
    const admins = await response.json();

    if (!admins.length) {
      adminElements.adminsListBody.innerHTML = `
        <div class="empty-state">
            <p>暂无管理员</p>
        </div>
      `;
      return;
    }

    adminElements.adminsListBody.innerHTML = `
      <table class="plain-table">
          <thead>
              <tr>
                  <th>用户名</th>
                  <th>邮箱</th>
                  <th>角色</th>
                  <th>创建时间</th>
                  <th class="text-right">操作</th>
              </tr>
          </thead>
          <tbody>
              ${admins
                .map((admin) => {
                  const createdAt = new Date(admin.created_at).toLocaleDateString("zh-CN");
                  const roleLabel =
                    admin.role === "super_admin"
                      ? '<span class="badge badge--warning">超级管理员</span>'
                      : '<span class="badge badge--info">管理员</span>';
                  const deleteBtn =
                    admin.role !== "super_admin"
                      ? `<button
                            class="btn btn--link text-danger"
                            data-action="delete-admin"
                            data-admin-id="${admin.id}"
                            data-username="${admin.username}"
                        >删除</button>`
                      : "";
                  return `
                    <tr>
                        <td>${admin.username}</td>
                        <td>${admin.email || "无邮箱"}</td>
                        <td>${roleLabel}</td>
                        <td>${createdAt}</td>
                        <td class="text-right">${deleteBtn}</td>
                    </tr>
                  `;
                })
                .join("")}
          </tbody>
      </table>
    `;
  } catch (error) {
    console.error("加载管理员列表失败:", error);
  }
}

function switchTab(tabName) {
  if (tabName === adminState.currentTab) {
    return;
  }

  adminState.currentTab = tabName;

  adminElements.tabs.forEach((tab) => {
    tab.classList.toggle("active", tab.dataset.tab === tabName);
  });

  adminElements.tabContents.forEach((content) => {
    content.classList.toggle("active", content.id === `${tabName}Content`);
  });

  if (tabName === "apk") {
    loadApkInfo();
  }

  if (tabName === "admins") {
    loadAdmins();
  }
}

async function deleteDevice(deviceId, username) {
  if (!confirm(`确定要删除设备 "${username}" 吗？`)) {
    return;
  }

  try {
    await fetchAPI(`/api/admin/devices/${deviceId}`, { method: "DELETE" });
    alert("删除成功");
    await Promise.all([loadDevices(), loadStats()]);
  } catch (error) {
    alert(`删除失败: ${error.message}`);
  }
}

function showCreateDeviceModal() {
  const username = prompt("请输入设备用户名:");
  if (!username) {
    return;
  }

  const password = prompt("请输入设备密码:");
  if (!password) {
    return;
  }

  createDevice(username, password);
}

async function createDevice(username, password) {
  try {
    await fetchAPI("/api/admin/devices", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    });
    alert("创建成功");
    await Promise.all([loadDevices(), loadStats()]);
  } catch (error) {
    alert(`创建失败: ${error.message}`);
  }
}

function showCreateAdminModal() {
  alert("管理员管理功能开发中，敬请期待。");
}

function deleteAdmin(adminId, username) {
  console.warn("管理员删除功能待后端支持", adminId);
  alert(`管理员 ${username} 删除功能暂未开放。`);
}

function setApkMessage(type, text) {
  if (!adminElements.apkMessage) {
    return;
  }
  adminElements.apkMessage.textContent = text || "";
  adminElements.apkMessage.classList.remove("error", "success");
  if (type) {
    adminElements.apkMessage.classList.add(type);
  }
}

function formatBytes(bytes) {
  if (bytes === null || bytes === undefined || Number.isNaN(Number(bytes))) {
    return "-";
  }
  const value = Number(bytes);
  if (value <= 0) {
    return "-";
  }
  const units = ["B", "KB", "MB", "GB", "TB"];
  const exponent = Math.min(Math.floor(Math.log(value) / Math.log(1024)), units.length - 1);
  const result = value / 1024 ** exponent;
  const formatted = exponent === 0 ? result.toFixed(0) : result.toFixed(1);
  return `${formatted} ${units[exponent]}`;
}

function formatDate(value) {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString("zh-CN");
}

async function fetchAPI(url, options = {}) {
  const headers = {
    Authorization: `Bearer ${adminState.token}`,
    ...(options.headers || {}),
  };

  const isFormData = options.body instanceof FormData;
  if (!isFormData && !("Content-Type" in headers)) {
    headers["Content-Type"] = "application/json";
  }

  const response = await fetch(url, {
    ...options,
    headers,
  });

  if (response.status === 401) {
    logout();
    throw new Error("认证失败");
  }

  if (!response.ok) {
    try {
      const error = await response.json();
      throw new Error(error.detail || "请求失败");
    } catch {
      throw new Error("请求失败");
    }
  }

  return response;
}

function logout() {
  clearInterval(adminState.refreshTimer);
  adminState.token = "";
  localStorage.removeItem("auth_token");
  localStorage.removeItem("auth_username");
  localStorage.removeItem("auth_role");
  window.location.href = "/login";
}

function startAutoRefresh() {
  adminState.refreshTimer = setInterval(() => {
    loadStats();
    if (adminState.currentTab === "devices") {
      loadDevices();
    }
  }, 5000);
}
