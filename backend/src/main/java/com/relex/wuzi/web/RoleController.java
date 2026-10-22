package com.relex.wuzi.web;

import com.relex.wuzi.annotation.RequirePerm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.SysPermission;
import com.relex.wuzi.domain.SysRole;
import com.relex.wuzi.domain.SysRolePermission;
import com.relex.wuzi.mapper.SysPermissionMapper;
import com.relex.wuzi.mapper.SysRoleMapper;
import com.relex.wuzi.mapper.SysRolePermissionMapper;
import com.relex.wuzi.mapper.SysUserRoleMapper;
import com.relex.wuzi.service.OperationLogService;
import com.relex.wuzi.service.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RoleController {

    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final OperationLogService operationLogService;
    private final PermissionService permissionService;

    @GetMapping("/roles")
    public R<PageResult<SysRole>> page(@RequestParam(defaultValue = "1") long page,
                                       @RequestParam(defaultValue = "10") long size,
                                       @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<SysRole> qw = new LambdaQueryWrapper<SysRole>()
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(SysRole::getRoleCode, keyword)
                        .or().like(SysRole::getRoleName, keyword))
                .orderByAsc(SysRole::getId);
        Page<SysRole> p = roleMapper.selectPage(new Page<>(page, size), qw);
        return R.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @GetMapping("/roles/all")
    public R<List<SysRole>> all() {
        return R.ok(roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .orderByAsc(SysRole::getId)));
    }

    @Data
    public static class SaveRoleReq {
        private Long id;
        private String roleCode;
        private String roleName;
        private String remark;
    }

    @RequirePerm("sys:role:create")
    @PostMapping("/roles")
    public R<SysRole> create(@RequestBody SaveRoleReq req,
                             @RequestAttribute(value = "userId", required = false) Long opId,
                             @RequestAttribute(value = "username", required = false) String opName,
                             HttpServletRequest request) {
        if (req.getRoleCode() == null || req.getRoleCode().isBlank()) {
            throw new BizException("角色编码不能为空");
        }
        if (req.getRoleName() == null || req.getRoleName().isBlank()) {
            throw new BizException("角色名称不能为空");
        }
        Long exists = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, req.getRoleCode().trim()));
        if (exists != null && exists > 0) {
            throw new BizException("角色编码已存在");
        }
        SysRole role = new SysRole();
        role.setRoleCode(req.getRoleCode().trim());
        role.setRoleName(req.getRoleName().trim());
        role.setRemark(req.getRemark());
        role.setIsDeleted(0);
        role.setCreatedAt(LocalDateTime.now());
        roleMapper.insert(role);
        operationLogService.record(request, opId, opName, "ROLE", "CREATE",
                "roleCode=" + role.getRoleCode(), "SUCCESS");
        return R.ok(role);
    }

    @RequirePerm("sys:role:update")
    @PutMapping("/roles/{id}")
    public R<SysRole> update(@PathVariable Long id, @RequestBody SaveRoleReq req,
                             @RequestAttribute(value = "userId", required = false) Long opId,
                             @RequestAttribute(value = "username", required = false) String opName,
                             HttpServletRequest request) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException("角色不存在");
        }
        if (req.getRoleCode() != null && !req.getRoleCode().isBlank()
                && !req.getRoleCode().equals(role.getRoleCode())) {
            Long exists = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                    .eq(SysRole::getRoleCode, req.getRoleCode().trim()));
            if (exists != null && exists > 0) {
                throw new BizException("角色编码已存在");
            }
            role.setRoleCode(req.getRoleCode().trim());
        }
        if (req.getRoleName() != null && !req.getRoleName().isBlank()) {
            role.setRoleName(req.getRoleName().trim());
        }
        role.setRemark(req.getRemark());
        role.setUpdatedAt(LocalDateTime.now());
        roleMapper.updateById(role);
        operationLogService.record(request, opId, opName, "ROLE", "UPDATE",
                "roleId=" + id, "SUCCESS");
        return R.ok(role);
    }

    @RequirePerm("sys:role:delete")
    @DeleteMapping("/roles/{id}")
    public R<Void> delete(@PathVariable Long id,
                          @RequestAttribute(value = "userId", required = false) Long opId,
                          @RequestAttribute(value = "username", required = false) String opName,
                          HttpServletRequest request) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException("角色不存在");
        }
        roleMapper.deleteById(id);
        rolePermissionMapper.deleteByRoleId(id);
        // 清理用户-角色关联
        userRoleMapper.delete(new LambdaQueryWrapper<com.relex.wuzi.domain.SysUserRole>()
                .eq(com.relex.wuzi.domain.SysUserRole::getRoleId, id));
        operationLogService.record(request, opId, opName, "ROLE", "DELETE",
                "roleId=" + id + ", roleCode=" + role.getRoleCode(), "SUCCESS");
        return R.ok();
    }

    @GetMapping("/roles/{id}/permissions")
    public R<Map<String, Object>> rolePermissions(@PathVariable Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException("角色不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("roleId", id);
        data.put("roleCode", role.getRoleCode());
        data.put("roleName", role.getRoleName());
        data.put("permIds", rolePermissionMapper.selectPermIds(id));
        data.put("permissions", buildPermTree(permissionMapper.selectList(null)));
        return R.ok(data);
    }

    @Data
    public static class AssignPermReq {
        private List<Long> permIds;
    }

    @RequirePerm("sys:role:assign-perm")
    @PostMapping("/roles/{id}/permissions")
    public R<Map<String, Object>> assignPermissions(@PathVariable Long id,
                                                    @RequestBody AssignPermReq req,
                                                    @RequestAttribute(value = "userId", required = false) Long opId,
                                                    @RequestAttribute(value = "username", required = false) String opName,
                                                    HttpServletRequest request) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException("角色不存在");
        }
        rolePermissionMapper.deleteByRoleId(id);
        List<Long> permIds = req.getPermIds() == null ? List.of() : req.getPermIds();
        for (Long permId : permIds) {
            SysPermission perm = permissionMapper.selectById(permId);
            if (perm == null) {
                throw new BizException("权限点不存在：" + permId);
            }
            SysRolePermission rp = new SysRolePermission();
            rp.setRoleId(id);
            rp.setPermId(permId);
            rolePermissionMapper.insert(rp);
        }
        operationLogService.record(request, opId, opName, "ROLE", "ASSIGN_PERMS",
                "roleId=" + id + ", permIds=" + permIds, "SUCCESS");
        // 角色权限变更后清空权限缓存，下次登录/刷新 /auth/perms 生效
        permissionService.evict(null);
        Map<String, Object> data = new HashMap<>();
        data.put("roleId", id);
        data.put("permIds", rolePermissionMapper.selectPermIds(id));
        return R.ok(data);
    }

    @GetMapping("/permissions")
    public R<List<SysPermission>> listPerms() {
        return R.ok(permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                .orderByAsc(SysPermission::getSortNo)
                .orderByAsc(SysPermission::getId)));
    }

    @GetMapping("/permissions/tree")
    public R<List<Map<String, Object>>> permTree() {
        return R.ok(buildPermTree(permissionMapper.selectList(null)));
    }

    private List<Map<String, Object>> buildPermTree(List<SysPermission> all) {
        Map<Long, Map<String, Object>> nodes = new HashMap<>();
        for (SysPermission p : all) {
            Map<String, Object> n = new HashMap<>();
            n.put("id", p.getId());
            n.put("parentId", p.getParentId() == null ? 0L : p.getParentId());
            n.put("permCode", p.getPermCode());
            n.put("permName", p.getPermName());
            n.put("permType", p.getPermType());
            n.put("path", p.getPath());
            n.put("sortNo", p.getSortNo());
            n.put("children", new ArrayList<Map<String, Object>>());
            nodes.put(p.getId(), n);
        }
        List<Map<String, Object>> roots = new ArrayList<>();
        for (Map<String, Object> n : nodes.values()) {
            Long parentId = (Long) n.get("parentId");
            Map<String, Object> parent = nodes.get(parentId);
            if (parent != null && parent != n) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) parent.get("children");
                children.add(n);
            } else {
                roots.add(n);
            }
        }
        sortTree(roots);
        return roots;
    }

    @SuppressWarnings("unchecked")
    private void sortTree(List<Map<String, Object>> nodes) {
        nodes.sort(Comparator.comparing(n -> {
            Object s = n.get("sortNo");
            return s == null ? 0 : (Integer) s;
        }));
        for (Map<String, Object> n : nodes) {
            List<Map<String, Object>> children = (List<Map<String, Object>>) n.get("children");
            if (children != null && !children.isEmpty()) {
                sortTree(children);
            }
        }
    }
}
