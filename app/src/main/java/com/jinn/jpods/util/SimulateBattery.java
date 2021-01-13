package com.jinn.jpods.util;

import com.jinn.jpods.Config;
import com.jinn.jpods.Services.PodsService;

public class SimulateBattery {
    public SimulateBattery(){

    }

    // Get Data SharedPreference
    public static long lastSeenConnected = 0, lastDisConnected = 0;
    public static boolean correctionWhenConnecting = true;
    public static int k;
    public static int lastK;
    public static double
            correction = 0,
            differenceLowBattery,
            nowK,
            disconectTime,
            timeCharged = (25*60/100),                                                   // Зарядиться один % за timeCharged сек
            timeDisCharged = (5*60)*60/100,                                              // Розрядиться один % за timeDisCharged сек
            passiveTimeDisCharged = (20*60)*60/100;

    public static int getStatusFull(int timeReCall) {
        if(!Config.audioPlaying) timeDisCharged = passiveTimeDisCharged;
        else timeDisCharged = timeDisCharged;

        nowK = nowK+(timeReCall/1000)/timeDisCharged;                                                                // Відсоток на який розрядився

        lastSeenConnected = HashDevice.list.get(PodsService.device.getAddress()).getLastSeenConnected();
        lastDisConnected = HashDevice.list.get(PodsService.device.getAddress()).getLastDisConnected();
        lastK = HashDevice.list.get(PodsService.device.getAddress()).getLastK();
        if(correctionWhenConnecting){
            disconectTime = (int) (lastSeenConnected - lastDisConnected)/1000;                                   // Час в неактивності в секундах
            differenceLowBattery = (int) (disconectTime/timeCharged);                                            // Зарядився на таку кількість відсотків

            double x = lastK - differenceLowBattery;                                                             // Відсоток розряду після підзарядки
            if(x > 0 && x < 100) correction = x;                                                                                  // Теперішній рівень розряду
            if(x < 0) correction = 0;                                                                                  // Теперішній рівень розряду
            if(x > 100) correction = 100;                                                                                  // Теперішній рівень розряду
            correctionWhenConnecting = false;
        }

        k = (int) (nowK + correction);
        k = Util.minMax(k);
        return k;
    }

    public static int getStatus(int timeReCall) {
        if(!Config.audioPlaying) timeDisCharged = passiveTimeDisCharged;
        else timeDisCharged = timeDisCharged;

        nowK = nowK+(timeReCall/1000)/timeDisCharged;                                                                // Відсоток на який розрядився

        k = Util.minMax((int) nowK);
        return k;
    }

}
