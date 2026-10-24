param([ValidateSet('local','demo','mysql','mariadb')][string]$Profile='local',[int]$Port=8080)
$ErrorActionPreference='Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$env:PORT="$Port"
if($Profile -eq 'local') {
    if(!(Test-Path -LiteralPath 'config/local.ps1')) {throw '请复制 config/local.example.ps1 为 config/local.ps1 并填写连接信息，或用 -Profile demo 启动。'}
    . ./config/local.ps1
} else {$env:SPRING_PROFILES_ACTIVE=$Profile}
$jar=if(Test-Path -LiteralPath 'dist/rental-hub-1.0.0.jar') {'dist/rental-hub-1.0.0.jar'} else {'target/rental-hub-1.0.0.jar'}
if(!(Test-Path -LiteralPath $jar)) {
    & mvn -B -ntp package
    if($LASTEXITCODE -ne 0) {throw 'Maven 构建失败'}
}
& java '-Dfile.encoding=UTF-8' -jar $jar
exit $LASTEXITCODE
