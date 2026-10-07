package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.baomidou.mybatisplus.core.conditions.ISqlSegment;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TCtldTaskDispatchMapper;
import com.fc.v2.mapper.auto.TCtldTaskItemMapper;
import com.fc.v2.mapper.auto.TCtldTaskOffDayMapper;
import com.fc.v2.mapper.auto.TCtldTaskSendLogMapper;
import com.fc.v2.model.auto.TCtldTaskDispatch;
import com.fc.v2.model.auto.TCtldTaskItem;
import com.fc.v2.model.auto.TCtldTaskOffDay;
import com.fc.v2.model.auto.TCtldTaskSendLog;
import com.fc.v2.service.ctld.TaskDispatchEngine;
import com.fc.v2.service.ctld.TaskDispatchReply;
import com.fc.v2.service.ctld.notify.StationTaskMessageSender;
import com.fc.v2.service.ctld.notify.TaskSmsSender;

/**
 * 早班翻单服务层规矩（库层全 mock 成内存态，事务立刻提交）：
 * 闸外不查不落只回一句、空叠报 0、记号两处同事务落、短信回落站内、两条皆不通记发不成、
 * 同日第二回照头一趟回话、发不成次日再试、缺项按下无人代填、同键去重、500 上限。
 */
class TCtldTaskDispatchServiceImplTest {

    private TCtldTaskItemMapper itemMapper;
    private TCtldTaskOffDayMapper offDayMapper;
    private TCtldTaskDispatchMapper dispatchMapper;
    private TCtldTaskSendLogMapper sendLogMapper;
    private TaskSmsSender smsSender;
    private StationTaskMessageSender stationSender;
    private TCtldTaskDispatchServiceImpl svc;

    private final AtomicLong ids = new AtomicLong(8000);
    private final List<TCtldTaskItem> itemStore = new ArrayList<>();
    private final List<TCtldTaskDispatch> runStore = new ArrayList<>();
    private final List<TCtldTaskSendLog> logStore = new ArrayList<>();
    private final List<TCtldTaskOffDay> offStore = new ArrayList<>();

    private static Date at(int y, int m, int d, int hh, int mm) {
        return Date.from(LocalDate.of(y, m, d).atTime(hh, mm)
                .atZone(TaskDispatchEngine.ZONE).toInstant());
    }

    private static Date due(LocalDate d) {
        return Date.from(d.atStartOfDay(TaskDispatchEngine.ZONE).toInstant());
    }

    private TCtldTaskItem item(long id, String plot, String measure, String crop, String post,
                               LocalDate due, int status) {
        TCtldTaskItem r = new TCtldTaskItem();
        r.setId(id);
        r.setItemNo("T" + id);
        r.setPlotNo(plot);
        r.setMeasureName(measure);
        r.setMainCrop(crop);
        r.setAssigneePost(post);
        r.setDueAt(due(due));
        r.setStatus(status);
        r.setDelFlag(0);
        return r;
    }

    private TCtldTaskItem standard(long id, LocalDate due) {
        return item(id, "P" + id, "深翻耕", "水稻", "北浚镇农艺岗", due, TaskDispatchEngine.STATUS_WAIT);
    }

    @BeforeEach
    void setUp() {
        itemMapper = mock(TCtldTaskItemMapper.class);
        offDayMapper = mock(TCtldTaskOffDayMapper.class);
        dispatchMapper = mock(TCtldTaskDispatchMapper.class);
        sendLogMapper = mock(TCtldTaskSendLogMapper.class);
        smsSender = mock(TaskSmsSender.class);
        stationSender = mock(StationTaskMessageSender.class);

        PlatformTransactionManager txm = mock(PlatformTransactionManager.class);
        when(txm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        svc = new TCtldTaskDispatchServiceImpl(txm);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "itemMapper", itemMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "offDayMapper", offDayMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "dispatchMapper", dispatchMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "sendLogMapper", sendLogMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "smsSender", smsSender);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "stationSender", stationSender);

