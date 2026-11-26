#!/usr/bin/env bash
# MyOJ 服务器端部署脚本：放好新版本 → 切换 → 检查，检查不过就切回上一版。
#
# 由 setup-server.sh 安装到 /opt/my-oj-deploy/deploy.sh（root 所有，部署账号改不了它）。
# GitHub Actions 用部署专用的 SSH 钥匙调用本脚本；这把钥匙在 authorized_keys 里被限定为只能执行本脚本，
# ssh 命令末尾写的内容（SSH_ORIGINAL_COMMAND）就是参数。也可以在服务器上手动执行，例如：
#   sudo -u deploy /opt/my-oj-deploy/deploy.sh status
#
# 命令：
#   frontend <提交号>           从标准输入读前端包（dist/ 和 nginx.conf 的 tar.gz），部署为新版本
#   backend <提交号>            从标准输入读后端包（my-oj.jar、Dockerfile、docker-compose.yml、create_table.sql 的 tar.gz）
#   rollback frontend|backend   切回上一个版本
#   status                      显示当前版本和最近的部署记录
#
# 仓库是公开的，GitHub Actions 的日志所有人都能看到：脚本输出只写版本号和步骤，
# 不输出容器日志、配置内容和任何秘密；排查用的容器日志只写进服务器上的部署日志（.deploy/deploy.log）。

set -Eeuo pipefail
umask 022

readonly DEFAULT_CONFIG=/etc/my-oj-deploy.conf
readonly KEEP_RELEASES=5
readonly MAX_UPLOAD_BYTES=$((400 * 1024 * 1024))
readonly HEALTH_TIMEOUT_SECONDS=300
readonly LOCK_WAIT_SECONDS=900

readonly DB_CONTAINER=my-oj-db
readonly BACKEND_SERVICE=my-oj-backend
readonly BACKEND_CONTAINER=my-oj-backend
readonly FRONTEND_CONTAINER=my-oj-frontend
readonly BACKEND_IMAGE=my-oj-backend
# 容器里看到的前端目录（compose 把 oj-frontend/www 挂到这里，nginx 的 root 是它下面的 current）
readonly CONTAINER_WWW=/usr/share/nginx/www

LOG_FILE=""
WORK_DIR=""

log() {
    local line
    line="[$(date '+%F %T')] $*"
    printf '%s\n' "$line"
    if [[ -n $LOG_FILE ]]; then
        printf '%s\n' "$line" >>"$LOG_FILE"
    fi
}

fail() {
    log "失败：$*"
    exit 1
}

usage() {
    cat <<'EOF'
用法：
  deploy.sh frontend <提交号>    < 前端包.tar.gz
  deploy.sh backend <提交号>     < 后端包.tar.gz
  deploy.sh rollback frontend|backend
  deploy.sh status
EOF
    exit 2
}

# region 配置

config_file=$DEFAULT_CONFIG
# 测试时可以用环境变量换配置文件；通过 SSH 调用时不接受（sshd 默认也不让客户端传环境变量）
if [[ -z ${SSH_CONNECTION:-} && -n ${MY_OJ_DEPLOY_CONF:-} ]]; then
    config_file=$MY_OJ_DEPLOY_CONF
