# 从熟悉的业务开始读 MyBatis-Plus

项目已将医生、身份、预约、Agent 动作与会话、Graph 检查点的数据访问统一迁到 MyBatis-Plus 3.5.17。Service 直接调用 Mapper，已移除额外的 Repository 封装。业务接口与表结构沿用原有设计，Flyway V1～V8 不变，服务器上的数据可以继续使用。

## 先看调用链

```text
DoctorController
  → DoctorService                 组织业务规则与事务
  → DoctorMapper / BaseMapper     执行 SQL、映射查询结果
  → MySQL
```

`Service` 是我们编写的业务类，负责校验、组织操作和事务；`Mapper` 是交给 MyBatis 实现的接口。Spring 启动时，`MybatisConfiguration` 扫描带 `@Mapper` 的接口，为它们创建代理对象，并注入 Service。调用代理的方法时，框架执行对应 SQL。

通用读写直接使用 `BaseMapper`，筛选和行锁等查询在本模块 Mapper 上声明 SQL。只有一个 Maven 工程，各模块仍按业务分包；预约模块使用医生能力时调用 `DoctorService`，不会直接查询医生表。

## 第一段：回看医生查询和创建

依次读：

1. [Doctor](../src/main/java/com/op1ed/appointmentmanager/doctor/domain/Doctor.java)
2. [DoctorMapper](../src/main/java/com/op1ed/appointmentmanager/doctor/infrastructure/persistence/mapper/DoctorMapper.java)
3. [DoctorService](../src/main/java/com/op1ed/appointmentmanager/doctor/application/DoctorService.java)
4. [MybatisConfiguration](../src/main/java/com/op1ed/appointmentmanager/shared/persistence/MybatisConfiguration.java)

`@TableName("doctors")` 指明表名；`@TableId(type = IdType.AUTO)` 让 MySQL 生成自增主键，插入成功后回填 `id`。字段名默认使用驼峰转下划线，例如 `passwordHash` 对应 `password_hash`。UUID 字符串主键使用 `IdType.INPUT`，由业务创建对象时提供。

`DoctorMapper extends BaseMapper<Doctor>` 表示它继承 MyBatis-Plus 提供的通用方法。例如 `insert` 插入医生，`selectById` 按主键查询，`updateById` 按主键更新。框架根据表与字段映射生成这些 SQL。

`DoctorService.create` 创建 Doctor 后调用 `doctorMapper.insert(doctor)`。这个方法返回影响行数，医生对象的 `id` 由框架回填，随后将 doctor 转为 DoctorInfo 返回。按编号查询时，Service 调用 `doctorMapper.selectById(id)`；不存在时返回 null，Service 用 `Optional.ofNullable` 转为“可能不存在”的业务结果。

医生的列表、姓名筛选、锁定查询使用 Mapper 上明确声明的 `@Select`。自定义方法名不会自动生成查询：`listOrdered`、`search`、`lockById` 的 SQL 都在 Mapper 中。Service 将姓名整理为 LIKE 查询参数，其中 `%`、`_` 被转义，继续表示普通字符。

`#{id}`、`#{pattern}` 是绑定参数。框架用预编译参数传值，用户输入不会作为 SQL 结构拼接。

## 第二段：回看账号注册

从 [IdentityService.register](../src/main/java/com/op1ed/appointmentmanager/identity/application/IdentityService.java) 往下读 [UserAccountMapper](../src/main/java/com/op1ed/appointmentmanager/identity/infrastructure/persistence/mapper/UserAccountMapper.java)。

注册的业务流程仍是：整理参数 → 检查用户名 → BCrypt 散列密码 → INSERT 账号 → 返回账号资料。登录继续使用 `PasswordEncoder.matches`，数据库访问框架不改变密码存储方式。

提前查重便于给出错误提示，真正的并发兜底仍是数据库用户名唯一约束。两个请求即使都通过查重，同时插入同名账号也只能成功一个。MyBatis 插入立即发送 SQL，Spring 将约束错误转换为 `DataIntegrityViolationException`，业务服务再转换为原有冲突响应。

## 与之前写法最重要的区别

