package com.relex.wuzi.web.mobile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.SysUser;
import com.relex.wuzi.mapper.SysUserMapper;
import com.relex.wuzi.security.JwtUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 手机端会话：账号密码登录（复用 JWT）与当前用户信息。
 */
@RestController
@RequestMapping("/api/m")
@RequiredArgsConstructor
public class MobileAuthController {

    private final SysUserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Data
    public static class LoginReq {
        private String username;
        private String password;
    }

    @PostMapping("/auth/login")
    public R<Map<String, Object>> login(@RequestBody LoginReq req) {
        if (req.getUsername() == null || req.getUsername().isBlank()) {
            throw new BizException("用户名不能为空");
        }
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, req.getUsername()));
        if (user == null || (user.getIsDeleted() != null && user.getIsDeleted() == 1)) {
            throw new BizException("用户不存在");
        }
        if (!encoder.matches(req.getPassword() == null ? "" : req.getPassword(), user.getPassword())) {
            throw new BizException(401, "用户名或密码错误");
        }
        List<String> roles = userMapper.selectRoleCodes(user.getId());
        String token = jwtUtil.createToken(user.getId(), user.getUsername(), roles);
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("realName", user.getRealName());
        data.put("roles", roles);
        data.put("deptId", user.getDeptId());
        return R.ok(data);
    }

    @GetMapping("/me")
    public R<Map<String, Object>> me(@RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(401, "用户不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("realName", user.getRealName());
        data.put("roles", userMapper.selectRoleCodes(user.getId()));
        data.put("deptId", user.getDeptId());
        return R.ok(data);
    }
}
