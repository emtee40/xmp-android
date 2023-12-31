package org.helllabs.android.xmp.browser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.helllabs.android.xmp.R;
import org.helllabs.android.xmp.preferences.Preferences;
import org.helllabs.android.xmp.util.ChangeLog;
import org.helllabs.android.xmp.util.Log;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;


public class PlaylistMenu extends ActionBarActivity {
	private static final String TAG = "PlaylistMenu";
	private static final int SETTINGS_REQUEST = 45;
	private static final int PLAYLIST_REQUEST = 46;
	private SharedPreferences prefs;
	private String mediaPath;
	private int deletePosition;
	private Context context;
	private ListView listView;

	@Override
	public void onCreate(final Bundle icicle) {		
		super.onCreate(icicle);
		context = this;
		setContentView(R.layout.playlist_menu);

		listView = (ListView)findViewById(R.id.plist_menu_list);

		listView.setOnItemClickListener(
				new AdapterView.OnItemClickListener() {
					@Override
					public void onItemClick(AdapterView<?> l, View view, int position, long id) {
						if (position == 0) {
							final Intent intent = new Intent(PlaylistMenu.this, FilelistActivity.class);
							startActivityForResult(intent, PLAYLIST_REQUEST);
						} else {
							final Intent intent = new Intent(PlaylistMenu.this, PlaylistActivity.class);
							intent.putExtra("name", PlaylistUtils.listNoSuffix()[position -1]);
							startActivityForResult(intent, PLAYLIST_REQUEST);
						}
					}
				});

		registerForContextMenu(listView);
		prefs = PreferenceManager.getDefaultSharedPreferences(this);

		if (!checkStorage()) {
			Message.fatalError(this, getString(R.string.error_storage), PlaylistMenu.this);
		}

		if (Preferences.DATA_DIR.isDirectory()) {
			updateList();
		} else {
			if (Preferences.DATA_DIR.mkdirs()) {
				try {
					final Playlist playlist = new Playlist(this, getString(R.string.empty_playlist));
					playlist.setComment(getString(R.string.empty_comment));
					playlist.commit();
				} catch (IOException e) {
					Message.error(this, getString(R.string.error_create_playlist));
				}			
			} else {
				Message.fatalError(this, getString(R.string.error_datadir), PlaylistMenu.this);
			}
		}

		final ChangeLog changeLog = new ChangeLog(this);

		changeLog.show();

	}

	private boolean checkStorage() {
		final String state = Environment.getExternalStorageState();

		if (Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			return true;
		} else {
			Log.e(TAG, "External storage state error: " + state);
			return false;
		}
	}

	private void updateList() {
		mediaPath = prefs.getString(Preferences.MEDIA_PATH, Preferences.DEFAULT_MEDIA_PATH);

		final List<PlaylistItem> list = new ArrayList<PlaylistItem>();

		list.clear();
		list.add(new PlaylistItem("File browser", "Files in " + mediaPath,
				R.drawable.browser));

		for (final String name : PlaylistUtils.listNoSuffix()) {
			list.add(new PlaylistItem(name, Playlist.readComment(this, name), R.drawable.list));
		}

		final PlaylistItemAdapter playlist = new PlaylistItemAdapter(PlaylistMenu.this,
				R.layout.playlist_item, R.id.plist_info, list, false);

		listView.setAdapter(playlist);
	}


	// Playlist context menu

	@Override
	public void onCreateContextMenu(final ContextMenu menu, final View view, final ContextMenuInfo menuInfo) {
		final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)menuInfo;
		menu.setHeaderTitle("Playlist options");

