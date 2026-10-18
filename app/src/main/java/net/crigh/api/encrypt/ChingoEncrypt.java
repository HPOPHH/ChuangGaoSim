package net.crigh.api.encrypt;

import android.util.Log;

public class ChingoEncrypt {
    // so 库会检查签名，签名不对会将返回体的其中一个字节设置为 '1'，导致服务器验签失败
    // 但很显然，我们已经用 ida 屏蔽了 so 库的相关逻辑
    private static final String TAG = "ChingoEncrypt";
    private static boolean isLibraryLoaded = false;

    static {
        try {
            // 必须加载这个假高德的名字
            System.loadLibrary("AMapSDK_Location_v6_6_0");
            isLibraryLoaded = true;
            Log.d(TAG, "Native library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load native library: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            Log.e(TAG, "Exception loading native library: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static native String cgapiAESEncrypt(String str);
    public static native String cgapiEnrypt(String str, String str2);
    public static native int cgapiVerify(String str, String str2, String str3, String str4);

    public static boolean isLibraryLoaded() {
        return isLibraryLoaded;
    }
}