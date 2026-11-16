package cn.pencillink;

final class ActionPolicy {
    static final long SWIPE_MS = 120;
    static long interval(int target) { return target == 1 ? 220 : 400; }
    // 1: 认识; 2: 下一个; 0: 无匹配; -1: 同名控件有歧义。
    static int reviewAction(int knownCount, int nextCount) {
        if (knownCount > 1) return -1;
        if (knownCount == 1) return 1;
        if (nextCount > 1) return -1;
        return nextCount == 1 ? 2 : 0;
    }
}
