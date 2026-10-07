package com.fc.v2.service.ctld;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fc.v2.model.auto.TCtldCaseFlowTrace;

/**
 * 只读回放 {@link CaseFlowReplayer} 的规矩：
 * 档数从履历倒捋、缺档缺栏点名、轧账与受理簿合得上、单档抽查自洽、与行面对号、回放绝不写回。
 */
class CaseFlowReplayerTest {

    private static final class B {
        final List<TCtldCaseFlowTrace> list = new ArrayList<>();
        int seq = 0;
        long clock = 1_000_000L;

        B put(int stage, String action, String note, Integer closeKind) {
            TCtldCaseFlowTrace t = new TCtldCaseFlowTrace();
            t.setCaseId(7L);
            t.setSeq(++seq);
            t.setStage(stage);
            t.setAction(action);
            t.setNoteText(note);
            t.setCloseKind(closeKind);
            // 同一刻两通道各送一笔也照 seq 分先后：这里逐条加一毫秒，顺序即插入序
            t.setCreateTime(new java.util.Date(clock++));
            list.add(t);
            return this;
        }

        List<TCtldCaseFlowTrace> asc() {
            return list;
        }
    }

    private static B happy() {
        B b = new B();
        b.put(0, CaseFlowEngine.ACT_OPEN, null, null);
        b.put(1, CaseFlowEngine.ACT_ADVANCE, null, null);
        b.put(1, CaseFlowEngine.ACT_ACCEPT, "核收：可", null);
        b.put(2, CaseFlowEngine.ACT_ADVANCE, null, null);
        b.put(2, CaseFlowEngine.ACT_WITNESS, "见证：甲岗", null);
        b.put(2, CaseFlowEngine.ACT_WITNESS, "见证：乙岗", null);
        b.put(2, CaseFlowEngine.ACT_VOUCHER, null, null);
        b.put(3, CaseFlowEngine.ACT_ADVANCE, null, null);
        b.put(3, CaseFlowEngine.ACT_CLOSE, null, CaseFlowEngine.CLOSE_FULL);
        return b;
    }

    @Test
    void 走满四档倒捋停末档_封住_账平_对得上号() {
        CaseFlowReplayer.Result r = CaseFlowReplayer.replay(happy().asc(), 3,
                CaseFlowEngine.STATUS_CLOSED, 4);
        assertEquals(3, r.getTracedStage());
        assertTrue(r.isTracedClosed());
        assertEquals(CaseFlowEngine.CLOSE_FULL, r.getCloseKind());
        assertEquals(4, r.getTotalEntries(), "来件入档=OPEN+ADVANCE=4，与受理簿合得上");
        assertTrue(r.isBookBalanced(), "四档轧账自洽");
        assertTrue(r.isRowMatchesTrace(), "与行面当下对得上号");
        assertTrue(r.getGaps().isEmpty(), () -> "不该有缺口：" + r.getGaps());
        // 倒序：末档在前
        assertEquals(3, r.getStagesReverse().get(0).getStage());
    }

    @Test
    void 回放只读_传入行面是错的也不去改_只点名不对号() {
        // 行面仍记第2档在办，履历却已走到第3档封住：回放照履历给结论，不写回
        CaseFlowReplayer.Result r = CaseFlowReplayer.replay(happy().asc(), 1,
                CaseFlowEngine.STATUS_ACTIVE, 9);
        assertEquals(3, r.getTracedStage());
        assertTrue(r.isTracedClosed());
        assertFalse(r.isRowMatchesTrace());
        boolean stageHit = false, statusHit = false, countHit = false;
        for (CaseFlowReplayer.Gap g : r.getGaps()) {
            stageHit |= g.getField().contains("当前档");
            statusHit |= g.getField().contains("封口");
            countHit |= g.getField().contains("来件笔数");
        }
        assertTrue(stageHit && statusHit && countHit, "三处对不上号都要点名");
    }

    @Test
    void 越档缺栏逐一点名_缺第几档缺哪一栏() {
        B b = new B();
        b.put(0, CaseFlowEngine.ACT_OPEN, null, null);
        b.put(1, CaseFlowEngine.ACT_ADVANCE, null, null);
        // 第2档不记核收「可」，却硬跨第3档
        b.put(2, CaseFlowEngine.ACT_ADVANCE, null, null);
        // 第3档只有一名见证、无凭证，却硬跨末档
        b.put(2, CaseFlowEngine.ACT_WITNESS, "见证：甲岗", null);
        b.put(3, CaseFlowEngine.ACT_ADVANCE, null, null);
        CaseFlowReplayer.Result r = CaseFlowReplayer.replay(b.asc(), 3,
                CaseFlowEngine.STATUS_ACTIVE, 4);

        boolean gate2 = false, w2 = false, voucher = false;
        for (CaseFlowReplayer.Gap g : r.getGaps()) {
            gate2 |= g.getStage() == 2 && g.getField().contains("核收");
            w2 |= g.getStage() == 3 && g.getField().contains("见证岗第二笔");
            voucher |= g.getStage() == 3 && g.getField().contains("凭证");
        }
        assertTrue(gate2, "第2档核收缺栏要指名");
        assertTrue(w2, "第3档第二名见证缺笔要指名");
        assertTrue(voucher, "第3档凭证串缺栏要指名");
    }

