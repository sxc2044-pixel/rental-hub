$ErrorActionPreference='Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$base='http://127.0.0.1:8080/api'
$session=Invoke-RestMethod "$base/session" -SessionVariable webSession
$question=Invoke-RestMethod "$base/captcha" -WebSession $webSession
$numbers=[regex]::Matches($question.question,'\d+')
$answer=[int]$numbers[0].Value+[int]$numbers[1].Value
$payload=@{account='admin';password='Admin123!';captcha="$answer"} | ConvertTo-Json
$login=Invoke-RestMethod "$base/auth/login" -Method Post -WebSession $webSession -Headers @{'X-CSRF-Token'=$session.csrf} -ContentType 'application/json' -Body $payload
$headers=@{'X-CSRF-Token'=$login.csrf}
$label='验收区'+[DateTimeOffset]::Now.ToUnixTimeMilliseconds()
$created=Invoke-RestMethod "$base/admin/dictionaries" -Method Post -WebSession $webSession -Headers $headers -ContentType 'application/json' -Body (@{category='REGION';label=$label}|ConvertTo-Json)
$updated=Invoke-RestMethod "$base/admin/dictionaries/$($created.id)" -Method Put -WebSession $webSession -Headers $headers -ContentType 'application/json' -Body (@{label=($label+'改')}|ConvertTo-Json)
$rows=Invoke-RestMethod "$base/dictionaries" -WebSession $webSession
if(!($rows|Where-Object {$_.id -eq $created.id -and $_.label -eq ($label+'改')})){throw '字典编辑结果不正确'}
Invoke-RestMethod "$base/admin/dictionaries/$($created.id)" -Method Delete -WebSession $webSession -Headers $headers | Out-Null
$day=(Get-Date).AddDays(2).ToString('yyyy-MM-dd')
$houses=Invoke-RestMethod "$base/houses" -WebSession $webSession
$slots=Invoke-RestMethod "$base/houses/$($houses.items[0].id)/availability?day=$day" -WebSession $webSession
if($slots.Count -ne 10){throw '时段数量不正确'}
$result=@{executedAt=[DateTimeOffset]::Now.ToString('o');dictionaryCreateEditDelete='PASS';availability='PASS';slotCount=$slots.Count}
$result|ConvertTo-Json|Set-Content -Encoding utf8 'docs/evidence/supplementary.json'
Write-Output '字典新增编辑删除与时段可用性检查通过。'
