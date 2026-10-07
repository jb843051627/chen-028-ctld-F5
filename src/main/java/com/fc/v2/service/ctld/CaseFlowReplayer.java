package com.fc.v2.service.ctld;

import java.util.ArrayList;
import java.util.List;

import com.fc.v2.model.auto.TCtldCaseFlowTrace;

/**
 * 只读回放：从末档一关一关往回倒着演。
 * 档数、进出笔数、各栏齐缺，一律从追溯串（履历）当场捋，不信行面 stage、不借别件结论；
 * 演完只出结果，绝不写回当下任何栏位，也不给封住的行重新张口。
 *
 * 走法：履历按时序正走一遍（回放出结果时倒序呈现），每「进来一档」开一轮；
 * 门槛在 ADVANCE 离开源档那一刻核——核的是刚走完的这一轮，回退重走开的是新一轮，旧轮结论不借过来。
 *
 * @author fuce
 * @date 2026-10-06
 */
public final class CaseFlowReplayer {

    private CaseFlowReplayer() {
    }

    /** 一档里走一轮（重新挪回本档即另开一轮，按语与门槛从头累计） */
    private static class Round {
        String note;
        Integer limitPass;
        final List<String> witnesses = new ArrayList<>();
        boolean voucher;
    }

    public static class Gap {
        /** 缺在第几档（1..4）；整条级别的对不上号用 0 */
        private final int stage;
        /** 缺的是哪一栏/哪一笔，点名给字 */
        private final String field;
        private final String detail;

        Gap(int stage, String field, String detail) {
            this.stage = stage;
            this.field = field;
            this.detail = detail;
        }

        public int getStage() {
            return stage;
        }

        public String getField() {
            return field;
        }

        public String getDetail() {
            return detail;
        }
    }

    public static class Result {
        /** 从履历倒捋出来当下该停在第几档（0..3） */
        private int tracedStage;
        /** 从履历倒捋出来封住没有 */
        private boolean tracedClosed;
        private Integer closeKind;
        /** 倒着演的各档视图：末档在前；只给最近一轮 */
        private final List<StageView> stagesReverse = new ArrayList<>();
        /** 与行面当下对不对得上号（挪档与留痕口径一致） */
        private boolean rowMatchesTrace;
        /** 缺档/缺栏，逐条点名 */
        private final List<Gap> gaps = new ArrayList<>();
        /** 来件入档总笔数（OPEN+ADVANCE），与当天受理簿一笔一笔轧 */
        private int totalEntries;
        /** 轧账是否自洽：每档 来件入档+回退再入 = 退出+在档 */
        private boolean bookBalanced;

        public int getTracedStage() {
            return tracedStage;
        }

        public boolean isTracedClosed() {
            return tracedClosed;
        }

        public Integer getCloseKind() {
            return closeKind;
        }

        public List<StageView> getStagesReverse() {
            return stagesReverse;
        }

        public boolean isRowMatchesTrace() {
            return rowMatchesTrace;
        }

        public List<Gap> getGaps() {
            return gaps;
        }

        public int getTotalEntries() {
            return totalEntries;
        }

        public boolean isBookBalanced() {
            return bookBalanced;
        }
    }

    private static String stageName(int s) {
        switch (s) {
            case 0:
                return "初核受理";
            case 1:
                return "纳入受限";
            case 2:
                return "措施核查";
            case 3:
                return "解除复核";
            default:
                return "第" + (s + 1) + "档";
        }
    }

