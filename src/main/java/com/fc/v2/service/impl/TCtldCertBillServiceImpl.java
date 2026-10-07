package com.fc.v2.service.impl;

import org.springframework.stereotype.Service;

import com.fc.v2.mapper.auto.TCtldCertBillMapper;
import com.fc.v2.model.auto.TCtldCertBill;
import com.fc.v2.service.ITCtldCertBillService;

/**
 * 产地准出合格证签批单 Service业务层处理（approval-chain 形状：多阶段签批）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TCtldCertBillServiceImpl implements ITCtldCertBillService {

    private static final int MAX_NODE = 2;
    private static final int MODE_OR = 0;
    private static final int MODE_AND = 1;
    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_PASS = 1;
    private static final int STATUS_VETO = 2;

    @javax.annotation.Resource
    private TCtldCertBillMapper ctldCertBillMapper;

    @Override
    public TCtldCertBill selectTCtldCertBillById(Long id) {
        return this.ctldCertBillMapper.selectById(id);
    }

    @Override
    public TCtldCertBill approve(Long id, String approver, String comment) {
        TCtldCertBill r = this.ctldCertBillMapper.selectById(id);
        if (r == null || approver == null || approver.trim().isEmpty()) {
            return null;
        }
        r.setNodeNo(Integer.valueOf((r.getNodeNo() == null ? 0 : r.getNodeNo()) + 1));
        r.setStatus(Integer.valueOf(r.getNodeNo() >= MAX_NODE ? STATUS_PASS : STATUS_RUNNING));
        this.ctldCertBillMapper.updateById(r);
        return r;
    }

    @Override
    public TCtldCertBill reject(Long id, String approver, String comment) {
        TCtldCertBill r = this.ctldCertBillMapper.selectById(id);
        if (r == null) {
            return null;
        }
        this.ctldCertBillMapper.updateById(r);
        return r;
    }

    @Override
    public TCtldCertBill rollback(Long id, String comment) {
        TCtldCertBill r = this.ctldCertBillMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo();
        r.setNodeNo(Integer.valueOf(Math.max(0, node - 1)));
        this.ctldCertBillMapper.updateById(r);
        return r;
    }
}
