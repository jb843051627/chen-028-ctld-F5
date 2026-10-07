package com.fc.v2.service.ctld;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 流转单每一次进出的回话：五问里当下阶、封口态、是否重笔都在这一回给齐，前端不另起口径。
 *
 * @author fuce
 * @date 2026-10-06
 */
public class CaseFlowResult {

    private int code;
    private String msg;
    private Long caseId;
    private String bizNo;
    private String plotNo;
    /** 当下停在第几阶 0..3，唯一出处是本行+履历，不读报文序号 */
    private Integer stage;
    /** 0未起 1在办 2已封住 */
    private Integer status;
    private Integer closeKind;
    /** 本笔是不是重笔照回（幂等） */
    private boolean replayed;
    private Date time;

    public static CaseFlowResult ok(Long caseId, String bizNo, String plotNo, Integer stage, Integer status,
                                    Integer closeKind, String msg) {
        CaseFlowResult r = new CaseFlowResult();
        r.code = 200;
        r.msg = msg;
        r.caseId = caseId;
        r.bizNo = bizNo;
        r.plotNo = plotNo;
        r.stage = stage;
        r.status = status;
        r.closeKind = closeKind;
        return r;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Long getCaseId() {
        return caseId;
    }

    public void setCaseId(Long caseId) {
        this.caseId = caseId;
    }

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public String getPlotNo() {
        return plotNo;
    }

    public void setPlotNo(String plotNo) {
        this.plotNo = plotNo;
    }

    public Integer getStage() {
        return stage;
    }

    public void setStage(Integer stage) {
        this.stage = stage;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getCloseKind() {
        return closeKind;
    }

    public void setCloseKind(Integer closeKind) {
        this.closeKind = closeKind;
    }

    public boolean isReplayed() {
        return replayed;
    }

    public void setReplayed(boolean replayed) {
        this.replayed = replayed;
    }

    public Date getTime() {
        return time;
    }

    public void setTime(Date time) {
        this.time = time;
    }

    /** 起件入参：四样要件齐身才起得来 */
    public static class OpenCmd {
        private String plotNo;
        private String zoneNo;
        private BigDecimal cdValue;
        private String measureName;
        private String submitter;
        /** 递件笔号：同号重投照头一笔回话 */
        private String reqNo;

        public String getPlotNo() {
            return plotNo;
        }

        public void setPlotNo(String plotNo) {
            this.plotNo = plotNo;
        }

        public String getZoneNo() {
            return zoneNo;
        }

        public void setZoneNo(String zoneNo) {
            this.zoneNo = zoneNo;
        }

        public BigDecimal getCdValue() {
            return cdValue;
        }

        public void setCdValue(BigDecimal cdValue) {
            this.cdValue = cdValue;
        }

        public String getMeasureName() {
            return measureName;
        }

        public void setMeasureName(String measureName) {
            this.measureName = measureName;
        }

        public String getSubmitter() {
            return submitter;
        }

        public void setSubmitter(String submitter) {
            this.submitter = submitter;
        }

        public String getReqNo() {
            return reqNo;
        }

        public void setReqNo(String reqNo) {
            this.reqNo = reqNo;
        }
    }
}
