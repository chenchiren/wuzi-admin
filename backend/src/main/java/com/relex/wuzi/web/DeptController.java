package com.relex.wuzi.web;

import com.relex.wuzi.annotation.RequirePerm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.SysDept;
import com.relex.wuzi.mapper.SysDeptMapper;
import com.relex.wuzi.service.OperationLogService;
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
@RequestMapping("/api/depts")
@RequiredArgsConstructor
public class DeptController {

    private final SysDeptMapper deptMapper;
    private final OperationLogService operationLogService;

    @GetMapping
    public R<List<SysDept>> list() {
        return R.ok(deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                .orderByAsc(SysDept::getSortNo)
                .orderByAsc(SysDept::getId)));
    }

    @GetMapping("/tree")
    public R<List<Map<String, Object>>> tree() {
        List<SysDept> all = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                .orderByAsc(SysDept::getSortNo)
                .orderByAsc(SysDept::getId));
        return R.ok(buildTree(all));
    }

    @Data
    public static class SaveDeptReq {
        private Long id;
        private Long parentId = 0L;
        private String deptCode;
        private String deptName;
        private String deptType;
        private Integer sortNo;
        private Integer status;
    }

    @RequirePerm("sys:dept:create")
    @PostMapping
    public R<SysDept> create(@RequestBody SaveDeptReq req,
                             @RequestAttribute(value = "userId", required = false) Long opId,
                             @RequestAttribute(value = "username", required = false) String opName,
                             HttpServletRequest request) {
        SysDept dept = new SysDept();
        dept.setParentId(req.getParentId() == null ? 0L : req.getParentId());
        dept.setDeptCode(req.getDeptCode());
        dept.setDeptName(req.getDeptName());
        dept.setDeptType(req.getDeptType() == null || req.getDeptType().isBlank()
                ? (req.getParentId() == null || req.getParentId() <= 0 ? "COMPANY" : "DEPT")
                : req.getDeptType().trim().toUpperCase());
        dept.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
        dept.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        dept.setIsDeleted(0);
        dept.setCreatedAt(LocalDateTime.now());
        deptMapper.insert(dept);
        operationLogService.record(request, opId, opName, "DEPT", "CREATE",
                "deptCode=" + dept.getDeptCode(), "SUCCESS");
        return R.ok(dept);
    }

    @RequirePerm("sys:dept:update")
    @PutMapping("/{id}")
    public R<SysDept> update(@PathVariable Long id, @RequestBody SaveDeptReq req,
                             @RequestAttribute(value = "userId", required = false) Long opId,
                             @RequestAttribute(value = "username", required = false) String opName,
                             HttpServletRequest request) {
        SysDept dept = deptMapper.selectById(id);
        if (dept == null) {
            return R.fail("部门不存在");
        }
        if (req.getParentId() != null) {
            dept.setParentId(req.getParentId());
        }
        if (req.getDeptCode() != null && !req.getDeptCode().isBlank()) {
            dept.setDeptCode(req.getDeptCode());
        }
        if (req.getDeptName() != null && !req.getDeptName().isBlank()) {
            dept.setDeptName(req.getDeptName());
        }
        if (req.getDeptType() != null && !req.getDeptType().isBlank()) {
            dept.setDeptType(req.getDeptType().trim().toUpperCase());
        }
        if (req.getSortNo() != null) {
            dept.setSortNo(req.getSortNo());
        }
        if (req.getStatus() != null) {
            dept.setStatus(req.getStatus());
        }
        dept.setUpdatedAt(LocalDateTime.now());
        deptMapper.updateById(dept);
        operationLogService.record(request, opId, opName, "DEPT", "UPDATE",
                "deptId=" + id, "SUCCESS");
        return R.ok(dept);
    }

    private List<Map<String, Object>> buildTree(List<SysDept> all) {
        Map<Long, Map<String, Object>> nodes = new HashMap<>();
        for (SysDept d : all) {
            Map<String, Object> n = new HashMap<>();
            n.put("id", d.getId());
            n.put("parentId", d.getParentId() == null ? 0L : d.getParentId());
            n.put("deptCode", d.getDeptCode());
            n.put("deptName", d.getDeptName());
            n.put("deptType", d.getDeptType());
            n.put("sortNo", d.getSortNo());
            n.put("status", d.getStatus());
            n.put("children", new ArrayList<Map<String, Object>>());
            nodes.put(d.getId(), n);
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
