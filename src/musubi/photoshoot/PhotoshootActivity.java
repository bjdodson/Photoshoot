package musubi.photoshoot;

import java.io.IOException;
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
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class PhotoshootActivity extends Activity implements OnClickListener {
    final static Map<Uri, CameraContentObserver> mCameraObservers = new HashMap<Uri, CameraContentObserver>();
    static final String TAG = "Photoshoot";
    static final int PHOTOSHOOT_ID = 2;

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
        CharSequence tickerText = "New photo shoot.";
        long when = System.currentTimeMillis();
        Notification notification = new Notification(icon, tickerText, when);

        /** User notification **/
        CharSequence contentTitle = "Photo Shoot";
        CharSequence contentText = "Sharing photos. Click to end photoshoot.";
        Intent notifyIntent = new Intent(this, PhotoshootActivity.class);
        notifyIntent.putExtra(Musubi.EXTRA_FEED_URI, mMusubi.getFeed().getUri());
        notifyIntent.putExtra(Musubi.EXTRA_OBJ_URI, mAlbumUri);
        PendingIntent contentIntent = PendingIntent.getActivity(this,
                0, notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        notification.setLatestEventInfo(this, contentTitle, contentText, contentIntent);
        notification.flags = Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.notify(PHOTOSHOOT_ID, notification);
    }

    void cancelNotification() {
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.cancel(PHOTOSHOOT_ID);
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
            meta.put(Obj.FIELD_RENDER_TYPE, Obj.RENDER_LATEST);
        } catch (JSONException e) {
            throw new IllegalStateException("Bad json library");
        }
        return new MemObj("album", meta);
    }

    @Override
    public void onClick(View v) {
        Button button = (Button)findViewById(R.id.shoot);
        if (mShooting) {
            for (Uri uri : mCameraObservers.keySet()) {
                CameraContentObserver obs = mCameraObservers.get(uri);
                getContentResolver().unregisterContentObserver(obs);   
            }
            mCameraObservers.clear();
            cancelNotification();
            button.setText("Start shooting");
        } else {
            if (mAlbumUri == null) {
                Obj album = createAlbumObj();
                mAlbumUri = mMusubi.getFeed().insert(album);
            }
            CameraContentObserver obs = new CameraContentObserver(mAlbumUri);
            mCameraObservers.put(mAlbumUri, obs);
            getContentResolver().registerContentObserver(Images.Media.EXTERNAL_CONTENT_URI, true, obs);
            doNotification();
            launchCamera();
            button.setText("Stop shooting");
        }
        mShooting = !mShooting;
    };

    /**
     * Listen for newly captured images from the camera.
     * @author bjdodson
     *
     */
    class CameraContentObserver extends ContentObserver {
        private Uri mmAlbumUri;
        private Uri mmLastShared;

        public CameraContentObserver(Uri albumUri) {
            super(new Handler(getMainLooper()));
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
                UriImage image = new UriImage(PhotoshootActivity.this, photo);
                JSONObject meta = new JSONObject();
                byte[] data = image.getImageThumbnailData();
                Obj obj = new MemObj("picture", meta, data);
                mMusubi.objForUri(mmAlbumUri).getSubfeed().postObj(obj);

                /** TODO: Put in the Corral **/
                // (currently handled in PictureObj)
            } catch (IOException e) {
                Log.e(TAG, "Error capturing photo", e);
            }
        };

        private Uri getLatestCameraPhoto() {
            String selection = ImageColumns.BUCKET_DISPLAY_NAME + " = 'Camera'";
            String[] selectionArgs = null;
            String sort = ImageColumns._ID + " DESC LIMIT 1";
            Cursor c = android.provider.MediaStore.Images.Media.query(getContentResolver(),
                            Images.Media.EXTERNAL_CONTENT_URI, new String[] { ImageColumns._ID },
                            selection, selectionArgs, sort);
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
}