package com.hpophh.chuanggaosim;

import android.content.Context;
import android.util.Log;

import com.hpophh.chuanggaosim.cg.SportLatLngBean;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

// 创建虚假运动数据的核心类
public class fake {
    private static final String TAG = "fake";
    private static final double MIN_ODOMETER_KM = 4.85;
    private static final double ODOMETER_VARIATION_KM = 0.30;
    private static final String FIXED_ROUTE_ID = "15";
    private static final String FIXED_ROUTE_NAME = "津南校区主体育场";
    // The server-side polyline revision is not available from sportlist and must not reuse the old route's value.
    private static final String FIXED_ROUTE_POLYLINE_BH = "";

    // ── route registry (from /api/l/v6/sporttype latlng, lng,lat pairs) ──
    private static class RouteDef {
        final String id;
        final String name;
        final double[][] polyline; // {lng, lat}
        final Double centerLng;    // measured ground-truth track center (null = derive)
        final Double centerLat;
        double[] geom;             // {centerLat, centerLng, headingDeg, mPerDegLng}

        RouteDef(String id, String name, double[][] polyline, Double centerLng, Double centerLat) {
            this.id = id;
            this.name = name;
            this.polyline = polyline;
            this.centerLng = centerLng;
            this.centerLat = centerLat;
        }
    }

    private static final java.util.HashMap<String, RouteDef> ROUTES = new java.util.HashMap<>();

    private static void putRoute(String id, String name, String latlngStr) {
        putRoute(id, name, 0.0, 0.0, latlngStr);
    }

    private static void putRoute(String id, String name, double centerLng, double centerLat, String latlngStr) {
        String[] pairs = latlngStr.split(";");
        double[][] pts = new double[pairs.length][2];
        for (int i = 0; i < pairs.length; i++) {
            String[] xy = pairs[i].split(",");
            pts[i][0] = Double.parseDouble(xy[0].trim());
            pts[i][1] = Double.parseDouble(xy[1].trim());
        }
        ROUTES.put(id, new RouteDef(id, name, pts, centerLng, centerLat));
    }

    static {
        // Route geometry is user-supplied at runtime via the workbench; routes not
        // registered here fall back to the custom route-ID input in the UI.
        putRoute("16", "津南校区理科田径场", 117.33871008396147, 38.985740885155025,
                "117.338128,38.985905;117.338395,38.986615;117.33901,38.986574;117.339604,38.986368;"
                        + "117.339001,38.984779;117.338375,38.984925;117.337846,38.985174;117.338091,38.985809;117.338128,38.985905");
    }

    private static RouteDef resolveRoute(String routeId) {
        RouteDef def = routeId == null ? null : ROUTES.get(routeId.trim());
        if (def != null) {
            return def;
        }
        // Unregistered route: derive geometry from the custom route-ID path in the UI.
        for (RouteDef any : ROUTES.values()) {
            return any; // single registered fallback keeps the pipeline alive
        }
        return null;
    }

    private static String resolveRouteName(String routeId) {
        return resolveRoute(routeId).name;
    }
    private static volatile Context appContext;

    public static void bind(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
    }

    public static com.hpophh.chuanggaosim.cg.UploadJsonSports generateFakeSportBean(Context context) {
        return generateFakeSportBean(context, "", "", 0L, 5.0, FIXED_ROUTE_ID);
    }

    public static com.hpophh.chuanggaosim.cg.UploadJsonSports generateFakeSportBean(Context context, String studentId, String studentName) {
        com.hpophh.chuanggaosim.cg.UploadJsonSports uploadJsonSports = generateFakeSportBean(context);
        uploadJsonSports.xh = studentId == null ? "" : studentId.trim();
        uploadJsonSports.name = studentName == null ? "" : studentName.trim();
        return uploadJsonSports;
    }

    public static com.hpophh.chuanggaosim.cg.UploadJsonSports generateFakeSportBean(Context context, String studentId, String studentName, long beginTimestampMs) {
        return generateFakeSportBean(context, studentId, studentName, beginTimestampMs, 5.0, FIXED_ROUTE_ID);
    }

