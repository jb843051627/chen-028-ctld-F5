package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 流转单同件重递幂等账对象 t_ctld_case_flow_idem
 * 同一件、同一动作、同一递件笔号再投：照头一笔的回话原封回给，不再落痕、不再挪档。
 *
 * @author fuce
 * @date 2026-10-06
 */
@TableName("t_ctld_case_flow_idem")
@ApiModel(value = "TCtldCaseFlowIdem", description = "流转单同件重递幂等账")
public class TCtldCaseFlowIdem implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 所属流转单 id（起件笔以 0 占位，真实 id 另存 resultCaseId） */
    @TableField("case_id")
    @ApiModelProperty(value = "所属流转单 id")
    private Long caseId;

    /** 被幂等的动作名 */
    @TableField("action")
    @ApiModelProperty(value = "被幂等的动作名")
    private String action;

    /** 递件笔号 */
    @TableField("req_no")
    @ApiModelProperty(value = "递件笔号")
    private String reqNo;

    /** 头一笔处理结果码 */
    @TableField("result_code")
    @ApiModelProperty(value = "头一笔处理结果码")
    private Integer resultCode;

    /** 头一笔回话原文 */
    @TableField("result_msg")
    @ApiModelProperty(value = "头一笔回话原文")
    private String resultMsg;

    /** 处理后停在第几档 */
    @TableField("result_stage")
    @ApiModelProperty(value = "处理后停在第几档")
    private Integer resultStage;

    /** 起件笔发号后的真实件 id */
    @TableField("result_case_id")
    @ApiModelProperty(value = "起件笔真实件 id")
    private Long resultCaseId;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCaseId() {
        return caseId;
    }

    public void setCaseId(Long caseId) {
        this.caseId = caseId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getReqNo() {
        return reqNo;
    }

    public void setReqNo(String reqNo) {
        this.reqNo = reqNo;
    }

    public Integer getResultCode() {
        return resultCode;
    }

    public void setResultCode(Integer resultCode) {
        this.resultCode = resultCode;
    }

    public String getResultMsg() {
        return resultMsg;
    }

    public void setResultMsg(String resultMsg) {
        this.resultMsg = resultMsg;
    }

    public Integer getResultStage() {
        return resultStage;
    }

    public void setResultStage(Integer resultStage) {
        this.resultStage = resultStage;
    }

    public Long getResultCaseId() {
        return resultCaseId;
    }

    public void setResultCaseId(Long resultCaseId) {
        this.resultCaseId = resultCaseId;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
