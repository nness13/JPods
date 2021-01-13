package com.jinn.jpods;

import com.jinn.jpods.Services.PodsService;

public class Config {
    public static final String  APP_PREFERENCES = "JPods",
                                APP_PREFERENCES_DEVICES = "APP_PREFERENCES_DEVICES";
    public static boolean   statusVersion = false;


    // State JPods
    public static String    name = "AirPods";
    public static String    model = PodsService.MODEL_AIRPODS_NORMAL;
    public static boolean   maybeConnected = false,
                            deviceConnect = false,
                            deviceFound = false,
                            audioPlaying = false,
                            serviceDisable = false,
                            chargeL = false, chargeR = false;
    public static int       leftStatus = 0, rightStatus = 0, caseStatus = 0,
                            leftStatusF = leftStatus, rightStatusF = rightStatus, caseStatusF = caseStatus,
                            lastK = 0;

    public static boolean   popUpIsOpen = false,
                            popUpAttach = false;
    public static long      lastBLEdataAds = System.currentTimeMillis() + 999999999,
                            periodBLENo = 10*1000;

    public static boolean   viewAdsNotify = false;
    public static long      lastViewAds = System.currentTimeMillis(),
                            periodViewAds = 30*60 * 1000;




}


