package com.fc.v2.service.ctld;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 早班翻单一趟的回话。
 * 趟尾三个数（挑中/递出/未成）只认本处本次返回的这份；页面那一屏只是转述，
 * 内存临时数、另一轮逐行清点都不许拿来回话。同一趟号第二回，本份照头一趟复述，{@link #replayed}=true。
 *
 * @author fuce
 * @date 2026-10-07
 */
public class TaskDispatchResult {

    private Long roundId;
    private String roundNo;
    /** 本趟窗口开没开：false 时三数全 0，回话固定「本轮未动单」 */
    private boolean gateOpen;
    /** 本份是不是第二遍照面（同趟号幂等回放） */
    private boolean replayed;
    private Date runAt;
    private Date finishAt;

    /** 趟尾三数之一：挑中几行 */
    private int pickedRows;
    /** 趟尾三数之二：递出几行 */
    private int sentRows;
    /** 趟尾三数之三：几行未成 */
    private int failedRows;
    /** 文面缺件按住几行（仍算挑中） */
    private int heldRows;
    /** 被一日去重挡下几行（不算挑中） */
    private int dedupSkippedRows;
    private int smsRows;
    private int innerRows;

    /** 趟尾回话原话（只复述三数；闸门外固定一句） */
    private String replyText;

    private final List<RowReceipt> rows = new ArrayList<>();

    /** 一行的去向回执（逐行与三数同一次算定） */
    public static class RowReceipt {
        private Long itemId;
        private String itemNo;
        /** 本行地块码（回放时带去重账上那份） */
        private String plotCode;
        /** SENT 递出 / FAILED 两条渠道都不通 / HELD 缺件按住 / DEDUP_SKIP 今日已递挡下 */
        private String kind;
        private String channel;
        private String messageText;
        private String failReason;
        private List<String> missingFields;
        private String contentNote;

        public Long getItemId() {
            return itemId;
        }

        public void setItemId(Long itemId) {
            this.itemId = itemId;
        }

        public String getItemNo() {
            return itemNo;
        }

        public void setItemNo(String itemNo) {
            this.itemNo = itemNo;
        }

        public String getPlotCode() {
            return plotCode;
        }

        public void setPlotCode(String plotCode) {
            this.plotCode = plotCode;
        }

        public String getKind() {
            return kind;
        }

        public void setKind(String kind) {
            this.kind = kind;
        }

        public String getChannel() {
            return channel;
        }

        public void setChannel(String channel) {
            this.channel = channel;
        }

        public String getMessageText() {
            return messageText;
        }

        public void setMessageText(String messageText) {
            this.messageText = messageText;
        }

        public String getFailReason() {
            return failReason;
        }

        public void setFailReason(String failReason) {
            this.failReason = failReason;
        }

        public List<String> getMissingFields() {
            return missingFields;
        }

        public void setMissingFields(List<String> missingFields) {
            this.missingFields = missingFields;
        }

        public String getContentNote() {
            return contentNote;
        }

        public void setContentNote(String contentNote) {
            this.contentNote = contentNote;
        }
    }

    public Long getRoundId() {
        return roundId;
    }

    public void setRoundId(Long roundId) {
        this.roundId = roundId;
    }

    public String getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(String roundNo) {
        this.roundNo = roundNo;
    }

    public boolean isGateOpen() {
        return gateOpen;
    }

    public void setGateOpen(boolean gateOpen) {
        this.gateOpen = gateOpen;
    }

    public boolean isReplayed() {
        return replayed;
    }

    public void setReplayed(boolean replayed) {
        this.replayed = replayed;
    }

    public Date getRunAt() {
        return runAt;
    }

    public void setRunAt(Date runAt) {
        this.runAt = runAt;
    }

    public Date getFinishAt() {
        return finishAt;
    }

    public void setFinishAt(Date finishAt) {
        this.finishAt = finishAt;
    }

    public int getPickedRows() {
        return pickedRows;
    }

    public void setPickedRows(int pickedRows) {
        this.pickedRows = pickedRows;
    }

    public int getSentRows() {
        return sentRows;
    }

    public void setSentRows(int sentRows) {
        this.sentRows = sentRows;
    }

    public int getFailedRows() {
        return failedRows;
    }

    public void setFailedRows(int failedRows) {
        this.failedRows = failedRows;
    }

    public int getHeldRows() {
        return heldRows;
    }

    public void setHeldRows(int heldRows) {
        this.heldRows = heldRows;
    }

    public int getDedupSkippedRows() {
        return dedupSkippedRows;
    }

    public void setDedupSkippedRows(int dedupSkippedRows) {
        this.dedupSkippedRows = dedupSkippedRows;
    }

    public int getSmsRows() {
        return smsRows;
    }

    public void setSmsRows(int smsRows) {
        this.smsRows = smsRows;
    }

    public int getInnerRows() {
        return innerRows;
    }

    public void setInnerRows(int innerRows) {
        this.innerRows = innerRows;
    }

    public String getReplyText() {
        return replyText;
    }

    public void setReplyText(String replyText) {
        this.replyText = replyText;
    }

    public List<RowReceipt> getRows() {
        return rows;
    }
}
