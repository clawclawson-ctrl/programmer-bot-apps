package com.relaximus.dreamchords;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.*;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.WindowManager;
import android.webkit.*;
import android.widget.Toast;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;

public class MainActivity extends Activity {
    static final String HOST = "appassets.androidplatform.net";
    WebView web;
    Uri lastUri;
    final HashMap<String, File> uploads = new HashMap<>();
    int nextId = 0;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        web.setBackgroundColor(0xff000000);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (HOST.equals(u.getHost())) {
                    try {
                        String path = u.getPath() == null ? "" : u.getPath().replaceFirst("^/", "");
                        String mime = path.endsWith(".html") ? "text/html" : "application/octet-stream";
                        return new WebResourceResponse(mime, "utf-8", getAssets().open(path));
                    } catch (IOException e) { return new WebResourceResponse("text/plain", "utf-8", 404, "Not found", null, null); }
                }
                return null;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return !HOST.equals(r.getUrl().getHost()); // block navigation away
            }
        });
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "DCNative");
        setContentView(web);
        web.loadUrl("https://" + HOST + "/dream-chords.html");
    }

    @Override protected void onDestroy() { web.destroy(); super.onDestroy(); }

    class Bridge {
        @JavascriptInterface public void setKeepOn(final boolean on) {
            runOnUiThread(() -> { if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); });
        }
        @JavascriptInterface public synchronized String begin() {
            String id = "u" + (nextId++);
            File f = new File(getCacheDir(), id + ".bin"); f.delete();
            uploads.put(id, f); return id;
        }
        @JavascriptInterface public void write(String id, String b64) throws IOException {
            File f = uploads.get(id); if (f == null) return;
            try (FileOutputStream o = new FileOutputStream(f, true)) { o.write(Base64.decode(b64, Base64.DEFAULT)); }
        }
        @JavascriptInterface public String save(String id, String name, String mime) {
            try { File f = uploads.remove(id); Uri u = publish(f, name, mime); f.delete(); return u.toString(); }
            catch (Exception e) { return "ERR: " + e.getMessage(); }
        }
        @JavascriptInterface public String muxSave(String videoId, String pcmId, int sr, String name) {
            File v = uploads.remove(videoId), a = uploads.remove(pcmId);
            File out = new File(getCacheDir(), "mux.mp4");
            try { mux(v, a, sr, out); Uri u = publish(out, name, "video/mp4"); return u.toString(); }
            catch (Exception e) { return "ERR: " + e; }
            finally { if (v != null) v.delete(); if (a != null) a.delete(); out.delete(); }
        }
        @JavascriptInterface public void shareLast() {
            runOnUiThread(() -> {
                if (lastUri == null) { Toast.makeText(MainActivity.this, "Save a video first", Toast.LENGTH_SHORT).show(); return; }
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("video/mp4"); i.putExtra(Intent.EXTRA_STREAM, lastUri);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(i, "Share Dream Chords video"));
            });
        }
    }

    Uri publish(File src, String name, String mime) throws IOException {
        ContentResolver cr = getContentResolver();
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        Uri uri;
        if (Build.VERSION.SDK_INT >= 29) {
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/DreamChords");
            cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
            uri = cr.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), cv);
            if (uri == null) throw new IOException("MediaStore insert failed");
            try (InputStream in = new FileInputStream(src); OutputStream o = cr.openOutputStream(uri)) { copy(in, o); }
            ContentValues done = new ContentValues(); done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            cr.update(uri, done, null, null);
        } else {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread(() -> requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1));
                throw new IOException("Storage permission needed. Allow it, then save again.");
            }
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "DreamChords");
            dir.mkdirs(); File dst = new File(dir, name);
            try (InputStream in = new FileInputStream(src); OutputStream o = new FileOutputStream(dst)) { copy(in, o); }
            cv.put(MediaStore.MediaColumns.DATA, dst.getAbsolutePath());
            uri = cr.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) uri = Uri.fromFile(dst);
        }
        lastUri = uri;
        return uri;
    }

    static void copy(InputStream in, OutputStream o) throws IOException {
        byte[] b = new byte[65536]; int n; while ((n = in.read(b)) > 0) o.write(b, 0, n);
    }

    /** Video track from the JS-made MP4 + native AAC from 16-bit stereo PCM -> out.mp4 */
    static void mux(File video, File pcm, int sr, File out) throws IOException {
        MediaExtractor ex = new MediaExtractor();
        ex.setDataSource(video.getAbsolutePath());
        int vt = -1;
        for (int i = 0; i < ex.getTrackCount(); i++) {
            String m = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if (m != null && m.startsWith("video/")) { vt = i; break; }
        }
        if (vt < 0) throw new IOException("no video track");
        ex.selectTrack(vt);
        MediaFormat vf = ex.getTrackFormat(vt);

        MediaFormat af = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sr, 2);
        af.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        af.setInteger(MediaFormat.KEY_BIT_RATE, 192000);
        af.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
        MediaCodec enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        enc.configure(af, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        enc.start();
        ArrayList<byte[]> pk = new ArrayList<>(); ArrayList<Long> pts = new ArrayList<>();
        MediaFormat outFmt = null;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long framesIn = 0; boolean inDone = false, outDone = false;
        byte[] tmp = new byte[8192];
        try (InputStream in = new BufferedInputStream(new FileInputStream(pcm), 1 << 16)) {
            while (!outDone) {
                if (!inDone) {
                    int ii = enc.dequeueInputBuffer(10000);
                    if (ii >= 0) {
                        ByteBuffer ib = enc.getInputBuffer(ii); ib.clear();
                        int want = Math.min(tmp.length, ib.remaining()) & ~3, n = 0, r;
                        while (n < want && (r = in.read(tmp, n, want - n)) > 0) n += r;
                        n &= ~3;
                        long t = framesIn * 1000000L / sr;
                        if (n <= 0) { enc.queueInputBuffer(ii, 0, 0, t, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true; }
                        else { ib.put(tmp, 0, n); enc.queueInputBuffer(ii, 0, n, t, 0); framesIn += n / 4; }
                    }
                }
                int oi = enc.dequeueOutputBuffer(info, 10000);
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) outFmt = enc.getOutputFormat();
                else if (oi >= 0) {
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && info.size > 0) {
                        ByteBuffer ob = enc.getOutputBuffer(oi);
                        byte[] d = new byte[info.size]; ob.position(info.offset); ob.get(d);
                        pk.add(d); pts.add(info.presentationTimeUs);
                    }
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outDone = true;
                    enc.releaseOutputBuffer(oi, false);
                }
            }
        } finally { enc.stop(); enc.release(); }
        if (outFmt == null) throw new IOException("AAC encoder gave no format");

        MediaMuxer mx = new MediaMuxer(out.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        int vIdx = mx.addTrack(vf), aIdx = mx.addTrack(outFmt);
        mx.start();
        int cap = vf.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE) ? vf.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) : 0;
        ByteBuffer vb = ByteBuffer.allocate(Math.max(cap, 4 << 20));
        MediaCodec.BufferInfo vi = new MediaCodec.BufferInfo();
        int ai = 0;
        while (true) {
            vb.clear();
            int n = ex.readSampleData(vb, 0);
            if (n < 0) break;
            long t = ex.getSampleTime();
            // interleave audio packets up to this video time
            while (ai < pk.size() && pts.get(ai) <= t) { writeA(mx, aIdx, pk.get(ai), pts.get(ai)); ai++; }
            int fl = (ex.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
            vi.set(0, n, t, fl);
            mx.writeSampleData(vIdx, vb, vi);
            ex.advance();
        }
        while (ai < pk.size()) { writeA(mx, aIdx, pk.get(ai), pts.get(ai)); ai++; }
        mx.stop(); mx.release(); ex.release();
    }
    static void writeA(MediaMuxer mx, int idx, byte[] d, long t) {
        MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo(); bi.set(0, d.length, t, 0);
        mx.writeSampleData(idx, ByteBuffer.wrap(d), bi);
    }
}
