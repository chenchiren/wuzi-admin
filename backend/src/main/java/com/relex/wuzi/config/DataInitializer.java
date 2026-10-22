package com.relex.wuzi.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.domain.SysUser;
import com.relex.wuzi.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 启动时确保 admin/admin123 可登录（修正常见假 hash）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final SysUserMapper userMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public void run(String... args) {
        String raw = "admin123";
        var users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getUsername, "admin", "wh01", "pur01", "rd01"));
        for (SysUser u : users) {
            if (!encoder.matches(raw, u.getPassword())) {
                u.setPassword(encoder.encode(raw));
                userMapper.updateById(u);
                log.info("已重置用户 {} 密码为 admin123", u.getUsername());
            }
        }
    }
}
