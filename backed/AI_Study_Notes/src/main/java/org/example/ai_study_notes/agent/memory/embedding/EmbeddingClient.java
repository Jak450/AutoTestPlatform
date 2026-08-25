package org.example.ai_study_notes.agent.memory.embedding;

import java.util.List;

/**
 * Embedding 服务抽象：业务代码只依赖此接口，不感知具体实现（本地/云端可切换）。
 */
public interface EmbeddingClient {

    List<Float> embed(String text);

    List<List<Float>> embedAll(List<String> texts);
}
