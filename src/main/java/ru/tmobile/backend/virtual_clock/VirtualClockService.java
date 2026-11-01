package ru.tmobile.backend.virtual_clock;

import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;


@Service
public class VirtualClockService extends Clock {
    private long realStartTime = System.currentTimeMillis();
    private long virtualStartTime = System.currentTimeMillis();
    private int multiplier = 1;
    private boolean isPaused = false;

    private long pauseRealTime = 0;
    private long pauseVirtualTime = 0;

    public synchronized void syncTime(long newVirtualStart, long newRealStart, int newMultiplier, boolean newIsPaused){
        this.virtualStartTime = newVirtualStart;
        this.realStartTime = newRealStart;
        this.multiplier = newMultiplier;
        this.isPaused = newIsPaused;

        if (isPaused){
            this.pauseRealTime = System.currentTimeMillis();
            this.pauseVirtualTime = getCurrentVirtualTime();
        }
    }

    private long getCurrentVirtualTime(){
        if (isPaused) return pauseVirtualTime;
        long realElapsed = System.currentTimeMillis() - realStartTime;
        return (long) (virtualStartTime + (realElapsed * multiplier)); 
    }

    @Override
    public ZoneId getZone(){
        return ZoneId.systemDefault();
    }

    @Override
    public Clock withZone(ZoneId zone){
        throw new UnsupportedOperationException("VirtualClock does not support withZone");
    }

    @Override
    public Instant instant(){
        return Instant.ofEpochMilli(getCurrentVirtualTime());
    }

    public int getMultiplier() { return multiplier; }
    public boolean isPaused() { return isPaused; }
}