    public static com.hpophh.chuanggaosim.cg.UploadJsonSports generateFakeSportBean(Context context, String studentId, String studentName,
                                                                             long beginTimestampMs, double km, String routeId) {
        bind(context);
        com.hpophh.chuanggaosim.cg.SportBean sportBean = new com.hpophh.chuanggaosim.cg.SportBean();
        com.hpophh.chuanggaosim.cg.UploadJsonSports uploadJsonSports = new com.hpophh.chuanggaosim.cg.UploadJsonSports();
        try {
            String safeRouteId = routeId == null || routeId.trim().isEmpty() ? FIXED_ROUTE_ID : routeId.trim();
            double safeKm = km > 0 ? km : 5.0;
            RouteDef route = resolveRoute(safeRouteId);

            String sportId = UUID.randomUUID().toString();

            // Custom km with a small natural jitter so two runs never look identical.
            double targetMeters = safeKm * 1000.0 + (Math.random() - 0.5) * 80.0;
            double plannedMinutes = 25.0 + Math.random() * 15.0;
            long durationMillis = Math.max(60_000L, (long) (plannedMinutes * 60_000));
            double durationMinutes = durationMillis / 60_000.0;

            // beginTime 锁定为传入的 beginTimestampMs，endTime = beginTime + durationMillis
            long endTimestamp = beginTimestampMs + durationMillis;

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            Calendar calBegin = Calendar.getInstance();
            calBegin.setTimeInMillis(beginTimestampMs);
            String beginTime = dateFormat.format(calBegin.getTime());

            Calendar calEnd = Calendar.getInstance();
            calEnd.setTimeInMillis(endTimestamp);
            String endTime = dateFormat.format(calEnd.getTime());

            int isValid = 1;
            String isValidReason = "";

            int stepCount = estimateStepCount(targetMeters);

            List<SportLatLngBean> routeData = generateFakeRouteData(beginTimestampMs, durationMinutes, targetMeters, safeRouteId);
            if (routeData.isEmpty()) {
                routeData = createFallbackRouteData(beginTimestampMs, durationMinutes, targetMeters / 1000.0);
            }
            routeData = normalizeRouteData(routeData, beginTimestampMs, durationMillis, targetMeters, stepCount);

            double odometer = targetMeters / 1000.0;
            double speed = odometer / durationMinutes * 60.0;
            String avgSpeed = format2(speed);
            String avgPace = formatPaceFromSpeed(speed);
            int paceKmCount = Math.max(0, (int) Math.floor(odometer));
            double remainKm = Math.max(0.0, odometer - paceKmCount);
            long lastOdometerMillis = Math.round((remainKm / Math.max(speed, 0.0001)) * 3_600_000.0);
            String lastOdometerTime = formatDurationHms(lastOdometerMillis);
            String activeTime = formatDurationHms(durationMillis);
            double calorie = Math.round((320.0 / 30.0) * durationMinutes * 10.0) / 10.0;
            double stepMinute = stepCount / durationMinutes;

            int minuteCount = Math.max(1, (int) Math.round(durationMinutes));
            List<Double> minuteSpeedValues = buildMinuteSpeedSeries(speed, minuteCount);
            double maxSpeedVal = speed;
            double minSpeedVal = speed;
            for (int i = 0; i < minuteSpeedValues.size(); i++) {
                double v = minuteSpeedValues.get(i);
                maxSpeedVal = Math.max(maxSpeedVal, v);
                minSpeedVal = Math.min(minSpeedVal, v);

                com.hpophh.chuanggaosim.cg.CminuteSpeedstr cminuteSpeedstr = new com.hpophh.chuanggaosim.cg.CminuteSpeedstr();
                cminuteSpeedstr.min = String.valueOf(i + 1);
                cminuteSpeedstr.v = format2(v);
                sportBean.addCminuteSpeedstr(cminuteSpeedstr);
            }
            String maxSpeed = format2(maxSpeedVal);
            String minSpeed = format2(minSpeedVal);

            for (int i = 1; i <= paceKmCount; i++) {
                com.hpophh.chuanggaosim.cg.Cpacestr cpacestr = new com.hpophh.chuanggaosim.cg.Cpacestr();
                cpacestr.km = String.valueOf(i);
                double kmSpeed = speed * (0.97 + Math.random() * 0.06);
                cpacestr.t = formatPaceFromSpeed(kmSpeed);
                sportBean.addPaceStr(cpacestr);
            }

            for (SportLatLngBean point : routeData) {
                sportBean.addSportLatLngBean(point);
            }

            sportBean.sportId = sportId;
            sportBean.startDate = beginTime;
            sportBean.endDate = endTime;
            sportBean.startDateLong = beginTimestampMs;
            sportBean.endDateLong = endTimestamp;
            sportBean.distance = odometer;
            sportBean.validOdometer = odometer;
            sportBean.totalStep = stepCount;
            sportBean.stepMinute = format2(stepMinute);
            sportBean.avgSpeed = avgSpeed;
            sportBean.maxSpeedPerHour = maxSpeed;
            sportBean.minSpeedPerHour = minSpeed;
            sportBean.lastOdometerTime = lastOdometerTime;
            sportBean.avgPace = avgPace;
            sportBean.cal = calorie;
            sportBean.dlTime = activeTime;
            sportBean.dllc = format2(odometer);
            sportBean.activityStatus = com.hpophh.chuanggaosim.cg.SportBean.ACTIVITY_STATUS_FINISH;
            sportBean.validCount = isValid;
            sportBean.isValidReason = isValidReason;

            uploadJsonSports.activeTime = activeTime;
            uploadJsonSports.alreadyPassPoint = "1;2;3;4;5";
            uploadJsonSports.alreadyPassPointResult = "";
            uploadJsonSports.avgPace = avgPace;
            uploadJsonSports.avgSpeed = avgSpeed;
            uploadJsonSports.beganPoint = routeData.get(0).a + "|" + routeData.get(0).o;
            uploadJsonSports.beginTime = beginTime;
            uploadJsonSports.calorie = calorie;
            uploadJsonSports.coordinate = routeData;
            uploadJsonSports.endPoint = routeData.get(routeData.size() - 1).a + "|" + routeData.get(routeData.size() - 1).o;
            uploadJsonSports.endTime = endTime;
            uploadJsonSports.indoor = 0;
            uploadJsonSports.isValid = 1;
            uploadJsonSports.isValidReason = "";
            uploadJsonSports.lastOdometerTime = lastOdometerTime;
            uploadJsonSports.maxSpeedPerHour = maxSpeed;
            uploadJsonSports.minSpeedPerHour = minSpeed;
            uploadJsonSports.minuteSpeed = sportBean.minuteSpeedStr;
            uploadJsonSports.modementMode = "1";
            uploadJsonSports.name = studentName == null ? "" : studentName.trim();
            uploadJsonSports.needPassPointCount = "5";
            uploadJsonSports.odometer = format2(odometer);
            uploadJsonSports.pace = sportBean.paceStr;
            uploadJsonSports.phoneVersion = "Android,31,13|2.9.5";
            uploadJsonSports.planRouteName = route != null ? route.name : "自定义路线 " + safeRouteId;
            uploadJsonSports.routeId = safeRouteId;
            uploadJsonSports.routePolylineBh = FIXED_ROUTE_POLYLINE_BH;
            uploadJsonSports.sportId = sportId;
            // Declare "has photo" at submit time; the file itself is uploaded via
            // uploadpic right after savesports. Server review marks reason 6
            // ("未上传有效照片") when the flag is missing at review time.
            uploadJsonSports.sportImages = "1";
            uploadJsonSports.stepCount = stepCount;
            uploadJsonSports.stepMinute = format2(stepMinute);
            uploadJsonSports.xh = studentId == null ? "" : studentId.trim();
        } catch (Exception e) {
            Log.e(TAG, "generateFakeSportBean failed", e);
        }
        return uploadJsonSports;
    }

