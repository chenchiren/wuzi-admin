# Full regression - ASCII only
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

function CheckText($name, $method, $path, $body = $null) {
  try {
    $uri = "$Base$path"
    $p = @{ Method = $method; Uri = $uri; ContentType = "application/json" }
    if ($script:headers) { $p.Headers = $script:headers }
    if ($body) { $p.Body = ($body | ConvertTo-Json -Depth 8) }
    $r = Invoke-WebRequest @p -UseBasicParsing
    if ($r.StatusCode -eq 200 -and $r.Content -and $r.Content.Length -gt 10) {
      $script:pass++
      Write-Host ("PASS  " + $name + " len=" + $r.Content.Length)
      return $r.Content
    } else {
      $script:fail++
      $script:issues += ("$name -> status=$($r.StatusCode) len=$($r.Content.Length)")
      Write-Host ("FAIL  " + $name)
      return $null
    }
  } catch {
    $script:fail++
    $script:issues += ("$name -> EX " + $_.Exception.Message)
    Write-Host ("FAIL  " + $name + " " + $_.Exception.Message)
    return $null
  }
}

function Check($name, $method, $path, $body = $null, $expectCode = 200) {
  try {
    $r = Req $method $path $body
    if ($r.code -eq $expectCode) {
      $script:pass++
      Write-Host ("PASS  " + $name)
      return $r.data
    } else {
      $script:fail++
      $script:issues += ("$name -> code=$($r.code) msg=$($r.msg)")
      Write-Host ("FAIL  " + $name + " code=" + $r.code + " " + $r.msg)
      return $null
    }
  } catch {
    $script:fail++
    $script:issues += ("$name -> EX " + $_.Exception.Message)
    Write-Host ("FAIL  " + $name + " " + $_.Exception.Message)
    return $null
  }
}

Write-Host "=== AUTH ==="
$login = Check "login" POST "/api/auth/login" @{ username = "admin"; password = "admin123" }
if ($login) { $script:headers = @{ Authorization = "Bearer $($login.token)" } }
Check "me" GET "/api/auth/me"

Write-Host "=== TAX / PRICE ==="
Check "tax-rates" GET "/api/tax-rates"
$calc = Check "price-calc" POST "/api/price/calc" @{ taxInclusivePrice = 22; taxRate = 13; qty = 100; mode = "INCLUSIVE" }
if ($calc) {
  $sum = [double]$calc.taxExclAmount + [double]$calc.taxAmount
  if ([math]::Abs([double]$calc.taxInclusiveAmount - $sum) -gt 0.02) {
    $script:fail++; $script:issues += "tax identity fail"
    Write-Host "FAIL  tax-identity"
  } else {
    $script:pass++; Write-Host "PASS  tax-identity"
  }
}
Check "price-example" GET "/api/price/example"

Write-Host "=== MASTER DATA ==="
Check "categories" GET "/api/categories"
Check "materials" GET "/api/materials?page=1&size=5"
Check "suppliers" GET "/api/suppliers?page=1&size=5"
Check "supplier-products" GET "/api/supplier-products?page=1&size=5"
Check "warehouses" GET "/api/warehouses/list"
Check "depts" GET "/api/depts"
Check "roles" GET "/api/roles/all"
Check "users" GET "/api/users"

Write-Host "=== PURCHASE CHAIN ==="
$po = Check "po-create" POST "/api/purchase/orders" @{
  supplierId = 1
  items = @(@{ materialId = 1; qty = 3; taxInclusivePrice = 22; taxRate = 13 })
}
$poId = 0
if ($po) { if ($po.order) { $poId = $po.order.id } elseif ($po.id) { $poId = $po.id } }
Check "po-list" GET "/api/purchase/orders?page=1&size=5"
if ($poId -gt 0) { Check "po-detail" GET "/api/purchase/orders/$poId" }

$pa = Check "pa-create" POST "/api/purchase/applies" @{
  applyUserId = 1; deptId = 4
  items = @(@{ materialId = 2; qty = 2; refPrice = 18 })
}
$paId = 0
if ($pa) { if ($pa.id) { $paId = $pa.id } elseif ($pa.apply) { $paId = $pa.apply.id } }
if ($paId -gt 0) {
  Check "pa-submit" POST "/api/purchase/applies/$paId/submit"
}
Check "pa-list" GET "/api/purchase/applies?page=1&size=5"
Check "inquiry-list" GET "/api/purchase/inquiries?page=1&size=5"
Check "arrival-list" GET "/api/purchase/arrivals?page=1&size=5"
Check "return-list" GET "/api/purchase/returns?page=1&size=5"
Check "approval-todo" GET "/api/approvals/todo"
Check "flow-approvals-flows" GET "/api/flow-approvals/flows"
Check "flow-todo" GET "/api/flow-approvals/todo"

Write-Host "=== WAREHOUSE ==="
Check "inbound-list" GET "/api/inbound/orders?page=1&size=5"
Check "outbound-list" GET "/api/outbound/orders?page=1&size=5"
Check "stock-list" GET "/api/stock?page=1&size=5"
Check "stock-records" GET "/api/stock/records?page=1&size=5"
Check "stock-batches" GET "/api/stock/batches?page=1&size=5"
Check "stock-alerts" GET "/api/stock/alerts"
Check "transfer-list" GET "/api/transfer/orders?page=1&size=5"
Check "check-list" GET "/api/inventory/checks?page=1&size=5"
Check "check-spot" POST "/api/inventory/checks/spot" @{ warehouseId = 1; sampleRatio = 10; sampleCount = 3 }
Check "return-stores" GET "/api/return-stores?page=1&size=5"
Check "red-flush-root" GET "/api/alerts"

