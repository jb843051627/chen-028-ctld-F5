package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.exception.ctld.CaseFlowException;
import com.fc.v2.mapper.auto.TCtldFlowBatchMapper;
import com.fc.v2.mapper.auto.TCtldFlowRowMapper;
import com.fc.v2.mapper.auto.TCtldFlowRowMarkMapper;
import com.fc.v2.mapper.auto.TCtldLimitLineMapper;
import com.fc.v2.mapper.auto.TCtldPlotMapper;
import com.fc.v2.model.auto.TCtldFlowBatch;
import com.fc.v2.model.auto.TCtldFlowRow;
import com.fc.v2.model.auto.TCtldFlowRowMark;
import com.fc.v2.model.auto.TCtldLimitLine;
import com.fc.v2.model.auto.TCtldPlot;
import com.fc.v2.service.ctld.FlowIntakeCmd;
import com.fc.v2.service.ctld.FlowIntakeEngine;
import com.fc.v2.service.ctld.FlowIntakeEngine.BatchOutcome;
import com.fc.v2.service.ctld.FlowIntakeEngine.BookGate;
import com.fc.v2.service.ctld.FlowIntakeEngine.BookInfo;
import com.fc.v2.service.ctld.FlowIntakeEngine.IntakeResult;
import com.fc.v2.service.ctld.FlowIntakeEngine.ParseSpec;
import com.fc.v2.service.ctld.FlowIntakeEngine.RowDecision;
import com.fc.v2.service.ctld.FlowIntakeEngine.RowMark;
import com.fc.v2.service.ctld.FlowIntakeResult;
import com.fc.v2.service.ctld.ITCtldFlowIntakeService;

/**
 * 土壤-农产品协同检测流水「四节核收」Service 实现。
 * 口径全部出自 {@link FlowIntakeEngine}；收拨与流水联动：拨账、各行、标注同一事务，一起成一起回。
 * 同一批次码同期并着进两回：第二遍照头一拨回话（幂等），不另立一拨、不落一行、不翻回未勾。
 *
 * @author fuce
 * @date 2026-10-07
 */
@Service
public class TCtldFlowIntakeServiceImpl implements ITCtldFlowIntakeService {

    @javax.annotation.Resource
    private TCtldFlowBatchMapper batchMapper;
    @javax.annotation.Resource
    private TCtldFlowRowMapper rowMapper;
    @javax.annotation.Resource
    private TCtldFlowRowMarkMapper markMapper;
    @javax.annotation.Resource
    private TCtldPlotMapper plotMapper;
    @javax.annotation.Resource
    private TCtldLimitLineMapper limitLineMapper;

    private final TransactionTemplate transactionTemplate;