    private static String padZero(double num)  {
        int intValue = (int) num; // 将double转换为int，截断小数部分
        if (intValue < 10) {
            return "0" + intValue;
        }
        return String.valueOf(intValue);
    }

    private static List<SportLatLngBean> generateFakeRouteData(long beginTimestamp,
                                                                 double minutes,
                                                                 double targetMeters,
                                                                 String routeId) {
        List<SportLatLngBean> routeData = new ArrayList<>();
        if (targetMeters <= 0.0) {
            return routeData;
        }
        RouteDef route = resolveRoute(routeId);
        double[] geom = fitTrackIfNeeded(route);
        if (sessionStartOffset < 0.0) {
            // Start anywhere on the track, like a runner joining mid-session.
            sessionStartOffset = Math.random() * LAP_METERS;
        }

        int pointCount = Math.max(2, (int) Math.round(minutes * 30.0) + 1);
        double[][] ring = null;
        double[] cum = null;
        int cachedLap = -1;

        for (int i = 0; i < pointCount; i++) {
            double travelled = targetMeters * i / (pointCount - 1.0);
            double totalS = sessionStartOffset + travelled;
            int lap = (int) Math.floor(totalS / LAP_METERS);
            if (lap != cachedLap) {
                ring = buildTrackRing(geom);
                cum = cumulativeLengths(ring);
                cachedLap = lap;
            }
            double distInLap = totalS - lap * LAP_METERS;
            double perim = cum[cum.length - 1];
            if (distInLap > perim) {
                distInLap = perim;
            }
            double[] pos = interpolateOnRing(ring, cum, distInLap);

            SportLatLngBean bean = new SportLatLngBean();
            bean.a = pos[0];
            bean.o = pos[1];
            bean.v = 1;
            bean.t = beginTimestamp + Math.round(minutes * 60_000.0 * i / (pointCount - 1.0));
            routeData.add(bean);
        }

        return routeData;
    }

