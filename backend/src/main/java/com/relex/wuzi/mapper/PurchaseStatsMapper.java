package com.relex.wuzi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.relex.wuzi.domain.PurchaseOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Aggregate queries only; existing PurchaseOrderMapper is untouched.
 */
@Mapper
public interface PurchaseStatsMapper extends BaseMapper<PurchaseOrder> {

    @Select("SELECT COALESCE(SUM(tax_inclusive_amount), 0) FROM purchase_order WHERE is_deleted = 0 AND order_date >= #{start} AND order_date < #{end}")
    BigDecimal sumMonthAmount(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Select("SELECT COUNT(DISTINCT id) FROM supplier WHERE is_deleted = 0")
    Long countSupplier();

    @Select("SELECT s.id AS supplierId, s.supplier_name AS supplierName, COALESCE(SUM(o.tax_inclusive_amount), 0) AS amount, COUNT(o.id) AS orderCount FROM supplier s INNER JOIN purchase_order o ON o.supplier_id = s.id AND o.is_deleted = 0 WHERE s.is_deleted = 0 GROUP BY s.id, s.supplier_name ORDER BY amount DESC LIMIT #{limit}")
    List<Map<String, Object>> supplierPurchaseRank(@Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM material WHERE is_deleted = 0")
    Long countMaterial();
}
