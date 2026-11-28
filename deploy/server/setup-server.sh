#!/usr/bin/env bash
# MyOJ 服务器一次性准备：给 GitHub Actions 自动部署建好账号、目录和钥匙。
#
# 用 root 在服务器上执行，先用 --check 看一遍（只检查，不改任何东西）：
#   sudo bash setup-server.sh --check
#   sudo bash setup-server.sh --nginx-conf <前端仓库>/deploy/oj-frontend/nginx.conf
#
# 会做的事（可以重复执行，做过的步骤会跳过）：
#   1. 检查 docker、compose 和 .env 里的变量；新的 compose 文件和 nginx.conf 先校验，再给你看改了什么
#   2. 建部署账号 deploy，加入 docker 组
#   3. 把 deploy.sh 装到 /opt/my-oj-deploy/（root 所有），写 /etc/my-oj-deploy.conf
#   4. 前端改成「版本目录 + current 链接」：把现在的 oj-frontend/dist 复制成第一个版本
#   5. 把正在运行的后端镜像记成第一个版本（my-oj-backend:initial），以后可以切回去
#   6. 换上新的 docker-compose.yml 和 nginx.conf，重建 nginx 容器（网站中断几秒）
#   7. 生成部署专用的 SSH 钥匙：公钥限定只能执行 deploy.sh，私钥打印出来贴到 GitHub 后就删掉
#
# 不会动：.env 里已有的值、证书、数据库数据、沙箱、sshd 配置。

set -Eeuo pipefail
umask 022

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
REPO_DEPLOY_DIR=$(cd -- "$SCRIPT_DIR/.." && pwd -P)
readonly SCRIPT_DIR REPO_DEPLOY_DIR

readonly DEPLOY_USER=deploy
readonly INSTALL_DIR=/opt/my-oj-deploy
readonly CONFIG_FILE=/etc/my-oj-deploy.conf
readonly KEY_COMMENT=my-oj-github-deploy
readonly FRONTEND_CONTAINER=my-oj-frontend
readonly BACKEND_CONTAINER=my-oj-backend
readonly REQUIRED_ENV_VARS=(MYSQL_ROOT_PASSWORD MYSQL_PASSWORD CODESANDBOX_URL CODESANDBOX_AUTH_KEY_ID
    CODESANDBOX_AUTH_PRIVATE_KEY AI_KEY_ENCRYPTION_KEY)

MODE=setup
MY_OJ_DIR=""
NGINX_CONF_SRC=""
PUBLIC_HOST=""
ROTATE_KEY=false
ASSUME_YES=false
PRIVATE_KEY=""

say() { printf '\033[1;32m==>\033[0m %s\n' "$*"; }
info() { printf '    %s\n' "$*"; }
warn() { printf '\033[1;33m注意：\033[0m%s\n' "$*"; }
die() {
    printf '\033[1;31m停止：\033[0m%s\n' "$*" >&2
    exit 1
}

trap 'die "第 $LINENO 行的命令出错（上面是它的输出）。服务器上已经做过的步骤都可以重新执行，修好后再运行一次本脚本即可。"' ERR

usage() {
    cat <<'EOF'
用法：sudo bash setup-server.sh [选项]
  --check              只检查并显示服务器信息，不做任何改动
  --nginx-conf <文件>  新的 nginx.conf（前端仓库的 deploy/oj-frontend/nginx.conf），正式执行时必填
  --dir <目录>         my-oj 目录（默认从正在运行的容器自动找）
  --host <IP或域名>    GitHub 连服务器用的地址（默认从阿里云元数据读公网 IP，读不到会问你）
  --rotate-key         重新生成部署钥匙（旧钥匙立即失效，要把新私钥重新贴到 GitHub）
  --yes                不再逐项确认
EOF
}

while (($# > 0)); do
    case $1 in
        --check) MODE=check ;;
        --nginx-conf) NGINX_CONF_SRC=${2:?--nginx-conf 后面要跟文件路径}; shift ;;
        --dir) MY_OJ_DIR=${2:?--dir 后面要跟目录}; shift ;;
        --host) PUBLIC_HOST=${2:?--host 后面要跟地址}; shift ;;
        --rotate-key) ROTATE_KEY=true ;;
        --yes) ASSUME_YES=true ;;
        -h | --help) usage; exit 0 ;;
        *) usage; die "不认识的参数：$1" ;;
    esac
    shift
