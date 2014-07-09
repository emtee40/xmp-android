package org.helllabs.android.xmp.browser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.helllabs.android.xmp.R;
import org.helllabs.android.xmp.preferences.Preferences;
import org.helllabs.android.xmp.util.InfoCache;
import org.helllabs.android.xmp.util.Log;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class Playlist {
	private static final String TAG = "Playlist";
	private static final String COMMENT_SUFFIX = ".comment";
	private static final String PLAYLIST_SUFFIX = ".playlist";
	private static final String OPTIONS_PREFIX = "options_";
	private static final String SHUFFLE_MODE = "_shuffleMode";
	private static final String LOOP_MODE = "_loopMode";
	private static final boolean DEFAULT_SHUFFLE_MODE = true;
	private static final boolean DEFAULT_LOOP_MODE = false;
	
	private String mName;
	private String mComment;
	private boolean mListChanged;
	private boolean mCommentChanged;
	private boolean mShuffleMode;
	private boolean mLoopMode;
	private final List<PlaylistItem> mList;
	private final SharedPreferences mPrefs;

	@SuppressWarnings("serial")
	private static class ListFile extends File {
		public ListFile(final String name) {
			super(Preferences.DATA_DIR, name + PLAYLIST_SUFFIX);
		}
		
		public ListFile(final String name, final String suffix) {
			super(Preferences.DATA_DIR, name + PLAYLIST_SUFFIX + suffix);
		}
	}
	
	@SuppressWarnings("serial")
	private static class CommentFile extends File {
		public CommentFile(final String name) {
			super(Preferences.DATA_DIR, name + COMMENT_SUFFIX);
		}
		
		public CommentFile(final String name, final String suffix) {
			super(Preferences.DATA_DIR, name + COMMENT_SUFFIX + suffix);
		}
	}
	
	public Playlist(Context context, String name) throws IOException {
		mName = name;
		mList = new ArrayList<PlaylistItem>();
		mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
		
		final File file = new ListFile(name);
		if (file.exists()) {
			final String comment = FileUtils.readFromFile(new CommentFile(name));
				
			// read list contents
			if (readList(name)) {
				mComment = comment;
				mShuffleMode = readShuffleModePref(name);
				mLoopMode = readLoopModePref(name);
			}
		} else {
			mShuffleMode = DEFAULT_SHUFFLE_MODE;
			mLoopMode = DEFAULT_LOOP_MODE;
			mListChanged = true;
			mCommentChanged = true;
		}
	}	
	
	/**
	 * Save the current playlist.
	 * 
	 * @throws IOException
	 */
	public void commit() throws IOException {
		if (mListChanged) {
			commitList();
		}
		if (mCommentChanged) {
			commitComment();
		}
		
		boolean saveModes = false;
		if (mShuffleMode != readShuffleModePref(mName)) {
			saveModes = true;
		}
		if (mLoopMode != readLoopModePref(mName)) {
			saveModes = true;
		}
		if (saveModes) {
			final SharedPreferences.Editor editor = mPrefs.edit();
			editor.putBoolean(optionName(mName, SHUFFLE_MODE), mShuffleMode);
			editor.putBoolean(optionName(mName, LOOP_MODE), mLoopMode);
			editor.commit();
		}
	}
	
//	/**
//	 * Add a new item to the playlist.
//	 * 
//	 * @param item The item to be added
//	 */
//	public void add(final PlaylistItem item) {
//		mList.add(item);
//	}
//	
//	/**
//	 * Add new items to the playlist.
//	 * 
//	 * @param items The items to be added
//	 */
//	public void add(final PlaylistItem[] items) {
//		for (final PlaylistItem item : items) {
//			add(item);
//		}
//	}
	
	/**
	 * Remove an item from the playlist.
	 * 
	 * @param index The index of the item to be removed
	 */
	public void remove(int index) {
		mList.remove(index);
	}
	

	// Static utilities
	
	/**
	 * Rename a playlist.
	 * 
	 * @param context The context we're running in
	 * @param oldName The current name of the playlist
	 * @param newName The new name of the playlist
	 * 
	 * @return Whether the rename was successful
	 */
	public static boolean rename(final Context context, final String oldName, final String newName) {
		final File old1 = new ListFile(oldName);
		final File old2 = new CommentFile(oldName);
		final File new1 = new ListFile(newName);
		final File new2 = new CommentFile(newName);

		boolean error = false;
		
		if (!old1.renameTo(new1)) { 
			error = true;
		} else if (!old2.renameTo(new2)) {
			new1.renameTo(old1);
			error = true;
		}

		if (error) {
			return false;
		}

		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		final SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean(optionName(newName, SHUFFLE_MODE), prefs.getBoolean(optionName(oldName, SHUFFLE_MODE), DEFAULT_SHUFFLE_MODE));
		editor.putBoolean(optionName(newName, LOOP_MODE), prefs.getBoolean(optionName(oldName, LOOP_MODE), DEFAULT_LOOP_MODE));
		editor.remove(optionName(oldName, SHUFFLE_MODE));
		editor.remove(optionName(oldName, LOOP_MODE));
		editor.commit();
		
		return true;
	}
	
	/**
	 * Delete the specified playlist.
	 * 
	 * @param context The context the playlist is being created in
	 * @param name The playlist name
	 */
	public static void delete(Context context, final String name) {		
		(new ListFile(name)).delete();
		(new CommentFile(name)).delete();

		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		final SharedPreferences.Editor editor = prefs.edit();
		editor.remove(optionName(name, SHUFFLE_MODE));
		editor.remove(optionName(name, LOOP_MODE));
		editor.commit();
	}
	
	
	// Helper methods
	
	private boolean readList(final String name) {
		mList.clear();
		
		final File file = new ListFile(name);
		String line;
		int lineNum;
		
		final List<Integer> invalidList = new ArrayList<Integer>();
		
	    try {
	    	final BufferedReader reader = new BufferedReader(new FileReader(file), 512);
	    	lineNum = 0;
	    	while ((line = reader.readLine()) != null) {
	    		final String[] fields = line.split(":", 3);
	    		if (InfoCache.fileExists(fields[0])) {
	    			mList.add(new PlaylistItem(fields[2], fields[1], fields[0], R.drawable.grabber));
	    		} else {
	    			invalidList.add(lineNum);
	    		}
	    		lineNum++;
	    	}
	    	reader.close();
	    } catch (IOException e) {
	    	Log.e(TAG, "Error reading playlist " + file.getPath());
	    	return false;
	    }		
		
	    if (!invalidList.isEmpty()) {
	    	final int[] array = new int[invalidList.size()];
	    	final Iterator<Integer> iterator = invalidList.iterator();
	    	for (int i = 0; i < array.length; i++) {
	    		array[i] = iterator.next().intValue();
	    	}
	    	
			try {
				FileUtils.removeLineFromFile(file, array);
			} catch (FileNotFoundException e) {
				Log.e(TAG, "Playlist file " + file.getPath() + " not found");
			} catch (IOException e) {
				Log.e(TAG, "I/O error removing invalid lines from " + file.getPath());
			}
		}
	    
	    return true;
	}
	
	private final void writeList(final String name) {		
		final File file = new ListFile(name,  ".new");
		Log.i(TAG, "Write playlist " + name);
		file.delete();
		
		try {
			final BufferedWriter out = new BufferedWriter(new FileWriter(file), 512);
			for (final PlaylistItem info : mList) {
				out.write(String.format("%s:%s:%s\n", info.filename, info.comment, info.name));
			}
			out.close();
			
			final File oldFile = new ListFile(name);
			oldFile.delete();
			file.renameTo(oldFile);
		} catch (IOException e) {
			Log.e(TAG, "Error writing playlist file " + file.getPath());
		}
	}
	
	private final void writeComment(final String name) {
		final File file = new CommentFile(name,  ".new");
		file.delete();//	/**
//		 * Add a new item to the playlist.
//		 * 
//		 * @param item The item to be added
//		 */
//		public void add(final PlaylistItem item) {
//			mList.add(item);
//		}
	//	
//		/**
//		 * Add new items to the playlist.
//		 * 
//		 * @param items The items to be added
//		 */
//		public void add(final PlaylistItem[] items) {
//			for (final PlaylistItem item : items) {
//				add(item);
//			}
//		}

		
		try {
			FileUtils.writeToFile(file, mComment);
			final File oldFile = new CommentFile(name);
			oldFile.delete();
			file.renameTo(oldFile);
		} catch (IOException e) {
			Log.e(TAG, "Error writing comment file " + file.getPath());
		}
	}
	
	private void commitList() throws IOException {
		writeList(mName);
		mListChanged = false;
	}
	
	private void commitComment() throws IOException {
		writeComment(mName);
		final File file = new CommentFile(mName);
		file.createNewFile();
		FileUtils.writeToFile(file, mComment);
		mCommentChanged = false;
	}
		
	private static String optionName(final String name, final String option) {
		return OPTIONS_PREFIX + name + option;
	}
	
	private boolean readShuffleModePref(final String name) {
		return mPrefs.getBoolean(optionName(name, SHUFFLE_MODE), DEFAULT_SHUFFLE_MODE);
	}
	
	private boolean readLoopModePref(final String name) {
		return mPrefs.getBoolean(optionName(name, LOOP_MODE), DEFAULT_LOOP_MODE);
	}
	

	// Accessors
	
	public String getName() {
		return mName;
	}
	
	public String getComment() {
		return mComment;
	}
	
	public List<PlaylistItem> getList() {
		return mList;
	}
	
	public boolean getLoopMode() {
		return mLoopMode;
	}
	
	public boolean getShuffleMode() {
		return mShuffleMode;
	}
	
	public void setComment(String comment) {
		mComment = comment;
	}
	
	public void setLoopMode(final boolean loopMode) {
		mLoopMode = loopMode;
	}
	
	public void setShuffleMode(final boolean shuffleMode) {
		mShuffleMode = shuffleMode;
	}
}