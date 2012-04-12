package musubi.photoshoot;

import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.musubi.Musubi;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.widget.ImageView;
import android.widget.SeekBar;

public class PhotoshootViewerActivity extends FragmentActivity implements LoaderCallbacks<Cursor>,
        SeekBar.OnSeekBarChangeListener {
    DbObj mAlbum;
    Cursor mCursor;
    int mCount;

    SeekBar mSeekBar;
    ImageView mImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.album);
        mSeekBar = (SeekBar)findViewById(R.id.seek);
        mSeekBar.setOnSeekBarChangeListener(this);
        mImage = (ImageView)findViewById(R.id.image);

        // Activity has a single intent filter for viewing data of
        // type vnd.mobisocial.obj/album
        mAlbum = Musubi.forIntent(this, getIntent()).getObj();
        getSupportLoaderManager().initLoader(0, null, this);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
        Uri uri = mAlbum.getSubfeed().getObjectsUri();
        String[] projection = new String[] { DbObj.COL_ID, DbObj.COL_RAW };
        String selection = "type = ?";
        String[] selectionArgs = new String[] { "picture" };
        String sortOrder = DbObj.COL_INT_KEY + " asc," + DbObj.COL_ID + " asc";
        return new CursorLoader(this, uri, projection, selection, selectionArgs, sortOrder);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        mCursor = cursor;
        mCount = cursor.getCount();
        mSeekBar.setMax(mCount - 1);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> arg0) {

    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        mCursor.moveToPosition(progress);
        byte[] data = mCursor.getBlob(1);
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        mImage.setImageBitmap(bitmap);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        
    }
}