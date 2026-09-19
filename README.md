# NeteasePets

一个纯服务端的 Spigot 狐狸伙伴插件。玩家不需要安装客户端模组或资源包。

插件参考了 LostPigYoo 的“我的伙伴”原版数据包所表达的行为设计，并针对多人服务器重新实现：每位玩家拥有独立狐狸、平滑跟随、管理菜单、可见性控制和持久化背包。仓库不包含参考数据包文件。

## 功能

- 每位玩家拥有按 UUID 隔离的狐狸伙伴
- 狐狸平滑移动并持续面朝主人，而不是频繁传送
- 接近主人后自然停下，不会自动坐下或蹲伏
- 遇到较低障碍会主动跳跃
- 仅在跨世界或严重掉队时安全传送到主人附近
- 玩家退出服务器时自动注销狐狸，并清理重复或遗留实体
- 狐狸免疫伤害和燃烧，不捡拾物品且不会自然消失
- 原地等待、睡觉、爱心、转圈等动作
- 赤狐和雪狐外观切换
- 临时粒子信标和随机唱片播放
- 四格持久化伙伴背包
- 原生箱子管理界面，无需书本菜单
- 玩家可分别控制：
  - 其他玩家是否能看到自己的狐狸
  - 自己是否显示其他玩家的狐狸
- 即使狐狸可见，非主人也不能操作它
- 本地文件或 MySQL 存储
- MySQL 支持域名、IPv4、IPv6 和带 Zone ID 的 IPv6 地址

## 兼容性

- 编译基线：Spigot API `1.20-R0.1-SNAPSHOT`
- Java：17 或更高版本
- 目标服务端：Spigot/Paper 1.20 及以上版本
- 不使用 NMS，因此不绑定特定服务端内部实现

项目针对新版服务端的 `Attribute` 常量改名做了兼容处理，但升级生产服务器前仍建议先在测试服验证。

## 安装

1. 从 Releases 下载插件，或按照下方步骤自行构建。
2. 将 JAR 放入服务端的 `plugins` 目录。
3. 启动服务端生成配置文件。
4. 如需 MySQL，停止服务器后编辑 `plugins/NeteasePets/config.yml`。
5. 重新启动服务端。

不建议使用 `/reload` 热重载插件。

## 构建

项目源码当前位于 `reconstructed` 目录：

```bash
cd reconstructed
mvn clean package
```

构建结果：

```text
reconstructed/target/NeteasePets-1.0-SNAPSHOT.jar
```

## 使用方法

执行 `/npets` 或 `/npets menu` 打开管理界面。右键自己的狐狸也会打开同一个管理界面。

| 命令 | 说明 |
| --- | --- |
| `/npets show` | 召唤伙伴 |
| `/npets hide` | 隐藏伙伴 |
| `/npets follow` | 恢复跟随 |
| `/npets stay` | 原地等待 |
| `/npets sleep` | 睡觉 |
| `/npets spin` | 转圈三秒 |
| `/npets happy` | 显示爱心动作 |
| `/npets red` | 切换为赤狐 |
| `/npets snow` | 切换为雪狐 |
| `/npets beacon` | 显示十秒临时信标 |
| `/npets music` | 随机播放唱片 |
| `/npets share` | 切换其他玩家是否能看到自己的狐狸 |
| `/npets others` | 切换自己是否显示其他玩家的狐狸 |

所有子命令均支持 Tab 补全。

## 可见性规则

默认情况下：

- 玩家始终可以看到自己的狐狸。
- 其他玩家可以看到该玩家的狐狸。
- 玩家可以看到其他人的狐狸。

最终是否显示一只别人的狐狸，同时取决于狐狸主人的 `share` 开关和观察者的 `others` 开关。可见性设置保存在玩家 PDC 中，服务器重启后仍然有效。

实体隐藏由服务端分别向每位玩家处理，不需要客户端配合。非主人对狐狸的交互事件始终会被取消。

## 背包与物品完整性

伙伴背包只有前四格可以使用，其余槽位被封锁。

背包以固定槽位的完整 `ItemStack[]` 通过 Bukkit `ConfigurationSerializable` 对象流保存，可保留槽位、数量、耐久、名称、Lore、附魔、属性、PDC、自定义模型数据和各类专用 ItemMeta。

旧版 JSON 背包会自动迁移。旧格式没有记录真实槽位编号，因此旧数据原本已经遗失的位置无法恢复。若旧背包第 5–9 格存在物品，升级后会返还玩家背包；放不下的物品会掉在玩家脚边。

不同 Minecraft 大版本可能改变内部物品表示。跨大版本升级前应备份插件数据并进行恢复测试。

## 配置

### 跟随行为

```yaml
pet:
  follow-distance: 2.8
  teleport-distance: 18.0
  max-speed: 0.42
```

- `follow-distance`：进入此距离后停止移动并注视主人。
- `teleport-distance`：超过此距离才使用兜底传送。
- `max-speed`：平滑跟随的最高水平速度，单位为格/tick。

### 本地存储

```yaml
storage: file
```

数据以原子替换方式写入 `plugins/NeteasePets/data`。

### MySQL

```yaml
storage: mysql

mysql:
  host: 2001:db8::1234
  port: 3306
  database: minecraft
  table: npets_inventory
  username: npets
  password: change-me
  ssl: true
  allow-public-key-retrieval: false
  connect-timeout-seconds: 10
  socket-timeout-seconds: 15
```

IPv6 地址可以填写为 `2001:db8::1234` 或 `[2001:db8::1234]`。插件会自动构造合法 JDBC 地址。

插件启动时会自动创建指定数据表。表名和数据库名只允许字母、数字及下划线。数据库读写在独立 I/O 线程完成，不会阻塞服务器 Tick；读取失败时不会用空背包覆盖已有数据。

建议为插件创建权限受限的独立数据库用户，并在公网连接中启用 TLS。密码目前由服务端配置文件明文保存，请正确限制文件访问权限。

## 数据安全

- 更换存储后端不会自动批量搬迁所有离线玩家数据。
- 玩家读取到旧本地数据时会按需迁移到当前存储后端。
- 切换后端或升级 Minecraft 大版本前，请备份 `plugins/NeteasePets` 和 MySQL 数据表。

## 许可证

本项目使用 [MIT License](LICENSE)。
