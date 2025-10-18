#!/usr/bin/env bash
# Android Automation Server - One-click deployment

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}" )" && pwd)
PROJECT_ROOT="$SCRIPT_DIR"
CONFIG_FILE=${CONFIG_FILE:-"$PROJECT_ROOT/deploy.env"}

log() { printf '\033[0;32m[%s]\033[0m %s\n' "$(date '+%H:%M:%S')" "$1"; }
warn() { printf '\033[0;33m[WARN]\033[0m %s\n' "$1"; }
fail() { printf '\033[0;31m[ERR ] %s\033[0m\n' "$1" >&2; exit 1; }

create_example() {
  local sample_file="$PROJECT_ROOT/deploy.env.example"
  [[ -f "$sample_file" ]] && return
  cat <<'SAMPLE_ENV' > "$sample_file"
# 示例配置，请复制为 deploy.env 后修改
REMOTE_HOST="root@your-server"
SSH_PORT=22
REMOTE_DIR="/opt/android-automation"
PYTHON_BIN="/usr/bin/python3.11"
SERVICE_NAME="android-automation"
SERVICE_USER="root"
SERVICE_GROUP="root"
APP_PORT=8000
KEEP_RELEASES=5
FRONTEND_BUILD=false
# SSH_PASSWORD="your-password"
# ENV_FILE=".env.deploy"
SAMPLE_ENV
}

if [[ ! -f "$CONFIG_FILE" ]]; then
  create_example
  fail "未找到部署配置：$CONFIG_FILE。已生成 deploy.env.example，请复制为 deploy.env 并填写信息。"
fi

# shellcheck disable=SC1090
source "$CONFIG_FILE"

: "${REMOTE_HOST:?请在 deploy.env 中配置 REMOTE_HOST}"
: "${REMOTE_DIR:?请在 deploy.env 中配置 REMOTE_DIR}"
: "${SERVICE_NAME:?请在 deploy.env 中配置 SERVICE_NAME}"

SSH_PORT=${SSH_PORT:-22}
PYTHON_BIN=${PYTHON_BIN:-python3}
SERVICE_USER=${SERVICE_USER:-root}
SERVICE_GROUP=${SERVICE_GROUP:-$SERVICE_USER}
APP_PORT=${APP_PORT:-8000}
KEEP_RELEASES=${KEEP_RELEASES:-5}
FRONTEND_BUILD=${FRONTEND_BUILD:-true}
ENV_FILE=${ENV_FILE:-}

if [[ -n "${SSH_PASSWORD:-}" ]]; then
  SSH_CMD=(sshpass -p "$SSH_PASSWORD" ssh -p "$SSH_PORT" -o StrictHostKeyChecking=no "$REMOTE_HOST")
  SCP_CMD=(sshpass -p "$SSH_PASSWORD" scp -P "$SSH_PORT" -o StrictHostKeyChecking=no)
  RSYNC_CMD=(sshpass -p "$SSH_PASSWORD" rsync -az --delete -e "ssh -p $SSH_PORT -o StrictHostKeyChecking=no")
else
  SSH_CMD=(ssh -p "$SSH_PORT" -o StrictHostKeyChecking=no "$REMOTE_HOST")
  SCP_CMD=(scp -P "$SSH_PORT" -o StrictHostKeyChecking=no)
  RSYNC_CMD=(rsync -az --delete -e "ssh -p $SSH_PORT -o StrictHostKeyChecking=no")
fi

REQUIRED_CMDS=(ssh rsync python3)
if [[ -n "${SSH_PASSWORD:-}" ]]; then
  REQUIRED_CMDS+=(sshpass)
fi

for cmd in "${REQUIRED_CMDS[@]}"; do
  command -v "$cmd" >/dev/null 2>&1 || fail "缺少依赖命令：$cmd"
done

log "配置加载完成"

if [[ "$FRONTEND_BUILD" != "false" && -d "$PROJECT_ROOT/frontend" ]]; then
  if ! command -v npm >/dev/null 2>&1; then
    warn "未检测到 npm，将跳过前端构建。"
    FRONTEND_BUILD=false
  elif command -v node >/dev/null 2>&1; then
    NODE_MAJOR=$(node -v | sed 's/v\([0-9]*\).*/\1/')
    if (( NODE_MAJOR < 18 )); then
      warn "检测到 Node.js 版本 < 18，构建可能失败。"
    fi
  fi
fi

log "准备部署"

if [[ "$FRONTEND_BUILD" != "false" && -d "$PROJECT_ROOT/frontend" ]]; then
  log "安装前端依赖"
  pushd "$PROJECT_ROOT/frontend" >/dev/null
  npm install >/dev/null
  log "构建前端静态资源"
  npm run build >/dev/null
  popd >/dev/null

  log "同步静态资源到 app/web/static/frontend"
  rm -rf "$PROJECT_ROOT/app/web/static/frontend"
  mkdir -p "$PROJECT_ROOT/app/web/static/frontend"
  cp -R "$PROJECT_ROOT/frontend/dist/." "$PROJECT_ROOT/app/web/static/frontend/"
fi

RELEASE_NAME="release-$(date '+%Y%m%d%H%M%S')"
log "发布版本：$RELEASE_NAME"

log "创建远程目录"
"${SSH_CMD[@]}" "mkdir -p '$REMOTE_DIR/releases' '$REMOTE_DIR/shared/logs'"
"${SSH_CMD[@]}" "mkdir -p '$REMOTE_DIR/releases/$RELEASE_NAME'"

