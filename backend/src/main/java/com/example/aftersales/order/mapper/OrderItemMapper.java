package com.example.aftersales.order.mapper;

import com.example.aftersales.order.domain.po.OrderItemPO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrderItemMapper {
    List<OrderItemPO> findByOwnedOrder(@Param("userId") long userId, @Param("orderId") long orderId);

    /** 为一页订单批量加载商品，避免逐订单查询；空集合返回空列表。 */
    List<OrderItemPO> findByOwnedOrders(@Param("userId") long userId,
            @Param("orderIds") List<Long> orderIds);
}
