package com.fc.v2.service.ctld;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fc.v2.service.ctld.TaskDispatchEngine.DispatchResult;
import com.fc.v2.service.ctld.TaskDispatchEngine.ItemRow;
import com.fc.v2.service.ctld.TaskDispatchEngine.NotifyGate;
import com.fc.v2.service.ctld.TaskDispatchEngine.NotifyOutcome;
import com.fc.v2.service.ctld.TaskDispatchEngine.RowOutcome;

/**
 * 早班翻单唯一出处 {@link TaskDispatchEngine} 的规矩测试：
 * 闸门与静默、届期同尺压平、提前量零头与停办日、只挑搁着/500上限、读本行、
 * 地块码+事由去重、短信回落站内、话术缺项、单行失手、三数同源与空趟报0。
 */
class TaskDispatchEngineTest {

    /** 可控渠道：记录每次投递，可指定哪个渠道通 */
    private static class FakeGate implements NotifyGate {
        boolean smsOk = true;
        boolean stationOk = true;
        String smsReason = "短信网关超时";
        String stationReason = "站内服务不可用";
        final List<String[]> calls = new ArrayList<>();

        @Override
        public NotifyOutcome send(String channel, String text) {
            calls.add(new String[] {channel, text});
            if (TaskDispatchEngine.CHANNEL_SMS.equals(channel)) {
                return smsOk ? NotifyOutcome.ok() : NotifyOutcome.fail(smsReason);
            }
            if (TaskDispatchEngine.CHANNEL_STATION.equals(channel)) {
                return stationOk ? NotifyOutcome.ok() : NotifyOutcome.fail(stationReason);
            }
            return NotifyOutcome.fail("不认得的渠道");
        }
    }

    private static Date at(int y, int m, int d, int hh, int mm) {
        return Date.from(LocalDate.of(y, m, d).atTime(hh, mm)
                .atZone(TaskDispatchEngine.ZONE).toInstant());
    }

    private static ItemRow row(long id, String plot, String measure, String crop, String post,
                               LocalDate due, BigDecimal amount) {
        return new ItemRow(id, "T" + id, plot, measure, crop, post,
                due == null ? null : Date.from(due.atStartOfDay(TaskDispatchEngine.ZONE).toInstant()),
                amount, true);
    }

    private static ItemRow standard(long id, LocalDate due) {
        return row(id, "P1001", "叶面阻控", "水稻", "北浚镇农艺岗", due, null);
    }

    private static Set<LocalDate> off(LocalDate... ds) {
        return new HashSet<>(Arrays.asList(ds));
    }

    // ============ 第 1 条：闸门 ============

    @Test
    void 闸门只在六点半到九点之间_夜里一字不发() {
        assertTrue(TaskDispatchEngine.gateOpen(at(2026, 10, 8, 6, 30)));
        assertTrue(TaskDispatchEngine.gateOpen(at(2026, 10, 8, 8, 59)));
        assertFalse(TaskDispatchEngine.gateOpen(at(2026, 10, 8, 6, 29)));
        assertFalse(TaskDispatchEngine.gateOpen(at(2026, 10, 8, 9, 0)));
        assertFalse(TaskDispatchEngine.gateOpen(at(2026, 10, 8, 0, 0)));
        assertFalse(TaskDispatchEngine.gateOpen(at(2026, 10, 8, 23, 59)));
        assertFalse(TaskDispatchEngine.gateOpen(null));
    }

    @Test
    void 闸外按两回回话一字不差_单上一行不起皱() {
        ItemRow r = standard(1, LocalDate.of(2026, 10, 8));
        FakeGate gate = new FakeGate();
        Date night = at(2026, 10, 8, 5, 0);
        DispatchResult a = TaskDispatchEngine.dispatch(night, Collections.singletonList(r),
                null, new HashSet<>(), gate);
        DispatchResult b = TaskDispatchEngine.dispatch(night, Collections.singletonList(r),
                null, new HashSet<>(), gate);
        assertFalse(a.isGateOpen());
        assertEquals(TaskDispatchEngine.GATE_CLOSED_REPLY, a.getResultMsg());
        assertEquals(TaskDispatchEngine.GATE_CLOSED_REPLY, b.getResultMsg());
        assertEquals(0, a.getPickedRows());
        assertTrue(a.getOutcomes().isEmpty());
        assertTrue(gate.calls.isEmpty());
    }

    // ============ 第 2 条：届期同一把尺（含压平） ============

