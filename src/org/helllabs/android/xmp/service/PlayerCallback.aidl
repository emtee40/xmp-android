package org.helllabs.android.xmp.service;

interface PlayerCallback {
	void newModCallback(String name, in String[] instruments);
	void endModCallback();
	void endPlayCallback();
	void pauseCallback();
	void newSequenceCallback();
}