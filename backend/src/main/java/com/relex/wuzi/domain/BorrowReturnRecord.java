package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 借用归还记录。表 borrow_return_record 已建（见 sql/wuzi_full.sql）。
 */
@Data
@TableName("borrow_return_record")
public class BorrowReturnRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long borrowId;
    private Long borrowItemId;
    private Long materialId;
    private BigDecimal returnQty;
    private String conditionStatus;
    private Long operatorId;
    private String remark;
    private LocalDateTime returnTime;
    private LocalDateTime createdAt;
}
