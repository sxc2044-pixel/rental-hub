param([Parameter(Mandatory=$true)][string]$Archive,[string]$OutputDirectory='./restored-demo')
$ErrorActionPreference='Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archivePath=(Resolve-Path -LiteralPath $Archive).Path
$outputPath=[System.IO.Path]::GetFullPath((Join-Path (Get-Location) $OutputDirectory))
if(Test-Path -LiteralPath $outputPath){throw '恢复目录已存在，请指定一个新的空目录。本脚本不会覆盖现有数据。'}
$projectPath=(Get-Location).Path
if(!$outputPath.StartsWith($projectPath+[System.IO.Path]::DirectorySeparatorChar,[System.StringComparison]::OrdinalIgnoreCase)){throw '恢复目录必须位于项目目录内。'}
$archiveReader=[System.IO.Compression.ZipFile]::OpenRead($archivePath)
try {
 foreach($entry in $archiveReader.Entries){
  $resolved=[System.IO.Path]::GetFullPath((Join-Path $outputPath $entry.FullName))
  if(!$resolved.StartsWith($outputPath+[System.IO.Path]::DirectorySeparatorChar,[System.StringComparison]::OrdinalIgnoreCase)){throw '备份包含不安全路径。'}
 }
} finally {$archiveReader.Dispose()}
New-Item -ItemType Directory -Path $outputPath | Out-Null
[System.IO.Compression.ZipFile]::ExtractToDirectory($archivePath,$outputPath)
if(!(Test-Path -LiteralPath (Join-Path $outputPath 'database.zip'))){throw '缺少database.zip'}
$jarPath=Join-Path $projectPath 'target/rental-hub-1.0.0.jar'
if(!(Test-Path -LiteralPath $jarPath)){$jarPath=Join-Path $projectPath 'dist/rental-hub-1.0.0.jar'}
$jar=[System.IO.Compression.ZipFile]::OpenRead($jarPath)
try {
 $entry=$jar.Entries | Where-Object FullName -Match '^BOOT-INF/lib/h2-[^/]+\.jar$' | Select-Object -First 1
 if(!$entry){throw '运行包中缺少H2依赖'}
 $h2Path=Join-Path $outputPath 'h2-tools.jar'
 [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry,$h2Path,$false)
} finally {$jar.Dispose()}
& java -cp $h2Path org.h2.tools.Restore -file (Join-Path $outputPath 'database.zip') -dir (Join-Path $outputPath 'data') -db rental
if($LASTEXITCODE -ne 0){throw '数据库恢复失败'}
& java -cp $h2Path org.h2.tools.Shell -url ('jdbc:h2:file:'+ (Join-Path $outputPath 'data/rental')+';MODE=MySQL;DATABASE_TO_LOWER=TRUE') -user sa -password '' -sql 'SELECT COUNT(*) AS users FROM t_user; SELECT COUNT(*) AS houses FROM t_house; SELECT COUNT(*) AS reservations FROM t_reservation;'
if($LASTEXITCODE -ne 0){throw '恢复数据核验失败'}
Write-Host "恢复及核验完成：$outputPath。原数据库未修改；请先停止原服务，再决定是否切换恢复数据。"
