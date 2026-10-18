package com.hpophh.chuanggaosim;

import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebChromeClient;
import android.webkit.ValueCallback;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String LOGIN_PAGE = "file:///android_asset/web/login.html";
    private static final String WORK_PAGE = "file:///android_asset/web/work.html";

    private WebView webView;
    private volatile boolean processingLoginCallback;
    private ValueCallback<Uri[]> fileChooserCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableFullscreen();

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        applyEdgeInsets();

        webView = findViewById(R.id.webview);
        setupWebView();
        setupBackNavigation();

        login.bind(this, webView);
        work.bind(this);
        decideStartupRoute();
    }

    private void enableFullscreen() {
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decorView.setSystemUiVisibility(uiOptions);
    }

    private void applyEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            v.setPadding(0, 0, 0, 0);
            return insets;
        });
    }

    private void setupWebView() {
        Modules.WebViewConfig.configure(webView, url -> {
            String jsessionId = login.onPageFinished(url);
            if (jsessionId != null && !jsessionId.isEmpty()) {
                Log.d(TAG, "Login successful, JSESSIONID: " + jsessionId);
                handleLoginCallback(jsessionId);
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileChooserCallback != null) {
                    fileChooserCallback.onReceiveValue(null);
                }
                fileChooserCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                startActivityForResult(intent, 4101);
                return true;
            }
        });

        webView.addJavascriptInterface(new WebAppInterface(this, webView), "Bridge");
    }

    private void decideStartupRoute() {
        if (login.hasValidSavedLogin(this)) {
            runWorkFlow();
        } else {
            runLoginFlow();
        }
    }

    private void runLoginFlow() {
        loadLocalPage(LOGIN_PAGE);
    }

    private void runWorkFlow() {
        loadLocalPage(WORK_PAGE);
    }

    private void loadLocalPage(String url) {
        webView.loadUrl(url);
    }

    private void handleLoginCallback(String jsessionId) {
        if (processingLoginCallback) {
            return;
        }
        processingLoginCallback = true;

        new Thread(() -> {
            login.UserBody userBody = null;
            try {
                String html = login.getLoginData(jsessionId);
                String jwtToken = login.GetJwtToken(html);
                userBody = login.buildUserBody(jwtToken);
                if (userBody == null || Modules.LoginParser.isJwtExpired(userBody.jwt)) {
                    userBody = null;
                } else {
                    login.saveUserBody(userBody);
                }
            } catch (Exception e) {
                Log.e(TAG, "处理登录回调失败", e);
            }

            login.UserBody finalUserBody = userBody;
            runOnUiThread(() -> {
                processingLoginCallback = false;
                if (finalUserBody != null) {
                    Toast.makeText(MainActivity.this, "登录成功", Toast.LENGTH_SHORT).show();
                    runWorkFlow();
                } else {
                    login.clearSavedAuth(MainActivity.this);
                    Toast.makeText(MainActivity.this, "登录信息无效，请重试", Toast.LENGTH_SHORT).show();
                    runLoginFlow();
                }
            });
        }).start();
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 4101 || fileChooserCallback == null) {
            return;
        }
        Uri[] results = null;
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            results = new Uri[]{data.getData()};
        }
        fileChooserCallback.onReceiveValue(results);
        fileChooserCallback = null;
    }
}
