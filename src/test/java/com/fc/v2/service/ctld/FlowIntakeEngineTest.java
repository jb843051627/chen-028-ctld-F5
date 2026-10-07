package com.fc.v2.service.ctld;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fc.v2.service.ctld.FlowIntakeEngine.BatchOutcome;
import com.fc.v2.service.ctld.FlowIntakeEngine.BookGate;
import com.fc.v2.service.ctld.FlowIntakeEngine.BookInfo;
import com.fc.v2.service.ctld.FlowIntakeEngine.IntakeResult;
import com.fc.v2.service.ctld.FlowIntakeEngine.ParseSpec;
import com.fc.v2.service.ctld.FlowIntakeEngine.ParsedRow;
import com.fc.v2.service.ctld.FlowIntakeEngine.RowDecision;
import com.fc.v2.service.ctld.FlowIntakeEngine.RowMark;

/**
 * 四节核收唯一出处 {@link FlowIntakeEngine} 的规矩测试：
 * 抬头上限归一、一拨一种切法与三类标注、五条退回逐行点名、同地块多行各定各、
 * 到顶分拨不硬塞、空拨、重报不重排队、三栏同一次点算（未勾不计）、题名比对不合另列。
 */
class FlowIntakeEngineTest {

    /** 内存底册：P1001/P1002 在册，告警线 0.400（QC00 / 2025秋） */
    private static final class FakeBook implements BookGate {
        final Map<String, BookInfo> books = new HashMap<>();
        BigDecimal warn = new BigDecimal("0.400");

        FakeBook() {
            books.put("P1001", new BookInfo("东片一号地", "QC00"));
            books.put("P1002", new BookInfo("东片二号地", "QC00"));
            books.put("P1003", new BookInfo("南片三号地", "QC01"));
        }

        @Override
        public BookInfo lookup(String plotNo) {
            return books.get(plotNo);
        }

        @Override
        public BigDecimal warnLine(String plotNo, String zoneNo, String harvestSeason) {
            return warn;
        }
    }

    private final ParseSpec delim = ParseSpec.delim("|");

    /** 好行：样别|季|码|题名|分区|送检数|达标数|镉 */
    private String good(String kind, String code, String qty, String chk, String cd) {
        return kind + "|2025秋|" + code + "|东片一号地|QC00|" + qty + "|" + chk + "|" + cd;
    }

    private IntakeResult run(String capRaw, List<String> lines, BookGate gate) {
        return FlowIntakeEngine.intake("B1", FlowIntakeEngine.PERIOD_TOWN, capRaw, delim, lines, gate);
    }

    // ============ 第一节：抬头 ============

    @Test
    void 抬头上限_空着负数离谱不成数都按300() {
        assertEquals(300, FlowIntakeEngine.normalizeCap(null));
        assertEquals(300, FlowIntakeEngine.normalizeCap("  "));
        assertEquals(300, FlowIntakeEngine.normalizeCap("-1"));
        assertEquals(300, FlowIntakeEngine.normalizeCap("0"));
        assertEquals(300, FlowIntakeEngine.normalizeCap("999999999"));
        assertEquals(300, FlowIntakeEngine.normalizeCap("abc"));
        assertEquals(300, FlowIntakeEngine.normalizeCap("12.5"));
        assertEquals(2, FlowIntakeEngine.normalizeCap("2"));
        assertEquals(100000, FlowIntakeEngine.normalizeCap("100000"));
    }

    @Test
    void 空拨合法_只落空拨两字_不出收讫_三栏全0() {
        IntakeResult r = run(null, new ArrayList<>(), new FakeBook());
        assertEquals(1, r.getBatches().size());
        BatchOutcome b = r.firstBatch();
        assertTrue(b.isEmpty());
        assertEquals("空拨", b.getReceiptText());
        assertEquals(0, b.getTotalRows());
        assertEquals(0, b.getAcceptedRows());
        assertEquals(0, b.getRejectedRows());
        assertTrue(b.getRows().isEmpty());
    }

