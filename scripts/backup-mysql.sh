#!/usr/bin/env bash
# Kali: chmod 700 scripts/backup-mysql.sh
# 凭据保存到 ~/.my-rental.cnf（chmod 600），不在命令行暴露密码。
set -euo pipefail
umask 077
backup_dir="${BACKUP_DIR:-$HOME/rental-backups}"
database="${DB_NAME:-rental}"
credentials="${MYSQL_DEFAULTS_FILE:-$HOME/.my-rental.cnf}"
mkdir -p "$backup_dir"
if [[ ! -f "$credentials" ]]; then printf '%s\n' '请先创建仅当前用户可读的MySQL defaults配置文件。' >&2; exit 1; fi
if [[ ! "$database" =~ ^[a-zA-Z0-9_]+$ ]]; then printf '%s\n' '数据库名无效' >&2; exit 1; fi
stamp="$(date +%Y%m%d-%H%M%S)"
output="$backup_dir/rental-$stamp.sql.gz"
partial="$output.partial"
trap 'rm -f -- "$partial"' EXIT
mysqldump --defaults-extra-file="$credentials" --single-transaction --routines --triggers --hex-blob --databases "$database" | gzip > "$partial"
gzip -t "$partial"
mv -- "$partial" "$output"
sha256sum "$output" > "$output.sha256"
printf '备份完成 %s\n' "$output"
# 保留期限由管理员决定；本脚本不自动删除历史备份。
