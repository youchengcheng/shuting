-- =====================================================================
-- 小哈书 AI 模块：pgvector 向量库建表脚本（PostgreSQL）
-- 说明：应用侧 VectorStoreProvider 已开启 initializeSchema，
--       正常情况下会自动建表；生产环境可先手工执行本脚本再关掉自动建表。
--
-- 为什么单独建 t_ai_note_vector_store，而不是复用 t_vector_store？
--   1. 旧的 t_vector_store 是 xiaoha-ai-robot 面试项目建的，主键 id 是 uuid，
--      而笔记检索的向量 id 形如 "1234567:0"（noteId:chunkIndex），类型不兼容；
--   2. 旧表里还留着面试知识库的历史向量，混在一起会污染笔记召回结果。
--   所以本模块使用独立表，互不影响。
-- =====================================================================

-- 1. 向量扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. 向量表（维度必须与 embedding 模型一致，默认 text-embedding-v4 的 1536）
CREATE TABLE IF NOT EXISTS t_ai_note_vector_store (
    id        TEXT PRIMARY KEY,
    content   TEXT,
    metadata  JSON,
    embedding vector(1536)
);

-- 3. HNSW + COSINE 索引
CREATE INDEX IF NOT EXISTS t_ai_note_vector_store_embedding_idx
    ON t_ai_note_vector_store USING hnsw (embedding vector_cosine_ops);