		if (info.position == 0) {					// Module list
			menu.add(Menu.NONE, 0, 0, "Change directory");
			//menu.add(Menu.NONE, 1, 1, "Add to playlist");
		} else {									// Playlists
			menu.add(Menu.NONE, 0, 0, "Rename");
			menu.add(Menu.NONE, 1, 1, "Edit comment");
			menu.add(Menu.NONE, 2, 2, "Delete playlist");
		}
	}

	@Override
	public boolean onContextItemSelected(final MenuItem item) {
		final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
		final int index = item.getItemId();

		if (info.position == 0) {		// First item of list
			if (index == 0) {			// First item of context menu
				changeDir(this);
				return true;
			}
		} else {
			switch (index) {
			case 0:						// Rename
				renameList(this, info.position -1);
				updateList();
				return true;
			case 1:						// Edit comment
				editComment(this, info.position -1);
				updateList();
				return true;
			case 2:						// Delete
				deletePosition = info.position - 1;
				Message.yesNoDialog(this, "Delete", "Are you sure to delete playlist " +
						PlaylistUtils.listNoSuffix()[deletePosition] + "?", new DialogInterface.OnClickListener() {
					public void onClick(final DialogInterface dialog, final int which) {
						if (which == DialogInterface.BUTTON_POSITIVE) {
							Playlist.delete(context, PlaylistUtils.listNoSuffix()[deletePosition]);
							updateList();
						}
					}
				});

				return true;
			default:
				break;
			}			
		}

		return true;
	}

	private void renameList(final Context context, final int index) {
		final String name = PlaylistUtils.listNoSuffix()[index];
		final InputDialog alert = new InputDialog(context);		  
		alert.setTitle("Rename playlist");
		alert.setMessage("Enter the new playlist name:");
		alert.input.setText(name);		

		alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {  
			public void onClick(final DialogInterface dialog, final int whichButton) {
				final String value = alert.input.getText().toString();
				
				if (!Playlist.rename(context, name, value)) {
					Message.error(context, getString(R.string.error_rename_playlist));
				}

				updateList();
			}  
		});  

		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {  
			public void onClick(final DialogInterface dialog, final int whichButton) {  
				// Canceled.  
			}  
		});  

		alert.show(); 
	}

	private void changeDir(final Context context) {
		final InputDialog alert = new InputDialog(context);		  
		alert.setTitle("Change directory");  
		alert.setMessage("Enter the mod directory:");
		alert.input.setText(mediaPath);

		alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {  
			public void onClick(final DialogInterface dialog, final int whichButton) {  
				final String value = alert.input.getText().toString();
				if (!value.equals(mediaPath)) {
					final SharedPreferences.Editor editor = prefs.edit();
					editor.putString(Preferences.MEDIA_PATH, value);
					editor.commit();
					updateList();
				}
			}  
		});  

		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {  
			public void onClick(final DialogInterface dialog, final int whichButton) {  
				// Canceled.  
			}  
		});  

		alert.show(); 
	}

	private void editComment(final Context context, final int index) {
		final String name = PlaylistUtils.listNoSuffix()[index];
		final InputDialog alert = new InputDialog(context);		  
		alert.setTitle("Edit comment");
		alert.setMessage("Enter the new comment for " + name + ":");  
		alert.input.setText(Playlist.readComment(context, name));

		alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {  
			public void onClick(final DialogInterface dialog, final int whichButton) {  
				final String value = alert.input.getText().toString().replace("\n", " ");				
				final File file = new File(Preferences.DATA_DIR, name + Playlist.COMMENT_SUFFIX);
				try {
					file.delete();
					file.createNewFile();
					FileUtils.writeToFile(file, value);
				} catch (IOException e) {
					Message.error(context, getString(R.string.error_edit_comment));
				}

				updateList();
			}  
		});  

		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {  
			public void onClick(final DialogInterface dialog, final int whichButton) {
				// Canceled.  
			}  
		});  

		alert.show(); 
	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		switch (requestCode) {
		case SETTINGS_REQUEST:
			if (resultCode == RESULT_OK) {
				updateList();
			}
			break;
		case PLAYLIST_REQUEST:
			updateList();
			break;
		default:
			break;
		}
	}


	// Menu

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		final MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.options_menu, menu);

		// Calling super after populating the menu is necessary here to ensure that the
		// action bar helpers have a chance to handle this event.
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch(item.getItemId()) {
		case R.id.menu_new_playlist:
			PlaylistUtils.newPlaylistDialog(this, new Runnable() {
				public void run() {
					updateList();
				}
			});
			break;
		case R.id.menu_prefs:		
			startActivityForResult(new Intent(this, Preferences.class), SETTINGS_REQUEST);
			break;
		case R.id.menu_refresh:
			updateList();
			break;
		default:
			break;
		}
		return super.onOptionsItemSelected(item);
	}
}
