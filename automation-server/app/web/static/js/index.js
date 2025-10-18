// 页面初始化后根据登录状态执行重定向
const token = localStorage.getItem("auth_token");
const role = localStorage.getItem("auth_role");

if (token) {
  if (["admin", "super_admin"].includes(role || "")) {
    window.location.href = "/admin";
  } else {
    window.location.href = "/customer";
  }
} else {
  window.location.href = "/login";
}
