/********************************************************************************
 * Copyright (c) 2011, Scott Ferguson
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the software nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 * 
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
 *******************************************************************************/

package com.ferg.awful;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.*;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.os.*;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.*;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.*;
import android.support.v4.app.Fragment;

import org.json.*;

import com.ferg.awful.constants.Constants;
import com.ferg.awful.preferences.AwfulPreferences;
import com.ferg.awful.preferences.ColorPickerPreference;
import com.ferg.awful.reply.Reply;
import com.ferg.awful.service.AwfulServiceConnection.ThreadListAdapter;
import com.ferg.awful.thread.AwfulPost;
import com.ferg.awful.thread.AwfulThread;
import com.ferg.awful.widget.NumberPicker;

public class ThreadDisplayFragment extends Fragment implements OnSharedPreferenceChangeListener, AwfulUpdateCallback {
    private static final String TAG = "ThreadDisplayActivity";

    private ThreadListAdapter mAdapter;
    private ParsePostQuoteTask mPostQuoteTask;
    private ParseEditPostTask mEditPostTask;

    private ImageButton mNext;
    private ImageButton mReply;
    private ImageButton mRefresh;
    private TextView mTitle;
    private ProgressDialog mDialog;
    private SharedPreferences mPrefs;

    private WebView mThreadView;

    private boolean queueDataUpdate;
    private Handler handler = new Handler();
    private class RunDataUpdate implements Runnable{
        boolean pageChange;

        public RunDataUpdate(boolean hasPageChanged){
            pageChange = hasPageChanged;
        }

        @Override
        public void run() {
            delayedDataUpdate(pageChange);
        }
    };
    private int savedPage = 0;
    private int savedPos = 0;



