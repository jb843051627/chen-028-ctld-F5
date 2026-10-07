package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.fc.v2.common.exception.ctld.CaseFlowException;
import com.fc.v2.mapper.auto.TCtldCaseFlowIdemMapper;
import com.fc.v2.mapper.auto.TCtldCaseFlowMapper;
import com.fc.v2.mapper.auto.TCtldCaseFlowTraceMapper;
import com.fc.v2.mapper.auto.TCtldPlotMapper;
import com.fc.v2.mapper.auto.TCtldZoneBookMapper;
import com.fc.v2.model.auto.TCtldCaseFlow;
import com.fc.v2.model.auto.TCtldCaseFlowIdem;
import com.fc.v2.model.auto.TCtldCaseFlowTrace;
import com.fc.v2.model.auto.TCtldZoneBook;
import com.fc.v2.service.ctld.CaseFlowEngine;
import com.fc.v2.service.ctld.CaseFlowReplayer;
import com.fc.v2.service.ctld.CaseFlowResult;
import com.fc.v2.service.ctld.CaseFlowResult.OpenCmd;

/**
 * 服务层规矩：挪档与留痕同事务联动、一地一活件、同件重递幂等、seq 先后不吞笔、回放只读不写。
 * 库层全部 mock 成内存态，事务用立刻提交的假事务。
 */
class TCtldCaseFlowServiceImplTest {

    private TCtldCaseFlowMapper caseMapper;
    private TCtldCaseFlowTraceMapper traceMapper;
    private TCtldCaseFlowIdemMapper idemMapper;
    private TCtldPlotMapper plotMapper;
    private TCtldZoneBookMapper zoneMapper;
    private TCtldCaseFlowServiceImpl svc;

    private final AtomicLong ids = new AtomicLong(100);
    private final List<TCtldCaseFlowTrace> savedTraces = new ArrayList<>();
    private final Map<Long, TCtldCaseFlow> store = new HashMap<>();

    @BeforeEach
    void setUp() {
        caseMapper = mock(TCtldCaseFlowMapper.class);
        traceMapper = mock(TCtldCaseFlowTraceMapper.class);
        idemMapper = mock(TCtldCaseFlowIdemMapper.class);
        plotMapper = mock(TCtldPlotMapper.class);
        zoneMapper = mock(TCtldZoneBookMapper.class);

        PlatformTransactionManager txm = mock(PlatformTransactionManager.class);
        when(txm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        svc = new TCtldCaseFlowServiceImpl(txm);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "ctldCaseFlowMapper", caseMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "traceMapper", traceMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "idemMapper", idemMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "plotMapper", plotMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "zoneBookMapper", zoneMapper);

        // 单据内存态
        doAnswer((InvocationOnMock inv) -> {
            TCtldCaseFlow c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(ids.incrementAndGet());
            }
            store.put(c.getId(), c);
            return 1;
        }).when(caseMapper).insert(any());
        when(caseMapper.updateById(any())).thenReturn(1);
        when(caseMapper.selectById(any())).thenAnswer(inv -> store.get(inv.getArgument(0)));
        when(caseMapper.selectByIdForUpdate(any())).thenAnswer(inv -> store.get(inv.getArgument(0)));
        when(caseMapper.deleteById(any())).thenAnswer(inv -> store.remove(inv.getArgument(0)) == null ? 0 : 1);

        // 履历 seq/计数随内存单走
        when(traceMapper.maxSeq(any())).thenAnswer(inv -> {
            Long cid = inv.getArgument(0);
            int n = 0;
            for (TCtldCaseFlowTrace t : savedTraces) {
                if (cid.equals(t.getCaseId())) {
                    n = Math.max(n, t.getSeq());
                }
            }
            return n;
        });
        when(traceMapper.countEntries(any())).thenAnswer(inv -> {
            Long cid = inv.getArgument(0);
            int n = 0;
            for (TCtldCaseFlowTrace t : savedTraces) {
                if (cid.equals(t.getCaseId())
                        && (CaseFlowEngine.ACT_OPEN.equals(t.getAction())
                        || CaseFlowEngine.ACT_ADVANCE.equals(t.getAction()))) {
                    n++;
                }
            }
            return n;
        });
        doAnswer((InvocationOnMock inv) -> {
            savedTraces.add(inv.getArgument(0));
            return 1;
        }).when(traceMapper).insert(any());
        when(traceMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(savedTraces));

