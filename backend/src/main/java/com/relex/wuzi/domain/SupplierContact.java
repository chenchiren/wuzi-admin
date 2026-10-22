package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 供应商联系人：一个供应商可挂多个联系人，其中至多一个主联系人。
 */
@Data
@TableName("supplier_contact")
public class SupplierContact {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long supplierId;
    private String contactName;
    private String phone;
    private String email;
    private String position;
    private Integer isPrimary;
    private String remark;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
