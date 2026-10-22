package com.relex.wuzi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.domain.SysDept;
import com.relex.wuzi.domain.SysPosition;
import com.relex.wuzi.domain.SysUser;
import com.relex.wuzi.mapper.SysDeptMapper;
import com.relex.wuzi.mapper.SysPositionMapper;
import com.relex.wuzi.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 组织树（公司 → 部门 → 小组）+ 职位 解析审批人。
 *
 * <ul>
 *   <li>TEAM_LEADER：申请人所在小组（或最细节点）的组长</li>
 *   <li>DEPT_LEADER：向上跳过小组，找 DEPT/COMPANY 节点上的最高职级领导（正部长）</li>
 *   <li>POSITION + 范围：SELF 同节点 / DEPT 本部门含兄弟组 / PARENT 上级 / ALL 不限</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class OrgPositionService {

    public static final String TYPE_USER = "USER";
    public static final String TYPE_ROLE = "ROLE";
    public static final String TYPE_POSITION = "POSITION";
    public static final String TYPE_DEPT_LEADER = "DEPT_LEADER";
    public static final String TYPE_TEAM_LEADER = "TEAM_LEADER";

    public static final String SCOPE_ALL = "ALL";
    public static final String SCOPE_SELF = "SELF";
    public static final String SCOPE_DEPT = "DEPT";
    public static final String SCOPE_PARENT = "PARENT";

    public static final String NODE_COMPANY = "COMPANY";
    public static final String NODE_DEPT = "DEPT";
    public static final String NODE_GROUP = "GROUP";

    private final SysPositionMapper positionMapper;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;

    // ---------- 对外解析 ----------

    /** 按职位编码解析；scope 决定组织范围 */
    public List<Long> usersByPosition(String positionCode, Long applicantDeptId, String deptScope) {
        if (positionCode == null || positionCode.isBlank()) {
            return List.of();
        }
        SysPosition pos = positionMapper.selectOne(new LambdaQueryWrapper<SysPosition>()
                .eq(SysPosition::getPositionCode, positionCode.trim())
                .last("limit 1"));
        if (pos == null) {
            return List.of();
        }
        Set<Long> allowed = resolveScopeNodes(applicantDeptId, deptScope);
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getPositionId, pos.getId())
                        .eq(SysUser::getStatus, 1))
                .stream()
                .filter(u -> allowed == null || (u.getDeptId() != null && allowed.contains(u.getDeptId())))
                .map(SysUser::getId)
                .distinct()
                .toList();
    }

    /**
     * 部门负责人（正职）：向上跳过小组，取 DEPT/COMPANY 节点上职级最高的领导岗。
     * 部门节点无领导时再向上找；副部长用 POSITION+DEPT_VICE 单独配节点。
     */
    public List<Long> deptLeaders(Long applicantDeptId, String deptScope) {
        String scope = scopeOf(deptScope);
        Long start;
        if (SCOPE_PARENT.equals(scope)) {
            start = parentOf(resolveDeptNodeId(applicantDeptId));
        } else {
            start = resolveDeptNodeId(applicantDeptId);
        }
        if (start == null) {
            return List.of();
        }
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        Long cur = start;
        int guard = 0;
        while (cur != null && cur > 0 && guard++ < 12) {
            // 只认挂在该组织节点上的领导，避免小组长冒充部长
            List<Long> leaders = leadersOfNode(cur);
            if (!leaders.isEmpty()) {
                result.addAll(leaders);
                break;
            }
            SysDept dept = deptMapper.selectById(cur);
            if (dept == null || dept.getParentId() == null || dept.getParentId() <= 0
                    || Objects.equals(dept.getParentId(), dept.getId())) {
                break;
            }
            cur = dept.getParentId();
        }
        return new ArrayList<>(result);
    }

    /**
     * 小组长：申请人所在最细节点（小组）的领导；无则找最近 GROUP 祖先；
     * 仍无则退回部门节点领导（兼容尚未建小组的结构）。
     */
    public List<Long> teamLeaders(Long applicantDeptId) {
        if (applicantDeptId == null) {
            return List.of();
        }
        // 1) 本节点（组员挂在小组下）
        List<Long> here = leadersOfNode(applicantDeptId);
        if (!here.isEmpty()) {
            return here;
        }
        // 2) 最近 GROUP 祖先
        Long group = findNearestGroup(applicantDeptId);
        if (group != null && !Objects.equals(group, applicantDeptId)) {
            List<Long> g = leadersOfNode(group);
            if (!g.isEmpty()) {
                return g;
            }
        }
        // 3) 兜底：部门节点领导
        Long dept = resolveDeptNodeId(applicantDeptId);
        return dept == null ? List.of() : leadersOfNode(dept);
    }

    /** 某组织节点内职级最高的领导岗用户（同职级多人则全部） */
    public List<Long> leadersOfNode(Long nodeId) {
        if (nodeId == null) {
            return List.of();
        }
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeptId, nodeId)
                .eq(SysUser::getStatus, 1)
                .isNotNull(SysUser::getPositionId));
        if (users.isEmpty()) {
            return List.of();
        }
        List<Long> posIds = users.stream().map(SysUser::getPositionId).distinct().toList();
        Map<Long, SysPosition> posMap = positionMapper.selectBatchIds(posIds).stream()
                .collect(Collectors.toMap(SysPosition::getId, p -> p));
        int maxLevel = Integer.MIN_VALUE;
        List<Long> best = new ArrayList<>();
        for (SysUser u : users) {
            SysPosition p = posMap.get(u.getPositionId());
            if (p == null || p.getIsLeader() == null || p.getIsLeader() != 1) {
                continue;
            }
            int level = p.getPositionLevel() == null ? 0 : p.getPositionLevel();
            if (level > maxLevel) {
                maxLevel = level;
                best = new ArrayList<>(List.of(u.getId()));
            } else if (level == maxLevel) {
                best.add(u.getId());
            }
        }
        return best;
    }

    public boolean userHoldsPosition(Long userId, String positionCode, Long applicantDeptId, String deptScope) {
        return usersByPosition(positionCode, applicantDeptId, deptScope).contains(userId);
    }

    public boolean userIsDeptLeader(Long userId, Long applicantDeptId, String deptScope) {
        return deptLeaders(applicantDeptId, deptScope).contains(userId);
    }

    public boolean userIsTeamLeader(Long userId, Long applicantDeptId) {
        return teamLeaders(applicantDeptId).contains(userId);
    }

    // ---------- 组织树 ----------

    /**
     * 所属部门节点：向上跳过 GROUP，取最近 DEPT；无则取 COMPANY；再无返回原节点。
     * 用户应挂在最细节点（小组），部长/副部长挂在部门节点。
     */
    public Long resolveDeptNodeId(Long leafDeptId) {
        if (leafDeptId == null) {
            return null;
        }
        Long cur = leafDeptId;
        Long fallback = leafDeptId;
        int guard = 0;
        while (cur != null && cur > 0 && guard++ < 12) {
            SysDept d = deptMapper.selectById(cur);
            if (d == null) {
                break;
            }
            fallback = d.getId();
            String t = nodeType(d);
            if (NODE_DEPT.equals(t) || NODE_COMPANY.equals(t)) {
                return d.getId();
            }
            Long parentId = d.getParentId();
            if (parentId == null || parentId <= 0 || Objects.equals(parentId, d.getId())) {
                break;
            }
            cur = parentId;
        }
        return fallback;
    }

    /** 最近的 GROUP 祖先（含自身）；无小组结构返回 null */
    public Long findNearestGroup(Long leafDeptId) {
        Long cur = leafDeptId;
        int guard = 0;
        while (cur != null && cur > 0 && guard++ < 12) {
            SysDept d = deptMapper.selectById(cur);
            if (d == null) {
                return null;
            }
            if (NODE_GROUP.equals(nodeType(d))) {
                return d.getId();
            }
            // 显式标成 DEPT/COMPANY 的不再当小组
            if (NODE_DEPT.equals(nodeType(d)) || NODE_COMPANY.equals(nodeType(d))) {
                return null;
            }
            Long parentId = d.getParentId();
            if (parentId == null || parentId <= 0 || Objects.equals(parentId, d.getId())) {
                return null;
            }
            cur = parentId;
        }
        return null;
    }

    /** 节点 + 全部子孙（本部门含兄弟组） */
    public Set<Long> subtreeOf(Long rootId) {
        if (rootId == null) {
            return Set.of();
        }
        List<SysDept> all = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                .select(SysDept::getId, SysDept::getParentId));
        Map<Long, List<Long>> children = new HashMap<>();
        for (SysDept d : all) {
            Long p = d.getParentId() == null ? 0L : d.getParentId();
            children.computeIfAbsent(p, k -> new ArrayList<>()).add(d.getId());
        }
        LinkedHashSet<Long> out = new LinkedHashSet<>();
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(rootId);
        int guard = 0;
        while (!stack.isEmpty() && guard++ < 5000) {
            Long id = stack.pop();
            if (id == null || !out.add(id)) {
                continue;
            }
            for (Long c : children.getOrDefault(id, List.of())) {
                stack.push(c);
            }
        }
        return out;
    }

    /** 范围内节点集合；null=不限 */
    public Set<Long> resolveScopeNodes(Long applicantDeptId, String deptScope) {
        String scope = scopeOf(deptScope);
        if (SCOPE_ALL.equals(scope)) {
            return null;
        }
        if (SCOPE_PARENT.equals(scope)) {
            Long parent = parentOf(applicantDeptId);
            return parent == null ? Set.of() : Set.of(parent);
        }
        if (SCOPE_DEPT.equals(scope)) {
            Long dept = resolveDeptNodeId(applicantDeptId);
            return dept == null ? Set.of() : subtreeOf(dept);
        }
        // SELF：同一组织节点
        return applicantDeptId == null ? Set.of() : Set.of(applicantDeptId);
    }

    private Long parentOf(Long deptId) {
        if (deptId == null) {
            return null;
        }
        SysDept dept = deptMapper.selectById(deptId);
        if (dept == null || dept.getParentId() == null || dept.getParentId() <= 0
                || Objects.equals(dept.getParentId(), dept.getId())) {
            return null;
        }
        return dept.getParentId();
    }

    private static String nodeType(SysDept d) {
        String t = d.getDeptType();
        if (t != null && !t.isBlank()) {
            return t.trim().toUpperCase(Locale.ROOT);
        }
        // 兼容旧数据：根节点视为公司，其余视为部门
        if (d.getParentId() == null || d.getParentId() <= 0) {
            return NODE_COMPANY;
        }
        return NODE_DEPT;
    }

    private static String scopeOf(String deptScope) {
        if (deptScope == null || deptScope.isBlank()) {
            return SCOPE_ALL;
        }
        String s = deptScope.trim().toUpperCase(Locale.ROOT);
        // 兼容：旧 SELF 在小组结构下按「本部门含兄弟组」理解更合理时用 DEPT 显式配置
        return s;
    }
}