    // Standard 400 m track: two straights + two semicircles (2L + 2πR = 400).
    private static final double LAP_METERS = 400.0;
    private static final double TRACK_RADIUS_M = 36.5;
    private static final double TRACK_STRAIGHT_M = 85.35;
    private static double sessionStartOffset = -1.0;

    // Fit the track heading from the official polyline via PCA. Center comes from
    // the user-measured ground truth when available (polylines are biased).
    private static double[] fitTrackIfNeeded(RouteDef route) {
        if (route.geom != null) {
            return route.geom;
        }
        double cLat = 0.0;
        double cLng = 0.0;
        for (double[] p : route.polyline) {
            cLat += p[1];
            cLng += p[0];
        }
        cLat /= route.polyline.length;
        cLng /= route.polyline.length;
        if (route.centerLat != null && route.centerLng != null) {
            // Measured center is authoritative — no nudge math at all.
            cLat = route.centerLat;
            cLng = route.centerLng;
        }

        double mPerDegLng = 111_320.0 * Math.cos(Math.toRadians(cLat));
        double cxx = 0.0;
        double cyy = 0.0;
        double cxy = 0.0;
        for (double[] p : route.polyline) {
            double dx = (p[0] - cLng) * mPerDegLng;
            double dy = (p[1] - cLat) * 111_320.0;
            cxx += dx * dx;
            cyy += dy * dy;
            cxy += dx * dy;
        }
        double thetaEast = 0.5 * Math.atan2(2.0 * cxy, cxx - cyy);
        double headingDeg = 90.0 - Math.toDegrees(thetaEast); // compass heading of long axis
        // Normalize to the long-axis equivalent (a track's heading is axis-symmetric).
        while (headingDeg > 90.0) {
            headingDeg -= 180.0;
        }
        while (headingDeg <= -90.0) {
            headingDeg += 180.0;
        }
        // Route 15: its 4-point polyline is a bounding rectangle — corner-weighted PCA
        // skews the axis. User ground truth: rotate the PCA result 15° CCW
        // (compass heading decreases by 15).
        if ("15".equals(route.id)) {
            headingDeg -= 15.0;
        }
        // Route 14: the ground track is exactly horizontal (east-west) - lock it.
        if ("14".equals(route.id)) {
            headingDeg = 90.0;
        }

        route.geom = new double[]{cLat, cLng, headingDeg, mPerDegLng};
        return route.geom;
    }

