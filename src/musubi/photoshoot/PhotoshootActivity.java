package musubi.photoshoot;

import java.io.File;
import java.io.IOException;

import mobisocial.musubi.util.UriImage;
import mobisocial.socialkit.Obj;
import mobisocial.socialkit.musubi.Musubi;
import mobisocial.socialkit.obj.MemObj;

import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

public class PhotoshootActivity extends Activity {
    Musubi mMusubi;

    static final int REQUEST_CAPTURE_IMAGE = 1;
  
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mMusubi = Musubi.forIntent(this, getIntent());
        startActivityForResult(captureImageIntent(), REQUEST_CAPTURE_IMAGE);
    }

    Intent captureImageIntent() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(getTempFile()));
        return intent;
    }

    Obj objFromIntent(Intent intent) {
        UriImage image = new UriImage(this, Uri.fromFile(getTempFile()));
        try {
            JSONObject meta = new JSONObject();
            byte[] data = image.getImageThumbnailData();
            return new MemObj("picture", meta, data);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CAPTURE_IMAGE) {
            if (resultCode != Activity.RESULT_OK) {
                finish();
                return;
            }

            Obj obj = objFromIntent(data);
            if (obj == null) {
                Toast.makeText(this, "Failed to capture image.", Toast.LENGTH_SHORT).show();
                return;
            }

            mMusubi.getFeed().postObj(obj);
            startActivityForResult(captureImageIntent(), REQUEST_CAPTURE_IMAGE);
        }
    }

    File getTempFile() {
        // it will return /sdcard/tmp/
        final File path = new File(Environment.getExternalStorageDirectory(), "tmp");
        if (!path.exists()) {
            path.mkdir();
        }
        return new File(path, "photoshoot-capture.tmp");
    }
}