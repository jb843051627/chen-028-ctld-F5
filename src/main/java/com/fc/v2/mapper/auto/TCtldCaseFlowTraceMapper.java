package com.fc.v2.mapper.auto;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fc.v2.model.auto.TCtldCaseFlowTrace;

/**
 * 流转单变更履历数据层（追溯串只追加）
 *
 * @author fuce
 * @date 2026-10-06
 */
public interface TCtldCaseFlowTraceMapper extends BaseMapper<TCtldCaseFlowTrace> {

    /** 本件已用最大 seq；空履历回 0。须在取行锁之后调用 */
    @Select("SELECT COALESCE(MAX(seq), 0) FROM t_ctld_case_flow_trace WHERE case_id = #{caseId}")
    int maxSeq(@Param("caseId") Long caseId);

    /** 入档笔数：OPEN 与 ADVANCE 之和（档数由后端从履历数，不信报文序号） */
    @Select("SELECT COUNT(1) FROM t_ctld_case_flow_trace "
            + "WHERE case_id = #{caseId} AND action IN ('OPEN', 'ADVANCE')")
    Integer countEntries(@Param("caseId") Long caseId);
}
