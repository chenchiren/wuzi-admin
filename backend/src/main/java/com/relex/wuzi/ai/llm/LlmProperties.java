package com.relex.wuzi.ai.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 智能助手配置（wuzi.ai.*，环境变量 WUZI_AI_*）。
 */
@Data
@ConfigurationProperties(prefix = "wuzi.ai")
public class LlmProperties {
    /** 总开关；false 时 /api/ai/** 关闭 */
    private boolean enabled = true;
    /** OpenAI 兼容端点，如 http://10.0.33.1:31511/v1 */
    private String baseUrl;
    private String model;
    /** 本地 vLLM 留空；云 provider 才需要 */
    private String apiKey = "";
    private int timeoutSeconds = 60;
    private int maxTokens = 2048;
    /** 回传给模型的最近对话轮数 */
    private int historyRounds = 8;
    /** 每用户每分钟提问上限 */
    private int rateLimitPerMinute = 10;
    /** 单次提问工具调用轮数上限 */
    private int toolMaxRounds = 4;
    /** 向量语义检索（物资名称第三级兜底） */
    private Embedding embedding = new Embedding();

    @Data
    public static class Embedding {
        /** 总开关；false 时解析链只有别名 + LIKE */
        private boolean enabled = true;
        /** Ollama OpenAI 兼容端点 */
        private String baseUrl = "http://127.0.0.1:11434/v1";
        private String model = "bge-m3";
        /** 余弦相似度阈值，低于丢弃 */
        private double threshold = 0.60;
        /** 最多返回候选数 */
        private int topK = 5;
        /** 快照重载间隔（毫秒） */
        private long reloadMs = 1800000;
    }
}
