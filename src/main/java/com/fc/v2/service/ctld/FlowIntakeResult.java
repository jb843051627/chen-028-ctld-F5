package com.fc.v2.service.ctld;

import java.util.ArrayList;
import java.util.List;

import com.fc.v2.model.auto.TCtldFlowRow;
import com.fc.v2.model.auto.TCtldFlowRowMark;

/**
 * 一拨（或一次递交接成的一串拨）核收完的回执。
 * 拨头上三个数只认核收这一处当场返回的本份；屏上抢先亮出来的不作凭，两处对不齐以本份为是。
 * 同一拨并着进第二回，本份照头一拨复述，{@link #replayed}=true，不再落一行。
 *
 * @author fuce
 * @date 2026-10-07
 */
public class FlowIntakeResult {

    private String batchNo;
    private int periodNo;
    private int lineCap;
    private String declaredCapRaw;
    /** 本份是不是第二遍照面（幂等回放头一拨） */
    private boolean replayed;
    private final List<BatchReceipt> batches = new ArrayList<>();

    public static class BatchReceipt {
        private Long batchId;
        private int recvSeq;
        private int lineCap;
        /** 三栏：几行进门（只含收/退落定行，未勾悬行不计） */
        private int totalRows;
        private int acceptedRows;
        private int rejectedRows;
        /** 去处空悬未勾的行数（单列待核） */
        private int uncheckedRows;
        private boolean empty;
        private String receiptText;
        private final List<TCtldFlowRow> rows = new ArrayList<>();
        private final List<TCtldFlowRowMark> marks = new ArrayList<>();

        public Long getBatchId() {
            return batchId;
        }

        public void setBatchId(Long batchId) {
            this.batchId = batchId;
        }

        public int getRecvSeq() {
            return recvSeq;
        }

        public void setRecvSeq(int recvSeq) {
            this.recvSeq = recvSeq;
        }

        public int getLineCap() {
            return lineCap;
        }

        public void setLineCap(int lineCap) {
            this.lineCap = lineCap;
        }

        public int getTotalRows() {
            return totalRows;
        }

        public void setTotalRows(int totalRows) {
            this.totalRows = totalRows;
        }

        public int getAcceptedRows() {
            return acceptedRows;
        }

        public void setAcceptedRows(int acceptedRows) {
            this.acceptedRows = acceptedRows;
        }

        public int getRejectedRows() {
            return rejectedRows;
        }

        public void setRejectedRows(int rejectedRows) {
            this.rejectedRows = rejectedRows;
        }

        public int getUncheckedRows() {
            return uncheckedRows;
        }

        public void setUncheckedRows(int uncheckedRows) {
            this.uncheckedRows = uncheckedRows;
        }

        public boolean isEmpty() {
            return empty;
        }

        public void setEmpty(boolean empty) {
            this.empty = empty;
        }

        public String getReceiptText() {
            return receiptText;
        }

        public void setReceiptText(String receiptText) {
            this.receiptText = receiptText;
        }

        public List<TCtldFlowRow> getRows() {
            return rows;
        }

        public List<TCtldFlowRowMark> getMarks() {
            return marks;
        }
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public int getPeriodNo() {
        return periodNo;
    }

    public void setPeriodNo(int periodNo) {
        this.periodNo = periodNo;
    }

    public int getLineCap() {
        return lineCap;
    }

    public void setLineCap(int lineCap) {
        this.lineCap = lineCap;
    }

    public String getDeclaredCapRaw() {
        return declaredCapRaw;
    }

    public void setDeclaredCapRaw(String declaredCapRaw) {
        this.declaredCapRaw = declaredCapRaw;
    }

    public boolean isReplayed() {
        return replayed;
    }

    public void setReplayed(boolean replayed) {
        this.replayed = replayed;
    }

    public List<BatchReceipt> getBatches() {
        return batches;
    }

    /** 头一拨回执（一次递交接成一串拨时，屏上通常先复述这一份） */
    public BatchReceipt firstBatch() {
        return batches.get(0);
    }
}