    @Test
    void 到顶即止_多出来的另起一拨_不硬塞() {
        List<String> lines = Arrays.asList(
                good("SOIL", "P1001", "5", "5", "0.20"),
                good("PRODUCT", "P1001", "5", "4", ""),
                good("SOIL", "P1002", "3", "3", "0.10"),
                good("PRODUCT", "P1002", "3", "3", ""),
                good("SOIL", "P1003", "2", "2", "0.30"));
        IntakeResult r = run("2", lines, new FakeBook());
        assertEquals(3, r.getBatches().size());
        assertEquals(2, r.getBatches().get(0).getRows().size());
        assertEquals(2, r.getBatches().get(1).getRows().size());
        assertEquals(1, r.getBatches().get(2).getRows().size());
        assertEquals(1, r.getBatches().get(0).getRecvSeq());
        assertEquals(2, r.getBatches().get(1).getRecvSeq());
        assertEquals(3, r.getBatches().get(2).getRecvSeq());
        // 行不许漂拨：每行都带着自己进的拨序，批次码一致
        assertEquals("B1", r.getBatchNo());
    }

    // ============ 第二节：行次与去处 ============

    @Test
    void 同一地块两行_土壤与农产品_两季_各定各的去向互不覆盖() {
        List<String> lines = Arrays.asList(
                "SOIL|2025秋|P1001|东片一号地|QC00|5|5|0.20",
                "PRODUCT|2025秋|P1001|东片一号地|QC00|5|0|",
                "SOIL|2026春|P1001|东片一号地|QC00|0|0|0.10");
        BatchOutcome b = run(null, lines, new FakeBook()).firstBatch();
        assertEquals(3, b.getRows().size());
        assertEquals(FlowIntakeEngine.STATUS_ACCEPTED, b.getRows().get(0).getStatus(), "土壤样收下");
        assertEquals(FlowIntakeEngine.STATUS_ACCEPTED, b.getRows().get(1).getStatus(), "农产品样也收下，不被覆盖");
        assertEquals(FlowIntakeEngine.STATUS_REJECTED, b.getRows().get(2).getStatus(), "另一季送检数0退回，不连累前两行");
        assertEquals("2025秋", b.getRows().get(0).getHarvestSeason());
        assertEquals("2026春", b.getRows().get(2).getHarvestSeason());
        assertEquals(2, b.getAcceptedRows());
        assertEquals(1, b.getRejectedRows());
    }

    @Test
    void 退回行过几日重报_行面仍显示当初行次_不重新排队_新行不留空位() {
        // 第2条是当初第7行退回后的重报；其余新行连续排 1,2,3，重报行显 7，不把 7 之前填出空位
        List<String> lines = Arrays.asList(
                "SOIL|2025秋|P1001|东片一号地|QC00|5|5|0.20",
                "SOIL|2025秋|P1002|东片二号地|QC00|4|4|0.20|7",
                "PRODUCT|2025秋|P1003|南片三号地|QC01|2|2|");
        BatchOutcome b = run(null, lines, new FakeBook()).firstBatch();
        assertEquals(1, b.getRows().get(0).getRowNo());
        assertEquals(7, b.getRows().get(1).getRowNo(), "重报行照显当初那一个行次");
        assertEquals(Integer.valueOf(7), b.getRows().get(1).getOrigRowNo());
        assertEquals(2, b.getRows().get(2).getRowNo(), "新行接着排，不留空位");
    }

    // ============ 第三节：数与解析 ============

    @Test
    void 两个数只到一个_不进门_标注半数_不代填不按0认_整拨不被按住() {
        List<String> lines = Arrays.asList(
                "SOIL|2025秋|P1001|东片一号地|QC00|5|5|0.20",
                "SOIL|2025秋|P1002|东片二号地|QC00||4|0.20",
                "SOIL|2025秋|P1003|南片三号地|QC01|3||");
        BatchOutcome b = run(null, lines, new FakeBook()).firstBatch();
        assertEquals(1, b.getRows().size(), "只到一个数的两行都不进门");
        assertEquals(1, b.getTotalRows());
        long half = b.getMarks().stream().filter(m -> FlowIntakeEngine.MARK_HALF_NUM.equals(m.getMarkKind())).count();
        assertEquals(2, half);
        assertTrue(b.getMarks().stream().anyMatch(m -> m.getDetail().contains("本行送检样品数")));
        assertTrue(b.getMarks().stream().anyMatch(m -> m.getDetail().contains("本行配对达标数")));
    }

    @Test
    void 切不开的行_标注_不按住整拨() {
        List<String> lines = Arrays.asList(
                "SOIL|2025秋|P1001|东片一号地|QC00|5|5|0.20",
                "SOIL|2025秋|P1001|只切出几段");
        BatchOutcome b = run(null, lines, new FakeBook()).firstBatch();
        assertEquals(1, b.getRows().size());
        assertEquals(FlowIntakeEngine.MARK_SPLIT, b.getMarks().get(0).getMarkKind());
        assertEquals(Integer.valueOf(2), b.getMarks().get(0).getRowNo(), "标注点到报文行次");
    }

