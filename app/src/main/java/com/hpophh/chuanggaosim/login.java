package com.hpophh.chuanggaosim;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebView;

import java.lang.ref.WeakReference;

// 登录相关接口（兼容旧调用方式）
public class login {
    private static WeakReference<Activity> activityRef = new WeakReference<>(null);
    private static WeakReference<WebView> webViewRef = new WeakReference<>(null);

    private static final String TAG = "login";
    private static final String CALLBACK_PREFIX = "https://tyapp.nankai.edu.cn/cgapp-server/cas/appLogin";
    private static final String LOGIN_URL = "https://iam.nankai.edu.cn/api/cas/login?service=https%3A%2F%2Ftyapp.nankai.edu.cn%2Fcgapp-server%2Fcas%2FappLogin";

    public static class UserBody {
        public String jwt;
        public String secret;
    }

    private static Modules.LoginPrefs getStore() {
        Activity activity = activityRef.get();
        if (activity == null) {
            return null;
        }
        return new Modules.LoginPrefs(activity);
    }

    private static Modules.LoginPrefs getStore(Context context) {
        if (context == null) {
            return null;
        }
        return new Modules.LoginPrefs(context);
    }

    public static void bind(Activity act, WebView view) {
        activityRef = new WeakReference<>(act);
        webViewRef = new WeakReference<>(view);
    }

    // 获取登录数据
    public static String getLoginData(String jsessionId) {
        return Modules.LoginHttp.getLoginData(CALLBACK_PREFIX, jsessionId);
    }

    // 获取 jwtToken
    public static String GetJwtToken(String html) {
        return Modules.LoginParser.extractJwtToken(html);
    }

    public static UserBody buildUserBody(String jwtToken) {
        return Modules.LoginParser.buildUserBody(jwtToken);
    }

    public static void saveUserBody(UserBody userBody) {
        if (userBody == null) {
            Log.e(TAG, "saveUserBody failed: userBody is null");
            return;
        }

        Modules.LoginPrefs store = getStore();
        if (store == null) {
            Log.e(TAG, "saveUserBody failed: activity not initialized! Call bind() first.");
            return;
        }

        store.saveUserBody(userBody);
        Log.d(TAG, "UserBody saved to SharedPreferences");
    }

    public static UserBody loadUserBody(Context context) {
        Modules.LoginPrefs store = getStore(context);
        if (store == null) {
            return null;
        }
        return store.loadUserBody();
    }

    public static boolean hasValidSavedLogin() {
        Activity activity = activityRef.get();
        if (activity == null) {
            return false;
        }
        return hasValidSavedLogin(activity);
    }

    public static boolean hasValidSavedLogin(Context context) {
        UserBody userBody = loadUserBody(context);
        boolean valid = userBody != null && !Modules.LoginParser.isJwtExpired(userBody.jwt);
        if (!valid) {
            clearSavedAuth(context);
        }
        return valid;
    }

    public static void clearSavedAuth() {
        Activity activity = activityRef.get();
        if (activity != null) {
            clearSavedAuth(activity);
        }
    }

    public static void clearSavedAuth(Context context) {
        Modules.LoginPrefs store = getStore(context);
        if (store != null) {
            store.clearAuth();
        }
    }

    public static String startOAuthPage() {
        WebView webView = webViewRef.get();
        if (webView == null) {
            Log.e(TAG, "WebView not initialized! Call bind() first.");
            return null;
        }

        Log.d(TAG, "Starting OAuth page...");
        webView.post(() -> webView.loadUrl(LOGIN_URL));
        return getSavedJSessionId();
    }

    /**
     * 在 WebView.onPageFinished 中调用。
     * 命中回调 URL 且拿到 JSESSIONID 时返回该值，否则返回 null。
     */
    public static String onPageFinished(String url) {
        if (url == null || !url.startsWith(CALLBACK_PREFIX) || !url.contains("ticket=")) {
            return null;
        }

        CookieManager cookieManager = CookieManager.getInstance();
        String cookies = cookieManager.getCookie(url);
        if (cookies == null || cookies.isEmpty()) {
            Log.d(TAG, "No cookies found on callback url");
            return null;
        }

        String jsessionId = extractJSessionId(cookies);
        if (jsessionId == null || jsessionId.isEmpty()) {
            Log.d(TAG, "JSESSIONID not found in cookies");
            return null;
        }

        Modules.LoginPrefs store = getStore();
        if (store == null) {
            Log.e(TAG, "Activity not initialized! Call bind() first.");
            return null;
        }

        store.saveSession(cookies, jsessionId);
        return jsessionId;
    }

    public static String getSavedJSessionId() {
        Modules.LoginPrefs store = getStore();
        return store == null ? null : store.getSavedJSessionId();
    }

    /**
     * 从 cookie 字符串中提取 JSESSIONID
     */
    public static String extractJSessionId(String cookies) {
        return Modules.LoginParser.extractJSessionId(cookies);
    }
}
