package musubi.flipit;

import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.musubi.Musubi;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;

public class FlipbookViewerActivity extends FragmentActivity implements LoaderCallbacks<Cursor>,
        SeekBar.OnSeekBarChangeListener {
    DbObj mAlbum;
    Cursor mCursor;
    int mCount;

    boolean mPlayForward = true;
    enum PlayMode { NONE, LOOP, ROCK };
    PlayMode mPlayMode = PlayMode.NONE;
    Button mPlayButton;
    Button mRockButton;
    Button mStopButton;
    SeekBar mSeekBar;
    ImageView mImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.album);
        mPlayButton = (Button)findViewById(R.id.play);
        mPlayButton.setOnClickListener(mPlayListener);

        mRockButton = (Button)findViewById(R.id.rock);
        mRockButton.setOnClickListener(mRockListener);

        mStopButton = (Button)findViewById(R.id.stop);
        mStopButton.setOnClickListener(mStopListener);

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
        String[] selectionArgs = new String[] { FlipbookCreatorActivity.TYPE_IMAGE };
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
    public void onProgressChanged(SeekBar seekBar, final int progress, boolean fromUser) {
        if (mCount == 0) {
            return;
        }
        Drawable d = mImage.getDrawable();
        if (d != null) {
            ((BitmapDrawable)d).getBitmap().recycle();
        }

        mCursor.moveToPosition(progress);
        byte[] data = mCursor.getBlob(1);
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        mImage.setImageBitmap(bitmap);

        if (mPlayMode != PlayMode.NONE) {
            new Thread() {
                public void run() {
                    try {
                        Thread.sleep(30);
                    } catch (InterruptedException e) {}

                    if (mPlayMode == PlayMode.LOOP) {
                        mSeekBar.setProgress((progress + 1) % mCount);
                    } else if (mPlayMode == PlayMode.ROCK) {
                        if (0 == progress) {
                            mPlayForward = true;
                        } else if (progress == mCount - 1) {
                            mPlayForward = false;
                        }

                        if (mPlayForward) {
                            mSeekBar.setProgress(progress + 1);
                        } else {
                            mSeekBar.setProgress(progress - 1);
                        }
                    }
                };
            }.start();
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }

    View.OnClickListener mPlayListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            boolean wasPlaying = mPlayMode != PlayMode.NONE;
            mPlayMode = PlayMode.LOOP;
            if (!wasPlaying) {
                if (mSeekBar.getProgress() == mCount - 1) {
                    mSeekBar.setProgress(0);
                } else {
                    mSeekBar.incrementProgressBy(1);
                }
            }
        }
    };

    View.OnClickListener mRockListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            boolean wasPlaying = mPlayMode != PlayMode.NONE;
            mPlayMode = PlayMode.ROCK;
            if (!wasPlaying) {
                if (mSeekBar.getProgress() == mCount - 1) {
                    mSeekBar.setProgress(0);
                } else {
                    mSeekBar.incrementProgressBy(1);
                }
            }
        }
    };

    View.OnClickListener mStopListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mPlayMode = PlayMode.NONE;
        }
    };
}
