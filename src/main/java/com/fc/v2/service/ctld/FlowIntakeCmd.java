package com.fc.v2.service.ctld;

import java.util.List;

/**
 * 一拨上送的入参命令。
 * 交法固定：整拨一次递齐（rawLines 整段报文，按切法在后端切），零散逐行不收。
 *
 * @author fuce
 * @date 2026-10-07
 */
public class FlowIntakeCmd {

    /** 批次码：同一批次码走两回（镇级上报、县局复核）各留一份流水 */
    private String batchNo;
    /** 期数 1镇级上报 2县局复核；不传按镇级上报认 */
    private Integer periodNo;
    /** 拨头抬头那一格原文（容行上限唯一出处） */
    private String declaredCap;
    /** 报文切法：DELIM 按分隔符 / FIXED 按定宽，一拨之内只认一种 */
    private String parseKind;
    /** DELIM 用的分隔符 */
    private String delimiter;
    /** FIXED 用的各段宽度 */
    private int[] widths;
    /** 整段报文（按行排好） */
    private List<String> rawLines;
    /** 来路（县农技中心直报／镇级补报），随行落账 */
    private String putKind;

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public Integer getPeriodNo() {
        return periodNo;
    }

    public void setPeriodNo(Integer periodNo) {
        this.periodNo = periodNo;
    }

    public String getDeclaredCap() {
        return declaredCap;
    }

    public void setDeclaredCap(String declaredCap) {
        this.declaredCap = declaredCap;
    }

    public String getParseKind() {
        return parseKind;
    }

    public void setParseKind(String parseKind) {
        this.parseKind = parseKind;
    }

    public String getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public int[] getWidths() {
        return widths;
    }

    public void setWidths(int[] widths) {
        this.widths = widths;
    }

    public List<String> getRawLines() {
        return rawLines;
    }

    public void setRawLines(List<String> rawLines) {
        this.rawLines = rawLines;
    }

    public String getPutKind() {
        return putKind;
    }

    public void setPutKind(String putKind) {
        this.putKind = putKind;
    }
}
