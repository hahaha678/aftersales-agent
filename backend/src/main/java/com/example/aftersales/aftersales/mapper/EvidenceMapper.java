package com.example.aftersales.aftersales.mapper;

import com.example.aftersales.aftersales.domain.vo.EvidenceVO;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface EvidenceMapper {
    @Select(
        "SELECT id,media_type,byte_size,created_at FROM aftersale_evidence WHERE request_id=#{requestId} ORDER BY created_at,id"
    )
    List<EvidenceVO> list(long requestId);

    @Select(
        "SELECT id,media_type,byte_size,created_at FROM aftersale_evidence WHERE request_id=#{requestId} AND sha256=#{hash}"
    )
    EvidenceVO duplicate(@Param("requestId") long requestId, @Param("hash") String hash);

    @Select("SELECT content FROM aftersale_evidence WHERE request_id=#{requestId} AND id=#{id}")
    com.example.aftersales.aftersales.domain.po.EvidenceContentPO content(
        @Param("requestId") long requestId,
        @Param("id") String id
    );

    @Insert(
        "INSERT INTO aftersale_evidence(id,request_id,sha256,media_type,byte_size,content) VALUES(#{id},#{requestId},#{hash},#{mediaType},#{size},#{content})"
    )
    void insert(
        @Param("id") String id,
        @Param("requestId") long requestId,
        @Param("hash") String hash,
        @Param("mediaType") String mediaType,
        @Param("size") int size,
        @Param("content") byte[] content
    );
}