    /**
     * 回放。traceAsc 必须按 (create_time, seq) 升序——同一刻两通道各送一笔，两笔各记各的先后，
     * 不许并成一条、不许吞掉一条。rowStage/rowStatus/rowEnterCount 传行面当下，只供对号，不参与倒捋。
     */
    public static Result replay(List<TCtldCaseFlowTrace> traceAsc, Integer rowStage, Integer rowStatus,
                                Integer rowEnterCount) {
        Result r = new Result();

        @SuppressWarnings("unchecked")
        List<Round>[] rounds = new List[4];
        for (int i = 0; i < 4; i++) {
            rounds[i] = new ArrayList<>();
        }
        int[] entries = new int[4];
        int[] rollIns = new int[4];
        int[] exits = new int[4];

        int current = -1;
        Round[] active = new Round[4];
        boolean closed = false;
        Integer closeKind = null;

        for (TCtldCaseFlowTrace t : traceAsc) {
            String act = t.getAction();
            Integer st = t.getStage();
            if (st == null || st < 0 || st > 3) {
                continue;
            }

            if (CaseFlowEngine.ACT_OPEN.equals(act)) {
                Round rd = new Round();
                rounds[0].add(rd);
                active[0] = rd;
                entries[0]++;
                current = 0;
            } else if (CaseFlowEngine.ACT_ADVANCE.equals(act)) {
                // 门槛核在离开源档那一刻，核的是源档刚走完的这一轮
                if (st - 1 >= 0 && active[st - 1] != null) {
                    checkGateTo(r, st, active[st - 1]);
                }
                Round rd = new Round();
                rounds[st].add(rd);
                active[st] = rd;
                entries[st]++;
                if (st - 1 >= 0) {
                    exits[st - 1]++;
                }
                current = st;
            } else if (CaseFlowEngine.ACT_ROLLBACK.equals(act)) {
                // 退出源档、落回目标档；目标档另开新一轮，按语与门槛从头累计，不借旧轮结论
                exits[st]++;
                active[st] = null;
                if (st - 1 >= 0) {
                    Round rd = new Round();
                    rounds[st - 1].add(rd);
                    active[st - 1] = rd;
                    rollIns[st - 1]++;
                }
                current = st - 1;
            } else if (CaseFlowEngine.ACT_CLOSE.equals(act)) {
                closed = true;
                closeKind = t.getCloseKind();
            } else if (current == st && active[st] != null) {
                Round rd = active[st];
                if (CaseFlowEngine.ACT_NOTE.equals(act)) {
                    // 一档只留头一句；二次投来的话服务层根本不会落痕，这里只兜头一句
                    if (rd.note == null) {
                        rd.note = t.getNoteText();
                    }
                } else if (CaseFlowEngine.ACT_ACCEPT.equals(act)) {
                    String n = t.getNoteText();
                    rd.limitPass = (n != null && n.contains("可") && !n.contains("不可"))
                            ? CaseFlowEngine.LIMIT_PASS_YES : CaseFlowEngine.LIMIT_PASS_NO;
                } else if (CaseFlowEngine.ACT_WITNESS.equals(act)) {
                    String w = t.getNoteText() == null ? null
                            : t.getNoteText().replaceFirst("^见证[：:]", "");
                    if (w != null && !rd.witnesses.contains(w)) {
                        rd.witnesses.add(w);
                    }
                } else if (CaseFlowEngine.ACT_VOUCHER.equals(act)) {
                    rd.voucher = true;
                }
            }
        }

        int totalEntries = 0;
        for (int i = 0; i < 4; i++) {
            totalEntries += entries[i];
        }
        r.totalEntries = totalEntries;

        // 轧账：每档 来件入档+回退再入 = 退出+在档(1/0)
        boolean balanced = true;
        for (int i = 0; i < 4; i++) {
            int on = (i == current) ? 1 : 0;
            if (entries[i] + rollIns[i] != exits[i] + on) {
                balanced = false;
                r.gaps.add(new Gap(i + 1, "受理簿笔数轧不平",
                        "第" + (i + 1) + "档（" + stageName(i) + "）来件入档 " + entries[i] + " 笔、回退再入 "
                                + rollIns[i] + " 笔、退出 " + exits[i] + " 笔、在档 " + on
                                + "，入+回退再入≠退出+在档"));
            }
        }
        r.bookBalanced = balanced;

        // 倒着演：末档在头，视图取该档最近一轮
        for (int s = 3; s >= 0; s--) {
            StageView v = new StageView();
            v.setStage(s);
            v.setStageName(stageName(s));
            v.setEntryCount(entries[s]);
            v.setExitCount(exits[s]);
            List<Round> list = rounds[s];
            v.setEntered(!list.isEmpty());
            if (!list.isEmpty()) {
                Round last = list.get(list.size() - 1);
                v.setNote(last.note);
                v.setLimitPass(last.limitPass);
                v.setWitness1(last.witnesses.size() > 0 ? last.witnesses.get(0) : null);
                v.setWitness2(last.witnesses.size() > 1 ? last.witnesses.get(1) : null);
                v.setVoucherNo(last.voucher ? "已写回（凭证明文以本行 voucher_no 为准）" : null);
            }
            r.stagesReverse.add(v);
        }

        r.tracedStage = current < 0 ? 0 : current;
        r.tracedClosed = closed;
        r.closeKind = closeKind;

        if (traceAsc.isEmpty()) {
            r.gaps.add(new Gap(1, "追溯串", "本件一笔履历都没有，受理簿上查无来痕"));
        }

        // 与当下对号：倒捋阶次/封口/来件数必须与行面当场那一步一致，不另摆尺子凑数
        boolean stageMatch = rowStage != null && rowStage == r.tracedStage;
        boolean statusMatch = rowStatus != null
                && (rowStatus == CaseFlowEngine.STATUS_CLOSED) == closed;
        boolean countMatch = rowEnterCount == null || rowEnterCount == totalEntries;
        r.rowMatchesTrace = stageMatch && statusMatch && countMatch;
        if (!stageMatch) {
            r.gaps.add(new Gap(0, "当前档对不上号", "追溯串倒捋停在第" + (r.tracedStage + 1)
                    + "档，行面却记第" + (rowStage == null ? "空" : rowStage + 1) + "档，两处摆不齐"));
        }
        if (!statusMatch) {
            r.gaps.add(new Gap(0, "封口对不上号", "追溯串倒捋" + (closed ? "已封住" : "未封住")
                    + "，行面状态却不同"));
        }
        if (!countMatch) {
            r.gaps.add(new Gap(0, "来件笔数对不上号", "追溯串来件入档 " + totalEntries + " 笔，行面 enter_count 记 "
                    + rowEnterCount + "，与当天受理簿合不上"));
        }
        return r;
    }

