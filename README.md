# 酒店筛选助手

基于 Spring AI Alibaba Workflow、Spring Boot、MyBatis、H2 和 Vue 的酒店筛选演示项目。模型负责需求提取和基于证据的排序，后端固定编排查询、筛选及复核。

## 主要流程

1. 模型提取本轮结构化需求补丁，后端校验并合并；城市按后端行政区划常量校验，支持省略“市”，无法识别时先追问，不视为无库存。缺少城市或日期时按实际缺失项追问；上一轮追问退房日期且仅缺退房日期时，支持直接回复“11月21日”。日期、路线起点或其他信息不清时也先追问。
2. 后端查询完整入住区间库存，在同一房型上筛选优先和一般价格、设施，维护本轮确认候选。未知价格或设施不计入确认候选，也不能当作已证实不满足。
3. 存在本次或适用的长期评价偏好时逐条匹配候选酒店全部评价，后端统计相关评价的同意和反对比例，并优先采用本用户对该酒店的最新明确评价；有明确路线起点时查询高德路线。
4. 模型根据候选和可用证据排序，后端检查排序并复核酒店、房型和筛选条件，生成推荐卡片。模型对评价和排序理由的语义判断未由后端逐项核实。
5. 确认候选少于三家时，若已有库存证明调整单一价格或设施条件可能增加候选，则先展示已有结果，再询问用户。明确修改后下一轮重新查询；拒绝后不重复询问同一条件及其相同值。
6. 区分 `NO_INVENTORY`（无完整入住库存）与 `NO_FILTER_MATCH`（有库存但无确认合格候选）；服务错误不是空结果。不可用的路线不会用模拟数据替代。

评价匹配按相关评价条数而非人数计算比例；至少有一条同意且同意数不少于反对数的两倍才判为符合，没有同意或反对时为未知。本用户对该酒店的最新明确评价优先于统计判定。

后端从用户历史消息和评价提取并按记录计数，每条记录对同一偏好最多计一次，保存最多两个高频偏好；并列时按最近记录，再按类型和名称排序。发送消息或评价后更新，下一轮排序时使用。本次要求与长期偏好冲突时，本次要求优先；模型综合评分，不设固定加分。删除会话不会删除已提取的长期记忆。偏好提取和冲突判断的语义不由后端独立核验。前端使用共享演示用户，项目没有身份认证或用户间隐私隔离。

对被模型误放到设施字段的已知安静、卫生类描述，后端将其转为相应优先级的评价偏好；无法识别的设施仍须用户确认。旧会话中误存为未知设施的这两类描述会恢复为一般评价偏好（原优先级未保存，无法还原）。

观察结果包含每轮后端工作流耗时（不含工作流前后的长期记忆更新、会话持久化和网络传输）；历史会话保留最近一轮的耗时。

观察结果中的评估模块按会话累计消息处理：

- 运行时间累计各轮从进入消息接口至记忆更新完成的耗时，不含评估结果写入、事务提交和网络传输；模型调用次数累计各轮，包括记忆提取及 JSON 格式重试，但不包含模型客户端内部的 HTTP 重试。
- 满足硬条件率为各轮通过最终复核的推荐酒店数之和除以各轮推荐酒店数之和；重复推荐分别计数。系统只展示已通过复核的酒店，因此该值通常为 100%，不是对筛选召回率的评估。无推荐时显示“—”。
- 满足偏好率累计各轮推荐酒店与偏好项的已满足项数除以全部项数，重复推荐分别计数，未知计入分母并单独显示。包括卡片中的价格、设施和公共交通偏好、评价匹配结果和适用的长期偏好；各来源分别计数，长期设施仅对可识别的设施名称核验，其他长期偏好记为未知。评价语义由模型判断，指标并非人工评测结果；无偏好时显示“—”。
- 会话错误率为该会话已进入消息处理的失败请求数除以已进入处理的消息请求总数；澄清、无库存和无匹配不算错误。网络未到达后端、接口参数校验失败及评估记录写入失败无法计入。无推荐或失败的轮次不改变已累计的满足率。
- 旧会话此前只保存最近一次消息的评估值，更早轮次无法补算；保留已有值并继续累计，界面标记为不完整。错误率仍按已记录请求累计。

