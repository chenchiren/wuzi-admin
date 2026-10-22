package com.relex.wuzi.web;

import com.relex.wuzi.annotation.RequirePerm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.SysDict;
import com.relex.wuzi.domain.SysDictType;
import com.relex.wuzi.mapper.SysDictMapper;
import com.relex.wuzi.mapper.SysDictTypeMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 字典：按「事件类型」组织。
 * - 类型列表：某事件（如入库）的说明
 * - 明细列表：该事件下全部状态/枚举
 * - map：前端缓存 { TYPE_CODE: { key: label } }
 */
@RestController
@RequestMapping("/api/dicts")
@RequiredArgsConstructor
public class SysDictController {

    private final SysDictMapper dictMapper;
    private final SysDictTypeMapper typeMapper;

    @Data
    public static class SaveTypeReq {
        private Long id;
        private String typeCode;
        private String typeName;
        private String typeGroup;
        private String usageRemark;
        private Integer sortNo;
        private Integer enabled;
    }

    @Data
    public static class SaveItemReq {
        private Long id;
        private String dictType;
        private String dictKey;
        private String dictLabel;
        private Integer sortNo;
        private Integer enabled;
        private String remark;
    }

    /** 类型列表（事件列表） */
    @GetMapping("/types")
    public R<List<SysDictType>> types(@RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) String typeGroup) {
        LambdaQueryWrapper<SysDictType> qw = new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getIsDeleted, 0)
                .eq(typeGroup != null && !typeGroup.isBlank(), SysDictType::getTypeGroup, typeGroup)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(SysDictType::getTypeCode, keyword)
                        .or().like(SysDictType::getTypeName, keyword))
                .orderByAsc(SysDictType::getSortNo)
                .orderByAsc(SysDictType::getId);
        return R.ok(typeMapper.selectList(qw));
    }

    /** 某事件下的全部字典项（列表调用） */
    @GetMapping("/items")
    public R<List<SysDict>> items(@RequestParam String typeCode,
                                  @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<SysDict> qw = new LambdaQueryWrapper<SysDict>()
                .eq(SysDict::getIsDeleted, 0)
                .eq(SysDict::getDictType, typeCode)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(SysDict::getDictKey, keyword)
                        .or().like(SysDict::getDictLabel, keyword))
                .orderByAsc(SysDict::getSortNo)
                .orderByAsc(SysDict::getId);
        return R.ok(dictMapper.selectList(qw));
    }

    /** 类型 + 明细 一次返回（管理页左列表右明细） */
    @GetMapping("/catalog")
    public R<Map<String, Object>> catalog() {
        List<SysDictType> types = typeMapper.selectList(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getIsDeleted, 0)
                .orderByAsc(SysDictType::getSortNo)
                .orderByAsc(SysDictType::getId));
        List<SysDict> items = dictMapper.selectList(new LambdaQueryWrapper<SysDict>()
                .eq(SysDict::getIsDeleted, 0)
                .orderByAsc(SysDict::getDictType)
                .orderByAsc(SysDict::getSortNo)
                .orderByAsc(SysDict::getId));
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("types", types);
        map.put("items", items);
        return R.ok(map);
    }

    /** 前端全量缓存：{ TYPE_CODE: { key: label } } */
    @GetMapping("/map")
    public R<Map<String, Map<String, String>>> map() {
        List<SysDict> list = dictMapper.selectList(new LambdaQueryWrapper<SysDict>()
                .eq(SysDict::getEnabled, 1)
                .eq(SysDict::getIsDeleted, 0)
                .orderByAsc(SysDict::getDictType)
                .orderByAsc(SysDict::getSortNo)
                .orderByAsc(SysDict::getId));
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (SysDict d : list) {
            result.computeIfAbsent(d.getDictType(), k -> new LinkedHashMap<>())
                    .put(d.getDictKey(), d.getDictLabel());
        }
        return R.ok(result);
    }

    /** 兼容旧路径：按 dictType 列明细 */
    @GetMapping
    public R<List<SysDict>> list(@RequestParam(required = false) String dictType,
                                 @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<SysDict> qw = new LambdaQueryWrapper<SysDict>()
                .eq(SysDict::getIsDeleted, 0)
                .eq(dictType != null && !dictType.isBlank(), SysDict::getDictType, dictType)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(SysDict::getDictKey, keyword)
                        .or().like(SysDict::getDictLabel, keyword)
                        .or().like(SysDict::getDictType, keyword))
                .orderByAsc(SysDict::getDictType)
                .orderByAsc(SysDict::getSortNo);
        return R.ok(dictMapper.selectList(qw));
    }

    @RequirePerm("sys:dict:create")
    @PostMapping("/types")
    @Transactional(rollbackFor = Exception.class)
    public R<SysDictType> createType(@RequestBody SaveTypeReq req) {
        if (blank(req.getTypeCode()) || blank(req.getTypeName())) {
            throw new BizException("类型编码/中文名不能为空");
        }
        Long exists = typeMapper.selectCount(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getIsDeleted, 0)
                .eq(SysDictType::getTypeCode, req.getTypeCode().trim()));
        if (exists != null && exists > 0) {
            throw new BizException("类型编码已存在: " + req.getTypeCode());
        }
        SysDictType t = new SysDictType();
        applyType(t, req);
        typeMapper.insert(t);
        return R.ok(t);
    }

    @RequirePerm("sys:dict:update")
    @PutMapping("/types/{id}")
    @Transactional(rollbackFor = Exception.class)
    public R<SysDictType> updateType(@PathVariable Long id, @RequestBody SaveTypeReq req) {
        SysDictType t = typeMapper.selectById(id);
        if (t == null) {
            throw new BizException("类型不存在");
        }
        req.setId(id);
        applyType(t, req);
        typeMapper.updateById(t);
        return R.ok(t);
    }

    @RequirePerm("sys:dict:delete")
    @DeleteMapping("/types/{id}")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> deleteType(@PathVariable Long id) {
        SysDictType t = typeMapper.selectById(id);
        if (t == null) {
            throw new BizException("类型不存在");
        }
        t.setIsDeleted(1);
        typeMapper.updateById(t);
        return R.ok();
    }

    @RequirePerm("sys:dict:create")
    @PostMapping("/items")
    @Transactional(rollbackFor = Exception.class)
    public R<SysDict> createItem(@RequestBody SaveItemReq req) {
        validateItem(req, null);
        SysDict d = new SysDict();
        applyItem(d, req);
        dictMapper.insert(d);
        return R.ok(d);
    }

    @RequirePerm("sys:dict:update")
    @PutMapping("/items/{id}")
    @Transactional(rollbackFor = Exception.class)
    public R<SysDict> updateItem(@PathVariable Long id, @RequestBody SaveItemReq req) {
        SysDict d = dictMapper.selectById(id);
        if (d == null) {
            throw new BizException("字典项不存在");
        }
        req.setId(id);
        validateItem(req, id);
        applyItem(d, req);
        dictMapper.updateById(d);
        return R.ok(d);
    }

    @RequirePerm("sys:dict:delete")
    @DeleteMapping("/items/{id}")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> deleteItem(@PathVariable Long id) {
        SysDict d = dictMapper.selectById(id);
        if (d == null) {
            throw new BizException("字典项不存在");
        }
        d.setIsDeleted(1);
        dictMapper.updateById(d);
        return R.ok();
    }

    // 兼容：原 POST/PUT/DELETE /dicts 当作 item
    @RequirePerm("sys:dict:create")
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public R<SysDict> create(@RequestBody SaveItemReq req) {
        return createItem(req);
    }

    @RequirePerm("sys:dict:update")
    @PutMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public R<SysDict> update(@PathVariable Long id, @RequestBody SaveItemReq req) {
        return updateItem(id, req);
    }

    @RequirePerm("sys:dict:delete")
    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> remove(@PathVariable Long id) {
        return deleteItem(id);
    }

    private void applyType(SysDictType t, SaveTypeReq req) {
        t.setTypeCode(req.getTypeCode().trim());
        t.setTypeName(req.getTypeName().trim());
        t.setTypeGroup(req.getTypeGroup() == null || req.getTypeGroup().isBlank()
                ? "BUSINESS" : req.getTypeGroup().trim());
        t.setUsageRemark(req.getUsageRemark());
        t.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
        t.setEnabled(req.getEnabled() == null ? 1 : req.getEnabled());
        if (t.getIsDeleted() == null) {
            t.setIsDeleted(0);
        }
    }

    private void validateItem(SaveItemReq req, Long excludeId) {
        if (blank(req.getDictType()) || blank(req.getDictKey()) || blank(req.getDictLabel())) {
            throw new BizException("类型/编码/中文显示 均必填");
        }
        Long exists = dictMapper.selectCount(new LambdaQueryWrapper<SysDict>()
                .eq(SysDict::getIsDeleted, 0)
                .eq(SysDict::getDictType, req.getDictType().trim())
                .eq(SysDict::getDictKey, req.getDictKey().trim())
                .ne(excludeId != null, SysDict::getId, excludeId));
        if (exists != null && exists > 0) {
            throw new BizException("该类型下编码已存在: " + req.getDictKey());
        }
    }

    private void applyItem(SysDict d, SaveItemReq req) {
        d.setDictType(req.getDictType().trim());
        d.setDictKey(req.getDictKey().trim());
        d.setDictLabel(req.getDictLabel().trim());
        d.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
        d.setEnabled(req.getEnabled() == null ? 1 : req.getEnabled());
        d.setRemark(req.getRemark());
        if (d.getIsDeleted() == null) {
            d.setIsDeleted(0);
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
