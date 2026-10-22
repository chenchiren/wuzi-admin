package com.relex.wuzi.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.SysUser;
import com.relex.wuzi.mapper.SysUserMapper;
import com.relex.wuzi.security.JwtUtil;
import com.relex.wuzi.security.LoginSecurityService;
import com.relex.wuzi.service.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final PermissionService permissionService;
    private final LoginSecurityService loginSecurityService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Data
    public static class LoginReq {
        private String username;
        private String password;
    }

    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody LoginReq req, HttpServletRequest request) {
        String username = req.getUsername() == null ? "" : req.getUsername().trim();
        String ip = resolveIp(request);
        // 限流：username+IP 窗口内失败超限则拒绝
        loginSecurityService.checkAllowed(username, ip);

        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (user == null || user.getIsDeleted() == 1) {
            loginSecurityService.onFailure(username, ip);
            throw new BizException("用户不存在");
        }
        if (!encoder.matches(req.getPassword(), user.getPassword())) {
            loginSecurityService.onFailure(username, ip);
            throw new BizException(401, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            loginSecurityService.onFailure(username, ip);
            throw new BizException(401, "账号已停用");
        }
        loginSecurityService.onSuccess(username, ip);

        List<String> roles = userMapper.selectRoleCodes(user.getId());
        List<String> perms = permissionService.listUserPerms(user.getId(), roles);
        String token = jwtUtil.createToken(user.getId(), user.getUsername(), roles);
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("realName", user.getRealName());
        data.put("roles", roles);
        data.put("perms", perms);
        data.put("deptId", user.getDeptId());
        data.put("positionId", user.getPositionId());
        return R.ok(data);
    }

    @GetMapping("/me")
    public R<Map<String, Object>> me(@RequestAttribute("userId") Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(401, "用户不存在");
        }
        List<String> roles = userMapper.selectRoleCodes(user.getId());
        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("realName", user.getRealName());
        data.put("roles", roles);
        data.put("perms", permissionService.listUserPerms(user.getId(), roles));
        data.put("deptId", user.getDeptId());
        data.put("positionId", user.getPositionId());
        return R.ok(data);
    }

    /** 角色权限变更后刷新前端缓存 */
    @GetMapping("/perms")
    public R<Map<String, Object>> perms(@RequestAttribute("userId") Long userId,
                                        @RequestAttribute("roles") List<String> roles) {
        permissionService.evict(userId);
        return R.ok(Map.of(
                "roles", roles,
                "perms", permissionService.listUserPerms(userId, roles)
        ));
    }

    private String resolveIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            int comma = ip.indexOf(',');
            return comma > 0 ? ip.substring(0, comma).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr() == null ? "" : request.getRemoteAddr();
    }
}
