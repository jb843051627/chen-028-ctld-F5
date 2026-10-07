package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

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
import com.fc.v2.service.ctld.FlowIntakeResult;
import com.fc.v2.service.ctld.ITCtldFlowIntakeService;

/**
 * 服务层规矩：收拨与流水同事务联动、同拨并着进两回只认头一拨（幂等）、
 * 已收的不被整拨倒回未勾、行不漂拨、待核只定悬行、回核印证三栏同源。
 * 库层全部 mock 成内存态，事务立刻提交。
 */
class TCtldFlowIntakeServiceImplTest {

    private TCtldFlowBatchMapper batchMapper;
    private TCtldFlowRowMapper rowMapper;
    private TCtldFlowRowMarkMapper markMapper;
    private TCtldPlotMapper plotMapper;
    private TCtldLimitLineMapper limitLineMapper;
    private TCtldFlowIntakeServiceImpl svc;

    private final AtomicLong batchIds = new AtomicLong(5000);
    private final AtomicLong rowIds = new AtomicLong(7000);
    private final AtomicLong markIds = new AtomicLong(9000);
    private final List<TCtldFlowBatch> batchStore = new ArrayList<>();
    private final List<TCtldFlowRow> rowStore = new ArrayList<>();
    private final List<TCtldFlowRowMark> markStore = new ArrayList<>();

    private ParseCall capture;

    private interface ParseCall {
        FlowIntakeResult run(FlowIntakeCmd cmd);
    }

    @BeforeEach
    void setUp() {
        batchMapper = mock(TCtldFlowBatchMapper.class);
        rowMapper = mock(TCtldFlowRowMapper.class);
        markMapper = mock(TCtldFlowRowMarkMapper.class);
        plotMapper = mock(TCtldPlotMapper.class);
        limitLineMapper = mock(TCtldLimitLineMapper.class);

        PlatformTransactionManager txm = mock(PlatformTransactionManager.class);
        when(txm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        svc = new TCtldFlowIntakeServiceImpl(txm);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "batchMapper", batchMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "rowMapper", rowMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "markMapper", markMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "plotMapper", plotMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "limitLineMapper", limitLineMapper);
        capture = cmd -> svc.intake(cmd);

        doAnswer((InvocationOnMock inv) -> {
            TCtldFlowBatch b = inv.getArgument(0);
            if (b.getId() == null) {
                b.setId(batchIds.incrementAndGet());
            }
            batchStore.add(b);
            return 1;
        }).when(batchMapper).insert(any());
        when(batchMapper.selectOne(any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            com.baomidou.mybatisplus.core.conditions.Wrapper<TCtldFlowBatch> w = inv.getArgument(0);
            List<TCtldFlowBatch> hit = applyWrapper(w, batchStore, TCtldFlowBatch.class, true);
            return hit.isEmpty() ? null : hit.get(0);
        });
        when(batchMapper.selectList(any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            com.baomidou.mybatisplus.core.conditions.Wrapper<TCtldFlowBatch> w = inv.getArgument(0);
            return applyWrapper(w, batchStore, TCtldFlowBatch.class, false);
        });
        when(batchMapper.selectById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return batchStore.stream().filter(b -> id.equals(b.getId())).findFirst().orElse(null);
        });

