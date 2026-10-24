param([ValidateSet('local','demo','mysql','mariadb')][string]$Profile='mysql',[int]$Port=8080)
$ErrorActionPreference='Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$env:PORT="$Port"
if($Profile -eq 'local') {$Profile='mysql'} # 兼容旧命令；连接参数统一从同一份 properties 读取。
$env:SPRING_PROFILES_ACTIVE=$Profile
if($Profile -ne 'demo' -and !(Test-Path -LiteralPath 'config/application-local.properties')) {
    Copy-Item -LiteralPath 'config/application-local.example.properties' -Destination 'config/application-local.properties'
    throw '请填写 config/application-local.properties 中的数据库密码，然后重新启动。'
}
$jar=if(Test-Path -LiteralPath 'dist/rental-hub-1.0.0.jar') {'dist/rental-hub-1.0.0.jar'} else {'target/rental-hub-1.0.0.jar'}
if(!(Test-Path -LiteralPath $jar)) {
    & mvn -B -ntp package
    if($LASTEXITCODE -ne 0) {throw 'Maven 构建失败'}
}
& java '-Dfile.encoding=UTF-8' -jar $jar
exit $LASTEXITCODE
