// 登录页脚本：处理统一登录及角色跳转
const loginForm = document.getElementById("loginForm");
const usernameInput = document.getElementById("username");
const passwordInput = document.getElementById("password");
const loginBtn = document.getElementById("loginBtn");
const errorMessage = document.getElementById("errorMessage");
const loginContainer = document.querySelector(".auth-card");

const showError = (message) => {
  errorMessage.textContent = message;
  errorMessage.classList.add("show");
};

const hideError = () => {
  errorMessage.classList.remove("show");
};

const shakeContainer = () => {
  loginContainer.classList.add("shake");
  setTimeout(() => {
    loginContainer.classList.remove("shake");
  }, 300);
};

function clearLegacyStorage() {
  localStorage.removeItem("admin_token");
  localStorage.removeItem("admin_username");
  localStorage.removeItem("is_super_admin");
  localStorage.removeItem("customer_token");
  localStorage.removeItem("customer_username");
}

async function requestProfile(url, token) {
  const response = await fetch(url, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    return { ok: false, status: response.status, data };
  }
  return { ok: true, data };
}

async function identifyRole(token) {
  const adminResp = await requestProfile("/api/admin/me", token);
  if (adminResp.ok) {
    return { profile: adminResp.data, role: adminResp.data.role };
  }

  if (adminResp.status !== 401 && adminResp.status !== 403) {
    throw new Error(adminResp.data.detail || "权限验证失败");
  }

  const customerResp = await requestProfile("/api/customer/me", token);
  if (customerResp.ok) {
    return { profile: customerResp.data, role: customerResp.data.role || "user" };
  }

  throw new Error(customerResp.data.detail || "账号权限异常");
}

function cacheSession(token, profile, role) {
  clearLegacyStorage();
  localStorage.setItem("auth_token", token);
  localStorage.setItem("auth_username", profile.username);
  localStorage.setItem("auth_role", role);
}

function redirectByRole(role) {
  if (role === "admin" || role === "super_admin") {
    window.location.href = "/admin";
    return;
  }
  window.location.href = "/customer";
}

loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  const username = usernameInput.value.trim();
  const password = passwordInput.value;

  if (!username || !password) {
    showError("请填写完整信息");
    return;
  }

  loginBtn.disabled = true;
  loginBtn.textContent = "登录中...";
  hideError();

  try {
    const response = await fetch("/api/auth/web/login", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ username, password }),
    });

    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      showError(data.detail || "登录失败");
      shakeContainer();
      return;
    }

    const data = await response.json();
    const { profile, role } = await identifyRole(data.access_token);
    cacheSession(data.access_token, profile, role);
    redirectByRole(role);
  } catch (error) {
    showError(error.message || "网络错误，请稍后重试");
    shakeContainer();
  } finally {
    loginBtn.disabled = false;
    loginBtn.textContent = "登录";
  }
});

window.addEventListener("DOMContentLoaded", () => {
  const token = localStorage.getItem("auth_token");
  const role = localStorage.getItem("auth_role");

  if (token && role) {
    redirectByRole(role);
  } else {
    clearLegacyStorage();
  }
});
