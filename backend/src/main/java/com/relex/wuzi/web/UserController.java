package com.relex.wuzi.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.SysRole;
import com.relex.wuzi.domain.SysUser;
import com.relex.wuzi.domain.SysUserRole;
import com.relex.wuzi.mapper.SysRoleMapper;
import com.relex.wuzi.mapper.SysUserMapper;
import com.relex.wuzi.mapper.SysUserRoleMapper;
import com.relex.wuzi.annotation.RequirePerm;
import com.relex.wuzi.security.PasswordPolicy;
import com.relex.wuzi.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final OperationLogService operationLogService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @RequirePerm("sys:user:list")
    @GetMapping
    public R<PageResult<Map<String, Object>>> page(@RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "10") long size,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) Long deptId) {
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<SysUser>()
                .eq(deptId != null, SysUser::getDeptId, deptId)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(SysUser::getUsername, keyword)
                        .or().like(SysUser::getRealName, keyword)
                        .or().like(SysUser::getPhone, keyword))
                .orderByDesc(SysUser::getId);
        Page<SysUser> p = userMapper.selectPage(new Page<>(page, size), qw);
        List<Map<String, Object>> records = new ArrayList<>();
        for (SysUser u : p.getRecords()) {
            records.add(toView(u));
        }
        return R.ok(PageResult.of(p.getTotal(), records));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        return R.ok(toView(user));
    }

    @Data
    public static class CreateUserReq {
        private String username;
        private String password;
        private String realName;
        private String phone;
        private String email;
        private Long deptId;
        private Long positionId;
        private Integer status;
    }

    @RequirePerm("sys:user:update")
    @PutMapping("/{id}")
    public R<Map<String, Object>> update(@PathVariable Long id,
                                         @RequestBody CreateUserReq req,
                                         @RequestAttribute(value = "userId", required = false) Long opId,
                                         @RequestAttribute(value = "username", required = false) String opName,
                                         HttpServletRequest request) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        if (req.getRealName() != null && !req.getRealName().isBlank()) {
            user.setRealName(req.getRealName().trim());
        }
        if (req.getPhone() != null) {
            user.setPhone(req.getPhone());
        }
        if (req.getEmail() != null) {
            user.setEmail(req.getEmail());
        }
        if (req.getDeptId() != null) {
            user.setDeptId(req.getDeptId());
        }
        if (req.getPositionId() != null) {
            user.setPositionId(req.getPositionId());
        }
        if (req.getStatus() != null) {
            user.setStatus(req.getStatus());
        }
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        operationLogService.record(request, opId, opName, "USER", "UPDATE",
                "userId=" + id, "SUCCESS");
        return R.ok(toView(user));
    }

    @RequirePerm("sys:user:create")
    @PostMapping
    public R<Map<String, Object>> create(@RequestBody CreateUserReq req,
                                         @RequestAttribute(value = "userId", required = false) Long opId,
                                         @RequestAttribute(value = "username", required = false) String opName,
                                         HttpServletRequest request) {
        if (req.getUsername() == null || req.getUsername().isBlank()) {
            throw new BizException("用户名不能为空");
        }
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            throw new BizException("密码不能为空");
        }
        PasswordPolicy.validate(req.getPassword());
        if (req.getRealName() == null || req.getRealName().isBlank()) {
            throw new BizException("姓名不能为空");
        }
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, req.getUsername().trim()));
        if (exists != null && exists > 0) {
            throw new BizException("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(req.getUsername().trim());
        user.setPassword(encoder.encode(req.getPassword()));
        user.setRealName(req.getRealName().trim());
        user.setPhone(req.getPhone());
        user.setEmail(req.getEmail());
        user.setDeptId(req.getDeptId());
        user.setPositionId(req.getPositionId());
        user.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        user.setIsDeleted(0);
        user.setCreatedAt(LocalDateTime.now());
        userMapper.insert(user);
        operationLogService.record(request, opId, opName, "USER", "CREATE",
                "username=" + user.getUsername(), "SUCCESS");
        return R.ok(toView(user));
    }

    @Data
    public static class ChangePasswordReq {
        private String oldPassword;
        private String password;
        /** true 表示管理员重置：未传 password 时生成默认密码并在返回中提示 */
        private Boolean reset;
    }

    @RequirePerm("sys:user:reset-pwd")
    @PutMapping("/{id}/password")
    public R<Map<String, Object>> changePassword(@PathVariable Long id,
                                                 @RequestBody ChangePasswordReq req,
                                                 @RequestAttribute(value = "userId", required = false) Long opId,
                                                 @RequestAttribute(value = "username", required = false) String opName,
                                                 HttpServletRequest request) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        boolean adminReset = Boolean.TRUE.equals(req.getReset());
        String newPassword = req.getPassword();
        String hint = null;
        if (adminReset && (newPassword == null || newPassword.isBlank())) {
            // 管理员重置：生成默认密码并提示
            newPassword = PasswordPolicy.generateDefaultPassword();
            hint = PasswordPolicy.resetHint();
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new BizException("新密码不能为空");
        }
        PasswordPolicy.validate(newPassword);
        // 提供 oldPassword 时校验旧密码；管理员重置可不传
        if (!adminReset && req.getOldPassword() != null && !req.getOldPassword().isBlank()) {
            if (!encoder.matches(req.getOldPassword(), user.getPassword())) {
                throw new BizException(401, "原密码错误");
            }
        }
        user.setPassword(encoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        operationLogService.record(request, opId, opName, "USER", "RESET_PASSWORD",
                "userId=" + id + ", username=" + user.getUsername(), "SUCCESS");
        Map<String, Object> data = new HashMap<>();
        data.put("reset", adminReset);
        if (hint != null) {
            data.put("hint", hint);
            data.put("defaultPassword", newPassword);
        }
        return R.ok(data);
    }

    @Data
    public static class AssignRolesReq {
        private List<Long> roleIds;
    }

    @RequirePerm("sys:user:assign-role")
    @PostMapping("/{id}/roles")
    public R<Map<String, Object>> assignRoles(@PathVariable Long id,
                                              @RequestBody AssignRolesReq req,
                                              @RequestAttribute(value = "userId", required = false) Long opId,
                                              @RequestAttribute(value = "username", required = false) String opName,
                                              HttpServletRequest request) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        userRoleMapper.deleteByUserId(id);
        List<Long> roleIds = req.getRoleIds() == null ? List.of() : req.getRoleIds();
        for (Long roleId : roleIds) {
            SysRole role = roleMapper.selectById(roleId);
            if (role == null) {
                throw new BizException("角色不存在：" + roleId);
            }
            SysUserRole ur = new SysUserRole();
            ur.setUserId(id);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }
        operationLogService.record(request, opId, opName, "USER", "ASSIGN_ROLES",
                "userId=" + id + ", roleIds=" + roleIds, "SUCCESS");
        return R.ok(toView(userMapper.selectById(id)));
    }

    private Map<String, Object> toView(SysUser u) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("realName", u.getRealName());
        m.put("phone", u.getPhone());
        m.put("email", u.getEmail());
        m.put("deptId", u.getDeptId());
        m.put("positionId", u.getPositionId());
        m.put("companyId", u.getCompanyId());
        m.put("status", u.getStatus());
        m.put("lastLoginAt", u.getLastLoginAt());
        m.put("createdAt", u.getCreatedAt());
        List<Long> roleIds = userRoleMapper.selectRoleIds(u.getId());
        m.put("roleIds", roleIds);
        List<String> roleCodes = userMapper.selectRoleCodes(u.getId());
        m.put("roles", roleCodes);
        return m;
    }
}