## 列表页查询

首页可按城市、入住日期和退房日期查询酒店列表。前端调用 `GET /api/hotels?city=...&checkIn=...&checkOut=...`；后端仅返回同一酒店至少有一个房型覆盖整个入住区间的结果。点击列表项可进入该酒店详情页。

## 工作流观察项

| 观察项 | 用途 |
| --- | --- |
| `requirement_analysis` | 解析本轮需求补丁 |
| `memory_fetch` | 提取、保存并读取用户高频长期偏好 |
| `hotel_list` | 查询完整入住区间库存并筛选硬条件 |
| `distance_calculation` | 查询高德路线及路线步骤 |
| `review_matching` | 按需逐条匹配候选酒店评价并统计结果，供模型排序参考 |
| `multiple_ranking` | 对后端硬条件筛选后的候选排序 |
| `recommendation_validation` | 独立复核最终推荐 |
| `condition_relax` | 候选不足且有单项调整证据时征询用户 |

各能力由后端代码实现，并由 `StateGraph` 固定编排，不由模型自主选择。
需求提取或排序的模型响应不是有效 JSON 时，后端会重新请求一次；仍无效则返回错误，不接受未经校验的结果。

## 数据与限制

酒店数据来自 `dataset/酒店数据四表.xlsx`，应用使用离线生成的 SQL seed，不在启动期间解析 Excel。数据仅用于可复现演示，不代表实时价格、房态或预订结果；缺失字段以未知处理，来源没有提供的币种不会由界面臆测。

