package cn.pencillink;

public class PressResolverTest {
    static int count;
    static void eq(int want,int got){count++;if(want!=got)throw new AssertionError("check "+count+": expected "+want+", got "+got);}
    public static void main(String[] args){
        PressResolver p=new PressResolver();
        // The first swipe cannot fire while the second short press is still held.
        eq(PressResolver.NONE,p.down(0));eq(PressResolver.WAIT,p.up(100,true));
        eq(PressResolver.WAIT,p.down(400));eq(PressResolver.NONE,p.timeout(550));
        eq(PressResolver.DOUBLE,p.up(600,true));eq(PressResolver.NONE,p.timeout(1000));
        // Ordinary single press is resolved exactly once.
        eq(PressResolver.WAIT,p.up(1100,true));eq(PressResolver.NONE,p.timeout(1549));
        eq(PressResolver.SINGLE,p.timeout(1550));eq(PressResolver.NONE,p.timeout(1600));
        // Long/cancelled second press suppresses the pending swipe and the like.
        eq(PressResolver.WAIT,p.up(2000,true));eq(PressResolver.WAIT,p.down(2400));
        eq(PressResolver.NONE,p.up(3000,false));eq(PressResolver.NONE,p.timeout(4000));
        // If the timer was delayed, a late second press flushes only the old single.
        eq(PressResolver.WAIT,p.up(5000,true));eq(PressResolver.SINGLE,p.down(5451));
        eq(PressResolver.WAIT,p.up(5500,true));eq(PressResolver.SINGLE,p.timeout(5950));
        // Leaving the app cancels the pending action.
        eq(PressResolver.WAIT,p.up(6000,true));p.reset();eq(PressResolver.NONE,p.timeout(7000));
        // Window customization and inclusive DOWN boundary.
        p.window=800;eq(PressResolver.WAIT,p.up(8000,true));
        eq(PressResolver.WAIT,p.down(8800));eq(PressResolver.DOUBLE,p.up(8900,true));
        // Review action priority and ambiguity protection.
        eq(1,ActionPolicy.reviewAction(1,0));eq(2,ActionPolicy.reviewAction(0,1));
        eq(1,ActionPolicy.reviewAction(1,1));eq(0,ActionPolicy.reviewAction(0,0));
        eq(-1,ActionPolicy.reviewAction(2,1));eq(-1,ActionPolicy.reviewAction(0,2));
        System.out.println("PASS: "+count+" checks (press sequences and review actions)");
    }
}
