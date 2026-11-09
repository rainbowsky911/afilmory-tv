# afilmory-tv

帧忆相册的 Android TV / 手机 app——极简 WebView 壳，加载 [afilmory-web](https://github.com/rainbowsky911/afilmory-web) 网站。

## 为什么用 WebView 壳（不用 TWA）

- **不依赖 Chrome**：TWA 需要 Chrome 浏览器作为 provider，国内电视（小米/海信/创维等）普遍没装 Chrome，TWA 打不开。WebView 壳用 **Android System WebView**（所有 Android 设备自带）。
- **网站是主体**：所有功能（画廊、灯箱、搜索、TV 适配）在 React 网站里（afilmory-web），app 只是全屏加载网站，代码量极小。
- **网站更新即生效**：改网站 push 到 Vercel，app 打开自动加载新版，**不用重新打包 APK**。
- **一个 APK 手机/电视通用**：Android 根据设备类型自动选图标（手机方形 / TV 宽幅 banner）。

## 架构

```
afilmory-tv (本仓库)          afilmory-web (网站仓库)
┌──────────────────┐          ┌──────────────────────┐
│ MainActivity.kt  │  WebView  │ React + Vite + TS     │
│  加载网站 URL ────────────→ │  画廊/灯箱/搜索/TV适配 │
│  全屏 + TV 检测   │          │  部署在 Vercel         │
└──────────────────┘          └──────────────────────┘
```

- `MainActivity.kt`：一个 Activity，WebView 加载 `https://photo.dawei666.xyz`
- **TV 检测**（多重条件，兼容小米 MIUI TV）：
  - `FEATURE_LEANBACK`（标准 Android TV）
  - `UI_MODE_TYPE_TELEVISION`（系统声明 TV 模式）
  - 无触摸屏（电视通常无触摸）
- TV 设备 UA 追加 `"TV Leanback"`，网站 `TV_MODE` 自动激活（大字、少列、焦点高亮）
- 返回键：WebView 先后退，退无可退才退出 app
- 切后台暂停 JS 定时器（`pauseTimers`）+ 退出销毁 WebView（释放内存）

## 构建

GitHub Actions CI 自动打包（`.github/workflows/build.yml`）：

1. push 到 `main` → CI 自动构建 debug APK
2. 从 [Actions 页面](https://github.com/rainbowsky911/afilmory-tv/actions)下载 artifact（`afilmory-tv-debug-apk.zip`）
3. 解压得到 `app-debug.apk`

### 签名（debug APK 需重新签名才能装）

```bash
# 1. 去掉 debug 签名
python -c "
import zipfile
with zipfile.ZipFile('app-debug.apk') as z, zipfile.ZipFile('app-unsigned.apk', 'w', zipfile.ZIP_DEFLATED) as z2:
    for i in z.infolist():
        if not (i.filename.startswith('META-INF/') and i.filename.endswith(('.SF','.RSA','.DSA','.MF'))):
            z2.writestr(i, z.read(i.filename))
"

# 2. 用 uber-apk-signer 签 v1+v2+v3
java -jar uber-apk-signer.jar \
  -a app-unsigned.apk \
  --out signed/ \
  --ks your.keystore \
  --ksAlias yourAlias \
  --ksPass yourPassword \
  --ksKeyPass yourPassword

# 输出 signed/app-aligned-signed.apk
```

## 图标

两套图标共存，Android 按设备类型选：

| 图标 | 路径 | 尺寸 | 用途 |
|---|---|---|---|
| ic_launcher | `mipmap-{mdpi..xxxhdpi}/ic_launcher.png` | 48~192 方形 | 手机 launcher |
| banner | `drawable-{xhdpi,xxhdpi}/banner.png` | 640x360 / 960x540（16:9） | Android TV launcher |

生成图标：用 PIL 从原图缩放/裁剪（方形中心裁剪，banner 16:9 裁剪）。

## 安装

```bash
# 手机/电视 adb 安装
adb install -r app-aligned-signed.apk

# Android TV 网络调试
adb connect <电视IP>:5555
adb install -r app-aligned-signed.apk
```

## 内存优化（1GB 可用内存电视）

- 网格用 **thumbnail**（小图，几十 KB/张）
- 灯箱用 **preview**（1080p 够清晰，不用原图 5-20MB）
- 后台暂停 JS 定时器（`pauseTimers`）
- 退出销毁 WebView（`webView.destroy()`）
- 前台占用 ~250MB，后台系统自动回收

## D-pad 遥控器支持

Android WebView 自带 D-pad 映射：
- **上下键** → 页面滚动
- **左右键** → button/link 间切换焦点 / 灯箱翻图
- **确认键** → 点击聚焦元素
- **返回键** → 退出灯箱 / 返回 / 退出 app

网站端 TV 模式加了焦点高亮 CSS（`.tv-mode button:focus` 显示白色边框），遥控器导航可见选中状态。
