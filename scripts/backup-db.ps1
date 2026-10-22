# 物资库备份脚本（mysqldump）
param(
  [string]$Host = "localhost",
  [int]$Port = 3306,
  [string]$User = "root",
  [string]$Database = "wuzi_test",
  [string]$OutDir = "backups"
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Path $OutDir | Out-Null }

$ts = Get-Date -Format "yyyyMMdd-HHmmss"
$out = Join-Path $OutDir "wuzi_test-$ts.sql"
Write-Host "Dumping $Database to $out ..."

# 密码请通过环境变量 MYSQL_PWD 传入，避免命令行明文
if (-not $env:MYSQL_PWD) {
  Write-Warning "MYSQL_PWD 未设置，mysqldump 可能提示输入密码"
}

mysqldump -h $Host -P $Port -u $User --default-character-set=utf8mb4 `
  --single-transaction --routines --triggers `
  $Database | Out-File -FilePath $out -Encoding utf8

if ($LASTEXITCODE -ne 0) { throw "mysqldump failed" }
Write-Host "OK: $out ($((Get-Item $out).Length) bytes)"