    /**
     * 离开源档进目标档时，核源档这一轮门槛闭没闭；没闭却有 ADVANCE 落痕，就是越档/缺栏，逐一点名。
     * @param toStage 正要进入的档 1..3
     */
    private static void checkGateTo(Result r, int toStage, Round leaving) {
        if (toStage == CaseFlowEngine.STAGE_MEASURE) {
            // 离开第2档（纳入受限）进第3档：核收必须已记「可」
            if (leaving.limitPass == null) {
                r.gaps.add(new Gap(2, "核收", "第2档（纳入受限）缺：受限档核收结论栏，却已跨进第3档"));
            } else if (leaving.limitPass != CaseFlowEngine.LIMIT_PASS_YES) {
                r.gaps.add(new Gap(2, "核收", "第2档（纳入受限）核收不是「可」，第3档本不该开工"));
            }
        } else if (toStage == CaseFlowEngine.STAGE_RELEASE) {
            // 离开第3档（措施核查）进第4档：两名见证各一笔 + 凭证串写回
            if (leaving.witnesses.isEmpty()) {
                r.gaps.add(new Gap(3, "见证岗第一笔", "第3档（措施核查）缺：见证岗第一笔"));
            }
            if (leaving.witnesses.size() < 2) {
                r.gaps.add(new Gap(3, "见证岗第二笔", "第3档（措施核查）缺：见证岗第二笔"));
            }
            if (!leaving.voucher) {
                r.gaps.add(new Gap(3, "措施核验凭证串", "第3档（措施核查）缺：措施核验凭证串写回"));
            }
        }
    }
}
