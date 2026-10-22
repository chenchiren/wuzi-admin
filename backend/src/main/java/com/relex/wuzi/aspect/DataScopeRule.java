package com.relex.wuzi.aspect;

import lombok.Data;

import java.util.List;

/**
 * 当前线程的数据权限规则（由 DataScopeAspect 写入）。
 */
@Data
public class DataScopeRule {

    /** 超管 / 不限数据 */
    private boolean all;

    private String deptField;
    private String userField;
    private String warehouseField;

    private Long userId;
    private Long deptId;
    private Long companyId;
    private List<Long> deptIds;
    private List<Long> warehouseIds;
    private List<String> roles;
}
