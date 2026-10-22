package com.relex.wuzi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.relex.wuzi.domain.StockRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface StockRecordMapper extends BaseMapper<StockRecord> {

    @Select("""
            SELECT IFNULL(SUM(CASE WHEN IFNULL(change_qty, 0) > 0 THEN change_qty ELSE 0 END), 0)
            FROM stock_record
            WHERE created_at >= #{start}
              AND biz_type <> 'BALANCE_FIX'
            """)
    BigDecimal sumInboundQty(@Param("start") LocalDateTime start);

    @Select("""
            SELECT IFNULL(SUM(CASE WHEN IFNULL(change_qty, 0) < 0 THEN -change_qty ELSE 0 END), 0)
            FROM stock_record
            WHERE created_at >= #{start}
              AND biz_type <> 'BALANCE_FIX'
            """)
    BigDecimal sumOutboundQty(@Param("start") LocalDateTime start);

    @Select("""
            SELECT biz_type AS bizType,
                   IFNULL(SUM(IFNULL(change_qty, 0)), 0) AS totalChangeQty,
                   IFNULL(SUM(ABS(IFNULL(change_qty, 0))), 0) AS qty,
                   COUNT(1) AS recordCount
            FROM stock_record
            WHERE created_at >= #{start}
              AND biz_type <> 'BALANCE_FIX'
            GROUP BY biz_type
            ORDER BY ABS(IFNULL(SUM(IFNULL(change_qty, 0)), 0)) DESC
            """)
    List<Map<String, Object>> sumQtyByBizType(@Param("start") LocalDateTime start);
}