        when(itemMapper.selectList(any())).thenAnswer(inv -> filter(inv.getArgument(0), itemStore));
        when(itemMapper.selectById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return itemStore.stream().filter(x -> id.equals(x.getId())).findFirst().orElse(null);
        });
        when(itemMapper.updateById(any())).thenAnswer((InvocationOnMock inv) -> {
            TCtldTaskItem nr = inv.getArgument(0);
            for (int i = 0; i < itemStore.size(); i++) {
                if (nr.getId().equals(itemStore.get(i).getId())) {
                    itemStore.set(i, nr);
                    return 1;
                }
            }
            return 0;
        });
        when(offDayMapper.selectList(any())).thenAnswer(inv -> filter(inv.getArgument(0), offStore));
        when(dispatchMapper.selectOne(any())).thenAnswer(inv -> {
            List<TCtldTaskDispatch> hit = filter(inv.getArgument(0), runStore);
            return hit.isEmpty() ? null : hit.get(0);
        });
        // uk_run_date：同日第二份趟账插不进，逼服务走幂等回放
        doAnswer((InvocationOnMock inv) -> {
            TCtldTaskDispatch d = inv.getArgument(0);
            boolean dup = runStore.stream().anyMatch(x -> sameDay(x.getRunDate(), d.getRunDate()));
            if (dup) {
                throw new DuplicateKeyException("uk_run_date");
            }
            if (d.getId() == null) {
                d.setId(ids.incrementAndGet());
            }
            runStore.add(d);
            return 1;
        }).when(dispatchMapper).insert(any());
        when(dispatchMapper.updateById(any())).thenAnswer((InvocationOnMock inv) -> {
            TCtldTaskDispatch nr = inv.getArgument(0);
            for (int i = 0; i < runStore.size(); i++) {
                if (nr.getId().equals(runStore.get(i).getId())) {
                    runStore.set(i, nr);
                    return 1;
                }
            }
            return 0;
        });
        when(sendLogMapper.selectList(any())).thenAnswer(inv -> filter(inv.getArgument(0), logStore));
        doAnswer((InvocationOnMock inv) -> {
            TCtldTaskSendLog l = inv.getArgument(0);
            if (l.getId() == null) {
                l.setId(ids.incrementAndGet());
            }
            logStore.add(l);
            return 1;
        }).when(sendLogMapper).insert(any());

        when(smsSender.send(anyString())).thenReturn(TaskDispatchEngine.NotifyOutcome.ok());
        when(stationSender.send(anyString())).thenReturn(TaskDispatchEngine.NotifyOutcome.ok());
    }

    private static boolean sameDay(Date a, Date b) {
        return a != null && b != null && TaskDispatchEngine.dateOf(a).equals(TaskDispatchEngine.dateOf(b));
    }

    // ========================= 第 1 条 =========================

    @Test
    void 闸外只回一句_不查库不落库_回话逐字相同() {
        itemStore.add(standard(1, LocalDate.of(2026, 10, 8)));
        Date early = at(2026, 10, 8, 6, 29);
        TaskDispatchReply a = svc.runOnce(early);
        TaskDispatchReply b = svc.runOnce(early);
        assertFalse(a.isGateOpen());
        assertEquals("本轮未动单", a.getResultMsg());
        assertEquals("本轮未动单", b.getResultMsg());
        assertEquals(0, a.getPickedRows());
        verify(dispatchMapper, never()).insert(any());
        verify(sendLogMapper, never()).insert(any());
        verify(smsSender, never()).send(anyString());
    }

    // ========================= 第 9 条：空叠报 0 =========================

    @Test
    void 一行挑不出来老实报0_落趟账_不去够头一条() {
        TaskDispatchReply r = svc.runOnce(at(2026, 10, 8, 7, 0));
        assertTrue(r.isGateOpen());
        assertEquals(0, r.getPickedRows());
        assertEquals(0, r.getSentRows());
        assertEquals(0, r.getFailedRows());
        assertEquals(1, runStore.size());
        assertEquals(0, runStore.get(0).getSentRows());
        assertTrue(r.getResultMsg().contains("挑中0行"));
    }

    // ========================= 第 10 条：记号两处 =========================

    @Test
    void 办成一行_情形改已发出_记要补时刻与渠道_三数同源() {
        itemStore.add(standard(1, LocalDate.of(2026, 10, 8)));
        TaskDispatchReply r = svc.runOnce(at(2026, 10, 8, 7, 0));
        assertEquals(1, r.getPickedRows());
        assertEquals(1, r.getSentRows());
        assertEquals(0, r.getFailedRows());
        TCtldTaskItem after = itemStore.get(0);
        assertEquals(TaskDispatchEngine.STATUS_SENT, after.getStatus());
        assertNotNull(after.getContent());
        assertTrue(after.getContent().contains("走短信渠道发出"));
        assertTrue(after.getContent().contains("2026-10-08 07:00:00"));
        // 趟账三数与回话同一份
        TCtldTaskDispatch d = runStore.get(0);
        assertEquals(d.getPickedRows(), r.getPickedRows());
        assertEquals(d.getSentRows(), r.getSentRows());
        assertEquals(d.getFailedRows(), r.getFailedRows());
        assertEquals(TaskDispatchEngine.CHANNEL_SMS, logStore.get(0).getChannel());
    }

    // ========================= 第 7 条：回落与发不成 =========================

    @Test
    void 短信不通回落站内_原由写本行_仍算递出() {
        itemStore.add(standard(1, LocalDate.of(2026, 10, 8)));
        when(smsSender.send(anyString()))
                .thenReturn(TaskDispatchEngine.NotifyOutcome.fail("短信网关超时"));
        TaskDispatchReply r = svc.runOnce(at(2026, 10, 8, 7, 0));
        assertEquals(1, r.getSentRows());
        assertEquals(TaskDispatchEngine.CHANNEL_STATION, logStore.get(0).getChannel());
        assertTrue(itemStore.get(0).getContent().contains("走站内消息渠道发出"));
        assertTrue(itemStore.get(0).getRemark().contains("短信网关超时"));
    }

    @Test
    void 两条皆不通记发不成_不占去重名额_次日条件够再试() {
        itemStore.add(standard(1, LocalDate.of(2026, 10, 8)));
        when(smsSender.send(anyString())).thenReturn(TaskDispatchEngine.NotifyOutcome.fail("断网"));
        // 头一日站内也不通，第二日站内通（按调用次序）
        when(stationSender.send(anyString()))
                .thenReturn(TaskDispatchEngine.NotifyOutcome.fail("站内断网"))
                .thenReturn(TaskDispatchEngine.NotifyOutcome.ok());

        TaskDispatchReply d1 = svc.runOnce(at(2026, 10, 8, 7, 0));
        assertEquals(0, d1.getSentRows());
        assertEquals(1, d1.getFailedRows());
        assertEquals(TaskDispatchEngine.STATUS_FAILED, itemStore.get(0).getStatus());
        assertTrue(itemStore.get(0).getRemark().contains("断网"));
        // 发不成的流水不占去重槽（status=2，不写已发出）
        assertEquals(TaskDispatchEngine.STATUS_FAILED, logStore.get(0).getStatus());

        // 次日（10-09 闸内）再试：站内通，改判已发出
        TaskDispatchReply d2 = svc.runOnce(at(2026, 10, 9, 7, 0));
        assertEquals(1, d2.getSentRows());
        assertEquals(TaskDispatchEngine.STATUS_SENT, itemStore.get(0).getStatus());
        assertEquals(2, logStore.size());
        assertEquals(TaskDispatchEngine.STATUS_SENT, logStore.get(1).getStatus());
        assertEquals(TaskDispatchEngine.CHANNEL_STATION, logStore.get(1).getChannel());
    }

    // ========================= 幂等 =========================

    @Test
    void 同一日闸门内按两回_第二回照头一趟回话_不再递一句() {
        itemStore.add(standard(1, LocalDate.of(2026, 10, 8)));
        TaskDispatchReply a = svc.runOnce(at(2026, 10, 8, 7, 0));
        TaskDispatchReply b = svc.runOnce(at(2026, 10, 8, 8, 30));
        assertFalse(a.isReplayed());
        assertTrue(b.isReplayed());
        assertEquals(a.getPickedRows(), b.getPickedRows());
        assertEquals(a.getSentRows(), b.getSentRows());
        assertEquals(a.getFailedRows(), b.getFailedRows());
        assertEquals(a.getRunId(), b.getRunId());
        assertEquals(1, runStore.size());
        verify(smsSender, org.mockito.Mockito.times(1)).send(anyString());
    }

    // ========================= 第 8 条：缺项按下 =========================

    @Test
    void 缺项本行按兵不发_无人代填_缺几项流水照原样吐() {
        itemStore.add(item(1, "  ", "深翻耕", "水稻", "北浚镇农艺岗",
                LocalDate.of(2026, 10, 8), TaskDispatchEngine.STATUS_WAIT));
        TaskDispatchReply r = svc.runOnce(at(2026, 10, 8, 7, 0));
        assertEquals(1, r.getPickedRows());
        assertEquals(0, r.getSentRows());
        assertEquals(1, r.getFailedRows());
        // 条目仍搁着、记要不被代填
        assertEquals(TaskDispatchEngine.STATUS_WAIT, itemStore.get(0).getStatus());
        assertNull(itemStore.get(0).getContent());
        assertEquals(TaskDispatchEngine.KIND_HELD_MISSING, logStore.get(0).getResultKind());
        assertTrue(logStore.get(0).getFailReason().contains("地块码"));
        verify(smsSender, never()).send(anyString());
    }

    // ========================= 第 6 条：同键去重 =========================

    @Test
    void 同地块同事由一行递出后第二行不再递_不同事由各递一句() {
        itemStore.add(item(1, "P1", "深翻耕", "水稻", "岗",
                LocalDate.of(2026, 10, 8), TaskDispatchEngine.STATUS_WAIT));
        itemStore.add(item(2, "P1", "深翻耕", "水稻", "岗",
                LocalDate.of(2026, 10, 8), TaskDispatchEngine.STATUS_WAIT));
        itemStore.add(item(3, "P1", "叶面阻控", "水稻", "岗",
                LocalDate.of(2026, 10, 8), TaskDispatchEngine.STATUS_WAIT));
        TaskDispatchReply r = svc.runOnce(at(2026, 10, 8, 7, 0));
        assertEquals(3, r.getPickedRows());
        assertEquals(2, r.getSentRows());
        assertEquals(1, r.getFailedRows());
        assertEquals(TaskDispatchEngine.KIND_SENT_SMS, logStore.get(0).getResultKind());
        assertEquals(TaskDispatchEngine.KIND_DEDUP_SKIP, logStore.get(1).getResultKind());
        assertEquals(TaskDispatchEngine.KIND_SENT_SMS, logStore.get(2).getResultKind());
        // 被跳过的第二行仍是搁着，但今日不再试
        assertEquals(TaskDispatchEngine.STATUS_WAIT, itemStore.get(1).getStatus());
    }

    // ========================= 第 4 条：500 上限 =========================

    @Test
    void 装满500收手_余下留下一趟() {
        for (long i = 1; i <= 502; i++) {
            itemStore.add(standard(i, LocalDate.of(2026, 10, 8)));
        }
        TaskDispatchReply r = svc.runOnce(at(2026, 10, 8, 7, 0));
        assertEquals(500, r.getPickedRows());
        assertEquals(500, r.getSentRows());
        assertEquals(0, r.getFailedRows());
        assertEquals(500, logStore.size());
    }

    // ========================= 第 9 条：单行失手不撂整趟 =========================

    @Test
    void 一处抛错不抹已发出的行_其余照走() {
        itemStore.add(item(1, "P1", "m1", "水稻", "岗",
                LocalDate.of(2026, 10, 8), TaskDispatchEngine.STATUS_WAIT));
        itemStore.add(item(2, "P2", "m2", "水稻", "岗",
                LocalDate.of(2026, 10, 8), TaskDispatchEngine.STATUS_WAIT));
        when(smsSender.send(anyString())).thenAnswer((InvocationOnMock inv) -> {
            String text = inv.getArgument(0);
            return text.contains("地块码：P1")
                    ? TaskDispatchEngine.NotifyOutcome.fail("断网")
                    : TaskDispatchEngine.NotifyOutcome.ok();
        });
        when(stationSender.send(anyString())).thenAnswer((InvocationOnMock inv) -> {
            String text = inv.getArgument(0);
            return text.contains("地块码：P1")
                    ? TaskDispatchEngine.NotifyOutcome.fail("站内断网")
                    : TaskDispatchEngine.NotifyOutcome.ok();
        });
        TaskDispatchReply r = svc.runOnce(at(2026, 10, 8, 7, 0));
        assertEquals(2, r.getPickedRows());
        assertEquals(1, r.getSentRows());
        assertEquals(1, r.getFailedRows());
        assertEquals(TaskDispatchEngine.STATUS_FAILED, itemStore.get(0).getStatus());
        assertEquals(TaskDispatchEngine.STATUS_SENT, itemStore.get(1).getStatus());
    }

    // ========================= 内存 QueryWrapper 过滤（支持 eq / in） =========================

    private static final Pattern VAL = Pattern.compile("MPGENVAL[0-9]+");

    @SuppressWarnings("unchecked")
    private <T> List<T> filter(Wrapper<T> w, List<T> store) {
        if (!(w instanceof QueryWrapper)) {
            return new ArrayList<>(store);
        }
        QueryWrapper<T> qw = (QueryWrapper<T>) w;
        Map<String, Object> params = qw.getParamNameValuePairs();
        List<T> out = new ArrayList<>(store);
        String col = null;
        String op = null;
        for (ISqlSegment seg : qw.getExpression().getNormal()) {
            String sql = seg.getSqlSegment();
            if ("AND".equalsIgnoreCase(sql) || "OR".equalsIgnoreCase(sql)) {
                col = null;
                op = null;
                continue;
            }
            if (sql.contains("paramNameValuePairs")) {
                List<Object> vals = new ArrayList<>();
                Matcher mt = VAL.matcher(sql);
                while (mt.find()) {
                    vals.add(params.get(mt.group()));
                }
                final String column = col;
                final boolean inOp = "IN".equalsIgnoreCase(op);
                out.removeIf(x -> {
                    Object got = fieldValue(x, column);
                    return vals.stream().noneMatch(v -> String.valueOf(got).equals(String.valueOf(v)));
                });
                col = null;
                op = null;
            } else if ("=".equals(sql)) {
                op = "=";
            } else if ("IN".equalsIgnoreCase(sql)) {
                op = "IN";
            } else {
                col = sql;
            }
        }
        return out;
    }

    private static Object fieldValue(Object o, String column) {
        if (column == null) {
            return null;
        }
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
            Field f = o.getClass().getDeclaredField(sb.toString());
            f.setAccessible(true);
            return f.get(o);
        } catch (Exception e) {
            return null;
        }
    }
}
