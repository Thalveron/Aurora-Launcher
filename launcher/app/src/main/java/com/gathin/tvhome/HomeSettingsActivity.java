package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

/** Lets the user change HOME through the system UI, without shell or privileged permissions. */
public class HomeSettingsActivity extends Activity {
    private String desktopLocaleAtCreation;
    @Override protected void attachBaseContext(android.content.Context base){desktopLocaleAtCreation=DesktopLanguage.code(base);super.attachBaseContext(DesktopLanguage.wrap(base));}
    @Override protected void onResume(){super.onResume();if(!DesktopLanguage.code(this).equals(desktopLocaleAtCreation))recreate();}

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            if(android.os.Build.VERSION.SDK_INT>=29){android.app.role.RoleManager roles=getSystemService(android.app.role.RoleManager.class);if(roles!=null&&roles.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)&&!roles.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)){startActivityForResult(roles.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME),1);return;}}
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            try{startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));}catch(Exception noChooser){Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();}
        } finally {
            finish();
        }
    }
}