done

confirm() {
    local answer
    if $ASSUME_YES; then
        return 0
    fi
    read -r -p "$1 [y/N] " answer
    [[ $answer == [yY] || $answer == [yY][eE][sS] ]]
}

compose() {
    docker compose --project-directory "$MY_OJ_DIR" -f "$MY_OJ_DIR/docker-compose.yml" "$@"
}

# region 检测

detect_dir() {
    local name dir
    if [[ -n $MY_OJ_DIR ]]; then
        MY_OJ_DIR=$(cd -- "$MY_OJ_DIR" && pwd -P)
        return
    fi
    for name in "$FRONTEND_CONTAINER" "$BACKEND_CONTAINER" my-oj-db; do
        dir=$(docker inspect -f '{{ index .Config.Labels "com.docker.compose.project.working_dir" }}' "$name" 2>/dev/null || true)
        if [[ -n $dir && $dir != "<no value>" && -d $dir ]]; then
            MY_OJ_DIR=$dir
            return
        fi
    done
    die "没找到正在运行的 my-oj 容器，判断不出 my-oj 目录。请用 --dir 指定（就是放 docker-compose.yml 和 .env 的目录）"
}

detect_ssh_port() {
    local sshd port
    sshd=$(command -v sshd || echo /usr/sbin/sshd)
    port=$("$sshd" -T 2>/dev/null | awk '$1 == "port" { print $2; exit }' || true)
    printf '%s\n' "${port:-22}"
}

# 阿里云元数据服务（只在实例内部能访问，不经过外网）
detect_public_ip() {
    local path ip
    command -v curl >/dev/null || return 0
    for path in eipv4 public-ipv4; do
        ip=$(curl -fsS --max-time 2 "http://100.100.100.200/latest/meta-data/$path" 2>/dev/null || true)
        if [[ $ip =~ ^[0-9]+(\.[0-9]+){3}$ ]]; then
            printf '%s\n' "$ip"
            return 0
        fi
    done
}

missing_env_vars() {
    local name
    for name in "${REQUIRED_ENV_VARS[@]}"; do
        if ! grep -qE "^[[:space:]]*${name}=[^[:space:]]" "$MY_OJ_DIR/.env"; then
            printf '%s\n' "$name"
        fi
    done
}

key_installed() {
    local home
    home=$(getent passwd "$DEPLOY_USER" | cut -d: -f6) || return 1
    grep -qs "$KEY_COMMENT" "$home/.ssh/authorized_keys"
}

preflight() {
    [[ $(id -u) -eq 0 ]] || die "请用 root 执行：sudo bash $0 ..."
    local command_name
    for command_name in docker flock tar ssh-keygen useradd usermod runuser getent cmp diff find base64; do
        command -v "$command_name" >/dev/null || die "缺少命令 $command_name"
    done
    docker compose version >/dev/null 2>&1 || die "没有 docker compose（v2）。现在的 compose 文件需要它"
    detect_dir
    [[ -f $MY_OJ_DIR/docker-compose.yml ]] || die "$MY_OJ_DIR 下没有 docker-compose.yml"
    [[ -f $MY_OJ_DIR/.env ]] || die "$MY_OJ_DIR 下没有 .env"
}