RSYNC_EXCLUDES=(
  "--exclude" ".git"
  "--exclude" ".idea"
  "--exclude" ".vscode"
  "--exclude" "*.pyc"
  "--exclude" "__pycache__"
  "--exclude" "*.db"
  "--exclude" "*.log"
  "--exclude" "venv"
  "--exclude" ".venv"
  "--exclude" "frontend/node_modules"
  "--exclude" "deploy.env"
  "--exclude" "deploy.env.example"
)

log "同步项目文件到远程 releases/$RELEASE_NAME"
"${RSYNC_CMD[@]}" "${RSYNC_EXCLUDES[@]}" "$PROJECT_ROOT/" "$REMOTE_HOST:$REMOTE_DIR/releases/$RELEASE_NAME/"

if [[ -n "$ENV_FILE" ]]; then
  if [[ -f "$ENV_FILE" ]]; then
    log "上传环境变量文件"
    "${SSH_CMD[@]}" "mkdir -p '$REMOTE_DIR/shared'"
    "${SCP_CMD[@]}" "$ENV_FILE" "$REMOTE_HOST:$REMOTE_DIR/shared/.env.new"
  else
    warn "ENV_FILE 指定的文件不存在：$ENV_FILE"
  fi
fi

log "执行远程部署"
"${SSH_CMD[@]}" "RELEASE_NAME='$RELEASE_NAME' REMOTE_DIR='$REMOTE_DIR' PYTHON_BIN='$PYTHON_BIN' SERVICE_NAME='$SERVICE_NAME' SERVICE_USER='$SERVICE_USER' SERVICE_GROUP='$SERVICE_GROUP' APP_PORT='$APP_PORT' KEEP_RELEASES='$KEEP_RELEASES' bash -s" <<'REMOTE_SCRIPT'
set -euo pipefail

cd "$REMOTE_DIR"

if [[ -f "shared/.env.new" ]]; then
  mv "shared/.env.new" "shared/.env"
fi

ensure_group() {
  local group_name="$1"
  if getent group "$group_name" >/dev/null 2>&1; then
    return 0
  fi
  if [[ "$group_name" == "root" ]]; then
    echo "[remote] group root 不需要创建" >&2
    return 0
  fi
  groupadd --system "$group_name"
}

ensure_user() {
  local user_name="$1"
  local primary_group="$2"
  if id "$user_name" >/dev/null 2>&1; then
    return 0
  fi
  if [[ "$user_name" == "root" ]]; then
    echo "[remote] user root 已存在" >&2
    return 0
  fi
  useradd --system --gid "$primary_group" --home-dir "$REMOTE_DIR" --shell /usr/sbin/nologin "$user_name"
}

ensure_group "$SERVICE_GROUP"
ensure_user "$SERVICE_USER" "$SERVICE_GROUP"

if [[ ! -d "$REMOTE_DIR/venv" ]]; then
  "$PYTHON_BIN" -m venv "$REMOTE_DIR/venv"
fi
source "$REMOTE_DIR/venv/bin/activate"

cd "$REMOTE_DIR/releases/$RELEASE_NAME"
python -m pip install --upgrade pip wheel setuptools >/dev/null
python -m pip install -r requirements.txt >/dev/null

if [[ -f "alembic.ini" ]]; then
  alembic upgrade head
else
  python -c 'from app.db.session import init_db; import asyncio; asyncio.run(init_db())'
fi

echo "[remote] 初始化默认账号"
python init_admin.py
python init_account.py

cd "$REMOTE_DIR"
ln -sfn "$REMOTE_DIR/releases/$RELEASE_NAME" "$REMOTE_DIR/current"

if [[ -f "$REMOTE_DIR/shared/.env" ]]; then
  ln -sfn "$REMOTE_DIR/shared/.env" "$REMOTE_DIR/current/.env"
fi
ln -sfn "$REMOTE_DIR/shared/logs" "$REMOTE_DIR/current/logs"

if [[ "$SERVICE_USER" != "root" ]]; then
  chown -R "$SERVICE_USER:$SERVICE_GROUP" "$REMOTE_DIR/shared"
  chown -R "$SERVICE_USER:$SERVICE_GROUP" "$REMOTE_DIR/current"
  chown -R "$SERVICE_USER:$SERVICE_GROUP" "$REMOTE_DIR/venv"
fi

cat <<SERVICE > /etc/systemd/system/$SERVICE_NAME.service
[Unit]
Description=Android Automation Server
After=network.target

[Service]
Type=simple
User=$SERVICE_USER
Group=$SERVICE_GROUP
WorkingDirectory=$REMOTE_DIR/current
Environment=PATH=$REMOTE_DIR/venv/bin:/usr/bin
Environment=PYTHONUNBUFFERED=1
EnvironmentFile=-$REMOTE_DIR/shared/.env
ExecStart=$REMOTE_DIR/venv/bin/uvicorn app.main:app --host 0.0.0.0 --port $APP_PORT
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
SERVICE

systemctl daemon-reload
systemctl stop $SERVICE_NAME 2>/dev/null || true
systemctl enable $SERVICE_NAME >/dev/null
systemctl start $SERVICE_NAME
sleep 2
if ! systemctl is-active --quiet $SERVICE_NAME; then
  systemctl status $SERVICE_NAME --no-pager
  exit 1
fi

echo "[remote] 服务已启动：$SERVICE_NAME"

cd "$REMOTE_DIR/releases"
ls -1dt */ 2>/dev/null | tail -n +$((KEEP_RELEASES + 1)) | xargs -r rm -rf
REMOTE_SCRIPT

REMOTE_HOST_NOUSER=$(echo "$REMOTE_HOST" | sed 's/^.*@//')
log "部署完成"
log "当前版本：$RELEASE_NAME"
log "访问地址：http://$REMOTE_HOST_NOUSER:$APP_PORT"
