package com.ferg.awfulapp.widget;

/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.ArrayList;
import java.util.Iterator;

import com.ferg.awfulapp.widget.AwfulFragmentPagerAdapter.AwfulPagerFragment;

import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

/**
 * Implementation of {@link android.support.v4.view.PagerAdapter} that
 * represents each page as a {@link Fragment} that is persistently
 * kept in the fragment manager as long as the user can return to the page.
 *
 * <p>This version of the pager is best for use when there are a handful of
 * typically more static fragments to be paged through, such as a set of tabs.
 * The fragment of each page the user visits will be kept in memory, though its
 * view hierarchy may be destroyed when not visible.  This can result in using
 * a significant amount of memory since fragment instances can hold on to an
 * arbitrary amount of state.  For larger sets of pages, consider
 * {@link FragmentStatePagerAdapter}.
 *
 * <p>When using FragmentPagerAdapter the host ViewPager must have a
 * valid ID set.</p>
 *
 * <p>Subclasses only need to implement {@link #getItem(int)}
 * and {@link #getCount()} to have a working adapter.
 *
 * <p>Here is an example implementation of a pager containing fragments of
 * lists:
 *
 * {@sample development/samples/Support4Demos/src/com/example/android/supportv4/app/FragmentPagerSupport.java
 *      complete}
 *
 * <p>The <code>R.layout.fragment_pager</code> resource of the top-level fragment is:
 *
 * {@sample development/samples/Support4Demos/res/layout/fragment_pager.xml
 *      complete}
 *
 * <p>The <code>R.layout.fragment_pager_list</code> resource containing each
 * individual fragment's layout is:
 *
 * {@sample development/samples/Support4Demos/res/layout/fragment_pager_list.xml
 *      complete}
 */
public abstract class AwfulFragmentPagerAdapter extends AwfulPagerAdapter implements Iterable<AwfulFragmentPagerAdapter.AwfulPagerFragment> {

	private static final String TAG = "FragmentPagerAdapter";
    private static final boolean DEBUG = false;

    private final FragmentManager mFragmentManager;
    private FragmentTransaction mCurTransaction = null;
    private Fragment mCurrentPrimaryItem = null;
    
    private ArrayList<AwfulPagerFragment> fragList;

    public AwfulFragmentPagerAdapter(FragmentManager fm) {
        mFragmentManager = fm;
		fragList = new ArrayList<AwfulPagerFragment>();
    }
    

	public void addFragment(AwfulPagerFragment frag){
		if(!fragList.contains(frag)){
			fragList.add(frag);
			notifyDataSetChanged();
		}
	}
	
	public void deleteFragment(AwfulPagerFragment frag){
		fragList.remove(frag);
		notifyDataSetChanged();
	}

	public AwfulPagerFragment deletePage(int x) {
		AwfulPagerFragment tmp = fragList.remove(x);
		notifyDataSetChanged();
		return tmp;
	}
	
    @Override
	public Iterator<AwfulPagerFragment> iterator() {
		return fragList.iterator();
	}

    /**
     * Return the Fragment associated with a specified position.
     */
	public AwfulPagerFragment getItem(int position) {
		AwfulPagerFragment frag = fragList.get(position);
		if(DEBUG) Log.e(TAG,"getItem "+position+" - "+frag.toString());
		return frag;
	}
	
    public interface AwfulPagerFragment{
    	/**
    	 * This event is called when the user presses the back button. Return true to consume this back-button event and prevent the activity from finishing.
    	 * 
    	 * @return true if you are consuming this back-button event.
    	 */
    	public boolean onBackPressed();
    	public void onPageVisible();
    	public void onPageHidden();
    	public String getTitle();
    	/**
    	 * Check to see if this point is horizontally scrollable, and by X distance.
    	 * @param dx Distance on X axis to scroll.
    	 * @param y Position on Y axis where the event was triggered.
    	 * @return True to allow horizontal scrolling, false to allow viewpager to take over.
    	 */
    	public boolean canScrollX(int dx, int y);
    	public int getProgressPercent();
    	public void fragmentMessage(String type, String contents);
    }

    @Override
    public void startUpdate(ViewGroup container) {
    }
    
    protected abstract Fragment resolveConflict(int position, Fragment oldFrag, Fragment newFrag);

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        if (mCurTransaction == null) {
            mCurTransaction = mFragmentManager.beginTransaction();
        }

        // Do we already have this fragment?
        String name = makeFragmentName(container.getId(), position);
        Fragment existingFragment = mFragmentManager.findFragmentByTag(name);
        Fragment listFragment = (Fragment) getItem(position);
        Fragment fragment;
        if(existingFragment != null && listFragment != null && existingFragment != listFragment){
        	fragment = resolveConflict(position, existingFragment, listFragment);
        }else if(existingFragment != null){
        	fragment = existingFragment;
        }else{
        	fragment = listFragment;
        }
        if (existingFragment == fragment) {
            if (DEBUG) Log.v(TAG, "Attaching item #" + position + ": f=" + fragment);
            mCurTransaction.attach(fragment);
        } else {
            if (DEBUG) Log.v(TAG, "Adding item #" + position + ": f=" + fragment);
            mCurTransaction.add(container.getId(), fragment,
                    makeFragmentName(container.getId(), position));
        }
        if (fragment != mCurrentPrimaryItem) {
            fragment.setMenuVisibility(false);
            fragment.setUserVisibleHint(false);
        }

        return fragment;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        if (mCurTransaction == null) {
            mCurTransaction = mFragmentManager.beginTransaction();
        }
        if (DEBUG) Log.v(TAG, "Detaching item #" + position + ": f=" + object
                + " v=" + ((Fragment)object).getView());
        mCurTransaction.detach((Fragment)object);
    }

    @Override
    public void setPrimaryItem(ViewGroup container, int position, Object object) {
        Fragment fragment = (Fragment)object;
        if (fragment != mCurrentPrimaryItem) {
            if (mCurrentPrimaryItem != null) {
                mCurrentPrimaryItem.setMenuVisibility(false);
                mCurrentPrimaryItem.setUserVisibleHint(false);
            }
            if (fragment != null) {
                fragment.setMenuVisibility(true);
                fragment.setUserVisibleHint(true);
            }
            mCurrentPrimaryItem = fragment;
        }
    }

    @Override
    public void finishUpdate(ViewGroup container) {
        if (mCurTransaction != null) {
            mCurTransaction.commitAllowingStateLoss();
            mCurTransaction = null;
            mFragmentManager.executePendingTransactions();
        }
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return ((Fragment)object).getView() == view;
    }

    @Override
    public Parcelable saveState() {
        return null;
    }

    @Override
    public void restoreState(Parcelable state, ClassLoader loader) {
    }

    private static String makeFragmentName(int viewId, int index) {
        return "android:switcher:" + viewId + ":" + index;
    }
    
	@Override
	public CharSequence getPageTitle(int position) {
		return getItem(position).getTitle();
	}

	@Override
	public int getItemPosition(Object object) {
		int pos = fragList.indexOf(object);
		if(pos < 0){
			return AwfulFragmentPagerAdapter.POSITION_NONE;
		}else{
			return pos;
		}
	}

	@Override
	public int getCount() {
		return fragList.size();
	}
}