    // These just store values from shared preferences. This way, we only have to do redraws
    // and the like if the preferences defining drawing have actually changed since we last
    // saw them
    private int mDefaultPostBackgroundColor;
    private int mReadPostBackgroundColor;

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }
    @Override
    public View onCreateView(LayoutInflater aInflater, ViewGroup aContainer, Bundle aSavedState) {
        super.onCreateView(aInflater, aContainer, aSavedState);
        View result = aInflater.inflate(R.layout.thread_display, aContainer, true);
        
        if (!isHoneycomb()) {
            View actionbar = ((ViewStub) result.findViewById(R.id.actionbar)).inflate();

            mTitle    = (TextView) actionbar.findViewById(R.id.title);
            mNext     = (ImageButton) actionbar.findViewById(R.id.next_page);
            mReply    = (ImageButton) actionbar.findViewById(R.id.reply);
            mRefresh  = (ImageButton) actionbar.findViewById(R.id.refresh);

            mTitle.setMovementMethod(new ScrollingMovementMethod());
        }

        mThreadView = (WebView) result.findViewById(R.id.thread);
        
        return result;
    }

    @Override
    public void onActivityCreated(Bundle aSavedState) {
        super.onActivityCreated(aSavedState);
        setRetainInstance(true);
        
        PreferenceManager.setDefaultValues(getActivity(), R.xml.settings, false);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

        // TODO: Swap this to the javascript interface
        // getListView().setBackgroundColor(mPrefs.getInt("default_post_background_color", getResources().getColor(R.color.background)));
        // getListView().setCacheColorHint(mPrefs.getInt("default_post_background_color", getResources().getColor(R.color.background)));
        
        mDefaultPostBackgroundColor = mPrefs.getInt("default_post_background_color", getResources().getColor(R.color.background));

        if (!isHoneycomb()) {
            mNext.setOnClickListener(onButtonClick);
            mReply.setOnClickListener(onButtonClick);
            mRefresh.setOnClickListener(onButtonClick);
        }

        mThreadView.getSettings().setJavaScriptEnabled(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            mThreadView.getSettings().setEnableSmoothTransition(true);
            mThreadView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        
        mThreadView.setWebChromeClient(new WebChromeClient() {
            public void onConsoleMessage(String message, int lineNumber, String sourceID) {
                Log.d("Web Console", message + " -- From line " + lineNumber + " of " + sourceID);
            }
        });
    }

    private boolean isHoneycomb() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;
    }

    private void setActionbarTitle(String aTitle) {
        if (!isHoneycomb()) {
            mTitle.setText(Html.fromHtml(aTitle));
        } else {
            ((ThreadDisplayActivity) getActivity()).setThreadTitle(aTitle);
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        setActionbarTitle(mAdapter.getTitle());
    }
    
    public void setListAdapter(ListAdapter adapter){
        mAdapter = (ThreadListAdapter) adapter;
    }
    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if("default_post_background_color".equals(key)) {
            int newBackground = prefs.getInt(key, R.color.background);
            if(newBackground != mDefaultPostBackgroundColor) {
                mDefaultPostBackgroundColor = newBackground;
                Log.d(TAG, "invalidating (color)");
                // TODO: Invalidate views
            }               
        } else if("read_post_background_color".equals(key)) {
            int newReadBG = prefs.getInt(key, R.color.background_read);
            if(newReadBG != mReadPostBackgroundColor) {
                mReadPostBackgroundColor = newReadBG;
                // TODO: Invalidate views
            }
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();

        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
        cleanupTasks();
    }
        
    @Override
    public void onStop() {
        super.onStop();
        cleanupTasks();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        savedPage = mAdapter.getPage(); // saves page for orientation change.
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        cleanupTasks();
    }

    private void cleanupTasks() {
        if (mDialog != null) {
            mDialog.dismiss();
        }
        if (mEditPostTask != null) {
            mEditPostTask.cancel(true);
        }
        
        if (mPostQuoteTask != null) {
            mPostQuoteTask.cancel(true);
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        
        mPrefs.registerOnSharedPreferenceChangeListener(this);
        if(queueDataUpdate){
            dataUpdate(false);
        }
    }
    
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if(menu.size() == 0){
            inflater.inflate(R.menu.post_menu, menu);
        }
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        if(menu == null || isHoneycomb()){
            return;
        }

        MenuItem bk = menu.findItem(R.id.bookmark);

        if(bk != null){
            AwfulThread th = (AwfulThread) mAdapter.getState();
            if(th != null){
                bk.setTitle((th.isBookmarked()? getString(R.string.unbookmark):getString(R.string.bookmark)));
            }
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.next_page:
                showNextPage();
                break;
            case R.id.reply:
                displayPostReplyDialog();
                break;
            case R.id.go_back:
                mAdapter.goToPage(mAdapter.getPage()-1);
                break;
            case R.id.usercp:
                displayUserCP();
                break;
            case R.id.go_to:
                displayPagePicker();
                break;
            case R.id.refresh:
                refresh();
                break;
            case R.id.settings:
                startActivity(new Intent().setClass(getActivity(), SettingsActivity.class));
                break;
            case R.id.bookmark:
                mAdapter.toggleBookmark();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }

        return true;
    }

    private void displayUserCP() {
        startActivity(new Intent().setClass(getActivity(), UserCPActivity.class));
    }

    private void displayPagePicker() {
        final NumberPicker jumpToText = new NumberPicker(getActivity());
        jumpToText.setRange(1, mAdapter.getLastPage());
        jumpToText.setCurrent(mAdapter.getPage());
        new AlertDialog.Builder(getActivity())
            .setTitle("Jump to Page")
            .setView(jumpToText)
            .setPositiveButton("OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface aDialog, int aWhich) {
                        try {
                            int pageInt = jumpToText.getCurrent();
                            if (pageInt > 0 && pageInt <= mAdapter.getLastPage()) {
                                mAdapter.goToPage(pageInt);
                            }
                        } catch (NumberFormatException e) {
                            Toast.makeText(getActivity(),
                                R.string.invalid_page, Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Log.d(TAG, e.toString());
                        }
                    }
                })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private boolean onPostActionItemSelected(int aItem, String aPostId, String aLastReadUrl) {
        switch (aItem) {
            case ClickInterface.EDIT:
                mEditPostTask = new ParseEditPostTask();
                mEditPostTask.execute(aPostId);
                return true;
            case ClickInterface.QUOTE:
                mPostQuoteTask = new ParsePostQuoteTask();
                mPostQuoteTask.execute(aPostId);
                return true;
            case ClickInterface.LAST_READ:
                mAdapter.markLastRead(aLastReadUrl);
                return true;
        }

        return false;
    }
    
    @Override
    public void onActivityResult(int aRequestCode, int aResultCode, Intent aData) {
        // If we're here because of a post result, refresh the thread
        switch (aResultCode) {
            case PostReplyFragment.RESULT_POSTED:
                refresh();
                break;
        }
    }

    public void refresh() {
        mAdapter.refresh();
        populateThreadView();
    }

    private View.OnClickListener onButtonClick = new View.OnClickListener() {
        public void onClick(View aView) {
            switch (aView.getId()) {
                case R.id.next_page:
                    showNextPage();
                    break;
                case R.id.reply:
                    displayPostReplyDialog();
                    break;
                case R.id.refresh:
                    refresh();
                    break;
            }
        }
    };

    private void showNextPage() {
        if (mAdapter.getPage() < mAdapter.getLastPage()) {
            mAdapter.goToPage(mAdapter.getPage()+1);
        }
    }

    private void displayPostReplyDialog() {
        Bundle args = new Bundle();
        args.putString(Constants.THREAD, mAdapter.getState().getID() + "");

        displayPostReplyDialog(args);
    }

    private void displayPostReplyDialog(Bundle aArgs) {
        if (isHoneycomb()) {
            PostReplyFragment fragment = PostReplyFragment.newInstance(aArgs);
            fragment.setTargetFragment(this, 0);
            fragment.show(getActivity().getSupportFragmentManager(), "post_reply_dialog");
        } else {
            Intent postReply = new Intent().setClass(getActivity(),
                    PostReplyActivity.class);
            postReply.putExtras(aArgs);
            startActivityForResult(postReply, 0);
        }
    }

    private class ParseEditPostTask extends AsyncTask<String, Void, String> {
        private String mPostId = null;

        public void onPreExecute() {
            mDialog = ProgressDialog.show(getActivity(), "Loading", 
                "Hold on...", true);
        }

        public String doInBackground(String... aParams) {
            String result = null;

            if (!isCancelled()) {
                try {
                    mPostId = aParams[0];

                    result = Reply.getPost(mPostId);
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.i(TAG, e.toString());
                }
            }
            return result;
        }

        public void onPostExecute(String aResult) {
            if (!isCancelled()) {
                if (mDialog != null) {
                    mDialog.dismiss();
                }

                Bundle args = new Bundle();

                args.putString(Constants.THREAD, mAdapter.getState().getID()+"");
                args.putString(Constants.QUOTE, aResult);
                args.putBoolean(Constants.EDITING, true);
                args.putString(Constants.POST_ID, mPostId);

                displayPostReplyDialog(args);
            }
        }
    }

    private class ParsePostQuoteTask extends AsyncTask<String, Void, String> {
        public void onPreExecute() {
            mDialog = ProgressDialog.show(getActivity(), "Loading", "Hold on...", true);
        }

        public String doInBackground(String... aParams) {
            String result = null;

            if (!isCancelled()) {
                try {
                    result = Reply.getQuote(aParams[0]);
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.i(TAG, e.toString());
                }
            }
            return result;
        }

        public void onPostExecute(String aResult) {
            if (!isCancelled()) {
                if (mDialog != null) {
                    mDialog.dismiss();
                }

                Bundle args = new Bundle();
                args.putString(Constants.THREAD, Integer.toString(mAdapter.getState().getID()));
                args.putString(Constants.QUOTE, aResult);

                displayPostReplyDialog(args);
            }
        }
    }

    @Override
    public void dataUpdate(boolean pageChange) {
        if(!this.isResumed()){
            queueDataUpdate = true;
            return;
        }else{
            queueDataUpdate = false;
            handler.post(new RunDataUpdate(pageChange));
        }
    }

    public void delayedDataUpdate(boolean pageChange) {
        setActionbarTitle(mAdapter.getTitle());

        if (!isHoneycomb()) {
            if (mAdapter.getPage() == mAdapter.getLastPage()) {
                mNext.setVisibility(View.GONE);
            } else {
                mNext.setVisibility(View.VISIBLE);
            }
            if(mAdapter.getThreadClosed()){
                mReply.setVisibility(View.GONE);
            } else {
                mReply.setVisibility(View.VISIBLE);
            }
        }
    }

    public int getSavedPage() {
        return savedPage;
    }

    @Override
    public void loadingFailed() {
        if (!isHoneycomb()) {
            mRefresh.setVisibility(View.VISIBLE);
            mRefresh.setAnimation(null);
            mRefresh.setImageResource(android.R.drawable.ic_dialog_alert);
            mRefresh.startAnimation(mAdapter.getBlinkingAnimation());
        } else {
            getActivity().setProgressBarIndeterminateVisibility(false);
        }

        Toast.makeText(getActivity(), "Loading Failed!", Toast.LENGTH_LONG).show();
    }

    @Override
    public void loadingStarted() {
        getActivity().runOnUiThread(new Runnable() {
            public void run() {
                if (!isHoneycomb()) {
                    mRefresh.setVisibility(View.VISIBLE);
                    mRefresh.setImageResource(R.drawable.ic_menu_refresh);
                    mRefresh.startAnimation(mAdapter.getRotateAnimation());
                } else {
                    getActivity().setProgressBarIndeterminateVisibility(true);
                }
            }
        });
    }

    @Override
    public void loadingSucceeded() {
        if (!isHoneycomb()) {
            mRefresh.setAnimation(null);
            mRefresh.setVisibility(View.GONE);
        } else {
            getActivity().setProgressBarIndeterminateVisibility(false);
        }

        populateThreadView();
    }

    private void populateThreadView() {
        mThreadView.addJavascriptInterface(mAdapter.getSerializedChildren().toString(), "post_list");
        mThreadView.addJavascriptInterface(new ClickInterface(), "listener");
        mThreadView.addJavascriptInterface(getSerializedPreferences(new AwfulPreferences(getActivity())), "preferences");
        mThreadView.addJavascriptInterface(mAdapter.getLastPage(), "pageTotal");
        mThreadView.addJavascriptInterface(mAdapter.getPage(), "currentPage");

        if (isTablet()) {
            mThreadView.loadUrl("file:///android_asset/thread-tablet.html");
        } else {
            mThreadView.loadUrl("file:///android_asset/thread-phone.html");
        }
    }
    
    private boolean isTablet() {
        if (isHoneycomb()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
                Configuration config = getActivity().getResources().getConfiguration();
                return config.smallestScreenWidthDp >= 600;
            }

            return true;
        }

        return false;
    }

    private String getSerializedPreferences(final AwfulPreferences aAppPrefs) {
        JSONObject result = new JSONObject();

        try {
            result.put("username", aAppPrefs.username);
            result.put("userQuote", "#a2cd5a");
            result.put("fontSize", Integer.toString(aAppPrefs.postFontSize));
            result.put("fontColor", ColorPickerPreference.convertToARGB(aAppPrefs.postFontColor));
            result.put("fontColor2", ColorPickerPreference.convertToARGB(aAppPrefs.postFontColor2));
            result.put("backgroundColor", ColorPickerPreference.convertToARGB(aAppPrefs.postBackgroundColor));
            result.put("backgroundColor2", ColorPickerPreference.convertToARGB(aAppPrefs.postBackgroundColor2));
            result.put("readBackgroundColor", ColorPickerPreference.convertToARGB(aAppPrefs.postReadBackgroundColor));
            result.put("readBackgroundColor2", ColorPickerPreference.convertToARGB(aAppPrefs.postReadBackgroundColor2));
            result.put("OPColor", ColorPickerPreference.convertToARGB(aAppPrefs.postOPColor));
            result.put("linkQuoteColor", ColorPickerPreference.convertToARGB(aAppPrefs.postLinkQuoteColor));
            result.put("highlightUserQuote", Boolean.toString(aAppPrefs.highlightUserQuote));
            result.put("highlightUsername", Boolean.toString(aAppPrefs.highlightUsername));
            result.put("imagesEnabled", Boolean.toString(aAppPrefs.imagesEnabled));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return result.toString();
    }

    private class ClickInterface {
        public static final int QUOTE     = 0;
        public static final int LAST_READ = 1;
        public static final int EDIT      = 2;

        final CharSequence[] mEditablePostItems = {
            "Quote", 
            "Mark last read",
            "Edit Post"
        };
        final CharSequence[] mPostItems = {
            "Quote", 
            "Mark last read"
        };

        // Post ID is the item tapped
        public void onPostClick(final String aPostId, final String aLastReadUrl) {
            new AlertDialog.Builder(getActivity())
                .setTitle("Select an Action")
                .setItems(mPostItems, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface aDialog, int aItem) {
                        onPostActionItemSelected(aItem, aPostId, aLastReadUrl);
                    }
                })
                .show();
        }

        // Post ID is the item tapped
        public void onEditablePostClick(final String aPostId, final String aLastReadUrl) {
            new AlertDialog.Builder(getActivity())
                .setTitle("Select an Action")
                .setItems(mEditablePostItems, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface aDialog, int aItem) {
                        onPostActionItemSelected(aItem, aPostId, aLastReadUrl);
                    }
                })
                .show();
        }

        public void onPreviousPageClick() {
            mAdapter.goToPage(mAdapter.getPage() - 1);
        }

        public void onNextPageClick() {
            mAdapter.goToPage(mAdapter.getPage() - 1);
        }
    }
}
