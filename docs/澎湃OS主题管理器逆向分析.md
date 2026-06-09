# 澎湃OS主题管理器逆向分析

## 项目概览
- **应用包名**: com.android.thememanager
- **版本**: 4.2.4.8 (versionCode: 4248)
- **编译SDK**: 33 (Android 13)
- **最低支持**: Android 8.0 (API 26)
- **共享UID**: android.uid.theme

---

## 一、核心架构分析

### 1.1 应用初始化 (ThemeApplication.java)

**继承关系**:
- 父类: `miuix.app.zy`
- 实现接口: `com.android.thememanager.basemodule.analysis.toq`, `InterfaceC7074y`

**初始化流程** (onCreate方法:238-287行):
```java
// 1. 根据进程名称决定是否禁用WebView
if (i2 >= 28 && !"com.android.thememanager".equals(Application.getProcessName())) {
    WebView.disableWebView();
}

// 2. 核心模块初始化
- C1688q: 账户管理
- C1742y: 图像加载器
- C1781k: 隐私协议管理
- C1708s: 统计分析
- UserAgreementVersionManager: 用户协议版本管理

// 3. 设备类型判断
if (Build.IS_TABLET) {
    // 平板设备不启动调度服务
} else {
    ThemeSchedulerService.n7h(); // 启动主题调度服务
}

// 4. 自动密度配置
AutoDensityConfig.init(this);
AutoDensityConfig.setUpdateSystemRes(false);
```

**关键子类**:
- `RunnableC1531k`: 异步初始化任务
- `toq`: 外部存储变化广播接收器

### 1.2 关键服务组件 (service/目录)

| 服务类 | 功能描述 | 权限要求 |
|--------|---------|----------|
| `ThemeService` | 提供主题应用能力（图标、壁纸） | miui.permission.USE_INTERNAL_GENERAL_API |
| `ThemeSchedulerService` | 定时任务调度 | android.permission.BIND_JOB_SERVICE |
| `VideoWallpaperService` | 动态壁纸服务 | android.permission.BIND_WALLPAPER |
| `ThemePCService` | PC端连接服务 | - |
| `ThemeOtaUpdateProvider` | OTA更新提供者 | miui.permission.USE_INTERNAL_GENERAL_API |
| `ThemeRuntimeDataProvider` | 运行时数据提供者 | miui.permission.USE_INTERNAL_GENERAL_API |

**ThemeService Binder接口** (ThemeService.java:29-45):
```java
private Binder f14786k = new IThemeService.Stub() {
    // 保存自定义图标
    public boolean saveCustomizedIcon(String fileName, Bitmap icon)
    
    // 保存图标
    public boolean saveIcon(String srcImagePath)
    
    // 保存锁屏壁纸
    public boolean saveLockWallpaper(String srcImagePath)
    
    // 保存桌面壁纸
    public boolean saveWallpaper(String srcImagePath)
}
```

---

## 二、主题资源管理

### 2.1 Resource模型 (basemodule/resource/model/Resource.java)

**双状态管理结构**:
```java
private ResourceLocalProperties localProperties;   // 本地属性
private ResourceOnlineProperties onlineProperties;  // 在线属性
private Map<String, String> extraMeta;             // 额外元数据
```

**状态常量**:
- `STATUS_LOCAL = 1`: 本地资源
- `STATUS_ONLINE = 2`: 在线资源
- `STATUS_OLD = 4`: 过期资源

**支持的资源类型** (C1791k.java:38-71):
- `getTheme()`: 主题 (resourceCode: "theme")
- `getWallpaper()`: 壁纸 (resourceCode: "wallpaper")
- `getLockScreen()`: 锁屏 (resourceCode: "lockscreen")
- `getIcon()`: 图标 (resourceCode: "icons")
- `getFont()`: 字体 (resourceCode: "fonts")
- `getRingtone()`: 铃声 (resourceCode: "ringtone")
- `getAlarm()`: 闹钟 (resourceCode: "alarm")
- `getAod()`: 息屏显示 (resourceCode: "aod")

### 2.2 资源路径管理

**关键路径常量** (C1791k.java:73-113):
```java
// 获取资源内容文件夹
getContentFolder() -> 返回如: /data/system/theme/ 或特定资源路径

// 获取元数据文件夹
getMetaFolder() -> 返回如: /data/system/theme/meta/

// 获取构建时图像文件夹
getBuildInImageFolder() -> 返回预览图路径
```

**运行时路径映射** (n7h.java:50-55):
```java
f16651g.put("/data/system/theme/", "/data/system/theme/rights/");
f16651g.put(ThemeRuntimeManager.RUNTIME_PATH_BOOT_ANIMATION, "/data/system/theme/rights/");
```

---

## 三、主题应用流程

### 3.1 应用调用链

```
用户操作
  ↓
ThemeDetailActivity (主题详情页)
  ↓
Resource.java (资源模型)
  ↓
C1793p/C1794q (资源管理器)
  ↓
ThemeService (Binder接口)
  ↓
  → saveIcon/saveWallpaper/saveLockWallpaper
    ↓
    → d8wk/g1/uc 工具类
      ↓
      → ThemeNativeUtils.updateFilePermissionWithThemeContext (native层)
```

