# nfc-odm17 1.3

在小米 15 Pro（haotian / ST54L）上移植小米 17（pudding）NFC 栈，并给华为四色墨水屏壳子增加“断场复位”入口。

## 修复

- **`q13.jar` 的 pause 超时不再写死 60000 ms**。服务端对 `pausePolling` 的超时有上限，
  实测 60000 会被拒绝（`Invalid timeout 60000 ms!`），旧版 `prep.sh` 的“暂停轮询”
  从未真正生效。现在自动从 `10000 / 5000 / 3000 / 2000 / 1000` 中探测可用值。
- **长窗口续期**：`pausePolling` 是“暂停 N 毫秒后自动恢复”，单次上限约 10 秒；
  现在每 3 秒续期一次，40 秒窗口不会提前漏场。

## 新增

- **`action.sh`**：KernelSU 模块的“执行”按钮，一键完成
  “关场 2 s → 配 7A → 留 40 秒窗口 → 开场”，替代把手机从壳子上拿开再贴回。
- **`prep.sh` 支持参数**：`prep.sh [dwellMs] [holdMs]`，例如 `prep.sh 2000 40000`。
- `q13.jar` 会打印每个 pause 候选的结果和 7A 配置返回码（`resp=1300` 为成功）。

## 使用

- 刷图前：KernelSU → 模块 → `nfc-odm17` → **执行**；然后在 40 秒内切到 QPollLab
  点「发送当前预览到墨水屏」→「确认并开始刷新」。
- 命令行等价：`su -c 'sh /data/adb/modules/nfc-odm17/prep.sh 2000 40000'`。
- 只配 7A（不做断场）：`su -c 'sh /data/adb/modules/nfc-odm17/prep.sh'`，开机
  `service.sh` 使用的就是这条。

## 说明与限制

- 模块只做运行时 bind mount，不修改真实分区；删除模块目录并重启即可恢复原厂。
- 依赖 `/data/local/tmp/frida-inject`（Frida 17.24.x android-arm64）注入 `mifare.js`。
- `0x13` 配置在 NFC 服务重启后会丢失；`action.sh` / `prep.sh` 会重新下发。
- 已知限制：刷新阶段可能出现 `SW=68CA`（低电压/供能不足），属于贴合压力与供电问题，
  与寻卡无关；压紧壳子或调大 App 的「上电等待」后重试。