    // One lap: base 400 m track in local meter coords (u = long axis, v = short axis),
    // true-normal wobble (lane offset + strong low-frequency sway), perimeter renorm
    // to exactly 400 m, then rotate by heading and convert to lat/lng + GPS noise.
    private static double[][] buildTrackRing(double[] geom) {
        double centerLat = geom[0];
        double centerLng = geom[1];
        double heading = Math.toRadians(geom[2]);
        double mPerDegLng = geom[3];

        double r = TRACK_RADIUS_M;
        double straight = TRACK_STRAIGHT_M;
        double seg1 = straight;
        double seg2 = straight + Math.PI * r;
        double seg3 = 2.0 * straight + Math.PI * r;

        int n = 240;
        double[][] uv = new double[n][2];
        for (int i = 0; i < n; i++) {
            double s = LAP_METERS * i / n;
            double u;
            double v;
            if (s < seg1) {                 // straight 1
                u = -straight / 2.0 + s;
                v = r;
            } else if (s < seg2) {          // semicircle 1
                double phi = Math.PI / 2.0 - (s - seg1) / r;
                u = straight / 2.0 + r * Math.cos(phi);
                v = r * Math.sin(phi);
            } else if (s < seg3) {          // straight 2
                u = straight / 2.0 - (s - seg2);
                v = -r;
            } else {                        // semicircle 2
                double phi = -Math.PI / 2.0 - (s - seg3) / r;
                u = -straight / 2.0 + r * Math.cos(phi);
                v = r * Math.sin(phi);
            }
            uv[i][0] = u;
            uv[i][1] = v;
        }

        // Per-lap personality: lane offset (in/out), center drift, strong sway.
        double laneOff = (Math.random() - 0.5) * 5.6;      // ±2.8 m in/out per lap
        double driftU = (Math.random() - 0.5) * 3.0;
        double driftV = (Math.random() - 0.5) * 3.0;
        double amp1 = 1.2 + Math.random() * 1.0;           // 1.2–2.2 m sway
        double amp2 = 0.5 + Math.random() * 0.6;           // 0.5–1.1 m ripple
        double freq1 = 2.0 + Math.random() * 1.5;
        double freq2 = 5.0 + Math.random() * 3.0;
        double phase1 = Math.random() * 2.0 * Math.PI;
        double phase2 = Math.random() * 2.0 * Math.PI;

        // True-normal displacement: normals from neighboring-point tangents, so
        // straights sway sideways and curves breathe radially (never path-lengthening).
        double[][] off = new double[n][2];
        for (int i = 0; i < n; i++) {
            double[] prev = uv[(i - 1 + n) % n];
            double[] next = uv[(i + 1) % n];
            double tu = next[0] - prev[0];
            double tv = next[1] - prev[1];
            double len = Math.sqrt(tu * tu + tv * tv);
            if (len < 1e-9) {
                off[i][0] = uv[i][0];
                off[i][1] = uv[i][1];
                continue;
            }
            double nu = -tv / len;
            double nv = tu / len;
            double s = LAP_METERS * i / n;
            double w = amp1 * Math.sin(2.0 * Math.PI * freq1 * s / LAP_METERS + phase1)
                    + amp2 * Math.sin(2.0 * Math.PI * freq2 * s / LAP_METERS + phase2);
            double d = laneOff + w;
            off[i][0] = uv[i][0] + nu * d;
            off[i][1] = uv[i][1] + nv * d;
        }

        // Renormalize perimeter to exactly 400 m around the local origin.
        double perim = 0.0;
        for (int i = 0; i < n; i++) {
            double du = off[(i + 1) % n][0] - off[i][0];
            double dv = off[(i + 1) % n][1] - off[i][1];
            perim += Math.sqrt(du * du + dv * dv);
        }
        double scale = LAP_METERS / perim;

        // Rotate by heading, translate to track center, convert to lat/lng, add noise.
        double sinH = Math.sin(heading);
        double cosH = Math.cos(heading);
        double[][] ring = new double[n][2];
        for (int i = 0; i < n; i++) {
            double u = off[i][0] * scale + driftU;
            double v = off[i][1] * scale + driftV;
            double east = u * sinH + v * cosH;
            double north = u * cosH - v * sinH;
            double lat = centerLat + north / 111_320.0 + (Math.random() - 0.5) * 0.8 / 111_320.0;
            double lng = centerLng + east / mPerDegLng + (Math.random() - 0.5) * 0.8 / mPerDegLng;
            ring[i][0] = lng;
            ring[i][1] = lat;
        }
        return ring;
    }