    @Test
    void 数栏不成数_标注切不开() {
        Object out = FlowIntakeEngine.parseLine(
                "SOIL|2025秋|P1001|东片一号地|QC00|五个|5|0.20", delim, 1);
        assertTrue(out instanceof RowMark);
        assertEquals(FlowIntakeEngine.MARK_SPLIT, ((RowMark) out).getMarkKind());
    }

    @Test
    void 换页符混进来_整段归编码标注() {
        Object out = FlowIntakeEngine.parseLine(
                "SOIL|2025秋|P1001|东片一号地|QC00|5|5|0.20", delim, 3);
        assertTrue(out instanceof RowMark);
        assertEquals(FlowIntakeEngine.MARK_ENCODING, ((RowMark) out).getMarkKind());
    }

    @Test
    void 全宽数字混用_整段归编码标注() {
        Object out = FlowIntakeEngine.parseLine(
                "SOIL|2025秋|P1001|东片一号地|QC00|５|5|0.20", delim, 1);
        assertTrue(out instanceof RowMark);
        assertEquals(FlowIntakeEngine.MARK_ENCODING, ((RowMark) out).getMarkKind());
    }

    @Test
    void 全行带不上编码_头一拨算空拨_标注仍在() {
        List<String> lines = Arrays.asList(
                "SOIL|2025秋|P1001|东片一号地|QC00|5|5|0.20",
                "切不成行的一段");
        BatchOutcome b = run(null, lines, new FakeBook()).firstBatch();
        assertTrue(b.isEmpty());
        assertEquals("空拨", b.getReceiptText());
        assertEquals(2, b.getMarks().size());
    }

    @Test
    void 定宽切法_一拨一种_够宽才进门() {
        // 字段宽：4,8,8,16,8,4,4,8 合计60
        ParseSpec fixed = ParseSpec.fixed(new int[] {4, 8, 8, 16, 8, 4, 4, 8});
        String line = String.format("%-4s%-8s%-8s%-16s%-8s%-4s%-4s%-8s",
                "SOIL", "2025秋", "P1001", "东片一号地", "QC00", "5", "5", "0.20");
        Object ok = FlowIntakeEngine.parseLine(line, fixed, 1);
        assertTrue(ok instanceof ParsedRow);
        assertEquals("P1001", ((ParsedRow) ok).plotCode);

        Object tooShort = FlowIntakeEngine.parseLine("短一截", fixed, 2);
        assertTrue(tooShort instanceof RowMark);
        assertEquals(FlowIntakeEngine.MARK_SPLIT, ((RowMark) tooShort).getMarkKind());
    }

    // ============ 第四节：五条退回 + 告警线 + 底册 ============

    @Test
    void 五条退回逐行点名_镉高过当地告警线退() {
        FakeBook gate = new FakeBook();
        List<String> lines = Arrays.asList(
                "SOIL|2025秋||无题|QC00|5|5|0.20",
                "SOIL|2025秋|P1|东片|QC00|5|5|0.20",
                "SOIL|2025秋|P123456789012345678901234567890123|超长地块|QC00|5|5|0.20",
                "SOIL|2025秋|P1001|东片一号地|QC00|-2|5|0.20",
                "SOIL|2025秋|P1002|东片二号地|QC00|0|0|0.20",
                "SOIL|2025秋|P1003|南片三号地|QC01|5|5|0.450");
        BatchOutcome b = run(null, lines, gate).firstBatch();
        assertEquals(6, b.getRejectedRows());
        assertEquals(0, b.getAcceptedRows());
        assertEquals(FlowIntakeEngine.REJ_CODE_EMPTY, b.getRows().get(0).getRejectCode());
        assertEquals(FlowIntakeEngine.REJ_CODE_TOO_SHORT, b.getRows().get(1).getRejectCode());
        assertEquals(FlowIntakeEngine.REJ_CODE_TOO_LONG, b.getRows().get(2).getRejectCode());
        assertEquals(FlowIntakeEngine.REJ_QTY_NEGATIVE, b.getRows().get(3).getRejectCode());
        assertEquals(FlowIntakeEngine.REJ_QTY_ZERO, b.getRows().get(4).getRejectCode());
        assertEquals(FlowIntakeEngine.REJ_CD_OVER_WARN, b.getRows().get(5).getRejectCode());
        assertTrue(b.getRows().get(5).getRejectMsg().contains("告警线"));
        // 每条行套哪条理由，逐行追溯得出来
        for (RowDecision d : b.getRows()) {
            assertTrue(d.getRejectMsg() != null && !d.getRejectMsg().isEmpty());
        }
    }

