package com.example.aftersales.conversation.mapper;

import com.example.aftersales.agent.domain.po.RunPO;
import com.example.aftersales.conversation.domain.po.*;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ConversationMapper {
    @Insert("INSERT INTO conversation(id,user_id,title) VALUES(#{id},#{user},#{title})")
    int create(@Param("id") String id, @Param("user") long user, @Param("title") String title);

    @Select("SELECT id,user_id,title,created_at FROM conversation WHERE id=#{id} AND user_id=#{user}")
    ConversationPO owned(@Param("id") String id, @Param("user") long user);

    @Select(
        "SELECT id,user_id,title,created_at FROM conversation WHERE user_id=#{user} ORDER BY created_at DESC,id LIMIT 100"
    )
    List<ConversationPO> list(long user);

    @Select("SELECT id FROM app_user WHERE id=#{user} FOR UPDATE")
    Long lockUser(long user);

    @Select("SELECT COUNT(*) FROM agent_run WHERE user_id=#{user} AND status='RUNNING'")
    int active(long user);

    @Select("SELECT COUNT(*) FROM agent_run WHERE user_id=#{user} AND created_at>UTC_TIMESTAMP()-INTERVAL 1 MINUTE")
    int recent(long user);

    @Select("SELECT * FROM agent_run WHERE user_id=#{user} AND request_key=#{key}")
    RunPO byKey(@Param("user") long user, @Param("key") String key);

    @Select("SELECT * FROM agent_run WHERE id=#{id}")
    RunPO run(String id);

    @Select("SELECT * FROM agent_run WHERE conversation_id=#{id} AND status='RUNNING' ORDER BY created_at DESC LIMIT 1")
    RunPO activeRun(String id);

    @Select(
        "SELECT * FROM agent_run WHERE conversation_id=#{id} AND status='SUCCEEDED' ORDER BY created_at DESC,id DESC LIMIT 8"
    )
    List<RunPO> history(String id);

    @Insert(
        "INSERT INTO agent_run(id,conversation_id,user_id,request_key,status,user_content,assistant_content,model,expires_at) VALUES(#{id},#{conversation},#{user},#{key},'RUNNING',#{content},'',#{model},DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 100 SECOND))"
    )
    int begin(
        @Param("id") String id,
        @Param("conversation") String conversation,
        @Param("user") long user,
        @Param("key") String key,
        @Param("content") String content,
        @Param("model") String model
    );

    @Update(
        "UPDATE agent_run SET status=#{status},assistant_content=#{content},error_message=#{error},input_tokens=#{input},output_tokens=#{output},finished_at=UTC_TIMESTAMP(3) WHERE id=#{id} AND status='RUNNING'"
    )
    int finish(
        @Param("id") String id,
        @Param("status") String status,
        @Param("content") String content,
        @Param("error") String error,
        @Param("input") int input,
        @Param("output") int output
    );

    @Select("SELECT * FROM agent_run WHERE user_id=#{user} AND status='RUNNING' AND expires_at<=UTC_TIMESTAMP(3)")
    List<RunPO> expired(long user);

    @Insert(
        "INSERT INTO conversation_message(conversation_id,run_id,role,content,status) VALUES(#{conversation},#{run},#{role},#{content},#{status})"
    )
    int message(
        @Param("conversation") String conversation,
        @Param("run") String run,
        @Param("role") String role,
        @Param("content") String content,
        @Param("status") String status
    );

    @Select(
        "SELECT CAST(m.id AS CHAR) AS id,m.run_id,m.role,m.content,m.status,m.created_at FROM conversation_message m WHERE m.conversation_id=#{id} AND m.id<#{before} ORDER BY m.id DESC LIMIT 50"
    )
    List<MessagePO> messages(@Param("id") String id, @Param("before") long before);

    @Insert(
        "INSERT INTO agent_tool_call(run_id,tool_name,status,duration_ms) VALUES(#{run},#{tool},#{status},#{duration})"
    )
    int audit(
        @Param("run") String run,
        @Param("tool") String tool,
        @Param("status") String status,
        @Param("duration") long duration
    );
}
