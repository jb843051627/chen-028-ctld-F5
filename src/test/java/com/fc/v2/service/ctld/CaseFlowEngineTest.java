package com.fc.v2.service.ctld;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fc.v2.common.exception.ctld.CaseFlowException;
import com.fc.v2.model.auto.TCtldCaseFlow;

/**
 * 五问唯一出处 {@link CaseFlowEngine} 的规矩测试：
 * 四要件点名、一档一跨、三道门槛、两种收口、回退转存、封口只读、二次按语、越档不采信。
 */
class CaseFlowEngineTest {

    private static final class Gates implements CaseFlowEngine.GateContext {
        boolean plot = true;
        Integer zone = 0;

        @Override
        public boolean plotRegistered(String plotNo) {
            return plot;
        }

        @Override
        public Integer zoneStatus(String zoneNo) {
            return zone;
        }
    }

    private TCtldCaseFlow opened() {
        TCtldCaseFlow c = new TCtldCaseFlow();
        c.setDelFlag(0);
        CaseFlowEngine.open(c, "P1", "QC00", new BigDecimal("1.35"), "撒施石灰", "张三");
        return c;
    }

    @Test
    void open缺件逐一点名_不许只回信息不全() {
        List<String> missAll = CaseFlowEngine.missingOpenFields(null, " ", null, "");
        assertEquals(4, missAll.size());
        assertTrue(missAll.contains(CaseFlowEngine.FIELD_PLOT_NO));
        assertTrue(missAll.contains(CaseFlowEngine.FIELD_ZONE_NO));
        assertTrue(missAll.contains(CaseFlowEngine.FIELD_CD_VALUE));
        assertTrue(missAll.contains(CaseFlowEngine.FIELD_MEASURE));

        CaseFlowException ex = assertThrows(CaseFlowException.class,
                () -> CaseFlowEngine.open(new TCtldCaseFlow(), "P1", null, new BigDecimal("1"), "m", "张三"));
        assertTrue(ex.getMessage().contains("所属分区代号"), "报错必须点出缺的是分区代号：" + ex.getMessage());
        assertFalse(ex.getMessage().contains("信息不全"));
    }

    @Test
    void open四样齐身落在头一档在办() {
        TCtldCaseFlow c = opened();
        assertEquals(0, c.getStage());
        assertEquals(CaseFlowEngine.STATUS_ACTIVE, c.getStatus());
        assertNull(c.getCloseKind());
    }

    @Test
    void 推进一次只跨一档() {
        TCtldCaseFlow c = opened();
        CaseFlowEngine.advance(c, new Gates());
        assertEquals(1, c.getStage());
        assertEquals(CaseFlowEngine.STATUS_ACTIVE, c.getStatus());
    }

    @Test
    void 纳入受限_底册没挂名进不去() {
        TCtldCaseFlow c = opened();
        Gates g = new Gates();
        g.plot = false;
        CaseFlowException ex = assertThrows(CaseFlowException.class, () -> CaseFlowEngine.advance(c, g));
        assertTrue(ex.getMessage().contains("底册"));
        assertEquals(0, c.getStage(), "被拒整条留在原地");
    }

    @Test
    void 纳入受限_分区并走撤出的进不去() {
        TCtldCaseFlow c = opened();
        Gates g = new Gates();
        g.zone = 1;
        CaseFlowException ex = assertThrows(CaseFlowException.class, () -> CaseFlowEngine.advance(c, g));
        assertTrue(ex.getMessage().contains("并走"));
        assertEquals(0, c.getStage());
    }

    @Test
    void 纳入受限_分区册查无也进不去() {
        TCtldCaseFlow c = opened();
        Gates g = new Gates();
        g.zone = null;
        assertThrows(CaseFlowException.class, () -> CaseFlowEngine.advance(c, g));
        assertEquals(0, c.getStage());
    }

    @Test
    void 措施核查_核收没记可不许开工() {
        TCtldCaseFlow c = opened();
        Gates g = new Gates();
        CaseFlowEngine.advance(c, g); // ->1
        // 不记核收就推
        CaseFlowException ex = assertThrows(CaseFlowException.class, () -> CaseFlowEngine.advance(c, g));
        assertTrue(ex.getMessage().contains("核收") && ex.getMessage().contains("可"));
        assertEquals(1, c.getStage());

        CaseFlowEngine.acceptLimit(c, false);
        CaseFlowException ex2 = assertThrows(CaseFlowException.class, () -> CaseFlowEngine.advance(c, g));
        assertTrue(ex2.getMessage().contains("可"));
        assertEquals(1, c.getStage());
    }

    @Test
    void 解除复核_两名见证加凭证齐了跨进末档即封住() {
        TCtldCaseFlow c = opened();
        Gates g = new Gates();
        CaseFlowEngine.advance(c, g); // 1
        CaseFlowEngine.acceptLimit(c, true);
        CaseFlowEngine.advance(c, g); // 2

        // 缺见证缺凭证
        CaseFlowException ex = assertThrows(CaseFlowException.class, () -> CaseFlowEngine.advance(c, g));
        assertTrue(ex.getMessage().contains("见证岗第一笔"));
        assertTrue(ex.getMessage().contains("见证岗第二笔"));
        assertTrue(ex.getMessage().contains("措施核验凭证串"));
        assertEquals(2, c.getStage());
        assertEquals(CaseFlowEngine.STATUS_ACTIVE, c.getStatus());

        CaseFlowEngine.signWitness(c, "村监岗甲");
        CaseFlowException still = assertThrows(CaseFlowException.class, () -> CaseFlowEngine.advance(c, g));
        assertFalse(still.getMessage().contains("见证岗第一笔"));
        assertTrue(still.getMessage().contains("见证岗第二笔"));

        CaseFlowException sameMan = assertThrows(CaseFlowException.class,
                () -> CaseFlowEngine.signWitness(c, "村监岗甲"));
        assertTrue(sameMan.getMessage().contains("各是各的岗"));

        CaseFlowEngine.signWitness(c, "镇监岗乙");
        CaseFlowEngine.fillVoucher(c, "VCH-2026-0001");
        CaseFlowEngine.advance(c, g); // ->3 闭
        assertEquals(3, c.getStage());
        assertEquals(CaseFlowEngine.STATUS_CLOSED, c.getStatus());
        assertEquals(CaseFlowEngine.CLOSE_FULL, c.getCloseKind());
    }