    private static double[] cumulativeLengths(double[][] ring) {
        int n = ring.length;
        double[] cum = new double[n + 1];
        for (int i = 0; i < n; i++) {
            double[] a = ring[i];
            double[] b = ring[(i + 1) % n];
            cum[i + 1] = cum[i] + haversineMeters(a[1], a[0], b[1], b[0]);
        }
        return cum;
    }

    private static double[] interpolateOnRing(double[][] ring, double[] cum, double dist) {
        int n = ring.length;
        int i = 0;
        while (i < n - 1 && cum[i + 1] < dist) {
            i++;
        }
        double segLen = cum[i + 1] - cum[i];
        double ratio = segLen <= 0.0 ? 0.0 : (dist - cum[i]) / segLen;
        int next = (i + 1) % n;
        double lat = ring[i][1] + (ring[next][1] - ring[i][1]) * ratio;
        double lng = ring[i][0] + (ring[next][0] - ring[i][0]) * ratio;
        return new double[]{lat, lng};
    }

    private static double haversineMeters(double latitude1, double longitude1,
                                          double latitude2, double longitude2) {
        double latitudeDelta = Math.toRadians(latitude2 - latitude1);
        double longitudeDelta = Math.toRadians(longitude2 - longitude1);
        double a = Math.sin(latitudeDelta / 2.0) * Math.sin(latitudeDelta / 2.0)
                + Math.cos(Math.toRadians(latitude1)) * Math.cos(Math.toRadians(latitude2))
                * Math.sin(longitudeDelta / 2.0) * Math.sin(longitudeDelta / 2.0);
        return 6_371_000.0 * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
    }

    private static List<SportLatLngBean> createFallbackRouteData(long beginTimestamp, double minutes, double targetKm) {
        List<SportLatLngBean> routeData = new ArrayList<>();
        long durationMillis = Math.max(2000L, (long) (minutes * 60_000));
        double totalMeters = Math.max(targetKm * 1000.0, 1.0);
        // Fallback shares the fixed route's start point so off-campus checks stay plausible.
        double baseLat = 38.991185;
        double baseLng = 117.349327;
        double deltaLng = totalMeters / 111_320.0;

        SportLatLngBean start = new SportLatLngBean();
        start.a = baseLat;
        start.o = baseLng;
        start.ac = 0.0;
        start.d = 0.0;
        start.da = 0.0;
        start.s = 0.0;
        start.st = 0;
        start.sta = 0;
        start.t = beginTimestamp;
        start.v = 1;
        routeData.add(start);

        SportLatLngBean end = new SportLatLngBean();
        end.a = baseLat;
        end.o = baseLng + deltaLng;
        end.ac = 0.0;
        end.d = totalMeters;
        end.da = totalMeters;
        end.s = totalMeters / Math.max(1.0, durationMillis / 1000.0);
        end.st = 120;
        end.sta = Math.max(1, (int) Math.round(totalMeters / 0.64));
        end.t = beginTimestamp + durationMillis;
        end.v = 1;
        routeData.add(end);

        return routeData;
    }

    private static String format2(double value) {
        return String.valueOf(Math.round(value * 100.0) / 100.0);
    }

    private static int estimateStepCount(double targetMeters) {
        double strideMeters = 0.62 + Math.random() * 0.10;
        return Math.max(1000, (int) Math.round(targetMeters / strideMeters));
    }