report() {
    local os ip port missing
    os=$(. /etc/os-release 2>/dev/null && printf '%s' "${PRETTY_NAME:-未知}" || echo 未知)
    ip=${PUBLIC_HOST:-$(detect_public_ip)}
    port=$(detect_ssh_port)
    say "服务器信息"
    info "系统：$os"
    info "my-oj 目录：$MY_OJ_DIR"
    info "公网 IP：${ip:-读不到（到阿里云控制台的实例列表里看）}"
    info "SSH 端口：$port"
    info "内存：$(free -m | awk '/^Mem:/ { printf "共 %s MB，可用 %s MB", $2, $7 }')"
    info "磁盘：$(df -h "$MY_OJ_DIR" | awk 'NR == 2 { printf "共 %s，剩余 %s", $2, $4 }')"

    say "正在运行的 my-oj 容器"
    docker ps -a --filter name=my-oj- --format '    {{.Names}}  {{.Status}}  {{.Image}}'

    say ".env 里的变量（只看有没有值，不显示内容）"
    missing=$(missing_env_vars)
    local name
    for name in "${REQUIRED_ENV_VARS[@]}"; do
        if grep -qx "$name" <<<"$missing"; then
            info "$name：缺少"
        else
            info "$name：已设置"
        fi
    done

    say "sshd 设置"
    local sshd
    sshd=$(command -v sshd || echo /usr/sbin/sshd)
    "$sshd" -T 2>/dev/null | awk '$1 ~ /^(passwordauthentication|pubkeyauthentication|permitrootlogin|allowusers|allowgroups|denyusers|denygroups)$/ { print "    " $0 }' || true

    say "自动部署准备情况"
    if id "$DEPLOY_USER" >/dev/null 2>&1; then info "部署账号 $DEPLOY_USER：已建"; else info "部署账号 $DEPLOY_USER：未建"; fi
    if [[ -x $INSTALL_DIR/deploy.sh ]]; then info "部署脚本：已安装"; else info "部署脚本：未安装"; fi
    if [[ -L $MY_OJ_DIR/oj-frontend/www/current ]]; then info "前端版本目录：已建"; else info "前端版本目录：未建"; fi
    if key_installed; then info "部署钥匙：已配置"; else info "部署钥匙：未配置"; fi
}

# endregion

# region 准备

# .env 只缺 AI_KEY_ENCRYPTION_KEY 时可以替你生成；缺别的变量要你自己补
ensure_env() {
    local missing value
    missing=$(missing_env_vars)
    [[ -n $missing ]] || return 0
    if [[ $missing != AI_KEY_ENCRYPTION_KEY ]]; then
        die ".env 缺少这些变量：$(tr '\n' ' ' <<<"$missing")。按 my-oj-backend 仓库 deploy/.env.example 的说明补上后再运行"
    fi
    warn ".env 里没有 AI_KEY_ENCRYPTION_KEY（新版后端加密用户 AI Key 的主密钥，缺了后端起不来）"
    info "现在线上还没有用它加密过任何数据，可以新生成一个。生成后不要再换，否则已保存的用户 Key 都解不开。"
    confirm "生成一个随机值写进 .env？" || die "请自己在 .env 里加上 AI_KEY_ENCRYPTION_KEY（openssl rand -base64 32 生成）后再运行"
    value=$(head -c 32 /dev/urandom | base64 -w0)
    cp -p "$MY_OJ_DIR/.env" "$MY_OJ_DIR/.env.before-setup"
    if grep -qE '^[[:space:]]*AI_KEY_ENCRYPTION_KEY=' "$MY_OJ_DIR/.env"; then
        sed -i "s|^[[:space:]]*AI_KEY_ENCRYPTION_KEY=.*|AI_KEY_ENCRYPTION_KEY=$value|" "$MY_OJ_DIR/.env"
    else
        [[ -z $(tail -c 1 "$MY_OJ_DIR/.env") ]] || printf '\n' >>"$MY_OJ_DIR/.env"
        printf 'AI_KEY_ENCRYPTION_KEY=%s\n' "$value" >>"$MY_OJ_DIR/.env"
    fi
    say "已写入 .env（原文件备份为 .env.before-setup，确认没问题后可以删掉）"
}

