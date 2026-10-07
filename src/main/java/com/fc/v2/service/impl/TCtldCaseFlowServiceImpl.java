package com.fc.v2.service.impl;

import java.util.Date;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.exception.ctld.CaseFlowException;
import com.fc.v2.mapper.auto.TCtldCaseFlowIdemMapper;
import com.fc.v2.mapper.auto.TCtldCaseFlowMapper;
import com.fc.v2.mapper.auto.TCtldCaseFlowTraceMapper;
import com.fc.v2.mapper.auto.TCtldPlotMapper;
import com.fc.v2.mapper.auto.TCtldZoneBookMapper;
import com.fc.v2.model.auto.TCtldCaseFlow;
import com.fc.v2.model.auto.TCtldCaseFlowIdem;
import com.fc.v2.model.auto.TCtldCaseFlowTrace;
import com.fc.v2.model.auto.TCtldPlot;
import com.fc.v2.model.auto.TCtldZoneBook;
import com.fc.v2.service.ITCtldCaseFlowService;
import com.fc.v2.service.ctld.CaseFlowEngine;
import com.fc.v2.service.ctld.CaseFlowEngine.Event;
import com.fc.v2.service.ctld.CaseFlowEngine.GateContext;
import com.fc.v2.service.ctld.CaseFlowReplayer;
import com.fc.v2.service.ctld.CaseFlowResult;
import com.fc.v2.service.ctld.CaseFlowResult.OpenCmd;
import com.fc.v2.shiro.util.ShiroUtils;

