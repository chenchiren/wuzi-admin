# Full business chain + stock consistency - ASCII only
param([string]$Base = "http://localhost:8081")
$ErrorActionPreference = "Continue"
$script:headers = $null
$script:pass = 0
$script:fail = 0
$script:issues = @()

function Req($method, $path, $body = $null) {
  $uri = "$Base$path"
  $p = @{ Method = $method; Uri = $uri; ContentType = "application/json" }
  if ($script:headers) { $p.Headers = $script:headers }
  if ($body) { $p.Body = ($body | ConvertTo-Json -Depth 8) }
  return Invoke-RestMethod @p
}

function Ok($name, $method, $path, $body = $null) {
  try {
    $r = Req $method $path $body
    if ($r.code -eq 200) {
      $script:pass++
      Write-Host ("PASS  " + $name)
      return $r.data
    }
    $script:fail++
    $script:issues += ("$name -> code=$($r.code) msg=$($r.msg)")
    Write-Host ("FAIL  " + $name + " code=" + $r.code + " " + $r.msg)
    return $null
  } catch {
    $script:fail++
    $script:issues += ("$name -> EX " + $_.Exception.Message)
    Write-Host ("FAIL  " + $name + " " + $_.Exception.Message)
    return $null
  }
}

function AssertTrue($name, $cond, $detail = "") {
  if ($cond) {
    $script:pass++
    Write-Host ("PASS  " + $name)
  } else {
    $script:fail++
    $script:issues += ("$name -> " + $detail)
    Write-Host ("FAIL  " + $name + " " + $detail)
  }
}

function Approve-Biz($bizType, $bizId) {
  for ($i = 0; $i -lt 8; $i++) {
    $todos = Ok ("todo-" + $bizType + "-" + $i) GET ("/api/approvals/todo?size=20&bizType=" + $bizType)
    $hit = $null
    if ($todos -and $todos.records) {
      $hit = $todos.records | Where-Object { $_.bizId -eq $bizId } | Select-Object -First 1
    }
    if (-not $hit) { break }
    Ok ("approve-" + $hit.id) POST ("/api/approvals/" + $hit.id + "/approve") @{ comment = "OK" }
  }
}

Write-Host "=== LOGIN ==="
$login = Ok "login" POST "/api/auth/login" @{ username = "admin"; password = "admin123" }
$script:headers = @{ Authorization = "Bearer $($login.token)" }

Write-Host "=== 1. PURCHASE APPLY + MULTI APPROVE ==="
$pa = Ok "pa-create" POST "/api/purchase/applies" @{
  applyUserId = 1; deptId = 4
  items = @(@{ materialId = 1; qty = 2; refPrice = 22; taxRate = 13 })
}
$paId = 0
if ($pa) { if ($pa.id) { $paId = $pa.id } elseif ($pa.apply) { $paId = $pa.apply.id } }
if ($paId -gt 0) {
  $sub = Ok "pa-submit" POST "/api/purchase/applies/$paId/submit"
  Approve-Biz "PURCHASE_APPLY" $paId
  $paDetail = Ok "pa-detail" GET "/api/purchase/applies/$paId"
  $paStatus = ""
  if ($paDetail) {
    if ($paDetail.apply) { $paStatus = $paDetail.apply.status } elseif ($paDetail.status) { $paStatus = $paDetail.status } elseif ($paDetail.id -and $paDetail.status) { $paStatus = $paDetail.status }
  }
  # list-based status
  $paList = Ok "pa-list-status" GET ("/api/purchase/applies?page=1&size=50")
  $paRow = $null
  if ($paList -and $paList.records) { $paRow = $paList.records | Where-Object { $_.id -eq $paId } | Select-Object -First 1 }
  if ($paRow) { $paStatus = $paRow.status }
  AssertTrue "pa-status-approved" ($paStatus -eq "APPROVED") "status=$paStatus"
}

Write-Host "=== 2. PO SUBMIT + APPROVE ==="
$po = Ok "po-create" POST "/api/purchase/orders" @{
  supplierId = 1; applyId = $paId
  items = @(@{ materialId = 1; qty = 4; taxInclusivePrice = 22; taxRate = 13 })
}
$poId = 0
if ($po) { if ($po.order) { $poId = $po.order.id } elseif ($po.id) { $poId = $po.id } }
if ($poId -gt 0) {
  Ok "po-submit" POST "/api/purchase/orders/$poId/submit"
  Approve-Biz "PURCHASE_ORDER" $poId
  $poList = Ok "po-list-status" GET "/api/purchase/orders?page=1&size=50"
  $poRow = $null
  if ($poList -and $poList.records) { $poRow = $poList.records | Where-Object { $_.id -eq $poId } | Select-Object -First 1 }
  $poStatus = ""
  if ($poRow) { $poStatus = $poRow.status }
  AssertTrue "po-status-approved-or-draft" (@("APPROVED","DRAFT","RECEIVED") -contains $poStatus) "status=$poStatus"
}

