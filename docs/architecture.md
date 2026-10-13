# 架构与阅读指南

项目采用 **模块化单体 + 按业务分包 + 模块内轻量分层**。所有代码在一个 Maven 工程中，构建、部署一个应用；通过包结构和明确的调用规则区分业务职责。

## 业务模块如何划分

| 模块 | 拥有的业务 | 提供的能力 |
| --- | --- | --- |
| `doctor` | 医生资料 | 创建、查询医生；为排班锁定医生记录 |
| `appointment` | 排班时段、预约、取消、改期、操作记录、占用规则 | 发布时段、查询可用性、创建、取消和改期预约、查询操作记录 |
| `identity` | 账号、密码校验、登录令牌与身份信息 | 注册、登录、验证令牌、退出、初始化管理员；提供 `UserIdentity` |
| `shared` | 各模块共用的错误定义与 HTTP 错误转换 | 业务异常、统一错误响应 |

`shared` 保持小范围，不承载医生或预约业务。以后接入 Agent 时再创建相应模块，由工具调用应用服务；当前没有空的 Agent 模块。

### 为什么时段与预约属于同一个模块

时段的可用性由预约状态决定；创建预约需要锁定时段，取消预约也要与同一时段上的预约操作协调。如果按表拆成排班模块和预约模块，就容易出现“排班查预约、预约查排班”的双向依赖。

当前将这组规则放在 `appointment` 中，既能集中阅读，也能清晰控制事务。以后若出现独立的排班产品、多个业务使用同一套排班或更复杂的容量管理，再依据实际需求调整边界。

## 每层负责什么

```text
com.op1ed.appointmentmanager
├── AppointmentManagerApplication
├── doctor
│   ├── web
│   ├── application
│   ├── domain
│   └── infrastructure
│       └── persistence
├── appointment
│   ├── web
│   ├── application
│   ├── domain
│   └── infrastructure
│       └── persistence
├── identity
│   ├── web
│   │   └── security
│   ├── application
│   ├── domain
│   └── infrastructure
│       ├── persistence
│       └── bootstrap
└── shared
    ├── error
    └── web
```

| 层 | 负责什么 | 预约中的例子 |
| --- | --- | --- |
| `web` | HTTP 路径、JSON、输入格式校验、响应转换 | Controller、请求和响应 DTO |
| `application` | 一次业务用例的流程、业务输入、事务边界 | 创建预约、发布时段、取消与改期、记录业务操作 |
| `domain` | 业务对象及其自身行为 | 预约、时段、状态、改期条件、操作记录 |
| `infrastructure.persistence` | 具体数据库访问 | JPA Repository、带行锁的查询 |

`CreateAppointmentRequest` 是 HTTP 请求 DTO；Controller 将它转为 `BookAppointmentCommand`，调用预约应用服务。应用服务返回 `AppointmentResult`，再由 HTTP 响应 DTO 的 `from` 方法转换为原有 JSON。

时段创建同样使用 `CreateSlotCommand` 与 `SlotResult`，医生创建使用 `CreateDoctorCommand` 与 `DoctorInfo`。Command 和 Result 都放在本模块的 `application` 包中。预约与时段结果的时间字段保存 `Instant`，HTTP 响应转换时输出 UTC 的 `OffsetDateTime`。这样业务流程不需要知道 JSON 字段校验注解、HTTP 状态码或响应对象。

应用服务负责资源存在、时段有效和占用情况等业务校验。HTTP 格式校验是入口的便利，业务规则仍要保护未来的 Agent 等调用入口。

分页查询使用 `AppointmentQuery` 表达查询条件。`AppointmentService.search` 把筛选条件交给本模块 Repository，在数据库中分页和统计；再把查询结果转换为 `AppointmentPageResult`，由 `web` 转换成 `AppointmentPageResponse`。HTTP 层不直接暴露 Spring Data 的 `Page`，也不把所有预约先加载到内存后分页。

身份验证在 `identity.web.security` 适配器中完成：Spring Security 从 Bearer 令牌取得登录身份，Controller 收到普通 record `UserIdentity`，把它显式传给应用服务。应用服务不读取 `SecurityContextHolder`，也不依赖 HTTP 登录对象。身份模块内部验证账号和令牌，其他模块只使用其 `application` 入口。

### 当前分层的取舍

这是轻量分层：

- 业务模型继续使用 JPA 实体注解，一套对象承担业务表达与持久化映射。
- 应用服务直接调用本模块的 JPA Repository。
- 不为每个服务建立只有一个实现的接口。
- 不增加 Maven 子模块、事件总线或远程服务调用。

因此，这个项目没有宣称领域层完全独立于框架，也没有完整实现依赖倒置。先把真实职责、业务规则和事务边界建立清楚，后续需求需要替换数据库或分离模型时，再增加相应接口与映射。

## 依赖规则