    @Test
    void 届期不晚于当口算到_正正压平也算到_未来不到() {
        LocalDate today = LocalDate.of(2026, 10, 8);
        assertTrue(TaskDispatchEngine.isDue(LocalDate.of(2026, 10, 8), today, 0, null));
        assertTrue(TaskDispatchEngine.isDue(LocalDate.of(2026, 10, 7), today, 0, null));
        assertFalse(TaskDispatchEngine.isDue(LocalDate.of(2026, 10, 9), today, 0, null));
    }

    // ============ 第 3 条：提前量 ============

    @Test
    void 提前量_空格零头负数按0_整日前推() {
        assertEquals(0, TaskDispatchEngine.leadDays(null));
        assertEquals(0, TaskDispatchEngine.leadDays(new BigDecimal("0.9")));
        assertEquals(0, TaskDispatchEngine.leadDays(new BigDecimal("-2")));
        assertEquals(3, TaskDispatchEngine.leadDays(new BigDecimal("3.9")));
        LocalDate due = LocalDate.of(2026, 10, 10);
        assertEquals(LocalDate.of(2026, 10, 7),
                TaskDispatchEngine.earliestSendDate(due, 3, Collections.emptySet()));
    }

    @Test
    void 提前量_法定节假日与县里停办日不抵日_周末不剔() {
        LocalDate due = LocalDate.of(2026, 10, 12); // 周一
        // 10-09~10-11 停办：往前数 3 个办日，这三日跳过不抵日，落到 10-06
        Set<LocalDate> holidays = off(LocalDate.of(2026, 10, 9),
                LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 11));
        LocalDate earliest = TaskDispatchEngine.earliestSendDate(due, 3, holidays);
        assertEquals(LocalDate.of(2026, 10, 6), earliest);
        // 无停办日时 10-10/11（周末）照算办日
        assertEquals(LocalDate.of(2026, 10, 9),
                TaskDispatchEngine.earliestSendDate(due, 3, Collections.emptySet()));
    }

    @Test
    void 提前三日后_届期未到也够日子() {
        LocalDate today = LocalDate.of(2026, 10, 8);
        LocalDate due = LocalDate.of(2026, 10, 10);
        assertTrue(TaskDispatchEngine.isDue(due, today, 3, Collections.emptySet()));
        assertFalse(TaskDispatchEngine.isDue(due, today, 1, Collections.emptySet()));
    }

    // ============ 第 4 条：挑谁 / 500 上限 ============

    @Test
    void 发不成今日已试过的行_retryable为假_翻出不碰() {
        DispatchResult r = TaskDispatchEngine.dispatch(at(2026, 10, 8, 7, 0),
                Collections.singletonList(new ItemRow(1L, "T1", "P1", "m", "水稻", "岗",
                        Date.from(LocalDate.of(2026, 10, 1).atStartOfDay(TaskDispatchEngine.ZONE).toInstant()),
                        null, false)),
                null, new HashSet<>(), new FakeGate());
        assertEquals(0, r.getPickedRows());
    }

    @Test
    void 一趟至多500行_装满收手_余下留到下一趟() {
        List<ItemRow> rows = new ArrayList<>();
        for (long i = 1; i <= 600; i++) {
            rows.add(row(i, "P" + (1000 + i), "措施" + i, "水稻", "岗",
                    LocalDate.of(2026, 10, 8), null));
        }
        DispatchResult r = TaskDispatchEngine.dispatch(at(2026, 10, 8, 7, 0), rows, null,
                new HashSet<>(), new FakeGate());
        assertEquals(500, r.getPickedRows());
        assertEquals(500, r.getSentRows());
        assertEquals(0, r.getFailedRows());
    }

    @Test
    void 一行挑不出来合法_老实报0_不去够头一条() {
        // 届期都在未来：候选给了也不挑
        List<ItemRow> rows = Arrays.asList(standard(1, LocalDate.of(2026, 10, 20)),
                standard(2, LocalDate.of(2026, 10, 21)));
        DispatchResult r = TaskDispatchEngine.dispatch(at(2026, 10, 8, 7, 0), rows, null,
                new HashSet<>(), new FakeGate());
        assertEquals(0, r.getPickedRows());
        assertEquals(0, r.getSentRows());
        assertEquals(0, r.getFailedRows());
        assertTrue(r.getOutcomes().isEmpty());
        assertTrue(r.isGateOpen());
        // 空候选也不炸（上一版折在 due.get(0)）
        DispatchResult empty = TaskDispatchEngine.dispatch(at(2026, 10, 8, 7, 0),
                new ArrayList<>(), null, new HashSet<>(), new FakeGate());
        assertEquals(0, empty.getPickedRows());
    }

    // ============ 第 5 条：读本行（字段原样落话术，不借邻行） ============

    @Test
    void 话术四项照本行各落各的() {
        ItemRow r = row(1, "P2002", "深翻耕", "小麦", "西片承办岗",
                LocalDate.of(2026, 10, 8), null);
        String msg = TaskDispatchEngine.messageText(r);
        assertTrue(msg.contains("地块码：P2002"));
        assertTrue(msg.contains("主栽作物：小麦"));
        assertTrue(msg.contains("届期那一一日：2026-10-08"));
        assertTrue(msg.contains("承办岗：西片承办岗"));
    }

    // ============ 第 6 条：去重键 ============

    @Test
    void 去重键只由地块码与事由接成_缺段为null_分区不能充当() {
        assertEquals("P1#深翻耕", TaskDispatchEngine.dedupKey("P1", "深翻耕"));
        assertEquals("P1#深翻耕", TaskDispatchEngine.dedupKey(" P1 ", " 深翻耕"));
        assertNull(TaskDispatchEngine.dedupKey("", "深翻耕"));
        assertNull(TaskDispatchEngine.dedupKey("P1", ""));
        assertNull(TaskDispatchEngine.dedupKey(null, null));
    }

    @Test
    void 同地块同事由一日只递一回_两桩不同事各递一句不并() {
        FakeGate gate = new FakeGate();
        ItemRow a = row(1, "P1", "深翻耕", "水稻", "岗", LocalDate.of(2026, 10, 8), null);
        ItemRow a2 = row(2, "P1", "深翻耕", "水稻", "岗", LocalDate.of(2026, 10, 8), null);
        ItemRow b = row(3, "P1", "叶面阻控", "水稻", "岗", LocalDate.of(2026, 10, 8), null);
        DispatchResult r = TaskDispatchEngine.dispatch(at(2026, 10, 8, 7, 0),
                Arrays.asList(a, a2, b), null, new HashSet<>(), gate);
        assertEquals(3, r.getPickedRows());
        assertEquals(2, r.getSentRows());
        assertEquals(1, r.getFailedRows());
        assertEquals(TaskDispatchEngine.KIND_DEDUP_SKIP, r.getOutcomes().get(1).getResultKind());
        assertEquals(TaskDispatchEngine.KIND_SENT_SMS, r.getOutcomes().get(0).getResultKind());
        assertEquals(TaskDispatchEngine.KIND_SENT_SMS, r.getOutcomes().get(2).getResultKind());
    }

    @Test
    void 同地块同事由今日账上已递过_本趟不再递() {
        Set<String> already = new HashSet<>(Collections.singletonList("P1#深翻耕"));
        ItemRow a = row(1, "P1", "深翻耕", "水稻", "岗", LocalDate.of(2026, 10, 8), null);
        FakeGate gate = new FakeGate();
        DispatchResult r = TaskDispatchEngine.dispatch(at(2026, 10, 8, 7, 0),
                Collections.singletonList(a), null, already, gate);
        assertEquals(1, r.getPickedRows());
        assertEquals(0, r.getSentRows());
        assertEquals(TaskDispatchEngine.KIND_DEDUP_SKIP, r.getOutcomes().get(0).getResultKind());
        assertTrue(gate.calls.isEmpty());
    }

    // ============ 第 7 条：渠道回落 ============

    @Test
    void 短信走不通就地回落站内_原由写下_仍算递出() {
        FakeGate gate = new FakeGate();
        gate.smsOk = false;
        DispatchResult r = TaskDispatchEngine.dispatch(at(2026, 10, 8, 7, 0),
                Collections.singletonList(standard(1, LocalDate.of(2026, 10, 8))),
                null, new HashSet<>(), gate);
        assertEquals(1, r.getSentRows());
        RowOutcome o = r.getOutcomes().get(0);
        assertEquals(TaskDispatchEngine.KIND_SENT_STATION, o.getResultKind());
        assertEquals(TaskDispatchEngine.CHANNEL_STATION, o.getChannel());
        assertTrue(o.getReason().contains("短信网关超时"));
        assertEquals(2, gate.calls.size());
    }

    @Test
    void 两条渠道皆不通才记发不成_不占去重名额() {
        FakeGate gate = new FakeGate();
        gate.smsOk = false;
        gate.stationOk = false;
        Set<String> sentKeys = new HashSet<>();
        DispatchResult r = TaskDispatchEngine.dispatch(at(2026, 10, 8, 7, 0),
                Collections.singletonList(standard(1, LocalDate.of(2026, 10, 8))),
                null, sentKeys, gate);
        assertEquals(1, r.getPickedRows());
        assertEquals(0, r.getSentRows());
        assertEquals(1, r.getFailedRows());
        assertEquals(TaskDispatchEngine.KIND_FAILED, r.getOutcomes().get(0).getResultKind());
        assertEquals(TaskDispatchEngine.STATUS_FAILED, r.getOutcomes().get(0).getItemStatus());
        // 发不成不把去重键加进集合
        assertTrue(sentKeys.isEmpty());
    }

    // ============ 第 8 条：话术缺项 ============

    @Test
    void 四项缺一按下不发_缺项一项一项点出_不带空位出口() {
        ItemRow bad = row(1, "", null, " ", "岗", LocalDate.of(2026, 10, 8), null);
        List<String> missing = TaskDispatchEngine.missingFields(bad);
        assertTrue(missing.contains("地块码"));
        assertTrue(missing.contains("事由名目"));
        assertTrue(missing.contains("主栽作物"));
        assertNull(TaskDispatchEngine.messageText(bad));

        FakeGate gate = new FakeGate();
        DispatchResult r = TaskDispatchEngine.dispatch(at(2026, 10, 8, 7, 0),
                Collections.singletonList(bad), null, new HashSet<>(), gate);
        RowOutcome o = r.getOutcomes().get(0);
        assertEquals(TaskDispatchEngine.KIND_HELD_MISSING, o.getResultKind());
        assertEquals(TaskDispatchEngine.STATUS_WAIT, o.getItemStatus());
        assertTrue(o.getReason().contains("地块码"));
        assertTrue(o.getReason().contains("事由名目"));
        assertTrue(o.getReason().contains("主栽作物"));
        assertTrue(gate.calls.isEmpty());
        assertEquals(1, r.getPickedRows());
        assertEquals(0, r.getSentRows());
        assertEquals(1, r.getFailedRows());
    }

    @Test
    void 缺届期那一项也点出() {
        ItemRow bad = row(1, "P1", "m", "水稻", "岗", null, null);
        assertTrue(TaskDispatchEngine.missingFields(bad).contains("届期那一一日"));
    }

    // ============ 第 9/10 条：三数同源、单行不撂整趟 ============

    @Test
    void 成与不成同行_三数当场轧平_各归各类() {
        // 按话术中的地块判：P9001 两条皆不通，P9002 短信成——单行失手不撂下另一行
        FakeGate mixedGate = new FakeGate() {
            @Override
            public NotifyOutcome send(String channel, String text) {
                calls.add(new String[] {channel, text});
                if (TaskDispatchEngine.CHANNEL_SMS.equals(channel)) {
                    return text.contains("P9001") ? NotifyOutcome.fail("断网") : NotifyOutcome.ok();
                }
                if (TaskDispatchEngine.CHANNEL_STATION.equals(channel)) {
                    return text.contains("P9001") ? NotifyOutcome.fail("站内断网") : NotifyOutcome.ok();
                }
                return NotifyOutcome.fail("不认得的渠道");
            }
        };
        ItemRow fail = row(1, "P9001", "m1", "水稻", "岗", LocalDate.of(2026, 10, 8), null);
        ItemRow ok = row(2, "P9002", "m2", "水稻", "岗", LocalDate.of(2026, 10, 8), null);
        DispatchResult r = TaskDispatchEngine.dispatch(at(2026, 10, 8, 7, 0),
                Arrays.asList(fail, ok), null, new HashSet<>(), mixedGate);
        assertEquals(2, r.getPickedRows());
        assertEquals(1, r.getSentRows());
        assertEquals(1, r.getFailedRows());
        assertEquals(TaskDispatchEngine.KIND_FAILED, r.getOutcomes().get(0).getResultKind());
        assertEquals(TaskDispatchEngine.KIND_SENT_SMS, r.getOutcomes().get(1).getResultKind());
        assertTrue(r.getResultMsg().contains("挑中2行，递出1行，未成1行"));
    }
}
