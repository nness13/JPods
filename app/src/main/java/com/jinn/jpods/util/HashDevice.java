package com.jinn.jpods.util;

import java.util.HashMap;

public class HashDevice {
    public static HashMap<String, Device> list = new HashMap<>();

    public static class Device {
        private String  name = "AirPods";

        private long lastSeenConnected = 0;
        private long lastDisConnected = 0;
        private long lastSeenConnectedLeftPod = 0;
        private long lastDisConnectedLeftPod = 0;
        private long lastSeenConnectedRightPod = 0;
        private long lastDisConnectedRightPod = 0;
        private int k = 0;
        private int lastK = 0;

        public void setName(String name){ this.name = name; }
        public String getName() { return name; }

        public long getLastSeenConnected() { return lastSeenConnected; }
        public void setLastSeenConnected(long lastSeenConnected) { this.lastSeenConnected = lastSeenConnected; }

        public long getLastDisConnected() { return lastDisConnected; }
        public void setLastDisConnected(long lastDisConnected) { this.lastDisConnected = lastDisConnected; }

        public long getLastSeenConnectedLeftPod() { return lastSeenConnectedLeftPod; }
        public void setLastSeenConnectedLeftPod(long lastSeenConnectedLeftPod) { this.lastSeenConnectedLeftPod = lastSeenConnectedLeftPod; }

        public long getLastDisConnectedLeftPod() { return lastDisConnectedLeftPod; }
        public void setLastDisConnectedLeftPod(long lastDisConnectedLeftPod) { this.lastDisConnectedLeftPod = lastDisConnectedLeftPod; }

        public long getLastSeenConnectedRightPod() { return lastSeenConnectedRightPod; }
        public void setLastSeenConnectedRightPod(long lastSeenConnectedRightPod) { this.lastSeenConnectedRightPod = lastSeenConnectedRightPod; }

        public long getLastDisConnectedRightPod() { return lastDisConnectedRightPod; }
        public void setLastDisConnectedRightPod(long lastDisConnectedRightPod) { this.lastDisConnectedRightPod = lastDisConnectedRightPod; }

        public int getK() { return k; }
        public void setK(int k) { this.k = k; }

        public int getLastK() { return lastK; }
        public void setLastK(int lastK) { this.lastK = lastK; }
    }
}