    private static String formatDurationHms(long durationMillis) {
        long totalSeconds = Math.max(0L, durationMillis / 1000L);
        long h = totalSeconds / 3600L;
        long m = (totalSeconds % 3600L) / 60L;
        long s = totalSeconds % 60L;
        return padZero(h) + ":" + padZero(m) + ":" + padZero(s);
    }

    private static List<Double> buildMinuteSpeedSeries(double avgSpeed, int minuteCount) {
        List<Double> values = new ArrayList<>();
        if (minuteCount <= 0) {
            values.add(Math.max(0.1, avgSpeed));
            return values;
        }

        double rawSum = 0.0;
        for (int i = 0; i < minuteCount; i++) {
            double factor = 0.92 + Math.random() * 0.16;
            double v = Math.max(0.1, avgSpeed * factor);
            values.add(v);
            rawSum += v;
        }

        double mean = rawSum / minuteCount;
        double scale = mean > 0 ? avgSpeed / mean : 1.0;
        for (int i = 0; i < values.size(); i++) {
            values.set(i, Math.max(0.1, values.get(i) * scale));
        }
        return values;
    }

    private static List<SportLatLngBean> normalizeRouteData(List<SportLatLngBean> routeData,
                                                             long beginTimestamp,
                                                             long durationMillis,
                                                             double targetMeters,
                                                             int totalStep) {
        if (routeData == null || routeData.isEmpty()) {
            return routeData;
        }

        if (routeData.size() == 1) {
            SportLatLngBean clone = new SportLatLngBean();
            clone.a = routeData.get(0).a;
            clone.o = routeData.get(0).o;
            clone.v = 1;
            clone.t = beginTimestamp + durationMillis;
            clone.d = targetMeters;
            clone.da = targetMeters;
            clone.sta = totalStep;
            routeData.add(clone);
        }

        long prevTs = beginTimestamp;
        double prevDa = 0.0;
        int prevSta = 0;
        int count = routeData.size();

        for (int i = 0; i < count; i++) {
            SportLatLngBean point = routeData.get(i);
            if (point == null) {
                continue;
            }

            double progress = (double) i / (double) (count - 1);
            long currentTs = beginTimestamp + Math.round(durationMillis * progress);
            point.t = currentTs;
            point.ac = 0.0;
            point.v = point.v <= 0 ? 1 : point.v;

            if (i == 0) {
                point.d = 0.0;
                point.da = 0.0;
                point.s = 0.0;
                point.st = 0;
                point.sta = 0;
                prevTs = currentTs;
                continue;
            }

            double da = targetMeters * progress;
            double d = Math.max(0.0, da - prevDa);
            long dtMs = Math.max(1L, currentTs - prevTs);
            double dtSec = dtMs / 1000.0;
            int sta = (int) Math.round(totalStep * progress);
            int stepDelta = Math.max(0, sta - prevSta);
            int cadence = (int) Math.round(stepDelta * 60.0 / dtSec);

            point.da = da;
            point.d = d;
            point.s = d / dtSec;
            point.st = Math.max(0, cadence);
            point.sta = Math.min(totalStep, Math.max(prevSta, sta));

            prevDa = da;
            prevTs = currentTs;
            prevSta = point.sta;
        }

        SportLatLngBean last = routeData.get(routeData.size() - 1);
        last.t = beginTimestamp + durationMillis;
        last.da = targetMeters;
        last.sta = totalStep;
        return routeData;
    }

    private static String formatPaceFromSpeed(double speedKmPerHour) {
        if (Double.isNaN(speedKmPerHour) || Double.isInfinite(speedKmPerHour) || speedKmPerHour <= 0) {
            return "99'59''";
        }

        double paceMinutes = 60.0 / speedKmPerHour;
        int min = (int) paceMinutes;
        int sec = (int) Math.round((paceMinutes - min) * 60.0);

        if (sec >= 60) {
            min += 1;
            sec = 0;
        }

        if (min > 99) {
            min = 99;
            sec = 59;
        }

        return padZero(min) + "'" + padZero(sec) + "''";
    }
}
