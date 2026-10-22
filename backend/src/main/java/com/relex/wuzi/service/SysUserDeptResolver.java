package com.relex.wuzi.service;

import com.relex.wuzi.domain.SysUser;
import com.relex.wuzi.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 解析当前用户归属部门，用于写入业务单据 dept_id 以支撑数据权限。
 */
@Service
@RequiredArgsConstructor
public class SysUserDeptResolver {

    private final SysUserMapper sysUserMapper;

    public Long resolveDeptId(Long userId, Long fallbackDeptId) {
        if (userId == null) {
            return fallbackDeptId;
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user != null && user.getDeptId() != null) {
            return user.getDeptId();
        }
        return fallbackDeptId;
    }
}
