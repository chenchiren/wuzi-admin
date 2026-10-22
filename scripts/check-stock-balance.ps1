param([string]$Base = "http://localhost:8081")
$ErrorActionPreference = "Continue"
$login = Invoke-RestMethod -Method Post -Uri "$Base/api/auth/login" -ContentType "application/json" -Body '{"username":"admin","password":"admin123"}'
$h = @{ Authorization = "Bearer $($login.data.token)" }

# page through all stock records
$all = @()
$page = 1
do {
  $r = Invoke-RestMethod -Uri "$Base/api/stock/records?page=$page&size=200" -Headers $h
  if ($r.data.records) { $all += $r.data.records }
  $total = $r.data.total
  $page++
} while (($all.Count -lt $total) -and $page -le 20)

$stocks = Invoke-RestMethod -Uri "$Base/api/stock?page=1&size=200" -Headers $h
$bad = @()
foreach ($s in $stocks.data.records) {
  $sum = 0.0
  $hit = 0
  foreach ($rec in $all) {
    if ($rec.warehouseId -eq $s.warehouseId -and $rec.materialId -eq $s.materialId -and ("" + $rec.batchNo) -eq ("" + $s.batchNo)) {
      $sum += [double]$rec.changeQty
      $hit++
    }
  }
  if ($hit -gt 0) {
    $diff = [math]::Abs([double]$s.qty - $sum)
    if ($diff -gt 0.01) {
      $bad += "wh=$($s.warehouseId) mat=$($s.materialId) batch='$($s.batchNo)' stock=$($s.qty) recSum=$sum hits=$hit"
    }
  }
}
Write-Host ("records=" + $all.Count + " stocks=" + $stocks.data.records.Count + " imbalances=" + $bad.Count)
if ($bad.Count -gt 0) { $bad | ForEach-Object { Write-Host ("  - " + $_) } ; exit 1 }
Write-Host "BALANCE OK"
exit 0
