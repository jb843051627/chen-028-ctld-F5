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
 * 农时催办发出流水对象 t_ctld_task_send_log
 * 逐行记下走的是哪条渠道、走不通那句原由；它同时是同地块同事由一日一条的去重账
 * （只有 status=1 已发出的行才占唯一槽，发不成/按下未发不占名额）。
 *
 * @author fuce
 * @date 2026-10-07
 */
@TableName("t_ctld_task_send_log")
@ApiModel(value = "TCtldTaskSendLog", description = "农时催办发出流水")
public class TCtldTaskSendLog implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 所属趟账 id */
    @TableField("run_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "所属趟账 id")
    private Long runId;

    /** 本流水所属那一一日 */
    @TableField("run_date")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @ApiModelProperty(value = "本流水所属那一一日")
    private Date runDate;

    /** 催办条目 id */
    @TableField("item_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "催办条目 id")
    private Long itemId;

    /** 条目代号（照抄本行） */
    @TableField("item_no")
    @ApiModelProperty(value = "条目代号")
    private String itemNo;

    /** 地块码（本行那格） */
    @TableField("plot_no")
    @ApiModelProperty(value = "地块码")
    private String plotNo;

    /** 事由名目（本行那格） */
    @TableField("measure_name")
    @ApiModelProperty(value = "事由名目")
    private String measureName;

    /** 届期那一一日 */
    @TableField("due_date")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @ApiModelProperty(value = "届期那一一日")
    private Date dueDate;

    /** 本回发出那一一日 */
    @TableField("send_date")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @ApiModelProperty(value = "本回发出那一一日")
    private Date sendDate;

    /** 实际走通的渠道 SMS短信 STATION站内消息；未递出为空 */
    @TableField("channel")
    @ApiModelProperty(value = "实际走通的渠道 SMS/STATION；未递出为空")
    private String channel;

    /** 本行结局 SENT_SMS/SENT_STATION/FAILED/HELD_MISSING/DEDUP_SKIP */
    @TableField("result_kind")
    @ApiModelProperty(value = "本行结局 SENT_SMS/SENT_STATION/FAILED/HELD_MISSING/DEDUP_SKIP")
    private String resultKind;

    /** 记到条目上的情形 1已发出 2发不成 0按下未发 */
    @TableField("status")
    @ApiModelProperty(value = "记到条目上的情形 1已发出 2发不成 0按下未发")
    private Integer status;

    /** 递出去那句原话（四项齐全才成形） */
    @TableField("msg_text")
    @ApiModelProperty(value = "递出去那句原话")
    private String msgText;

    /** 走不通那句原由/缺项清单 */
    @TableField("fail_reason")
    @ApiModelProperty(value = "走不通那句原由/缺项清单")
    private String failReason;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    private String updateBy;

    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Date getSendDate() {
        return sendDate;
    }

    public void setSendDate(Date sendDate) {
        this.sendDate = sendDate;
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

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