    @Test
    void 镉刚好在线上_不退_高过才退() {
        FakeBook gate = new FakeBook();
        ParsedRow row = new ParsedRow();
        row.plotCode = "P1001";
        row.qty = new BigDecimal("5");
        row.chkNum = new BigDecimal("5");
        row.cdValue = new BigDecimal("0.400");
        assertNull(FlowIntakeEngine.rejectReason(row, gate.lookup("P1001"), gate));
        row.cdValue = new BigDecimal("0.401");
        assertEquals(FlowIntakeEngine.REJ_CD_OVER_WARN,
                FlowIntakeEngine.rejectReason(row, gate.lookup("P1001"), gate));
    }

    @Test
    void 当地没立告警线_不拿无中生出的线退人() {
        FakeBook gate = new FakeBook();
        gate.warn = null;
        List<String> lines = Arrays.asList(
                "SOIL|2025秋|P1001|东片一号地|QC00|5|5|9.999");
        BatchOutcome b = run(null, lines, gate).firstBatch();
        assertEquals(1, b.getAcceptedRows());
    }

    @Test
    void 码像样但底册查无_去处空悬未勾_进待核_不计三栏_系统不收() {
        List<String> lines = Arrays.asList(
                "SOIL|2025秋|P9999|查无此地|QC00|5|5|0.20",
                "SOIL|2025秋|P1001|东片一号地|QC00|5|5|0.20");
        BatchOutcome b = run(null, lines, new FakeBook()).firstBatch();
        assertEquals(FlowIntakeEngine.STATUS_UNCHECKED, b.getRows().get(0).getStatus());
        assertTrue(b.getRows().get(0).getPendingNote().contains("底册查无"));
        assertEquals(1, b.getUncheckedRows());
        // 三栏只轧收/退：进门栏=1，未勾那行不混进去，收+退=进门
        assertEquals(1, b.getTotalRows());
        assertEquals(1, b.getAcceptedRows());
        assertEquals(0, b.getRejectedRows());
        assertEquals(b.getAcceptedRows() + b.getRejectedRows(), b.getTotalRows());
    }

    @Test
    void 题名近似不算_只认码() {
        // 码对、题名乱填：仍按码定去处（收下），题名另列比对不合
        List<String> lines = Arrays.asList(
                "SOIL|2025秋|P1001|听起来差不多的另一块地|QC00|5|5|0.20");
        BatchOutcome b = run(null, lines, new FakeBook()).firstBatch();
        assertEquals(FlowIntakeEngine.STATUS_ACCEPTED, b.getRows().get(0).getStatus());
        long mismatch = b.getMarks().stream()
                .filter(m -> FlowIntakeEngine.MARK_TITLE_MISMATCH.equals(m.getMarkKind())).count();
        assertEquals(1, mismatch);
        // 行面题名以底册带出的为准
        assertEquals("东片一号地", b.getRows().get(0).getBookTitle());
    }

    @Test
    void 行面分区以底册为准_报文那串只用来比对() {
        List<String> lines = Arrays.asList(
                "SOIL|2025秋|P1001|东片一号地|QC99|5|5|0.20");
        BatchOutcome b = run(null, lines, new FakeBook()).firstBatch();
        assertEquals("QC00", b.getRows().get(0).getBookZoneNo());
        assertEquals("QC99", b.getRows().get(0).getMsgZoneNo());
        assertTrue(b.getMarks().stream().anyMatch(m -> m.getDetail().contains("QC99")));
    }

    @Test
    void 三栏同一次点算_收加退等于进门_个别退回不倒整拨() {
        List<String> lines = Arrays.asList(
                "SOIL|2025秋|P1001|东片一号地|QC00|5|5|0.20",
                "SOIL|2025秋|P1002|东片二号地|QC00|0|0|0.20",
                "SOIL|2025秋|P1003|南片三号地|QC01|2|2|0.30");
        BatchOutcome b = run(null, lines, new FakeBook()).firstBatch();
        assertEquals(3, b.getTotalRows());
        assertEquals(2, b.getAcceptedRows());
        assertEquals(1, b.getRejectedRows());
        assertEquals(b.getAcceptedRows() + b.getRejectedRows(), b.getTotalRows());
        assertFalse(b.isEmpty());
        assertTrue(b.getReceiptText().startsWith("进门3行"));
    }
}
