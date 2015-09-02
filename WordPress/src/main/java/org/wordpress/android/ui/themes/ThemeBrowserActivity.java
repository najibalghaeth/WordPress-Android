package org.wordpress.android.ui.themes;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.webkit.WebViewFragment;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.VolleyError;
import com.wordpress.rest.RestRequest.ErrorListener;
import com.wordpress.rest.RestRequest.Listener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.wordpress.android.R;
import org.wordpress.android.WordPress;
import org.wordpress.android.analytics.AnalyticsTracker;
import org.wordpress.android.models.Blog;
import org.wordpress.android.models.Theme;
import org.wordpress.android.ui.ActivityId;
import org.wordpress.android.ui.ActivityLauncher;
import org.wordpress.android.ui.themes.ThemeBrowserFragment.ThemeBrowserFragmentCallback;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.AppLog.T;
import org.wordpress.android.util.NetworkUtils;
import org.wordpress.android.widgets.WPAlertDialogFragment;

import java.util.ArrayList;

/**
 * The theme browser.
 */
public class ThemeBrowserActivity extends AppCompatActivity implements ThemeBrowserFragmentCallback {
    private boolean mFetchingThemes = false;
    private boolean mIsRunning;

    private boolean mIsActivatingTheme = false;
    private ThemeBrowserFragment mThemeBrowserFragment;
    private static final String KEY_IS_ACTIVATING_THEME = "is_activating_theme";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (WordPress.wpDB == null) {
            Toast.makeText(this, R.string.fatal_db_error, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (savedInstanceState == null) {
            AnalyticsTracker.track(AnalyticsTracker.Stat.THEMES_ACCESSED_THEMES_BROWSER);
        }

        setContentView(R.layout.theme_browser_activity);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setElevation(0.0f);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle("back");

        mThemeBrowserFragment = (ThemeBrowserFragment) getFragmentManager().findFragmentById(R.id.theme_tab_fragment);
    }

    @Override
    public void finish() {
        super.finish();
        ActivityLauncher.slideOutToRight(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsRunning = true;
        ActivityId.trackLastActivity(ActivityId.THEMES);

        // fetch themes if we don't have any
        if (NetworkUtils.isNetworkAvailable(this) && WordPress.getCurrentBlog() != null
                && WordPress.wpDB.getThemeCount(getBlogId()) == 0) {
            fetchThemes();
            mThemeBrowserFragment.setRefreshing(true);
        }
    }

    public void fetchThemes() {
        if (mFetchingThemes) {
            return;
        }
        String siteId = getBlogId();
        mFetchingThemes = true;
        WordPress.getRestClientUtilsV1_2().getThemes(siteId, 100, mThemeBrowserFragment.getPage(), new Listener() {
                    @Override
                    public void onResponse(JSONObject response) {
                        new FetchThemesTask().execute(response);
                    }
                }, new ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError response) {
                        if (response.toString().equals(AuthFailureError.class.getName())) {
                            String errorTitle = getString(R.string.theme_auth_error_title);
                            String errorMsg = getString(R.string.theme_auth_error_message);

                            if (mIsRunning) {
                                FragmentTransaction ft = getFragmentManager().beginTransaction();
                                WPAlertDialogFragment fragment = WPAlertDialogFragment.newAlertDialog(errorMsg,
                                        errorTitle);
                                ft.add(fragment, "alert");
                                ft.commitAllowingStateLoss();
                            }
                            AppLog.d(T.THEMES, "Failed to fetch themes: failed authenticate user");
                        } else {
                            Toast.makeText(ThemeBrowserActivity.this, R.string.theme_fetch_failed, Toast.LENGTH_LONG)
                                    .show();
                            AppLog.d(T.THEMES, "Failed to fetch themes: " + response.toString());
                        }
                        mFetchingThemes = false;
                    }
                }
        );
    }