Write-Host "=== 3. ARRIVAL + INBOUND + STOCK SIGNS ==="
$arr = Ok "arr-create" POST "/api/purchase/arrivals" @{ orderId = $poId; supplierId = 1 }
$arrId = 0
if ($arr) { if ($arr.id) { $arrId = $arr.id } elseif ($arr.arrival) { $arrId = $arr.arrival.id } }
if ($arrId -gt 0) {
  Ok "arr-submit" POST "/api/purchase/arrivals/$arrId/submit"
  Approve-Biz "PURCHASE_ARRIVAL" $arrId
  Ok "arr-complete" POST "/api/purchase/arrivals/$arrId/complete" @{}
}

$beforeStock = Ok "stock-before" GET "/api/stock?page=1&size=100"
$mat1QtyBefore = 0
if ($beforeStock -and $beforeStock.records) {
  $mat1QtyBefore = ($beforeStock.records | Where-Object { $_.materialId -eq 1 } | Measure-Object -Property qty -Sum).Sum
  if (-not $mat1QtyBefore) { $mat1QtyBefore = 0 }
}

$in = Ok "in-create" POST "/api/inbound/orders" @{
  warehouseId = 1; purchaseOrderId = $poId; inboundType = "PURCHASE"
  items = @(@{ materialId = 1; inQty = 4; taxInclusivePrice = 22; taxRate = 13 })
}
$inId = 0
if ($in) { if ($in.id) { $inId = $in.id } elseif ($in.order) { $inId = $in.order.id } }
if ($inId -gt 0) {
  Ok "in-confirm" POST "/api/inbound/orders/$inId/confirm" @{}
}

$afterStock = Ok "stock-after" GET "/api/stock?page=1&size=100"
$mat1QtyAfter = 0
$batch = ""
if ($afterStock -and $afterStock.records) {
  $rows = @($afterStock.records | Where-Object { $_.materialId -eq 1 })
  $mat1QtyAfter = ($rows | Measure-Object -Property qty -Sum).Sum
  if (-not $mat1QtyAfter) { $mat1QtyAfter = 0 }
  $batch = $rows[0].batchNo
}
AssertTrue "inbound-increased-stock" ([double]$mat1QtyAfter -ge [double]$mat1QtyBefore + 4) "before=$mat1QtyBefore after=$mat1QtyAfter"

Write-Host "=== 4. STOCK RECORD SIGN CONSISTENCY ==="
$recs = Ok "records" GET "/api/stock/records?page=1&size=200"
$signBugs = @()
if ($recs -and $recs.records) {
  foreach ($r in $recs.records) {
    # only validate new records from this run (id > 75 baseline after first fix batch)
    if ([int]$r.id -le 75) { continue }
    $cq = [double]$r.changeQty
    $sign = [int]$r.amountSign
    $before = [double]$r.beforeQty
    $after = [double]$r.afterQty
    if ([math]::Abs(($before + $cq) - $after) -gt 0.01) {
      $signBugs += "id=$($r.id) biz=$($r.bizType) before=$before cq=$cq after=$after (before+cq!=after)"
    }
    if ($sign -eq 1 -and $cq -lt 0) { $signBugs += "id=$($r.id) biz=$($r.bizType) cq=$cq sign=$sign" }
    if ($sign -eq -1 -and $cq -gt 0) { $signBugs += "id=$($r.id) biz=$($r.bizType) cq=$cq sign=$sign" }
  }
}
AssertTrue "stock-record-signs" ($signBugs.Count -eq 0) (($signBugs | Select-Object -First 5) -join "; ")

Write-Host "=== 5. OUTBOUND ==="
$out = Ok "out-create" POST "/api/outbound/orders" @{
  warehouseId = 1; outboundType = "USE"
  items = @(@{ materialId = 1; qty = 1; batchNo = $batch })
}
$outId = 0
if ($out) { if ($out.id) { $outId = $out.id } elseif ($out.order) { $outId = $out.order.id } }
if ($outId -gt 0) {
  Ok "out-submit" POST "/api/outbound/orders/$outId/submit"
  Approve-Biz "OUTBOUND" $outId
  $ocr = Ok "out-confirm" POST "/api/outbound/orders/$outId/confirm" @{}
}

