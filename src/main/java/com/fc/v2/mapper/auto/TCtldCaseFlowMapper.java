package com.fc.v2.mapper.auto;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fc.v2.model.auto.TCtldCaseFlow;

/**
 * 超标地块纳入受限与解除办件流转单数据层
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface TCtldCaseFlowMapper extends BaseMapper<TCtldCaseFlow> {

    /** 行锁取件：同一件并发的两笔在锁上排队，先后来序不吞笔、不并笔 */
    @Select("SELECT * FROM t_ctld_case_flow WHERE id = #{id} AND del_flag = 0 FOR UPDATE")
    TCtldCaseFlow selectByIdForUpdate(@Param("id") Long id);
}
