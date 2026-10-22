package com.relex.wuzi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.relex.wuzi.domain.PurchaseOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 高级报表聚合查询，不改动既有业务 Mapper。
 */
@Mapper
public interface AdvancedReportMapper extends BaseMapper<PurchaseOrder> {

    @Select("""
            SELECT DATE_FORMAT(order_date, '%Y-%m') AS month,
                   IFNULL(SUM(tax_inclusive_amount), 0) AS amount
            FROM purchase_order
            WHERE is_deleted = 0
              AND order_date >= #{start}
              AND order_date < #{end}
            GROUP BY DATE_FORMAT(order_date, '%Y-%m')
            ORDER BY month
            """)
    List<Map<String, Object>> purchaseMonthly(@Param("start") LocalDate start,
                                              @Param("end") LocalDate end);

    @Select("""
            SELECT DATE_FORMAT(created_at, '%Y-%m') AS month,
                   IFNULL(SUM(CASE WHEN IFNULL(change_qty, 0) > 0 THEN change_qty ELSE 0 END), 0) AS inQty,
                   IFNULL(SUM(CASE WHEN IFNULL(change_qty, 0) < 0 THEN -change_qty ELSE 0 END), 0) AS outQty
            FROM stock_record
            WHERE created_at >= #{start}
              AND created_at < #{end}
              AND biz_type <> 'BALANCE_FIX'
            GROUP BY DATE_FORMAT(created_at, '%Y-%m')
            ORDER BY month
            """)
    List<Map<String, Object>> inoutMonthly(@Param("start") LocalDateTime start,
                                           @Param("end") LocalDateTime end);

    @Select("""
            SELECT IFNULL(c.category_name, '未分类') AS name,
                   IFNULL(SUM(IFNULL(s.qty, 0)), 0) AS qty,
                   IFNULL(SUM(IFNULL(s.cost_tax_incl, 0)), 0) AS amount
            FROM stock s
            INNER JOIN material m ON m.id = s.material_id AND m.is_deleted = 0
            LEFT JOIN material_category c ON c.id = m.category_id AND c.is_deleted = 0
            WHERE s.is_deleted = 0
            GROUP BY c.id, c.category_name
            ORDER BY amount DESC, qty DESC
            """)
    List<Map<String, Object>> categoryStock();

    @Select("""
            SELECT w.warehouse_name AS name,
                   IFNULL(SUM(IFNULL(s.qty, 0)), 0) AS qty,
                   IFNULL(SUM(IFNULL(s.cost_tax_incl, 0)), 0) AS amount
            FROM stock s
            INNER JOIN warehouse w ON w.id = s.warehouse_id AND w.is_deleted = 0
            WHERE s.is_deleted = 0
            GROUP BY w.id, w.warehouse_name
            ORDER BY amount DESC, qty DESC
            """)
    List<Map<String, Object>> warehouseStock();

    @Select("""
            SELECT IFNULL(d.dept_name, '未分配') AS name,
                   IFNULL(SUM(IFNULL(i.qty, 0)), 0) AS qty,
                   COUNT(DISTINCT a.id) AS applyCount
            FROM material_use_apply a
            INNER JOIN material_use_apply_item i ON i.apply_id = a.id
            LEFT JOIN sys_dept d ON d.id = a.dept_id AND d.is_deleted = 0
            WHERE a.is_deleted = 0
            GROUP BY d.id, d.dept_name
            ORDER BY qty DESC, applyCount DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> deptUseRank(@Param("limit") int limit);

    @Select("""
            SELECT s.supplier_name AS name,
                   s.supplier_code AS code,
                   IFNULL(SUM(o.tax_inclusive_amount), 0) AS amount,
                   COUNT(o.id) AS orderCount
            FROM supplier s
            INNER JOIN purchase_order o ON o.supplier_id = s.id AND o.is_deleted = 0
            WHERE s.is_deleted = 0
            GROUP BY s.id, s.supplier_name, s.supplier_code
            ORDER BY amount DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> supplierRank(@Param("limit") int limit);
}