```mermaid
flowchart TD
    HTTP[预约 HTTP Controller] --> APP[预约应用服务]
    FUTURE[未来的 Agent 工具] -.-> APP
    APP --> DOMAIN[预约与时段模型]
    APP --> REPO[预约模块 Repository]
    APP --> DOCTOR[医生应用服务]
    HTTP --> IDENTITY[UserIdentity]
    APP --> IDENTITY
    DOCTOR --> INFO[DoctorInfo]
    DOCTOR --> DREPO[医生模块 Repository]
    REPO --> MYSQL[(MySQL)]
    DREPO --> MYSQL
    WEBERROR[统一 HTTP 异常处理] --> ERROR[共享业务异常]
    APP --> ERROR
    DOCTOR --> ERROR
```

遵循以下规则：

1. `web` 调用 `application`，应用服务不依赖 Controller 或 HTTP DTO。
2. 应用服务可以访问本模块的领域模型和 Repository。
3. `appointment` 通过 `doctor.application.DoctorService` 使用医生能力，拿到普通 record `DoctorInfo`；不导入医生实体或 Repository。
4. `doctor` 不依赖 `appointment`，避免业务模块循环依赖。
5. `domain` 不调用 Controller、应用服务或其他模块的 Repository。
6. 业务代码使用 `shared.error` 中的业务异常，`shared.web` 把错误转换为 HTTP ProblemDetail。
7. `appointment` 和 `doctor` 只通过 `identity.application` 使用身份能力；身份模块不依赖预约或医生业务。
8. 管理员初始化位于 `identity.infrastructure.bootstrap`，是调用应用服务的启动适配器；持久化 Repository 仍不依赖应用层。
9. Agent 工具将来调用应用服务，共用规则、权限与事务，不直接操作 Repository。调用身份由服务端注入，不能由模型生成账号 ID 或管理员标记。

架构测试用于检查这些边界，防止后续开发逐渐绕过约定。

## 一次创建预约如何执行

客户端先创建医生、发布时段，取得真实 `slotId`，再发送：

```json
{
  "customerName": "张三",
  "slotId": 12
}
```

创建预约的流程：

1. Spring Security 验证令牌。Controller 对 HTTP 输入做格式校验，转成 `BookAppointmentCommand`，连同登录身份传给服务。
2. 预约应用服务开启事务，第一条数据库查询锁住目标时段。
3. 检查时段存在且尚未开始。
4. 通过医生应用服务取得医生信息。
5. 检查该时段是否存在 `BOOKED` 预约。
6. 创建预约，保存登录账号的 `owner_id`、预约人姓名、时段 ID、医生姓名与起止时间。
7. 保存并刷新数据，数据库唯一索引检查有效预约约束。
8. 同一事务保存 `BOOKED` 操作记录，操作人取当前登录账号。
9. 返回业务结果；事务提交后释放行锁，Controller 转换为响应。

两个请求预约同一个时段时，后一个请求等待行锁。前一个事务提交后，后一个再检查占用情况，返回冲突。时段列表中 `available=true` 只是读取当时的状态，不能代替这个事务内检查。

### 数据库约束如何保留取消历史

预约表的 `active_slot_id` 是数据库生成列：`BOOKED` 时等于 `slot_id`，取消时为 `NULL`。唯一索引限制同一个时段只存在一条有效预约，允许多条取消记录。

取消修改指定预约的状态并记录操作。重新预约创建一条新记录，因此再次取消旧预约不会影响新预约。取消先锁定有权访问的预约，读取最新状态；已取消时直接返回，否则再锁时段并保存取消与操作记录。创建预约先锁时段，随后只插入新预约，不申请已有预约的行锁。

改期先锁定有权访问的预约，再按 ID 升序锁定原时段与目标时段。确认状态、目标可用性和调用方的 `expectedSlotId` 后，在原预约上更新时段和医生快照，并在同一事务保存 `RESCHEDULED` 记录。真正修改前由领域模型再次检查起止条件，防止等待锁期间跨过开始时间。详细流程与并发边界见 [改期与操作记录](rescheduling-and-history.md)。

V3 以前的练习记录仍保留。它们的 `slot_id`、`end_time` 为空，可以查询和取消，但不占用新发布的时段。响应转换需要保留对空结束时间的处理。

V4 之前的预约 `owner_id` 为空，只向管理员开放，普通用户不能按姓名认领它们。新预约归属来自登录账号，与填写的预约人姓名独立。

## 跨模块调用不能破坏事务

发布时段时，需要锁住医生记录，再查询重叠时段，最后保存新时段：

```text
排班应用服务开启事务
    → DoctorService.lockForScheduling(doctorId)
    → 查询是否重叠
    → 保存时段
排班事务提交并释放医生锁
```

`lockForScheduling` 使用 `Propagation.MANDATORY`，要求调用方已有事务，并加入该事务。如果脱离事务单独调用，会直接失败。

这里不另开事务：如果医生服务返回时就提交并释放医生锁，两个排班请求仍可能同时通过重叠检查。普通读取医生使用 `requireById`，不申请排班锁。

