param(
    [ValidateSet('local','demo','mysql','mariadb')][string]$Profile='mysql',
    [ValidateRange(1,65535)][int]$Port=8080,
    [switch]$OpenBrowser
)
$ErrorActionPreference='Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$siteUrl="http://127.0.0.1:$Port"

function Test-Listening {
    $client=New-Object System.Net.Sockets.TcpClient
    try {
        $connection=$client.BeginConnect('127.0.0.1',$Port,$null,$null)
        if(!$connection.AsyncWaitHandle.WaitOne(400)) {return $false}
        $client.EndConnect($connection)
        return $true
    } catch {return $false}
    finally {$client.Dispose()}
}

function Test-RentalSite {
    try {
        $pageResponse=Invoke-WebRequest "$siteUrl/" -UseBasicParsing -TimeoutSec 3
        # Windows PowerShell 5.1 对未带 charset 的 text/html 可能按系统编码解码。
        $html=[System.Text.Encoding]::UTF8.GetString($pageResponse.RawContentStream.ToArray())
        if($pageResponse.StatusCode -ne 200 -or $html -notmatch '<title>校园安居 · 租房信息管理系统</title>') {return $false}
        $session=Invoke-RestMethod "$siteUrl/api/session" -TimeoutSec 3
        return ($session.csrf -is [string] -and $session.csrf.Length -gt 0 -and $session.PSObject.Properties.Name -contains 'user')
    } catch {return $false}
}

function Show-RunningSite {
    Write-Host "校园安居已在运行：$siteUrl"
    Write-Host '已复用现有服务，无需重复启动。切换数据库或运行模式前，请先在原服务窗口按 Ctrl+C 停止。'
    if($OpenBrowser) {Start-Process $siteUrl}
}

# CMD 与 PowerShell 共用端口互斥锁，覆盖数据库连接期间尚未监听端口的阶段。
$startupLock=New-Object System.Threading.Mutex($false,"Local\CampusLivingRentalHub-Port$Port")
$ownsLock=$false
$result=0
try {
    try {$ownsLock=$startupLock.WaitOne(0)}
    catch [System.Threading.AbandonedMutexException] {$ownsLock=$true}
    if(!$ownsLock) {
        Write-Host '已有启动窗口，正在确认服务状态，请稍候…'
        $deadline=[DateTime]::UtcNow.AddSeconds(45)
        while([DateTime]::UtcNow -lt $deadline) {
            if(Test-RentalSite) {Show-RunningSite; return}
            Start-Sleep -Milliseconds 1000
        }
        throw "另一个启动窗口仍未就绪。请查看原窗口及 logs/rental-hub.log，稍后重试 $siteUrl。"
    }
    if(Test-Listening) {
        if(Test-RentalSite) {Show-RunningSite; return}
        throw "端口 $Port 已被占用，但未确认是可用的校园安居服务。请查看原启动窗口；也可用 scripts/start.ps1 -Port 8081 启动到其他端口。"
    }

    $env:PORT="$Port"
    if($Profile -eq 'local') {$Profile='mysql'}
    $env:SPRING_PROFILES_ACTIVE=$Profile
    if($Profile -ne 'demo' -and !(Test-Path -LiteralPath 'config/application-local.properties')) {
        Copy-Item -LiteralPath 'config/application-local.example.properties' -Destination 'config/application-local.properties'
        if($OpenBrowser) {Start-Process notepad.exe -ArgumentList 'config/application-local.properties'}
        throw '已创建数据库配置。请填写 config/application-local.properties 中的密码，然后重新启动。'
    }
    $jar=if(Test-Path -LiteralPath 'dist/rental-hub-1.0.0.jar') {'dist/rental-hub-1.0.0.jar'} else {'target/rental-hub-1.0.0.jar'}
    if(!(Test-Path -LiteralPath $jar)) {
        & mvn -B -ntp package
        if($LASTEXITCODE -ne 0) {throw 'Maven 构建失败'}
    }
    Write-Host "校园安居将在 Windows 本机启动，运行模式：$Profile。"
    if($Profile -ne 'demo') {Write-Host '连接 Kali 数据库时可能需要约10至15秒，请等待启动完成。'}
    Write-Host "启动成功后访问 $siteUrl 。请保留服务窗口，按 Ctrl+C 可停止服务。"
    & java '-Dfile.encoding=UTF-8' -jar $jar
    $result=$LASTEXITCODE
    if($result -ne 0) {throw '应用启动失败或异常退出，请查看上方错误及 logs/rental-hub.log。'}
    Write-Host '服务已停止。'
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    $result=1
} finally {
    if($ownsLock) {$startupLock.ReleaseMutex()}
    $startupLock.Dispose()
}
exit $result
