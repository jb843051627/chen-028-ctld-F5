package com.fc.v2.service.ctld;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 土壤-农产品协同检测流水「四节核收」纯引擎：四问只此一处给口径。
 * 一、这一拨进来后各行落到什么下场（{@link #decideRow}）；
 * 二、同一个批次码重到门口怎么办（service 照头一拨回话，引擎只管按期分拨，见 periodNo）；
 * 三、整拨成不成（逐行定，个别行退回不拖整拨，已收的不回未勾）；
 * 四、拨头上三个数（几行进门/几行收下/几行退回）从哪出来——
 *     只认 {@link #intake} 同一次点算返回的那份，旁处不另起一回去数。
 * 引擎不落库、不取时刻、不发号，只把报文切成行、把行定成去处、当场轧出三栏。
 *
 * @author fuce
 * @date 2026-10-07
 */
public final class FlowIntakeEngine {

    private FlowIntakeEngine() {
    }

    /** 抬头上限缺省：那一格空着按 300 认 */
    public static final int DEFAULT_CAP = 300;
    /** 抬头写成「大得离谱」的界：超过视同没写，仍按 300 认 */
    public static final int CAP_ABSURD = 100_000;

    /** 行的去处只有三种写法，第四种落不了库 */
    public static final int STATUS_UNCHECKED = 0;
    public static final int STATUS_ACCEPTED = 1;
    public static final int STATUS_REJECTED = 2;

    /** 期数：同一批次码走两回，两回各留一份流水，并起来才对得平 */
    public static final int PERIOD_TOWN = 1;
    public static final int PERIOD_COUNTY = 2;

    /** 样别 */
    public static final String SAMPLE_SOIL = "SOIL";
    public static final String SAMPLE_PRODUCT = "PRODUCT";

    /** 报文切法：一拨之内只认一种 */
    public static final String PARSE_DELIM = "DELIM";
    public static final String PARSE_FIXED = "FIXED";

    /** 字段位次（分隔符式与定宽式同一套字段序）：
     * 0样别 1收获季 2地块码 3报文题名 4报文分区 5送检样品数 6配对达标数 7土壤镉实测数 8原报行次(可空) */
    public static final int FIELD_COUNT = 8;
    public static final int FIELD_COUNT_WITH_ORIG = 9;
    /** 地块码长规范：不足/超出都不认得 */
    public static final int CODE_MIN_LEN = 4;
    public static final int CODE_MAX_LEN = 32;

    // ========================= 退回条款（写明为哪一条退，逐行追溯） =========================
    public static final String REJ_CD_OVER_WARN = "CD_OVER_WARN";
    public static final String REJ_QTY_ZERO = "QTY_ZERO";
    public static final String REJ_QTY_NEGATIVE = "QTY_NEGATIVE";
    public static final String REJ_CODE_EMPTY = "CODE_EMPTY";
    public static final String REJ_CODE_TOO_SHORT = "CODE_TOO_SHORT";
    public static final String REJ_CODE_TOO_LONG = "CODE_TOO_LONG";

    public static final String MSG_CD_OVER_WARN = "土壤镉实测数高过当地告警线";
    public static final String MSG_QTY_ZERO = "送检样品数为0";
    public static final String MSG_QTY_NEGATIVE = "送检样品数为负";
    public static final String MSG_CODE_EMPTY = "地块码没填";
    public static final String MSG_CODE_TOO_SHORT = "地块码长不足规范";
    public static final String MSG_CODE_TOO_LONG = "地块码长超出规范";

    // ========================= 异常标注（不进门，单列给回核，不许按住整拨） =========================
    /** 切不开：分隔符切不够刀数／定宽不够宽／数值不成数／样别不成样 */
    public static final String MARK_SPLIT = "UNSPLITTABLE";
    /** 整段带不上编码：换页符混进来、全宽半宽混着用 */
    public static final String MARK_ENCODING = "ENCODING";
    /** 送检数与达标数只到一个：不代填、不按 0 认 */
    public static final String MARK_HALF_NUM = "HALF_NUM";
    /** 行面题名/分区与底册带出的那份比对不合（行仍按码照常定去处，另列一单） */
    public static final String MARK_TITLE_MISMATCH = "TITLE_MISMATCH";

    /** 底册取数：地块题名/分区只从这里带，告警线也问它 */
    public interface BookGate {
        /** 按地块码精确取底册行；题名近似不算、查无返回 null */
        BookInfo lookup(String plotNo);

        /** 当地（地块所属分区/收获季）那条土壤镉告警线；当地没立线返回 null（不拿无中生出的线退人） */
        BigDecimal warnLine(String plotNo, String zoneNo, String harvestSeason);
    }

    /** 底册带出的一份：行面题名与分区代号一律以它作准 */
    public static class BookInfo {
        private final String title;
        private final String zoneNo;

        public BookInfo(String title, String zoneNo) {
            this.title = title;
            this.zoneNo = zoneNo;
        }

        public String getTitle() {
            return title;
        }

        public String getZoneNo() {
            return zoneNo;
        }
    }

    /** 报文切法：一拨之内认一种，不许多数行用分隔符、少数行用定宽 */
    public static class ParseSpec {
        private final String kind;
        private final String delimiter;
        private final int[] widths;

        private ParseSpec(String kind, String delimiter, int[] widths) {
            this.kind = kind;
            this.delimiter = delimiter;
            this.widths = widths;
        }

        public static ParseSpec delim(String delimiter) {
            if (delimiter == null || delimiter.isEmpty()) {
                throw new IllegalArgumentException("分隔符空着，按分隔符切就切不成");
            }
            return new ParseSpec(PARSE_DELIM, delimiter, null);
        }

        public static ParseSpec fixed(int[] widths) {
            if (widths == null || widths.length < FIELD_COUNT) {
                throw new IllegalArgumentException("定宽切法至少要给齐" + FIELD_COUNT + "段宽度");
            }
            int[] copy = new int[widths.length];
            int sum = 0;
            for (int i = 0; i < widths.length; i++) {
                if (widths[i] <= 0) {
                    throw new IllegalArgumentException("第" + (i + 1) + "段定宽不是正数");
                }
                copy[i] = widths[i];
                sum += widths[i];
            }
            ParseSpec s = new ParseSpec(PARSE_FIXED, null, copy);
            s.totalWidth = sum;
            return s;
        }

        private int totalWidth;

        public String getKind() {
            return kind;
        }
    }

    /** 切出来且两个数齐了的行，待判定去处 */
    public static class ParsedRow {
        public String sampleKind;
        public String harvestSeason;
        public String plotCode;
        public String msgTitle;
        public String msgZoneNo;
        public BigDecimal qty;
        public BigDecimal chkNum;
        public BigDecimal cdValue;
        public Integer origRowNo;
    }

    /** 一行定下来的去处与落库所需各栏；三栏与它同一次点算 */
    public static class RowDecision {
        private int rowNo;
        private Integer origRowNo;
        private String sampleKind;
        private String harvestSeason;
        private String plotCode;
        private String msgTitle;
        private String msgZoneNo;
        private String bookTitle;
        private String bookZoneNo;
        private BigDecimal qty;
        private BigDecimal chkNum;
        private BigDecimal cdValue;
        private int status;
        private String rejectCode;
        private String rejectMsg;
        /** 未勾行的来由（码底册查无）；收下/退回行为空 */
        private String pendingNote;

        public int getRowNo() {
            return rowNo;
        }

        public Integer getOrigRowNo() {
            return origRowNo;
        }

        public String getSampleKind() {
            return sampleKind;
        }

        public String getHarvestSeason() {
            return harvestSeason;
        }

        public String getPlotCode() {
            return plotCode;
        }

        public String getMsgTitle() {
            return msgTitle;
        }

        public String getMsgZoneNo() {
            return msgZoneNo;
        }

        public String getBookTitle() {
            return bookTitle;
        }

        public String getBookZoneNo() {
            return bookZoneNo;
        }

        public BigDecimal getQty() {
            return qty;
        }

        public BigDecimal getChkNum() {
            return chkNum;
        }

        public BigDecimal getCdValue() {
            return cdValue;
        }

        public int getStatus() {
            return status;
        }

        public String getRejectCode() {
            return rejectCode;
        }

        public String getRejectMsg() {
            return rejectMsg;
        }

        public String getPendingNote() {
            return pendingNote;
        }
    }

    /** 异常行标注：不进门、不进三栏，递给回核看的人 */
    public static class RowMark {
        private final Integer rowNo;
        private final String markKind;
        private final String detail;
        private final String raw;

        public RowMark(Integer rowNo, String markKind, String detail, String raw) {
            this.rowNo = rowNo;
            this.markKind = markKind;
            this.detail = detail;
            this.raw = raw;
        }

        public Integer getRowNo() {
            return rowNo;
        }

        public String getMarkKind() {
            return markKind;
        }

        public String getDetail() {
            return detail;
        }

        public String getRaw() {
            return raw;
        }
    }

    /** 一拨点算完的结果：行去处、标注、三栏同出此处 */
    public static class BatchOutcome {
        private final int recvSeq;
        private final int lineCap;
        private final List<RowDecision> rows;
        private final List<RowMark> marks;
        /** 进门三栏：头一栏只点已落定（收下/退回）的行；未勾悬行在待核单另计，不混进三栏 */
        private final int totalRows;
        private final int acceptedRows;
        private final int rejectedRows;
        /** 去处空悬在未勾的行数（单列待核清单，系统不替它收） */
        private final int uncheckedRows;
        private final boolean empty;
        private final String receiptText;

        BatchOutcome(int recvSeq, int lineCap, List<RowDecision> rows, List<RowMark> marks,
                     int totalRows, int acceptedRows, int rejectedRows, int uncheckedRows,
                     boolean empty, String receiptText) {
            this.recvSeq = recvSeq;
            this.lineCap = lineCap;
            this.rows = rows;
            this.marks = marks;
            this.totalRows = totalRows;
            this.acceptedRows = acceptedRows;
            this.rejectedRows = rejectedRows;
            this.uncheckedRows = uncheckedRows;
            this.empty = empty;
            this.receiptText = receiptText;
        }

        public int getRecvSeq() {
            return recvSeq;
        }

        public int getLineCap() {
            return lineCap;
        }

        public List<RowDecision> getRows() {
            return rows;
        }

        public List<RowMark> getMarks() {
            return marks;
        }

        public int getTotalRows() {
            return totalRows;
        }

        public int getAcceptedRows() {
            return acceptedRows;
        }

        public int getRejectedRows() {
            return rejectedRows;
        }

        public int getUncheckedRows() {
            return uncheckedRows;
        }

        public boolean isEmpty() {
            return empty;
        }

        public String getReceiptText() {
            return receiptText;
        }
    }

    /** 一次递交点算完：可能因到顶接成一串拨 */
    public static class IntakeResult {
        private final String batchNo;
        private final int periodNo;
        private final int lineCap;
        private final String declaredCapRaw;
        private final List<BatchOutcome> batches;

        IntakeResult(String batchNo, int periodNo, int lineCap, String declaredCapRaw,
                     List<BatchOutcome> batches) {
            this.batchNo = batchNo;
            this.periodNo = periodNo;
            this.lineCap = lineCap;
            this.declaredCapRaw = declaredCapRaw;
            this.batches = batches;
        }

        public String getBatchNo() {
            return batchNo;
        }

        public int getPeriodNo() {
            return periodNo;
        }

        public int getLineCap() {
            return lineCap;
        }

        public String getDeclaredCapRaw() {
            return declaredCapRaw;
        }

        public List<BatchOutcome> getBatches() {
            return batches;
        }

        /** 头一拨的回执（屏上那一份只是复述，数据源仍是各拨的三栏） */
        public BatchOutcome firstBatch() {
            return batches.get(0);
        }
    }

    // ========================= 第一节：抬头与分拨 =========================

    /**
     * 抬头上限只有一个出处：抬头那一格。空着、负数、不成数、大得离谱，都视同没写，按 {@value #DEFAULT_CAP} 认。
     * 返回的 [0] 是归一后的上限；原文由调用方原样留存，日后翻得出当初报了几行/写了什么。
     */
    public static int normalizeCap(String declaredCapRaw) {
        if (declaredCapRaw == null) {
            return DEFAULT_CAP;
        }
        String s = declaredCapRaw.trim();
        if (s.isEmpty()) {
            return DEFAULT_CAP;
        }
        try {
            int v = new BigDecimal(s).intValueExact();
            if (v <= 0 || v > CAP_ABSURD) {
                return DEFAULT_CAP;
            }
            return v;
        } catch (NumberFormatException notANumber) {
            return DEFAULT_CAP;
        } catch (ArithmeticException notAnInteger) {
            return DEFAULT_CAP;
        }
    }

    // ========================= 第三节：报文解析（一拨一种切法） =========================

    private static boolean hasFormFeed(String raw) {
        return raw.indexOf('') >= 0;
    }

    /** 全宽数字（U+FF10..FF19）混进来，或全宽英文字母混进码槽：全宽半宽混用，编码带不上 */
    private static boolean hasFullWidthMix(String raw, String codeSlot) {
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch >= '０' && ch <= '９') {
                return true;
            }
        }
        if (codeSlot != null) {
            for (int i = 0; i < codeSlot.length(); i++) {
                char ch = codeSlot.charAt(i);
                if (ch >= 'Ａ' && ch <= 'ｚ') {
                    return true;
                }
            }
        }
        return false;
    }

    private static String[] splitBySpec(String raw, ParseSpec spec) {
        if (PARSE_DELIM.equals(spec.kind)) {
            // -1 保留尾空刀：少一刀就是切不开，不许悄悄丢掉
            return raw.split(java.util.regex.Pattern.quote(spec.delimiter), -1);
        }
        int n = spec.widths.length;
        String[] tokens = new String[n];
        int pos = 0;
        for (int i = 0; i < n; i++) {
            int end = pos + spec.widths[i];
            tokens[i] = end > raw.length() ? raw.substring(pos) : raw.substring(pos, end);
            pos = end;
        }
        return tokens;
    }

    private static BigDecimal asNumber(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return null;
        }
        return new BigDecimal(t);
    }

    private static String normalizeSampleKind(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (SAMPLE_SOIL.equals(t) || "土壤".equals(t) || "土壤样".equals(t)) {
            return SAMPLE_SOIL;
        }
        if (SAMPLE_PRODUCT.equals(t) || "农产品".equals(t) || "农产品样".equals(t)) {
            return SAMPLE_PRODUCT;
        }
        return null;
    }

    /**
     * 切一行。切不开/带不上编码/两个数只到一个的，不出 ParsedRow，出 RowMark——
     * 这类行不进门，另做标注递给回核，但绝不能因它把整拨按住。
     *
     * @param rawRowNo 报文里这一行排第几个（仅供标注点名，进门行的正式行次另在定去处时排）
     */
    public static Object parseLine(String raw, ParseSpec spec, int rawRowNo) {
        if (raw == null) {
            raw = "";
        }
        // 编码这一关先过：换页符、全宽数字整段不认
        String codeSlotGuess = guessCodeSlot(raw, spec);
        if (hasFormFeed(raw)) {
            return new RowMark(rawRowNo, MARK_ENCODING, "换页符混进来，整段带不上编码", raw);
        }
        if (hasFullWidthMix(raw, codeSlotGuess)) {
            return new RowMark(rawRowNo, MARK_ENCODING, "全宽半宽混着用，整段带不上编码", raw);
        }

        String[] tokens;
        try {
            tokens = splitBySpec(raw, spec);
        } catch (RuntimeException e) {
            return new RowMark(rawRowNo, MARK_SPLIT, "切报文时出错：" + e.getMessage(), raw);
        }
        if (tokens.length < FIELD_COUNT || tokens.length > FIELD_COUNT_WITH_ORIG) {
            return new RowMark(rawRowNo, MARK_SPLIT,
                    "刀数不对：应切出" + FIELD_COUNT + "（或带原报行次" + FIELD_COUNT_WITH_ORIG
                            + "）段，实际" + tokens.length + "段", raw);
        }
        // 定宽式：最后一段都不够宽，也算切不开
        if (PARSE_FIXED.equals(spec.kind) && raw.length() < spec.totalWidth) {
            return new RowMark(rawRowNo, MARK_SPLIT,
                    "定宽不够宽：应满" + spec.totalWidth + "字，实际" + raw.length() + "字", raw);
        }

        String kind = normalizeSampleKind(tokens[0]);
        if (kind == null) {
            return new RowMark(rawRowNo, MARK_SPLIT, "样别不成样（只认土壤样/农产品样）：" + tokens[0], raw);
        }

        BigDecimal qty;
        BigDecimal chk;
        BigDecimal cd;
        Integer origRowNo = null;
        try {
            qty = asNumber(tokens[5]);
            chk = asNumber(tokens[6]);
            cd = asNumber(tokens[7]);
            if (tokens.length == FIELD_COUNT_WITH_ORIG && !tokens[8].trim().isEmpty()) {
                origRowNo = Integer.valueOf(tokens[8].trim());
            }
        } catch (NumberFormatException badNumber) {
            return new RowMark(rawRowNo, MARK_SPLIT, "数栏不成数：" + badNumber.getMessage(), raw);
        }

        // 两个数要同时到；只到一个就不进，系统不代填、不按 0 认
        if (qty == null || chk == null) {
            if (qty == null && chk == null) {
                return new RowMark(rawRowNo, MARK_SPLIT, "送检样品数与配对达标数两个都没到", raw);
            }
            String missing = qty == null ? "本行送检样品数" : "本行配对达标数";
            return new RowMark(rawRowNo, MARK_HALF_NUM, "两个数只到一个，缺的是：" + missing, raw);
        }

        ParsedRow row = new ParsedRow();
        row.sampleKind = kind;
        row.harvestSeason = tokens[1].trim();
        row.plotCode = tokens[2].trim();
        row.msgTitle = tokens[3].trim();
        row.msgZoneNo = tokens[4].trim();
        row.qty = qty;
        row.chkNum = chk;
        row.cdValue = cd;
        row.origRowNo = origRowNo;
        return row;
    }

    /** 编码关要先看码槽：分隔符式取第 3 段，定宽式取第 3 段宽，取不到也不打紧（后续按切不开论） */
    private static String guessCodeSlot(String raw, ParseSpec spec) {
        try {
            if (PARSE_DELIM.equals(spec.kind)) {
                String[] t = raw.split(java.util.regex.Pattern.quote(spec.delimiter), -1);
                return t.length >= 3 ? t[2] : null;
            }
            int start = spec.widths[0] + spec.widths[1];
            int end = start + spec.widths[2];
            return raw.length() >= start ? raw.substring(start, Math.min(end, raw.length())) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ========================= 第二、四节：逐行定去处 =========================

    /** 五条退回条款的次序：码空→码短→码长→数负→数零→镉超线；中一条即退，逐行只记头一条 */
    public static String rejectReason(ParsedRow row, BookInfo book, BookGate gates) {
        if (row.plotCode.isEmpty()) {
            return REJ_CODE_EMPTY;
        }
        if (row.plotCode.length() < CODE_MIN_LEN) {
            return REJ_CODE_TOO_SHORT;
        }
        if (row.plotCode.length() > CODE_MAX_LEN) {
            return REJ_CODE_TOO_LONG;
        }
        if (row.qty.signum() < 0) {
            return REJ_QTY_NEGATIVE;
        }
        if (row.qty.signum() == 0) {
            return REJ_QTY_ZERO;
        }
        if (row.cdValue != null && book != null) {
            BigDecimal line = gates.warnLine(row.plotCode, book.getZoneNo(), row.harvestSeason);
            if (line != null && row.cdValue.compareTo(line) > 0) {
                return REJ_CD_OVER_WARN;
            }
        }
        return null;
    }

    /** 条款码翻译成回执上那句原话 */
    public static String rejectMessage(String code) {
        switch (code) {
            case REJ_CD_OVER_WARN:
                return MSG_CD_OVER_WARN;
            case REJ_QTY_ZERO:
                return MSG_QTY_ZERO;
            case REJ_QTY_NEGATIVE:
                return MSG_QTY_NEGATIVE;
            case REJ_CODE_EMPTY:
                return MSG_CODE_EMPTY;
            case REJ_CODE_TOO_SHORT:
                return MSG_CODE_TOO_SHORT;
            case REJ_CODE_TOO_LONG:
                return MSG_CODE_TOO_LONG;
            default:
                return code;
        }
    }

    private static RowDecision toDecision(ParsedRow row, int rowNo, BookInfo book, String rejectCode) {
        RowDecision d = new RowDecision();
        d.rowNo = rowNo;
        d.origRowNo = row.origRowNo;
        d.sampleKind = row.sampleKind;
        d.harvestSeason = row.harvestSeason;
        d.plotCode = row.plotCode;
        d.msgTitle = row.msgTitle;
        d.msgZoneNo = row.msgZoneNo;
        d.qty = row.qty;
        d.chkNum = row.chkNum;
        d.cdValue = row.cdValue;
        if (book != null) {
            // 行面题名与分区一律以底册带出的那份作准，报文那串只用来比对
            d.bookTitle = book.getTitle();
            d.bookZoneNo = book.getZoneNo();
        }
        if (rejectCode == null) {
            d.status = STATUS_ACCEPTED;
        } else {
            d.status = STATUS_REJECTED;
            d.rejectCode = rejectCode;
            d.rejectMsg = rejectMessage(rejectCode);
        }
        return d;
    }

    // ========================= 一口气走完：点行数、看数据、定去处、出回执 =========================

    /**
     * 整拨一次点算。rawLines 为报文原文按序排好；到顶即止，多出来的另起一拨接住，不许硬塞。
     * 标注行不进门、不占三栏；切不开/编码/半数/码查无各归各类，整拨不被按住。
     * 一行也没进门（真空拨或全行被标注）也合法：头一拨只落「空拨」，不出收讫，更不许去够集合头一条。
     */
    public static IntakeResult intake(String batchNo, int periodNo, String declaredCapRaw,
                                      ParseSpec spec, List<String> rawLines, BookGate gates) {
        int cap = normalizeCap(declaredCapRaw);
        List<String> lines = rawLines == null ? new ArrayList<>() : rawLines;

        // 第一节先点行数：报文里每一条都点到；点完才知道哪几条进得了门
        List<RowMark> headMarks = new ArrayList<>();
        List<ParsedRow> parsed = new ArrayList<>();
        int rawIdx = 0;
        for (String line : lines) {
            rawIdx++;
            Object out = parseLine(line, spec, rawIdx);
            if (out instanceof RowMark) {
                headMarks.add((RowMark) out);
                continue;
            }
            parsed.add((ParsedRow) out);
        }

        // 定去处 + 到顶分拨。行次由系统在本拨内排上、不留空位：
        // 新行连续排队；吃了退回过几日重报的（带原报行次）行面仍显示当初那一个行次，不重新排队、不占新号位。
        List<BatchOutcome> outcomes = new ArrayList<>();
        int seq = 1;
        int from = 0;
        // 标注挂在头一拨（它是报文点行的产物；溢出拨只接进门行，不挪标注）
        boolean marksAttached = false;
        if (parsed.isEmpty()) {
            outcomes.add(buildEmptyBatch(seq, cap, headMarks));
            return new IntakeResult(batchNo, periodNo, cap, declaredCapRaw, outcomes);
        }
        while (from < parsed.size()) {
            int to = Math.min(from + cap, parsed.size());
            List<RowDecision> decisions = new ArrayList<>();
            List<RowMark> seqMarks = new ArrayList<>();
            if (!marksAttached) {
                seqMarks.addAll(headMarks);
                marksAttached = true;
            }
            int accepted = 0;
            int rejected = 0;
            int unchecked = 0;
            int newRowSeq = 0;
            for (int i = from; i < to; i++) {
                ParsedRow row = parsed.get(i);
                boolean reReport = row.origRowNo != null;
                int rowNo = reReport ? row.origRowNo : ++newRowSeq;
                BookInfo book = row.plotCode.isEmpty() ? null : gates.lookup(row.plotCode);
                if (book == null && !row.plotCode.isEmpty()
                        && row.plotCode.length() >= CODE_MIN_LEN && row.plotCode.length() <= CODE_MAX_LEN) {
                    // 码本身像样、底册却查无：题名近似不算，五条退回也套不上——
                    // 去处空悬在未勾，进待核清单由回核的人逐行定，系统不自作主张替它收；不进三栏
                    RowDecision d = toDecision(row, rowNo, null, null);
                    d.status = STATUS_UNCHECKED;
                    d.pendingNote = "地块码「" + row.plotCode + "」底册查无（只认码、不认题名近似），去处空悬待核";
                    decisions.add(d);
                    unchecked++;
                    continue;
                }
                String reason = rejectReason(row, book, gates);

                // 题名/分区与底册比对：不合另列一单，不改变按码定下来的去处
                if (book != null) {
                    if (!row.msgTitle.isEmpty() && !row.msgTitle.equals(book.getTitle())) {
                        seqMarks.add(new RowMark(rowNo, MARK_TITLE_MISMATCH,
                                "报文题名「" + row.msgTitle + "」与底册带出的「" + book.getTitle()
                                        + "」比对不合，行面以底册为准", null));
                    }
                    if (!row.msgZoneNo.isEmpty() && !row.msgZoneNo.equals(book.getZoneNo())) {
                        seqMarks.add(new RowMark(rowNo, MARK_TITLE_MISMATCH,
                                "报文分区代号「" + row.msgZoneNo + "」与底册带出的「" + book.getZoneNo()
                                        + "」比对不合，行面以底册为准", null));
                    }
                }

                RowDecision d = toDecision(row, rowNo, book, reason);
                decisions.add(d);
                if (d.status == STATUS_ACCEPTED) {
                    accepted++;
                } else {
                    rejected++;
                }
            }
            int settled = accepted + rejected;
            boolean empty = decisions.isEmpty();
            String receipt;
            if (empty) {
                // 一行也没落着：只落「空拨」两个字，收讫那一句不出（标注另递给回核）
                receipt = "空拨";
            } else {
                receipt = "进门" + settled + "行，收下" + accepted + "行，退回" + rejected + "行；三栏同一次点算"
                        + (unchecked > 0 ? "；另有" + unchecked + "行去处空悬，单列待核清单，不计三栏" : "");
            }
            outcomes.add(new BatchOutcome(seq, cap, decisions, seqMarks, settled, accepted, rejected,
                    unchecked, empty, receipt));
            from = to;
            seq++;
        }
        if (outcomes.isEmpty()) {
            // 理论上到不了（parsed 非空至少切一拨），兜底也不许够集合头一条
            outcomes.add(buildEmptyBatch(seq, cap, headMarks));
        }
        return new IntakeResult(batchNo, periodNo, cap, declaredCapRaw, outcomes);
    }

    private static BatchOutcome buildEmptyBatch(int seq, int cap, List<RowMark> marks) {
        // 空拨：只落「空拨」两个字，收讫那一句不出
        return new BatchOutcome(seq, cap, new ArrayList<>(),
                marks == null ? new ArrayList<>() : marks, 0, 0, 0, 0, true, "空拨");
    }
}
