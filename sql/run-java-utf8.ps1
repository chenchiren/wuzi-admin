# UTF-8 初始化 / 演示数据工具链（禁止 java Foo.java 直接跑源文件）
# 用法示例：
#   .\sql\run-java-utf8.ps1 seed
#   .\sql\run-java-utf8.ps1 check
#   .\sql\run-java-utf8.ps1 init .\sql\wuzi_full.sql

param(
    [Parameter(Mandatory = $true)][string]$Tool,  # seed | check | init | fix
    [string]$SqlFile
)

$ErrorActionPreference = 'Stop'

# 账号/口令来源优先级：进程环境变量 > %USERPROFILE%\.wuzi\env（仓库外） > sql/.env.local（已 gitignore）
$envFiles = @(
    (Join-Path $env:USERPROFILE '.wuzi\env'),
    (Join-Path $PSScriptRoot '.env.local')
)
foreach ($envFile in $envFiles) {
    if (Test-Path $envFile) {
        foreach ($line in Get-Content $envFile) {
            if ($line -match '^\s*(WUZI_DB_USER|WUZI_DB_PASSWORD)\s*=\s*(.+?)\s*$') {
                $name = $Matches[1]
                $value = $Matches[2]
                if (-not [Environment]::GetEnvironmentVariable($name)) {
                    Set-Item -Path ("Env:" + $name) -Value $value
                }
            }
        }
    }
}
if (-not $env:WUZI_DB_USER -or -not $env:WUZI_DB_PASSWORD) {
    throw "缺少 WUZI_DB_USER / WUZI_DB_PASSWORD：请设置环境变量，或写入 %USERPROFILE%\.wuzi\env（仓库外）"
}

$jar = Join-Path $env:USERPROFILE '.m2\repository\com\mysql\mysql-connector-j\8.3.0\mysql-connector-j-8.3.0.jar'
if (-not (Test-Path $jar)) {
    # 回退：从 backend 解析依赖
    Write-Host "resolving mysql-connector from maven..."
    Push-Location (Join-Path $PSScriptRoot '..\backend')
    mvn -q org.apache.maven.plugins:maven-dependency-plugin:3.6.1:build-classpath '-Dmdep.outputFile=cp.txt'
    $cpLine = Get-Content cp.txt -Raw
    Pop-Location
    $jar = ($cpLine -split ';') | Where-Object { $_ -match 'mysql-connector' } | Select-Object -First 1
}

$srcDir = $PSScriptRoot
$outDir = Join-Path $srcDir 'out'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$map = @{
    'seed'  = @{ main = 'SeedDemoChain'; args = @() }
    'check' = @{ main = 'EncCheck'; args = @() }
    'init'  = @{ main = 'InitDb'; args = @($SqlFile) }
    'fix'   = @{ main = 'FixDbEncoding'; args = @() }
}
if (-not $map.ContainsKey($Tool)) { throw "unknown tool: $Tool (seed|check|init|fix)" }
$entry = $map[$Tool]
$src = Join-Path $srcDir ($entry.main + '.java')

javac -encoding UTF-8 -cp $jar -d $outDir $src
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

$cp = "$outDir;$jar"
$args = @('-Dfile.encoding=UTF-8', '-cp', $cp, $entry.main) + $entry.args
java @args
