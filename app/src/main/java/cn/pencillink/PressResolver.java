package cn.pencillink;

/** Times use Android uptime. A second DOWN reserves the pair before the single timer fires. */
final class PressResolver {
    static final int NONE=0, WAIT=1, SINGLE=2, DOUBLE=3;
    private boolean pending, secondHeld;
    private long deadline;
    long window=450;
    boolean active() { return pending; }
    boolean waitingForRelease() { return secondHeld; }
    void reset() { pending=false; secondHeld=false; }
    long delay(long now) { return Math.max(0,deadline-now); }
    int down(long now) {
        if(!pending) return NONE;
        if(now<=deadline) { secondHeld=true; return WAIT; }
        reset(); return SINGLE;
    }
    int up(long now, boolean validShort) {
        if(secondHeld) { reset(); return validShort?DOUBLE:NONE; }
        if(!validShort) { reset(); return NONE; }
        pending=true; deadline=now+window; return WAIT;
    }
    int timeout(long now) {
        if(pending && !secondHeld && now>=deadline) { reset(); return SINGLE; }
        return NONE;
    }
}
