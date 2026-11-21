package com.streamhub.app.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.streamhub.app.App

/**
 * Bot korumasi devredeyken siteyi gercek bir tarayici gibi acar; sayfa acilinca
 * "Çerezleri Kaydet ve Dön" ile çerezler uygulamaya aktarilir ve normal
 * istekler tekrar calismaya baslar.
 */
class WebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlText: TextView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = intent.getStringExtra("url") ?: run { finish(); return }
        val app = application as App

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
        }

        urlText = TextView(this).apply {
            text = target
            setTextColor(Ui.TEXT_DIM)
            textSize = 12f
            setPadding(Ui.dp(this@WebViewActivity, 12), Ui.dp(this@WebViewActivity, 8), Ui.dp(this@WebViewActivity, 12), 0)
            maxLines = 1
        }
        root.addView(urlText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val saveBtn = TextView(this).apply {
            text = com.streamhub.app.core.I18n.s("cookies_save")
            setTextColor(Ui.ACCENT)
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(0, Ui.dp(this@WebViewActivity, 12), 0, Ui.dp(this@WebViewActivity, 12))
            setOnClickListener {
                val url = webView.url ?: target
                val host = Uri.parse(url).host
                val raw = CookieManager.getInstance().getCookie(url)
                if (host != null && raw != null) {
                    app.http.importCookies(host, raw)
                    Ui.toast(this@WebViewActivity, com.streamhub.app.core.I18n.s("cookies_saved"))
                    finish()
                } else {
                    Ui.toast(this@WebViewActivity, com.streamhub.app.core.I18n.s("cookies_failed"))
                }
            }
        }
        root.addView(saveBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(TextView(this).apply {
            text = com.streamhub.app.core.I18n.s("webview_hint")
            setTextColor(Ui.TEXT_DIM)
            textSize = 12f
            setPadding(Ui.dp(this@WebViewActivity, 12), 0, Ui.dp(this@WebViewActivity, 12), Ui.dp(this@WebViewActivity, 8))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = com.streamhub.app.net.Http.UA
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    urlText.text = url ?: ""
                }
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
        webView.loadUrl(target)
    }

    override fun onBackPressed() {
        if (this::webView.isInitialized && webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}
