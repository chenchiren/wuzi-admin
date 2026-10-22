# Functional tests for plan/budget/contract/payment/position/config - ASCII only
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

Write-Host "=== LOGIN ==="
$login = Ok "login" POST "/api/auth/login" @{ username = "admin"; password = "admin123" }
$script:headers = @{ Authorization = "Bearer $($login.token)" }

Write-Host "=== POSITIONS ==="
Ok "positions" GET "/api/positions?page=1&size=5"
Ok "positions-all" GET "/api/positions/all"
$pos = Ok "position-create" POST "/api/positions" @{
  positionCode = ("P" + [DateTime]::Now.ToString("HHmmss"))
  positionName = "E2E_TEST_POS"
  sortNo = 99
}
$posId = 0
if ($pos) { if ($pos.id) { $posId = $pos.id } }
if ($posId -gt 0) {
  Ok "position-update" PUT "/api/positions/$posId" @{ positionName = "E2E_TEST_POS2"; sortNo = 98 }
  Ok "position-delete" DELETE "/api/positions/$posId"
}

Write-Host "=== PURCHASE PLAN ==="
$plan = Ok "plan-create" POST "/api/purchase-plans" @{
  title = "E2E_PLAN"
  planYear = 2026
  planMonth = 10
  deptId = 3
  planType = "MONTHLY"
  items = @(
    @{ materialId = 1; materialName = "A4"; qty = 10; estimatedPrice = 22 },
    @{ materialId = 2; materialName = "PEN"; qty = 5; estimatedPrice = 18 }
  )
}
$planId = 0
$planItemId = 0
if ($plan) {
  if ($plan.id) { $planId = $plan.id }
  elseif ($plan.plan) { $planId = $plan.plan.id }
}
if ($planId -gt 0) {
  Ok "plan-detail" GET "/api/purchase-plans/$planId"
  $items0 = Ok "plan-items" GET "/api/purchase-plans/$planId/items"
  AssertTrue "plan-has-items" ((@($items0)).Count -ge 1) "items=$((@($items0)).Count)"
  Ok "plan-update" PUT "/api/purchase-plans/$planId" @{
    title = "E2E_PLAN_UPD"
    planYear = 2026
    planMonth = 10
    deptId = 3
    planType = "MONTHLY"
    items = @(@{ materialId = 1; materialName = "A4"; qty = 12; estimatedPrice = 22 })
  }
  # replaceItems regenerates line ids — fetch again before convert
  $items1 = Ok "plan-items-after-update" GET "/api/purchase-plans/$planId/items"
  $itemArr = @($items1)
  if ($itemArr.Count -gt 0) { $planItemId = $itemArr[0].id }
  $sub = Ok "plan-submit" POST "/api/purchase-plans/$planId/submit"
  $appr = Ok "plan-approve" POST "/api/purchase-plans/$planId/approve"
  $planStatus = ""
  if ($appr) { $planStatus = $appr.status }
  AssertTrue "plan-approved" ($planStatus -eq "APPROVED") "status=$planStatus"
  if ($planItemId -gt 0) {
    Ok "plan-item-convert" POST "/api/purchase-plans/items/$planItemId/convert?applyId=1"
  }
  Ok "plan-list" GET "/api/purchase-plans?page=1&size=5"
}

Write-Host "=== BUDGET ==="
$bud = Ok "budget-create" POST "/api/budgets" @{
  budgetYear = 2026
  deptId = 3
  category = "PURCHASE"
  budgetAmount = 100000
  remark = "E2E"
}
$budId = 0
if ($bud) { if ($bud.id) { $budId = $bud.id } }
if ($budId -gt 0) {
  $bd = Ok "budget-detail" GET "/api/budgets/$budId"
  AssertTrue "budget-available" ($bd -and $bd.available -ne $null) "available missing"
  Ok "budget-update" PUT "/api/budgets/$budId" @{ budgetAmount = 120000; remark = "E2E_UPD" }
  $tg = Ok "budget-toggle" POST "/api/budgets/$budId/toggle"
  AssertTrue "budget-frozen" ($tg -and $tg.status -eq "FROZEN") "status=$($tg.status)"
  $tg2 = Ok "budget-toggle-back" POST "/api/budgets/$budId/toggle"
  AssertTrue "budget-active" ($tg2 -and $tg2.status -eq "ACTIVE") "status=$($tg2.status)"
  Ok "budget-logs" GET "/api/budgets/$budId/logs"
  Ok "budget-list" GET "/api/budgets?page=1&size=5&budgetYear=2026"
}