Write-Host "=== USE / BORROW / SCRAP ==="
Check "material-use" GET "/api/material-use?page=1&size=5"
Check "borrows" GET "/api/borrows?page=1&size=5"
Check "borrows-overdue" GET "/api/borrows/overdue"
Check "scraps" GET "/api/scraps?page=1&size=5"

Write-Host "=== TRACE / LABEL ==="
$qr = Check "qr-gen" POST "/api/trace/qr/generate" @{
  qrType = "BATCH"; materialId = 2; materialName = "Pen"; materialCode = "PEN-001"
  batchNo = ("RT" + [DateTime]::Now.ToString("HHmmssfff"))
}
$qrId = 0
if ($qr) {
  $qrId = $qr.id
  Check "qr-public" GET ("/api/trace/public/" + $qr.traceToken)
  Check "qr-activate" POST "/api/trace/qr/$qrId/activate"
}
Check "qr-list" GET "/api/trace/qr?page=1&size=5"
Check "print-logs" GET "/api/trace/print-logs?page=1&size=5"
Check "m-scan-resolve" POST "/api/m/scan/resolve" @{ code = "SKU-MAT-1"; channel = "H5" }
Check "scan-recent" GET "/api/m/scan/recent"
if ($qrId -gt 0) {
  Check "label-print" POST "/api/trace/qr/$qrId/print" @{ copyCount = 1; printerName = "TEST" }
  CheckText "label-zpl" GET "/api/labels/zpl/$qrId"
  CheckText "label-html" GET "/api/labels/html/$qrId"
  Check "label-data" GET "/api/labels/data/$qrId"
} else {
  CheckText "label-zpl-fallback" GET "/api/labels/zpl/1"
}

Write-Host "=== FINANCE ==="
Check "invoices" GET "/api/invoices?page=1&size=5"
Check "settles" GET "/api/settles?page=1&size=5"
if ($poId -gt 0) {
  $inv = Check "invoice-create" POST "/api/invoices" @{
    invoiceNo = ("INV" + [DateTime]::Now.ToString("HHmmss"))
    invoiceType = "VAT_SPECIAL"
    orderId = $poId
    invoiceDate = "2026-09-24"
    taxRate = 13
    taxInclusiveAmount = 66
    taxAmount = 7.59
  }
  if ($inv) {
    $invId = $inv.id; if (-not $invId) { $invId = $inv.invoice.id }
    if ($invId) { Check "invoice-match" POST "/api/invoices/$invId/match" }
  }
}
Check "supplier-eval" GET "/api/suppliers/1/eval"
Check "supplier-eval-history" GET "/api/suppliers/1/eval/history"
Check "supplier-summary" GET "/api/suppliers/1/summary"
Check "price-trend" GET "/api/price-trends?materialId=1"
Check "price-trend-detail" GET "/api/price-trend-details?materialId=1&months=6"

Write-Host "=== REPORTS / ALERTS / MSG / LOG ==="
Check "rep-purchase" GET "/api/reports/purchase-monthly"
Check "rep-inout" GET "/api/reports/inout-monthly"
Check "rep-category" GET "/api/reports/category-stock"
Check "rep-warehouse" GET "/api/reports/warehouse-stock"
Check "rep-dept" GET "/api/reports/dept-use"
Check "rep-supplier" GET "/api/reports/supplier-rank"
Check "alerts" GET "/api/alerts"
Check "alerts-low" GET "/api/alerts/low-stock"
Check "alerts-over" GET "/api/alerts/over-stock"
Check "alerts-expiry" GET "/api/alerts/expiry"
Check "alerts-record" GET "/api/alerts/record?page=1&size=5"
Check "messages" GET "/api/messages?page=1&size=5"
Check "messages-unread" GET "/api/messages/unread-count"
Check "logs" GET "/api/logs?page=1&size=5"
Check "stats" GET "/api/stats/dashboard"
Check "stats-rank" GET "/api/stats/supplier-purchase-rank?limit=5"
Check "stats-inout" GET "/api/stats/inout"

Write-Host "=== MOBILE ==="
Check "m-home" GET "/api/m/home"
Check "m-todos" GET "/api/m/todos"
Check "m-stocks" GET "/api/m/stocks"
Check "m-me" GET "/api/m/me"
$sync = Check "m-sync-batch" POST "/api/m/sync/batch" @{
  requestId = ("SYNC" + [DateTime]::Now.Ticks)
  scanLogs = @(@{ qrCode = "SKU-MAT-1"; scanType = "QUERY"; channel = "H5" })
}

Write-Host "=== IMPORT CENTER ==="
Check "import-records" GET "/api/import/records?page=1&size=5"
Check "import-templates" GET "/api/import/templates?dataType=SUPPLIER"

Write-Host ""
Write-Host ("RESULT pass=" + $script:pass + " fail=" + $script:fail)
if ($script:issues.Count -gt 0) {
  Write-Host "ISSUES:"
  $script:issues | ForEach-Object { Write-Host ("  - " + $_) }
}
if ($script:fail -eq 0) { Write-Host "REGRESSION ALL PASSED" } else { Write-Host "REGRESSION HAS FAILURES" }
exit $script:fail
