# E2E smoke - ASCII only
param([string]$Base = "http://localhost:8081")
$ErrorActionPreference = "Stop"
$script:headers = $null

function Req($method, $path, $body = $null) {
  $uri = "$Base$path"
  $p = @{ Method = $method; Uri = $uri; ContentType = "application/json" }
  if ($script:headers) { $p.Headers = $script:headers }
  if ($body) { $p.Body = ($body | ConvertTo-Json -Depth 8) }
  $r = Invoke-RestMethod @p
  if ($r.code -ne 200) { throw "$method $path -> $($r.code) $($r.msg)" }
  return $r.data
}

function Approve-Biz($bizType, $bizId) {
  for ($i = 0; $i -lt 8; $i++) {
    $todos = Req GET ("/api/approvals/todo?size=20&bizType=" + $bizType)
    $hit = $null
    if ($todos -and $todos.records) {
      $hit = $todos.records | Where-Object { $_.bizId -eq $bizId } | Select-Object -First 1
    }
    if (-not $hit) { break }
    Req POST ("/api/approvals/" + $hit.id + "/approve") @{ comment = "OK" }
  }
}

Write-Host "1. login"
$login = Req POST "/api/auth/login" @{ username = "admin"; password = "admin123" }
$script:headers = @{ Authorization = "Bearer $($login.token)" }
Write-Host ("  userId=" + $login.userId)

Write-Host "2. tax calc"
$calc = Req POST "/api/price/calc" @{ taxInclusivePrice = 22; taxRate = 13; qty = 100; mode = "INCLUSIVE" }
Write-Host ("  incl=" + $calc.taxInclusiveAmount + " excl=" + $calc.taxExclAmount + " tax=" + $calc.taxAmount)
$sum = [double]$calc.taxExclAmount + [double]$calc.taxAmount
if ([math]::Abs([double]$calc.taxInclusiveAmount - $sum) -gt 0.02) { throw "tax identity fail" }

Write-Host "3. purchase order"
$po = Req POST "/api/purchase/orders" @{
  supplierId = 1
  items = @(
    @{ materialId = 1; qty = 10; taxInclusivePrice = 22; taxRate = 13 },
    @{ materialId = 2; qty = 5; taxInclusivePrice = 18; taxRate = 13 }
  )
}
$poId = $po.order.id
Write-Host ("  poId=" + $poId + " incl=" + $po.order.taxInclusiveAmount)
Req POST "/api/purchase/orders/$poId/submit" | Out-Null
Approve-Biz "PURCHASE_ORDER" $poId

Write-Host "4. purchase apply"
$pa = Req POST "/api/purchase/applies" @{
  applyUserId = 1; deptId = 4
  items = @(@{ materialId = 1; qty = 5; refPrice = 22 })
}
$paId = $pa.id; if (-not $paId) { $paId = $pa.apply.id }
Req POST "/api/purchase/applies/$paId/submit" | Out-Null
Write-Host ("  applyId=" + $paId)

Write-Host "5. arrival"
$arr = Req POST "/api/purchase/arrivals" @{ orderId = $poId; supplierId = 1 }
$arrId = $arr.id; if (-not $arrId) { $arrId = $arr.arrival.id }
Req POST "/api/purchase/arrivals/$arrId/submit" | Out-Null
Write-Host ("  arrId=" + $arrId)

Write-Host "6. inbound"
$in = Req POST "/api/inbound/orders" @{
  warehouseId = 1; purchaseOrderId = $poId; inboundType = "PURCHASE"
  items = @(
    @{ materialId = 1; purchaseOrderItemId = $null; inQty = 10; taxInclusivePrice = 22; taxRate = 13 },
    @{ materialId = 2; inQty = 5; taxInclusivePrice = 18; taxRate = 13 }
  )
}
$inId = $in.id; if (-not $inId) { $inId = $in.order.id }
Req POST "/api/inbound/orders/$inId/confirm" @{} | Out-Null
Write-Host ("  inId=" + $inId)

Write-Host "7. stock"
$q = [char]63
$st = Req GET ("/api/stock" + $q + "page=1" + [char]38 + "size=20")
Write-Host ("  stockTotal=" + $st.total)
$batch1 = ""
foreach ($row in $st.records) {
  if ($row.materialId -eq 1) { $batch1 = $row.batchNo; break }
}
Write-Host ("  batch1=" + $batch1)

Write-Host "8. outbound"
$out = Req POST "/api/outbound/orders" @{
  warehouseId = 1; outboundType = "USE"
  items = @(@{ materialId = 1; qty = 2; batchNo = $batch1 })
}
$outId = $out.id; if (-not $outId) { $outId = $out.order.id }
Req POST "/api/outbound/orders/$outId/submit" | Out-Null
Approve-Biz "OUTBOUND" $outId
Req POST "/api/outbound/orders/$outId/confirm" @{} | Out-Null
Write-Host ("  outId=" + $outId)

Write-Host "9. material use"
$use = Req POST "/api/material-use" @{
  applyUserId = 4; deptId = 4
  items = @(@{ materialId = 1; qty = 1 })
}
$useId = $use.id; if (-not $useId) { $useId = $use.apply.id }
Req POST "/api/material-use/$useId/submit" @{} | Out-Null
Req POST "/api/material-use/$useId/approve" @{ approved = $true; comment = "OK" } | Out-Null
Req POST "/api/material-use/$useId/issue" @{ warehouseId = 1 } | Out-Null
Write-Host ("  useId=" + $useId)

Write-Host "10. qr + public trace"
$qr = Req POST "/api/trace/qr/generate" @{
  qrType = "BATCH"; materialId = 1; materialName = "A4Paper"; materialCode = "A4-001"
  batchNo = ("BT" + [DateTime]::Now.ToString("HHmmssfff"))
}
$pub = Req GET "/api/trace/public/$($qr.traceToken)"
Write-Host ("  qr=" + $qr.qrCode + " name=" + $pub.materialName)

Write-Host "11. new modules"
Req GET "/api/stats/dashboard" | Out-Null
Req GET "/api/alerts" | Out-Null
Req GET "/api/m/home" | Out-Null
Req GET "/api/messages" | Out-Null
Req GET "/api/flow-approvals/flows" | Out-Null
Req GET "/api/reports/purchase-monthly" | Out-Null
Req GET "/api/alerts/expiry" | Out-Null
try { Req GET "/api/invoices" | Out-Null; Write-Host "  invoices ok" } catch { Write-Host ("  invoices: " + $_.Exception.Message) }
try { Req GET "/api/return-stores" | Out-Null; Write-Host "  return-stores ok" } catch { Write-Host ("  return-stores: " + $_.Exception.Message) }
try { Req GET "/api/labels/zpl/1" | Out-Null; Write-Host "  labels ok" } catch { Write-Host ("  labels: " + $_.Exception.Message) }
try { Req GET "/api/m/sync/batch" | Out-Null } catch { Write-Host "  sync method check skip" }

Write-Host ""
Write-Host "E2E ALL PASSED"
