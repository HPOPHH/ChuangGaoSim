package com.hpophh.chuanggaosim.cg;

import java.util.ArrayList;
import java.util.List;

public class SportBean {
    public static final int ACTIVITY_STATUS_FINISH = 1;
    public static final int ACTIVITY_STATUS_UN_FINISH = 0;
    public static final int MAX_PHOTO_COUNT = 5;
    public static final int STATUS_WTB = 0;
    public static final int STATUS_YTB = 1;
    public int activityStatus;
    public String alreadyPassPoint;
    public String avgPace;
    public String avgSpeed;
    public double cal;
    public String checkResult;
    public String checkStatus;
    public String considerStatus;
    public double distance;
    public String dlTime;
    public String dllc;
    public String endDate;
    public long endDateLong;
    public int hasPhoto;
    public int id;
    public String images;
    public int indoor;
    public String isValidReason;
    public String lastOdometerTime;
    public String maxSpeedPerHour;
    public String minSpeedPerHour;
    public String name;
    public String resourceType;
    public int result;
    public String routeId;
    public String routeName;
    public String routePolylineBh;
    public String sportId;
    public String sportType;
    public String startDate;
    public long startDateLong;
    public int status;
    public String stepMinute;
    public String tableName;
    public String tip;
    public int totalStep;
    public String uid;
    public int validCount;
    public double validOdometer;
    public List<Cpacestr> paceStr = new ArrayList();
    public List<CminuteSpeedstr> minuteSpeedStr = new ArrayList();
    public List<SportLatLngBean> slllist = new ArrayList();
    public List<ImageItem> imageItems = new ArrayList();

    public void addCminuteSpeedstr(CminuteSpeedstr cminuteSpeedstr) {
        this.minuteSpeedStr.add(cminuteSpeedstr);
    }

    public void addPaceStr(Cpacestr cpacestr) {
        this.paceStr.add(cpacestr);
    }

    public void addSportLatLngBean(SportLatLngBean sportLatLngBean) {
        this.slllist.add(sportLatLngBean);
    }

}