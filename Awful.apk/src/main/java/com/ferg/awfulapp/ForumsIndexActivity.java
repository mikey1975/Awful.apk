/**
 * *****************************************************************************
 * Copyright (c) 2011, Scott Ferguson
 * All rights reserved.
 * <p/>
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * * Neither the name of the software nor the
 * names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 * <p/>
 * THIS SOFTWARE IS PROVIDED BY SCOTT FERGUSON ''AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL SCOTT FERGUSON BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * *****************************************************************************
 */

package com.ferg.awfulapp;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.ferg.awfulapp.announcements.AnnouncementsManager;
import com.ferg.awfulapp.constants.Constants;
import com.ferg.awfulapp.dialog.ChangelogDialog;
import com.ferg.awfulapp.dialog.LogOutDialog;
import com.ferg.awfulapp.messages.PmManager;
import com.ferg.awfulapp.network.NetworkUtils;
import com.ferg.awfulapp.preferences.AwfulPreferences;
import com.ferg.awfulapp.preferences.Keys;
import com.ferg.awfulapp.preferences.SettingsActivity;
import com.ferg.awfulapp.provider.ColorProvider;
import com.ferg.awfulapp.provider.StringProvider;
import com.ferg.awfulapp.sync.SyncManager;
import com.ferg.awfulapp.thread.AwfulURL;
import com.ferg.awfulapp.util.AwfulUtils;
import com.ferg.awfulapp.widget.ToggleViewPager;

import java.util.Locale;

//import com.ToxicBakery.viewpager.transforms.*;

