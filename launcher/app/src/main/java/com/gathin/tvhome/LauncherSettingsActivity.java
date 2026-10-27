package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    private String desktopLocaleAtCreation;
    @Override protected void attachBaseContext(android.content.Context base){desktopLocaleAtCreation=DesktopLanguage.code(base);super.attachBaseContext(DesktopLanguage.wrap(base));}
    @Override protected void onResume(){super.onResume();if(!DesktopLanguage.code(this).equals(desktopLocaleAtCreation))recreate();}

    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private int columns;
    private boolean animations;
    private boolean advancedEffects;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        android.widget.TextView language=new android.widget.TextView(this);language.setText("语言 / Language: "+(DesktopLanguage.english(this)?"English":"简体中文"));language.setTextSize(24);language.setTextColor(android.graphics.Color.WHITE);language.setPadding(20,20,20,20);language.setFocusable(true);language.setBackgroundResource(R.drawable.settings_choice_bg);language.setOnClickListener(v->DesktopLanguage.choose(this));
        android.widget.LinearLayout languageRows=(android.widget.LinearLayout)((android.widget.ScrollView)((android.view.ViewGroup)findViewById(android.R.id.content)).getChildAt(0)).getChildAt(0);
        languageRows.addView(language,1);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        columns = config.columns;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                setResult(RESULT_OK);
                refresh();
            });
        }
        // Keep the numeric choices increasing in the Hebrew reading direction. Reorder the
        // actual views so D-pad focus follows their positions without changing the saved values.
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            LinearLayout row = (LinearLayout) columnChoices[0].getParent();
            row.removeAllViews();
            for (int i = columnChoices.length - 1; i >= 0; i--) row.addView(columnChoices[i]);
        }
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            setResult(RESULT_OK);
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        TextView ambientChoice=findViewById(R.id.appAmbientChoice);
        Runnable refreshAmbient=()->ambientChoice.setText(DesktopLanguage.t(LauncherSettingsActivity.this,"应用颜色氛围光：")+(choices.getBoolean("app_ambient",true)?DesktopLanguage.t(LauncherSettingsActivity.this,"开启"):DesktopLanguage.t(LauncherSettingsActivity.this,"关闭")));
        refreshAmbient.run();ambientChoice.setOnClickListener(v->{choices.edit().putBoolean("app_ambient",!choices.getBoolean("app_ambient",true)).apply();refreshAmbient.run();setResult(RESULT_OK);});
        TextView positionChoice=findViewById(R.id.musicPositionChoice);
        final String[] positions={DesktopLanguage.t(LauncherSettingsActivity.this,"靠左"),DesktopLanguage.t(LauncherSettingsActivity.this,"居中"),DesktopLanguage.t(LauncherSettingsActivity.this,"靠右")};
        Runnable refreshPosition=()->positionChoice.setText(DesktopLanguage.t(LauncherSettingsActivity.this,"播放卡片位置：")+positions[Math.max(0,Math.min(2,choices.getInt("music_card_position",0)))]+DesktopLanguage.t(LauncherSettingsActivity.this,"（频谱跟随，歌词独立显示）"));
        refreshPosition.run();
        positionChoice.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(DesktopLanguage.t(LauncherSettingsActivity.this,"播放卡片位置")).setSingleChoiceItems(positions,choices.getInt("music_card_position",0),(dialog,index)->{
            choices.edit().putInt("music_card_position",index).apply();refreshPosition.run();setResult(RESULT_OK);dialog.dismiss();
        }).show());
        TextView lyricsChoice=findViewById(R.id.desktopLyricsChoice);
        findViewById(R.id.ambientMusicChoice).setOnClickListener(v->startActivity(new Intent(this,AmbientMusicActivity.class)));
        Runnable refreshLyrics=()->lyricsChoice.setText("桌面显示 · 播放卡片："+(choices.getBoolean("desktop_card_visible",choices.getBoolean("desktop_lyrics",true))?"开启":"关闭")+" · 歌词："+(choices.getBoolean("desktop_lyrics_visible",choices.getBoolean("desktop_lyrics",true))?"开启":"关闭"));
        refreshLyrics.run();lyricsChoice.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("分别显示或隐藏")
            .setMultiChoiceItems(new String[]{"桌面播放卡片","桌面歌词"},new boolean[]{choices.getBoolean("desktop_card_visible",choices.getBoolean("desktop_lyrics",true)),choices.getBoolean("desktop_lyrics_visible",choices.getBoolean("desktop_lyrics",true))},(dialog,index,checked)->{choices.edit().putBoolean(index==0?"desktop_card_visible":"desktop_lyrics_visible",checked).apply();refreshLyrics.run();setResult(RESULT_OK);})
            .setPositiveButton("完成",null).show());
        findViewById(R.id.lyricsColorChoice).setOnClickListener(v->{
            final int[] colors={0xFF79E8FF,0xFFFFFFFF,0xFFFFD166,0xFF76F0B4,0xFFFF8CB7,0xFFC7A4FF,0xFFFF9B54,0xFF65A8FF};
            final String[] names={DesktopLanguage.t(LauncherSettingsActivity.this,"冰蓝"),DesktopLanguage.t(LauncherSettingsActivity.this,"纯白"),DesktopLanguage.t(LauncherSettingsActivity.this,"暖金"),DesktopLanguage.t(LauncherSettingsActivity.this,"薄荷绿"),DesktopLanguage.t(LauncherSettingsActivity.this,"樱花粉"),DesktopLanguage.t(LauncherSettingsActivity.this,"淡紫"),DesktopLanguage.t(LauncherSettingsActivity.this,"橙色"),DesktopLanguage.t(LauncherSettingsActivity.this,"天蓝")};
            new AlertDialog.Builder(this).setTitle(DesktopLanguage.t(LauncherSettingsActivity.this,"修改哪种歌词颜色？")).setItems(new String[]{DesktopLanguage.t(LauncherSettingsActivity.this,"未唱部分颜色"),DesktopLanguage.t(LauncherSettingsActivity.this,"跟随播放的逐字高亮颜色")},(d,kind)->new AlertDialog.Builder(this).setTitle(kind==0?DesktopLanguage.t(LauncherSettingsActivity.this,"未唱部分颜色"):DesktopLanguage.t(LauncherSettingsActivity.this,"逐字高亮颜色")).setItems(names,(dialog,index)->{choices.edit().putInt(kind==0?"lyrics_color":"lyrics_highlight",colors[index]).apply();setResult(RESULT_OK);}).show()).show();
        });
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove("animations").remove("advanced_effects").commit();
            Config current = Config.load(this);
            columns = current.columns;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            setResult(RESULT_OK);
            refresh();
        });
        TextView homeReturn = findViewById(R.id.homeReturnChoice);
        homeReturn.setText(HomeCompatibility.enabled(this) ? R.string.home_return_on : R.string.home_return_off);
        homeReturn.setOnClickListener(v -> {
            HomeCompatibility.setEnabled(this, !HomeCompatibility.enabled(this));
            homeReturn.setText(HomeCompatibility.enabled(this) ? R.string.home_return_on : R.string.home_return_off);
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        findViewById(R.id.backgroundChoice).setOnClickListener(v -> startActivity(new Intent(this, BackgroundActivity.class)));
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        refresh();
        if(getIntent().getBooleanExtra("music_card_settings",false))positionChoice.requestFocus();else columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        setResult(RESULT_OK);
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains("animations")
                || choices.contains("advanced_effects");
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
