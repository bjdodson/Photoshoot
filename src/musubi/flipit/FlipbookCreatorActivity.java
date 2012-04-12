package musubi.flipit;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import mobisocial.musubi.util.UriImage;
import mobisocial.socialkit.Obj;
import mobisocial.socialkit.musubi.Musubi;
import mobisocial.socialkit.obj.MemObj;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class FlipbookCreatorActivity extends Activity implements OnClickListener {
    static final String TAG = "FlipIt";
    static final int FLIPIT_ID = 2;

    static final String TYPE_FLIPBOOK = "flipbook";
    static final String TYPE_IMAGE = "image";

    final static Map<Uri, CameraContentObserver> sCameraObservers = new HashMap<Uri, CameraContentObserver>();
    final static LooperThread sLooper = new LooperThread();


    Musubi mMusubi;
    boolean mShooting;
    Uri mAlbumUri;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mMusubi = Musubi.forIntent(this, getIntent());
        Button button = (Button)findViewById(R.id.shoot);
        button.setOnClickListener(this);

        if (getIntent().hasExtra(Musubi.EXTRA_OBJ_URI)) {
            mAlbumUri = getIntent().getParcelableExtra(Musubi.EXTRA_OBJ_URI);
            button.setText("Stop shooting");
            mShooting = true;
        } else {
            button.setText("Start shooting");
            mShooting = false;
        }
    }

    void doNotification() {
        int icon = android.R.drawable.ic_menu_camera;
        CharSequence tickerText = "New flipbook.";
        long when = System.currentTimeMillis();
        Notification notification = new Notification(icon, tickerText, when);

        /** User notification **/
        CharSequence contentTitle = "Flip-It";
        CharSequence contentText = "Capturing flipbook. Click to stop.";
        Intent notifyIntent = new Intent(this, FlipbookCreatorActivity.class);
        notifyIntent.putExtra(Musubi.EXTRA_FEED_URI, mMusubi.getFeed().getUri());
        notifyIntent.putExtra(Musubi.EXTRA_OBJ_URI, mAlbumUri);
        PendingIntent contentIntent = PendingIntent.getActivity(this,
                0, notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        notification.setLatestEventInfo(this, contentTitle, contentText, contentIntent);
        notification.flags = Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.notify(FLIPIT_ID, notification);
    }

    void cancelNotification() {
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.cancel(FLIPIT_ID);
    }

    void launchCamera() {
        try {
            Intent camera = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
            camera.addCategory(Intent.CATEGORY_DEFAULT);
            startActivity(camera);
        } catch (ActivityNotFoundException e) {
            Intent launchCamera = new Intent(Intent.ACTION_CAMERA_BUTTON);
            launchCamera.addCategory(Intent.CATEGORY_DEFAULT);
            KeyEvent event = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CAMERA);
            launchCamera.putExtra(Intent.EXTRA_KEY_EVENT, event);
            sendOrderedBroadcast(launchCamera, null);
        }
    }

    Obj createAlbumObj() {
        JSONObject meta = new JSONObject();
        try {
            StringBuilder html = new StringBuilder();
            html.append("<span style=\"background-color:#8bc7e1;\">F</span>")
                .append("<span style=\"background-color:#ccdf8d;\">l</span>")
                .append("<span style=\"background-color:#f3dd7a;\">i</span>")
                .append("<span style=\"background-color:#cc5086;\">p</span>")
                .append("<span style=\"background-color:#ffffff;\">-</span>")
                .append("<span style=\"background-color:#a374a0;\">I</span>")
                .append("<span style=\"background-color:#eec630;\">t</span>")
                .append("<span style=\"background-color:#5e90b1;\">!</span>");
            meta.put(Obj.FIELD_HTML, html.toString());
        } catch (JSONException e) {
            throw new IllegalStateException("Bad json library");
        }
        return new MemObj(TYPE_FLIPBOOK, meta);
    }

    @Override
    public void onClick(View v) {
        Button button = (Button)findViewById(R.id.shoot);
        if (mShooting) {
            for (Uri uri : sCameraObservers.keySet()) {
                CameraContentObserver obs = sCameraObservers.get(uri);
                getContentResolver().unregisterContentObserver(obs);   
            }
            sCameraObservers.clear();
            cancelNotification();
            button.setText("Start shooting");

            Intent view = new Intent(Intent.ACTION_VIEW);
            view.setDataAndType(mAlbumUri, Musubi.mimeTypeFor(TYPE_FLIPBOOK));
            startActivity(view);
            finish();
        } else {
            if (mAlbumUri == null) {
                Obj album = createAlbumObj();
                mAlbumUri = mMusubi.getFeed().insert(album);
            }
            CameraContentObserver obs = new CameraContentObserver(mAlbumUri);
            sCameraObservers.put(mAlbumUri, obs);
            getContentResolver().registerContentObserver(Images.Media.EXTERNAL_CONTENT_URI, true, obs);
            doNotification();
            launchCamera();
            button.setText("Stop shooting");
        }
        mShooting = !mShooting;
    };

    /**
     * Listen for newly captured images from the camera.
     */
    class CameraContentObserver extends ContentObserver {
        private Uri mmAlbumUri;
        private Uri mmLastShared;
        private int mPictureCount = 0;
        private final long mCaptureStartTime = new Date().getTime();

        public CameraContentObserver(Uri albumUri) {
            super(sLooper.mHandler);
            mmAlbumUri = albumUri;
        }

        /**
         * A new photo has been detected in the media store.
         */
        public void onChange(boolean selfChange) {
            try {
                Uri photo = getLatestCameraPhoto();
                if (photo == null || photo.equals(mmLastShared)) {
                    return;
                }

                mmLastShared = photo;
                UriImage image = new UriImage(FlipbookCreatorActivity.this, photo);
                JSONObject meta = new JSONObject();
                byte[] data = image.getImageThumbnailData();
                Obj obj = new MemObj(TYPE_IMAGE, meta, data, mPictureCount++);
                mMusubi.objForUri(mmAlbumUri).getSubfeed().postObj(obj);

                Uri url = Images.Media.EXTERNAL_CONTENT_URI;
                String where = BaseColumns._ID + "=" + ContentUris.parseId(photo);
                getContentResolver().delete(url, where, null);
            } catch (IOException e) {
                Log.e(TAG, "Error capturing photo", e);
            }
        };

        private Uri getLatestCameraPhoto() {
            String[] projection = new String[] { ImageColumns._ID };
            String selection = ImageColumns.BUCKET_DISPLAY_NAME + " = 'Camera' AND "
                    + ImageColumns.DATE_TAKEN + " > ?";
            String[] selectionArgs = new String[] { Long.toString(mCaptureStartTime) };
            String sort = ImageColumns.DATE_TAKEN + " DESC LIMIT 1";
            Cursor c = MediaStore.Images.Media.query(getContentResolver(),
                            Images.Media.EXTERNAL_CONTENT_URI, projection, selection,
                            selectionArgs, sort);
            try {
                int idx = c.getColumnIndex(ImageColumns._ID);
                if (c.moveToFirst()) {
                    return Uri.withAppendedPath(Images.Media.EXTERNAL_CONTENT_URI, c.getString(idx));
                }
                return null;
            } finally {
                c.close();
            }
        }
    }

    /**
     * A background looper for long-lasting operations.
     */
    static class LooperThread extends Thread {
        public Handler mHandler;

        public void run() {
            Looper.prepare();

            mHandler = new Handler() {
                public void handleMessage(Message msg) {
                }
            };

            Looper.loop();
        }
    }
}