    @Test
    void 空履历点名查无来痕() {
        CaseFlowReplayer.Result r = CaseFlowReplayer.replay(new ArrayList<>(), 0,
                CaseFlowEngine.STATUS_ACTIVE, 0);
        assertFalse(r.getGaps().isEmpty());
        assertTrue(r.getGaps().get(0).getField().contains("追溯串"));
    }

    @Test
    void 回退后重走_账仍平_新轮从头累计且旧轮不借结论() {
        B b = new B();
        b.put(0, CaseFlowEngine.ACT_OPEN, null, null);          // 入0
        b.put(1, CaseFlowEngine.ACT_ADVANCE, null, null);       // 入1 出0
        b.put(1, CaseFlowEngine.ACT_ACCEPT, "核收：可", null);
        b.put(2, CaseFlowEngine.ACT_ADVANCE, null, null);       // 入2 出1
        b.put(2, CaseFlowEngine.ACT_WITNESS, "见证：甲岗", null);
        b.put(2, CaseFlowEngine.ACT_ROLLBACK, "三档头一轮的话", null); // 出2 再入1
        b.put(1, CaseFlowEngine.ACT_ACCEPT, "核收：可", null);   // 第2档新一轮重记核收
        b.put(2, CaseFlowEngine.ACT_ADVANCE, null, null);       // 再入2 出1
        CaseFlowReplayer.Result r = CaseFlowReplayer.replay(b.asc(), 2,
                CaseFlowEngine.STATUS_ACTIVE, 4);
        assertTrue(r.isBookBalanced(), () -> "回退重走后轧账要平：" + r.getGaps());
        assertEquals(2, r.getTracedStage());
        assertEquals(4, r.getTotalEntries(), "回退再入不算新受理件，来件仍 4 笔");
        // 倒序视图里第3档（下标1）确实来过
        assertEquals(2, r.getStagesReverse().get(1).getStage());
        assertTrue(r.getStagesReverse().get(1).isEntered());
    }

    @Test
    void 同刻两通道各送一笔_各记先后_不并一条() {
        List<TCtldCaseFlowTrace> list = new ArrayList<>();
        java.util.Date same = new java.util.Date(5_000_000L);
        TCtldCaseFlowTrace t1 = new TCtldCaseFlowTrace();
        t1.setCaseId(9L);
        t1.setSeq(1);
        t1.setStage(0);
        t1.setAction(CaseFlowEngine.ACT_NOTE);
        t1.setNoteText("通道甲的话");
        t1.setCreateTime(same);
        TCtldCaseFlowTrace t2 = new TCtldCaseFlowTrace();
        t2.setCaseId(9L);
        t2.setSeq(2);
        t2.setStage(0);
        t2.setAction(CaseFlowEngine.ACT_NOTE);
        t2.setNoteText("通道乙的话");
        t2.setCreateTime(same);
        list.add(t1);
        list.add(t2);
        // 还需 OPEN 才成件
        TCtldCaseFlowTrace open = new TCtldCaseFlowTrace();
        open.setCaseId(9L);
        open.setSeq(0);
        open.setStage(0);
        open.setAction(CaseFlowEngine.ACT_OPEN);
        open.setCreateTime(new java.util.Date(4_000_000L));
        list.add(0, open);

        CaseFlowReplayer.Result r = CaseFlowReplayer.replay(list, 0,
                CaseFlowEngine.STATUS_ACTIVE, 1);
        // 两句都在履历里各记各的（不并、不吞）；行面按语只留头一句
        assertEquals("通道甲的话", r.getStagesReverse().get(3).getNote());
        assertTrue(r.isBookBalanced());
    }

    @Test
    void 中间档收口_不向后档追门槛_账平() {
        B b = new B();
        b.put(0, CaseFlowEngine.ACT_OPEN, null, null);
        b.put(1, CaseFlowEngine.ACT_ADVANCE, null, null);
        b.put(1, CaseFlowEngine.ACT_CLOSE, "所属分区并走撤出", CaseFlowEngine.CLOSE_ZONE);
        CaseFlowReplayer.Result r = CaseFlowReplayer.replay(b.asc(), 1,
                CaseFlowEngine.STATUS_CLOSED, 2);
        assertTrue(r.isTracedClosed());
        assertEquals(CaseFlowEngine.CLOSE_ZONE, r.getCloseKind());
        assertTrue(r.isBookBalanced());
        assertTrue(r.isRowMatchesTrace());
        boolean askedLater = r.getGaps().stream().anyMatch(g -> g.getStage() >= 3);
        assertFalse(askedLater, "并走收口在第2档，不向第3/4档追门槛");
    }
}