public class ForumsIndexActivity extends AwfulActivity
        implements PmManager.Listener, AnnouncementsManager.AnnouncementListener {
    protected static final String TAG = "ForumsIndexActivity";

    private static final int DEFAULT_HIDE_DELAY = 300;

    private static final int MESSAGE_HIDING = 0;
    private static final int MESSAGE_VISIBLE_CHANGE_IN_PROGRESS = 1;
    private ForumsIndexFragment mIndexFragment = null;

    private ForumDisplayFragment mForumFragment = null;
    private ThreadDisplayFragment mThreadFragment = null;
    private boolean skipLoad = false;
    private boolean isTablet;
    private AwfulURL url = new AwfulURL();

    private ToggleViewPager mViewPager;

    private View mDecorView;
    private ForumPagerAdapter pagerAdapter;
    private Toolbar mToolbar;

    private DrawerLayout mDrawerLayout;

    private ActionBarDrawerToggle mDrawerToggle;

    public static final int NULL_FORUM_ID = 0;
    private static final int NULL_THREAD_ID = 0;
    private static final int NULL_PAGE_ID = -1;

    private volatile int mNavForumId    = Constants.USERCP_ID;
    private volatile int mNavThreadId   = NULL_THREAD_ID;
    private volatile int mForumId       = Constants.USERCP_ID;
    private volatile int mForumPage     = 1;
    private volatile int mThreadId      = NULL_THREAD_ID;
    private volatile int mThreadPage    = 1;

    private GestureDetector mImmersionGestureDetector = null;
    private boolean mIgnoreFling;
    private String mThreadPost="";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.e(TAG,"onCreate");
        super.onCreate(savedInstanceState);
        isTablet = AwfulUtils.isTablet(this);
        int initialPage;
        if (savedInstanceState != null) {
            int forumId = savedInstanceState.getInt(Constants.FORUM_ID, mForumId);
            int forumPage = savedInstanceState.getInt(Constants.FORUM_PAGE, mForumPage);
            setForum(forumId, forumPage);

            int threadPage = savedInstanceState.getInt(Constants.THREAD_PAGE, 1);
            int threadId = savedInstanceState.getInt(Constants.THREAD_ID, NULL_THREAD_ID);
            setThread(threadId, threadPage);

            initialPage = savedInstanceState.getInt("viewPage", NULL_PAGE_ID);
        } else {
            initialPage = parseNewIntent(getIntent());
        }

        setContentView(R.layout.forum_index_activity);

        mViewPager = (ToggleViewPager) findViewById(R.id.forum_index_pager);
        mViewPager.setSwipeEnabled(!mPrefs.lockScrolling);
        if (!isTablet && AwfulUtils.isAtLeast(Build.VERSION_CODES.JELLY_BEAN_MR1) && !mPrefs.transformer.equals("Disabled")) {
            mViewPager.setPageTransformer(true, AwfulUtils.getViewPagerTransformer());
        }
        mViewPager.setOffscreenPageLimit(2);
        if (isTablet) {
            mViewPager.setPageMargin(1);
            //TODO what color should it use here?
            mViewPager.setPageMarginDrawable(new ColorDrawable(ColorProvider.ACTION_BAR.getColor()));
        }
        pagerAdapter = new ForumPagerAdapter(getSupportFragmentManager());
        mViewPager.setAdapter(pagerAdapter);
        mViewPager.setOnPageChangeListener(pagerAdapter);
        if (initialPage >= 0) {
            mViewPager.setCurrentItem(initialPage);
        }

        mToolbar = (Toolbar) findViewById(R.id.awful_toolbar);
        setSupportActionBar(mToolbar);
        setNavigationDrawer();
        setActionBar();
        checkIntentExtras();
        setupImmersion();

        PmManager.registerListener(this);
        AnnouncementsManager.getInstance().registerListener(this);
    }

    @Override
    public void onNewPm(@NonNull String messageUrl, @NonNull final String sender, final int unreadCount) {
        // TODO: 16/08/2016 probably best to put this in a method that the menu option calls too
        final Intent pmIntent = new Intent().setClass(this, PrivateMessageActivity.class);
        Uri uri = Uri.parse(messageUrl);
        if (uri != null) {
            pmIntent.setData(uri);
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String message = "Private message from %s\n(%d unread)";
                Snackbar.make(mToolbar, String.format(Locale.getDefault(), message, sender, unreadCount), Snackbar.LENGTH_LONG)
                        .setDuration(3000)
                        .setAction("View", new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                startActivity(pmIntent);
                            }
                        })
                        .show();
            }
        });
    }

    @Override
    public void onAnnouncementsUpdated(int newCount, int oldUnread, int oldRead, boolean isFirstUpdate) {
        // update the nav drawer, in case it needs to reflect the new announcements
        // TODO: 27/01/2017 maybe the nav drawer should register itself as a listener, if it needs updates
        if (isFirstUpdate || newCount > 0) {
            Resources res = getResources();
            // only show one of 'new announcements' or 'unread announcements', ignoring read ones
            // (only notify about unread for the first update after opening the app, to remind the user)
            if (newCount > 0) {
                showAnnouncementSnackbar(res.getQuantityString(R.plurals.numberOfNewAnnouncements, newCount, newCount));
            } else if (oldUnread > 0) {
                showAnnouncementSnackbar(res.getQuantityString(R.plurals.numberOfOldUnreadAnnouncements, oldUnread, oldUnread));
            }
        }
        updateNavigationMenu();
    }

    private void showAnnouncementSnackbar(String message) {
        Snackbar.make(mToolbar, message, Snackbar.LENGTH_LONG)
                .setDuration(3000)
                .setAction("View", click -> AnnouncementsManager.getInstance().showAnnouncements(this))
                .show();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }


    @SuppressLint("NewApi")
    private void setupImmersion() {
        if (AwfulUtils.isKitKat() && mPrefs.immersionMode) {
            mDecorView = getWindow().getDecorView();

            mDecorView.setOnSystemUiVisibilityChangeListener(
                    new View.OnSystemUiVisibilityChangeListener() {
                        @Override
                        public void onSystemUiVisibilityChange(int flags) {
                            boolean visible = (flags & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0;
                            if (visible) {
                                // Add some delay so the act of swiping to bring system UI into view doesn't turn it back off
                                mHideHandler.removeMessages(MESSAGE_VISIBLE_CHANGE_IN_PROGRESS);
                                mHideHandler.sendEmptyMessageDelayed(MESSAGE_VISIBLE_CHANGE_IN_PROGRESS, 800);
                                mIgnoreFling = true;
                            }
                        }
                    });

            mImmersionGestureDetector = new GestureDetector(this,
                    new GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                            if (mIgnoreFling) return true;

                            boolean visible = (mDecorView.getSystemUiVisibility()
                                    & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0;
                            if (visible) {
                                hideSystemUi();
                            }

                            return true;
                        }
                    });
            showSystemUi();
        }
    }


    /** Listener for all the navigation drawer menu items */
    private final NavigationView.OnNavigationItemSelectedListener navDrawerSelectionListener;
    {
        final Context context = this;
        navDrawerSelectionListener = new NavigationView.OnNavigationItemSelectedListener() {

            @Override
            public boolean onNavigationItemSelected (MenuItem menuItem){
                switch (menuItem.getItemId()) {
                    case R.id.sidebar_index:
                        displayForumIndex();
                        break;
                    case R.id.sidebar_forum:
                        displayForum(mNavForumId, 1);
                        break;
                    case R.id.sidebar_thread:
                        displayThread(mNavThreadId, mThreadPage, mNavForumId, mForumPage, false);
                        break;
                    case R.id.sidebar_bookmarks:
                        startActivity(new Intent().setClass(context, ForumsIndexActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                .putExtra(Constants.FORUM_ID, Constants.USERCP_ID)
                                .putExtra(Constants.FORUM_PAGE, 1));
                        break;
                    case R.id.sidebar_settings:
                        startActivity(new Intent().setClass(context, SettingsActivity.class));
                        break;
                    case R.id.sidebar_search:
                        startActivity(new Intent().setClass(context, SearchActivity.class));
                        break;
                    case R.id.sidebar_pm:
                        startActivity(new Intent().setClass(context, PrivateMessageActivity.class));
                        break;
                    case R.id.sidebar_announcements:
                        AnnouncementsManager.getInstance().showAnnouncements(ForumsIndexActivity.this);
                        break;
                    case R.id.sidebar_logout:
                        new LogOutDialog(context).show();
                        break;
                    default:
                        return false;
                }
                mDrawerLayout.closeDrawer(Gravity.LEFT);
                return true;
            }
        };
    }


    /**
     * Initialise the navigation drawer
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setNavigationDrawer() {
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        NavigationView navigation = (NavigationView) findViewById(R.id.navigation);
        navigation.setNavigationItemSelectedListener(navDrawerSelectionListener);

        mDrawerToggle = new ActionBarDrawerToggle(
                this,                  /* host Activity */
                mDrawerLayout,         /* DrawerLayout object */
                mToolbar,  /* nav drawer icon to replace 'Up' caret */
                R.string.drawer_open,  /* "open drawer" description */
                R.string.drawer_close  /* "close drawer" description */
        );
        mDrawerLayout.setDrawerListener(mDrawerToggle);

        View nav = navigation.getHeaderView(0);

        // update the navigation drawer header
        TextView username = (TextView) nav.findViewById(R.id.sidebar_username);
        if (null != username) {
            username.setText(mPrefs.username);
        }

        final ImageView avatar = (ImageView) nav.findViewById(R.id.sidebar_avatar);
        if (null != avatar) {
            if (null != mPrefs.userTitle) {
                if (!("".equals(mPrefs.userTitle))) {
                    NetworkUtils.getImageLoader().get(mPrefs.userTitle, new ImageLoader.ImageListener() {
                        @Override
                        public void onResponse(ImageLoader.ImageContainer response, boolean isImmediate) {
                            avatar.setImageBitmap(response.getBitmap());
                        }

                        @Override
                        public void onErrorResponse(VolleyError error) {
                            avatar.setImageResource(R.mipmap.ic_launcher);
                        }
                    });
                    if (AwfulUtils.isLollipop()) {
                        avatar.setClipToOutline(true);
                    }
                } else {
                    avatar.setImageResource(R.mipmap.ic_launcher);
                    if (AwfulUtils.isLollipop()) {
                        avatar.setClipToOutline(false);
                    }
                }
            }
        }

        updateNavigationMenu();
        mDrawerToggle.syncState();
    }


    /**
     * Update any changeable menu items, e.g. current thread title
     * Synchronized because it gets called by other threads through
     * {@link #setNavIds(int, Integer)}
     */
    private synchronized void updateNavigationMenu() {
        // avoid premature update calls (e.g. through onCreate)
        NavigationView navView = (NavigationView) findViewById(R.id.navigation);
        if (navView == null) {
            return;
        }
        Menu navMenu = navView.getMenu();
        final MenuItem forumItem = navMenu.findItem(R.id.sidebar_forum);
        final MenuItem threadItem = navMenu.findItem(R.id.sidebar_thread);

        // display the current forum title (or not)
        boolean showForumName = mNavForumId != NULL_FORUM_ID && mNavForumId != Constants.USERCP_ID;
        if (forumItem != null) {
            forumItem.setVisible(showForumName);
        }

        boolean showThreadName = mNavThreadId != NULL_THREAD_ID;
        if (threadItem != null) {
            threadItem.setVisible(showThreadName);
        }

        // update the forum and thread titles in the background, to avoid hitting the DB on the UI thread
        // to keep things simple, it also sets the text on anything we just hid, which will be placeholder text if the IDs are invalid
        updateNavMenuText(mNavForumId, mNavThreadId, forumItem, threadItem);

        // private messages - show 'em if you got 'em
        final MenuItem pmItem = navMenu.findItem(R.id.sidebar_pm);
        if (pmItem != null) {
            pmItem.setEnabled(mPrefs.hasPlatinum).setVisible(mPrefs.hasPlatinum);
        }

        // private messages - show 'em if you got 'em
        final MenuItem searchItem = navMenu.findItem(R.id.sidebar_search);
        if (searchItem != null) {
            searchItem.setEnabled(mPrefs.hasPlatinum).setVisible(mPrefs.hasPlatinum);
        }

        // show the unread announcement count
        final MenuItem announcements = navMenu.findItem(R.id.sidebar_announcements);
        if (announcements != null) {
            int unread = AnnouncementsManager.getInstance().getUnreadCount();
            announcements.setTitle(getString(R.string.announcements) + (unread == 0 ? "" : " (" + unread + ")"));
        }
    }


    /**
     * Update the nav menu's forum and thread titles in the background.
     *
     * This does some DB lookups and hurts the UI thread a lot, so let's async it for now
     * @param forumId       The ID of the forum to lookup
     * @param threadId      The ID of the thread to lookup
     * @param forumItem     An optional menu item to update with the forum name
     * @param threadItem    An optional menu item to update with the thread name
     */
    private void updateNavMenuText(final int forumId,
                                   final int threadId,
                                   @Nullable final MenuItem forumItem,
                                   @Nullable final MenuItem threadItem) {
        final AwfulActivity context = this;
        new AsyncTask<Void, Void, Void>() {

            private String forumName;
            private String threadName;

            @Override
            protected Void doInBackground(Void... params) {
                threadName = StringProvider.getThreadName(context, threadId);
                if(forumId >= 0){
                    forumName = StringProvider.getForumName(context, forumId);
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                if (forumItem != null) {
                    forumItem.setTitle(forumName);
                }
                if (threadItem != null) {
                    threadItem.setTitle(threadName);
                }
            }
        }.execute();
    }


    @Override
    @SuppressLint("NewApi")
    public boolean dispatchTouchEvent(MotionEvent e) {
        if (AwfulUtils.isKitKat() && mPrefs.immersionMode) {
            super.dispatchTouchEvent(e);
            return mImmersionGestureDetector.onTouchEvent(e);
        }

        return super.dispatchTouchEvent(e);
    }

    private final Handler mHideHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MESSAGE_HIDING) {
                hideSystemUi();
            } else if (msg.what == MESSAGE_VISIBLE_CHANGE_IN_PROGRESS) {
                mIgnoreFling = false;
            }
        }
    };

    /**
     * Hide the system UI.
     * @param delayMillis - delay in milliseconds before hiding.
     */
    public void delayedHide(int delayMillis) {
        mHideHandler.removeMessages(MESSAGE_HIDING);
        mHideHandler.sendEmptyMessageDelayed(MESSAGE_HIDING, delayMillis);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (AwfulUtils.isKitKat() && mPrefs.immersionMode) {
            // When the window loses focus (e.g. the action overflow is shown),
            // cancel any pending hide action. When the window gains focus,
            // hide the system UI.
            if (hasFocus) {
                delayedHide(DEFAULT_HIDE_DELAY);
            } else {
                mHideHandler.removeMessages(0);
            }
        }
    }

    @SuppressLint("NewApi")
    private void showSystemUi() {
        if (AwfulUtils.isKitKat() && mPrefs.immersionMode) {
            mDecorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    @SuppressLint("NewApi")
    private void hideSystemUi() {
        if (AwfulUtils.isKitKat() && mPrefs.immersionMode) {
            mDecorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (DEBUG) Log.e(TAG, "onNewIntent");
        setIntent(intent);
        int initialPage = parseNewIntent(intent);
        if (mViewPager != null && pagerAdapter != null && pagerAdapter.getCount() >= initialPage && initialPage >= 0) {
            mViewPager.setCurrentItem(initialPage);
        }
        if (mForumFragment != null) {
            mForumFragment.openForum(mForumId, mForumPage);
        }
        if (mThreadFragment != null) {
            if (url.isThread() || url.isPost()) {
                mThreadFragment.openThread(url);
            } else if (intent.getIntExtra(Constants.THREAD_ID, NULL_THREAD_ID) > 0) {
                if (DEBUG) Log.e(TAG, "else: "+mThreadPost);
                mThreadFragment.openThread(mThreadId, mThreadPage, mThreadPost);
            }
        }
    }

    private int parseNewIntent(Intent intent) {
        int initialPage = NULL_PAGE_ID;
        int forumId     = getIntent().getIntExtra(Constants.FORUM_ID, mForumId);
        int forumPage   = getIntent().getIntExtra(Constants.FORUM_PAGE, mForumPage);
        int threadId    = getIntent().getIntExtra(Constants.THREAD_ID, mThreadId);
        int threadPage  = getIntent().getIntExtra(Constants.THREAD_PAGE, mThreadPage);
        mThreadPost = getIntent().getStringExtra(Constants.THREAD_FRAGMENT);

        if (forumId == 2) {//workaround for old userCP ID, ugh. the old id still appears if someone created a bookmark launch shortcut prior to b23
            forumId = Constants.USERCP_ID;//should never have used 2 as a hard-coded forum-id, what a horror.
        }

        boolean displayIndex = false;
        String scheme = (getIntent().getData() == null) ? null : getIntent().getData().getScheme();
        if ("http".equals(scheme) || "https".equals(scheme)) {
            url = AwfulURL.parse(getIntent().getDataString());
            switch (url.getType()) {
                case FORUM:
                    forumId = (int) url.getId();
                    forumPage = (int) url.getPage();
                    break;
                case THREAD:
                    if (!url.isRedirect()) {
                        threadPage = (int) url.getPage();
                        threadId = (int) url.getId();
                    }
                    break;
                case POST:
                    break;
                case INDEX:
                    displayIndex = true;
                    break;
                default:
            }
        }

        setForum(forumId, forumPage);
        setThread(threadId, threadPage);

        if (displayIndex) {
            displayForumIndex();
        }
        if (intent.getIntExtra(Constants.FORUM_ID, 0) > 1 || url.isForum()) {
            initialPage = isTablet ? 0 : 1;
        } else {
            skipLoad = !isTablet;
        }
        if (intent.getIntExtra(Constants.THREAD_ID, NULL_THREAD_ID) > 0 || url.isRedirect() || url.isThread()) {
            initialPage = 2;
        }
        return initialPage;
    }

    @Override
    protected void onResume() {
        super.onResume();

        int versionCode = 0;
        try {
            versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        // check if this is the first run, and if so show the 'welcome' dialog
        if (mPrefs.alertIDShown == 0) {
            new AlertDialog.Builder(this).
                    setTitle(getString(R.string.alert_title_1))
                    .setMessage(getString(R.string.alert_message_1))
                    .setPositiveButton(getString(R.string.alert_ok), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .setNegativeButton(getString(R.string.alert_settings), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            startActivity(new Intent().setClass(ForumsIndexActivity.this, SettingsActivity.class));
                        }
                    })
                    .show();
            mPrefs.setPreference(Keys.ALERT_ID_SHOWN, 1);
        } else if (mPrefs.lastVersionSeen != versionCode) {
            Log.i(TAG, String.format("App version changed from %d to %d - showing changelog", mPrefs.lastVersionSeen, versionCode));
            ChangelogDialog.show(this);
            mPrefs.setPreference(Keys.LAST_VERSION_SEEN, versionCode);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mForumFragment != null) {
            setForum(mForumFragment.getForumId(), mForumFragment.getPage());
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(Constants.FORUM_ID, mForumId);
        outState.putInt(Constants.FORUM_PAGE, mForumPage);
        outState.putInt(Constants.THREAD_ID, mThreadId);
        outState.putInt(Constants.THREAD_PAGE, mThreadPage);
        if (mViewPager != null) {
            outState.putInt("viewPage", mViewPager.getCurrentItem());
        }
    }


    private void checkIntentExtras() {
        if (getIntent().hasExtra(Constants.SHORTCUT)) {
            if (getIntent().getBooleanExtra(Constants.SHORTCUT, false)) {
                displayForum(Constants.USERCP_ID, 1);
            }
        }
    }

    public int getThreadId() {
        return mThreadId;
    }

    public int getThreadPage() {
        return mThreadPage;
    }


    public synchronized void setForum(int forumId, int page) {
        mForumId = forumId;
        mForumPage = page;
        setNavIds(mForumId, null);
    }


    public synchronized void setThread(@Nullable Integer threadId, @Nullable Integer page) {
        if (page != null) {
            mThreadPage = page;
        }
        if (threadId != null) {
            int oldThreadId = mThreadId;
            mThreadId = threadId;
            if ((oldThreadId < 1 || threadId < 1) && threadId != oldThreadId && pagerAdapter != null) {
                pagerAdapter.notifyDataSetChanged();//notify pager adapter so it'll show/hide the thread view
            }
        }
        setNavIds(mNavForumId, mThreadId);
    }


    public class ForumPagerAdapter extends FragmentPagerAdapter implements ViewPager.OnPageChangeListener {
        public ForumPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        private AwfulFragment visible;


        @Override
        public void onPageSelected(int arg0) {
            if (DEBUG) Log.i(TAG, "onPageSelected: " + arg0);
            if (visible != null) {
                visible.onPageHidden();
            }
            AwfulFragment apf = (AwfulFragment) getItem(arg0);
            if (apf != null) {
                setActionbarTitle(apf.getTitle(), null);
                apf.onPageVisible();
                setProgress(apf.getProgressPercent());
            }
            visible = apf;
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        }

        @Override
        public void onPageScrollStateChanged(int state) {
        }

        @Override
        public Fragment getItem(int ix) {
            switch (ix) {
                case 0:
                    if (mIndexFragment == null) {
                        mIndexFragment = new ForumsIndexFragment();
                    }
                    return mIndexFragment;
                case 1:
                    if (mForumFragment == null) {
                        mForumFragment = ForumDisplayFragment.getInstance(mForumId, mForumPage, skipLoad);
                    }
                    return mForumFragment;
                case 2:
                    if (mThreadFragment == null) {
                        mThreadFragment = new ThreadDisplayFragment();
                    }
                    return mThreadFragment;
            }
            Log.e(TAG, "ERROR: asked for too many fragments in ForumPagerAdapter.getItem");
            return null;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            Object frag = super.instantiateItem(container, position);
            if (frag instanceof ForumsIndexFragment) {
                mIndexFragment = (ForumsIndexFragment) frag;
            }
            if (frag instanceof ForumDisplayFragment) {
                mForumFragment = (ForumDisplayFragment) frag;
            }
            if (frag instanceof ThreadDisplayFragment) {
                mThreadFragment = (ThreadDisplayFragment) frag;
            }
            return frag;
        }

        @Override
        public int getCount() {
            if (getThreadId() < 1) {
                return 2;
            }
            return 3;
        }

        @Override
        public int getItemPosition(Object object) {
            if (mIndexFragment != null && mIndexFragment.equals(object)) {
                return 0;
            }
            if (mForumFragment != null && mForumFragment.equals(object)) {
                return 1;
            }
            if (mThreadFragment != null && mThreadFragment.equals(object)) {
                return 2;
            }
            return super.getItemPosition(object);
        }

        @Override
        public float getPageWidth(int position) {
            if (isTablet) {
                switch (position) {
                    case 0:
                        return 0.4f;
                    case 1:
                        return 0.6f;
                    case 2:
                        return 1f;
                }
            }
            return super.getPageWidth(position);
        }
    }


    @Override
    public void setActionbarTitle(String aTitle, Object requestor) {
        if (requestor != null && mViewPager != null) {
            //This will only honor the request if the requestor is the currently active view.
            if (requestor instanceof AwfulFragment &&  isFragmentVisible((AwfulFragment)requestor)) {
                super.setActionbarTitle(aTitle, requestor);
            } else {
                if (DEBUG)
                    Log.i(TAG, "Failed setActionbarTitle: " + aTitle + " - " + requestor.toString());
            }
        } else {
            super.setActionbarTitle(aTitle, requestor);
        }
    }

    @Override
    public void onBackPressed() {
        if (mDrawerLayout.isDrawerOpen(Gravity.LEFT)) {
            mDrawerLayout.closeDrawers();
        } else {
            if (mViewPager != null && mViewPager.getCurrentItem() > 0) {
                if (!((AwfulFragment) pagerAdapter.getItem(mViewPager.getCurrentItem())).onBackPressed()) {
                    mViewPager.setCurrentItem(mViewPager.getCurrentItem() - 1);
                }
            } else {
                super.onBackPressed();
            }
        }

    }

    @Override
    public void displayForum(int id, int page) {
        Log.d(TAG, "displayForum " + id);
        setForum(id, page);
        setNavIds(id, null);
        if (mForumFragment != null) {
            mForumFragment.openForum(id, page);
            if (mViewPager != null) {
                mViewPager.setCurrentItem(pagerAdapter.getItemPosition(mForumFragment));
            }
        }
    }


    @Override
    public boolean isFragmentVisible(AwfulFragment awfulFragment) {
        if (awfulFragment != null && mViewPager != null && pagerAdapter != null) {
            if (isTablet) {
                int itemPos = pagerAdapter.getItemPosition(awfulFragment);
                return itemPos == mViewPager.getCurrentItem() || itemPos == mViewPager.getCurrentItem() + 1;
            } else {
                return pagerAdapter.getItemPosition(awfulFragment) == mViewPager.getCurrentItem();
            }
        }
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    public void displayThread(int id, int page, int forumId, int forumPg, boolean forceReload) {
        Log.d(TAG, "displayThread " + id + " " + forumId);
        if (mViewPager != null) {
            if (mThreadFragment != null) {
                if (!forceReload && getThreadId() == id && getThreadPage() == page) {
                    setNavIds(mThreadFragment.getParentForumId(), mNavThreadId);
                } else {
                    mThreadFragment.openThread(id, page, null);
                    mViewPager.getAdapter().notifyDataSetChanged();
                }
            } else {
                setThread(id, page);
            }
            mViewPager.setCurrentItem(pagerAdapter.getItemPosition(mThreadFragment));
        } else {
            super.displayThread(id, page, forumId, forumPg, forceReload);
        }
    }


    @Override
    public void displayUserCP() {
        displayForum(Constants.USERCP_ID, 1);
    }


    @Override
    public void displayForumIndex() {
        if (mViewPager != null) {
            mViewPager.setCurrentItem(0);
        }
    }


    @Override
    protected void onActivityResult(int request, int result, Intent intent) {
        if (DEBUG) Log.e(TAG, "onActivityResult: " + request + " result: " + result);
        super.onActivityResult(request, result, intent);
        if (request == Constants.LOGIN_ACTIVITY_REQUEST && result == Activity.RESULT_OK) {
            SyncManager.sync(this);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        AwfulFragment pagerItem = (AwfulFragment) pagerAdapter.getItem(mViewPager.getCurrentItem());
        if (mPrefs.volumeScroll && pagerItem != null && pagerItem.attemptVolumeScroll(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onPreferenceChange(AwfulPreferences prefs,String key) {
        super.onPreferenceChange(prefs, key);
        if (prefs.immersionMode && mDecorView == null) {
            setupImmersion();
        }
        if (mViewPager != null) {
            mViewPager.setSwipeEnabled(!prefs.lockScrolling);
        }
        setNavigationDrawer();
        if (!AwfulUtils.isTablet(this) && AwfulUtils.isAtLeast(Build.VERSION_CODES.JELLY_BEAN_MR1) && !prefs.transformer.equals("Disabled")) {
            mViewPager.setPageTransformer(true, AwfulUtils.getViewPagerTransformer());
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.e(TAG,"onConfigurationChanged()");
        boolean oldTab = isTablet;
        isTablet = AwfulUtils.isTablet(this);
        if (oldTab != isTablet && mViewPager != null) {
            if (isTablet) {
                mViewPager.setPageMargin(1);
                //TODO what color should it use here?
                mViewPager.setPageMarginDrawable(new ColorDrawable(ColorProvider.ACTION_BAR.getColor()));
            } else {
                mViewPager.setPageMargin(0);
            }

            mViewPager.setAdapter(pagerAdapter);
        }
        if(mViewPager != null){
            ((ForumPagerAdapter)mViewPager.getAdapter()).getItem(mViewPager.getCurrentItem()).onConfigurationChanged(newConfig);
        }
        mDrawerToggle.onConfigurationChanged(newConfig);
    }


    /**
     * Set the IDs that the navigation view knows about?
     * Updates the navigation menu to reflect these
     * @param forumId   The current forum
     * @param threadId
     */
    public synchronized void setNavIds(int forumId, @Nullable Integer threadId) {
        // if we only get a forumId, clear the thread one so it's not displayed
        mNavForumId = forumId;
        mNavThreadId = threadId != null ? threadId : NULL_THREAD_ID;
        updateNavigationMenu();
    }

    public void preventSwipe() {
        this.mViewPager.setSwipeEnabled(false);
    }

    public void reenableSwipe() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mViewPager.beginFakeDrag()) {
                    mViewPager.endFakeDrag();
                }
                mViewPager.setSwipeEnabled(true);
            }
        });
    }


    @Override
    protected void onStop() {
        super.onStop();
    }
}
