package com.fc.v2.service.ctld;

import java.util.Date;

/**
 * 回放时一档一档的快照：这一档何时进来、留了什么话、门槛各栏齐不齐。
 *
 * @author fuce
 * @date 2026-10-06
 */
public class StageView {

    private int stage;
    private String stageName;
    /** 是否真正进来过（由履历里的 OPEN/ADVANCE 判定，不信行面 stage） */
    private boolean entered;
    private Date enteredAt;
    /** 这一档留下的那一句按语原文 */
    private String note;
    /** 本档核收：null无 / 1可 / 2不可 */
    private Integer limitPass;
    private String witness1;
    private String witness2;
    private String voucherNo;
    /** 进来几笔（OPEN/ADVANCE 落本档的次数） */
    private int entryCount;
    /** 退出几笔（ROLLBACK 出本档的次数） */
    private int exitCount;

    public int getStage() {
        return stage;
    }

    public void setStage(int stage) {
        this.stage = stage;
    }

    public String getStageName() {
        return stageName;
    }

    public void setStageName(String stageName) {
        this.stageName = stageName;
    }

    public boolean isEntered() {
        return entered;
    }

    public void setEntered(boolean entered) {
        this.entered = entered;
    }

    public Date getEnteredAt() {
        return enteredAt;
    }

    public void setEnteredAt(Date enteredAt) {
        this.enteredAt = enteredAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Integer getLimitPass() {
        return limitPass;
    }

    public void setLimitPass(Integer limitPass) {
        this.limitPass = limitPass;
    }

    public String getWitness1() {
        return witness1;
    }

    public void setWitness1(String witness1) {
        this.witness1 = witness1;
    }

    public String getWitness2() {
        return witness2;
    }

    public void setWitness2(String witness2) {
        this.witness2 = witness2;
    }

    public String getVoucherNo() {
        return voucherNo;
    }

    public void setVoucherNo(String voucherNo) {
        this.voucherNo = voucherNo;
    }

    public int getEntryCount() {
        return entryCount;
    }

    public void setEntryCount(int entryCount) {
        this.entryCount = entryCount;
    }

    public int getExitCount() {
        return exitCount;
    }

    public void setExitCount(int exitCount) {
        this.exitCount = exitCount;
    }
}