| 操作 | 当前项目的写法 | 需要理解的行为 |
| --- | --- | --- |
| 新增 | `mapper.insert(newEntity)` | 返回影响行数，数据库生成的主键回填到传入对象 |
| 查询 | `mapper.selectById(id)` | 执行查询，返回普通 Java 对象或 null |
| 修改对象 | `appointment.cancel()` | 只改变内存里的对象 |
| 保存修改 | `mapper.updateById(appointment)` | 显式执行 UPDATE，返回影响行数 |
| 一组操作共同提交 | 应用服务上的 `@Transactional` | SQL 即使已经执行，事务回滚仍能撤销其数据库变更 |

新增与修改分别调用 `insert`、`updateById`，不再依赖 JPA 的脏检查，也没有 `flush` 步骤。Service 用 `PersistenceChecks.requireUpdatedRow(mapper.updateById(entity))` 检查更新结果：已锁定的记录应更新一行，否则抛异常，让同一事务中的其他变更一起回滚。这个工具只检查整数结果，不持有 Mapper，也不执行 SQL。

目前更新入口接收从数据库完整读取的实体。配置 `update-strategy=always` 使可空字段设为 `null` 时也能写回数据库；不要把只有部分字段的 HTTP 请求对象当成完整实体更新，否则其他列会被清空。Controller 的 DTO 与领域对象仍然分开。

`AppointmentStatus` 等普通枚举按名称保存，例如 `BOOKED`、`CANCELLED`，沿用原有字符串列。字段长度、非空、外键和唯一约束由 Flyway 中的建表 SQL 定义。

## 分页、锁与时间怎么保留

**分页**：应用与 HTTP 页号仍从 0 开始，Service 用 `StoragePage.request` 转为 MyBatis-Plus 从 1 开始的页号并传给 Mapper；`StoragePage.from` 把查询结果转回从 0 开始的页号。分页拦截器执行 count 与 LIMIT 查询，筛选条件和排序写在 Mapper 中。当前关闭 count SQL 的结构优化，使用完整筛选查询统计总数。`StoragePage` 是分页数据转换工具，HTTP 响应结构保持原样。

**行锁**：`DoctorMapper.lockById`、预约与时段的锁定查询直接执行 `SELECT ... FOR UPDATE`。锁必须在同一个 Spring 事务内持有，事务提交或回滚后释放。原有的医生排班锁、预约锁、时段按 ID 升序加锁规则不变。`local-cache-scope=STATEMENT` 避免后续 SQL 复用一级缓存对象，但数据库的隔离级别和查询顺序仍决定并发可见性。

**时间**：[UtcInstantTypeHandler](../src/main/java/com/op1ed/appointmentmanager/shared/persistence/typehandler/UtcInstantTypeHandler.java) 使用 UTC Calendar 通过 JDBC 读写时间，继续把 `DATETIME(6)` 当作 UTC 存储，保留微秒精度。预约输入的偏移时间仍先转换为 `Instant`。这保护服务器时区变化前后的时间含义。

**Graph**：`MybatisWorkflowCheckpointSaver` 新增检查点时 INSERT，覆盖检查点时显式 UPDATE。检查点、工作流修订号、动作状态、预约和请求响应仍在同一事务提交，模型网络调用仍在数据库事务之外。

## 验证与服务器更新

原有注册、权限、筛选分页、并发预约、改期、事务回滚、会话与 Graph 恢复测试继续验证业务行为。补充的 [MybatisPersistenceTest](../src/test/java/com/op1ed/appointmentmanager/shared/persistence/MybatisPersistenceTest.java) 使用真实 MySQL 检查主键回填、枚举与 UTC 微秒时间、显式写入 null，以及同一事务内重复锁定读不复用对象。

MyBatis-Plus 不提供之前 Hibernate 的启动映射结构校验。现在由 Flyway 管理结构，由真实 MySQL 集成测试验证实际读写。切换框架不需要新增迁移，也不需要删除数据库或 Docker 数据卷。

GitHub CI 通过后，在服务器项目目录拉取代码并重建 app：

```bash
cd /workspace/appointment-manager
git pull --ff-only
docker compose up -d --build app
docker compose logs --tail=100 app
```

如果你的 workspace 位于用户主目录，第一行改为实际路径。更新后可沿用 README 中的持久化验证脚本。

框架用法可对照官方文档：[安装与依赖](https://baomidou.com/getting-started/install/)、[表映射注解](https://baomidou.com/reference/annotation/)、[分页插件](https://baomidou.com/plugins/pagination/)。
