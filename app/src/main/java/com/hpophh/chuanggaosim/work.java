package com.hpophh.chuanggaosim;

import android.app.Activity;
import android.content.Context;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Time;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class work {
    private static Context appContext;
    private static final String TAG = "work";

    public static void bind(Activity act) {
        appContext = act == null ? null : act.getApplicationContext();
    }

    public static String buildUploadJsonSportsJson(Context context, String studentId, String studentName) {
        return buildUploadJsonSportsJson(context, studentId, studentName, 0L);
    }

    public static String buildUploadJsonSportsJson(Context context, String studentId, String studentName, long fetchedAtMs) {
        return buildUploadJsonSportsJson(context, studentId, studentName, fetchedAtMs, 0.0, "");
    }

    public static String buildUploadJsonSportsJson(Context context, String studentId, String studentName,
                                                   long fetchedAtMs, double km, String routeId) {
        String safeStudentId = studentId == null ? "" : studentId.trim();
        String safeStudentName = studentName == null ? "" : studentName.trim();
        if (safeStudentId.isEmpty()) {
            return buildErrorJson("studentId is required");
        }
        if (safeStudentName.isEmpty()) {
            return buildErrorJson("studentName is required");
        }

        Context safeContext = context != null ? context : appContext;
        if (safeContext == null) {
            return buildErrorJson("context is not initialized");
        }

        try {
            long beginTimestampMs;

            if (fetchedAtMs > 0) {
                // 前端已持久化保存了 sportId 的获取时间，直接用 fetchedAtMs + 2 秒
                beginTimestampMs = fetchedAtMs + 2000L;
                Log.d(TAG, "buildUploadJsonSportsJson: using persisted fetchedAtMs=" + fetchedAtMs + ", beginTimestampMs=" + beginTimestampMs);
            } else {
                // 前端没有传入有效的 fetchedAtMs（首次或重置），才请求新的 sportId
                String sportIdJsonStr = requestSportIdJson(safeContext, safeStudentId, safeStudentName);
                JSONObject sportIdJson;
                try {
                    sportIdJson = new JSONObject(sportIdJsonStr);
                } catch (Exception e) {
                    return buildErrorJson("failed to parse sportId response: " + e.getMessage());
                }
                if (sportIdJson.has("error")) {
                    return sportIdJsonStr;
                }
                long sportIdTimestamp = sportIdJson.optLong("timestamp", System.currentTimeMillis());
                beginTimestampMs = sportIdTimestamp + 2000L;
                Log.d(TAG, "buildUploadJsonSportsJson: fetched new sportId, timestamp=" + sportIdTimestamp + ", beginTimestampMs=" + beginTimestampMs);
            }

            com.hpophh.chuanggaosim.cg.UploadJsonSports sportBean = com.hpophh.chuanggaosim.fake.generateFakeSportBean(
                    safeContext, safeStudentId, safeStudentName, beginTimestampMs, km, routeId);
            JSONObject json = toJsonObject(sportBean);
            Log.d(TAG, "uploadJsonSports json=\n" + json);
            return json.toString();
        } catch (Exception e) {
            Log.e(TAG, "Failed to build UploadJsonSports json", e);
            return buildErrorJson("build UploadJsonSports failed: " + e.getMessage());
        }
    }

    public static String requestSportIdJson(Context context, String studentId, String studentName) {
        String safeStudentId = studentId == null ? "" : studentId.trim();
        String safeStudentName = studentName == null ? "" : studentName.trim();
        if (safeStudentId.isEmpty()) {
            return buildErrorJson("studentId is required");
        }
        if (safeStudentName.isEmpty()) {
            return buildErrorJson("studentName is required");
        }

        Context safeContext = context != null ? context : appContext;
        if (safeContext == null) {
            return buildErrorJson("context is not initialized");
        }

        try {
            login.UserBody userBody = login.loadUserBody(safeContext);
            if (userBody == null || userBody.jwt == null || userBody.secret == null) {
                return buildErrorJson("auth credentials are unavailable, please login again");
            }

            String responseText = http.get(safeContext, "/api/l/v7/sportsId", null, userBody.jwt, userBody.secret, safeStudentId);
            if (responseText.trim().isEmpty()) {
                return buildErrorJson("request sportId failed: empty response");
            }

            JSONObject serverJson;
            try {
                serverJson = new JSONObject(responseText);
            } catch (Exception parseError) {
                Log.w(TAG, "sportId response is not JSON: " + responseText);
                return buildErrorJson("request sportId failed: response is not JSON");
            }

            String sportsId = serverJson.getString("data");

            if (sportsId.isEmpty()) {
                return buildErrorJson("request succeeded but sportId is missing");
            }

            JSONObject result = new JSONObject();
            result.put("sportId", sportsId);
            result.put("timestamp", System.currentTimeMillis());
            return result.toString();
        } catch (Exception e) {
            Log.e(TAG, "Failed to request SportId", e);
            return buildErrorJson("request sportId failed: " + e.getMessage());
        }
    }

    public static String getSportTypesJson(Context context, String studentId) {
        Context safeContext = context != null ? context : appContext;
        if (safeContext == null) {
            return buildErrorJson("context is not initialized");
        }

        try {
            login.UserBody userBody = login.loadUserBody(safeContext);
            if (userBody == null || userBody.jwt == null || userBody.secret == null) {
                return buildErrorJson("auth credentials are unavailable, please login again");
            }

            // sporttype needs uid, which comes from the user info endpoint.
            String infoText = http.get(safeContext, "/api/l/v6/u/info", paramsOf("xh", studentId),
                    userBody.jwt, userBody.secret, studentId);
            String uid = "";
            try {
                JSONObject infoJson = new JSONObject(infoText);
                Object data = infoJson.opt("data");
                if (data instanceof JSONObject) {
                    JSONObject info = (JSONObject) data;
                    uid = info.optString("uid", "");
                    if (uid.isEmpty()) {
                        uid = info.optString("id", "");
                    }
                } else if (data instanceof String) {
                    uid = ((String) data).trim();
                }
                if (uid.isEmpty()) {
                    uid = infoJson.optString("uid", "");
                }
            } catch (Exception parseError) {
                Log.w(TAG, "u/info response is not JSON: " + infoText);
                return buildErrorJson("request uid failed: response is not JSON");
            }
            if (uid.isEmpty()) {
                return buildErrorJson("request uid failed: uid missing, raw=" + infoText);
            }

            // gender is a server-side filter; empty keeps all sport types/routes.
            String typeText = http.get(safeContext, "/api/l/v6/sporttype", paramsOf("gender", "", "uid", uid),
                    userBody.jwt, userBody.secret, studentId);
            if (typeText == null || typeText.trim().isEmpty()) {
                return buildErrorJson("request sporttype failed: empty response");
            }
            return typeText;
        } catch (Exception e) {
            Log.e(TAG, "Failed to request sport types", e);
            return buildErrorJson("request sporttype failed: " + e.getMessage());
        }
    }

    private static JSONObject paramsOf(String... kv) {
        JSONObject params = new JSONObject();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            try {
                params.put(kv[i], kv[i + 1] == null ? "" : kv[i + 1]);
            } catch (Exception ignored) {}
        }
        return params;
    }

    public static String getRouteJson(Context context, String routeId) {
        Context safeContext = context != null ? context : appContext;
        if (safeContext == null) {
            return buildErrorJson("context is not initialized");
        }

        String safeRouteId = routeId == null ? "" : routeId.trim();
        if (safeRouteId.isEmpty()) {
            return buildErrorJson("routeId is required");
        }

        try {
            login.UserBody userBody = login.loadUserBody(safeContext);
            if (userBody == null || userBody.jwt == null || userBody.secret == null) {
                return buildErrorJson("auth credentials are unavailable, please login again");
            }

            // Official contract (CrighService.getRoute): schoolid + distance + currPoint.
            // routeId doubles as an override JSON: {"schoolid":"..","distance":"..","currPoint":".."}
            String schoolid = "1";
            String distance = "";
            String currPoint = "117.349327,38.991185";
            if (safeRouteId.startsWith("{")) {
                try {
                    JSONObject override = new JSONObject(safeRouteId);
                    schoolid = override.optString("schoolid", schoolid);
                    distance = override.optString("distance", distance);
                    currPoint = override.optString("currPoint", currPoint);
                } catch (Exception ignored) {}
            }
            JSONObject params = new JSONObject();
            params.put("schoolid", schoolid);
            params.put("distance", distance);
            params.put("currPoint", currPoint);
            String responseText = http.get(safeContext, "/api/getRoute", params, userBody.jwt, userBody.secret, null);
            return responseText == null || responseText.trim().isEmpty()
                    ? buildErrorJson("request route failed: empty response")
                    : responseText;
        } catch (Exception e) {
            Log.e(TAG, "Failed to request route", e);
            return buildErrorJson("request route failed: " + e.getMessage());
        }
    }

    public static String getSportConfigJson(Context context) {
        Context safeContext = context != null ? context : appContext;
        if (safeContext == null) {
            return buildErrorJson("context is not initialized");
        }

        try {
            login.UserBody userBody = login.loadUserBody(safeContext);
            if (userBody == null || userBody.jwt == null || userBody.secret == null) {
                return buildErrorJson("auth credentials are unavailable, please login again");
            }

            String responseText = http.get(safeContext, "/api/l/v6.1/hangconf", null, userBody.jwt, userBody.secret, null);
            return responseText == null || responseText.trim().isEmpty()
                    ? buildErrorJson("request sport config failed: empty response")
                    : responseText;
        } catch (Exception e) {
            Log.e(TAG, "Failed to request sport config", e);
            return buildErrorJson("request sport config failed: " + e.getMessage());
        }
    }

    public static String requestImageUploadAuthJson(Context context, String sportId, String fileName) {
        Context safeContext = context != null ? context : appContext;
        if (safeContext == null) {
            return buildErrorJson("context is not initialized");
        }
        try {
            login.UserBody userBody = login.loadUserBody(safeContext);
            if (userBody == null || userBody.jwt == null || userBody.secret == null) {
                return buildErrorJson("auth credentials are unavailable, please login again");
            }
            // Official contract (CrighService.uploadAuthInfos): multipart with
            // xh + sportId + authType + authInfos(JSON array of AuthInfoCard).
            // For the manual workbench we report a face-collection pass at the route start.
            String safeSportId = sportId == null ? "" : sportId.trim();
            String safeName = fileName == null || fileName.trim().isEmpty()
                    ? "auth-face.jpg" : fileName.trim();

            String infoText = http.get(safeContext, "/api/l/v6/u/info", paramsOf("xh", ""),
                    userBody.jwt, userBody.secret, null);
            String uid = "";
            try {
                JSONObject infoJson = new JSONObject(infoText);
                Object data = infoJson.opt("data");
                if (data instanceof JSONObject) {
                    uid = ((JSONObject) data).optString("id", "");
                }
            } catch (Exception ignored) {}

            org.json.JSONArray authArray = new org.json.JSONArray();
            JSONObject card = new JSONObject();
            card.put("pointId", "1");
            card.put("result", 1);
            card.put("datetime", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                    .format(new java.util.Date()));
            card.put("faceImage", safeName);
            card.put("latlng", "38.991185,117.349327");
            card.put("pointType", 1);
            authArray.put(card);

            java.util.LinkedHashMap<String, String> fields = new java.util.LinkedHashMap<>();
            fields.put("authType", "1");
            fields.put("authInfos", authArray.toString());
            // Empty file part mirrors the official "no face image" branch (m9521("","")).
            return http.postMultipartRequest(safeContext, "/api/l/v6/sports/uploadAuthInfos",
                    new byte[]{0}, safeName, safeSportId, userBody.jwt, userBody.secret, uid, fields);
        } catch (Exception e) {
            Log.e(TAG, "Failed to request image upload auth", e);
            return buildErrorJson("request image upload auth failed: " + e.getMessage());
        }
    }

    public static String uploadSportImageJson(Context context, String dataUrl, String fileName,
                                              String studentId, String sportId) {
        Context safeContext = context != null ? context : appContext;
        if (safeContext == null) {
            return buildErrorJson("context is not initialized");
        }
        try {
            login.UserBody userBody = login.loadUserBody(safeContext);
            if (userBody == null || userBody.jwt == null || userBody.secret == null) {
                return buildErrorJson("auth credentials are unavailable, please login again");
            }
            String encoded = dataUrl == null ? "" : dataUrl;
            int comma = encoded.indexOf(',');
            if (comma >= 0) {
                encoded = encoded.substring(comma + 1);
            }
            byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
            if (bytes.length == 0 || bytes.length > 10 * 1024 * 1024) {
                return buildErrorJson("image must be between 1 byte and 10 MB");
            }
            String responseText = http.postMultipartBytes(safeContext, "/api/l/v6/uploadpic", bytes,
                    fileName, sportId, userBody.jwt, userBody.secret, studentId);
            JSONObject result = new JSONObject();
            result.put("uploadResponse", parseJsonObject(responseText, new JSONObject().put("rawText", responseText)));
            // Official semantic: sportImages is a "has image" marker ("1"), not a URL.
            result.put("sportImages", responseText != null && responseText.contains("\"code\":200") ? "1" : "");
            return result.toString();
        } catch (Exception e) {
            Log.e(TAG, "Failed to upload sport image", e);
            return buildErrorJson("upload sport image failed: " + e.getMessage());
        }
    }

    private static String extractImageReference(String responseText) {
        JSONObject response = parseJsonObject(responseText, null);
        if (response == null) {
            return "";
        }
        String[] keys = {"url", "fileUrl", "imageUrl", "path", "key"};
        for (String key : keys) {
            String value = response.optString(key, "").trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        Object data = response.opt("data");
        if (data instanceof String) {
            return ((String) data).trim();
        }
        if (data instanceof JSONObject) {
            JSONObject dataObject = (JSONObject) data;
            for (String key : keys) {
                String value = dataObject.optString(key, "").trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return "";
    }

    public static String getSportRecordsJson(Context context, String queryJson) {
        Context safeContext = context != null ? context : appContext;
        if (safeContext == null) {
            return buildErrorJson("context is not initialized");
        }

        try {
            login.UserBody userBody = login.loadUserBody(safeContext);
            if (userBody == null || userBody.jwt == null || userBody.secret == null) {
                return buildErrorJson("auth credentials are unavailable, please login again");
            }

            JSONObject query = parseJsonObject(queryJson, new JSONObject());
            String endpoint = query.optString("endpoint", "/api/l/v7/sportlist").trim();
            if (endpoint.isEmpty()) {
                endpoint = "/api/l/v7/sportlist";
            }
            if (!endpoint.startsWith("/")) {
                endpoint = "/" + endpoint;
            }

            String studentId = query.optString("studentId", "").trim();
            int type = query.optInt("type", 1);

            JSONObject params = new JSONObject();
            if (!studentId.isEmpty()) {
                params.put("xh", studentId);
            }
            params.put("type", type);
            // Extra pass-through params (e.g. sportId/term for sport/detail).
            JSONObject extra = query.optJSONObject("params");
            if (extra != null) {
                java.util.Iterator<String> keys = extra.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    params.put(key, extra.optString(key, ""));
                }
            }

            String responseText = http.get(safeContext, endpoint, params, userBody.jwt, userBody.secret, studentId);
            if (responseText == null || responseText.trim().isEmpty()) {
                return buildErrorJson("request sport records failed: empty response");
            }

            JSONObject serverJson = parseJsonObject(responseText, null);
            if (serverJson == null) {
                JSONObject fallback = new JSONObject();
                fallback.put("success", false);
                fallback.put("endpoint", endpoint);
                fallback.put("items", new JSONArray());
                fallback.put("error", "response is not JSON");
                fallback.put("rawText", responseText);
                return fallback.toString();
            }

            if (serverJson.optString("error", "").trim().length() > 0) {
                return serverJson.toString();
            }

            // Non-list endpoints (e.g. sport/detail) return an object — pass it through raw.
            if (!endpoint.contains("sportlist")) {
                return responseText;
            }

            JSONArray items = extractRecordsArray(serverJson);
            JSONArray normalizedItems = new JSONArray();
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null) {
                    normalizedItems.put(normalizeRecord(item));
                }
            }

            // 按 beginTime 从近到远排序
            sortRecordsByTime(normalizedItems);

            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("endpoint", endpoint);
            result.put("total", resolveTotal(serverJson, normalizedItems.length()));
            result.put("hasMore", resolveHasMore(serverJson, normalizedItems.length()));
            result.put("items", normalizedItems);
            return result.toString();
        } catch (Exception e) {
            Log.e(TAG, "Failed to request sport records", e);
            return buildErrorJson("request sport records failed: " + e.getMessage());
        }
    }

    private static JSONObject normalizeRecord(JSONObject raw) {
        try {
            JSONObject normalized = new JSONObject();

            // 关键展示字段
            normalized.put("recordId", raw.optString("id", ""));
            normalized.put("beginTime", raw.optString("beginTime", ""));
            normalized.put("endTime", raw.optString("endTime", ""));
            normalized.put("activeTime", raw.optString("activeTime", ""));
            normalized.put("distance", raw.optString("odometer", ""));
            normalized.put("calorie", raw.optString("calorie", ""));
            normalized.put("avgSpeed", raw.optString("avgSpeed", ""));
            normalized.put("avgPace", raw.optString("avgPace", ""));
            normalized.put("stepCount", raw.optString("stepCount", ""));

            // 状态字段
            normalized.put("tip", raw.optString("tip", ""));
            normalized.put("isValid", raw.optString("isValid", ""));
            normalized.put("checkStatus", raw.optString("checkStatus", ""));

            // 运动路线信息
            normalized.put("planRouteName", raw.optString("planRouteName", ""));
            normalized.put("subType", raw.optString("subType", ""));
            normalized.put("routeId", firstNonEmptyString(raw, "routeId", "routeid", "RouteId"));
            normalized.put("routePolylineBh", firstNonEmptyString(raw,
                    "routePolylineBh", "routePolylineBH", "RoutePolylineBh"));

            // Server-side photo path (uploads/sportimages/<term>/<xh>/<uuid>.jpg).
            // The web UI builds the thumbnail URL: serverUrl + "/" + sportImages.
            normalized.put("sportImages", firstNonEmptyString(raw,
                    "sportImages", "sportimages", "sportImg", "images"));
            normalized.put("serverUrl", serverUrlForUi());

            return normalized;
        } catch (Exception e) {
            Log.w(TAG, "Failed to normalize record", e);
            try {
                JSONObject fallback = new JSONObject();
                fallback.put("recordId", raw.optString("id", ""));
                fallback.put("error", "normalize failed");
                return fallback;
            } catch (Exception ignored) {
                return new JSONObject();
            }
        }
    }

    private static String firstNonEmptyString(JSONObject source, String... keys) {
        if (source == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            String value = source.optString(key, "").trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static String serverUrlForUi() {
        return http.baseUrlForUi();
    }

    private static JSONObject parseJsonObject(String text, JSONObject fallback) {
        if (text == null || text.trim().isEmpty()) {
            return fallback;
        }
        try {
            return new JSONObject(text);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static JSONArray extractRecordsArray(JSONObject source) {
        if (source == null) {
            return new JSONArray();
        }

        JSONArray direct = firstNonNullArray(
                source.optJSONArray("data"),
                source.optJSONArray("items"),
                source.optJSONArray("list"),
                source.optJSONArray("rows"),
                source.optJSONArray("records")
        );
        if (direct != null) {
            return direct;
        }

        JSONObject nested = firstNonNullObject(
                source.optJSONObject("data"),
                source.optJSONObject("result"),
                source.optJSONObject("response"),
                source.optJSONObject("page")
        );
        if (nested != null) {
            JSONArray nestedArray = firstNonNullArray(
                    nested.optJSONArray("items"),
                    nested.optJSONArray("list"),
                    nested.optJSONArray("rows"),
                    nested.optJSONArray("records"),
                    nested.optJSONArray("data")
            );
            if (nestedArray != null) {
                return nestedArray;
            }
        }

        return new JSONArray();
    }

    private static int resolveTotal(JSONObject source, int fallbackCount) {
        if (source == null) {
            return fallbackCount;
        }

        int direct = firstPositiveInt(
                source.optInt("total", -1),
                source.optInt("count", -1),
                source.optInt("totalCount", -1)
        );
        if (direct >= 0) {
            return direct;
        }

        JSONObject nested = firstNonNullObject(
                source.optJSONObject("data"),
                source.optJSONObject("result"),
                source.optJSONObject("page")
        );
        if (nested != null) {
            int nestedTotal = firstPositiveInt(
                    nested.optInt("total", -1),
                    nested.optInt("count", -1),
                    nested.optInt("totalCount", -1)
            );
            if (nestedTotal >= 0) {
                return nestedTotal;
            }
        }

        return fallbackCount;
    }

    private static boolean resolveHasMore(JSONObject source, int currentCount) {
        if (source != null) {
            if (source.has("hasMore")) {
                return source.optBoolean("hasMore", false);
            }
            JSONObject nested = firstNonNullObject(
                    source.optJSONObject("data"),
                    source.optJSONObject("result"),
                    source.optJSONObject("page")
            );
            if (nested != null && nested.has("hasMore")) {
                return nested.optBoolean("hasMore", false);
            }
        }

        return false;
    }

    private static JSONArray firstNonNullArray(JSONArray... arrays) {
        if (arrays == null) {
            return null;
        }
        for (JSONArray array : arrays) {
            if (array != null) {
                return array;
            }
        }
        return null;
    }

    private static int firstPositiveInt(int... values) {
        if (values == null) {
            return -1;
        }
        for (int value : values) {
            if (value >= 0) {
                return value;
            }
        }
        return -1;
    }

    private static String extractSportId(JSONObject json) {
        if (json == null) {
            return "";
        }

        String direct = firstNonBlank(
                json.optString("sportId", ""),
                json.optString("sportid", ""),
                json.optString("id", "")
        );
        if (!direct.isEmpty()) {
            return direct;
        }

        JSONObject nested = firstNonNullObject(
                json.optJSONObject("data"),
                json.optJSONObject("result"),
                json.optJSONObject("response")
        );
        if (nested != null) {
            return extractSportId(nested);
        }

        JSONArray dataArray = json.optJSONArray("data");
        if (dataArray != null && dataArray.length() > 0) {
            Object first = dataArray.opt(0);
            if (first instanceof JSONObject) {
                return extractSportId((JSONObject) first);
            }
            if (first != null) {
                return String.valueOf(first).trim();
            }
        }

        return "";
    }

    private static JSONObject firstNonNullObject(JSONObject... items) {
        if (items == null) {
            return null;
        }
        for (JSONObject item : items) {
            if (item != null) {
                return item;
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return "";
    }

    private static String buildErrorJson(String message) {
        try {
            JSONObject error = new JSONObject();
            error.put("error", message);
            return error.toString();
        } catch (Exception e) {
            return "{\"error\":\"" + message + "\"}";
        }
    }

    private static JSONObject toJsonObject(Object bean) throws Exception {
        Object converted = toJsonValue(bean, Collections.newSetFromMap(new IdentityHashMap<>()));
        if (converted instanceof JSONObject) {
            return (JSONObject) converted;
        }
        JSONObject fallback = new JSONObject();
        fallback.put("value", converted);
        return fallback;
    }

    private static Object toJsonValue(Object value, Set<Object> visited) throws Exception {
        if (value == null) {
            return JSONObject.NULL;
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Character) {
            return value.toString();
        }

        Class<?> clazz = value.getClass();
        if (clazz.isEnum()) {
            return value.toString();
        }

        if (clazz.isArray()) {
            JSONArray array = new JSONArray();
            int len = Array.getLength(value);
            for (int i = 0; i < len; i++) {
                array.put(toJsonValue(Array.get(value, i), visited));
            }
            return array;
        }

        if (value instanceof Collection) {
            JSONArray array = new JSONArray();
            for (Object item : (Collection<?>) value) {
                array.put(toJsonValue(item, visited));
            }
            return array;
        }

        if (visited.contains(value)) {
            return JSONObject.NULL;
        }
        visited.add(value);

        JSONObject object = new JSONObject();
        for (Field field : clazz.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                continue;
            }
            object.put(field.getName(), toJsonValue(field.get(value), visited));
        }
        visited.remove(value);
        return object;
    }

    private static void sortRecordsByTime(JSONArray items) {
        if (items == null || items.length() <= 1) {
            return;
        }

        // 使用冒泡排序，按 beginTime 从新到旧排序
        for (int i = 0; i < items.length(); i++) {
            for (int j = 0; j < items.length() - i - 1; j++) {
                JSONObject curr = items.optJSONObject(j);
                JSONObject next = items.optJSONObject(j + 1);

                if (curr == null || next == null) {
                    continue;
                }

                String currTime = curr.optString("beginTime", "");
                String nextTime = next.optString("beginTime", "");

                // 比较时间戳字符串，从新到旧（降序）
                // "2026-03-25 13:07:57" > "2026-03-22 14:17:19" 时需要交换
                if (currTime.compareTo(nextTime) < 0) {
                    // 交换两个元素
                    try {
                        items.put(j, next);
                        items.put(j + 1, curr);
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to swap records during sort", e);
                    }
                }
            }
        }
    }
}
