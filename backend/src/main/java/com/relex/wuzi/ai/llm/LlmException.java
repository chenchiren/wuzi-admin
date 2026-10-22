package com.relex.wuzi.ai.llm;

/**
 * LLM 调用独立异常：触发熔断计数，对外统一降级文案，绝不影响业务接口。
 */
public class LlmException extends RuntimeException {
    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
