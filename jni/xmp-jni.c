/* Simple and ugly interface adaptor for jni
 * If you need a JNI interface for libxmp, check the Libxmp Java API
 * at https://github.com/cmatsuoka/libxmp-java
 */

#include <stdio.h>
#include <stdlib.h>
#include <jni.h>
#include "xmp.h"

/* #include <android/log.h> */

static xmp_context ctx = NULL;
static struct xmp_module_info mi;
static struct xmp_frame_info fi;

static int _playing = 0;
static int _cur_vol[XMP_MAX_CHANNELS];
static int _hold_vol[XMP_MAX_CHANNELS];
static int _pan[XMP_MAX_CHANNELS];
static int _ins[XMP_MAX_CHANNELS];
static int _key[XMP_MAX_CHANNELS];
static int _period[XMP_MAX_CHANNELS];
static int _finalvol[XMP_MAX_CHANNELS];
static int _last_key[XMP_MAX_CHANNELS];
static int _pos[XMP_MAX_CHANNELS];
static int _decay = 4;
static int _sequence;
static int _mod_is_loaded;

#define MAX_BUFFER_SIZE 256
static char _buffer[MAX_BUFFER_SIZE];


/* For ModList */
JNIEXPORT void JNICALL
Java_org_helllabs_android_xmp_Xmp_init(JNIEnv *env, jobject obj)
{
	if (ctx != NULL)
		return;

	ctx = xmp_create_context();
}

JNIEXPORT jint JNICALL
Java_org_helllabs_android_xmp_Xmp_deinit(JNIEnv *env, jobject obj)
{
	/* xmp_free_context(ctx); */
	return 0;
}

JNIEXPORT jint JNICALL
Java_org_helllabs_android_xmp_Xmp_loadModule(JNIEnv *env, jobject obj, jstring name)
{
	const char *filename;
	int res;

	filename = (*env)->GetStringUTFChars(env, name, NULL);
	/* __android_log_print(ANDROID_LOG_DEBUG, "libxmp", "%s", filename); */
	res = xmp_load_module(ctx, (char *)filename);
	(*env)->ReleaseStringUTFChars(env, name, filename);

	xmp_get_module_info(ctx, &mi);

	memset(_pos, 0, XMP_MAX_CHANNELS * sizeof (int));
	_sequence = 0;
	_mod_is_loaded = 1;

	return res;
}

