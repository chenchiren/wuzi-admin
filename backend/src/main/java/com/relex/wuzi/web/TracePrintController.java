package com.relex.wuzi.web;

import com.relex.wuzi.annotation.RequirePerm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.TraceQrCode;
import com.relex.wuzi.domain.TraceQrPrintLog;
import com.relex.wuzi.mapper.TraceQrCodeMapper;
import com.relex.wuzi.mapper.TraceQrPrintLogMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 二维码打印登记：trace_qr_print_log + 回写 print_count。
 */
@RestController
@RequestMapping("/api/trace")
@RequiredArgsConstructor
public class TracePrintController {

    private final TraceQrCodeMapper qrMapper;
    private final TraceQrPrintLogMapper printLogMapper;

    @Data
    public static class PrintReq {
        private String printType;
        private String reprintReason;
        private String printBatchNo;
        private String templateCode;
        private String templateSize;
        private String printerName;
        private Integer copyCount;
        private String bizType;
        private Long bizId;
    }

    @RequirePerm("trace:qr:print")
    @PostMapping("/qr/{id}/print")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> print(@PathVariable Long id,
                                        @RequestBody(required = false) PrintReq req,
                                        @RequestAttribute(value = "userId", required = false) Long userId) {
        TraceQrCode qr = qrMapper.selectById(id);
        if (qr == null) {
            throw new BizException("码不存在");
        }
        PrintReq body = req != null ? req : new PrintReq();
        String printType = body.getPrintType() == null || body.getPrintType().isBlank()
                ? (qr.getPrintCount() == null || qr.getPrintCount() == 0 ? "FIRST" : "REPRINT")
                : body.getPrintType();
        int copies = body.getCopyCount() == null || body.getCopyCount() <= 0 ? 1 : body.getCopyCount();

        TraceQrPrintLog log = new TraceQrPrintLog();
        log.setQrId(qr.getId());
        log.setQrCode(qr.getQrCode());
        log.setPrintBatchNo(body.getPrintBatchNo());
        log.setPrintType(printType);
        log.setReprintReason(body.getReprintReason());
        log.setTemplateCode(body.getTemplateCode());
        log.setTemplateSize(body.getTemplateSize());
        log.setPrinterName(body.getPrinterName());
        log.setCopyCount(copies);
        log.setOperatorId(userId);
        log.setPrintTime(LocalDateTime.now());
        log.setBizType(body.getBizType());
        log.setBizId(body.getBizId());
        printLogMapper.insert(log);

        qr.setPrintCount((qr.getPrintCount() == null ? 0 : qr.getPrintCount()) + copies);
        qrMapper.updateById(qr);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("printLog", log);
        data.put("qr", qr);
        return R.ok(data);
    }

    @GetMapping({"/print-logs", "/qr/print-logs"})
    public R<PageResult<TraceQrPrintLog>> printLogs(@RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "10") long size,
                                                    @RequestParam(required = false) Long qrId,
                                                    @RequestParam(required = false) String qrCode,
                                                    @RequestParam(required = false) String printType) {
        LambdaQueryWrapper<TraceQrPrintLog> qw = new LambdaQueryWrapper<TraceQrPrintLog>()
                .eq(qrId != null, TraceQrPrintLog::getQrId, qrId)
                .eq(qrCode != null && !qrCode.isBlank(), TraceQrPrintLog::getQrCode, qrCode)
                .eq(printType != null && !printType.isBlank(), TraceQrPrintLog::getPrintType, printType)
                .orderByDesc(TraceQrPrintLog::getId);
        Page<TraceQrPrintLog> p = printLogMapper.selectPage(new Page<>(page, size), qw);
        return R.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }
}
