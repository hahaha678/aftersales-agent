package com.example.aftersales.order.mapper;

import com.example.aftersales.order.domain.po.ShipmentEventPO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ShipmentEventMapper {
    List<ShipmentEventPO> findByOwnedShipment(@Param("userId") long userId, @Param("shipmentId") long shipmentId);
}