JNIEXPORT jboolean JNICALL
Java_org_helllabs_android_xmp_Xmp_testModule(JNIEnv *env, jobject obj, jstring name, jobject info)
{
	const char *filename;
	int i, res;
	struct xmp_test_info ti;

	filename = (*env)->GetStringUTFChars(env, name, NULL);
	/* __android_log_print(ANDROID_LOG_DEBUG, "libxmp", "%s", filename); */
	res = xmp_test_module((char *)filename, &ti);

	/* If the module title is empty, use the file basename */
	for (i = strlen(ti.name) - 1; i >= 0; i--) {
		if (ti.name[i] == ' ') {
			ti.name[i] = 0;
		} else {
			break;
		}
	}
	if (strlen(ti.name) == 0) {
		const char *x = strrchr(filename, '/');
		if (x == NULL) {
			x = filename;
		}
		strncpy(ti.name, x + 1, XMP_NAME_SIZE);
	}

	(*env)->ReleaseStringUTFChars(env, name, filename);

	if (res == 0) {
		if (info != NULL) {
			jclass modInfoClass = (*env)->FindClass(env,
	                        	"org/helllabs/android/xmp/util/ModInfo");
			jfieldID field;
	
			if (modInfoClass == NULL)
				return JNI_FALSE;
			
			field = (*env)->GetFieldID(env, modInfoClass, "name",
	                        	"Ljava/lang/String;");
			if (field == NULL)
				return JNI_FALSE;
			(*env)->SetObjectField(env, info, field,
					(*env)->NewStringUTF(env, ti.name));
	
			field = (*env)->GetFieldID(env, modInfoClass, "type",
	                        	"Ljava/lang/String;");
			if (field == NULL)
				return JNI_FALSE;
			(*env)->SetObjectField(env, info, field,
					(*env)->NewStringUTF(env, ti.type));
		}

		return JNI_TRUE;
	}

	return JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_org_helllabs_android_xmp_Xmp_releaseModule(JNIEnv *env, jobject obj)
{
	_mod_is_loaded = 0;
	xmp_release_module(ctx);
	return 0;
}

JNIEXPORT jint JNICALL
Java_org_helllabs_android_xmp_Xmp_startPlayer(JNIEnv *env, jobject obj, jint start, jint rate, jint flags)
{
	int i;

	for (i = 0; i < XMP_MAX_CHANNELS; i++) {
		_key[i] = -1;
		_last_key[i] = -1;
	}

	_playing = 1;
	return xmp_start_player(ctx, rate, flags);
}

JNIEXPORT jint JNICALL
Java_org_helllabs_android_xmp_Xmp_endPlayer(JNIEnv *env, jobject obj)
{
	_playing = 0;
	xmp_end_player(ctx);
	return 0;
}

JNIEXPORT jint JNICALL
Java_org_helllabs_android_xmp_Xmp_playFrame(JNIEnv *env, jobject obj)
{
	int i, ret;

	ret = xmp_play_frame(ctx);
	xmp_get_frame_info(ctx, &fi);

	return ret;
}

JNIEXPORT jint JNICALL
Java_org_helllabs_android_xmp_Xmp_getBuffer(JNIEnv *env, jobject obj, jshortArray buffer)
{
	(*env)->SetShortArrayRegion(env, buffer, 0, fi.buffer_size, fi.buffer);
	return fi.buffer_size / 2;
}

JNIEXPORT jint JNICALL
Java_org_helllabs_android_xmp_Xmp_nextPosition(JNIEnv *env, jobject obj)
{
	return xmp_next_position(ctx);
}

JNIEXPORT jint JNICALL
Java_org_helllabs_android_xmp_Xmp_prevPosition(JNIEnv *env, jobject obj)
{
	return xmp_prev_position(ctx);
}

JNIEXPORT jint JNICALL
Java_org_helllabs_android_xmp_Xmp_setPosition(JNIEnv *env, jobject obj, jint n)
{
	return xmp_set_position(ctx, n);
}

JNIEXPORT jint JNICALL
Java_org_helllabs_android_xmp_Xmp_stopModule(JNIEnv *env, jobject obj)
{
	xmp_stop_module(ctx);
	return 0;
}

JNIEXPORT jint JNICALL
Java_org_helllabs_android_xmp_Xmp_restartModule(JNIEnv *env, jobject obj)
{
	xmp_restart_module(ctx);
	return 0;
}

JNIEXPORT jint JNICALL
Java_org_helllabs_android_xmp_Xmp_seek(JNIEnv *env, jobject obj, jint time)
{
	int ret = xmp_seek_time(ctx, time);
	fi.time = time;
	return ret;
}

JNIEXPORT jint JNICALL
Java_org_helllabs_android_xmp_Xmp_time(JNIEnv *env, jobject obj)
{
	return _playing ? fi.time : -1;
}

JNIEXPORT jint JNICALL
Java_org_helllabs_android_xmp_Xmp_mute(JNIEnv *env, jobject obj, jint chn, jint status)
{
	return xmp_channel_mute(ctx, chn, status);
}

JNIEXPORT void JNICALL
Java_org_helllabs_android_xmp_Xmp_getInfo(JNIEnv *env, jobject obj, jintArray values)
{
	int v[7];

	v[0] = fi.pos;
	v[1] = fi.pattern;
	v[2] = fi.row;
	v[3] = fi.num_rows;
	v[4] = fi.frame;
	v[5] = fi.speed;
	v[6] = fi.bpm;

	(*env)->SetIntArrayRegion(env, values, 0, 7, v);
}

JNIEXPORT jint JNICALL
Java_org_helllabs_android_xmp_Xmp_setPlayer(JNIEnv *env, jobject obj, jint parm, jint val)
{
	return xmp_set_player(ctx, parm, val);
}

JNIEXPORT jint JNICALL
Java_org_helllabs_android_xmp_Xmp_getPlaySpeed(JNIEnv *env, jobject obj)
{
	return fi.speed;
}

JNIEXPORT jint JNICALL
Java_org_helllabs_android_xmp_Xmp_getPlayBpm(JNIEnv *env, jobject obj)
{
	return fi.bpm;
}

JNIEXPORT jint JNICALL
Java_org_helllabs_android_xmp_Xmp_getPlayPos(JNIEnv *env, jobject obj)
{
	return fi.pos;
}

JNIEXPORT jint JNICALL
Java_org_helllabs_android_xmp_Xmp_getPlayPat(JNIEnv *env, jobject obj)
{
	return fi.pattern;
}

JNIEXPORT jint JNICALL
Java_org_helllabs_android_xmp_Xmp_getLoopCount(JNIEnv *env, jobject obj)
{
	return fi.loop_count;
}

JNIEXPORT void JNICALL
Java_org_helllabs_android_xmp_Xmp_getModVars(JNIEnv *env, jobject obj, jintArray vars)
{
	int v[8];

	if (!_mod_is_loaded)
		return;

	v[0] = mi.seq_data[_sequence].duration;
	v[1] = mi.mod->len;
	v[2] = mi.mod->pat;
	v[3] = mi.mod->chn;
	v[4] = mi.mod->ins;
	v[5] = mi.mod->smp;
	v[6] = mi.num_sequences;
	v[7] = _sequence;

	(*env)->SetIntArrayRegion(env, vars, 0, 8, v);
}

JNIEXPORT jstring JNICALL
Java_org_helllabs_android_xmp_Xmp_getVersion(JNIEnv *env, jobject obj)
{
	char buf[20];
	snprintf(buf, 20, "%d.%d.%d",
		(xmp_vercode & 0x00ff0000) >> 16,
		(xmp_vercode & 0x0000ff00) >> 8,
		(xmp_vercode & 0x000000ff));

	return (*env)->NewStringUTF(env, buf);
}

JNIEXPORT jobjectArray JNICALL
Java_org_helllabs_android_xmp_Xmp_getFormats(JNIEnv *env, jobject obj)
{
	jstring s;
	jclass stringClass;
	jobjectArray stringArray;
	int i, num;
	char **list;
	char buf[80];

	list = xmp_get_format_list();
	for (num = 0; list[num] != NULL; num++);

	stringClass = (*env)->FindClass(env,"java/lang/String");
	if (stringClass == NULL)
		return NULL;

	stringArray = (*env)->NewObjectArray(env, num, stringClass, NULL);
	if (stringArray == NULL)
		return NULL;

	for (i = 0; i < num; i++) {
		s = (*env)->NewStringUTF(env, list[i]);
		(*env)->SetObjectArrayElement(env, stringArray, i, s);
		(*env)->DeleteLocalRef(env, s);
	}

	return stringArray;
}

JNIEXPORT jstring JNICALL
Java_org_helllabs_android_xmp_Xmp_getModName(JNIEnv *env, jobject obj)
{
	char *s = _mod_is_loaded ? mi.mod->name : "";
	return (*env)->NewStringUTF(env, s);
}

JNIEXPORT jstring JNICALL
Java_org_helllabs_android_xmp_Xmp_getModType(JNIEnv *env, jobject obj)
{
	char *s = _mod_is_loaded ? mi.mod->type : "";
	return (*env)->NewStringUTF(env, s);
}

JNIEXPORT jobjectArray JNICALL
Java_org_helllabs_android_xmp_Xmp_getInstruments(JNIEnv *env, jobject obj)
{
	jstring s;
	jclass stringClass;
	jobjectArray stringArray;
	int i;
	char buf[80];
	int ins;

	if (!_mod_is_loaded)
		return NULL;

	stringClass = (*env)->FindClass(env,"java/lang/String");
	if (stringClass == NULL)
		return NULL;

	stringArray = (*env)->NewObjectArray(env, mi.mod->ins, stringClass, NULL);
	if (stringArray == NULL)
		return NULL;

	for (i = 0; i < mi.mod->ins; i++) {
		snprintf(buf, 80, "%02X %s", i + 1, mi.mod->xxi[i].name);
		s = (*env)->NewStringUTF(env, buf);
		(*env)->SetObjectArrayElement(env, stringArray, i, s);
		(*env)->DeleteLocalRef(env, s);
	}

	return stringArray;
}

static struct xmp_subinstrument *get_subinstrument(int ins, int key)
{
	if (ins >= 0 && ins < mi.mod->ins) {
		if (mi.mod->xxi[ins].map[key].ins != 0xff) {
			int mapped = mi.mod->xxi[ins].map[key].ins;
			return &mi.mod->xxi[ins].sub[mapped];
		}
	}

	return NULL;
}

JNIEXPORT void JNICALL
Java_org_helllabs_android_xmp_Xmp_getChannelData(JNIEnv *env, jobject obj, jintArray vol, jintArray finalvol, jintArray pan, jintArray ins, jintArray key, jintArray period)
{
	struct xmp_subinstrument *sub;
	int chn = mi.mod->chn;
	int i;

	if (!_mod_is_loaded)
		return;

	for (i = 0; i < chn; i++) {
                struct xmp_channel_info *ci = &fi.channel_info[i];

		if (ci->event.vol > 0) {
			_hold_vol[i] = ci->event.vol * 0x40 / mi.vol_base;
		}

		_cur_vol[i] -= _decay;
		if (_cur_vol[i] < 0) {
			_cur_vol[i] = 0;
		}

		if (ci->event.note > 0 && ci->event.note <= 0x80) {
			_key[i] = ci->event.note - 1;
			_last_key[i] = _key[i];
			sub = get_subinstrument(ci->instrument, _key[i]);
			if (sub != NULL) {
				_cur_vol[i] = sub->vol * 0x40 / mi.vol_base;
			}
		} else {
			_key[i] = -1;
		}

		if (ci->event.vol > 0) {
			_key[i] = _last_key[i];
			_cur_vol[i] = ci->event.vol * 0x40 / mi.vol_base;
		}

		_ins[i] = (signed char)ci->instrument;
		_finalvol[i] = ci->volume;
		_pan[i] = ci->pan;
		_period[i] = ci->period >> 8;
	}

	(*env)->SetIntArrayRegion(env, vol, 0, chn, _cur_vol);
	(*env)->SetIntArrayRegion(env, finalvol, 0, chn, _finalvol);
	(*env)->SetIntArrayRegion(env, pan, 0, chn, _pan);
	(*env)->SetIntArrayRegion(env, ins, 0, chn, _ins);
	(*env)->SetIntArrayRegion(env, key, 0, chn, _key);
	(*env)->SetIntArrayRegion(env, period, 0, chn, _period);
}

JNIEXPORT void JNICALL
Java_org_helllabs_android_xmp_Xmp_getPatternRow(JNIEnv *env, jobject obj, jint pat, jint row, jbyteArray rowNotes, jbyteArray rowInstruments)
{
	struct xmp_pattern *xxp;
	unsigned char row_note[XMP_MAX_CHANNELS];
	unsigned char row_ins[XMP_MAX_CHANNELS];
	int chn;
	int i;

	if (!_mod_is_loaded)
		return;

	if (pat > mi.mod->pat || row > mi.mod->xxp[pat]->rows)
		return;

 	xxp = mi.mod->xxp[pat];
	chn = mi.mod->chn;

	for (i = 0; i < chn; i++) {
		struct xmp_track *xxt = mi.mod->xxt[xxp->index[i]];
		struct xmp_event *e = &xxt->event[row];

		row_note[i] = e->note;
		row_ins[i] = e->ins;
	}

	(*env)->SetByteArrayRegion(env, rowNotes, 0, chn, row_note);
	(*env)->SetByteArrayRegion(env, rowInstruments, 0, chn, row_ins);
}

JNIEXPORT void JNICALL
Java_org_helllabs_android_xmp_Xmp_getSampleData(JNIEnv *env, jobject obj, jboolean trigger, jint ins, jint key, jint period, jint chn, jint width, jbyteArray buffer)
{
	struct xmp_subinstrument *sub;
	struct xmp_sample *xxs;
	int i, pos, transient_size;
	int limit;
	int step, len, lps, lpe;
 
	if (!_mod_is_loaded)
		return;

	if (width > MAX_BUFFER_SIZE) {
		width = MAX_BUFFER_SIZE;
	}

	if (period == 0) {
		goto err;
	}

	if (ins < 0 || ins > mi.mod->ins || key > 0x80) {
		goto err;
	}

	sub = get_subinstrument(ins, key);
	if (sub == NULL || sub->sid < 0 || sub->sid >= mi.mod->smp) {
		goto err;
	}

	xxs = &mi.mod->xxs[sub->sid];
	if (xxs == NULL || xxs->flg & XMP_SAMPLE_SYNTH || xxs->len == 0) {
		goto err;
	}

	step = (XMP_PERIOD_BASE << 5) / period;
	len = xxs->len << 5;
	lps = xxs->lps << 5;
	lpe = xxs->lpe << 5;

	pos = _pos[chn];

	/* In case of new keypress, reset sample */
	if (trigger == JNI_TRUE || (pos >> 5) >= xxs->len) {
		pos = 0;
	}

	/* Limit is the buffer size or the remaining transient size */
	if (step == 0) {
		transient_size = 0;
	} else if (xxs->flg & XMP_SAMPLE_LOOP) {
		transient_size = (lps - pos) / step;
	} else {
		transient_size = (len - pos) / step;
	}

	if (transient_size < 0) {
		transient_size = 0;
	}

	limit = width;
	if (limit > transient_size) {
		limit = transient_size;
	}

	if (xxs->flg & XMP_SAMPLE_16BIT) {
		/* transient */
		for (i = 0; i < limit; i++) {
			_buffer[i] = ((short *)xxs->data)[pos >> 5] >> 8;
			pos += step;
		}

		/* loop */
		if (xxs->flg & XMP_SAMPLE_LOOP) {
			for (i = limit; i < width; i++) {
				_buffer[i] = ((short *)xxs->data)[pos >> 5] >> 8;	
				pos += step;
				if (pos >= lpe)
					pos = lps + pos - lpe;
				if (pos >= lpe)		/* avoid division */
					pos = lps;
			}
		} else {
			for (i = limit; i < width; i++) {
				_buffer[i] = 0;	
			}
		}
	} else {
		/* transient */
		for (i = 0; i < limit; i++) {
			_buffer[i] = xxs->data[pos >> 5];
			pos += step;
		}

		/* loop */
		if (xxs->flg & XMP_SAMPLE_LOOP) {
			for (i = limit; i < width; i++) {
				_buffer[i] = xxs->data[pos >> 5];
				pos += step;
				if (pos >= lpe)
					pos = lps + pos - lpe;
				if (pos >= lpe)		/* avoid division */
					pos = lps;
			}
		} else {
			for (i = limit; i < width; i++) {
				_buffer[i] = 0;	
			}
		}
	}

	_pos[chn] = pos;

	(*env)->SetByteArrayRegion(env, buffer, 0, width, _buffer);
	return;

    err:
	memset(_buffer, 0, width);
	(*env)->SetByteArrayRegion(env, buffer, 0, width, _buffer);
}

JNIEXPORT jboolean JNICALL
Java_org_helllabs_android_xmp_Xmp_setSequence(JNIEnv *env, jobject obj, jint seq)
{
	if (seq >= mi.num_sequences)
		return JNI_FALSE;

	if (mi.seq_data[_sequence].duration <= 0)
		return JNI_FALSE;

	if (_sequence == seq)
		return JNI_FALSE;

	_sequence = seq;

	xmp_set_position(ctx, mi.seq_data[_sequence].entry_point);

	return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_org_helllabs_android_xmp_Xmp_getSeqVars(JNIEnv *env, jobject obj, jintArray vars)
{
	int i, num, v[16];

	if (!_mod_is_loaded)
		return;

	num = mi.num_sequences;
	if (num > 16) {
		num = 16;
	}

	for (i = 0; i < num; i++) {
		v[i] = mi.seq_data[i].duration;
	}

	(*env)->SetIntArrayRegion(env, vars, 0, num, v);
}
