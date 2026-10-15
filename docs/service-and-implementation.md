# Service 接口与 Impl 怎么配合

医生、身份、时段、预约这四组业务服务现在采用接口与实现类分开的方式。原来的业务逻辑放在 `application/impl` 中，Controller 和其他模块继续使用 `application` 下的 Service 接口。

```text
DoctorController
  → DoctorService 接口             声明业务能力与输入输出
  → DoctorServiceImpl              执行校验、组织事务、调用 Mapper
  → DoctorMapper                   执行 SQL
  → MySQL
```

接口与实现类属于同一个业务层。调用方声明自己需要 DoctorService，Spring 为它提供对应实现的 Bean；实际调用通常先经过 Spring 的代理，再进入实现方法。

## 从医生模块开始看

1. [DoctorService](../src/main/java/com/op1ed/appointmentmanager/doctor/application/DoctorService.java)：先看业务提供哪些方法、接收什么参数、返回什么结果。
2. [DoctorController](../src/main/java/com/op1ed/appointmentmanager/doctor/web/DoctorController.java)：构造方法接收 DoctorService，Controller 调用接口上的业务方法。
3. [DoctorServiceImpl](../src/main/java/com/op1ed/appointmentmanager/doctor/application/impl/DoctorServiceImpl.java)：原来的业务代码在这里，继续看权限、分页和 Mapper 调用。
4. [DoctorMapper](../src/main/java/com/op1ed/appointmentmanager/doctor/infrastructure/persistence/mapper/DoctorMapper.java)：最后看 SQL。

接口中的方法以分号结束，描述要提供的能力，例如：

```java
DoctorInfo requireById(long id);
```

实现类声明 `implements DoctorService`，表示自己实现这份业务约定。方法上的 `@Override` 让编译器检查签名是否正确。实现类上的 `@Service` 将它注册为 Spring 组件，Spring 把构造方法需要的 Mapper 等对象注入进来。

当前每个接口只有一个实现，调用方按接口类型注入即可。实现类的 `Impl` 后缀是命名约定；这些类没有继承 MyBatis-Plus 的 `ServiceImpl`，接口也没有继承 `IService`。接口只提供实际需要的业务方法。

## 四组接口对应哪些文件

| 业务 | 对外接口 | 具体实现 | 主要能力 |
| --- | --- | --- | --- |
| 医生 | [DoctorService](../src/main/java/com/op1ed/appointmentmanager/doctor/application/DoctorService.java) | [DoctorServiceImpl](../src/main/java/com/op1ed/appointmentmanager/doctor/application/impl/DoctorServiceImpl.java) | 创建、查询、分页、排班锁 |
| 身份 | [IdentityService](../src/main/java/com/op1ed/appointmentmanager/identity/application/IdentityService.java) | [IdentityServiceImpl](../src/main/java/com/op1ed/appointmentmanager/identity/application/impl/IdentityServiceImpl.java) | 注册、登录、令牌验证与撤销、管理员初始化 |
| 时段 | [SlotService](../src/main/java/com/op1ed/appointmentmanager/appointment/application/SlotService.java) | [SlotServiceImpl](../src/main/java/com/op1ed/appointmentmanager/appointment/application/impl/SlotServiceImpl.java) | 发布、查询、空闲时段分页 |
| 预约 | [AppointmentService](../src/main/java/com/op1ed/appointmentmanager/appointment/application/AppointmentService.java) | [AppointmentServiceImpl](../src/main/java/com/op1ed/appointmentmanager/appointment/application/impl/AppointmentServiceImpl.java) | 创建、查询、取消、改期、操作记录 |

接口使用 Command、Query、Result、UserIdentity 等业务对象表达输入输出，Mapper 与数据库实体留在实现中。Agent 的内部会话协调类、参数工具类继续按各自职责组织。

## 事务放在哪里

`@Transactional` 保留在 Impl 上。类上的 `readOnly = true` 是查询方法的默认配置，创建、取消、改期等方法单独声明写事务。Controller 和其他模块通过注入的接口调用，实现类的方法依然能被 Spring 事务代理拦截。

例如发布时段：

```text
SlotServiceImpl.create 开启写事务
  → 通过 DoctorService 调用 lockForScheduling
  → DoctorServiceImpl 的 MANDATORY 要求已有事务，并加入该事务
  → DoctorMapper 锁定医生记录
  → 检查重叠并插入时段
外层事务结束，释放医生锁
```

接口拆分不会创建新的事务边界。原有行锁顺序、预约与操作记录共同提交、Agent 确认时加入同一事务等规则都保留在实现中。

单元测试可以手动创建 Impl 并注入 mock，验证业务规则；这种调用不会经过 Spring 事务代理。事务、行锁和回滚由启动 Spring 的真实 MySQL 集成测试验证。架构测试还检查调用方依赖服务接口，跨模块不导入对应的 impl 包。

## 接下来怎么读

先用 DoctorService 与 DoctorServiceImpl 熟悉这组对应关系，再回到 IdentityServiceImpl 复习已经读过的注册、登录和管理员初始化。数据库调用继续参考 [MyBatis-Plus 阅读说明](mybatis-plus-migration.md)。下一批进入 SlotServiceImpl 的发布时段流程。