    public TCtldFlowIntakeServiceImpl(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // ============================ 整拨核收 ============================

    @Override
    public FlowIntakeResult intake(FlowIntakeCmd cmd) {
        if (cmd == null || cmd.getBatchNo() == null || cmd.getBatchNo().trim().isEmpty()) {
            throw new CaseFlowException("批次码空着，整拨没法落账，不受理");
        }
        String batchNo = cmd.getBatchNo().trim();
        int periodNo = cmd.getPeriodNo() == null ? FlowIntakeEngine.PERIOD_TOWN : cmd.getPeriodNo();
        if (periodNo != FlowIntakeEngine.PERIOD_TOWN && periodNo != FlowIntakeEngine.PERIOD_COUNTY) {
            throw new CaseFlowException("期数只认 1镇级上报 / 2县局复核，报来的是：" + periodNo);
        }
        final ParseSpec spec = buildSpec(cmd);

        // 事务外先照一面：头一拨已在就照回
        if (headBatch(batchNo, periodNo) != null) {
            return replay(batchNo, periodNo);
        }
        try {
            return transactionTemplate.execute(status -> {
                // 锁并发：同批次同期并着进两回，事务内再照一面，后到者照头一拨回话
                TCtldFlowBatch head = headBatch(batchNo, periodNo);
                if (head != null) {
                    return replay(batchNo, periodNo);
                }
                BookGate gate = buildGate();
                IntakeResult engineResult = FlowIntakeEngine.intake(
                        batchNo, periodNo, cmd.getDeclaredCap(), spec, cmd.getRawLines(), gate);

                FlowIntakeResult reply = new FlowIntakeResult();
                reply.setBatchNo(batchNo);
                reply.setPeriodNo(periodNo);
                reply.setLineCap(engineResult.getLineCap());
                reply.setDeclaredCapRaw(engineResult.getDeclaredCapRaw());
                Date now = new Date();
                for (BatchOutcome ob : engineResult.getBatches()) {
                    TCtldFlowBatch b = new TCtldFlowBatch();
                    b.setBatchNo(batchNo);
                    b.setPeriodNo(periodNo);
                    b.setRecvSeq(ob.getRecvSeq());
                    b.setLineCap(ob.getLineCap());
                    b.setDeclaredCap(cmd.getDeclaredCap());
                    b.setParseKind(spec.getKind());
                    b.setTotalRows(ob.getTotalRows());
                    b.setAcceptedRows(ob.getAcceptedRows());
                    b.setRejectedRows(ob.getRejectedRows());
                    b.setEmptyFlag(ob.isEmpty() ? 1 : 0);
                    b.setReceiptText(ob.getReceiptText());
                    b.setDelFlag(0);
                    b.setCreateTime(now);
                    batchMapper.insert(b);

                    Map<Integer, Long> rowNoToId = new HashMap<>();
                    for (RowDecision d : ob.getRows()) {
                        TCtldFlowRow r = new TCtldFlowRow();
                        r.setBatchNo(batchNo);
                        r.setPeriodNo(periodNo);
                        r.setRecvSeq(ob.getRecvSeq());
                        r.setRowNo(d.getRowNo());
                        r.setOrigRowNo(d.getOrigRowNo());
                        r.setItemCode(d.getPlotCode());
                        r.setQty(d.getQty());
                        r.setChkNum(d.getChkNum());
                        r.setCdValue(d.getCdValue());
                        r.setSampleKind(d.getSampleKind());
                        r.setHarvestSeason(d.getHarvestSeason());
                        r.setPutKind(cmd.getPutKind());
                        r.setStatus(d.getStatus());
                        r.setRejectCode(d.getRejectCode());
                        r.setRejectMsg(d.getRejectMsg());
                        r.setMsgTitle(emptyToNull(d.getMsgTitle()));
                        r.setMsgZoneNo(emptyToNull(d.getMsgZoneNo()));
                        r.setBookTitle(d.getBookTitle());
                        r.setBookZoneNo(d.getBookZoneNo());
                        r.setManualSettle(0);
                        r.setDelFlag(0);
                        r.setCreateTime(now);
                        if (d.getStatus() == FlowIntakeEngine.STATUS_UNCHECKED) {
                            // 去处空悬的来由记在备注，待回核逐行定去向，系统不替它收
                            r.setRemark(d.getPendingNote());
                        }
                        rowMapper.insert(r);
                        rowNoToId.put(d.getRowNo(), r.getId());
                    }
                    for (RowMark m : ob.getMarks()) {
                        TCtldFlowRowMark mk = new TCtldFlowRowMark();
                        mk.setBatchId(b.getId());
                        mk.setBatchNo(batchNo);
                        mk.setPeriodNo(periodNo);
                        mk.setRecvSeq(ob.getRecvSeq());
                        mk.setRowNo(m.getRowNo());
                        // 题名/分区比对不合挂得到行；切不开/编码那类无行可挂
                        mk.setRowId(m.getRowNo() == null ? null : rowNoToId.get(m.getRowNo()));
                        mk.setMarkKind(m.getMarkKind());
                        mk.setMarkDetail(m.getDetail());
                        mk.setRawLine(m.getRaw());
                        mk.setHandled(0);
                        mk.setDelFlag(0);
                        mk.setCreateTime(now);
                        markMapper.insert(mk);
                    }
                    reply.getBatches().add(toReceipt(b));
                }
                return reply;
            });
        } catch (DuplicateKeyException dup) {
            // 并发同拨：uk_batch_period_seq 挡住后到者，照头一拨回话，不许另起一拨
            return replay(batchNo, periodNo);
        }
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private ParseSpec buildSpec(FlowIntakeCmd cmd) {
        String kind = cmd.getParseKind() == null ? FlowIntakeEngine.PARSE_DELIM : cmd.getParseKind().trim();
        try {
            if (FlowIntakeEngine.PARSE_FIXED.equals(kind)) {
                return ParseSpec.fixed(cmd.getWidths());
            }
            if (FlowIntakeEngine.PARSE_DELIM.equals(kind)) {
                return ParseSpec.delim(cmd.getDelimiter() == null ? "|" : cmd.getDelimiter());
            }
        } catch (IllegalArgumentException bad) {
            throw new CaseFlowException("报文切法定不下来：" + bad.getMessage());
        }
        throw new CaseFlowException("报文切法只认 DELIM按分隔符 / FIXED按定宽，报来的是：" + kind);
    }

    private TCtldFlowBatch headBatch(String batchNo, int periodNo) {
        QueryWrapper<TCtldFlowBatch> q = new QueryWrapper<>();
        q.eq("batch_no", batchNo).eq("period_no", periodNo).eq("recv_seq", 1)
                .eq("del_flag", 0).last("limit 1");
        return batchMapper.selectOne(q);
    }

    private List<TCtldFlowBatch> batchesOf(String batchNo, int periodNo) {
        QueryWrapper<TCtldFlowBatch> q = new QueryWrapper<>();
        q.eq("batch_no", batchNo).eq("period_no", periodNo).eq("del_flag", 0)
                .orderByAsc("recv_seq");
        return batchMapper.selectList(q);
    }

    private List<TCtldFlowRow> rowsOf(String batchNo, int periodNo, int recvSeq) {
        QueryWrapper<TCtldFlowRow> q = new QueryWrapper<>();
        q.eq("batch_no", batchNo).eq("period_no", periodNo).eq("recv_seq", recvSeq)
                .eq("del_flag", 0).orderByAsc("id");
        return rowMapper.selectList(q);
    }

    private List<TCtldFlowRowMark> marksOf(String batchNo, int periodNo, int recvSeq) {
        QueryWrapper<TCtldFlowRowMark> q = new QueryWrapper<>();
        q.eq("batch_no", batchNo).eq("period_no", periodNo).eq("recv_seq", recvSeq)
                .eq("del_flag", 0).orderByAsc("id");
        return markMapper.selectList(q);
    }

    /** 第二遍照面：头一拨怎么落的，原样回给，不重算、不另落 */
    private FlowIntakeResult replay(String batchNo, int periodNo) {
        List<TCtldFlowBatch> bs = batchesOf(batchNo, periodNo);
        if (bs.isEmpty()) {
            return null;
        }
        FlowIntakeResult reply = new FlowIntakeResult();
        reply.setBatchNo(batchNo);
        reply.setPeriodNo(periodNo);
        reply.setLineCap(bs.get(0).getLineCap());
        reply.setDeclaredCapRaw(bs.get(0).getDeclaredCap());
        reply.setReplayed(true);
        for (TCtldFlowBatch b : bs) {
            reply.getBatches().add(toReceipt(b));
        }
        return reply;
    }

    private FlowIntakeResult.BatchReceipt toReceipt(TCtldFlowBatch b) {
        FlowIntakeResult.BatchReceipt rc = new FlowIntakeResult.BatchReceipt();
        rc.setBatchId(b.getId());
        rc.setRecvSeq(b.getRecvSeq());
        rc.setLineCap(b.getLineCap());
        rc.setTotalRows(b.getTotalRows());
        rc.setAcceptedRows(b.getAcceptedRows());
        rc.setRejectedRows(b.getRejectedRows());
        rc.setEmpty(b.getEmptyFlag() != null && b.getEmptyFlag() == 1);
        rc.setReceiptText(b.getReceiptText());
        int unchecked = 0;
        for (TCtldFlowRow r : rowsOf(b.getBatchNo(), b.getPeriodNo(), b.getRecvSeq())) {
            rc.getRows().add(r);
            if (r.getStatus() != null && r.getStatus() == FlowIntakeEngine.STATUS_UNCHECKED) {
                unchecked++;
            }
        }
        rc.setUncheckedRows(unchecked);
        rc.getMarks().addAll(marksOf(b.getBatchNo(), b.getPeriodNo(), b.getRecvSeq()));
        return rc;
    }

    // ============================ 底册与告警线 ============================

    private BookGate buildGate() {
        return new BookGate() {
            @Override
            public BookInfo lookup(String plotNo) {
                // 对地块只认地块码：精确相等，题名近似不算
                QueryWrapper<TCtldPlot> q = new QueryWrapper<>();
                q.eq("plot_no", plotNo).eq("del_flag", 0).last("limit 1");
                TCtldPlot p = plotMapper.selectOne(q);
                if (p == null) {
                    return null;
                }
                return new BookInfo(p.getContent(), p.getZoneNo());
            }

            @Override
            public BigDecimal warnLine(String plotNo, String zoneNo, String harvestSeason) {
                // 当地那条告警线：取在位限量档里的告警限值（th2_max），交班次序高的先说话
                QueryWrapper<TCtldLimitLine> q = new QueryWrapper<>();
                q.eq("del_flag", 0).eq("status", 0).isNotNull("th2_max")
                        .orderByDesc("priority", "id");
                List<TCtldLimitLine> lines = limitLineMapper.selectList(q);
                Date now = new Date();
                TCtldLimitLine best = null;
                for (TCtldLimitLine l : lines) {
                    boolean afterStart = l.getEffStart() == null || !now.before(l.getEffStart());
                    boolean beforeEnd = l.getEffEnd() == null || now.before(l.getEffEnd());
                    if (afterStart && beforeEnd) {
                        best = l;
                        break;
                    }
                }
                return best == null ? null : best.getTh2Max();
            }
        };
    }

    // ============================ 待核 / 回核 ============================

    @Override
    public List<TCtldFlowRow> pendingList(String batchNo) {
        QueryWrapper<TCtldFlowRow> q = new QueryWrapper<>();
        q.eq("status", FlowIntakeEngine.STATUS_UNCHECKED).eq("del_flag", 0);
        if (batchNo != null && !batchNo.trim().isEmpty()) {
            q.eq("batch_no", batchNo.trim());
        }
        q.orderByAsc("batch_no", "period_no", "recv_seq", "row_no");
        return rowMapper.selectList(q);
    }

    @Override
    public boolean settlePending(Long rowId, int status, String rejectCode) {
        if (rowId == null) {
            return false;
        }
        if (status != FlowIntakeEngine.STATUS_ACCEPTED && status != FlowIntakeEngine.STATUS_REJECTED) {
            throw new CaseFlowException("回核定去向只准写 1收下 / 2退回，第四种写法落不了库");
        }
        TCtldFlowRow r = rowMapper.selectById(rowId);
        if (r == null || (r.getDelFlag() != null && r.getDelFlag() == 1)) {
            return false;
        }
        if (r.getStatus() == null || r.getStatus() != FlowIntakeEngine.STATUS_UNCHECKED) {
            // 已收下/已退回的行不许借回核翻案；只退该退的那几条，已收的不回未勾
            throw new CaseFlowException("本行去处早已落定（" + r.getStatus() + "），回核只定去处空悬的行，不动旧账");
        }
        String rejectMsg = null;
        if (status == FlowIntakeEngine.STATUS_REJECTED) {
            String code = rejectCode == null ? null : rejectCode.trim();
            if (code == null || code.isEmpty()) {
                // 先拒后改：条款没写明，行面原样留在未勾
                throw new CaseFlowException("退回必须写明为哪一条退，不许只落个「退回」");
            }
            rejectMsg = FlowIntakeEngine.rejectMessage(code);
        }
        r.setStatus(status);
        r.setManualSettle(1);
        if (status == FlowIntakeEngine.STATUS_REJECTED) {
            r.setRejectCode(rejectCode.trim());
            r.setRejectMsg(rejectMsg);
        }
        r.setUpdateTime(new Date());
        return rowMapper.updateById(r) > 0;
    }

    @Override
    public List<TCtldFlowRowMark> markList(String batchNo) {
        QueryWrapper<TCtldFlowRowMark> q = new QueryWrapper<>();
        q.eq("del_flag", 0);
        if (batchNo != null && !batchNo.trim().isEmpty()) {
            q.eq("batch_no", batchNo.trim());
        }
        q.orderByAsc("batch_no", "period_no", "recv_seq", "row_no");
        return markMapper.selectList(q);
    }

    @Override
    public ITCtldFlowIntakeService.RowVerify verify(Long rowId) {
        TCtldFlowRow r = rowId == null ? null : rowMapper.selectById(rowId);
        if (r == null || (r.getDelFlag() != null && r.getDelFlag() == 1)) {
            throw new CaseFlowException("抽不到这一行，无档可核");
        }
        QueryWrapper<TCtldFlowBatch> q = new QueryWrapper<>();
        q.eq("batch_no", r.getBatchNo()).eq("period_no", r.getPeriodNo())
                .eq("recv_seq", r.getRecvSeq()).eq("del_flag", 0).last("limit 1");
        TCtldFlowBatch b = batchMapper.selectOne(q);
        if (b == null) {
            throw new CaseFlowException("本行所属拨账查无，行不许漂拨——落库出了岔子");
        }

        // 当场点算的行（manual_settle=0）重新逐行数一遍，与拨头三栏对印；事后定夺的悬行不进当场三栏
        List<TCtldFlowRow> sameBatch = rowsOf(r.getBatchNo(), r.getPeriodNo(), r.getRecvSeq());
        int accepted = 0;
        int rejected = 0;
        List<String> oddRows = new ArrayList<>();
        for (TCtldFlowRow x : sameBatch) {
            if (x.getManualSettle() != null && x.getManualSettle() == 1) {
                continue;
            }
            if (x.getStatus() != null && x.getStatus() == FlowIntakeEngine.STATUS_ACCEPTED) {
                accepted++;
            } else if (x.getStatus() != null && x.getStatus() == FlowIntakeEngine.STATUS_REJECTED) {
                rejected++;
            } else {
                oddRows.add("第" + x.getRowNo() + "行去处不是收/退却混进了三栏");
            }
        }
        boolean countsMatch = accepted == b.getAcceptedRows()
                && rejected == b.getRejectedRows()
                && (accepted + rejected) == b.getTotalRows();
        boolean rowCountsItself = (r.getManualSettle() != null && r.getManualSettle() == 1)
                || (r.getStatus() != null
                        && (r.getStatus() == FlowIntakeEngine.STATUS_ACCEPTED
                        || r.getStatus() == FlowIntakeEngine.STATUS_REJECTED));
        boolean matched = countsMatch && rowCountsItself;

        ITCtldFlowIntakeService.RowVerify v = new ITCtldFlowIntakeService.RowVerify();
        v.setRow(r);
        v.setBatchTotal(b.getTotalRows());
        v.setBatchAccepted(b.getAcceptedRows());
        v.setBatchRejected(b.getRejectedRows());
        StringBuilder sb = new StringBuilder();
        if (matched) {
            sb.append("对印得上：本行去处=").append(statusName(r.getStatus()))
                    .append("；拨头三栏 进门").append(b.getTotalRows()).append("=收下")
                    .append(b.getAcceptedRows()).append("+退回").append(b.getRejectedRows())
                    .append("，与逐行点算同源");
        } else {
            sb.append("对印不上，逐行点名：");
            if (!countsMatch) {
                sb.append("逐行数出 收下").append(accepted).append(" 退回").append(rejected)
                        .append("，拨头记的是 收下").append(b.getAcceptedRows()).append(" 退回")
                        .append(b.getRejectedRows()).append(" 进门").append(b.getTotalRows()).append("；");
            }
            if (!oddRows.isEmpty()) {
                sb.append(String.join("；", oddRows)).append("；");
            }
            if (!rowCountsItself) {
                sb.append("第").append(r.getRowNo()).append("行去处空悬在未勾，本就不在当场三栏内（看待核单）；");
            }
        }
        v.setMatched(matched);
        v.setDetail(sb.toString());
        return v;
    }

    private static String statusName(Integer status) {
        if (status == null) {
            return "未勾";
        }
        switch (status) {
            case FlowIntakeEngine.STATUS_ACCEPTED:
                return "收下";
            case FlowIntakeEngine.STATUS_REJECTED:
                return "退回";
            case FlowIntakeEngine.STATUS_UNCHECKED:
                return "未勾";
            default:
                return "第四种写法（落不了库）";
        }
    }

    @Override
    public FlowIntakeResult.BatchReceipt receipt(Long batchId) {
        TCtldFlowBatch b = batchId == null ? null : batchMapper.selectById(batchId);
        if (b == null || (b.getDelFlag() != null && b.getDelFlag() == 1)) {
            throw new CaseFlowException("查无此拨，无回执可复述");
        }
        return toReceipt(b);
    }
}
