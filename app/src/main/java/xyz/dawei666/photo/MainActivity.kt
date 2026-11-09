package xyz.dawei666.photo

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

// 极简 WebView 壳：加载 afilmory-web 网站。
// - 电视（leanback）UA 追加 "TV Leanback"，网站 TV_MODE 自动激活（图变大、列变少、灯箱出原图）
// - 手机 UA 不改，网站走正常布局
// - 不依赖 Chrome，用 Android System WebView（所有设备自带）
class MainActivity : Activity() {
    private lateinit var webView: WebView
    private val targetUrl = "https://photo.dawei666.xyz/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 全屏沉浸（无状态栏/标题栏）
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        webView = WebView(this)
        setContentView(webView)

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true            // localStorage（PWA 缓存/状态用）
            mediaPlaybackRequiresUserGesture = false  // 允许背景音乐/视频自动播放
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
            // TV 检测（多重条件，兼容小米 MIUI TV 等定制系统）：
            // 1. FEATURE_LEANBACK（标准 Android TV）
            // 2. UI_MODE_TYPE_TELEVISION（系统声明 TV 模式）
            // 3. 无触摸屏（电视通常无触摸，手机有）
            val uiMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK
            val isTV = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || uiMode == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
                || !packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN)
            if (isTV) {
                userAgentString = "$userAgentString TV Leanback"
            }
        }
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()

        // 旋转/切后台恢复时不重载（保留页面状态）
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(targetUrl)
        }
    }

    // 返回键：WebView 先 goBack，退无可退才退出 app
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    // app 切后台时暂停 WebView + JS 定时器（省 CPU/内存），回前台恢复
    override fun onPause() {
        super.onPause()
        webView.onPause()
        webView.pauseTimers()
    }
    override fun onResume() {
        super.onResume()
        webView.resumeTimers()
        webView.onResume()
    }
    // 退出时彻底销毁 WebView（释放渲染引擎 + 图片解码缓存，避免后台残留几百 MB）
    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