城市常量包含直辖市、地级市、县级市和港澳目的地，不包含自治州、地区或盟。内地名单依据[民政部 2023 年县以上行政区划代码](https://www.mca.gov.cn/mzsj/xzqh/2023/202301xzqh.html)，台湾城市依据[县市](https://api.nlsc.gov.tw/other/ListCounty)和[乡镇市区](https://data.gov.tw/dataset/102013)公开资料；名单为静态快照，行政区划变更需手动更新。合法城市没有酒店时会返回无库存。

库存数据按原始库存行顺序平均分配到 2026-11-20、2026-11-25、2026-11-28 和 2026-11-30 四个单晚日期，保留原库存行数及酒店、房型和价格等信息。每行只代表一晚库存；这些日期之间没有连续的每日库存，不能据此拼接多晚行程。

用户提出路线或距离需求但未明确提供起点时，Agent 会先追问出发地点；路线查询只在起点明确后执行。需要在本机配置高德密钥；未配置或服务失败时不生成替代路线。项目不实现真实房态交易、预订、支付或身份认证。

## 数据库表结构

数据库初始化定义见 `backend/app/src/main/resources/schema.sql`。下表描述全新初始化后的结构；该脚本也包含用于兼容旧 H2 数据库的 `ALTER TABLE` 语句。除特别注明外，字段均允许为空。
旧数据库启动时会将 `hotel_detail` 中的省份、区县迁移到 `hotel` 并删除旧列；已有的 `hotel` 值优先保留。种子数据仍按工作簿在启动时更新。

### 酒店与业务数据

| 表 | 字段 | 类型 | 约束与说明 |
| --- | --- | --- | --- |
| `hotel` | `id` | `BIGINT` | 主键；本地酒店 ID |
|  | `source_id` | `VARCHAR(32)` | 非空、唯一；源数据酒店 ID |
|  | `name` | `VARCHAR(255)` | 非空；酒店名称 |
|  | `city` | `VARCHAR(80)` | 非空；城市 |
|  | `province` / `district` | `VARCHAR(80)` | 省份、区县 |
|  | `address` | `VARCHAR(500)` | 地址 |
|  | `rating` | `DECIMAL(3,1)` | 评分；缺失时为空 |
|  | `image` | `VARCHAR(1000)` | 图片地址 |
|  | `amenities` | `VARCHAR(1000)` | 设施文本，非空，默认空字符串 |
|  | `keywords` | `VARCHAR(1000)` | 搜索关键词 |
| `hotel_detail` | `hotel_id` | `BIGINT` | 主键，同时外键引用 `hotel.id`；与酒店一对一 |
|  | `longitude` / `latitude` | `DECIMAL(13,9)` / `DECIMAL(12,9)` | 经纬度 |
|  | `opening_year` / `renovation_year` | `INT` | 开业年份、装修年份 |
|  | `phone` | `VARCHAR(80)` | 联系电话 |
|  | `gym`、`laundry`、`parking`、`luggage_storage`、`robot_service`、`restaurant`、`taxi_service`、`meeting_room`、`accessibility` | `BOOLEAN` | 酒店级设施；`NULL` 表示未知 |
| `room_offer` | `id` | `BIGINT` | 主键 |
|  | `hotel_id` | `BIGINT` | 非空；外键引用 `hotel.id` |
|  | `room_name` | `VARCHAR(255)` | 房型名称 |
|  | `price_per_night` | `DECIMAL(10,2)` | 每晚价格；缺失时为空 |
|  | `breakfast`、`hair_dryer`、`towels` | `BOOLEAN` | 房型级早餐、吹风机和毛巾信息；`NULL` 表示未知 |
|  | `available_on` | `DATE` | 非空；该库存记录对应的单晚日期，查询整段入住库存以此字段为准 |
|  | `available_from` / `available_to` | `DATE` | 库存日期范围字段；导入数据中每条记录只表示一晚 |
|  | `inventory_source_hotel_id` | `VARCHAR(32)` | 工作簿库存表中的原始酒店 ID，用于追溯映射 |
| `review` | `id` | `BIGINT` | 自增主键 |
|  | `hotel_id` | `BIGINT` | 非空；外键引用 `hotel.id` |
|  | `source_review_id` | `VARCHAR(32)` | 源评价 ID |
|  | `user_id` / `user_name` | `VARCHAR(120)` | 源评价用户标识和用户名 |
|  | `rating` | `INT` | 评价评分；缺失时为空 |
|  | `content` | `VARCHAR(4000)` | 评价内容 |
|  | `created_at` | `TIMESTAMP` | 评价时间；源数据缺失时为空 |

### 会话数据

| 表 | 字段 | 类型 | 约束与说明 |
| --- | --- | --- | --- |
| `agent_session` | `id` | `VARCHAR(36)` | 主键；会话 ID |
|  | `user_id` | `VARCHAR(120)` | 非空；会话所属用户 |
|  | `title` | `VARCHAR(120)` | 会话标题 |
|  | `state` | `VARCHAR(40)` | 非空；会话状态 |
|  | `pending_relaxation` | `VARCHAR(40)` | 待用户确认的条件放宽项 |
|  | `workflow_version` | `INT` | 工作流版本；旧版本会话仅可查看或删除 |
|  | `requirements_json` / `latest_reply_json` | `TEXT` | 非空；当前需求和最近回复的 JSON |
|  | `created_at` / `updated_at` | `TIMESTAMP` | 非空；创建和更新时间 |
| `agent_message` | `id` | `BIGINT` | 自增主键 |
|  | `session_id` | `VARCHAR(36)` | 非空；外键引用 `agent_session.id`，删除会话时级联删除消息 |
|  | `role` | `VARCHAR(10)` | 非空；消息角色 |
|  | `content` | `TEXT` | 非空；消息正文 |
|  | `created_at` | `TIMESTAMP` | 非空；消息创建时间 |
| `session_evaluation` | `session_id` | `VARCHAR(36)` | 主键；删除会话时级联删除 |
|  | `request_count` / `error_count` | `BIGINT` | 非空；会话消息请求和失败数 |
|  | `duration_millis` / `model_calls` | `BIGINT` / `INT` | 会话累计耗时和模型调用数 |
|  | `hard_satisfied` / `hard_total` | `INT` | 各轮推荐酒店中满足硬条件的累计数量及总数 |
|  | `preference_satisfied` / `preference_total` / `preference_unknown` | `INT` | 各轮满足、总计和未知偏好项数的累计值 |
|  | `history_complete` | `BOOLEAN` | 非空；旧会话缺少历史评估数据时为 false |
| `user_preference_memory` | `user_id` | `VARCHAR(120)` | 主键；演示用户标识 |
|  | `preferences_json` / `updated_at` | `TEXT` / `TIMESTAMP` | 非空；最多两个长期偏好及其更新时间 |
| `user_preference_event` | `user_id` / `source_type` / `source_id` | `VARCHAR(120)` / `VARCHAR(10)` / `BIGINT` | 联合主键；已处理的消息或评价 |
|  | `occurred_at` / `preferences_json` | `TIMESTAMP` / `TEXT` | 来源时间和提取的偏好；删除会话后保留 |

`hotel_detail` 的 `hotel_id` 同时是主键和外键。酒店设施与房型设施分开存储；筛选房间级硬条件时，必须由同一房型满足。`room_offer` 的可订性按同一酒店、同一房型在 `checkIn`（含）至 `checkOut`（不含）的每个 `available_on` 日期均有记录来判断。

## 目录结构

```text
agent/                         Workflow Agent Maven 模块
  src/main/java/.../agent/     模型提示词及 StateGraph 编排
backend/
  pom.xml                      Maven Reactor，统一构建 Agent 与后端模块
  core/                        领域模型、技能业务逻辑、数据库访问和酒店查询 API
  app/                         Spring Boot 启动入口、会话 API/服务、配置、SQL seed 和测试
  scripts/                     离线数据转换脚本
frontend/                      Vue 用户界面与 Vite 配置
dataset/                       原始酒店数据工作簿
```

依赖方向为 `agent -> backend/core`、`backend/app -> agent + backend/core`；核心业务模块不依赖 Agent 模块。`backend/pom.xml` 聚合这三个 Maven 模块。

## 本地运行

需要 Java 17+、Maven 和 Node.js/npm。

1. 在仓库根目录创建 `.env`（可复制 [.env.example](./.env.example)），配置 `HOTEL_LLM_BASE_URL`、`HOTEL_LLM_API_KEY`、`HOTEL_LLM_MODEL`。路线查询另需填写 `amap_key`。不要提交实际密钥，也不要覆盖已有 `.env`。
   `HOTEL_LLM_BASE_URL` 应填写 OpenAI 兼容服务的基础地址；Spring AI 默认会在其后追加 `/v1/chat/completions`。例如完整接口为 `https://example.com/provider/v1/chat/completions` 时，基础地址应填写 `https://example.com/provider`，不要包含 `/v1/chat/completions`。如果服务使用不同的接口路径，需另行配置 `spring.ai.openai.chat.completions-path`。
2. 在仓库根目录构建后端：

   ```sh
   mvn -f backend/pom.xml -pl app -am package
   ```

   然后在同一个终端切换到 `backend/app` 并运行可执行 jar：

   ```sh
   cd backend/app
   java -jar target/hotel-selection-backend-app-0.1.0-SNAPSHOT.jar
   ```

   不要对 reactor 根 POM 执行 `spring-boot:run`；该插件必须在应用模块的运行时依赖 classpath 中启动。

3. 在另一个终端启动前端：

   ```sh
   cd frontend
   npm install
   npm run dev
   ```

4. 使用中文输入酒店需求。示例使用一个库存日期；该数据集没有覆盖连续多晚的同房型库存：

   ```text
   杭州市，2026-11-20 入住，2026-11-21 退房，预算 1000，必须有吹风机，从西湖出发步行怎么去？
   ```

后端测试：`mvn -f backend/pom.xml test`。前端构建：`cd frontend && npm run build`。测试中的模型和高德服务使用模拟响应，不访问真实线上服务。

## 数据重新生成

仅在数据工作簿或转换逻辑变更时执行：

```sh
python3 backend/scripts/generate_seed.py
```

生成文件位于 `backend/app/src/main/resources/data.sql`；不要手工编辑生成文件。
