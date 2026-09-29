package com.example.aftersales.knowledge.mapper;

import com.example.aftersales.knowledge.domain.po.*;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PolicyMapper {
    @Update(
        """
        UPDATE knowledge_policy SET title=#{title},content=#{content},effective_from=#{effectiveFrom},effective_until=#{effectiveUntil}
        WHERE id=#{id} AND status='DRAFT'
        """
    )
    void edit(PolicyPO row);

    @Insert("INSERT IGNORE INTO knowledge_policy_lock(policy_key,scope) VALUES(#{policyKey},#{scope})")
    void ensureGroup(PolicyPO row);

    @Select("SELECT policy_key FROM knowledge_policy_lock WHERE policy_key=#{policyKey} AND scope=#{scope} FOR UPDATE")
    String lockGroup(PolicyPO row);

    @Select(
        "SELECT COALESCE(MAX(version),0) FROM knowledge_policy WHERE policy_key=#{policyKey} AND scope=#{scope} AND embedding_identity IS NOT NULL"
    )
    int latestPublishedVersion(PolicyPO row);

    @Insert("INSERT IGNORE INTO knowledge_run_source(run_id,source_id,source_json) VALUES(#{run},#{source},#{json})")
    void recordSource(@Param("run") String run, @Param("source") String source, @Param("json") String json);

    @Select("SELECT source_json FROM knowledge_run_source WHERE run_id=#{run} ORDER BY source_id")
    List<String> sources(String run);

    @Select("SELECT * FROM knowledge_policy ORDER BY created_at DESC, id LIMIT 100")
    List<PolicyPO> list();

    @Select("SELECT * FROM knowledge_policy WHERE id=#{id}")
    PolicyPO find(String id);

    @Select("SELECT * FROM knowledge_policy WHERE id=#{id} FOR UPDATE")
    PolicyPO lock(String id);

    @Insert(
        """
        INSERT INTO knowledge_policy(id,policy_key,version,title,scope,content,status,effective_from,effective_until)
        VALUES(#{id},#{policyKey},#{version},#{title},#{scope},#{content},'DRAFT',#{effectiveFrom},#{effectiveUntil})
        """
    )
    void insert(PolicyPO row);

    @Select(
        """
        SELECT * FROM knowledge_policy WHERE status='PUBLISHED'
        AND effective_from <= #{today} AND effective_until >= #{today}
        AND (scope='GLOBAL' OR scope=#{scope}) ORDER BY id
        """
    )
    List<PolicyPO> active(@Param("scope") String scope, @Param("today") LocalDate today);

    @Select("SELECT * FROM knowledge_chunk WHERE policy_id=#{id} ORDER BY ordinal")
    List<PolicyChunkPO> chunks(String id);

    @Insert(
        """
        INSERT INTO knowledge_chunk(id,policy_id,ordinal,content,embedding)
        VALUES(#{id},#{policyId},#{ordinal},#{content},#{embedding})
        """
    )
    void insertChunk(PolicyChunkPO row);

    @Update(
        """
        UPDATE knowledge_policy SET status='ARCHIVED'
        WHERE policy_key=#{policyKey} AND scope=#{scope} AND status='PUBLISHED'
        """
    )
    void archivePrevious(PolicyPO row);

    @Update("UPDATE knowledge_policy SET status='PUBLISHED',embedding_identity=#{identity} WHERE id=#{id}")
    void publish(@Param("id") String id, @Param("identity") String identity);

    @Update("UPDATE knowledge_policy SET status='ARCHIVED' WHERE id=#{id}")
    void archive(String id);
}
