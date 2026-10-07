package com.fc.v2.service.ctld;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.fc.v2.common.exception.ctld.CaseFlowException;
import com.fc.v2.model.auto.TCtldCaseFlow;

/**
 * 流转单纯引擎：五问只此一处给口径。
 * 一、件上当下停在第几阶；
 * 二、下一阶放不放行（canAdvance）；
 * 三、已经合口（封住）的行还吞不吞新按语（一律不吞）；
 * 四、同一阶二次投来的那句话怎么安置（留头一句，第二句不覆不存）；
 * 五、逆着捋回来该数出几阶（replay 数给的为准）。
 * 追溯串的读法也在本类，旁处不另立尺子。引擎不落库、不取时刻，只改入参状态、追加待写事件。
 *
 * @author fuce
 * @date 2026-10-06
 */
public final class CaseFlowEngine {

    private CaseFlowEngine() {
    }

    /** 四档钉死，没有旁路 */
    public static final int STAGE_ACCEPT = 0;
    public static final int STAGE_LIMIT = 1;
    public static final int STAGE_MEASURE = 2;
    public static final int STAGE_RELEASE = 3;
    public static final int STAGE_MIN = STAGE_ACCEPT;
    public static final int STAGE_MAX = STAGE_RELEASE;

    /** 0未起 1在办 2已封住 */
    public static final int STATUS_DRAFT = 0;
    public static final int STATUS_ACTIVE = 1;
    public static final int STATUS_CLOSED = 2;

    /** 收口情形 1走满末档 2递件人收回 3分区并走 */
    public static final int CLOSE_FULL = 1;
    public static final int CLOSE_WITHDRAW = 2;
    public static final int CLOSE_ZONE = 3;

    /** 纳入受限档核收 1可 2不可 */
    public static final int LIMIT_PASS_YES = 1;
    public static final int LIMIT_PASS_NO = 2;

    /** 履历动作名（挪档与留痕同一套字，两处口径一致） */
    public static final String ACT_OPEN = "OPEN";
    public static final String ACT_NOTE = "NOTE";
    public static final String ACT_ACCEPT = "ACCEPT";
    public static final String ACT_WITNESS = "WITNESS";
    public static final String ACT_VOUCHER = "VOUCHER";
    public static final String ACT_ADVANCE = "ADVANCE";
    public static final String ACT_ROLLBACK = "ROLLBACK";
    public static final String ACT_CLOSE = "CLOSE";

    /** 起件四要件栏名（报错逐一点名，不许笼统一句「信息不全」） */
    public static final String FIELD_PLOT_NO = "地块码";
    public static final String FIELD_ZONE_NO = "所属分区代号";
    public static final String FIELD_CD_VALUE = "土壤镉实测值";
    public static final String FIELD_MEASURE = "拟采用措施名目";

    /** 门槛外部取数：底册挂名、分区在册与否 */
    public interface GateContext {
        /** 地块在底册上是否还挂着名（在册、未删） */
        boolean plotRegistered(String plotNo);

        /** 分区册情形：0在册 1已撤出/并走；查无册行返回 null */
        Integer zoneStatus(String zoneNo);
    }

    /** 引擎落定的一笔待写履历，seq/时刻/落笔者由持久层在事务里补上 */
    public static class Event {
        public final int stage;
        public final String action;
        public final String noteText;
        public final Integer closeKind;

        public Event(int stage, String action, String noteText, Integer closeKind) {
            this.stage = stage;
            this.action = action;
            this.noteText = noteText;
            this.closeKind = closeKind;
        }
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String stageName(int stage) {
        switch (stage) {
            case STAGE_ACCEPT:
                return "初核受理";
            case STAGE_LIMIT:
                return "纳入受限";
            case STAGE_MEASURE:
                return "措施核查";
            case STAGE_RELEASE:
                return "解除复核";
            default:
                return "第" + (stage + 1) + "档";
        }
    }

