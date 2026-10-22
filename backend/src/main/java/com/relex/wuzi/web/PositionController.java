package com.relex.wuzi.web;

import com.relex.wuzi.annotation.RequirePerm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.SysPosition;
import com.relex.wuzi.domain.SysUser;
import com.relex.wuzi.mapper.SysPositionMapper;
import com.relex.wuzi.mapper.SysUserMapper;
import com.relex.wuzi.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 职位管理：组织职务字典（部长/副部长/主管等），供用户挂载与审批流节点引用。
 */
@RestController
@RequestMapping("/api/positions")
@RequiredArgsConstructor
public class PositionController {

    private final SysPositionMapper positionMapper;
    private final SysUserMapper userMapper;
    private final OperationLogService operationLogService;

    @GetMapping
    public R<List<SysPosition>> list() {
        return R.ok(positionMapper.selectList(new LambdaQueryWrapper<SysPosition>()
                .orderByDesc(SysPosition::getPositionLevel)
                .orderByAsc(SysPosition::getSortNo)
                .orderByAsc(SysPosition::getId)));
    }

    @GetMapping("/all")
    public R<List<SysPosition>> all() {
        return list();
    }

    @Data
    public static class SavePositionReq {
        private String positionCode;
        private String positionName;
        private Integer positionLevel;
        private Integer isLeader;
        private Integer sortNo;
        private Integer status;
        private String remark;
    }

    @RequirePerm("sys:position:create")
    @PostMapping
    public R<SysPosition> create(@RequestBody SavePositionReq req,
                                 @RequestAttribute(value = "userId", required = false) Long opId,
                                 @RequestAttribute(value = "username", required = false) String opName,
                                 HttpServletRequest request) {
        if (req.getPositionCode() == null || req.getPositionCode().isBlank()) {
            throw new BizException("职位编码不能为空");
        }
        if (req.getPositionName() == null || req.getPositionName().isBlank()) {
            throw new BizException("职位名称不能为空");
        }
        Long exists = positionMapper.selectCount(new LambdaQueryWrapper<SysPosition>()
                .eq(SysPosition::getPositionCode, req.getPositionCode().trim()));
        if (exists != null && exists > 0) {
            throw new BizException("职位编码已存在");
        }
        SysPosition p = new SysPosition();
        p.setPositionCode(req.getPositionCode().trim());
        p.setPositionName(req.getPositionName().trim());
        p.setPositionLevel(req.getPositionLevel() == null ? 0 : req.getPositionLevel());
        p.setIsLeader(req.getIsLeader() == null ? 0 : req.getIsLeader());
        p.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
        p.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        p.setRemark(req.getRemark());
        p.setIsDeleted(0);
        p.setCreatedAt(LocalDateTime.now());
        positionMapper.insert(p);
        operationLogService.record(request, opId, opName, "POSITION", "CREATE",
                "positionCode=" + p.getPositionCode(), "SUCCESS");
        return R.ok(p);
    }

    @RequirePerm("sys:position:update")
    @PutMapping("/{id}")
    public R<SysPosition> update(@PathVariable Long id, @RequestBody SavePositionReq req,
                                 @RequestAttribute(value = "userId", required = false) Long opId,
                                 @RequestAttribute(value = "username", required = false) String opName,
                                 HttpServletRequest request) {
        SysPosition p = positionMapper.selectById(id);
        if (p == null) {
            return R.fail("职位不存在");
        }
        if (req.getPositionCode() != null && !req.getPositionCode().isBlank()) {
            Long exists = positionMapper.selectCount(new LambdaQueryWrapper<SysPosition>()
                    .eq(SysPosition::getPositionCode, req.getPositionCode().trim())
                    .ne(SysPosition::getId, id));
            if (exists != null && exists > 0) {
                throw new BizException("职位编码已存在");
            }
            p.setPositionCode(req.getPositionCode().trim());
        }
        if (req.getPositionName() != null && !req.getPositionName().isBlank()) {
            p.setPositionName(req.getPositionName().trim());
        }
        if (req.getPositionLevel() != null) {
            p.setPositionLevel(req.getPositionLevel());
        }
        if (req.getIsLeader() != null) {
            p.setIsLeader(req.getIsLeader());
        }
        if (req.getSortNo() != null) {
            p.setSortNo(req.getSortNo());
        }
        if (req.getStatus() != null) {
            p.setStatus(req.getStatus());
        }
        if (req.getRemark() != null) {
            p.setRemark(req.getRemark());
        }
        p.setUpdatedAt(LocalDateTime.now());
        positionMapper.updateById(p);
        operationLogService.record(request, opId, opName, "POSITION", "UPDATE",
                "positionId=" + id, "SUCCESS");
        return R.ok(p);
    }

    @RequirePerm("sys:position:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id,
                          @RequestAttribute(value = "userId", required = false) Long opId,
                          @RequestAttribute(value = "username", required = false) String opName,
                          HttpServletRequest request) {
        SysPosition p = positionMapper.selectById(id);
        if (p == null) {
            return R.fail("职位不存在");
        }
        Long used = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPositionId, id));
        if (used != null && used > 0) {
            throw new BizException("该职位已被 " + used + " 名用户占用，请先调整用户职位");
        }
        positionMapper.deleteById(id);
        operationLogService.record(request, opId, opName, "POSITION", "DELETE",
                "positionId=" + id, "SUCCESS");
        return R.ok();
    }
}
