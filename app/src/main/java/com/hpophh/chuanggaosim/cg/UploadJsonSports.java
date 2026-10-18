package com.hpophh.chuanggaosim.cg;

import java.util.List;

// Field names/types mirror the official net.crigh.cgsport.model.UploadJsonSports:
// Gson on the server deserializes strictly, so randomPointStr must be a List
// (or absent) — a string like "[]" crashes it with a 500.
public class UploadJsonSports {
    public String activeTime;
    public String alreadyPassPoint;
    public String alreadyPassPointResult;
    public String avgPace;
    public String avgSpeed;
    public String beganPoint;
    public String beginTime;
    public double calorie;
    public List<SportLatLngBean> coordinate;
    public String endPoint;
    public String endTime;
    public int indoor;
    public int isValid;
    public String isValidReason;
    public String lastOdometerTime;
    public String maxSpeedPerHour;
    public String minSpeedPerHour;
    public List<CminuteSpeedstr> minuteSpeed;
    public String modementMode;
    public String name;
    public String needPassPointCount;
    public String odometer;
    public List<Cpacestr> pace;
    public String phoneVersion;
    public String planRouteName;
    public List<RandomPointStr> randomPointStr;
    public String routeId;
    public String routePolylineBh;
    public String sportId;
    public String sportImages;
    public int stepCount;
    public String stepMinute;
    public String xh;
}

