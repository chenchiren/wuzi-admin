package com.relex.wuzi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.relex.wuzi.domain.SysRolePermission;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysRolePermissionMapper extends BaseMapper<SysRolePermission> {

    @Select("SELECT perm_id FROM sys_role_permission WHERE role_id = #{roleId}")
    List<Long> selectPermIds(Long roleId);

    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    int deleteByRoleId(Long roleId);
}