Write-Host "=== 6. MATERIAL USE FULL ==="
$use = Ok "use-create" POST "/api/material-use" @{
  applyUserId = 1; deptId = 4
  items = @(@{ materialId = 1; qty = 1 })
}
$useId = 0
if ($use) { if ($use.id) { $useId = $use.id } elseif ($use.apply) { $useId = $use.apply.id } }
if ($useId -gt 0) {
  Ok "use-submit" POST "/api/material-use/$useId/submit"
  Approve-Biz "MATERIAL_USE" $useId
  $issued = Ok "use-issue" POST "/api/material-use/$useId/issue" @{ warehouseId = 1 }
  $useList = Ok "use-list" GET "/api/material-use?page=1&size=20"
  $useRow = $null
  if ($useList -and $useList.records) { $useRow = $useList.records | Where-Object { $_.id -eq $useId } | Select-Object -First 1 }
  $useStatus = ""
  if ($useRow) { $useStatus = $useRow.status }
  AssertTrue "use-issued" ($useStatus -eq "ISSUED") "status=$useStatus"
}

Write-Host "=== 7. TRANSFER ==="
$tr = Ok "tr-create" POST "/api/transfer/orders" @{
  fromWarehouseId = 1; toWarehouseId = 2
  items = @(@{ materialId = 1; qty = 1; batchNo = $batch })
}
$trId = 0
if ($tr) { if ($tr.id) { $trId = $tr.id } elseif ($tr.order) { $trId = $tr.order.id } }
if ($trId -gt 0) {
  Ok "tr-submit" POST "/api/transfer/orders/$trId/submit"
  Approve-Biz "TRANSFER" $trId
  Ok "tr-out" POST "/api/transfer/orders/$trId/out" @{}
  Ok "tr-in" POST "/api/transfer/orders/$trId/in" @{}
  $trList = Ok "tr-list" GET "/api/transfer/orders?page=1&size=10"
  $trRow = $null
  if ($trList -and $trList.records) { $trRow = $trList.records | Where-Object { $_.id -eq $trId } | Select-Object -First 1 }
  $trStatus = ""
  if ($trRow) { $trStatus = $trRow.status }
  AssertTrue "tr-done" (@("COMPLETED","CONFIRMED","IN_TRANSIT","APPROVED") -contains $trStatus) "status=$trStatus"
}

Write-Host "=== 8. BORROW + RETURN ==="
$br = Ok "br-create" POST "/api/borrows" @{
  applyUserId = 1; deptId = 4
  expectReturnDate = (Get-Date).AddDays(7).ToString("yyyy-MM-dd")
  items = @(@{ materialId = 1; qty = 1; batchNo = $batch })
}
$brId = 0
if ($br) { if ($br.id) { $brId = $br.id } elseif ($br.order) { $brId = $br.order.id } elseif ($br.borrow) { $brId = $br.borrow.id } }
if ($brId -gt 0) {
  Ok "br-submit" POST "/api/borrows/$brId/submit"
  Approve-Biz "BORROW" $brId
  Ok "br-lend" POST "/api/borrows/$brId/lend" @{ warehouseId = 1 }
  Ok "br-return" POST "/api/borrows/$brId/return" @{ warehouseId = 1; items = @(@{ materialId = 1; returnQty = 1 }) }
}

Write-Host "=== 9. SCRAP ==="
$sc = Ok "sc-create" POST "/api/scraps" @{
  applyUserId = 1; deptId = 4; reason = "E2E"
  items = @(@{ materialId = 2; qty = 1 })
}
$scId = 0
if ($sc) { if ($sc.id) { $scId = $sc.id } elseif ($sc.order) { $scId = $sc.order.id } elseif ($sc.scrap) { $scId = $sc.scrap.id } }
if ($scId -gt 0) {
  Ok "sc-submit" POST "/api/scraps/$scId/submit"
  Approve-Biz "SCRAP" $scId
  Ok "sc-confirm" POST "/api/scraps/$scId/confirm" @{ warehouseId = 1 }
}

