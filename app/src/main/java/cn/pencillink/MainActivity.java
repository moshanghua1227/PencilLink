package cn.pencillink;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
    private LinearLayout body;
    private TextView state;
    private String page="home";
    private final int ink=Color.rgb(42,55,81), muted=Color.rgb(111,126,151), blue=Color.rgb(92,123,186),
        canvas=Color.rgb(245,247,252), pale=Color.rgb(232,239,253), line=Color.rgb(223,231,246);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){
        if(state!=null) state.setText((PenService.live==null?"等待连接 · 请先开启无障碍服务":PenService.status)
            +"\n已录入："+KeyEvent.keyCodeToString(Config.get(MainActivity.this).getInt("key",0))
            +(page.equals("connect")||page.equals("advanced")?"\n最近事件："+PenService.lastKey:"")
            +(SystemClock.elapsedRealtime()<PenService.learnUntil?"\n录入中，请短按笔键…":""));
        handler.postDelayed(this,600);
    }};
    @Override public void onCreate(Bundle b){
        super.onCreate(b);if(b!=null)page=b.getString("page","home");
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,()->back());
        render();
    }
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private android.graphics.drawable.Drawable ripple(int color,int radius){return new RippleDrawable(ColorStateList.valueOf(0x185C7BBA),bg(color,radius),bg(Color.WHITE,radius));}
    private void open(String next){page=next;render();}
    private void back(){if(!page.equals("home"))open("home");else finish();}
    @Override public void onBackPressed(){back();}
    @Override protected void onSaveInstanceState(Bundle b){b.putString("page",page);super.onSaveInstanceState(b);}
    private void render(){
        state=null;
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(canvas);scroll.setClipToPadding(false);
        FrameLayout frame=new FrameLayout(this);scroll.addView(frame,new ScrollView.LayoutParams(-1,-2));
        body=new LinearLayout(this);body.setOrientation(1);body.setPadding(dp(24),dp(24),dp(24),dp(36));
        int width=Math.min(getResources().getDisplayMetrics().widthPixels,dp(640));
        FrameLayout.LayoutParams centered=new FrameLayout.LayoutParams(width,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);frame.addView(body,centered);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
                scroll.setPadding(bars.left,bars.top,bars.right,bars.bottom);}
            return insets;
        });
        TextView brand=text("P E N C I L   L I N K",11);brand.setTextColor(blue);brand.setTypeface(null,Typeface.BOLD);
        if(!page.equals("home"))button("‹  首页",()->open("home"),false);
        switch(page){case "connect":connection();break;case "douyin":douyin();break;case "words":words();break;case "advanced":advanced();break;default:home();}
        text("笔键联动  0.3.1  ·  本机处理",11).setTextColor(muted);
    }
    private void home(){
        heading("轻按，继续。","让笔上的一个按键，接住你的下一步。");
        LinearLayout hero=new LinearLayout(this);hero.setOrientation(1);hero.setPadding(dp(20),dp(18),dp(20),dp(18));hero.setBackground(bg(pale,24));add(hero,12);
        TextView label=label("连接状态",12,blue);hero.addView(label);
        state=label("正在检查连接…",15,ink);state.setPadding(0,dp(10),0,dp(4));state.setTextIsSelectable(true);hero.addView(state);
        toggle("开启按键联动","enabled",false);
        text("功能与设置",18).setTypeface(null,Typeface.BOLD);
        menu("01","连接与按键","开启服务，录入你的操作键","connect");
        menu("02","抖音","单击切视频，双击点赞","douyin");
        menu("03","不背单词","认识与下一个，一键继续","words");
        menu("04","高级与诊断","动作测试、应用匹配与停用","advanced");
        text("首次使用，先进入「连接与按键」完成设置。",13);
    }
    private void connection(){
        heading("连接与按键","先连接，再录入。准备好就能继续。");status();
        section("第一步 · 开启服务");
        text("用于接收按键信号，并在目标应用中执行滑动或点击。",15);
        button("阅读说明并开启无障碍",()->new AlertDialog.Builder(this).setTitle("开启无障碍服务")
            .setMessage("本应用需要读取前台应用名称和界面控件、接收系统转发的按键，并代表你滑动或点击。所有处理在本机完成，无联网权限、不保存界面内容。\n\n手写笔按键可能被系统拦截；开启服务不代表一定兼容。开启后请录入按键。")
            .setNegativeButton("取消",null).setPositiveButton("前往设置",(d,w)->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).show(),true);
        section("第二步 · 录入按键");
        text("开始后，在 15 秒内短按一次笔上的操作键。",15);
        button("开始录入",()->{
            if(PenService.live==null){toast("请先开启无障碍服务");return;}
            PenService.learnUntil=SystemClock.elapsedRealtime()+15000;PenService.status="等待按键";
            toast("请短按操作键；也可在倒计时内切到目标应用再试");
        },true);
        note("笔需先在系统中配对。超过 450 毫秒视为长按；返回、电源、Home 等系统键不参与录入。");
        section("没有收到事件？");
        text("可用蓝牙键盘或音量键做对照，之后重新录入笔键。如果其他键正常、笔键始终没有事件，可能需要继续适配厂商按键通道。",14);
    }
    private void douyin(){
        heading("抖音","换一个视频，或留下一个喜欢。");
        note("短按一次 → 切到下一个视频\n快速短按两次 → 点赞，不切视频");
        section("双击节奏");
        TextView value=text("",25);value.setTextColor(blue);
        SeekBar slider=new SeekBar(this);slider.setMax(550);slider.setProgressTintList(ColorStateList.valueOf(blue));slider.setThumbTintList(ColorStateList.valueOf(blue));
        int saved=Math.max(250,Math.min(800,Config.get(this).getInt("doubleWindow",450)));slider.setProgress(saved-250);body.addView(slider);value.setText(saved+" ms");
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int progress,boolean user){value.setText((progress+250)+" ms");if(user)Config.get(MainActivity.this).edit().putInt("doubleWindow",progress+250).apply();}
            public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}
        });
        LinearLayout ends=new LinearLayout(this);
        TextView fast=label("更快识别",12,muted), relaxed=label("更宽松",12,muted);relaxed.setGravity(Gravity.END);
        ends.addView(fast,new LinearLayout.LayoutParams(0,-2,1));ends.addView(relaxed,new LinearLayout.LayoutParams(0,-2,1));body.addView(ends);
        text("默认 450 毫秒。若双击仍变成两次切视频，调到 600–800 毫秒。第二次按下进入窗口，就暂停第一次上滑，等松开后确认点赞。",14);
        note("识别时间越长，单击等待越久。上滑动作本身仍为 120 毫秒；长按不触发操作。");
        button("恢复推荐值 450 ms",()->{Config.get(this).edit().putInt("doubleWindow",450).apply();render();},false);
        button("打开动作测试设置",()->open("advanced"),true);
        text("请只在全屏视频页使用。评论、搜索、私信等页面不会自动识别为非视频页。浮窗可单独测试点赞动作。",13);
    }
    private void words(){
        heading("不背单词","复习的两步，用同一个按键。");
        note("看到「认识」→ 短按标记认识\n出现「下一个」→ 再短按继续复习\n每次只执行一步，不自动连点。");
        section("按钮文字");
        field("认识按钮","label","认识");field("下一个按钮","nextLabel","下一个");
        text("优先精确匹配“认识”，不匹配“不认识”。实际显示“下一词”等文字时，在上方修改并保存。",14);
        section("坐标备用方案");toggle("文字查找失败时使用坐标","coordinate",false);
        button("打开校准浮窗设置",()->open("advanced"),false);
        note("只保存一个坐标。两种按钮位置相同时才适合一起使用。坐标无法判断复习页面，在其他页面可能误点；旋转、分屏、布局变化后需要重新校准。");
    }
    private void advanced(){
        heading("高级与诊断","确认信号，也确认每一步动作。");status();
        section("测试浮窗");toggle("显示动作测试与校准浮窗","panel",false);
        text("打开目标应用后，浮窗可测试当前动作、测试抖音点赞、校准位置或复制前台包名。先测试动作，再测试笔键，便于定位问题。",14);
        section("应用匹配");field("抖音包名","douyin","com.ss.android.ugc.aweme");field("不背单词包名","words","cn.com.langeasy.LangEasyLexis");
        section("停用与隐私");
        button("关闭所有联动与浮窗",()->{
            Config.get(this).edit().putBoolean("enabled",false).putBoolean("panel",false).apply();
            if(PenService.live!=null)PenService.live.refreshPanel();toast("已关闭联动和浮窗");render();
        },false);
        text("若提示受限设置，请在系统应用信息中按提示允许，再开启服务。完全停用可在系统无障碍设置关闭服务。\n\n所有处理在本机完成，无联网权限，不保存屏幕内容。本应用并非 OPPO 官方应用。",14);
    }
    private TextView label(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));v.setLineSpacing(dp(3),1.08f);return v;}
    private TextView text(String s,int size){TextView v=label(s,size,size<=15?muted:ink);v.setPadding(0,dp(10),0,dp(10));body.addView(v);return v;}
    private void add(View v,int margin){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(margin);body.addView(v,p);}
    private void heading(String title,String sub){TextView t=text(title,32);t.setTypeface(Typeface.create("sans-serif-medium",0));text(sub,15);}
    private void section(String title){TextView t=text(title,18);t.setPadding(0,dp(26),0,dp(8));t.setTypeface(null,Typeface.BOLD);}
    private void note(String s){TextView v=label(s,14,ink);v.setPadding(dp(18),dp(16),dp(18),dp(16));v.setBackground(bg(pale,18));add(v,12);}
    private void status(){state=label("正在检查连接…",14,ink);state.setTextIsSelectable(true);state.setPadding(dp(18),dp(16),dp(18),dp(16));state.setBackground(bg(pale,18));add(state,12);}
    private void menu(String number,String title,String description,String target){
        LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(16),dp(20),dp(16),dp(20));card.setBackground(ripple(Color.WHITE,22));add(card,12);
        TextView n=label(number,13,blue);n.setGravity(Gravity.CENTER);n.setBackground(bg(pale,14));card.addView(n,new LinearLayout.LayoutParams(dp(42),dp(42)));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(1);LinearLayout.LayoutParams c=new LinearLayout.LayoutParams(0,-2,1);c.leftMargin=dp(14);card.addView(copy,c);
        TextView t=label(title,19,ink);t.setTypeface(null,Typeface.BOLD);copy.addView(t);
        TextView d=label(description,13,muted);d.setPadding(0,dp(5),0,0);copy.addView(d);
        TextView chevron=label("›",26,blue);chevron.setPadding(dp(8),0,0,0);card.addView(chevron);
        card.setContentDescription(title+"，"+description);card.setOnClickListener(v->open(target));
    }
    private void button(String s,Runnable action,boolean primary){TextView b=label(s,15,primary?Color.WHITE:blue);b.setTypeface(null,Typeface.BOLD);b.setGravity(Gravity.CENTER);b.setMinHeight(dp(50));b.setPadding(dp(14),dp(13),dp(14),dp(13));b.setBackground(ripple(primary?blue:Color.WHITE,16));b.setFocusable(true);b.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName("android.widget.Button");}});add(b,10);b.setOnClickListener(v->action.run());}
    private void toggle(String title,String key,boolean def){Switch s=new Switch(this);s.setText(title);s.setTextSize(15);s.setTextColor(ink);s.setPadding(dp(16),dp(18),dp(16),dp(18));s.setBackground(bg(Color.WHITE,18));s.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{blue,Color.rgb(182,193,215)}));s.setChecked(Config.get(this).getBoolean(key,def));add(s,14);s.setOnCheckedChangeListener((b,on)->{Config.get(this).edit().putBoolean(key,on).apply();if(key.equals("panel")&&PenService.live!=null)PenService.live.refreshPanel();});}
    private void field(String title,String key,String def){text(title,14);EditText e=new EditText(this);e.setText(Config.get(this).getString(key,def));e.setTextSize(16);e.setTextColor(ink);e.setSingleLine(true);e.setPadding(dp(16),dp(14),dp(16),dp(14));GradientDrawable field=bg(Color.WHITE,14);field.setStroke(dp(1),line);e.setBackground(field);e.setBackgroundTintList(null);body.addView(e);button("保存"+title,()->{String value=e.getText().toString().trim();if(value.isEmpty()){toast("不能留空");return;}if(!key.equals("label")&&!key.equals("nextLabel")&&(value.equals(getPackageName())||!value.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))){toast("请输入正确的应用包名");return;}Config.get(this).edit().putString(key,value).apply();toast("已保存");},false);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override protected void onResume(){super.onResume();handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    @Override public boolean dispatchKeyEvent(KeyEvent e){if(PenService.live==null)PenService.lastKey=KeyEvent.keyCodeToString(e.getKeyCode())+" / Activity";return super.dispatchKeyEvent(e);}
}