### 3.2 DRM验证机制 (n7h.java:66-109)

**验证流程**:
```java
private Pair<Boolean, DrmManager.DrmResult> m9889n(Context context, String contentPath, String rightsPath) {
    // 1. 检查文件是否存在
    if (!file.exists()) {
        return DRM_SUCCESS; // 文件不存在视为合法
    }
    
    // 2. 如果是目录，递归验证子文件
    if (file.isDirectory()) {
        for (File subFile : file.listFiles()) {
            // 递归验证
        }
    } else {
        // 3. 调用DRM管理器验证
        DrmManager.DrmResult result = DrmManager.isLegal(context, file, rightsFile);
        
        // 4. 验证失败记录日志
        if (result != DRM_SUCCESS) {
            DrmManager.exportFatalLog("illegal theme component found");
        }
    }
}
```

**白名单机制** (n7h.java:53-55):
```java
for (String str : ThemeManagerConstants.DRM_WHITE_LIST) {
    f61364f7l8.add("/data/system/theme/" + str);
}
```

---

## 四、超级壁纸模块 (superwallpaper/)

### 4.1 壁纸类型

| 类名 | 技术栈 | 功能描述 |
|------|---------|----------|
| `MamlSuperWallpaperDetailActivity` | MAML (MIUI XML) | MIUI自定义标记语言渲染 |
| `UnitySuperWallpaperDetailActivity` | Unity引擎 | 3D实时渲染壁纸 |
| `VideoWallpaperService` | 视频播放 | 动态视频壁纸 |

### 4.2 关键Activity

- **WallpaperDetailActivity**: 普通壁纸详情
- **WallpaperSubjectActivity**: 壁纸专题页
- **WallpaperLoopPreferenceActivity**: 循环壁纸设置
- **HomeWallpaperPreviewActivity**: 桌面壁纸预览

---

## 五、付费与账户系统

### 5.1 小米币支付 (com/mibi/)

**集成方式**:
- 支付SDK: `com.mibi.*`
- 权限: `com.xiaomi.xmsf.permission.PAYMENT`
- 相关Activity: `WidgetPurchaseActivity` (组件购买)

### 5.2 账户管理

**关键类**:
- `C1688q`: 账户核心管理
- `LoginDialogActivity`: 登录对话框（exported=true，可被外部调用）

**登录Intent Filter**:
```xml
<action android:name="miui.thememanager.Login"/>
<action android:name="miui.thememanager.ALL_CTA_AND_NETWORK"/>
```

---

## 六、个性化与定制 (settings/personalize/)

### 6.1 PersonalizeActivity

**功能范围**:
- 主题定制
- 壁纸选择
- 锁屏样式
- 字体设置
- 图标包
- 动画效果

**Intent入口**:
```xml
<action android:name="android.intent.action.VIEW"/>
<data android:scheme="theme" android:host="zhuti.xiaomi.com" android:path="/personalize"/>
```

---

## 七、网络与数据

### 7.1 网络框架

- **HTTP客户端**: okhttp3
- **网络请求**: retrofit2
- **接口域名**: zhuti.xiaomi.com
- **备用域名**: m.zhuti.xiaomi.com

### 7.2 主要功能模块

| 模块 | 功能 |
|------|------|
| 主题商店 | 浏览、搜索、下载主题 |
| 推荐系统 | 个性化推荐、排行榜 |
| 评论系统 | 主题评论、评分 |
| 搜索功能 | 关键词搜索、筛选 |
| 收藏管理 | 我的收藏、购买记录 |

### 7.3 网络配置

**网络安全配置** (AndroidManifest.xml:110):
```xml
android:networkSecurityConfig="@xml/network_security_config"
```

---

## 八、混淆与反编译现状

### 8.1 混淆特征

**类名混淆规则**:
- 主包: a5id, a9, a98o, ab等（无实际意义字符串）
- 方法名: 常见重命名（f7l8, toq, zy, n7h等）
- 字段名: f14786k, f10211k等

**未混淆部分**:
- Android系统类
- 第三方库（okhttp3, retrofit2, glide等）
- 部分MIUI框架类

### 8.2 反编译状态

**当前状态**:
- 工具: JADX或类似反编译工具
- 输出: Java源码（非smali）
- 限制: 无法直接回编译（无smali层）

**如需修改重打包**:
```bash
apktool d app-release-backup.apk -o thernesource_d
# 修改smali代码
apktool b thernesource_d -o modified.apk
# 签名
apksigner sign --ks keystore modified.apk
```

---

## 九、静默更换主题方案

### 方案1: 通过ThemeService Binder（需系统权限）

**权限要求**:
- `miui.permission.USE_INTERNAL_GENERAL_API`
- 系统签名或与系统相同签名
- 或ROOT权限

**实现代码**:
```java
// 1. 绑定ThemeService
Intent intent = new Intent();
intent.setComponent(new ComponentName(
    "com.android.thememanager",
    "com.android.thememanager.service.ThemeService"
));

// 2. 获取Binder并调用
IThemeService themeService = IThemeService.Stub.asInterface(service);

// 应用桌面壁纸
themeService.saveWallpaper("/path/to/wallpaper.jpg");

// 应用锁屏壁纸
themeService.saveLockWallpaper("/path/to/lockscreen.jpg");

// 应用图标
themeService.saveIcon("/path/to/icon.png");
```

