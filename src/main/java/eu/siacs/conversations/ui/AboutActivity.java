package eu.siacs.conversations.ui;

import static eu.siacs.conversations.ui.XmppActivity.configureActionBar;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import eu.siacs.conversations.R;
import eu.siacs.conversations.ui.util.SettingsUtils;
import eu.siacs.conversations.utils.ThemeHelper;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onResume(){
        super.onResume();
        SettingsUtils.applyScreenshotPreventionSetting(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTheme(ThemeHelper.find(this));
        Integer override = ThemeHelper.findThemeOverrideStyle(this);
        if (override != null) {
            getTheme().applyStyle(override, true);
        }

        setContentView(R.layout.activity_about);
        setSupportActionBar(findViewById(R.id.toolbar));
        configureActionBar(getSupportActionBar());
        setTitle(getString(R.string.title_activity_about_x, getString(R.string.app_name)));
    }
}
