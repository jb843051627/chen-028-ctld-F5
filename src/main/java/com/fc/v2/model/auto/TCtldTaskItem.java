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
import java.math.BigDecimal;
import java.util.Date;

/**
 * 农时催办提醒条目对象 t_ctld_task_item
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_ctld_task_item")
@ApiModel(value = "TCtldTaskItem", description = "农时催办提醒条目")
public class TCtldTaskItem implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 农时催办条目代号 */
    @TableField("item_no")
    @ApiModelProperty(value = "农时催办条目代号")
    private String itemNo;

    /** 本行所对地块码（去重键前段，只读本行） */
    @TableField("plot_no")
    @ApiModelProperty(value = "本行所对地块码（去重键前段，只读本行）")
    private String plotNo;

    /** 事由名目/措施名目（去重键后段，只读本行） */
    @TableField("measure_name")
    @ApiModelProperty(value = "事由名目/措施名目（去重键后段，只读本行）")
    private String measureName;

    /** 主栽作物（话术四要件之一，取不着不代填） */
    @TableField("main_crop")
    @ApiModelProperty(value = "主栽作物（话术四要件之一，取不着不代填）")
    private String mainCrop;

    /** 承办岗（话术四要件之一） */
    @TableField("assignee_post")
    @ApiModelProperty(value = "承办岗（话术四要件之一）")
    private String assigneePost;

    /** 该发提醒那一一日（落库为 datetime） */
    @TableField("due_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "该发提醒那一一日（落库为 datetime）")
    private Date dueAt;

    /** 至多许提前几日 */
    @TableField("amount")
    @ApiModelProperty(value = "至多许提前几日")
    private BigDecimal amount;

    /** 事由与所对地块的记要 */
    @TableField("content")
    @ApiModelProperty(value = "事由与所对地块的记要")
    private String content;

    /** 条目情形 0搁着 1已发出 2发不成 */
    @TableField("status")
    @ApiModelProperty(value = "条目情形 0搁着 1已发出 2发不成")
    private Integer status;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getMainCrop() {
        return mainCrop;
    }

    public void setMainCrop(String mainCrop) {
        this.mainCrop = mainCrop;
    }

    public String getAssigneePost() {
        return assigneePost;
    }

    public void setAssigneePost(String assigneePost) {
        this.assigneePost = assigneePost;
    }

    public Date getDueAt() {
        return dueAt;
    }

    public void setDueAt(Date dueAt) {
        this.dueAt = dueAt;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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
