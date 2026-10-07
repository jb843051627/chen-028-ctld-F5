package com.fc.v2.service.ctld;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 农时催办「早班翻单」纯引擎：十条规矩只此一处给口径，旁处不另立尺子。
 * 一、这一截窗口开不开闸（{@link #gateOpen}，06:30 起 09:00 收）；
 * 二、本行届期够不够日子（{@link #isDue}，筛行与回写同一把尺，压平也算到）；
 * 三、至多提前几日（{@link #earliestSendDate}，零头不计、法定节假日与县里停办日不计）；
 * 四、挑谁不挑谁（只挑搁着的，一趟至多 500 行）；
 * 五、一行只读本行的字（地块码/事由/作物/承办岗各取本行，不向左邻右舍借）；
 * 六、去重键认不认作同一桩（{@link #dedupKey}：地块码＋事由名目两段，同一日只递一回）；
 * 七、渠道（先短信，当场递不出去就地回落站内并记下原由，两条皆不通才记发不成）；
 * 八、话术四项缺一不发，缺项一项一项点出来；
 * 九、单行失手不撂整趟，一行挑不出来老实报 0，不去够头一条；
 * 十、记号与三数同源（{@link #dispatch} 同一次算定），同轮两回照头一趟回话。
 * 引擎不落库、不取时刻（时刻由入参带入），只定每一行的去处并当场轧出三个数。
 *
 * @author fuce
 * @date 2026-10-07
 */
public final class TaskDispatchEngine {

    private TaskDispatchEngine() {
    }

    /** 落库与文面一律按此时区对账（JSON 也按 GMT+8），不跟着部署机器的时区漂 */
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    // ========================= 第 1 条：闸门 =========================

    /** 闸起：06:30（含这一刻，按下即动手） */
    public static final int GATE_FROM_MINUTE = 6 * 60 + 30;
    /** 闸收：09:00（不含这一刻，夜里静默口子绝不穿透） */
    public static final int GATE_TO_MINUTE = 9 * 60;

    /** 闸外按多少回都是同一句话，单上一行都不许起皱 */
    public static final String GATE_CLOSED_REPLY = "本轮未动单";

    // ========================= 条目情形（与 t_ctld_task_item.status 对齐） =========================

    public static final int STATUS_WAIT = 0;
    public static final int STATUS_SENT = 1;
    public static final int STATUS_FAILED = 2;

    // ========================= 第 7 条：渠道 =========================

    public static final String CHANNEL_SMS = "SMS";
    public static final String CHANNEL_STATION = "STATION";

    // ========================= 逐行结局 =========================

    /** 短信递出 */
    public static final String KIND_SENT_SMS = "SENT_SMS";
    /** 短信走不通、站内递出（短信原由落账） */
    public static final String KIND_SENT_STATION = "SENT_STATION";
    /** 两条渠道皆不通：记发不成，不占去重名额，次日条件够再试 */
    public static final String KIND_FAILED = "FAILED";
    /** 文面缺项按下不发，缺项原样吐出，无人代填 */
    public static final String KIND_HELD_MISSING = "HELD_MISSING";
    /** 同地块码同事由名目一日内已递过，本行不再递 */
    public static final String KIND_DEDUP_SKIP = "DEDUP_SKIP";

    /** 第 4 条：一趟至多派 500 行，装满收手 */
    public static final int PICK_LIMIT = 500;

    // ========================= 入参：本行只读本行 =========================

    /**
     * 一条候选行（逐格照抄 t_ctld_task_item 本行，不许借底册、借邻行）。
     * retryable：上几趟记过发不成、但今日还没再试过的行（第 7 条次日再试，第 4 条同日翻出不碰由它为 false 兜住）。
     */
    public static class ItemRow {
        private final Long id;
        private final String itemNo;
        private final String plotNo;
        private final String measureName;
        private final String mainCrop;
        private final String assigneePost;
        private final Date dueAt;
        private final BigDecimal amount;
        private final boolean retryable;

        public ItemRow(Long id, String itemNo, String plotNo, String measureName, String mainCrop,
                       String assigneePost, Date dueAt, BigDecimal amount, boolean retryable) {
            this.id = id;
            this.itemNo = itemNo;
            this.plotNo = plotNo;
            this.measureName = measureName;
            this.mainCrop = mainCrop;
            this.assigneePost = assigneePost;
            this.dueAt = dueAt;
            this.amount = amount;
            this.retryable = retryable;
        }

        public Long getId() {
            return id;
        }

        public String getItemNo() {
            return itemNo;
        }

        public String getPlotNo() {
            return plotNo;
        }

        public String getMeasureName() {
            return measureName;
        }

        public String getMainCrop() {
            return mainCrop;
        }

        public String getAssigneePost() {
            return assigneePost;
        }

        public Date getDueAt() {
            return dueAt;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public boolean isRetryable() {
            return retryable;
        }
    }

    /** 渠道口子：当场递一句，递不出去把那句原由带回 */
    public interface NotifyGate {
        NotifyOutcome send(String channel, String text);
    }

    public static class NotifyOutcome {
        private final boolean ok;
        private final String reason;

        private NotifyOutcome(boolean ok, String reason) {
            this.ok = ok;
            this.reason = reason;
        }

        public static NotifyOutcome ok() {
            return new NotifyOutcome(true, null);
        }

        public static NotifyOutcome fail(String reason) {
            return new NotifyOutcome(false, reason == null ? "渠道没给出原由" : reason);
        }

        public boolean isOk() {
            return ok;
        }

        public String getReason() {
            return reason;
        }
    }

    // ========================= 出参：一行的去处与一趟的三数 =========================

    /** 一行定下来的去处，service 照此落两处记号（条目情形＋事由地块记要） */
    public static class RowOutcome {
        private final Long itemId;
        private final String itemNo;
        private final String plotNo;
        private final String measureName;
        private final LocalDate dueDate;
        private final String resultKind;
        /** 回写条目情形：1已发出 / 2发不成 / 0按下未发 */
        private final int itemStatus;
        private final String channel;
        private final String msgText;
        private final String reason;

        RowOutcome(Long itemId, String itemNo, String plotNo, String measureName, LocalDate dueDate,
                   String resultKind, int itemStatus, String channel, String msgText, String reason) {
            this.itemId = itemId;
            this.itemNo = itemNo;
            this.plotNo = plotNo;
            this.measureName = measureName;
            this.dueDate = dueDate;
            this.resultKind = resultKind;
            this.itemStatus = itemStatus;
            this.channel = channel;
            this.msgText = msgText;
            this.reason = reason;
        }

        public Long getItemId() {
            return itemId;
        }

        public String getItemNo() {
            return itemNo;
        }

        public String getPlotNo() {
            return plotNo;
        }

        public String getMeasureName() {
            return measureName;
        }

        public LocalDate getDueDate() {
            return dueDate;
        }

        public String getResultKind() {
            return resultKind;
        }

        public int getItemStatus() {
            return itemStatus;
        }

        public String getChannel() {
            return channel;
        }

        public String getMsgText() {
            return msgText;
        }

        public String getReason() {
            return reason;
        }
    }

    /** 一趟点算完：三数与逐行去处同源于此，页面只复述，内存临时数不许拿来回话 */
    public static class DispatchResult {
        private final LocalDate runDate;
        private final Date runAt;
        private final boolean gateOpen;
        private final List<RowOutcome> outcomes;
        private final int pickedRows;
        private final int sentRows;
        private final int failedRows;
        private final String resultMsg;

        private DispatchResult(LocalDate runDate, Date runAt, boolean gateOpen, List<RowOutcome> outcomes,
                               int pickedRows, int sentRows, int failedRows, String resultMsg) {
            this.runDate = runDate;
            this.runAt = runAt;
            this.gateOpen = gateOpen;
            this.outcomes = outcomes;
            this.pickedRows = pickedRows;
            this.sentRows = sentRows;
            this.failedRows = failedRows;
            this.resultMsg = resultMsg;
        }

        static DispatchResult closed(LocalDate runDate, Date at) {
            return new DispatchResult(runDate, at, false, new ArrayList<>(), 0, 0, 0, GATE_CLOSED_REPLY);
        }

        public LocalDate getRunDate() {
            return runDate;
        }

        public Date getRunAt() {
            return runAt;
        }

        public boolean isGateOpen() {
            return gateOpen;
        }

        public List<RowOutcome> getOutcomes() {
            return outcomes;
        }

        public int getPickedRows() {
            return pickedRows;
        }

        public int getSentRows() {
            return sentRows;
        }

        public int getFailedRows() {
            return failedRows;
        }

        public String getResultMsg() {
            return resultMsg;
        }
    }

    // ========================= 时刻换算 =========================

    public static LocalDate dateOf(Date d) {
        return Instant.ofEpochMilli(d.getTime()).atZone(ZONE).toLocalDate();
    }

    private static int minuteOfDay(Date d) {
        return Instant.ofEpochMilli(d.getTime()).atZone(ZONE).toLocalTime().toSecondOfDay() / 60;
    }

    // ========================= 第 1 条 =========================

    /** 只在 [06:30, 09:00) 这一截动手；闸外、夜里一字不发 */
    public static boolean gateOpen(Date at) {
        if (at == null) {
            return false;
        }
        int m = minuteOfDay(at);
        return m >= GATE_FROM_MINUTE && m < GATE_TO_MINUTE;
    }

    // ========================= 第 3 条：提前量 =========================

    /** 那一格空着按不提前办（0）；凑不满一整日的零头舍去（向零取整）；负数不成立也按 0 */
    public static int leadDays(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return 0;
        }
        return amount.intValue();
    }

    /**
     * 最早许递那一一日：从届期往前数 leadDays 个「办日」，法定节假日与县里停办日落在前进路上一律跳过、不抵日。
     * 普通周末不在停办册里，不剔。
     */
    public static LocalDate earliestSendDate(LocalDate due, int leadDays, Set<LocalDate> offDays) {
        LocalDate d = due;
        int remain = leadDays;
        while (remain > 0) {
            d = d.minusDays(1);
            if (offDays != null && offDays.contains(d)) {
                continue;
            }
            remain--;
        }
        return d;
    }

    // ========================= 第 2 条：届期（筛与回写共用此尺） =========================

    /**
     * 本行届期那一一日不晚于本次当口就算到——按日压平，due 与 today 正正相等也算到。
     * 提前量让最早许递日往前挪：today 不早于最早许递日即够日子。
     */
    public static boolean isDue(LocalDate due, LocalDate today, int leadDays, Set<LocalDate> offDays) {
        if (due == null || today == null) {
            return false;
        }
        // 不提前（leadDays=0）时 earliest == due：due <= today 即到，两处同一把尺
        return !today.isBefore(earliestSendDate(due, leadDays, offDays));
    }

    // ========================= 第 6 条：去重键 =========================

    /** 键只由本行的地块码与事由名目两段接成；少一段、拿分区代号充当都算走样，故缺一即返回 null（按缺项按下） */
    public static String dedupKey(String plotNo, String measureName) {
        String p = plotNo == null ? "" : plotNo.trim();
        String m = measureName == null ? "" : measureName.trim();
        if (p.isEmpty() || m.isEmpty()) {
            return null;
        }
        return p + "#" + m;
    }

    // ========================= 第 8 条：话术四项 =========================

    /** 话术落四项：地块码、主栽作物、届期那一一日、承办岗；另加去重键所必需的事由名目。缺一项点一项 */
    public static List<String> missingFields(ItemRow row) {
        List<String> missing = new ArrayList<>();
        if (isBlank(row.getPlotNo())) {
            missing.add("地块码");
        }
        if (isBlank(row.getMeasureName())) {
            missing.add("事由名目");
        }
        if (isBlank(row.getMainCrop())) {
            missing.add("主栽作物");
        }
        if (row.getDueAt() == null) {
            missing.add("届期那一一日");
        }
        if (isBlank(row.getAssigneePost())) {
            missing.add("承办岗");
        }
        return missing;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** 四项齐了才成形；带着空位的话不许出口，故缺项时返回 null */
    public static String messageText(ItemRow row) {
        if (!missingFields(row).isEmpty()) {
            return null;
        }
        return "【农时催办】地块码：" + row.getPlotNo().trim()
                + "；主栽作物：" + row.getMainCrop().trim()
                + "；届期那一一日：" + dateOf(row.getDueAt())
                + "；承办岗：" + row.getAssigneePost().trim()
                + "。届期将到，请按时落措施。";
    }

    // ========================= 一口气翻完一趟 =========================

    /**
     * 早班翻单唯一口径入口。candidates 已由调用方按届期、id 排稳序给齐（搁着的＋今日可再试的发不成行）；
     * 本引擎只负责按尺筛届期、装满 500 即止、逐行定去处、当场轧三数。
     * sentKeysToday：今日已经递成的去重键（前几趟落下的账），撞上即跳过，不占递出数。
     * 闸闭时原样回 {@link #GATE_CLOSED_REPLY}，候选一行不碰；闸开而一行挑不出来是合法结果，三数老实报 0。
     */
    public static DispatchResult dispatch(Date at, List<ItemRow> candidates, Set<LocalDate> offDays,
                                          Set<String> sentKeysToday, NotifyGate gate) {
        LocalDate today = dateOf(at);
        if (!gateOpen(at)) {
            return DispatchResult.closed(today, at);
        }
        Set<LocalDate> off = offDays == null ? new LinkedHashSet<>() : offDays;
        Set<String> sentKeys = sentKeysToday == null ? new LinkedHashSet<>() : sentKeysToday;

        List<RowOutcome> outcomes = new ArrayList<>();
        int sent = 0;
        if (candidates != null) {
            for (ItemRow row : candidates) {
                if (outcomes.size() >= PICK_LIMIT) {
                    break; // 第 4 条：装满 500 收手，余者留下一趟
                }
                // 第 4/7 条：发不成的行次日条件够才再试；今日已经试过一回的，翻出来不再碰
                if (!row.isRetryable()) {
                    continue;
                }
                LocalDate due = row.getDueAt() == null ? null : dateOf(row.getDueAt());
                // 第 2/3 条：筛行与回写同一把尺
                if (!isDue(due, today, leadDays(row.getAmount()), off)) {
                    continue;
                }

                // 第 8 条：缺项按下不发，缺几项原样点出，无人代填
                List<String> missing = missingFields(row);
                if (!missing.isEmpty()) {
                    outcomes.add(new RowOutcome(row.getId(), row.getItemNo(), trim(row.getPlotNo()),
                            trim(row.getMeasureName()), due, KIND_HELD_MISSING, STATUS_WAIT, null, null,
                            "文面缺项，本行按兵不发；缺的是：" + String.join("、", missing)));
                    continue;
                }

                // 第 6 条：同地块码＋同事由名目，一日内只递一回（键缺段的已在缺项处拦下）
                String key = dedupKey(row.getPlotNo(), row.getMeasureName());
                if (sentKeys.contains(key)) {
                    outcomes.add(new RowOutcome(row.getId(), row.getItemNo(), trim(row.getPlotNo()),
                            trim(row.getMeasureName()), due, KIND_DEDUP_SKIP, STATUS_WAIT, null, null,
                            "同一处地块、同一桩事由今日已递过一回，本行不再递"));
                    continue;
                }

                String text = messageText(row);
                // 第 7 条：先短信；当场不通就地回落站内，并记下短信走不通那句原由
                NotifyOutcome sms = gate.send(CHANNEL_SMS, text);
                if (sms.isOk()) {
                    sentKeys.add(key);
                    sent++;
                    outcomes.add(new RowOutcome(row.getId(), row.getItemNo(), trim(row.getPlotNo()),
                            trim(row.getMeasureName()), due, KIND_SENT_SMS, STATUS_SENT,
                            CHANNEL_SMS, text, null));
                    continue;
                }
                NotifyOutcome station = gate.send(CHANNEL_STATION, text);
                if (station.isOk()) {
                    sentKeys.add(key);
                    sent++;
                    outcomes.add(new RowOutcome(row.getId(), row.getItemNo(), trim(row.getPlotNo()),
                            trim(row.getMeasureName()), due, KIND_SENT_STATION, STATUS_SENT,
                            CHANNEL_STATION, text,
                            "短信当场递不出去：" + sms.getReason() + "；已就地回落站内消息递出"));
                    continue;
                }
                // 两条皆不通才记发不成；不占去重名额，次日条件仍够再试一回
                outcomes.add(new RowOutcome(row.getId(), row.getItemNo(), trim(row.getPlotNo()),
                        trim(row.getMeasureName()), due, KIND_FAILED, STATUS_FAILED, null, null,
                        "短信走不通：" + sms.getReason() + "；站内消息也走不通：" + station.getReason()));
            }
        }

        int picked = outcomes.size();
        int failed = picked - sent; // 挑中未递出者皆归本趟未成：发不成/缺项按下/同键跳过
        String msg = "本轮挑中" + picked + "行，递出" + sent + "行，未成" + failed + "行；三个数与逐行记号同一次算定";
        return new DispatchResult(today, at, true, outcomes, picked, sent, failed, msg);
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
}
