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
 * 流转单变更履历对象 t_ctld_case_flow_trace
 * 追溯串只追加、不改写；回放只读此表，倒着演给审计看，不写回当下。
 *
 * @author fuce
 * @date 2026-10-06
 */
@TableName("t_ctld_case_flow_trace")
@ApiModel(value = "TCtldCaseFlowTrace", description = "流转单变更履历（追溯串）")
public class TCtldCaseFlowTrace implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 所属流转单 id */
    @TableField("case_id")
    @ApiModelProperty(value = "所属流转单 id")
    private Long caseId;

    /** 本件内时序序号（挪档与留痕同事务内取，先后只认这一处） */
    @TableField("seq")
    @ApiModelProperty(value = "本件内时序序号")
    private Integer seq;

    /** 落这笔时单据所在档 0..3 */
    @TableField("stage")
    @ApiModelProperty(value = "落这笔时单据所在档 0..3")
    private Integer stage;

    /** 动作 OPEN/NOTE/ACCEPT/WITNESS/VOUCHER/ADVANCE/ROLLBACK/CLOSE */
    @TableField("action")
    @ApiModelProperty(value = "动作")
    private String action;

    /** 本笔按语原文（回退时照原先后转来，不改字） */
    @TableField("note_text")
    @ApiModelProperty(value = "本笔按语原文")
    private String noteText;

    /** CLOSE 笔的收口情形 1走满末档 2收回 3并走 */
    @TableField("close_kind")
    @ApiModelProperty(value = "CLOSE 笔的收口情形 1走满 2收回 3并走")
    private Integer closeKind;

    /** 递件笔号（同号重复=幂等；异号同刻=两笔各记） */
    @TableField("req_no")
    @ApiModelProperty(value = "递件笔号")
    private String reqNo;

    /** 落笔者 */
    @TableField("operator")
    @ApiModelProperty(value = "落笔者")
    private String operator;

    /** 删除标记（履历只追加，不置1） */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间（提交那一刻） */
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

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
    }

    public Integer getStage() {
        return stage;
    }

    public void setStage(Integer stage) {
        this.stage = stage;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getNoteText() {
        return noteText;
    }

    public void setNoteText(String noteText) {
        this.noteText = noteText;
    }

    public Integer getCloseKind() {
        return closeKind;
    }

    public void setCloseKind(Integer closeKind) {
        this.closeKind = closeKind;
    }

    public String getReqNo() {
        return reqNo;
    }

    public void setReqNo(String reqNo) {
        this.reqNo = reqNo;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
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
