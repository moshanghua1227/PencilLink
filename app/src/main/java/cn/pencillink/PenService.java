package cn.pencillink;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import java.util.ArrayList;

public class PenService extends AccessibilityService {
    static PenService live;
    static String status = "等待开启无障碍服务", lastKey = "尚未接收到按键";
    static long learnUntil;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private View panel, picker;
    private boolean held, busy;
    private int heldDevice, heldCode, heldTarget;
    private long downTime, lastAction;
    private Runnable pendingSwipe;
    private final PressResolver presses=new PressResolver();

    @Override protected void onServiceConnected() {
        live = this; wm = (WindowManager)getSystemService(WINDOW_SERVICE); status = "服务已连接";
        refreshPanel();
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if(presses.active() && target()!=1) cancelPendingSwipe();
    }
    private void cancelPendingSwipe() {
        if(pendingSwipe!=null) handler.removeCallbacks(pendingSwipe);
        pendingSwipe=null;
        presses.reset();
    }
    @Override public void onInterrupt() { held = false; cancelPendingSwipe(); }
    @Override public void onDestroy() { handler.removeCallbacksAndMessages(null); removePanel(); removePicker(); live = null; super.onDestroy(); }
    static boolean reserved(int k) {
        return k == KeyEvent.KEYCODE_POWER || k == KeyEvent.KEYCODE_HOME || k == KeyEvent.KEYCODE_BACK
            || k == KeyEvent.KEYCODE_APP_SWITCH || k == KeyEvent.KEYCODE_UNKNOWN;
    }
    void record(KeyEvent e) {
        lastKey = KeyEvent.keyCodeToString(e.getKeyCode()) + " / scan=" + e.getScanCode()
            + " / " + (e.getAction()==0 ? "按下" : "松开") + " / device=" + e.getDeviceId();
    }
    @Override protected boolean onKeyEvent(KeyEvent e) {
        record(e);
        if (SystemClock.elapsedRealtime() < learnUntil) {
            cancelPendingSwipe(); held=false;
            if (e.getAction()==KeyEvent.ACTION_DOWN && e.getRepeatCount()==0 && !reserved(e.getKeyCode())) {
                Config.get(this).edit().putInt("key",e.getKeyCode()).putInt("scan",e.getScanCode()).apply();
                learnUntil=0; status="已录入："+KeyEvent.keyCodeToString(e.getKeyCode());
            }
            return false;
        }
        int code = Config.get(this).getInt("key", -1);
        if (e.getKeyCode()!=code || e.getScanCode()!=Config.get(this).getInt("scan",0)) return false;
        if (e.getAction()==KeyEvent.ACTION_UP && held && e.getDeviceId()==heldDevice && e.getKeyCode()==heldCode) {
            held=false;
            boolean valid=e.getEventTime()-downTime<=450 && !e.isCanceled();
            if(!Config.get(this).getBoolean("enabled",false) || target()!=heldTarget) { cancelPendingSwipe(); return true; }
            if(heldTarget==1) resolve(presses.up(e.getEventTime(),valid));
            else if(valid) act();
            return true;
        }
        if (!Config.get(this).getBoolean("enabled",false) || target()==0) return false;
        if (e.getAction()==KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount()==0) {
                held=true; downTime=e.getEventTime(); heldDevice=e.getDeviceId(); heldCode=e.getKeyCode(); heldTarget=target();
                if(heldTarget==1) {
                    long window=Config.get(this).getInt("doubleWindow",450);
                    if(presses.window!=window) { cancelPendingSwipe(); presses.window=window; }
                    resolve(presses.down(e.getEventTime()));
                } else cancelPendingSwipe();
            }
            return true;
        }
        return false;
    }
    private void resolve(int decision) {
        if(decision==PressResolver.NONE) return;
        if(pendingSwipe!=null) handler.removeCallbacks(pendingSwipe);
        pendingSwipe=null;
        if(decision==PressResolver.DOUBLE) { status="已识别双击"; likeVideo(); return; }
        if(decision==PressResolver.SINGLE) { if(target()==1) act(); return; }
        if(presses.waitingForRelease()) { status="已捕获第二次按下，等待松开点赞"; return; }
        pendingSwipe=()->{
            pendingSwipe=null;
            if(target()==1 && Config.get(this).getBoolean("enabled",false)) {
                if(presses.timeout(SystemClock.uptimeMillis())==PressResolver.SINGLE) act();
            } else presses.reset();
        };
        // On second DOWN the timeout becomes inert until UP resolves the pair.
        handler.postDelayed(pendingSwipe,presses.delay(SystemClock.uptimeMillis()));
        status="等待确认单击 / 双击";
    }
    private void likeVideo() {
        if(!Config.get(this).getBoolean("enabled",false) || target()!=1 || busy
            || (lastAction!=0 && SystemClock.elapsedRealtime()-lastAction<ActionPolicy.interval(1))) return;
        Rect r=screen(); float x=r.width()*.5f, y=r.height()*.45f;
        Path a=new Path(); a.moveTo(x,y);
        Path b=new Path(); b.moveTo(x,y);
        GestureDescription taps=new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(a,0,35))
            .addStroke(new GestureDescription.StrokeDescription(b,100,35)).build();
        lastAction=SystemClock.elapsedRealtime();
        submitGesture(taps,"已发送视频双击点赞手势");
    }
    private int target() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root==null || root.getPackageName()==null) return 0;
        String p=root.getPackageName().toString();
        if (p.equals(Config.douyin(this))) return 1;
        if (p.equals(Config.words(this))) return 2;
        return 0;
    }
    private Rect screen() {
        if (android.os.Build.VERSION.SDK_INT>=30) return new Rect(wm.getCurrentWindowMetrics().getBounds());
        android.util.DisplayMetrics d=new android.util.DisplayMetrics(); wm.getDefaultDisplay().getRealMetrics(d);
        return new Rect(0,0,d.widthPixels,d.heightPixels);
    }
    private void gesture(float x, float y, float endY, long duration) {
        if (busy) return;
        Path p=new Path(); p.moveTo(x,y); if (y!=endY) p.lineTo(x,endY);
        submitGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,duration)).build(),"手势已完成");
    }
    private void submitGesture(GestureDescription description, String completed) {
        busy=true;
        boolean accepted=dispatchGesture(description,
            new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription d) { busy=false; status=completed; }
                @Override public void onCancelled(GestureDescription d) { busy=false; status="手势被系统取消"; }
            },null);
        if (!accepted) { busy=false; status="系统未接受手势"; }
    }
    void act() {
        if (!Config.get(this).getBoolean("enabled",false)) { status="请先开启联动总开关"; return; }
        int t=target();
        if (t==0) { status="当前界面不是配置的目标应用"; return; }
        if (busy || (lastAction!=0 && SystemClock.elapsedRealtime()-lastAction<ActionPolicy.interval(t))) return;
        lastAction=SystemClock.elapsedRealtime();
        Rect r=screen();
        if (t==1) { gesture(r.width()*.50f,r.height()*.77f,r.height()*.25f,ActionPolicy.SWIPE_MS); return; }
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null || !Config.words(this).contentEquals(root.getPackageName()==null?"":root.getPackageName())) {
            status="前台应用已变化，请重试"; return;
        }
        String knownLabel=Config.get(this).getString("label","认识");
        String nextLabel=Config.get(this).getString("nextLabel","下一个");
        ArrayList<AccessibilityNodeInfo> known=new ArrayList<>(), next=new ArrayList<>();
        find(root,knownLabel,known,0);
        find(root,nextLabel,next,0);
        int action=ActionPolicy.reviewAction(known.size(),next.size());
        if(action==-1) { status="找到多个同名控件，已跳过"; return; }
        if(action==0 && Config.get(this).getBoolean("coordinate",false)) {
            int orientation=getResources().getConfiguration().orientation;
            if (!Config.get(this).contains("x") || orientation!=Config.get(this).getInt("orientation",0)
                || r.width()!=Config.get(this).getInt("width",0) || r.height()!=Config.get(this).getInt("height",0)) {
                status="坐标未校准或屏幕布局变化，请重新校准"; return;
            }
            float x=Config.get(this).getFloat("x",0)*r.width(), y=Config.get(this).getFloat("y",0)*r.height();
            gesture(x,y,y,70); return;
        }
        if(action==0) { status="没找到“认识”或“下一个”，请核对按钮文字"; return; }
        String clicked=action==1?knownLabel:nextLabel;
        AccessibilityNodeInfo n=(action==1?known:next).get(0); Rect b=new Rect(); n.getBoundsInScreen(b);
        AccessibilityNodeInfo parent=n;
        for(int i=0;i<4 && parent!=null;i++,parent=parent.getParent()) {
            Rect pb=new Rect(); parent.getBoundsInScreen(pb);
            if (parent.isClickable() && parent.isEnabled() && pb.width()<=r.width()*.9 && pb.height()<=r.height()*.3
                && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { status="已点击“"+clicked+"”"; return; }
        }
        if (r.contains(b) && !b.isEmpty()) gesture(b.exactCenterX(),b.exactCenterY(),b.exactCenterY(),70);
        else status="控件不在当前屏幕内";
    }
    private void find(AccessibilityNodeInfo n,String label,ArrayList<AccessibilityNodeInfo> hits,int depth) {
        if(n==null || depth>60 || hits.size()>1) return;
        if(n.isVisibleToUser() && n.isEnabled() && (label.contentEquals(n.getText()==null?"":n.getText())
            || label.contentEquals(n.getContentDescription()==null?"":n.getContentDescription()))) hits.add(n);
        for(int i=0;i<n.getChildCount();i++) find(n.getChild(i),label,hits,depth+1);
    }
    private WindowManager.LayoutParams params(int w,int h,int flags) {
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,flags,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.LEFT; return p;
    }
    void removePanel() { if(panel!=null) { wm.removeView(panel); panel=null; } }
    void refreshPanel() {
        removePanel();
        if(!Config.get(this).getBoolean("panel",false)) return;
        LinearLayout box=new LinearLayout(this); box.setOrientation(1); box.setPadding(8,8,8,8); box.setBackgroundColor(0xEEEDF5FF);
        TextView title=new TextView(this); title.setText("笔键联动 · 可拖动"); box.addView(title);
        WindowManager.LayoutParams p=params(-2,-2,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE); p.x=12; p.y=180;
        title.setOnTouchListener(new View.OnTouchListener(){float x,y; int px,py;
            public boolean onTouch(View v,android.view.MotionEvent e){
                if(e.getAction()==0){x=e.getRawX(); y=e.getRawY(); px=p.x; py=p.y;}
                if(e.getAction()==2){p.x=px+(int)(e.getRawX()-x);p.y=py+(int)(e.getRawY()-y);wm.updateViewLayout(box,p);}return true;}});
        Button test=new Button(this); test.setText("测试当前应用动作"); box.addView(test); test.setOnClickListener(v->{act(); Toast.makeText(this,status,Toast.LENGTH_SHORT).show();});
        Button like=new Button(this); like.setText("测试抖音点赞"); box.addView(like); like.setOnClickListener(v->{cancelPendingSwipe();likeVideo();});
        Button calibrate=new Button(this); calibrate.setText("校准“认识”位置"); box.addView(calibrate); calibrate.setOnClickListener(v->calibrate());
        Button inspect=new Button(this); inspect.setText("查看前台包名"); box.addView(inspect); inspect.setOnClickListener(v->{
            AccessibilityNodeInfo root=getRootInActiveWindow();
            String pkg=root==null?"无法读取窗口":String.valueOf(root.getPackageName());
            android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("package",pkg));
            Toast.makeText(this,pkg+"（已复制）",Toast.LENGTH_LONG).show();
        });
        panel=box; wm.addView(panel,p);
    }
    void removePicker() { if(picker!=null){wm.removeView(picker);picker=null;} }
    void calibrate() {
        if(target()!=2){Toast.makeText(this,"请先打开不背单词复习页",Toast.LENGTH_SHORT).show();return;}
        removePanel();
        TextView shade=new TextView(this); shade.setBackgroundColor(0x2277AAFF); shade.setText("请点一下“认识”中心位置\n此次触摸只记录位置，不会点击底层按钮\n10 秒后自动取消"); shade.setTextSize(20); shade.setGravity(Gravity.CENTER);
        picker=shade;
        wm.addView(shade,params(-1,-1,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN));
        shade.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_UP){
                Rect r=screen();
                Config.get(this).edit().putFloat("x",e.getRawX()/r.width()).putFloat("y",e.getRawY()/r.height())
                    .putInt("orientation",getResources().getConfiguration().orientation).putInt("width",r.width()).putInt("height",r.height()).apply();
                removePicker();refreshPanel();status="位置已保存；需在主界面手动启用坐标模式";
                Toast.makeText(this,status,Toast.LENGTH_LONG).show();
            } return true;
        });
        handler.postDelayed(()->{if(picker==shade){removePicker();refreshPanel();}},10000);
    }
}
