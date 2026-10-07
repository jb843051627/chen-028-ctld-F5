package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.ISqlSegment;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.exception.ctld.CaseFlowException;
import com.fc.v2.mapper.auto.TCtldTaskCalendarOffMapper;
import com.fc.v2.mapper.auto.TCtldTaskDedupMapper;
import com.fc.v2.mapper.auto.TCtldTaskItemMapper;
import com.fc.v2.mapper.auto.TCtldTaskRoundMapper;
import com.fc.v2.model.auto.TCtldTaskCalendarOff;
import com.fc.v2.model.auto.TCtldTaskDedup;
import com.fc.v2.model.auto.TCtldTaskItem;
import com.fc.v2.model.auto.TCtldTaskRound;
import com.fc.v2.service.ctld.TaskDispatchEngine;
import com.fc.v2.service.ctld.TaskDispatchResult;
import com.fc.v2.service.ctld.TaskInnerPort;
import com.fc.v2.service.ctld.TaskSmsPort;

/**
 * 翻单服务层规矩：闸门外不写库、趟账与两处记号同事务、同趟号第二回照头一趟（幂等）、
 * 短信不通回落站内、两条都不通记发不成且次日搁回再试、缺件按住不动行、三数只信趟账。
 * 库层全部 mock 成内存态，事务立刻提交。
 */
class TCtldTaskDispatchServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private TCtldTaskItemMapper itemMapper;
    private TCtldTaskDedupMapper dedupMapper;
    private TCtldTaskRoundMapper roundMapper;
    private TCtldTaskCalendarOffMapper calendarOffMapper;
    private TaskSmsPort smsPort;
    private TaskInnerPort innerPort;
    private TCtldTaskDispatchServiceImpl svc;

    private final List<TCtldTaskItem> itemStore = new ArrayList<>();
    private final List<TCtldTaskDedup> dedupStore = new ArrayList<>();
    private final List<TCtldTaskRound> roundStore = new ArrayList<>();
    private final List<TCtldTaskCalendarOff> offStore = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(8000);

    private static Date at(int y, int m, int d, int hh, int mm) {
        return Date.from(LocalDateTime.of(y, m, d, hh, mm).atZone(ZONE).toInstant());
    }

    private static Date day(int y, int m, int d) {
        return Date.from(LocalDate.of(y, m, d).atStartOfDay(ZONE).toInstant());
    }

    private TCtldTaskItem item(long id, String plot, String measure, Date due, String crop,
                               String post, int status) {
        TCtldTaskItem it = new TCtldTaskItem();
        it.setId(id);
        it.setItemNo("T" + id);
        it.setPlotCode(plot);
        it.setMeasureName(measure);
        it.setDueAt(due);
        it.setCropName(crop);
        it.setHandlerPost(post);
        it.setStatus(status);
        it.setDelFlag(0);
        it.setContent("记要" + id);
        return it;
    }

    @BeforeEach
    void setUp() {
        itemMapper = mock(TCtldTaskItemMapper.class);
        dedupMapper = mock(TCtldTaskDedupMapper.class);
        roundMapper = mock(TCtldTaskRoundMapper.class);
        calendarOffMapper = mock(TCtldTaskCalendarOffMapper.class);
        smsPort = mock(TaskSmsPort.class);
        innerPort = mock(TaskInnerPort.class);

        PlatformTransactionManager txm = mock(PlatformTransactionManager.class);
        when(txm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        svc = new TCtldTaskDispatchServiceImpl(txm);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "itemMapper", itemMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "dedupMapper", dedupMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "roundMapper", roundMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "calendarOffMapper", calendarOffMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "smsPort", smsPort);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "innerPort", innerPort);

        when(itemMapper.selectList(any())).thenAnswer(inv -> apply(inv.getArgument(0), itemStore));
        doAnswer(inv -> {
            TCtldTaskItem it = inv.getArgument(0);
            if (it.getId() == null) {
                it.setId(ids.incrementAndGet());
            }
            for (int i = 0; i < itemStore.size(); i++) {
                if (it.getId().equals(itemStore.get(i).getId())) {
                    itemStore.set(i, it);
                    return 1;
                }
            }
            itemStore.add(it);
            return 1;
        }).when(itemMapper).updateById(any());

        doAnswer(inv -> {
            TCtldTaskDedup d = inv.getArgument(0);
            if (d.getId() == null) {
                d.setId(ids.incrementAndGet());
            }
            dedupStore.add(d);
            return 1;
        }).when(dedupMapper).insert(any());
        when(dedupMapper.selectCount(any())).thenAnswer(inv ->
                Integer.valueOf(apply(inv.getArgument(0), dedupStore).size()));
        when(dedupMapper.selectList(any())).thenAnswer(inv -> apply(inv.getArgument(0), dedupStore));

        doAnswer(inv -> {
            TCtldTaskRound r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(ids.incrementAndGet());
            }
            roundStore.add(r);
            return 1;
        }).when(roundMapper).insert(any());
        when(roundMapper.selectOne(any())).thenAnswer(inv -> {
            List<TCtldTaskRound> hit = apply(inv.getArgument(0), roundStore);
            return hit.isEmpty() ? null : hit.get(0);
        });

        when(calendarOffMapper.selectList(any())).thenAnswer(inv -> apply(inv.getArgument(0), offStore));
    }

    /** 按 QueryWrapper normal 段等值条件做内存过滤（排序/limit 忽略），与既有服务测试同一套做法 */
    private <T> List<T> apply(Wrapper<T> w, List<T> all) {
        List<T> out = new ArrayList<>(all);
        QueryWrapper<T> qw = (QueryWrapper<T>) w;
        List<ISqlSegment> normals = new ArrayList<>(qw.getExpression().getNormal());
        java.util.Map<String, Object> params = qw.getParamNameValuePairs();
        String col = null;
        for (ISqlSegment seg : normals) {
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
        return out;
    }

    private Object fieldValue(Object o, String column) {
        try {
            StringBuilder sb = new StringBuilder();
            boolean up = false;
            for (char ch : column.toCharArray()) {
                if (ch == '_') {
                    up = true;
                } else {
                    sb.append(up ? Character.toUpperCase(ch) : ch);
                    up = false;
                }
            }
            java.lang.reflect.Field f = o.getClass().getDeclaredField(sb.toString());
            f.setAccessible(true);
            return f.get(o);
        } catch (Exception e) {
            return null;
        }
    }

    @Test
    void 闸门外不读不发不落趟账_固定本轮未动单() {
        itemStore.add(item(1, "P1", "甲", day(2026, 10, 7), "水稻", "农办岗", 0));
        TaskDispatchResult r = svc.runEarlyShift("R1", at(2026, 10, 7, 23, 0));
        assertFalse(r.isGateOpen());
        assertEquals(0, r.getPickedRows());
        assertEquals("本轮未动单", r.getReplyText());
        verify(roundMapper, never()).insert(any());
        verify(itemMapper, never()).selectList(any());
        verify(smsPort, never()).send(any());
    }

    @Test
    void 窗口内同刻两趟号各跑_同一趟号第二回照头一趟_不重发() {
        itemStore.add(item(1, "P1", "甲", day(2026, 10, 7), "水稻", "农办岗", 0));
        TaskDispatchResult first = svc.runEarlyShift("RDUP", at(2026, 10, 7, 8, 0));
        assertTrue(first.isGateOpen());
        assertEquals(1, first.getSentRows());
        assertEquals(1, first.getPickedRows());
        assertEquals(0, first.getFailedRows());

        TaskDispatchResult again = svc.runEarlyShift("RDUP", at(2026, 10, 7, 8, 5));
        assertTrue(again.isReplayed(), "同趟号第二回照面");
        assertEquals(1, again.getSentRows());
        verify(smsPort, org.mockito.Mockito.times(1)).send(any());
        assertEquals(1, roundStore.size(), "不另落一趟");
    }

    @Test
    void 办成一行两处记号_情形已发出_记要补时刻渠道_去重账落键() {
        itemStore.add(item(1, "P1001", "喷施钝化剂", day(2026, 10, 7), "水稻", "北浚镇农办岗", 0));
        TaskDispatchResult r = svc.runEarlyShift("R2", at(2026, 10, 7, 7, 0));
        assertEquals(1, r.getSentRows());
        TCtldTaskItem saved = itemStore.get(0);
        assertEquals(TaskDispatchEngine.STATUS_SENT, saved.getStatus());
        assertEquals(TaskDispatchEngine.CHANNEL_SMS, saved.getChannel());
        assertNull(saved.getFailReason());
        assertTrue(saved.getContent().contains("经短信发出"), "记要补渠道");
        assertTrue(saved.getContent().contains("2026-10-07 07:00"));
        assertEquals(1, dedupStore.size());
        assertEquals("P1001||喷施钝化剂", dedupStore.get(0).getDedupKey());
        assertEquals(1, roundStore.get(0).getSentRows());
    }

    @Test
    void 短信不通回落站内_渠道记站内_记要带回落原由() {
        org.mockito.Mockito.doThrow(new TaskDispatchEngine.ChannelException("短信网关超时"))
                .when(smsPort).send(any());
        itemStore.add(item(1, "P1", "甲", day(2026, 10, 7), "水稻", "农办岗", 0));
        TaskDispatchResult r = svc.runEarlyShift("R3", at(2026, 10, 7, 8, 0));
        assertEquals(1, r.getSentRows());
        assertEquals(1, r.getInnerRows());
        assertEquals(TaskDispatchEngine.CHANNEL_INNER, itemStore.get(0).getChannel());
        assertTrue(itemStore.get(0).getContent().contains("短信网关超时"));
        verify(innerPort).send(any());
        assertEquals(1, dedupStore.size(), "回落站内也算发出，占去重名额");
    }

    @Test
    void 两条都不通记发不成_不占去重_当日不重抛_次日搁回再试() {
        org.mockito.Mockito.doThrow(new TaskDispatchEngine.ChannelException("短信网关超时"))
                .when(smsPort).send(any());
        org.mockito.Mockito.doThrow(new TaskDispatchEngine.ChannelException("站内服务不可用"))
                .when(innerPort).send(any());
        itemStore.add(item(1, "P1", "甲", day(2026, 10, 7), "水稻", "农办岗", 0));

        TaskDispatchResult r1 = svc.runEarlyShift("F1", at(2026, 10, 7, 8, 0));
        assertEquals(1, r1.getFailedRows());
        assertEquals(0, r1.getSentRows());
        assertEquals(TaskDispatchEngine.STATUS_FAILED, itemStore.get(0).getStatus());
        assertTrue(itemStore.get(0).getFailReason().contains("短信"));
        assertTrue(itemStore.get(0).getFailReason().contains("站内"));
        assertEquals(0, dedupStore.size(), "发不成不占去重名额");

        // 当日另起一趟：不重抛
        TaskDispatchResult r1again = svc.runEarlyShift("F2", at(2026, 10, 7, 8, 30));
        assertEquals(0, r1again.getPickedRows());
        verify(smsPort, org.mockito.Mockito.times(1)).send(any());

        // 次日：搁回再试（此时让短信恢复，不再抛）
        org.mockito.Mockito.reset(smsPort);
        TaskDispatchResult r2 = svc.runEarlyShift("F3", at(2026, 10, 8, 8, 0));
        assertEquals(1, r2.getSentRows());
        assertEquals(TaskDispatchEngine.STATUS_SENT, itemStore.get(0).getStatus());
        verify(smsPort).send(any());
    }

    @Test
    void 文面缺件本行按住_不动行_缺项照原样吐出() {
        itemStore.add(item(1, "P1", "甲", day(2026, 10, 7), "  ", "农办岗", 0));
        TaskDispatchResult r = svc.runEarlyShift("H1", at(2026, 10, 7, 8, 0));
        assertEquals(1, r.getPickedRows());
        assertEquals(1, r.getHeldRows());
        assertEquals(0, r.getSentRows());
        assertEquals(TaskDispatchEngine.STATUS_PENDING, itemStore.get(0).getStatus(), "无人代填，原样搁着");
        TaskDispatchResult.RowReceipt rc = r.getRows().get(0);
        assertEquals("HELD", rc.getKind());
        assertTrue(rc.getMissingFields().contains(TaskDispatchEngine.FIELD_CROP));
        verify(smsPort, never()).send(any());
    }

    @Test
    void 一行挑不出_趟账照落三数0_不去够叠头一条() {
        // 有行但届期在十天后、且不提前
        itemStore.add(item(1, "P1", "甲", day(2026, 10, 17), "水稻", "农办岗", 0));
        TaskDispatchResult r = svc.runEarlyShift("E0", at(2026, 10, 7, 8, 0));
        assertEquals(0, r.getPickedRows());
        assertEquals(0, r.getSentRows());
        assertEquals(0, r.getFailedRows());
        assertNotNull(r.getRoundId());
        assertEquals(1, roundStore.size());
        verify(smsPort, never()).send(any());
    }

    @Test
    void 同地块同桩事由当日第二趟被去重挡下_不同事由各发() {
        itemStore.add(item(1, "P1", "喷药", day(2026, 10, 7), "水稻", "农办岗", 0));
        svc.runEarlyShift("D1", at(2026, 10, 7, 7, 0));
        // 库里新搁进同键一行（模拟补录），同日再跑
        itemStore.add(item(2, "P1", "喷药", day(2026, 10, 7), "水稻", "农办岗", 0));
        itemStore.add(item(3, "P1", "灌水", day(2026, 10, 7), "水稻", "农办岗", 0));
        TaskDispatchResult r = svc.runEarlyShift("D2", at(2026, 10, 7, 8, 0));
        assertEquals(1, r.getSentRows(), "只有不同事由那行发出");
        assertEquals(1, r.getDedupSkippedRows());
        assertEquals(2, dedupStore.size());
    }

    @Test
    void 趟号复述只信趟账_查无趟号报错() {
        itemStore.add(item(1, "P1", "甲", day(2026, 10, 7), "水稻", "农办岗", 0));
        svc.runEarlyShift("Q1", at(2026, 10, 7, 8, 0));
        TaskDispatchResult replay = svc.replay("Q1");
        assertTrue(replay.isReplayed());
        assertEquals(1, replay.getSentRows());
        assertThrows(CaseFlowException.class, () -> svc.replay("NOPE"));
    }

    @Test
    void 老条目与今年新条目同一条通道一套筛法() {
        TCtldTaskItem old = item(1, "P-OLD", "甲", day(2020, 5, 1), "水稻", "农办岗", 0);
        old.setCreateTime(day(2020, 5, 1));
        TCtldTaskItem fresh = item(2, "P-NEW", "乙", day(2026, 10, 7), "小麦", "农办岗", 0);
        fresh.setCreateTime(day(2026, 10, 1));
        itemStore.addAll(Arrays.asList(old, fresh));
        TaskDispatchResult r = svc.runEarlyShift("OLD", at(2026, 10, 7, 8, 0));
        assertEquals(2, r.getSentRows(), "老条目不因存得早另开一门算法");
    }
}