fi
[[ -r $config_file ]] || fail "找不到配置文件 $config_file（先在服务器上运行 setup-server.sh）"
MY_OJ_DIR=$(sed -n 's/^MY_OJ_DIR=//p' "$config_file" | tail -n 1)
[[ $MY_OJ_DIR == /* && -f $MY_OJ_DIR/docker-compose.yml ]] ||
    fail "配置文件里的 MY_OJ_DIR 不对：${MY_OJ_DIR:-（空）}"
readonly MY_OJ_DIR

readonly STATE_DIR=$MY_OJ_DIR/.deploy
readonly FRONTEND_STATE=$STATE_DIR/frontend
readonly BACKEND_STATE=$STATE_DIR/backend
readonly COMPOSE_FILE=$MY_OJ_DIR/docker-compose.yml
readonly WWW_DIR=$MY_OJ_DIR/oj-frontend/www
readonly RELEASES_DIR=$WWW_DIR/releases
readonly NGINX_CONF=$MY_OJ_DIR/oj-frontend/nginx.conf

compose() {
    docker compose --project-directory "$MY_OJ_DIR" -f "$COMPOSE_FILE" "$@"
}

cleanup() {
    if [[ -n $WORK_DIR && -d $WORK_DIR ]]; then
        rm -rf -- "$WORK_DIR"
    fi
    rm -f -- "$WWW_DIR/.nginx-candidate.conf" "$WWW_DIR/.current.tmp" 2>/dev/null || true
}

trap cleanup EXIT
trap 'log "失败：脚本第 $LINENO 行的命令出错"' ERR

# endregion

# region 收包

# 从标准输入收部署包，解到 $1。只接受普通文件和目录，路径不能跳出目标目录。
receive_package() {
    local dest=$1 archive=$WORK_DIR/package.tar.gz size
    [[ ! -t 0 ]] || fail "没有从标准输入收到部署包"
    head -c $((MAX_UPLOAD_BYTES + 1)) >"$archive"
    size=$(stat -c %s "$archive")
    ((size > 0)) || fail "部署包是空的"
    ((size <= MAX_UPLOAD_BYTES)) || fail "部署包超过 $((MAX_UPLOAD_BYTES / 1024 / 1024)) MB"
    tar -tzf "$archive" >"$WORK_DIR/package.list" || fail "部署包不是有效的 tar.gz"
    if grep -qE '(^/|(^|/)\.\.(/|$))' "$WORK_DIR/package.list"; then
        fail "部署包里有不安全的路径"
    fi
    mkdir -p -- "$dest"
    tar -xzf "$archive" -C "$dest" --no-same-owner --no-same-permissions
    if [[ -n $(find "$dest" ! -type f ! -type d -print -quit) ]]; then
        fail "部署包里只能有普通文件和目录"
    fi
    log "收到部署包（$((size / 1024)) KB）"
}

# endregion

# region 前端

current_release() {
    local link
    link=$(readlink -- "$WWW_DIR/current" 2>/dev/null) || return 0
    printf '%s\n' "${link##*/}"
}

# 版本目录名是「时间-提交号」，按名字排序就是按时间排序
list_releases() {
    find "$RELEASES_DIR" -mindepth 1 -maxdepth 1 -type d ! -name '.*' -printf '%f\n' | sort
}

# 原子地把 current 指到 releases/$1（先建临时链接再改名，nginx 不会看到半成品）
switch_frontend() {
    ln -sfn -- "releases/$1" "$WWW_DIR/.current.tmp"
    mv -Tf -- "$WWW_DIR/.current.tmp" "$WWW_DIR/current"
}

frontend_ok() {
    docker exec "$FRONTEND_CONTAINER" test -f "$CONTAINER_WWW/current/index.html" &&
        docker exec "$FRONTEND_CONTAINER" wget -q -O /dev/null http://127.0.0.1/healthz
}

# 新 nginx.conf 和线上的不同时，先在运行中的容器里用 nginx -t 检查一遍
check_nginx_candidate() {
    local src=$1
    if cmp -s -- "$src" "$NGINX_CONF"; then
        return 0
    fi
    cp -- "$src" "$WWW_DIR/.nginx-candidate.conf"
    docker exec "$FRONTEND_CONTAINER" nginx -t -q -c "$CONTAINER_WWW/.nginx-candidate.conf"
}

# 把 $1 装成线上的 nginx.conf 并 reload，失败时恢复原来的内容。
# nginx.conf 是单文件挂载，必须原地写入：换成新文件（inode 变了）容器里就看不到了。
apply_nginx_conf() {
    local src=$1 backup=$WORK_DIR/nginx.conf.backup
    if cmp -s -- "$src" "$NGINX_CONF"; then
        return 0
    fi
    cp -- "$NGINX_CONF" "$backup"
    cat -- "$src" >"$NGINX_CONF"
    if docker exec "$FRONTEND_CONTAINER" nginx -t -q && docker exec "$FRONTEND_CONTAINER" nginx -s reload; then
        log "nginx.conf 已更新并生效"
        return 0
    fi
    cat -- "$backup" >"$NGINX_CONF"
    docker exec "$FRONTEND_CONTAINER" nginx -s reload || true
    log "nginx.conf 没能生效，已恢复原来的配置"
    return 1
}

