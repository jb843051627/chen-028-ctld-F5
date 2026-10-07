-- ctld 受污染耕地安全利用与农产品产地准出监管 -- schema (chen-028)
-- 列名与基线实体契约（@TableName/@TableField）逐列对齐，改列必须同步实体。
-- 库：chen_028

CREATE TABLE IF NOT EXISTS t_ctld_case_flow (
  id bigint NOT NULL COMMENT '主键',
  biz_no varchar(64) DEFAULT NULL COMMENT '超标地块纳入受限与解除的办件件次',
  plot_no varchar(64) DEFAULT NULL COMMENT '地块码（起件四要件之一）',
  zone_no varchar(64) DEFAULT NULL COMMENT '所属分区代号（起件四要件之二，对分区册）',
  cd_value decimal(10,3) DEFAULT NULL COMMENT '土壤镉实测值（起件四要件之三）',
  measure_name varchar(128) DEFAULT NULL COMMENT '拟采用的措施名目（起件四要件之四）',
  submitter varchar(64) DEFAULT NULL COMMENT '递件人（本人方可收回反映）',
  stage int DEFAULT NULL COMMENT '当前关次 0..3（初核受理/纳入受限/措施核查/解除复核，档数由后端从履历轧出）',
  status int DEFAULT NULL COMMENT '单子走到哪一关 0未起 1在办 2已封住',
  content varchar(255) DEFAULT NULL COMMENT '一关一记',
  stage_note varchar(500) DEFAULT NULL COMMENT '当前档留下的那一句按语（一档只许一句，二次投来不覆不存）',
  limit_pass int DEFAULT NULL COMMENT '纳入受限档核收 0未核 1可 2不可（不记可不许开工）',
  witness1 varchar(64) DEFAULT NULL COMMENT '解除复核见证岗第一笔',
  witness2 varchar(64) DEFAULT NULL COMMENT '解除复核见证岗第二笔（须与第一笔不同岗）',
  voucher_no varchar(128) DEFAULT NULL COMMENT '措施核验凭证串（写回本行方闭解除复核门槛）',
  close_kind int DEFAULT NULL COMMENT '收口情形 1走满末档 2递件人收回 3分区并走 NULL尚未收口',
  enter_count int DEFAULT '0' COMMENT '本件累计入档笔数（后端从履历轧出，不信报文序号）',
  last_action varchar(64) DEFAULT NULL COMMENT '最近一次挪关动作',
  active_slot varchar(64) GENERATED ALWAYS AS (CASE WHEN status = 1 THEN plot_no ELSE NULL END) VIRTUAL COMMENT '在办活动槽=地块码，唯一索引保一地一活件',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_plot_active (active_slot)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='超标地块纳入受限与解除办件流转单';

-- 变更履历（追溯串，只追加不改写；倒着回放从这里取数，只看不写）
CREATE TABLE IF NOT EXISTS t_ctld_case_flow_trace (
  id bigint NOT NULL COMMENT '主键',
  case_id bigint NOT NULL COMMENT '所属流转单 id',
  seq int NOT NULL COMMENT '本件内时序序号（挪档与留痕同事务内取）',
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

-- 同件重递幂等账：同一件号+动作+递件笔号再投，照头一笔回话，不再落痕
CREATE TABLE IF NOT EXISTS t_ctld_case_flow_idem (
  id bigint NOT NULL COMMENT '主键',
  case_id bigint NOT NULL COMMENT '所属流转单 id（起件笔以 0 占位）',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流转单同件重递幂等账';

CREATE TABLE IF NOT EXISTS t_ctld_cert_bill (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '产地准出合格证签批单',
  node_no int DEFAULT NULL COMMENT '当前所在关 0..2（村级初检/镇级核认/县局存备）',
  sign_mode int DEFAULT NULL COMMENT '本关算齐办法 0一名即齐 1两名须同判',
  need_count int DEFAULT NULL COMMENT '本关应签人数',
  sign_count int DEFAULT NULL COMMENT '本关已到人数',
  status int DEFAULT NULL COMMENT '签批情形 0在签 1已点齐 2已压回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='产地准出合格证签批单';

CREATE TABLE IF NOT EXISTS t_ctld_flow_row (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) DEFAULT NULL COMMENT '协同检测流水批次码',
  row_no int DEFAULT NULL COMMENT '原报行次',
  item_code varchar(64) DEFAULT NULL COMMENT '被对上的地块码',
  qty decimal(12,2) DEFAULT NULL COMMENT '本行送检样品数',
  chk_num decimal(12,2) DEFAULT NULL COMMENT '本行配对达标数',
  put_kind varchar(32) DEFAULT NULL COMMENT '来路（县农技中心直报／镇级补报两条来路）',
  status int DEFAULT NULL COMMENT '行落地情形 0未勾 1已收下 2已退回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='土壤-农产品协同检测流水核收行';

CREATE TABLE IF NOT EXISTS t_ctld_limit_line (
  id bigint NOT NULL COMMENT '主键',
  rule_code varchar(64) DEFAULT NULL COMMENT '作物镉限量线代号',
  rule_name varchar(128) DEFAULT NULL COMMENT '线名',
  obj_kind varchar(32) DEFAULT NULL COMMENT '这条线管谷物、薯类还是豆类',
  th1_max decimal(12,3) DEFAULT NULL COMMENT '关注限值',
  th2_max decimal(12,3) DEFAULT NULL COMMENT '告警限值',
  th3_max decimal(12,3) DEFAULT NULL COMMENT '封顶限值',
  eff_start datetime DEFAULT NULL COMMENT '启用那一一日',
  eff_end datetime DEFAULT NULL COMMENT '交班那一一日(不含)',
  priority int DEFAULT NULL COMMENT '交班次序(数值大的先说话)',
  status int DEFAULT NULL COMMENT '线的情形 0在位 1已撤下',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='作物镉限量贴线档';

CREATE TABLE IF NOT EXISTS t_ctld_plot (
  id bigint NOT NULL COMMENT '主键',
  plot_no varchar(64) DEFAULT NULL COMMENT '地块码',
  zone_id int DEFAULT NULL COMMENT '挂在哪一个管控分区名下',
  zone_no varchar(64) DEFAULT NULL COMMENT '分区代号（冗余自分区册）',
  src_kind varchar(32) DEFAULT NULL COMMENT '来路（省里下发／镇级补报／县级自录三条来路）',
  need_num int DEFAULT NULL COMMENT '核定应落措施项数',
  got_num int DEFAULT NULL COMMENT '已核验落实的项数',
  lack_num int DEFAULT NULL COMMENT '还差几项（轧出来，不留填数的空位）',
  content varchar(255) DEFAULT NULL COMMENT '随件交来的地块题名与主栽作物',
  status int DEFAULT NULL COMMENT '册面情形 0新入册 1已核齐 2缺项',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='受污染耕地地块档案底册';

CREATE TABLE IF NOT EXISTS t_ctld_switch_cfg (
  id bigint NOT NULL COMMENT '主键',
  sw_no varchar(64) DEFAULT NULL COMMENT '核验口径切换项代号（由系统排上，不留手填的格子）',
  sw_name varchar(128) DEFAULT NULL COMMENT '切换名目（同季配对回话／逐条人工签认／两式并行）',
  sw_kind int DEFAULT NULL COMMENT '开关三态 0一律走旧口径 1一律走新口径 2按桶推进',
  gray_num int DEFAULT NULL COMMENT '开桶个数 0..10（按地块码尾数落桶，写 3 即尾数 0/1/2 走新口径）',
  level_kind varchar(32) DEFAULT NULL COMMENT '定档层级（省定／市定／县定／系统兜底）',
  parent_no varchar(64) DEFAULT NULL COMMENT '继承自哪一项',
  zone_id int DEFAULT NULL COMMENT '挂在哪一个管控分区名下',
  zone_no varchar(64) DEFAULT NULL COMMENT '适用的管控分区代号（冗余自分区册）',
  zone_name varchar(128) DEFAULT NULL COMMENT '分区全称（冗余一份，以分区册为准回写）',
  ver_no int DEFAULT NULL COMMENT '改动次序（数值大的先说话）',
  crt_num int DEFAULT NULL COMMENT '本项应覆盖处数',
  hit_num int DEFAULT NULL COMMENT '已切到新口径的处数',
  lack_num int DEFAULT NULL COMMENT '还差几处（轧出来，不留填数的空位）',
  eff_start datetime DEFAULT NULL COMMENT '启用那一一日',
  eff_end datetime DEFAULT NULL COMMENT '交班那一一日(不含)',
  status int DEFAULT NULL COMMENT '走到哪一级 0待核对 1已核对 2已压住',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  content varchar(255) DEFAULT NULL COMMENT '核验串（定形后不被页面覆写）',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='产地准出核验口径切换开关表';

CREATE TABLE IF NOT EXISTS t_ctld_task_item (
  id bigint NOT NULL COMMENT '主键',
  item_no varchar(64) DEFAULT NULL COMMENT '农时催办条目代号',
  due_at datetime DEFAULT NULL COMMENT '该发提醒那一一日（落库为 datetime）',
  amount decimal(12,2) DEFAULT NULL COMMENT '至多许提前几日',
  content varchar(255) DEFAULT NULL COMMENT '事由与所对地块的记要',
  status int DEFAULT NULL COMMENT '条目情形 0搁着 1已发出 2发不成',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='农时催办提醒条目';

CREATE TABLE IF NOT EXISTS t_ctld_zone_book (
  id bigint NOT NULL COMMENT '主键',
  zone_no varchar(64) DEFAULT NULL COMMENT '受污染耕地管控分区册代号',
  zone_name varchar(128) DEFAULT NULL COMMENT '分区册全称',
  zone_kind varchar(32) DEFAULT NULL COMMENT '层类别（甲层稳产好地·乙层要落措施·丙层退出耕种）',
  road_name varchar(128) DEFAULT NULL COMMENT '属地详址（澜原省—渠州市—浚安县—镇—田片）',
  align_kind varchar(32) DEFAULT NULL COMMENT '来路（省里分区册下发／本局自录）',
  status int DEFAULT NULL COMMENT '分区册情形 0在册 1已撤出',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='受污染耕地管控分区册底册';

-- 初始档案数据（id=1 启用 / id=2 停用）
-- 预置两条分区册底册行：zone_id=0 在册（供切换项挂靠、并走之后挡住新入册的判定取用）、
-- zone_id=1 已并走撤出（并走撤出这一段不再收新行的判定取用）。分区代号与层类别按三层各排各的序给值。
INSERT IGNORE INTO t_ctld_zone_book (id, zone_no, zone_name, zone_kind, road_name, status, del_flag, create_by, create_time)
VALUES (0, 'QC00', '澜原省渠州市浚安县北浚镇东片（甲层稳产好地，在册）', '甲层', '澜原省—渠州市—浚安县—北浚镇—东片', 0, 0, 'seed', NOW()),
       (1, 'QC01', '南浚镇西片·并走撤出的旧分区（已撤出）', '丙层', '澜原省—渠州市—浚安县—南浚镇—西片', 1, 0, 'seed', NOW());

