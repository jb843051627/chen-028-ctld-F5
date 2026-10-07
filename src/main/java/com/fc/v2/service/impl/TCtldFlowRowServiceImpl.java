package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TCtldFlowRowMapper;
import com.fc.v2.model.auto.TCtldFlowRow;
import com.fc.v2.service.ITCtldFlowRowService;

/**
 * 土壤-农产品协同检测流水核收行 Service业务层处理（batch-process 形状：整批提交）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TCtldFlowRowServiceImpl implements ITCtldFlowRowService {

    private static final int MAX_ROWS = 500;
    private static final int STATUS_OK = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TCtldFlowRowMapper ctldFlowRowMapper;

    @Override
    public TCtldFlowRow selectTCtldFlowRowById(Long id) {
        return this.ctldFlowRowMapper.selectById(id);
    }

    @Override
    public int submitBatch(String batchNo, List<TCtldFlowRow> rows) {
        String no = rows.get(0).getBatchNo();
        java.util.List<TCtldFlowRow> errors = new java.util.ArrayList<TCtldFlowRow>();
        int seq = 0;
        for (TCtldFlowRow r : rows) {
            if (r.getItemCode() == null || r.getItemCode().trim().isEmpty()
                    || r.getQty() == null
                    || r.getQty().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                seq++;
                r.setRowNo(Integer.valueOf(seq));
                r.setBatchNo(no);
                r.setStatus(STATUS_FAIL);
                this.ctldFlowRowMapper.insert(r);
                errors.add(r);
            }
        }
        if (!errors.isEmpty()) {
            return 0;
        }
        int ok = 0;
        for (TCtldFlowRow r : rows) {
            r.setBatchNo(no);
            r.setStatus(STATUS_OK);
            this.ctldFlowRowMapper.insert(r);
            ok++;
        }
        return ok;
    }

    @Override
    public List<TCtldFlowRow> listErrors(String batchNo) {
        return this.ctldFlowRowMapper.selectList(new QueryWrapper<TCtldFlowRow>()
                .eq("batch_no", batchNo).eq("status", STATUS_FAIL));
    }
}
