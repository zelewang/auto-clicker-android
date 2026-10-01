# 自动连点器（AutoClicker）

一个极简的安卓屏幕连点工具：指定一个屏幕坐标，按设定间隔循环自动点击，用于个人日常的重复点击场景（如游戏挂机、抢票、签到等）。

## 功能

- 指定 X / Y 坐标（像素）进行连点，默认屏幕中心
- 可配置连点间隔（毫秒）和连点次数（0 = 无限，手动停止）
- 「测点」按钮：先单击一次验证坐标位置
- 通过系统无障碍服务模拟真实点击，无需 root
- 连点期间显示前台服务通知，可切到任意应用使用

## 安装使用

1. 构建 APK（见下）或直接安装已构建的 `out/AutoClicker-v1.0.apk`，传到手机后点击安装。
   - 如提示"未知来源"，在系统设置中允许安装未知应用。
2. 打开「自动连点器」，点「去开启无障碍服务」，在系统设置中开启本应用的服务开关。
3. 返回应用，填入 X/Y 坐标、间隔、次数。
4. 先点「测点」验证位置，再点「开始连点」。

> 注意事项：无障碍服务是敏感权限，请仅在你自己的手机上使用；部分应用（如银行、部分游戏）会限制或检测无障碍点击，可能违反其用户协议，请自行判断使用场景。

## 技术说明

- 语言/环境：Java（JDK 17 编译，target Java 8 字节码），minSdk 24（Android 7.0+），targetSdk 34
- 核心实现：`AutoClickService` 继承 `AccessibilityService`，用 `dispatchGesture()` 在 Handler 循环中按间隔派发点击手势；`MainActivity` 负责参数配置与启停控制
- 前台服务类型 `specialUse`，兼容 Android 14（API 34）

## 构建（从源码打包，无需 Gradle / Android Studio）

使用 Android SDK 命令行工具链（aapt2 + javac + d8 + zipalign + apksigner）：

1. 准备 SDK：`C:\Android\Sdk` 下放置 `platforms\android-34\android.jar` 与 `build-tools\34.0.0\`，以及 JDK 17。
2. 运行：

```powershell
powershell -ExecutionPolicy Bypass -File build.ps1 -SdkRoot "C:\Android\Sdk" -JdkHome "C:\Android\jdk-17.0.20.1+1"
```

3. 产物输出到 `out\AutoClicker-v1.0.apk`；构建时自动用 `tools/make_icon.py` 生成图标（无需提交二进制）。

> 提示：若项目所在路径含中文等非 ASCII 字符，请先将工程复制到纯英文路径（如 `C:\build\AutoClicker`）再运行构建脚本，避免 Windows 命令行对原生工具传参时出现乱码。

## 目录结构

```
AutoClicker/
├── AndroidManifest.xml
├── build.ps1                # 一键打包脚本
├── src/com/doubao/autoclicker/
│   ├── MainActivity.java    # 主界面：参数配置 + 启停
│   └── AutoClickService.java# 无障碍连点服务
├── res/                     # 资源（字符串、无障碍配置、图标）
└── tools/make_icon.py       # 图标生成脚本（纯 Python 标准库）
```