    /** 一问之一：起件四要件逐样点名缺哪样；齐全返回空单 */
    public static List<String> missingOpenFields(String plotNo, String zoneNo, BigDecimal cdValue, String measureName) {
        List<String> missing = new ArrayList<>();
        if (blank(plotNo)) {
            missing.add(FIELD_PLOT_NO);
        }
        if (blank(zoneNo)) {
            missing.add(FIELD_ZONE_NO);
        }
        if (cdValue == null) {
            missing.add(FIELD_CD_VALUE);
        }
        if (blank(measureName)) {
            missing.add(FIELD_MEASURE);
        }
        return missing;
    }

    /** 起件：四样齐身才起得来；落在头一档，在办 */
    public static List<Event> open(TCtldCaseFlow c, String plotNo, String zoneNo, BigDecimal cdValue,
                                   String measureName, String submitter) {
        List<String> missing = missingOpenFields(plotNo, zoneNo, cdValue, measureName);
        if (!missing.isEmpty()) {
            throw new CaseFlowException("起件缺件，缺的是：" + String.join("、", missing) + "；不受理");
        }
        c.setPlotNo(plotNo.trim());
        c.setZoneNo(zoneNo.trim());
        c.setCdValue(cdValue);
        c.setMeasureName(measureName.trim());
        c.setSubmitter(blank(submitter) ? null : submitter.trim());
        c.setStage(STAGE_ACCEPT);
        c.setStatus(STATUS_ACTIVE);
        c.setCloseKind(null);
        c.setStageNote(null);
        c.setLimitPass(null);
        c.setWitness1(null);
        c.setWitness2(null);
        c.setVoucherNo(null);
        c.setEnterCount(1);
        List<Event> events = new ArrayList<>();
        events.add(new Event(STAGE_ACCEPT, ACT_OPEN, null, null));
        return events;
    }

    private static void requireActive(TCtldCaseFlow c) {
        if (c.getStatus() != null && c.getStatus() == STATUS_CLOSED) {
            throw new CaseFlowException("本件已封住（第" + (c.getStage() + 1) + "档·" + closeName(c.getCloseKind())
                    + "），封口只读不动，添按语、更正、抽回一概不接");
        }
    }

    private static String closeName(Integer closeKind) {
        if (closeKind == null) {
            return "未收口";
        }
        switch (closeKind) {
            case CLOSE_FULL:
                return "走满末档";
            case CLOSE_WITHDRAW:
                return "递件人收回";
            case CLOSE_ZONE:
                return "分区并走";
            default:
                return "未知收口";
        }
    }

    private static int stageOf(TCtldCaseFlow c) {
        return c.getStage() == null ? STAGE_MIN : c.getStage();
    }

    /**
     * 一问之四：同档第二次塞按语——行面留头一句，第二句既盖不上去、也不另存副本，整笔不采信。
     */
    public static List<Event> note(TCtldCaseFlow c, String note) {
        requireActive(c);
        if (blank(note)) {
            throw new CaseFlowException("第" + (stageOf(c) + 1) + "档（" + stageName(stageOf(c))
                    + "）按语为空，无处可落");
        }
        if (!blank(c.getStageNote())) {
            throw new CaseFlowException("第" + (stageOf(c) + 1) + "档（" + stageName(stageOf(c))
                    + "）按语栏已留有头一句，第二句不盖头一句、也不另存副本，本笔不采信；件仍停在原档");
        }
        c.setStageNote(note.trim());
        List<Event> events = new ArrayList<>();
        events.add(new Event(stageOf(c), ACT_NOTE, note.trim(), null));
        return events;
    }

    /** 纳入受限档的核收落「可/不可」：不记成「可」，下一道门不开 */
    public static List<Event> acceptLimit(TCtldCaseFlow c, boolean pass) {
        requireActive(c);
        if (stageOf(c) != STAGE_LIMIT) {
            throw new CaseFlowException("核收只在第2档（纳入受限）落；本件当下在第" + (stageOf(c) + 1)
                    + "档（" + stageName(stageOf(c)) + "），不采信，件留原地");
        }
        c.setLimitPass(pass ? LIMIT_PASS_YES : LIMIT_PASS_NO);
        List<Event> events = new ArrayList<>();
        events.add(new Event(STAGE_LIMIT, ACT_ACCEPT, pass ? "核收：可" : "核收：不可", null));
        return events;
    }

