package com.jinn.jpods.util;

import java.io.IOException;

public class Util {
    public static boolean isOnline() {
        Runtime runtime = Runtime.getRuntime();
        try {
            Process ipProcess = runtime.exec("/system/bin/ping -c 1 8.8.8.8");
            int     exitValue = ipProcess.waitFor();
            return (exitValue == 0);
        }
        catch (IOException e)          { e.printStackTrace(); }
        catch (InterruptedException e) { e.printStackTrace(); }

        return false;
    }

    public static int minMax(int l) {
        if(l > 100) return 100;
        if(l < 0) return 0;
        return l;
    }
}