# 新 compose 和 nginx.conf 先校验，再把和线上的差别给你看
check_new_files() {
    [[ -n $NGINX_CONF_SRC ]] || die "正式执行需要 --nginx-conf（前端仓库的 deploy/oj-frontend/nginx.conf）"
    [[ -f $NGINX_CONF_SRC ]] || die "找不到 $NGINX_CONF_SRC"
    grep -q '/usr/share/nginx/www/current' "$NGINX_CONF_SRC" ||
        die "$NGINX_CONF_SRC 不是新版的 nginx.conf（应该来自前端仓库的 ci/github-actions 分支或更新的 master）"
    grep -q 'my-oj-backend:current' "$REPO_DEPLOY_DIR/docker-compose.yml" ||
        die "$REPO_DEPLOY_DIR/docker-compose.yml 不是新版的（应该来自后端仓库的 ci/github-actions 分支或更新的 master）"

    say "校验新的 docker-compose.yml"
    docker compose --project-directory "$MY_OJ_DIR" -f "$REPO_DEPLOY_DIR/docker-compose.yml" config -q ||
        die "新的 docker-compose.yml 校验不通过（看上面的报错，通常是 .env 缺变量）"

    if docker inspect "$FRONTEND_CONTAINER" >/dev/null 2>&1; then
        say "在现在的 nginx 容器里检查新的 nginx.conf"
        docker cp "$NGINX_CONF_SRC" "$FRONTEND_CONTAINER:/tmp/nginx-candidate.conf"
        if ! docker exec "$FRONTEND_CONTAINER" nginx -t -q -c /tmp/nginx-candidate.conf; then
            docker exec "$FRONTEND_CONTAINER" rm -f /tmp/nginx-candidate.conf || true
            die "新的 nginx.conf 检查不通过（看上面的报错，比如证书文件名和服务器上的不一样）"
        fi
        docker exec "$FRONTEND_CONTAINER" rm -f /tmp/nginx-candidate.conf || true
    else
        warn "nginx 容器不存在，跳过 nginx.conf 的预检查"
    fi

    say "docker-compose.yml 的变化（- 是服务器上现在的，+ 是新的）"
    diff -u "$MY_OJ_DIR/docker-compose.yml" "$REPO_DEPLOY_DIR/docker-compose.yml" | sed 's/^/    /' || true
    say "nginx.conf 的变化"
    diff -u "$MY_OJ_DIR/oj-frontend/nginx.conf" "$NGINX_CONF_SRC" | sed 's/^/    /' || true
    confirm "上面的变化确认没问题，继续？" || die "已取消，服务器没有任何改动"
}

ensure_user() {
    if id "$DEPLOY_USER" >/dev/null 2>&1; then
        say "部署账号 $DEPLOY_USER 已存在"
    else
        useradd --create-home --shell /bin/bash "$DEPLOY_USER"
        say "建好部署账号 $DEPLOY_USER（没有密码，只能用钥匙登录）"
    fi
    usermod -aG docker "$DEPLOY_USER"
}

# my-oj 在 /root 这类别人进不去的目录下时，只给部署账号开「进入」权限（不能列出内容）
grant_traverse() {
    local path=$1 current="" part
    local -a parts
    IFS=/ read -r -a parts <<<"${path#/}"
    for part in "${parts[@]}"; do
        current="$current/$part"
        if runuser -u "$DEPLOY_USER" -- test -x "$current"; then
            continue
        fi
        command -v setfacl >/dev/null ||
            die "部署账号进不了 $current，系统又没有 setfacl。可以安装 acl 软件包后重试，或把 my-oj 目录移到 /opt 下"
        setfacl -m "u:$DEPLOY_USER:--x" "$current"
        say "给部署账号开了 $current 的进入权限（只能进入，不能列出里面的文件）"
    done
}

install_script() {
    install -d -m 755 -o root -g root "$INSTALL_DIR"
    install -m 755 -o root -g root "$SCRIPT_DIR/deploy.sh" "$INSTALL_DIR/deploy.sh"
    printf 'MY_OJ_DIR=%s\n' "$MY_OJ_DIR" >"$CONFIG_FILE"
    chmod 644 "$CONFIG_FILE"
    say "部署脚本装到了 $INSTALL_DIR/deploy.sh"
}

prepare_dirs() {
    install -d -m 755 -o "$DEPLOY_USER" -g "$DEPLOY_USER" \
        "$MY_OJ_DIR/.deploy" "$MY_OJ_DIR/.deploy/frontend" "$MY_OJ_DIR/.deploy/backend"
    install -d -m 755 "$MY_OJ_DIR/oj-backend"
    chown -R "$DEPLOY_USER:$DEPLOY_USER" "$MY_OJ_DIR/oj-backend"
    # compose 要读 .env：root 可读写，部署账号只读，其他人不可读
    chgrp "$DEPLOY_USER" "$MY_OJ_DIR/.env"
    chmod 640 "$MY_OJ_DIR/.env"
}

record_backend() {
    local state=$MY_OJ_DIR/.deploy/backend image
    if [[ -s $state/current ]]; then
        say "后端版本记录已存在（当前：$(cat "$state/current")）"
        return
    fi
    image=$(docker inspect -f '{{.Image}}' "$BACKEND_CONTAINER" 2>/dev/null || true)
    if [[ -z $image ]]; then
        warn "后端容器不存在，第一次自动部署时会新建"
        return
    fi
    docker tag "$image" my-oj-backend:initial
    docker tag "$image" my-oj-backend:current
    printf 'initial\n' >"$state/current"
    printf '%s deploy initial\n' "$(date '+%F %T')" >>"$state/history"
    chown "$DEPLOY_USER:$DEPLOY_USER" "$state/current" "$state/history"
    say "正在运行的后端镜像记成了第一个版本 my-oj-backend:initial"
}

