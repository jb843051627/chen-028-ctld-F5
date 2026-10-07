package com.fc.v2.service.ctld;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 早班翻单一趟的回话。
 * 趟末三个数（挑中/递出/未成）只认本处本次返回：它们与逐行落下的记号在同一次点算里算定，
 * 页面那屏只是转述，内存里的临时数不许拿来回话，也不拉另一轮逐行清点做对冲。
 * 同一日闸门内按第二回，本回话照头一趟复述，{@link #replayed}=true，单上不再起皱。
 *
 * @author fuce
 * @date 2026-10-07
 */
public class TaskDispatchReply {

    /** 趟账 id（闸闭未动单时为空） */
    private Long runId;
    private Date runDate;
    private Date runAt;
    /** 本趟闸是否开着 */
    private boolean gateOpen;
    /** 本回话是不是照头一趟复述（幂等） */
    private boolean replayed;
    /** 趟末数一：挑中几行 */
    private int pickedRows;
    /** 趟末数二：递出几行 */
    private int sentRows;
    /** 趟末数三：几行未成（发不成／缺项按下／同键跳过，逐行 kind 可辨） */
    private int failedRows;
    /** 趟末回话原文 */
    private String resultMsg;
    /** 逐行落下的记号（与三数同一次点算） */
    private final List<RowView> rows = new ArrayList<>();

    /** 逐行记号视图：发出流水那一行的复述 */
    public static class RowView {
        private Long itemId;
        private String itemNo;
        private String plotNo;
        private String measureName;
        private Date dueDate;
        private String channel;
        private String resultKind;
        private Integer status;
        private String msgText;
        private String failReason;

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

        public String getPlotNo() {
            return plotNo;
        }

        public void setPlotNo(String plotNo) {
            this.plotNo = plotNo;
        }

        public String getMeasureName() {
            return measureName;
        }

        public void setMeasureName(String measureName) {
            this.measureName = measureName;
        }

        public Date getDueDate() {
            return dueDate;
        }

        public void setDueDate(Date dueDate) {
            this.dueDate = dueDate;
        }

        public String getChannel() {
            return channel;
        }

        public void setChannel(String channel) {
            this.channel = channel;
        }

        public String getResultKind() {
            return resultKind;
        }

        public void setResultKind(String resultKind) {
            this.resultKind = resultKind;
        }

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }

        public String getMsgText() {
            return msgText;
        }

        public void setMsgText(String msgText) {
            this.msgText = msgText;
        }

        public String getFailReason() {
            return failReason;
        }

        public void setFailReason(String failReason) {
            this.failReason = failReason;
        }
    }

    public Long getRunId() {
        return runId;
    }

    public void setRunId(Long runId) {
        this.runId = runId;
    }

    public Date getRunDate() {
        return runDate;
    }

    public void setRunDate(Date runDate) {
        this.runDate = runDate;
    }

    public Date getRunAt() {
        return runAt;
    }

    public void setRunAt(Date runAt) {
        this.runAt = runAt;
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

    public String getResultMsg() {
        return resultMsg;
    }

    public void setResultMsg(String resultMsg) {
        this.resultMsg = resultMsg;
    }

    public List<RowView> getRows() {
        return rows;
    }
}