        when(idemMapper.selectOne(any())).thenReturn(null);
        when(idemMapper.insert(any())).thenReturn(1);

        // 门槛默认：地块在册、分区在册
        when(plotMapper.selectCount(any())).thenReturn(1);
        TCtldZoneBook zone = new TCtldZoneBook();
        zone.setStatus(0);
        when(zoneMapper.selectOne(any())).thenReturn(zone);
    }

    private OpenCmd openCmd(String reqNo) {
        OpenCmd cmd = new OpenCmd();
        cmd.setPlotNo("P9");
        cmd.setZoneNo("QC00");
        cmd.setCdValue(new BigDecimal("1.20"));
        cmd.setMeasureName("品种替代");
        cmd.setSubmitter("张三");
        cmd.setReqNo(reqNo);
        return cmd;
    }

    private List<TCtldCaseFlowTrace> tracesOf(Long id) {
        List<TCtldCaseFlowTrace> l = new ArrayList<>();
        for (TCtldCaseFlowTrace t : savedTraces) {
            if (id.equals(t.getCaseId())) {
                l.add(t);
            }
        }
        return l;
    }

    private void driveToMeasure(Long id) {
        svc.stepForward(id, "F1");
        svc.acceptLimit(id, true, "A1");
        svc.stepForward(id, "F2");
    }

    @Test
    void 起件落头档并落OPEN履历_entercount后端数() {
        CaseFlowResult r = svc.open(openCmd("REQ-1"));
        assertEquals(200, r.getCode());
        assertEquals(0, r.getStage());
        assertEquals(CaseFlowEngine.STATUS_ACTIVE, r.getStatus());
        assertEquals(1, savedTraces.size());
        assertEquals(CaseFlowEngine.ACT_OPEN, savedTraces.get(0).getAction());
        assertEquals(1, store.get(r.getCaseId()).getEnterCount(), "档数后端从履历数，不看报文");
    }

    @Test
    void 一地一活件_插第二条撞活动槽被拒() {
        svc.open(openCmd("REQ-A"));
        org.mockito.Mockito.doThrow(new DuplicateKeyException("uk_plot_active"))
                .when(caseMapper).insert(any());
        CaseFlowException ex = assertThrows(CaseFlowException.class,
                () -> svc.open(openCmd("REQ-B")));
        assertTrue(ex.getMessage().contains("没闭上的件"));
    }

    @Test
    void 同件同号重递幂等_照头一笔回话_不再落痕不挪档() {
        CaseFlowResult first = svc.open(openCmd("DUP"));
        int tracesAfterFirst = savedTraces.size();

        TCtldCaseFlowIdem saved = new TCtldCaseFlowIdem();
        saved.setCaseId(0L);
        saved.setAction(CaseFlowEngine.ACT_OPEN);
        saved.setReqNo("DUP");
        saved.setResultCode(200);
        saved.setResultMsg(first.getMsg());
        saved.setResultStage(0);
        saved.setResultCaseId(first.getCaseId());
        when(idemMapper.selectOne(any())).thenReturn(saved);

        CaseFlowResult again = svc.open(openCmd("DUP"));
        assertTrue(again.isReplayed());
        assertEquals(first.getCaseId(), again.getCaseId());
        assertEquals(tracesAfterFirst, savedTraces.size(), "重笔不再落痕");
        org.mockito.Mockito.verify(caseMapper, org.mockito.Mockito.times(1)).insert(any());
    }

    @Test
    void 推进门槛不闭_一步不走也不留痕() {
        CaseFlowResult opened = svc.open(openCmd("REQ-1"));
        Long id = opened.getCaseId();
        when(plotMapper.selectCount(any())).thenReturn(0); // 底册没挂名

        assertThrows(CaseFlowException.class, () -> svc.stepForward(id, "F1"));
        assertEquals(0, store.get(id).getStage(), "整条留在原地");
        boolean hasAdvance = savedTraces.stream()
                .anyMatch(t -> id.equals(t.getCaseId()) && CaseFlowEngine.ACT_ADVANCE.equals(t.getAction()));
        assertFalse(hasAdvance, "越档/门槛不闭不留痕，挪档与留痕一处口径");
    }

    @Test
    void 连走各档_seq连续不并不吞_走满末档封住() {
        CaseFlowResult opened = svc.open(openCmd("REQ-1"));
        Long id = opened.getCaseId();
        driveToMeasure(id);
        svc.witness(id, "甲岗", "W1");
        svc.witness(id, "乙岗", "W2");
        svc.voucher(id, "VCH-9", "V1");
        CaseFlowResult last = svc.stepForward(id, "F3");

        assertEquals(3, last.getStage());
        assertEquals(CaseFlowEngine.STATUS_CLOSED, last.getStatus());
        assertEquals(CaseFlowEngine.CLOSE_FULL, last.getCloseKind());

        List<TCtldCaseFlowTrace> mine = tracesOf(id);
        for (int i = 0; i < mine.size(); i++) {
            assertEquals(i + 1, mine.get(i).getSeq(), "seq 必须连续，第 " + i + " 笔");
        }
        long advances = mine.stream().filter(t -> CaseFlowEngine.ACT_ADVANCE.equals(t.getAction())).count();
        assertEquals(3, advances);
        // 来件数后端轧：OPEN + 3 ADVANCE
        assertEquals(4, store.get(id).getEnterCount());
    }

    @Test
    void 同档二次按语不采信_只有头一句落痕() {
        CaseFlowResult opened = svc.open(openCmd("REQ-1"));
        Long id = opened.getCaseId();
        svc.note(id, "头一句", "N1");
        CaseFlowException ex = assertThrows(CaseFlowException.class,
                () -> svc.note(id, "第二句", "N2"));
        assertTrue(ex.getMessage().contains("不盖头一句"));
        long notes = tracesOf(id).stream().filter(t -> CaseFlowEngine.ACT_NOTE.equals(t.getAction())).count();
        assertEquals(1, notes, "第二句不另存副本");
        assertEquals("头一句", store.get(id).getStageNote());
    }

    @Test
    void 回退一档_被收按语照原文进追溯串_一次只收一档() {
        CaseFlowResult opened = svc.open(openCmd("REQ-1"));
        Long id = opened.getCaseId();
        driveToMeasure(id);                       // 到第3档（措施核查）
        svc.note(id, "三档留的话", null);

        svc.stepBack(id, "RB1");
        assertEquals(1, store.get(id).getStage(), "一次只收一档：第3档收到第2档");
        TCtldCaseFlowTrace rollback = null;
        for (TCtldCaseFlowTrace t : tracesOf(id)) {
            if (CaseFlowEngine.ACT_ROLLBACK.equals(t.getAction())) {
                rollback = t;
            }
        }
        assertNotNull(rollback);
        assertEquals(2, rollback.getStage(), "退出笔记在被收走的源档（第三档）");
        assertEquals("三档留的话", rollback.getNoteText(), "那句按语照原文转进追溯串末尾");
        // 落回第2档：第3档的见证/凭证从头累计
        assertEquals(0, tracesOf(id).stream()
                .filter(t -> CaseFlowEngine.ACT_WITNESS.equals(t.getAction())).count());
    }

    @Test
    void 封口后更正与抽回都不接() {
        CaseFlowResult opened = svc.open(openCmd("REQ-1"));
        Long id = opened.getCaseId();
        driveToMeasure(id);
        svc.witness(id, "甲", null);
        svc.witness(id, "乙", null);
        svc.voucher(id, "V", null);
        svc.stepForward(id, null); // 封住

        assertFalse(svc.updateContent(id, "想改"), "封口更正不接，老契约回 false");
        assertFalse(svc.remove(id), "封口抽回不接，老契约回 false");
        verify(caseMapper, never()).deleteById(any());
    }

    @Test
    void 回放只读不写_不碰当下任何栏() {
        CaseFlowResult opened = svc.open(openCmd("REQ-1"));
        Long id = opened.getCaseId();
        int before = savedTraces.size();
        CaseFlowReplayer.Result rr = svc.replay(id);
        assertNotNull(rr);
        assertEquals(before, savedTraces.size(), "回放不追加履历");
        assertEquals(0, store.get(id).getStage(), "当下档不被回放改动");
    }

    @Test
    void 未闭条数只由一处数() {
        when(caseMapper.selectCount(any())).thenReturn(7);
        assertEquals(7L, svc.countOpen());
    }
}
