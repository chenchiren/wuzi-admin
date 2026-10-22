package com.relex.wuzi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.relex.wuzi.domain.Stock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface StockMapper extends BaseMapper<Stock> {

    @Select("""
            SELECT s.id AS stockId, s.warehouse_id AS warehouseId, s.material_id AS materialId,
                   s.batch_no AS batchNo, s.qty AS qty, m.safety_stock AS safetyStock,
                   m.material_code AS materialCode, m.material_name AS materialName
            FROM stock s
            INNER JOIN material m ON m.id = s.material_id AND m.is_deleted = 0
            WHERE s.is_deleted = 0
              AND IFNULL(m.safety_stock, 0) > 0
              AND IFNULL(s.qty, 0) <= IFNULL(m.safety_stock, 0)
            """)
    List<Map<String, Object>> selectLowStock();

    @Select("""
            SELECT s.id AS stockId, s.warehouse_id AS warehouseId, s.material_id AS materialId,
                   s.batch_no AS batchNo, s.qty AS qty, m.max_stock AS maxStock,
                   m.material_code AS materialCode, m.material_name AS materialName
            FROM stock s
            INNER JOIN material m ON m.id = s.material_id AND m.is_deleted = 0
            WHERE s.is_deleted = 0
              AND IFNULL(m.max_stock, 0) > 0
              AND IFNULL(s.qty, 0) >= IFNULL(m.max_stock, 0)
            """)
    List<Map<String, Object>> selectOverStock();

    @Select("SELECT IFNULL(SUM(IFNULL(qty, 0)), 0) FROM stock WHERE is_deleted = 0")
    BigDecimal sumQty();

    @Select("SELECT IFNULL(SUM(IFNULL(cost_tax_incl, 0)), 0) FROM stock WHERE is_deleted = 0")
    BigDecimal sumAmount();

    @Select("""
            SELECT COUNT(1) FROM stock s
            INNER JOIN material m ON m.id = s.material_id AND m.is_deleted = 0
            WHERE s.is_deleted = 0
              AND IFNULL(m.safety_stock, 0) > 0
              AND IFNULL(s.qty, 0) <= IFNULL(m.safety_stock, 0)
            """)
    Long countLowStock();

    @Select("SELECT COUNT(DISTINCT material_id) FROM stock WHERE is_deleted = 0 AND IFNULL(qty, 0) > 0")
    Long countMaterialWithStock();
}
