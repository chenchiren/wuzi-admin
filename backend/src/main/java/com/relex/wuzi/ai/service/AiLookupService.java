package com.relex.wuzi.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.ai.embed.MaterialVectorIndex;
import com.relex.wuzi.ai.llm.LlmProperties;
import com.relex.wuzi.domain.Material;
import com.relex.wuzi.domain.MaterialAlias;
import com.relex.wuzi.domain.Supplier;
import com.relex.wuzi.domain.Warehouse;
import com.relex.wuzi.mapper.MaterialAliasMapper;
import com.relex.wuzi.mapper.MaterialMapper;
import com.relex.wuzi.mapper.SupplierMapper;
import com.relex.wuzi.mapper.WarehouseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 名称 → 主数据解析与反查（模糊命中、候选澄清），供各工具复用。
 * 物资解析链：① 别名词典等值 ② LIKE 包含+截尾 ③ 向量语义 Top-K 兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiLookupService {

    private final MaterialMapper materialMapper;
    private final WarehouseMapper warehouseMapper;
    private final SupplierMapper supplierMapper;
    private final MaterialAliasMapper materialAliasMapper;
    private final MaterialVectorIndex vectorIndex;
    private final LlmProperties props;

    public List<Material> findMaterials(String name) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        String kw = name.trim();
        // ① 别名词典优先（等值命中，受控词典无歧义："复印纸"→"A4打印纸"）
        List<Long> aliasIds = materialAliasMapper.selectList(new LambdaQueryWrapper<MaterialAlias>()
                        .eq(MaterialAlias::getAlias, kw))
                .stream().map(MaterialAlias::getMaterialId).toList();
        if (!aliasIds.isEmpty()) {
            return materialMapper.selectBatchIds(aliasIds);
        }
        // ② LIKE 包含 + 截尾降级
        List<Material> hit = findLike(materialMapper, Material::getMaterialName, kw);
        if (!hit.isEmpty()) {
            return hit;
        }
        // ③ 向量语义兜底（仅②未命中才触发；服务不可用/未构建时安静跳过）
        if (vectorIndex.ready()) {
            try {
                List<MaterialVectorIndex.Scored> semantic = vectorIndex.search(
                        kw, props.getEmbedding().getTopK(), props.getEmbedding().getThreshold());
                if (!semantic.isEmpty()) {
                    log.info("AI 语义召回: '{}' → {} 个候选", kw, semantic.size());
                    return semantic.stream().map(MaterialVectorIndex.Scored::material).toList();
                }
            } catch (Exception e) {
                log.warn("AI 语义召回跳过: {}", e.getMessage());
            }
        }
        return List.of();
    }

    public List<Warehouse> findWarehouses(String name) {
        return findLike(warehouseMapper, Warehouse::getWarehouseName, name);
    }

    public List<Supplier> findSuppliers(String name) {
        return findLike(supplierMapper, Supplier::getSupplierName, name);
    }

    /**
     * 渐进降级的模糊匹配：先整词包含（%A4纸%）；0 命中时逐字截短重试
     * （A4纸 → A4打 → A4），处理用户简称与档案全名不一致的常见场景。
     */
    private <T> List<T> findLike(com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper,
                                 com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> col,
                                 String name) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        String kw = name.trim();
        List<T> hit = mapper.selectList(new LambdaQueryWrapper<T>().like(col, kw).last("LIMIT 10"));
        for (int cut = 1; hit.isEmpty() && kw.length() - cut >= 2 && cut <= 3; cut++) {
            hit = mapper.selectList(new LambdaQueryWrapper<T>()
                    .like(col, kw.substring(0, kw.length() - cut)).last("LIMIT 10"));
        }
        return hit;
    }

    public Map<Long, String> materialNames(Collection<Long> ids) {
        return names(materialMapper, Material::getId, Material::getMaterialName, ids);
    }

    public Map<Long, String> warehouseNames(Collection<Long> ids) {
        return names(warehouseMapper, Warehouse::getId, Warehouse::getWarehouseName, ids);
    }

    private static <T> Map<Long, String> names(com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper,
                                               Function<T, Long> key, Function<T, String> val,
                                               Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return mapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(key, val, (a, b) -> a));
    }
}
