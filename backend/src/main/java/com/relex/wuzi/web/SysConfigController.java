package com.relex.wuzi.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.annotation.RequirePerm;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.SysConfig;
import com.relex.wuzi.mapper.SysConfigMapper;
import com.relex.wuzi.web.annotation.OpLog;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统配置中心：统一 key/value 参数。
 */
@RestController
@RequestMapping("/api/configs")
@RequiredArgsConstructor
public class SysConfigController {

    private final SysConfigMapper configMapper;

    @Data
    public static class SaveReq {
        private String configKey;
        private String configValue;
        private String configName;
        private String remark;
    }

    @GetMapping
    public R<List<SysConfig>> list(@RequestParam(required = false) String keyword) {
        return R.ok(configMapper.selectList(new LambdaQueryWrapper<SysConfig>()
                .and(keyword != null && !keyword.isBlank(),
                        w -> w.like(SysConfig::getConfigKey, keyword)
                                .or().like(SysConfig::getConfigName, keyword))
                .orderByAsc(SysConfig::getConfigKey)));
    }

    @GetMapping("/key/{key}")
    public R<SysConfig> getByKey(@PathVariable String key) {
        SysConfig c = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key));
        return R.ok(c);
    }

    @PostMapping
    @RequirePerm("sys:config:save")
    @OpLog(module = "业务", action = "保存系统配置")
    public R<SysConfig> save(@RequestBody SaveReq req, @RequestAttribute("userId") Long userId) {
        if (req.getConfigKey() == null || req.getConfigKey().isBlank()) {
            throw new BizException("configKey 不能为空");
        }
        SysConfig c = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, req.getConfigKey()));
        if (c == null) {
            c = new SysConfig();
            c.setConfigKey(req.getConfigKey().trim());
            c.setConfigValue(req.getConfigValue());
            c.setConfigName(req.getConfigName());
            c.setRemark(req.getRemark());
            c.setIsDeleted(0);
            c.setCreatedBy(userId);
            c.setCreatedAt(LocalDateTime.now());
            configMapper.insert(c);
        } else {
            c.setConfigValue(req.getConfigValue());
            if (req.getConfigName() != null) c.setConfigName(req.getConfigName());
            if (req.getRemark() != null) c.setRemark(req.getRemark());
            c.setUpdatedBy(userId);
            c.setUpdatedAt(LocalDateTime.now());
            configMapper.updateById(c);
        }
        return R.ok(c);
    }

    @DeleteMapping("/{id}")
    @RequirePerm("sys:config:delete")
    @OpLog(module = "业务", action = "删除系统配置")
    public R<Void> delete(@PathVariable Long id) {
        SysConfig c = configMapper.selectById(id);
        if (c != null) {
            c.setIsDeleted(1);
            configMapper.updateById(c);
        }
        return R.ok();
    }
}
