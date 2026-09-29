package com.example.aftersales.support.mapper;

import com.example.aftersales.conversation.domain.po.MessagePO;
import com.example.aftersales.support.domain.po.*;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TicketMapper {
    String COLUMNS =
        "id,user_id,conversation_id,request_key,problem,order_id,aftersale_id,context_end_id,status,assignee_id,resolution,created_at,updated_at";

    @Select("SELECT " + COLUMNS + " FROM support_ticket WHERE id=#{id}")
    TicketPO find(long id);

    @Select("SELECT " + COLUMNS + " FROM support_ticket WHERE id=#{id} FOR UPDATE")
    TicketPO lock(long id);

    @Select("SELECT " + COLUMNS + " FROM support_ticket WHERE user_id=#{user} AND request_key=#{key}")
    TicketPO byKey(@Param("user") long user, @Param("key") String key);

    @Select("SELECT " + COLUMNS + " FROM support_ticket WHERE active_conversation=#{id}")
    TicketPO active(String id);

    @Select("SELECT COALESCE(MAX(id),0) FROM conversation_message WHERE conversation_id=#{id}")
    long contextEnd(String id);

    @Insert(
        "INSERT INTO support_ticket(user_id,conversation_id,request_key,problem,order_id,aftersale_id,context_end_id) VALUES(#{userId},#{conversationId},#{requestKey},#{problem},#{orderId},#{aftersaleId},#{contextEndId})"
    )
    int insert(TicketPO row);

    @Select(
        "<script>SELECT " +
            COLUMNS +
            " FROM support_ticket WHERE id &lt; #{before}<if test='user != null'> AND user_id=#{user}</if><if test='status != null'> AND status=#{status}</if> ORDER BY id DESC LIMIT 50</script>"
    )
    List<TicketPO> list(@Param("user") Long user, @Param("status") String status, @Param("before") long before);

    @Update(
        "UPDATE support_ticket SET status='IN_PROGRESS',assignee_id=#{actor},updated_at=UTC_TIMESTAMP(3) WHERE id=#{id} AND status='OPEN'"
    )
    int claim(@Param("id") long id, @Param("actor") long actor);

    @Update(
        "UPDATE support_ticket SET status='RESOLVED',resolution=#{note},updated_at=UTC_TIMESTAMP(3) WHERE id=#{id} AND status='IN_PROGRESS'"
    )
    int resolve(@Param("id") long id, @Param("note") String note);

    @Insert("INSERT INTO support_ticket_event(ticket_id,actor_id,action,note) VALUES(#{id},#{actor},#{action},#{note})")
    int event(
        @Param("id") long id,
        @Param("actor") long actor,
        @Param("action") String action,
        @Param("note") String note
    );

    @Select("SELECT action,note,occurred_at FROM support_ticket_event WHERE ticket_id=#{id} ORDER BY id")
    List<TicketEventPO> events(long id);

    // 只分享提交时已存在的消息，后续私人对话不会扩大客服的读取范围。
    @Insert(
        "INSERT INTO support_ticket_context(ticket_id,id,conversation_id,run_id,role,content,status,created_at) SELECT #{ticket},id,conversation_id,run_id,role,content,status,created_at FROM conversation_message WHERE conversation_id=#{conversation} AND id <= #{end}"
    )
    int snapshot(@Param("ticket") long ticket, @Param("conversation") String conversation, @Param("end") long end);

    @Select(
        "SELECT CAST(id AS CHAR) AS id,run_id,role,content,status,created_at FROM support_ticket_context WHERE ticket_id=#{ticket} AND id < #{before} ORDER BY id DESC LIMIT 50"
    )
    List<MessagePO> context(@Param("ticket") long ticket, @Param("before") long before);

    @Select(
        "SELECT ticket_id FROM support_ticket_submission WHERE user_id=#{user} AND request_key=#{key} AND fingerprint=#{fingerprint}"
    )
    Long submitted(@Param("user") long user, @Param("key") String key, @Param("fingerprint") String fingerprint);

    @Select("SELECT COUNT(*) FROM support_ticket_submission WHERE user_id=#{user} AND request_key=#{key}")
    int keyExists(@Param("user") long user, @Param("key") String key);

    @Insert(
        "INSERT INTO support_ticket_submission(user_id,request_key,fingerprint,ticket_id) VALUES(#{user},#{key},#{fingerprint},#{ticket})"
    )
    int submission(
        @Param("user") long user,
        @Param("key") String key,
        @Param("fingerprint") String fingerprint,
        @Param("ticket") long ticket
    );
}
