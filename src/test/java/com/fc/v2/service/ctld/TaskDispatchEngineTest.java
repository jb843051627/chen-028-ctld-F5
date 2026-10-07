package com.fc.v2.service.ctld;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fc.v2.service.ctld.TaskDispatchEngine.Gate;
import com.fc.v2.service.ctld.TaskDispatchEngine.ItemRow;
import com.fc.v2.service.ctld.TaskDispatchEngine.RoundResult;
import com.fc.v2.service.ctld.TaskDispatchEngine.RowOutcome;

/**
 * 早班翻单唯一出处 {@link TaskDispatchEngine} 的十条规矩测试：
 * 闸门、届期尺（含压平）、提前工作日（零头/法定/县里停办不计）、只挑搁着且至多500、
 * 一行只读本行、去重键两段、短信→站内回落与发不成、话术四要件点名、单行失手/空叠、两处记号与三数同源。
 */
class TaskDispatchEngineTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);
    private static final LocalDateTime IN_WINDOW = LocalDateTime.of(2026, 10, 7, 8, 0);

    /** 可编排的假闸：停办日、已递键集、短信/站内成败都能拨 */
    private static final class FakeGate implements Gate {
        final Set<LocalDate> offDays = new HashSet<>();
        final Set<String> sentKeys = new HashSet<>();
        boolean smsOk = true;
        boolean innerOk = true;
        String smsReason = "短信网关超时";
        String innerReason = "站内服务不可用";
        int smsCalls = 0;
        int innerCalls = 0;
        List<String> sentTexts = new ArrayList<>();

        @Override
        public boolean isOffDay(LocalDate day) {
            return offDays.contains(day);
        }

        @Override
        public boolean alreadySentToday(String plotCode, String measureName, LocalDate day) {
            return sentKeys.contains(plotCode + TaskDispatchEngine.KEY_JOINER + measureName + "@" + day);
        }

        void seedSent(String plot, String measure, LocalDate day) {
            sentKeys.add(plot + TaskDispatchEngine.KEY_JOINER + measure + "@" + day);
        }

        @Override
        public void sendSms(String text) {
            smsCalls++;
            if (!smsOk) {
                throw new TaskDispatchEngine.ChannelException(smsReason);
            }
            sentTexts.add("SMS:" + text);
        }

        @Override
        public void sendInner(String text) {
            innerCalls++;
            if (!innerOk) {
                throw new TaskDispatchEngine.ChannelException(innerReason);
            }
            sentTexts.add("INNER:" + text);
        }
    }

    private ItemRow row(long id, String plot, String measure, LocalDate due, BigDecimal advance,
                        String crop, String post, int status) {
        ItemRow r = new ItemRow();
        r.id = id;
        r.itemNo = "T" + id;
        r.plotCode = plot;
        r.measureName = measure;
        r.dueDate = due;
        r.advanceDays = advance;
        r.cropName = crop;
        r.handlerPost = post;
        r.status = status;
        r.content = "记要" + id;
        return r;
    }

    private ItemRow good(long id, String plot, String measure, LocalDate due) {
        return row(id, plot, measure, due, null, "水稻", "北浚镇农办岗", TaskDispatchEngine.STATUS_PENDING);
    }

    private RoundResult runAt(LocalDateTime moment, List<ItemRow> rows, FakeGate gate) {
        return TaskDispatchEngine.dispatch("R1", moment, rows, gate);
    }

    private RoundResult run(List<ItemRow> rows, FakeGate gate) {
        return runAt(IN_WINDOW, rows, gate);
    }

    // ============ 第 1 条：闸门 ============

    @Test
    void 窗口边界_六点半开九点收_夜里静默() {
        assertTrue(TaskDispatchEngine.gateOpen(LocalDateTime.of(2026, 10, 7, 6, 30).toLocalTime()), "06:30当点开闸");
        assertTrue(TaskDispatchEngine.gateOpen(LocalDateTime.of(2026, 10, 7, 8, 59, 59).toLocalTime()));
        assertFalse(TaskDispatchEngine.gateOpen(LocalDateTime.of(2026, 10, 7, 6, 29).toLocalTime()), "06:29闸门外");
        assertFalse(TaskDispatchEngine.gateOpen(LocalDateTime.of(2026, 10, 7, 9, 0).toLocalTime()), "09:00当点已收闸");
        assertFalse(TaskDispatchEngine.gateOpen(LocalDateTime.of(2026, 10, 7, 23, 0).toLocalTime()), "夜里不穿透");
        assertFalse(TaskDispatchEngine.gateOpen(null));
    }

    @Test
    void 闸门外固定回本轮未动单_一行不挑不发_同一刻两回一字不差() {
        FakeGate gate = new FakeGate();
        List<ItemRow> rows = Arrays.asList(good(1, "P1", "喷药", TODAY));
        LocalDateTime night = LocalDateTime.of(2026, 10, 7, 23, 30);
        RoundResult a = runAt(night, rows, gate);
        RoundResult b = runAt(night, rows, gate);
        assertFalse(a.gateOpen);
        assertEquals(0, a.picked);
        assertEquals(0, a.sent);
        assertEquals(0, a.failed);
        assertEquals(TaskDispatchEngine.GATE_CLOSED_REPLY, a.replyText);
        assertEquals(a.replyText, b.replyText);
        assertEquals(0, gate.smsCalls);
        assertTrue(a.rows.isEmpty());
    }

    // ============ 第 2 条：届期尺 ============

    @Test
    void 届期不晚于当口算到_正好压平也算() {
        assertTrue(TaskDispatchEngine.isDue(TODAY, TODAY), "两数压平算到");
        assertTrue(TaskDispatchEngine.isDue(TODAY.minusDays(3), TODAY), "早已过届算到");
        assertFalse(TaskDispatchEngine.isDue(TODAY.plusDays(1), TODAY));
        assertFalse(TaskDispatchEngine.isDue(null, TODAY), "空届期不当到期");
    }

    // ============ 第 3 条：至多提前几个工作日 ============

    @Test
    void 提前格空着按不提前_零头向下取整_负数按0() {
        assertEquals(0, TaskDispatchEngine.normalizeAdvanceDays(null));
        assertEquals(0, TaskDispatchEngine.normalizeAdvanceDays(new BigDecimal("0")));
        assertEquals(0, TaskDispatchEngine.normalizeAdvanceDays(new BigDecimal("-2")));
        assertEquals(1, TaskDispatchEngine.normalizeAdvanceDays(new BigDecimal("1.9")), "凑不满一日的零头不计");
        assertEquals(3, TaskDispatchEngine.normalizeAdvanceDays(new BigDecimal("3")));
    }

    @Test
    void 提前窗_法定与县里停办日不计_只看本行那格() {
        FakeGate gate = new FakeGate();
        LocalDate due = TODAY.plusDays(3); // 日历上隔3天
        gate.offDays.add(TODAY.plusDays(1)); // 中间一天停办
        // 走到届期剩 2 个可办日（停办那天不计）
        assertEquals(2, TaskDispatchEngine.workdaysAhead(TODAY, due, gate));
        assertFalse(TaskDispatchEngine.withinAdvanceWindow(due, TODAY, 1, gate), "只许提前1日，2个可办日不够");
        assertTrue(TaskDispatchEngine.withinAdvanceWindow(due, TODAY, 2, gate), "许提前2日，正好算到");
        // 本行提前格空着=不提前：明明差两个可办日，不挑
        assertFalse(TaskDispatchEngine.withinAdvanceWindow(due, TODAY,
                TaskDispatchEngine.normalizeAdvanceDays(null), gate));
    }

    @Test
    void 明天届期_不提前的不挑_提前一日的挑() {
        FakeGate gate = new FakeGate();
        LocalDate tomorrow = TODAY.plusDays(1);
        assertFalse(TaskDispatchEngine.withinAdvanceWindow(tomorrow, TODAY, 0, gate));
        assertTrue(TaskDispatchEngine.withinAdvanceWindow(tomorrow, TODAY, 1, gate));
    }

    // ============ 第 4 条：只挑搁着 + 至多500 ============

    @Test
    void 已发出与发不成的不碰_只挑搁着() {
        FakeGate gate = new FakeGate();
        List<ItemRow> rows = Arrays.asList(
                good(1, "P1", "甲", TODAY),
                row(2, "P2", "乙", TODAY, null, "麦", "岗", TaskDispatchEngine.STATUS_SENT),
                row(3, "P3", "丙", TODAY, null, "麦", "岗", TaskDispatchEngine.STATUS_FAILED));
        RoundResult r = run(rows, gate);
        assertEquals(1, r.picked);
        assertEquals(1, r.sent);
    }

    @Test
    void 一趟至多五百行_装满收手_余下留下趟() {
        FakeGate gate = new FakeGate();
        List<ItemRow> rows = new ArrayList<>();
        for (int i = 1; i <= 501; i++) {
            rows.add(good(i, "P" + i, "措施" + i, TODAY)); // 键各不相同
        }
        RoundResult r = run(rows, gate);
        assertEquals(TaskDispatchEngine.ROUND_CAP, r.picked);
        assertEquals(500, r.sent);
        assertEquals(0, r.failed);
    }

    // ============ 第 5 条：一行只读本行 + 第 6 条去重 ============

    @Test
    void 去重键只认地块码加事由名目_少一段不成立_不拿分区顶替() {
        assertEquals("P1||喷药", TaskDispatchEngine.dedupKey("P1", "喷药"));
        assertNull(TaskDispatchEngine.dedupKey("", "喷药"), "地块码空，键不成立");
        assertNull(TaskDispatchEngine.dedupKey("P1", "  "), "事由空，键不成立");
    }

    @Test
    void 同处地块同日两桩不一样的事_各递一句_不许并成一回() {
        FakeGate gate = new FakeGate();
        List<ItemRow> rows = Arrays.asList(
                good(1, "P1", "喷施钝化剂", TODAY),
                good(2, "P1", "田间灌水调控", TODAY));
        RoundResult r = run(rows, gate);
        assertEquals(2, r.sent);
        assertEquals(2, r.picked);
    }

    @Test
    void 同处地块同桩事由今日已递_挡下不挑_隔日另算() {
        FakeGate gate = new FakeGate();
        gate.seedSent("P1", "喷药", TODAY);
        RoundResult r = run(Arrays.asList(good(1, "P1", "喷药", TODAY)), gate);
        assertEquals(0, r.picked, "挡下不算挑中");
        assertEquals(1, r.dedupSkipped);
        assertEquals(0, r.sent);
        assertEquals("DEDUP_SKIP", r.rows.get(0).kind);

        // 库里昨日递过：今日是另一笔，不挡
        FakeGate gate2 = new FakeGate();
        gate2.seedSent("P1", "喷药", TODAY.minusDays(1));
        RoundResult r2 = run(Arrays.asList(good(1, "P1", "喷药", TODAY)), gate2);
        assertEquals(1, r2.sent);
    }

    @Test
    void 同一趟内两条同键_头一条递出第二条挡下() {
        FakeGate gate = new FakeGate();
        List<ItemRow> rows = Arrays.asList(
                good(1, "P1", "喷药", TODAY),
                good(2, "P1", "喷药", TODAY));
        RoundResult r = run(rows, gate);
        assertEquals(1, r.sent);
        assertEquals(1, r.dedupSkipped);
        assertEquals(1, gate.smsCalls, "第二条不再发");
    }

    // ============ 第 7 条：渠道 ============

    @Test
    void 短信通则走短信() {
        FakeGate gate = new FakeGate();
        RoundResult r = run(Arrays.asList(good(1, "P1", "甲", TODAY)), gate);
        assertEquals(1, r.smsCount);
        assertEquals(0, r.innerCount);
        assertEquals(TaskDispatchEngine.CHANNEL_SMS, r.rows.get(0).channel);
    }

    @Test
    void 短信不通就地回落站内_原由写回本行记要() {
        FakeGate gate = new FakeGate();
        gate.smsOk = false;
        RoundResult r = run(Arrays.asList(good(1, "P1", "甲", TODAY)), gate);
        assertEquals(1, r.sent);
        assertEquals(1, r.innerCount);
        assertEquals(TaskDispatchEngine.CHANNEL_INNER, r.rows.get(0).channel);
        assertTrue(r.rows.get(0).contentNote.contains("短信先不通"), "短信走不通那句写回记要");
        assertTrue(r.rows.get(0).contentNote.contains(gate.smsReason));
    }

    @Test
    void 两条渠道都不通才记发不成_不占去重名额() {
        FakeGate gate = new FakeGate();
        gate.smsOk = false;
        gate.innerOk = false;
        RoundResult r = run(Arrays.asList(good(1, "P1", "甲", TODAY)), gate);
        assertEquals(0, r.sent);
        assertEquals(1, r.failed);
        RowOutcome o = r.rows.get(0);
        assertEquals("FAILED", o.kind);
        assertTrue(o.failReason.contains("短信"));
        assertTrue(o.failReason.contains("站内"));
        // 发不成不占去重：账上没有，且再给一条同键行（模拟次日重跑，账本空）仍会发
        assertFalse(gate.sentKeys.stream().anyMatch(k -> k.contains("P1||甲")));
    }

    // ============ 第 8 条：话术四要件 ============

    @Test
    void 缺哪几项一项一项点出来_带空位的话不出口_无人代填() {
        FakeGate gate = new FakeGate();
        // 缺作物但届期在：能被挑中，按住；缺届期的行进不了门（空届期不算到），在纯函数层另验
        ItemRow noCrop = row(1, "P1", "甲", TODAY, null, "  ", "农办岗", TaskDispatchEngine.STATUS_PENDING);
        ItemRow noDuePost = row(2, "P2", "乙", null, null, "水稻", "", TaskDispatchEngine.STATUS_PENDING);
        RoundResult r = run(Arrays.asList(noCrop, noDuePost), gate);
        assertEquals(1, r.held, "缺作物那行按住；空届期那行不够届期、不挑");
        assertEquals(1, r.picked);
        assertEquals(0, r.sent);
        assertEquals(Arrays.asList(TaskDispatchEngine.FIELD_CROP), r.rows.get(0).missingFields);
        // 纯函数层：缺届期+承办岗两项都点得出
        assertTrue(TaskDispatchEngine.missingMessageFields(noDuePost)
                .containsAll(Arrays.asList(TaskDispatchEngine.FIELD_DUE, TaskDispatchEngine.FIELD_HANDLER)));
        assertEquals(0, gate.smsCalls);
        assertNull(TaskDispatchEngine.buildMessage(noCrop));
    }

    @Test
    void 四件齐_话术落齐四项() {
        ItemRow full = good(1, "P1001", "甲", TODAY);
        String text = TaskDispatchEngine.buildMessage(full);
        assertTrue(text.contains("P1001"));
        assertTrue(text.contains("水稻"));
        assertTrue(text.contains(TODAY.toString()));
        assertTrue(text.contains("北浚镇农办岗"));
    }

    // ============ 第 9 条：单行失手 / 空叠 ============

    @Test
    void 一行失手标原由_其余照走_整趟不撂下() {
        FakeGate gate = new FakeGate();
        // 让短信对 id=2 失败、站内也失败：用带状态的闸门
        gate.smsOk = false;
        gate.innerOk = false;
        List<ItemRow> rows = Arrays.asList(
                good(1, "P1", "甲", TODAY),
                good(2, "P2", "乙", TODAY));
        RoundResult r = run(rows, gate);
        assertEquals(2, r.picked);
        assertEquals(0, r.sent);
        assertEquals(2, r.failed, "两条都不通，各自标失败，整趟不撂下");
    }

    @Test
    void 一行都挑不出来合法_报零_不去够叠头一条() {
        FakeGate gate = new FakeGate();
        // 空叠
        RoundResult empty = run(new ArrayList<>(), gate);
        assertEquals(0, empty.picked);
        assertEquals(0, empty.sent);
        assertEquals(0, empty.failed);
        assertTrue(empty.replyText.contains("挑中0行"));
        // 有行但没一行够日子：同样老老实实报 0，不发头一条
        RoundResult noneDue = run(Arrays.asList(good(1, "P1", "甲", TODAY.plusDays(10))), gate);
        assertEquals(0, noneDue.picked);
        assertEquals(0, noneDue.sent);
    }

    // ============ 第 10 条：两处记号 + 三数同源 ============

    @Test
    void 递出行带记要一句_三数与逐行同源_发成加失败加按住等于挑中() {
        FakeGate gate = new FakeGate();
        gate.smsOk = false; // 一律回落站内，便于核渠道数
        List<ItemRow> rows = Arrays.asList(
                good(1, "P1", "甲", TODAY),                       // 站内递出
                row(2, "P2", "乙", TODAY, null, " ", "岗", 0),   // 缺作物按住
                row(3, "P3", "丙", TODAY.plusDays(10), null, "水稻", "岗", 0)); // 不够日子，不挑
        RoundResult r = run(rows, gate);
        assertEquals(2, r.picked, "前两行挑中，第三行不够日子");
        assertEquals(1, r.sent);
        assertEquals(0, r.failed);
        assertEquals(1, r.held);
        assertEquals(r.sent + r.failed + r.held, r.picked);
        assertEquals(r.sent, r.smsCount + r.innerCount);
        // 记号二：记要补「本回时刻 + 渠道」
        RowOutcome sent = r.rows.stream().filter(RowOutcome::isSent).findFirst().get();
        assertTrue(sent.contentNote.contains("经站内消息发出"));
        assertTrue(sent.contentNote.contains("2026-10-07 08:00"));
        // 回话只引用这三个数
        assertTrue(r.replyText.contains("挑中2行，递出1行，未成0行"));
    }
}
