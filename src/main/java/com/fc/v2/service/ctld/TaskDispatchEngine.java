package com.fc.v2.service.ctld;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 农时催办《安排单》早班「翻单」纯引擎：十条规矩只此一处给裁决，旁处不另立尺子。
 * 一、闸门（{@link #GATE_OPEN}）：只在 06:30 起到 09:00 收这一截动手；这一截之外固定回「本轮未动单」，
 *     一行都不挑，同一刻按两回两回话一字不差。
 * 二、届期尺（{@link #isDue}）：本行届期那一一日不晚于本次当口就算到，两数正好压平也算到；
 *     筛行与回写共用这一把尺。
 * 三、至多提前几日（{@link #advanceWorkdays}）：只看本行 amount 那一格，空着按不提前办；
 *     凑不满一整日的零头不计，法定节假日不计，县里挂出停办的那几日也不计。
 * 四、只从还搁着的（status=0）那叠里挑，已发出（1）、发不成（2）翻出来也不碰；一趟至多派 500 行，装满收手。
 * 五、一行只读本行的字：地块码、事由名目、主栽作物、承办岗一律取本行那格，不向左邻右舍借。
 * 六、去重键（{@link #dedupKey}）只由本行地块码+事由名目两段接成：同处地块、同桩事由一日只一回；
 *     少接一段、或拿分区代号充当都算走样；同处地块同日两桩不一样的事各递一句，合法。
 * 七、渠道：先走短信；当场递不出去就地回落站内消息，并在本行写下走不通那句原由；两条都不通才记发不成，
 *     发不成不占去重名额，次日条件仍够还得再试。
 * 八、话术四要件（地块码、主栽作物、届期那一一日、承办岗）缺一不发，缺几项一项一项点出来；带空位的话不许出口。
 * 九、单行失手：本行标失败原由，其余各行照走，整趟不撂下；已发出的不回滚。一行都挑不出来合法，老老实实报 0，
 *     不许去够那叠头一条（上一版就折在 due.get(0) 这一处）。
 * 十、记号两处同落（条目情形改已发出 + 记要补一句本回发出时刻与渠道），趟末三数（挑中/递出/未成）
 *     与逐行记号同一次算定；回话只引用这三个数，不拿内存临时数、不拉另一轮逐行清点做对冲。
 * 引擎不落库、不取系统时刻、不发号；当口时刻、趟号、外部渠道、停办日历都由入参（{@link Gate}）递进来。
 *
 * @author fuce
 * @date 2026-10-07
 */
public final class TaskDispatchEngine {

    private TaskDispatchEngine() {
    }

    // ============================ 第 1 条：闸门 ============================
    /** 起闸：06:30:00（含此刻起开闸） */
    public static final int GATE_FROM_HOUR = 6;
    public static final int GATE_FROM_MINUTE = 30;
    /** 收闸：09:00:00（到此刻即收，09:00:00 已在闸门外） */
    public static final int GATE_TO_HOUR = 9;
    public static final int GATE_TO_MINUTE = 0;
    /** 闸门外固定回话，同一刻两回一字不差 */
    public static final String GATE_CLOSED_REPLY = "本轮未动单";

    // ============================ 第 4 条：条目情形与一趟上限 ============================
    /** 条目情形 0搁着（只从这叠挑） 1已发出 2发不成 */
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_SENT = 1;
    public static final int STATUS_FAILED = 2;
    /** 一趟至多派 500 行，装满收手，余下留下一趟 */
    public static final int ROUND_CAP = 500;

    // ============================ 第 7 条：渠道 ============================
    public static final String CHANNEL_SMS = "SMS";
    public static final String CHANNEL_INNER = "INNER";

    /** 话术四要件栏名（缺件一项一项点出来，不许只回「信息不全」） */
    public static final String FIELD_PLOT_CODE = "地块码";
    public static final String FIELD_CROP = "主栽作物";
    public static final String FIELD_DUE = "届期那一一日";
    public static final String FIELD_HANDLER = "承办岗";

    /** 去重键两段之间的接缝（键原文留痕，日后翻得出怎么接的） */
    public static final String KEY_JOINER = "||";

    /**
     * 外部取数/出手：引擎不自己摸库、不自己发信、不自己看钟。
     */
    public interface Gate {
        /** 这一日算不算停办（法定节假日或县里挂出停办）；返回 true 则数提前日时跳过这一天 */
        boolean isOffDay(LocalDate day);

        /** 去重：同一处地块、同一桩事由，在给定那一日是否已经递过一回 */
        boolean alreadySentToday(String plotCode, String measureName, LocalDate day);

        /** 走短信。递不出去抛 {@link ChannelException}，原由照原样带出来，回落时写回本行 */
        void sendSms(String text);

        /** 回落站内消息。递不出去抛 {@link ChannelException} */
        void sendInner(String text);
    }

    /** 渠道当场走不通；那句原由由渠道自己给，引擎照原样落格，不改字 */
    public static class ChannelException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public ChannelException(String reason) {
            super(reason);
        }
    }

    /** 翻单读到的一行（一条还搁着的安排单条目）；只读本行各格 */
    public static class ItemRow {
        public Long id;
        public String itemNo;
        /** 本行地块码（去重键前段、话术要件一） */
        public String plotCode;
        /** 本行事由名目（去重键后段） */
        public String measureName;
        /** 届期那一一日 */
        public LocalDate dueDate;
        /** 至多许提前几日；空着=不提前办 */
        public BigDecimal advanceDays;
        /** 本行主栽作物（话术要件二） */
        public String cropName;
        /** 承办岗（话术要件四） */
        public String handlerPost;
        public Integer status;
        public String content;

        public ItemRow() {
        }
    }

    /** 一行翻完的去向下发，service 照这个落库；引擎不动库 */
    public static class RowOutcome {
        public final Long itemId;
        public final String plotCode;
        public final String measureName;
        /** 去处：SENT 递出 / FAILED 两条渠道都不通 / HELD 文面缺件按住 / DEDUP_SKIP 今日已递过 */
        public final String kind;
        /** 递出行实走渠道 SMS/INNER；其余为 null */
        public final String channel;
        /** 递出去的那句话术原文 */
        public final String messageText;
        /** 失败原由（短信走不通那句也要落本行；两条都不通时两句并陈） */
        public final String failReason;
        /** 缺件按住时点出缺哪几项 */
        public final List<String> missingFields;
        /** 要补进记要的那一句（发出时刻+渠道）；未递出为 null */
        public final String contentNote;

        private RowOutcome(Long itemId, String plotCode, String measureName, String kind, String channel,
                           String messageText, String failReason, List<String> missingFields, String contentNote) {
            this.itemId = itemId;
            this.plotCode = plotCode;
            this.measureName = measureName;
            this.kind = kind;
            this.channel = channel;
            this.messageText = messageText;
            this.failReason = failReason;
            this.missingFields = missingFields;
            this.contentNote = contentNote;
        }

        public boolean isSent() {
            return "SENT".equals(kind);
        }

        public static RowOutcome sent(Long id, String plot, String measure, String channel, String text, String note) {
            return new RowOutcome(id, plot, measure, "SENT", channel, text, null, null, note);
        }

        public static RowOutcome failed(Long id, String plot, String measure, String reason) {
            return new RowOutcome(id, plot, measure, "FAILED", null, null, reason, null, null);
        }

        public static RowOutcome held(Long id, String plot, String measure, List<String> missing) {
            return new RowOutcome(id, plot, measure, "HELD", null, null, null, missing, null);
        }

        public static RowOutcome dedupSkipped(Long id, String plot, String measure) {
            return new RowOutcome(id, plot, measure, "DEDUP_SKIP", null, null, null, null, null);
        }
    }

    /** 一趟翻单的当场结果：三数与逐行去向同一次算定，回话只引用这里 */
    public static class RoundResult {
        public final boolean gateOpen;
        public final String roundNo;
        public final LocalDate runDay;
        public final List<RowOutcome> rows;
        public final int picked;
        public final int sent;
        public final int failed;
        /** 缺件按住几行（仍算挑中） */
        public final int held;
        /** 被一日去重挡下几行（不算挑中） */
        public final int dedupSkipped;
        public final int smsCount;
        public final int innerCount;
        public final String replyText;

        RoundResult(boolean gateOpen, String roundNo, LocalDate runDay, List<RowOutcome> rows,
                    int picked, int sent, int failed, int held, int dedupSkipped,
                    int smsCount, int innerCount, String replyText) {
            this.gateOpen = gateOpen;
            this.roundNo = roundNo;
            this.runDay = runDay;
            this.rows = rows;
            this.picked = picked;
            this.sent = sent;
            this.failed = failed;
            this.held = held;
            this.dedupSkipped = dedupSkipped;
            this.smsCount = smsCount;
            this.innerCount = innerCount;
            this.replyText = replyText;
        }

        /** 闸门外：一行不挑，三数全 0，固定回话 */
        static RoundResult closed(String roundNo) {
            return new RoundResult(false, roundNo, null, new ArrayList<>(), 0, 0, 0, 0, 0, 0, 0,
                    GATE_CLOSED_REPLY);
        }
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    // ============================ 第 1 条：窗口判法（只此一处） ============================

    /**
     * 这一刻在不在动手那一截里：[06:30, 09:00)。
     * 06:30:00 当点开闸；09:00:00 当点已收闸；夜里静默口不穿透。
     */
    public static boolean gateOpen(java.time.LocalTime moment) {
        if (moment == null) {
            return false;
        }
        java.time.LocalTime from = java.time.LocalTime.of(GATE_FROM_HOUR, GATE_FROM_MINUTE);
        java.time.LocalTime to = java.time.LocalTime.of(GATE_TO_HOUR, GATE_TO_MINUTE);
        return !moment.isBefore(from) && moment.isBefore(to);
    }

    // ============================ 第 2 条：届期尺（筛行/回写同一把） ============================

    /**
     * 本行届期那一一日不晚于本次当口，就算到；两数正好压平也算到（按日比，不看钟点零头）。
     */
    public static boolean isDue(LocalDate dueDate, LocalDate today) {
        if (dueDate == null || today == null) {
            // 没写届期的行，无届可到，不算到（不拿空当到期）
            return false;
        }
        return !dueDate.isAfter(today);
    }

    // ============================ 第 3 条：至多提前几个工作日 ============================

    /**
     * 从今日的次日起走到届期日，中间经过几个「可办日」：停办日（法定节假日、县里停办）跳过不计，
     * 只数整日，钟点零头进不来。届期不晚于今日返回 0。
     */
    public static int workdaysAhead(LocalDate today, LocalDate dueDate, Gate gate) {
        int days = 0;
        LocalDate cursor = today;
        while (cursor.isBefore(dueDate)) {
            cursor = cursor.plusDays(1);
            if (gate == null || !gate.isOffDay(cursor)) {
                days++;
            }
        }
        return days;
    }

    /** 本行自报的「至多提前几日」：空着/负数，按不提前办（0）；零头向下取整，凑不满一日不计 */
    public static int normalizeAdvanceDays(BigDecimal amount) {
        if (amount == null) {
            return 0;
        }
        BigDecimal floor = amount.setScale(0, java.math.RoundingMode.FLOOR);
        int n = floor.intValue();
        return n > 0 ? n : 0;
    }

    /**
     * 这一行本轮够不够日子，与届期尺同一处判：
     * 届期不晚于今日（第2条，含压平、含已过届）→ 到；
     * 届期还在以后 → 只看本行提前格，走到届期剩的可办日不超过 n 才算到，停办日不计在内。
     */
    public static boolean withinAdvanceWindow(LocalDate dueDate, LocalDate today, int n, Gate gate) {
        if (isDue(dueDate, today)) {
            return true;
        }
        if (dueDate == null || today == null) {
            return false;
        }
        return workdaysAhead(today, dueDate, gate) <= n;
    }

    // ============================ 第 6 条：去重键 ============================

    /**
     * 键只由本行地块码 + 事由名目两段接成；任一段空着，键不成立（也不许拿分区代号顶替）。
     */
    public static String dedupKey(String plotCode, String measureName) {
        if (blank(plotCode) || blank(measureName)) {
            return null;
        }
        return plotCode.trim() + KEY_JOINER + measureName.trim();
    }

    // ============================ 第 8 条：话术四要件 ============================

    /** 逐一点名缺哪几项；齐全返回空单。四项：地块码、主栽作物、届期那一一日、承办岗 */
    public static List<String> missingMessageFields(ItemRow row) {
        List<String> missing = new ArrayList<>();
        if (blank(row.plotCode)) {
            missing.add(FIELD_PLOT_CODE);
        }
        if (blank(row.cropName)) {
            missing.add(FIELD_CROP);
        }
        if (row.dueDate == null) {
            missing.add(FIELD_DUE);
        }
        if (blank(row.handlerPost)) {
            missing.add(FIELD_HANDLER);
        }
        return missing;
    }

    /**
     * 拼那句要递出去的话：地块码、主栽作物、届期那一一日、承办岗四项落齐才拼；有缺件返回 null（带空位的话不许出口）。
     */
    public static String buildMessage(ItemRow row) {
        if (!missingMessageFields(row).isEmpty()) {
            return null;
        }
        return "【农时催办】地块码 " + row.plotCode.trim() + "，主栽作物 " + row.cropName.trim()
                + "，届期那一一日 " + row.dueDate.toString() + "，承办岗 " + row.handlerPost.trim()
                + "：措施届期，请按时落办。";
    }

    // ============================ 一口气翻完一趟 ============================

    /**
     * 翻一趟。moment 是触发那一刻（窗口、当口今日都按它）；rows 是从册上读来的全部条目
     *（引擎不挑库，只按第 4 条从还搁着的那叠里挑）；roundNo 是本趟号。
     * 闸门外：不挑、不发、不落记号，三数全 0，固定回「本轮未动单」。
     */
    public static RoundResult dispatch(String roundNo, java.time.LocalDateTime moment,
                                       List<ItemRow> rows, Gate gate) {
        if (moment == null || !gateOpen(moment.toLocalTime())) {
            return RoundResult.closed(roundNo);
        }
        LocalDate today = moment.toLocalDate();
        String sentAtText = moment.toString().replace('T', ' ');

        List<RowOutcome> outcomes = new ArrayList<>();
        // 同一趟内已占的键：逐行落库在趟尾，账本来不及在中途挡住第二条，这里先挡一道
        java.util.Set<String> sentKeysThisRound = new java.util.HashSet<>();
        int picked = 0;
        int sent = 0;
        int failed = 0;
        int held = 0;
        int dedupSkipped = 0;
        int smsCount = 0;
        int innerCount = 0;

        if (rows != null) {
            for (ItemRow row : rows) {
                // 第 4 条：只挑还搁着的；已发出、发不成翻出来也不碰
                if (row.status == null || row.status != STATUS_PENDING) {
                    continue;
                }
                // 第 2、3 条同一把尺：届期到 + 本行自己的提前窗
                int n = normalizeAdvanceDays(row.advanceDays);
                if (!withinAdvanceWindow(row.dueDate, today, n, gate)) {
                    continue;
                }

                // 第 6 条：去重键只认地块码+事由名目；今日已递过（库里或本趟头一条）不再碰，不算挑中
                String key = dedupKey(row.plotCode, row.measureName);
                boolean seen = key != null && (sentKeysThisRound.contains(key)
                        || gate.alreadySentToday(row.plotCode.trim(), row.measureName.trim(), today));
                if (seen) {
                    outcomes.add(RowOutcome.dedupSkipped(row.id, row.plotCode, row.measureName));
                    dedupSkipped++;
                    continue;
                }

                // 一趟至多派 500 行：装满收手，余下留下一趟
                if (picked >= ROUND_CAP) {
                    break;
                }
                picked++;

                // 第 8 条：话术四要件缺一项，本行按兵不发，缺几项点几项，无人代填
                List<String> missing = missingMessageFields(row);
                if (!missing.isEmpty()) {
                    outcomes.add(RowOutcome.held(row.id, row.plotCode, row.measureName, missing));
                    held++;
                    continue;
                }

                String text = buildMessage(row);
                // 第 7 条：先短信，不通就地回落站内，并写下短信走不通那句原由；两条都不通才记发不成
                String channel;
                String smsFailReason = null;
                try {
                    gate.sendSms(text);
                    channel = CHANNEL_SMS;
                    smsCount++;
                } catch (ChannelException smsFail) {
                    smsFailReason = smsFail.getMessage();
                    try {
                        gate.sendInner(text);
                        channel = CHANNEL_INNER;
                        innerCount++;
                    } catch (ChannelException innerFail) {
                        // 两条都不通：记发不成；原由两句并陈，不占去重名额，次日条件仍够再试
                        String reason = "短信走不通：" + smsFailReason + "；站内消息也走不通：" + innerFail.getMessage();
                        outcomes.add(RowOutcome.failed(row.id, row.plotCode, row.measureName, reason));
                        failed++;
                        continue;
                    }
                }

                // 第 10 条：记要补一句「本回发出时刻 + 走的渠道」，与情形改已发出同一次算定；
                // 第 7 条：短信先不通才回落站内的，那句走不通的原由一并写回本行事由记要
                String channelText = CHANNEL_SMS.equals(channel) ? "短信" : "站内消息";
                String note = "本回于 " + sentAtText + " 经" + channelText + "发出"
                        + (smsFailReason != null ? "（短信先不通：" + smsFailReason + "，就地回落站内消息）" : "");
                outcomes.add(RowOutcome.sent(row.id, row.plotCode, row.measureName, channel, text, note));
                if (key != null) {
                    sentKeysThisRound.add(key);
                }
                sent++;
            }
        }

        String reply = "本轮挑中" + picked + "行，递出" + sent + "行，未成" + failed + "行"
                + (held > 0 || dedupSkipped > 0
                ? "（另：缺件按住" + held + "行、今日已递挡下" + dedupSkipped + "行，均不计入递出）" : "")
                + "；三数与逐行记号同一次算定";
        return new RoundResult(true, roundNo, today, outcomes, picked, sent, failed, held, dedupSkipped,
                smsCount, innerCount, reply);
    }

    /** 便捷重载：用 JVM 默认时区把 java.util.Date 当日时刻换成本地日期时间（service 层可取固定时区） */
    public static RoundResult dispatch(String roundNo, java.util.Date moment, java.time.ZoneId zone,
                                       List<ItemRow> rows, Gate gate) {
        java.time.LocalDateTime ldt = moment.toInstant().atZone(zone == null ? ZoneId.systemDefault() : zone)
                .toLocalDateTime();
        return dispatch(roundNo, ldt, rows, gate);
    }
}
