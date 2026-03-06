package com.mvxgreen.ytdloader.frag

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.mvxgreen.ytdloader.MainActivity
import com.mvxgreen.ytdloader.R
import java.io.File

/**
 * Created by MVX on 3/23/21.
 * 
 * GOAL: Initialize dialog fragment with proper layout
 * 
 * GIVEN:
 * 1) Clicked menu item id
 */
class FileFragment : Fragment() {
    var viewFileIntent: Intent? = null
    var fileName: String? = null
    var fileSubtitle: String? = null
    var mediaPlayer: MediaPlayer? = null
    val MIME_MP3: String = "audio/mpeg"
    val MIME_VIDEO: String = "video/*"
    val MIME_MP4: String = "video/mp4"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val ctx = container?.getContext()
        val absFilePath = if (getArguments() != null)
            requireArguments().getString(getString(R.string.key_extra_abs_filepath), "")
        else
            ""
        val video = File(absFilePath)
        val uri = FileProvider.getUriForFile(
            MainActivity.activityCurrent!!,
            ctx?.getApplicationContext()
                ?.getPackageName() + ".provider", video
        )

        fileName = absFilePath.substring(absFilePath.lastIndexOf('/') + 1)
        fileSubtitle = absFilePath

        viewFileIntent = Intent()
        viewFileIntent!!.setAction(Intent.ACTION_VIEW)
        viewFileIntent!!.setDataAndType(uri, MIME_MP4)
        viewFileIntent!!.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        // Check menu item id; inflate proper fragment
        val rootView = inflater.inflate(R.layout.frag_file, container, false)
        fillFileLayout(rootView)
        rootView.setBackgroundColor(
            ContextCompat.getColor(rootView.getContext(), R.color.shadowInvisible)
        )

        rootView.setOnClickListener(object : View.OnClickListener {
            override fun onClick(view: View?) {
                startActivity(viewFileIntent)
            }
        })

        return rootView
    }

    /**
     * Fill elements of file fragment
     * @param root root view
     */
    private fun fillFileLayout(root: View) {
        val name = root.findViewById<TextView>(R.id.item_file_name)
        val subtitle = root.findViewById<TextView>(R.id.item_file_subtitle)


        //FloatingActionButton playPause = root.findViewById(R.id.player_play_pause);
        name.setText(this.fileName)
        subtitle.setText(this.fileSubtitle)
    }

    companion object {
        private val TAG: String = FileFragment::class.java.getCanonicalName()
    }
}
