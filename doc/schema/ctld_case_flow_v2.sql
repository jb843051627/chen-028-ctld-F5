-- ctld_case_flow 状态机补强迁移（chen-028）
-- 与基线 doc/schema/ctld.sql 配套：本脚本只做增量，已在库上执行一次即可，重复执行靠 IF NOT EXISTS / 元信息判断。
-- 口径：当前档、放不放行、封口行吞不吞新按语、同档二次按语怎么落、倒演数几档，五问只由后端 t_ctld_case_flow 这一处回答。

-- ============ 一、流转单增列 ============
-- 起件四要件
ALTER TABLE t_ctld_case_flow
  ADD COLUMN plot_no varchar(64) DEFAULT NULL COMMENT '地块码（起件四要件之一）' AFTER biz_no,
  ADD COLUMN zone_no varchar(64) DEFAULT NULL COMMENT '所属分区代号（起件四要件之二，对分区册）' AFTER plot_no,
  ADD COLUMN cd_value decimal(10,3) DEFAULT NULL COMMENT '土壤镉实测值（起件四要件之三）' AFTER zone_no,
  ADD COLUMN measure_name varchar(128) DEFAULT NULL COMMENT '拟采用的措施名目（起件四要件之四）' AFTER cd_value,
  ADD COLUMN submitter varchar(64) DEFAULT NULL COMMENT '递件人（本人方可收回反映）' AFTER measure_name;

-- 本档按语与各档门槛落痕
ALTER TABLE t_ctld_case_flow
  ADD COLUMN stage_note varchar(500) DEFAULT NULL COMMENT '当前档留下的那一句按语（一档只许一句）' AFTER content,
  ADD COLUMN limit_pass int DEFAULT NULL COMMENT '纳入受限档核收 0未核 1可 2不可（不记可不许开工）' AFTER stage_note,
  ADD COLUMN witness1 varchar(64) DEFAULT NULL COMMENT '解除复核见证岗第一笔' AFTER limit_pass,
  ADD COLUMN witness2 varchar(64) DEFAULT NULL COMMENT '解除复核见证岗第二笔（须与第一笔不同岗）' AFTER witness1,
  ADD COLUMN voucher_no varchar(128) DEFAULT NULL COMMENT '措施核验凭证串（写回本行方闭解除复核门槛）' AFTER witness2,
  ADD COLUMN close_kind int DEFAULT NULL COMMENT '收口情形 1走满末档 2递件人收回 3分区并走 NULL尚未收口' AFTER voucher_no,
  ADD COLUMN enter_count int DEFAULT '0' COMMENT '本件累计入档笔数（后端从履历轧出，不信报文序号）' AFTER close_kind;

-- 活动槽：一处地块任何一刻只许一条未闭上的件。
-- status=1 在办 -> 槽位=地块码；其余 -> NULL。唯一索引对多个 NULL 放行，故只挡在办重复。
ALTER TABLE t_ctld_case_flow
  ADD COLUMN active_slot varchar(64) GENERATED ALWAYS AS (CASE WHEN status = 1 THEN plot_no ELSE NULL END) VIRTUAL,
  ADD UNIQUE KEY uk_plot_active (active_slot);

-- ============ 二、变更履历（追溯串，只追加） ============
CREATE TABLE IF NOT EXISTS t_ctld_case_flow_trace (
  id bigint NOT NULL COMMENT '主键',
  case_id bigint NOT NULL COMMENT '所属流转单 id',
  seq int NOT NULL COMMENT '本件内时序序号（挪档与留痕同事务内取，先后只认这一处）',
  stage int NOT NULL COMMENT '落这笔时单据所在档 0..3',
  action varchar(32) NOT NULL COMMENT '动作 OPEN/NOTE/ACCEPT/WITNESS/VOUCHER/ADVANCE/ROLLBACK/CLOSE',
  note_text varchar(500) DEFAULT NULL COMMENT '本笔按语原文（回退时照原先后转来，不改字）',
  close_kind int DEFAULT NULL COMMENT 'CLOSE 笔的收口情形 1走满末档 2收回 3并走',
  req_no varchar(64) DEFAULT NULL COMMENT '递件笔号（同号重复=幂等；异号同刻=两笔各记）',
  operator varchar(64) DEFAULT NULL COMMENT '落笔者',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除（履历只追加，不置1）',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_case_seq (case_id, seq),
  KEY idx_case_time (case_id, create_time, seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流转单变更履历（追溯串，只追加不改写）';

-- ============ 三、同件重递幂等账 ============
CREATE TABLE IF NOT EXISTS t_ctld_case_flow_idem (
  id bigint NOT NULL COMMENT '主键',
  case_id bigint NOT NULL COMMENT '所属流转单 id（起件笔用 0 占位，回头发号后不改）',
  action varchar(32) NOT NULL COMMENT '被幂等的动作名',
  req_no varchar(64) NOT NULL COMMENT '递件笔号',
  result_code int DEFAULT NULL COMMENT '头一笔处理结果码',
  result_msg varchar(255) DEFAULT NULL COMMENT '头一笔回话原文',
  result_stage int DEFAULT NULL COMMENT '处理后停在第几档',
  result_case_id bigint DEFAULT NULL COMMENT '起件笔发号后的真实件 id',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_case_action_req (case_id, action, req_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流转单同件重递幂等账（重笔照头一笔回话，不再落痕）';

-- ============ 四、演练用底册行（可选，供人工照演；不影响既有 seed） ============
INSERT IGNORE INTO t_ctld_plot (id, plot_no, zone_id, zone_no, src_kind, need_num, got_num, lack_num, content, status, del_flag, create_by, create_time)
VALUES (9001, 'P9001', 0, 'QC00', '县级自录', 1, 0, 1, '在册地块·可纳入演练', 0, 0, 'seed', NOW()),
       (9002, 'P9002', 1, 'QC01', '镇级补报', 1, 0, 1, '分区已并走撤出·应挡在纳限门外', 0, 0, 'seed', NOW());