    /** 解除复核要两名见证岗各落一笔，同人同岗不许顶两笔 */
    public static List<Event> signWitness(TCtldCaseFlow c, String post) {
        requireActive(c);
        if (stageOf(c) != STAGE_MEASURE) {
            throw new CaseFlowException("见证签字只在第3档（措施核查）往解除复核门槛上落；本件当下在第"
                    + (stageOf(c) + 1) + "档（" + stageName(stageOf(c)) + "），不采信，件留原地");
        }
        if (blank(post)) {
            throw new CaseFlowException("见证岗名空着，落不了笔；缺的是：见证岗署名");
        }
        String p = post.trim();
        if (p.equals(c.getWitness1()) || p.equals(c.getWitness2())) {
            throw new CaseFlowException("见证岗「" + p + "」已落过一笔，两名见证须各是各的岗，不许同人顶两笔");
        }
        List<Event> events = new ArrayList<>();
        if (blank(c.getWitness1())) {
            c.setWitness1(p);
        } else if (blank(c.getWitness2())) {
            c.setWitness2(p);
        } else {
            throw new CaseFlowException("两名见证岗已落齐，不再收第三笔");
        }
        events.add(new Event(STAGE_MEASURE, ACT_WITNESS, "见证：" + p, null));
        return events;
    }

    /** 措施核验凭证那一串写回本行 */
    public static List<Event> fillVoucher(TCtldCaseFlow c, String voucherNo) {
        requireActive(c);
        if (stageOf(c) != STAGE_MEASURE) {
            throw new CaseFlowException("凭证串只在第3档（措施核查）写回；本件当下在第" + (stageOf(c) + 1)
                    + "档（" + stageName(stageOf(c)) + "），不采信，件留原地");
        }
        if (blank(voucherNo)) {
            throw new CaseFlowException("凭证串空着，写不回本行；缺的是：措施核验凭证串");
        }
        c.setVoucherNo(voucherNo.trim());
        List<Event> events = new ArrayList<>();
        events.add(new Event(STAGE_MEASURE, ACT_VOUCHER, null, null));
        return events;
    }

    /**
     * 一问之二：下一阶放不放行。返回 null 为放行；否则给出挡门的那一条。
     * 一次只跨一档；门槛依次为：底册挂名且分区在册 → 核收「可」 → 两名见证+凭证串。
     */
    public static String canAdvance(TCtldCaseFlow c, GateContext gates) {
        if (c.getStatus() != null && c.getStatus() == STATUS_CLOSED) {
            return "本件已封住，封口只读不动，不再往前挪";
        }
        int s = stageOf(c);
        if (s >= STAGE_MAX) {
            return "已在末档（解除复核），无前可挪";
        }
        if (s == STAGE_ACCEPT) {
            if (gates == null || !gates.plotRegistered(c.getPlotNo())) {
                return "地块「" + c.getPlotNo() + "」在底册上没挂着名，纳入受限这一档进不去";
            }
            Integer zs = gates.zoneStatus(c.getZoneNo());
            if (zs == null) {
                return "所属分区代号「" + c.getZoneNo() + "」在分区册里查无此分区，纳入受限这一档进不去";
            }
            if (zs == 1) {
                return "所属分区「" + c.getZoneNo() + "」已从分区册并走撤出，并走撤出的进不去";
            }
            return null;
        }
        if (s == STAGE_LIMIT) {
            if (c.getLimitPass() == null || c.getLimitPass() != LIMIT_PASS_YES) {
                return "纳入受限档的核收没记成「可」，措施核查不许开工";
            }
            return null;
        }
        if (s == STAGE_MEASURE) {
            List<String> lack = new ArrayList<>();
            if (blank(c.getWitness1())) {
                lack.add("见证岗第一笔");
            }
            if (blank(c.getWitness2())) {
                lack.add("见证岗第二笔");
            }
            if (blank(c.getVoucherNo())) {
                lack.add("措施核验凭证串");
            }
            if (!lack.isEmpty()) {
                return "解除复核门槛未闭，缺的是：" + String.join("、", lack);
            }
            return null;
        }
        return "无前可挪";
    }

