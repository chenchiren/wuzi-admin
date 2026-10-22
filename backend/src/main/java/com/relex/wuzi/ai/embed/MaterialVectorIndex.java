package com.relex.wuzi.ai.embed;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.ai.llm.LlmProperties;
import com.relex.wuzi.ai.llm.LlmProperties.Embedding;
import com.relex.wuzi.domain.Material;
import com.relex.wuzi.mapper.MaterialMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 物资内存向量索引（快照式，无锁切换）。
 * <ul>
 *   <li>只做第三级兜底：别名/LIKE 都未命中才调用，命中路径零开销；</li>
 *   <li>暴力余弦扫描，万级物资毫秒级，不需要向量库；</li>
 *   <li>embedding 服务不可用时安静降级为空结果，业务零感知；</li>
 *   <li>快照原子切换：reload 期间查询继续用旧快照。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaterialVectorIndex {

    private final MaterialMapper materialMapper;
    private final EmbeddingClient embeddingClient;
    private final LlmProperties props;

    /** 一条命中结果 */
    public record Scored(Material material, double score) {
    }

    private record Snapshot(List<Material> materials, List<float[]> vectors) {
        static final Snapshot EMPTY = new Snapshot(List.of(), List.of());
    }

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.EMPTY);

    @PostConstruct
    void lazyInit() {
        if (!props.getEmbedding().isEnabled()) {
            return;
        }
        // 后台首次构建，不阻塞启动
        Thread t = new Thread(this::reloadSafely, "ai-vector-init");
        t.setDaemon(true);
        t.start();
    }

    /** 定时重载（物资新增/改名在本周期内生效）；embedding 关闭时不跑 */
    @Scheduled(fixedDelayString = "${wuzi.ai.embedding.reload-ms:1800000}", initialDelayString = "${wuzi.ai.embedding.reload-ms:1800000}")
    public void scheduledReload() {
        if (props.getEmbedding().isEnabled()) {
            reloadSafely();
        }
    }

    public synchronized void reloadSafely() {
        try {
            reload();
        } catch (Exception e) {
            log.warn("AI 向量索引构建失败（向量检索暂不可用，别名/LIKE 不受影响）: {}", e.getMessage());
        }
    }

    private void reload() {
        Embedding cfg = props.getEmbedding();
        List<Material> all = materialMapper.selectList(new LambdaQueryWrapper<Material>()
                .orderByAsc(Material::getId));
        if (all.isEmpty()) {
            snapshot.set(Snapshot.EMPTY);
            return;
        }
        List<String> texts = new ArrayList<>(all.size());
        for (Material m : all) {
            texts.add(buildText(m));
        }
        // 分批 embed（每批 64 条，防单请求 payload 过大）
        List<float[]> vectors = new ArrayList<>(all.size());
        for (int i = 0; i < texts.size(); i += 64) {
            vectors.addAll(embeddingClient.embedBatch(texts.subList(i, Math.min(texts.size(), i + 64))));
        }
        snapshot.set(new Snapshot(all, vectors));
        log.info("AI 向量索引构建完成: {} 条物资", all.size());
    }

    private String buildText(Material m) {
        StringBuilder sb = new StringBuilder(m.getMaterialName() == null ? "" : m.getMaterialName());
        if (m.getSpec() != null && !m.getSpec().isBlank()) {
            sb.append(' ').append(m.getSpec());
        }
        if (m.getBrand() != null && !m.getBrand().isBlank()) {
            sb.append(' ').append(m.getBrand());
        }
        return sb.toString();
    }

    public boolean ready() {
        return props.getEmbedding().isEnabled() && snapshot.get() != Snapshot.EMPTY;
    }

    /**
     * 语义检索：余弦 Top-K，低于阈值丢弃。
     * 候选是"语义推荐"，交给上层走候选澄清流程，不直接采信。
     */
    public List<Scored> search(String query, int topK, double threshold) {
        Snapshot snap = snapshot.get();
        if (snap == Snapshot.EMPTY || query == null || query.isBlank()) {
            return List.of();
        }
        float[] q = embeddingClient.embed(query);
        List<Scored> all = new ArrayList<>(snap.materials().size());
        List<float[]> vectors = snap.vectors();
        List<Material> materials = snap.materials();
        for (int i = 0; i < materials.size() && i < vectors.size(); i++) {
            double score = EmbeddingClient.cosine(q, vectors.get(i));
            if (score >= threshold) {
                all.add(new Scored(materials.get(i), score));
            }
        }
        all.sort(Comparator.comparingDouble(Scored::score).reversed());
        return all.subList(0, Math.min(topK, all.size()));
    }
}