移动包路径时，这些事务与查询顺序都要保留。包结构不会自动保证并发正确性，必须由事务、锁、数据库约束和测试共同验证。

## 错误如何从业务层到 HTTP

业务层使用业务异常表达非法输入、资源不存在或业务冲突，异常带稳定的 `code` 与中文说明。`INVALID_ARGUMENT`、`NOT_FOUND`、`CONFLICT` 分别表达这三类情况。它不抛 `ResponseStatusException`，也不选择 HTTP 状态码。

统一异常处理将错误映射为 400、401、403、404 或 409，并返回 ProblemDetail。认证过滤器与权限处理器使用同样的错误结构；HTTP 校验或无法读取请求 JSON，也由 HTTP 层给出统一错误响应。

创建、详情、取消等接口继续使用原有预约对象。预约列表接口现已升级为分页对象，结构为 `content`、`page`、`size`、`totalElements`、`totalPages`、`hasNext`，调用方需要从 `content` 读取记录。错误响应明确提供错误类型。以后 Agent 工具可以依据同一个业务错误判断如何回复、是否重新查询时段，而无需处理 HTTP 特有异常。

## 分页查询如何分工

```text
GET /api/appointments 的查询参数
    → HTTP Controller：解析数字、状态和带偏移的时间
    → Query：校验页码、页大小、姓名与时间边界
    → 应用服务：组织筛选、固定排序与数据库分页
    → Repository：执行筛选查询和总数统计
    → 分页 Result：业务记录与分页信息
    → 分页 Response：转换 content 中的预约并输出 JSON
```

规则放在 Query 与应用层，未来的 Agent 查询同样需要满足它们：

- HTTP 接口的页码默认 0，页大小默认 20。Query 校验页码非负、页大小为 1～100，且 `page * size` 不超过 `Integer.MAX_VALUE`；其他入口需显式传入页码和页大小。
- 固定按 `id ASC` 排序，避免数据库没有指定顺序时返回不稳定的页内容。
- 状态可以限制为 `BOOKED` 或 `CANCELLED`。
- 提供预约人姓名时，去掉首尾空格后不能为空、不能超过 100 个字符；使用 SQL 等值匹配，匹配行为遵循 MySQL 的 collation，不是模糊搜索。
- `from`、`to` 转为 UTC `Instant`，归一化到微秒精度，限定预约 `startTime` 的半开区间 `[from, to)`。
- 可只提供一个时间边界；同时提供时，归一化后的 `to` 必须晚于 `from`。查询可以使用历史时间。

应用服务计算 `hasNext`，分页 Result 保留它以及总记录数、总页数等分页信息，Response 负责输出这些字段。只有当前页的预约记录需要转换为业务结果和响应对象。

普通用户查询时，应用服务把登录账号 ID 作为强制归属条件传给 Repository，记录查询和 countQuery 都包含这个条件。管理员允许查询全部记录。详情、取消与改期同样在数据库查询中限制归属，访问他人的 ID 与不存在的 ID 都返回 404。操作历史先确认预约可见，再查询该预约的记录与总数。

## 建议的阅读顺序

1. 从预约 Controller 看输入输出，再看 `BookAppointmentCommand` 和 `AppointmentResult`，理解入口与业务的交接；对照列表接口的 [AppointmentQuery](../src/main/java/com/op1ed/appointmentmanager/appointment/application/AppointmentQuery.java)、[AppointmentPageResult](../src/main/java/com/op1ed/appointmentmanager/appointment/application/AppointmentPageResult.java) 与 [AppointmentPageResponse](../src/main/java/com/op1ed/appointmentmanager/appointment/web/dto/AppointmentPageResponse.java)，区分创建输入与查询输入。
2. 阅读 [预约应用服务](../src/main/java/com/op1ed/appointmentmanager/appointment/application/AppointmentService.java) 的创建方法，沿着事务、行锁、占用查询、保存的顺序画出流程。
3. 阅读 `Appointment`、`AppointmentSlot`、`AppointmentStatus`，区分对象自身行为与跨对象协调。
4. 阅读本模块 Repository 与 V3 迁移，理解代码检查和数据库约束各自保证什么；再沿着列表查询阅读筛选条件如何组合、分页如何交给数据库执行。
5. 阅读 [医生应用服务](../src/main/java/com/op1ed/appointmentmanager/doctor/application/DoctorService.java)，重点看普通读取与 `lockForScheduling` 的区别。
6. 阅读排班创建和取消逻辑，检查锁在什么时候获取、在什么时候释放。
7. 阅读 [统一异常处理](../src/main/java/com/op1ed/appointmentmanager/shared/web/GlobalExceptionHandler.java)，跟踪一个业务冲突如何变成 HTTP 响应。
8. 最后阅读集成测试与架构测试，理解它们分别检查行为和依赖边界。

继续沿着 [身份与权限说明](identity-and-permissions.md) 阅读登录、请求认证和归属过滤，再扩展 Agent 工具。目录结构的价值是帮助维护这些规则，而不是让项目拥有更多文件夹。
