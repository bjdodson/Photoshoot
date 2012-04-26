package musubi.flipit;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

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
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
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

    final static Set<Uri> sFlipBooks = new HashSet<Uri>();
    final BroadcastReceiver mNewPictureReceiver = new NewPictureReceiver();

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

    Obj createFlipbookObj() {
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
            sFlipBooks.clear();
            try {
                unregisterReceiver(mNewPictureReceiver);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "receiver wasn't registered", e);
            }
            cancelNotification();
            button.setText("Start shooting");

            Intent view = new Intent(Intent.ACTION_VIEW);
            view.setDataAndType(mAlbumUri, Musubi.mimeTypeFor(TYPE_FLIPBOOK));
            startActivity(view);
            finish();
        } else {
            if (mAlbumUri == null) {
                Obj album = createFlipbookObj();
                mAlbumUri = mMusubi.getFeed().insert(album);
            }
            if (sFlipBooks.isEmpty()) {
                try {
                    IntentFilter filter = new IntentFilter();
                    filter.addAction("com.android.camera.NEW_PICTURE");
                    filter.addDataType("image/*");
                    registerReceiver(mNewPictureReceiver, filter);
                } catch (MalformedMimeTypeException e) {
                    Log.e(TAG, "Mime fail", e);
                }
            }
            sFlipBooks.add(mAlbumUri);
            doNotification();
            launchCamera();
            button.setText("Stop shooting");
        }
        mShooting = !mShooting;
    };

    /**
     * Listen for NEW_PICTURE intent broadcasted from the camera.
     */
    class NewPictureReceiver extends BroadcastReceiver {
        int mPictureCount;

        @Override
        public void onReceive(Context context, Intent intent) {
            Uri img = intent.getData();
            if (img != null) {
                try {
                    UriImage image = new UriImage(FlipbookCreatorActivity.this, img);
                    JSONObject meta = new JSONObject();
                    byte[] data = image.getImageThumbnailData();
                    Obj obj = new MemObj(TYPE_IMAGE, meta, data, mPictureCount++);
                    for (Uri flip : sFlipBooks) {
                        mMusubi.objForUri(flip).getSubfeed().postObj(obj);
                    }

                    Uri url = Images.Media.EXTERNAL_CONTENT_URI;
                    String where = BaseColumns._ID + "=" + ContentUris.parseId(img);
                    getContentResolver().delete(url, where, null);
                } catch (IOException e) {
                    Log.e(TAG, "Error converting image", e);
                }
            }
        }
    }
}