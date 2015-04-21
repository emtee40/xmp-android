package org.helllabs.android.xmp.browser;

import java.io.IOException;
import java.util.List;

import org.helllabs.android.xmp.R;
import org.helllabs.android.xmp.browser.playlist.Playlist;
import org.helllabs.android.xmp.browser.playlist.PlaylistAdapter;
import org.helllabs.android.xmp.browser.playlist.PlaylistItem;
import org.helllabs.android.xmp.preferences.Preferences;
import org.helllabs.android.xmp.util.Log;
import org.helllabs.android.xmp.util.Message;

import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.TextView;

import com.commonsware.cwac.tlv.TouchListView;

public class PlaylistActivity extends BasePlaylistActivity {
	private static final String TAG = "PlaylistActivity";
	private Playlist playlist;

	// List reorder

	/*private final TouchListView.DropListener onDrop = new TouchListView.DropListener() {
		@Override
		public void drop(final int from, final int to) {
			final PlaylistItem item = playlistAdapter.getItem(from);
			playlistAdapter.remove(item);
			playlistAdapter.insert(item, to);
			playlist.setListChanged(true);
		}
	};

	private final TouchListView.RemoveListener onRemove = new TouchListView.RemoveListener() {
		@Override
		public void remove(final int which) {
			playlistAdapter.remove(playlistAdapter.getItem(which));
		}
	};	*/

	@Override
	public void onCreate(final Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.playlist);

		final Bundle extras = getIntent().getExtras();
		if (extras == null) {
			return;
		}

		setTitle(R.string.browser_playlist_title);

		final RecyclerView recyclerView = (RecyclerView)findViewById(R.id.plist_list);

        final LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        recyclerView.setLayoutManager(layoutManager);

		super.setOnItemClickListener(recyclerView);

		//listView.setDropListener(onDrop);
		//listView.setRemoveListener(onRemove);

		final String name = extras.getString("name");
		final boolean useFilename = mPrefs.getBoolean(Preferences.USE_FILENAME, false);

		try {
			playlist = new Playlist(this, name);
			playlistAdapter = new PlaylistAdapter(this, R.layout.song_item, R.id.info, playlist.getList(), useFilename);
			recyclerView.setAdapter(playlistAdapter);
		} catch (IOException e) {
			Log.e(TAG, "Can't read playlist " + name);
		}

		final TextView curListName = (TextView)findViewById(R.id.current_list_name);
		final TextView curListDesc = (TextView)findViewById(R.id.current_list_description);

		curListName.setText(name);
		curListDesc.setText(playlist.getComment());
		registerForContextMenu(recyclerView);

		setupButtons();
	}

	@Override
	public void onPause() {
		super.onPause();

		try {
			playlist.commit();
		} catch (IOException e) {
			Message.toast(this, getString(R.string.error_write_to_playlist));
		}

	}

	@Override
	protected void setShuffleMode(final boolean shuffleMode) {
		playlist.setShuffleMode(shuffleMode);
	}

	@Override
	protected void setLoopMode(final boolean loopMode) {
		playlist.setLoopMode(loopMode);
	}

	@Override
	protected boolean isShuffleMode() {
		return playlist.isShuffleMode();
	}

	@Override
	protected boolean isLoopMode() {
		return playlist.isLoopMode();
	}

	@Override
	protected List<String> getAllFiles() {
		return playlistAdapter.getFilenameList();
	}

	@Override
	public void update() {
		playlistAdapter.notifyDataSetChanged();
	}

	// Playlist context menu

	@Override
	public void onCreateContextMenu(final ContextMenu menu, final View view, final ContextMenuInfo menuInfo) {

		final int mode = Integer.parseInt(mPrefs.getString(Preferences.PLAYLIST_MODE, "1"));

		menu.setHeaderTitle("Edit playlist");
		menu.add(Menu.NONE, 0, 0, "Remove from playlist");
		menu.add(Menu.NONE, 1, 1, "Add to play queue");
		menu.add(Menu.NONE, 2, 2, "Add all to play queue");
		if (mode != 2) {
			menu.add(Menu.NONE, 3, 3, "Play this module");
		}
		if (mode != 1) {
			menu.add(Menu.NONE, 4, 4, "Play all starting here");
		}
	}

	@Override
	public boolean onContextItemSelected(final MenuItem item) {
		final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
		final int itemId = item.getItemId();

		switch (itemId) {
		case 0:										// Remove from playlist
			playlist.remove(info.position);
			try {
				playlist.commit();
			} catch (IOException e) {
				Message.toast(this, getString(R.string.error_write_to_playlist));
			}
			update();
			break;
		case 1:										// Add to play queue
			addToQueue(playlistAdapter.getFilename(info.position));
			break;
		case 2:										// Add all to play queue
			addToQueue(playlistAdapter.getFilenameList());
			break;
		case 3:										// Play only this module
			playModule(playlistAdapter.getFilename(info.position));
			break;
		case 4:										// Play all starting here
			playModule(playlistAdapter.getFilenameList(), info.position);
			break;
		}

		return true;
	}
}