migrate_frontend() {
    local www=$MY_OJ_DIR/oj-frontend/www state=$MY_OJ_DIR/.deploy/frontend name
    if [[ -L $www/current ]]; then
        say "前端已经是版本目录结构"
        return
    fi
    [[ -f $MY_OJ_DIR/oj-frontend/dist/index.html ]] || die "没找到现在的前端文件 $MY_OJ_DIR/oj-frontend/dist/index.html"
    name="$(date '+%Y%m%d-%H%M%S')-initial"
    install -d -m 755 "$www" "$www/releases"
    cp -a "$MY_OJ_DIR/oj-frontend/dist" "$www/releases/$name"
    (cd -- "$www/releases/$name" && find . \( -type f -o -type l \) -printf '%P\n' | sort) >"$state/$name.files"
    ln -s "releases/$name" "$www/current"
    chown -R "$DEPLOY_USER:$DEPLOY_USER" "$www" "$state"
    chmod -R a+rX "$www"
    say "现在的前端复制成了第一个版本 $name（原来的 oj-frontend/dist 不动，确认没问题后可以删掉）"
}

wait_frontend() {
    local i
    for i in $(seq 1 30); do
        if docker exec "$FRONTEND_CONTAINER" test -f /usr/share/nginx/www/current/index.html 2>/dev/null &&
            docker exec "$FRONTEND_CONTAINER" wget -q -O /dev/null http://127.0.0.1/healthz 2>/dev/null; then
            return 0
        fi
        sleep 1
    done
    return 1
}

