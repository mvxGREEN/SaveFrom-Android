package com.mvxgreen.ytdloader.frag;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.mvxgreen.ytdloader.R;

import java.io.File;


/**
 * Created by MVX on 3/23/21.
 *
 * GOAL: Initialize dialog fragment with proper layout
 *
 * GIVEN:
 *  1) Clicked menu item id
 */

public class FileFragment extends Fragment {
    private static final String TAG = FileFragment.class.getCanonicalName();

    Intent viewFileIntent;
    String fileName, fileSubtitle;
    MediaPlayer mediaPlayer;
    final String MIME_MP3 = "audio/mpeg",
            MIME_VIDEO = "video/*",
            MIME_MP4 = "video/mp4";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        Context ctx = container.getContext();
        String absFilePath = getArguments() != null ?
                getArguments().getString(getString(R.string.key_extra_abs_filepath), "")
                : "";
        File video = new File(absFilePath);
        Uri uri = FileProvider.getUriForFile(
                container.getContext(),
                ctx.getApplicationContext()
                        .getPackageName() + ".provider", video);

        fileName = absFilePath.substring(absFilePath.lastIndexOf('/')+1);
        fileSubtitle = absFilePath;

        viewFileIntent = new Intent();
        viewFileIntent.setAction(Intent.ACTION_VIEW);
        viewFileIntent.setDataAndType(uri, MIME_MP4);
        viewFileIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        // Check menu item id; inflate proper fragment
        View rootView = inflater.inflate(R.layout.frag_file, container, false);
        fillFileLayout(rootView);
        rootView.setBackgroundColor(
                ContextCompat.getColor(rootView.getContext(), R.color.shadowInvisible)
        );

        rootView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(viewFileIntent);
            }
        });

        return rootView;
    }

    /**
     * Fill elements of file fragment
     * @param root root view
     */
    private void fillFileLayout(View root) {
        TextView name = root.findViewById(R.id.item_file_name);
        TextView subtitle = root.findViewById(R.id.item_file_subtitle);
        //FloatingActionButton playPause = root.findViewById(R.id.player_play_pause);


        name.setText(this.fileName);
        subtitle.setText(this.fileSubtitle);


    }
}
