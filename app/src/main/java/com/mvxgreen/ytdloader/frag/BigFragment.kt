package com.mvxgreen.ytdloader.frag;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.mvxgreen.ytdloader.R;


/**
 * Created by MVX on 7/6/2017.
 *
 * GOAL: Initialize dialog fragment with proper layout
 *
 * GIVEN:
 *  1) Clicked menu item id
 */

public class BigFragment extends Fragment {
    private static final String TAG = BigFragment.class.getCanonicalName();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView;
        String title = getArguments() != null ?
                getArguments().getString(getString(R.string.key_extra_menu_item_title), "") : "";

        // Check menu item title; inflate proper fragment
        if (title.equals("Enable Notifications")) {
            rootView = inflater.inflate(R.layout.frag_justify_notifications, container, false);
        } else if (title.equals("InFlyer")) {
            rootView = inflater.inflate(R.layout.frag_inflyer, container, false);
        } else {
            rootView = inflater.inflate(R.layout.frag_upgrade, container, false);
        }

        return rootView;
    }

}