switch_config() {
    local compose_file=$MY_OJ_DIR/docker-compose.yml nginx_conf=$MY_OJ_DIR/oj-frontend/nginx.conf
    local compose_backup=$MY_OJ_DIR/.deploy/backend/docker-compose.before-setup.yml
    local nginx_backup=$MY_OJ_DIR/.deploy/frontend/nginx.conf.before-setup
    local release
    release=$(readlink "$MY_OJ_DIR/oj-frontend/www/current")
    release=${release##*/}

    if cmp -s "$REPO_DEPLOY_DIR/docker-compose.yml" "$compose_file" && cmp -s "$NGINX_CONF_SRC" "$nginx_conf" && wait_frontend; then
        say "compose 文件和 nginx.conf 已经是新版本，nginx 也在用版本目录"
    else
        [[ -f $compose_backup ]] || cp -p "$compose_file" "$compose_backup"
        [[ -f $nginx_backup ]] || cp -p "$nginx_conf" "$nginx_backup"
        cat "$REPO_DEPLOY_DIR/docker-compose.yml" >"$compose_file"
        cat "$NGINX_CONF_SRC" >"$nginx_conf"
        say "重建 nginx 容器（网站会中断几秒）"
        if ! compose up -d --no-deps --force-recreate "$FRONTEND_CONTAINER" || ! wait_frontend; then
            warn "新的 nginx 容器没起来，恢复原来的配置"
            cat "$compose_backup" >"$compose_file"
            cat "$nginx_backup" >"$nginx_conf"
            compose up -d --no-deps --force-recreate "$FRONTEND_CONTAINER" || true
            die "nginx 切换失败，已恢复原来的配置。请把上面的输出发给我"
        fi
        say "nginx 已改用版本目录，网站恢复访问"
    fi
    cp "$NGINX_CONF_SRC" "$MY_OJ_DIR/.deploy/frontend/$release.nginx.conf"
    chown "$DEPLOY_USER:$DEPLOY_USER" "$compose_file" "$nginx_conf" \
        "$MY_OJ_DIR/.deploy/frontend/$release.nginx.conf"
}

setup_key() {
    local home ssh_dir auth tmp
    home=$(getent passwd "$DEPLOY_USER" | cut -d: -f6)
    ssh_dir=$home/.ssh
    auth=$ssh_dir/authorized_keys
    if key_installed && ! $ROTATE_KEY; then
        say "部署钥匙已经配置过（要换新钥匙，加 --rotate-key 重新运行）"
        return
    fi
    tmp=$(mktemp -d)
    chmod 700 "$tmp"
    ssh-keygen -q -t ed25519 -N '' -C "$KEY_COMMENT" -f "$tmp/key"
    {
        if [[ -f $auth ]]; then
            grep -v "$KEY_COMMENT" "$auth" || true
        fi
        printf 'command="%s",restrict %s\n' "$INSTALL_DIR/deploy.sh" "$(cat "$tmp/key.pub")"
    } >"$tmp/authorized_keys"
    # 钥匙文件归 root，部署账号自己改不了「只能执行 deploy.sh」这个限制
    install -d -m 755 -o root -g root "$ssh_dir"
    install -m 644 -o root -g root "$tmp/authorized_keys" "$auth"
    if command -v restorecon >/dev/null; then
        restorecon -R "$ssh_dir" || true
    fi
    PRIVATE_KEY=$(cat "$tmp/key")
    rm -rf -- "$tmp"
    say "生成了部署钥匙，公钥限定只能执行 $INSTALL_DIR/deploy.sh"
}

check_sshd_access() {
    local sshd allow_users allow_groups
    sshd=$(command -v sshd || echo /usr/sbin/sshd)
    allow_users=$("$sshd" -T 2>/dev/null | awk '$1 == "allowusers" { $1 = ""; print }' || true)
    allow_groups=$("$sshd" -T 2>/dev/null | awk '$1 == "allowgroups" { $1 = ""; print }' || true)
    if [[ -n $allow_users$allow_groups ]] && ! grep -qw "$DEPLOY_USER" <<<"$allow_users"; then
        warn "sshd 配置了 AllowUsers/AllowGroups，部署账号可能登录不了。请在 /etc/ssh/sshd_config 的 AllowUsers 里加上 $DEPLOY_USER 并重启 sshd"
    fi
}

print_secrets() {
    local host=$1 port=$2 prefix file
    if [[ $port == 22 ]]; then prefix=$host; else prefix="[$host]:$port"; fi
    printf '\n\033[1;36m%s\033[0m\n' "======== 把下面的值填进 GitHub（前端、后端两个仓库的 production 环境）========"
    printf '\nDEPLOY_HOST\n%s\n' "$host"
    printf '\nDEPLOY_PORT\n%s\n' "$port"
    printf '\nDEPLOY_KNOWN_HOSTS（多行，整段复制）\n'
    for file in /etc/ssh/ssh_host_ed25519_key.pub /etc/ssh/ssh_host_ecdsa_key.pub /etc/ssh/ssh_host_rsa_key.pub; do
        if [[ -r $file ]]; then
            printf '%s %s\n' "$prefix" "$(cut -d' ' -f1,2 "$file")"
        fi
    done
    if [[ -n $PRIVATE_KEY ]]; then
        printf '\nDEPLOY_SSH_KEY（从 BEGIN 那行到 END 那行整段复制）\n%s\n' "$PRIVATE_KEY"
        printf '\n\033[1;33m%s\033[0m\n' "私钥只显示这一次，服务器上没有留存。贴进 GitHub 后执行 clear 清屏，不要发给任何人。"
    else
        printf '\nDEPLOY_SSH_KEY：钥匙之前已经配置过，这次没有重新生成。\n'
        printf '如果 GitHub 里还没填或填错了，加 --rotate-key 重新运行一次，会生成新钥匙并显示。\n'
    fi
    printf '\033[1;36m%s\033[0m\n\n' "==========================================================================="
}

run_setup() {
    local host port
    ensure_env
    check_new_files
    host=${PUBLIC_HOST:-$(detect_public_ip)}
    while [[ -z $host ]]; do
        read -r -p "读不到公网 IP，请输入服务器公网 IP（阿里云控制台的实例列表里有）：" host
    done
    port=$(detect_ssh_port)

    ensure_user
    grant_traverse "$MY_OJ_DIR"
    install_script
    prepare_dirs
    record_backend
    migrate_frontend
    switch_config
    setup_key
    check_sshd_access

    say "用部署账号试运行一次 deploy.sh status"
    runuser -u "$DEPLOY_USER" -- "$INSTALL_DIR/deploy.sh" status

    print_secrets "$host" "$port"
    say "服务器准备好了。接下来把上面的值填进 GitHub，然后告诉 Claude 可以合并了。"
}

# endregion

preflight
if [[ $MODE == check ]]; then
    report
    printf '\n只做了检查，服务器没有任何改动。\n'
else
    report
    run_setup
fi
