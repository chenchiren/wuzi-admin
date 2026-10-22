package com.relex.wuzi.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.annotation.RequirePerm;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.MaterialUnit;
import com.relex.wuzi.mapper.MaterialUnitMapper;
import com.relex.wuzi.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 计量单位字典：编码唯一，支持分类与相对基准单位换算系数。
 */
@RestController
@RequestMapping("/api/units")
@RequiredArgsConstructor
public class MaterialUnitController {

    private final MaterialUnitMapper unitMapper;
    private final OperationLogService operationLogService;

    @GetMapping
    public R<List<MaterialUnit>> list(@RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) String category) {
        return R.ok(unitMapper.selectList(new LambdaQueryWrapper<MaterialUnit>()
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(MaterialUnit::getUnitCode, keyword)
                        .or().like(MaterialUnit::getUnitName, keyword))
                .eq(category != null && !category.isBlank(), MaterialUnit::getCategory, category)
                .orderByAsc(MaterialUnit::getSortNo)
                .orderByAsc(MaterialUnit::getId)));
    }

    @GetMapping("/all")
    public R<List<MaterialUnit>> all() {
        return R.ok(unitMapper.selectList(new LambdaQueryWrapper<MaterialUnit>()
                .orderByAsc(MaterialUnit::getSortNo)
                .orderByAsc(MaterialUnit::getId)));
    }

    @Data
    public static class SaveUnitReq {
        private String unitCode;
        private String unitName;
        private String category;
        private BigDecimal convertFactor;
        private String baseUnitCode;
        private Integer sortNo;
        private Integer status;
        private String remark;
    }

    @PostMapping
    @RequirePerm("base:unit:create")
    public R<MaterialUnit> create(@RequestBody SaveUnitReq req,
                                  @RequestAttribute(value = "userId", required = false) Long opId,
                                  @RequestAttribute(value = "username", required = false) String opName,
                                  HttpServletRequest request) {
        if (req.getUnitCode() == null || req.getUnitCode().isBlank()) {
            throw new BizException("单位编码不能为空");
        }
        if (req.getUnitName() == null || req.getUnitName().isBlank()) {
            throw new BizException("单位名称不能为空");
        }
        String code = req.getUnitCode().trim();
        Long exists = unitMapper.selectCount(new LambdaQueryWrapper<MaterialUnit>()
                .eq(MaterialUnit::getUnitCode, code));
        if (exists != null && exists > 0) {
            throw new BizException("单位编码已存在");
        }
        MaterialUnit u = new MaterialUnit();
        u.setUnitCode(code);
        u.setUnitName(req.getUnitName().trim());
        u.setCategory(req.getCategory() == null || req.getCategory().isBlank() ? "COUNT" : req.getCategory());
        u.setConvertFactor(req.getConvertFactor() == null ? BigDecimal.ONE : req.getConvertFactor());
        u.setBaseUnitCode(req.getBaseUnitCode() == null || req.getBaseUnitCode().isBlank()
                ? code : req.getBaseUnitCode().trim());
        u.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
        u.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        u.setRemark(req.getRemark());
        u.setIsDeleted(0);
        u.setCreatedAt(LocalDateTime.now());
        unitMapper.insert(u);
        operationLogService.record(request, opId, opName, "MATERIAL_UNIT", "CREATE",
                "unitCode=" + u.getUnitCode(), "SUCCESS");
        return R.ok(u);
    }

    @PutMapping("/{id}")
    @RequirePerm("base:unit:update")
    public R<MaterialUnit> update(@PathVariable Long id, @RequestBody SaveUnitReq req,
                                  @RequestAttribute(value = "userId", required = false) Long opId,
                                  @RequestAttribute(value = "username", required = false) String opName,
                                  HttpServletRequest request) {
        MaterialUnit u = unitMapper.selectById(id);
        if (u == null) {
            return R.fail("计量单位不存在");
        }
        if (req.getUnitCode() != null && !req.getUnitCode().isBlank()) {
            String code = req.getUnitCode().trim();
            Long exists = unitMapper.selectCount(new LambdaQueryWrapper<MaterialUnit>()
                    .eq(MaterialUnit::getUnitCode, code)
                    .ne(MaterialUnit::getId, id));
            if (exists != null && exists > 0) {
                throw new BizException("单位编码已存在");
            }
            u.setUnitCode(code);
        }
        if (req.getUnitName() != null && !req.getUnitName().isBlank()) {
            u.setUnitName(req.getUnitName().trim());
        }
        if (req.getCategory() != null && !req.getCategory().isBlank()) {
            u.setCategory(req.getCategory());
        }
        if (req.getConvertFactor() != null) {
            if (req.getConvertFactor().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("换算系数必须大于 0");
            }
            u.setConvertFactor(req.getConvertFactor());
        }
        if (req.getBaseUnitCode() != null && !req.getBaseUnitCode().isBlank()) {
            u.setBaseUnitCode(req.getBaseUnitCode().trim());
        }
        if (req.getSortNo() != null) {
            u.setSortNo(req.getSortNo());
        }
        if (req.getStatus() != null) {
            u.setStatus(req.getStatus());
        }
        if (req.getRemark() != null) {
            u.setRemark(req.getRemark());
        }
        u.setUpdatedAt(LocalDateTime.now());
        unitMapper.updateById(u);
        operationLogService.record(request, opId, opName, "MATERIAL_UNIT", "UPDATE",
                "unitId=" + id, "SUCCESS");
        return R.ok(u);
    }

    @DeleteMapping("/{id}")
    @RequirePerm("base:unit:delete")
    public R<Void> delete(@PathVariable Long id,
                          @RequestAttribute(value = "userId", required = false) Long opId,
                          @RequestAttribute(value = "username", required = false) String opName,
                          HttpServletRequest request) {
        MaterialUnit u = unitMapper.selectById(id);
        if (u == null) {
            return R.fail("计量单位不存在");
        }
        unitMapper.deleteById(id);
        operationLogService.record(request, opId, opName, "MATERIAL_UNIT", "DELETE",
                "unitId=" + id, "SUCCESS");
        return R.ok();
    }
}