# 把上一版本自己构建出来的 js/ 和 assets/ 文件补进新版本（新版本里没有同名文件时）。
# 用户开着旧页面再切路由，会去请求旧版本按需加载的文件，删掉就会加载失败。
# 只补上一版本「自己的」文件（它的构建清单里的），所以不会一代代累积下去。
carry_over_assets() {
    local previous=$1 target=$2 manifest path count=0
    [[ -n $previous ]] || return 0
    manifest=$FRONTEND_STATE/$previous.files
    if [[ ! -f $manifest || ! -d $RELEASES_DIR/$previous ]]; then
        return 0
    fi
    while IFS= read -r path; do
        case $path in
            js/* | assets/*) ;;
            *) continue ;;
        esac
        if [[ -e $target/$path || ! -f $RELEASES_DIR/$previous/$path ]]; then
            continue
        fi
        mkdir -p -- "$(dirname -- "$target/$path")"
        cp -p -- "$RELEASES_DIR/$previous/$path" "$target/$path"
        count=$((count + 1))
    done <"$manifest"
    log "从上一版本保留了 $count 个静态文件，开着旧页面的用户不受影响"
}

prune_frontend() {
    local current name total
    local -a releases
    current=$(current_release)
    mapfile -t releases < <(list_releases)
    total=${#releases[@]}
    if ((total <= KEEP_RELEASES)); then
        return 0
    fi
    for name in "${releases[@]:0:total-KEEP_RELEASES}"; do
        if [[ $name == "$current" ]]; then
            continue
        fi
        rm -rf -- "${RELEASES_DIR:?}/$name"
        rm -f -- "$FRONTEND_STATE/$name.files" "$FRONTEND_STATE/$name.nginx.conf"
        log "删除旧版本 $name"
    done
}

deploy_frontend() {
    local sha=$1 package=$WORK_DIR/package previous name staging
    receive_package "$package"
    [[ -f $package/dist/index.html ]] || fail "部署包里没有 dist/index.html"
    [[ -f $package/nginx.conf ]] || fail "部署包里没有 nginx.conf"
    check_nginx_candidate "$package/nginx.conf" ||
        fail "新的 nginx.conf 没有通过 nginx -t 检查，线上没有任何改动"

    previous=$(current_release)
    name="$(date '+%Y%m%d-%H%M%S')-$sha"
    rm -rf -- "$RELEASES_DIR"/.staging-*
    staging=$RELEASES_DIR/.staging-$name
    cp -a -- "$package/dist" "$staging"
    (cd -- "$staging" && find . \( -type f -o -type l \) -printf '%P\n' | sort) >"$FRONTEND_STATE/$name.files"
    cp -- "$package/nginx.conf" "$FRONTEND_STATE/$name.nginx.conf"
    carry_over_assets "$previous" "$staging"
    mv -T -- "$staging" "$RELEASES_DIR/$name"

    switch_frontend "$name"
    if ! apply_nginx_conf "$FRONTEND_STATE/$name.nginx.conf" || ! frontend_ok; then
        if [[ -n $previous ]]; then
            switch_frontend "$previous"
            if [[ -f $FRONTEND_STATE/$previous.nginx.conf ]]; then
                apply_nginx_conf "$FRONTEND_STATE/$previous.nginx.conf" || true
            fi
        fi
        fail "新版本切换后检查不通过，已切回 ${previous:-（没有上一版本）}"
    fi
    printf '%s deploy %s\n' "$(date '+%F %T')" "$name" >>"$FRONTEND_STATE/history"
    log "前端已切换到 $name（上一版本：${previous:-无}）"
    prune_frontend
}

rollback_frontend() {
    local current target="" name
    local -a releases
    current=$(current_release)
    mapfile -t releases < <(list_releases)
    for name in "${releases[@]}"; do
        if [[ $name == "$current" ]]; then
            break
        fi
        target=$name
    done
    [[ -n $target ]] || fail "没有比当前版本 ${current:-（无）} 更早的前端版本"
    switch_frontend "$target"
    if [[ -f $FRONTEND_STATE/$target.nginx.conf ]]; then
        apply_nginx_conf "$FRONTEND_STATE/$target.nginx.conf" || log "注意：nginx 仍在用当前的配置"
    fi
    frontend_ok || fail "切回 $target 后检查不通过"
    printf '%s rollback %s\n' "$(date '+%F %T')" "$target" >>"$FRONTEND_STATE/history"
    log "前端已切回 $target（原来是 $current）"
}

# endregion

# region 后端

backend_current() {
    cat -- "$BACKEND_STATE/current" 2>/dev/null || true
}

backend_state() {
    docker inspect -f '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' \
        "$BACKEND_CONTAINER" 2>/dev/null || echo "missing none"
}

# 后端换了容器，IP 可能变；nginx 只在加载配置时解析 upstream 的地址，所以要 reload 一下
reload_nginx() {
    docker exec "$FRONTEND_CONTAINER" nginx -s reload >/dev/null 2>&1 ||
        log "注意：nginx reload 失败，如果网站报 502，请在服务器上执行 docker restart $FRONTEND_CONTAINER"
}

wait_backend_healthy() {
    local deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS)) state=missing health=none
    log "等后端启动并通过健康检查（最多 $HEALTH_TIMEOUT_SECONDS 秒）"
    while ((SECONDS < deadline)); do
        read -r state health < <(backend_state)
        case "$state/$health" in
            running/healthy)
                log "后端健康检查通过"
                return 0
                ;;
            running/none)
                log "后端容器没有配置健康检查，无法确认新版本是否正常"
                break
                ;;
            */unhealthy | exited/* | dead/* | restarting/* | missing/*)
                break
                ;;
        esac
        sleep 5
    done
    log "后端没有通过健康检查（容器状态：$state/$health）"
    if [[ -n $LOG_FILE ]]; then
        {
            printf -- '---- %s docker logs --tail 200 %s ----\n' "$(date '+%F %T')" "$BACKEND_CONTAINER"
            docker logs --tail 200 "$BACKEND_CONTAINER" 2>&1 || true
        } >>"$LOG_FILE"
        log "容器日志只写进了服务器上的 $LOG_FILE"
    fi
    return 1
}

switch_backend() {
    docker tag "$BACKEND_IMAGE:$1" "$BACKEND_IMAGE:current"
    compose up -d --no-deps --force-recreate "$BACKEND_SERVICE"
}

# 新的 compose 文件和线上的不同时，先校验再替换（旧文件备份在 .deploy/backend/）
update_compose_file() {
    local new=$1
    if cmp -s -- "$new" "$COMPOSE_FILE"; then
        return 0
    fi
    docker compose --project-directory "$MY_OJ_DIR" -f "$new" config -q ||
        fail "新的 docker-compose.yml 校验不通过（常见原因：服务器的 .env 缺少新加的变量），线上没有任何改动"
    cp -p -- "$COMPOSE_FILE" "$BACKEND_STATE/docker-compose.$(date '+%Y%m%d-%H%M%S').yml"
    cat -- "$new" >"$COMPOSE_FILE"
    find "$BACKEND_STATE" -maxdepth 1 -name 'docker-compose.*.yml' -printf '%f\n' | sort | head -n -10 |
        while IFS= read -r old; do rm -f -- "$BACKEND_STATE/$old"; done
    log "docker-compose.yml 已更新。这次只重建后端；数据库和 nginx 的配置变化要在服务器上执行 docker compose up -d 才生效"
}

# 建表脚本全是「不存在才建」，重复执行安全。用数据库容器里的应用账号执行，脚本本身不接触密码。
apply_schema() {
    log "执行建表脚本（只建还不存在的表）"
    docker exec -i "$DB_CONTAINER" sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --default-character-set=utf8mb4 -uoj_app' <"$1" ||
        fail "建表脚本执行失败，后端没有切换版本"
}

prune_backend_images() {
    local current tag
    local -a keep
    current=$(backend_current)
    # 部署记录里最近的几个版本保留，其余由部署流程打的 my-oj-backend:<提交号> 镜像删掉
    mapfile -t keep < <(awk '{print $NF}' "$BACKEND_STATE/history" | tac | awk '!seen[$0]++' | head -n "$KEEP_RELEASES")
    is_kept() {
        local item
        for item in "${keep[@]}"; do
            if [[ $item == "$1" ]]; then
                return 0
            fi
        done
        return 1
    }
    while IFS= read -r tag; do
        # 只管提交号形式的标签；current、initial 和手动打的标签都不动
        if [[ ! $tag =~ ^[0-9a-f]{7,40}$ || $tag == "$current" ]] || is_kept "$tag"; then
            continue
        fi
        if docker rmi "$BACKEND_IMAGE:$tag" >/dev/null 2>&1; then
            log "删除旧镜像 $BACKEND_IMAGE:$tag"
        fi
    done < <(docker images "$BACKEND_IMAGE" --format '{{.Tag}}')
}

deploy_backend() {
    local sha=$1 package=$WORK_DIR/package context=$WORK_DIR/context previous file
    receive_package "$package"
    for file in my-oj.jar Dockerfile docker-compose.yml create_table.sql; do
        [[ -s $package/$file ]] || fail "部署包里没有 $file"
    done

    update_compose_file "$package/docker-compose.yml"

    mkdir -p -- "$context"
    cp -- "$package/my-oj.jar" "$package/Dockerfile" "$context/"
    log "构建镜像 $BACKEND_IMAGE:$sha"
    docker build -q -t "$BACKEND_IMAGE:$sha" "$context" >/dev/null
    # 手动部署和数据库首次初始化用到的文件也同步一份
    cp -- "$package/Dockerfile" "$MY_OJ_DIR/oj-backend/Dockerfile"
    cp -- "$package/create_table.sql" "$MY_OJ_DIR/oj-backend/init.sql"

    apply_schema "$package/create_table.sql"

    previous=$(backend_current)
    log "切换后端到 $sha"
    switch_backend "$sha"
    if wait_backend_healthy; then
        reload_nginx
        printf '%s\n' "$sha" >"$BACKEND_STATE/current"
        printf '%s deploy %s\n' "$(date '+%F %T')" "$sha" >>"$BACKEND_STATE/history"
        log "后端已切换到 $sha（上一版本：${previous:-无}）"
        prune_backend_images
        return 0
    fi

    if [[ -n $previous ]] && docker image inspect "$BACKEND_IMAGE:$previous" >/dev/null 2>&1; then
        log "切回上一版本 $previous"
        switch_backend "$previous"
        if wait_backend_healthy; then
            reload_nginx
            log "已切回 $previous，线上恢复正常"
        else
            log "上一版本也没有起来，需要登录服务器排查"
        fi
    fi
    fail "后端新版本 $sha 部署失败"
}

rollback_backend() {
    local current target="" i j
    local -a entries
    current=$(backend_current)
    [[ -n $current ]] || fail "没有后端的部署记录"
    mapfile -t entries < <(awk '{print $NF}' "$BACKEND_STATE/history" 2>/dev/null)
    # 部署记录里，当前版本最后一次出现之前、镜像还在的那个版本
    for ((i = ${#entries[@]} - 1; i >= 0; i--)); do
        if [[ ${entries[i]} != "$current" ]]; then
            continue
        fi
        for ((j = i - 1; j >= 0; j--)); do
            if [[ ${entries[j]} != "$current" ]] && docker image inspect "$BACKEND_IMAGE:${entries[j]}" >/dev/null 2>&1; then
                target=${entries[j]}
                break
            fi
        done
        break
    done
    [[ -n $target ]] || fail "没有可以切回的后端旧版本"
    log "切回后端版本 $target"
    switch_backend "$target"
    wait_backend_healthy || fail "切回 $target 后没有通过健康检查"
    reload_nginx
    printf '%s\n' "$target" >"$BACKEND_STATE/current"
    log "后端已切回 $target（原来是 $current）"
}

# endregion

show_status() {
    log "前端当前版本：$(current_release)"
    log "后端当前版本：$(backend_current)（容器状态：$(backend_state)）"
    local file
    for file in "$FRONTEND_STATE/history" "$BACKEND_STATE/history"; do
        if [[ -f $file ]]; then
            log "最近的记录（${file#"$STATE_DIR"/}）："
            tail -n 5 -- "$file"
        fi
    done
}

main() {
    local -a args
    local arg
    if [[ -n ${SSH_ORIGINAL_COMMAND+x} ]]; then
        read -r -a args <<<"$SSH_ORIGINAL_COMMAND"
    else
        args=("$@")
    fi
    ((${#args[@]} >= 1 && ${#args[@]} <= 2)) || usage
    for arg in "${args[@]}"; do
        [[ $arg =~ ^[a-z0-9-]{1,40}$ ]] || usage
    done
    [[ $(id -u) -ne 0 ]] || fail "不要用 root 执行，请用：sudo -u deploy $0 ${args[*]}"
    [[ -d $STATE_DIR ]] || fail "没有 $STATE_DIR（先在服务器上运行 setup-server.sh）"
    mkdir -p -- "$FRONTEND_STATE" "$BACKEND_STATE"
    LOG_FILE=$STATE_DIR/deploy.log

    exec 9>>"$STATE_DIR/deploy.lock"
    flock -w "$LOCK_WAIT_SECONDS" 9 || fail "另一次部署 $((LOCK_WAIT_SECONDS / 60)) 分钟还没结束"
    WORK_DIR=$(mktemp -d "$STATE_DIR/work.XXXXXX")

    case "${args[0]}" in
        frontend | backend)
            [[ ${#args[@]} -eq 2 && ${args[1]} =~ ^[0-9a-f]{7,40}$ ]] || usage
            log "开始部署 ${args[0]} ${args[1]}"
            "deploy_${args[0]}" "${args[1]}"
            ;;
        rollback)
            case "${args[1]:-}" in
                frontend) rollback_frontend ;;
                backend) rollback_backend ;;
                *) usage ;;
            esac
            ;;
        status)
            show_status
            ;;
        *)
            usage
            ;;
    esac
}

main "$@"