Write-Host "=== CONTRACT ==="
$ct = Ok "contract-create" POST "/api/contracts" @{
  title = "E2E_CONTRACT"
  contractType = "PURCHASE"
  supplierId = 1
  signDate = "2026-09-28"
  startDate = "2026-10-01"
  endDate = "2026-12-31"
  budgetId = $budId
  deptId = 3
  ownerId = 1
  items = @(
    @{ materialId = 1; materialName = "A4"; qty = 100; price = 22; taxRate = 13 }
  )
  payNodes = @(
    @{ nodeName = "PREPAY"; planDate = "2026-10-05"; planAmount = 1000 },
    @{ nodeName = "FINAL"; planDate = "2026-12-01"; planAmount = 1200 }
  )
}
$ctId = 0
if ($ct) {
  if ($ct.id) { $ctId = $ct.id }
  elseif ($ct.contract) { $ctId = $ct.contract.id }
}
if ($ctId -gt 0) {
  Ok "contract-detail" GET "/api/contracts/$ctId"
  $ci = Ok "contract-items" GET "/api/contracts/$ctId/items"
  AssertTrue "contract-has-items" ((@($ci)).Count -ge 1) "items=$((@($ci)).Count)"
  $pn = Ok "contract-pay-nodes" GET "/api/contracts/$ctId/pay-nodes"
  AssertTrue "contract-has-nodes" ((@($pn)).Count -ge 1) "nodes=$((@($pn)).Count)"
  Ok "contract-submit" POST "/api/contracts/$ctId/submit"
  $act = Ok "contract-activate" POST "/api/contracts/$ctId/activate"
  $ctStatus = ""
  if ($act) { $ctStatus = $act.status }
  AssertTrue "contract-active" (@("ACTIVE","EFFECTIVE","VALID") -contains $ctStatus) "status=$ctStatus"
  Ok "contract-list" GET "/api/contracts?page=1&size=5"
}

Write-Host "=== PAYMENT ==="
$pay = Ok "payment-create" POST "/api/payments" @{
  supplierId = 1
  contractId = $ctId
  budgetId = $budId
  amount = 500
  payMethod = "BANK"
  payDate = "2026-10-08"
  deptId = 3
  remark = "E2E"
}
$payId = 0
if ($pay) {
  if ($pay.id) { $payId = $pay.id }
  elseif ($pay.payment) { $payId = $pay.payment.id }
}
if ($payId -gt 0) {
  Ok "payment-detail" GET "/api/payments/$payId"
  Ok "payment-submit" POST "/api/payments/$payId/submit"
  $conf = Ok "payment-confirm" POST "/api/payments/$payId/confirm"
  $payStatus = ""
  if ($conf) { $payStatus = $conf.status }
  AssertTrue "payment-paid" (@("PAID","CONFIRMED","COMPLETED") -contains $payStatus) "status=$payStatus"
}
Ok "payment-list" GET "/api/payments?page=1&size=5"
Ok "ap-aging" GET "/api/payments/ap-aging"

Write-Host "=== SYS CONFIG / DICTS / PERMS ==="
Ok "sys-config" GET "/api/configs"
Ok "sys-config-save" POST "/api/configs" @{
  configKey = "e2e.test.key"
  configValue = "1"
  configName = "E2E"
}
Ok "sys-config-get" GET "/api/configs/key/e2e.test.key"
Ok "dict-types" GET "/api/dicts/types"
Ok "dict-catalog" GET "/api/dicts/catalog"
Ok "perms-tree" GET "/api/permissions/tree"
Ok "auth-perms" GET "/api/auth/perms"
Ok "flow-configs" GET "/api/flow-approvals/flows"

Write-Host "=== SETTLES / ATTACHMENTS ==="
Ok "settles-list" GET "/api/settles?page=1&size=5"
Ok "attachments-list" GET "/api/attachments?bizType=CONTRACT&bizId=1"
Ok "labels-template" GET "/api/labels/template"

Write-Host ""
Write-Host ("SUPPLEMENT RESULT pass=" + $script:pass + " fail=" + $script:fail)
if ($script:issues.Count -gt 0) {
  Write-Host "ISSUES:"
  $script:issues | ForEach-Object { Write-Host ("  - " + $_) }
}
if ($script:fail -eq 0) { Write-Host "SUPPLEMENT ALL PASSED" } else { Write-Host "SUPPLEMENT HAS FAILURES" }
exit $script:fail
