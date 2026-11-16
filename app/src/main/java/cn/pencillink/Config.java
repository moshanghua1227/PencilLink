package cn.pencillink;

import android.content.Context;
import android.content.SharedPreferences;

final class Config {
    static SharedPreferences get(Context c) { return c.getSharedPreferences("pen", Context.MODE_PRIVATE); }
    static String douyin(Context c) { return get(c).getString("douyin", "com.ss.android.ugc.aweme"); }
    static String words(Context c) { return get(c).getString("words", "cn.com.langeasy.LangEasyLexis"); }
}