    @Test
    void 递件人本人收回即收口_旁人收不回() {
        TCtldCaseFlow c = opened();
        CaseFlowException notMe = assertThrows(CaseFlowException.class,
                () -> CaseFlowEngine.withdraw(c, "李四"));
        assertTrue(notMe.getMessage().contains("递件人本人"));
        assertEquals(CaseFlowEngine.STATUS_ACTIVE, c.getStatus());

        CaseFlowEngine.withdraw(c, "张三");
        assertEquals(CaseFlowEngine.STATUS_CLOSED, c.getStatus());
        assertEquals(CaseFlowEngine.CLOSE_WITHDRAW, c.getCloseKind());
    }

    @Test
    void 分区并走在册不收口_撤出才收口() {
        TCtldCaseFlow c = opened();
        Gates g = new Gates();
        assertThrows(CaseFlowException.class, () -> CaseFlowEngine.closeByZoneMerged(c, g));
        g.zone = 1;
        CaseFlowEngine.closeByZoneMerged(c, g);
        assertEquals(CaseFlowEngine.STATUS_CLOSED, c.getStatus());
        assertEquals(CaseFlowEngine.CLOSE_ZONE, c.getCloseKind());
    }

    @Test
    void 封口后添按语更正推进抽回一概不接() {
        TCtldCaseFlow c = opened();
        Gates g = new Gates();
        CaseFlowEngine.advance(c, g);
        CaseFlowEngine.acceptLimit(c, true);
        CaseFlowEngine.advance(c, g);
        CaseFlowEngine.signWitness(c, "甲岗");
        CaseFlowEngine.signWitness(c, "乙岗");
        CaseFlowEngine.fillVoucher(c, "V1");
        CaseFlowEngine.advance(c, g); // 封住

        assertThrows(CaseFlowException.class, () -> CaseFlowEngine.note(c, "封口还想塞话"));
        assertThrows(CaseFlowException.class, () -> CaseFlowEngine.advance(c, g));
        assertThrows(CaseFlowException.class,
                () -> CaseFlowEngine.requireNotClosedForEdit(c, "内容更正"));
        assertThrows(CaseFlowException.class,
                () -> CaseFlowEngine.requireNotClosedForEdit(c, "整条抽回"));
        // 名册里照常露脸，只是没把手：实体还在
        assertNotNull(c.getStage());
    }

    @Test
    void 同档二次按语留头一句_第二句不覆不存() {
        TCtldCaseFlow c = opened();
        CaseFlowEngine.note(c, "头一句");
        assertEquals("头一句", c.getStageNote());
        CaseFlowException ex = assertThrows(CaseFlowException.class,
                () -> CaseFlowEngine.note(c, "第二句"));
        assertTrue(ex.getMessage().contains("不盖头一句"));
        assertEquals("头一句", c.getStageNote(), "第二句盖不上去");
    }

    @Test
    void 回退一次一档_头一档没得再收_按语照原文转出() {
        TCtldCaseFlow c = opened();
        Gates g = new Gates();
        CaseFlowEngine.note(c, "头档话");
        CaseFlowEngine.advance(c, g); // 1
        CaseFlowEngine.acceptLimit(c, true);
        CaseFlowEngine.note(c, "二档留的话");
        CaseFlowEngine.advance(c, g); // 2
        CaseFlowEngine.note(c, "三档留的话");

        List<CaseFlowEngine.Event> evs = CaseFlowEngine.rollback(c);
        assertEquals(1, c.getStage(), "一次只收一档：2->1");
        assertEquals("三档留的话", evs.get(0).noteText, "被收那句按语照原文转进追溯串末尾");
        assertEquals(2, evs.get(0).stage, "退出笔记在被收走的源档（第三档）");
        // 重新挪回第2档（纳入受限）：第3档的见证/凭证从头累计，行面按语空
        assertNull(c.getStageNote());
        assertNull(c.getWitness1());
        assertEquals(CaseFlowEngine.LIMIT_PASS_YES, c.getLimitPass(), "落回第2档，纳限核收仍在（没退到那一档）");

        CaseFlowEngine.rollback(c); // 1->0
        assertEquals(0, c.getStage());
        assertNull(c.getStageNote(), "头档按语已随头档离开压进履历，回退后行面不残留");
        CaseFlowException head = assertThrows(CaseFlowException.class, () -> CaseFlowEngine.rollback(c));
        assertTrue(head.getMessage().contains("头一档"));
    }

    @Test
    void 越档落痕不采信_整条留原地() {
        TCtldCaseFlow c = opened(); // 在第1档
        assertThrows(CaseFlowException.class, () -> CaseFlowEngine.acceptLimit(c, true));
        assertThrows(CaseFlowException.class, () -> CaseFlowEngine.signWitness(c, "甲岗"));
        assertThrows(CaseFlowException.class, () -> CaseFlowEngine.fillVoucher(c, "V1"));
        assertEquals(0, c.getStage());
        assertNull(c.getLimitPass());
    }
}
