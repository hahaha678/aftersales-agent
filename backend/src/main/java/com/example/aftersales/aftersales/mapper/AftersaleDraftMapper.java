package com.example.aftersales.aftersales.mapper;

import com.example.aftersales.aftersales.domain.po.AftersaleDraftPO;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AftersaleDraftMapper {
    @Select("SELECT * FROM aftersale_draft WHERE id=#{id} AND user_id=#{user} FOR UPDATE")
    AftersaleDraftPO lock(@Param("id") String id, @Param("user") long user);

    @Select("SELECT * FROM aftersale_draft WHERE id=#{id} AND user_id=#{user}")
    AftersaleDraftPO owned(@Param("id") String id, @Param("user") long user);

    @Select(
        "SELECT * FROM aftersale_draft WHERE conversation_id=#{conversation} AND user_id=#{user} ORDER BY created_at DESC,id LIMIT 50"
    )
    List<AftersaleDraftPO> list(@Param("conversation") String conversation, @Param("user") long user);

    @Insert(
        "INSERT INTO aftersale_draft(id,user_id,conversation_id,run_id,order_id,order_item_id,quantity,reason,description,amount,available_quantity,product_name,order_number,rule_version,expires_at) VALUES(#{id},#{userId},#{conversationId},#{runId},#{orderId},#{orderItemId},#{quantity},#{reason},#{description},#{amount},#{availableQuantity},#{productName},#{orderNumber},#{ruleVersion},#{expiresAt})"
    )
    int insert(AftersaleDraftPO row);

    @Update(
        "UPDATE aftersale_draft SET amount=#{amount},available_quantity=#{available},version=version+1 WHERE id=#{id}"
    )
    int refresh(@Param("id") String id, @Param("amount") BigDecimal amount, @Param("available") int available);

    @Update(
        "UPDATE aftersale_draft SET status='CONFIRMED',aftersale_id=#{application} WHERE id=#{id} AND status='READY'"
    )
    int confirm(@Param("id") String id, @Param("application") long application);

    @Update("UPDATE aftersale_draft SET status='CANCELLED' WHERE id=#{id} AND status='READY'")
    int cancel(String id);
}
