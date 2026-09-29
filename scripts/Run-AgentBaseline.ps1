[CmdletBinding()]
param(
    [switch]$DryRun,
    [string]$KeyFile,
    [string]$MySqlBin = 'D:\Program Files\MySQL\MySQL Server 8.0\bin',
    [string]$RedisBin = 'E:\Redis-x64-5.0.14.1',
    [int]$MySqlPort = 33310,
    [int]$RedisPort = 16382
)
$ErrorActionPreference = 'Stop'
$workspace = Split-Path $PSScriptRoot -Parent
$backend = Join-Path $workspace 'backend'
$root = Join-Path $backend 'target/agent-evaluation'
New-Item -ItemType Directory -Force -Path $root | Out-Null
# Only this explicitly named variable/file is read. Never echo credentials.
$names = @('DEEPSEEK_API_KEY','AGENT_EVAL_ENABLED','AGENT_EVAL_MODE','EVAL_DB_URL','EVAL_DB_USERNAME','EVAL_DB_PASSWORD','EVAL_REDIS_PORT','EVAL_CODE_REVISION','EVAL_WORKTREE_DIRTY')
$saved = @{}
foreach ($name in $names) { $saved[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
$mysqlProcess = $null
$redisProcess = $null
$runtime = $null
$exitStatus = 0
try {
    if ($KeyFile) {
        $env:DEEPSEEK_API_KEY = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $KeyFile)).Trim()
    }
    if (-not $DryRun -and [string]::IsNullOrWhiteSpace($env:DEEPSEEK_API_KEY)) {
        @{status='BLOCKED'; reason='DEEPSEEK_API_KEY_MISSING'; realModelCalled=$false; time=[DateTimeOffset]::UtcNow.ToString('o')} |
            ConvertTo-Json | Set-Content -LiteralPath (Join-Path $root 'preflight.json') -Encoding utf8
        throw 'DEEPSEEK_API_KEY is missing. Set it in this terminal or pass -KeyFile with a local secret file. Do not paste it into chat.'
    }
    if ($MySqlPort -eq 3306 -or $RedisPort -eq 6379 -or $MySqlPort -eq $RedisPort) { throw 'Use separate non-default ports for evaluation.' }
    foreach ($port in @($MySqlPort,$RedisPort)) {
        $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,$port)
        try { $listener.Start() } finally { $listener.Stop() }
    }
    foreach ($exe in @((Join-Path $MySqlBin 'mysqld.exe'),(Join-Path $MySqlBin 'mysqladmin.exe'),(Join-Path $RedisBin 'redis-server.exe'),(Join-Path $RedisBin 'redis-cli.exe'))) {
        if (-not (Test-Path -LiteralPath $exe)) { throw "Missing executable: $exe" }
    }
    Get-Command mvn -ErrorAction Stop | Out-Null
    $runtime = Join-Path $root ('services-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,6))
    New-Item -ItemType Directory -Path $runtime | Out-Null
    $data = Join-Path $runtime 'data'
    $password = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
    $init = Join-Path $runtime 'init.sql'
    $client = Join-Path $runtime 'client.cnf'
    [IO.File]::WriteAllText($init,"ALTER USER 'root'@'localhost' IDENTIFIED BY '$password'; CREATE DATABASE aftersales_agent_eval CHARACTER SET utf8mb4;")
    [IO.File]::WriteAllText($client,"[client]`nuser=root`npassword=$password`nhost=127.0.0.1`nport=$MySqlPort`nprotocol=tcp`n")
    & (Join-Path $MySqlBin 'mysqld.exe') --no-defaults --initialize-insecure "--datadir=$data" --console 2>&1 |
        Out-File (Join-Path $runtime 'initialize.log') -Encoding utf8
    if ($LASTEXITCODE -ne 0) { throw 'MySQL initialization failed; see initialize.log.' }
    $mysqlProcess = Start-Process -FilePath (Join-Path $MySqlBin 'mysqld.exe') -WindowStyle Hidden -PassThru -ArgumentList @('--no-defaults',"`"--datadir=$data`"","--port=$MySqlPort",'--bind-address=127.0.0.1','--mysqlx=0',"`"--init-file=$init`"",'--console') -RedirectStandardOutput (Join-Path $runtime 'mysql.out.log') -RedirectStandardError (Join-Path $runtime 'mysql.err.log')
    [IO.File]::WriteAllText((Join-Path $runtime 'redis.conf'),"bind 127.0.0.1`nport $RedisPort`nsave `"`"`nappendonly no`n")
    $redisProcess = Start-Process -FilePath (Join-Path $RedisBin 'redis-server.exe') -WindowStyle Hidden -PassThru -WorkingDirectory $runtime -ArgumentList @("`"$(Join-Path $runtime 'redis.conf')`"") -RedirectStandardOutput (Join-Path $runtime 'redis.out.log') -RedirectStandardError (Join-Path $runtime 'redis.err.log')
    $ready = $false
    for ($i=0; $i -lt 30; $i++) {
        & (Join-Path $MySqlBin 'mysqladmin.exe') "--defaults-extra-file=$client" ping 2>$null | Out-Null
        $mysqlReady = $LASTEXITCODE -eq 0
        $pong = & (Join-Path $RedisBin 'redis-cli.exe') -h 127.0.0.1 -p $RedisPort ping 2>$null
        if ($mysqlReady -and $pong -eq 'PONG') { $ready=$true; break }
        if ($mysqlProcess.HasExited -or $redisProcess.HasExited) { throw 'An evaluation service exited during startup.' }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw 'Evaluation services did not become ready.' }
    $env:AGENT_EVAL_ENABLED='true'
    $env:AGENT_EVAL_MODE=if($DryRun){'DRY_RUN'}else{'REAL'}
    $env:EVAL_DB_URL="jdbc:mysql://127.0.0.1:$MySqlPort/aftersales_agent_eval?serverTimezone=UTC&characterEncoding=UTF-8"
    $env:EVAL_DB_USERNAME='root'
    $env:EVAL_DB_PASSWORD=$password
    $env:EVAL_REDIS_PORT="$RedisPort"
    $env:EVAL_CODE_REVISION=(& git -C $workspace rev-parse HEAD).Trim()
    $env:EVAL_WORKTREE_DIRTY=if(@(& git -C $workspace status --porcelain).Count -gt 0){'true'}else{'false'}
    Push-Location $backend
    try {
        Write-Output "Running $($env:AGENT_EVAL_MODE): 10 scenarios, at most 11 conversation turns; no automatic retries."
        & mvn -o "-Dmaven.repo.local=$(Join-Path $workspace '.m2-local')" '-Dtest=RealAgentBaselineTest' test *> (Join-Path $runtime 'maven.log')
        if ($LASTEXITCODE -ne 0) { throw "Evaluation runner failed. See $runtime/maven.log" }
        Get-ChildItem -LiteralPath $root -Directory | Where-Object Name -Like $(if($DryRun){'dry-*'}else{'real-*'}) | Sort-Object Name -Descending | Select-Object -First 1 -ExpandProperty FullName
    } finally { Pop-Location }
} catch {
    $exitStatus=1
    Write-Warning $_.Exception.Message
} finally {
    # Only stop process handles created by this invocation.
    if ($mysqlProcess -and -not $mysqlProcess.HasExited) {
        & (Join-Path $MySqlBin 'mysqladmin.exe') "--defaults-extra-file=$(Join-Path $runtime 'client.cnf')" shutdown 2>$null | Out-Null
        if (-not $mysqlProcess.WaitForExit(5000)) { Stop-Process -Id $mysqlProcess.Id }
    }
    if ($redisProcess -and -not $redisProcess.HasExited) {
        & (Join-Path $RedisBin 'redis-cli.exe') -h 127.0.0.1 -p $RedisPort shutdown nosave 2>$null | Out-Null
        if (-not $redisProcess.WaitForExit(5000)) { Stop-Process -Id $redisProcess.Id }
    }
    if ($runtime) {
        foreach ($file in @('init.sql','client.cnf')) {
            $secretPath=Join-Path $runtime $file
            if(Test-Path -LiteralPath $secretPath){Remove-Item -LiteralPath $secretPath}
        }
    }
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name,$saved[$name],'Process') }
}
exit $exitStatus