/**
 * 超标地块纳入受限与解除办件流转单 Service业务层。
 * 口径全部出自 {@link CaseFlowEngine}（挪档）与 {@link CaseFlowReplayer}（留痕读法），两处一套字；
 * 挪档与写履历在同一事务，要么一起成、要么一起回。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TCtldCaseFlowServiceImpl implements ITCtldCaseFlowService {

    /** 起件笔在幂等账上的占位件号 */
    private static final long IDEM_OPEN_CASE_ID = 0L;

    @javax.annotation.Resource
    private TCtldCaseFlowMapper ctldCaseFlowMapper;
    @javax.annotation.Resource
    private TCtldCaseFlowTraceMapper traceMapper;
    @javax.annotation.Resource
    private TCtldCaseFlowIdemMapper idemMapper;
    @javax.annotation.Resource
    private TCtldPlotMapper plotMapper;
    @javax.annotation.Resource
    private TCtldZoneBookMapper zoneBookMapper;

    private final TransactionTemplate transactionTemplate;

    public TCtldCaseFlowServiceImpl(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    private final GateContext gates = new GateContext() {
        @Override
        public boolean plotRegistered(String plotNo) {
            if (plotNo == null) {
                return false;
            }
            QueryWrapper<TCtldPlot> q = new QueryWrapper<>();
            q.eq("plot_no", plotNo).eq("del_flag", 0);
            return plotMapper.selectCount(q) > 0;
        }

        @Override
        public Integer zoneStatus(String zoneNo) {
            if (zoneNo == null) {
                return null;
            }
            QueryWrapper<TCtldZoneBook> q = new QueryWrapper<>();
            q.eq("zone_no", zoneNo).eq("del_flag", 0).orderByDesc("id").last("limit 1");
            TCtldZoneBook z = zoneBookMapper.selectOne(q);
            return z == null ? null : z.getStatus();
        }
    };

    @Override
    public TCtldCaseFlow selectTCtldCaseFlowById(Long id) {
        return this.ctldCaseFlowMapper.selectById(id);
    }

    @Override
    public List<TCtldCaseFlow> selectTCtldCaseFlowList(QueryWrapper<TCtldCaseFlow> queryWrapper) {
        return this.ctldCaseFlowMapper.selectList(queryWrapper);
    }

    @Override
    public long countOpen() {
        QueryWrapper<TCtldCaseFlow> q = new QueryWrapper<>();
        q.eq("status", CaseFlowEngine.STATUS_ACTIVE).eq("del_flag", 0);
        return ctldCaseFlowMapper.selectCount(q);
    }

    // ============================ 起件 ============================

    @Override
    public CaseFlowResult open(OpenCmd cmd) {
        String reqNo = cmd == null ? null : cmd.getReqNo();
        // 事务外先查一次省一笔；事务内落件前还会再查，兜并发
        CaseFlowResult existed = lookupIdem(IDEM_OPEN_CASE_ID, CaseFlowEngine.ACT_OPEN, reqNo);
        if (existed != null) {
            return existed;
        }
        return runIdempotent(IDEM_OPEN_CASE_ID, CaseFlowEngine.ACT_OPEN, reqNo, () -> {
            CaseFlowResult again = lookupIdem(IDEM_OPEN_CASE_ID, CaseFlowEngine.ACT_OPEN, reqNo);
            if (again != null) {
                return again;
            }
            TCtldCaseFlow c = new TCtldCaseFlow();
            c.setDelFlag(0);
            c.setContent(null);
            List<Event> events = CaseFlowEngine.open(c, cmd.getPlotNo(), cmd.getZoneNo(), cmd.getCdValue(),
                    cmd.getMeasureName(), cmd.getSubmitter());
            c.setBizNo("CF" + System.currentTimeMillis());
            // 先落单据；uk_plot_active 挡「一处地块同时两条未闭上的件」
            ctldCaseFlowMapper.insert(c);
            appendTraces(c.getId(), events, reqNo);
            c.setEnterCount(countEntries(c.getId()));
            ctldCaseFlowMapper.updateById(c);
            return resultOf(c, "已起件，落在第1档（初核受理）");
        });
    }

    // ============================ 本档按语 ============================

    @Override
    public CaseFlowResult note(Long id, String note, String reqNo) {
        return mutate(id, CaseFlowEngine.ACT_NOTE, reqNo,
                c -> CaseFlowEngine.note(c, note),
                c -> "第" + (c.getStage() + 1) + "档按语已落");
    }

    // ============================ 纳限档核收 ============================

    @Override
    public CaseFlowResult acceptLimit(Long id, boolean pass, String reqNo) {
        return mutate(id, CaseFlowEngine.ACT_ACCEPT, reqNo,
                c -> CaseFlowEngine.acceptLimit(c, pass),
                c -> "纳入受限档核收已记成「" + (pass ? "可" : "不可") + "」");
    }

    // ============================ 解除复核门槛：见证 ============================

    @Override
    public CaseFlowResult witness(Long id, String witnessPost, String reqNo) {
        return mutate(id, CaseFlowEngine.ACT_WITNESS, reqNo,
                c -> CaseFlowEngine.signWitness(c, witnessPost),
                c -> "见证岗「" + witnessPost.trim() + "」已落笔"
                        + (c.getWitness2() != null ? "，两名见证已齐" : "，还欠一名见证岗"));
    }

    // ============================ 解除复核门槛：凭证串 ============================

    @Override
    public CaseFlowResult voucher(Long id, String voucherNo, String reqNo) {
        return mutate(id, CaseFlowEngine.ACT_VOUCHER, reqNo,
                c -> CaseFlowEngine.fillVoucher(c, voucherNo),
                c -> "措施核验凭证串已写回本行");
    }

    // ============================ 视同收口 ============================

    @Override
    public CaseFlowResult withdraw(Long id, String reqNo) {
        String operator = safeLoginName();
        return mutate(id, CaseFlowEngine.ACT_CLOSE, reqNo,
                c -> CaseFlowEngine.withdraw(c, operator),
                c -> "递件人已收回反映，本件就此收口封住，不再往前挪");
    }

    @Override
    public CaseFlowResult closeByZoneMerged(Long id, String reqNo) {
        return mutate(id, CaseFlowEngine.ACT_CLOSE, reqNo,
                c -> CaseFlowEngine.closeByZoneMerged(c, gates),
                c -> "所属分区已并走撤出，本件就此收口封住，不再往前挪");
    }

    // ============================ 老入口：推进/回退（签名一字不改） ============================

    @Override
    public TCtldCaseFlow advance(Long id, String remark) {
        try {
            // 老契约：remark 是随件的话，不是递件笔号，不拿去当幂等键；并发由行锁串行，不吞笔
            CaseFlowResult r = mutate(id, CaseFlowEngine.ACT_ADVANCE, null,
                    c -> CaseFlowEngine.advance(c, gates),
                    c -> CaseFlowEngine.isClosed(c)
                            ? "已跨进末档，走满末档，本件封住"
                            : "已推进到第" + (c.getStage() + 1) + "档");
            return selectTCtldCaseFlowById(r.getCaseId());
        } catch (CaseFlowException e) {
            // 老契约：被拒返回 null，整条留在原地
            return null;
        }
    }

    @Override
    public TCtldCaseFlow rollback(Long id, String remark) {
        try {
            CaseFlowResult r = mutate(id, CaseFlowEngine.ACT_ROLLBACK, null,
                    CaseFlowEngine::rollback,
                    c -> "已收回第" + (c.getStage() + 2) + "档，退到第" + (c.getStage() + 1) + "档");
            return selectTCtldCaseFlowById(r.getCaseId());
        } catch (CaseFlowException e) {
            return null;
        }
    }

    /** 新并列入口：推进一档，回话把挡门的那一条点清楚；reqNo 管同件重递幂等 */
    @Override
    public CaseFlowResult stepForward(Long id, String reqNo) {
        return mutate(id, CaseFlowEngine.ACT_ADVANCE, reqNo,
                c -> CaseFlowEngine.advance(c, gates),
                c -> CaseFlowEngine.isClosed(c)
                        ? "已跨进末档（解除复核），走满末档，本件封住"
                        : "已推进到第" + (c.getStage() + 1) + "档");
    }

    /** 新并列入口：回退一档，被收档按语照原文转进追溯串 */
    @Override
    public CaseFlowResult stepBack(Long id, String reqNo) {
        return mutate(id, CaseFlowEngine.ACT_ROLLBACK, reqNo,
                CaseFlowEngine::rollback,
                c -> "已收回第" + (c.getStage() + 2) + "档，退到第" + (c.getStage() + 1)
                        + "档；被收那句按语照原文转进追溯串末尾，新档按语从头累计");
    }

    // ============================ 封口只读：更正/抽回 ============================

    @Override
    public boolean updateContent(Long id, String remark) {
        TCtldCaseFlow c = ctldCaseFlowMapper.selectById(id);
        if (c == null) {
            return false;
        }
        // 一问之三：封住的行吞不吞新按语、改不改内容——不吞、不改；口径只问引擎
        if (CaseFlowEngine.isClosed(c)) {
            return false;
        }
        c.setContent(remark);
        return ctldCaseFlowMapper.updateById(c) > 0;
    }

    @Override
    public boolean remove(Long id) {
        TCtldCaseFlow c = ctldCaseFlowMapper.selectById(id);
        if (c == null) {
            return false;
        }
        // 封住的件在册面露脸、不给抽回的把手；口径只问引擎
        if (CaseFlowEngine.isClosed(c)) {
            return false;
        }
        // 活动件的抽回沿用物理删除，连履历、幂等账一并清走
        QueryWrapper<TCtldCaseFlowTrace> tq = new QueryWrapper<>();
        tq.eq("case_id", id);
        traceMapper.delete(tq);
        QueryWrapper<TCtldCaseFlowIdem> iq = new QueryWrapper<>();
        iq.eq("case_id", id);
        idemMapper.delete(iq);
        return ctldCaseFlowMapper.deleteById(id) > 0;
    }

    // ============================ 履历与只读回放 ============================

    @Override
    public List<TCtldCaseFlowTrace> trace(Long id) {
        QueryWrapper<TCtldCaseFlowTrace> q = new QueryWrapper<>();
        // 同一刻两通道各送一笔：按 seq 分先后，两笔各记各的，不并一条
        q.eq("case_id", id).orderByAsc("create_time", "seq");
        return traceMapper.selectList(q);
    }

    @Override
    public CaseFlowReplayer.Result replay(Long id) {
        TCtldCaseFlow c = ctldCaseFlowMapper.selectById(id);
        if (c == null) {
            throw new CaseFlowException("查无此件，无档可演");
        }
        // 只读：只把履历与行面当下交出去，演完不写回一个字
        return CaseFlowReplayer.replay(trace(id), c.getStage(), c.getStatus(), c.getEnterCount());
    }

    // ============================ 内部：加锁、落痕、幂等 ============================

    /** 一件之内的动作执行器 */
    private interface EngineWork {
        List<Event> apply(TCtldCaseFlow c);
    }

    private CaseFlowResult mutate(Long id, String action, String reqNo, EngineWork work,
                                  java.util.function.Function<TCtldCaseFlow, String> msg) {
        // 事务外先查一次省一笔；事务内锁行后还会再查，兜并发
        CaseFlowResult existed = lookupIdem(id, action, reqNo);
        if (existed != null) {
            return existed;
        }
        return runIdempotent(id, action, reqNo, () -> {
            // 锁行：同一件并发的两笔在此排队，先到先办、后到照回，各拿各的 seq，不吞笔
            TCtldCaseFlow c = ctldCaseFlowMapper.selectByIdForUpdate(id);
            if (c == null) {
                throw new CaseFlowException("查无此件，件留原地");
            }
            CaseFlowResult again = lookupIdem(id, action, reqNo);
            if (again != null) {
                // 重放：锁已拿到但头一笔已办，照头一笔回话，不动当下、不再落痕
                return again;
            }
            List<Event> events = work.apply(c);
            ctldCaseFlowMapper.updateById(c);
            appendTraces(id, events, reqNo);
            c.setEnterCount(countEntries(id));
            ctldCaseFlowMapper.updateById(c);
            return resultOf(c, msg.apply(c));
        });
    }

    private void appendTraces(Long caseId, List<Event> events, String reqNo) {
        int seq = traceMapper.maxSeq(caseId);
        String operator = safeLoginName();
        Date now = new Date();
        for (Event e : events) {
            TCtldCaseFlowTrace t = new TCtldCaseFlowTrace();
            t.setCaseId(caseId);
            t.setSeq(++seq);
            t.setStage(e.stage);
            t.setAction(e.action);
            t.setNoteText(e.noteText);
            t.setCloseKind(e.closeKind);
            t.setReqNo(reqNo);
            t.setOperator(operator);
            t.setDelFlag(0);
            t.setCreateBy(operator);
            t.setCreateTime(now);
            traceMapper.insert(t);
        }
    }

    private int countEntries(Long caseId) {
        Integer n = traceMapper.countEntries(caseId);
        return n == null ? 0 : n;
    }

    private String safeLoginName() {
        try {
            return ShiroUtils.getLoginName();
        } catch (RuntimeException e) {
            return "Task";
        }
    }

    private CaseFlowResult resultOf(TCtldCaseFlow c, String msg) {
        return CaseFlowResult.ok(c.getId(), c.getBizNo(), c.getPlotNo(), c.getStage(), c.getStatus(),
                c.getCloseKind(), msg);
    }

    /** 幂等账先查：有头一笔就照头一笔的回话原封回给，不再动当下 */
    private CaseFlowResult lookupIdem(Long caseId, String action, String reqNo) {
        if (reqNo == null || reqNo.trim().isEmpty()) {
            return null;
        }
        QueryWrapper<TCtldCaseFlowIdem> q = new QueryWrapper<>();
        q.eq("case_id", caseId).eq("action", action).eq("req_no", reqNo.trim()).last("limit 1");
        TCtldCaseFlowIdem row = idemMapper.selectOne(q);
        if (row == null) {
            return null;
        }
        CaseFlowResult r = new CaseFlowResult();
        r.setCode(row.getResultCode() == null ? 200 : row.getResultCode());
        r.setMsg(row.getResultMsg());
        r.setCaseId(row.getResultCaseId() != null ? row.getResultCaseId()
                : (caseId == 0L ? null : caseId));
        r.setBizNo(null);
        r.setPlotNo(null);
        r.setStage(row.getResultStage());
        r.setReplayed(true);
        return r;
    }

    /**
     * 事务里办事，末了落幂等账。并发同号重递时，后到者撞唯一键，整笔回滚后照头一笔回话。
     */
    private CaseFlowResult runIdempotent(Long caseId, String action, String reqNo,
                                         java.util.function.Supplier<CaseFlowResult> work) {
        CaseFlowResult r;
        try {
            r = transactionTemplate.execute(status -> {
                CaseFlowResult result = work.get();
                // 命中头一笔照回的重放结果不再落幂等账（账里头一笔已在）
                if (!result.isReplayed() && reqNo != null && !reqNo.trim().isEmpty()) {
                    TCtldCaseFlowIdem row = new TCtldCaseFlowIdem();
                    row.setCaseId(caseId);
                    row.setAction(action);
                    row.setReqNo(reqNo.trim());
                    row.setResultCode(result.getCode());
                    row.setResultMsg(result.getMsg());
                    row.setResultStage(result.getStage());
                    row.setResultCaseId(result.getCaseId());
                    row.setCreateBy(safeLoginName());
                    row.setCreateTime(new Date());
                    idemMapper.insert(row);
                }
                return result;
            });
        } catch (DuplicateKeyException dup) {
            // 同件并发：要么撞活动槽，要么撞幂等账
            CaseFlowResult saved = lookupIdem(caseId, action, reqNo);
            if (saved != null) {
                return saved;
            }
            if (CaseFlowEngine.ACT_OPEN.equals(action)) {
                throw new CaseFlowException("这处地块已有一条没闭上的件；前一件还停在中间档，第二条起不来");
            }
            throw dup;
        }
        return r;
    }
}
