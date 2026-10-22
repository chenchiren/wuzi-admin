package com.relex.wuzi.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.annotation.RequirePerm;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.Project;
import com.relex.wuzi.mapper.ProjectMapper;
import com.relex.wuzi.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目 / 成本中心主数据：project_type 区分 PROJECT 与 COST_CENTER。
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectMapper projectMapper;
    private final OperationLogService operationLogService;

    @GetMapping
    public R<List<Project>> list(@RequestParam(required = false) String keyword,
                                 @RequestParam(required = false) String projectType,
                                 @RequestParam(required = false) Integer status) {
        return R.ok(projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(Project::getProjectCode, keyword)
                        .or().like(Project::getProjectName, keyword))
                .eq(projectType != null && !projectType.isBlank(), Project::getProjectType, projectType)
                .eq(status != null, Project::getStatus, status)
                .orderByAsc(Project::getProjectCode)
                .orderByAsc(Project::getId)));
    }

    @GetMapping("/all")
    public R<List<Project>> all() {
        return R.ok(projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .orderByAsc(Project::getProjectCode)
                .orderByAsc(Project::getId)));
    }

    @Data
    public static class SaveProjectReq {
        private String projectCode;
        private String projectName;
        private String projectType;
        private Long deptId;
        private String managerName;
        private LocalDate startDate;
        private LocalDate endDate;
        private Integer status;
        private String remark;
    }

    @PostMapping
    @RequirePerm("base:project:create")
    public R<Project> create(@RequestBody SaveProjectReq req,
                             @RequestAttribute(value = "userId", required = false) Long opId,
                             @RequestAttribute(value = "username", required = false) String opName,
                             HttpServletRequest request) {
        if (req.getProjectCode() == null || req.getProjectCode().isBlank()) {
            throw new BizException("项目编码不能为空");
        }
        if (req.getProjectName() == null || req.getProjectName().isBlank()) {
            throw new BizException("项目名称不能为空");
        }
        String code = req.getProjectCode().trim();
        Long exists = projectMapper.selectCount(new LambdaQueryWrapper<Project>()
                .eq(Project::getProjectCode, code));
        if (exists != null && exists > 0) {
            throw new BizException("项目编码已存在");
        }
        String type = req.getProjectType() == null || req.getProjectType().isBlank()
                ? "PROJECT" : req.getProjectType().trim();
        if (!"PROJECT".equals(type) && !"COST_CENTER".equals(type)) {
            throw new BizException("项目类型仅支持 PROJECT / COST_CENTER");
        }
        Project p = new Project();
        p.setProjectCode(code);
        p.setProjectName(req.getProjectName().trim());
        p.setProjectType(type);
        p.setDeptId(req.getDeptId());
        p.setManagerName(req.getManagerName());
        p.setStartDate(req.getStartDate());
        p.setEndDate(req.getEndDate());
        p.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        p.setRemark(req.getRemark());
        p.setIsDeleted(0);
        p.setCreatedAt(LocalDateTime.now());
        projectMapper.insert(p);
        operationLogService.record(request, opId, opName, "PROJECT", "CREATE",
                "projectCode=" + p.getProjectCode(), "SUCCESS");
        return R.ok(p);
    }

    @PutMapping("/{id}")
    @RequirePerm("base:project:update")
    public R<Project> update(@PathVariable Long id, @RequestBody SaveProjectReq req,
                             @RequestAttribute(value = "userId", required = false) Long opId,
                             @RequestAttribute(value = "username", required = false) String opName,
                             HttpServletRequest request) {
        Project p = projectMapper.selectById(id);
        if (p == null) {
            return R.fail("项目不存在");
        }
        if (req.getProjectCode() != null && !req.getProjectCode().isBlank()) {
            String code = req.getProjectCode().trim();
            Long exists = projectMapper.selectCount(new LambdaQueryWrapper<Project>()
                    .eq(Project::getProjectCode, code)
                    .ne(Project::getId, id));
            if (exists != null && exists > 0) {
                throw new BizException("项目编码已存在");
            }
            p.setProjectCode(code);
        }
        if (req.getProjectName() != null && !req.getProjectName().isBlank()) {
            p.setProjectName(req.getProjectName().trim());
        }
        if (req.getProjectType() != null && !req.getProjectType().isBlank()) {
            String type = req.getProjectType().trim();
            if (!"PROJECT".equals(type) && !"COST_CENTER".equals(type)) {
                throw new BizException("项目类型仅支持 PROJECT / COST_CENTER");
            }
            p.setProjectType(type);
        }
        if (req.getDeptId() != null) {
            p.setDeptId(req.getDeptId());
        }
        if (req.getManagerName() != null) {
            p.setManagerName(req.getManagerName());
        }
        if (req.getStartDate() != null) {
            p.setStartDate(req.getStartDate());
        }
        if (req.getEndDate() != null) {
            p.setEndDate(req.getEndDate());
        }
        if (req.getStatus() != null) {
            p.setStatus(req.getStatus());
        }
        if (req.getRemark() != null) {
            p.setRemark(req.getRemark());
        }
        p.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(p);
        operationLogService.record(request, opId, opName, "PROJECT", "UPDATE",
                "projectId=" + id, "SUCCESS");
        return R.ok(p);
    }

    @DeleteMapping("/{id}")
    @RequirePerm("base:project:delete")
    public R<Void> delete(@PathVariable Long id,
                          @RequestAttribute(value = "userId", required = false) Long opId,
                          @RequestAttribute(value = "username", required = false) String opName,
                          HttpServletRequest request) {
        Project p = projectMapper.selectById(id);
        if (p == null) {
            return R.fail("项目不存在");
        }
        projectMapper.deleteById(id);
        operationLogService.record(request, opId, opName, "PROJECT", "DELETE",
                "projectId=" + id, "SUCCESS");
        return R.ok();
    }
}