    public void fetchCurrentTheme() {
        final String siteId = getBlogId();

        WordPress.getRestClientUtilsV1_1().getCurrentTheme(siteId, new Listener() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            Theme theme = Theme.fromJSONV1_1(response);
                            if (theme != null) {
                                WordPress.wpDB.setCurrentTheme(siteId, theme.getId());
                                mThemeBrowserFragment.setRefreshing(false);
                                if (mThemeBrowserFragment.mCurrentThemeTextView != null) {
                                    mThemeBrowserFragment.mCurrentThemeTextView.setText(theme.getName());
                                    mThemeBrowserFragment.mCurrentThemeId = theme.getId();
                                }
                            }
                        } catch (JSONException e) {
                            AppLog.e(T.THEMES, e);
                        }
                    }
                }, new ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError response) {
                        String themeId = WordPress.wpDB.getCurrentThemeId(siteId);
                        Theme currentThemeFromDB = WordPress.wpDB.getTheme(siteId, themeId);
                        if (currentThemeFromDB != null) {
                            mThemeBrowserFragment.mCurrentThemeTextView.setText(currentThemeFromDB.getName());
                            mThemeBrowserFragment.mCurrentThemeId = currentThemeFromDB.getId();
                        }
                    }
                }
        );
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int i = item.getItemId();
        if (i == android.R.id.home) {
            onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        FragmentManager fm = getFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStack();
        } else {
            super.onBackPressed();
        }
    }

    private String getBlogId() {
        if (WordPress.getCurrentBlog() == null)
            return "0";
        return String.valueOf(WordPress.getCurrentBlog().getRemoteBlogId());
    }

    @Override
    public void onActivateSelected(String themeId) {

    }

    @Override
    public void onPreviewSelected(String themeId) {

    }

    @Override
    public void onDemoSelected(String themeId) {
        
    }

    @Override
    public void onDetailsSelected(String themeId) {
        Intent intent = new Intent(this, ThemeSupportActivity.class);
        intent.putExtra("themeId", themeId);
        intent.putExtra("type", 1);
        startActivity(intent);
    }

    @Override
    public void onSupportSelected(String themeId) {
        Intent intent = new Intent(this, ThemeSupportActivity.class);
        intent.putExtra("themeId", themeId);
        intent.putExtra("type", 0);
        startActivity(intent);
    }

    @Override
    public void onCustomizeSelected(String themeId) {

    }

    public class FetchThemesTask extends AsyncTask<JSONObject, Void, ArrayList<Theme>> {
        @Override
        protected ArrayList<Theme> doInBackground(JSONObject... args) {
            JSONObject response = args[0];
            final ArrayList<Theme> themes = new ArrayList<>();

            if (response != null) {
                JSONArray array;
                try {
                    array = response.getJSONArray("themes");

                    if (array != null) {
                        int count = array.length();
                        for (int i = 0; i < count; i++) {
                            JSONObject object = array.getJSONObject(i);
                            Theme theme = Theme.fromJSONV1_2(object);
                            if (theme != null) {
                                theme.save();
                                themes.add(theme);
                            }
                        }
                    }
                } catch (JSONException e) {
                    AppLog.e(T.THEMES, e);
                }
            }

            fetchCurrentTheme();

            if (themes.size() > 0) {
                return themes;
            }

            return null;
        }

        @Override
        protected void onPostExecute(final ArrayList<Theme> result) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mFetchingThemes = false;
                    if (result == null) {
                        Toast.makeText(ThemeBrowserActivity.this, R.string.theme_fetch_failed, Toast.LENGTH_SHORT)
                             .show();
                    }
                    mThemeBrowserFragment.setRefreshing(false);
                }
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mIsRunning = false;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_IS_ACTIVATING_THEME, mIsActivatingTheme);
    }

    public static boolean isAccessible() {
        // themes are only accessible to admin wordpress.com users
        Blog blog = WordPress.getCurrentBlog();
        return (blog != null && blog.isAdmin() && blog.isDotcomFlag());
    }
}