        doAnswer((InvocationOnMock inv) -> {
            TCtldFlowRow r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(rowIds.incrementAndGet());
            }
            rowStore.add(r);
            return 1;
        }).when(rowMapper).insert(any());
        when(rowMapper.selectOne(any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            com.baomidou.mybatisplus.core.conditions.Wrapper<TCtldFlowRow> w = inv.getArgument(0);
            List<TCtldFlowRow> hit = applyWrapper(w, rowStore, TCtldFlowRow.class, true);
            return hit.isEmpty() ? null : hit.get(0);
        });
        when(rowMapper.selectList(any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            com.baomidou.mybatisplus.core.conditions.Wrapper<TCtldFlowRow> w = inv.getArgument(0);
            return applyWrapper(w, rowStore, TCtldFlowRow.class, false);
        });
        when(rowMapper.selectById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return rowStore.stream().filter(r -> id.equals(r.getId())).findFirst().orElse(null);
        });
        when(rowMapper.updateById(any())).thenAnswer(inv -> {
            TCtldFlowRow nr = inv.getArgument(0);
            for (int i = 0; i < rowStore.size(); i++) {
                if (nr.getId().equals(rowStore.get(i).getId())) {
                    rowStore.set(i, nr);
                    return 1;
                }
            }
            return 0;
        });

        doAnswer((InvocationOnMock inv) -> {
            TCtldFlowRowMark m = inv.getArgument(0);
            if (m.getId() == null) {
                m.setId(markIds.incrementAndGet());
            }
            markStore.add(m);
            return 1;
        }).when(markMapper).insert(any());
        when(markMapper.selectList(any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            com.baomidou.mybatisplus.core.conditions.Wrapper<TCtldFlowRowMark> w = inv.getArgument(0);
            return applyWrapper(w, markStore, TCtldFlowRowMark.class, false);
        });

        // 底册：P1001/P1002 在册
        Map<String, TCtldPlot> plots = new HashMap<>();
        plots.put("P1001", plot("P1001", "东片一号地", "QC00"));
        plots.put("P1002", plot("P1002", "东片二号地", "QC00"));
        when(plotMapper.selectOne(any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            com.baomidou.mybatisplus.core.conditions.Wrapper<TCtldPlot> w = inv.getArgument(0);
            List<TCtldPlot> hit = applyWrapper(w, new ArrayList<>(plots.values()), TCtldPlot.class, true);
            return hit.isEmpty() ? null : hit.get(0);
        });

        // 告警线 0.400 在位
        TCtldLimitLine line = new TCtldLimitLine();
        line.setTh2Max(new BigDecimal("0.400"));
        line.setPriority(1);
        line.setStatus(0);
        when(limitLineMapper.selectList(any())).thenReturn(Arrays.asList(line));
    }

    private TCtldPlot plot(String no, String title, String zone) {
        TCtldPlot p = new TCtldPlot();
        p.setPlotNo(no);
        p.setContent(title);
        p.setZoneNo(zone);
        p.setDelFlag(0);
        return p;
    }

    /** 按 QueryWrapper normal 段里的等值条件（列 = #{ew.paramNameValuePairs.MPGENVALn}）做内存过滤；
     *  排序/limit 忽略（selectOne 处自行取头一条）。 */
    private <T> List<T> applyWrapper(com.baomidou.mybatisplus.core.conditions.Wrapper<T> w, List<T> all,
                                     Class<T> clazz, boolean one) {
        List<T> out = new ArrayList<>(all);
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<T> qw =
                (com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<T>) w;
        List<com.baomidou.mybatisplus.core.conditions.ISqlSegment> normals =
                new ArrayList<>(qw.getExpression().getNormal());
        Map<String, Object> params = qw.getParamNameValuePairs();
        String col = null;
        for (com.baomidou.mybatisplus.core.conditions.ISqlSegment seg : normals) {
            String sql = seg.getSqlSegment();
            if ("AND".equalsIgnoreCase(sql) || "OR".equalsIgnoreCase(sql)) {
                col = null;
                continue;
            }
            if ("=".equals(sql)) {
                continue;
            }
            if (sql.contains("paramNameValuePairs.")) {
                String key = sql.substring(sql.lastIndexOf('.') + 1).replaceAll("[^A-Za-z0-9_]", "");
                Object want = params.get(key);
                final String column = col;
                if (column != null) {
                    out.removeIf(x -> {
                        Object got = fieldValue(x, column);
                        return got == null || !String.valueOf(got).equals(String.valueOf(want));
                    });
                }
                col = null;
            } else if (!sql.equalsIgnoreCase("IS") && !sql.equalsIgnoreCase("NOT")
                    && !sql.equalsIgnoreCase("NULL")) {
                col = sql;
            }
        }
        if (one && out.size() > 1) {
            return new ArrayList<>(Arrays.asList(out.get(0)));
        }
        return out;
    }

    private Object fieldValue(Object o, String column) {
        try {
            String field = toCamel(column);
            java.lang.reflect.Field f = null;
            Class<?> c = o.getClass();
            while (c != null && f == null) {
                try {
                    f = c.getDeclaredField(field);
                } catch (NoSuchFieldException e) {
                    c = c.getSuperclass();
                }
            }
            if (f == null) {
                return null;
            }
            f.setAccessible(true);
            return f.get(o);
        } catch (Exception e) {
            return null;
        }
    }

    private String toCamel(String col) {
        StringBuilder sb = new StringBuilder();
        boolean up = false;
        for (char ch : col.toCharArray()) {
            if (ch == '_') {
                up = true;
            } else {
                sb.append(up ? Character.toUpperCase(ch) : ch);
                up = false;
            }
        }
        return sb.toString();
    }

    private FlowIntakeCmd cmd(String batchNo, int period, String cap, List<String> lines) {
        FlowIntakeCmd c = new FlowIntakeCmd();
        c.setBatchNo(batchNo);
        c.setPeriodNo(period);
        c.setDeclaredCap(cap);
        c.setParseKind(FlowIntakeEngine.PARSE_DELIM);
        c.setDelimiter("|");
        c.setPutKind("镇级补报");
        c.setRawLines(lines);
        return c;
    }

    private List<String> sampleLines() {
        return Arrays.asList(
                "SOIL|2025秋|P1001|东片一号地|QC00|5|5|0.20",
                "SOIL|2025秋|P1002|东片二号地|QC00|0|0|0.20",
                "PRODUCT|2025秋|P1001|东片一号地|QC00|5|4|");
    }

    @Test
    void 整拨落账_拨账行标注同事务联动_三栏与逐行同源() {
        FlowIntakeResult r = capture.run(cmd("B-1", FlowIntakeEngine.PERIOD_TOWN, null, sampleLines()));
        assertEquals(1, r.getBatches().size());
        FlowIntakeResult.BatchReceipt rc = r.firstBatch();
        assertEquals(3, rc.getTotalRows());
        assertEquals(2, rc.getAcceptedRows());
        assertEquals(1, rc.getRejectedRows());
        assertEquals(rc.getAcceptedRows() + rc.getRejectedRows(), rc.getTotalRows());
        // 联动：一拨、三行同落
        assertEquals(1, batchStore.size());
        assertEquals(3, rowStore.size());
        // 退回行逐行追溯得到条款
        TCtldFlowRow rejected = rowStore.stream()
                .filter(x -> x.getStatus() == FlowIntakeEngine.STATUS_REJECTED).findFirst().get();
        assertEquals(FlowIntakeEngine.REJ_QTY_ZERO, rejected.getRejectCode());
        // 拨头记下的就是引擎当场那份，不是另起一回算的
        assertEquals(rc.getTotalRows(), batchStore.get(0).getTotalRows());
        assertEquals(rc.getAcceptedRows(), batchStore.get(0).getAcceptedRows());
    }

    @Test
    void 空拨只落空拨_三栏0_不出收讫() {
        FlowIntakeResult r = capture.run(cmd("B-0", FlowIntakeEngine.PERIOD_TOWN, "10", new ArrayList<>()));
        assertTrue(r.firstBatch().isEmpty());
        assertEquals("空拨", r.firstBatch().getReceiptText());
        assertEquals(0, r.firstBatch().getTotalRows());
        assertEquals(1, batchStore.size());
        assertEquals(0, rowStore.size());
    }

    @Test
    void 同一拨并着进两回_只认头一拨_照回话不落行不另立拨() {
        FlowIntakeResult first = capture.run(cmd("B-DUP", FlowIntakeEngine.PERIOD_TOWN, null, sampleLines()));
        int rowsAfterFirst = rowStore.size();
        int batchesAfterFirst = batchStore.size();

        FlowIntakeResult again = capture.run(cmd("B-DUP", FlowIntakeEngine.PERIOD_TOWN, null,
                Arrays.asList("SOIL|2025秋|P1001|东片一号地|QC00|9|9|0.01")));
        assertTrue(again.isReplayed(), "第二遍只能照面");
        assertEquals(3, again.firstBatch().getTotalRows(), "照头一拨的三栏回话");
        assertEquals(rowsAfterFirst, rowStore.size(), "不再落一行");
        assertEquals(batchesAfterFirst, batchStore.size(), "不另立一拨");
        // 头一遍落定的行没被翻回未勾
        assertFalse(rowStore.stream().anyMatch(x -> x.getStatus() == FlowIntakeEngine.STATUS_UNCHECKED
                && "P1002".equals(x.getItemCode())));
        // 回执仍与头一份一致
        assertEquals(first.firstBatch().getReceiptText(), again.firstBatch().getReceiptText());
        verify(batchMapper, times(1)).insert(any());
    }

    @Test
    void 两期各留一份_镇级与县局并起来期数对得平() {
        capture.run(cmd("B-P", 1, null, sampleLines()));
        FlowIntakeResult county = capture.run(cmd("B-P", 2, null, sampleLines()));
        assertFalse(county.isReplayed(), "县局复核是另一期，不是重拨，各自留账");
        assertEquals(2, batchStore.size());
        assertEquals(6, rowStore.size());
    }

    @Test
    void 到顶接成两拨_行不漂拨() {
        FlowIntakeResult r = capture.run(cmd("B-CAP", 1, "2", sampleLines()));
        assertEquals(2, r.getBatches().size());
        List<Integer> seqs = new ArrayList<>();
        for (TCtldFlowRow x : rowStore) {
            seqs.add(x.getRecvSeq());
            assertEquals("B-CAP", x.getBatchNo());
        }
        assertTrue(seqs.contains(1) && seqs.contains(2));
    }

    @Test
    void 个别行退回_已收的行不被倒回未勾() {
        capture.run(cmd("B-RB", 1, null, sampleLines()));
        // 再以同批次第二回投递，不得改动头一遍任何行
        capture.run(cmd("B-RB", 1, null, Arrays.asList(
                "SOIL|2025秋|P1001|东片一号地|QC00|1|1|9.99")));
        long acceptedKept = rowStore.stream()
                .filter(x -> "P1001".equals(x.getItemCode())
                        && x.getStatus() == FlowIntakeEngine.STATUS_ACCEPTED).count();
        assertEquals(2, acceptedKept, "头一拨收下的行一条不倒回");
    }

    @Test
    void 待核清单只装未勾_回核逐行定夺_系统不替它收() {
        capture.run(cmd("B-U", 1, null, Arrays.asList(
                "SOIL|2025秋|P9999|查无此地|QC00|5|5|0.20",
                "SOIL|2025秋|P1001|东片一号地|QC00|5|5|0.20")));
        List<TCtldFlowRow> pending = svc.pendingList("B-U");
        assertEquals(1, pending.size());
        assertEquals("P9999", pending.get(0).getItemCode());
        assertEquals(FlowIntakeEngine.STATUS_UNCHECKED, pending.get(0).getStatus());

        // 回核定为收下：落定，但标记为事后定夺，不回写拨头三栏
        assertTrue(svc.settlePending(pending.get(0).getId(), FlowIntakeEngine.STATUS_ACCEPTED, null));
        assertEquals(FlowIntakeEngine.STATUS_ACCEPTED, pending.get(0).getStatus());
        assertEquals(Integer.valueOf(1), pending.get(0).getManualSettle());
        assertEquals(1, batchStore.get(0).getAcceptedRows(), "拨头三栏仍是当场那份，不被事后改动");

        // 第四种写法落不了库
        assertThrows(CaseFlowException.class,
                () -> svc.settlePending(pending.get(0).getId(), 9, null));
        // 已落定的行回核不许再翻
        assertThrows(CaseFlowException.class,
                () -> svc.settlePending(pending.get(0).getId(), FlowIntakeEngine.STATUS_REJECTED, "QTY_ZERO"));
    }

    @Test
    void 回核退回必须写明条款() {
        capture.run(cmd("B-U2", 1, null, Arrays.asList(
                "SOIL|2025秋|P8888|查无|QC00|5|5|0.20")));
        Long id = svc.pendingList("B-U2").get(0).getId();
        assertThrows(CaseFlowException.class,
                () -> svc.settlePending(id, FlowIntakeEngine.STATUS_REJECTED, null));
        assertTrue(svc.settlePending(id, FlowIntakeEngine.STATUS_REJECTED,
                FlowIntakeEngine.REJ_CD_OVER_WARN));
    }

    @Test
    void 抽行回核_去处与三栏相互印证() {
        capture.run(cmd("B-V", 1, null, sampleLines()));
        TCtldFlowRow one = rowStore.stream().filter(x -> "P1001".equals(x.getItemCode())).findFirst().get();
        ITCtldFlowIntakeService.RowVerify v = svc.verify(one.getId());
        assertTrue(v.isMatched(), v.getDetail());
        assertEquals(3, v.getBatchTotal());
        assertNotNull(v.getDetail());
    }

    @Test
    void 标注单递给回核_切不开与编码不进门不算三栏() {
        capture.run(cmd("B-M", 1, null, Arrays.asList(
                "SOIL|2025秋|P1001|东片一号地|QC00|5|5|0.20",
                "SOIL|2025秋|P1002|东片二号地|QC00||4|0.20",
                "SOIL|2025秋|P1001|东片一号地|QC00|５|5|0.20")));
        assertEquals(1, batchStore.get(0).getTotalRows(), "三栏只点进门那一行");
        List<TCtldFlowRowMark> marks = svc.markList("B-M");
        assertEquals(2, marks.size());
        assertTrue(marks.stream().anyMatch(m -> FlowIntakeEngine.MARK_HALF_NUM.equals(m.getMarkKind())));
        assertTrue(marks.stream().anyMatch(m -> FlowIntakeEngine.MARK_ENCODING.equals(m.getMarkKind())));
    }

    @Test
    void 批次码空着不受理() {
        assertThrows(CaseFlowException.class,
                () -> capture.run(cmd("  ", 1, null, sampleLines())));
    }
}
