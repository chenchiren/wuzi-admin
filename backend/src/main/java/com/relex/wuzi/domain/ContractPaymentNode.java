package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("contract_payment_node")
public class ContractPaymentNode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long contractId;
    private String nodeName;
    private LocalDate planDate;
    private BigDecimal planAmount;
    private BigDecimal paidAmount;
    private String status;
    private String remark;
}