### 方案2: 通过系统API（需系统级访问）

**关键类**:
- `miui.content.res.ThemeRuntimeManager`: 管理运行时主题路径
- `miui.content.res.ThemeNativeUtils`: Native层主题应用

**实现思路**:
```java
// 1. 将主题包放到系统主题目录
String themePath = "/data/system/theme/";

// 2. 调用Native API应用主题
ThemeNativeUtils.updateFilePermissionWithThemeContext(themePath);

// 3. 触发系统重新加载主题
// 可能需要发送广播或调用隐藏API
```

### 方案3: 通过shell命令（需ROOT）

```bash
# 1. 将主题文件推送到系统目录
adb shell su -c "cp /sdcard/theme.mtz /data/system/theme/"

# 2. 修改权限
adb shell su -c "chmod 644 /data/system/theme/theme.mtz"

# 3. 调用theme命令（如果存在）
adb shell su -c "theme apply /data/system/theme/theme.mtz"
```

### 方案4: 通过Intent启动（无需特殊权限，但非完全静默）

```java
Intent intent = new Intent();
intent.setAction("miui.intent.action.START_WALLPAPER_DETAIL");
intent.setDataAndType(Uri.fromFile(themeFile), "image/*");
intent.setPackage("com.android.thememanager");
context.startActivity(intent);
```

---

## 十、关键注意事项

### 10.1 DRM验证

**验证点** (n7h.java):
- 主题包会通过 `DrmManager.isLegal()` 进行DRM验证
- 验证不通过无法应用
- 验证日志会导出到: `ThemeBugreportDumpReceiver.f57252toq`

### 10.2 权限要求总结

| 权限 | 用途 | 获取难度 |
|------|------|----------|
| `miui.permission.USE_INTERNAL_GENERAL_API` | 调用内部API | 需系统签名 |
| `android.uid.theme` | 共享主题UID | 需相同签名 |
| `android.permission.BIND_WALLPAPER` | 绑定壁纸服务 | 系统权限 |
| `com.xiaomi.xmsf.permission.PAYMENT` | 支付功能 | 小米内部 |

### 10.3 推荐方案选择

- **系统开发者**: 使用方案1或2，直接调用系统API
- **ROOT用户**: 使用方案3，通过shell命令
- **逆向研究**: 可Hook相关API，绕过权限检查
- **普通开发者**: 使用方案4，通过Intent交互

---

## 十一、反编译代码结构

### 11.1 目录组织

```
thememanager_src/
├── sources/                    # 反编译的Java源码
│   ├── com/android/thememanager/  # 主应用包
│   │   ├── activity/             # Activity组件
│   │   ├── service/              # 服务组件
│   │   ├── basemodule/           # 基础模块
│   │   │   ├── resource/         # 资源管理
│   │   │   ├── account/         # 账户管理
│   │   │   └── utils/           # 工具类
│   │   ├── superwallpaper/       # 超级壁纸
│   │   ├── settings/             # 设置相关
│   │   └── util/                # 工具类
│   ├── miui/                     # MIUI框架
│   ├── okhttp3/                  # 网络库
│   └── retrofit2/                # 网络请求库
└── resources/                   # 资源文件
    ├── AndroidManifest.xml       # 清单文件
    ├── lib/                      # native库
    └── kotlin/                   # Kotlin内置
```

### 11.2 关键文件路径速查

| 功能 | 文件路径 |
|------|----------|
| 应用入口 | `com/android/thememanager/ThemeApplication.java` |
| 主题服务 | `com/android/thememanager/service/ThemeService.java` |
| 资源管理 | `com/android/thememanager/basemodule/resource/` |
| 主题模型 | `com/android/thememanager/basemodule/resource/model/Resource.java` |
| 应用验证 | `com/android/thememanager/util/n7h.java` |
| 超级壁纸 | `com/android/thememanager/superwallpaper/` |
| 支付模块 | `com/mibi/` |

---

## 附录：AndroidManifest.xml关键组件

### 入口Activity
- `ThemeResourceTabActivity`: 主界面（LAUNCHER）
- `ThemeTabActivity`: 主题标签页

### 关键Intent Filter
- `miui.intent.action.PICK_GADGET`: 选择小工具
- `android.intent.action.RINGTONE_PICKER`: 铃声选择
- `miui.intent.action.START_WALLPAPER_DETAIL`: 启动壁纸详情
- `theme://zhuti.xiaomi.com/`: 主题商店URL scheme

### Provider授权
- `ThemeRuntimeDataProvider`: 运行时数据
- `ThemeOtaUpdateProvider`: OTA更新
- `MamlDataProvider`: MAML数据（进程: mamldataprovider）

---

**文档版本**: v1.0  
**生成时间**: 2026年4月30日  
**分析对象**: com.android.thememanager_4.2.4.8  
**工具**: JADX反编译 + 手动分析
