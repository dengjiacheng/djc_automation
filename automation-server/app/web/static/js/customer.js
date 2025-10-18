// 客户页面脚本：拉取当前账户信息与设备列表
const customerState = {
  token: localStorage.getItem("auth_token"),
  role: localStorage.getItem("auth_role"),
  username: localStorage.getItem("auth_username"),
};

localStorage.removeItem("customer_token");

const customerElements = {
  info: document.getElementById("customerInfo"),
  table: document.getElementById("customerDeviceTable"),
  logoutBtn: document.getElementById("customerLogoutBtn"),
};

function renderLoading() {
  customerElements.table.innerHTML = '<div class="loading">加载中...</div>';
}

function renderEmpty() {
  customerElements.table.innerHTML = '<div class="empty-state">暂无绑定设备，如需添加请联系管理员</div>';
}

function renderDevices(devices) {
  if (!devices.length) {
    renderEmpty();
    return;
  }

  const rows = devices
    .map((device) => {
      const onlineClass = device.is_online ? "status-online" : "status-offline";
      const onlineText = device.is_online ? "在线" : "离线";
      const createdAt = device.created_at
        ? new Date(device.created_at).toLocaleString("zh-CN")
        : "-";

      return `
        <tr>
            <td>${device.device_name || "-"}</td>
            <td>${device.device_model || "-"}</td>
            <td>${device.android_version || "-"}</td>
            <td>${device.local_ip || "-"}</td>
            <td>${device.public_ip || "-"}</td>
            <td><span class="status-badge ${onlineClass}">${onlineText}</span></td>
            <td>${createdAt}</td>
        </tr>
      `;
    })
    .join("");

  customerElements.table.innerHTML = `
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
        <tbody>
            ${rows}
        </tbody>
    </table>
  `;
}

async function fetchWithAuth(url) {
  if (!customerState.token) {
    throw new Error("认证失效");
  }
  const response = await fetch(url, {
    headers: {
      Authorization: `Bearer ${customerState.token}`,
    },
  });
  if (response.status === 401 || response.status === 403) {
    throw new Error("认证失败");
  }
  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    throw new Error(data.detail || "请求失败");
  }
  return response.json();
}

async function loadCustomerProfile() {
  const profile = await fetchWithAuth("/api/customer/me");
  customerState.username = profile.username;
  localStorage.setItem("auth_username", profile.username);
  if (profile.role) {
    localStorage.setItem("auth_role", profile.role);
    customerState.role = profile.role;
  }
  customerElements.info.textContent = `欢迎, ${profile.username}`;
}

async function loadCustomerDevices() {
  renderLoading();
  const data = await fetchWithAuth("/api/customer/devices");
  renderDevices(data.devices || []);
}

function handleLogout() {
  localStorage.removeItem("auth_token");
  localStorage.removeItem("auth_role");
  localStorage.removeItem("auth_username");
  localStorage.removeItem("customer_token");
  localStorage.removeItem("customer_username");
  window.location.href = "/login";
}

async function initCustomerPage() {
  if (!customerState.token) {
    window.location.href = "/login";
    return;
  }

  if (customerState.role === "admin" || customerState.role === "super_admin") {
    window.location.href = "/admin";
    return;
  }

  try {
    await loadCustomerProfile();
    await loadCustomerDevices();
  } catch (error) {
    console.error("加载客户数据失败:", error);
    handleLogout();
  }
}

if (customerElements.logoutBtn) {
  customerElements.logoutBtn.addEventListener("click", handleLogout);
}

window.addEventListener("DOMContentLoaded", initCustomerPage);