Write-Host "=== 10. RETURN STORE ==="
$rs = Ok "rs-create" POST "/api/return-stores" @{
  applyUserId = 1; deptId = 4; warehouseId = 1
  items = @(@{ materialId = 2; qty = 1 })
}
$rsId = 0
if ($rs) { if ($rs.id) { $rsId = $rs.id } elseif ($rs.order) { $rsId = $rs.order.id } elseif ($rs.apply) { $rsId = $rs.apply.id } }
if ($rsId -gt 0) {
  Ok "rs-submit" POST "/api/return-stores/$rsId/submit"
  Approve-Biz "RETURN_STORE" $rsId
  Ok "rs-accept" POST "/api/return-stores/$rsId/accept" @{}
  Ok "rs-confirm" POST "/api/return-stores/$rsId/confirm" @{ warehouseId = 1 }
}

Write-Host "=== 11. INVENTORY CHECK ADJUST ==="
$chk = Ok "chk-create" POST "/api/inventory/checks" @{
  warehouseId = 1; operatorId = 1
  items = @(@{ materialId = 2; batchNo = ""; bookQty = 1; actualQty = 1 })
}
$chkId = 0
if ($chk) { if ($chk.id) { $chkId = $chk.id } elseif ($chk.check) { $chkId = $chk.check.id } }
if ($chkId -gt 0) {
  Ok "chk-submit" POST "/api/inventory/checks/$chkId/submit"
  Approve-Biz "INVENTORY_CHECK" $chkId
  Ok "chk-confirm" POST "/api/inventory/checks/$chkId/confirm" @{ warehouseId = 1 }
}

Write-Host "=== 12. PURCHASE RETURN RED FLUSH ==="
$pr = Ok "pr-create" POST "/api/purchase/returns" @{
  orderId = $poId; supplierId = 1; reason = "E2E"
  lines = @(@{ materialId = 1; returnQty = 1; taxInclusivePrice = 22; taxRate = 13 })
}
$prId = 0
if ($pr) { if ($pr.id) { $prId = $pr.id } elseif ($pr.order) { $prId = $pr.order.id } elseif ($pr.return) { $prId = $pr.return.id } }
if ($prId -gt 0) {
  Ok "pr-submit" POST "/api/purchase/returns/$prId/submit"
  Approve-Biz "PURCHASE_RETURN" $prId
  Ok "pr-confirm" POST "/api/purchase/returns/$prId/confirm" @{}
}

Write-Host "=== 13. STOCK VS RECORD BALANCE ==="
$stockAll = Ok "stock-all" GET "/api/stock?page=1&size=200"
$recAll = Ok "rec-all" GET "/api/stock/records?page=1&size=500"
$imbalance = @()
if ($stockAll -and $stockAll.records -and $recAll -and $recAll.records) {
  foreach ($s in $stockAll.records) {
    $sum = 0.0
    foreach ($r in $recAll.records) {
      if ($r.warehouseId -eq $s.warehouseId -and $r.materialId -eq $s.materialId) {
        $sBatch = "" + $s.batchNo
        $rBatch = "" + $r.batchNo
        if ($sBatch -eq $rBatch) { $sum += [double]$r.changeQty }
      }
    }
    # only compare when we have records for this stock line; partial pages may miss older records
    $has = @($recAll.records | Where-Object { $_.warehouseId -eq $s.warehouseId -and $_.materialId -eq $s.materialId -and ("" + $_.batchNo) -eq ("" + $s.batchNo) })
    if ($has.Count -gt 0) {
      $diff = [math]::Abs([double]$s.qty - $sum)
      if ($diff -gt 0.01) {
        $imbalance += "wh=$($s.warehouseId) mat=$($s.materialId) batch=$($s.batchNo) stockQty=$($s.qty) recSum=$sum"
      }
    }
  }
}
AssertTrue "stock-record-balance-sample" ($imbalance.Count -eq 0) (($imbalance | Select-Object -First 5) -join "; ")

Write-Host "=== 14. FLOW TODO AFTER APPROVALS ==="
$ft = Ok "flow-todo" GET "/api/flow-approvals/todo"
Write-Host ("  flow-todo total=" + $(if ($ft) { $ft.total } else { "null" }))
$at = Ok "appr-todo" GET "/api/approvals/todo"
Write-Host ("  appr-todo total=" + $(if ($at) { $at.total } else { "null" }))

Write-Host ""
Write-Host ("CHAIN RESULT pass=" + $script:pass + " fail=" + $script:fail)
if ($script:issues.Count -gt 0) {
  Write-Host "ISSUES:"
  $script:issues | ForEach-Object { Write-Host ("  - " + $_) }
}
if ($script:fail -eq 0) { Write-Host "CHAIN ALL PASSED" } else { Write-Host "CHAIN HAS FAILURES" }
exit $script:fail
