package com.example.aftersales.agent.mapper;

import com.example.aftersales.agent.domain.po.*;
import com.example.aftersales.agent.domain.query.MonitorFilter;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AgentMonitorMapper {
    String FILTER =
        " WHERE 1=1 <if test='status != null'> AND (CASE WHEN status='RUNNING' AND expires_at &lt;= UTC_TIMESTAMP(3) THEN 'EXPIRED' ELSE status END)=#{status}</if><if test='conversationId != null'> AND conversation_id=#{conversationId}</if><if test='from != null'> AND created_at &gt;= #{from}</if><if test='until != null'> AND created_at &lt; #{until}</if>";

    @Select(
        "<script>SELECT * FROM agent_run" +
            FILTER +
            " ORDER BY created_at DESC,id DESC LIMIT #{size} OFFSET #{offset}</script>"
    )
    List<RunPO> page(MonitorFilter query);

    @Select("<script>SELECT COUNT(*) FROM agent_run" + FILTER + "</script>")
    long count(MonitorFilter query);

    @Select("SELECT * FROM agent_run WHERE id=#{id}")
    RunPO find(String id);

    @Select(
        "<script>SELECT run_id,COUNT(*) AS total,SUM(CASE WHEN status='FAILED' THEN 1 ELSE 0 END) AS failed FROM agent_tool_call WHERE run_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> GROUP BY run_id</script>"
    )
    List<ToolCountPO> counts(@Param("ids") List<String> ids);

    @Select(
        "SELECT id,tool_name,status,duration_ms,call_index,started_at,created_at,input_summary,result_summary,error_code FROM agent_tool_call WHERE run_id=#{id} ORDER BY COALESCE(call_index,id),id"
    )
    List<ToolCallPO> tools(String id);

    @Select(
        "SELECT CAST(id AS CHAR) FROM support_ticket WHERE conversation_id=#{conversation} ORDER BY id DESC LIMIT 20"
    )
    List<String> tickets(String conversation);
}
