package com.fateironist.jawf.ai;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量嵌入抽象类。
 * <p>
 * 屏蔽底层不同 Embedding 模型厂商的差异，对外提供统一的文本向量化能力，
 * 便于与 SQLite vec0 向量库配合做语义检索。
 * <p>
 * 子类只需实现 {@link #getEmbeddingModel()} 返回具体的 {@link EmbeddingModel}。
 */
public abstract class EmbeddingChat {

    /**
     * 返回底层 {@link EmbeddingModel}，由具体实现类提供。
     */
    protected abstract EmbeddingModel getEmbeddingModel();

    /**
     * 对单段文本生成向量。
     *
     * @param text 输入文本
     * @return 向量（float 数组）
     */
    public float[] embed(String text) {
        return getEmbeddingModel().embed(text);
    }

    /**
     * 批量生成向量。
     *
     * @param texts 输入文本列表
     * @return 向量列表，顺序与输入一致
     */
    public List<float[]> embed(List<String> texts) {
        return getEmbeddingModel().embed(texts);
    }

    /**
     * 批量生成向量并返回完整响应（含 token 用量等元信息）。
     */
    public EmbeddingResponse embedForResponse(List<String> texts) {
        return getEmbeddingModel().embedForResponse(texts);
    }

    /**
     * 返回当前模型的向量维度。
     */
    public int dimensions() {
        return getEmbeddingModel().dimensions();
    }

    /**
     * 将文本批量转为 {@link Document} 后再嵌入，便于后续写入向量库。
     */
    public List<float[]> embedDocuments(List<String> texts) {
        List<Document> docs = new ArrayList<>(texts.size());
        for (String text : texts) {
            docs.add(new Document(text));
        }
        return getEmbeddingModel().embed(docs, null, null);
    }

    /**
     * 获取底层 EmbeddingModel。
     */
    public EmbeddingModel getModel() {
        return getEmbeddingModel();
    }
}