    /** 推进：只跨一档；跨进末档即闭上（走满末档） */
    public static List<Event> advance(TCtldCaseFlow c, GateContext gates) {
        String block = canAdvance(c, gates);
        if (block != null) {
            throw new CaseFlowException(block + "；本笔越档不采信，件仍停在第" + (stageOf(c) + 1) + "档");
        }
        int from = stageOf(c);
        int to = from + 1;
        // 离开本档，本档按语已压在履历里；新档从头累计
        c.setStageNote(null);
        c.setStage(to);
        List<Event> events = new ArrayList<>();
        events.add(new Event(to, ACT_ADVANCE, null, null));
        if (to == STAGE_MAX) {
            // 两样齐了跨进末档，走满末档即闭上
            c.setStatus(STATUS_CLOSED);
            c.setCloseKind(CLOSE_FULL);
            events.add(new Event(to, ACT_CLOSE, null, CLOSE_FULL));
        }
        return events;
    }

    /**
     * 后收：一次只收一档。被收那一档先前留下的一句按语，照原先后转进追溯串末尾；
     * 重新挪回这一档时从头累计。已在头一档，没得再收。
     */
    public static List<Event> rollback(TCtldCaseFlow c) {
        requireActive(c);
        int s = stageOf(c);
        if (s <= STAGE_MIN) {
            throw new CaseFlowException("已在头一档（初核受理），没得再收；件留原地");
        }
        int to = s - 1;
        String withdrawnNote = c.getStageNote();
        List<Event> events = new ArrayList<>();
        // 退出笔记在被收走的那一档（源档）：退出笔数记源档，按语原文也属源档，照原先后转进追溯串末尾
        events.add(new Event(s, ACT_ROLLBACK, withdrawnNote, null));
        // 被收档位上的门槛落痕一并从头累计
        if (s == STAGE_LIMIT) {
            c.setLimitPass(null);
        }
        if (s == STAGE_MEASURE) {
            c.setWitness1(null);
            c.setWitness2(null);
            c.setVoucherNo(null);
        }
        c.setStageNote(null);
        c.setStage(to);
        return events;
    }

    /** 视同收口之一：递件人本人把反映收回去 */
    public static List<Event> withdraw(TCtldCaseFlow c, String operator) {
        requireActive(c);
        if (blank(c.getSubmitter()) || !c.getSubmitter().equals(operator)) {
            throw new CaseFlowException("只有递件人本人能收回反映；递件人记的是「" + c.getSubmitter()
                    + "」，来人是「" + operator + "」，不采信");
        }
        return close(c, CLOSE_WITHDRAW, "递件人收回反映");
    }

    /** 视同收口之二：所属分区从分区册并走（须确在册上已是撤出态） */
    public static List<Event> closeByZoneMerged(TCtldCaseFlow c, GateContext gates) {
        requireActive(c);
        Integer zs = gates == null ? null : gates.zoneStatus(c.getZoneNo());
        if (zs == null) {
            throw new CaseFlowException("分区册里查无「" + c.getZoneNo() + "」，不能按并走收口");
        }
        if (zs != 1) {
            throw new CaseFlowException("分区「" + c.getZoneNo() + "」仍在册，未并走，不能按并走收口；件留第"
                    + (stageOf(c) + 1) + "档");
        }
        return close(c, CLOSE_ZONE, "所属分区并走撤出");
    }

    private static List<Event> close(TCtldCaseFlow c, int kind, String note) {
        c.setStatus(STATUS_CLOSED);
        c.setCloseKind(kind);
        List<Event> events = new ArrayList<>();
        events.add(new Event(stageOf(c), ACT_CLOSE, note, kind));
        return events;
    }

    /** 一问之三：封住的行吞不吞新按语——不吞。更正内容、整条抽回同样问这一处。 */
    public static boolean isClosed(TCtldCaseFlow c) {
        return c.getStatus() != null && c.getStatus() == STATUS_CLOSED;
    }

    /** 一问之三：封住的行不张口；要动作就抛点名为哪一桩的错 */
    public static void requireNotClosedForEdit(TCtldCaseFlow c, String actionName) {
        if (isClosed(c)) {
            throw new CaseFlowException("本件已封住，" + actionName + "不接；封口行在册面照常露脸，只读不动");
        }
    }
}
