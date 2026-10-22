package com.relex.wuzi.web;

import com.relex.wuzi.annotation.RequirePerm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.Material;
import com.relex.wuzi.domain.MaterialCategory;
import com.relex.wuzi.mapper.MaterialCategoryMapper;
import com.relex.wuzi.mapper.MaterialMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 物资分类树 CRUD
 */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final MaterialCategoryMapper categoryMapper;
    private final MaterialMapper materialMapper;

    @GetMapping
    public R<List<MaterialCategory>> list(@RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<MaterialCategory> qw = new LambdaQueryWrapper<MaterialCategory>()
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(MaterialCategory::getCategoryName, keyword)
                        .or().like(MaterialCategory::getCategoryCode, keyword))
                .orderByAsc(MaterialCategory::getSortNo)
                .orderByAsc(MaterialCategory::getId);
        return R.ok(categoryMapper.selectList(qw));
    }

    @GetMapping("/tree")
    public R<List<MaterialCategory>> tree() {
        List<MaterialCategory> all = categoryMapper.selectList(new LambdaQueryWrapper<MaterialCategory>()
                .orderByAsc(MaterialCategory::getSortNo)
                .orderByAsc(MaterialCategory::getId));
        return R.ok(buildTree(all));
    }

    @GetMapping("/{id}")
    public R<MaterialCategory> detail(@PathVariable Long id) {
        MaterialCategory c = categoryMapper.selectById(id);
        if (c == null) {
            throw new BizException("分类不存在");
        }
        return R.ok(c);
    }

    @RequirePerm("base:category:create")
    @PostMapping
    public R<MaterialCategory> create(@RequestBody MaterialCategory c) {
        if (c.getCategoryName() == null || c.getCategoryName().isBlank()) {
            throw new BizException("分类名称不能为空");
        }
        if (c.getCategoryCode() == null || c.getCategoryCode().isBlank()) {
            throw new BizException("分类编码不能为空");
        }
        Long count = categoryMapper.selectCount(new LambdaQueryWrapper<MaterialCategory>()
                .eq(MaterialCategory::getCategoryCode, c.getCategoryCode()));
        if (count != null && count > 0) {
            throw new BizException("分类编码已存在");
        }
        if (c.getParentId() == null) {
            c.setParentId(0L);
        }
        if (c.getSortNo() == null) {
            c.setSortNo(0);
        }
        if (c.getStatus() == null) {
            c.setStatus(1);
        }
        if (c.getIsDeleted() == null) {
            c.setIsDeleted(0);
        }
        categoryMapper.insert(c);
        return R.ok(c);
    }

    @RequirePerm("base:category:update")
    @PutMapping("/{id}")
    public R<MaterialCategory> update(@PathVariable Long id, @RequestBody MaterialCategory req) {
        MaterialCategory c = categoryMapper.selectById(id);
        if (c == null) {
            throw new BizException("分类不存在");
        }
        if (req.getCategoryCode() != null && !req.getCategoryCode().equals(c.getCategoryCode())) {
            Long count = categoryMapper.selectCount(new LambdaQueryWrapper<MaterialCategory>()
                    .eq(MaterialCategory::getCategoryCode, req.getCategoryCode())
                    .ne(MaterialCategory::getId, id));
            if (count != null && count > 0) {
                throw new BizException("分类编码已存在");
            }
            c.setCategoryCode(req.getCategoryCode());
        }
        if (req.getCategoryName() != null) {
            c.setCategoryName(req.getCategoryName());
        }
        if (req.getParentId() != null) {
            if (Objects.equals(req.getParentId(), id)) {
                throw new BizException("父分类不能是自身");
            }
            c.setParentId(req.getParentId());
        }
        if (req.getSortNo() != null) {
            c.setSortNo(req.getSortNo());
        }
        if (req.getStatus() != null) {
            c.setStatus(req.getStatus());
        }
        categoryMapper.updateById(c);
        return R.ok(c);
    }

    @RequirePerm("base:category:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        MaterialCategory c = categoryMapper.selectById(id);
        if (c == null) {
            throw new BizException("分类不存在");
        }
        Long childCount = categoryMapper.selectCount(new LambdaQueryWrapper<MaterialCategory>()
                .eq(MaterialCategory::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BizException("存在子分类，无法删除");
        }
        Long matCount = materialMapper.selectCount(new LambdaQueryWrapper<Material>()
                .eq(Material::getCategoryId, id));
        if (matCount != null && matCount > 0) {
            throw new BizException("分类下存在物资，无法删除");
        }
        categoryMapper.deleteById(id);
        return R.ok();
    }

    private List<MaterialCategory> buildTree(List<MaterialCategory> all) {
        Map<Long, MaterialCategory> map = new java.util.HashMap<>();
        for (MaterialCategory c : all) {
            c.setChildren(new ArrayList<>());
            map.put(c.getId(), c);
        }
        List<MaterialCategory> roots = new ArrayList<>();
        for (MaterialCategory c : all) {
            Long parentId = c.getParentId() == null ? 0L : c.getParentId();
            MaterialCategory parent = map.get(parentId);
            if (parent == null || Objects.equals(parentId, c.getId())) {
                roots.add(c);
            } else {
                parent.getChildren().add(c);
            }
        }
        Comparator<MaterialCategory> cmp = Comparator
                .comparing(MaterialCategory::getSortNo, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(MaterialCategory::getId, Comparator.nullsLast(Comparator.naturalOrder()));
        sortTree(roots, cmp);
        return roots;
    }

    private void sortTree(List<MaterialCategory> nodes, Comparator<MaterialCategory> cmp) {
        nodes.sort(cmp);
        for (MaterialCategory n : nodes) {
            if (n.getChildren() != null && !n.getChildren().isEmpty()) {
                sortTree(n.getChildren(), cmp);
            }
        }
    }
}
