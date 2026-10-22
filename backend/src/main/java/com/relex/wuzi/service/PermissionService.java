package com.relex.wuzi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.domain.SysPermission;
import com.relex.wuzi.mapper.SysPermissionMapper;
import com.relex.wuzi.mapper.SysRolePermissionMapper;
import com.relex.wuzi.mapper.SysUserMapper;
import com.relex.wuzi.mapper.SysUserRoleMapper;
import com.relex.wuzi.domain.SysRolePermission;
import com.relex.wuzi.domain.SysUserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户权限码查询（按钮/菜单）。
 * ADMIN 直接拥有全部权限；其它角色按 sys_role_permission 聚合。
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    public static final String ADMIN = "ADMIN";

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysPermissionMapper permissionMapper;

    /** userId -> perm codes，短缓存；角色分配后调用 evict */
    private final Map<Long, Set<String>> cache = new ConcurrentHashMap<>();

    public void evict(Long userId) {
        if (userId != null) {
            cache.remove(userId);
        } else {
            cache.clear();
        }
    }

    public Set<String> permCodes(Long userId, List<String> roles) {
        if (userId == null) {
            return Set.of();
        }
        if (roles != null && roles.contains(ADMIN)) {
            return allCodes();
        }
        return cache.computeIfAbsent(userId, this::loadFromDb);
    }

    public boolean hasPerm(Long userId, List<String> roles, String code, boolean requireAll, String... codes) {
        if (code == null && (codes == null || codes.length == 0)) {
            return true;
        }
        if (roles != null && roles.contains(ADMIN)) {
            return true;
        }
        Set<String> owned = permCodes(userId, roles);
        List<String> need = new ArrayList<>();
        if (code != null && !code.isBlank()) {
            need.add(code.trim());
        }
        if (codes != null) {
            for (String c : codes) {
                if (c != null && !c.isBlank()) {
                    need.add(c.trim());
                }
            }
        }
        if (need.isEmpty()) {
            return true;
        }
        if (requireAll) {
            return owned.containsAll(need);
        }
        for (String c : need) {
            if (owned.contains(c)) {
                return true;
            }
        }
        return false;
    }

    public List<String> listUserPerms(Long userId, List<String> roles) {
        return new ArrayList<>(permCodes(userId, roles));
    }

    private Set<String> loadFromDb(Long userId) {
        Set<String> codes = new HashSet<>();
        List<SysUserRole> urs = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));
        if (urs == null || urs.isEmpty()) {
            return codes;
        }
        Set<Long> roleIds = new HashSet<>();
        for (SysUserRole ur : urs) {
            roleIds.add(ur.getRoleId());
        }
        Set<Long> permIds = new HashSet<>();
        for (Long roleId : roleIds) {
            List<Long> ids = rolePermissionMapper.selectPermIds(roleId);
            if (ids != null) {
                permIds.addAll(ids);
            }
        }
        if (permIds.isEmpty()) {
            return codes;
        }
        List<SysPermission> perms = permissionMapper.selectBatchIds(permIds);
        for (SysPermission p : perms) {
            if (p != null && p.getPermCode() != null && (p.getIsDeleted() == null || p.getIsDeleted() == 0)) {
                codes.add(p.getPermCode());
            }
        }
        return codes;
    }

    public Set<String> allCodes() {
        List<SysPermission> all = permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getIsDeleted, 0));
        Set<String> codes = new HashSet<>();
        for (SysPermission p : all) {
            if (p.getPermCode() != null) {
                codes.add(p.getPermCode());
            }
        }
        return codes;
    }
}
