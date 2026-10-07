package com.fc.v2.service.ctld;

import java.util.List;

import com.fc.v2.model.auto.TCtldFlowRow;
import com.fc.v2.model.auto.TCtldFlowRowMark;

/**
 * 土壤-农产品协同检测流水「四节核收」Service（新的一条：整拨一次递齐，无零散逐行入口）。
 * 老的收发口径（submitBatch 那一套）冻住，按镇收、按季收将来各另起一条，不改它的参数。
 *
 * @author fuce
 * @date 2026-10-07
 */
public interface ITCtldFlowIntakeService {

    /**
     * 整拨核收，一口气走完四节：点行数、看数据、定去处、出回执。
     * 收拨与流水联动：拨账、各行、标注同一事务落。
     * 同一批次码并着进第二回（同期）只认头一拨：照头一拨三栏回话，不再落一行、不另立一拨、不翻回未勾。
     */
    FlowIntakeResult intake(FlowIntakeCmd cmd);

    /** 去处一直空悬在未勾的行：单列待核清单，由回核的人逐行定去向，系统不替它收 */
    List<TCtldFlowRow> pendingList(String batchNo);

    /** 回核人逐行定去向（只准对未勾行落笔：1收下 2退回）；返回是否落定 */
    boolean settlePending(Long rowId, int status, String rejectCode);

    /** 切不开/带不上编码/半数/比对不合的标注单（递给回核看的人） */
    List<TCtldFlowRowMark> markList(String batchNo);

    /** 抽任意一行回核：本行去处与所属拨的三栏须相互印证；对不上逐行点名 */
    RowVerify verify(Long rowId);

    /** 一拨的回执（三栏以拨账当场落下的那份为准，页面只是复述） */
    FlowIntakeResult.BatchReceipt receipt(Long batchId);

    /** 回核行视图：去处、三栏、逐行追溯一并处 */
    class RowVerify {
        private boolean matched;
        private String detail;
        private TCtldFlowRow row;
        private int batchTotal;
        private int batchAccepted;
        private int batchRejected;

        public boolean isMatched() {
            return matched;
        }

        public void setMatched(boolean matched) {
            this.matched = matched;
        }

        public String getDetail() {
            return detail;
        }

        public void setDetail(String detail) {
            this.detail = detail;
        }

        public TCtldFlowRow getRow() {
            return row;
        }

        public void setRow(TCtldFlowRow row) {
            this.row = row;
        }

        public int getBatchTotal() {
            return batchTotal;
        }

        public void setBatchTotal(int batchTotal) {
            this.batchTotal = batchTotal;
        }

        public int getBatchAccepted() {
            return batchAccepted;
        }

        public void setBatchAccepted(int batchAccepted) {
            this.batchAccepted = batchAccepted;
        }

        public int getBatchRejected() {
            return batchRejected;
        }

        public void setBatchRejected(int batchRejected) {
            this.batchRejected = batchRejected;
        }
    }
}
